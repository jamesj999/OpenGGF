package com.openggf.level.objects;

/**
 * Immutable capture of the standard mutable field surface of
 * {@link AbstractObjectInstance} for rewind snapshots.
 *
 * <p>This record covers every field declared on {@code AbstractObjectInstance}
 * that changes during gameplay.  Fields that are {@code final} or purely
 * transient (service handles, static camera cache) are intentionally excluded.
 *
 * <p><strong>Subclass contract:</strong> Subclasses that hold private
 * gameplay-relevant state (boss phase counters, badnik AI timers, sub-state
 * machine indices, etc.) <em>must</em> override both
 * {@link AbstractObjectInstance#captureRewindState()} and
 * {@link AbstractObjectInstance#restoreRewindState(PerObjectRewindSnapshot)} and
 * incorporate their own extra fields.  Otherwise that state will silently fail
 * to round-trip across a rewind.  Classes known to need overrides include any
 * boss instance (phase counters, arena flags), badniks with multi-phase AI
 * (e.g. Turtloid, Spiker), and any object that accumulates a timer beyond
 * {@code animTimer} (e.g. CNZ bumper reload, HTZ earthquake object).
 */
public record PerObjectRewindSnapshot(
        // Lifecycle / destruction flags
        boolean destroyed,
        boolean destroyedRespawnable,

        // Dynamic position (null when object has not called updateDynamicSpawn yet;
        // stored as pair to avoid capturing the live ObjectSpawn reference beyond
        // what is needed — position is the only mutable part of a dynamic spawn).
        boolean hasDynamicSpawn,
        int dynamicSpawnX,
        int dynamicSpawnY,

        // Pre-update position snapshot (frame-start position used by touch collision)
        int preUpdateX,
        int preUpdateY,
        boolean preUpdateValid,
        int preUpdateCollisionFlags,

        // Per-frame timing / touch gating flags
        boolean skipTouchThisFrame,
        boolean solidContactFirstFrame,

        // Slot bookkeeping (set by ObjectManager at construction, may change if slot
        // is released and re-assigned, e.g. ring parent-slot release)
        int slotIndex,

        // S1 counter-based respawn index (-1 when not used)
        int respawnStateIndex,

        // Badnik movement state (nullable; only present when capturing
        // AbstractBadnikInstance or subclass)
        BadnikRewindExtra badnikExtra,

        // Badnik subclass state (nullable; only present for concrete badniks
        // with private AI timers, substates, or movement helpers)
        BadnikSubclassRewindExtra badnikSubclassExtra,

        // Player gameplay state (nullable; only present when capturing
        // AbstractPlayableSprite or subclass)
        PlayerRewindExtra playerExtra
) {
    public sealed interface BadnikSubclassRewindExtra
            permits MasherRewindExtra, BuzzerRewindExtra, CoconutsRewindExtra {
    }

    /**
     * Immutable capture of {@link AbstractBadnikInstance} movement-state fields
     * (currentX, currentY, xVelocity, yVelocity, animTimer, animFrame, facingLeft).
     */
    public static record BadnikRewindExtra(
            int currentX,
            int currentY,
            int xVelocity,
            int yVelocity,
            int animTimer,
            int animFrame,
            boolean facingLeft
    ) {}

    public record MasherRewindExtra(
            int motionX,
            int motionY,
            int motionXSub,
            int motionYSub,
            int motionXVel,
            int motionYVel,
            int initialYPos
    ) implements BadnikSubclassRewindExtra {}

    public record BuzzerRewindExtra(
            int stateOrdinal,
            int moveTimer,
            int turnDelay,
            int shotTimer,
            boolean shootingDisabled,
            boolean initPending
    ) implements BadnikSubclassRewindExtra {}

    public record CoconutsRewindExtra(
            int stateOrdinal,
            int throwStateOrdinal,
            int timer,
            int climbTableIndex,
            int attackTimer,
            int yVelocity
    ) implements BadnikSubclassRewindExtra {}

    /**
     * Mutable scalar gameplay state on a sidekick's CPU controller. Structural
     * runtime wiring (leader, respawn strategy, carry trigger, owning sidekick)
     * is intentionally excluded and restored from the live controller graph.
     */
    public record SidekickCpuRewindExtra(
            com.openggf.sprites.playable.SidekickCpuController.State state,
            int despawnCounter,
            int frameCounter,
            int controlCounter,
            int controller2Held,
            int controller2Logical,
            boolean inputUp,
            boolean inputDown,
            boolean inputLeft,
            boolean inputRight,
            boolean inputJump,
            boolean inputJumpPress,
            boolean jumpingFlag,
            int minXBound,
            int maxXBound,
            int maxYBound,
            int lastInteractObjectId,
            int normalFrameCount,
            int sidekickCount,
            int normalPushingGraceFrames,
            boolean suppressNextAirbornePushFollowSteering,
            boolean aizObjectOrderGracePushBypassThisFrame,
            int pendingGroundedFollowNudge,
            int pendingGroundedFollowNudgeFrame,
            boolean aizIntroDormantMarkerPrimed,
            boolean suppressNextAizIntroNormalMovement,
            boolean skipPhysicsThisFrame,
            boolean cpuFrameCounterFromStoredLevelFrame,
            com.openggf.sprites.playable.SidekickCpuController.NormalStepDiagnostics latestNormalStepDiagnostics,
            short carryLatchX,
            short carryLatchY,
            boolean flyingCarryingFlag,
            boolean carryParentagePending,
            int releaseCooldown,
            boolean mgzCarryIntroAscend,
            int mgzCarryFlapTimer,
            boolean mgzReleasedChaseLatched,
            short mgzReleasedChaseXAccel,
            short mgzReleasedChaseYAccel,
            int flightTimer,
            int catchUpTargetX,
            int catchUpTargetY
    ) {}

    /**
     * Mutable gameplay state on AbstractPlayableSprite that AbstractObjectInstance's
     * default 11-field capture surface does NOT cover. Per the
     * v1.5.1 plan, render-only state, character physics constants, animation
     * state, and collision radii are excluded — they are derived from these
     * fields and regenerate within one forward frame of replay.
     */
    public record PlayerRewindExtra(
            // AbstractSprite base position fields (not in AbstractObjectInstance hierarchy)
            short xPixel, short yPixel,
            short xSubpixel, short ySubpixel,
            int width, int height,
            // Movement / physics
            short gSpeed, short xSpeed, short ySpeed, short jump,
            byte angle, byte statusTertiary, boolean loopLowPlane,
            byte topSolidBit, byte lrbSolidBit,
            boolean prePhysicsAir, byte prePhysicsAngle,
            short prePhysicsGSpeed, short prePhysicsXSpeed, short prePhysicsYSpeed,
            boolean air, boolean rolling, boolean jumping, boolean rollingJump,
            boolean pinballMode, boolean pinballSpeedLock, boolean tunnelMode,
            // Surface interaction / collision
            boolean onObject, boolean onObjectAtFrameStart,
            int latchedSolidObjectId, boolean slopeRepelJustSlipped,
            boolean stickToConvex, boolean sliding, boolean pushing,
            boolean skidding, int skidDustTimer,
            short wallClimbX, int rightWallPenetrationTimer,
            int balanceState,
            // Special states / hazards
            boolean springing, int springingFrames,
            boolean dead, boolean drowningDeath, int drownPreDeathTimer,
            boolean hurt, int deathCountdown,
            int invulnerableFrames, int invincibleFrames,
            // Player abilities
            boolean spindash, short spindashCounter,
            boolean crouching, boolean lookingUp, short lookDelayCounter,
            int doubleJumpFlag, byte doubleJumpProperty,
            boolean shield, boolean instaShieldRegistered,
            boolean speedShoes, boolean superSonic,
            // Input gating / control
            boolean forceInputRight, int forcedInputMask,
            boolean forcedJumpPress, boolean suppressNextJumpPress,
            boolean deferredObjectControlRelease,
            boolean controlLocked, boolean hasQueuedControlLockedState, boolean queuedControlLocked,
            boolean hasQueuedForceInputRightState, boolean queuedForceInputRight,
            int moveLockTimer,
            boolean objectControlled, boolean objectControlAllowsCpu, boolean objectControlSuppressesMovement,
            int objectControlReleasedFrame,
            boolean suppressAirCollision, boolean suppressGroundWallCollision, boolean forceFloorCheck,
            boolean hidden,
            // MGZ-specific
            boolean mgzTopPlatformSpringHandoffPending,
            int mgzTopPlatformSpringHandoffXVel,
            int mgzTopPlatformSpringHandoffYVel,
            // Input edge tracking
            boolean jumpInputPressed, boolean jumpInputJustPressed, boolean jumpInputPressedPreviousFrame,
            boolean upInputPressed, boolean downInputPressed,
            boolean leftInputPressed, boolean rightInputPressed,
            boolean movementInputActive,
            short logicalInputState, boolean logicalJumpPressState,
            // CPU / sidekick / spiral
            boolean cpuControlled, byte historyPos, boolean followerHistoryRecordedThisTick,
            int spiralActiveFrame, byte flipAngle, byte flipSpeed,
            byte flipsRemaining, boolean flipTurned,
            // Water
            boolean inWater, boolean waterPhysicsActive, boolean wasInWater, boolean waterSkimActive,
            boolean preventTailsRespawn,
            // Other
            int badnikChainCounter,
            int bubbleAnimId,
            boolean initPhysicsActive,
            boolean objectMappingFrameControl,
            SidekickCpuRewindExtra sidekickCpuExtra
    ) {}

    /** Returns a copy of this snapshot with the given {@link PlayerRewindExtra} attached. */
    public PerObjectRewindSnapshot withPlayerExtra(PlayerRewindExtra extra) {
        return new PerObjectRewindSnapshot(
                destroyed, destroyedRespawnable,
                hasDynamicSpawn, dynamicSpawnX, dynamicSpawnY,
                preUpdateX, preUpdateY, preUpdateValid, preUpdateCollisionFlags,
                skipTouchThisFrame, solidContactFirstFrame,
                slotIndex, respawnStateIndex,
                badnikExtra,
                badnikSubclassExtra,
                extra
        );
    }

    /** Returns a copy of this snapshot with concrete badnik subclass state attached. */
    public PerObjectRewindSnapshot withBadnikSubclassExtra(BadnikSubclassRewindExtra extra) {
        return new PerObjectRewindSnapshot(
                destroyed, destroyedRespawnable,
                hasDynamicSpawn, dynamicSpawnX, dynamicSpawnY,
                preUpdateX, preUpdateY, preUpdateValid, preUpdateCollisionFlags,
                skipTouchThisFrame, solidContactFirstFrame,
                slotIndex, respawnStateIndex,
                badnikExtra,
                extra,
                playerExtra
        );
    }
}
