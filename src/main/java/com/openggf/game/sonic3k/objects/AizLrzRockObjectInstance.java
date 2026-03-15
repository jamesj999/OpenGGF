package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ShieldType;

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

    // Push mode constants (sub_200A2, child_dx=$42 init at sonic3k.asm:43855)
    private static final int PUSH_RATE_PERIOD = 0x10;   // every 17 frames after first push
    private static final int PUSH_MAX_DISTANCE = 0x40;  // 64 pixels total (ROM: move.w #$40,child_dx(a0))

    // Subtype behavior bits
    private static final int BIT_BREAK_TOP = 0x01;
    private static final int BIT_PUSHABLE = 0x02;
    private static final int BIT_BREAK_SIDE = 0x04;
    private static final int BIT_BREAK_BOTTOM = 0x08;

    // Side-break speed threshold: ROM loc_1FD90 cmpi.w #$480,d0
    private static final int SIDE_BREAK_SPEED_THRESHOLD = 0x480;

    // -----------------------------------------------------------------------
    // Per-frame debris position and velocity tables from ROM.
    // ROM: sub_2011E -> sub_2013A uses off_2026E (positions) and off_202E4
    // (velocities), both indexed by mapping_frame.
    //
    // Each entry: {xOffset, yOffset} (positions) or {xVel, yVel} (velocities).
    // Velocities have their sign randomised per piece by the ROM (odd pieces
    // get negated X); we apply the same pattern below.
    // -----------------------------------------------------------------------

    /** Per-frame debris position offsets from rock centre (off_2026E, sonic3k.asm:44643).
     *  Indexed by mapping_frame (0-3 for AIZ). Each size has its own debris layout. */
    private static final int[][][] DEBRIS_POSITIONS = {
            // Frame 0 (word_2027E): Large AIZ, 8 pieces
            {{-8, -0x18}, {0x0B, -0x1C}, {-4, -0x0C}, {0x0C, -4},
             {-0x0C, 4}, {4, 0x0C}, {-0x0C, 0x1C}, {0x0C, 0x1C}},
            // Frame 1 (word_20290): Medium AIZ, 5 pieces
            {{-4, -0x0C}, {0x0B, -0x0C}, {-4, -4}, {-0x0C, 0x0C}, {0x0C, 0x0C}},
            // Frame 2 (word_2029C): Small AIZ, 4 pieces
            {{-4, -4}, {0x0C, -4}, {-0x0C, 4}, {0x0C, 4}},
            // Frame 3 (word_202A6): Small square, 6 pieces
            {{-8, -8}, {8, -8}, {-8, 0}, {8, 0}, {-8, 8}, {8, 8}},
    };

    /** Per-frame debris velocities in subpixels (off_202E4, sonic3k.asm:44703).
     *  ROM tables contain final signed values — no odd-piece negation needed.
     *  Each size has unique velocity patterns; some have alternating X signs for scatter. */
    private static final int[][][] DEBRIS_VELOCITIES = {
            // Frame 0 (word_202F4): Large AIZ, 8 pieces — all fly left+up
            {{-0x300, -0x300}, {-0x2C0, -0x280}, {-0x2C0, -0x280}, {-0x280, -0x200},
             {-0x280, -0x180}, {-0x240, -0x180}, {-0x240, -0x100}, {-0x200, -0x100}},
            // Frame 1 (word_20334): Medium AIZ, 5 pieces — alternating X
            {{-0x200, -0x200}, {0x200, -0x200}, {-0x100, -0x1E0},
             {-0x1B0, -0x1C0}, {0x1C0, -0x1C0}},
            // Frame 2 (word_20348): Small AIZ, 4 pieces — alternating X
            {{-0x100, -0x200}, {0x100, -0x1E0}, {-0x1B0, -0x1C0}, {0x1C0, -0x1C0}},
            // Frame 3 (word_20358): Small square, 6 pieces — alternating X
            {{-0xB0, -0x1E0}, {0xB0, -0x1D0}, {-0x80, -0x200},
             {0x80, -0x1E0}, {-0xD8, -0x1C0}, {0xE0, -0x1C0}},
    };

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

    // ROM: player state saved BEFORE SolidObjectFull, checked AFTER.
    // Our engine's resolveContact zeroes velocity and clears rolling before onSolidContact,
    // so we snapshot the pre-contact values from ObjectManager.
    private boolean savedPreContactRolling;
    private int savedPreContactXSpeed;
    private int savedPreContactYSpeed;

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

        // ROM: objects save player velocity/anim BEFORE SolidObjectFull.
        // Our resolveContact has already zeroed velocity and cleared rolling by this point.
        // Read the pre-contact snapshot captured at the start of SolidContacts.update().
        ObjectManager om = LevelManager.getInstance().getObjectManager();
        if (om != null) {
            savedPreContactRolling = om.getPreContactRolling();
            savedPreContactXSpeed = om.getPreContactXSpeed();
            savedPreContactYSpeed = om.getPreContactYSpeed();
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
        // ROM (loc_1FF48): breaks on ANY bottom contact - no spin check.
        // ROM restores saved y_vel so the player continues upward through the breaking rock.
        if ((behaviorBits & BIT_BREAK_BOTTOM) != 0 && contact.touchBottom()) {
            breakRock(player);
            // ROM (loc_1FF84): move.w $30(a0),y_vel(a1) — restore pre-contact y velocity
            player.setYSpeed((short) savedPreContactYSpeed);
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (breaking) {
            // Already broken — destroyed by debris children going offscreen
            return;
        }

        // Check break from top: player standing + rolling/spinning (pre-contact state)
        // ROM (loc_1FB62): checks SAVED anim ($32(a0)) from BEFORE SolidObjectFull, not current.
        // This is critical because landing on the rock clears rolling via clearRollingOnLanding.
        if ((behaviorBits & BIT_BREAK_TOP) != 0 && playerStandingOnRock && player != null) {
            if (savedPreContactRolling) {
                // ROM (sub_1FBAE): set rolling, launch upward, detach from object
                player.setRolling(true);
                player.setYSpeed((short) -0x300);
                player.setAir(true);
                player.setOnObject(false);
                breakRock(player);
                playerStandingOnRock = false;
                playerPushingSide = false;
                return;
            }
        }

        // Check break from sides
        // ROM (loc_1FD72-loc_1FDA4): ordered checks:
        //   1. Knuckles (character_id == 2) -> break immediately
        //   2. Super_Sonic_Knux_flag -> break immediately
        //   3. Fire shield + rolling + |x_vel| >= 0x480 -> break
        //   4. Pushing + rolling + |x_vel| >= 0x480 -> break
        if ((behaviorBits & BIT_BREAK_SIDE) != 0 && playerPushingSide && player != null) {
            if (canSideBreak(player)) {
                // ROM (sub_1FE34): restore saved velocity, shift player, clear pushing
                player.setXSpeed((short) savedPreContactXSpeed);
                int playerX = player.getCentreX();
                if (playerX < currentX) {
                    // Player is left of rock — push left (ROM: subi.w #8 after addq.w #4 = net -4)
                    player.setCentreX((short) (playerX - 4));
                } else {
                    // Player is right of rock — push right (ROM: addq.w #4)
                    player.setCentreX((short) (playerX + 4));
                }
                player.setGSpeed(player.getXSpeed());
                player.setPushing(false);
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

    /**
     * Checks if the player can break this rock from the side.
     * ROM (loc_1FD72-loc_1FDA4):
     * <ol>
     *   <li>Knuckles (character_id == 2) -> always break</li>
     *   <li>Super Sonic/Knuckles flag set -> always break</li>
     *   <li>Fire shield + rolling + |x_vel| >= 0x480 -> break</li>
     *   <li>Pushing + rolling + |x_vel| >= 0x480 -> break</li>
     * </ol>
     */
    private boolean canSideBreak(AbstractPlayableSprite player) {
        // Check 1: Knuckles always breaks side-break rocks
        // ROM: cmpi.b #2,character_id(a1) / beq.s loc_1FDA4
        if (isKnuckles()) {
            return true;
        }

        // Check 2: Super mode bypasses all checks
        // ROM: tst.b (Super_Sonic_Knux_flag).w / bne.s loc_1FDA4
        if (player.isSuperSonic()) {
            return true;
        }

        // Checks 3 & 4 both require rolling + speed >= 0x480
        // ROM (loc_1FD90): checks CURRENT anim (which is unchanged within the object routine).
        // Our engine runs handleMovement() BETWEEN SolidContacts and object update, and
        // handleMovement un-rolls the player when gSpeed drops to 0 (zeroed by contact resolution).
        // Use pre-contact rolling state to match ROM behavior.
        if (!savedPreContactRolling || Math.abs(savedPreContactXSpeed) < SIDE_BREAK_SPEED_THRESHOLD) {
            return false;
        }

        // Check 3: Fire shield enables breaking (still needs rolling + speed)
        // ROM: btst #Status_FireShield,status_secondary(a1) / bne.s loc_1FD90
        if (player.getShieldType() == ShieldType.FIRE) {
            return true;
        }

        // Check 4: Player actively pushing against wall (normal break path)
        // ROM: btst #p1_pushing_bit,status(a0) — checks object's pushing bit
        return playerPushingSide;
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

        // Spawn debris fragments using per-frame ROM tables
        spawnDebrisFragments(player);

        // Mark as destroyed since the rock is now replaced by debris children.
        // Bug 5: breaking flag suppresses rendering, and setDestroyed(true)
        // removes the object from the object manager on next tick.
        setDestroyed(true);
    }

    /**
     * Spawns debris fragment children using per-frame position and velocity
     * tables from ROM (off_2026E positions, off_202E4 velocities).
     * <p>
     * ROM (sub_2011E -> sub_2013A): each mapping frame has a different piece
     * count and its own position/velocity tables. Velocities are used directly
     * from the table — no per-piece negation (the tables already contain the
     * final signed values with alternating X signs where intended).
     * Debris mapping frames cycle 3→4→5→6→3→... for AIZ.
     */
    private void spawnDebrisFragments(AbstractPlayableSprite player) {
        if (variant.artKey == null) {
            return;
        }

        // Clamp display frame to the table range (0-3 for AIZ frames)
        int frameIdx = Math.clamp(sizeIndex, 0, DEBRIS_POSITIONS.length - 1);
        int[][] positions = DEBRIS_POSITIONS[frameIdx];
        int[][] velocities = DEBRIS_VELOCITIES[frameIdx];
        int fragmentCount = positions.length;

        // ROM (sub_2013A): debris frames cycle 3,4,5,6,3,4,5,6...
        int debrisStartFrame = variant.debrisBaseFrame;

        for (int i = 0; i < fragmentCount; i++) {
            int xPos = currentX + positions[i][0];
            int yPos = currentY + positions[i][1];

            int xVel = velocities[i][0];
            int yVel = velocities[i][1];

            // ROM: d6 starts at 3, increments, wraps at 7 back to 3
            int debrisFrame = debrisStartFrame + (i % 4);

            ObjectSpawn debrisSpawn = new ObjectSpawn(
                    xPos, yPos, 0, 0, 0, false, 0);
            RockDebrisChild debris = new RockDebrisChild(
                    debrisSpawn, xVel, yVel, debrisFrame, variant.artKey);
            spawnDynamicObject(debris);
        }
    }

    /**
     * Pushable rock movement. ROM: sub_200A2 → sub_200CC.
     * Player must be to the RIGHT of the rock, pushing LEFT.
     * Rate-limited to 1px every 17 frames, max 64px total.
     * <p>
     * ROM checks saved player status bit 5 (pushing from previous frame) as a
     * debounce — the player must be pushing for 2 consecutive frames. Our
     * contactPushingActive flag provides equivalent debounce since it's set in
     * onSolidContact (during SolidContacts) and checked here (during update).
     */
    private void handlePush(AbstractPlayableSprite player) {
        boolean wasPushing = contactPushingActive;
        contactPushingActive = false;

        if (!wasPushing || player == null) {
            return;
        }

        int playerX = player.getCentreX();
        // ROM (sub_200CC): cmp.w x_pos(a1),d2 / bhs.s locret
        // Player must be to the RIGHT of the rock (playerX > rockX).
        if (currentX >= playerX) {
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
        // ROM: subq.w #1,x_pos(a0) / subq.w #1,x_pos(a1) — push LEFT
        currentX--;
        player.setCentreX((short) (playerX - 1));
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
     * Checks if the current player character is Knuckles.
     * ROM: cmpi.b #2,character_id(a1) — character_id 2 = Knuckles in S3K
     * (Player_mode 3 = KNUCKLES in the engine's PlayerCharacter enum).
     */
    private static boolean isKnuckles() {
        try {
            Sonic3kLevelEventManager lem = Sonic3kLevelEventManager.getInstance();
            return lem != null && lem.getPlayerCharacter() == PlayerCharacter.KNUCKLES;
        } catch (Exception e) {
            return false;
        }
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
