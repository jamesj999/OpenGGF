package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Barrel-fired shot used by the AIZ miniboss.
 *
 * ROM references:
 * - sub_6885A / loc_688B0 (projectile setup)
 * - loc_68C96 / loc_68D5E / loc_68D70 (hazardous variant + floor explode)
 * - sub_68EE4 (camera-relative top spawn X selection)
 */
public class AizMinibossBarrelShotChild extends AbstractObjectInstance implements TouchResponseProvider {
    private static final int COLLISION_FLAGS_HAZARD = 0x98;
    private static final int SHIELD_REACTION_BOUNCE = 1 << 3;
    private static final int FRAME_RISE_A = 0x0C;
    private static final int FRAME_RISE_B = 0x0D;
    private static final int PROJECTILE_PALETTE = 0;

    private static final int[] SELECT_TABLE_NORMAL = {
            2, 3, 4, 0, 0, 2, 4, 0, 1, 3, 4, 0, 0, 1, 4, 0
    };
    private static final int[] SELECT_TABLE_FLIPPED = {
            3, 2, 0, 0, 4, 3, 1, 0, 4, 2, 0, 0, 3, 2, 1, 0
    };
    private static final int[] X_COLUMNS_NORMAL = {0x24, 0x4C, 0x74, 0x9C, 0xC4};
    private static final int[] X_COLUMNS_FLIPPED = {0x7C, 0xA4, 0xCC, 0xF4, 0x11C};

    enum Mode {
        SIMPLE,
        ADVANCED_NON_COLLIDING,
        ADVANCED_COLLIDING
    }

    private enum State {
        PRELAUNCH,
        PRE_DROP_WAIT,
        TOP_DROP
    }

    private final AbstractBossInstance parent;
    private final int barrelIndex;
    private final Mode mode;

    private int currentX;
    private int currentY;
    private int xFixed;
    private int yFixed;
    private int xVel;
    private int yVel;
    private int frame;
    private int animTimer;
    private int timer;
    private State state;

    public AizMinibossBarrelShotChild(AbstractBossInstance parent,
                                      int barrelIndex,
                                      int x,
                                      int y,
                                      Mode mode) {
        super(new ObjectSpawn(x, y, 0x90, barrelIndex, 0, false, 0), "AIZMinibossBarrelShot");
        this.parent = parent;
        this.barrelIndex = barrelIndex & 0xFF;
        this.mode = mode;

        this.currentX = x;
        this.currentY = y;
        this.xFixed = x << 16;
        this.yFixed = y << 16;

        // sub_6885A equivalent
        this.xVel = 0;
        this.yVel = -0x400;
        this.frame = FRAME_RISE_A;
        this.animTimer = 2;
        this.timer = 0x60;
        this.state = State.PRELAUNCH;

        AudioManager.getInstance().playSfx(Sonic3kSfx.PROJECTILE.id);
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (parent == null || parent.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        switch (state) {
            case PRELAUNCH -> updatePrelaunch();
            case PRE_DROP_WAIT -> updatePreDropWait();
            case TOP_DROP -> updateTopDrop();
        }
    }

    private void updatePrelaunch() {
        move();
        animateRise();

        timer--;
        if (timer >= 0) {
            return;
        }

        if (mode == Mode.SIMPLE) {
            setDestroyed(true);
            return;
        }

        timer = 8;
        state = State.PRE_DROP_WAIT;
    }

    private void updatePreDropWait() {
        timer--;
        if (timer >= 0) {
            return;
        }
        enterTopDropPhase();
        state = State.TOP_DROP;
    }

    private void updateTopDrop() {
        move();
        animateRise();

        if (!isOnScreen(128)) {
            setDestroyed(true);
            return;
        }

        if (mode == Mode.SIMPLE) {
            return;
        }

        int floorY = Camera.getInstance().getY() + 0xA0;
        if (currentY >= floorY) {
            AudioManager.getInstance().playSfx(Sonic3kSfx.MISSILE_EXPLODE.id);
            setDestroyed(true);
        }
    }

    private void enterTopDropPhase() {
        int timerCounter = (parent.getCustomFlag(0x39) + 4) & 0xFF;
        parent.setCustomFlag(0x39, timerCounter);

        int index = ((barrelIndex >> 1) + (timerCounter & 0x0C)) & 0x0F;
        boolean hFlip = (parent.getState().renderFlags & 1) != 0;
        int selector = hFlip ? SELECT_TABLE_FLIPPED[index] : SELECT_TABLE_NORMAL[index];
        int xOffset = hFlip ? X_COLUMNS_FLIPPED[selector] : X_COLUMNS_NORMAL[selector];

        currentX = Camera.getInstance().getX() + xOffset;
        currentY = Camera.getInstance().getY() - 0x20;
        xFixed = currentX << 16;
        yFixed = currentY << 16;

        yVel = 0x400;
        xVel = 0;
        animTimer = 2;
        AudioManager.getInstance().playSfx(Sonic3kSfx.MISSILE_THROW.id);
    }

    private void move() {
        xFixed += (xVel << 8);
        yFixed += (yVel << 8);
        currentX = xFixed >> 16;
        currentY = yFixed >> 16;
    }

    private void animateRise() {
        if (--animTimer > 0) {
            return;
        }
        animTimer = 2;
        frame = (frame == FRAME_RISE_A) ? FRAME_RISE_B : FRAME_RISE_A;
    }

    @Override
    public int getCollisionFlags() {
        if (mode != Mode.ADVANCED_COLLIDING || state != State.TOP_DROP || isDestroyed()) {
            return 0;
        }
        return COLLISION_FLAGS_HAZARD;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public int getShieldReactionFlags() {
        return SHIELD_REACTION_BOUNCE;
    }

    @Override
    public boolean onShieldDeflect(AbstractPlayableSprite player) {
        setDestroyed(true);
        return true;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(
                currentX,
                currentY,
                spawn.objectId(),
                spawn.subtype(),
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
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
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager rm = LevelManager.getInstance().getObjectRenderManager();
        if (rm == null) {
            return;
        }
        PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(frame, currentX, currentY, false, false, PROJECTILE_PALETTE);
    }

    @Override
    public int getPriorityBucket() {
        return mode == Mode.SIMPLE ? 1 : 2;
    }
}
