package com.openggf.game.sonic3k.objects;

import com.openggf.audio.GameSound;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x4A in zone 0x14 - Pachinko round bumper.
 *
 * <p>This shares the same core bumper physics as the CNZ bumper path in the ROM, but
 * uses the Pachinko-specific mappings and vertical off-screen despawn behavior.
 */
public class PachinkoBumperObjectInstance extends AbstractObjectInstance {

    private static final int BOUNCE_VELOCITY = 0x700;
    private static final int COLLISION_HALF_WIDTH = 8;
    private static final int COLLISION_HALF_HEIGHT = 8;
    private static final int ANIM_DURATION = 8;
    private static final int BOUNCE_COOLDOWN = 8;

    private int animFrame;
    private int animTimer;
    private int bounceCooldown;

    public PachinkoBumperObjectInstance(ObjectSpawn spawn) {
        super(spawn, "PachinkoBumper");
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (animTimer > 0) {
            animTimer--;
            if (animTimer == 0) {
                animFrame = 0;
            }
        }
        if (bounceCooldown > 0) {
            bounceCooldown--;
        }

        if (playerEntity instanceof AbstractPlayableSprite player
                && !player.isHurt()
                && !player.getDead()
                && bounceCooldown == 0
                && checkCollision(player)) {
            applyBounce(player, frameCounter);
        }
    }

    private boolean checkCollision(AbstractPlayableSprite player) {
        int dx = Math.abs(player.getCentreX() - spawn.x());
        int dy = Math.abs(player.getCentreY() - spawn.y());
        return dx < (COLLISION_HALF_WIDTH + 8)
                && dy < (COLLISION_HALF_HEIGHT + player.getYRadius());
    }

    private void applyBounce(AbstractPlayableSprite player, int frameCounter) {
        int dx = spawn.x() - player.getCentreX();
        int dy = spawn.y() - player.getCentreY();
        int angle = (TrigLookupTable.calcAngle((short) dx, (short) dy) + (frameCounter & 3)) & 0xFF;

        int cosVal = TrigLookupTable.cosHex(angle);
        int sinVal = TrigLookupTable.sinHex(angle);
        player.setXSpeed((short) (cosVal * -BOUNCE_VELOCITY >> 8));
        player.setYSpeed((short) (sinVal * -BOUNCE_VELOCITY >> 8));
        player.setAir(true);
        player.setPushing(false);
        player.setJumping(false);
        player.setGSpeed((short) 0);

        animFrame = 1;
        animTimer = ANIM_DURATION;
        bounceCooldown = BOUNCE_COOLDOWN;

        try {
            services().playSfx(GameSound.BUMPER);
        } catch (Exception e) {
            // Keep gameplay logic independent from audio state.
        }
        services().gameState().addScore(10);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(1);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.PACHINKO_BUMPER);
        if (renderer == null) {
            return;
        }
        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
        renderer.drawFrameIndex(animFrame, spawn.x(), spawn.y(), hFlip, vFlip);
    }
}
