package com.openggf.sprites.playable;

import java.util.Objects;

import com.openggf.camera.Camera;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.GameModule;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.PlayerCharacter;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.PerObjectRewindSnapshot.SidekickCpuRewindExtra;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.Direction;

/**
 * CPU-controlled sidekick follower with daisy-chain support.
 */
public class SidekickCpuController {

    // ROM subtracts $44 bytes from Sonic_Pos_Record_Index in TailsCPU_Normal/Flying.
    // That index points at the next free 4-byte slot, while engine historyPos points
    // at the latest written slot, so the equivalent engine lookback is 16 frames.
    static final int ROM_FOLLOW_DELAY_FRAMES = 16;
    // loc_13DD0's push bypass uses the same Stat_table entry fetched after
    // ROM's raw $44-byte displacement (sonic3k.asm:26683-26705). In the
    // engine's per-sprite update order this bypassed control/status word is
    // one slot older than the normal follow target slot.
    private static final int ROM_PUSH_BYPASS_STAT_DELAY_FRAMES = 17;
    /** Fallback used when the sidekick sprite has no PhysicsFeatureSet resolved yet
     *  (e.g. unit tests that bypass the full game-module bootstrap). Matches the S2
     *  value so existing S2 behaviour is preserved. */
    private static final int DEFAULT_HORIZONTAL_SNAP_THRESHOLD =
            PhysicsFeatureSet.SIDEKICK_FOLLOW_SNAP_S2;
    /** Fallback used when the sidekick sprite has no PhysicsFeatureSet resolved yet
     *  (e.g. unit tests that bypass the full game-module bootstrap). Matches the
     *  S2 placeholder so existing S2 traces/tests are unaffected. */
    private static final int DEFAULT_DESPAWN_X =
            PhysicsFeatureSet.SIDEKICK_DESPAWN_X_S2;
    private static final int JUMP_DISTANCE_TRIGGER = 64;
    private static final int JUMP_HEIGHT_THRESHOLD = 32;
    private static final int PUSH_STATUS_GRACE_FRAMES = 16;
    private static final int PUSH_BRIDGE_LOCAL_OBJECT_BAND_Y = 0x80;
    private static final int S3K_ZONE_AIZ = 0;
    private static final int S3K_AIZ_HOLLOW_TREE_OBJECT_ID = 0x03;
    private static final int AIZ_HOLLOW_TREE_CONTEXT_RADIUS_X = 0x80;
    private static final int AIZ_HOLLOW_TREE_CONTEXT_RADIUS_Y = 0x100;
    private static final int LEVEL_START_X_OFFSET = -0x20;
    private static final int LEVEL_START_Y_OFFSET = 4;
    private static final int DESPAWN_TIMEOUT = 300;
    private static final int MANUAL_CONTROL_FRAMES = 600;
    private final int flyAnimId;
    private final int duckAnimId;
    private static final int INPUT_START = 0x20;
    private static final int MANUAL_HELD_MASK = AbstractPlayableSprite.INPUT_UP
            | AbstractPlayableSprite.INPUT_DOWN
            | AbstractPlayableSprite.INPUT_LEFT
            | AbstractPlayableSprite.INPUT_RIGHT
            | AbstractPlayableSprite.INPUT_JUMP;
    private static final int RESPAWN_BYPASS_MASK = AbstractPlayableSprite.INPUT_JUMP | INPUT_START;

    public enum State {
        INIT,
        SPAWNING,
        APPROACHING,
        NORMAL,
        PANIC,
        MGZ_RESCUE_WAIT,       // ROM Tails_CPU_routine $12: clear Ctrl_2_logical while physics continues
        CARRY_INIT,            // ROM carry init; MGZ boss transition uses Tails_CPU_routine $14
        CARRYING,              // ROM routine 0x0E / 0x20 - per-frame carry body
        CATCH_UP_FLIGHT,       // ROM routine 0x02 (Tails_Catch_Up_Flying, sonic3k.asm:26474)
        FLIGHT_AUTO_RECOVERY,  // ROM routine 0x04 (Tails_FlySwim_Unknown, sonic3k.asm:26534)
        DORMANT_MARKER,        // ROM routine 0x0A (locret_13FC0); AIZ1 intro waits off-screen
        // ROM Tails OBJECT routine 0x06 (death state, dispatch loc_1578E in
        // sonic3k.asm:29263). Entered the frame Player_LevelBound calls
        // Kill_Character (sonic3k.asm:21136) on the sidekick.
        DEAD_FALLING
    }

    /**
     * Why despawn was invoked. Selects between the
     * Kill_Character-equivalent flow (LEVEL_BOUNDARY) that zeroes velocities
     * and runs a one-frame death routine before warping to the despawn
     * marker, and the simpler immediate-warp paths used by off-screen
     * timeout, S2 object-id-mismatch, and explicit cleanup callers.
     */
    public enum DespawnCause {
        LEVEL_BOUNDARY,
        OFF_SCREEN_TIMEOUT,
        OBJECT_ID_MISMATCH,
        EXPLICIT
    }

    private static final int SETTLED_FRAME_THRESHOLD = 15;

    private final AbstractPlayableSprite sidekick;
    private AbstractPlayableSprite leader;
    private SidekickRespawnStrategy respawnStrategy;

    private State state = State.INIT;
    private int despawnCounter;
    private int frameCounter;
    private int controlCounter;
    private int controller2Held;
    private int controller2Logical;
    private boolean inputUp;
    private boolean inputDown;
    private boolean inputLeft;
    private boolean inputRight;
    private boolean inputJump;
    private boolean inputJumpPress;
    private boolean jumpingFlag;
    private int minXBound = Integer.MIN_VALUE;
    private int maxXBound = Integer.MIN_VALUE;
    private int maxYBound = Integer.MIN_VALUE;
    private int lastInteractObjectId;
    private int normalFrameCount;
    private int sidekickCount = 1;
    private int normalPushingGraceFrames;
    private boolean suppressNextAirbornePushFollowSteering;
    private boolean aizObjectOrderGracePushBypassThisFrame;
    private int pendingGroundedFollowNudge;
    private int pendingGroundedFollowNudgeFrame = -1;
    private boolean aizIntroDormantMarkerPrimed;
    private boolean suppressNextAizIntroNormalMovement;
    private boolean skipPhysicsThisFrame;
    private boolean cpuFrameCounterFromStoredLevelFrame;
    private NormalStepDiagnostics latestNormalStepDiagnostics;

    // =====================================================================
    // Tails-carry-Sonic support (S3K-only; null trigger = feature disabled)
    // =====================================================================
    private SidekickCarryTrigger carryTrigger;
    private short carryLatchX;
    private short carryLatchY;
    private boolean flyingCarryingFlag;
    private boolean carryParentagePending;
    private int releaseCooldown;
    private boolean mgzCarryIntroAscend;
    private int mgzCarryFlapTimer;
    private boolean mgzReleasedChaseLatched;
    private short mgzReleasedChaseXAccel;
    private short mgzReleasedChaseYAccel;

    // =====================================================================
    // Tails flight/catch-up state (ROM Tails_CPU_flight_timer + steering state)
    // =====================================================================
    private int flightTimer;
    private int catchUpTargetX;
    private int catchUpTargetY;

    public SidekickCpuController(AbstractPlayableSprite sidekick) {
        this(sidekick, null);
    }

    public SidekickCpuController(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader) {
        this.sidekick = sidekick;
        this.leader = leader;
        this.respawnStrategy = new TailsRespawnStrategy(this);
        this.flyAnimId = sidekick.resolveAnimationId(CanonicalAnimation.FLY);
        this.duckAnimId = sidekick.resolveAnimationId(CanonicalAnimation.DUCK);
    }

    public void update(int frameCount) {
        this.frameCounter = resolveCpuFrameCounter(frameCount);

        boolean mgzReleasedCarryCooldown =
                state == State.CARRYING
                        && carryTrigger != null
                        && carryTrigger.usesMgzBossTransitionControl()
                        && !flyingCarryingFlag;

        // Decrement release cooldown every frame regardless of state (applies after carry).
        // MGZ's released carry path decrements inside routine $18 and returns for
        // that frame, matching loc_14534's byte 1(a2) cooldown gate.
        if (releaseCooldown > 0 && !mgzReleasedCarryCooldown) {
            releaseCooldown--;
        }

        if (leader == null) {
            clearInputs();
            carryParentagePending = false;
            return;
        }

        clearInputs();
        carryParentagePending = false;
        if ((controller2Held & MANUAL_HELD_MASK) != 0) {
            controlCounter = MANUAL_CONTROL_FRAMES;
        }

        switch (state) {
            case INIT                 -> updateInit();
            case SPAWNING             -> updateSpawning();
            case APPROACHING          -> updateApproaching();
            case NORMAL               -> updateNormal();
            case PANIC                -> updatePanic();
            case MGZ_RESCUE_WAIT      -> clearInputs();
            case CARRY_INIT           -> updateCarryInit();
            case CARRYING             -> updateCarrying();
            case CATCH_UP_FLIGHT      -> updateCatchUpFlight();
            case FLIGHT_AUTO_RECOVERY -> updateFlightAutoRecovery();
            case DORMANT_MARKER       -> clearInputs();
            case DEAD_FALLING         -> updateDeadFalling();
        }
    }

    private int resolveCpuFrameCounter(int fallbackFrameCount) {
        LevelManager levelManager = sidekick.currentLevelManager();
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        if (fs != null && fs.sidekickCpuUsesLevelFrameCounter()
                && levelManager != null && levelManager.getFrameCounter() > 0) {
            // S3K Tails CPU reads (Level_frame_counter).w inside sprite CPU
            // handlers such as Tails_Catch_Up_Flying (sonic3k.asm:26474-26531).
            // LevelLoop increments the RAM counter before Process_Sprites
            // (sonic3k.asm:7884-7894), while some engine inline sidekick calls
            // receive a caller fallback that is one tick ahead of the stored
            // counter. Use the stored counter for the ROM-visible S3K gates.
            cpuFrameCounterFromStoredLevelFrame = true;
            return levelManager.getFrameCounter();
        }
        cpuFrameCounterFromStoredLevelFrame = false;
        if (fallbackFrameCount > 0) {
            // ROM increments Level_frame_counter before object/player CPU slots
            // (s2.asm:5092, sonic3k.asm:7889). SpriteManager passes that
            // already-incremented cadence; LevelManager stores it later in the
            // engine frame and is one tick stale for Tails' $3F jump gate.
            return fallbackFrameCount;
        }
        if (levelManager != null && levelManager.getFrameCounter() > 0) {
            return levelManager.getFrameCounter();
        }
        if (levelManager != null && levelManager.getObjectManager() != null
                && levelManager.getObjectManager().getFrameCounter() > 0) {
            // Legacy object-manager update paths mirror the same cadence source
            // here when the level counter has not been initialized yet.
            return levelManager.getObjectManager().getFrameCounter();
        }
        return fallbackFrameCount;
    }

    private int resolvePanicPhaseCounter() {
        return frameCounter;
    }

    public void setController2Input(int held, int logical) {
        controller2Held = held;
        controller2Logical = logical;
    }

    /**
     * Comparison-only trace replay diagnostic for the latest normal CPU step.
     * It is never used to drive gameplay state.
     */
    public NormalStepDiagnostics getLatestNormalStepDiagnostics() {
        return latestNormalStepDiagnostics;
    }

    public String formatLatestNormalStepDiagnostics() {
        if (latestNormalStepDiagnostics == null) {
            return "eng-tails-cpu none";
        }
        NormalStepDiagnostics d = latestNormalStepDiagnostics;
        return String.format(
                "eng-tails-cpu f=%d state=%s branch=%s hist=%d/%02d in=%04X stat=%02X push=%02X "
                        + "pre=obj%02X st%02X xv%04X gv%04X "
                        + "gen=%04X postCpu=obj%02X st%02X xv%04X gv%04X "
                        + "postPhys=%s obj%02X st%02X xv%04X gv%04X dx=%04X dy=%04X skip=%s",
                d.frameCounter(),
                d.state(),
                d.followBranch(),
                d.followDelayFrames(),
                d.followHistorySlot(),
                d.recordedInput() & 0xFFFF,
                d.recordedStatus() & 0xFF,
                d.pushBypassStatus() & 0xFF,
                d.preObjectControl() & 0xFF,
                d.preStatus() & 0xFF,
                d.preXVel() & 0xFFFF,
                d.preGroundVel() & 0xFFFF,
                d.generatedInput() & 0xFFFF,
                d.postCpuObjectControl() & 0xFF,
                d.postCpuStatus() & 0xFF,
                d.postCpuXVel() & 0xFFFF,
                d.postCpuGroundVel() & 0xFFFF,
                d.postPhysicsRecorded() ? "seen" : "missing",
                d.postPhysicsObjectControl() & 0xFF,
                d.postPhysicsStatus() & 0xFF,
                d.postPhysicsXVel() & 0xFFFF,
                d.postPhysicsGroundVel() & 0xFFFF,
                d.dx() & 0xFFFF,
                d.dy() & 0xFFFF,
                d.skipFollowSteering());
    }

    public void recordDiagnosticPostPhysics() {
        if (latestNormalStepDiagnostics == null
                || latestNormalStepDiagnostics.frameCounter() != frameCounter) {
            return;
        }
        latestNormalStepDiagnostics = latestNormalStepDiagnostics.withPostPhysics(
                diagnosticStatusByte(),
                diagnosticObjectControlByte(),
                sidekick.getXSpeed(),
                sidekick.getGSpeed());
    }

    /**
     * Returns true only for the AIZ object-order bridge that extends S3K's
     * Status_Push handoff after the live push bit has already cleared locally.
     * ROM loc_13DD0 branches from the current Status_Push bit and preserves the
     * already-loaded Ctrl_2 sample (sonic3k.asm:26702-26705,26775-26785); MGZ
     * F1466-F1470 uses that same no-input deceleration path, so this flag is
     * limited to the AIZ hollow-tree/collapsing-platform ordering bridge.
     */
    public boolean usedAizObjectOrderGracePushBypassThisFrame() {
        return aizObjectOrderGracePushBypassThisFrame;
    }

    private NormalStepDiagnostics beginNormalStepDiagnostics(String branch) {
        latestNormalStepDiagnostics = new NormalStepDiagnostics(
                frameCounter,
                state,
                branch,
                diagnosticStatusByte(),
                diagnosticObjectControlByte(),
                sidekick.getXSpeed(),
                sidekick.getGSpeed(),
                -1,
                -1,
                0,
                0,
                0,
                -1,
                -1,
                0,
                diagnosticStatusByte(),
                diagnosticObjectControlByte(),
                sidekick.getXSpeed(),
                sidekick.getGSpeed(),
                0,
                0,
                (short) 0,
                (short) 0,
                false,
                false);
        return latestNormalStepDiagnostics;
    }

    private void finishNormalStepDiagnostics(NormalStepDiagnostics base,
                                             String branch,
                                             int followDelayFrames,
                                             int followHistorySlot,
                                             int recordedInput,
                                             int recordedStatus,
                                             int pushBypassStatus,
                                             int dx,
                                             int dy,
                                             boolean skipFollowSteering) {
        latestNormalStepDiagnostics = base.withCpuResult(
                branch,
                followDelayFrames,
                followHistorySlot,
                recordedInput,
                recordedStatus,
                pushBypassStatus,
                dx,
                dy,
                diagnosticGeneratedInput(),
                diagnosticStatusByte(),
                diagnosticObjectControlByte(),
                sidekick.getXSpeed(),
                sidekick.getGSpeed(),
                skipFollowSteering);
    }

    private int diagnosticGeneratedInput() {
        int input = 0;
        if (inputUp) input |= AbstractPlayableSprite.INPUT_UP;
        if (inputDown) input |= AbstractPlayableSprite.INPUT_DOWN;
        if (inputLeft) input |= AbstractPlayableSprite.INPUT_LEFT;
        if (inputRight) input |= AbstractPlayableSprite.INPUT_RIGHT;
        if (inputJump) input |= AbstractPlayableSprite.INPUT_JUMP;
        return input;
    }

    private int diagnosticStatusByte() {
        int status = 0;
        if (sidekick.getDirection() == Direction.LEFT) status |= AbstractPlayableSprite.STATUS_FACING_LEFT;
        if (sidekick.getAir()) status |= AbstractPlayableSprite.STATUS_IN_AIR;
        if (sidekick.getRolling()) status |= AbstractPlayableSprite.STATUS_ROLLING;
        if (sidekick.isOnObject()) status |= AbstractPlayableSprite.STATUS_ON_OBJECT;
        if (sidekick.getPushing()) status |= AbstractPlayableSprite.STATUS_PUSHING;
        if (sidekick.isInWater()) status |= AbstractPlayableSprite.STATUS_UNDERWATER;
        return status;
    }

    private int diagnosticObjectControlByte() {
        int objectControl = 0;
        if (sidekick.isObjectControlled()) objectControl |= 0x80;
        if (sidekick.isObjectControlAllowsCpu()) objectControl |= 0x40;
        if (sidekick.isObjectControlSuppressesMovement()) objectControl |= 0x01;
        return objectControl;
    }

    public record NormalStepDiagnostics(
            int frameCounter,
            State state,
            String followBranch,
            int preStatus,
            int preObjectControl,
            short preXVel,
            short preGroundVel,
            int followDelayFrames,
            int followHistorySlot,
            int recordedInput,
            int recordedStatus,
            int pushBypassStatus,
            int dx,
            int dy,
            int generatedInput,
            int postCpuStatus,
            int postCpuObjectControl,
            short postCpuXVel,
            short postCpuGroundVel,
            int postPhysicsStatus,
            int postPhysicsObjectControl,
            short postPhysicsXVel,
            short postPhysicsGroundVel,
            boolean skipFollowSteering,
            boolean postPhysicsRecorded) {

        NormalStepDiagnostics withCpuResult(String branch,
                                            int followDelayFrames,
                                            int followHistorySlot,
                                            int recordedInput,
                                            int recordedStatus,
                                            int pushBypassStatus,
                                            int dx,
                                            int dy,
                                            int generatedInput,
                                            int postCpuStatus,
                                            int postCpuObjectControl,
                                            short postCpuXVel,
                                            short postCpuGroundVel,
                                            boolean skipFollowSteering) {
            return new NormalStepDiagnostics(frameCounter, state, branch,
                    preStatus, preObjectControl, preXVel, preGroundVel,
                    followDelayFrames, followHistorySlot,
                    recordedInput, recordedStatus, pushBypassStatus,
                    dx, dy, generatedInput,
                    postCpuStatus, postCpuObjectControl, postCpuXVel, postCpuGroundVel,
                    postPhysicsStatus, postPhysicsObjectControl, postPhysicsXVel, postPhysicsGroundVel,
                    skipFollowSteering, postPhysicsRecorded);
        }

        NormalStepDiagnostics withPostPhysics(int postPhysicsStatus,
                                              int postPhysicsObjectControl,
                                              short postPhysicsXVel,
                                              short postPhysicsGroundVel) {
            return new NormalStepDiagnostics(frameCounter, state, followBranch,
                    preStatus, preObjectControl, preXVel, preGroundVel,
                    followDelayFrames, followHistorySlot,
                    recordedInput, recordedStatus, pushBypassStatus,
                    dx, dy, generatedInput,
                    postCpuStatus, postCpuObjectControl, postCpuXVel, postCpuGroundVel,
                    postPhysicsStatus, postPhysicsObjectControl, postPhysicsXVel, postPhysicsGroundVel,
                    skipFollowSteering, true);
        }
    }

    private void updateInit() {
        // S3K Tails-carry hook (null trigger = no-op, keeps S1/S2 behaviour).
        if (carryTrigger != null && leader != null) {
            LevelManager lm = sidekick.currentLevelManager();
            if (lm != null) {
                int zone = lm.getCurrentZone();
                int act = lm.getCurrentAct();
                PlayerCharacter pc = resolvePlayerCharacter();
                if (carryTrigger.shouldEnterCarry(zone, act, pc)
                        && carryTrigger.isLeaderAtIntroPosition(leader)) {
                    // ROM loc_13A10 (sonic3k.asm:26414) for CNZ act 0:
                    //   move.w #$C,(Tails_CPU_routine).w
                    //   rts
                    // The INIT handler sets routine=0x0C and RETURNS. It does
                    // NOT fall through into loc_13FC2 (the 0x0C body). That
                    // body (which writes x_vel=$100) only runs on the NEXT
                    // tick. The same-frame fall-through that DOES exist is
                    // 0x0C -> 0x0E (loc_13FC2 -> loc_13FFA, no rts at the
                    // end of loc_13FC2); see updateCarryInit() which mirrors
                    // that fall-through by calling updateCarrying() directly.
                    carryTrigger.applyInitialPlacement(sidekick, leader);
                    // CNZ loc_13A5A sets status=$02 before returning
                    // (sonic3k.asm:26410-26415). The 0x0C body waits until
                    // the next CPU tick, but the current Tails object tick
                    // still runs airborne movement and applies the +$38
                    // first-frame gravity visible in the CNZ trace seed row.
                    sidekick.setAir(true);
                    sidekick.setXSpeed((short) 0);
                    sidekick.setYSpeed((short) 0);
                    sidekick.setGSpeed((short) 0);
                    state = State.CARRY_INIT;
                    return;
                }
            }
        }

        if (shouldEnterAizIntroDormantMarker()) {
            if (aizIntroDormantMarkerPrimed) {
                aizIntroDormantMarkerPrimed = false;
                applyAizIntroDormantMarker();
                return;
            }
            initializeLevelStartSidekickPlacement();
            aizIntroDormantMarkerPrimed = true;
            skipPhysicsThisFrame = true;
            return;
        }

        aizIntroDormantMarkerPrimed = false;
        initializeLevelStartSidekickPlacement();

        state = State.NORMAL;

        // ROM Obj02/Tails object init has already completed by the first live
        // gameplay comparison frame in normal level-start replay paths, so do
        // not spend an engine-only frame with blank controller input. Continue
        // into the normal CPU follow routine.
        updateNormal();
    }

    private void initializeLevelStartSidekickPlacement() {
        // S2 InitPlayers (s2.asm:5192-5195) and S3K SpawnLevelMainSprites
        // (s3.asm:6334-6337) place Player_2 with centre coordinates at
        // Player_1 - $20 X, +4 Y. The engine's level-load reanchor path uses
        // sprite top-left coordinates, so correct the first native CPU tick
        // before follow AI reads the sidekick position.
        sidekick.setCentreXPreserveSubpixel((short) (leader.getCentreX() + LEVEL_START_X_OFFSET));
        sidekick.setCentreYPreserveSubpixel((short) (leader.getCentreY() + LEVEL_START_Y_OFFSET));

        controlCounter = 0;
        despawnCounter = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        lastInteractObjectId = 0;
        sidekick.setForcedAnimationId(-1);
        sidekick.setControlLocked(false);
        sidekick.setObjectControlled(false);
        sidekick.setXSpeed((short) 0);
        sidekick.setYSpeed((short) 0);
        sidekick.setGSpeed((short) 0);
        sidekick.setAir(false);

        // The ROM CPU routine reads Sonic's delayed position buffer
        // (S2 s2.asm:38808-38815, S3K sonic3k.asm:26564-26565).
        // Trace/bootstrap level placement can move the leader after sprite
        // construction, so seed the engine's native buffer before the first
        // follow read instead of reading trace sidekick state back in.
        leader.resetPositionHistory();
    }

    private boolean shouldEnterAizIntroDormantMarker() {
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        if (fs == null || !fs.sidekickRespawnEntersCatchUpFlight()) {
            return false;
        }
        LevelManager lm = sidekick.currentLevelManager();
        Camera camera = sidekick.currentCamera();
        return lm != null
                && camera != null
                && !camera.isLevelStarted()
                && lm.getCurrentZone() == 0
                && lm.getCurrentAct() == 0
                && resolvePlayerCharacter() == PlayerCharacter.SONIC_AND_TAILS;
    }

    private void applyAizIntroDormantMarker() {
        // ROM loc_13A10 (sonic3k.asm:26389-26397) special-cases
        // Current_zone_and_act=0: call sub_13ECA, then overwrite
        // Tails_CPU_routine with $0A and object_control with $83.
        state = State.DORMANT_MARKER;
        despawnCounter = 0;
        controlCounter = 0;
        flightTimer = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        sidekick.setAir(true);
        sidekick.setCentreXPreserveSubpixel(resolveDespawnX());
        sidekick.setCentreYPreserveSubpixel((short) 0);
        sidekick.setDoubleJumpFlag(0);
        sidekick.setControlLocked(true);
        sidekick.setObjectControlled(true);
        sidekick.setForcedAnimationId(flyAnimId);
        lastInteractObjectId = 0;
    }

    public boolean consumeSkipPhysicsThisFrame() {
        boolean result = skipPhysicsThisFrame;
        skipPhysicsThisFrame = false;
        return result;
    }

    /**
     * Per-game snap threshold for the follow-AI input override in updateNormal().
     *
     * <p>Read from the sidekick's physics feature set (ROM parity). Falls back
     * to the S2 default (0x10) when no feature set is resolved yet — this only
     * happens in unit tests that construct a standalone {@code AbstractPlayableSprite}
     * without a game module, and those tests assert the existing S2 threshold.
     */
    private int resolveFollowSnapThreshold() {
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        if (fs == null) {
            return DEFAULT_HORIZONTAL_SNAP_THRESHOLD;
        }
        return fs.sidekickFollowSnapThreshold();
    }

    /**
     * Per-game off-screen marker X-position written by {@link #triggerDespawn()}.
     *
     * <p>Read from the sidekick's physics feature set (ROM parity). Falls back
     * to the S2 placeholder ({@code 0x4000}) when no feature set is resolved
     * yet — this only happens in unit tests that construct a standalone
     * {@code AbstractPlayableSprite} without a game module, and those tests
     * assert the existing S2 placeholder value.
     */
    private short resolveDespawnX() {
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        if (fs == null) {
            return (short) DEFAULT_DESPAWN_X;
        }
        return (short) fs.sidekickDespawnX();
    }

    private PlayerCharacter resolvePlayerCharacter() {
        GameModule gameModule = sidekick.currentGameModule();
        if (gameModule != null) {
            LevelEventProvider lep = gameModule.getLevelEventProvider();
            if (lep instanceof AbstractLevelEventManager alem) {
                return alem.getPlayerCharacter();
            }
        }
        return PlayerCharacter.SONIC_AND_TAILS;
    }

    private void updateSpawning() {
        // Use effective leader for respawn checks — if our direct leader is also
        // despawned, chain-heal to whoever IS available (allows parallel respawn
        // instead of sequential cascade).
        AbstractPlayableSprite target = getEffectiveLeader();
        if (target == null || target.getDead()) {
            return;
        }
        if ((controller2Logical & RESPAWN_BYPASS_MASK) != 0) {
            respawnToApproaching(target);
            return;
        }
        if ((frameCounter & 0x3F) != 0) {
            return;
        }
        if (target.isObjectControlled()) {
            return;
        }
        // Per-game grounded-leader gate: S2 TailsCPU_Spawning checks for
        // grounded / not in water / not roll-jumping (s2.asm:38751-38762);
        // S3K Tails_Catch_Up_Flying does NOT (sonic3k.asm:26474-26486) —
        // it only honours the 64-frame gate, leader.object_control bit 7,
        // and leader.Status_Super. Without gating, CNZ's catch-up handover
        // never fires because Sonic stays airborne after the carry release
        // and the engine's SPAWNING state would block forever.
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        boolean strictGate = fs == null || fs.sidekickSpawningRequiresGroundedLeader();
        if (strictGate) {
            if (target.getAir() || target.getRollingJump() || target.isInWater() || target.isPreventTailsRespawn()) {
                return;
            }
        }
        respawnToApproaching(target);
    }

    private void respawnToApproaching(AbstractPlayableSprite target) {
        boolean started = respawnStrategy.beginApproach(sidekick, target);
        if (!started) {
            return; // Strategy can't start — stay in SPAWNING
        }
        state = State.APPROACHING;
        controlCounter = 0;
        despawnCounter = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        suppressNextAirbornePushFollowSteering = false;
    }

    private void updateApproaching() {
        if (checkDespawn()) {
            normalPushingGraceFrames = 0;
            suppressNextAirbornePushFollowSteering = false;
            return;
        }

        AbstractPlayableSprite effectiveLeader = getEffectiveLeader();
        if (effectiveLeader == null) {
            return;
        }
        if (respawnStrategy.updateApproaching(sidekick, effectiveLeader, frameCounter)) {
            respawnStrategy.onApproachComplete(sidekick, effectiveLeader);
            sidekick.setForcedAnimationId(-1);
            sidekick.setControlLocked(false);
            sidekick.setObjectControlled(false);
            sidekick.setXSpeed((short) 0);
            sidekick.setYSpeed((short) 0);
            sidekick.setGSpeed((short) 0);
            sidekick.setMoveLockTimer(0);
            sidekick.setHurt(false);
            sidekick.setAir(true);
            state = State.NORMAL;
            normalFrameCount = 0;
            despawnCounter = 0;
        }
    }

    private void updateNormal() {
        normalFrameCount++;
        boolean currentPushing = sidekick.getPushing();
        NormalStepDiagnostics diagnostics = beginNormalStepDiagnostics("entry");

        if (leader.getDead()) {
            // ROM loc_13D4A (sonic3k.asm:26656-26665):
            //   cmpi.b #6, (Player_1+routine).w
            //   blo.s  loc_13D78               ; continue NORMAL if routine < 6
            //   move.w #4, (Tails_CPU_routine).w
            // `blo.s` is branch-if-lower (unsigned <); the fall-through path
            // therefore fires only when Sonic's routine byte is >= 6, which
            // engine-side is {@code leader.getDead()}. Routine 0x04 is the
            // hurt bounce (before a potential death) — that case is NOT
            // covered by this ROM branch; Tails stays in NORMAL and the
            // follow AI continues to track the bouncing Sonic. An earlier
            // iteration of this branch also called leader.isHurt() here,
            // which mis-routed AIZ1's Rhinobot-hurt sequence (Sonic hurt
            // for ~43 frames at F1047+) into a spurious flight transition
            // and caused a new first-divergence at AIZ frame 1611.
            flightTimer = 0;
            normalPushingGraceFrames = 0;
            suppressNextAirbornePushFollowSteering = false;
            // S2 TailsCPU_Normal writes obj_control=$81 on dead-Sonic
            // recovery (s2.asm:38910-38915); S3K loc_13D4A does the same
            // before entering Tails_FlySwim_Unknown (sonic3k.asm:26656-26665).
            sidekick.setObjectControlled(true);
            sidekick.setAir(true);
            sidekick.setDoubleJumpFlag(1);
            sidekick.setForcedAnimationId(flyAnimId);
            state = State.FLIGHT_AUTO_RECOVERY;
            finishNormalStepDiagnostics(diagnostics, "leader_dead", -1, -1,
                    0, 0, 0, 0, 0, false);
            return;
        }
        if (sidekick.getDead()) {
            normalPushingGraceFrames = 0;
            suppressNextAirbornePushFollowSteering = false;
            finishNormalStepDiagnostics(diagnostics, "sidekick_dead", -1, -1,
                    0, 0, 0, 0, 0, false);
            return;
        }
        PhysicsFeatureSet featureSet = sidekick.getPhysicsFeatureSet();
        if (sidekick.isHurt()
                && featureSet != null
                && featureSet.sidekickNormalCpuSkipsHurtRoutine()) {
            // S3K Tails_Index dispatches routine 4 to the hurt/object path
            // instead of Tails_Control (docs/skdisasm/sonic3k.asm:26091-26096).
            // The off-screen timeout lives under sub_13EFC, which is called only
            // from Tails_Control's normal CPU route (sonic3k.asm:26159-26190,
            // 26816-26833), so routine-4 frames must not tick despawnCounter.
            updateNormalPushingGrace(currentPushing);
            finishNormalStepDiagnostics(diagnostics, "sidekick_hurt_object_routine", -1, -1,
                    0, 0, 0, 0, 0, false);
            return;
        }
        if (checkDespawn()) {
            finishNormalStepDiagnostics(diagnostics, "despawn", -1, -1,
                    0, 0, 0, 0, 0, false);
            return;
        }
        if (controlCounter != 0) {
            applyManualControl();
            updateNormalPushingGrace(currentPushing);
            finishNormalStepDiagnostics(diagnostics, "manual_control", -1, -1,
                    controller2Held, 0, 0, 0, 0, false);
            return;
        }
        // ROM Tails_Normal Part 2 entry sonic3k.asm:26672:
        //   tst.b   object_control(a0)
        //   bmi.w   loc_13EBE          ; only branch on sign bit (bit 7)
        // ROM's `bmi.w` only suppresses the CPU controller when bit 7 of
        // object_control is set (flight $81, despawn $81, super $83, debug
        // $83). Bits 0-6 (CNZ wire cage's $42, MGZ twisting loop's $43,
        // etc.) leave Tails_CPU_Control running so the auto-jump trigger
        // at loc_13E9C can still fire while the player is "stuck" on the
        // controlling object — that's how ROM launches Tails off the CNZ
        // wire cage (CNZ1 trace F1791: cage's loc_33ADE reads
        // Ctrl_2_logical=$78 set by the auto-jump trigger).
        // Engine's setObjectControlled(true) maps to ANY bit set, so the
        // ROM-bit-7 distinction is carried via objectControlAllowsCpu —
        // bit-7 callers leave it false (default), bits 0-6 callers set
        // it true. See AbstractPlayableSprite#setObjectControlAllowsCpu.
        if (sidekick.isObjectControlled() && !sidekick.isObjectControlAllowsCpu()) {
            updateNormalPushingGrace(currentPushing);
            finishNormalStepDiagnostics(diagnostics, "object_control_bit7", -1, -1,
                    0, 0, 0, 0, 0, false);
            return;
        }

        if (sidekick.getMoveLockTimer() > 0 && sidekick.getGSpeed() == 0) {
            state = State.PANIC;
            normalFrameCount = 0;
        }

        AbstractPlayableSprite effectiveLeader = getEffectiveLeader();
        if (effectiveLeader == null) {
            updateNormalPushingGrace(currentPushing);
            finishNormalStepDiagnostics(diagnostics, "no_effective_leader", -1, -1,
                    0, 0, 0, 0, 0, false);
            return;
        }
        int followStatDelayFrames = resolveFollowStatDelayFrames();
        short recordedInput = effectiveLeader.getInputHistory(followStatDelayFrames);
        byte recordedStatus = effectiveLeader.getStatusHistory(followStatDelayFrames);
        int targetX = effectiveLeader.getCentreX(ROM_FOLLOW_DELAY_FRAMES);
        int targetY = effectiveLeader.getCentreY(ROM_FOLLOW_DELAY_FRAMES);

        // ROM loc_13DA6 (sonic3k.asm:26688-26694): bias the leader-x history
        // target a fixed amount to the LEFT before computing dx, so Tails
        // tracks slightly behind Sonic on flat ground. Suppressed when:
        //   - leader's Status_OnObj bit is set (not just a stale object reference;
        //     sonic3k.asm:26690-26691) — no useful position to lead to.
        //   - leader.ground_vel >= $400 (sonic3k.asm:26692-26693) — leader
        //     is already faster than the follower can chase.
        // S2 has no equivalent (s2.asm:38933 reads d2 directly), so the
        // offset is gated by PhysicsFeatureSet.sidekickFollowLeadOffset().
        //
        // The OnObj read here is mid-frame relative to the leader's tick:
        // ROM only clears Status_OnObj later, in solid-object processing
        // (sub_1FF1E sonic3k.asm:44306-44319, loc_1FFC4 sonic3k.asm:44369-44381),
        // which runs AFTER Tails_CPU_Control. Sonic_Jump (sonic3k.asm:
        // 23288-23354) sets Status_InAir but never clears Status_OnObj.
        // The engine's PlayableSpriteMovement.doJump (line 642) and the
        // air-unseat path in ObjectManager.processInlineObjectForPlayer clear
        // onObject EARLIER in the same frame, so the live isOnObject() value
        // here can already reflect the leader's post-tick state. The
        // frame-start snapshot {@link AbstractPlayableSprite#getOnObjectAtFrameStart()}
        // (captured by SpriteManager.beginPlayableFrame before any player
        // ticks run) is intended to recover the ROM mid-frame view, but is
        // NOT plumbed in here yet — the engine's own frame-start OnObj
        // diverges from ROM's mid-frame OnObj at some object-release
        // transitions (see docs/S3K_KNOWN_BUGS.md, CNZ F7872 / AIZ F7381),
        // so swapping in the snapshot alone regresses AIZ1 around F2021.
        // Resolving that requires aligning the engine's OnObj clear timing
        // with ROM's solid-object-processing-driven clear; until then this
        // gate keeps the existing live read plus {@code !getAir()} heuristic.
        int leadOffset = sidekick.getPhysicsFeatureSet() != null
                ? sidekick.getPhysicsFeatureSet().sidekickFollowLeadOffset()
                : 0;
        // ROM loc_13DA6 (sonic3k.asm:26690-26691, s2.asm:38933+) reads
        // Status_OnObj on the leader BEFORE solid-object processing has run for
        // the frame, so the spec view is the leader's frame-start OnObj snapshot
        // (captured by SpriteManager.beginPlayableFrame). The previous live
        // isOnObject() && !getAir() heuristic compensated for engine paths that
        // SET or KEPT OnObj for an airborne leader (e.g. Sonic3kSpringObjectInstance
        // before the sub_22F98 bclr Status_OnObj fix landed at sonic3k.asm:47723-47724).
        // With the spring trigger now clearing OnObj to match ROM, the snapshot
        // matches ROM's mid-frame view and the air filter is no longer required;
        // ROM btst #Status_OnObj at sonic3k.asm:26690 has no air gate.
        boolean leaderStatusOnObject = effectiveLeader.getOnObjectAtFrameStart();
        if (leadOffset > 0
                && !leaderStatusOnObject
                && effectiveLeader.getGSpeed() < 0x400) {
            targetX -= leadOffset;
        }

        int dx = targetX - sidekick.getCentreX();
        int dy = targetY - sidekick.getCentreY();
        inputLeft = (recordedInput & AbstractPlayableSprite.INPUT_LEFT) != 0;
        inputRight = (recordedInput & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        inputUp = (recordedInput & AbstractPlayableSprite.INPUT_UP) != 0;
        inputDown = (recordedInput & AbstractPlayableSprite.INPUT_DOWN) != 0;
        inputJump = (recordedInput & AbstractPlayableSprite.INPUT_JUMP) != 0;

        byte pushBypassStatus = effectiveLeader.getStatusHistory(ROM_PUSH_BYPASS_STAT_DELAY_FRAMES);
        // ROM loc_13DD0 tests Tails' current Status_Push byte before loc_13E9C
        // (sonic3k.asm:26702-26705), before the follow-left/right steering
        // blocks that can write Ctrl_2 left/right or nudge x_pos
        // (sonic3k.asm:26717-26741). AIZ giant-vine/collapsing-platform object
        // order can leave the engine's transient push flag clear for an inline
        // player tick even though ROM still treats Tails as pushing: the platform
        // resolves solid/release before its collapse transition (sonic3k.asm:
        // 44784-44883), and the vine handles P1 then P2 after capture has cleared
        // velocities once but does not keep clearing them while held
        // (sonic3k.asm:46481-46743,46749-46950). For follow steering, bridge the
        // engine-side clear only while Tails is still in the same local object
        // band as the delayed leader target. The AIZ collapsing-platform/vine
        // bridge at F2709-F2720 has Tails object_control=$20/status=$20 and a
        // small vertical delta; F3075 is far below the target, so ROM's normal
        // height gate (sonic3k.asm:26768-26775) has already left this bridge
        // context and the current Ctrl_2 RIGHT pulse remains live. Grounded
        // Tails_InputAcceleration_Path converts that into +$000C ground_vel/x_vel
        // (sonic3k.asm:27798-27805,28103-28122).
        boolean currentPushBypass = !sidekick.getAir()
                && currentPushing
                && (pushBypassStatus & AbstractPlayableSprite.STATUS_PUSHING) == 0;
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        boolean pushBypassGraceEnabled = fs != null && fs.sidekickPushBypassUsesGraceStatus();
        boolean gracePushBypass = !sidekick.getAir()
                && pushBypassGraceEnabled
                && normalPushingGraceFrames > 0
                && (pushBypassStatus & AbstractPlayableSprite.STATUS_PUSHING) == 0;
        boolean localGracePushBypass = gracePushBypass
                && Math.abs(dy) < PUSH_BRIDGE_LOCAL_OBJECT_BAND_Y;
        boolean airbornePushHandoff = false;
        if (suppressNextAirbornePushFollowSteering) {
            // After loc_13DD0 branches to loc_13E9C, Tails_Spin_Freespace runs
            // next frame when Status_InAir|Roll is set (sonic3k.asm:27765-27784).
            // The ROM-side object/platform handoff has not yet produced a fresh
            // follow-steering Ctrl_2 RIGHT bit for Tails_InputAcceleration_Freespace
            // (sonic3k.asm:28330-28401), so suppress only that first airborne tick.
            airbornePushHandoff = sidekick.getAir();
            suppressNextAirbornePushFollowSteering = false;
        }
        boolean skipFollowSteering = currentPushBypass
                || localGracePushBypass
                || airbornePushHandoff;
        String followBranch = currentPushBypass ? "current_push_bypass"
                : localGracePushBypass ? "grace_push_bypass"
                : airbornePushHandoff ? "airborne_push_handoff"
                : leaderStatusOnObject ? "leader_on_object"
                : effectiveLeader.getGSpeed() >= 0x400 ? "leader_fast"
                : "follow_steering";
        // ROM loc_13DD0 only uses d4 (the delayed status byte) to decide
        // whether to bypass FollowLeft/FollowRight. The Ctrl_2 word in d1 was
        // already loaded from the same Stat_table entry and is preserved when
        // branching to loc_13E9C (sonic3k.asm:26696-26705,26775-26785; S2
        // s2.asm:38939-38946). Do not re-read an older input slot here: CNZ1
        // F3925 has Status_Push set but still carries delayed RIGHT in d1, and
        // Tails_InputAcceleration_Path consumes it for +$000C ground speed
        // (sonic3k.asm:27798-27805,28103-28122).
        //
        // The S3K-only AIZ grace/airborne handoff is not a direct ROM branch;
        // it is an engine object-order bridge for AIZ's transient push clear.
        // Keep its older input sample only in the AIZ hollow-tree/collapsing-
        // platform context where object order can leave the engine one sample
        // behind the ROM handoff (sonic3k.asm:26690-26705,41668-41679,
        // 41793-41818,43649-43810). MGZ F1466 has the same delayed
        // Status_OnObj bit but ROM keeps the already-loaded d1 sample
        // (input=0000/stat=08) through loc_13DD0; re-reading the older sample
        // manufactures a right input and over-accelerates Tails.
        // Grounded grace with no AIZ object-order status is the CNZ cylinder
        // release shape instead; it preserves the already-loaded d1 Ctrl_2 word
        // after Tails_CPU_Control, and the cylinder/P2 and path-acceleration
        // paths consume that same sample (sonic3k.asm:26195-26208,
        // 67656-67672,27798-27805,28103-28122).
        boolean aizObjectOrderGrace = localGracePushBypass
                && isAizHollowTreeFollowSteeringContext(effectiveLeader)
                && (leaderStatusOnObject
                || (recordedStatus & AbstractPlayableSprite.STATUS_ON_OBJECT) != 0);
        aizObjectOrderGracePushBypassThisFrame = aizObjectOrderGrace;
        if (airbornePushHandoff || aizObjectOrderGrace) {
            recordedInput = effectiveLeader.getInputHistory(ROM_PUSH_BYPASS_STAT_DELAY_FRAMES);
            inputLeft = (recordedInput & AbstractPlayableSprite.INPUT_LEFT) != 0;
            inputRight = (recordedInput & AbstractPlayableSprite.INPUT_RIGHT) != 0;
            inputUp = (recordedInput & AbstractPlayableSprite.INPUT_UP) != 0;
            inputDown = (recordedInput & AbstractPlayableSprite.INPUT_DOWN) != 0;
            inputJump = (recordedInput & AbstractPlayableSprite.INPUT_JUMP) != 0;
        }
        if (!skipFollowSteering) {
            // ROM enters FollowLeft/FollowRight for any nonzero dx; the per-game
            // snap threshold only decides whether Tails overrides left/right input,
            // not whether the +/-1 x_pos nudge runs.
            //
            // S2:  0x10 (s2.asm:38952 TailsCPU_Normal_FollowLeft,
            //            s2.asm:38967 TailsCPU_Normal_FollowRight).
            // S3K: 0x30 (sonic3k.asm:26712 loc_13DF2,
            //            sonic3k.asm:26729 loc_13E26).
            int snapThreshold = resolveFollowSnapThreshold();
            int steeringDx = resolveFollowSteeringDx(dx, effectiveLeader, leadOffset, leaderStatusOnObject,
                    snapThreshold);
            if (steeringDx < 0) {
                int absDx = -steeringDx;
                if (absDx >= snapThreshold) {
                    inputLeft = true;
                    inputRight = false;
                }
            } else if (steeringDx > 0) {
                if (steeringDx >= snapThreshold) {
                    inputRight = true;
                    inputLeft = false;
                }
            } else if ((recordedStatus & AbstractPlayableSprite.STATUS_FACING_LEFT) != 0) {
                sidekick.setDirection(Direction.LEFT);
            } else {
                sidekick.setDirection(Direction.RIGHT);
            }
            int nudgeDx = resolveFollowNudgeDx(dx, effectiveLeader);
            if (nudgeDx < 0
                    && sidekick.getDirection() == Direction.LEFT
                    // ROM loc_13E0A gates the positional nudge on
                    // object_control bit 0, not on the broader control
                    // lock state (sonic3k.asm:26722-26724).
                    && !sidekick.isObjectControlSuppressesMovement()) {
                if (sidekick.getGSpeed() != 0) {
                    sidekick.shiftX(-1);
                } else if (sidekick.getAir()) {
                    pendingGroundedFollowNudge = -1;
                    pendingGroundedFollowNudgeFrame = frameCounter;
                }
            } else if (nudgeDx > 0
                    && sidekick.getDirection() == Direction.RIGHT
                    // ROM loc_13E34 gates the positional nudge on
                    // object_control bit 0, not on the broader control
                    // lock state (sonic3k.asm:26739-26741).
                    && !sidekick.isObjectControlSuppressesMovement()) {
                if (sidekick.getGSpeed() != 0) {
                    sidekick.shiftX(1);
                } else if (sidekick.getAir()) {
                    pendingGroundedFollowNudge = 1;
                    pendingGroundedFollowNudgeFrame = frameCounter;
                }
            }
        }

        if (jumpingFlag) {
            inputJump = true;
            if (!sidekick.getAir()) {
                jumpingFlag = false;
            }
        }

        if (!jumpingFlag) {
            // ROM runs the auto-jump distance/height/gate path regardless of
            // Status_InAir; the in-air check only belongs to the existing
            // Tails_CPU_auto_jump_flag clear path above (S2 s2.asm:38994-39022,
            // S3K sonic3k.asm:26753-26782). CNZ1 uses this when delayed Sonic
            // jump input makes Tails airborne one frame before the auto-jump
            // latch itself fires.
            // ROM sonic3k.asm:26702-26705 (loc_13DD0) and s2.asm:38943-38946
            // (TailsCPU_Normal): if Tails is currently pushing AND the leader was
            // NOT pushing 16 frames ago, branch directly to the auto-jump trigger
            // gate (loc_13E9C / TailsCPU_Normal_FilterAction_Part2), bypassing the
            // dx/dy distance and height gates entirely. Without this bypass, Tails
            // gets stuck pushing against terrain whenever Sonic has moved past it,
            // because dx-distance is typically too large to pass the standard
            // distance gate. AIZ trace F2721 reproduces this: Tails was pushing
            // (status=0x20) at the end of F2720, Sonic was on an object (status=0x08
            // = OnObject, not Pushing) 16 frames before, dx=0x4D so distance gate
            // would fail, but ROM auto-jumps via the bypass and y_speed becomes
            // -0x680 (Tails_Jump initial velocity).
            //
            // Auto-jump still needs the short engine-side push continuity bridge,
            // but only in the same local object band used for follow steering.
            // AIZ F2721 has Tails object_control=$20/status=$20 next to the
            // delayed platform target, so ROM still reaches loc_13E9C through
            // the push bypass. AIZ F3169 has the same stale engine-side grace
            // while Tails is far below the delayed leader target; ROM falls
            // through the normal loc_13E7C distance/height gates instead and
            // leaves Ctrl_2_logical as RIGHT (sonic3k.asm:26702-26705,
            // 26760-26783, 27798-27805, 28103-28122).
            boolean pushingBypass = currentPushBypass || localGracePushBypass;
            boolean passesDistanceGate = pushingBypass
                    || (frameCounter & 0xFF) == 0
                    || Math.abs(dx) < JUMP_DISTANCE_TRIGGER;
            boolean passesHeightGate = pushingBypass
                    || dy <= -JUMP_HEIGHT_THRESHOLD;
            if (passesDistanceGate
                    && passesHeightGate
                    && (frameCounter & 0x3F) == 0
                    && sidekick.getAnimationId() != duckAnimId) {
                inputJump = true;
                inputJumpPress = true;
                jumpingFlag = true;
                if (aizObjectOrderGrace && pushBypassGraceEnabled) {
                    // The first airborne tick after the engine-side AIZ bridge
                    // may still be one object-order sample ahead of ROM. Do not
                    // apply this suppression to MGZ's normal push-jump handoff:
                    // Tails_Stand_Freespace/Tails_InputAcceleration_Freespace
                    // consumes live follow steering immediately after the jump
                    // (MGZ1 F1472, input=7808, x_vel $00A4->$00BC;
                    // sonic3k.asm:26712-26741,28330-28401).
                    suppressNextAirbornePushFollowSteering = true;
                }
            }
        }

        if (suppressNextAizIntroNormalMovement) {
            // AIZ1 intro releases the dormant marker before Tails' first
            // visible follow pulse. Consume this on the first post-release CPU
            // tick regardless of generated input: ROM's resize write occurs
            // outside Tails normal movement, so the release-side object frame is
            // suppressed but the following frame's fresh Ctrl_2 state is usable.
            suppressNextAizIntroNormalMovement = false;
            skipPhysicsThisFrame = true;
        }
        updateNormalPushingGrace(currentPushing);
        int reportedDelayFrames = (airbornePushHandoff || aizObjectOrderGrace)
                ? ROM_PUSH_BYPASS_STAT_DELAY_FRAMES
                : followStatDelayFrames;
        finishNormalStepDiagnostics(diagnostics, followBranch,
                reportedDelayFrames,
                effectiveLeader.getHistorySlotIndex(reportedDelayFrames),
                recordedInput & 0xFFFF,
                recordedStatus & 0xFF,
                pushBypassStatus & 0xFF,
                dx,
                dy,
                skipFollowSteering);
    }

    private int resolveFollowStatDelayFrames() {
        // ROM Sonic_RecordPos writes Pos_table and Stat_table with the same
        // Pos_table_index (sonic3k.asm:22124-22136), then Tails_Normal reads the
        // delayed stat word in loc_13DD0 (sonic3k.asm:26683-26700). The engine
        // updates CPU sidekicks before the main player, so the latest completed
        // player history entry already corresponds to the previous ROM sample.
        return ROM_FOLLOW_DELAY_FRAMES;
    }

    private int resolveFollowSteeringDx(int dx, AbstractPlayableSprite effectiveLeader, int leadOffset,
            boolean leaderStatusOnObject, int snapThreshold) {
        if (dx >= 0
                || sidekick.getPhysicsFeatureSet() == null
                || sidekick.getPhysicsFeatureSet().sidekickFollowLeadOffset() <= 0
                || !sidekick.getAir()
                || !sidekick.getRolling()
                || effectiveLeader.getOnObjectAtFrameStart()
                || effectiveLeader.getGSpeed() >= 0x400
                || !isAizHollowTreeFollowSteeringContext(effectiveLeader)) {
            return dx;
        }

        // Tails_Normal reads the delayed Pos_table entry before applying the
        // FollowLeft/FollowRight threshold (sonic3k.asm:26683-26732). In AIZ,
        // Obj_AIZHollowTree runs later in Process_Sprites after Player_1 and
        // Player_2 (sonic3k.asm:35965-35988,43649-43655) and rewrites both
        // player slots with AIZTree_SetPlayerPos (sonic3k.asm:43776-43810).
        // During the airborne release, the engine's completed player history
        // can sit one object-order sample behind the ROM-visible handoff. Use
        // the adjacent newer sample only when it keeps the same follow side but
        // falls back below S3K's $30 steering override, preserving the delayed
        // Ctrl_2 RIGHT/jump bits instead of manufacturing a LEFT pulse.
        // Do not apply this bridge to the fast-leader branch: ROM loc_13DA6 only
        // skips the S3K lead bias when leader ground_vel >= $400, then still runs
        // FollowLeft/FollowRight from the original delayed Pos_table sample
        // (sonic3k.asm:26692-26694,26707-26732).
        int objectOrderTargetX = effectiveLeader.getCentreX(ROM_FOLLOW_DELAY_FRAMES - 1);
        if (leadOffset > 0
                && !leaderStatusOnObject
                && effectiveLeader.getGSpeed() < 0x400) {
            objectOrderTargetX -= leadOffset;
        }
        int objectOrderDx = objectOrderTargetX - sidekick.getCentreX();
        if (objectOrderDx <= 0 && -objectOrderDx < snapThreshold) {
            return objectOrderDx;
        }
        return dx;
    }

    private int resolveFollowNudgeDx(int dx, AbstractPlayableSprite effectiveLeader) {
        if (dx <= 0
                && sidekick.getPhysicsFeatureSet() != null
                && sidekick.getPhysicsFeatureSet().sidekickFollowLeadOffset() > 0
                // ROM loc_13DA6 branches at Status_OnObj before applying the
                // S3K follow bias (sonic3k.asm:26690-26694). While that bit is
                // set, keep the same delayed position sample for the +/-1 nudge
                // instead of substituting an adjacent AIZ object-order sample.
                && !effectiveLeader.getOnObjectAtFrameStart()
                // The fast-leader branch uses the same unadjusted delayed d2 for
                // both steering and the loc_13E0A/loc_13E34 +/-1 x_pos nudge
                // (sonic3k.asm:26692-26694,26707-26741).
                && effectiveLeader.getGSpeed() < 0x400
                && isAizHollowTreeFollowSteeringContext(effectiveLeader)) {
            // S3K reads Pos_table_index-$44 for the positional follow target
            // (sonic3k.asm:26683-26689), then applies the +1 x_pos nudge in
            // FollowRight when Tails faces right and object_control bit 0 is
            // clear (sonic3k.asm:26734-26741). Around AIZ's hollow-tree handoff,
            // Sonic is on Obj_AIZHollowTree (sonic3k.asm:43605,43649-43655);
            // that object-order player update can leave the nudge sign on either
            // adjacent completed leader-position sample while the delayed
            // input/status sample remains aligned.
            int sidekickX = sidekick.getCentreX();
            int olderObjectOrderDx = resolveObjectOrderNudgeDx(effectiveLeader, ROM_FOLLOW_DELAY_FRAMES + 1,
                    sidekickX);
            if (olderObjectOrderDx > 0) {
                return olderObjectOrderDx;
            }
            int newerObjectOrderDx = resolveObjectOrderNudgeDx(effectiveLeader, ROM_FOLLOW_DELAY_FRAMES - 1,
                    sidekickX);
            if (newerObjectOrderDx > 0) {
                return newerObjectOrderDx;
            }
        }
        return dx;
    }

    private int resolveObjectOrderNudgeDx(AbstractPlayableSprite effectiveLeader, int delayFrames, int sidekickX) {
        int targetX = effectiveLeader.getCentreX(delayFrames);
        int leadOffset = sidekick.getPhysicsFeatureSet() != null
                ? sidekick.getPhysicsFeatureSet().sidekickFollowLeadOffset()
                : 0;
        // ROM loc_13DA6 (sonic3k.asm:26690-26691, s2.asm:38933+) reads
        // Status_OnObj on the leader BEFORE solid-object processing has run for
        // the frame, so the spec view is the leader's frame-start OnObj snapshot
        // (captured by SpriteManager.beginPlayableFrame). The previous live
        // isOnObject() && !getAir() heuristic compensated for engine paths that
        // SET or KEPT OnObj for an airborne leader (e.g. Sonic3kSpringObjectInstance
        // before the sub_22F98 bclr Status_OnObj fix landed at sonic3k.asm:47723-47724).
        // With the spring trigger now clearing OnObj to match ROM, the snapshot
        // matches ROM's mid-frame view and the air filter is no longer required;
        // ROM btst #Status_OnObj at sonic3k.asm:26690 has no air gate.
        boolean leaderStatusOnObject = effectiveLeader.getOnObjectAtFrameStart();
        if (leadOffset > 0
                && !leaderStatusOnObject
                && effectiveLeader.getGSpeed() < 0x400) {
            targetX -= leadOffset;
        }
        return targetX - sidekickX;
    }

    private boolean isAizHollowTreeFollowSteeringContext(AbstractPlayableSprite effectiveLeader) {
        return isAizHollowTreeZone()
                && (isAizHollowTreeNear(sidekick.getCentreX(), sidekick.getCentreY())
                    || isAizHollowTreeFollowNudgeContext(effectiveLeader));
    }

    private boolean isAizHollowTreeFollowNudgeContext(AbstractPlayableSprite effectiveLeader) {
        if (!isAizHollowTreeZone()) {
            return false;
        }
        if (effectiveLeader.getLatchedSolidObjectId() == S3K_AIZ_HOLLOW_TREE_OBJECT_ID) {
            return true;
        }
        ObjectInstance latched = effectiveLeader.getLatchedSolidObjectInstance();
        if (latched != null && latched.getSpawn() != null
                && latched.getSpawn().objectId() == S3K_AIZ_HOLLOW_TREE_OBJECT_ID) {
            return true;
        }

        return isAizHollowTreeNear(effectiveLeader.getCentreX(), effectiveLeader.getCentreY());
    }

    private boolean isAizHollowTreeZone() {
        LevelManager levelManager = sidekick.currentLevelManager();
        return levelManager != null
                && levelManager.getFeatureZoneId() == S3K_ZONE_AIZ
                && levelManager.getFeatureActId() == 0;
    }

    private boolean isAizHollowTreeNear(int x, int y) {
        LevelManager levelManager = sidekick.currentLevelManager();
        if (levelManager == null) {
            return false;
        }
        ObjectManager objectManager = levelManager.getObjectManager();
        if (objectManager == null) {
            return false;
        }
        for (ObjectInstance object : objectManager.getActiveObjects()) {
            if (object == null || object.isDestroyed() || object.getSpawn() == null) {
                continue;
            }
            if (object.getSpawn().objectId() != S3K_AIZ_HOLLOW_TREE_OBJECT_ID) {
                continue;
            }
            if (Math.abs(object.getX() - x) <= AIZ_HOLLOW_TREE_CONTEXT_RADIUS_X
                    && Math.abs(object.getY() - y) <= AIZ_HOLLOW_TREE_CONTEXT_RADIUS_Y) {
                return true;
            }
        }
        return false;
    }

    private void updateNormalPushingGrace(boolean currentPushing) {
        if (currentPushing) {
            normalPushingGraceFrames = PUSH_STATUS_GRACE_FRAMES;
        } else if (normalPushingGraceFrames > 0) {
            normalPushingGraceFrames--;
        }
    }

    private void updatePanic() {
        if (checkDespawn()) {
            return;
        }
        if (controlCounter != 0) {
            applyManualControl();
            return;
        }
        if (sidekick.getMoveLockTimer() > 0) {
            return;
        }

        // ROM: tst.b spin_dash_flag(a0) (sonic3k.asm:26858). Player spindash
        // and S3K AutoSpin both write that byte; the engine stores AutoSpin's
        // value in pinballMode because it also preserves rolling on landing.
        if (!sidekick.getSpindash() && !sidekick.getPinballMode()) {
            if (sidekick.getGSpeed() != 0) {
                return;
            }
            sidekick.setDirection(leader.getCentreX() < sidekick.getCentreX() ? Direction.LEFT : Direction.RIGHT);
            inputDown = true;
            int phase = resolvePanicPhaseCounter() & 0x7F;
            if (phase == 0) {
                clearInputs();
                state = State.NORMAL;
                normalFrameCount = 0;
                return;
            }
            if (sidekick.getAnimationId() == duckAnimId) {
                inputJump = true;
                inputJumpPress = true;
            }
            return;
        }

        inputDown = true;
        int phase = resolvePanicPhaseCounter() & 0x7F;
        if (phase == 0) {
            clearInputs();
            state = State.NORMAL;
            normalFrameCount = 0;
            return;
        }
        if ((phase & 0x1F) == 0) {
            inputJump = true;
            inputJumpPress = true;
        }
    }

    /** ROM routine 0x0C. Mirrors sub_1459E (pickup) then falls through to 0x20. */
    private void updateCarryInit() {
        // Tails's per-carry state
        sidekick.setAir(true);
        sidekick.setXSpeed(carryTrigger.carryInitXVel());
        sidekick.setYSpeed((short) 0);
        sidekick.setGSpeed((short) 0);
        sidekick.setDoubleJumpFlag(1);
        sidekick.setDoubleJumpProperty((byte) 0xF0);
        // CNZ carry setup (loc_13A5A -> loc_13FC2) does not set
        // object_control on Tails; the CPU routine drives Ctrl_2_logical, and
        // that input must remain visible to Tails_Move_FlySwim.
        sidekick.setControlLocked(false);
        sidekick.setForcedAnimationId(flyAnimId);
        pickupLeaderForCarry();

        // Initialize the latch
        flyingCarryingFlag = true;
        mgzCarryIntroAscend = carryTrigger.usesMgzBossTransitionControl();
        mgzCarryFlapTimer = 0;
        releaseCooldown = 0;

        state = State.CARRYING;
        // ROM 0x0C -> 0x20 fall-through: one tick of the body this same frame.
        updateCarrying();
    }

    /** ROM routines 0x0E / 0x20 body. Runs each carry frame. */
    private void updateCarrying() {
        // ROM order inside Tails_Carry_Sonic:

        // Tails's hurt/death/drown object routines bypass Tails_CPU_Control and
        // immediately clear Player_1 object_control plus Flying_carrying_Sonic_flag
        // before running hurt/death motion (sonic3k.asm:29180, 29272, 29316).
        if (sidekick.isHurt() || sidekick.getDead()) {
            releaseCarryForCarrierDisabled();
            return;
        }

        if (carryTrigger.usesMgzBossTransitionControl() && !flyingCarryingFlag) {
            updateMgzReleasedCarry();
            return;
        }

        // 1. Hurt/dead (Sonic routine >= 4)
        if (leader.isHurt() || leader.getDead()) {
            carryParentagePending = false;
            releaseCarry(carryTrigger.carryLatchReleaseCooldownFrames());
            return;
        }

        // 2. External velocity change (release path C: latch mismatch)
        if (leader.getXSpeed() != carryLatchX || leader.getYSpeed() != carryLatchY) {
            carryParentagePending = false;
            releaseCarry(carryTrigger.carryLatchReleaseCooldownFrames());
            return;
        }

        // 3. A/B/C just-pressed (release path B)
        if (leader.isJumpJustPressed()) {
            carryParentagePending = false;
            performJumpRelease();
            return;
        }

        // 4. Ground release (release path A): Sonic in-air bit clear
        if (!leader.getAir()) {
            // ROM loc_14016 (sonic3k.asm:26923-26946) runs BEFORE Tails_Carry_Sonic
            // branches to loc_1445A. It resets Tails's own airborne state so the
            // next tick runs Tails_FlyingSwimming from a freshly-zeroed velocity
            // (y_vel=0 + Tails_Move_FlySwim's +0x08 gravity -> trace y_vel=0x008):
            //   move.w #0, x_vel(a0)     ; Tails
            //   move.w #0, y_vel(a0)     ; Tails
            //   move.w #0, ground_vel(a0); Tails
            //   move.b #1<<Status_InAir, status(a0)  ; Tails stays airborne
            // double_jump_flag(a0) is deliberately NOT cleared here; the flight
            // physics persist until Tails actually lands.
            sidekick.setXSpeed((short) 0);
            sidekick.setYSpeed((short) 0);
            sidekick.setGSpeed((short) 0);
            sidekick.setAir(true);

            // ROM loc_1445A (sonic3k.asm:27268): move.w #-$100, y_vel(a1)
            // Small upward impulse on the carried Sonic before clearing
            // object_control, matching ROM fall-through into loc_14460/loc_14466.
            leader.setYSpeed((short) -0x100);
            carryParentagePending = false;
            releaseCarry(0);
            return;
        }

        if (carryTrigger.usesMgzBossTransitionControl()) {
            updateMgzBossTransitionCarryInput();
            carryParentagePending = true;
            return;
        }

        // Synthetic input injection. For S3K carry states, resolveCpuFrameCounter()
        // reads the stored level counter after replay/bootstrap alignment, which
        // already corresponds to the ROM-visible (Level_frame_counter+1) cadence
        // used by loc_13FFA. CNZ pulses Right every 32 frames; other carry
        // triggers may pulse A/B/C instead.
        if ((frameCounter & carryTrigger.carryInputInjectMask()) == 0) {
            if (carryTrigger.carryInjectsJump()) {
                inputJump = true;
                inputJumpPress = true;
            } else {
                inputRight = true;
            }
        }

        // ROM loc_13FC2 writes x_vel=$100 only when carry starts. The
        // loc_13FFA body only injects a right press every 32 frames, letting
        // normal Tails flight movement raise x_vel ($118/$130/$148...).
        carryParentagePending = true;
    }

    private void updateMgzBossTransitionCarryInput() {
        // ROM loc_14106 ($16): keep flight timer full and pulse A/B/C every
        // eight frames until Tails reaches Camera_Y+$90.
        sidekick.setDoubleJumpProperty((byte) 0xF0);
        if (mgzCarryIntroAscend) {
            if (((frameCounter + 1) & 0x07) == 0) {
                inputJump = true;
                inputJumpPress = true;
            }
            Camera camera = sidekick.currentCamera();
            if (camera != null
                    && ((camera.getY() & 0xFFFF) + 0x90) >= (sidekick.getCentreY() & 0xFFFF)) {
                mgzCarryIntroAscend = false;
                mgzCarryFlapTimer = 0;
            }
            return;
        }

        // ROM loc_14164 ($18): P1 has coarse control over carrier Tails.
        inputLeft = leader.isLeftPressed();
        inputRight = leader.isRightPressed();
        int threshold = leader.isDownPressed() ? 0xC0
                : leader.isUpPressed() ? 0x20
                : 0x58;
        mgzCarryFlapTimer++;
        if (mgzCarryFlapTimer >= threshold) {
            mgzCarryFlapTimer = 0;
            inputJump = true;
            inputJumpPress = true;
        }
    }

    private void updateMgzReleasedCarry() {
        sidekick.setAir(true);
        sidekick.setDoubleJumpProperty((byte) 0xF0);
        sidekick.setForcedAnimationId(flyAnimId);
        carryParentagePending = false;

        // ROM loc_142E2 runs Tails's released rescue/chase body before
        // falling through to Tails_Carry_Sonic's cooldown/proximity probe.
        updateMgzReleasedCarryChase();

        // ROM loc_14534: if byte 1(a2) is nonzero, decrement and return
        // only while it remains nonzero. When the decrement reaches zero, the
        // same frame continues into the proximity pickup test.
        if (releaseCooldown > 0) {
            releaseCooldown--;
            if (releaseCooldown > 0) {
                return;
            }
        }

        if (canMgzRegrabLeader()) {
            pickupLeaderForCarry();
            flyingCarryingFlag = true;
            carryParentagePending = true;
            mgzReleasedChaseLatched = false;
            return;
        }
    }

    private void releaseCarryForCarrierDisabled() {
        boolean mgzBossTransitionCarry = carryTrigger != null && carryTrigger.usesMgzBossTransitionControl();
        if (leader != null && flyingCarryingFlag) {
            leader.setObjectControlled(false);
            leader.setForcedAnimationId(-1);
            leader.setAir(true);
        }
        flyingCarryingFlag = false;
        carryParentagePending = false;
        mgzCarryIntroAscend = false;
        mgzCarryFlapTimer = 0;
        mgzReleasedChaseLatched = false;
        releaseCooldown = 0;
        if (!mgzBossTransitionCarry) {
            state = State.NORMAL;
            normalFrameCount = 0;
        }
    }

    private boolean canMgzRegrabLeader() {
        int dxWindow = signedWord(leader.getCentreX() - sidekick.getCentreX() + 0x10);
        if (dxWindow < 0 || dxWindow >= 0x20) {
            return false;
        }
        int dyWindow = signedWord(leader.getCentreY() - sidekick.getCentreY() - 0x20);
        if (dyWindow < 0 || dyWindow >= 0x10) {
            return false;
        }
        return !leader.isObjectControlled()
                && !leader.isHurt()
                && !leader.getDead()
                && !leader.isDebugMode()
                && !leader.getSpindash();
    }

    private void updateMgzReleasedCarryChase() {
        if (!mgzReleasedChaseLatched) {
            boolean leaderOnScreen = leader.hasRenderFlagOnScreenState()
                    ? leader.isRenderFlagOnScreen()
                    : isSpriteCurrentlyVisible(leader);
            if (leaderOnScreen && leader.getYSpeed() < 0x0300) {
                sidekick.setXSpeed((short) 0);
                if (sidekick.getYSpeed() >= 0x0200) {
                    inputJump = true;
                    inputJumpPress = true;
                } else {
                    mgzCarryFlapTimer++;
                    if (mgzCarryFlapTimer >= 0x58) {
                        mgzCarryFlapTimer = 0;
                        inputJump = true;
                        inputJumpPress = true;
                    }
                }
                return;
            }

            mgzReleasedChaseLatched = true;
            int dy = Math.abs(signedWord(leader.getCentreY() - sidekick.getCentreY()));
            int quarterDy = dy >> 2;
            mgzReleasedChaseYAccel = (short) (quarterDy + (quarterDy >> 1));
            int dx = Math.abs(signedWord(leader.getCentreX() - sidekick.getCentreX()));
            mgzReleasedChaseXAccel = (short) (dx >> 2);
            return;
        }

        int xAccel = mgzReleasedChaseXAccel;
        int sidekickX = sidekick.getCentreX() & 0xFFFF;
        int leaderX = leader.getCentreX() & 0xFFFF;
        if (sidekickX >= leaderX) {
            sidekick.setDirection(Direction.LEFT);
            xAccel = -xAccel;
        } else {
            sidekick.setDirection(Direction.RIGHT);
        }
        sidekick.setXSpeed((short) (sidekick.getXSpeed() + xAccel));

        int probeY = signedWord(sidekick.getCentreY() - 0x10);
        int leaderY = signedWord(leader.getCentreY());
        if (probeY < leaderY) {
            sidekick.setYSpeed((short) (sidekick.getYSpeed() + mgzReleasedChaseYAccel));
        }
    }

    private boolean isSpriteCurrentlyVisible(AbstractPlayableSprite sprite) {
        Camera camera = sprite.currentCamera();
        return camera != null && camera.isOnScreen(sprite);
    }

    private void pickupLeaderForCarry() {
        // ROM sub_1459E (sonic3k.asm:27399): clear Sonic's velocities/angle,
        // parent him to Tails, then copy Tails's current x/y velocity into both
        // Sonic and the latch globals used by Tails_Carry_Sonic.
        leader.setObjectControlled(true);
        leader.setAir(true);
        leader.setRolling(false);
        leader.setRollingJump(false);
        leader.setSpindash(false);
        leader.setSpindashCounter((short) 0);
        leader.setJumping(false);
        leader.setGSpeed((short) 0);
        leader.setCentreXPreserveSubpixel(sidekick.getCentreX());
        leader.setCentreYPreserveSubpixel(
                (short) (sidekick.getCentreY() + carryTrigger.carryDescendOffsetY()));
        leader.setDirection(sidekick.getDirection());
        leader.setForcedAnimationId(leader.resolveAnimationId(CanonicalAnimation.TAILS_CARRIED));
        leader.setXSpeed(sidekick.getXSpeed());
        leader.setYSpeed(sidekick.getYSpeed());
        carryLatchX = leader.getXSpeed();
        carryLatchY = leader.getYSpeed();
    }

    private int signedWord(int value) {
        return (short) value;
    }

    /**
     * ROM {@code Tails_Catch_Up_Flying} (sonic3k.asm:26474). Entered when
     * {@code Tails_CPU_routine == 2}. Waits on either (a) the sidekick's Ctrl_2
     * A/B/C/START press, or (b) a 64-frame gate firing while Sonic's
     * object_control sign bit is clear and Sonic is not super. On trigger, teleports Tails to
     * (Sonic.x, Sonic.y - 0xC0), sets routine = 4, and enters flight AI.
     *
     * <p>Stubbed in Task 2; body lands in Task 4.
     */
    private void updateCatchUpFlight() {
        // ROM Tails_Catch_Up_Flying (sonic3k.asm:26474-26531)
        boolean trigger = false;

        // Ctrl_2_logical A/B/C/START press → immediate trigger
        if ((controller2Logical & (AbstractPlayableSprite.INPUT_JUMP | INPUT_START)) != 0) {
            trigger = true;
        } else {
            // ROM checks Sonic's object_control with `bmi`, so only bit 7 suppresses
            // the 64-frame catch-up warp (sonic3k.asm:26478-26488).
            if ((frameCounter & 0x3F) == 0
                    && (!leader.isObjectControlled() || leader.isObjectControlAllowsCpu())
                    && !leader.isSuperSonic()) {
                trigger = true;
            }
        }

        if (!trigger) {
            // ROM routine 2's wait path only returns: Tails_Catch_Up_Flying
            // branches to locret_13BF6 without writing object_control until
            // the catch-up trigger fires (sonic3k.asm:26474-26500). Preserve
            // the current object-control state so CNZ cylinder releases
            // (sonic3k.asm:68071-68077) can expose the marker to the same
            // screen-boundary/movement writes recorded at CNZ1 F4790.
            sidekick.setAir(true);
            sidekick.setControlLocked(true);
            sidekick.setObjectControlled(true);
            sidekick.setForcedAnimationId(flyAnimId);
            return;
        }

        // sonic3k.asm:26487 (loc_13B50) — teleport and enter FLIGHT_AUTO_RECOVERY.
        int targetX = leader.getCentreX() & 0xFFFF;
        int targetY = leader.getCentreY() & 0xFFFF;
        catchUpTargetX = targetX;
        catchUpTargetY = targetY;
        sidekick.setCentreXPreserveSubpixel((short) targetX);
        sidekick.setCentreYPreserveSubpixel(
                (short) (targetY - com.openggf.game.sonic3k.constants.Sonic3kConstants.TAILS_CATCH_UP_Y_OFFSET));
        sidekick.setXSpeed((short) 0);
        sidekick.setYSpeed((short) 0);
        sidekick.setGSpeed((short) 0);
        sidekick.setAir(true);
        sidekick.setRolling(false);
        sidekick.setRollingJump(false);
        sidekick.setJumping(false);
        sidekick.setPushing(false);
        sidekick.setOnObject(false);
        sidekick.setMoveLockTimer(0);
        sidekick.setForcedAnimationId(flyAnimId);
        sidekick.setControlLocked(true);
        sidekick.setObjectControlled(true);
        // ROM loc_13B50 (sonic3k.asm:26502-26508) writes double_jump_flag=0,
        // status=2, and object_control=$81. Movement remains owned by the CPU
        // flight routine; normal air physics must not be used to carry Tails.
        sidekick.setDoubleJumpFlag(0);

        flightTimer = 0;
        state = State.FLIGHT_AUTO_RECOVERY;
    }

    /**
     * ROM {@code Tails_FlySwim_Unknown} (sonic3k.asm:26534). Entered when
     * {@code Tails_CPU_routine == 4}. Per-frame: increments Tails_CPU_flight_timer;
     * after 5*60 frames off-screen, falls back to {@code CATCH_UP_FLIGHT}.
     * Otherwise computes the 16-frame delayed Sonic position, steers Tails toward
     * it (X step &le; 0xC, Y step = 1 plus optional -0x20 lead), and transitions
     * to {@code NORMAL} (routine 0x06) once Tails is close enough to Sonic and
     * Sonic isn't hurt/dead.
     *
     * <p>Stubbed in Task 2; body lands in Task 5.
     */
    private void updateFlightAutoRecovery() {
        // ROM Tails_FlySwim_Unknown (sonic3k.asm:26534-26653).
        final int AUTO_LAND_FRAMES = com.openggf.game.sonic3k.constants
                .Sonic3kConstants.TAILS_FLIGHT_AUTO_LAND_FRAMES;
        final int MAX_X_STEP = com.openggf.game.sonic3k.constants
                .Sonic3kConstants.TAILS_FLIGHT_MAX_X_STEP;
        final int Y_STEP = com.openggf.game.sonic3k.constants
                .Sonic3kConstants.TAILS_FLIGHT_Y_STEP;
        final int LEAD_SUPPRESS = com.openggf.game.sonic3k.constants
                .Sonic3kConstants.TAILS_FLIGHT_LEAD_SUPPRESS_GSPEED;
        final int LEAD_OFFSET = com.openggf.game.sonic3k.constants
                .Sonic3kConstants.TAILS_FLIGHT_LEAD_X_OFFSET;
        final int FLIGHT_FUEL = (8 * 60) / 2;   // ROM loc_13C3A:26552 double_jump_property reload

        // 1. Off-screen timer. The ROM check is `tst.b render_flags(a0); bmi.s loc_13C3A`.
        //    Engine: hasRenderFlagOnScreenState() + isRenderFlagOnScreen() mirrors the bit.
        boolean onScreen = sidekick.hasRenderFlagOnScreenState()
                ? sidekick.isRenderFlagOnScreen()
                : isCurrentlyVisible();
        if (!onScreen) {
            flightTimer++;
            if (flightTimer >= AUTO_LAND_FRAMES) {
                // ROM sonic3k.asm:26540-26547 — reset and bounce back to CATCH_UP.
                // S2 uses the same word writes at s2.asm:38769-38775. These
                // write x_pos/y_pos only, preserving x_sub/y_sub for the later
                // MoveSprite position add.
                flightTimer = 0;
                sidekick.setCentreXPreserveSubpixel((short) 0);
                sidekick.setCentreYPreserveSubpixel((short) 0);
                sidekick.setObjectControlled(true);
                sidekick.setAir(true);
                sidekick.setDoubleJumpFlag(1);
                sidekick.setDoubleJumpProperty((byte) FLIGHT_FUEL);
                sidekick.setForcedAnimationId(flyAnimId);
                state = State.CATCH_UP_FLIGHT;
                return;
            }
        } else {
            // ROM loc_13C3A (sonic3k.asm:26551-26555): every on-screen frame
            // resets the flight timer, refuels double_jump_property, and ORs
            // Status_InAir to keep Tails in flight recovery even if terrain
            // collision touched the flag on the previous movement tick.
            // (8*60)/2 = 240. The refuel is what keeps Tails's flapping
            // animation + flight state active indefinitely while on-screen.
            flightTimer = 0;
            sidekick.setDoubleJumpProperty((byte) FLIGHT_FUEL);
            sidekick.setAir(true);
            // Tails_Set_Flying_Animation is normally called here; the engine's
            // animation is driven by the forced-anim slot already set at entry.
        }

        // 3. Target = Sonic's 16-frame-delayed position. ROM
        //    Tails_FlySwim_Unknown reads Pos_table directly
        //    (sonic3k.asm:26564-26565) with NO lead offset — the `subi.w #$20, d2`
        //    adjustment lives only in the NORMAL follow AI at loc_13DA6
        //    (sonic3k.asm:26690-26694). An earlier iteration of this body
        //    mis-applied that offset here and produced a chronic -0x20 X drift.
        int targetX = leader.getCentreX(ROM_FOLLOW_DELAY_FRAMES) & 0xFFFF;
        int targetY = leader.getCentreY(ROM_FOLLOW_DELAY_FRAMES) & 0xFFFF;
        catchUpTargetX = targetX;
        catchUpTargetY = targetY;

        // 4. X steer: dx = Tails.x - target.x. Track residual distance AFTER
        //    the step (ROM d0 is zeroed in the overshoot-clamp branch at
        //    loc_13CA6/loc_13CAA, so the close-enough check uses the
        //    post-step value, not the pre-step value).
        int dx = (sidekick.getCentreX() & 0xFFFF) - targetX;
        int residualX = dx;
        if (dx != 0) {
            int absDx = Math.abs(dx);
            int step = absDx >> 4;
            if (step > MAX_X_STEP) {
                step = MAX_X_STEP;
            }
            // ROM sonic3k.asm:26580-26586: move.b x_vel(a1), d1 reads the HIGH
            // byte of Sonic's 16-bit x_vel (big-endian 68000). Engine x_vel is
            // stored in subpixels (256/px), so the ROM's "pixel velocity" byte
            // is (xSpeed >> 8) & 0xFF. Use the signed 8-bit absolute value.
            int sonicPixelXVel = (leader.getXSpeed() >> 8);
            int sonicXVelMag = Math.abs((byte) sonicPixelXVel);
            step += sonicXVelMag + 1;   // ROM addq.w #1, d2
            if (step >= absDx) {
                step = absDx;           // Clamp to |dx| — overshoot branch
                residualX = 0;          //   (loc_13CA6 / loc_13CAA clear d0)
            }
            int newX = (dx > 0)
                    ? (sidekick.getCentreX() & 0xFFFF) - step
                    : (sidekick.getCentreX() & 0xFFFF) + step;
            sidekick.setCentreXPreserveSubpixel((short) newX);
        }

        // 5. Y steer: +/-1 per frame. Same post-step residual tracking.
        int dy = (sidekick.getCentreY() & 0xFFFF) - targetY;
        int residualY = dy;
        if (dy != 0) {
            int newY = (dy > 0)
                    ? (sidekick.getCentreY() & 0xFFFF) - Y_STEP
                    : (sidekick.getCentreY() & 0xFFFF) + Y_STEP;
            sidekick.setCentreYPreserveSubpixel((short) newY);
        }

        // 6. Transition to NORMAL when close enough AND Sonic alive AND
        //    Sonic not in an uninterruptible state. Close-enough uses the
        //    post-step residuals (see residualX/residualY above).
        boolean closeEnough = residualX == 0 && residualY == 0;
        boolean sonicAlive = !leader.isHurt() && !leader.getDead();
        // ROM sonic3k.asm:26624-26630: also checks a stat_table flag bit 7.
        // Engine approximation: isObjectControlled.
        boolean sonicFreeOfLock = !leader.isObjectControlled();

        if (closeEnough && sonicAlive && sonicFreeOfLock) {
            // ROM sonic3k.asm:26631-26648 — return to NORMAL (routine 0x06).
            sidekick.setObjectControlled(false);
            sidekick.setControlLocked(false);
            sidekick.setXSpeed((short) 0);
            sidekick.setYSpeed((short) 0);
            sidekick.setGSpeed((short) 0);
            sidekick.setMoveLockTimer(0);
            sidekick.setForcedAnimationId(-1);
            sidekick.setAir(true);
            // ROM loc_1384A (sonic3k.asm:26213): while object_control bit 0 is
            // set (FLIGHT_AUTO_RECOVERY keeps it high), double_jump_flag is
            // cleared every frame by the dispatcher. On the NORMAL transition
            // the engine just cleared object_control, so the dispatcher's
            // auto-clear won't fire next tick; without this explicit write,
            // doubleJumpFlag would still be 1 and
            // PlayableSpriteMovement.applyGravity() would keep applying the
            // +0x08 flight gravity to a grounded Tails in NORMAL.
            sidekick.setDoubleJumpFlag(0);
            state = State.NORMAL;
            normalFrameCount = 0;
            if (suppressNextAizIntroNormalMovement) {
                // AIZ1's intro marker release reaches NORMAL through this
                // handoff tick, which does not run normal follow AI yet. Clear
                // the release-side suppression here so the next object tick can
                // use fresh Ctrl_2 state; do not suppress handoff physics,
                // because ROM still applies the airborne +$38 gravity step.
                suppressNextAizIntroNormalMovement = false;
            }
            return;
        }

        // 7. Otherwise keep object_control locked to keep flight AI active.
        sidekick.setObjectControlled(true);
    }

    /**
     * Finishes the Tails-carry body after Tails has run current-frame movement.
     *
     * <p>ROM {@code Tails_FlyingSwimming} runs {@code Tails_Move_FlySwim},
     * input acceleration, {@code MoveSprite_TestGravity2}, and
     * {@code Tails_DoLevelCollision} before calling {@code Tails_Carry_Sonic}.
     * The controller update only prepares carry input/release checks; this hook
     * mirrors the later {@code Tails_Carry_Sonic} parentage/probe timing.
     */
    public void finishCarryAfterCarrierMovement() {
        if (!carryParentagePending || state != State.CARRYING || !flyingCarryingFlag
                || leader == null || carryTrigger == null) {
            return;
        }
        carryParentagePending = false;

        // Sonic parentage (Tails_Carry_Sonic steps 5 + 8):
        //   x_pos = Tails.x_pos
        //   y_pos = Tails.y_pos + carryDescendOffsetY()
        //   x_vel = Tails.x_vel
        //   y_vel = Tails.y_vel
        leader.setCentreXPreserveSubpixel(sidekick.getCentreX());
        leader.setCentreYPreserveSubpixel(
                (short) (sidekick.getCentreY() + carryTrigger.carryDescendOffsetY()));
        leader.setDirection(sidekick.getDirection());
        leader.setXSpeed(sidekick.getXSpeed());
        leader.setYSpeed(sidekick.getYSpeed());

        // ROM Tails_Carry_Sonic loc_144F8 (sonic3k.asm:27328-27331):
        //   movem.l d0-a6,-(sp)
        //   lea     (Player_1).w,a0
        //   bsr.w   SonicKnux_DoLevelCollision
        //   movem.l (sp)+,d0-a6
        //
        // After parentage writes the ROM explicitly runs the full airborne
        // collision on Sonic (Player_1).  That probe's Player_TouchFloor tail
        // (sonic3k.asm:24366) clears Status_InAir when Tails's descended
        // position puts Sonic on ground.  Next frame's in-air check at
        // sonic3k.asm:27227 then branches to loc_1445A (release path A).
        //
        // Without this probe, the engine leaves Sonic's air flag set forever
        // while object_control holds him to Tails, so the ground-release path
        // is unreachable once Tails lands.
        //
        // NOTE: the normal landing handler (PlayableSpriteMovement.calculateLanding)
        // calls resetOnFloor(), which early-returns when isObjectControlled() is
        // true, so it would not apply this carried landing state. The inline
        // handler mirrors the flat-floor result used by SonicKnux_DoLevelCollision:
        // y_vel = 0, inertia = x_vel, then the Player_TouchFloor tail clears
        // Status_InAir/Push/RollJump (sonic3k.asm:24366-24369).
        CollisionSystem collision = Objects.requireNonNull(
                leader.currentCollisionSystem(),
                "CollisionSystem must be available during CARRYING state "
                        + "(Tails-carry post-parentage probe, sonic3k.asm:27330)");
        collision.resolveAirCollision(leader, sprite -> {
            // ROM Player_TouchFloor (sonic3k.asm:24366-24369):
            //   bclr #Status_InAir,status(a0)
            //   bclr #Status_Push,status(a0)
            //   bclr #Status_RollJump,status(a0)
            //   move.b #0,jumping(a0)
            sprite.setSubpixelRaw(sprite.getXSubpixelRaw(), 0);
            sprite.setYSpeed((short) 0);
            sprite.setGSpeed(sprite.getXSpeed());
            sprite.setAir(false);
            sprite.setPushing(false);
            sprite.setRollingJump(false);
            sprite.setJumping(false);
        });

        // Refresh the latch AFTER our writes so the next frame's compare is
        // against what we just wrote, not stale values.  The probe above may
        // have cleared the leader's y_vel via the collision adjustment, so
        // the latch captures the post-probe values to avoid a false latch
        // mismatch on the next frame.
        carryLatchX = leader.getXSpeed();
        carryLatchY = leader.getYSpeed();
    }

    private void performJumpRelease() {
        short xMag = carryTrigger.carryReleaseJumpXVel();
        short xVel = leader.getDirection() == Direction.LEFT
                ? (short) -xMag
                : xMag;
        leader.setXSpeed(xVel);
        leader.setYSpeed(carryTrigger.carryReleaseJumpYVel());
        leader.setAir(true);
        leader.setJumping(true);
        leader.setRolling(true);
        leader.setRollingJump(false);
        releaseCarry(carryTrigger.carryJumpReleaseCooldownFrames());
    }

    private void releaseCarry(int cooldownFrames) {
        boolean mgzBossTransitionCarry = carryTrigger != null && carryTrigger.usesMgzBossTransitionControl();
        leader.setObjectControlled(false);
        leader.setForcedAnimationId(-1);
        sidekick.setControlLocked(false);
        sidekick.setForcedAnimationId(mgzBossTransitionCarry ? flyAnimId : -1);
        flyingCarryingFlag = false;
        carryParentagePending = false;
        mgzCarryIntroAscend = false;
        mgzCarryFlapTimer = 0;
        mgzReleasedChaseLatched = false;
        releaseCooldown = cooldownFrames;
        if (mgzBossTransitionCarry) {
            state = State.CARRYING;
            sidekick.setAir(true);
            sidekick.setDoubleJumpProperty((byte) 0xF0);
        } else {
            state = State.NORMAL;
            normalFrameCount = 0;
        }
    }

    private void applyManualControl() {
        inputUp = (controller2Held & AbstractPlayableSprite.INPUT_UP) != 0;
        inputDown = (controller2Held & AbstractPlayableSprite.INPUT_DOWN) != 0;
        inputLeft = (controller2Held & AbstractPlayableSprite.INPUT_LEFT) != 0;
        inputRight = (controller2Held & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        inputJump = (controller2Held & AbstractPlayableSprite.INPUT_JUMP) != 0;
        inputJumpPress = (controller2Logical & AbstractPlayableSprite.INPUT_JUMP) != 0;
        controlCounter--;
    }

    private void enterApproachingState() {
        AbstractPlayableSprite target = getEffectiveLeader();
        if (target == null) {
            triggerDespawn(DespawnCause.EXPLICIT);
            return;
        }
        sidekick.setSpindash(false);
        sidekick.setSpindashCounter((short) 0);
        boolean started = respawnStrategy.beginApproach(sidekick, target);
        if (!started) {
            triggerDespawn(DespawnCause.EXPLICIT);
            return;
        }
        state = State.APPROACHING;
        despawnCounter = 0;
        controlCounter = 0;
        normalFrameCount = 0;
    }

    int clampTargetYToWater(int targetY) {
        LevelManager levelManager = sidekick.currentLevelManager();
        if (levelManager == null) {
            return targetY;
        }
        WaterSystem waterSystem = sidekick.currentWaterSystem();
        int waterY = waterSystem.getWaterLevelY(levelManager.getCurrentZone(), levelManager.getCurrentAct());
        if (waterY == 0) {
            return targetY;
        }
        return Math.min(targetY, waterY - 0x10);
    }

    private boolean checkDespawn() {
        boolean onScreen = sidekick.hasRenderFlagOnScreenState()
                ? sidekick.isRenderFlagOnScreen()
                : isCurrentlyVisible();
        int currentInteractObjectId = sidekick.getLatchedSolidObjectId() & 0xFF;
        if (onScreen) {
            lastInteractObjectId = currentInteractObjectId;
            despawnCounter = 0;
            return false;
        }

        ObjectInstance currentRidingInstance = sidekick.getLatchedSolidObjectInstance();
        if (sidekick.isOnObject()
                && currentInteractObjectId != 0
                && currentRidingInstance != null
                && currentRidingInstance.isDestroyed()) {
            lastInteractObjectId = currentInteractObjectId;
            triggerDespawn(DespawnCause.OBJECT_ID_MISMATCH);
            return true;
        }

        // Object-id-mismatch despawn path. ROM semantics differ across games:
        //
        //   S2 (s2.asm:39051 TailsCPU_CheckDespawn): cmp.b id(a3),d0 — compare
        //       Tails_interact_ID byte against the object's id field. The object
        //       ID is the per-game object-pointer-table index, so different
        //       object types compare differently. Engine's existing behaviour
        //       (compare 8-bit object IDs) matches this byte-for-byte.
        //
        //   S3K (sonic3k.asm:26823 sub_13EFC): cmp.w (a3),d0 — compare cached
        //       Tails_CPU_interact word against the FIRST WORD of the object's
        //       structure (the high word of its routine pointer). For S3K all
        //       gameplay objects live in ROM 0x0001xxxx-0x0007xxxx, so the high
        //       word is identical (0x0000-0x0007) for virtually every object
        //       type encountered in normal play. The check therefore almost
        //       never fires in S3K — it is effectively a sanity guard against
        //       wholly different code regions, which CNZ-style barber-pole →
        //       wire-cage transitions do NOT trigger (both routines live in
        //       0x000338xx, same high word 0x0003).
        //
        // Gate the path via PhysicsFeatureSet so S2 keeps its existing semantics
        // and S3K stops despawning Tails on legitimate same-region object
        // transitions (CNZ1 trace F1685 barber-pole → wire-cage divergence).
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        boolean useObjectIdMismatchDespawn = fs == null
                || fs.sidekickDespawnUsesObjectIdMismatch();
        if (useObjectIdMismatchDespawn
                && sidekick.isOnObject()
                && currentInteractObjectId != 0
                && lastInteractObjectId != 0
                && currentInteractObjectId != lastInteractObjectId) {
            lastInteractObjectId = currentInteractObjectId;
            triggerDespawn(DespawnCause.OBJECT_ID_MISMATCH);
            return true;
        }

        despawnCounter++;
        lastInteractObjectId = currentInteractObjectId;
        if (despawnCounter >= DESPAWN_TIMEOUT) {
            triggerDespawn(DespawnCause.OFF_SCREEN_TIMEOUT);
            return true;
        }
        return false;
    }

    private boolean isCurrentlyVisible() {
        Camera camera = sidekick.currentCamera();
        return camera != null && camera.isOnScreen(sidekick);
    }

    /**
     * Legacy entry point. Existing callers default to
     * EXPLICIT (immediate marker warp).
     */
    public void despawn() {
        despawn(DespawnCause.EXPLICIT);
    }

    /**
     * Trigger a sidekick despawn with explicit cause. LEVEL_BOUNDARY
     * mirrors ROM Kill_Character (sonic3k.asm:21136): Frame N zeroes
     * velocities and enters DEAD_FALLING; Frame N+1 (updateDeadFalling)
     * runs sub_123C2 -> sub_13ECA equivalent (warp + +$38 gravity).
     * Other causes go straight to applyDespawnMarker.
     */
    public void despawn(DespawnCause cause) {
        sidekick.setDead(false);
        sidekick.setDeathCountdown(0);
        triggerDespawn(cause);
    }

    private void triggerDespawn(DespawnCause cause) {
        if (cause == DespawnCause.LEVEL_BOUNDARY) {
            beginLevelBoundaryKill();
            return;
        }
        applyDespawnMarker();
    }

    /**
     * ROM Kill_Character (sonic3k.asm:21136-21159) entry reached from
     * Tails_Check_Screen_Boundaries (sonic3k.asm:28442-28443
     * `loc_14F56: jmp (Kill_Character).l`) when the sidekick crosses the
     * bottom kill plane. ROM Kill_Character at sonic3k.asm:21148-21151
     * writes:
     *
     * <pre>
     *     bset    #Status_InAir,status(a0)
     *     move.w  #-$700,y_vel(a0)
     *     move.w  #0,x_vel(a0)
     *     move.w  #0,ground_vel(a0)
     * </pre>
     *
     * y_vel is set to {@code -$700}, NOT zero. Because Kill_Character was
     * reached via {@code jmp} (not {@code jsr}), the {@code rts} at
     * sonic3k.asm:21159 unwinds to Kill_Character's caller's caller — for
     * Tails the relevant chain is Tails_Stand_Path
     * (sonic3k.asm:27520-27526), so control falls through to
     * {@code jsr (MoveSprite_TestGravity2).l} on line 27526.
     * MoveSprite_TestGravity2 with Reverse_gravity_flag clear is just
     * MoveSprite2 (sonic3k.asm:36088-36101) which applies the freshly
     * written {@code y_vel = -$700} to {@code y_pos}, shifting Tails up by
     * 7 pixels in the same frame. Trace AIZ F7171 records the post-shift
     * state: {@code y_pos = $0477} (down 7 from $047E) with
     * {@code y_vel = -$700} retained. Engine therefore preserves the
     * negative y-velocity so the airborne movement manager's
     * SpeedToPos-equivalent ({@code modeNormal} → {@code sprite.move})
     * applies the same 7-pixel shift inside the kill frame.
     *
     * Position is intentionally NOT warped this frame; ROM keeps Tails at
     * post-MoveSprite2 position for one frame, then sub_13ECA writes the
     * marker on Frame N+1 (see updateDeadFalling).
     */
    private void beginLevelBoundaryKill() {
        state = State.DEAD_FALLING;
        despawnCounter = 0;
        controlCounter = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        applyKillCharacterTouchFloorReset();
        sidekick.setXSpeed((short) 0);
        // ROM Kill_Character (sonic3k.asm:21149) writes y_vel=-$700.
        sidekick.setYSpeed((short) -0x700);
        sidekick.setGSpeed((short) 0);
        sidekick.setHurt(false);
        sidekick.setRollingJump(false);
        sidekick.setOnObject(false);
        sidekick.setPushing(false);
        sidekick.setLatchedSolidObjectId(0);
        sidekick.setSpindash(false);
        sidekick.setSpindashCounter((short) 0);
        sidekick.setAir(true);
        sidekick.setMoveLockTimer(0);
        sidekick.setForcedAnimationId(flyAnimId);
        sidekick.setControlLocked(true);
        // NOT object_controlled - DEAD_FALLING is its own dispatch state
        // so updateDeadFalling fires on the next tick regardless.
        lastInteractObjectId = 0;
    }

    private void applyKillCharacterTouchFloorReset() {
        int centreX = sidekick.getCentreX();
        int centreY = sidekick.getCentreY();
        if (sidekick.getRolling()) {
            // ROM Kill_Character calls Player_TouchFloor before setting death
            // velocities (sonic3k.asm:21142-21151). For Tails this restores
            // default radii, clears Status_Roll, and adds the current y_radius
            // delta to y_pos (sonic3k.asm:29133-29156).
            //
            // ROM Tails_TouchFloor (sonic3k.asm:29133-29156):
            //   move.b y_radius(a0),d0          ; d0 = OLD y_radius
            //   move.b default_y_radius(a0),y_radius(a0)
            //   ...
            //   sub.b default_y_radius(a0),d0   ; d0 = old_y_radius - default_y_radius
            //   ext.w d0
            //   ...
            //   add.w d0,y_pos(a0)              ; y_pos += d0 (sign-flipped by angle)
            //
            // The delta is the radius difference, NOT half the height difference.
            // Reading sidekick.getHeight() (full height = 2 * y_radius) instead
            // of getYRadius() previously returned ~13 px on Tails roll->stand,
            // shifting end-of-frame y by +13 — see AIZ F4679 (16 px gap).
            int delta = sidekick.getYRadius() - sidekick.getStandYRadius();
            if ((((sidekick.getAngle() & 0xFF) + 0x40) & 0x80) != 0) {
                delta = -delta;
            }
            sidekick.setRolling(false);
            sidekick.setCentreXPreserveSubpixel((short) centreX);
            sidekick.setCentreYPreserveSubpixel((short) (centreY + delta));
        } else if (sidekick.getYRadius() != sidekick.getStandYRadius()
                || sidekick.getXRadius() != sidekick.getStandXRadius()) {
            sidekick.restoreDefaultRadii();
        }
        sidekick.setAir(false);
        sidekick.setPushing(false);
        sidekick.setRollingJump(false);
        sidekick.setJumping(false);
        sidekick.setDoubleJumpFlag(0);
    }

    /**
     * One-frame death-routine equivalent of ROM loc_1578E ->
     * loc_157C8 -> sub_123C2 -> sub_13ECA. Runs the frame after
     * beginLevelBoundaryKill.  Mirrors ROM where the dispatcher enters
     * the death routine; sub_123C2 (sonic3k.asm:24538-24578) sees Tails
     * fell below Camera_Y_pos+0x100, sets Tails_CPU_routine=2 and
     * branches to sub_13ECA (sonic3k.asm:26800-26809) which warps
     * x_pos=0x7F00, y_pos=0 (and clears double_jump_flag, sets
     * object_control=$81, Status_InAir).  Control then unwinds via the
     * <code>bsr</code> at sonic3k.asm:29284 back to {@code loc_157C8}, where
     * <code>jsr (MoveSprite_TestGravity).l</code> at line 29285 falls through to
     * {@code MoveSprite} (sonic3k.asm:36032-36042) and applies the still-
     * preserved {@code y_vel = -$700} (Kill_Character at sonic3k.asm:21149)
     * to {@code y_pos} <em>before</em> the +$38 gravity write, shifting Tails
     * 7 px up to {@code y_pos = -7} and leaving {@code y_vel = -$6C8}.
     * Trace AIZ F7172 records exactly that: {@code y = -0x0007},
     * {@code y_vel = -0x06C8}.
     *
     * <p>{@link #applyDespawnMarker()} flips
     * {@link AbstractPlayableSprite#setObjectControlled(boolean)} to true,
     * which enables {@code objectControlSuppressesMovement} and short-circuits
     * the regular {@link com.openggf.sprites.managers.PlayableSpriteMovement}
     * path entirely.  The post-warp MoveSprite step is therefore inlined here
     * to mirror the ROM call chain.  We capture {@code y_vel} before the warp
     * because {@link #applyDespawnMarker()} preserves velocity (sub_13ECA does
     * not touch x_vel/y_vel/ground_vel) but we still want to be explicit about
     * the order of operations matching {@code MoveSprite}.
     */
    private void updateDeadFalling() {
        // ROM MoveSprite (sonic3k.asm:36037-36041) uses the OLD y_vel for
        // position before adding gravity; sub_13ECA does not touch y_vel so
        // the value entering MoveSprite is the Kill_Character write of -$700.
        short oldYSpeed = sidekick.getYSpeed();
        applyDespawnMarker();
        // sub_13ECA wrote y_pos=0; now apply MoveSprite's position step
        // using the pre-gravity y_vel.
        int newCentreY = (sidekick.getCentreY() & 0xFFFF) + (oldYSpeed >> 8);
        sidekick.setCentreYPreserveSubpixel((short) newCentreY);
        // MoveSprite then adds +$38 (sonic3k.asm:36038) to y_vel.
        sidekick.setYSpeed((short) (oldYSpeed + 0x38));
    }

    /**
     * ROM sub_13ECA (sonic3k.asm:26800-26809) marker warp body. Writes
     * despawn marker x/y, sets Tails_CPU_routine=2, and leaves the next
     * S3K CPU tick in Tails_Catch_Up_Flying. S2 keeps the older SPAWNING
     * flow because its TailsCPU_Respawn path owns the approach sequence.
     */
    private void applyDespawnMarker() {
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        boolean s3kCatchUpMarker = fs != null && fs.sidekickRespawnEntersCatchUpFlight();
        state = s3kCatchUpMarker
                ? State.CATCH_UP_FLIGHT
                : State.SPAWNING;
        despawnCounter = 0;
        controlCounter = 0;
        if (s3kCatchUpMarker) {
            flightTimer = 0;
            sidekick.setDoubleJumpFlag(0);
        }
        normalFrameCount = 0;
        jumpingFlag = false;
        sidekick.setHurt(false);
        // ROM sub_13ECA writes status=Status_InAir directly
        // (sonic3k.asm:26804-26808). It clears Status_Roll and
        // Status_Underwater, but does not restore x_radius/y_radius or water
        // speed constants, so preserve those separate ROM fields.
        sidekick.clearRollingFlagPreserveRadii();
        sidekick.clearUnderwaterStatusPreserveWaterPhysics();
        sidekick.setRollingJump(false);
        sidekick.setOnObject(false);
        sidekick.setPushing(false);
        sidekick.setLatchedSolidObjectId(0);
        sidekick.setDirection(Direction.RIGHT);
        sidekick.setAir(true);
        sidekick.setCentreXPreserveSubpixel(resolveDespawnX());
        sidekick.setCentreYPreserveSubpixel((short) 0);
        sidekick.setDead(false);
        sidekick.setDeathCountdown(0);
        sidekick.setSpindash(false);
        sidekick.setSpindashCounter((short) 0);
        sidekick.setForcedAnimationId(flyAnimId);
        sidekick.setControlLocked(true);
        sidekick.setObjectControlled(true);
        // ROM sub_13ECA (sonic3k.asm:26800-26809) only writes x_pos,
        // y_pos, Tails_CPU_routine, object_control, status, and
        // double_jump_flag - it does NOT touch x_vel/y_vel/ground_vel.
        // Trace AIZ F2405 confirms this: ROM applies the marker warp
        // mid-trajectory and the recorded sidekick_x_speed/y_speed/g_speed
        // at F2405 retain the pre-warp values (0xFE07, 0x022D, 0xFD0D).
        // Don't zero velocities here. The LEVEL_BOUNDARY kill chain
        // (beginLevelBoundaryKill) does its own zeroing earlier in the
        // Kill_Character (sonic3k.asm:21148-21151) phase, which runs
        // before this marker warp on Frame N+1.
        lastInteractObjectId = 0;
    }

    /**
     * ROM AIZ1_Resize loc_1C4C4 (sonic3k.asm:38898-38900) releases
     * intro-dormant Tails by writing {@code Tails_CPU_routine=2} when the
     * camera reaches the main palette handoff. The marker position and
     * object-control byte remain intact until routine 2 performs its own
     * catch-up warp.
     */
    public void releaseAizIntroDormantMarker() {
        if (state != State.DORMANT_MARKER) {
            return;
        }
        state = State.CATCH_UP_FLIGHT;
        despawnCounter = 0;
        controlCounter = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        suppressNextAizIntroNormalMovement = true;
        sidekick.setAir(true);
        sidekick.setControlLocked(true);
        sidekick.setObjectControlled(true);
        sidekick.setForcedAnimationId(flyAnimId);
    }

    private void clearInputs() {
        inputUp = false;
        inputDown = false;
        inputLeft = false;
        inputRight = false;
        inputJump = false;
        inputJumpPress = false;
        aizObjectOrderGracePushBypassThisFrame = false;
    }

    public void setRespawnStrategy(SidekickRespawnStrategy strategy) {
        this.respawnStrategy = strategy;
    }

    public SidekickRespawnStrategy getRespawnStrategy() {
        return respawnStrategy;
    }

    public int consumePendingGroundedFollowNudge(int maxAgeFrames) {
        if (pendingGroundedFollowNudgeFrame < 0
                || frameCounter - pendingGroundedFollowNudgeFrame > maxAgeFrames) {
            pendingGroundedFollowNudge = 0;
            pendingGroundedFollowNudgeFrame = -1;
            return 0;
        }
        int nudge = pendingGroundedFollowNudge;
        pendingGroundedFollowNudge = 0;
        pendingGroundedFollowNudgeFrame = -1;
        return nudge;
    }

    public void setLeader(AbstractPlayableSprite leader) {
        this.leader = leader;
    }

    public AbstractPlayableSprite getLeader() {
        return leader;
    }

    public void setSidekickCount(int sidekickCount) {
        this.sidekickCount = sidekickCount;
    }

    /**
     * Returns true when this sidekick has been in NORMAL state for at least
     * {@link #SETTLED_FRAME_THRESHOLD} consecutive frames, meaning it has
     * "caught up" to its position in the chain.
     */
    public boolean isSettled() {
        return state == State.NORMAL && normalFrameCount >= SETTLED_FRAME_THRESHOLD;
    }

    /**
     * Walks up the leader chain to find the nearest settled leader (or the main
     * player). If the direct leader is not CPU-controlled or is settled, it is
     * returned immediately. Otherwise the chain is walked until a settled
     * sidekick or the main player is found.
     */
    public AbstractPlayableSprite getEffectiveLeader() {
        AbstractPlayableSprite current = leader;
        int maxSteps = sidekickCount;
        while (current != null && current.isCpuControlled() && maxSteps-- > 0) {
            SidekickCpuController ctrl = current.getCpuController();
            if (ctrl == null) {
                return current;
            }
            if (ctrl.isSettled()) {
                return current;
            }
            current = ctrl.getLeader();
        }
        return current;
    }

    /**
     * Sets the initial state for production use (e.g. pre-setting SPAWNING
     * after a level transition).
     */
    public void setInitialState(State state) {
        this.state = state;
        aizIntroDormantMarkerPrimed = false;
        suppressNextAizIntroNormalMovement = false;
        skipPhysicsThisFrame = false;
        normalFrameCount = state == State.NORMAL ? SETTLED_FRAME_THRESHOLD : 0;
        normalPushingGraceFrames = 0;
        suppressNextAirbornePushFollowSteering = false;
        aizObjectOrderGracePushBypassThisFrame = false;
        if (state != State.CARRYING && state != State.CARRY_INIT) {
            mgzCarryIntroAscend = false;
            mgzCarryFlapTimer = 0;
            mgzReleasedChaseLatched = false;
        }
    }

    /**
     * Restores the ROM Tails CPU globals captured by the trace recorder.
     * Used by trace replay bootstrap so sidekick AI decisions continue from the
     * exact pre-trace state instead of restarting from a generic NORMAL state.
     */
    public void hydrateFromRomCpuState(int cpuRoutine, int controlCounter,
                                       int respawnCounter, int interactId,
                                       boolean jumping) {
        state = mapRomCpuRoutine(cpuRoutine);
        aizIntroDormantMarkerPrimed = false;
        suppressNextAizIntroNormalMovement = false;
        skipPhysicsThisFrame = false;
        this.controlCounter = Math.max(0, controlCounter);
        this.despawnCounter = Math.max(0, respawnCounter);
        this.lastInteractObjectId = interactId & 0xFF;
        sidekick.setLatchedSolidObjectId(interactId);
        this.jumpingFlag = jumping;
        this.normalFrameCount = state == State.NORMAL ? SETTLED_FRAME_THRESHOLD : 0;
        normalPushingGraceFrames = 0;
        suppressNextAirbornePushFollowSteering = false;
        aizObjectOrderGracePushBypassThisFrame = false;
        clearInputs();
    }

    /**
     * Package-private test helper: sets both state and normalFrameCount directly.
     */
    void forceStateForTest(State state, int normalFrames) {
        this.state = state;
        aizIntroDormantMarkerPrimed = false;
        suppressNextAizIntroNormalMovement = false;
        skipPhysicsThisFrame = false;
        this.normalFrameCount = normalFrames;
        suppressNextAirbornePushFollowSteering = false;
        aizObjectOrderGracePushBypassThisFrame = false;
    }

    /**
     * ROM {@code Tails_CPU_Control_Index} (sonic3k.asm:26368-26386) is an 18-entry
     * word table indexed by {@code Tails_CPU_routine}, which the dispatcher reads at
     * sonic3k.asm:26362-26364. Each entry value is the CPU routine byte (0x00, 0x02,
     * 0x04, ...) — the table stride is 2 bytes, so the value equals the offset.
     *
     * <pre>
     *   0x00  loc_13A10               engine State.INIT  (zone-specific init, carry gate)
     *   0x02  Tails_Catch_Up_Flying   engine State.CATCH_UP_FLIGHT  (teleport-to-Sonic gate, sonic3k.asm:26474)
     *   0x04  Tails_FlySwim_Unknown   engine State.FLIGHT_AUTO_RECOVERY (fly-toward-Sonic + 5s timer, sonic3k.asm:26534)
     *   0x06  loc_13D4A               engine State.NORMAL (ground follow AI, sonic3k.asm:26656)
     *   0x08  loc_13F40               engine State.PANIC  (idle/standing ground, sonic3k.asm:26851)
     *   0x0A  locret_13FC0            engine State.DORMANT_MARKER (empty; used by AIZ1 intro marker)
     *   0x0C  loc_13FC2               engine State.CARRY_INIT (carry body init)
     *   0x0E  loc_13FFA               engine State.CARRYING  (carry body per-frame)
     *   0x12  Obj_MGZ2_BossTransition engine State.MGZ_RESCUE_WAIT
     *   0x10-0x22  super/Knuckles/2P variants — not modelled
     * </pre>
     *
     * <p>Note: earlier versions of this file mapped 0x02 and 0x04 to SPAWNING and
     * APPROACHING respectively. Those engine states are behavioural inventions
     * (despawn-respawn flow, approach strategy) that the ROM doesn't have a
     * matching routine for; hydrating them from a recorded CPU routine byte was
     * never semantically correct. Prefer to leave hydration undefined for
     * engine-only states until there's a concrete trace that exercises them.
     */
    private static State mapRomCpuRoutine(int cpuRoutine) {
        return switch (cpuRoutine) {
            case 0x00 -> State.INIT;
            case 0x02 -> State.CATCH_UP_FLIGHT;
            case 0x04 -> State.FLIGHT_AUTO_RECOVERY;
            case 0x06 -> State.NORMAL;
            case 0x08 -> State.PANIC;
            case 0x0A -> State.DORMANT_MARKER;
            case 0x12 -> State.MGZ_RESCUE_WAIT;
            case 0x0C -> State.CARRY_INIT;
            case 0x0E, 0x20 -> State.CARRYING;
            default -> throw new IllegalArgumentException(
                    "Unsupported ROM Tails CPU routine: 0x"
                            + Integer.toHexString(cpuRoutine));
        };
    }

    public boolean getInputUp() { return inputUp; }
    public boolean getInputDown() { return inputDown; }
    public boolean getInputLeft() { return inputLeft; }
    public boolean getInputRight() { return inputRight; }
    public boolean getInputJumpPress() { return inputJumpPress; }

    /** Package-private: allows respawn strategies to set directional input toward the leader. */
    void setApproachInput(boolean left, boolean right) {
        this.inputLeft = left;
        this.inputRight = right;
    }
    public boolean getInputJump() { return inputJump; }
    public State getState() { return state; }
    public boolean isApproaching() { return state == State.APPROACHING; }

    public int getMinXBound(int fallback) {
        return minXBound == Integer.MIN_VALUE ? fallback : minXBound;
    }

    public int getMaxXBound(int fallback) {
        return maxXBound == Integer.MIN_VALUE ? fallback : maxXBound;
    }

    public int getMaxYBound(int fallback) {
        return maxYBound == Integer.MIN_VALUE ? fallback : maxYBound;
    }

    public void setLevelBounds(Integer minX, Integer maxX, Integer maxY) {
        if (minX != null) {
            minXBound = minX;
        }
        if (maxX != null) {
            maxXBound = maxX;
        }
        if (maxY != null) {
            maxYBound = maxY;
        }
    }

    /**
     * Installs the game-specific carry trigger. Null (default) disables the
     * carry state machine; S1/S2 game modules pass null and the driver behaves
     * as before.
     */
    public void setCarryTrigger(SidekickCarryTrigger trigger) {
        this.carryTrigger = trigger;
        mgzReleasedChaseLatched = false;
    }

    /**
     * True while Tails is actively carrying Sonic in flight (ROM
     * Flying_carrying_Sonic_flag). Used by PlayableSpriteMovement.applyGravity
     * to substitute Tails's flight gravity (+0x08/frame, Tails_Move_FlySwim
     * loc_1488C in sonic3k.asm:27633) for the standard +0x38 air gravity.
     */
    public boolean isFlyingCarrying() {
        return flyingCarryingFlag;
    }

    public boolean usesFlyingCarryMovement() {
        if (sidekick.isHurt() || sidekick.getDead()) {
            return false;
        }
        return flyingCarryingFlag
                || (state == State.CARRYING
                && carryTrigger != null
                && carryTrigger.usesMgzBossTransitionControl());
    }

    public SidekickCpuRewindExtra captureRewindState() {
        return new SidekickCpuRewindExtra(
                state,
                despawnCounter,
                frameCounter,
                controlCounter,
                controller2Held,
                controller2Logical,
                inputUp,
                inputDown,
                inputLeft,
                inputRight,
                inputJump,
                inputJumpPress,
                jumpingFlag,
                minXBound,
                maxXBound,
                maxYBound,
                lastInteractObjectId,
                normalFrameCount,
                sidekickCount,
                normalPushingGraceFrames,
                suppressNextAirbornePushFollowSteering,
                aizObjectOrderGracePushBypassThisFrame,
                pendingGroundedFollowNudge,
                pendingGroundedFollowNudgeFrame,
                aizIntroDormantMarkerPrimed,
                suppressNextAizIntroNormalMovement,
                skipPhysicsThisFrame,
                cpuFrameCounterFromStoredLevelFrame,
                latestNormalStepDiagnostics,
                carryLatchX,
                carryLatchY,
                flyingCarryingFlag,
                carryParentagePending,
                releaseCooldown,
                mgzCarryIntroAscend,
                mgzCarryFlapTimer,
                mgzReleasedChaseLatched,
                mgzReleasedChaseXAccel,
                mgzReleasedChaseYAccel,
                flightTimer,
                catchUpTargetX,
                catchUpTargetY);
    }

    public void restoreRewindState(SidekickCpuRewindExtra snapshot) {
        state = snapshot.state();
        despawnCounter = snapshot.despawnCounter();
        frameCounter = snapshot.frameCounter();
        controlCounter = snapshot.controlCounter();
        controller2Held = snapshot.controller2Held();
        controller2Logical = snapshot.controller2Logical();
        inputUp = snapshot.inputUp();
        inputDown = snapshot.inputDown();
        inputLeft = snapshot.inputLeft();
        inputRight = snapshot.inputRight();
        inputJump = snapshot.inputJump();
        inputJumpPress = snapshot.inputJumpPress();
        jumpingFlag = snapshot.jumpingFlag();
        minXBound = snapshot.minXBound();
        maxXBound = snapshot.maxXBound();
        maxYBound = snapshot.maxYBound();
        lastInteractObjectId = snapshot.lastInteractObjectId();
        normalFrameCount = snapshot.normalFrameCount();
        sidekickCount = snapshot.sidekickCount();
        normalPushingGraceFrames = snapshot.normalPushingGraceFrames();
        suppressNextAirbornePushFollowSteering = snapshot.suppressNextAirbornePushFollowSteering();
        aizObjectOrderGracePushBypassThisFrame = snapshot.aizObjectOrderGracePushBypassThisFrame();
        pendingGroundedFollowNudge = snapshot.pendingGroundedFollowNudge();
        pendingGroundedFollowNudgeFrame = snapshot.pendingGroundedFollowNudgeFrame();
        aizIntroDormantMarkerPrimed = snapshot.aizIntroDormantMarkerPrimed();
        suppressNextAizIntroNormalMovement = snapshot.suppressNextAizIntroNormalMovement();
        skipPhysicsThisFrame = snapshot.skipPhysicsThisFrame();
        cpuFrameCounterFromStoredLevelFrame = snapshot.cpuFrameCounterFromStoredLevelFrame();
        latestNormalStepDiagnostics = snapshot.latestNormalStepDiagnostics();
        carryLatchX = snapshot.carryLatchX();
        carryLatchY = snapshot.carryLatchY();
        flyingCarryingFlag = snapshot.flyingCarryingFlag();
        carryParentagePending = snapshot.carryParentagePending();
        releaseCooldown = snapshot.releaseCooldown();
        mgzCarryIntroAscend = snapshot.mgzCarryIntroAscend();
        mgzCarryFlapTimer = snapshot.mgzCarryFlapTimer();
        mgzReleasedChaseLatched = snapshot.mgzReleasedChaseLatched();
        mgzReleasedChaseXAccel = snapshot.mgzReleasedChaseXAccel();
        mgzReleasedChaseYAccel = snapshot.mgzReleasedChaseYAccel();
        flightTimer = snapshot.flightTimer();
        catchUpTargetX = snapshot.catchUpTargetX();
        catchUpTargetY = snapshot.catchUpTargetY();
    }

    public void applyFlyingCarryVerticalVelocity() {
        if (!usesFlyingCarryMovement()) {
            return;
        }

        int flightTimer = sidekick.getDoubleJumpProperty() & 0xFF;
        if (((frameCounter + 1) & 1) != 0 && flightTimer != 0) {
            flightTimer = (flightTimer - 1) & 0xFF;
            sidekick.setDoubleJumpProperty((byte) flightTimer);
        }

        int flag = sidekick.getDoubleJumpFlag() & 0xFF;
        int ySpeed = sidekick.getYSpeed();
        if (flag != 1) {
            if (ySpeed >= -0x100) {
                ySpeed -= 0x20;
                flag = (flag + 1) & 0xFF;
                if (flag == 0x20) {
                    flag = 1;
                }
            } else {
                flag = 1;
            }
        } else {
            if (inputJumpPress && ySpeed >= -0x100 && flightTimer != 0) {
                flag = 2;
            }
            ySpeed += 0x08;
        }

        Camera camera = sidekick.currentCamera();
        if (camera != null && ySpeed < 0) {
            int cameraMinY = camera.getMinY() & 0xFFFF;
            if ((sidekick.getCentreY() & 0xFFFF) <= cameraMinY + 0x10) {
                ySpeed = 0;
            }
        }

        sidekick.setDoubleJumpFlag(flag);
        sidekick.setYSpeed((short) ySpeed);
    }

    /** Test/debug accessor for the release-cooldown byte (ROM Flying_carrying_Sonic_flag+1). */
    int getReleaseCooldownForTest() { return releaseCooldown; }

    int resolveAnimationId(CanonicalAnimation animation) {
        return sidekick.resolveAnimationId(animation);
    }

    public void reset() {
        state = State.INIT;
        despawnCounter = 0;
        controlCounter = 0;
        controller2Held = 0;
        controller2Logical = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        normalPushingGraceFrames = 0;
        suppressNextAirbornePushFollowSteering = false;
        aizIntroDormantMarkerPrimed = false;
        suppressNextAizIntroNormalMovement = false;
        skipPhysicsThisFrame = false;
        // Note: leader is NOT cleared — it's a structural chain relationship set at
        // construction time, not per-level state. Clearing it would break the sidekick
        // permanently since findLeader() scanning was removed in favor of explicit assignment.
        lastInteractObjectId = 0;
        minXBound = Integer.MIN_VALUE;
        maxXBound = Integer.MIN_VALUE;
        maxYBound = Integer.MIN_VALUE;
        clearInputs();
        sidekick.setForcedAnimationId(-1);
        sidekick.setControlLocked(false);
        sidekick.setObjectControlled(false);
        // Carry state (carryTrigger is intentionally NOT cleared — level-load-scoped)
        carryLatchX = 0;
        carryLatchY = 0;
        flyingCarryingFlag = false;
        releaseCooldown = 0;
        flightTimer = 0;
        catchUpTargetX = 0;
        catchUpTargetY = 0;
    }
}
