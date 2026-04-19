package com.openggf.game.sonic3k.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x50 - MGZ Twisting Loop.
 *
 * <p>Invisible controller for the spiral descent after the top-platform launcher.
 * ROM reference: Obj_MGZTwistingLoop (sonic3k.asm:70187-70387).
 */
public class MGZTwistingLoopObjectInstance extends AbstractObjectInstance {
    private static final int CAPTURE_X_BIAS = 0x24;
    private static final int CAPTURE_Y_RANGE = 0x20;
    private static final int ACTIVE_RELEASE_COOLDOWN = 8;
    private static final int JUMP_RELEASE_FRAMES = 8;
    private static final int JUMP_RELEASE_X_VEL = 0x800;
    private static final int JUMP_RELEASE_Y_VEL = -0x200;
    private static final int JUMP_RELEASE_GRAVITY = 0x38;
    private static final int MIN_GROUND_SPEED = 0x400;
    private static final int MAX_GROUND_SPEED = 0x0C00;
    private static final int DESCENT_PROGRESS_SCALE = 0xC0;
    private static final int ANGLE_PROGRESS_SCALE = 0x155;
    private static final int CAPTURE_ANIMATION = 0;
    private static final int RELEASE_ANIMATION = 1;
    private static final int[] TWIST_FRAMES = {
            0x76, 0x76, 0x77, 0x77, 0x6C, 0x6C, 0x6D, 0x6D, 0x6E, 0x6E, 0x6F, 0x6F,
            0x70, 0x70, 0x71, 0x71, 0x72, 0x72, 0x73, 0x73, 0x74, 0x74, 0x75, 0x75
    };

    private static final class PlayerState {
        boolean active;
        int progressFixed;
        int sidePhaseOffset;
        int releaseFrames;
        int cooldownFrames;
    }

    private final int centerX;
    private final int centerY;
    private final int captureThreshold;
    private final boolean flipped;
    private final PlayerState player1 = new PlayerState();
    private final PlayerState player2 = new PlayerState();

    public MGZTwistingLoopObjectInstance(ObjectSpawn spawn) {
        super(spawn, "MGZTwistingLoop");
        this.centerX = spawn.x();
        this.centerY = spawn.y();
        this.captureThreshold = (spawn.subtype() & 0xFF) << 4;
        this.flipped = (spawn.renderFlags() & 0x01) != 0;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (playerEntity instanceof AbstractPlayableSprite player) {
            processPlayer(frameCounter, player, player1);
        }
        ObjectServices svc = tryServices();
        if (svc == null) {
            return;
        }
        boolean handledSidekick = false;
        for (PlayableEntity sidekickEntity : svc.sidekicks()) {
            if (sidekickEntity instanceof AbstractPlayableSprite sidekick) {
                processPlayer(frameCounter, sidekick, player2);
                handledSidekick = true;
                break;
            }
        }
        if (!handledSidekick) {
            player2.active = false;
            player2.releaseFrames = 0;
            player2.cooldownFrames = 0;
        }
    }

    private void processPlayer(int frameCounter, AbstractPlayableSprite player, PlayerState state) {
        if (state.releaseFrames > 0) {
            updateReleasedPlayer(frameCounter, player, state);
            return;
        }
        if (state.cooldownFrames > 0) {
            state.cooldownFrames--;
        }
        if (state.active) {
            updateCapturedPlayer(frameCounter, player, state);
            return;
        }
        if (state.cooldownFrames == 0) {
            tryCapturePlayer(frameCounter, player, state);
        }
    }

    private void tryCapturePlayer(int frameCounter, AbstractPlayableSprite player, PlayerState state) {
        if (player == null || player.getDead() || player.isHurt() || player.isDebugMode()) {
            return;
        }
        if (player.isObjectControlled()) {
            return;
        }
        if (player.wasRecentlyObjectControlled(frameCounter, ACTIVE_RELEASE_COOLDOWN)) {
            return;
        }
        if (player.getAir()) {
            return;
        }
        if ((player.getAngle() & 0x7F) != 0x40) {
            return;
        }

        int range = player.getYRadius() + CAPTURE_X_BIAS;
        int dx = player.getCentreX() - centerX;
        if (dx < -range || dx >= range) {
            return;
        }

        int dy = player.getCentreY() - centerY;
        if (dy < 0 || dy >= CAPTURE_Y_RANGE) {
            return;
        }

        state.active = true;
        state.progressFixed = dy << 16;
        state.sidePhaseOffset = dx < 0 ? 0x80 : 0x00;
        state.releaseFrames = 0;
        state.cooldownFrames = 0;

        if (player.isOnObject()) {
            ObjectServices svc = tryServices();
            if (svc != null && svc.objectManager() != null) {
                svc.objectManager().clearRidingObject(player);
            }
        }

        player.setControlLocked(true);
        player.setObjectControlled(true);
        player.setObjectMappingFrameControl(true);
        player.setOnObject(true);
        player.setAir(false);
        player.setPushing(false);
        player.setRolling(false);
        player.setRollingJump(false);
        player.setJumping(false);
        player.restoreDefaultRadii();
        player.setAnimationId(CAPTURE_ANIMATION);
        player.setHighPriority(false);
        player.setXSpeed((short) 0);

        if (dx < 0) {
            player.setAngle((byte) 0xC0);
            player.setDirection(Direction.LEFT);
            player.setRenderFlips(false, false);
        } else {
            player.setAngle((byte) 0x40);
            player.setDirection(Direction.RIGHT);
            player.setRenderFlips(true, false);
        }
    }

    private void updateCapturedPlayer(int frameCounter, AbstractPlayableSprite player, PlayerState state) {
        if (player == null || player.getDead() || player.isHurt() || player.isDebugMode()) {
            releaseCapturedPlayer(frameCounter, player, state, false);
            return;
        }

        if (player.isJumpPressed()) {
            releaseCapturedPlayer(frameCounter, player, state, true);
            return;
        }

        int currentProgressPixels = state.progressFixed >> 16;
        if (currentProgressPixels >= captureThreshold) {
            releaseCapturedPlayer(frameCounter, player, state, false);
            return;
        }

        int groundSpeed = player.getGSpeed();
        int speedSign = (groundSpeed < 0) ? -1 : 1;
        int speedMagnitude = Math.abs(groundSpeed);
        if (speedMagnitude < MIN_GROUND_SPEED) {
            speedMagnitude = MIN_GROUND_SPEED;
        } else if (speedMagnitude > MAX_GROUND_SPEED) {
            speedMagnitude = MAX_GROUND_SPEED;
        }
        player.setGSpeed((short) (speedSign * speedMagnitude));

        int ySpeed = Math.max(player.getYSpeed() & 0xFFFF, speedMagnitude);
        if (ySpeed > MAX_GROUND_SPEED) {
            ySpeed = MAX_GROUND_SPEED;
        }
        player.setYSpeed((short) ySpeed);

        state.progressFixed += ySpeed * DESCENT_PROGRESS_SCALE;
        int progressPixels = state.progressFixed >> 16;
        int phaseBase = ((progressPixels * ANGLE_PROGRESS_SCALE) >> 8) & 0xFF;
        int phase = (phaseBase + state.sidePhaseOffset) & 0xFF;
        int cosine = TrigLookupTable.cosHex(phase);
        int horizontalOffset = (cosine >> 3) + ((player.getYRadius() * cosine) >> 8);

        player.setCentreX((short) (centerX + horizontalOffset));
        player.setCentreY((short) (centerY + progressPixels));
        player.setAnimationId(CAPTURE_ANIMATION);
        player.setRolling(false);
        player.setOnObject(true);
        player.setAir(false);
        player.restoreDefaultRadii();
        player.setHighPriority(phaseBase < 0x80);
        applyTwistFrame(player, phaseBase);
        player.setAngle((byte) ((flipped ? 0x80 : 0x00) + phase));
    }

    private void applyTwistFrame(AbstractPlayableSprite player, int phaseBase) {
        int frameIndex = (((0x40 - phaseBase) & 0xFF) / 0x0B);
        if (frameIndex < 0) {
            frameIndex = 0;
        } else if (frameIndex >= TWIST_FRAMES.length) {
            frameIndex = TWIST_FRAMES.length - 1;
        }
        player.setMappingFrame(TWIST_FRAMES[frameIndex]);
    }

    private void releaseCapturedPlayer(int frameCounter, AbstractPlayableSprite player, PlayerState state, boolean jumpedOut) {
        state.active = false;
        state.cooldownFrames = ACTIVE_RELEASE_COOLDOWN;
        state.releaseFrames = jumpedOut ? JUMP_RELEASE_FRAMES : 0;

        if (player == null) {
            return;
        }

        player.setObjectMappingFrameControl(false);
        player.setControlLocked(false);
        if (jumpedOut) {
            player.setObjectControlled(true);
        } else {
            player.releaseFromObjectControl(frameCounter);
        }
        player.setPushing(false);
        player.setRolling(false);
        player.restoreDefaultRadii();
        player.setAnimationId(RELEASE_ANIMATION);
        player.setHighPriority(false);
        player.setOnObject(false);
        ObjectServices svc = tryServices();
        if (svc != null && svc.objectManager() != null) {
            svc.objectManager().clearRidingObject(player);
        }

        if (jumpedOut) {
            int xVel = player.getCentreX() < centerX ? -JUMP_RELEASE_X_VEL : JUMP_RELEASE_X_VEL;
            player.setAir(true);
            player.setJumping(false);
            player.setXSpeed((short) xVel);
            player.setYSpeed((short) JUMP_RELEASE_Y_VEL);
            player.setDirection(xVel < 0 ? Direction.LEFT : Direction.RIGHT);
            player.suppressNextJumpPress();
        } else {
            player.setAir(false);
        }
    }

    private void updateReleasedPlayer(int frameCounter, AbstractPlayableSprite player, PlayerState state) {
        if (player == null) {
            state.releaseFrames = 0;
            return;
        }

        state.releaseFrames--;
        if (state.releaseFrames == 0) {
            player.releaseFromObjectControl(frameCounter);
            return;
        }

        int nextCenterX = player.getCentreX() + (player.getXSpeed() >> 8);
        int nextCenterY = player.getCentreY() + (player.getYSpeed() >> 8);
        player.setCentreX((short) nextCenterX);
        player.setCentreY((short) nextCenterY);
        player.setYSpeed((short) (player.getYSpeed() + JUMP_RELEASE_GRAVITY));
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Invisible controller.
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        if (ctx == null) {
            return;
        }
        ctx.drawRect(centerX - 0x38, centerY, 0x70, CAPTURE_Y_RANGE, 0.3f, 0.8f, 1.0f);
    }

    @Override
    public int getX() {
        return centerX;
    }

    @Override
    public int getY() {
        return centerY;
    }
}
