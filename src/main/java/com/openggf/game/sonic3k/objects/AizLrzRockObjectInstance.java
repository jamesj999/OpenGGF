package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.ShieldType;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x05 - AIZ/LRZ/EMZ Rock (Sonic 3 & Knuckles).
 */
public class AizLrzRockObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOG = Logger.getLogger(AizLrzRockObjectInstance.class.getName());

    private static final int[][] SIZE_TABLE = {
            {24, 39},
            {24, 23},
            {24, 15},
            {14, 15},
            {16, 40},
            {40, 16},
            {40, 16},
            {16, 32},
    };

    private static final int PUSH_RATE_PERIOD = 0x10;
    private static final int PUSH_MAX_DISTANCE = 0x40;

    private static final int BIT_BREAK_TOP = 0x01;
    private static final int BIT_PUSHABLE = 0x02;
    private static final int BIT_BREAK_SIDE = 0x04;
    private static final int BIT_BREAK_BOTTOM = 0x08;
    private static final int KNUCKLES_ONLY_STANDING_NIBBLE = 0x0F;
    private static final int SIDE_BREAK_SPEED_THRESHOLD = 0x480;

    private static final int[][][] DEBRIS_POSITIONS = {
            {{-8, -0x18}, {0x0B, -0x1C}, {-4, -0x0C}, {0x0C, -4},
             {-0x0C, 4}, {4, 0x0C}, {-0x0C, 0x1C}, {0x0C, 0x1C}},
            {{-4, -0x0C}, {0x0B, -0x0C}, {-4, -4}, {-0x0C, 0x0C}, {0x0C, 0x0C}},
            {{-4, -4}, {0x0C, -4}, {-0x0C, 4}, {0x0C, 4}},
            {{-8, -8}, {8, -8}, {-8, 0}, {8, 0}, {-8, 8}, {8, 8}},
    };

    private static final int[][][] DEBRIS_VELOCITIES = {
            {{-0x300, -0x300}, {-0x2C0, -0x280}, {-0x2C0, -0x280}, {-0x280, -0x200},
             {-0x280, -0x180}, {-0x240, -0x180}, {-0x240, -0x100}, {-0x200, -0x100}},
            {{-0x200, -0x200}, {0x200, -0x200}, {-0x100, -0x1E0},
             {-0x1B0, -0x1C0}, {0x1C0, -0x1C0}},
            {{-0x100, -0x200}, {0x100, -0x1E0}, {-0x1B0, -0x1C0}, {0x1C0, -0x1C0}},
            {{-0xB0, -0x1E0}, {0xB0, -0x1D0}, {-0x80, -0x200},
             {0x80, -0x1E0}, {-0xD8, -0x1C0}, {0xE0, -0x1C0}},
    };

    private enum ZoneVariant {
        AIZ1(Sonic3kObjectArtKeys.AIZ1_ROCK, 0, 1, 3),
        AIZ2(Sonic3kObjectArtKeys.AIZ2_ROCK, 0, 2, 3),
        LRZ1(Sonic3kObjectArtKeys.LRZ1_ROCK, 4, 2, 0),
        LRZ2(Sonic3kObjectArtKeys.LRZ2_ROCK, 0, 3, 0),
        UNKNOWN(null, 0, 0, 0);

        final String artKey;
        final int frameOffset;
        final int sheetPalette;
        final int debrisBaseFrame;

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
    private final ZoneVariant variant;
    private final int sizeIndex;
    private final int behaviorBits;
    private final boolean knucklesOnly;
    private final boolean knucklesOnlyStanding;
    private final int displayFrame;

    private boolean contactPushingActive;
    private int pushRateTimer;
    private int pushDistanceRemaining = PUSH_MAX_DISTANCE;

    private boolean playerStandingOnRock;
    private boolean playerPushingSide;

    private boolean savedPreContactRolling;
    private int savedPreContactXSpeed;
    private int savedPreContactYSpeed;

    private boolean breaking;

    public AizLrzRockObjectInstance(ObjectSpawn spawn) {
        super(spawn, "AIZLRZRock");
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.currentX = baseX;
        this.currentY = baseY;

        this.sizeIndex = (spawn.subtype() >> 4) & 0x07;
        int lowerNibble = spawn.subtype() & 0x0F;
        this.knucklesOnlyStanding = (lowerNibble == KNUCKLES_ONLY_STANDING_NIBBLE);
        this.behaviorBits = knucklesOnlyStanding ? 0 : lowerNibble;
        this.knucklesOnly = (spawn.subtype() & 0x80) != 0;

        this.variant = resolveVariant();
        this.displayFrame = sizeIndex + variant.frameOffset;
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        // Manual checkpoints drive the current-frame contact state from update().
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (breaking) {
            return;
        }

        SolidCheckpointBatch batch = checkpointAll();
        applyCheckpointContact(player, player != null ? batch.perPlayer().get(player) : null);
        for (PlayableEntity sidekick : services().sidekicks()) {
            if (breaking) {
                break;
            }
            if (sidekick instanceof AbstractPlayableSprite sidekickSprite) {
                applyCheckpointContact(sidekickSprite, batch.perPlayer().get(sidekick));
            }
        }
        if (breaking) {
            return;
        }

        if (knucklesOnlyStanding && playerStandingOnRock && player != null) {
            if (isKnuckles()) {
                player.setRolling(true);
                player.setYSpeed((short) -0x300);
                player.setAir(true);
                player.setOnObject(false);
                breakRock(player);
            }
            playerStandingOnRock = false;
            playerPushingSide = false;
            return;
        }

        if ((behaviorBits & BIT_BREAK_TOP) != 0 && playerStandingOnRock && player != null) {
            if (savedPreContactRolling) {
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

        if ((behaviorBits & BIT_BREAK_SIDE) != 0 && playerPushingSide && player != null) {
            if (canSideBreak(player)) {
                if (savedPreContactRolling) {
                    player.setRolling(true);
                }
                player.setXSpeed((short) savedPreContactXSpeed);
                int playerX = player.getCentreX();
                if (playerX < currentX) {
                    player.setCentreX((short) (playerX - 4));
                } else {
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

        if ((behaviorBits & BIT_PUSHABLE) != 0) {
            handlePush(player);
        }

        playerStandingOnRock = false;
        playerPushingSide = false;

        updateDynamicSpawn(currentX, currentY);
    }

    private void applyCheckpointContact(AbstractPlayableSprite player, PlayerSolidContactResult result) {
        if (player == null || result == null || breaking || result.kind() == ContactKind.NONE) {
            return;
        }

        savedPreContactRolling = result.preContact().rolling();
        savedPreContactXSpeed = result.preContact().xSpeed();
        savedPreContactYSpeed = result.preContact().ySpeed();

        if (result.standingNow()) {
            playerStandingOnRock = true;
        }
        if (result.pushingNow()) {
            playerPushingSide = true;
            if ((behaviorBits & BIT_PUSHABLE) != 0) {
                contactPushingActive = true;
            }
        }
        if (result.kind() == ContactKind.SIDE && knucklesOnly) {
            playerPushingSide = true;
        }

        if (!knucklesOnlyStanding
                && (behaviorBits & BIT_BREAK_BOTTOM) != 0
                && result.kind() == ContactKind.BOTTOM) {
            breakRock(player);
            player.setYSpeed((short) savedPreContactYSpeed);
        }
    }

    private boolean canSideBreak(AbstractPlayableSprite player) {
        if (knucklesOnly) {
            return isKnuckles();
        }
        if (isKnuckles()) {
            return true;
        }
        if (player.isSuperSonic()) {
            return true;
        }
        if (!savedPreContactRolling || Math.abs(savedPreContactXSpeed) < SIDE_BREAK_SPEED_THRESHOLD) {
            return false;
        }
        if (player.getShieldType() == ShieldType.FIRE) {
            return true;
        }
        return playerPushingSide;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        int idx = Math.clamp(sizeIndex, 0, SIZE_TABLE.length - 1);
        int halfWidth = SIZE_TABLE[idx][0];
        int halfHeight = SIZE_TABLE[idx][1];
        return new SolidObjectParams(halfWidth + 0x0B, halfHeight, halfHeight + 1);
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
        if (breaking || variant == ZoneVariant.UNKNOWN || variant.artKey == null) {
            return;
        }

        ObjectRenderManager renderManager = services().renderManager();
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

    private void breakRock(AbstractPlayableSprite player) {
        breaking = true;

        if (isOnScreen()) {
            try {
                services().playSfx(Sonic3kSfx.COLLAPSE.id);
            } catch (Exception e) {
                // Ignore audio failures.
            }
        }

        spawnDebrisFragments(player);
        setDestroyed(true);
    }

    private void spawnDebrisFragments(AbstractPlayableSprite player) {
        if (variant.artKey == null) {
            return;
        }

        int frameIdx = Math.clamp(sizeIndex, 0, DEBRIS_POSITIONS.length - 1);
        int[][] positions = DEBRIS_POSITIONS[frameIdx];
        int[][] velocities = DEBRIS_VELOCITIES[frameIdx];
        int fragmentCount = positions.length;
        int debrisStartFrame = variant.debrisBaseFrame;

        for (int i = 0; i < fragmentCount; i++) {
            int xPos = currentX + positions[i][0];
            int yPos = currentY + positions[i][1];
            int xVel = velocities[i][0];
            int yVel = velocities[i][1];
            int debrisFrame = debrisStartFrame + (i % 4);

            ObjectSpawn debrisSpawn = new ObjectSpawn(xPos, yPos, 0, 0, 0, false, 0);
            RockDebrisChild debris = new RockDebrisChild(
                    debrisSpawn, xVel, yVel, debrisFrame, variant.artKey);
            spawnDynamicObject(debris);
        }
    }

    private void handlePush(AbstractPlayableSprite player) {
        boolean wasPushing = contactPushingActive;
        contactPushingActive = false;

        if (!wasPushing || player == null) {
            return;
        }

        int playerX = player.getCentreX();
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
        currentX--;
        player.setCentreX((short) (playerX - 1));
    }

    private boolean isKnuckles() {
        return S3kRuntimeStates.resolvePlayerCharacter(
                services().zoneRuntimeRegistry(),
                services().configuration()) == PlayerCharacter.KNUCKLES;
    }

    private ZoneVariant resolveVariant() {
        try {
            int zone = services().romZoneId();
            int act = services().currentAct();
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

    private SolidCheckpointBatch checkpointAll() {
        return services().solidExecution().resolveSolidNowAll();
    }
}
