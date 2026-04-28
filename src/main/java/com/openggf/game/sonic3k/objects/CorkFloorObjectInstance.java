package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x2A - Cork Floor (Sonic 3 & Knuckles).
 */
public class CorkFloorObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOG = Logger.getLogger(CorkFloorObjectInstance.class.getName());

    private static final int FRAGMENT_GRAVITY = 0x18;
    private static final int PRIORITY = 5;
    private static final int ROLL_BREAK_LAUNCH_YVEL = -0x300;

    private static final int[][] VEL_TABLE_SMALL = {
            {-0x200, -0x200}, {0x200, -0x200},
            {-0x100, -0x100}, {0x100, -0x100},
    };

    private static final int[][] VEL_TABLE_MEDIUM = {
            {-0x100, -0x200}, {0x100, -0x200},
            {-0x0E0, -0x1C0}, {0x0E0, -0x1C0},
            {-0x0C0, -0x180}, {0x0C0, -0x180},
            {-0x0A0, -0x140}, {0x0A0, -0x140},
            {-0x080, -0x100}, {0x080, -0x100},
            {-0x060, -0x0C0}, {0x060, -0x0C0},
    };

    private static final int[][] VEL_TABLE_LARGE = {
            {-0x400, -0x400}, {-0x200, -0x400}, {0x200, -0x400}, {0x400, -0x400},
            {-0x3C0, -0x3C0}, {-0x1C0, -0x3C0}, {0x1C0, -0x3C0}, {0x3C0, -0x3C0},
            {-0x380, -0x380}, {-0x180, -0x380}, {0x180, -0x380}, {0x380, -0x380},
            {-0x340, -0x340}, {-0x140, -0x340}, {0x140, -0x340}, {0x340, -0x340},
    };

    private record ZoneConfig(
            String artKey,
            int halfWidth,
            int halfHeight,
            int[][] velTable,
            boolean iczPlaneMode
    ) {}

    private static final ZoneConfig AIZ1_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_AIZ1, 0x10, 0x28, VEL_TABLE_MEDIUM, false);
    private static final ZoneConfig AIZ2_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_AIZ2, 0x10, 0x2C, VEL_TABLE_MEDIUM, false);
    private static final ZoneConfig CNZ_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_CNZ, 0x20, 0x20, VEL_TABLE_LARGE, false);
    private static final ZoneConfig FBZ_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_FBZ, 0x10, 0x10, VEL_TABLE_SMALL, false);
    private static final ZoneConfig ICZ_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_ICZ, 0x10, 0x24, VEL_TABLE_MEDIUM, true);
    private static final ZoneConfig ICZ_SMALL_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_ICZ, 0x10, 0x10, VEL_TABLE_SMALL, false);
    private static final ZoneConfig LBZ_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_LBZ, 0x20, 0x20, VEL_TABLE_LARGE, false);

    private enum Mode {
        BREAK_FROM_BELOW,
        ROLL_TO_BREAK,
        ICZ_PLANE_SWITCH
    }

    private final ZoneConfig config;
    private final Mode mode;
    private final int subtype;
    private final boolean hFlip;
    private int mappingFrame;
    private final int x;
    private final int y;
    private final int[][] effectiveVelTable;

    private boolean broken;
    private boolean playerStanding;
    private int savedPreContactYSpeed;
    private boolean savedPreContactRolling;
    private AbstractPlayableSprite rollingBreakPlayer;

    public CorkFloorObjectInstance(ObjectSpawn spawn) {
        super(spawn, "CorkFloor");
        this.x = spawn.x();
        this.y = spawn.y();
        this.subtype = spawn.subtype() & 0xFF;
        this.hFlip = (spawn.renderFlags() & 0x01) != 0;
        this.config = resolveConfig(subtype);

        if (subtype == 0) {
            this.mode = Mode.BREAK_FROM_BELOW;
        } else if (config.iczPlaneMode && (subtype & 0x10) == 0) {
            this.mode = Mode.ICZ_PLANE_SWITCH;
        } else {
            this.mode = Mode.ROLL_TO_BREAK;
        }

        this.mappingFrame = config.iczPlaneMode ? (subtype & 0x0F) * 2 : 0;
        this.effectiveVelTable = config.iczPlaneMode && (subtype & 0x10) != 0
                ? VEL_TABLE_SMALL
                : config.velTable;
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

        playerStanding = false;
        savedPreContactRolling = false;
        rollingBreakPlayer = null;

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

        if (mode == Mode.BREAK_FROM_BELOW) {
            playerStanding = false;
            return;
        }

        if (rollingBreakPlayer != null) {
            if (mode == Mode.ICZ_PLANE_SWITCH) {
                applyPlaneSwitch(rollingBreakPlayer);
            }

            rollingBreakPlayer.setRolling(true);
            rollingBreakPlayer.setYSpeed((short) ROLL_BREAK_LAUNCH_YVEL);
            rollingBreakPlayer.setAir(true);
            rollingBreakPlayer.setOnObject(false);

            performBreak(rollingBreakPlayer);
            playerStanding = false;
            return;
        }

        playerStanding = false;
    }

    private void applyCheckpointContact(AbstractPlayableSprite player, PlayerSolidContactResult result) {
        if (player == null || result == null || broken || result.kind() == ContactKind.NONE) {
            return;
        }

        savedPreContactYSpeed = result.preContact().ySpeed();
        // ROM Obj_CorkFloor caches Player_1+anim / Player_2+anim before
        // SolidObjectFull (sonic3k.asm:58493-58505) and breaks only when that
        // cached byte is anim=$02 (sonic3k.asm:58515-58528, 58532-58540).
        // Keep the per-frame decision per rider: the ROM stores P1/P2 cached
        // animation bytes separately, so a later non-rolling sidekick contact
        // must not erase the main player's roll-break checkpoint.
        boolean preContactRollAnimation = result.preContact().animationId() == 2;
        savedPreContactRolling |= preContactRollAnimation;

        if (result.standingNow()) {
            playerStanding = true;
            if (preContactRollAnimation && rollingBreakPlayer == null) {
                rollingBreakPlayer = player;
            }
        }

        if (mode == Mode.BREAK_FROM_BELOW && result.kind() == ContactKind.BOTTOM) {
            player.setYSpeed((short) savedPreContactYSpeed);
            player.setAir(true);
            player.setOnObject(false);
            performBreak(player);
        }
    }

    private void applyPlaneSwitch(AbstractPlayableSprite player) {
        if ((subtype & 0x80) != 0) {
            player.setTopSolidBit((byte) 0x0E);
            player.setLrbSolidBit((byte) 0x0F);
        } else {
            player.setTopSolidBit((byte) 0x0C);
            player.setLrbSolidBit((byte) 0x0D);
        }
    }

    private void performBreak(AbstractPlayableSprite player) {
        if (broken) {
            return;
        }
        broken = true;

        int brokenFrame = mappingFrame + 1;

        if (isOnScreen()) {
            try {
                services().playSfx(Sonic3kSfx.COLLAPSE.id);
            } catch (Exception e) {
                // Ignore audio failures.
            }
        }

        spawnFragments(brokenFrame);
        markRemembered();

        try {
            ObjectManager om = getObjectManager();
            if (om != null) {
                om.clearRidingObject(null);
            }
        } catch (Exception e) {
            // Safe fallback.
        }
    }

    private void spawnFragments(int brokenFrameIndex) {
        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            return;
        }

        com.openggf.level.objects.ObjectSpriteSheet sheet = renderManager.getSheet(config.artKey);
        if (sheet == null || brokenFrameIndex >= sheet.getFrameCount()) {
            LOG.fine(() -> "CorkFloor: broken frame " + brokenFrameIndex
                    + " not found in sheet " + config.artKey);
            return;
        }

        SpriteMappingFrame brokenFrame = sheet.getFrame(brokenFrameIndex);
        int pieceCount = brokenFrame.pieces().size();
        int maxFragments = Math.min(pieceCount, effectiveVelTable.length);

        for (int i = 0; i < maxFragments; i++) {
            int xVel = effectiveVelTable[i][0];
            int yVel = effectiveVelTable[i][1];
            CorkFloorFragment fragment = new CorkFloorFragment(
                    x, y, brokenFrameIndex, i, xVel, yVel, config.artKey, hFlip);
            spawnDynamicObject(fragment);
        }
    }

    private void markRemembered() {
        try {
            ObjectManager om = getObjectManager();
            if (om != null) {
                om.markRemembered(spawn);
            }
        } catch (Exception e) {
            // Safe fallback for tests.
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
            renderer.drawFrameIndex(mappingFrame, x, y, hFlip, false);
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

    private ObjectManager getObjectManager() {
        try {
            return services().objectManager();
        } catch (Exception e) {
            return null;
        }
    }

    protected SolidCheckpointBatch checkpointAll() {
        return services().solidExecution().resolveSolidNowAll();
    }

    private ZoneConfig resolveConfig(int subtype) {
        try {
            int zone = services().romZoneId();
            int act = services().currentAct();
            return switch (zone) {
                case Sonic3kZoneIds.ZONE_AIZ -> act == 0 ? AIZ1_CONFIG : AIZ2_CONFIG;
                case Sonic3kZoneIds.ZONE_CNZ -> CNZ_CONFIG;
                case Sonic3kZoneIds.ZONE_FBZ -> FBZ_CONFIG;
                case Sonic3kZoneIds.ZONE_ICZ ->
                        (subtype & 0x10) != 0 ? ICZ_SMALL_CONFIG : ICZ_CONFIG;
                case Sonic3kZoneIds.ZONE_LBZ -> LBZ_CONFIG;
                default -> {
                    LOG.warning("CorkFloor: unknown zone 0x" + Integer.toHexString(zone)
                            + ", defaulting to AIZ1 config");
                    yield AIZ1_CONFIG;
                }
            };
        } catch (Exception e) {
            LOG.fine("Could not resolve zone config: " + e.getMessage());
        }
        return AIZ1_CONFIG;
    }

    public static class CorkFloorFragment extends AbstractObjectInstance {

        private int currentX;
        private int currentY;
        private final int fragmentFrameIndex;
        private final int pieceIndex;
        private final String artKey;
        private final boolean hFlip;
        private final SubpixelMotion.State motionState;

        public CorkFloorFragment(int parentX, int parentY,
                                 int fragmentFrameIndex, int pieceIndex,
                                 int xVel, int yVel,
                                 String artKey, boolean hFlip) {
            super(new ObjectSpawn(parentX, parentY, Sonic3kObjectIds.CORK_FLOOR,
                    0, hFlip ? 1 : 0, false, 0), "CorkFloorFragment");
            this.currentX = parentX;
            this.currentY = parentY;
            this.fragmentFrameIndex = fragmentFrameIndex;
            this.pieceIndex = pieceIndex;
            this.artKey = artKey;
            this.hFlip = hFlip;
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
                        currentX, currentY, hFlip, false);
            }
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY);
        }

        @Override
        public boolean isPersistent() {
            return !isDestroyed();
        }
    }
}
