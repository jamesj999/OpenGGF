package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;
import java.util.logging.Logger;

/**
 * AIZ end boss propeller child (ROM: loc_69738).
 *
 * <p>Spawned by each arm child. Extends outward when fire signal is active,
 * then spawns flamethrower projectile at full extension. Retracts after firing.
 *
 * <p>State machine (4 routines):
 * <ol start="0">
 *   <li>Init: set attributes (ROM: word_69CE6)</li>
 *   <li>Wait for parent arm's fire signal</li>
 *   <li>Extend animation (step through 4 positions)</li>
 *   <li>Wait/retract delay</li>
 * </ol>
 *
 * <p>Extension positions vary by angle (ROM: byte_69B0E):
 * For angles 0/$C: dx=-$18/dy=8 stepping to dx=-$1C/dy=0
 * For angles 4/8: dx varies with vertical stepping
 */
public class AizEndBossPropellerChild extends AbstractBossChild {
    private static final Logger LOG = Logger.getLogger(AizEndBossPropellerChild.class.getName());

    private static final int ROUTINE_INIT = 0;
    private static final int ROUTINE_WAIT_FIRE = 2;
    private static final int ROUTINE_EXTEND = 4;
    private static final int ROUTINE_WAIT_RETRACT = 6;

    // Extension position tables (ROM: byte_69B0E)
    // Normal (angles 0/$C): child_dx, child_dy, frame per step
    private static final int[][] EXTEND_NORMAL = {
            {-0x18, 8, 5}, {-0x18, 8, 5}, {-0x1C, 0, 4}, {-0x1C, 0, 4}
    };
    // Vertical (angles 4/8)
    private static final int[][] EXTEND_VERTICAL = {
            {-0x18, 8, 5}, {-0x10, 0x10, 6}, {-0x18, 8, 5}, {-0x1C, 0, 4}
    };

    // Bomb velocity per angle (ROM: word_69BD2)
    private static final int[][] BOMB_VELOCITY = {
            {0x300, 0x300},   // angle 0
            {0, 0x400},       // angle 4
            {0, 0x400},       // angle 8
            {-0x300, 0x300},  // angle $C
    };

    private final AizEndBossInstance boss;
    private final AizEndBossArmChild arm;
    private final int subtype;
    private int routine;
    private int mappingFrame;
    private int extendStep;
    private int waitTimer;
    private int fireCount;
    private int childDx;
    private int childDy;

    public AizEndBossPropellerChild(AizEndBossInstance boss, AizEndBossArmChild arm, int subtype) {
        super(boss, "AIZEndBossPropeller", 0x180, 0);
        this.boss = boss;
        this.arm = arm;
        this.subtype = subtype;
        this.routine = ROUTINE_INIT;
        this.mappingFrame = 4;
        this.extendStep = 0;
        this.waitTimer = -1;
        this.fireCount = 0;
        this.childDx = -0x1C;
        this.childDy = 0;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (!beginUpdate(frameCounter)) return;

        // Sync position with arm (then apply child offsets)
        setPosition(arm.getX() + childDx, arm.getY() + childDy);

        if (boss.isDefeatSignal()) {
            setDestroyed(true);
            return;
        }
        if (boss.isHidden()) return;

        switch (routine) {
            case ROUTINE_INIT -> {
                routine = ROUTINE_WAIT_FIRE;
            }
            case ROUTINE_WAIT_FIRE -> {
                // Wait for parent boss's fire signal
                if (boss.isPropellerFireRequested()) {
                    routine = ROUTINE_EXTEND;
                    extendStep = 0;
                    waitTimer = (subtype != 0) ? 0x4F : 0; // ROM: subtype!=0 gets $4F delay

                    // ROM: move.b #1,$3A — fire count
                    fireCount = 1;
                }
            }
            case ROUTINE_EXTEND -> {
                if (waitTimer > 0) {
                    waitTimer--;
                    return;
                }
                // Step through extension positions
                int angleIndex = boss.getAngle() / 4;
                boolean vertical = (angleIndex == 1 || angleIndex == 2);
                int[][] table = vertical ? EXTEND_VERTICAL : EXTEND_NORMAL;

                if (extendStep < table.length) {
                    childDx = table[extendStep][0];
                    childDy = table[extendStep][1];
                    mappingFrame = table[extendStep][2];
                    extendStep++;
                    waitTimer = 3; // ROM: 3-frame delay between steps
                } else {
                    // Fully extended — fire projectile
                    fireProjectile();
                }
            }
            case ROUTINE_WAIT_RETRACT -> {
                if (waitTimer > 0) {
                    waitTimer--;
                } else {
                    // Retract
                    routine = ROUTINE_EXTEND;
                    extendStep = 0;
                    waitTimer = 0;
                    // After retract, go back to wait
                    routine = ROUTINE_WAIT_FIRE;
                    childDx = -0x1C;
                    childDy = 0;
                    mappingFrame = 4;
                    boss.clearPropellerFire();
                }
            }
        }
    }

    /** ROM: loc_697E6 — Fire flamethrower projectile based on angle. */
    private void fireProjectile() {
        // Spawn flame projectile child
        ObjectManager objectManager = services().objectManager();
        if (objectManager != null) {
            int angleIndex = boss.getAngle() / 4;
            AizEndBossFlameChild flame = new AizEndBossFlameChild(
                    boss, this, boss.getAngle());
            objectManager.addDynamicObject(flame);
        }

        // ROM: wait $5F then retract
        routine = ROUTINE_WAIT_RETRACT;
        waitTimer = 0x5F;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || boss.isHidden()) return;

        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) return;

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.AIZ_END_BOSS);
        if (renderer == null || !renderer.isReady()) return;

        boolean hFlip = (subtype != 0);
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), hFlip, false);
    }

    public int getCollisionFlags() {
        return 0; // Propellers are not collidable
    }
}
