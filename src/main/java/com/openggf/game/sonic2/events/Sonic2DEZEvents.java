package com.openggf.game.sonic2.events;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.bosses.Sonic2MechaSonicInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Death Egg Zone events.
 * ROM: LevEvents_DEZ (s2.asm:21311-21395)
 *
 * DEZ has two sequential boss fights in a single act:
 *   Routine 0: Camera X trigger >= $140 for Silver Sonic spawn + camera lock
 *   Routine 2: Silver Sonic fight in progress (empty - boss defeat advances this)
 *   Routine 4: Push Camera_Min_X forward; Camera X >= $300 triggers DEZ boss load
 *   Routine 6: Push Camera_Min_X forward; Camera X >= $680 locks camera for Death Egg Robot
 *   Routine 8: Death Egg Robot fight in progress (empty)
 */
public class Sonic2DEZEvents extends Sonic2ZoneEvents {
    private Sonic2MechaSonicInstance silverSonic;

    public Sonic2DEZEvents(Camera camera) {
        super(camera);
    }

    @Override
    public void init(int act) {
        super.init(act);
        silverSonic = null;
    }

    @Override
    public void update(int act, int frameCounter) {
        // DEZ has only one act in the ROM (act index 0)
        switch (eventRoutine) {
            case 0 -> {
                // Routine 0 (s2.asm LevEvents_DEZ_Routine1):
                // Wait for Camera_X >= $140
                if (camera.getX() >= 0x140) {
                    // Advance routine
                    eventRoutine += 2;
                    // Spawn Silver Sonic (ObjAF) at ($348, $A0) subtype $48
                    spawnSilverSonic();
                    // Mark boss fight active (boss_id = 9)
                    GameServices.gameState().setCurrentBossId(9);
                    // Fade out music
                    AudioManager.getInstance().fadeOutMusic();
                }
            }
            case 2 -> {
                // Routine 2: Silver Sonic fight in progress
                // Boss handles its own camera locking and music in its state machine.
                // When Silver Sonic is defeated, it advances this routine.
                if (silverSonic != null && silverSonic.isDefeated()) {
                    eventRoutine += 2;
                }
            }
            case 4 -> {
                // Routine 4: Post-Silver Sonic - push camera forward
                // ROM: Push Camera_Min_X forward (prevent backtracking)
                camera.setMinX(camera.getX());
                // Wait for Camera_X >= $300
                if (camera.getX() >= 0x300) {
                    eventRoutine += 2;
                    // Load PLC DEZ boss art here (in ROM: PlcList_DEZBoss)
                }
            }
            case 6 -> {
                // Routine 6: Push camera forward to Death Egg Robot arena
                // ROM: Push Camera_Min_X forward
                camera.setMinX(camera.getX());
                // Wait for Camera_X >= $680
                if (camera.getX() >= 0x680) {
                    // Lock camera for Death Egg Robot arena
                    camera.setMinX((short) 0x680);
                    camera.setMaxX((short) 0x740);
                    eventRoutine += 2;
                }
            }
            case 8 -> {
                // Routine 8: Death Egg Robot fight in progress (empty)
                // Will be populated when ObjC7 (Death Egg Robot) is implemented
            }
            default -> {
                // No more routines
            }
        }
    }

    /**
     * Spawns the Silver Sonic / Mecha Sonic boss.
     * ROM: Creates Object 0xAF at coordinates ($348, $A0) with subtype $48
     */
    private void spawnSilverSonic() {
        ObjectSpawn bossSpawn = new ObjectSpawn(
                0x348, 0xA0, Sonic2ObjectIds.MECHA_SONIC, 0x48, 0, false, 0);
        silverSonic = new Sonic2MechaSonicInstance(bossSpawn, LevelManager.getInstance());
        spawnObject(silverSonic);
    }

    /**
     * Get the DEZ event routine for external access (e.g., by the boss defeat handler).
     */
    public int getDEZEventRoutine() {
        return eventRoutine;
    }
}
