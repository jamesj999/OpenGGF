package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;

/**
 * AIZ draw bridge used by the AIZ2 boss-end cutscene.
 *
 * <p>ROM reference: Obj_AIZDrawBridge. The ROM object uses child multisprites;
 * this version keeps the same sequence timing and renders the repeated segments
 * directly from the parent for simplicity.
 */
public class AizDrawBridgeObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final int PRIORITY = 5;
    private static final int SEGMENT_COUNT = 14;
    private static final int SEGMENT_SPACING = 16;
    private static final int HALF_WIDTH = 0x6B;
    private static final int HEIGHT = 8;
    private static final int DROP_DISTANCE = 0x68;
    private static final int COLLAPSE_DELAY = 0x0E;
    private static final int[] FALL_DELAYS = {8, 0x10, 0x0C, 0x0E, 6, 0x0A, 4, 2, 8, 0x10, 0x0C, 0x0E, 6, 0x0A};

    private final int pivotX;
    private final int pivotY;
    private final boolean xFlip;
    private final boolean reverseVertical;
    private final int settledAngle;
    private final boolean cutsceneOverride;

    private int currentX;
    private int currentY;
    private int angle;
    private int angleStep;
    private boolean dropStarted;
    private boolean settled;
    private boolean collapseStarted;
    private int collapseTimer;

    private final List<PlayableEntity> standingPlayers = new ArrayList<>(2);
    private final int[] pieceX = new int[SEGMENT_COUNT];
    private final int[] pieceY = new int[SEGMENT_COUNT];

    public AizDrawBridgeObjectInstance(ObjectSpawn spawn) {
        this(spawn, false);
    }

    private AizDrawBridgeObjectInstance(ObjectSpawn spawn, boolean cutsceneOverride) {
        super(spawn, "AIZDrawBridge");
        this.pivotX = spawn.x();
        this.pivotY = spawn.y();
        this.xFlip = (spawn.renderFlags() & 0x01) != 0;
        this.reverseVertical = (spawn.renderFlags() & 0x02) != 0;
        this.cutsceneOverride = cutsceneOverride;
        this.angle = reverseVertical ? 0x40 : -0x40;
        this.settledAngle = reverseVertical ? 0x80 : 0;
        this.angleStep = xFlip ? -2 : 2;
        this.currentX = pivotX;
        this.currentY = pivotY + (reverseVertical ? DROP_DISTANCE : -DROP_DISTANCE);
        updateBridgePieces();
    }

    public static AizDrawBridgeObjectInstance createCutsceneOverride() {
        return new AizDrawBridgeObjectInstance(
                new ObjectSpawn(0x4B48, 0x0218, 0x32, 0, 2, false, 0), true);
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
    public boolean isPersistent() {
        return !isDestroyed();
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(HALF_WIDTH, HEIGHT, HEIGHT + 1);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        return settled && !collapseStarted;
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (contact.standing() && !standingPlayers.contains(player)) {
            standingPlayers.add(player);
        }
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (!cutsceneOverride && Aiz2BossEndSequenceState.isCutsceneOverrideObjectsActive()) {
            setDestroyed(true);
            return;
        }

        if (!dropStarted && Aiz2BossEndSequenceState.isBridgeDropTriggered()) {
            dropStarted = true;
            currentX = pivotX + (xFlip ? -DROP_DISTANCE : DROP_DISTANCE);
            currentY = pivotY;
            services().playSfx(Sonic3kSfx.FLIP_BRIDGE.id);
        }

        if (dropStarted && !settled) {
            angle += angleStep;
            if ((angleStep > 0 && angle >= settledAngle) || (angleStep < 0 && angle <= settledAngle)) {
                angle = settledAngle;
                settled = true;
                services().playSfx(Sonic3kSfx.FLIP_BRIDGE.id);
            }
            updateBridgePieces();
        }

        if (settled && !collapseStarted && Aiz2BossEndSequenceState.isButtonPressed()) {
            collapseStarted = true;
            collapseTimer = COLLAPSE_DELAY;
            spawnFallingSegments();
            services().playSfx(Sonic3kSfx.BRIDGE_COLLAPSE.id);
        }

        if (collapseStarted) {
            if (collapseTimer > 0) {
                collapseTimer--;
            } else {
                ejectStandingPlayers();
                setDestroyed(true);
            }
        }
    }

    private void updateBridgePieces() {
        double radians = (angle * Math.PI * 2.0) / 256.0;
        double stepX = Math.cos(radians) * SEGMENT_SPACING;
        double stepY = Math.sin(radians) * SEGMENT_SPACING;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            pieceX[i] = pivotX + (int) Math.round(stepX * i);
            pieceY[i] = pivotY + (int) Math.round(stepY * i);
        }
        currentX = (pieceX[0] + pieceX[SEGMENT_COUNT - 1]) / 2;
        currentY = (pieceY[0] + pieceY[SEGMENT_COUNT - 1]) / 2;
    }

    private void spawnFallingSegments() {
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            spawnDynamicObject(new FallingBridgeSegment(pieceX[i], pieceY[i], FALL_DELAYS[i]));
        }
    }

    private void ejectStandingPlayers() {
        for (PlayableEntity player : List.copyOf(standingPlayers)) {
            player.setOnObject(false);
            player.setPushing(false);
            player.setAir(true);
            if (player instanceof AbstractPlayableSprite sprite) {
                sprite.setAnimationId(Sonic3kAnimationIds.HURT_FALL);
            }
        }
        standingPlayers.clear();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (collapseStarted) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.AIZ_DRAW_BRIDGE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            renderer.drawFrameIndex(1, pieceX[i], pieceY[i], false, false);
        }
    }

    private static final class FallingBridgeSegment extends AbstractObjectInstance {
        private int x;
        private int y;
        private int delay;
        private final SubpixelMotion.State motion;

        private FallingBridgeSegment(int x, int y, int delay) {
            super(new ObjectSpawn(x, y, 0x32, 0, 0, false, 0), "AIZDrawBridgeSegment");
            this.x = x;
            this.y = y;
            this.delay = delay;
            this.motion = new SubpixelMotion.State(0, y, 0, x, 0, 0);
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
        public boolean isPersistent() {
            return !isDestroyed();
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY);
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (delay > 0) {
                delay--;
                return;
            }
            SubpixelMotion.objectFall(motion, 0x38);
            x = motion.x;
            y = motion.y;
            if (!isOnScreen(128)) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.AIZ_DRAW_BRIDGE);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(1, x, y, false, false);
        }
    }
}
