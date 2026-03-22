package com.openggf.game.sonic1.objects.bosses;

import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.level.objects.boss.BossExplosionObjectInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Object 0x86 — FZ Plasma Ball Launcher (generator portion).
 * ROM: _incObj/86 FZ Plasma Ball Launcher.asm (routines 0-6)
 *
 * Fixed position at boss_fz_x + $138, boss_fz_y + $2C.
 * State machine:
 *   Routine 2 (Generator): Wait for activation from boss, or become explosion if defeated
 *   Routine 4 (MakeBalls): Spawn 4 plasma balls with calculated positions
 *   Routine 6 (Wait): When all balls done (objoff_38 == 0), signal parent completion
 *
 * SolidObject params: d1=$13, d2=8, d3=$11
 */
public class FZPlasmaLauncher extends AbstractBossChild implements SolidObjectProvider {

    private static final int LAUNCHER_X = Sonic1Constants.BOSS_FZ_X + 0x138;
    private static final int LAUNCHER_Y = Sonic1Constants.BOSS_FZ_Y + 0x2C;

    // SolidObject params: d1=$13, d2=8, d3=$11
    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x13, 8, 0x11);


    // State: 0 = idle (Generator), 1 = spawning balls, 2 = waiting for balls
    private int launcherState;

    // Track active plasma balls
    private int activeBallCount; // objoff_38
    private boolean activated;   // objoff_29

    // Animation frame (0 = red, 1 = white, 2-3 = sparking)
    private int animFrame;
    private int animTimer;

    // Balls spawned tracking
    private final List<FZPlasmaBall> activeBalls = new ArrayList<>();
    private boolean explodedOnDefeat;

    public FZPlasmaLauncher(Sonic1FZBossInstance parent) {
        super(parent, "FZ Plasma Launcher", 3, Sonic1ObjectIds.BOSS_PLASMA);
        
        this.currentX = LAUNCHER_X;
        this.currentY = LAUNCHER_Y;
        this.launcherState = 0;
        this.activated = false;
        this.activeBallCount = 0;
        this.animFrame = 0;
        this.animTimer = 0;
        this.explodedOnDefeat = false;
    }

    /**
     * Called by the boss to activate the plasma launcher for a round.
     * ROM: objoff_29 set to non-zero
     */
    public void activateForBoss() {
        activated = true;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!beginUpdate(frameCounter)) return;

        Sonic1FZBossInstance fzParent = (Sonic1FZBossInstance) parent;

        switch (launcherState) {
            case 0 -> updateGenerator(fzParent);
            case 1 -> updateMakeBalls(fzParent);
            case 2 -> updateWaitForBalls(fzParent);
        }

        // Update animation
        updateAnimation();

        updateDynamicSpawn();
    }

    /**
     * Routine 2 (BossPlasma_Generator): Wait for boss activation.
     * ROM: Check if boss is in defeat phase (objoff_34 == 6) -> become explosion.
     * Otherwise wait for objoff_29 activation.
     */
    private void updateGenerator(Sonic1FZBossInstance fzParent) {
        // ROM: cmpi.b #6,objoff_34(a1) — if boss defeated, become explosion
        if (fzParent.isBossDefeated()) {
            if (!explodedOnDefeat) {
                ObjectRenderManager renderManager = services().renderManager();
                if (renderManager != null && services().objectManager() != null) {
                    services().objectManager().addDynamicObject(
                            new BossExplosionObjectInstance(currentX, currentY, renderManager, Sonic1Sfx.BOSS_EXPLOSION.id));
                }
                explodedOnDefeat = true;
            }
            setDestroyed(true);
            return;
        }

        animFrame = 0; // red (Ani_PLaunch anim 0)

        if (activated) {
            activated = false;
            launcherState = 1;
            animFrame = 0; // Will switch to sparking in MakeBalls
        }
    }

    /**
     * Routine 4 (BossPlasma_MakeBalls): Spawn 4 plasma balls.
     * ROM: Target X = boss_fz_x + $128 + (ballIndex * -$4F) + random($1F) - $10
     */
    private void updateMakeBalls(Sonic1FZBossInstance fzParent) {
        // Spawn 4 balls
        activeBalls.clear();
        activeBallCount = 4;
        var objectManager = services().objectManager();

        for (int i = 0; i < 4; i++) {
            // ROM: Target X calculation
            int random = ThreadLocalRandom.current().nextInt(0x10000);
            int targetX = Sonic1Constants.BOSS_FZ_X + 0x128 + (i * -0x4F);
            targetX += (random & 0x1F) - 0x10;

            FZPlasmaBall ball = new FZPlasmaBall(this, LAUNCHER_X, LAUNCHER_Y, targetX);
            activeBalls.add(ball);
            if (objectManager != null) {
                objectManager.addDynamicObject(ball);
            }
        }

        launcherState = 2; // Wait for balls to finish
        animFrame = 1; // ROM: move.b #1,obAnim — sparking animation
    }

    /**
     * Routine 6 (loc_1A962): Wait for all balls to complete.
     * ROM: tst.w objoff_38 — when all done, signal parent.
     */
    private void updateWaitForBalls(Sonic1FZBossInstance fzParent) {
        // Check if all balls are done
        activeBalls.removeIf(FZPlasmaBall::isDestroyed);
        activeBallCount = activeBalls.size();

        if (activeBallCount == 0) {
            // ROM: Signal parent completion
            fzParent.onPlasmaComplete();
            launcherState = 0; // Back to generator state
        }

        // ROM: White sparking animation while waiting
        animFrame = 2; // Ani_PLaunch anim 2 (white sparking)
    }

    /**
     * Called by a plasma ball when it's destroyed.
     */
    public void onBallDestroyed() {
        activeBallCount--;
    }

    private void updateAnimation() {
        animTimer++;
        // Simple animation cycling based on state
        if (launcherState >= 1) {
            // Sparking animation — cycle frames
            if ((animTimer & 3) == 0) {
                animFrame = (animFrame == 2) ? 3 : 2;
            }
        }
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) return;

        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.FZ_PLASMA_LAUNCHER);
        if (renderer == null || !renderer.isReady()) return;

        int frame = Math.min(animFrame, 3);
        renderer.drawFrameIndex(frame, currentX, currentY, false, false);
    }

    @Override
    public int getPriorityBucket() {
        return 3;
    }
}
