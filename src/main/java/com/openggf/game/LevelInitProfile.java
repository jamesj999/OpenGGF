package com.openggf.game;

import java.util.List;

/**
 * Declares the ordered initialization and teardown sequences for a game.
 * <p>
 * Each game module provides its own profile. The engine executes steps in
 * declared order — no topological sorting, no dependency resolution.
 * The disassembly IS the dependency graph.
 */
public interface LevelInitProfile {

    /** Ordered steps for entering a level (title card through control unlock).
     *  Deferred — returns empty list until production level loading is wired. */
    List<InitStep> levelLoadSteps();

    /** Ordered steps for tearing down all singletons before the next level load.
     *  Replaces GameContext.forTesting() phases 2-8. */
    List<InitStep> levelTeardownSteps();

    /** Subset of teardown safe for per-test reset (preserves level data).
     *  Replaces TestEnvironment.resetPerTest(). */
    List<InitStep> perTestResetSteps();

    /** Static field fixups applied after full teardown (e.g. GroundSensor wiring).
     *  Only used by levelTeardownSteps() path, not per-test reset. */
    List<StaticFixup> postTeardownFixups();
}
