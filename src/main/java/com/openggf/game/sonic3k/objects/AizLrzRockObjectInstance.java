package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x05 - AIZ/LRZ/EMZ Rock (Sonic 3 &amp; Knuckles).
 * <p>
 * Multi-zone breakable/pushable rock appearing in Angel Island Zone and Lava Reef Zone.
 * Uses zone-specific art and mappings. EMZ (competition mode) is out of scope.
 * <p>
 * Subtype encoding (from Obj_AIZLRZEMZRock, sonic3k.asm:43838):
 * <ul>
 *   <li>Bits 4-6: Size/frame index (0-7), indexes into collision size table</li>
 *   <li>Bit 0: Breakable from top (player spinning while standing)</li>
 *   <li>Bit 1: Pushable (push detection enabled)</li>
 *   <li>Bit 2: Breakable from sides (horizontal push break)</li>
 *   <li>Bit 3: Breakable from bottom (vertical crush)</li>
 * </ul>
 * <p>
 * ROM references: Obj_AIZLRZEMZRock (sonic3k.asm:43838), byte_1F9D0 (size table).
 */
public class AizLrzRockObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOG = Logger.getLogger(AizLrzRockObjectInstance.class.getName());

    // Collision size table (byte_1F9D0): halfWidth, halfHeight per size index
    // Indexed by subtype bits 4-6
    private static final int[][] SIZE_TABLE = {
            {24, 39},  // 0: Large (AIZ)
            {24, 23},  // 1: Medium (AIZ)
            {24, 15},  // 2: Small (AIZ)
            {14, 15},  // 3: Small square
            {16, 40},  // 4: Tall vertical (LRZ)
            {40, 16},  // 5: Wide horizontal (LRZ)
            {40, 16},  // 6: Wide horizontal (LRZ)
            {16, 32},  // 7: Tall (LRZ)
    };

    // Push mode constants (same as spikes: sub_200A2)
    private static final int PUSH_RATE_PERIOD = 0x10;   // every 17 frames
    private static final int PUSH_MAX_DISTANCE = 0x20;  // 32 pixels total

    // Subtype behavior bits
    private static final int BIT_BREAK_TOP = 0x01;
    private static final int BIT_PUSHABLE = 0x02;
    private static final int BIT_BREAK_SIDE = 0x04;
    private static final int BIT_BREAK_BOTTOM = 0x08;

    // Zone variant determines art key and frame mapping
    private enum ZoneVariant {
        AIZ1(Sonic3kObjectArtKeys.AIZ1_ROCK, 0, 1, 3),
        AIZ2(Sonic3kObjectArtKeys.AIZ2_ROCK, 0, 2, 3),
        LRZ1(Sonic3kObjectArtKeys.LRZ1_ROCK, 4, 2, 0),
        LRZ2(Sonic3kObjectArtKeys.LRZ2_ROCK, 0, 3, 0),
        UNKNOWN(null, 0, 0, 0);

        final String artKey;
        final int frameOffset;     // Added to size index for frame selection
        final int sheetPalette;
        final int debrisBaseFrame; // First debris animation frame in mapping

        ZoneVariant(String artKey, int frameOffset, int sheetPalette, int debrisBaseFrame) {
            this.artKey = artKey;
            this.frameOffset = frameOffset;
            this.sheetPalette = sheetPalette;
            this.debrisBaseFrame = debrisBaseFrame;
        }
    }

    private final int baseX;
    private final int baseY;
    private int currentX;
    private int currentY;
    private ObjectSpawn dynamicSpawn;

    private final ZoneVariant variant;
    private final int sizeIndex;
    private final int behaviorBits;
    private final int displayFrame;

    // Push mode state
    private boolean contactPushingActive;
    private int pushRateTimer;
    private int pushDistanceRemaining = PUSH_MAX_DISTANCE;

    // Solid contact tracking for break detection
    private boolean playerStandingOnRock;
    private boolean playerPushingSide;

    // State: false = solid, true = breaking/destroyed
    private boolean breaking;

    public AizLrzRockObjectInstance(ObjectSpawn spawn) {
        super(spawn, "AIZLRZRock");
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.currentX = baseX;
        this.currentY = baseY;
        this.dynamicSpawn = spawn;

        this.sizeIndex = (spawn.subtype() >> 4) & 0x07;
        this.behaviorBits = spawn.subtype() & 0x0F;

        // Determine zone variant from current level
        this.variant = resolveVariant();

        // Display frame = size index + zone-specific frame offset
        // ROM: AIZ uses size index directly (0-2), LRZ1 adds 4 (frames 4-7)
        this.displayFrame = sizeIndex + variant.frameOffset;
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (player == null || breaking) {
            return;
        }

        // Track standing/pushing for break detection in update()
        if (contact.standing()) {
            playerStandingOnRock = true;
        }
        if (contact.pushing()) {
            playerPushingSide = true;
            if ((behaviorBits & BIT_PUSHABLE) != 0) {
                contactPushingActive = true;
            }
        }

        // Bottom break: player hits from below
        if ((behaviorBits & BIT_BREAK_BOTTOM) != 0 && contact.touchBottom()) {
            if (isPlayerSpinning(player)) {
                breakRock(player);
            }
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (breaking) {
            // Already broken — destroyed by debris children going offscreen
            return;
        }

        // Check break from top: player standing + rolling/spinning
        if ((behaviorBits & BIT_BREAK_TOP) != 0 && playerStandingOnRock && player != null) {
            if (isPlayerSpinning(player)) {
                // Launch player upward (ROM: move.w #-$300,y_vel(a1))
                player.setYSpeed((short) -0x300);
                breakRock(player);
                playerStandingOnRock = false;
                playerPushingSide = false;
                return;
            }
        }

        // Check break from sides: player rolling with sufficient speed
        if ((behaviorBits & BIT_BREAK_SIDE) != 0 && playerPushingSide && player != null) {
            if (isPlayerSpinning(player) && Math.abs(player.getXSpeed()) >= 0x480) {
                breakRock(player);
                playerStandingOnRock = false;
                playerPushingSide = false;
                return;
            }
        }

        // Push mode
        if ((behaviorBits & BIT_PUSHABLE) != 0) {
            handlePush(player);
        }

        // Reset per-frame contact flags
        playerStandingOnRock = false;
        playerPushingSide = false;

        updateDynamicSpawn();
    }

    @Override
    public SolidObjectParams getSolidParams() {
        int idx = Math.clamp(sizeIndex, 0, SIZE_TABLE.length - 1);
        int halfWidth = SIZE_TABLE[idx][0];
        int halfHeight = SIZE_TABLE[idx][1];
        // +0x0B offset verified: sonic3k.asm:43924 (addi.w #$B,d1 before SolidObjectFull call)
        return new SolidObjectParams(halfWidth + 0x0B, halfHeight, halfHeight + 1);
    }

    @Override
    public ObjectSpawn getSpawn() {
        return dynamicSpawn;
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (breaking) {
            return;
        }
        if (variant == ZoneVariant.UNKNOWN || variant.artKey == null) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;

        PatternSpriteRenderer renderer = renderManager.getRenderer(variant.artKey);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(displayFrame, currentX, currentY, hFlip, vFlip);
        }
    }

    /**
     * Breaks the rock into debris fragments and plays collapse SFX.
     */
    private void breakRock(AbstractPlayableSprite player) {
        breaking = true;

        // Play collapse SFX
        if (isOnScreen()) {
            try {
                AudioManager.getInstance().playSfx(Sonic3kSfx.COLLAPSE.id);
            } catch (Exception e) {
                // Prevent audio failure from breaking game logic.
            }
        }

        // Spawn debris fragments using the existing BreakObjectToPieces velocity table
        spawnDebrisFragments(player);

        // Mark as destroyed since the rock is now replaced by debris children
        setDestroyed(true);
    }

    /**
     * Spawns debris fragment children with scattered velocities.
     * ROM: sub_2013A (BreakObjectToPieces) creates one child per mapping piece.
     */
    private void spawnDebrisFragments(AbstractPlayableSprite player) {
        if (variant.artKey == null) {
            return;
        }

        // Determine which debris frames to cycle through
        // AIZ: debris frames are 3-6 in the mapping
        // LRZ1: uses frames 0-3 (small rock rotations) for debris
        // LRZ2: uses frames 0-3 for debris
        int debrisStartFrame = variant.debrisBaseFrame;
        int debrisFrameCount = 4;

        // Use velocities from the shared BreakObjectToPieces table (word_2A8B0)
        int[][] velocities = AizRockFragmentChild.FRAGMENT_VELOCITIES;
        int fragmentCount = Math.min(velocities.length, 8);

        // Player direction affects scatter direction
        boolean scatterLeft = player != null && player.getXSpeed() < 0;

        for (int i = 0; i < fragmentCount; i++) {
            int xVel = velocities[i][0];
            int yVel = velocities[i][1];
            if (scatterLeft) {
                xVel = -xVel;
            }

            int debrisFrame = debrisStartFrame + (i % debrisFrameCount);

            // TODO: Should use per-piece rendering (AizRockFragmentChild pattern) for ROM-accurate debris
            ObjectSpawn debrisSpawn = new ObjectSpawn(
                    currentX, currentY, 0, 0, 0, false, 0);
            RockDebrisChild debris = new RockDebrisChild(
                    debrisSpawn, xVel, yVel, debrisFrame, variant.artKey);
            spawnDynamicObject(debris);
        }
    }

    /**
     * Pushable rock movement. ROM: sub_200A2.
     * Rate-limited to 1px every 17 frames, max 32px total.
     */
    // TODO: ROM sub_200A2 may support bidirectional push - currently only pushes right
    private void handlePush(AbstractPlayableSprite player) {
        boolean wasPushing = contactPushingActive;
        contactPushingActive = false;

        if (!wasPushing || player == null) {
            return;
        }
        if (!player.getPushing()) {
            return;
        }

        int playerX = player.getCentreX();
        if (currentX < playerX) {
            return;
        }

        pushRateTimer--;
        if (pushRateTimer >= 0) {
            return;
        }
        pushRateTimer = PUSH_RATE_PERIOD;

        if (pushDistanceRemaining <= 0) {
            return;
        }
        pushDistanceRemaining--;
        currentX++;
        player.setCentreX((short) (playerX + 1));
    }

    private void updateDynamicSpawn() {
        if (dynamicSpawn.x() == currentX && dynamicSpawn.y() == currentY) {
            return;
        }
        dynamicSpawn = new ObjectSpawn(
                currentX, currentY,
                spawn.objectId(), spawn.subtype(), spawn.renderFlags(),
                spawn.respawnTracked(), spawn.rawYWord());
    }

    /**
     * Checks if the player is in a spinning state (rolling, spindash, jumping while curled).
     * ROM checks: anim == 2 (rolling animation).
     */
    private boolean isPlayerSpinning(AbstractPlayableSprite player) {
        return player.getRolling();
    }

    /**
     * Determines zone variant based on current LevelManager zone/act.
     */
    private static ZoneVariant resolveVariant() {
        try {
            LevelManager lm = LevelManager.getInstance();
            if (lm == null) {
                return ZoneVariant.UNKNOWN;
            }
            int zone = lm.getCurrentZone();
            int act = lm.getCurrentAct();
            if (zone == Sonic3kZoneIds.ZONE_AIZ) {
                return act == 0 ? ZoneVariant.AIZ1 : ZoneVariant.AIZ2;
            }
            if (zone == Sonic3kZoneIds.ZONE_LRZ) {
                return act == 0 ? ZoneVariant.LRZ1 : ZoneVariant.LRZ2;
            }
        } catch (Exception e) {
            LOG.fine("Could not resolve zone variant: " + e.getMessage());
        }
        return ZoneVariant.UNKNOWN;
    }
}
