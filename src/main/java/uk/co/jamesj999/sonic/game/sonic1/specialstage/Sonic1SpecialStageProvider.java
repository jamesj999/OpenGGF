package uk.co.jamesj999.sonic.game.sonic1.specialstage;

import uk.co.jamesj999.sonic.game.ResultsScreen;
import uk.co.jamesj999.sonic.game.SpecialStageAccessType;
import uk.co.jamesj999.sonic.game.SpecialStageDebugProvider;
import uk.co.jamesj999.sonic.game.SpecialStageProvider;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Music;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Sfx;

import java.io.IOException;

/**
 * Sonic 1 special stage provider.
 *
 * <p>Implements the shared special-stage contract so Sonic 1 uses the same
 * game-mode pipeline as Sonic 2. The underlying stage gameplay is scaffolded
 * and expanded in follow-up parity passes.
 */
public final class Sonic1SpecialStageProvider implements SpecialStageProvider {
    private final Sonic1SpecialStageManager manager = new Sonic1SpecialStageManager();

    @Override
    public int getTransitionSfxId() {
        return Sonic1Sfx.ENTER_SS.id;
    }

    @Override
    public int getStageMusicId() {
        return Sonic1Music.SPECIAL_STAGE.id;
    }

    @Override
    public int getResultsMusicId() {
        return Sonic1Music.CHAOS_EMERALD.id;
    }

    @Override
    public boolean hasSpecialStages() {
        return true;
    }

    @Override
    public SpecialStageAccessType getAccessType() {
        return SpecialStageAccessType.GIANT_RING;
    }

    @Override
    public void initializeStage(int stageIndex) throws IOException {
        manager.reset();
        manager.initialize(stageIndex);
    }

    @Override
    public int getCurrentStage() {
        return manager.getCurrentStage();
    }

    @Override
    public boolean isEmeraldCollected() {
        return manager.isEmeraldCollected();
    }

    @Override
    public int getEmeraldIndex() {
        return isEmeraldCollected() ? getCurrentStage() : -1;
    }

    @Override
    public int getRingsCollected() {
        return manager.getRingsCollected();
    }

    @Override
    public void setEmeraldCollected(boolean collected) {
        manager.setEmeraldCollected(collected);
        if (collected) {
            manager.markFinished();
        }
    }

    @Override
    public boolean isGameplayDebugMode() {
        return manager.isDebugMode();
    }

    @Override
    public void toggleGameplayDebugMode() {
        manager.toggleDebugMode();
    }

    @Override
    public boolean isSpriteDebugMode() {
        return false;
    }

    @Override
    public void toggleSpriteDebugMode() {
        // No-op in scaffold.
    }

    @Override
    public void cyclePlaneDebugMode() {
        // No-op in scaffold.
    }

    @Override
    public SpecialStageDebugProvider getDebugProvider() {
        return null;
    }

    @Override
    public boolean isAlignmentTestMode() {
        return false;
    }

    @Override
    public void toggleAlignmentTestMode() {
        // No-op in scaffold.
    }

    @Override
    public void adjustAlignmentOffset(int delta) {
        // No-op in scaffold.
    }

    @Override
    public void adjustAlignmentSpeed(double delta) {
        // No-op in scaffold.
    }

    @Override
    public void toggleAlignmentStepMode() {
        // No-op in scaffold.
    }

    @Override
    public void renderAlignmentOverlay(int viewportWidth, int viewportHeight) {
        // No-op in scaffold.
    }

    @Override
    public void renderLagCompensationOverlay(int viewportWidth, int viewportHeight) {
        // No-op in scaffold.
    }

    @Override
    public double getLagCompensation() {
        return 0.0;
    }

    @Override
    public void setLagCompensation(double factor) {
        // No-op in scaffold.
    }

    @Override
    public ResultsScreen createResultsScreen(int ringsCollected, boolean gotEmerald,
            int stageIndex, int totalEmeraldCount) {
        return new Sonic1SpecialStageResultsScreen();
    }

    @Override
    public void initialize() throws IOException {
        // Use initializeStage(int).
    }

    @Override
    public void update() {
        manager.update();
    }

    @Override
    public void draw() {
        manager.draw();
    }

    @Override
    public void handleInput(int heldButtons, int pressedButtons) {
        manager.handleInput(heldButtons, pressedButtons);
    }

    @Override
    public boolean isFinished() {
        return manager.isFinished();
    }

    @Override
    public void reset() {
        manager.reset();
    }

    @Override
    public boolean isInitialized() {
        return manager.isInitialized();
    }
}
