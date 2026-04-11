package com.openggf.game.sonic1;

import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.sonic1.events.Sonic1LevelEventManager;
import com.openggf.game.sonic1.objects.Sonic1StomperDoorObjectInstance;

import java.util.List;

/**
 * Sonic 1 level initialization profile.
 * <p>
 * Aligned to the S1 {@code Level:} routine at {@code sonic.asm:2956} (44 steps
 * across phases A-L). The teardown steps undo the state set up by that routine.
 * <p>
 * S1-specific characteristics:
 * <ul>
 *   <li>Direct Nemesis decompression for title card art (no PLC queue)</li>
 *   <li>Unified collision model (single index, no dual-path switching)</li>
 *   <li>Single-phase data load ({@code LevelDataLoad} — all geometry in one call)</li>
 *   <li>Water only in LZ/SBZ3 ({@code LZWaterFeatures})</li>
 *   <li>Object placement BEFORE game state clear (phases J→K)</li>
 *   <li>Unique 4-frame VBla delay before fade-in (phase L, step 41)</li>
 *   <li>No sidekick (Tails) — step 19 (SpawnSidekick) omitted</li>
 * </ul>
 *
 * @see <a href="docs/plans/2026-02-27-rom-driven-init-profiles-design.md">
 *      Design doc: Sonic 1 Level Init Profile (44 steps)</a>
 */
public class Sonic1LevelInitProfile extends AbstractLevelInitProfile {
    private final Sonic1LevelEventManager levelEventManager;
    private final Sonic1SwitchManager switchManager;
    private final Sonic1ConveyorState conveyorState;

    public Sonic1LevelInitProfile(Sonic1LevelEventManager levelEventManager,
                                  Sonic1SwitchManager switchManager,
                                  Sonic1ConveyorState conveyorState) {
        this.levelEventManager = levelEventManager;
        this.switchManager = switchManager;
        this.conveyorState = conveyorState;
    }

    @Override
    public List<InitStep> levelLoadSteps(LevelLoadContext ctx) {
        List<InitStep> steps = buildCoreSteps(ctx);
        // Post-load assembly: 6 steps (no SpawnSidekick — S1 has no Tails)
        if (ctx.isIncludePostLoadAssembly()) {
            steps.add(restoreCheckpointStep(ctx));
            steps.add(spawnPlayerStep(ctx));
            steps.add(resetPlayerStateStep(ctx));
            steps.add(initCameraStep());
            steps.add(initLevelEventsStep());
            steps.add(requestTitleCardStep(ctx));
        }
        return List.copyOf(steps);
    }

    @Override
    protected InitStep levelEventTeardownStep() {
        return new InitStep("ResetS1LevelEvents",
            "Undoes S1 per-zone event handlers (GHZ/MZ/SYZ/LZ/SLZ/SBZ/FZ events)",
            levelEventManager::resetState);
    }

    @Override
    protected InitStep perTestLeadStep() {
        return new InitStep("ResetS1LevelEvents",
            "Undoes S1 per-zone event handlers, switch state, conveyor state, SBZ3 stomper door",
            () -> {
                levelEventManager.resetState();
                switchManager.reset();
                conveyorState.reset();
                Sonic1StomperDoorObjectInstance.resetSbz3Flag();
            });
    }
}
