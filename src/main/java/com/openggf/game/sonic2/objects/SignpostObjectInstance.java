package com.openggf.game.sonic2.objects;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * End of level signpost (Object 0D).
 * <p>
 * Behavior from s2.asm:
 * <ol>
 * <li>Wait for player to pass (Obj0D_Main)</li>
 * <li>On pass: lock screen, play signpost sound, start spinning
 * (Obj0D_Main_State2)</li>
 * <li>Spawn sparkle effects while spinning</li>
 * <li>When spin completes, lock player controls (Obj0D_Main_State3)</li>
 * <li>Player walks off-screen automatically</li>
 * <li>Spawn results screen (Obj3A), play end-level jingle</li>
 * </ol>
 */
public class SignpostObjectInstance extends BoxObjectInstance {
    private static final Logger LOGGER = Logger.getLogger(SignpostObjectInstance.class.getName());

    // Routine states (matching ROM)
    private static final int STATE_IDLE = 0;
    private static final int STATE_SPINNING = 2;
    private static final int STATE_WALK_OFF = 4;

    // Mapping frame indices matching obj0D_a.asm (ROM order from loadMappingFrames):
    // 0 = Sonic final face (tiles $22+$2E, wide) — held by Ani_obj0D anim 3: $0F,$00,$FF
    // 1 = Tails face (tiles $3A+$3E+hflip) — held by anim 4: $0F,$01,$FF
    // 2 = Eggman front face (tiles 0, h-flip) — held by anim 0: $0F,$02,$FF (initial state)
    // 3 = Spin transition A (tile $0C, 4x4)
    // 4 = Edge/thin view (tile $1C)
    // 5 = Spin transition B (tile $0C, h-flipped)
    private static final int FRAME_SONIC = 0;
    private static final int FRAME_TAILS = 1;
    private static final int FRAME_EGGMAN = 2;
    private static final int FRAME_SPIN_A = 3;
    private static final int FRAME_SIDE_ON = 4;
    private static final int FRAME_SPIN_B = 5;

    // Spin timing
    private static final int SPIN_FRAME_DELAY = 2;
    private static final int SPIN_CYCLES = 3;

    // ROM: Obj0D_Main_State3 - player must pass Camera_Max_X_pos + $128 to trigger results
    private static final int WALK_OFF_OFFSET = 0x128;

    // Spinning animation frame sequence matching Ani_obj0D anim 1:
    // $01, $02,$03,$04,$05, $01,$03,$04,$05, $00,$03,$04,$05, $FF
    private static final int[] SPIN_FRAMES = {
            FRAME_EGGMAN, FRAME_SPIN_A, FRAME_SIDE_ON, FRAME_SPIN_B,
            FRAME_TAILS, FRAME_SPIN_A, FRAME_SIDE_ON, FRAME_SPIN_B,
            FRAME_SONIC, FRAME_SPIN_A, FRAME_SIDE_ON, FRAME_SPIN_B
    };

    // Sparkle effect timing and positions (from s2.asm Obj0D_RingSparklePositions)
    private static final int SPARKLE_SPAWN_DELAY = 11;
    private static final int[][] SPARKLE_POSITIONS = {
            { -24, -16 }, { 8, 8 }, { -16, 0 }, { 24, -8 },
            { 0, -8 }, { 16, 0 }, { -24, 8 }, { 24, 16 }
    };

    private int routineState = STATE_IDLE;
    private int mappingFrame = FRAME_EGGMAN;
    private int animTimer = 0;
    private int spinFrameIndex = 0;
    private int spinCycleCount = 0;
    private int sparkleTimer = 0;
    private int sparkleIndex = 0;

    private boolean resultsSpawned = false;

    public SignpostObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name, 24, 40, 0.3f, 0.8f, 0.3f, false);

        // ROM: Obj0D_Init (s2.asm:34412-34418)
        // Signpost is disabled in Act 2+ except for MTZ Act 2
        // ROM sets x_pos to 0, causing MarkObjGone to delete it and clear respawn bit
        LevelManager levelMgr = LevelManager.getInstance();
        if (levelMgr != null) {
            int currentAct = levelMgr.getCurrentAct();
            int currentZone = levelMgr.getCurrentZone();

            // ROM check: if (Current_Act != 0 && Current_ZoneAndAct != metropolis_zone_act_2)
            // metropolis_zone_act_2 = (7 << 8) | 1 = 0x0701
            if (currentAct > 0) {
                boolean isMTZAct2 = (currentZone == 7 && currentAct == 1);
                if (!isMTZAct2) {
                    // Mark as remembered to prevent respawning, then destroy
                    // This prevents the spawn-destroy cycle every frame
                    ObjectManager objMgr = levelMgr.getObjectManager();
                    if (objMgr != null) {
                        objMgr.markRemembered(spawn);
                    }
                    setDestroyed(true);
                }
            }
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Don't update if destroyed (disabled in Act 2+)
        if (isDestroyed()) {
            return;
        }

        if (player == null) {
            return;
        }

        switch (routineState) {
            case STATE_IDLE -> checkPlayerPass(player);
            case STATE_SPINNING -> updateSpinning();
            case STATE_WALK_OFF -> updateWalkOff(player);
        }
    }

    /**
     * ROM: Obj0D_Main (s2.asm:34449-34456)
     * move.w x_pos(a1),d0 ; player center X
     * sub.w  x_pos(a0),d0 ; d0 = player_center - signpost_center
     * bcs.s  ...           ; skip if negative (player left of signpost)
     * cmpi.w #$20,d0
     * bhs.s  ...           ; skip if >= $20 (player too far past)
     */
    private void checkPlayerPass(AbstractPlayableSprite player) {
        int dx = player.getCentreX() - spawn.x();
        if (dx >= 0 && dx < 0x20) {
            activateSignpost(player);
        }
    }

    private void activateSignpost(AbstractPlayableSprite player) {
        LOGGER.info("Signpost activated at X=" + spawn.x());

        try {
            AudioManager.getInstance().playSfx(Sonic2Sfx.SIGNPOST.id);
        } catch (Exception e) {
            LOGGER.warning("Failed to play signpost sound: " + e.getMessage());
        }

        lockCamera();

        // Freeze the level timer when passing the signpost
        var levelGamestate = LevelManager.getInstance().getLevelGamestate();
        if (levelGamestate != null) {
            levelGamestate.pauseTimer();
        }

        routineState = STATE_SPINNING;
        spinFrameIndex = 0;
        spinCycleCount = 0;
        animTimer = 0;
        sparkleTimer = 0;
        sparkleIndex = 0;
        mappingFrame = SPIN_FRAMES[0];
    }

    /**
     * ROM: move.w (Camera_Max_X_pos).w,(Camera_Min_X_pos).w
     * Sets Camera_Min to Camera_Max, locking the camera at the level's right boundary.
     * Camera_Max_X_pos is NOT changed — the player's right movement limit in
     * Sonic_LevelBound still uses the original level boundary value.
     */
    private void lockCamera() {
        Camera camera = Camera.getInstance();
        if (camera != null) {
            camera.setMinX(camera.getMaxX());
            LOGGER.fine("Camera locked: minX set to maxX=" + camera.getMaxX());
        }
    }

    private void updateSpinning() {
        // Update animation frame
        animTimer++;
        if (animTimer >= SPIN_FRAME_DELAY) {
            animTimer = 0;
            spinFrameIndex++;

            if (spinFrameIndex >= SPIN_FRAMES.length) {
                spinFrameIndex = 0;
                spinCycleCount++;

                if (spinCycleCount >= SPIN_CYCLES) {
                    mappingFrame = FRAME_SONIC;
                    routineState = STATE_WALK_OFF;
                    LOGGER.fine("Signpost spin complete, entering walk-off state");
                    return;
                }
            }

            mappingFrame = SPIN_FRAMES[spinFrameIndex];
        }

        // Spawn sparkle effects
        spawnSparkleIfReady();
    }

    private void spawnSparkleIfReady() {
        sparkleTimer++;
        if (sparkleTimer >= SPARKLE_SPAWN_DELAY) {
            sparkleTimer = 0;

            int[] offset = SPARKLE_POSITIONS[sparkleIndex];
            int sparkleX = spawn.x() + offset[0];
            int sparkleY = spawn.y() + offset[1];

            SignpostSparkleObjectInstance sparkle = new SignpostSparkleObjectInstance(sparkleX, sparkleY);
            ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
            if (objectManager != null) {
                objectManager.addDynamicObject(sparkle);
            }

            // Cycle through positions (ROM: addq.b #2, andi.b #$E => 0,2,4,6,0,2...)
            sparkleIndex = (sparkleIndex + 1) % SPARKLE_POSITIONS.length;
        }
    }

    /**
     * ROM: Obj0D_Main_State3 (s2.asm:34593-34621)
     * <p>
     * Each frame:
     * 1. If player is airborne, skip control lock (wait for landing)
     * 2. Otherwise set Control_Locked and force right input
     * 3. Check: player_center_x >= Camera_Max_X_pos + $128 → trigger end of act
     */
    private void updateWalkOff(AbstractPlayableSprite player) {
        // ROM: btst #status.player.in_air — only lock controls when grounded
        if (!player.getAir()) {
            player.setForceInputRight(true);
            player.setControlLocked(true);
        }

        // ROM: move.w (MainCharacter+x_pos).w,d0
        //      move.w (Camera_Max_X_pos).w,d1
        //      addi.w #$128,d1
        //      cmp.w  d1,d0
        //      blo.w  return
        Camera camera = Camera.getInstance();
        if (camera != null && !resultsSpawned) {
            int triggerX = camera.getMaxX() + WALK_OFF_OFFSET;
            if (player.getCentreX() >= triggerX) {
                spawnResultsScreen(player);
            }
        }
    }

    private void spawnResultsScreen(AbstractPlayableSprite player) {
        resultsSpawned = true;
        LOGGER.info("Player off-screen, triggering end of act sequence");

        try {
            AudioManager.getInstance().playMusic(Sonic2Music.ACT_CLEAR.id);
        } catch (Exception e) {
            LOGGER.warning("Failed to play stage clear music: " + e.getMessage());
        }

        LevelManager levelManager = LevelManager.getInstance();
        var levelGamestate = levelManager.getLevelGamestate();
        int elapsedSeconds = levelGamestate != null ? levelGamestate.getElapsedSeconds() : 0;
        int ringCount = player.getRingCount();
        int actNumber = levelManager.getCurrentAct() + 1; // 1-indexed for display
        boolean allRingsCollected = levelManager != null && levelManager.areAllRingsCollected();

        // Spawn the results screen
        ResultsScreenObjectInstance resultsScreen = new ResultsScreenObjectInstance(
                elapsedSeconds, ringCount, actNumber, allRingsCollected);
        ObjectManager objectManager = levelManager.getObjectManager();
        if (objectManager != null) {
            objectManager.addDynamicObject(resultsScreen);
            LOGGER.info("Results screen spawned");
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            super.appendRenderCommands(commands);
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getSignpostRenderer();
        if (renderer == null || !renderer.isReady()) {
            super.appendRenderCommands(commands);
            return;
        }
        renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), false, false);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    public int getCenterX() {
        return spawn.x();
    }

    public int getCenterY() {
        return spawn.y();
    }
}
