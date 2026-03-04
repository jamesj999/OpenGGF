package com.openggf.game;

/**
 * A single step in a level initialization or teardown sequence.
 *
 * @param name        short identifier, e.g. "LoadZoneTiles"
 * @param romRoutine  disassembly reference, e.g. "s2.asm:Level, line 4934"
 * @param action      the operation to execute
 */
public record InitStep(
    String name,
    String romRoutine,
    Runnable action
) {
    public void execute() {
        action.run();
    }
}
