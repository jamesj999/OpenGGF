package com.openggf.game.sonic2.debug;

import com.openggf.game.SpecialStageDebugProvider;

/**
 * Sonic 2 implementation of the special stage debug provider.
 * Wraps {@link Sonic2SpecialStageSpriteDebug} with the provider interface.
 */
public class Sonic2SpecialStageDebugProvider implements SpecialStageDebugProvider {
    private final Sonic2SpecialStageSpriteDebug spriteDebug;

    public Sonic2SpecialStageDebugProvider() {
        this(new Sonic2SpecialStageSpriteDebug());
    }

    public Sonic2SpecialStageDebugProvider(Sonic2SpecialStageSpriteDebug spriteDebug) {
        this.spriteDebug = spriteDebug;
    }

    @Override
    public void draw() {
        spriteDebug.draw();
    }

    @Override
    public void nextPage() {
        spriteDebug.nextPage();
    }

    @Override
    public void previousPage() {
        spriteDebug.previousPage();
    }

    @Override
    public void nextSet() {
        spriteDebug.nextSet();
    }

    @Override
    public void previousSet() {
        spriteDebug.previousSet();
    }

    @Override
    public boolean isEnabled() {
        return spriteDebug.isEnabled();
    }

    @Override
    public void toggle() {
        spriteDebug.toggle();
    }
}
