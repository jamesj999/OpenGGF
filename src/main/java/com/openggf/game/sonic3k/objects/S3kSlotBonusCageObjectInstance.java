package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomData;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotStageController;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import static com.openggf.physics.TrigLookupTable.cosHex;
import static com.openggf.physics.TrigLookupTable.sinHex;

import java.util.List;

/**
 * Slot-machine bonus cage.
 *
 * <p>ROM reference: {@code sub_4C014} / {@code loc_4BF62}, lines 99308–99557.
 * Implements a 4-state machine: capture → spawn rewards → release → cooldown.
 */
public final class S3kSlotBonusCageObjectInstance extends AbstractObjectInstance {

    // Proximity threshold for capture (ROM: addi.w #$18,d0 / cmpi.w #$30,d0)
    private static final int CAPTURE_RADIUS = 0x18;

    // Maximum simultaneous reward objects alive
    private static final int MAX_ACTIVE_REWARDS = 0x10;

    // Radial angle increments per reward (ROM loc_4C21C, loc_4C0AA)
    private static final int RING_ANGLE_INCREMENT = 0x89;
    private static final int SPIKE_ANGLE_INCREMENT = 0x90;

    // State timers (frames)
    private static final int STATE0_TO_1_TIMER = 0x78;  // 120 frames
    private static final int STATE1_TO_2_TIMER = 8;
    private static final int STATE2_TO_3_TIMER = 8;
    private static final int STATE3_COOLDOWN = 8;

    // Snap-to position (ROM loc_4C026 lines 99391-99392): hard-coded slot bonus center
    private static final short SNAP_X = S3kSlotRomData.SLOT_BONUS_START_X;
    private static final short SNAP_Y = S3kSlotRomData.SLOT_BONUS_START_Y;

    private final S3kSlotStageController controller;

    // 4-state machine fields
    private int cageState;           // 0-3
    private int waitTimer;           // countdown for state transitions
    private int rewardAngle;         // radial spawn angle accumulator
    private int activeRewardCount;   // how many reward objects are alive
    private int rewardsToSpawn;      // how many more rewards to spawn
    private boolean spawnRings;      // true=rings, false=spikes
    private int sfxCounter;          // frame counter for sfx_SlotMachine throttle

    public S3kSlotBonusCageObjectInstance(ObjectSpawn spawn, S3kSlotStageController controller) {
        super(spawn, "S3kSlotBonusCage");
        this.controller = controller;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (!(playerEntity instanceof AbstractPlayableSprite player)) return;
        if (player.isDebugMode()) return;

        switch (cageState) {
            case 0 -> updateCapture(player);
            case 1 -> updateSpawnRewards(player, frameCounter);
            case 2 -> updateRelease(player);
            case 3 -> updateCooldown();
        }
    }

    // -------------------------------------------------------------------------
    // State 0: Capture (loc_4C026, lines 99375-99407)
    // -------------------------------------------------------------------------

    private void updateCapture(AbstractPlayableSprite player) {
        // Skip if player is already under object control (SStage_scalar_result_0 != 0)
        if (player.isObjectControlled()) {
            return;
        }

        if (!isWithinCaptureRange(player)) {
            return;
        }

        // Snap player to slot bonus center
        player.setCentreX(SNAP_X);
        player.setCentreY(SNAP_Y);

        // Clear velocities
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);

        // Lock controls: object_control = 0x81 (controlLocked + objectControlled)
        player.setControlLocked(true);
        player.setObjectControlled(true);
        player.setAir(true);
        player.setOnObject(false);

        // Initialise reward spawn parameters for state 1
        rewardsToSpawn = MAX_ACTIVE_REWARDS;
        activeRewardCount = 0;
        rewardAngle = 0;
        sfxCounter = 0;
        // spawnRings is set externally before capture (defaults false = spikes)

        // Advance to state 1
        waitTimer = STATE0_TO_1_TIMER;
        cageState = 1;

        // TODO: Enable palette cycling flag when palette system is wired
    }

    private boolean isWithinCaptureRange(AbstractPlayableSprite player) {
        int dx = Math.abs(player.getCentreX() - spawn.x());
        int dy = Math.abs(player.getCentreY() - spawn.y());
        return dx < CAPTURE_RADIUS && dy < CAPTURE_RADIUS;
    }

    // -------------------------------------------------------------------------
    // State 1: Spawn Rewards (loc_4C21C + loc_4C0AA, lines 99411-99525)
    // -------------------------------------------------------------------------

    private void updateSpawnRewards(AbstractPlayableSprite player, int frameCounter) {
        // Keep player centered while in spawn state
        player.setCentreX(SNAP_X);
        player.setCentreY(SNAP_Y);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);

        // Only spawn on odd frames (ROM: btst #0,(Level_frame_counter+1))
        if ((frameCounter & 1) != 0) {
            if (activeRewardCount < MAX_ACTIVE_REWARDS && rewardsToSpawn > 0) {
                // ROM loc_4C172/loc_4C0AA: spawn position = center + GetSineCosine(angle) >> 1
                int centerX = spawn.x();
                int centerY = spawn.y();
                if (spawnRings) {
                    int spawnX = centerX + (cosHex(rewardAngle) >> 1);
                    int spawnY = centerY + (sinHex(rewardAngle) >> 1);
                    controller.queueRingRewardAt(spawnX, spawnY, centerX, centerY);
                    rewardAngle = (rewardAngle + RING_ANGLE_INCREMENT) & 0xFF;
                } else {
                    int spawnX = centerX + (cosHex(rewardAngle) >> 1);
                    int spawnY = centerY + (sinHex(rewardAngle) >> 1);
                    controller.queueSpikeRewardAt(spawnX, spawnY, centerX, centerY);
                    rewardAngle = (rewardAngle + SPIKE_ANGLE_INCREMENT) & 0xFF;
                }
                rewardsToSpawn--;
                activeRewardCount++;
            }
        }

        // Play sfx every 16 frames (ROM: btst #3,d2)
        sfxCounter++;
        if ((sfxCounter & 0x0F) == 0) {
            // TODO: Play sfx_SlotMachine when audio is wired
        }

        if (rewardsToSpawn <= 0) {
            // All rewards spawned — advance to state 2
            // TODO: Disable palette cycling flag
            waitTimer = STATE1_TO_2_TIMER;
            cageState = 2;
        }
    }

    // -------------------------------------------------------------------------
    // State 2: Release (loc_4C250, lines 99528-99546)
    // -------------------------------------------------------------------------

    private void updateRelease(AbstractPlayableSprite player) {
        // Wait for rotation to reach aligned angle: Stat_table & 0x3C == 0
        if ((controller.angle() & 0x3C) != 0) {
            return;
        }

        // Compute release velocity: sin/cos of current angle × 4 (ROM: asl.w #2)
        int angle = controller.angle();
        short vx = (short) (TrigLookupTable.cosHex(angle) * 4);
        short vy = (short) (TrigLookupTable.sinHex(angle) * 4);

        player.setXSpeed(vx);
        player.setYSpeed(vy);

        // Clear object control
        player.setObjectControlled(false);
        player.setControlLocked(false);

        // Set airborne
        player.setAir(true);

        // Negate SStage_scalar_index_1 (rotation velocity)
        controller.negateScalar();

        // Advance to state 3
        waitTimer = STATE2_TO_3_TIMER;
        cageState = 3;
    }

    // -------------------------------------------------------------------------
    // State 3: Cooldown (loc_4C292, lines 99550-99557)
    // -------------------------------------------------------------------------

    private void updateCooldown() {
        if (waitTimer > 0) {
            waitTimer--;
            return;
        }

        // Reset to state 0, clear SStage_scalar_result_0
        cageState = 0;
        activeRewardCount = 0;
        rewardsToSpawn = 0;
        rewardAngle = 0;
        sfxCounter = 0;
    }

    // -------------------------------------------------------------------------
    // Accessors for testing
    // -------------------------------------------------------------------------

    /** Returns the current cage state (0-3). */
    public int cageStateForTest() {
        return cageState;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(0);
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Logic-only object for the slot bonus runtime.
    }
}
