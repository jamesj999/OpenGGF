package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x4B - CNZ triangle bumper.
 *
 * <p>ROM reference: {@code Obj_CNZTriangleBumpers} / {@code sub_329B8} in
 * {@code docs/skdisasm/sonic3k.asm}. The subtype is the horizontal half-width:
 * the ROM stores it at {@code $34(a0)} and stores twice that value at
 * {@code $32(a0)} for the unsigned range check.
 */
public class CnzTriangleBumperObjectInstance extends AbstractObjectInstance {

    private static final int VERTICAL_HALF_HEIGHT = 0x14;
    private static final int BOUNCE_SPEED = 0x800;
    private static final int MOVE_LOCK_FRAMES = 15;

    private final int halfWidth;
    private final int fullWidth;
    private final boolean launchLeft;
    private final boolean launchDown;

    public CnzTriangleBumperObjectInstance(ObjectSpawn spawn) {
        super(spawn, "CNZTriangleBumpers");
        this.halfWidth = spawn.subtype() & 0xFF;
        this.fullWidth = halfWidth << 1;
        this.launchLeft = (spawn.renderFlags() & 0x1) != 0;
        this.launchDown = (spawn.renderFlags() & 0x2) != 0;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (playerEntity instanceof AbstractPlayableSprite player) {
            checkPlayer(player);
        }

        ObjectServices svc = tryServices();
        if (svc == null) {
            return;
        }
        for (PlayableEntity sidekick : svc.sidekicks()) {
            if (sidekick instanceof AbstractPlayableSprite sprite && sidekick != playerEntity) {
                checkPlayer(sprite);
                break;
            }
        }
    }

    private void checkPlayer(AbstractPlayableSprite player) {
        if (player.getDead() || !isInsideRomTouchRegion(player)) {
            return;
        }
        applyBounce(player);
    }

    private boolean isInsideRomTouchRegion(AbstractPlayableSprite player) {
        int dx = player.getCentreX() - spawn.x() + halfWidth;
        if (Integer.compareUnsigned(dx, fullWidth) >= 0) {
            return false;
        }

        int dy = player.getCentreY() - spawn.y() + VERTICAL_HALF_HEIGHT;
        return Integer.compareUnsigned(dy, VERTICAL_HALF_HEIGHT << 1) < 0;
    }

    private void applyBounce(AbstractPlayableSprite player) {
        short xVelocity = (short) (launchLeft ? -BOUNCE_SPEED : BOUNCE_SPEED);
        short yVelocity = (short) (launchDown ? BOUNCE_SPEED : -BOUNCE_SPEED);

        player.setXSpeed(xVelocity);
        player.setYSpeed(yVelocity);
        player.setDirection(launchLeft ? Direction.LEFT : Direction.RIGHT);
        player.setMoveLockTimer(MOVE_LOCK_FRAMES);
        player.setGSpeed(xVelocity);
        if (!player.getRolling()) {
            player.setAnimationId(0);
        }

        if (player.getFlipAngle() == 0) {
            player.setFlipAngle(launchLeft ? -1 : 1);
            player.setAnimationId(0);
            player.setFlipsRemaining(3);
            player.setFlipSpeed(8);
        }

        player.setJumping(false);
        player.setAir(true);
        player.setRollingJump(false);
        player.setPushing(false);

        try {
            services().playSfx(Sonic3kSfx.SMALL_BUMPERS.id);
        } catch (Exception e) {
            // Gameplay state must not depend on audio availability in tests.
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }
}
