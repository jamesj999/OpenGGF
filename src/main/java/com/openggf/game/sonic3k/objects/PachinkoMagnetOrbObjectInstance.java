package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0xEC - Pachinko magnet orb.
 *
 * <p>ROM reference: {@code Obj_PachinkoMagnetOrb}. The orb captures the player when they
 * enter its catch box, rotates them around the orb while left/right adjust the orbit,
 * and launches them when jump is pressed.
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

    private AbstractPlayableSprite capturedPlayer;
    private int cooldownFrames;
    private int angleA;
    private int angleB;

    public PachinkoMagnetOrbObjectInstance(ObjectSpawn spawn) {
        super(spawn, "PachinkoMagnetOrb");
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (!(playerEntity instanceof AbstractPlayableSprite player)) {
            return;
        }

        if (capturedPlayer != null) {
            updateCapturedPlayer(player, frameCounter);
            return;
        }

        if (cooldownFrames > 0) {
            cooldownFrames--;
            if (cooldownFrames == 0) {
                restorePlayerPriority(player);
            }
        }

        if (player.isDebugMode()
                || player.getDead()
                || player.isHurt()
                || player.wasRecentlyObjectControlled(frameCounter, 1)) {
            return;
        }

        int dx = player.getX() - spawn.x();
        int dy = player.getY() - spawn.y();
        if (dx < -CATCH_RANGE || dx >= CATCH_RANGE || dy < -CATCH_RANGE || dy >= CATCH_RANGE) {
            return;
        }
        if (player.isObjectControlled() || player.isControlLocked()) {
            return;
        }

        capturePlayer(player);
    }

    private void updateCapturedPlayer(AbstractPlayableSprite player, int frameCounter) {
        if (player != capturedPlayer) {
            releasePlayer(capturedPlayer, frameCounter, false);
            return;
        }

        if (player.isDebugMode() || player.getDead() || player.isHurt() || !player.isObjectControlled()) {
            releasePlayer(player, frameCounter, false);
            return;
        }

        if (player.isJumpPressed()) {
            releasePlayer(player, frameCounter, true);
            return;
        }

        if (player.isLeftPressed()) {
            angleB = (angleB - 1) & 0xFF;
        }
        if (player.isRightPressed()) {
            angleB = (angleB + 1) & 0xFF;
        }

        int previousX = player.getX();
        int previousY = player.getY();
        placePlayerOnOrbit(player);
        player.setXSpeed((short) (-(player.getX() - previousX) << 8));
        player.setYSpeed((short) (-(player.getY() - previousY) << 8));
        player.setGSpeed((short) 0x0800);
        player.setAir(true);

        if ((frameCounter & (HOVER_SFX_PERIOD - 1)) == 0) {
            playSfx(Sonic3kSfx.HOVERPAD);
        }
    }

    private void capturePlayer(AbstractPlayableSprite player) {
        int angle = TrigLookupTable.calcAngle((short) (player.getX() - spawn.x()),
                (short) (player.getY() - spawn.y()));
        if ((angle & 0x80) != 0) {
            angleA = -0x80;
            angle = (angle + 0x80) & 0xFF;
        } else {
            angleA = 0;
        }
        angleB = (angle - 0x40) & 0xFF;

        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0x0800);
        player.setControlLocked(true);
        player.setObjectControlled(true);
        player.setAir(true);
        player.setOnObject(false);

        capturedPlayer = player;
        placePlayerOnOrbit(player);
    }

    private void placePlayerOnOrbit(AbstractPlayableSprite player) {
        int radius = (TrigLookupTable.cosHex(angleA & 0xFF) * ORBIT_RADIUS_SCALE) >> 16;
        int xOffset = (radius * TrigLookupTable.sinHex(angleB & 0xFF)) >> 8;
        int yOffset = (radius * TrigLookupTable.cosHex(angleB & 0xFF)) >> 8;

        player.setX((short) (spawn.x() - xOffset));
        player.setY((short) (spawn.y() + yOffset));

        if (angleA < 0) {
            player.setPriorityBucket(5);
            player.setHighPriority(false);
        } else {
            player.setPriorityBucket(RenderPriority.PLAYER_DEFAULT);
            player.setHighPriority(true);
        }

        angleA = (byte) ((angleA + 4) & 0xFF);
    }

    private void releasePlayer(AbstractPlayableSprite player, int frameCounter, boolean launched) {
        if (player == null) {
            return;
        }

        if (launched) {
            int combinedAngle = buildReleaseAngle();
            int launchRadius = TrigLookupTable.sinHex(combinedAngle) * RELEASE_MAGNITUDE;
            int xVelocity = (TrigLookupTable.sinHex(angleB & 0xFF) * launchRadius) >> 8;
            int yVelocity = -((TrigLookupTable.cosHex(angleB & 0xFF) * launchRadius) >> 8);
            player.setXSpeed((short) xVelocity);
            player.setYSpeed((short) yVelocity);
        }

        player.releaseFromObjectControl(frameCounter);
        player.setControlLocked(false);
        player.setAir(true);
        player.setOnObject(false);
        player.setRolling(true);
        player.setPinballMode(true);
        restorePlayerPriority(player);

        cooldownFrames = RELEASE_COOLDOWN;
        capturedPlayer = null;
    }

    private int buildReleaseAngle() {
        int angle = angleA & 0xFF;
        int highNibble = RELEASE_TABLE[(angle >>> 4) & 0x0F];
        return ((angle & 0x0F) | highNibble) & 0xFF;
    }

    private void restorePlayerPriority(AbstractPlayableSprite player) {
        player.setPriorityBucket(RenderPriority.PLAYER_DEFAULT);
        player.setHighPriority(true);
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
        return RenderPriority.clamp(4);
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
}
