package com.openggf.game.sonic1.objects.badniks;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Motobug exhaust smoke (child of Motobug 0x40).
 * Animation-only object with no collision. Uses the same Motobug art sheet
 * with smoke frames (3-5) and a blank frame (6).
 * <p>
 * In the ROM, smoke is a Motobug object with obAnim=2, which causes it to
 * skip straight to Moto_Animate (routine 4). The smoke animation script
 * (anim 2) plays frames 3,6,3,6,4,6,4,6,4,6,5 at speed 1, then triggers
 * afRoutine which advances to Moto_Delete (routine 6).
 * <p>
 * Based on docs/s1disasm/_incObj/40 Moto Bug.asm and _anim/Moto Bug.asm.
 */
public class Sonic1MotobugSmokeInstance extends AbstractObjectInstance {

    // Smoke animation sequence from _anim/Moto Bug.asm .smoke:
    // dc.b 1, 3, 6, 3, 6, 4, 6, 4, 6, 4, 6, 5, afRoutine
    // Speed 1 = each frame displayed for 2 ticks
    private static final int[] SMOKE_FRAMES = {3, 6, 3, 6, 4, 6, 4, 6, 4, 6, 5};
    private static final int SMOKE_SPEED = 2; // speed byte 1 = duration of 2 ticks per frame

    private final int posX;
    private final int posY;
    private final boolean facingLeft;
    private final LevelManager levelManager;
    private int animTimer;
    private int animStep;
    private boolean finished;

    public Sonic1MotobugSmokeInstance(int x, int y, boolean facingLeft, LevelManager levelManager) {
        super(new ObjectSpawn(x, y, 0x40, 0, 0, false, 0), "MotobugSmoke");
        this.posX = x;
        this.posY = y;
        this.facingLeft = facingLeft;
        this.levelManager = levelManager;
        this.animTimer = 0;
        this.animStep = 0;
        this.finished = false;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (finished) {
            return;
        }

        animTimer++;
        if (animTimer >= SMOKE_SPEED) {
            animTimer = 0;
            animStep++;
            if (animStep >= SMOKE_FRAMES.length) {
                // afRoutine: advance routine → Moto_Delete
                finished = true;
                setDestroyed(true);
            }
        }
    }

    @Override
    public boolean isPersistent() {
        return !finished;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4); // obPriority = 4 (same as parent)
    }

    @Override
    public int getX() {
        return posX;
    }

    @Override
    public int getY() {
        return posY;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (finished || animStep >= SMOKE_FRAMES.length) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.MOTOBUG);
        if (renderer == null) return;

        int frame = SMOKE_FRAMES[animStep];
        // S1 convention: default sprite art faces left, hFlip = true when facing right
        renderer.drawFrameIndex(frame, posX, posY, !facingLeft, false);
    }
}
