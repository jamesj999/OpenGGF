package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.ShieldType;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x0D - Breakable Wall (Sonic 3 & Knuckles).
 */
public class BreakableWallObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOG = Logger.getLogger(BreakableWallObjectInstance.class.getName());

    private static final int FRAGMENT_GRAVITY = 0x70;
    private static final int PRIORITY = 5;
    private static final int BREAK_SPEED_THRESHOLD = 0x480;
    private static final int GLIDE_ACTIVE = 1;
    private static final int GLIDE_FALLING = 2;
    private static final int KNUCKLES_FALL_FROM_GLIDE_ANIM = 0x21;

    private static final int[][] VEL_RIGHT_12 = {
            {0x400, -0x500}, {0x600, -0x600}, {0x600, -0x100}, {0x800, -0x200},
            {0x680, 0}, {0x880, 0}, {0x600, 0x100}, {0x800, 0x200},
            {0x400, 0x500}, {0x600, 0x600}, {0x300, 0x600}, {0x500, 0x700},
    };
    private static final int[][] VEL_LEFT_12 = {
            {-0x600, -0x600}, {-0x400, -0x500}, {-0x800, -0x200}, {-0x600, -0x100},
            {-0x880, 0}, {-0x680, 0}, {-0x800, 0x200}, {-0x600, 0x100},
            {-0x600, 0x600}, {-0x400, 0x500}, {-0x500, 0x700}, {-0x300, 0x600},
    };
    private static final int[][] VEL_RIGHT_8A = {
            {0x400, -0x500}, {0x600, -0x600}, {0x600, -0x100}, {0x800, -0x200},
            {0x600, 0x100}, {0x800, 0x200}, {0x400, 0x500}, {0x600, 0x600},
    };
    private static final int[][] VEL_LEFT_8A = {
            {-0x600, -0x600}, {-0x400, -0x500}, {-0x800, -0x200}, {-0x600, -0x100},
            {-0x800, 0x200}, {-0x600, 0x100}, {-0x600, 0x600}, {-0x400, 0x500},
    };
    private static final int[][] VEL_RIGHT_20 = {
            {0x400, -0x500}, {0x500, -0x580}, {0x600, -0x600}, {0x700, -0x680},
            {0x600, -0x100}, {0x700, -0x180}, {0x800, -0x200}, {0x900, -0x280},
            {0x680, 0}, {0x780, 0}, {0x880, 0}, {0x980, 0},
            {0x600, 0x100}, {0x700, 0x180}, {0x800, 0x200}, {0x900, 0x280},
            {0x400, 0x500}, {0x500, 0x580}, {0x600, 0x600}, {0x700, 0x680},
    };
    private static final int[][] VEL_LEFT_20 = {
            {-0x700, -0x680}, {-0x600, -0x600}, {-0x500, -0x580}, {-0x400, -0x500},
            {-0x900, -0x280}, {-0x800, -0x200}, {-0x700, -0x180}, {-0x600, -0x100},
            {-0x980, 0}, {-0x880, 0}, {-0x780, 0}, {-0x680, 0},
            {-0x900, 0x280}, {-0x800, 0x200}, {-0x700, 0x180}, {-0x600, 0x100},
            {-0x700, 0x680}, {-0x600, 0x600}, {-0x500, 0x580}, {-0x400, 0x500},
    };
    private static final int[][] VEL_RIGHT_8B = {
            {0x400, -0x500}, {0x600, -0x600}, {0x600, -0x100}, {0x800, -0x200},
            {0x600, 0x100}, {0x800, 0x200}, {0x400, 0x500}, {0x600, 0x600},
    };
    private static final int[][] VEL_LEFT_8B = {
            {-0x600, -0x600}, {-0x400, -0x500}, {-0x800, -0x200}, {-0x600, -0x100},
            {-0x800, 0x200}, {-0x600, 0x100}, {-0x600, 0x600}, {-0x400, 0x500},
    };

    private enum BreakMode {
        STANDARD,
        KNUCKLES_ONLY,
        MGZ_SPIN_BREAK
    }

    private record ZoneConfig(
            String artKey,
            int halfWidth,
            int halfHeight,
            int[][] velRight,
            int[][] velLeft,
            BreakMode breakMode,
            int sheetFrameOffset
    ) {
        ZoneConfig(String artKey, int halfWidth, int halfHeight,
                   int[][] velRight, int[][] velLeft, BreakMode breakMode) {
            this(artKey, halfWidth, halfHeight, velRight, velLeft, breakMode, 0);
        }
    }

    private final ZoneConfig config;
    private final boolean triggerControlled;
    private final int x;
    private final int y;
    private int mappingFrame;
    private boolean broken;

    private short savedPreContactXSpeed;
    private short savedPreContactYSpeed;
    private boolean savedPreContactRolling;

    public BreakableWallObjectInstance(ObjectSpawn spawn) {
        super(spawn, "BreakableWall");
        int subtype = spawn.subtype() & 0xFF;
        this.triggerControlled = (subtype & 0x80) != 0;
        this.mappingFrame = subtype & 0x0F;
        this.x = spawn.x();
        this.y = spawn.y();
        this.config = resolveConfig(subtype, mappingFrame);
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(config.halfWidth + 0x0B, config.halfHeight, config.halfHeight + 1);
    }

    @Override
    public boolean isTopSolidOnly() {
        return false;
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        return !broken;
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
        if (broken) {
            return;
        }

        SolidCheckpointBatch batch = checkpointAll();
        applyCheckpointContact(player, player != null ? batch.perPlayer().get(player) : null);
        for (PlayableEntity sidekick : services().sidekicks()) {
            if (broken) {
                break;
            }
            if (sidekick instanceof AbstractPlayableSprite sidekickSprite) {
                applyCheckpointContact(sidekickSprite, batch.perPlayer().get(sidekick));
            }
        }
        if (broken) {
            return;
        }

        if (triggerControlled && isTriggerActive()) {
            setDestroyed(true);
        }
    }

    private void applyCheckpointContact(AbstractPlayableSprite player, PlayerSolidContactResult result) {
        if (player == null || result == null || broken || result.kind() == com.openggf.game.solid.ContactKind.NONE) {
            return;
        }

        savedPreContactXSpeed = result.preContact().xSpeed();
        savedPreContactYSpeed = result.preContact().ySpeed();
        savedPreContactRolling = result.preContact().rolling();

        if (!result.pushingNow()) {
            return;
        }

        boolean shouldBreak = switch (config.breakMode) {
            case STANDARD -> checkStandardBreak(player);
            case KNUCKLES_ONLY -> isKnuckles();
            case MGZ_SPIN_BREAK -> checkMgzSpinBreak(player);
        };

        if (shouldBreak) {
            performBreak(player);
        }
    }

    private boolean checkStandardBreak(AbstractPlayableSprite player) {
        if (player.isSuperSonic()) {
            return true;
        }
        if (isKnuckles()) {
            return true;
        }
        if (!savedPreContactRolling) {
            return false;
        }
        if (Math.abs(savedPreContactXSpeed) < BREAK_SPEED_THRESHOLD) {
            return false;
        }
        return true;
    }

    private boolean checkMgzSpinBreak(AbstractPlayableSprite player) {
        return isKnuckles();
    }

    private void performBreak(AbstractPlayableSprite player) {
        if (broken) {
            return;
        }
        broken = true;

        player.setXSpeed(savedPreContactXSpeed);
        player.setGSpeed(savedPreContactXSpeed);

        int[][] velTable;
        if (config.breakMode == BreakMode.MGZ_SPIN_BREAK) {
            boolean playerIsRight = player.getCentreX() > x;
            velTable = playerIsRight ? config.velRight : config.velLeft;
            if (!playerIsRight) {
                player.setX((short) (player.getX() - 8));
            }
        } else {
            player.setX((short) (player.getX() + 4));
            boolean playerIsRight = player.getCentreX() > x;
            if (playerIsRight) {
                velTable = config.velRight;
            } else {
                player.setX((short) (player.getX() - 8));
                velTable = config.velLeft;
            }
        }

        player.setPushing(false);

        if (config.breakMode == BreakMode.KNUCKLES_ONLY
                && isKnuckles() && player.getDoubleJumpFlag() == GLIDE_ACTIVE) {
            player.setDoubleJumpFlag(GLIDE_FALLING);
            player.setAnimationId(KNUCKLES_FALL_FROM_GLIDE_ANIM);
            if (player.getXSpeed() >= 0) {
                player.setDirection(Direction.RIGHT);
            } else {
                player.setDirection(Direction.LEFT);
            }
        }

        if (isOnScreen()) {
            try {
                services().playSfx(Sonic3kSfx.COLLAPSE.id);
            } catch (Exception e) {
                // Ignore audio failures.
            }
        }

        int brokenFrame = (mappingFrame + 1) - config.sheetFrameOffset;
        spawnFragments(brokenFrame, velTable);
        markRemembered();
    }

    private boolean isTriggerActive() {
        return Sonic3kLevelTriggerManager.testAny(0);
    }

    private void spawnFragments(int brokenFrameIndex, int[][] velTable) {
        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            return;
        }

        ObjectSpriteSheet sheet = renderManager.getSheet(config.artKey);
        if (sheet == null || brokenFrameIndex >= sheet.getFrameCount()) {
            LOG.fine(() -> "BreakableWall: broken frame " + brokenFrameIndex
                    + " not found in sheet " + config.artKey);
            return;
        }

        SpriteMappingFrame brokenFrame = sheet.getFrame(brokenFrameIndex);
        int pieceCount = brokenFrame.pieces().size();
        int maxFragments = Math.min(pieceCount, velTable.length);

        for (int i = 0; i < maxFragments; i++) {
            int xVel = velTable[i][0];
            int yVel = velTable[i][1];
            BreakableWallFragment fragment = new BreakableWallFragment(
                    x, y, brokenFrameIndex, i, xVel, yVel, config.artKey);
            spawnDynamicObject(fragment);
        }
    }

    private void markRemembered() {
        try {
            var om = services().objectManager();
            if (om != null) {
                om.markRemembered(spawn);
            }
        } catch (Exception e) {
            // Safe fallback.
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (broken) {
            return;
        }

        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(config.artKey);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame - config.sheetFrameOffset, x, y, false, false);
        }
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    private boolean isKnuckles() {
        return S3kRuntimeStates.resolvePlayerCharacter(
                services().zoneRuntimeRegistry(),
                services().configuration()) == PlayerCharacter.KNUCKLES;
    }

    private SolidCheckpointBatch checkpointAll() {
        return services().solidExecution().resolveSolidNowAll();
    }

    private ZoneConfig resolveConfig(int subtype, int frame) {
        try {
            int zone = services().romZoneId();
            return resolveForZone(zone, subtype, frame);
        } catch (Exception e) {
            LOG.fine("Could not resolve zone config: " + e.getMessage());
        }
        return new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_AIZ,
                0x10, 0x28, VEL_RIGHT_12, VEL_LEFT_12, BreakMode.STANDARD);
    }

    private static ZoneConfig resolveForZone(int zone, int subtype, int frame) {
        return switch (zone) {
            case Sonic3kZoneIds.ZONE_AIZ ->
                    new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_AIZ,
                            0x10, 0x28, VEL_RIGHT_12, VEL_LEFT_12, BreakMode.STANDARD);
            case Sonic3kZoneIds.ZONE_HCZ -> {
                if (frame == 2) {
                    yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_HCZ_KNUX,
                            0x18, 0x20, VEL_RIGHT_8A, VEL_LEFT_8A, BreakMode.KNUCKLES_ONLY, 2);
                }
                yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_HCZ,
                        0x10, 0x20, VEL_RIGHT_8A, VEL_LEFT_8A, BreakMode.STANDARD);
            }
            case Sonic3kZoneIds.ZONE_MGZ -> {
                if ((subtype & 0x10) != 0) {
                    yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_MGZ,
                            0x20, 0x28, VEL_LEFT_20, VEL_RIGHT_20, BreakMode.KNUCKLES_ONLY);
                }
                if (frame == 2) {
                    yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_MGZ,
                            0x20, 0x28, VEL_LEFT_20, VEL_RIGHT_20, BreakMode.MGZ_SPIN_BREAK);
                }
                yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_MGZ,
                        0x20, 0x28, VEL_LEFT_20, VEL_RIGHT_20, BreakMode.STANDARD);
            }
            case Sonic3kZoneIds.ZONE_CNZ -> {
                if (frame == 2) {
                    yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_CNZ,
                            0x10, 0x20, VEL_RIGHT_8B, VEL_LEFT_8B, BreakMode.KNUCKLES_ONLY);
                }
                yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_CNZ,
                        0x10, 0x20, VEL_RIGHT_8B, VEL_LEFT_8B, BreakMode.STANDARD);
            }
            case Sonic3kZoneIds.ZONE_LBZ ->
                    new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_LBZ,
                            0x10, 0x20, VEL_RIGHT_8A, VEL_LEFT_8A, BreakMode.KNUCKLES_ONLY);
            case Sonic3kZoneIds.ZONE_MHZ ->
                    new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_MHZ,
                            0x10, 0x20, VEL_RIGHT_8B, VEL_LEFT_8B, BreakMode.KNUCKLES_ONLY);
            case Sonic3kZoneIds.ZONE_SOZ -> {
                if (frame == 4) {
                    yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_SOZ,
                            0x10, 0x30, VEL_RIGHT_12, VEL_LEFT_12, BreakMode.KNUCKLES_ONLY);
                }
                yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_SOZ,
                        0x10, 0x20, VEL_RIGHT_8B, VEL_LEFT_8B, BreakMode.KNUCKLES_ONLY);
            }
            case Sonic3kZoneIds.ZONE_LRZ ->
                    new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_LRZ,
                            0x10, 0x20, VEL_RIGHT_8B, VEL_LEFT_8B, BreakMode.STANDARD);
            default ->
                    new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_AIZ,
                            0x10, 0x28, VEL_RIGHT_12, VEL_LEFT_12, BreakMode.STANDARD);
        };
    }

    public static class BreakableWallFragment extends AbstractObjectInstance {

        private int currentX;
        private int currentY;
        private final int fragmentFrameIndex;
        private final int pieceIndex;
        private final String artKey;
        private final SubpixelMotion.State motionState;

        public BreakableWallFragment(int parentX, int parentY,
                                     int fragmentFrameIndex, int pieceIndex,
                                     int xVel, int yVel, String artKey) {
            super(new ObjectSpawn(parentX, parentY, Sonic3kObjectIds.BREAKABLE_WALL,
                    0, 0, false, 0), "BreakableWallFragment");
            this.currentX = parentX;
            this.currentY = parentY;
            this.fragmentFrameIndex = fragmentFrameIndex;
            this.pieceIndex = pieceIndex;
            this.artKey = artKey;
            this.motionState = new SubpixelMotion.State(
                    currentX, currentY, 0, 0, xVel, yVel);
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
        public void update(int frameCounter, PlayableEntity playerEntity) {
            motionState.x = currentX;
            motionState.y = currentY;
            SubpixelMotion.moveSprite(motionState, FRAGMENT_GRAVITY);
            currentX = motionState.x;
            currentY = motionState.y;

            if (!isOnScreen(128)) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = getRenderManager();
            if (renderManager == null) {
                return;
            }

            PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFramePieceByIndex(fragmentFrameIndex, pieceIndex,
                        currentX, currentY, false, false);
            }
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(1);
        }

        @Override
        public boolean isPersistent() {
            return !isDestroyed();
        }
    }
}
