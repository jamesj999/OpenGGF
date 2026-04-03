package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;
import java.util.logging.Logger;

/**
 * AIZ end boss flamethrower projectile (ROM: loc_69844).
 *
 * <p>Spawned by each propeller at full extension. Animates flame burst
 * then spawns a bomb projectile that falls with gravity.
 *
 * <p>ROM attributes: word_69CEC — priority $100, size 8x4, frame $B,
 * collision $97. Shield reaction: fire shield immune (bit 4).
 *
 * <p>Animation selects by angle:
 * - Angles 0/$C: byte_69DC9 (frames 7,8,9,$A)
 * - Angles 4/8: byte_69DF3 (frames $B,$C,$D,$E)
 */
public class AizEndBossFlameChild extends AbstractObjectInstance implements TouchResponseProvider {
    private static final Logger LOG = Logger.getLogger(AizEndBossFlameChild.class.getName());

    private static final int COLLISION_FLAGS = 0x97; // Hurts player, size index $17
    private static final int FLAME_DURATION = 40;    // Approximate flame animation duration

    private final AizEndBossInstance boss;
    private final int angle;
    private int currentX;
    private int currentY;
    private int animTimer;
    private int mappingFrame;
    private boolean faceRight;

    public AizEndBossFlameChild(AizEndBossInstance boss,
                                AizEndBossPropellerChild propeller, int angle) {
        super(null, "AIZEndBossFlame");
        this.boss = boss;
        this.angle = angle;
        this.currentX = propeller.getX();
        this.currentY = propeller.getY();
        this.animTimer = 0;
        this.faceRight = (angle < 8);

        // Select initial frame based on angle
        boolean vertical = (angle == 4 || angle == 8);
        this.mappingFrame = vertical ? 0x0B : 0x07;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (isDestroyed()) return;

        // Check if parent boss is defeated
        if (boss.isDefeatSignal()) {
            setDestroyed(true);
            return;
        }

        animTimer++;

        // Animate flame burst (ROM: byte_69DC9 / byte_69DF3)
        boolean vertical = (angle == 4 || angle == 8);
        if (vertical) {
            // Frames: $B, $C, $D, $E cycling
            int phase = (animTimer / 4) % 4;
            mappingFrame = 0x0B + phase;
        } else {
            // Frames: 7, 8, 9, $A cycling
            int phase = (animTimer / 4) % 4;
            mappingFrame = 0x07 + phase;
        }

        // After flame duration, spawn bomb and delete (ROM: loc_698BC)
        if (animTimer >= FLAME_DURATION) {
            spawnBomb();
            setDestroyed(true);
        }
    }

    /** ROM: ChildObjDat_69D56 — Spawn bomb projectile at flame position. */
    private void spawnBomb() {
        ObjectManager objectManager = services().objectManager();
        if (objectManager == null) return;

        AizEndBossBombChild bomb = new AizEndBossBombChild(boss, currentX, currentY, angle);
        objectManager.addDynamicObject(bomb);
    }

    @Override
    public int getCollisionFlags() {
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public int getX() { return currentX; }

    @Override
    public int getY() { return currentY; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;

        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) return;

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.AIZ_END_BOSS);
        if (renderer == null || !renderer.isReady()) return;

        renderer.drawFrameIndex(mappingFrame, currentX, currentY, faceRight, false);
    }

    @Override
    public boolean isHighPriority() { return false; }

    @Override
    public int getPriorityBucket() { return 2; }
}
