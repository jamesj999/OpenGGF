package com.openggf.game.sonic3k.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
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
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Object 0x2C - AIZ Collapsing Log Bridge (Sonic 3 & Knuckles).
 */
public class AizCollapsingLogBridgeObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final int STATE_IDLE = 0;
    private static final int STATE_COLLAPSING = 1;
    private static final int STATE_FINAL = 2;

    private static final int SEGMENT_COUNT = 6;
    private static final int NORMAL_HALF_WIDTH = 0x5A;
    private static final int NORMAL_SEGMENT_SPACING = 0x1E;
    private static final int NORMAL_FIRST_OFFSET = 0x4B;
    private static final int FIRE_HALF_WIDTH = 0x60;
    private static final int FIRE_SEGMENT_SPACING = 0x20;
    private static final int FIRE_FIRST_OFFSET = 0x50;
    private static final int HEIGHT_PIXELS = 8;
    private static final int PRIORITY = 4;
    private static final int COLLAPSE_DELAY_INCREMENT = 8;

    private static volatile boolean drawBridgeBurnActive;

    public static void setDrawBridgeBurnActive(boolean active) {
        drawBridgeBurnActive = active;
    }

    private final boolean isFireBridge;
    private final int halfWidth;
    private final int subtypeBase;
    private final int totalTimer;
    private final boolean hFlip;
    private final String artKey;

    private final int x;
    private final int y;
    private int state = STATE_IDLE;
    private int collapseTimer;
    private boolean segmentsSpawned;
    private boolean collapseArmedByStanding;
    private final List<PlayableEntity> standingPlayers = new ArrayList<>(2);
    private final Set<PlayableEntity> ejectedPlayers = new HashSet<>(2);

    private final int[] segmentX = new int[SEGMENT_COUNT];
    private final int[] segmentFrame = new int[SEGMENT_COUNT];

    public AizCollapsingLogBridgeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "AIZCollapsingLogBridge");
        this.x = spawn.x();
        this.y = spawn.y();
        this.hFlip = (spawn.renderFlags() & 0x01) != 0;

        int subtype = spawn.subtype() & 0xFF;
        this.isFireBridge = (subtype & 0x80) != 0;
        this.subtypeBase = isFireBridge ? (subtype & 0x7F) : subtype;
        this.totalTimer = subtypeBase + 0x30;
        this.collapseTimer = totalTimer;

        if (isFireBridge) {
            this.halfWidth = FIRE_HALF_WIDTH;
            this.artKey = Sonic3kObjectArtKeys.AIZ_DRAW_BRIDGE_FIRE;
            initSegments(FIRE_FIRST_OFFSET, FIRE_SEGMENT_SPACING);
        } else {
            this.halfWidth = NORMAL_HALF_WIDTH;
            this.artKey = Sonic3kObjectArtKeys.AIZ_COLLAPSING_LOG_BRIDGE;
            initSegments(NORMAL_FIRST_OFFSET, NORMAL_SEGMENT_SPACING);
        }
    }

    private void initSegments(int firstOffset, int spacing) {
        int startX = x - firstOffset;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segmentX[i] = startX + i * spacing;
            segmentFrame[i] = 0;
        }
        segmentFrame[0] = 1;
        segmentFrame[SEGMENT_COUNT - 1] = 2;
    }

    @Override
    public boolean isPersistent() {
        return !isDestroyed();
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // loc_2AE98/loc_2AF06 pass height_pixels(a0) directly as d3 to
        // SolidObjectTop; no bridge-local landing offset is applied afterward.
        return new SolidObjectParams(halfWidth, HEIGHT_PIXELS, HEIGHT_PIXELS);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean gatesNewTopSolidLandingWithPreviousPosition() {
        return true;
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        if (state == STATE_FINAL) {
            return false;
        }
        if ((state == STATE_COLLAPSING || collapseArmedByStanding || segmentsSpawned)
                && (services().objectManager() == null
                || !services().objectManager().isRidingObject(player, this))) {
            // ROM loc_2AF70 no longer calls SolidObjectTop. It only tests
            // the standing bits that were already set and ejects those
            // players as their segment drops, so new landings cannot attach.
            return false;
        }
        return !ejectedPlayers.contains(player);
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        // Manual checkpoints drive the current-frame standing state from update().
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        boolean collapseStartedThisFrame = false;
        if (state == STATE_IDLE && !isFireBridge && collapseArmedByStanding) {
            startCollapse();
            collapseStartedThisFrame = true;
        }

        if (state == STATE_IDLE || collapseStartedThisFrame) {
            SolidCheckpointBatch batch = checkpointAll();
            recordStandingPlayer(playerEntity, batch.perPlayer().get(playerEntity));
            for (PlayableEntity sidekick : services().sidekicks()) {
                recordStandingPlayer(sidekick, batch.perPlayer().get(sidekick));
            }
        }

        switch (state) {
            case STATE_IDLE -> {
                if (isFireBridge && drawBridgeBurnActive) {
                    startCollapse();
                }
            }
            case STATE_COLLAPSING -> {
                if (collapseStartedThisFrame) {
                    break;
                }
                collapseTimer--;
                boolean timerExpired = collapseTimer <= 0;
                if (timerExpired) {
                    state = STATE_FINAL;
                }
                for (int i = standingPlayers.size() - 1; i >= 0; i--) {
                    checkPlayerKnockoff(standingPlayers.get(i));
                }
            }
            case STATE_FINAL -> {
                for (int i = standingPlayers.size() - 1; i >= 0; i--) {
                    knockOff(standingPlayers.get(i));
                }
                setDestroyed(true);
            }
        }
        deleteSpriteIfNotInRange();
    }

    private void recordStandingPlayer(PlayableEntity player, PlayerSolidContactResult result) {
        if (player == null || result == null || !result.standingNow()) {
            return;
        }
        if (!standingPlayers.contains(player)) {
            standingPlayers.add(player);
        }
        if (state == STATE_IDLE && !isFireBridge) {
            // loc_2AE70 tests the parent's standing bits before the current
            // SolidObjectTop call, so a new rider collapses the bridge next frame.
            collapseArmedByStanding = true;
        }
    }

    private void startCollapse() {
        if (segmentsSpawned) {
            return;
        }
        segmentsSpawned = true;
        collapseArmedByStanding = false;
        state = STATE_COLLAPSING;
        collapseTimer = totalTimer;
        if (isFireBridge) {
            drawBridgeBurnActive = false;
        }

        var objManager = services().objectManager();
        if (objManager != null) {
            objManager.markRemembered(spawn);
        }

        int delay = subtypeBase;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            CollapsingLogSegment segment = new CollapsingLogSegment(
                    segmentX[i], y, segmentFrame[i], delay, artKey, isFireBridge);
            spawnDynamicObject(segment);
            delay += COLLAPSE_DELAY_INCREMENT;
        }

        services().playSfx(Sonic3kSfx.COLLAPSE.id);
    }

    private void deleteSpriteIfNotInRange() {
        if (isDestroyed()) {
            return;
        }
        int currentCameraX = services().camera().getX() & 0xFFFF;
        int cameraXPosCoarseBack = (currentCameraX - 0x80) & 0xFF80;
        int objectXCoarse = x & 0xFF80;
        int diff = (objectXCoarse - cameraXPosCoarseBack) & 0xFFFF;
        if (diff > 0x280) {
            // loc_2AE98/loc_2AF06/loc_2AF70 all tail-call
            // Delete_Sprite_If_Not_In_Range, which clears the respawn bit
            // before deleting so the layout entry can reload on camera return.
            setDestroyedByOffscreen();
        }
    }

    private void checkPlayerKnockoff(PlayableEntity player) {
        if (player.getAir()) {
            knockOff(player);
            return;
        }

        int fullWidth = halfWidth * 2;
        int relativeX = player.getCentreX() - x + halfWidth;
        if (relativeX < 0 || relativeX >= fullWidth) {
            knockOff(player);
            return;
        }

        if (hFlip) {
            relativeX = fullWidth - relativeX;
        }

        int segmentIndex = relativeX >> 5;
        int segmentOffset = segmentIndex << 3;
        int threshold = 0x30 - segmentOffset;

        if (collapseTimer <= threshold) {
            knockOff(player);
        }
    }

    private void knockOff(PlayableEntity player) {
        player.setOnObject(false);
        player.setPushing(false);
        player.setAir(true);
        standingPlayers.remove(player);
        ejectedPlayers.add(player);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (segmentsSpawned) {
            return;
        }

        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        for (int i = 0; i < SEGMENT_COUNT; i++) {
            renderer.drawFrameIndex(segmentFrame[i], segmentX[i], y, hFlip, false);
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

    @Override
    public boolean isHighPriority() {
        return isFireBridge;
    }

    @Override
    public String traceDebugDetails() {
        return String.format("state=%d armed=%s spawned=%s timer=%02X riders=%d",
                state, collapseArmedByStanding, segmentsSpawned,
                collapseTimer & 0xFF, standingPlayers.size());
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        float r = state == STATE_COLLAPSING ? 1.0f : 0.0f;
        float g = state == STATE_COLLAPSING ? 1.0f : 0.8f;
        float b = state == STATE_COLLAPSING ? 0.0f : 1.0f;
        int left = x - halfWidth;
        int right = x + halfWidth;
        int top = y - HEIGHT_PIXELS;
        int bottom = y + HEIGHT_PIXELS;
        ctx.drawLine(left, top, right, top, r, g, b);
        ctx.drawLine(right, top, right, bottom, r, g, b);
        ctx.drawLine(right, bottom, left, bottom, r, g, b);
        ctx.drawLine(left, bottom, left, top, r, g, b);
    }

    protected SolidCheckpointBatch checkpointAll() {
        return services().solidExecution().resolveSolidNowAll();
    }

    public static class CollapsingLogSegment extends AbstractObjectInstance {

        private static final int GRAVITY = 0x38;
        private static final int OFF_SCREEN_MARGIN = 128;
        private static final int FIRE_ANIM_FRAME_DELAY = 3;
        private static final int FIRE_ANIM_FIRST_FRAME = 3;
        private static final int FIRE_ANIM_LAST_FRAME = 7;

        private final String artKey;
        private final boolean isFireVariant;
        private final int fixedX;
        private int mappingFrame;
        private int delayTimer;
        private final SubpixelMotion.State motion;
        private int animFrameTimer = FIRE_ANIM_FRAME_DELAY;

        public CollapsingLogSegment(int x, int y, int frame, int delay,
                                     String artKey, boolean isFireVariant) {
            super(buildSpawnAt(x, y, Sonic3kObjectIds.AIZ_COLLAPSING_LOG_BRIDGE),
                    "LogSegment");
            this.fixedX = x;
            this.motion = new SubpixelMotion.State(0, y, 0, 0, 0, 0);
            this.mappingFrame = frame;
            this.delayTimer = delay;
            this.artKey = artKey;
            this.isFireVariant = isFireVariant;
        }

        private static ObjectSpawn buildSpawnAt(int x, int y, int objId) {
            return new ObjectSpawn(x, y, objId, 0, 0, false, 0);
        }

        @Override
        public int getX() {
            return fixedX;
        }

        @Override
        public int getY() {
            return motion.y;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (isDestroyed()) {
                return;
            }

            if (delayTimer > 0) {
                delayTimer--;
                if (isFireVariant && delayTimer == 0) {
                    mappingFrame = FIRE_ANIM_FIRST_FRAME;
                }
                return;
            }

            if (isFireVariant) {
                animFrameTimer--;
                if (animFrameTimer < 0) {
                    animFrameTimer = FIRE_ANIM_FRAME_DELAY;
                    mappingFrame++;
                    if (mappingFrame > FIRE_ANIM_LAST_FRAME) {
                        mappingFrame = FIRE_ANIM_FIRST_FRAME;
                    }
                }
            }

            SubpixelMotion.objectFall(motion, GRAVITY);

            if (!isOnScreen(OFF_SCREEN_MARGIN)) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(artKey);
            if (renderer == null) {
                return;
            }
            renderer.drawFrameIndex(mappingFrame, fixedX, motion.y, false, false);
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY);
        }

        @Override
        public boolean isHighPriority() {
            return isFireVariant;
        }

        @Override
        public boolean isPersistent() {
            return !isDestroyed();
        }
    }
}
