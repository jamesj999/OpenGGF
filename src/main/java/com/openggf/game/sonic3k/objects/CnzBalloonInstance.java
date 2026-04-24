package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectSnapshot;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM object: {@code Obj_CNZBalloon}.
 *
 * <p>CNZ balloons are local launchers, not path-following transport objects.
 * The S3K disassembly loads {@code Map_CNZBalloon} and uses the balloon's
 * center position as the contact anchor; on contact it applies the ROM bounce
 * impulse and restores normal player control.
 *
 * <p>The art sheet is loaded from the verified CNZ mapping table in
 * {@link com.openggf.game.sonic3k.Sonic3kObjectArt} using the lock-on ROM
 * offsets captured in {@code Sonic3kConstants}. The subtype's low 3 bits select
 * the balloon color variant, matching the SonLVL CNZ definition.
 */
public final class CnzBalloonInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener {

    private static final int COLLISION_FLAGS = 0x40 | 0x17;
    private static final int WIDTH_HALF = 0x10;
    private static final int HEIGHT_HALF = 0x20;
    private static final int ROM_BOUNCE_Y_SPEED = 0x700;
    private static final int OFFSCREEN_X = 0x7F00;
    private static final int NORMAL_FRAME_DELAY = 7;
    private static final int POP_FRAME_DELAY = 2;
    private static final int[] NORMAL_FRAME_SEQUENCE = {0, 1, 2, 1};
    private static final int[] POP_FRAME_SEQUENCE = {3, 4};
    private static final int[] FRAME_BY_COLOR = {0, 5, 10, 15, 20};
    private static final int SNAPSHOT_BASE_Y_OFFSET = 0x32;
    private static final int SNAPSHOT_COLLISION_FLAGS_OFFSET = 0x28;

    private final int subtype;
    private int baseY;
    private int angle;
    private boolean popped;
    private boolean movedOffscreen;
    private int animationTimer;
    private int normalAnimationIndex;
    private int popAnimationIndex;
    private int frameOffset;
    private int lastLaunchFrame = Integer.MIN_VALUE;

    public CnzBalloonInstance(ObjectSpawn spawn) {
        super(spawn, "CNZBalloon");
        this.subtype = spawn.subtype();
        this.baseY = spawn.y();
        this.angle = initialAngle(spawn);
    }

    @Override
    public void hydrateFromRomSnapshot(RomObjectSnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }

        int snapshotBaseY = snapshot.wordAt(SNAPSHOT_BASE_Y_OFFSET);
        if (snapshotBaseY != 0) {
            baseY = snapshotBaseY;
        }
        angle = snapshot.angle() & 0xFF;
        popped = snapshot.byteAt(SNAPSHOT_COLLISION_FLAGS_OFFSET) == 0;
        movedOffscreen = snapshot.xPos() == OFFSCREEN_X;
        updateDynamicSpawn(snapshot.xPos(), snapshot.yPos());

        int colorBase = FRAME_BY_COLOR[Math.min(subtype & 0x07, FRAME_BY_COLOR.length - 1)];
        int mappingFrame = snapshot.mappingFrame();
        if (mappingFrame >= colorBase && mappingFrame < colorBase + 5) {
            frameOffset = mappingFrame - colorBase;
        }
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }

        advanceAnimation();
        int bobbedY = baseY + bobOffset(angle);
        updateDynamicSpawn(movedOffscreen ? OFFSCREEN_X : spawn.x(), bobbedY);
        angle = (angle + 1) & 0xFF;

        // ROM Obj_CNZBalloon reacts only when Touch_Process sets
        // collision_property; the shared touch-response pass invokes
        // onTouchResponse with the Touch_Sizes hitbox.
    }

    @Override
    public int getCollisionFlags() {
        if (popped || movedOffscreen) {
            return 0;
        }
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public boolean requiresContinuousTouchCallbacks() {
        return !popped;
    }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (popped) {
            return;
        }
        launchPlayer(player, frameCounter);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.CNZ_BALLOON);
        if (renderer == null) {
            return;
        }

        boolean hFlip = (spawn.renderFlags() & 0x01) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x02) != 0;
        renderer.drawFrameIndex(getFrameIndex(), getX(), getY(), hFlip, vFlip);
    }

    private int getFrameIndex() {
        int color = subtype & 0x07;
        if (color >= FRAME_BY_COLOR.length) {
            color = FRAME_BY_COLOR.length - 1;
        }
        return FRAME_BY_COLOR[color] + frameOffset;
    }

    private void launchPlayer(PlayableEntity playerEntity, int frameCounter) {
        if (popped || playerEntity == null || lastLaunchFrame == frameCounter) {
            return;
        }
        lastLaunchFrame = frameCounter;

        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        boolean shouldSnapToCentre = (subtype & 0x80) != 0 && player.isInWater();
        if ((subtype & 0x80) != 0) {
            player.setYSpeed((short) -0x380);
        } else {
            player.setYSpeed((short) -ROM_BOUNCE_Y_SPEED);
        }
        player.setAir(true);
        player.setRollingJump(false);
        player.setJumping(false);
        player.setControlLocked(false);
        player.setObjectControlled(false);
        if (shouldSnapToCentre) {
            player.setCentreX((short) getX());
            player.setCentreY((short) getY());
        }
        popped = true;
        animationTimer = 0;
        popAnimationIndex = 0;
        frameOffset = POP_FRAME_SEQUENCE[0];

        try {
            services().playSfx(Sonic3kSfx.BALLOON.id);
        } catch (Exception ignored) {
            // Headless tests can omit the audio backend; launch state is still valid.
        }
    }

    int getRenderFrameForTest() {
        return getFrameIndex();
    }

    boolean isPoppedForTest() {
        return popped;
    }

    boolean hasMovedOffscreenForTest() {
        return movedOffscreen;
    }

    @Override
    public String traceDebugDetails() {
        return String.format("ang=%02X base=%04X frame=%d%s",
                angle & 0xFF,
                baseY & 0xFFFF,
                getFrameIndex(),
                popped ? ",popped" : "");
    }

    private void advanceAnimation() {
        if (movedOffscreen) {
            return;
        }
        if (popped) {
            advancePopAnimation();
        } else {
            advanceNormalAnimation();
        }
    }

    private void advanceNormalAnimation() {
        animationTimer--;
        if (animationTimer >= 0) {
            return;
        }
        animationTimer = NORMAL_FRAME_DELAY;
        frameOffset = NORMAL_FRAME_SEQUENCE[normalAnimationIndex];
        normalAnimationIndex = (normalAnimationIndex + 1) % NORMAL_FRAME_SEQUENCE.length;
    }

    private void advancePopAnimation() {
        animationTimer--;
        if (animationTimer >= 0) {
            return;
        }
        if (popAnimationIndex < POP_FRAME_SEQUENCE.length) {
            animationTimer = POP_FRAME_DELAY;
            frameOffset = POP_FRAME_SEQUENCE[popAnimationIndex++];
        } else {
            movedOffscreen = true;
        }
    }

    private static int initialAngle(ObjectSpawn spawn) {
        var ctx = constructionContext();
        if (ctx != null && ctx.rng() != null) {
            return ctx.rng().nextByte();
        }
        return (spawn.x() ^ spawn.y() ^ spawn.subtype()) & 0xFF;
    }

    private static int bobOffset(int angle) {
        return TrigLookupTable.sinHex(angle) >> 5;
    }
}
