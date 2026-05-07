package com.openggf.game.sonic3k.events;

import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.objects.IczSnowboardIntroInstance;
import com.openggf.level.objects.ObjectSpawn;

/**
 * IceCap Zone dynamic level events.
 *
 * <p>ROM references:
 * <ul>
 *   <li>{@code sonic3k.asm:76984} {@code Obj_LevelIntroICZ1}</li>
 *   <li>{@code sonic3k.asm:39416} {@code ICZ1_Resize}</li>
 *   <li>{@code sonic3k.asm:39454} {@code ICZ2_Resize}</li>
 * </ul>
 *
 * <p>This pass enables the full ICZ intro system shell for Sonic-alone only.
 * Other player-mode branches remain inactive until their ROM paths are ported.
 */
public class Sonic3kICZEvents extends Sonic3kZoneEvents {
    private static final int ICZ1_EVENTS_FG5_CAMERA_X_1 = 0x3700;
    private static final int ICZ1_EVENTS_FG5_CAMERA_Y_1 = 0x068C;
    private static final int ICZ1_EVENTS_FG5_CAMERA_X_2 = 0x3940;
    private static final int ICZ1_INDOOR_PALETTE_X = 0x3940;
    private static final int ICZ2_INDOOR_X_MIN = 0x1000;
    private static final int ICZ2_INDOOR_X_MAX = 0x3600;
    private static final int ICZ2_INDOOR_Y = 0x0720;
    private static final int ICZ2_START_INDOOR_X_MAX = 0x3600;
    private static final int ICZ2_START_INDOOR_Y_HIGH = 0x0720;
    private static final int ICZ2_START_INDOOR_X_LOW = 0x1000;
    private static final int ICZ2_START_INDOOR_Y_LOW = 0x0580;
    private static final int ICZ2_INDOOR_HYSTERESIS_X_MIN = 0x1900;
    private static final int ICZ2_INDOOR_HYSTERESIS_X_MAX = 0x1B80;
    private static final int ICZ2_MIN_X_LOCK_CAMERA_X = 0x0740;
    private static final int ICZ2_MIN_X_LOCK_CAMERA_Y = 0x0400;

    private boolean eventsFg5;
    private boolean introSpawned;
    private boolean indoorPaletteCyclingActive;

    @Override
    public void init(int act) {
        super.init(act);
        eventsFg5 = false;
        introSpawned = false;
        indoorPaletteCyclingActive = initialIndoorPaletteCycleState(act);
        if (act == 0 && playerCharacter() == PlayerCharacter.SONIC_ALONE) {
            spawnSonicSnowboardIntro();
        }
    }

    @Override
    public void update(int act, int frameCounter) {
        if (act == 0) {
            updateAct1Resize();
        } else if (act == 1) {
            updateAct2Resize();
        }
        updateIndoorPaletteCycleGate(act);
    }

    public boolean isEventsFg5() {
        return eventsFg5;
    }

    public void setEventsFg5(boolean value) {
        eventsFg5 = value;
    }

    /**
     * ROM: Events_bg+$16. AnPal_ICZ uses this word to decide whether palette
     * line 4 colors 12-15 are allowed to cycle.
     */
    public boolean isIndoorPaletteCyclingActive() {
        return indoorPaletteCyclingActive;
    }

    public void setIndoorPaletteCyclingActive(boolean value) {
        indoorPaletteCyclingActive = value;
    }

    private void spawnSonicSnowboardIntro() {
        if (introSpawned) {
            return;
        }
        introSpawned = true;
        ObjectSpawn spawn = new ObjectSpawn(
                IczSnowboardIntroInstance.INITIAL_SNOWBOARD_X,
                IczSnowboardIntroInstance.INITIAL_SNOWBOARD_Y,
                0, 0, 0, false,
                IczSnowboardIntroInstance.INITIAL_SNOWBOARD_Y);
        spawnObject(() -> new IczSnowboardIntroInstance(spawn));
    }

    private void updateAct1Resize() {
        int cameraX = camera().getX();
        int cameraY = camera().getY();
        switch (eventRoutine) {
            case 0 -> {
                if (cameraX >= ICZ1_EVENTS_FG5_CAMERA_X_1
                        && cameraY >= ICZ1_EVENTS_FG5_CAMERA_Y_1) {
                    eventsFg5 = true;
                    eventRoutine = 2;
                }
            }
            case 2 -> {
                if (cameraX >= ICZ1_EVENTS_FG5_CAMERA_X_2) {
                    eventsFg5 = true;
                    eventRoutine = 4;
                }
            }
            default -> {
                // ICZ1 routine 4 is a ROM rts terminal state.
            }
        }
    }

    private void updateAct2Resize() {
        if (eventRoutine != 0) {
            return;
        }
        if (camera().getX() >= ICZ2_MIN_X_LOCK_CAMERA_X
                && camera().getY() < ICZ2_MIN_X_LOCK_CAMERA_Y) {
            camera().setMinX((short) ICZ2_MIN_X_LOCK_CAMERA_X);
            eventRoutine = 2;
        }
    }

    private boolean initialIndoorPaletteCycleState(int act) {
        try {
            int x = camera().getX() & 0xFFFF;
            int y = camera().getY() & 0xFFFF;
            if (act == 0) {
                return x >= ICZ1_INDOOR_PALETTE_X;
            }
            if (act == 1) {
                return shouldAct2StartIndoors(x, y);
            }
        } catch (IllegalStateException ignored) {
            // Some tests construct event state before a gameplay camera exists.
        }
        return false;
    }

    private void updateIndoorPaletteCycleGate(int act) {
        int x = camera().getX() & 0xFFFF;
        int y = camera().getY() & 0xFFFF;
        if (act == 0) {
            if (x >= ICZ1_INDOOR_PALETTE_X) {
                indoorPaletteCyclingActive = true;
            }
            return;
        }
        if (act == 1) {
            indoorPaletteCyclingActive = isAct2IndoorPaletteCycleActive(x, y);
        }
    }

    private boolean shouldAct2StartIndoors(int x, int y) {
        if (x >= ICZ2_START_INDOOR_X_MAX) {
            return false;
        }
        if (y >= ICZ2_START_INDOOR_Y_HIGH) {
            return true;
        }
        return x >= ICZ2_START_INDOOR_X_LOW && y >= ICZ2_START_INDOOR_Y_LOW;
    }

    private boolean isAct2IndoorPaletteCycleActive(int x, int y) {
        if (!indoorPaletteCyclingActive) {
            return x >= ICZ2_INDOOR_X_MIN && x < ICZ2_INDOOR_X_MAX && y >= ICZ2_INDOOR_Y;
        }
        if (x >= ICZ2_INDOOR_HYSTERESIS_X_MIN && x < ICZ2_INDOOR_HYSTERESIS_X_MAX) {
            return true;
        }
        return !(x < ICZ2_INDOOR_X_MIN || x >= ICZ2_INDOOR_X_MAX || y < ICZ2_INDOOR_Y);
    }
}
