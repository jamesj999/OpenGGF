package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * S3K Obj 0x06 - AIZ Ride Vine.
 *
 * <p>Primary disassembly references:
 * Obj_AIZRideVine / Obj_AIZRideVineHandle (sonic3k.asm:46098-46748).
 */
public class AizRideVineObjectInstance extends AbstractObjectInstance {
    private static final int ROOT_FRAME = 0x21;
    private static final int HANDLE_FRAME = 0x20;
    private static final int PRIORITY_BUCKET = 4; // priority $200
    private static final int SEGMENT_GAP = 0x10;
    private static final int MOVE_STEP_X = 8; // addi.l #$80000
    private static final int MOVE_STEP_Y = 2; // addi.l #$20000
    private static final int STILL_ANIM_STEP_FRAMES = 4;
    private static final int STILL_ANIM_FIRST_LOOP_FRAME = 5;
    private static final int STILL_ANIM_LAST_LOOP_FRAME = 8;

    private enum State {
        WAIT_FOR_GRAB,
        ZIP_TO_TARGET,
        SWING_DAMPED,
        PENDULUM_DECAY,
        SIN_SWING,
        STILL_SPRITE
    }

    private static final class Segment {
        int x;
        int y;
        int angle;
        int value3A;
        int mappingFrame;
    }

    private final int subtype;
    private final int targetX;

    private int currentX;
    private int currentY;
    private ObjectSpawn dynamicSpawn;

    private final Segment first = new Segment();
    private final Segment[] chain = {
            new Segment(),
            new Segment(),
            new Segment()
    };
    private final AizVineHandleLogic.State handle = new AizVineHandleLogic.State();

    private State state = State.WAIT_FOR_GRAB;
    private int rootAngle;
    private int root3A;
    private int root2E;
    private int root38;
    private int root42;
    private int root44;

    private boolean firstCopiesParent;
    private int first2E;
    private int first38;
    private int first3A;

    private int rootFrame = ROOT_FRAME;
    private int stillXVel = 0x800;
    private int stillYVel = 0x200;
    private int stillFrame;
    private int stillAnimTimer;

    public AizRideVineObjectInstance(ObjectSpawn spawn) {
        super(spawn, "AIZRideVine");
        this.subtype = spawn.subtype();
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.dynamicSpawn = spawn;
        this.targetX = currentX + ((subtype & 0x7F) << 4);

        first.x = currentX;
        first.y = currentY;
        for (int i = 0; i < chain.length; i++) {
            chain[i].x = currentX;
            chain[i].y = currentY + ((i + 1) * SEGMENT_GAP);
        }
        handle.x = currentX;
        handle.y = currentY + (4 * SEGMENT_GAP);
        handle.prevX = handle.x;
        handle.prevY = handle.y;
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
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isPersistent() {
        return AizVineHandleLogic.anyGrabbed(handle);
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        updateRootState();
        updateSegments();
        updateHandle(player);
        updateDynamicSpawn();

        // ROM cull path in loc_21F38/loc_21F52.
        int coarse = (currentX & 0xFF80) - Camera.getInstance().getX();
        if ((coarse < 0 || coarse > 0x280) && !AizVineHandleLogic.anyGrabbed(handle)) {
            setDestroyed(true);
        }
    }

    @Override
    public void onUnload() {
        clearGrabbedPlayers();
    }

    private void updateRootState() {
        switch (state) {
            case WAIT_FOR_GRAB -> {
                if (AizVineHandleLogic.anyGrabbed(handle)) {
                    state = State.ZIP_TO_TARGET;
                    first2E = 1;
                    first38 = 0;
                }
            }
            case ZIP_TO_TARGET -> {
                currentX += MOVE_STEP_X;
                currentY += MOVE_STEP_Y;
                if (currentX >= targetX) {
                    if ((subtype & 0x80) != 0) {
                        state = State.STILL_SPRITE;
                        rootFrame = 0;
                        stillFrame = 0;
                        stillAnimTimer = 0;
                        AizVineHandleLogic.markGrabbedAsFastEject(handle);
                    } else {
                        state = State.SWING_DAMPED;
                        firstCopiesParent = true;
                        first3A = 0;
                        handle.mode = 1;
                        rootAngle = 0;
                        root3A = 0x400;
                    }
                }
            }
            case SWING_DAMPED -> updateSwingDamped();
            case PENDULUM_DECAY -> updatePendulumDecay();
            case SIN_SWING -> updateSinSwing();
            case STILL_SPRITE -> updateStillSprite();
        }
    }

    private void updateSwingDamped() {
        int d0 = root3A;
        int d1 = Math.abs(signedAngleByte(rootAngle));
        d0 -= d1 + d1;
        rootAngle = asSigned16(rootAngle - d0);

        if (!AizVineHandleLogic.anyGrabbed(handle)) {
            int angleByte = (angleByte(rootAngle) + 8) & 0xFF;
            if (angleByte < 0x10) {
                state = State.PENDULUM_DECAY;
                root42 = 0;
                root44 = -0x300;
                root38 = 0x1000;
                root2E = 0;
                handle.mode = 2;
            }
        }
    }

    private void updatePendulumDecay() {
        int d2 = angleByte(root38);
        int d0 = root44;
        if (root2E == 0) {
            d0 += d2;
            root44 = asSigned16(d0);
            root42 = asSigned16(root42 + root44);
            if (signedAngleByte(root42) >= 0) {
                int damp = root44 >> 4;
                root44 = asSigned16(root44 - damp);
                root2E = 1;
                if (root38 == 0x0C00) {
                    state = State.SIN_SWING;
                    root38 = 0;
                    handle.mode = 0;
                } else {
                    root38 = asSigned16(root38 - 0x40);
                }
            }
        } else {
            d0 -= d2;
            root44 = asSigned16(d0);
            root42 = asSigned16(root42 + root44);
            if (signedAngleByte(root42) < 0) {
                int damp = root44 >> 4;
                root44 = asSigned16(root44 - damp);
                root2E = 0;
                if (root38 == 0x0C00) {
                    state = State.SIN_SWING;
                    root38 = 0;
                    handle.mode = 0;
                } else {
                    root38 = asSigned16(root38 - 0x40);
                }
            }
        }

        rootAngle = asSigned16(root42);
        root3A = rootAngle >> 3;
        first3A = root3A;
    }

    private void updateSinSwing() {
        int angle = angleByte(root38);
        root38 = asSigned16(root38 + 0x200);
        int sin = TrigLookupTable.sinHex(angle) << 2;
        if (sin == 0x400) {
            sin = 0x3FF;
        }
        rootAngle = asSigned16(sin);
        root3A = rootAngle;
        first3A = root3A;
    }

    private void updateSegments() {
        updateFirstSegment();

        Segment parent = first;
        for (Segment segment : chain) {
            segment.value3A = parent.value3A;
            segment.angle = asSigned16(parent.angle + segment.value3A);
            segment.mappingFrame = ((angleByte(segment.angle) + 4) & 0xFF) >> 3;
            int[] offset = offsetFromAngle(parent.angle);
            segment.x = parent.x + offset[0];
            segment.y = parent.y + offset[1];
            parent = segment;
        }
    }

    private void updateFirstSegment() {
        if (firstCopiesParent) {
            first.angle = rootAngle;
        } else {
            if (first2E == 0) {
                int angle = angleByte(first38);
                first38 = asSigned16(first38 + 0x200);
                int sin = TrigLookupTable.sinHex(angle) << 2;
                if (sin == 0x400) {
                    sin = 0x3FF;
                }
                first3A = asSigned16(sin);
            } else {
                int angle = angleByte(first38);
                first38 = asSigned16(first38 + 0x100);
                first3A = asSigned16(TrigLookupTable.sinHex(angle) << 3);
            }
            first.angle = first3A;
        }

        first.value3A = first3A;
        first.mappingFrame = ((angleByte(first.angle) + 4) & 0xFF) >> 3;
        first.x = currentX;
        first.y = currentY;
    }

    private void updateHandle(AbstractPlayableSprite player) {
        Segment lastSegment = chain[chain.length - 1];
        AizVineHandleLogic.positionFromParent(handle, lastSegment.x, lastSegment.y, lastSegment.angle);
        var sidekicks = SpriteManager.getInstance().getSidekicks();
        AbstractPlayableSprite sidekick = sidekicks.isEmpty() ? null : sidekicks.getFirst();
        AizVineHandleLogic.updatePlayers(handle, player, sidekick, lastSegment.angle);
    }

    private void updateStillSprite() {
        currentX += stillXVel >> 8;
        currentY += stillYVel >> 8;

        stillAnimTimer++;
        if (stillAnimTimer < STILL_ANIM_STEP_FRAMES) {
            return;
        }
        stillAnimTimer = 0;

        if (stillFrame == 0) {
            stillFrame = STILL_ANIM_FIRST_LOOP_FRAME;
            return;
        }

        stillFrame++;
        if (stillFrame > STILL_ANIM_LAST_LOOP_FRAME) {
            stillFrame = STILL_ANIM_FIRST_LOOP_FRAME;
        }
    }

    private void clearGrabbedPlayers() {
        AbstractPlayableSprite player = resolveMainPlayer();
        var sidekicks = SpriteManager.getInstance().getSidekicks();
        AbstractPlayableSprite sidekick = sidekicks.isEmpty() ? null : sidekicks.getFirst();
        clearControlFor(player, handle.p1.grabFlag != 0);
        clearControlFor(sidekick, handle.p2.grabFlag != 0);
        handle.p1.grabFlag = 0;
        handle.p2.grabFlag = 0;
    }

    private AbstractPlayableSprite resolveMainPlayer() {
        var sprite = SpriteManager.getInstance().getSprite(
                SonicConfigurationService.getInstance()
                        .getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        return sprite instanceof AbstractPlayableSprite playable ? playable : null;
    }

    private static void clearControlFor(AbstractPlayableSprite player, boolean wasGrabbed) {
        if (player == null || !wasGrabbed) {
            return;
        }
        player.setControlLocked(false);
        player.setObjectControlled(false);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.AIZ_RIDE_VINE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        if (state == State.STILL_SPRITE) {
            PatternSpriteRenderer stillRenderer = renderManager.getRenderer(Sonic3kObjectArtKeys.ANIMATED_STILL_SPRITES);
            if (stillRenderer != null && stillRenderer.isReady()) {
                stillRenderer.drawFrameIndex(stillFrame, currentX, currentY, false, false);
            } else {
                renderer.drawFrameIndex(rootFrame, currentX, currentY, false, false);
            }
        } else {
            renderer.drawFrameIndex(rootFrame, currentX, currentY, false, false);
        }
        renderer.drawFrameIndex(first.mappingFrame, first.x, first.y, false, false);
        for (Segment segment : chain) {
            renderer.drawFrameIndex(segment.mappingFrame, segment.x, segment.y, false, false);
        }
        if (AizVineHandleLogic.shouldRender(handle)) {
            renderer.drawFrameIndex(HANDLE_FRAME, handle.x, handle.y, false, false);
        }
    }

    private void updateDynamicSpawn() {
        if (dynamicSpawn.x() == currentX && dynamicSpawn.y() == currentY) {
            return;
        }
        dynamicSpawn = buildSpawnAt(currentX, currentY);
    }

    private static int asSigned16(int value) {
        return (short) value;
    }

    private static int angleByte(int angleWord) {
        return (angleWord >> 8) & 0xFF;
    }

    private static int signedAngleByte(int angleWord) {
        return (byte) angleByte(angleWord);
    }

    private static int[] offsetFromAngle(int angle) {
        int byteAngle = (angleByte(angle) + 4) & 0xF8;
        int sin = TrigLookupTable.sinHex(byteAngle);
        int cos = TrigLookupTable.cosHex(byteAngle);
        return new int[]{(-sin + 8) >> 4, (cos + 8) >> 4};
    }
}
