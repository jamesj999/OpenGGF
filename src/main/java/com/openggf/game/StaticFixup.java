package com.openggf.game;

/**
 * A post-teardown static field fixup (e.g. re-wiring GroundSensor).
 *
 * @param name   short identifier
 * @param reason why this fixup is needed
 * @param action the fixup operation
 */
public record StaticFixup(
    String name,
    String reason,
    Runnable action
) {
    public void apply() {
        action.run();
    }
}
