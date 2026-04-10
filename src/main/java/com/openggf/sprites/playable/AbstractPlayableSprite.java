package com.openggf.sprites.playable;

import com.openggf.camera.Camera;
import com.openggf.game.AnimationId;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.CollisionModel;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.InstaShieldHandle;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.PhysicsModifiers;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.PowerUpObject;
import com.openggf.game.PowerUpSpawner;
import com.openggf.game.GroundMode;
import com.openggf.game.ShieldType;
import com.openggf.game.DamageCause;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.game.RuntimeManager;
import com.openggf.timer.TimerManager;

import com.openggf.audio.GameAudioProfile;

import java.util.logging.Logger;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.Direction;
import com.openggf.physics.Sensor;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.managers.SpriteMovementManager;
import com.openggf.sprites.managers.TailsTailsController;
import com.openggf.sprites.AbstractSprite;
import com.openggf.sprites.SensorConfiguration;
import com.openggf.sprites.managers.PlayableSpriteAnimation;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.graphics.RenderPriority;
import com.openggf.sprites.animation.SpriteAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.managers.SpindashDustController;
import com.openggf.timer.timers.SpeedShoesTimer;

/**
 * Movement speeds are in subpixels (256 subpixels per pixel...).
 * 
 * @author james
 * 
 */
public abstract class AbstractPlayableSprite extends AbstractSprite implements com.openggf.game.PlayableEntity {
        private static final Logger LOGGER = Logger.getLogger(AbstractPlayableSprite.class.getName());

        protected final PlayableSpriteController controller;

        protected GroundMode runningMode = GroundMode.GROUND;

        /**
         * gSpeed is the speed this sprite is moving across the 'ground'.
         * Calculations will be performed against this and 'angle' to calculate new
         * x/y values for each step.
         */
        protected short gSpeed = 0;

        /**
         * Current angle of the terrain this sprite is on.
         */
        protected byte angle;

        /**
         * Chunk entry solidity bit indices (REV01 defaults).
         */
        protected byte topSolidBit = 0x0C;
        protected byte lrbSolidBit = 0x0D;

        /**
         * Sonic 1 loop plane state. When true, the player is on the "low plane"
         * and collision uses alternate block data (block index + 1) for loop tiles.
         * Toggled by Sonic1LoopManager based on position within 256x256 blocks and ground angle.
         */
        protected boolean loopLowPlane = false;

        /**
         * Speed (in subpixels) at which this sprite walks
         */
        protected short jump = 0;

        protected short xSpeed = 0;
        protected short ySpeed = 0;

        // ROM: Sonic_Pos_Record_Buf is $100 bytes (256 bytes / 4 bytes per entry = 64 entries)
        // Used for spindash camera lag and Tails CPU following
        private short[] xHistory = new short[64];
        private short[] yHistory = new short[64];

        // ROM: Sonic_Stat_Record_Buf is $100 bytes (256 bytes / 4 bytes per entry = 64 entries)
        // Records input buttons and status flags each frame for Tails CPU input replay.
        // Entry format matches ROM: word 0 = Ctrl_1_Logical (input), word 1 = status
        private short[] inputHistory = new short[64];
        private byte[] statusHistory = new byte[64];

        // Input bitmask constants (matching Mega Drive controller layout)
        public static final int INPUT_UP    = 0x01;
        public static final int INPUT_DOWN  = 0x02;
        public static final int INPUT_LEFT  = 0x04;
        public static final int INPUT_RIGHT = 0x08;
        public static final int INPUT_JUMP  = 0x10;

        // Status flag constants (matching ROM status byte)
        public static final byte STATUS_FACING_LEFT          = 0x01;
        public static final byte STATUS_IN_AIR               = 0x02;
        public static final byte STATUS_ROLLING              = 0x04;
        public static final byte STATUS_ON_OBJECT            = 0x08;
        public static final byte STATUS_ROLLING_JUMP         = 0x10;
        public static final byte STATUS_PUSHING              = 0x20;
        public static final byte STATUS_UNDERWATER           = 0x40;
        public static final byte STATUS_PREVENT_TAILS_RESPAWN = (byte) 0x80;

        // ROM: Sonic_Pos_Record_Index - wraps at 256 (64 entries * 4 bytes)
        private byte historyPos = 0;

        /** Whether this sprite is controlled by CPU AI (e.g., Tails follower) */
        private boolean cpuControlled = false;

        /** The CPU controller for AI-driven sprites */
        private SidekickCpuController cpuController;

        /**
         * Whether or not this sprite is rolling
         */
        protected boolean rolling = false;

        /**
         * Pinball mode flag - when set, prevents rolling from being cleared on landing
         * and prevents rolling from stopping when speed reaches 0 (gives a boost instead).
         * This matches ROM's pinball_mode (spindash_flag when rolling) behavior used by
         * spin tubes, S-curves, and other "must roll" areas.
         * See s2.asm lines 36712, 37745 for usage in rolling/landing logic.
         */
        protected boolean pinballMode = false;

        /**
         * Whether the player is in a roll-tunnel section (S1 GHZ S-tubes).
         * Suppresses the S2-derived ground wall check which falsely detects
         * narrow tunnel walls. Set by Sonic1LoopManager each frame on tunnel tiles.
         */
        protected boolean tunnelMode = false;

        /**
         * Whether the current jump originated from a rolling state.
         * In Sonic 1, 2, 3 & K, air control is locked when jumping while rolling.
         * Reset to false when landing.
         */
        protected boolean rollingJump = false;

        /**
         * Whether or not this sprite is in the air
         */
        protected boolean air = false;

        /**
         * Whether this sprite is currently jumping (ROM: jumping(a0) status bit).
         * Distinct from 'air' - you can be airborne without having jumped
         * (e.g., walked off an edge, hit by enemy, launched by spring).
         * Set when jump starts, cleared on landing.
         */
        protected boolean jumping = false;

        /**
         * ROM: $2F double_jump_flag. For Sonic: 0=available, 1=used.
         * For Knuckles: 0=available, 1=gliding, 2=falling from glide,
         * 3=sliding on ground, 4=wall climbing, 5=climbing ledge.
         * Reset on landing and on damage.
         */
        protected int doubleJumpFlag = 0;

        /**
         * ROM: double_jump_property(a0). For Knuckles glide: angle byte controlling
         * glide direction. 0x00 = facing right, 0x80 (-128) = facing left.
         * Updated per-frame based on left/right input during glide.
         */
        protected byte doubleJumpProperty = 0;

        /**
         * X position when Knuckles grabbed a wall during glide (ROM: x_pos+2).
         * Used by wall climb to maintain horizontal position against the wall.
         */
        protected short wallClimbX = 0;

        /**
         * Whether this sprite is standing on a solid object (platform, moving block).
         * ROM: status.player.on_object
         * When true, AnglePos skips terrain collision - the object handles positioning.
         */
        protected boolean onObject = false;

        /**
         * Whether to stick to convex surfaces even at low speeds.
         * ROM: stick_to_convex status bit
         * When true, prevents slope repel/detachment on convex terrain.
         */
        protected boolean stickToConvex = false;

        /**
         * Whether this sprite is on an oil slide in OOZ.
         * ROM: status_secondary.sliding
         */
        protected boolean sliding = false;

        /**
         * Whether or not this sprite is pushing a solid object.
         */
        protected boolean pushing = false;

        /**
         * Whether or not this sprite is currently skidding (braking).
         * ROM: Set when pressing opposite direction from movement at speed >= 0x400.
         */
        protected boolean skidding = false;

        /**
         * Timer for skid dust spawning. ROM spawns dust every 4 frames while skidding.
         * Decrements each frame; when < 0, spawn dust and reset to 3.
         */
        protected int skidDustTimer = 0;

        /**
         * Frames remaining for post-hit invulnerability.
         */
        protected int invulnerableFrames = 0;

        /**
         * Frames remaining for invincibility power-up.
         */
        protected int invincibleFrames = 0;

        /**
         * Whether or not this sprite is in the spring animation state.
         */
        protected boolean springing = false;

        /**
         * Frames remaining for springing state.
         */
        protected int springingFrames = 0;

        /**
         * Whether or not this sprite is dead.
         */
        protected boolean dead = false;

        /**
         * Whether the player is in the drowning pre-death phase.
         * ROM: 120-frame countdown where controls are locked, ySpeed increases by $10/frame,
         * and the drown animation (0x17) plays. After 120 frames, transitions to dead=true.
         * ROM ref: s2.asm:41729-41768, s1disasm/0A Drowning Countdown.asm:229-264
         */
        protected boolean drowningDeath = false;

        /**
         * Timer for the drowning pre-death phase. Counts down from 120 frames.
         * When it reaches 0, the player transitions to the dead state.
         */
        protected int drownPreDeathTimer = 0;

        /**
         * Whether or not this sprite is in the hurt/knockback state.
         * Mirrors ROM routine=4 check. Invulnerability is set when landing from hurt.
         */
        protected boolean hurt = false;

        /**
         * Countdown frames before level reload after death.
         * Set to 60 when player falls off screen, decrements each frame.
         * When it reaches 0, triggers level reload.
         */
        protected int deathCountdown = 0;

        /**
         * Whether or not this sprite is preparing for a spindash.
         */
        protected boolean spindash = false;
        /**
         * Whether or not this sprite is crouching.
         */
        protected boolean crouching = false;

        /**
         * Whether or not this sprite is looking up (holding up while standing still).
         */
        protected boolean lookingUp = false;

        /**
         * ROM: Balance animation state when standing at a ledge edge.
         * 0 = not balancing
         * 1 = BALANCE (0x06) - safe distance, facing toward edge
         * 2 = BALANCE2 (0x0C) - closer to edge, facing toward edge
         * 3 = BALANCE3 (0x1D) - safe distance, facing away from edge
         * 4 = BALANCE4 (0x1E) - closer to edge, facing away from edge
         * See s2.asm:36246-36373 for the balance detection logic.
         */
        protected int balanceState = 0;

        /**
         * ROM-accurate spindash counter (spindash_counter).
         * Range: 0x000 to 0x800 (0 to 2048).
         * Speed table is indexed by counter >> 8 (gives 0-8).
         */
        protected short spindashCounter = 0;

        /**
         * ROM: Sonic_Look_delay_counter / Tails_Look_delay_counter
         * Counter for look up/down camera pan delay. Increments each frame while
         * up/down is held. Camera only starts panning after this reaches 0x78 (120 frames).
         * Reset to 0 when neither up nor down is pressed.
         */
        protected short lookDelayCounter = 0;

        /**
         * ROM: player byte at $41(a0), used by Player_WalkVertR as a short-lived
         * countdown after deep right-wall penetration in zone/act 0.
         */
        protected int rightWallPenetrationTimer = 0;

        private PlayerSpriteRenderer spriteRenderer;
        private int mappingFrame = 0;
        private int animationFrameCount = 0;
        private SpriteAnimationProfile animationProfile;
        private SpriteAnimationSet animationSet;
        private int animationId = 0;
        /** When >= 0, overrides profile-based animation resolution (e.g., Tails CPU fly anim). */
        private int forcedAnimationId = -1;
        /** Resolved native animation ID for CanonicalAnimation.BUBBLE. -1 if unsupported. */
        private int bubbleAnimId = -1;
        /**
         * When true, object code owns mapping_frame updates for this player.
         * Animation manager still handles flip state and controllers, but does not
         * overwrite mapping_frame.
         */
        private boolean objectMappingFrameControl = false;
        private int animationFrameIndex = 0;
        private int animationTick = 0;
        private boolean renderHFlip = false;
        private boolean renderVFlip = false;
        private boolean highPriority = false;
        private int priorityBucket = RenderPriority.PLAYER_DEFAULT;

        protected boolean shield = false;
        private ShieldType shieldType = null;
        private PowerUpObject shieldObject;
        private InstaShieldHandle instaShieldObject;
        private boolean instaShieldRegistered = false;
        private PowerUpObject invincibilityObject;
        private PowerUpSpawner powerUpSpawner;
        protected boolean speedShoes = false;
        /**
         * Super Sonic state flag.
         * Exposed for object scripts that branch on Super Sonic (e.g. ObjB2 jump timing).
         */
        protected boolean superSonic = false;

        // Physics provider fields — populated from GameModule when available
        private PhysicsProfile physicsProfile;
        private GameModule runtimeBoundStateModule;
        private PhysicsModifiers physicsModifiers;
        private PhysicsFeatureSet physicsFeatureSet;

        /**
         * Canonical "reset" profile — the base used for water/shoes modifier math.
         * For S3K, this is SONIC_2_SONIC ($600/$C/$80), which differs from the init profile.
         * For S1/S2, this is null (init profile equals canonical).
         */
        private PhysicsProfile canonicalProfile;
        /**
         * When true, the Character_Speeds init values (from {@code PhysicsProvider.getInitProfile()})
         * are active in the mutable speed fields. Cleared on first water, speed shoes, or Super
         * transition — those events reset the mutable fields to the canonical profile values.
         * <p>ROM ref: sonic3k.asm:202288 (Character_Speeds table loaded at init/respawn).
         */
        private boolean initPhysicsActive;

        /**
         * When true, forces right input regardless of actual keyboard input.
         * Used for end-of-act walk-off sequences (Control_Locked + button_right_mask in
         * ROM).
         */
        protected boolean forceInputRight = false;
        /**
         * ROM-style forced logical input bits set by object scripts.
         */
        protected int forcedInputMask = 0;
        /**
         * When true, BK2 playback detected a new action button press edge (A/B/C cycling).
         */
        protected boolean forcedJumpPress = false;
        protected boolean suppressNextJumpPress = false;
        protected boolean deferredObjectControlRelease = false;
        /**
         * When true, user inputs are ignored (Control_Locked in ROM).
         */
        protected boolean controlLocked = false;
        /**
         * Movement lock timer (ROM: move_lock). When > 0, player input is ignored
         * and the player cannot move. Decremented each frame.
         * Used by: air bubble collection (35 frames), springs, hurt state, etc.
         */
        protected int moveLockTimer = 0;
        /**
         * When true, an object has full control of the player and normal physics
         * (gravity, movement, collision) are skipped. This matches the ROM's
         * obj_control = $81 behavior used by spin tubes, corkscrews, etc.
         */
        protected boolean objectControlled = false;
        /**
         * When true, the sprite is not rendered. Used by the Giant Ring flash
         * to make Sonic invisible during the special stage entry sequence.
         * ROM: move.b #id_Null,(v_player+obAnim).w
         */
        protected boolean hidden = false;
        /**
         * Frame number when the player was last released from object control.
         * Used to prevent immediate re-capture by nearby objects (e.g., spin tubes).
         */
        protected int objectControlReleasedFrame = Integer.MIN_VALUE;
        /**
         * Tracks whether the jump button is currently pressed this frame.
         * Set by movement manager, used by objects (like flippers) to detect jump
         * input.
         */
        protected boolean jumpInputPressed = false;
        /**
         * Tracks whether jump transitioned from not-pressed to pressed this frame,
         * including forced/demo input, matching the ROM's logical-pad low-byte checks.
         */
        protected boolean jumpInputJustPressed = false;
        /**
         * Previous frame combined jump state (player input OR forced input).
         */
        protected boolean jumpInputPressedPreviousFrame = false;
        /**
         * Tracks whether the up button is currently pressed this frame.
         * Set by SpriteManager, used by objects (like VineSwitch) to detect directional input.
         */
        protected boolean upInputPressed = false;
        /**
         * Tracks whether the down button is currently pressed this frame.
         * Set by SpriteManager, used by objects (like VineSwitch) to detect directional input.
         */
        protected boolean downInputPressed = false;
        /**
         * Tracks whether the left button is currently pressed this frame.
         * Set by SpriteManager, used by objects (like Grabber) to detect directional input.
         */
        protected boolean leftInputPressed = false;
        /**
         * Tracks whether the right button is currently pressed this frame.
         * Set by SpriteManager, used by objects (like Grabber) to detect directional input.
         */
        protected boolean rightInputPressed = false;
        /**
         * Tracks whether the player is actively pressing a movement direction this frame,
         * after filtering for control locks and move locks. Used by animation system to
         * determine walk vs idle animation per ROM behavior (s2.asm:36558, 36619).
         * ROM: Walking animation is set in Sonic_MoveLeft/MoveRight, which are only called
         * when directional input passes control lock checks.
         */
        protected boolean movementInputActive = false;
        private int spiralActiveFrame = Integer.MIN_VALUE;
        private byte flipAngle = 0;
        private byte flipSpeed = 0;
        private byte flipsRemaining = 0;
        private boolean flipTurned = false;

        /**
         * Whether this sprite is currently underwater.
         * Affects physics constants and triggers entry/exit speed changes.
         */
        protected boolean inWater = false;
        protected boolean preventTailsRespawn = false;
        /**
         * Previous frame's water state, used for detecting transitions.
         */
        protected boolean wasInWater = false;
        /**
         * When true, the player is running across the water surface (HCZ skim).
         * Prevents onEnterWater() from triggering while feet are at water level.
         * Set by {@link com.openggf.game.sonic3k.features.HCZWaterSkimHandler}.
         */
        protected boolean waterSkimActive = false;
        /**
         * Manages drowning mechanics while underwater (air countdown, bubbles, etc.).
         */

        /**
         * Sets the power-up spawner used to create shield, invincibility, splash
         * and insta-shield objects. Injected by {@code LevelManager} during player
         * initialization so the sprite does not need to know the concrete object types.
         */
        public void setPowerUpSpawner(PowerUpSpawner spawner) {
                this.powerUpSpawner = spawner;
        }

        /**
         * Returns the current power-up spawner, or {@code null} if not yet injected.
         */
        public PowerUpSpawner getPowerUpSpawner() {
                return powerUpSpawner;
        }

        /**
         * Clears all active power-ups (shield, invincibility, speed shoes).
         * Called when entering special stage to remove power-up effects.
         */
        public void clearPowerUps() {
                // Clear shield
                this.shield = false;
                this.shieldType = null;
                if (this.shieldObject != null) {
                        this.shieldObject.destroy();
                        this.shieldObject = null;
                }
                // Clear invincibility
                if (this.invincibilityObject != null) {
                        this.invincibilityObject.destroy();
                        this.invincibilityObject = null;
                }
                this.invincibleFrames = 0;
                // Clear speed shoes
                if (this.speedShoes) {
                        this.speedShoes = false;
                        currentTimerManager().removeTimerForCode("SpeedShoes-" + getCode());
                        defineSpeeds(); // Reset speeds to default
                }
                // Clear Super state
                this.superSonic = false;
                if (controller != null && controller.getSuperState() != null) {
                        controller.getSuperState().reset();
                }
        }

        public void resetState() {
                this.shield = false;
                this.shieldType = null;
                if (this.shieldObject != null) {
                        this.shieldObject.destroy();
                        this.shieldObject = null;
                }
                if (this.invincibilityObject != null) {
                        this.invincibilityObject.destroy();
                        this.invincibilityObject = null;
                }
                this.speedShoes = false;
                // Cancel any active speed shoes timer
                currentTimerManager().removeTimerForCode("SpeedShoes-" + getCode());
                this.invincibleFrames = 0;
                this.invulnerableFrames = 0;
                this.invincibleFrames = 0;
                this.invulnerableFrames = 0;
                // Ring count is managed by LevelGamestate and reset by LevelManager
                this.dead = false;
                this.drowningDeath = false;
                this.drownPreDeathTimer = 0;
                this.hurt = false;
                this.deathCountdown = 0;
                this.air = false;
                this.jumping = false;
                this.doubleJumpFlag = 0;
                this.doubleJumpProperty = 0;
                this.objectMappingFrameControl = false;
                this.forcedAnimationId = -1;
                this.onObject = false;
                this.sliding = false;
                this.stickToConvex = false;
                // Reset ground mode to GROUND - critical for sensor direction on level load.
                // Without this, if player was on a wall/ceiling when previous level ended,
                // sensors would point in wrong direction and collision detection would fail.
                this.runningMode = GroundMode.GROUND;
                this.springing = false;
                this.springingFrames = 0;
                this.rolling = false;
                this.rollingJump = false;
                this.pinballMode = false;
                this.tunnelMode = false;
                this.spindash = false;
                this.lookDelayCounter = 0;
                this.rightWallPenetrationTimer = 0;
                this.pushing = false;
                this.skidding = false;
                this.skidDustTimer = 0;
                this.crouching = false;
                this.lookingUp = false;
                this.balanceState = 0;
                this.highPriority = false;
                this.priorityBucket = RenderPriority.PLAYER_DEFAULT;
                this.forceInputRight = false;
                this.forcedInputMask = 0;
                this.forcedAnimationId = -1;
                this.controlLocked = false;
                this.moveLockTimer = 0;
                this.objectControlled = false;
                this.hidden = false;
                this.objectControlReleasedFrame = Integer.MIN_VALUE;
                this.jumpInputPressed = false;
                this.jumpInputJustPressed = false;
                this.jumpInputPressedPreviousFrame = false;
                this.movementInputActive = false;
                this.spiralActiveFrame = Integer.MIN_VALUE;
                this.flipAngle = 0;
                this.flipSpeed = 0;
                this.flipsRemaining = 0;
                this.flipTurned = false;
                this.inWater = false;
                this.wasInWater = false;
                this.waterSkimActive = false;
                this.preventTailsRespawn = false;
                this.superSonic = false;
                if (controller != null && controller.getSuperState() != null) {
                        controller.getSuperState().reset();
                }
                // Reset collision path to Path 0 (primary collision).
                // Without this, if player was on Path 1 in previous level,
                // solidity bits would remain 0x0E/0x0F causing collision checks
                // against the wrong collision map, making player fall through floors.
                this.topSolidBit = 0x0C;
                this.lrbSolidBit = 0x0D;
                this.loopLowPlane = false;
                defineSpeeds(); // Reset speeds to default
                instaShieldRegistered = false; // Force re-registration with new ObjectManager on level load
                resolvePhysicsProfile();
                // ROM: Obj01_Init unconditionally sets y_radius=$13, x_radius=9.
                // Since we reuse the sprite rather than recreating it, we must
                // explicitly restore standing dimensions and sensor offsets here.
                setHeight(runHeight);
                applyStandingRadii(false);
        }

        public void giveShield() {
                giveShield(ShieldType.BASIC);
        }

        public void giveShield(ShieldType type) {
                // S2: Super Sonic cannot pick up shields
                if (superSonic) {
                        return;
                }
                // Remove existing shield before granting new one
                if (hasShield()) {
                        if (shieldObject != null) {
                                shieldObject.destroy();
                                shieldObject = null;
                        }
                }
                this.shield = true;
                this.shieldType = type;
                LOGGER.fine("DEBUG: Shield flag set to true, type=" + type);
                // Bubble shield replenishes air (s3.asm:34877 Player_ResetAirTimer)
                if (type == ShieldType.BUBBLE && controller != null && controller.getDrowning() != null) {
                        controller.getDrowning().replenishAir();
                }
                try {
                        this.shieldObject = powerUpSpawner != null
                                ? powerUpSpawner.spawnShield(this, type)
                                : null;
                        LOGGER.fine("DEBUG: ShieldObjectInstance created successfully: " + shieldObject);
                        // If picked up while invincible, hide shield until invincibility ends
                        if (shieldObject != null && invincibleFrames > 0) {
                                shieldObject.setVisible(false);
                        }
                } catch (Exception e) {
                        LOGGER.fine("DEBUG: Failed to create/add ShieldObjectInstance: " + e.getMessage());
                        throw e;
                }
        }

        public ShieldType getShieldType() {
                return shieldType;
        }

        public PowerUpObject getShieldObject() {
                return shieldObject;
        }

        public InstaShieldHandle getInstaShieldObject() {
                return instaShieldObject;
        }

        public void setInstaShieldObject(InstaShieldHandle obj) {
                this.instaShieldObject = obj;
        }

        /**
         * Marks the insta-shield for re-registration with the current ObjectManager.
         * Must be called after seamless transitions that rebuild the ObjectManager,
         * since the old manager (which held the dynamic object) is replaced.
         */
        public void markInstaShieldForReregistration() {
                this.instaShieldRegistered = false;
        }

        public PowerUpObject getInvincibilityObject() {
                return invincibilityObject;
        }

        public void giveSpeedShoes() {
                // S3K: shoes activation uses absolute values from canonical base ($C00/$18/$80),
                // not 2x of Character_Speeds init values (sonic3k.asm:40823-40825)
                clearInitOverride();
                this.speedShoes = true;
                // Register speed shoes timer using the existing timer framework
                // Duration is 1200 frames (20 seconds @ 60fps) per SPG Sonic 2
                currentTimerManager().registerTimer(
                                new SpeedShoesTimer("SpeedShoes-" + getCode(), this));
        }

        /**
         * Called by SpeedShoesTimer when the effect expires.
         * Deactivates speed shoes and resets physics values.
         * ROM: sets absolute canonical values ($600/$C/$80 normal, $A00/$30/$100 Super Sonic).
         */
        public void deactivateSpeedShoes() {
                clearInitOverride();
                this.speedShoes = false;
        }

        public void giveInvincibility() {
                setInvincibleFrames(1200); // 20 seconds @ 60fps
                if (shieldObject != null) {
                        shieldObject.setVisible(false);
                }
                if (invincibilityObject == null && powerUpSpawner != null) {
                        invincibilityObject = powerUpSpawner.spawnInvincibilityStars(this);
                }
        }

        public boolean hasShield() {
                return shield;
        }

        /**
         * Shows or hides the shield object without removing the shield power-up.
         * Used by Super Sonic (hides shield while active, re-shows on revert).
         */
        public void setShieldVisible(boolean visible) {
                if (shieldObject != null) {
                        shieldObject.setVisible(visible);
                }
        }

        public boolean hasSpeedShoes() {
                return speedShoes;
        }

        public boolean isSuperSonic() {
                return superSonic;
        }

        public final Camera currentCamera() {
                return GameServices.camera();
        }

        public final LevelManager currentLevelManager() {
                return GameServices.level();
        }

        public final LevelManager currentLevelManagerIfAvailable() {
                var runtime = RuntimeManager.getActiveRuntime();
                return runtime != null ? runtime.getLevelManager() : null;
        }

        public final GameModule currentGameModule() {
                return RuntimeManager.resolveCurrentOrBootstrapGameModule();
        }

        public final int resolveAnimationId(CanonicalAnimation animation) {
                refreshRuntimeBoundStateIfNeeded();
                GameModule module = currentGameModule();
                return module != null ? module.resolveAnimationId(animation) : -1;
        }

        public final LevelState currentLevelState() {
                LevelManager levelManager = currentLevelManagerIfAvailable();
                return levelManager != null ? levelManager.getLevelGamestate() : null;
        }

        public final TimerManager currentTimerManager() {
                return GameServices.timers();
        }

        public final GameStateManager currentGameState() {
                return GameServices.gameState();
        }

        public final CollisionSystem currentCollisionSystem() {
                return GameServices.collision();
        }

        public final AudioManager currentAudioManager() {
                return GameServices.audio();
        }

        public final WaterSystem currentWaterSystem() {
                return GameServices.water();
        }

        /**
         * Returns this character's secondary (double-jump) ability.
         * ROM: character_id determines Sonic=insta-shield, Tails=fly, Knuckles=glide.
         * Subclasses override to declare their ability; default is NONE.
         */
        public SecondaryAbility getSecondaryAbility() {
                return SecondaryAbility.NONE;
        }

        public void setSuperSonic(boolean superSonic) {
                this.superSonic = superSonic;
        }

        public int getRingCount() {
                var levelState = currentLevelState();
                return levelState != null ? levelState.getRings() : 0;
        }

        public void setRingCount(int ringCount) {
                var levelState = currentLevelState();
                if (levelState != null) {
                        levelState.setRings(ringCount);
                }
        }

        public void addRings(int delta) {
                var levelState = currentLevelState();
                if (levelState != null) {
                        levelState.addRings(delta);
                }
        }

        public PlayerSpriteRenderer getSpriteRenderer() {
                return spriteRenderer;
        }

        public void setSpriteRenderer(PlayerSpriteRenderer spriteRenderer) {
                this.spriteRenderer = spriteRenderer;
        }

        public int getMappingFrame() {
                return mappingFrame;
        }

        public void setMappingFrame(int mappingFrame) {
                this.mappingFrame = Math.max(0, mappingFrame);
        }

        public int getAnimationFrameCount() {
                return animationFrameCount;
        }

        public void setAnimationFrameCount(int animationFrameCount) {
                this.animationFrameCount = Math.max(0, animationFrameCount);
        }

        public SpriteAnimationProfile getAnimationProfile() {
                return animationProfile;
        }

        public void setAnimationProfile(SpriteAnimationProfile animationProfile) {
                this.animationProfile = animationProfile;
        }

        public SpriteAnimationSet getAnimationSet() {
                return animationSet;
        }

        public void setAnimationSet(SpriteAnimationSet animationSet) {
                this.animationSet = animationSet;
        }

        public int getAnimationId() {
                return animationId;
        }

        public void setAnimationId(int animationId) {
                this.animationId = Math.max(0, animationId);
        }

        public void setAnimationId(AnimationId animationId) {
                this.animationId = Math.max(0, animationId.id());
        }

        public int getForcedAnimationId() {
                return forcedAnimationId;
        }

        public void setForcedAnimationId(int forcedAnimationId) {
                this.forcedAnimationId = forcedAnimationId;
        }

        public void setForcedAnimationId(AnimationId animationId) {
                this.forcedAnimationId = animationId.id();
        }

        public boolean isObjectMappingFrameControl() {
                return objectMappingFrameControl;
        }

        public void setObjectMappingFrameControl(boolean objectMappingFrameControl) {
                this.objectMappingFrameControl = objectMappingFrameControl;
        }

        public int getAnimationFrameIndex() {
                return animationFrameIndex;
        }

        public void setAnimationFrameIndex(int animationFrameIndex) {
                this.animationFrameIndex = Math.max(0, animationFrameIndex);
        }

        public int getAnimationTick() {
                return animationTick;
        }

        public void setAnimationTick(int animationTick) {
                this.animationTick = Math.max(0, animationTick);
        }

        public SpindashDustController getSpindashDustController() {
                return controller.getSpindashDust();
        }

        public void setSpindashDustController(SpindashDustController spindashDustController) {
                controller.setSpindashDust(spindashDustController);
        }

        public TailsTailsController getTailsTailsController() {
                return controller.getTailsTails();
        }

        public void setTailsTailsController(TailsTailsController tailsTailsController) {
                controller.setTailsTails(tailsTailsController);
        }

        public SuperStateController getSuperStateController() {
                return controller.getSuperState();
        }

        public void setSuperStateController(SuperStateController superStateController) {
                controller.setSuperState(superStateController);
        }

        public boolean getRenderHFlip() {
                return renderHFlip;
        }

        public boolean getRenderVFlip() {
                return renderVFlip;
        }

        public void setRenderFlips(boolean hFlip, boolean vFlip) {
                this.renderHFlip = hFlip;
                this.renderVFlip = vFlip;
        }

        public boolean isHighPriority() {
                return highPriority;
        }

        public void setHighPriority(boolean highPriority) {
                this.highPriority = highPriority;
        }

        public int getPriorityBucket() {
                return priorityBucket;
        }

        public void setPriorityBucket(int bucket) {
                this.priorityBucket = RenderPriority.clamp(bucket);
        }

        public boolean getAir() {
                return air;
        }

        public void setAir(boolean air) {
                // If landing from hurt state, clear hurt flag and high-priority rendering
                // (invulnerableFrames already set in applyHurt() per ROM behavior)
                if (!air && this.air && hurt) {
                        hurt = false;
                        setHighPriority(false);
                        // ROM: Sonic_HurtStop resets invulnerable_time to $78 on landing.
                        // All 120 frames of post-hit flashing occur after landing.
                        invulnerableFrames = 0x78;
                }
                // Reset rolling jump flag when landing
                if (!air && this.air) {
                        rollingJump = false;
                }
                // Clear jumping flag and double-jump flag when landing
                if (!air && this.air) {
                        jumping = false;
                        // Restore standing radii if coming out of a glide state
                        // (glide uses custom 10x10 radii that won't be restored by
                        // setRolling(false) since rolling was already false)
                        if (doubleJumpFlag > 0 && !rolling) {
                                applyStandingRadii(false);
                                objectMappingFrameControl = false;
                                forcedAnimationId = -1;
                        }
                        doubleJumpFlag = 0;
                        doubleJumpProperty = 0;
                        // S1 ROM parity (Sonic_ResetOnFloor):
                        // move.w #0,(v_itembonus).w ; clear enemy/block score chain.
                        currentGameState().resetItemBonus();
                }
                this.air = air;
                // SPG: Push sensor Y offset changes based on air state
                updatePushSensorYOffset();
                if (air) {
                        setGroundMode(GroundMode.GROUND);
                        // SPG: Angle should gradually return to 0 while airborne,
                        // NOT immediately reset. See returnAngleToZero() called during air updates.
                } else {
                        // Reset badnik chain when landing
                        resetBadnikChain();
                }
        }

        public boolean isJumping() {
                return jumping;
        }

        public void setJumping(boolean jumping) {
                this.jumping = jumping;
        }

        public int getDoubleJumpFlag() {
                return doubleJumpFlag;
        }

        public void setDoubleJumpFlag(int doubleJumpFlag) {
                this.doubleJumpFlag = doubleJumpFlag;
        }

        public byte getDoubleJumpProperty() {
                return doubleJumpProperty;
        }

        public void setDoubleJumpProperty(byte doubleJumpProperty) {
                this.doubleJumpProperty = doubleJumpProperty;
        }

        public short getWallClimbX() {
                return wallClimbX;
        }

        public void setWallClimbX(short wallClimbX) {
                this.wallClimbX = wallClimbX;
        }

        public boolean isOnObject() {
                return onObject;
        }

        public void setOnObject(boolean onObject) {
                this.onObject = onObject;
        }

        public boolean isSliding() {
                return sliding;
        }

        public void setSliding(boolean sliding) {
                this.sliding = sliding;
        }

        public boolean isStickToConvex() {
                return stickToConvex;
        }

        public void setStickToConvex(boolean stickToConvex) {
                this.stickToConvex = stickToConvex;
        }

        /**
         * SPG: While airborne, Ground Angle smoothly returns toward 0 by 2 hex units per frame.
         * This affects the visual rotation of the sprite during air time.
         * Call this once per frame while airborne.
         */
        public void returnAngleToZero() {
                int currentAngle = angle & 0xFF;
                if (currentAngle == 0) {
                        return; // Already at 0
                }
                // ROM: Sonic_JumpAngle (s2.asm:37465-37486)
                // ROM uses bpl (branch if positive, i.e., bit 7 clear) to determine direction
                // Angles 0x01-0x7F (1-127): bit 7 clear = positive, subtract 2
                // Angles 0x80-0xFF (128-255): bit 7 set = negative, add 2
                if (currentAngle < 128) {
                        // Positive range (0x01-0x7F): decrease toward 0
                        currentAngle -= 2;
                        if (currentAngle < 0) {
                                currentAngle = 0;
                        }
                } else {
                        // Negative range (0x80-0xFF): increase toward 0 (wrapping through 256)
                        currentAngle += 2;
                        if (currentAngle >= 256) {
                                currentAngle = 0;
                        }
                }
                angle = (byte) currentAngle;
        }

        private int badnikChainCounter = 0;

        public void resetBadnikChain() {
                badnikChainCounter = 0;
        }

        public int incrementBadnikChain() {
                badnikChainCounter++;
                // 1st: 100, 2nd: 200, 3rd: 500, 4th+: 1000
                return switch (badnikChainCounter) {
                        case 1 -> 100;
                        case 2 -> 200;
                        case 3 -> 500;
                        default -> 1000;
                };
        }

        public byte getTopSolidBit() {
                return topSolidBit;
        }

        public void setTopSolidBit(byte topSolidBit) {
                if (physicsFeatureSet != null && !physicsFeatureSet.hasDualCollisionPaths()) {
                        return;
                }
                this.topSolidBit = topSolidBit;
        }

        public byte getLrbSolidBit() {
                return lrbSolidBit;
        }

        public void setLrbSolidBit(byte lrbSolidBit) {
                if (physicsFeatureSet != null && !physicsFeatureSet.hasDualCollisionPaths()) {
                        return;
                }
                this.lrbSolidBit = lrbSolidBit;
        }

        public boolean isLoopLowPlane() {
                return loopLowPlane;
        }

        public void setLoopLowPlane(boolean loopLowPlane) {
                this.loopLowPlane = loopLowPlane;
        }

        public short getJump() {
                if (physicsModifiers != null) {
                        return physicsModifiers.effectiveJump(jump, inWater);
                }
                // Fallback: Water reduced jump force (ROM s2.asm line 37019: 0x380 vs normal 0x680)
                if (inWater) {
                        return 0x380;
                }
                return jump;
        }

        public boolean getSpindash() {
                return spindash;
        }

        public void setSpindash(boolean spindash) {
                this.spindash = spindash;
        }

        public boolean getCrouching() {
                return crouching;
        }

        public void setCrouching(boolean crouching) {
                this.crouching = crouching;
        }

        public boolean getLookingUp() {
                return lookingUp;
        }

        public void setLookingUp(boolean lookingUp) {
                this.lookingUp = lookingUp;
        }

        public int getBalanceState() {
                return balanceState;
        }

        public void setBalanceState(int balanceState) {
                this.balanceState = balanceState;
        }

        public boolean isBalancing() {
                return balanceState > 0;
        }

        public boolean getPushing() {
                return pushing;
        }

        public void setPushing(boolean pushing) {
                this.pushing = pushing;
        }

        public boolean getSkidding() {
                return skidding;
        }

        public void setSkidding(boolean skidding) {
                this.skidding = skidding;
                if (!skidding) {
                        // Reset dust timer when skidding ends
                        this.skidDustTimer = 0;
                }
        }

        public int getSkidDustTimer() {
                return skidDustTimer;
        }

        public void setSkidDustTimer(int timer) {
                this.skidDustTimer = timer;
        }

        public boolean getInvulnerable() {
                // Debug mode and Super Sonic make player completely invulnerable
                return debugMode || superSonic || invulnerableFrames > 0 || invincibleFrames > 0 || hurt;
        }

        public int getInvulnerableFrames() {
                return invulnerableFrames;
        }

        public void setInvulnerableFrames(int frames) {
                invulnerableFrames = Math.max(0, frames);
        }

        public int getInvincibleFrames() {
                return invincibleFrames;
        }

        public void setInvincibleFrames(int frames) {
                invincibleFrames = Math.max(0, frames);
        }

        public boolean getSpringing() {
                return springing;
        }

        public boolean getDead() {
                return dead;
        }

        public void setDead(boolean dead) {
                this.dead = dead;
        }

        /**
         * Whether the player is in the drowning pre-death phase (120-frame sink before death).
         */
        public boolean isDrowningPreDeath() {
                return drowningDeath && drownPreDeathTimer > 0;
        }

        /**
         * Whether this death is a drowning death (used for animation selection).
         * True during both the pre-death sink phase and the subsequent dead fall.
         */
        public boolean isDrowningDeath() {
                return drowningDeath;
        }

        /**
         * Ticks the drown pre-death timer. Returns true when the timer expires (transition to dead).
         */
        public boolean tickDrownPreDeath() {
                if (drownPreDeathTimer > 0) {
                        drownPreDeathTimer--;
                        return drownPreDeathTimer <= 0;
                }
                return false;
        }

        public boolean isHurt() {
                return hurt;
        }

        public void setHurt(boolean hurt) {
                this.hurt = hurt;
        }

        public int getDeathCountdown() {
                return deathCountdown;
        }

        public void setDeathCountdown(int frames) {
                this.deathCountdown = Math.max(0, frames);
        }

        /**
         * Starts the death sequence countdown (60 frames).
         * Called when player falls below the level boundaries.
         */
        public void startDeathCountdown() {
                if (deathCountdown == 0 && dead) {
                        deathCountdown = 60;
                }
        }

        /**
         * Decrements death countdown and returns true if level should reload.
         */
        public boolean tickDeathCountdown() {
                if (deathCountdown > 0) {
                        deathCountdown--;
                        if (deathCountdown == 0) {
                                return true; // Time to reload level
                        }
                }
                return false;
        }

        public void setSpringing(int frames) {
                if (frames <= 0) {
                        springing = false;
                        springingFrames = 0;
                        return;
                }
                springing = true;
                springingFrames = frames;
        }

        public void tickStatus() {
                refreshRuntimeBoundStateIfNeeded();
                // ROM: invulnerable_time only decrements in Sonic_Display (routine 2).
                // During hurt routine (routine 4), DisplaySprite is called directly,
                // so the timer stays frozen until Sonic lands.
                if (invulnerableFrames > 0 && !hurt) {
                        invulnerableFrames--;
                }
                if (invincibleFrames > 0) {
                        invincibleFrames--;
                        if (invincibleFrames == 0) {
                                if (invincibilityObject != null) {
                                        invincibilityObject.destroy();
                                        invincibilityObject = null;
                                }
                                if (shieldObject != null) {
                                        shieldObject.setVisible(true);
                                }
                                AudioManager audioManager = currentAudioManager();
                                GameAudioProfile audioProfile = audioManager.getAudioProfile();
                                if (audioProfile != null) {
                                        audioManager.endMusicOverride(audioProfile.getInvincibilityMusicId());
                                }
                        }
                }
                if (springingFrames > 0) {
                        springingFrames--;
                        if (springingFrames == 0) {
                                springing = false;
                        }
                }
                // Speed shoes countdown is now handled by SpeedShoesTimer

                // Update Super Sonic state (ring drain, palette cycling, transformation)
                if (controller != null && controller.getSuperState() != null) {
                        controller.getSuperState().update();
                }
                // Lazy-register insta-shield with ObjectManager if not yet done (e.g. created before level load).
                // When registered, ObjectManager drives update(); explicit call only needed for headless tests.
                if (instaShieldObject != null && !instaShieldRegistered) {
                        if (powerUpSpawner != null) {
                                powerUpSpawner.registerObject(instaShieldObject);
                                instaShieldRegistered = true;
                        } else {
                                instaShieldObject.update(0, this);
                        }
                }
        }

        public boolean applyHurt(int sourceX) {
                return applyHurt(sourceX, false);
        }

        public boolean applyHurt(int sourceX, boolean spikeHit) {
                DamageCause cause = spikeHit ? DamageCause.SPIKE : DamageCause.NORMAL;
                return applyHurt(sourceX, cause);
        }

        public boolean applyHurt(int sourceX, DamageCause cause) {
                return applyHurt(sourceX, cause, false);
        }

        /**
         * Applies hurt while ignoring post-hit invulnerability frames.
         * <p>
         * This still respects debug invulnerability and invincibility power-up.
         * Intended for Sonic 1 spike behavior.
         */
        public boolean applyHurtIgnoringIFrames(int sourceX, boolean spikeHit) {
                DamageCause cause = spikeHit ? DamageCause.SPIKE : DamageCause.NORMAL;
                return applyHurt(sourceX, cause, true);
        }

        public boolean applyHurtIgnoringIFrames(int sourceX, DamageCause cause) {
                return applyHurt(sourceX, cause, true);
        }

        private boolean applyHurt(int sourceX, DamageCause cause, boolean ignoreIFrames) {
                if (isDamageBlocked(ignoreIFrames)) {
                        return false;
                }

                // Fire shield blocks fire damage (s3.asm shield_reaction bit 4)
                PhysicsFeatureSet fs = getPhysicsFeatureSet();
                if (cause == DamageCause.FIRE && shield && shieldType == ShieldType.FIRE
                                && fs != null && fs.elementalShieldsEnabled()) {
                        return false;
                }

                if (shield) {
                        LOGGER.fine("DEBUG: applyHurt called. removing shield.");
                        shield = false;
                        shieldType = null;
                        if (shieldObject != null) {
                                shieldObject.destroy();
                                shieldObject = null;
                        }
                        // Shield loss sound overrides generic hurt sound if desired, but often it's
                        // just HURT.
                        // However, we MUST prevent death logic if we had no rings.
                        // Handled in applyHurtOrDeath.
                } else {
                        LOGGER.fine("DEBUG: applyHurt called. No shield.");
                }

                hurt = true;
                doubleJumpFlag = 0;
                doubleJumpProperty = 0;
                objectMappingFrameControl = false;
                forcedAnimationId = -1;
                setInvulnerableFrames(0x78); // Set invulnerability immediately (ROM: s2.asm line 84954)
                setSpringing(0);
                setSpindash(false);

                // ROM: Sonic_ResetOnFloor adjusts Y when transitioning from rolling to standing.
                // s1.asm: "subq.w #5,obY(a0)" — subtracts radius diff from y_pos word only,
                // preserving the subpixel fraction. This keeps feet at the same position when
                // yRadius changes from 14 (rolling) to 19 (standing).
                //
                // Use setY() (not setCentreY) to modify only yPixel and preserve ySubpixel,
                // matching the ROM's word-only modification. getRollHeightAdjustment() returns
                // the full height difference (e.g. 10 for Sonic), which when subtracted from
                // yPixel produces the same centreY shift as the ROM's radius-based subtraction.
                boolean wasRolling = getRolling();
                setRolling(false);
                if (wasRolling) {
                        setY((short) (getY() - getRollHeightAdjustment()));
                }

                setCrouching(false);
                setAir(true);
                setGSpeed((short) 0);
                int dir = (getCentreX() >= sourceX) ? 1 : -1;
                // ROM s2.asm lines 84936-84941: knockback is halved underwater
                if (inWater) {
                        setXSpeed((short) (0x100 * dir));
                        setYSpeed((short) -0x200);
                } else {
                        setXSpeed((short) (0x200 * dir));
                        setYSpeed((short) -0x400);
                }
                currentAudioManager().playSfx(resolveDamageSound(cause));
                return true;
        }

        public boolean applyHurtOrDeath(int sourceX, boolean spikeHit, boolean hadRings) {
                DamageCause cause = spikeHit ? DamageCause.SPIKE : DamageCause.NORMAL;
                return applyHurtOrDeath(sourceX, cause, hadRings);
        }

        public boolean applyHurtOrDeath(int sourceX, DamageCause cause, boolean hadRings) {
                return applyHurtOrDeath(sourceX, cause, hadRings, false);
        }

        /**
         * Applies hurt/death while ignoring post-hit invulnerability frames.
         * <p>
         * This still respects debug invulnerability and invincibility power-up.
         * Intended for Sonic 1 spike behavior.
         */
        public boolean applyHurtOrDeathIgnoringIFrames(int sourceX, boolean spikeHit, boolean hadRings) {
                DamageCause cause = spikeHit ? DamageCause.SPIKE : DamageCause.NORMAL;
                return applyHurtOrDeath(sourceX, cause, hadRings, true);
        }

        public boolean applyHurtOrDeathIgnoringIFrames(int sourceX, DamageCause cause, boolean hadRings) {
                return applyHurtOrDeath(sourceX, cause, hadRings, true);
        }

        private boolean applyHurtOrDeath(int sourceX, DamageCause cause, boolean hadRings, boolean ignoreIFrames) {
                if (isDamageBlocked(ignoreIFrames)) {
                        return false;
                }
                if (!hadRings && !shield) {
                        return applyDeath(cause);
                }
                return applyHurt(sourceX, cause, ignoreIFrames);
        }

        private boolean isDamageBlocked(boolean ignoreIFrames) {
                if (debugMode || invincibleFrames > 0 || isSuperSonic()) {
                        return true;
                }
                // ROM: Touch_ChkHurt only checks invulnerable_time, not routine number.
                // With the timer frozen during hurt (see tickStatus), invulnerableFrames
                // is always > 0 while hurt, so the hurt flag check is unnecessary.
                return !ignoreIFrames && invulnerableFrames > 0;
        }

        /**
         * Initiates the ROM-accurate drowning death sequence.
         * ROM ref: s2.asm:41729-41768 (KillCharacter_Drown / Obj01_ChkDrown)
         *
         * Instead of immediate death, enters a 120-frame pre-death phase:
         * - Controls locked, velocities zeroed
         * - Gentle gravity ($10/frame) causes slow sinking
         * - Drowning animation (0x17) plays throughout
         * - After 120 frames, transitions to dead (no upward bounce)
         */
        public boolean applyDrownDeath() {
                if (dead || drownPreDeathTimer > 0) {
                        return false;
                }
                drowningDeath = true;
                controlLocked = true;
                gSpeed = 0;
                xSpeed = 0;
                ySpeed = 0;
                setAir(true);
                setHighPriority(true);
                setSpringing(0);
                setSpindash(false);
                setRolling(false);
                setCrouching(false);
                setPushing(false);
                drownPreDeathTimer = 120;
                // Lock camera - prevent following the sinking player
                if (!cpuControlled) {
                        currentCamera().setFrozen(true);
                }
                // ROM order: resume zone music first, then play drown SFX.
                // playMusic() replaces the SMPS driver, so any SFX queued on the
                // old driver would be lost. Playing SFX after ensures it lands on
                // the new driver.
                if (controller.getDrowning() != null) {
                        controller.getDrowning().onDrown();
                }
                currentAudioManager().playSfx(GameSound.DROWN);
                return true;
        }

        /**
         * Applies instant death from OOZ oil suffocation.
         * ROM: JmpTo3_KillCharacter (s2.asm:49694) - standard instant kill, NOT the
         * water drowning pre-death sequence. Uses DROWN sound.
         */
        public boolean applyOilSuffocateDeath() {
                return applyDeath(DamageCause.DROWN);
        }

        public boolean applyPitDeath() {
                return applyDeath(DamageCause.PIT);
        }

        /**
         * Applies instant death from being crushed between a solid object and terrain.
         * Matches ROM's KillCharacter call in SolidObject_Squash (s2.asm:35348-35356).
         * Unconditional death - bypasses rings, shields, and invulnerability frames.
         */
        public boolean applyCrushDeath() {
                return applyDeath(DamageCause.CRUSH);
        }

        private boolean applyDeath(DamageCause cause) {
                if (dead) {
                        return false;
                }
                dead = true;
                // Lock camera when dying - prevent following the falling corpse
                // Only freeze camera for the main player, not for CPU sidekick
                if (!cpuControlled) {
                        currentCamera().setFrozen(true);
                }
                setInvulnerableFrames(0);
                setInvincibleFrames(0);
                setSpringing(0);
                setSpindash(false);
                setRolling(false);
                setCrouching(false);
                setPushing(false);
                setAir(true);
                setGSpeed((short) 0);
                setXSpeed((short) 0);
                setYSpeed((short) -0x700);
                setHighPriority(true);
                GameSound sound = resolveDamageSound(cause);
                if (sound != null) {
                        currentAudioManager().playSfx(sound);
                }
                return true;
        }

        private GameSound resolveDamageSound(DamageCause cause) {
                return switch (cause) {
                        case SPIKE -> GameSound.HURT_SPIKE;
                        case DROWN, TIME_OVER -> GameSound.DROWN; // Time over usually uses Drown or specific logic,
                                                                  // checking s2 asm... usually it's just game over
                                                                  // music. but for damage sound?
                        case PIT -> GameSound.HURT;
                        default -> GameSound.HURT;
                };
        }

        /**
         * Get the spindash counter (ROM: spindash_counter).
         * Range: 0x000 to 0x800. Speed index = counter >> 8.
         */
        public short getSpindashCounter() {
                return spindashCounter;
        }

        /**
         * Set the spindash counter (ROM: spindash_counter).
         * @param spindashCounter Value 0x000 to 0x800
         */
        public void setSpindashCounter(short spindashCounter) {
                this.spindashCounter = spindashCounter;
        }

        /**
         * Get the look delay counter (ROM: Sonic_Look_delay_counter).
         * Camera panning starts when this reaches 0x78 (120 frames).
         */
        public short getLookDelayCounter() {
                return lookDelayCounter;
        }

        /**
         * Set the look delay counter (ROM: Sonic_Look_delay_counter).
         * @param lookDelayCounter Counter value (capped at 0x78)
         */
        public void setLookDelayCounter(short lookDelayCounter) {
                this.lookDelayCounter = lookDelayCounter;
        }

        public int getRightWallPenetrationTimer() {
                return rightWallPenetrationTimer;
        }

        public void setRightWallPenetrationTimer(int rightWallPenetrationTimer) {
                this.rightWallPenetrationTimer = Math.max(0, rightWallPenetrationTimer);
        }

        public boolean isForceInputRight() {
                return forceInputRight;
        }

        public void setForceInputRight(boolean forceInputRight) {
                this.forceInputRight = forceInputRight;
                if (forceInputRight) {
                        this.forcedInputMask |= INPUT_RIGHT;
                } else {
                        this.forcedInputMask &= ~INPUT_RIGHT;
                }
        }

        public int getForcedInputMask() {
                return forcedInputMask;
        }

        public void setForcedInputMask(int forcedInputMask) {
                this.forcedInputMask = forcedInputMask & (INPUT_UP | INPUT_DOWN | INPUT_LEFT | INPUT_RIGHT | INPUT_JUMP);
                this.forceInputRight = (this.forcedInputMask & INPUT_RIGHT) != 0;
        }

        public void clearForcedInputMask() {
                this.forcedInputMask = 0;
                this.forceInputRight = false;
                this.forcedJumpPress = false;
                this.suppressNextJumpPress = false;
                this.deferredObjectControlRelease = false;
        }

        public void setForcedJumpPress(boolean forcedJumpPress) {
                this.forcedJumpPress = forcedJumpPress;
        }

        public boolean isForcedJumpPress() {
                return forcedJumpPress;
        }

        public void suppressNextJumpPress() {
                this.suppressNextJumpPress = true;
        }

        public boolean consumeSuppressNextJumpPress() {
                boolean suppress = suppressNextJumpPress;
                suppressNextJumpPress = false;
                return suppress;
        }

        public boolean isForcedInputActive(int inputBit) {
                return (forcedInputMask & inputBit) != 0;
        }

        public boolean isControlLocked() {
                return controlLocked;
        }

        public void setControlLocked(boolean controlLocked) {
                this.controlLocked = controlLocked;
        }

        /**
         * Gets the movement lock timer (ROM: move_lock).
         * When > 0, player input is ignored.
         */
        public int getMoveLockTimer() {
                return moveLockTimer;
        }

        /**
         * Sets the movement lock timer (ROM: move_lock).
         * The timer is decremented each frame by the movement manager.
         */
        public void setMoveLockTimer(int moveLockTimer) {
                this.moveLockTimer = Math.max(0, moveLockTimer);
        }

        public boolean isHidden() {
                return hidden;
        }

        public void setHidden(boolean hidden) {
                this.hidden = hidden;
        }

        /**
         * Returns whether an object has full control of the player (physics disabled).
         * When true, the movement manager skips all physics processing.
         */
        public boolean isObjectControlled() {
                return objectControlled;
        }

        /**
         * Sets whether an object has full control of the player.
         * When true, normal physics (gravity, movement, collision) are skipped.
         * The controlling object is responsible for updating the player's position.
         */
        public void setObjectControlled(boolean objectControlled) {
                this.objectControlled = objectControlled;
                if (objectControlled) {
                        this.deferredObjectControlRelease = false;
                }
        }

        /**
         * Defers object-control release until the end of the current frame.
         * This matches ROM object ordering where Sonic's routine has already
         * run before a later object clears {@code f_playerctrl}.
         */
        public void deferObjectControlRelease() {
                this.deferredObjectControlRelease = true;
        }

        /**
         * Releases the player from object control and records the frame number.
         * Use this instead of setObjectControlled(false) when exiting a controlling object
         * to enable the cooldown period that prevents immediate re-capture.
         */
        public void releaseFromObjectControl(int frameCounter) {
                this.objectControlled = false;
                this.objectControlReleasedFrame = frameCounter;
        }

        /**
         * Returns true if the player was recently released from object control.
         * Used by objects like spin tubes to prevent immediate re-capture after exit.
         * @param frameCounter Current frame number
         * @param cooldownFrames Number of frames to wait before allowing re-capture
         */
        public boolean wasRecentlyObjectControlled(int frameCounter, int cooldownFrames) {
                if (objectControlReleasedFrame == Integer.MIN_VALUE) {
                        return false;
                }
                return (frameCounter - objectControlReleasedFrame) < cooldownFrames;
        }

        /**
         * Returns whether the jump button is currently pressed.
         * Used by objects (like CNZ flippers) to detect jump input for triggering.
         * Also checks forcedInputMask so demo playback input is visible to objects,
         * matching ROM behavior where jpadhold1 contains demo data during demos.
         */
        public boolean isJumpPressed() {
                return jumpInputPressed || isForcedInputActive(INPUT_JUMP);
        }

        /**
         * Returns whether jump was freshly pressed this frame, including forced/demo input.
         */
        public boolean isJumpJustPressed() {
                return jumpInputJustPressed;
        }

        /**
         * Sets the jump input state for this frame.
         * Called by movement manager each frame with the current jump button state.
         */
        public void setJumpInputPressed(boolean pressed) {
                this.jumpInputPressed = pressed;
                boolean combinedJumpPressed = pressed || isForcedInputActive(INPUT_JUMP);
                this.jumpInputJustPressed =
                        combinedJumpPressed && !jumpInputPressedPreviousFrame;
                this.jumpInputPressedPreviousFrame = combinedJumpPressed;
        }

        /**
         * Returns whether the up button is currently pressed.
         * Used by objects (like VineSwitch) to detect directional input for release delay.
         * Also checks forcedInputMask so demo playback input is visible to objects,
         * matching ROM behavior where jpadhold1 contains demo data during demos.
         */
        public boolean isUpPressed() {
                return upInputPressed || isForcedInputActive(INPUT_UP);
        }

        /**
         * Returns whether the down button is currently pressed.
         * Used by objects (like VineSwitch) to detect directional input for release delay.
         * Also checks forcedInputMask so demo playback input is visible to objects,
         * matching ROM behavior where jpadhold1 contains demo data during demos.
         */
        public boolean isDownPressed() {
                return downInputPressed || isForcedInputActive(INPUT_DOWN);
        }

        /**
         * Returns whether the left button is currently pressed.
         * Used by objects (like Grabber) to detect directional input for escape mechanism.
         * Also checks forcedInputMask so demo playback input is visible to objects,
         * matching ROM behavior where jpadhold1 contains demo data during demos.
         */
        public boolean isLeftPressed() {
                return leftInputPressed || isForcedInputActive(INPUT_LEFT);
        }

        /**
         * Returns whether the right button is currently pressed.
         * Used by objects (like Grabber) to detect directional input for escape mechanism.
         * Also checks forcedInputMask so demo playback input is visible to objects,
         * matching ROM behavior where jpadhold1 contains demo data during demos.
         */
        public boolean isRightPressed() {
                return rightInputPressed || isForcedInputActive(INPUT_RIGHT);
        }

        /**
         * Sets the directional input state for this frame.
         * Called by SpriteManager each frame with the current button states.
         */
        public void setDirectionalInputPressed(boolean up, boolean down, boolean left, boolean right) {
                this.upInputPressed = up;
                this.downInputPressed = down;
                this.leftInputPressed = left;
                this.rightInputPressed = right;
        }

        /**
         * Returns whether the player is actively pressing a movement direction this frame,
         * after all control lock filtering. Used by animation to match ROM behavior.
         */
        public boolean isMovementInputActive() {
                return movementInputActive;
        }

        /**
         * Sets whether movement directional input is active this frame.
         * Called by movement manager after control lock/move lock filtering.
         */
        public void setMovementInputActive(boolean active) {
                this.movementInputActive = active;
        }

        public void markSpiralActive(int frameCounter) {
                spiralActiveFrame = frameCounter;
        }

        public boolean wasSpiralActive(int frameCounter) {
                return spiralActiveFrame == frameCounter || spiralActiveFrame == frameCounter - 1;
        }

        public boolean isSpiralActiveThisFrame(int frameCounter) {
                return spiralActiveFrame == frameCounter;
        }

        public void clearSpiralActive() {
                spiralActiveFrame = Integer.MIN_VALUE;
        }

        public int getFlipAngle() {
                return flipAngle & 0xFF;
        }

        public void setFlipAngle(int value) {
                this.flipAngle = (byte) (value & 0xFF);
        }

        public int getFlipSpeed() {
                return flipSpeed & 0xFF;
        }

        public void setFlipSpeed(int value) {
                this.flipSpeed = (byte) (value & 0xFF);
        }

        public int getFlipsRemaining() {
                return flipsRemaining & 0xFF;
        }

        public void setFlipsRemaining(int value) {
                this.flipsRemaining = (byte) (value & 0xFF);
        }

        public boolean isFlipTurned() {
                return flipTurned;
        }

        public void setFlipTurned(boolean flipTurned) {
                this.flipTurned = flipTurned;
        }

        public short getXSpeed() {
                return xSpeed;
        }

        public void setXSpeed(short xSpeed) {
                this.xSpeed = xSpeed;
        }

        public short getYSpeed() {
                return ySpeed;
        }

        public void setYSpeed(short ySpeed) {
                this.ySpeed = ySpeed;
        }

        /**
         * The amount this sprite's speed is effected by when running down/up a
         * slope.
         */
        protected short slopeRunning;
        /**
         * The amount this sprite's speed is effected by when rolling up a slope.
         */
        protected short slopeRollingUp;
        /**
         * The amount this sprite's speed is effected by when rolling down a slope.
         */
        protected short slopeRollingDown;
        /**
         * The speed at which this sprite accelerates when running.
         */

        protected short runAccel;
        /**
         * The speed at which this sprite decelerates when the opposite direction is
         * pressed.
         */
        protected short runDecel;
        /**
         * The speed at which this sprite slows down while running with no
         * directional keys pressed.
         */
        protected short friction;
        /**
         * Maximum rolling speed of this Sprite per step.
         */
        protected short maxRoll;
        /**
         * Maximum running speed of this Sprite per step.
         */
        protected short max;

        /**
         * The speed at which this sprite slows down while rolling with no
         * directional keys pressed.
         */
        protected short rollDecel;

        /**
         * Minimum speed required to start rolling.
         */
        protected short minStartRollSpeed;

        /**
         * Speed at which to stop rolling
         */
        protected short minRollSpeed;

        /**
         * Height when rolling
         */
        protected short rollHeight;

        /**
         * Height when running
         */
        protected short runHeight;

        /**
         * Collision radii (standing/rolling) used for sensor placement.
         */
        protected short standXRadius = 9;
        protected short standYRadius = 19;
        protected short rollXRadius = 7;
        protected short rollYRadius = 14;

        protected short xRadius = standXRadius;
        protected short yRadius = standYRadius;

        /**
         * Visual render offsets (do not affect collision).
         */
        protected short renderXOffset = 0;
        protected short renderYOffset = 0;

        /**
         * When true, debug movement mode is active.
         * Player can fly freely with direction keys, ignores collision/damage.
         */
        protected boolean debugMode = false;

        protected AbstractPlayableSprite(String code, short x, short y) {
                super(code, x, y);
                // Must define speeds before creating Manager (it will read speeds upon
                // instantiation).
                defineSpeeds();
                resolvePhysicsProfile();

                applyStandingRadii(false);

                // Set our entire history for x and y to be the starting position so if
                // the player spindashes immediately the camera effect won't be b0rked.
                // ROM: Sonic_Pos_Record_Buf has 64 entries
                for (short i = 0; i < 64; i++) {
                        xHistory[i] = x;
                        yHistory[i] = y;
                        inputHistory[i] = 0;
                        statusHistory[i] = 0;
                }
                // Always use PlayableSpriteController - it checks debugMode internally
                controller = new PlayableSpriteController(this);
        }

        /**
         * Resolves physics profile, modifiers, and feature set from the active GameModule.
         * Overwrites the protected speed fields set by defineSpeeds() with values from the profile.
         * Falls back gracefully if no provider is available (defineSpeeds() values remain).
         */
        private void resolvePhysicsProfile() {
                resolvePhysicsProfile(bootstrapSafeGameModule());
        }

        private void resolvePhysicsProfile(GameModule module) {
                runtimeBoundStateModule = module;
                try {
                        PhysicsProvider provider = module != null ? module.getPhysicsProvider() : null;
                        if (provider == null) {
                                bubbleAnimId = module != null ? module.resolveAnimationId(CanonicalAnimation.BUBBLE) : -1;
                                return;
                        }
                        String charType;
                        if (this instanceof Tails) charType = "tails";
                        else if (this instanceof Knuckles) charType = "knuckles";
                        else charType = "sonic";
                        PhysicsProfile profile = provider.getProfile(charType);
                        if (profile != null) {
                                this.physicsProfile = profile;
                                applyProfileToFields(profile);
                        }
                        this.physicsModifiers = provider.getModifiers();
                        this.physicsFeatureSet = provider.getFeatureSet();

                        // S1 (UNIFIED collision) uses d5=$D for ALL terrain probes.
                        // After Sonic1Level.convertS1BlockData() maps S1→S2 chunk format,
                        // S1's raw bit 13 → S2 bit 12, S1's raw bit 14 → S2 bit 13.
                        // Default topSolidBit=0x0C (bit 12) is already correct for floor.
                        // Default lrbSolidBit=0x0D (bit 13) stays for ceiling/wall probes:
                        // although S1 ROM uses d5=$D for all probes, the conversion maps
                        // S1 solidity types so that top-solid (type 1) only sets bit 12
                        // and lrb-solid (type 2) only sets bit 13. Using 0x0D for
                        // ceiling/wall means top-solid-only tiles remain one-way platforms.

                        // S3K init override: Character_Speeds table provides different init-time
                        // values that persist until the first water or speed shoes event.
                        // ROM ref: sonic3k.asm:21467-21474 (Character_Speeds loaded at player init)
                        PhysicsProfile initProfile = provider.getInitProfile(charType);
                        if (initProfile != null && profile != null) {
                                this.canonicalProfile = profile;
                                // Overwrite only the fields that Character_Speeds sets (max, accel, decel)
                                this.runAccel = initProfile.runAccel();
                                this.runDecel = initProfile.runDecel();
                                this.max = initProfile.max();
                                this.initPhysicsActive = true;
                        } else {
                                this.canonicalProfile = null;
                                this.initPhysicsActive = false;
                        }
                } catch (Exception e) {
                        // Graceful fallback: defineSpeeds() values remain
                        LOGGER.fine("PhysicsProvider unavailable, using defineSpeeds() values: " + e.getMessage());
                }
                // Cross-game donation: override only the feature set with hybrid (donor spindash + base physics)
                if (CrossGameFeatureProvider.isActive()) {
                        this.physicsFeatureSet = CrossGameFeatureProvider.getInstance().getHybridFeatureSet();
                }
                // Create or re-register persistent insta-shield object (ROM: SpawnLevelMainSprites_SpawnPlayers)
                // ROM (sonic3k.asm:20614-20615): character_id == 0 check — Sonic only, not Tails/Knuckles
                if (physicsFeatureSet != null && physicsFeatureSet.instaShieldEnabled()
                        && getSecondaryAbility() == SecondaryAbility.INSTA_SHIELD) {
                        if (instaShieldObject == null && powerUpSpawner != null) {
                                try {
                                        instaShieldObject = powerUpSpawner.createInstaShield(this);
                                } catch (IllegalStateException e) {
                                        // Services not yet available (e.g., prepareForLevel before ObjectManager).
                                        // Deferred to tickStatus() where ObjectManager context is active.
                                }
                        }
                        // Registration deferred to tickStatus() to avoid double-add
                        // when resolvePhysicsProfile() and tickStatus() both run on the same frame
                }
                bubbleAnimId = module != null ? module.resolveAnimationId(CanonicalAnimation.BUBBLE) : -1;
        }

        private void refreshRuntimeBoundStateIfNeeded() {
                GameModule module = currentGameModule();
                if (module == null || module == runtimeBoundStateModule) {
                        return;
                }
                resolvePhysicsProfile(module);
        }

        private GameModule bootstrapSafeGameModule() {
                return RuntimeManager.resolveBootstrapGameModule();
        }

        /**
         * Clears the S3K Character_Speeds init override and resets the mutable speed fields
         * to the canonical profile values. Called on water entry/exit, speed shoes give/expire,
         * and Super state transitions — matching ROM behavior where any of these events
         * overwrites the Character_Speeds values with hardcoded constants.
         * <p>No-op if no init override was active (S1/S2, or already cleared).
         */
        private void clearInitOverride() {
                if (!initPhysicsActive) return;
                initPhysicsActive = false;
                if (canonicalProfile != null) {
                        this.runAccel = canonicalProfile.runAccel();
                        this.runDecel = canonicalProfile.runDecel();
                        this.max = canonicalProfile.max();
                }
        }

        /**
         * Applies an external physics profile, overwriting current speed values.
         * Used by SuperStateController to swap between normal and Super physics.
         * Also clears the S3K init override since Super transitions reset constants.
         */
        public void applyExternalPhysicsProfile(PhysicsProfile profile) {
                if (profile == null) return;
                clearInitOverride();
                this.physicsProfile = profile;
                applyProfileToFields(profile);
        }

        /** Writes all 18 profile values to the mutable speed fields. */
        private void applyProfileToFields(PhysicsProfile profile) {
                this.runAccel = profile.runAccel();
                this.runDecel = profile.runDecel();
                this.friction = profile.friction();
                this.max = profile.max();
                this.jump = profile.jump();
                this.slopeRunning = profile.slopeRunning();
                this.slopeRollingUp = profile.slopeRollingUp();
                this.slopeRollingDown = profile.slopeRollingDown();
                this.rollDecel = profile.rollDecel();
                this.minStartRollSpeed = profile.minStartRollSpeed();
                this.minRollSpeed = profile.minRollSpeed();
                this.maxRoll = profile.maxRoll();
                this.rollHeight = profile.rollHeight();
                this.runHeight = profile.runHeight();
                this.standXRadius = profile.standXRadius();
                this.standYRadius = profile.standYRadius();
                this.rollXRadius = profile.rollXRadius();
                this.rollYRadius = profile.rollYRadius();
        }

        /**
         * Returns the physics feature set (spindash availability, etc.) for the current game.
         * May be null if no GameModule provider is active.
         */
        public PhysicsFeatureSet getPhysicsFeatureSet() {
                return physicsFeatureSet;
        }

        /** Package-private for testing. */
        protected void setPhysicsFeatureSet(PhysicsFeatureSet fs) {
                this.physicsFeatureSet = fs;
        }

        /** Sets shield state directly without spawning a shield object. For testing only. */
        protected void setShieldState(boolean hasShield, ShieldType type) {
                this.shield = hasShield;
                this.shieldType = type;
        }

        /**
         * Returns the physics modifiers (water/speed shoes rules) for the current game.
         * May be null if no GameModule provider is active.
         */
        public PhysicsModifiers getPhysicsModifiers() {
                return physicsModifiers;
        }

        /**
         * Returns whether debug movement mode is active.
         */
        public boolean isDebugMode() {
                return debugMode;
        }

        /**
         * Toggles debug movement mode on/off.
         * Resets interaction states to prevent getting stuck in object-controlled states.
         */
        public void toggleDebugMode() {
                debugMode = !debugMode;
                // Reset ALL interaction states when entering or leaving debug mode
                // This prevents getting stuck on LauncherSprings or in other locked states
                controlLocked = false;
                pinballMode = false;
                objectControlled = false;
                onObject = false;           // Clear "standing on object" flag
                stickToConvex = false;      // Clear slope adhesion flag (set by slope-mode launches)
        }

        /**
         * Sets debug movement mode.
         */
        public void setDebugMode(boolean debugMode) {
                this.debugMode = debugMode;
        }

        public short getGSpeed() {
                return gSpeed;
        }

        public void setGSpeed(short gSpeed) {
                this.gSpeed = gSpeed;
        }

        public short getRunAccel() {
                // ROM: Speed shoes have no physics effect when Super Sonic is active.
                // The shoes timer/music still run, but Super profile values take priority.
                // (s2.asm:36014 — expiry code restores Super values, confirming this design)
                boolean effectiveShoes = hasSpeedShoes() && !isSuperSonic();
                if (physicsModifiers != null) {
                        return physicsModifiers.effectiveAccel(runAccel, inWater, effectiveShoes);
                }
                // Fallback: Water overrides shoes (ROM sets absolute values on water entry)
                if (inWater) {
                        return (short) (runAccel / 2);
                }
                if (effectiveShoes) {
                        return (short) (runAccel * 2);
                }
                return runAccel;
        }

        public short getRunDecel() {
                boolean effectiveShoes = hasSpeedShoes() && !isSuperSonic();
                if (physicsModifiers != null) {
                        return physicsModifiers.effectiveDecel(runDecel, inWater, effectiveShoes);
                }
                // Fallback: Water overrides shoes
                if (inWater) {
                        return (short) (runDecel / 2);
                }
                return runDecel;
        }

        public short getSlopeRunning() {
                return slopeRunning;
        }

        public short getSlopeRollingUp() {
                return slopeRollingUp;
        }

        public short getSlopeRollingDown() {
                return slopeRollingDown;
        }

        public short getFriction() {
                boolean effectiveShoes = hasSpeedShoes() && !isSuperSonic();
                if (physicsModifiers != null) {
                        return physicsModifiers.effectiveFriction(friction, inWater, effectiveShoes);
                }
                // Fallback: Water overrides shoes (ROM sets absolute values on water entry)
                if (inWater) {
                        return (short) (friction / 2);
                }
                if (effectiveShoes) {
                        return (short) (friction * 2);
                }
                return friction;
        }

        public short getMax() {
                boolean effectiveShoes = hasSpeedShoes() && !isSuperSonic();
                if (physicsModifiers != null) {
                        return physicsModifiers.effectiveMax(max, inWater, effectiveShoes);
                }
                // Fallback: Water overrides shoes (ROM sets absolute values on water entry)
                if (inWater) {
                        return (short) (max / 2);
                }
                if (effectiveShoes) {
                        return (short) (max * 2);
                }
                return max;
        }

        /**
         * Returns the base gravity value applied by ObjectMoveAndFall.
         * This method returns the FULL gravity regardless of water state.
         * Underwater gravity reduction is handled separately in PlayableSpriteMovement.modeAirborne()
         * by subtracting a reduction AFTER applying gravity, matching ROM behavior exactly:
         *
         * Normal airborne:
         *   - ROM: ObjectMoveAndFall adds 0x38 to y_vel (s2.asm:29950)
         *   - ROM: Then Obj01_MdAir subtracts 0x28 if underwater (s2.asm:36170)
         *   - Net underwater gravity = 0x38 - 0x28 = 0x10
         *
         * Hurt airborne:
         *   - ROM: Obj01_Hurt adds 0x30 to y_vel (s2.asm:37799)
         *   - ROM: Then subtracts 0x20 if underwater (s2.asm:37802)
         *   - Net hurt underwater gravity = 0x30 - 0x20 = 0x10
         *
         * Normal: 0x38 (56 subpixels)
         * Hurt: 0x30 (48 subpixels)
         *
         * @see docs/s2disasm/s2.asm lines 29950 (ObjectMoveAndFall), 36170 (normal underwater), 37799-37802 (hurt + underwater)
         */
        @Override
        public float getGravity() {
                if (hurt) {
                        return 0x30; // Reduced hurt gravity (SPG: 0.1875 = 48 subpixels)
                }
                return gravity; // Normal gravity (0x38)
        }

        public byte getAngle() {
                return angle;
        }

        public void setAngle(byte angle) {
                this.angle = angle;
        }

        public short[] getXHistory() {
                return xHistory;
        }

        public short[] getYHistory() {
                return yHistory;
        }

        public boolean isCpuControlled() {
                return cpuControlled;
        }

        public void setCpuControlled(boolean cpuControlled) {
                this.cpuControlled = cpuControlled;
        }

        public SidekickCpuController getCpuController() {
                return cpuController;
        }

        public void setCpuController(SidekickCpuController cpuController) {
                this.cpuController = cpuController;
        }

        public boolean getRolling() {
                return rolling;
        }

        /**
         * Returns the Y position adjustment needed when transitioning between standing and rolling.
         *
         * The ROM uses center-based coordinates where y_pos is the sprite center, so it adjusts by
         * the RADIUS difference (standYRadius - rollYRadius = 19 - 14 = 5 pixels for Sonic).
         *
         * This engine uses top-left coordinates. When height changes (floor/ceiling orientation),
         * we add the full HEIGHT difference (runHeight - rollHeight = 10) to the top-left Y so
         * the center shifts by 5 (matching ROM). When on a WALL (left/right), setRolling() adjusts
         * WIDTH instead of HEIGHT, so height stays constant — we only need to shift top by 5 to
         * move center by 5.
         *
         * When entering roll: setY(getY() + getRollHeightAdjustment()) - moves top down, feet stay planted
         * When exiting roll: setY(getY() - getRollHeightAdjustment()) - moves top up, feet stay planted
         *
         * @return the top-left Y adjustment needed (orientation-aware)
         */
        public short getRollHeightAdjustment() {
                int fullDiff = runHeight - rollHeight;
                // On walls, setRolling() changes WIDTH, not HEIGHT. Since height stays constant,
                // adjusting top by fullDiff would move centre by fullDiff, overshooting the ROM's
                // centre+=5 by 5px. Use half (the radius diff) to match ROM centre behaviour.
                if (GroundMode.LEFTWALL.equals(runningMode) || GroundMode.RIGHTWALL.equals(runningMode)) {
                        return (short) (fullDiff / 2);
                }
                return (short) fullDiff;
        }

        /**
         * Sets the rolling state and handles hitbox changes.
         *
         * IMPORTANT: This method changes the rolling flag, hitbox radii, and visual dimensions.
         * It does NOT adjust Y position. The Y position adjustment must be done by the caller using
         * getRollHeightAdjustment() because:
         * 1. ROM does the adjustment in specific contexts (jump, spindash, roll start/end)
         * 2. Some callers (like applyHurt) should NOT adjust Y position
         *
         * @param rolling true to enter rolling state, false to exit
         */
        public void setRolling(boolean rolling) {
                if (this.rolling == rolling) {
                        return;
                }

                // Update visual dimensions (no position adjustment)
                if (GroundMode.CEILING.equals(runningMode) || GroundMode.GROUND.equals(runningMode)) {
                        int newHeight = rolling ? rollHeight : runHeight;
                        setHeight(newHeight);
                } else {
                        int newWidth = rolling ? rollHeight : runHeight;
                        setWidth(newWidth);
                }

                // Apply appropriate collision radii
                if (rolling) {
                        applyRollingRadii(false);
                } else {
                        applyStandingRadii(false);
                }

                this.rolling = rolling;
        }

        public boolean getRollingJump() {
                return rollingJump;
        }

        public void setRollingJump(boolean rollingJump) {
                this.rollingJump = rollingJump;
        }

        /**
         * Returns whether pinball mode is active.
         * When true, rolling cannot be cleared on landing and rolling cannot stop at 0 speed.
         */
        public boolean getPinballMode() {
                return pinballMode;
        }

        /**
         * Sets pinball mode. When true, the player must continue rolling -
         * rolling won't be cleared on landing and if speed reaches 0, a boost is given.
         */
        public void setPinballMode(boolean pinballMode) {
                this.pinballMode = pinballMode;
        }

        public boolean isTunnelMode() {
                return tunnelMode;
        }

        public void setTunnelMode(boolean tunnelMode) {
                this.tunnelMode = tunnelMode;
        }

        @Override
        public void setHeight(int height) {
                super.setHeight(height);
        }

        public short getRollDecel() {
                return rollDecel;
        }

        public short getMaxRoll() {
                return maxRoll;
        }

        public short getMinStartRollSpeed() {
                return minStartRollSpeed;
        }

        public short getMinRollSpeed() {
                return minRollSpeed;
        }

        public short getXRadius() {
                return xRadius;
        }

        public short getYRadius() {
                return yRadius;
        }

        public short getStandYRadius() {
                return standYRadius;
        }

        /**
         * Apply standing hitbox radii (y_radius=19, x_radius=9).
         * ROM: Used when unrolling or landing.
         * @param adjustY If true, adjust Y position for height change (not used - always pass false)
         */
        public void applyStandingRadii(boolean adjustY) {
                setCollisionRadii(standXRadius, standYRadius, adjustY);
        }

        /**
         * Apply rolling hitbox radii (y_radius=14, x_radius=7).
         * ROM: Used when starting roll, spindash release, or jumping.
         * @param adjustY If true, adjust Y position for height change (not used - always pass false)
         */
        public void applyRollingRadii(boolean adjustY) {
                setCollisionRadii(rollXRadius, rollYRadius, adjustY);
        }

        /**
         * Apply custom collision radii (e.g., Knuckles glide: 10x10).
         * ROM: Knux_Test_For_Glide sets y_radius=x_radius=$0A.
         */
        public void applyCustomRadii(int newXRadius, int newYRadius) {
                setCollisionRadii((short) newXRadius, (short) newYRadius, false);
        }

        /**
         * Restore standing collision radii (default_y_radius, default_x_radius).
         * ROM: Knuckles uses this when exiting glide/climb states.
         */
        public void restoreDefaultRadii() {
                setCollisionRadii(standXRadius, standYRadius, false);
        }

        protected void setCollisionRadii(short newXRadius, short newYRadius, boolean adjustY) {
                this.xRadius = newXRadius;
                this.yRadius = newYRadius;
                updateSensorOffsetsFromRadii();
        }

        private void updateSensorOffsetsFromRadii() {
                if (groundSensors == null || ceilingSensors == null || pushSensors == null) {
                        return;
                }

                byte xRad = (byte) xRadius;
                byte yRad = (byte) yRadius;
                // SPG: Push sensors always use x = +/-10, regardless of rolling state
                byte push = 10;

                if (groundSensors != null && groundSensors.length >= 2) {
                        groundSensors[0].setOffset((byte) -xRad, yRad);
                        groundSensors[1].setOffset(xRad, yRad);
                }

                if (ceilingSensors != null && ceilingSensors.length >= 2) {
                        ceilingSensors[0].setOffset((byte) -xRad, (byte) -yRad);
                        ceilingSensors[1].setOffset(xRad, (byte) -yRad);
                }

                if (pushSensors != null && pushSensors.length >= 2) {
                        pushSensors[0].setOffset((byte) -push, (byte) 0);
                        pushSensors[1].setOffset(push, (byte) 0);
                }
                // Update push sensor Y offset based on current ground state
                updatePushSensorYOffset();
        }

        /**
         * SPG: Push sensors shift down by +8 on flat ground so low steps are pushed
         * against instead of being stepped onto. In air or on slopes, Y offset is 0.
         */
        public void updatePushSensorYOffset() {
                if (pushSensors == null || pushSensors.length < 2) {
                        return;
                }
                // ROM: Y offset = +8 when (angle & 0x38) == 0, i.e., near-flat angles (0-7, 248-255)
                // This allows the offset on slight slopes, not just strictly flat ground.
                // See s2.asm:43517-43519 in CalcRoomInFront
                boolean onFlatGround = !air && runningMode == GroundMode.GROUND && (angle & 0x38) == 0;
                byte yOffset = onFlatGround ? (byte) 8 : (byte) 0;
                // SPG: Push sensors always use x = +/-10, regardless of rolling state
                byte push = 10;
                pushSensors[0].setOffset((byte) -push, yOffset);
                pushSensors[1].setOffset(push, yOffset);
        }

        public SpriteMovementManager getMovementManager() {
                return controller.getMovement();
        }

        public PlayableSpriteAnimation getAnimationManager() {
                return controller.getAnimation();
        }

        protected abstract void defineSpeeds();

        public final void move() {
                move(xSpeed, ySpeed);
        }

        public GroundMode getGroundMode() {
                return runningMode;
        }

        public void setGroundMode(GroundMode groundMode) {
                if (this.runningMode != groundMode) {
                        updateSpriteShapeForRunningMode(groundMode, this.runningMode);
                        this.runningMode = groundMode;
                        // SPG: Push sensor Y offset changes based on ground mode
                        updatePushSensorYOffset();
                }
        }

        protected void updateSpriteShapeForRunningMode(GroundMode newRunningMode, GroundMode oldRunningMode) {
                // Best if statement ever...
                if (((GroundMode.CEILING.equals(newRunningMode) || GroundMode.GROUND.equals(newRunningMode)) &&
                                (GroundMode.LEFTWALL.equals(oldRunningMode)
                                                || GroundMode.RIGHTWALL.equals(oldRunningMode)))
                                ||
                                ((GroundMode.RIGHTWALL.equals(newRunningMode)
                                                || GroundMode.LEFTWALL.equals(newRunningMode)) &&
                                                ((GroundMode.CEILING.equals(oldRunningMode)
                                                                || GroundMode.GROUND.equals(oldRunningMode))))) {
                        int oldHeight = getHeight();
                        int oldWidth = getWidth();

                        short oldCentreX = getCentreX();
                        short oldCentreY = getCentreY();

                        setHeight(oldWidth);
                        setWidth(oldHeight);

                        setX((short) (oldCentreX - (getWidth() / 2)));
                        setY((short) (oldCentreY - (getHeight() / 2)));
                }
        }

        public final short getCentreX(int framesBehind) {
                int desired = historyPos - framesBehind;
                if (desired < 0) {
                        desired += xHistory.length;
                }
                return (short) (xHistory[desired] + (width / 2));
        }

        public final short getCentreY(int framesBehind) {
                int desired = historyPos - framesBehind;
                if (desired < 0) {
                        desired += yHistory.length;
                }
                return (short) (yHistory[desired] + (height / 2));
        }

        /**
         * Fills position history with current position.
         * ROM: Reset_Player_Position_Array — called on spindash release & fire dash
         * so the camera delay looks back to "right here" instead of stale positions.
         */
        public void resetPositionHistory() {
                short currentX = getX();
                short currentY = getY();
                for (int i = 0; i < xHistory.length; i++) {
                        xHistory[i] = currentX;
                        yHistory[i] = currentY;
                }
        }

        /**
         * Returns the recorded input bitmask from framesBehind frames ago.
         * ROM: Reads from Sonic_Stat_Record_Buf for Tails CPU input replay.
         * Use INPUT_UP/DOWN/LEFT/RIGHT/JUMP constants to test individual bits.
         */
        public final short getInputHistory(int framesBehind) {
                int desired = historyPos - framesBehind;
                if (desired < 0) {
                        desired += inputHistory.length;
                }
                return inputHistory[desired];
        }

        /**
         * Returns the recorded status flags from framesBehind frames ago.
         * ROM: Reads from Sonic_Stat_Record_Buf for Tails CPU input replay.
         * Use STATUS_FACING_LEFT/IN_AIR/ROLLING/PUSHING constants to test individual bits.
         */
        public final byte getStatusHistory(int framesBehind) {
                int desired = historyPos - framesBehind;
                if (desired < 0) {
                        desired += statusHistory.length;
                }
                return statusHistory[desired];
        }

        /**
         * Updates sensor active states based on movement direction and ground mode.
         * Refactored to avoid per-frame array allocations by directly setting sensor states.
         */
        public void updateSensors(short originalX, short originalY) {
                Sensor groundA = groundSensors[0];
                Sensor groundB = groundSensors[1];
                Sensor ceilingC = ceilingSensors[0];
                Sensor ceilingD = ceilingSensors[1];
                Sensor pushE = pushSensors[0];
                Sensor pushF = pushSensors[1];

                if (getAir()) {
                        // Use ROM-accurate angle calculation via TrigLookupTable.calcAngle
                        // ROM: Sonic_DoLevelCollision (s2.asm:37547-37557)
                        int motionAngle = TrigLookupTable.calcAngle(xSpeed, ySpeed);

                        // ROM quadrant calculation: subi.b #$20,d0 / andi.b #$C0,d0
                        // This creates quadrants offset by 32 degrees:
                        // - 0xC0: Angles 0-31 or 224-255 (mostly right)
                        // - 0x00: Angles 32-95 (mostly down)
                        // - 0x40: Angles 96-159 (mostly left)
                        // - 0x80: Angles 160-223 (mostly up)
                        int quadrant = ((motionAngle - 0x20) & 0xC0) & 0xFF;

                        switch (quadrant) {
                                case 0xC0 -> {
                                        // Mostly Right (angles 0-31, 224-255): A, B, C, D, F active; E inactive
                                        groundA.setActive(true);
                                        groundB.setActive(true);
                                        ceilingC.setActive(true);
                                        ceilingD.setActive(true);
                                        pushE.setActive(false);
                                        pushF.setActive(true);
                                }
                                case 0x40 -> {
                                        // Mostly Left (angles 96-159): A, B, C, D, E active; F inactive
                                        groundA.setActive(true);
                                        groundB.setActive(true);
                                        ceilingC.setActive(true);
                                        ceilingD.setActive(true);
                                        pushE.setActive(true);
                                        pushF.setActive(false);
                                }
                                case 0x80 -> {
                                        // Mostly Up (angles 160-223): C, D, E, F active; A, B inactive
                                        groundA.setActive(false);
                                        groundB.setActive(false);
                                        ceilingC.setActive(true);
                                        ceilingD.setActive(true);
                                        pushE.setActive(true);
                                        pushF.setActive(true);
                                }
                                default -> {
                                        // 0x00: Mostly Down (angles 32-95): A, B, E, F active; C, D inactive
                                        groundA.setActive(true);
                                        groundB.setActive(true);
                                        ceilingC.setActive(false);
                                        ceilingD.setActive(false);
                                        pushE.setActive(true);
                                        pushF.setActive(true);
                                }
                        }
                } else {
                        // Ground sensors always active when grounded
                        groundA.setActive(true);
                        groundB.setActive(true);
                        // Ceiling sensors always inactive when grounded
                        ceilingC.setActive(false);
                        ceilingD.setActive(false);

                        // Push sensors active on floor/ceiling, disabled on walls
                        boolean pushActive = (runningMode == GroundMode.GROUND || runningMode == GroundMode.CEILING);
                        // Use gSpeed (speed along surface) instead of xSpeed for direction
                        if (gSpeed > 0) {
                                pushE.setActive(false);
                                pushF.setActive(pushActive);
                        } else if (gSpeed < 0) {
                                pushE.setActive(pushActive);
                                pushF.setActive(false);
                        } else {
                                pushE.setActive(false);
                                pushF.setActive(false);
                        }
                }
        }

        public Sensor[] getAllSensors() {
                Sensor[] sensors = new Sensor[6];
                sensors[0] = groundSensors[0];
                sensors[1] = groundSensors[1];
                sensors[2] = ceilingSensors[0];
                sensors[3] = ceilingSensors[1];
                sensors[4] = pushSensors[0];
                sensors[5] = pushSensors[1];

                return sensors;
        }

        public void moveForGroundModeAndDirection(byte distance, Direction direction) {
                SensorConfiguration sensorConfiguration = SpriteManager
                                .getSensorConfigurationForGroundModeAndDirection(getGroundMode(), direction);
                switch (sensorConfiguration.direction()) {
                        case DOWN -> {
                                yPixel = (short) (yPixel + distance);
                        }
                        case RIGHT -> {
                                xPixel = (short) (xPixel + distance);
                        }
                        case UP -> {
                                yPixel = (short) (yPixel - distance);
                        }
                        case LEFT -> {
                                xPixel = (short) (xPixel - distance);
                        }
                }
        }

        /**
         * Causes the sprite to update its position history as we are now at the end
         * of the tick so all movement calculations have been performed.
         */
        public void endOfTick() {
                if (deferredObjectControlRelease) {
                        objectControlled = false;
                        deferredObjectControlRelease = false;
                }
                // ROM: Sonic_Pos_Record_Index wraps at 256 bytes (64 entries * 4 bytes per entry)
                if (historyPos == 63) {
                        historyPos = 0;
                } else {
                        historyPos++;
                }
                xHistory[historyPos] = xPixel;
                yHistory[historyPos] = yPixel;

                // ROM: Sonic_Stat_Record_Buf records input buttons and status each frame
                short input = 0;
                if (upInputPressed) input |= INPUT_UP;
                if (downInputPressed) input |= INPUT_DOWN;
                if (leftInputPressed) input |= INPUT_LEFT;
                if (rightInputPressed) input |= INPUT_RIGHT;
                if (jumpInputPressed) input |= INPUT_JUMP;
                inputHistory[historyPos] = input;

                byte status = 0;
                if (getDirection() == Direction.LEFT) status |= STATUS_FACING_LEFT;
                if (air) status |= STATUS_IN_AIR;
                if (rolling) status |= STATUS_ROLLING;
                if (onObject) status |= STATUS_ON_OBJECT;
                if (rollingJump) status |= STATUS_ROLLING_JUMP;
                if (pushing) status |= STATUS_PUSHING;
                if (inWater) status |= STATUS_UNDERWATER;
                if (preventTailsRespawn) status |= STATUS_PREVENT_TAILS_RESPAWN;
                statusHistory[historyPos] = status;
        }

        public short getRenderCentreX() {
                return (short) (getCentreX() + renderXOffset);
        }

        public short getRenderCentreY() {
                return (short) (getCentreY() + renderYOffset);
        }

        public void setRenderOffsets(short xOffset, short yOffset) {
                this.renderXOffset = xOffset;
                this.renderYOffset = yOffset;
        }

        // ==================== Water Physics ====================

        /**
         * Updates water state based on player Y position relative to water level.
         *
         * @param waterLevelY Water surface Y position in world coordinates (pixels)
         */
        public void updateWaterState(int waterLevelY) {
                wasInWater = inWater;

                // ROM compares y_pos (center Y) with Water_Level_1:
                //   cmp.w y_pos(a0),d0 ; is Sonic above the water?
                //   bge.s Obj01_OutWater
                // Player is in water when center Y > water level
                int playerCenterY = getCentreY();

                // When skimming across the water surface (HCZ), the player's feet
                // are at the water level but they are NOT underwater. The skim
                // handler pins the player above the surface. Suppress water entry
                // so the speed-halving and drowning timer don't activate.
                if (waterSkimActive) {
                        inWater = false;
                        return;
                }

                inWater = playerCenterY > waterLevelY;

                // Detect transitions
                if (!wasInWater && inWater) {
                        onEnterWater();
                } else if (wasInWater && !inWater) {
                        onExitWater();
                }

                // Update drowning manager each frame while underwater
                // Skip during drowning pre-death phase to prevent re-triggering
                if (inWater && !dead && !isDrowningPreDeath() && controller.getDrowning() != null) {
                        // Bubble shield prevents drowning (s3.asm: Player_ResetAirTimer)
                        PhysicsFeatureSet wfs = getPhysicsFeatureSet();
                        if (wfs != null && wfs.elementalShieldsEnabled()
                                        && shield && shieldType == ShieldType.BUBBLE) {
                                controller.getDrowning().replenishAir();
                        } else {
                                boolean shouldDrown = controller.getDrowning().update();
                                if (shouldDrown) {
                                        applyDrownDeath();
                                }
                        }
                }
        }

        /**
         * Called when player enters water.
         * Applies instantaneous velocity changes per original game logic.
         */
        protected void onEnterWater() {
                LOGGER.fine("Player entered water");
                // Increment global Water_entered_counter so objects can detect water transitions
                currentWaterSystem().incrementWaterEnteredCounter();
                // S3K: water entry resets Character_Speeds init values to canonical
                // (sonic3k.asm:22225-22227 sets absolute values, not relative to init)
                clearInitOverride();

                // Fire and Lightning shields dissipate on water entry (s3.asm:34693, 34780)
                PhysicsFeatureSet fs = getPhysicsFeatureSet();
                if (shield && shieldType != null && fs != null && fs.elementalShieldsEnabled()) {
                        if (shieldType == ShieldType.FIRE || shieldType == ShieldType.LIGHTNING) {
                                shield = false;
                                shieldType = null;
                                if (shieldObject != null) {
                                        shieldObject.destroy();
                                        shieldObject = null;
                                }
                        }
                }

                // ROM: asr.w x_vel(a0) - halve horizontal velocity once
                xSpeed = (short) (xSpeed / 2);
                gSpeed = (short) (gSpeed / 2);

                // ROM: asr.w y_vel(a0) twice - divide by 4 unconditionally
                // (both upward and downward velocity)
                ySpeed = (short) (ySpeed / 4);

                // ROM (s2.asm:36050-36110): Skip splash if y_vel is 0 after quartering
                //   tst.w   y_vel(a0)
                //   beq.s   loc_F6DE         ; Skip splash if y_vel is now 0
                if (ySpeed != 0) {
                        // Play splash sound
                        currentAudioManager().playSfx(GameSound.SPLASH);

                        // Spawn splash object at water surface
                        spawnSplash();
                }

                // Reset drowning manager for new underwater session
                if (controller.getDrowning() != null) {
                        controller.getDrowning().reset();
                }
        }

        /**
         * Called when player exits water.
         * Applies velocity boost per original game logic.
         */
        protected void onExitWater() {
                LOGGER.fine("Player exited water");
                // Increment global Water_entered_counter so objects can detect water transitions
                currentWaterSystem().incrementWaterEnteredCounter();
                // S3K: water exit resets Character_Speeds init values to canonical
                // (sonic3k.asm:22253-22255 sets absolute $600/$C/$80, not relative to init)
                clearInitOverride();

                // ROM does NOT modify x_vel on water exit - only top_speed/accel/decel
                // change, which affects future acceleration but not current velocity

                // ROM: cmpi.b #4,routine(a0) - skip y_vel doubling if hurt
                //      beq.s +
                //      asl y_vel(a0)
                if (!isHurt()) {
                        // Double y velocity (both up and down)
                        ySpeed = (short) (ySpeed * 2);
                }

                // ROM (s2.asm:36103-36104): tst.w y_vel(a0) / beq.w return_1A18C
                // If y velocity is zero after doubling, skip splash entirely
                if (ySpeed == 0) {
                        // Notify drowning manager but skip splash effects
                        if (controller.getDrowning() != null) {
                                controller.getDrowning().onExitWater();
                        }
                        return;
                }

                // ROM: cmpi.w #-$1000,y_vel(a0) - cap upward velocity at -$1000
                //      bgt.s +
                //      move.w #-$1000,y_vel(a0)
                if (ySpeed < -0x1000) {
                        ySpeed = -0x1000;
                }

                // Play splash sound
                currentAudioManager().playSfx(GameSound.SPLASH);

                // Spawn splash object at water surface
                spawnSplash();

                // Notify drowning manager of water exit (stops drowning music, resets state)
                if (controller.getDrowning() != null) {
                        controller.getDrowning().onExitWater();
                }
        }

        /**
         * Spawns a splash object at the water surface.
         * The splash appears at the player's X position at the water level Y.
         */
        private void spawnSplash() {
                if (powerUpSpawner != null) {
                        powerUpSpawner.spawnSplash(this);
                }
        }

        /**
         * Returns true if player is currently underwater.
         */
        public boolean isInWater() {
                return inWater;
        }

        /**
         * Returns true if the player is currently skimming across the water surface (HCZ).
         */
        public boolean isWaterSkimActive() {
                return waterSkimActive;
        }

        /**
         * Set by HCZWaterSkimHandler when the player enters/exits the skim state.
         * When active, updateWaterState() will not trigger water entry.
         */
        public void setWaterSkimActive(boolean waterSkimActive) {
                this.waterSkimActive = waterSkimActive;
        }

        public boolean isPreventTailsRespawn() {
                return preventTailsRespawn;
        }

        public void setPreventTailsRespawn(boolean preventTailsRespawn) {
                this.preventTailsRespawn = preventTailsRespawn;
        }

        /**
         * Replenishes air by collecting a large breathable bubble.
         * Implements full ROM behavior from s2.asm lines 44966-44998:
         * <ul>
         *   <li>Clears all velocity (x_vel, y_vel, inertia)</li>
         *   <li>Sets bubble-breathing animation</li>
         *   <li>Locks movement for 35 frames (0x23)</li>
         *   <li>Clears jumping, pushing, and roll-jumping flags</li>
         *   <li>Unrolls player if rolling (adjusts hitbox)</li>
         * </ul>
         */
        public void replenishAir() {
                // ROM: clr.w x_vel(a1) / clr.w y_vel(a1) / clr.w inertia(a1)
                xSpeed = 0;
                ySpeed = 0;
                gSpeed = 0;

                // ROM: move.b #AniIDSonAni_Bubble,anim(a1)
                if (bubbleAnimId >= 0) {
                        setAnimationId(bubbleAnimId);
                }

                // ROM: move.w #$23,move_lock(a1) (35 frames)
                moveLockTimer = 0x23;

                // ROM: move.b #0,jumping(a1) - we don't have a jumping flag, but air=false is similar
                air = false;

                // ROM: bclr #status.player.pushing,status(a1)
                pushing = false;

                // ROM: bclr #status.player.rolljumping,status(a1)
                rollingJump = false;

                // ROM: btst #status.player.rolling,status(a1) / beq.w loc_1FBB8
                // If rolling, unroll (setRolling handles hitbox adjustment and Y position)
                if (rolling) {
                        // ROM: bclr #status.player.rolling,status(a1)
                        // setRolling(false) handles:
                        // - Adjusting y_radius back to standing height
                        // - Adjusting x_radius back to standing width
                        // - Adjusting Y position (subq.w #5,y_pos for Sonic, subq.w #1 for Tails)
                        setRolling(false);
                }

                // Delegate to drowning manager for air timer reset and music handling
                if (controller.getDrowning() != null) {
                        controller.getDrowning().replenishAir();
                }
        }

        /**
         * Sets water state directly (for loading checkpoints, testing, etc.).
         */
        public void setInWater(boolean inWater) {
                this.inWater = inWater;
                this.wasInWater = inWater;
        }

        // ==================== Physics Constant Getters with Modifiers
        // ====================
        // These apply underwater and speed shoes modifiers dynamically

        /**
         * Returns effective run acceleration, accounting for underwater and speed
         * shoes.
         * Underwater: halved
         * Speed shoes: doubled
         */
        public short getEffectiveRunAccel() {
                // Water overrides shoes (ROM sets absolute values on water entry)
                if (inWater) {
                        return (short) (runAccel / 2);
                }
                if (speedShoes) {
                        return (short) (runAccel * 2);
                }
                return runAccel;
        }

        /**
         * Returns effective run deceleration, accounting for modifiers.
         */
        public short getEffectiveRunDecel() {
                if (inWater) {
                        return (short) (runDecel / 2);
                }
                // Speed shoes don't affect decel in original
                return runDecel;
        }

        /**
         * Returns effective friction, accounting for modifiers.
         */
        public short getEffectiveFriction() {
                // Water overrides shoes (ROM sets absolute values on water entry)
                if (inWater) {
                        return (short) (friction / 2);
                }
                if (speedShoes) {
                        return (short) (friction * 2);
                }
                return friction;
        }

        /**
         * Returns effective max speed, accounting for modifiers.
         */
        public short getEffectiveMax() {
                // Water overrides shoes (ROM sets absolute values on water entry)
                if (inWater) {
                        return (short) (max / 2);
                }
                if (speedShoes) {
                        return (short) (max * 2);
                }
                return max;
        }

        /**
         * Returns effective jump force, accounting for underwater modifier.
         * ROM s2.asm line 37019: Underwater = 0x380 (896), Normal = 0x680 (1664)
         */
        public short getEffectiveJump() {
                if (inWater) {
                        return 0x380; // Reduced underwater jump (ROM: 0x380)
                }
                return jump;
        }

        /**
         * Returns effective gravity value.
         * Normal: 0x38 (56 subpixels)
         * Underwater: 0x10 (16 subpixels)
         */
        public short getEffectiveGravity() {
                if (inWater) {
                        return 0x10; // Reduced underwater gravity
                }
                return 0x38; // Normal gravity
        }

        /**
         * Returns effective air drag threshold.
         * Normal: -0x400
         * Underwater: -0x200
         */
        public short getEffectiveAirDragThreshold() {
                if (inWater) {
                        return -0x200;
                }
                return -0x400;
        }
}
