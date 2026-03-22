package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * AIZ miniboss napalm projectile (Knuckles fight only).
 *
 * ROM: Fireball that launches upward off-screen, repositions at screen top,
 * drops downward, and explodes on floor contact.
 *
 * State machine: LAUNCH -> REPOSITION -> DROP -> EXPLODE -> destroyed
 */
public class AizMinibossNapalmProjectile extends AbstractObjectInstance {

    /** Upward velocity (fixed-point 8.8, negative = up). */
    private static final int LAUNCH_VEL = -0x400;
    /** Downward velocity (fixed-point 8.8, positive = down). */
    private static final int DROP_VEL = 0x400;
    /** Pixels above camera top to use as off-screen threshold. */
    private static final int OFFSCREEN_MARGIN = 0x20;
    /** Approximate screen bottom offset from camera Y. */
    private static final int FLOOR_OFFSET = 0xE0;
    /** Lifetime in frames before self-destruct. */
    private static final int MAX_LIFETIME = 0x9F;
    /** Explode animation duration in frames. */
    private static final int EXPLODE_TIME = 8;

    private enum State {
        LAUNCH,
        REPOSITION,
        DROP,
        EXPLODE
    }

    private int worldX;
    private int worldY;
    private int yVel;
    private int lifetime = MAX_LIFETIME;
    private int smokeTimer;
    private int explodeTimer;
    private State state = State.LAUNCH;

    /**
     * @param startX world X position (from parent boss)
     * @param startY world Y position (from parent boss)
     */
    public AizMinibossNapalmProjectile(int startX, int startY) {
        super(new ObjectSpawn(startX, startY, 0x91, 0, 0, false, 0), "AIZNapalmProjectile");
        this.worldX = startX;
        this.worldY = startY;
        this.yVel = LAUNCH_VEL;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        lifetime--;
        if (lifetime <= 0) {
            setDestroyed(true);
            return;
        }

        switch (state) {
            case LAUNCH -> updateLaunch();
            case REPOSITION -> updateReposition();
            case DROP -> updateDrop();
            case EXPLODE -> updateExplode();
        }
    }

    private void updateLaunch() {
        // Move upward (fixed-point: shift right 8 to get pixel delta)
        worldY += yVel >> 8;

        Camera camera = GameServices.camera();
        if (worldY <= camera.getY() - OFFSCREEN_MARGIN) {
            state = State.REPOSITION;
        }
    }

    private void updateReposition() {
        Camera camera = GameServices.camera();
        worldY = camera.getY() - OFFSCREEN_MARGIN;
        yVel = DROP_VEL;
        services().playSfx(Sonic3kSfx.MISSILE_THROW.id);
        state = State.DROP;
    }

    private void updateDrop() {
        worldY += yVel >> 8;
        smokeTimer++;

        Camera camera = GameServices.camera();
        if (worldY >= camera.getY() + FLOOR_OFFSET) {
            services().playSfx(Sonic3kSfx.MISSILE_EXPLODE.id);
            explodeTimer = EXPLODE_TIME;
            state = State.EXPLODE;
        }
    }

    private void updateExplode() {
        explodeTimer--;
        if (explodeTimer <= 0) {
            setDestroyed(true);
        }
    }

    @Override
    public int getX() {
        return worldX;
    }

    @Override
    public int getY() {
        return worldY;
    }

    @Override
    public int getPriorityBucket() {
        return 4;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Art loading is handled separately — stub for now.
    }
}
