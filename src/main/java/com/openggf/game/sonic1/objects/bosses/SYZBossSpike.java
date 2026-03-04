package com.openggf.game.sonic1.objects.bosses;

import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * SYZ Boss spike — child component that extends below the Eggpod.
 * ROM: BossSpringYard_SpikeMain (routine 8 in _incObj/75 Boss - Spring Yard.asm)
 *
 * Uses Map_BossItems frame 5 (.spike) — 4 sprite pieces forming a cross shape.
 * The spike has collision type $84 (harmful to player) when the boss isn't
 * holding a block and isn't invulnerable.
 *
 * The spike tracks a Y extension value (objoff_3C) that grows during the boss's
 * block drop sequence and retracts when ascending. Display offset = extension >> 2.
 */
public class SYZBossSpike extends AbstractBossChild implements TouchResponseProvider {

    // ROM: obColType = $84 when active (harmful, size category)
    private static final int SPIKE_COLLISION_TYPE = 0x84;

    // Extension tracking
    private int extensionDepth; // objoff_3C — tracks how far the spike extends
    private boolean spikeActive;

    // Boss state cache (updated each frame by parent)
    private int bossRoutineSecondary;
    private int bossDropSubPhase;
    private int bossTimer;

    public SYZBossSpike(AbstractBossInstance parent, LevelManager levelManager) {
        super(parent, "SYZSpike", 5, Sonic1ObjectIds.SYZ_BOSS);
        this.extensionDepth = 0;
        this.spikeActive = false;
    }

    /**
     * Called by parent boss to set spike collision state.
     */
    public void setSpikeActive(boolean active) {
        this.spikeActive = active;
    }

    /**
     * Called by parent boss to update state for extension tracking.
     */
    public void setBossState(int routineSecondary, int dropSubPhase, int timer) {
        this.bossRoutineSecondary = routineSecondary;
        this.bossDropSubPhase = dropSubPhase;
        this.bossTimer = timer;
    }

    @Override
    public int getCollisionFlags() {
        if (spikeActive) {
            return SPIKE_COLLISION_TYPE;
        }
        return 0;
    }

    @Override
    public int getCollisionProperty() {
        return 0; // Spike is harmful only — hit counter is on the main boss object
    }

    @Override
    public int getX() {
        return parent.getX();
    }

    @Override
    public int getY() {
        return parent.getY() + (extensionDepth >> 2);
    }

    @Override
    public int getPriorityBucket() {
        return 5; // ROM: obPriority = 5
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        LevelManager lm = LevelManager.getInstance();
        if (lm == null) {
            return;
        }
        ObjectRenderManager renderManager = lm.getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer weaponsRenderer = renderManager.getRenderer(ObjectArtKeys.BOSS_WEAPONS);
        if (weaponsRenderer == null || !weaponsRenderer.isReady()) {
            return;
        }

        boolean flipped = (parent.getState().renderFlags & 1) != 0;

        // Map_BossItems frame 5 = .spike
        weaponsRenderer.drawFrameIndex(5, getX(), getY(), flipped, false);
    }

    /**
     * ROM: BossSpringYard_SpikeMain spike extension tracking.
     * During block drop (ob2ndRout=4), the spike extends progressively.
     * Otherwise it retracts toward 0.
     */
    void updateExtension() {
        if (bossRoutineSecondary == 4) {
            // During block drop phase
            if (bossDropSubPhase == 0) {
                // Descending — extend spike
                if (extensionDepth < 0x94) {
                    extensionDepth += 7;
                }
            } else if (bossDropSubPhase == 6 && bossTimer < 0) {
                // Settling with timer negative — retract
                if (extensionDepth > 0) {
                    extensionDepth -= 5;
                    if (extensionDepth < 0) {
                        extensionDepth = 0;
                    }
                }
            }
        } else {
            // Not in block drop — retract
            if (extensionDepth > 0) {
                extensionDepth -= 5;
                if (extensionDepth < 0) {
                    extensionDepth = 0;
                }
            }
        }
    }
}
