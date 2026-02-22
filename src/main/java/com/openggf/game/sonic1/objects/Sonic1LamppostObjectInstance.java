package com.openggf.game.sonic1.objects;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.game.RespawnState;
import com.openggf.game.CheckpointState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Sonic 1 Lamppost / Checkpoint (Object 0x79).
 * <p>
 * From docs/s1disasm/_incObj/79 Lamppost.asm:
 * <ul>
 *   <li>Routine 0 (Lamp_Main): Init - sets frame based on visited state</li>
 *   <li>Routine 2 (Lamp_Blue): Active - checks for player collision</li>
 *   <li>Routine 4 (Lamp_Finish): Terminal - does nothing</li>
 * </ul>
 * <p>
 * Mapping frames (Map_Lamp_internal):
 * <ul>
 *   <li>Frame 0 (.blue): Pole + blue ball (inactive)</li>
 *   <li>Frame 1 (.poleonly): Pole only (ball removed during twirl)</li>
 *   <li>Frame 2 (.redballonly): Red ball only (twirl sparkle child)</li>
 *   <li>Frame 3 (.red): Pole + red ball (visited)</li>
 * </ul>
 */
public class Sonic1LamppostObjectInstance extends AbstractObjectInstance {
    private static final Logger LOGGER = Logger.getLogger(Sonic1LamppostObjectInstance.class.getName());

    // Mapping frame indices from Map_Lamp_internal
    private static final int FRAME_BLUE = 0;
    private static final int FRAME_POLE_ONLY = 1;
    private static final int FRAME_RED = 3;

    // Activation zone dimensions from disassembly:
    // X: player.X - lamp.X + 8 < $10 => abs(dx) < 8
    // Y: player.Y - lamp.Y + $40 < $68 => dy in [-$40, $28)
    private static final int ACTIVATION_HALF_WIDTH = 8; // addq.w #8,d0; cmpi.w #$10,d0
    private static final int ACTIVATION_Y_OFFSET = 0x40; // addi.w #$40,d0
    private static final int ACTIVATION_Y_RANGE = 0x68; // cmpi.w #$68,d0

    // Twirl child Y offset from lamppost center
    // From disassembly: subi.w #$18,lamp_origY(a1)
    static final int TWIRL_Y_OFFSET = 0x18;

    private final int checkpointIndex;
    private final boolean cameraLockFlag;
    private int mappingFrame;
    private boolean activated;
    private boolean twirlActive;

    public Sonic1LamppostObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Lamppost");
        // obSubtype bits 0-6 = lamppost number, bit 7 = camera lock flag
        this.checkpointIndex = spawn.subtype() & 0x7F;
        this.cameraLockFlag = (spawn.subtype() & 0x80) != 0;

        // Lamp_Main: check if already visited
        var checkpointState = LevelManager.getInstance().getCheckpointState();
        if (checkpointState != null && checkpointState.getLastCheckpointIndex() >= this.checkpointIndex) {
            // Already visited - show red, go to Lamp_Finish
            this.activated = true;
            this.mappingFrame = FRAME_RED;
        } else {
            // New lamppost - show blue, go to Lamp_Blue
            this.activated = false;
            this.mappingFrame = FRAME_BLUE;
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (activated || player == null) {
            return; // Lamp_Finish: rts
        }
        checkActivation(player);
    }

    /**
     * Lamp_Blue routine: checks if player is within activation zone.
     * Also handles the case where a higher checkpoint was activated since creation.
     */
    private void checkActivation(AbstractPlayableSprite player) {
        // Check if a higher/equal checkpoint was activated since we spawned
        var checkpointState = LevelManager.getInstance().getCheckpointState();
        if (checkpointState == null) {
            return;
        }
        if (checkpointState.getLastCheckpointIndex() >= this.checkpointIndex) {
            // Another checkpoint was hit - mark as visited (red)
            activated = true;
            mappingFrame = FRAME_RED;
            return;
        }

        // Collision check using center coordinates (matches ROM behavior)
        int px = player.getCentreX();
        int py = player.getCentreY();
        int cx = spawn.x();
        int cy = spawn.y();

        // ROM: move.w (v_player+obX).w,d0; sub.w obX(a0),d0; addq.w #8,d0; cmpi.w #$10,d0
        int dx = px - cx;
        if (dx + ACTIVATION_HALF_WIDTH < 0 || dx + ACTIVATION_HALF_WIDTH >= 16) {
            return;
        }

        // ROM: move.w (v_player+obY).w,d0; sub.w obY(a0),d0; addi.w #$40,d0; cmpi.w #$68,d0
        int dy = py - cy;
        if (dy + ACTIVATION_Y_OFFSET < 0 || dy + ACTIVATION_Y_OFFSET >= ACTIVATION_Y_RANGE) {
            return;
        }

        // Activate!
        activate(player, checkpointState);
    }

    private void activate(AbstractPlayableSprite player,
                          RespawnState respawnState) {
        activated = true;

        // Play lamppost sound: move.w #sfx_Lamppost,d0; jsr (QueueSound2).l
        try {
            AudioManager.getInstance().playSfx(GameSound.CHECKPOINT);
        } catch (Exception e) {
            // Don't let audio failure break game logic
        }

        // Spawn twirl child: jsr (FindFreeObj).l
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager != null) {
            twirlActive = true;
            objectManager.addDynamicObject(new Sonic1LamppostTwirlInstance(this));
        }

        // Set frame to pole only: move.b #1,obFrame(a0)
        mappingFrame = FRAME_POLE_ONLY;

        // Save checkpoint state: bsr.w Lamp_StoreInfo
        if (respawnState instanceof CheckpointState cs) {
            cs.saveCheckpoint(checkpointIndex, spawn.x(), spawn.y(), cameraLockFlag);
        }

        LOGGER.fine("S1 Lamppost " + checkpointIndex + " activated at (" + spawn.x() + ", " + spawn.y() + ")");
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getCheckpointRenderer();
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), false, false);
    }

    @Override
    public int getPriorityBucket() {
        // From disassembly: move.b #5,obPriority(a0)
        return RenderPriority.clamp(5);
    }

    /**
     * Called by the twirl child when its animation completes.
     * Switches from "pole only" (frame 1) to "red" (frame 3).
     * In the original ROM, the twirl child persists at its last position until off-screen,
     * and the lamppost re-creates as red on re-entry. Since our engine destroys the twirl
     * immediately, we switch to red here to match the intended visual result.
     */
    public void onTwirlComplete() {
        twirlActive = false;
        mappingFrame = FRAME_RED;
    }

    public int getCenterX() {
        return spawn.x();
    }

    public int getCenterY() {
        return spawn.y();
    }
}
