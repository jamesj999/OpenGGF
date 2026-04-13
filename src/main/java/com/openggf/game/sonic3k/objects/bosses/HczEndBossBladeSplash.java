package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;
import java.util.logging.Logger;

/**
 * HCZ end boss blade water splash (ROM: loc_6B4A2).
 *
 * <p>Spawned by the blade ({@code ChildObjDat_6BDD8}) when the blade's Y
 * position crosses the water level during FALL (transition from routine 8
 * to routine A). This is a simple animated visual splash at the water
 * surface — it has no collision or player interaction.
 *
 * <h3>ROM setup (loc_6B4A2):</h3>
 * <ul>
 *   <li>{@code SetUp_ObjAttributes2} with {@code word_6BD52}:
 *       art_tile = ArtTile_HCZEndBoss, priority = $80,
 *       width = $C, height = $8, mapping_frame = $18.</li>
 *   <li>Main handler = {@code AnimateRaw_DrawTouch}</li>
 *   <li>Animation = {@code byte_6BE36}: delay=$18 (24 ticks),
 *       frames 2, 0x18, 2, 0x30, 3, 0x19, 4, then $F4 (end→delete)</li>
 *   <li>Positioned at {@code (Water_level - 4)} via {@code loc_6B46E}</li>
 * </ul>
 *
 * <p>The splash sits at the blade's X position and at water_level - 4.
 * It plays through a short animation and deletes itself.
 */
public class HczEndBossBladeSplash extends AbstractBossChild {
    private static final Logger LOG = Logger.getLogger(HczEndBossBladeSplash.class.getName());

    // =========================================================================
    // Animation: byte_6BE36 (Animate_Raw format)
    // delay=$18, frames: 2, $18, 2, $30, 3, $19, 4, end $F4
    // =========================================================================
    private static final int ANIM_BASE_DELAY = 0x18;  // 24 ticks per frame
    private static final int[] ANIM_FRAMES = { 2, 0x18, 2, 0x30, 3, 0x19, 4 };

    // =========================================================================
    // Instance state
    // =========================================================================
    private final HczEndBossInstance boss;

    private int animFrameIndex;
    private int animFrameTimer;
    private int mappingFrame;
    private boolean animComplete;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * @param boss   Parent boss instance.
     * @param bladeX World X of the blade when it entered water.
     */
    public HczEndBossBladeSplash(HczEndBossInstance boss, int bladeX) {
        super(boss, "HCZEndBossBladeSplash", 3, 0);
        this.boss = boss;

        // Position at blade's X, water_level - 4 (ROM: loc_6B46E)
        this.currentX = bladeX;
        this.currentY = getWaterLevelY() - 4;

        // ROM: initial mapping_frame = $18 (from word_6BD52)
        this.mappingFrame = 0x18;
        this.animFrameIndex = 0;
        this.animFrameTimer = ANIM_BASE_DELAY;
        this.animComplete = false;

        updateDynamicSpawn();
    }

    // =========================================================================
    // Update
    // =========================================================================

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (!beginUpdate(frameCounter)) {
            return;
        }

        if (boss.isDefeatSignal() || animComplete) {
            setDestroyed(true);
            return;
        }

        // ROM loc_6B46E: keep Y pinned to water level - 4
        currentY = getWaterLevelY() - 4;

        tickAnimation();
    }

    // =========================================================================
    // Animation (Animate_Raw emulation)
    // =========================================================================

    private void tickAnimation() {
        animFrameTimer--;
        if (animFrameTimer >= 0) {
            return;
        }

        animFrameIndex++;
        if (animFrameIndex >= ANIM_FRAMES.length) {
            // $F4 end → delete
            animComplete = true;
            return;
        }

        mappingFrame = ANIM_FRAMES[animFrameIndex];
        animFrameTimer = ANIM_BASE_DELAY;
    }

    // =========================================================================
    // Water level access
    // =========================================================================

    private int getWaterLevelY() {
        try {
            WaterSystem ws = services().waterSystem();
            if (ws == null) {
                return 0x1000;
            }
            int zoneId = services().featureZoneId();
            int actId  = services().featureActId();
            if (ws.hasWater(zoneId, actId)) {
                return ws.getWaterLevelY(zoneId, actId);
            }
        } catch (Exception e) {
            LOG.fine(() -> "HczEndBossBladeSplash.getWaterLevelY: " + e.getMessage());
        }
        return 0x1000;
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.HCZ_END_BOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        renderer.drawFrameIndex(mappingFrame, currentX, currentY, false, false);
    }
}
