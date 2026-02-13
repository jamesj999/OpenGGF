package uk.co.jamesj999.sonic.game.sonic2.events;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.audio.Sonic2Music;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2CPZBossInstance;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.WaterSystem;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;

/**
 * Chemical Plant Zone events.
 * ROM: LevEvents_CPZ (s2.asm:20504-20542)
 *
 * Act 1: No dynamic events
 * Act 2: Water (Mega Mack) rises when player reaches trigger X coordinate
 */
public class Sonic2CPZEvents extends Sonic2ZoneEvents {
    private boolean cpzWaterTriggered;
    private Sonic2CPZBossInstance cpzBoss;

    public Sonic2CPZEvents(Camera camera) {
        super(camera);
    }

    @Override
    public void init(int act) {
        super.init(act);
        cpzWaterTriggered = false;
        cpzBoss = null;
    }

    @Override
    public void update(int act, int frameCounter) {
        if (act != 1) {
            // Only Act 2 has water rise events
            return;
        }
        updateCPZWaterRise();
        updateCPZBossEvents();
    }

    private void updateCPZWaterRise() {
        if (cpzWaterTriggered) {
            return;
        }
        final int ZONE_ID_CPZ_ROM = 0x0D;
        final int WATER_RISE_TRIGGER_X = 0x1E80;
        final int WATER_TARGET_Y = 0x508;
        var player = camera.getFocusedSprite();
        if (player != null && player.getX() >= WATER_RISE_TRIGGER_X) {
            WaterSystem.getInstance().setWaterLevelTarget(
                    ZONE_ID_CPZ_ROM, 1, WATER_TARGET_Y);
            cpzWaterTriggered = true;
        }
    }

    private void updateCPZBossEvents() {
        switch (eventRoutine) {
            case 0 -> {
                if (camera.getX() >= 0x2680) {
                    camera.setMinX(camera.getX());
                    camera.setMaxYTarget((short) 0x450);
                    eventRoutine += 2;
                }
            }
            case 2 -> {
                if (camera.getX() >= 0x2A20) {
                    // ROM locks camera completely at X=0x2A20 for the entire fight
                    camera.setMinX((short) 0x2A20);
                    camera.setMaxX((short) 0x2A20);
                    eventRoutine += 2;
                    bossSpawnDelay = 0;
                    AudioManager.getInstance().fadeOutMusic();
                    GameServices.gameState().setCurrentBossId(1);
                }
            }
            case 4 -> {
                if (camera.getY() >= 0x448) {
                    camera.setMinY((short) 0x448);
                }
                bossSpawnDelay++;
                if (bossSpawnDelay >= 0x5A) {
                    spawnCPZBoss();
                    eventRoutine += 2;
                    AudioManager.getInstance().playMusic(Sonic2Music.BOSS.id);
                }
            }
            case 6 -> {
                // Prevent backtracking - minX can only increase, never decrease
                // ROM: move.w (Camera_X_pos).w,(Camera_Min_X_pos).w
                short cameraX = camera.getX();
                if (cameraX > camera.getMinX()) {
                    camera.setMinX(cameraX);
                }
            }
            default -> {
            }
        }
    }

    private void spawnCPZBoss() {
        ObjectSpawn bossSpawn = new ObjectSpawn(
                0x2B80, 0x04B0, Sonic2ObjectIds.CPZ_BOSS, 0, 0, false, 0);
        cpzBoss = new Sonic2CPZBossInstance(bossSpawn, LevelManager.getInstance());
        spawnObject(cpzBoss);
    }
}
