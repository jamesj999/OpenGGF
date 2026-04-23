package com.openggf.game;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.logging.Logger;

/**
 * Stores checkpoint state for save/restore on player death.
 * <p>
 * Based on the Sonic 2 disassembly's Saved_* variables.
 * </p>
 */
public class CheckpointState implements RespawnState {
    private static final Logger LOGGER = Logger.getLogger(CheckpointState.class.getName());

    private int lastCheckpointIndex = -1;
    private int savedX;
    private int savedY;
    private int savedCameraX;
    private int savedCameraY;
    private boolean cameraLock;
    private boolean usedForSpecialStage; // Prevents stars from respawning after SS entry

    // Water state (ROM: v_lamp_wtrpos, v_lamp_wtrrout)
    private int savedWaterLevel;
    private int savedWaterRoutine;
    private boolean hasWaterState;
    private int savedCameraMaxY;
    private int savedDynamicResizeRoutine;
    private boolean hasS3kRuntimeState;
    private byte savedTopSolidBit = 0x0C;
    private byte savedLrbSolidBit = 0x0D;
    private boolean hasSolidBits;

    /**
     * Clear checkpoint state (called on level start/change).
     */
    public void clear() {
        lastCheckpointIndex = -1;
        savedX = 0;
        savedY = 0;
        savedCameraX = 0;
        savedCameraY = 0;
        cameraLock = false;
        usedForSpecialStage = false;
        savedWaterLevel = 0;
        savedWaterRoutine = 0;
        hasWaterState = false;
        savedCameraMaxY = 0;
        savedDynamicResizeRoutine = 0;
        hasS3kRuntimeState = false;
        savedTopSolidBit = 0x0C;
        savedLrbSolidBit = 0x0D;
        hasSolidBits = false;
    }

    /**
     * Save checkpoint state from raw values.
     * Game-agnostic — callers extract position/flags from their game-specific checkpoint object.
     */
    public void saveCheckpoint(int checkpointIndex, int x, int y, boolean cameraLockFlag) {
        this.lastCheckpointIndex = checkpointIndex;
        this.savedX = x;
        this.savedY = y;
        this.cameraLock = cameraLockFlag;

        Camera camera = GameServices.camera();
        this.savedCameraX = camera.getX();
        this.savedCameraY = camera.getY();
        saveS3kRuntimeStateIfPresent(camera);
        savePlayerSolidBitsIfPresent();

        LOGGER.fine("Saved checkpoint " + lastCheckpointIndex + " at (" + savedX + ", " + savedY + ")");
    }

    private void savePlayerSolidBitsIfPresent() {
        Camera camera = GameServices.cameraOrNull();
        if (camera == null || !(camera.getFocusedSprite() instanceof AbstractPlayableSprite playable)) {
            savedTopSolidBit = 0x0C;
            savedLrbSolidBit = 0x0D;
            hasSolidBits = false;
            return;
        }
        savedTopSolidBit = playable.getTopSolidBit();
        savedLrbSolidBit = playable.getLrbSolidBit();
        hasSolidBits = true;
    }

    private void saveS3kRuntimeStateIfPresent(Camera camera) {
        LevelEventProvider eventProvider = GameServices.module().getLevelEventProvider();
        if (camera == null || !(eventProvider instanceof Sonic3kLevelEventManager s3kEvents)) {
            savedCameraMaxY = 0;
            savedDynamicResizeRoutine = 0;
            hasS3kRuntimeState = false;
            return;
        }
        savedCameraMaxY = camera.getMaxY();
        savedDynamicResizeRoutine = s3kEvents.getDynamicResizeRoutine();
        hasS3kRuntimeState = true;
    }

    /**
     * Restore state after player death.
     * ROM behavior: restores position, camera, clears rings.
     */
    public void restoreToPlayer(AbstractPlayableSprite player, Camera camera) {
        if (!isActive()) {
            return;
        }

        // Restore player position to checkpoint location
        player.setX((short) savedX);
        player.setY((short) savedY);

        // Clear player state for fresh start
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setAir(false);
        player.setRolling(false);

        // Clear rings (ROM behavior)
        player.setRingCount(0);

        // Restore camera position directly from saved values (ROM-accurate)
        if (camera != null) {
            camera.setX((short) savedCameraX);
            camera.setY((short) savedCameraY);
            camera.setFocusedSprite(player);
            if (hasS3kRuntimeState) {
                camera.setMaxY((short) savedCameraMaxY);
                camera.setMaxYTarget((short) savedCameraMaxY);
            }

            // Apply camera min X lock if subtype bit 7 was set
            if (cameraLock) {
                int minX = savedX - 0xA0;
                camera.setMinX((short) Math.max(0, minX));
            }
        }

        LOGGER.info("Restored from checkpoint " + lastCheckpointIndex);
    }

    public boolean isActive() {
        return lastCheckpointIndex >= 0;
    }

    public int getLastCheckpointIndex() {
        return lastCheckpointIndex;
    }

    public int getSavedX() {
        return savedX;
    }

    public int getSavedY() {
        return savedY;
    }

    public int getSavedCameraX() {
        return savedCameraX;
    }

    public int getSavedCameraY() {
        return savedCameraY;
    }

    /**
     * Save water state at checkpoint time.
     * ROM: Lamp_StoreInfo saves v_waterpos2 and v_wtr_routine.
     */
    public void saveWaterState(int waterLevel, int waterRoutine) {
        this.savedWaterLevel = waterLevel;
        this.savedWaterRoutine = waterRoutine;
        this.hasWaterState = true;
    }

    public boolean hasWaterState() {
        return hasWaterState;
    }

    public int getSavedWaterLevel() {
        return savedWaterLevel;
    }

    public int getSavedWaterRoutine() {
        return savedWaterRoutine;
    }

    public boolean isUsedForSpecialStage() {
        return usedForSpecialStage;
    }

    public boolean hasS3kRuntimeState() {
        return hasS3kRuntimeState;
    }

    public int getSavedCameraMaxY() {
        return savedCameraMaxY;
    }

    public int getSavedDynamicResizeRoutine() {
        return savedDynamicResizeRoutine;
    }

    public boolean hasSolidBits() {
        return hasSolidBits;
    }

    public byte getSavedTopSolidBit() {
        return savedTopSolidBit;
    }

    public byte getSavedLrbSolidBit() {
        return savedLrbSolidBit;
    }

    public void markUsedForSpecialStage() {
        this.usedForSpecialStage = true;
        LOGGER.fine("Checkpoint " + lastCheckpointIndex + " marked as used for special stage entry");
    }

    /**
     * Restore checkpoint state from previously saved values.
     * Called after loadLevel() clears the state but we still need checkpoint for
     * respawn.
     */
    public void restoreFromSaved(int x, int y, int cameraX, int cameraY, int checkpointIndex) {
        this.lastCheckpointIndex = checkpointIndex;
        this.savedX = x;
        this.savedY = y;
        this.savedCameraX = cameraX;
        this.savedCameraY = cameraY;
        LOGGER.fine("Restored checkpoint " + checkpointIndex + " state at (" + x + ", " + y + ")");
    }

    public void saveS3kRuntimeState(int cameraMaxY, int dynamicResizeRoutine) {
        this.savedCameraMaxY = cameraMaxY;
        this.savedDynamicResizeRoutine = dynamicResizeRoutine;
        this.hasS3kRuntimeState = true;
    }

    public void saveSolidBits(byte topSolidBit, byte lrbSolidBit) {
        this.savedTopSolidBit = topSolidBit;
        this.savedLrbSolidBit = lrbSolidBit;
        this.hasSolidBits = true;
    }
}
