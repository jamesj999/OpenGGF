package com.openggf.game.sonic2;

import com.openggf.game.GameServices;
import com.openggf.game.ResultsScreen;
import com.openggf.game.SpecialStageAccessType;
import com.openggf.game.SpecialStageDebugProvider;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.objects.SpecialStageResultsScreenObjectInstance;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageManager;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.DefaultObjectServices;

import java.io.IOException;

/**
 * Sonic 2 special stage provider implementation.
 * Wraps the existing Sonic2SpecialStageManager with the SpecialStageProvider
 * interface.
 *
 * <p>
 * Sonic 2's special stages are accessed via star posts (checkpoints)
 * when the player has 50 or more rings. Each stage awards one of seven
 * Chaos Emeralds upon successful completion.
 */
public class Sonic2SpecialStageProvider implements SpecialStageProvider {
    private final Sonic2SpecialStageManager manager;

    public Sonic2SpecialStageProvider() {
        this(new Sonic2SpecialStageManager());
    }

    public Sonic2SpecialStageProvider(Sonic2SpecialStageManager manager) {
        this.manager = manager;
    }

    @Override
    public int getTransitionSfxId() {
        return Sonic2Sfx.SPECIAL_STAGE_ENTRY.id;
    }

    @Override
    public int getStageMusicId() {
        return Sonic2Music.SPECIAL_STAGE.id;
    }

    @Override
    public int getResultsMusicId() {
        return Sonic2Music.ACT_CLEAR.id;
    }

    @Override
    public boolean hasSpecialStages() {
        return true;
    }

    @Override
    public SpecialStageAccessType getAccessType() {
        return SpecialStageAccessType.STARPOST;
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
        return manager.hasEmeraldCollected();
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
    }

    @Override
    public int getDebugCompletionRingCount(int stageIndex) {
        // Ring requirements at final checkpoint (checkpoint 3) for each stage
        // From s2.asm Ring_Requirement_Table (solo mode)
        int[][] requirements = {
                { 30, 60, 90, 120 },   // Stage 1
                { 40, 80, 120, 160 },   // Stage 2
                { 50, 100, 140, 180 },  // Stage 3
                { 50, 100, 140, 180 },  // Stage 4
                { 60, 110, 160, 200 },  // Stage 5
                { 70, 120, 180, 220 },  // Stage 6
                { 80, 140, 200, 240 }   // Stage 7
        };
        if (stageIndex >= 0 && stageIndex < requirements.length) {
            return requirements[stageIndex][3];
        }
        return 100;
    }

    // ==================== Debug Methods ====================

    @Override
    public boolean isSpriteDebugMode() {
        return manager.isSpriteDebugMode();
    }

    @Override
    public void toggleSpriteDebugMode() {
        manager.toggleSpriteDebugMode();
    }

    @Override
    public void cyclePlaneDebugMode() {
        manager.cyclePlaneDebugMode();
    }

    @Override
    public SpecialStageDebugProvider getDebugProvider() {
        return manager.getDebugProvider();
    }

    // ==================== Alignment Test Methods ====================

    @Override
    public boolean isAlignmentTestMode() {
        return manager.isAlignmentTestMode();
    }

    @Override
    public void toggleAlignmentTestMode() {
        manager.toggleAlignmentTestMode();
    }

    @Override
    public void adjustAlignmentOffset(int delta) {
        manager.adjustAlignmentOffset(delta);
    }

    @Override
    public void adjustAlignmentSpeed(double delta) {
        manager.adjustAlignmentSpeed(delta);
    }

    @Override
    public void toggleAlignmentStepMode() {
        manager.toggleAlignmentStepMode();
    }

    @Override
    public void renderAlignmentOverlay(int viewportWidth, int viewportHeight) {
        manager.renderAlignmentOverlay(viewportWidth, viewportHeight);
    }

    // ==================== Lag Compensation Methods ====================

    @Override
    public void renderLagCompensationOverlay(int viewportWidth, int viewportHeight) {
        manager.renderLagCompensationOverlay(viewportWidth, viewportHeight);
    }

    @Override
    public double getLagCompensation() {
        return manager.getLagCompensation();
    }

    @Override
    public void setLagCompensation(double factor) {
        manager.setLagCompensation(factor);
    }

    // ==================== Results Screen ====================

    @Override
    public ResultsScreen createResultsScreen(int ringsCollected, boolean gotEmerald,
                                             int stageIndex, int totalEmeraldCount) {
        var runtime = GameServices.runtimeOrNull();
        if (runtime == null) {
            throw new IllegalStateException("Special-stage results screen requires an active GameRuntime");
        }
        DefaultObjectServices services = new DefaultObjectServices(runtime);
        ObjectConstructionContext.setConstructionContext(services);
        try {
            return new SpecialStageResultsScreenObjectInstance(
                    ringsCollected, gotEmerald, stageIndex, totalEmeraldCount, services);
        } finally {
            ObjectConstructionContext.clearConstructionContext();
        }
    }

    // ==================== MiniGameProvider Methods ====================

    @Override
    public void initialize() throws IOException {
        // No-op: Use initializeStage(int) instead
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
    public void handlePlayer2Input(int heldButtons, int logicalButtons) {
        manager.handlePlayer2Input(heldButtons, logicalButtons);
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

    /**
     * Gets the underlying manager for advanced functionality.
     * Used by Engine/GameLoop for debug overlays and other features.
     *
     * @return the Sonic2SpecialStageManager instance
     */
    public Sonic2SpecialStageManager getManager() {
        return manager;
    }
}
