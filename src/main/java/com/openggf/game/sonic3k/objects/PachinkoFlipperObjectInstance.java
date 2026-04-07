package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0xE7 - Pachinko flipper.
 *
 * <p>ROM reference: {@code Obj_PachinkoFlipper}. This is a sloped top-solid flipper
 * that locks the player into rolling while standing on it, accelerates them along
 * the surface, and launches them when jump is pressed.
 */
public class PachinkoFlipperObjectInstance extends AbstractObjectInstance
        implements SlopedSolidProvider, SolidObjectListener {

    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x20, 0x1C, 0x1D);

    // ROM: byte_49E5A
    private static final byte[] SLOPE_DATA = {
            -4, -4, -4, -4, -4, -4, -4, -4,
            -4, -5, -6, -7, -8, -9, -10, -11,
            -12, -13, -14, -15, -16, -17, -18, -19,
            -20, -21, -22, -23, -24, -25, -26, -27
    };

    // ROM idle anim frame: byte_49E7E = delay 0x1F, frame 4.
    private static final int IDLE_FRAME = 4;

    // ROM trigger anim: byte_49E81 = 3,2,1,0,1,2,3 with 1-frame delay.
    private static final int[] TRIGGER_SEQUENCE = {3, 2, 1, 0, 1, 2, 3};

    private static final int SURFACE_ACCEL = 0x18;
    private static final int SURFACE_ACCEL_FLIPPED = -0x19; // ROM uses NOT.W on 0x18, yielding -25.

    private AbstractPlayableSprite lockedPlayer;
    private boolean contactThisFrame;
    private int triggerFrame = -1;

    public PachinkoFlipperObjectInstance(ObjectSpawn spawn) {
        super(spawn, "PachinkoFlipper");
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public byte[] getSlopeData() {
        return SLOPE_DATA;
    }

    @Override
    public boolean isSlopeFlipped() {
        return isFlippedHorizontal();
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        if (!(playerEntity instanceof AbstractPlayableSprite player)) {
            return;
        }
        if (!contact.standing()) {
            return;
        }

        contactThisFrame = true;

        if (player.isDebugMode()) {
            releaseLockedPlayer();
            return;
        }

        lockPlayer(player);
        if (player.isJumpPressed()) {
            launchPlayer(player);
        } else {
            applySurfaceAcceleration(player);
        }
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (lockedPlayer != null && (!contactThisFrame || lockedPlayer.getAir() || lockedPlayer.isDebugMode())) {
            releaseLockedPlayer();
        }
        contactThisFrame = false;

        if (triggerFrame >= 0) {
            triggerFrame++;
            if (triggerFrame >= TRIGGER_SEQUENCE.length) {
                triggerFrame = -1;
            }
        }
    }

    private void lockPlayer(AbstractPlayableSprite player) {
        lockedPlayer = player;
        player.setControlLocked(true);
        player.setPinballMode(true);
        if (!player.getRolling()) {
            player.setRolling(true);
            player.applyRollingRadii(false);
            player.setY((short) (player.getY() + player.getRollHeightAdjustment()));
        }
    }

    private void applySurfaceAcceleration(AbstractPlayableSprite player) {
        int accel = isFlippedHorizontal() ? SURFACE_ACCEL_FLIPPED : SURFACE_ACCEL;
        player.setGSpeed((short) (player.getGSpeed() + accel));
        player.setDirection(accel < 0 ? Direction.LEFT : Direction.RIGHT);
    }

    private void launchPlayer(AbstractPlayableSprite player) {
        int launchDistance = player.getX() - spawn.x();
        if (isFlippedHorizontal()) {
            launchDistance = -launchDistance;
        }

        launchDistance += 0x20;
        int velocityMagnitude = -((launchDistance << 5) + 0x800);
        int angle = ((launchDistance >> 2) + 0x40) & 0xFF;

        int sinValue = TrigLookupTable.sinHex(angle);
        int cosValue = TrigLookupTable.cosHex(angle);
        int yVelocity = (sinValue * velocityMagnitude) >> 8;
        int xVelocity = (cosValue * velocityMagnitude) >> 8;
        if (isFlippedHorizontal()) {
            xVelocity = -xVelocity;
        }

        player.setYSpeed((short) yVelocity);
        player.setXSpeed((short) xVelocity);
        player.setAir(true);
        player.setOnObject(false);
        player.setPushing(false);
        player.setGSpeed((short) 0);
        player.setControlLocked(false);
        player.setPinballMode(false);
        player.setDirection(xVelocity < 0 ? Direction.LEFT : Direction.RIGHT);

        var objectManager = services().objectManager();
        if (objectManager != null) {
            objectManager.clearRidingObject(player);
        }

        lockedPlayer = null;
        triggerFrame = 0;
        playFlipperSfx();
    }

    private void releaseLockedPlayer() {
        if (lockedPlayer == null) {
            return;
        }
        lockedPlayer.setControlLocked(false);
        lockedPlayer.setPinballMode(false);
        lockedPlayer = null;
    }

    private void playFlipperSfx() {
        try {
            services().playSfx(Sonic3kSfx.FLIPPER.id);
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
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.PACHINKO_FLIPPER);
        if (renderer == null) {
            return;
        }
        boolean hFlip = isFlippedHorizontal();
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
        int frame = triggerFrame >= 0 ? TRIGGER_SEQUENCE[triggerFrame] : IDLE_FRAME;
        renderer.drawFrameIndex(frame, spawn.x(), spawn.y(), hFlip, vFlip);
    }

    private boolean isFlippedHorizontal() {
        return (spawn.renderFlags() & 0x1) != 0;
    }
}
