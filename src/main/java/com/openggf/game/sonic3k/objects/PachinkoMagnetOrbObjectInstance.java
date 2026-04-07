package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * Object 0xEC - Pachinko magnet orb.
 *
 * <p>ROM reference: {@code Obj_PachinkoMagnetOrb}. The orb tracks capture/orbit state
 * per player slot rather than sharing one global state across the object.
 */
public class PachinkoMagnetOrbObjectInstance extends AbstractObjectInstance {

    private static final int CATCH_RANGE = 0x38;
    private static final int RELEASE_COOLDOWN = 30;
    private static final int ORBIT_RADIUS_SCALE = 0x3800;
    private static final int RELEASE_MAGNITUDE = 0x0C;
    private static final int HOVER_SFX_PERIOD = 16;
    private static final int[] RELEASE_TABLE = {
            0x20, 0x20, 0x20, 0x30, 0x40, 0x50, 0x60, 0x60,
            0x60, 0xA0, 0xA0, 0xB0, 0xC0, 0xD0, 0xE0, 0xE0
    };

    private final IdentityHashMap<AbstractPlayableSprite, PlayerState> playerStates =
            new IdentityHashMap<>();

    public PachinkoMagnetOrbObjectInstance(ObjectSpawn spawn) {
        super(spawn, "PachinkoMagnetOrb");
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (playerEntity instanceof AbstractPlayableSprite player) {
            updatePlayer(player, frameCounter);
        }
        for (PlayableEntity sidekickEntity : services().sidekicks()) {
            if (sidekickEntity instanceof AbstractPlayableSprite sidekick) {
                updatePlayer(sidekick, frameCounter);
            }
        }
    }

    private void updatePlayer(AbstractPlayableSprite player, int frameCounter) {
        PlayerState state = playerStates.computeIfAbsent(player, ignored -> new PlayerState());
        if (state.captured) {
            updateCapturedPlayer(player, state, frameCounter);
            return;
        }

        if (state.cooldownFrames > 0) {
            state.cooldownFrames--;
            if (state.cooldownFrames == 0) {
                restoreReleasedPlayerPriority(player);
            } else {
                return;
            }
        }

        if (player.isDebugMode() || player.getDead() || player.isHurt()) {
            return;
        }

        int dx = player.getCentreX() - spawn.x();
        int dy = player.getCentreY() - spawn.y();
        if (dx < -CATCH_RANGE || dx >= CATCH_RANGE || dy < -CATCH_RANGE || dy >= CATCH_RANGE) {
            return;
        }
        if (player.isObjectControlled() || player.isControlLocked()) {
            return;
        }

        capturePlayer(player, state);
    }

    private void updateCapturedPlayer(AbstractPlayableSprite player, PlayerState state,
                                      int frameCounter) {
        if (player.isDebugMode() || player.getDead() || player.isHurt()) {
            releasePlayer(player, state, frameCounter, false);
            return;
        }
        if (shouldReleaseCapturedSidekick(player)) {
            releasePlayer(player, state, frameCounter, false);
            return;
        }

        if (player.isJumpJustPressed()) {
            releasePlayer(player, state, frameCounter, true);
            return;
        }

        if (player.isLeftPressed()) {
            state.angleB = (state.angleB - 1) & 0xFF;
        }
        if (player.isRightPressed()) {
            state.angleB = (state.angleB + 1) & 0xFF;
        }

        int previousX = player.getCentreX();
        int previousY = player.getCentreY();
        placePlayerOnOrbit(player, state);
        player.setXSpeed((short) (-(player.getCentreX() - previousX) << 8));
        player.setYSpeed((short) (-(player.getCentreY() - previousY) << 8));
        player.setGSpeed((short) 0x0800);
        player.setAir(true);

        if ((frameCounter & (HOVER_SFX_PERIOD - 1)) == 0) {
            playSfx(Sonic3kSfx.HOVERPAD);
        }
    }

    private boolean shouldReleaseCapturedSidekick(AbstractPlayableSprite player) {
        if (!player.isCpuControlled()) {
            return false;
        }
        Camera camera = services().camera();
        return camera != null && !camera.isOnScreen(player);
    }

    private void capturePlayer(AbstractPlayableSprite player, PlayerState state) {
        int angle = TrigLookupTable.calcAngle((short) (player.getCentreX() - spawn.x()),
                (short) (player.getCentreY() - spawn.y()));
        if ((angle & 0x80) != 0) {
            state.angleA = -0x80;
            angle = (angle + 0x80) & 0xFF;
        } else {
            state.angleA = 0;
        }
        state.angleB = (angle - 0x40) & 0xFF;

        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0x0800);
        player.setControlLocked(true);
        // Engine movement skips only when objectControlled is true, so mirror the
        // ROM's captured-player ownership with the engine's full-control flag.
        player.setObjectControlled(true);
        player.setAir(true);
        player.setOnObject(false);
        player.setJumping(false);
        player.setAnimationId(Sonic3kAnimationIds.ROLL);

        state.captured = true;
        placePlayerOnOrbit(player, state);
    }

    private void placePlayerOnOrbit(AbstractPlayableSprite player, PlayerState state) {
        int radius = (TrigLookupTable.cosHex(state.angleA & 0xFF) * ORBIT_RADIUS_SCALE) >> 16;
        int xOffset = (radius * TrigLookupTable.sinHex(state.angleB & 0xFF)) >> 8;
        int yOffset = (radius * TrigLookupTable.cosHex(state.angleB & 0xFF)) >> 8;

        setPlayerCenterPosition(player, spawn.x() - xOffset, spawn.y() + yOffset);

        if (state.angleA < 0) {
            setRenderPriority(player, 5, false);
        } else {
            setRenderPriority(player, RenderPriority.PLAYER_DEFAULT, true);
        }

        state.angleA = (byte) ((state.angleA + 4) & 0xFF);
    }

    private void releasePlayer(AbstractPlayableSprite player, PlayerState state,
                               int frameCounter, boolean launched) {
        if (launched) {
            int combinedAngle = buildReleaseAngle(state.angleA);
            int launchRadius = TrigLookupTable.sinHex(combinedAngle) * RELEASE_MAGNITUDE;
            int xVelocity = (TrigLookupTable.sinHex(state.angleB & 0xFF) * launchRadius) >> 8;
            int yVelocity = -((TrigLookupTable.cosHex(state.angleB & 0xFF) * launchRadius) >> 8);
            player.setXSpeed((short) xVelocity);
            player.setYSpeed((short) yVelocity);
        }

        player.releaseFromObjectControl(frameCounter);
        player.setControlLocked(false);
        player.setAir(true);
        player.setOnObject(false);
        if (!player.getRolling()) {
            player.setRolling(true);
            player.setY((short) (player.getY() + player.getRollHeightAdjustment()));
        }
        player.setRollingJump(false);
        player.setJumping(false);
        player.setPinballMode(true);
        player.setAnimationId(Sonic3kAnimationIds.ROLL);
        player.setFlipAngle(0);
        player.setDoubleJumpFlag(0);
        restoreReleasedPlayerPriority(player);

        state.captured = false;
        state.cooldownFrames = RELEASE_COOLDOWN;
    }

    private int buildReleaseAngle(int angleA) {
        int angle = angleA & 0xFF;
        int highNibble = RELEASE_TABLE[(angle >>> 4) & 0x0F];
        return ((angle & 0x0F) | highNibble) & 0xFF;
    }

    private void setPlayerCenterPosition(AbstractPlayableSprite player, int centerX, int centerY) {
        player.setX((short) (centerX - (player.getWidth() / 2)));
        player.setY((short) (centerY - (player.getHeight() / 2)));
    }

    private void restoreReleasedPlayerPriority(AbstractPlayableSprite player) {
        setRenderPriority(player, RenderPriority.PLAYER_DEFAULT, true);
    }

    private void setRenderPriority(AbstractPlayableSprite player, int bucket, boolean highPriority) {
        int clampedBucket = RenderPriority.clamp(bucket);
        boolean changed = player.getPriorityBucket() != clampedBucket
                || player.isHighPriority() != highPriority;
        player.setPriorityBucket(clampedBucket);
        player.setHighPriority(highPriority);
        if (changed) {
            services().spriteManager().invalidateRenderBuckets();
        }
    }

    private void playSfx(Sonic3kSfx sfx) {
        try {
            services().playSfx(sfx.id);
        } catch (Exception e) {
            // Keep gameplay logic independent from audio state.
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(5);
    }

    @Override
    public boolean isHighPriority() {
        return false;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.PACHINKO_F_ITEM);
        if (renderer == null) {
            return;
        }
        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
        renderer.drawFrameIndex(0, spawn.x(), spawn.y(), hFlip, vFlip);
    }

    private static final class PlayerState {
        private boolean captured;
        private int cooldownFrames;
        private int angleA;
        private int angleB;
    }
}
