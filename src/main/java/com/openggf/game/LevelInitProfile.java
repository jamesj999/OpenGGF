package com.openggf.game;

import java.util.List;

/**
 * Declares the ordered initialization and teardown sequences for a game.
 * <p>
 * Each Mega Drive Sonic game has a {@code Level:} routine in its disassembly
 * that defines the exact initialization sequence for entering a level. Each
 * game module provides its own profile that faithfully transcribes these
 * ROM sequences:
 * <ul>
 *   <li><b>S1:</b> {@code sonic.asm:2956} — 44 steps, phases A-L</li>
 *   <li><b>S2:</b> {@code s2.asm:4753} — 57 steps, phases A-J</li>
 *   <li><b>S3K:</b> {@code sonic3k.asm:7505} — 65 steps, phases A-Q</li>
 * </ul>
 * The engine executes steps in declared order — no topological sorting,
 * no dependency resolution. The disassembly IS the dependency graph.
 */
public interface LevelInitProfile {

    /**
     * Ordered steps for entering a level (title card through control unlock).
     * <p>
     * Maps to the game's {@code Level:} routine: S1 has 44 steps (phases A-L),
     * S2 has 57 steps (phases A-J), S3K has 65 steps (phases A-Q).
     * Returns empty list until production level loading is wired through the
     * profile system (Phase 3 of the implementation plan).
     */
    List<InitStep> levelLoadSteps();

    /**
     * Ordered steps for tearing down all singletons before the next level load.
     * <p>
     * These are the <em>inverse</em> of the ROM's init phases — each step
     * undoes the state set up by a corresponding phase in the {@code Level:}
     * routine. Replaces the manual 8-phase sequence that was in
     * {@code GameContext.forTesting()}.
     */
    List<InitStep> levelTeardownSteps();

    /**
     * Subset of teardown safe for per-test reset (preserves level data).
     * <p>
     * Clears transient gameplay state (event routines, sprites, camera,
     * collision, fade, game-state counters, timers, water) while preserving
     * loaded level geometry, art, and audio cache. Replaces
     * {@code TestEnvironment.resetPerTest()}.
     */
    List<InitStep> perTestResetSteps();

    /** Static field fixups applied after full teardown (e.g. GroundSensor wiring).
     *  Only used by levelTeardownSteps() path, not per-test reset. */
    List<StaticFixup> postTeardownFixups();
}
