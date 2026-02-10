package uk.co.jamesj999.sonic.game.sonic1.specialstage;

import uk.co.jamesj999.sonic.game.ResultsScreen;
import uk.co.jamesj999.sonic.graphics.GLCommand;

import java.util.List;

/**
 * Minimal results screen scaffold for Sonic 1 special stages.
 *
 * <p>No visuals are emitted yet; this provides deterministic timing so the
 * special-stage flow can return to level gameplay.
 */
public final class Sonic1SpecialStageResultsScreen implements ResultsScreen {
    private static final int COMPLETE_AFTER_FRAMES = 120;

    private int frameCounter;

    @Override
    public void update(int frameCounter, Object context) {
        this.frameCounter = Math.max(this.frameCounter, frameCounter);
    }

    @Override
    public boolean isComplete() {
        return frameCounter >= COMPLETE_AFTER_FRAMES;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // TODO: Implement Sonic 1 special stage result rendering.
    }
}
