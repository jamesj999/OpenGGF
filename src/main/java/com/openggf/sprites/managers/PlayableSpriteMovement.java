package com.openggf.sprites.managers;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.PhysicsFeatureSet;

import com.openggf.camera.Camera;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.MultiPieceSolidProvider;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.Direction;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.Sensor;
import com.openggf.physics.SensorResult;
import com.openggf.physics.TrigLookupTable;
import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.level.objects.SkidDustObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.ShieldType;
import com.openggf.sprites.playable.SecondaryAbility;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationProfile;
import com.openggf.game.GroundMode;

/**
 * ROM-accurate movement handler for playable sprites.
 * Implements exact order of operations from Sonic 2 ROM disassembly (s2.asm:36145-37700).
 *
 * Movement modes based on air/rolling status:
 * - Obj01_MdNormal: ground walking (air=false, rolling=false)
 * - Obj01_MdRoll: ground rolling (air=false, rolling=true)
 * - Obj01_MdAir/MdJump: airborne (air=true)
 */
public class PlayableSpriteMovement extends AbstractSpriteMovementManager<AbstractPlayableSprite> {

	// ROM spindash speed table (s2.asm:37294) - indexed by spindash_counter >> 8
	private static final short[] SPINDASH_SPEEDS = {
		0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00
	};

	// Angle classification thresholds
	private static final int ANGLE_STEEP_OFFSET = 0x20;
	private static final int ANGLE_STEEP_MASK = 0x40;
	private static final int ANGLE_FLAT_OFFSET = 0x10;
	private static final int ANGLE_FLAT_MASK = 0x20;
	private static final int ANGLE_SLOPE_OFFSET = 0x20;
	private static final int ANGLE_SLOPE_MASK = 0xC0;
	private static final int ANGLE_WALL_OFFSET = 0x40;
	private static final int ANGLE_WALL_MASK = 0x80;

	// Speed thresholds
	private static final int SLOPE_REPEL_MIN_SPEED = 0x280;
	private static final int SKID_SPEED_THRESHOLD = 0x400;
	private static final short YSPEED_LANDING_CAP = (short) 0xFC0;
	private static final int UPWARD_VELOCITY_CAP = -0xFC0;

	// Movement constants
	private static final int MOVE_LOCK_FRAMES = 0x1E;
	private static final int DEBUG_MOVE_SPEED = 3;
	// Controlled roll deceleration: derived per-frame from sprite.getRunDecel() >> 2
	// (s1:01 Sonic.asm:595-601 — rollDecel = decel/4 = $80/4 = $20)

	private final CollisionSystem bootstrapCollisionSystem;
	private final AudioManager audioManager = AudioManager.getInstance();
	private final GameStateManager bootstrapGameState;

	// Cached speed constants (don't change with speed shoes)
	private final short slopeRunning;
	private final short minStartRollSpeed;
	private final short maxRoll;
	private final short slopeRollingUp;
	private final short slopeRollingDown;
	private final short rollDecel;

	// Input tracking
	private boolean jumpPressed;
	private boolean jumpPrevious;
	private boolean jumpReleasedSinceJump;
	private boolean testKeyPressed;

	// Current frame input state
	private boolean inputUp, inputDown, inputLeft, inputRight;
	private boolean inputJump, inputJumpPress;
	private boolean inputRawLeft, inputRawRight;
	private boolean wasCrouching;

	public PlayableSpriteMovement(AbstractPlayableSprite sprite,
			CollisionSystem collisionSystem,
			GameStateManager gameState) {
		super(sprite);
		this.bootstrapCollisionSystem = collisionSystem;
		this.bootstrapGameState = gameState;
		slopeRunning = sprite.getSlopeRunning();
		minStartRollSpeed = sprite.getMinStartRollSpeed();
		maxRoll = sprite.getMaxRoll();
		slopeRollingUp = sprite.getSlopeRollingUp();
		slopeRollingDown = sprite.getSlopeRollingDown();
		rollDecel = sprite.getRollDecel();
	}

	public PlayableSpriteMovement(AbstractPlayableSprite sprite) {
		this(sprite, CollisionSystem.getInstance(), GameStateManager.getInstance());
	}

	private Camera camera() {
		return sprite.currentCamera();
	}

	private LevelManager levelManager() {
		return sprite.currentLevelManager();
	}

	private CollisionSystem collisionSystem() {
		CollisionSystem current = sprite.currentCollisionSystem();
		return current != null ? current : bootstrapCollisionSystem;
	}

	private GameStateManager gameState() {
		GameStateManager current = sprite.currentGameState();
		return current != null ? current : bootstrapGameState;
	}

	/**
	 * Returns the spindash speed table from the physics feature set,
	 * falling back to the static SPINDASH_SPEEDS constant.
	 * S3K Super/Hyper forms use a higher speed table (sonic3k.asm:23743 word_11D04).
	 * S2 Super Sonic uses the normal table (no separate Super table in S2).
	 */
	private short[] getSpindashSpeedTable() {
		PhysicsFeatureSet featureSet = sprite.getPhysicsFeatureSet();
		if (sprite.isSuperSonic() && featureSet != null
				&& featureSet.superSpindashSpeedTable() != null) {
			return featureSet.superSpindashSpeedTable();
		}
		if (featureSet != null && featureSet.spindashSpeedTable() != null) {
			return featureSet.spindashSpeedTable();
		}
		return SPINDASH_SPEEDS;
	}

	@Override
	public void handleMovement(boolean up, boolean down, boolean left, boolean right, boolean jump, boolean testKey,
			boolean speedUp, boolean slowDown) {
		// Note: Raw input state for objects is now stored in SpriteManager BEFORE filtering,
		// so objects can query button state even when control is locked (ROM: obj_control).
		// The parameters here are already filtered by control lock state.

		if (sprite.isDebugMode()) {
			handleDebugMovement(up, down, left, right, speedUp, slowDown);
			return;
		}

		if (sprite.isObjectControlled()) {
			return;
		}

		handleTestKey(testKey);

		// ROM has two separate control mechanisms:
		// 1. move_lock (moveLockTimer) - blocks left/right only, NOT jumping
		// 2. obj_control bit 0 (controlLocked) - blocks ALL input including jumping
		//
		// The ControlLockTimer (slope slip) uses move_lock behavior in the ROM,
		// so it should only block left/right movement.
		boolean moveLocked = sprite.getMoveLockTimer() > 0;

		// Clear stale forced animation when move lock expires (e.g., glide landing crouch)
		if (!moveLocked && sprite.getForcedAnimationId() >= 0
				&& sprite.getDoubleJumpFlag() == 0 && !sprite.getAir()) {
			sprite.setForcedAnimationId(-1);
		}

		// obj_control bit 0 - when an object (flipper, etc.) has partial control
		// This blocks ALL input including jumping
		boolean objControlLocked = sprite.isControlLocked();

		inputRawLeft = left;
		inputRawRight = right;

		// ROM-accurate control lock behavior:
		// - move_lock and springing only block input when GROUNDED (checked in Sonic_Move, not Sonic_ChgJumpDir)
		// - obj_control and hurt block input in ALL states
		boolean groundedControlLock = !sprite.getAir() && (moveLocked || sprite.getSpringing());
		if (groundedControlLock || objControlLocked || sprite.isHurt()) {
			left = false;
			if (!sprite.isForceInputRight()) {
				right = false;
			}
		}
		// Block up/down when hurt
		if (sprite.isHurt()) {
			up = down = false;
		}
		// Block jumping ONLY when obj_control is set (not move_lock)
		// ROM: obj_control bit 0 skips the entire movement routine including Sonic_Jump
		// ROM: move_lock only blocks Sonic_Move/Sonic_RollSpeed, NOT Sonic_Jump
		if (objControlLocked) {
			jump = false;
		}

		updatePushingOnDirectionChange(left, right);

		short originalX = sprite.getX();
		short originalY = sprite.getY();

		// ROM-accurate drowning pre-death: 120 frames of slow sinking before death
		// ROM ref: s2.asm:41729-41768 - addi.w #$10,y_vel; no terrain, no input
		if (sprite.isDrowningPreDeath()) {
			sprite.setYSpeed((short) (sprite.getYSpeed() + 0x10));
			if (sprite.tickDrownPreDeath()) {
				// Timer expired - transition to dead state (no upward bounce)
				sprite.setDead(true);
			}
			sprite.move(sprite.getXSpeed(), sprite.getYSpeed());
			sprite.updateSensors(originalX, originalY);
			return;
		}

		if (sprite.getDead()) {
			applyDeathMovement();
			sprite.move(sprite.getXSpeed(), sprite.getYSpeed());
			sprite.updateSensors(originalX, originalY);
			return;
		}

		wasCrouching = sprite.getCrouching();
		sprite.setCrouching(false);

		storeInputState(up, down, left, right, jump);

		// ROM-accurate: Track whether effective movement input is active for animation.
		// This is the input state AFTER control lock/move lock filtering, used to determine
		// walk vs idle animation (ROM: Sonic_MoveLeft/MoveRight set walk anim when called).
		sprite.setMovementInputActive(inputLeft || inputRight);

		if (sprite.getSpringing()) {
			jumpPressed = false;
		}
		if (!sprite.getAir() && !inputJump) {
			jumpPressed = false;
		}

		// Recover transient desync where object contact exists but air flag is stale.
		// This can happen around moving/sloped solids and causes jump presses to be
		// evaluated in airborne mode for one frame.
		if (sprite.getAir() && hasObjectSupport() && sprite.getYSpeed() >= 0) {
			sprite.setAir(false);
			sprite.setOnObject(true);
		}

		// ROM: Knuckles_Glide dispatch — intercepts normal mode when in glide states
		int glideState = sprite.getDoubleJumpFlag();
		if (glideState >= 3 && glideState <= 5
				&& sprite.getSecondaryAbility() == SecondaryAbility.GLIDE) {
			updateKnucklesGlide();
			// Direction is managed by glide code, not updateFacingDirection
			sprite.updateSensors(originalX, originalY);
			return;
		}

		// Mode dispatch (ROM: Obj01_MdNormal_Checks)
		if (sprite.getAir()) {
			modeAirborne();
		} else if (sprite.getRolling()) {
			modeRoll();
		} else {
			modeNormal();
		}

		// ROM: Knuckles_Glide manages direction itself via setGlideAnimation();
		// don't let the normal facing direction logic override it.
		if (!isInGlideDirectionControl()) {
			updateFacingDirection();
		}
		sprite.updateSensors(originalX, originalY);
	}

	/** Returns true when the glide state machine owns direction control. */
	private boolean isInGlideDirectionControl() {
		if (sprite.getSecondaryAbility() != SecondaryAbility.GLIDE) {
			return false;
		}
		int state = sprite.getDoubleJumpFlag();
		// States 1 (gliding), 3 (sliding), 4 (wall climb) manage direction themselves
		return state == 1 || state == 3 || state == 4;
	}

	// ========================================
	// MODE METHODS
	// ========================================

	/** Obj01_MdNormal: Ground walking state */
	private void modeNormal() {
		short originalX = sprite.getX();
		short originalY = sprite.getY();

		if (doCheckSpindash()) return;
		if (inputJumpPress && doJump()) {
			// ROM: Sonic_Jump uses addq.l #4,sp to pop the return address,
			// skipping the rest of Obj01_MdNormal (SlopeResist, Move,
			// SpeedToPos, AnglePos, SlopeRepel). Air physics and position
			// update begin on the next frame when modeAirborne() runs.
			return;
		}

		doSlopeResist();
		doGroundMove();
		doCheckStartRoll();
		doLevelBoundary();
		sprite.move(sprite.getXSpeed(), sprite.getYSpeed());
		doAnglePosWithSensorUpdate(originalX, originalY);
		doSlopeRepel();
		updateCrouchState();
	}

	/** Obj01_MdRoll: Rolling on ground state */
	private void modeRoll() {
		short originalX = sprite.getX();
		short originalY = sprite.getY();

		if (!sprite.getPinballMode() && inputJumpPress && doJump()) {
			// ROM: Sonic_Jump uses addq.l #4,sp to pop the return address,
			// skipping the rest of Obj01_MdRoll (RollRepel, RollSpeed,
			// SpeedToPos, AnglePos, SlopeRepel). Air physics and position
			// update begin on the next frame when modeAirborne() runs.
			return;
		}

		doRollRepel();
		doRollSpeed();
		doLevelBoundary();
		sprite.move(sprite.getXSpeed(), sprite.getYSpeed());
		doAnglePosWithSensorUpdate(originalX, originalY);
		doSlopeRepel();
	}

	/** Obj01_MdAir/MdJump: Airborne state */
	private void modeAirborne() {
		short originalX = sprite.getX();
		short originalY = sprite.getY();

		// Knuckles glide states 1-2 use custom physics
		int glideState = sprite.getDoubleJumpFlag();
		boolean inGlide = (glideState == 1 || glideState == 2)
				&& sprite.getSecondaryAbility() == SecondaryAbility.GLIDE;

		if (inGlide && glideState == 1) {
			// Active glide — custom physics replace normal airborne
			updateKnucklesGlide();
			// updateGliding() may have changed state (e.g., released jump → state 2)
			if (sprite.getDoubleJumpFlag() != 1) {
				// State changed during update (e.g., entered fall-from-glide)
				// Continue with normal airborne physics for this frame
			} else {
				doLevelBoundary();
				sprite.move(sprite.getXSpeed(), sprite.getYSpeed());
				// ROM: Knux_DoLevelCollision_CheckRet — custom collision for glide
				doGlideCollision();
				return;
			}
		}

		// ROM hurt routine (routine 4) skips both Sonic_JumpHeight and
		// Sonic_ChgJumpDir — only applies gravity + collision.
		if (!sprite.isHurt()) {
			doJumpHeight();
			doChgJumpDir();
		}
		doLevelBoundary();
		doObjectMoveAndFall();

		// Underwater gravity reduction
		// Normal airborne: net gravity = 0x38 - 0x28 = 0x10 (s2.asm:36170)
		// Hurt airborne:   net gravity = 0x30 - 0x20 = 0x10 (s2.asm:37802, s1:01 Sonic.asm:1410)
		// The ROM hurt routine (Obj01_Hurt) uses a separate code path with $20 reduction,
		// NOT the $28 used in Obj01_MdAir/MdJump. All three games (S1/S2/S3K) are identical.
		if (sprite.isInWater()) {
			short reduction = 0x28;
			var modifiers = sprite.getPhysicsModifiers();
			if (modifiers != null) {
				reduction = sprite.isHurt()
						? modifiers.waterHurtGravityReduction()
						: modifiers.waterGravityReduction();
			} else if (sprite.isHurt()) {
				reduction = 0x20;
			}
			sprite.setYSpeed((short) (sprite.getYSpeed() - reduction));
		}

		sprite.returnAngleToZero();
		sprite.updateSensors(originalX, originalY);
		boolean wasAirBeforeCollision = sprite.getAir();
		doLevelCollision();

		// ROM: Knuckles_Fall_From_Glide landing (sonic3k.asm:30913-30940).
		// When landing from fall-from-glide state (2), zero velocities,
		// play landing SFX, set move_lock, and show crouching pose.
		if (inGlide && glideState == 2 && wasAirBeforeCollision && !sprite.getAir()) {
			sprite.setGSpeed((short) 0);
			sprite.setXSpeed((short) 0);
			sprite.setYSpeed((short) 0);
			audioManager.playSfx(GameSound.GLIDE_LAND);
			int hexAngle = sprite.getAngle() & 0xFF;
			int adjusted = (hexAngle + 0x20) & 0xC0;
			if (adjusted == 0) {
				// Flat surface: crouch with move_lock
				sprite.setMoveLockTimer(0x0F);
				sprite.setForcedAnimationId(0x23);  // GLIDE_SLIDE (crouching frame)
			}
		}
	}

	// ========================================
	// SPINDASH
	// ========================================

	/** Sonic_CheckSpindash: Check for spindash initiation (s2.asm:37206) */
	private boolean doCheckSpindash() {
		// Feature gate: skip entirely if spindash is disabled (e.g., Sonic 1)
		PhysicsFeatureSet featureSet = sprite.getPhysicsFeatureSet();
		if (featureSet != null && !featureSet.spindashEnabled()) {
			return false;
		}

		if (sprite.getSpindash()) {
			return doUpdateSpindash();
		}

		int duckAnimId = getDuckAnimId();
		if (duckAnimId < 0 || sprite.getAnimationId() != duckAnimId) {
			return false;
		}
		if (!inputJumpPress) {
			return false;
		}

		setSpindashAnimation();
		audioManager.playSfx(GameSound.SPINDASH_CHARGE);
		sprite.setSpindash(true);
		sprite.setSpindashCounter((short) 0);
		doLevelBoundaryAndAnglePos();
		return true;
	}

	/** Sonic_UpdateSpindash: Handle spindash charging/release (s2.asm:37239) */
	private boolean doUpdateSpindash() {
		if (!inputDown) {
			doReleaseSpindash();
			// Return true to skip the rest of modeNormal().
			// The release routine handles movement and collision for this frame,
			// and next frame will properly dispatch to modeRoll().
			return true;
		}

		// Decay counter every frame
		short counter = sprite.getSpindashCounter();
		if (counter != 0) {
			counter = (short) Math.max(0, counter - (counter >> 5));
			sprite.setSpindashCounter(counter);
		}

		// Add charge on jump press
		if (inputJumpPress) {
			setSpindashAnimation();
			float pitch = 1.0f + (sprite.getSpindashCounter() / 2048.0f) / 3.0f;
			audioManager.playSfx(GameSound.SPINDASH_CHARGE, pitch);
			counter = (short) Math.min(sprite.getSpindashCounter() + 0x200, 0x800);
			sprite.setSpindashCounter(counter);
		}

		doLevelBoundaryAndAnglePos();
		return true;
	}

	/** Release charged spindash (s2.asm:37244) */
	private void doReleaseSpindash() {
		sprite.applyRollingRadii(false);
		setRollAnimation();
		sprite.setY((short) (sprite.getY() + sprite.getRollHeightAdjustment()));
		sprite.setSpindash(false);

		short[] table = getSpindashSpeedTable();
		int speedIndex = Math.min((sprite.getSpindashCounter() >> 8) & 0xFF, table.length - 1);
		short spindashGSpeed = table[speedIndex];

		// ROM: Reset_Player_Position_Array before setting scroll delay
		sprite.resetPositionHistory();
		Camera camera = camera();
		if (camera != null && camera.getFocusedSprite() == sprite) {
			camera.setHorizScrollDelay(32 - ((spindashGSpeed - 0x800) >> 7));
		}

		if (Direction.LEFT.equals(sprite.getDirection())) {
			spindashGSpeed = (short) -spindashGSpeed;
		}
		sprite.setGSpeed(spindashGSpeed);

		// Calculate X/Y velocity from gSpeed immediately.
		// This is critical for the ground attachment threshold in doAnglePos() -
		// without this, xSpeed would be 0 giving a threshold of only 4 pixels,
		// causing false airborne detection on release (ROM: s2.asm:42727).
		int hexAngle = sprite.getAngle() & 0xFF;
		sprite.setXSpeed((short) ((spindashGSpeed * TrigLookupTable.cosHex(hexAngle)) >> 8));
		sprite.setYSpeed((short) ((spindashGSpeed * TrigLookupTable.sinHex(hexAngle)) >> 8));

		sprite.setRolling(true);

		audioManager.playSfx(GameSound.SPINDASH_RELEASE);
		doSpindashReleaseCollision();
	}

	/**
	 * Handle collision and movement for the spindash release frame.
	 * ROM processes this as part of the normal rolling mode flow, but since we're
	 * transitioning mid-frame, we need to apply rolling physics here.
	 * Mirrors modeRoll() order of operations (ROM: Obj01_MdRoll s2.asm:36180).
	 */
	private void doSpindashReleaseCollision() {
		short originalX = sprite.getX();
		short originalY = sprite.getY();

		// Apply rolling slope physics (ROM: Sonic_RollRepel)
		doRollRepel();

		// Recalculate X/Y velocity from gSpeed after slope physics
		// (doRollRepel may have modified gSpeed on slopes)
		short gSpeed = sprite.getGSpeed();
		int hexAngle = sprite.getAngle() & 0xFF;
		sprite.setXSpeed((short) ((gSpeed * TrigLookupTable.cosHex(hexAngle)) >> 8));
		sprite.setYSpeed((short) ((gSpeed * TrigLookupTable.sinHex(hexAngle)) >> 8));

		// Level boundary check
		doLevelBoundary();

		// Move sprite based on velocity
		sprite.move(sprite.getXSpeed(), sprite.getYSpeed());

		// Update sensors and ground position
		sprite.updateSensors(originalX, originalY);
		doAnglePos();

		// Slope slip/fall check (ROM: Sonic_SlopeRepel)
		doSlopeRepel();
	}

	// ========================================
	// JUMP
	// ========================================

	/** Sonic_Jump: Handle jump initiation (s2.asm:36996) */
	private boolean doJump() {
		int hexAngle = sprite.getAngle() & 0xFF;

		if (!collisionSystem().hasEnoughHeadroom(sprite, hexAngle)) {
			return false;
		}

		// ROM: bclr #sta_onObj,obStatus(a0) — clear riding/on-object state before jump
		collisionSystem().clearRidingObject(sprite);
		sprite.setOnObject(false);
		boolean wasRolling = sprite.getRolling();

		// Apply jump velocity based on terrain angle
		int xJumpChange = (TrigLookupTable.sinHex(hexAngle) * sprite.getJump()) >> 8;
		int yJumpChange = (TrigLookupTable.cosHex(hexAngle) * sprite.getJump()) >> 8;
		sprite.setXSpeed((short) (sprite.getXSpeed() + xJumpChange));
		sprite.setYSpeed((short) (sprite.getYSpeed() - yJumpChange));

		sprite.setAir(true);
		sprite.setPushing(false);
		sprite.setJumping(true);
		jumpPressed = true;
		jumpReleasedSinceJump = false;
		sprite.setStickToConvex(false);
		audioManager.playSfx(GameSound.JUMP);

		if (!wasRolling) {
			sprite.applyRollingRadii(true);
			sprite.setRolling(true);
			sprite.setY((short) (sprite.getY() + sprite.getRollHeightAdjustment()));
		} else {
			sprite.setRollingJump(true);
		}

		return true;
	}

	/** Sonic_JumpHeight: Jump release velocity cap (s2.asm:37076) */
	private void doJumpHeight() {
		if (jumpPressed) {
			short ySpeedCap = sprite.isInWater() ? (short) 0x200 : (short) 0x400;
			if (sprite.getYSpeed() < -ySpeedCap && !inputJump) {
				sprite.setYSpeed((short) -ySpeedCap);
			}
			// Track jump release for shield ability detection
			if (!inputJump) {
				jumpReleasedSinceJump = true;
			}
			// Shield ability: re-press jump after release while airborne (s3.asm:21059)
			if (jumpReleasedSinceJump && inputJumpPress && sprite.getDoubleJumpFlag() == 0) {
				if (tryShieldAbility()) {
					jumpReleasedSinceJump = false;
					return;
				}
			}
			if (!sprite.getAir() && !inputJump) {
				jumpPressed = false;
				jumpReleasedSinceJump = false;
			}
		} else {
			applyUpwardVelocityCap();
		}
	}

	/**
	 * Sonic_ShieldMoves: Try to activate the player's shield ability (sonic3k.asm:23397-23479).
	 * @return true if an ability was activated (or suppressed by Super)
	 */
	private boolean tryShieldAbility() {
		PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
		if (fs == null) {
			return false;
		}
		boolean hasElemental = fs.elementalShieldsEnabled();
		boolean hasInsta = fs.instaShieldEnabled();
		if (!hasElemental && !hasInsta) {
			return false;
		}

		// ROM (sonic3k.asm:23404-23408): Super Sonic suppresses all abilities
		if (sprite.isSuperSonic()) {
			sprite.setDoubleJumpFlag(1);
			return true;
		}

		// ROM (sonic3k.asm:23412-23413): Invincibility suppresses all abilities
		if (sprite.getInvincibleFrames() > 0) {
			return false;
		}

		// ROM: Sonic_ShieldMoves is only reached by Sonic (character_id == 0).
		// Tails uses Tails_JumpHeight → flight. Knuckles uses Knux_JumpHeight → glide.
		// Neither Tails nor Knuckles ever reaches the shield ability code.
		ShieldType shield = sprite.getShieldType();
		if (sprite.getSecondaryAbility() == SecondaryAbility.INSTA_SHIELD) {
			// Sonic: elemental shield abilities OR insta-shield
			if (hasElemental && shield != null) {
				switch (shield) {
					case FIRE -> fireShieldDash();
					case LIGHTNING -> lightningShieldJump();
					case BUBBLE -> bubbleShieldBounce();
					default -> { return false; } // BASIC shield: no ability
				}
				sprite.setDoubleJumpFlag(1);
				return true;
			}
			if (hasInsta && shield == null) {
				activateInstaShield();
				return true;
			}
		}

		// ROM (sonic3k.asm:32539-32586): Knuckles glide activation
		// Activates regardless of shield — shields provide passive protection only
		if (sprite.getSecondaryAbility() == SecondaryAbility.GLIDE) {
			activateGlide();
			return true;
		}

		return false;
	}

	/** ROM: Sonic_InstaShield (sonic3k.asm:23473-23479) */
	private void activateInstaShield() {
		sprite.setDoubleJumpFlag(1);
		var instaShield = sprite.getInstaShieldObject();
		if (instaShield != null) {
			instaShield.triggerAttack();
		}
		audioManager.playSfx(GameSound.INSTA_SHIELD);
	}

	/** Fire dash: horizontal burst in facing direction (sonic3k.asm:23411-23430) */
	private void fireShieldDash() {
		int dir = sprite.getDirection() == Direction.RIGHT ? 1 : -1;
		sprite.setXSpeed((short) (0x800 * dir));
		sprite.setYSpeed((short) 0);
		// ROM: Reset_Player_Position_Array then set H_scroll_frame_offset = $2000
		sprite.resetPositionHistory();
		Camera camera = camera();
		if (camera != null && camera.getFocusedSprite() == sprite) {
			camera.setHorizScrollDelay(32);
		}
		audioManager.playSfx(GameSound.FIRE_ATTACK);
		var shield = sprite.getShieldObject();
		if (shield != null) shield.onAbilityActivated(1);
	}

	/** Lightning double jump: upward velocity boost (s3.asm:21094-21102) */
	private void lightningShieldJump() {
		sprite.setYSpeed((short) -0x580);
		audioManager.playSfx(GameSound.LIGHTNING_ATTACK);
		var shield = sprite.getShieldObject();
		if (shield != null) shield.onAbilityActivated(1);
	}

	/** Bubble bounce: slam downward (s3.asm:21105-21114) */
	private void bubbleShieldBounce() {
		sprite.setXSpeed((short) 0);
		sprite.setGSpeed((short) 0);
		sprite.setYSpeed((short) 0x800);
		audioManager.playSfx(GameSound.BUBBLE_ATTACK);
		var shield = sprite.getShieldObject();
		if (shield != null) shield.onAbilityActivated(1);
	}

	// ========================================
	// KNUCKLES GLIDE / WALL CLIMB
	// ========================================

	/**
	 * ROM: Knux_Test_For_Glide (sonic3k.asm:32560-32586).
	 * Initiates glide from airborne state.
	 */
	private void activateGlide() {
		// Clear rolling state and set glide radii (0x0A x 0x0A)
		sprite.setRolling(false);
		sprite.setRollingJump(false);
		sprite.applyCustomRadii(10, 10);

		// Add 0x200 to y_vel, cap at 0 if negative result
		int newYVel = sprite.getYSpeed() + 0x200;
		if (newYVel < 0) {
			newYVel = 0;
		}
		sprite.setYSpeed((short) newYVel);

		// Set horizontal velocity: 0x400 in facing direction
		int xDir = (sprite.getDirection() == Direction.RIGHT) ? 1 : -1;
		sprite.setXSpeed((short) (0x400 * xDir));
		sprite.setGSpeed((short) 0x400);

		// Store initial direction in doubleJumpProperty
		// 0x00 = facing right, 0x80 (-128) = facing left
		byte dirProp = (sprite.getDirection() == Direction.RIGHT) ? (byte) 0 : (byte) -128;
		sprite.setDoubleJumpProperty(dirProp);

		sprite.setDoubleJumpFlag(1);
		sprite.setAngle((byte) 0);

		// Set glide animation — uses the standard walk anim slot
		// which Knuckles' animation table maps to glide frames
		setGlideAnimation();
	}

	/**
	 * ROM: Knuckles_Glide (sonic3k.asm:30687-30733).
	 * Called each frame while doubleJumpFlag >= 1 and in air.
	 * Handles the glide state machine dispatch.
	 */
	private void updateKnucklesGlide() {
		int state = sprite.getDoubleJumpFlag();
		switch (state) {
			case 1 -> updateGliding();
			case 2 -> {} // Falling from glide — normal airborne physics apply
			case 3 -> updateSliding();
			case 4 -> updateWallClimb();
			case 5 -> updateLedgeClimb();
		}
	}

	/**
	 * ROM: Knuckles_Move_Glide (sonic3k.asm:31598-31717).
	 * Active glide physics — acceleration, turning, gravity balance.
	 */
	private void updateGliding() {
		// Check for jump button release → fall from glide
		boolean holdingJump = inputJump;
		if (!holdingJump) {
			enterFallFromGlide();
			return;
		}

		// Accelerate glide speed
		int gSpeed = sprite.getGSpeed() & 0xFFFF;
		if (gSpeed < 0x400) {
			// Low speed: accelerate by 8
			gSpeed += 8;
		} else if (gSpeed < 0x1800) {
			// Medium speed: accelerate by 4 (only if not turning)
			byte prop = sprite.getDoubleJumpProperty();
			if ((prop & 0x7F) == 0) {
				gSpeed += 4;
			}
		}
		sprite.setGSpeed((short) gSpeed);

		// Handle turning based on left/right input
		byte prop = sprite.getDoubleJumpProperty();
		if (inputLeft) {
			if (prop != (byte) 0x80) {
				prop = (byte) (prop < 0 ? -prop : prop);  // make positive if negative
				prop = (byte) (prop + 2);
			}
		} else if (inputRight) {
			if (prop != 0) {
				prop = (byte) (prop > 0 ? -prop : prop);  // make negative if positive
				prop = (byte) (prop + 2);
			}
		} else {
			// No input: decay turn toward center
			int absProp = prop & 0x7F;
			if (absProp != 0) {
				prop = (byte) (prop + 2);
			}
		}
		sprite.setDoubleJumpProperty(prop);

		// Calculate x velocity from angle and ground speed
		// ROM: GetSineCosine then muls.w ground_vel, asr.l #8
		int angle = prop & 0xFF;
		int cosVal = TrigLookupTable.cosHex(angle);
		int xVel = (cosVal * gSpeed) >> 8;
		sprite.setXSpeed((short) xVel);

		// Gravity balance: converge y_vel toward ~0x80
		int yVel = sprite.getYSpeed();
		if (yVel >= 0x80) {
			// Falling fast: reduce by 0x20 (parachute effect)
			sprite.setYSpeed((short) (yVel - 0x20));
		} else {
			// Falling slow or rising: add gravity 0x20
			sprite.setYSpeed((short) (yVel + 0x20));
		}

		// Collision is checked after doLevelCollision() in modeAirborne
		setGlideAnimation();
	}

	/**
	 * ROM: sonic3k.asm:30712-30729. Player released jump button during glide.
	 * Transitions to fall state (doubleJumpFlag = 2).
	 */
	private void enterFallFromGlide() {
		sprite.setDoubleJumpFlag(2);

		// Face the direction of movement
		if (sprite.getXSpeed() >= 0) {
			sprite.setDirection(Direction.RIGHT);
		} else {
			sprite.setDirection(Direction.LEFT);
		}

		// Divide X velocity by 4
		sprite.setXSpeed((short) (sprite.getXSpeed() >> 2));

		// Restore default radii and release direct frame control
		sprite.restoreDefaultRadii();
		sprite.setObjectMappingFrameControl(false);

		// Set fall-from-glide animation (GLIDE_DROP = 0x21)
		sprite.setForcedAnimationId(0x21);
	}

	/**
	 * ROM: Knux_Gliding_HitFloor (sonic3k.asm:30736-30769).
	 * Called when ground sensors detect floor contact during glide.
	 */
	private void glideHitFloor() {
		// Face the direction of movement
		if (sprite.getXSpeed() >= 0) {
			sprite.setDirection(Direction.RIGHT);
		} else {
			sprite.setDirection(Direction.LEFT);
		}

		int hexAngle = sprite.getAngle() & 0xFF;
		int adjustedAngle = (hexAngle + 0x20) & 0xC0;

		if (adjustedAngle != 0) {
			// Non-flat surface: land normally
			sprite.setXSpeed((short) sprite.getGSpeed());
			sprite.setYSpeed((short) 0);
			sprite.restoreDefaultRadii();
			sprite.setObjectMappingFrameControl(false);
			sprite.setDoubleJumpFlag(0);
			sprite.setDoubleJumpProperty((byte) 0);
			sprite.setForcedAnimationId(-1);
			sprite.setAir(false);
			sprite.setJumping(false);
			return;
		}

		// Flat surface: land first (setAir clears glide state), then enter sliding.
		// Order matters: setAir(false) triggers landing cleanup that clears
		// doubleJumpFlag, so sliding state must be set up AFTER.
		sprite.setAir(false);
		sprite.setJumping(false);
		// Now re-enter sliding state on top of the clean landing state
		sprite.setDoubleJumpFlag(3);
		sprite.applyCustomRadii(10, 10);
		sprite.setObjectMappingFrameControl(true);
		sprite.setMappingFrame(0xCC);  // ROM: sliding mapping frame
		sprite.setForcedAnimationId(-1);
		audioManager.playSfx(GameSound.GLIDE_LAND);
	}

	/**
	 * ROM: Knuckles_Sliding (sonic3k.asm:30946-31014).
	 * Ground slide after glide — continues while jump button is held,
	 * decelerating x_vel by 0x20 per frame. Stops immediately when
	 * button released or velocity crosses zero.
	 */
	private void updateSliding() {
		// ROM: Check if A/B/C button is held. If not → .getUp
		if (!inputJump) {
			slideGetUp();
			return;
		}

		// Decelerate x_vel by 0x20 toward zero
		int xVel = sprite.getXSpeed();
		if (xVel < 0) {
			// Going left: add 0x20
			xVel += 0x20;
			if (xVel >= 0) {
				// Velocity crossed zero → .getUp
				slideGetUp();
				return;
			}
		} else if (xVel > 0) {
			// Going right: subtract 0x20
			xVel -= 0x20;
			if (xVel <= 0) {
				// Velocity crossed zero → .getUp
				slideGetUp();
				return;
			}
		} else {
			// Already zero → .getUp
			slideGetUp();
			return;
		}

		sprite.setXSpeed((short) xVel);

		// ROM .continueSliding (sonic3k.asm:30992-31020):
		// Move horizontally, then snap to floor. If floor distance >= 14,
		// Knuckles has slid off a ledge → enter fall state.
		sprite.move(sprite.getXSpeed(), (short) 0);

		// Probe floor distance and snap
		var floorResult = ObjectTerrainUtils.checkFloorDist(
				sprite.getCentreX(), sprite.getCentreY() + sprite.getYRadius());
		if (floorResult != null) {
			if (floorResult.distance() >= 14) {
				// Slid off a ledge — enter fall state
				sprite.setDoubleJumpFlag(2);
				sprite.restoreDefaultRadii();
				sprite.setObjectMappingFrameControl(false);
				sprite.setForcedAnimationId(0x21);  // GLIDE_DROP
				sprite.setAir(true);
				return;
			}
			// Snap to floor and update angle
			sprite.setY((short) (sprite.getY() + floorResult.distance()));
			sprite.setAngle(floorResult.angle());
		}
	}

	/**
	 * ROM: Knuckles_Sliding .getUp (sonic3k.asm:30969-30989).
	 * Exits sliding state — zeroes velocity, restores default radii,
	 * sets GLIDE_LAND animation, applies move_lock.
	 */
	private void slideGetUp() {
		sprite.setGSpeed((short) 0);
		sprite.setXSpeed((short) 0);
		sprite.setYSpeed((short) 0);

		// Adjust Y position for radii change (current→default)
		int radiusDiff = sprite.getYRadius() - sprite.getStandYRadius();
		sprite.setY((short) (sprite.getY() + radiusDiff));

		sprite.restoreDefaultRadii();
		sprite.setObjectMappingFrameControl(false);
		sprite.setDoubleJumpFlag(0);
		sprite.setDoubleJumpProperty((byte) 0);

		// ROM: move.w #$F,move_lock — 15-frame input lock
		sprite.setMoveLockTimer(0x0F);
		// ROM: move.b #$22,anim — GLIDE_LAND animation (brief get-up/crouch pose)
		sprite.setForcedAnimationId(0x22);
	}

	/**
	 * Clears any lingering glide animation state.
	 * Called when transitioning out of all glide states to normal gameplay.
	 */
	private void clearGlideAnimationState() {
		sprite.setObjectMappingFrameControl(false);
		sprite.setForcedAnimationId(-1);
		sprite.setDoubleJumpFlag(0);
		sprite.setDoubleJumpProperty((byte) 0);
	}

	/**
	 * ROM: Knuckles_Wall_Climb (sonic3k.asm:31074-31434).
	 * Wall climbing state after grabbing a wall during glide.
	 * Knuckles moves up/down on the wall with input, or jumps away.
	 * Animation cycles through frames 0xB7-0xBC every 4 frames of movement.
	 */
	private void updateWallClimb() {
		// Maintain X position against wall
		sprite.setX(sprite.getWallClimbX());

		int climbAnimDelta = 0;  // +1 = forward (climbing up), -1 = backward (climbing down)

		if (inputUp) {
			// Climbing up: check wall distance at the top to detect ledge
			boolean facingRight = sprite.getDirection() == Direction.RIGHT;
			int probeY = sprite.getCentreY() - 11;
			var wallResult = getWallDistance(probeY, facingRight);

			if (wallResult != null && wallResult.distance() >= 4) {
				// Wall gone above — climb up over ledge
				enterLedgeClimb();
				return;
			}

			if (wallResult != null && wallResult.distance() != 0) {
				// Small dip in wall — don't move
			} else {
				// Check ceiling clearance
				var ceilResult = ObjectTerrainUtils.checkCeilingDist(
						sprite.getCentreX(), sprite.getCentreY(), sprite.getYRadius());
				if (ceilResult != null && ceilResult.distance() < 0) {
					// Bumping ceiling — push out
					sprite.setY((short) (sprite.getY() - ceilResult.distance()));
				} else {
					sprite.setY((short) (sprite.getY() - 1));
				}
				climbAnimDelta = 1;
			}
		} else if (inputDown) {
			// Climbing down: check wall distance at the bottom
			boolean facingRight = sprite.getDirection() == Direction.RIGHT;
			int probeY = sprite.getCentreY() + 11;
			var wallResult = getWallDistance(probeY, facingRight);

			if (wallResult != null && wallResult.distance() != 0) {
				// Climbed off bottom of wall — let go
				letGoOfWall();
				return;
			}

			// Check floor clearance
			var floorResult = ObjectTerrainUtils.checkFloorDist(
					sprite.getCentreX(), sprite.getCentreY() + sprite.getYRadius());
			if (floorResult != null && floorResult.distance() <= 0) {
				// Reached floor
				sprite.setY((short) (sprite.getY() + floorResult.distance()));
				exitWallClimbToGround();
				return;
			}
			sprite.setY((short) (sprite.getY() + 1));
			climbAnimDelta = -1;
		}

		// ROM: Animation frame cycling (sonic3k.asm:31384-31404)
		// Animate every 4 frames when moving, using double_jump_property as timer
		if (climbAnimDelta != 0) {
			byte timer = (byte) (sprite.getDoubleJumpProperty() - 1);
			if (timer < 0) {
				timer = 3;
				// Advance mapping frame
				int frame = sprite.getMappingFrame() + climbAnimDelta;
				// Wrap within range 0xB7-0xBC
				if (frame < 0xB7) frame = 0xBC;
				if (frame > 0xBC) frame = 0xB7;
				sprite.setMappingFrame(frame);
			}
			sprite.setDoubleJumpProperty(timer);
		}

		// ROM: Check for jump button to jump away (sonic3k.asm:31410-31434)
		if (inputJumpPress) {
			sprite.restoreDefaultRadii();
			sprite.setObjectMappingFrameControl(false);
			sprite.setDoubleJumpFlag(0);
			sprite.setDoubleJumpProperty((byte) 0);
			sprite.setForcedAnimationId(-1);

			// ROM: bchg #Status_Facing — flip direction (jump AWAY from wall)
			boolean wasFacingRight = sprite.getDirection() == Direction.RIGHT;
			sprite.setDirection(wasFacingRight ? Direction.LEFT : Direction.RIGHT);

			// ROM: y_vel = -$380, x_vel = $400 (toward new facing direction)
			int dir = wasFacingRight ? -1 : 1;
			sprite.setXSpeed((short) (0x400 * dir));
			sprite.setYSpeed((short) -0x380);
			sprite.setAir(true);
			sprite.setJumping(true);
			sprite.setRolling(true);
			sprite.applyRollingRadii(true);
			audioManager.playSfx(GameSound.JUMP);
			return;
		}
	}

	/** Probes wall distance at a given Y position in the facing direction. */
	private com.openggf.physics.TerrainCheckResult getWallDistance(int probeY, boolean facingRight) {
		int probeX = facingRight
				? sprite.getCentreX() + sprite.getXRadius()
				: sprite.getCentreX() - sprite.getXRadius();
		return facingRight
				? ObjectTerrainUtils.checkRightWallDist(probeX, probeY)
				: ObjectTerrainUtils.checkLeftWallDist(probeX, probeY);
	}

	/** ROM: Knuckles_LetGoOfWall (sonic3k.asm:31449-31461) — drop off bottom of wall. */
	private void letGoOfWall() {
		sprite.setDoubleJumpFlag(2);
		sprite.restoreDefaultRadii();
		sprite.setObjectMappingFrameControl(false);
		sprite.setForcedAnimationId(0x21);  // GLIDE_DROP
	}

	/** Transition from wall climb to standing on ground (reached floor while climbing down). */
	private void exitWallClimbToGround() {
		sprite.setGSpeed((short) 0);
		sprite.setXSpeed((short) 0);
		sprite.setYSpeed((short) 0);
		sprite.restoreDefaultRadii();
		sprite.setObjectMappingFrameControl(false);
		sprite.setDoubleJumpFlag(0);
		sprite.setDoubleJumpProperty((byte) 0);
		sprite.setForcedAnimationId(-1);
		sprite.setAir(false);
		sprite.setJumping(false);
	}

	/** ROM: Knuckles_ClimbUp (sonic3k.asm:31437-31446) — initiate ledge climb. */
	private void enterLedgeClimb() {
		sprite.setDoubleJumpFlag(5);
		sprite.setDoubleJumpProperty((byte) 0);
		doLedgeClimbAnimation();
	}

	/** ROM: Knuckles_ClimbLedge_Frames (sonic3k.asm:31503-31509) */
	private static final int[][] LEDGE_CLIMB_FRAMES = {
		// { mapping_frame, x_delta, y_delta, timer }
		{ 0xBD,  3,  -3, 6 },
		{ 0xBE,  8, -10, 6 },
		{ 0xBF, -8, -12, 6 },
		{ 0xD2,  8,  -5, 6 },
	};

	/**
	 * ROM: Knuckles_DoLedgeClimbingAnimation (sonic3k.asm:31467-31496).
	 * Advances through the ledge climb frame table one entry at a time.
	 */
	private void doLedgeClimbAnimation() {
		int index = (sprite.getDoubleJumpProperty() & 0xFF) / 4;
		if (index >= LEDGE_CLIMB_FRAMES.length) {
			// Animation complete — exit to standing
			exitWallClimbToGround();
			return;
		}

		int[] entry = LEDGE_CLIMB_FRAMES[index];
		sprite.setMappingFrame(entry[0]);

		// X delta is negated when facing left
		int xDelta = entry[1];
		if (sprite.getDirection() == Direction.LEFT) {
			xDelta = -xDelta;
		}
		sprite.setX((short) (sprite.getX() + xDelta));
		sprite.setY((short) (sprite.getY() + entry[2]));

		sprite.setDoubleJumpProperty((byte) (sprite.getDoubleJumpProperty() + 4));
	}

	/**
	 * ROM: Knuckles_Climb_Ledge (sonic3k.asm:31437).
	 * Called each frame while in state 5 — advances the ledge climb animation.
	 */
	private void updateLedgeClimb() {
		int index = (sprite.getDoubleJumpProperty() & 0xFF) / 4;
		if (index >= LEDGE_CLIMB_FRAMES.length) {
			exitWallClimbToGround();
			return;
		}
		doLedgeClimbAnimation();
	}

	/**
	 * ROM: Knux_DoLevelCollision_CheckRet (sonic3k.asm:32625-32684).
	 * Custom collision for glide state — probes walls and floor directly
	 * using ObjectTerrainUtils rather than the generic airborne collision.
	 */
	private void doGlideCollision() {
		int cx = sprite.getCentreX();
		int cy = sprite.getCentreY();
		int xRad = sprite.getXRadius();
		int yRad = sprite.getYRadius();

		// Check wall in movement direction
		// Save the movement direction BEFORE zeroing velocity (needed by glideHitWall)
		int xVel = sprite.getXSpeed();
		boolean movingRight = xVel >= 0;

		if (xVel > 0) {
			var result = ObjectTerrainUtils.checkRightWallDist(cx + xRad, cy);
			if (result != null && result.distance() < 0) {
				sprite.setX((short) (sprite.getX() + result.distance()));
				sprite.setXSpeed((short) 0);
				glideHitWall(movingRight);
				return;
			}
		} else if (xVel < 0) {
			var result = ObjectTerrainUtils.checkLeftWallDist(cx - xRad, cy);
			if (result != null && result.distance() < 0) {
				sprite.setX((short) (sprite.getX() - result.distance()));
				sprite.setXSpeed((short) 0);
				glideHitWall(movingRight);
				return;
			}
		}

		// Check floor (only when descending or level)
		if (sprite.getYSpeed() >= 0) {
			var result = ObjectTerrainUtils.checkFloorDist(cx, cy + yRad);
			if (result != null && result.distance() < 0) {
				sprite.setY((short) (sprite.getY() + result.distance()));
				sprite.setAngle(result.angle());
				sprite.setYSpeed((short) 0);
				glideHitFloor();
				return;
			}
		}

		// Check opposite wall too (ROM checks both walls in some quadrants)
		if (xVel <= 0) {
			var result = ObjectTerrainUtils.checkRightWallDist(cx + xRad, cy);
			if (result != null && result.distance() < 0) {
				sprite.setX((short) (sprite.getX() + result.distance()));
			}
		}
		if (xVel >= 0) {
			var result = ObjectTerrainUtils.checkLeftWallDist(cx - xRad, cy);
			if (result != null && result.distance() < 0) {
				sprite.setX((short) (sprite.getX() - result.distance()));
			}
		}
	}

	/**
	 * ROM: Knuckles_Gliding_HitWall (sonic3k.asm:30772-30827).
	 * Transitions to wall climb state when hitting a wall during glide.
	 * @param wasMovingRight the movement direction at the time of wall contact
	 *                       (before x velocity was zeroed by collision)
	 */
	private void glideHitWall(boolean wasMovingRight) {
		// Face toward the wall (use saved direction since xSpeed is already zeroed)
		sprite.setDirection(wasMovingRight ? Direction.RIGHT : Direction.LEFT);

		audioManager.playSfx(GameSound.GRAB);

		// Zero all velocities
		sprite.setGSpeed((short) 0);
		sprite.setXSpeed((short) 0);
		sprite.setYSpeed((short) 0);

		// Enter wall climb state
		sprite.setDoubleJumpFlag(4);
		sprite.setDoubleJumpProperty((byte) 3);  // ROM: double_jump_property = 3

		// Record wall X position
		sprite.setWallClimbX(sprite.getX());

		// Wall climb animation — mapping frame 0xB7
		sprite.setForcedAnimationId(-1);  // Let object mapping frame control take over
		sprite.setObjectMappingFrameControl(true);
		sprite.setMappingFrame(0xB7);
	}

	// Wall climb boundary checking is now integrated into updateWallClimb()
	// using ObjectTerrainUtils for wall/floor/ceiling probing.

	/** ROM: RawAni_Knuckles_GlideTurn (sonic3k.asm:31584-31593) */
	private static final int[] GLIDE_TURN_FRAMES = {
		0xC0, 0xC1, 0xC2, 0xC3, 0xC4, 0xC3, 0xC2, 0xC1
	};

	/**
	 * ROM: Knuckles_Set_Gliding_Animation (sonic3k.asm:31560-31581).
	 * Directly sets mapping_frame from a lookup table based on glide turn angle.
	 * Does NOT use the scripted animation system.
	 */
	private void setGlideAnimation() {
		// Enable direct mapping frame control (bypasses animation manager)
		sprite.setObjectMappingFrameControl(true);
		sprite.setPushing(false);

		// ROM: bclr #Status_Facing — always face RIGHT during glide
		sprite.setDirection(Direction.RIGHT);

		// Calculate frame index from double_jump_property:
		// ROM: moveq #0,d0; move.b double_jump_property,d0; addi.b #$10,d0; lsr.w #5,d0
		int prop = sprite.getDoubleJumpProperty() & 0xFF;
		int index = ((prop + 0x10) & 0xFF) >> 5;
		if (index >= GLIDE_TURN_FRAMES.length) {
			index = 0;
		}

		int frame = GLIDE_TURN_FRAMES[index];

		// ROM: If frame == $C4, set facing LEFT and use $C0 with h-flip instead
		if (frame == 0xC4) {
			sprite.setDirection(Direction.LEFT);
			frame = 0xC0;
		}

		sprite.setMappingFrame(frame);
	}

	// ========================================
	// GROUND MOVEMENT
	// ========================================

	/** Sonic_SlopeResist: Apply slope factor when walking (s2.asm:37360) */
	private void doSlopeResist() {
		int hexAngle = sprite.getAngle() & 0xFF;
		short gSpeed = sprite.getGSpeed();

		if (isOnSteepSurface(hexAngle) || gSpeed == 0) {
			return;
		}

		int slopeEffect = (slopeRunning * TrigLookupTable.sinHex(hexAngle)) >> 8;
		sprite.setGSpeed((short) (gSpeed + slopeEffect));
	}

	/** Sonic_Move: Ground input handling, accel/decel, wall collision (s2.asm:36220) */
	private void doGroundMove() {
		short gSpeed = sprite.getGSpeed();

		// ROM: _btst #status_secondary.sliding,status_secondary(a0) / _bne.w Obj01_Traction
		// (s2.asm:36224-36225, s1.asm:309-310)
		// When sliding (water slides, wind tunnels), skip ALL input processing and
		// friction — go straight to velocity conversion and wall collision.
		if (sprite.isSliding()) {
			sprite.setGSpeed(gSpeed);
			calculateXYFromGSpeed();
			collisionSystem().resolveGroundWallCollision(sprite);
			return;
		}

		short runAccel = sprite.getRunAccel();
		short runDecel = sprite.getRunDecel();
		short friction = sprite.getFriction();
		short max = sprite.getMax();
		Camera camera = camera();

		// Move lock - skip input processing but still apply friction
		// ROM: When move_lock is active, branches to Obj01_ResetScr which continues
		// to friction check. It does NOT return early and skip friction.
		boolean moveLockActive = sprite.getMoveLockTimer() > 0;

		if (moveLockActive) {
			// Camera easing during move lock
			if (camera != null) camera.easeYBiasToDefault();
		} else {
			// Left input (only when move_lock is not active)
			if (inputLeft) {
				if (gSpeed > 0) {
					gSpeed -= runDecel;
					if (gSpeed < 0) gSpeed = (short) -128;
					if (isOnFlatGround() && gSpeed > SKID_SPEED_THRESHOLD) {
						handleSkid();
					}
				} else {
					sprite.setSkidding(false);
					gSpeed = accelerateLeft(gSpeed, runAccel, max);
				}
			}

			// Right input (only when move_lock is not active)
			if (inputRight) {
				if (gSpeed < 0) {
					gSpeed += runDecel;
					// ROM: add.w d4,d0 / bcc.s ... / move.w #$80,d0
					// 68000 carry flag is SET when the unsigned add overflows (e.g.
					// 0xFF80+0x80=0x10000), which happens when the result is zero
					// or positive. BCC (branch if carry clear) is NOT taken, so
					// the reset to 0x80 executes for both gSpeed==0 and gSpeed>0.
					if (gSpeed >= 0) gSpeed = (short) 128;
					if (isOnFlatGround() && gSpeed < -SKID_SPEED_THRESHOLD) {
						handleSkid();
					}
				} else {
					sprite.setSkidding(false);
					gSpeed = accelerateRight(gSpeed, runAccel, max);
				}
			}

			if (!inputLeft && !inputRight) {
				sprite.setSkidding(false);
			}

			// Standing still handling (ROM: Sonic_Lookup, Sonic_Duck, Obj01_ResetScr)
			// S1: no delay - camera pans immediately (s1.asm: Sonic_LookUp/Sonic_Duck)
			// S2/S3K: 120-frame (2 second) delay before panning (s2.asm:36402-36405)
			if (isOnFlatGround() && gSpeed == 0) {
				sprite.setPushing(false);
				short lookDelay = sprite.getLookDelayCounter();
				PhysicsFeatureSet featureSet = sprite.getPhysicsFeatureSet();
				short lookScrollDelay = (featureSet != null) ? featureSet.lookScrollDelay()
						: PhysicsFeatureSet.LOOK_SCROLL_DELAY_S2;
				if (inputUp) {
					// ROM: Sonic_Lookup (s2.asm:36398-36409, s1.asm: Sonic_LookUp)
					// Animation is set immediately, camera pan may have delay
					sprite.setLookingUp(true);
					lookDelay++;
					if (camera != null) {
						if (lookDelay >= lookScrollDelay) {
							lookDelay = lookScrollDelay;
							camera.incrementLookUpBias();
						} else {
							// During delay, bias still eases toward default
							camera.easeYBiasToDefault();
						}
					}
				} else if (inputDown) {
					// ROM: Sonic_Duck (s2.asm:36412-36423, s1.asm: Sonic_Duck)
					// Animation (crouching) is handled by updateCrouchState()
					sprite.setLookingUp(false);
					lookDelay++;
					if (camera != null) {
						if (lookDelay >= lookScrollDelay) {
							lookDelay = lookScrollDelay;
							camera.decrementLookDownBias();
						} else {
							// During delay, bias still eases toward default
							camera.easeYBiasToDefault();
						}
					}
				} else {
					// ROM: Obj01_ResetScr (s2.asm:36428-36429)
					sprite.setLookingUp(false);
					lookDelay = 0;
					if (camera != null) {
						camera.easeYBiasToDefault();
					}
				}
				sprite.setLookDelayCounter(lookDelay);
			} else {
				// Not standing still - reset state and ease bias to default
				sprite.setLookingUp(false);
				sprite.setLookDelayCounter((short) 0);
				if (camera != null) {
					camera.easeYBiasToDefault();
				}
			}
		}

		// Friction
		// ROM ref: s2.asm:36443-36446 — Super Sonic uses normal friction (0x0C) not his profile friction (0x30)
		if (!inputRawLeft && !inputRawRight) {
			short effectiveFriction = sprite.isSuperSonic() ? (short) 0x0C : friction;
			gSpeed = applyFriction(gSpeed, effectiveFriction);
		}

		sprite.setGSpeed(gSpeed);
		calculateXYFromGSpeed();
		collisionSystem().resolveGroundWallCollision(sprite);
	}

	/** Sonic_Roll / SonicKnux_Roll: Check if should start rolling.
	 *  S2: s2.asm:36954 (threshold 0x80). S3K: sonic3k.asm:23223 (threshold 0x100). */
	private void doCheckStartRoll() {
		short gSpeed = sprite.getGSpeed();

		// S3K uses movingCrouchThreshold ($100) as the roll speed threshold;
		// below that speed, down enters crouch (handled in updateCrouchState).
		PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
		int rollThreshold = (fs != null && fs.movingCrouchThreshold() > 0)
				? fs.movingCrouchThreshold() : minStartRollSpeed;
		if (Math.abs(gSpeed) < rollThreshold) return;
		if (inputLeft || inputRight) return;
		if (!inputDown) return;
		if (sprite.getAir() || sprite.getRolling()) return;

		sprite.setRolling(true);
		sprite.setY((short) (sprite.getY() + sprite.getRollHeightAdjustment()));
		audioManager.playSfx(GameSound.ROLLING);

		if (sprite.getGSpeed() == 0) {
			sprite.setGSpeed((short) 0x200);
		}
	}

	/** Sonic_RollRepel: Apply rolling slope factor 80/20 (s2.asm:37393) */
	private void doRollRepel() {
		int hexAngle = sprite.getAngle() & 0xFF;
		short gSpeed = sprite.getGSpeed();

		if (isOnSteepSurface(hexAngle)) return;

		// ROM uses $50 (80) base factor, reduced to $50 >> 2 (20) when going uphill
		boolean goingDownhill = (gSpeed >= 0) == (TrigLookupTable.sinHex(hexAngle) >= 0);
		int slopeFactor = goingDownhill ? slopeRollingDown : slopeRollingUp;
		int slopeEffect = (slopeFactor * TrigLookupTable.sinHex(hexAngle)) >> 8;

		sprite.setGSpeed((short) (gSpeed + slopeEffect));
	}

	/** Sonic_RollSpeed: Roll deceleration and velocity conversion (s2.asm:36666) */
	private void doRollSpeed() {
		short gSpeed = sprite.getGSpeed();
		camera().easeYBiasToDefault();

		// ROM: tst.b (f_slidemode).w / bne.w loc_131CC (s1.asm:602-603)
		// When sliding, skip input and friction — go straight to velocity conversion.
		if (sprite.isSliding()) {
			sprite.setGSpeed(gSpeed);
			// Convert to X/Y with cap (same as below)
			int hexAngle = sprite.getAngle() & 0xFF;
			short xVel = (short) ((gSpeed * TrigLookupTable.cosHex(hexAngle)) >> 8);
			short yVel = (short) ((gSpeed * TrigLookupTable.sinHex(hexAngle)) >> 8);
			xVel = (short) Math.max(-0x1000, Math.min(0x1000, xVel));
			sprite.setXSpeed(xVel);
			sprite.setYSpeed(yVel);
			collisionSystem().resolveGroundWallCollision(sprite);
			return;
		}

		boolean inputAllowed = sprite.getMoveLockTimer() == 0;

		// Controlled roll deceleration: hardcoded $20 regardless of water state
		// ROM ref: s2.asm:36671 — move.w #$20,d4
		short rollDecel = (short) 0x20;
		if (inputAllowed && inputLeft && gSpeed > 0) {
			gSpeed -= rollDecel;
			if (gSpeed < 0) gSpeed = (short) -128;
		}
		if (inputAllowed && inputRight && gSpeed < 0) {
			gSpeed += rollDecel;
			if (gSpeed > 0) gSpeed = (short) 128;
		}

		// Natural deceleration
		if (gSpeed != 0) {
			short naturalDecel = (short) (sprite.getRunAccel() / 2);
			gSpeed = applyFriction(gSpeed, naturalDecel);
		}

		// Stop rolling check
		if (gSpeed == 0) {
			if (sprite.getPinballMode()) {
				gSpeed = (short) (sprite.getDirection() == Direction.LEFT ? -0x400 : 0x400);
			} else {
				sprite.setRolling(false);
				sprite.setY((short) (sprite.getY() - sprite.getRollHeightAdjustment()));
			}
		}

		sprite.setGSpeed(gSpeed);

		// Convert to X/Y with cap
		int hexAngle = sprite.getAngle() & 0xFF;
		short xVel = (short) ((gSpeed * TrigLookupTable.cosHex(hexAngle)) >> 8);
		short yVel = (short) ((gSpeed * TrigLookupTable.sinHex(hexAngle)) >> 8);
		xVel = (short) Math.max(-0x1000, Math.min(0x1000, xVel));

		sprite.setXSpeed(xVel);
		sprite.setYSpeed(yVel);
		collisionSystem().resolveGroundWallCollision(sprite);
	}

	// ========================================
	// AIR MOVEMENT
	// ========================================

	/** Sonic_ChgJumpDir: Air control and drag (s2.asm:36815) */
	private void doChgJumpDir() {
		short xSpeed = sprite.getXSpeed();
		short ySpeed = sprite.getYSpeed();
		short runAccel = sprite.getRunAccel();
		short max = sprite.getMax();

		// Air control (skip if rolling jump)
		// ROM behavior (s2.asm:36826-36840): ALWAYS apply acceleration and cap.
		// Unlike ground movement, air control caps even high speeds from slopes/springs.
		if (!sprite.getRollingJump()) {
			if (inputLeft) {
				sprite.setDirection(Direction.LEFT);
				xSpeed -= (2 * runAccel);
				if (xSpeed < -max) xSpeed = (short) -max;
			}
			if (inputRight) {
				sprite.setDirection(Direction.RIGHT);
				xSpeed += (2 * runAccel);
				if (xSpeed > max) xSpeed = max;
			}
		}

		camera().easeYBiasToDefault();

		// Air drag near apex (-1024 <= ySpeed < 0)
		// ROM: asr.w #5,d1 — arithmetic shift right rounds toward -∞.
		// Java /32 truncates toward zero, giving wrong drag for negative xSpeed.
		if (ySpeed < 0 && ySpeed >= -1024) {
			int drag = xSpeed >> 5;
			if (drag != 0) {
				int newXSpeed = xSpeed - drag;
				if ((xSpeed > 0 && newXSpeed < 0) || (xSpeed < 0 && newXSpeed > 0)) {
					newXSpeed = 0;
				}
				xSpeed = (short) newXSpeed;
			}
		}

		sprite.setXSpeed(xSpeed);
		sprite.setYSpeed(ySpeed);
	}

	/** ObjectMoveAndFall: Apply velocity and gravity (s2.asm:29945-29953)
	 * ROM applies gravity to y_vel BEFORE movement, but uses the OLD y_vel for position:
	 *   move.w  y_vel(a0),d0           ; Save old y_vel in d0
	 *   addi.w  #$38,y_vel(a0)         ; Add gravity to y_vel FIRST
	 *   ext.l   d0
	 *   asl.l   #8,d0
	 *   add.l   d0,d3                  ; Position uses OLD y_vel (d0, before gravity)
	 */
	private void doObjectMoveAndFall() {
		short oldYSpeed = sprite.getYSpeed();  // Save old y_vel before gravity
		sprite.setYSpeed((short) (oldYSpeed + sprite.getGravity()));  // Apply gravity first
		sprite.move(sprite.getXSpeed(), oldYSpeed);  // Move using OLD y_vel
	}

	// ========================================
	// COLLISION
	// ========================================

	/** Sonic_LevelBound: Check level boundaries (s2.asm:36890) */
	private void doLevelBoundary() {
		// ROM: When object_control is set, level boundary checks are skipped
		// (Obj01_Control skips movement routines entirely).
		if (sprite.isObjectControlled()) {
			return;
		}
		Camera camera = camera();
		if (camera == null) return;

		// ROM uses center coordinates for x_pos, so boundary offsets are calibrated for center
		final int SCREEN_WIDTH = 320, SONIC_WIDTH = 24, LEFT_OFFSET = 16, RIGHT_EXTRA = 64;

		// ROM: move.l obX(a0),d1 / ext.l d0 / asl.l #8,d0 / add.l d0,d1 / swap d1
		// Uses 32-bit position (pixel:16 | subpixel:16) + velocity << 8.
		// The subpixel byte is in bits 8-15 of xSubpixel (high byte of low word).
		int xTotal = (sprite.getCentreX() * 256) + ((sprite.getXSubpixel() >> 8) & 0xFF) + sprite.getXSpeed();
		int predictedX = xTotal >> 8;

		int minX = camera.getMinX();
		int maxX = camera.getMaxX();
		int maxY = Math.max(camera.getMaxY(), camera.getMaxYTarget());
		if (sprite.isCpuControlled() && sprite.getCpuController() != null) {
			minX = sprite.getCpuController().getMinXBound(minX);
			maxX = sprite.getCpuController().getMaxXBound(maxX);
			maxY = sprite.getCpuController().getMaxYBound(maxY);
		}

		int leftBoundary = minX + LEFT_OFFSET;
		int rightBoundary = maxX + SCREEN_WIDTH - SONIC_WIDTH;
		if (!gameState().isBossFightActive()) {
			rightBoundary += RIGHT_EXTRA;
		}

		// ROM comparison: bhi.s for left (<), bls.s for right (>=)
		if (predictedX < leftBoundary) {
			sprite.setCentreX((short) leftBoundary);
			sprite.setXSpeed((short) 0);
			sprite.setGSpeed((short) 0);
		} else if (predictedX >= rightBoundary) {
			sprite.setCentreX((short) rightBoundary);
			sprite.setXSpeed((short) 0);
			sprite.setGSpeed((short) 0);
		}

		// ROM fixBugs: Use max of current maxY and target to prevent premature death
		// while camera boundary is still easing down. Without this fix, falling
		// faster than the camera can adjust its maxY limit causes death.
		// See s2.asm:36913-36922 (Sonic_Boundary_CheckBottom)
		// ROM: When Level_started_flag is clear, boundary death is suppressed
		// (camera boundaries may not reflect actual level extents during intro).
		if (camera.isLevelStarted()) {
			short effectiveMaxY = (short) maxY;
			if (sprite.getY() > effectiveMaxY + 224) {
				if (sprite.isCpuControlled() && sprite.getCpuController() != null) {
					sprite.getCpuController().despawn();
				} else {
					// ROM: Sonic_LevelBound checks for zone-specific intercepts
					// (e.g. SBZ2 fall -> SBZ3 transition) before applying death.
					LevelEventProvider levelEvents = GameModuleRegistry.getCurrent() != null
							? GameModuleRegistry.getCurrent().getLevelEventProvider() : null;
					if (levelEvents == null || !levelEvents.interceptPitDeath(sprite)) {
						sprite.applyPitDeath();
					}
				}
			}
		}
	}

	/** AnglePos: Ground terrain collision (s2.asm:42534) */
	private void doAnglePos() {
		PhysicsFeatureSet featureSet = sprite.getPhysicsFeatureSet();
		int positiveThreshold = (featureSet != null && featureSet.fixedAnglePosThreshold())
				? 14
				: Math.min(getSpeedForThreshold() + 4, 14);
		collisionSystem().resolveGroundAttachment(sprite, positiveThreshold, this::hasObjectSupport);
	}

	/** Sonic_SlopeRepel: Slip/fall check (s2.asm:37432) */
	private void doSlopeRepel() {
		if (sprite.isStickToConvex()) return;
		// Object tops should not trigger terrain slope slip logic.
		// If this runs while standing on an object, it can incorrectly flip
		// the player to airborne and drop jump inputs for that frame.
		if (sprite.isOnObject() || collisionSystem().hasObjectSupport(sprite)) return;

		int moveLock = sprite.getMoveLockTimer();
		if (moveLock > 0) {
			sprite.setMoveLockTimer(moveLock - 1);
			return;
		}

		if (isOnFlatGround()) return;
		if (Math.abs(sprite.getGSpeed()) >= SLOPE_REPEL_MIN_SPEED) return;

		sprite.setGSpeed((short) 0);
		sprite.setAir(true);
		sprite.setMoveLockTimer(MOVE_LOCK_FRAMES);
	}

	/** Sonic_DoLevelCollision: Full airborne collision (s2.asm:37540) */
	private void doLevelCollision() {
		collisionSystem().resolveAirCollision(sprite, this::calculateLanding);
	}

	/** Obj01_CheckWallsOnGround: Ground wall collision (s2.asm:36486) */
	private void doWallCollisionGround() {
		if (collisionSystem() != null) {
			collisionSystem().resolveGroundWallCollision(sprite);
			return;
		}
		// S1 roll tunnels: suppress push sensor wall check to prevent false
		// detections against the narrow tunnel walls. S1's ROM has no separate
		// ground wall check (01 Sonic.asm: Sonic_MdNormal has no CalcRoomInFront).
		if (sprite.isTunnelMode()) {
			return;
		}
		// stick_to_convex: Sonic is on a convex loop surface (e.g. Running Disc in SBZ).
		// S1's ROM has no CalcRoomInFront, so when stick_to_convex is active the wall
		// check must be suppressed to prevent push sensors from detecting the disc's
		// curved terrain as walls — which zeros gSpeed and disrupts traversal.
		// In S2/S3K, objects that set stick_to_convex also set tunnelMode (caught above).
		if (sprite.isStickToConvex()) {
			return;
		}
		Sensor[] pushSensors = sprite.getPushSensors();
		if (pushSensors == null) return;

		int angle = sprite.getAngle() & 0xFF;
		short gSpeed = sprite.getGSpeed();

		// ROM s2.asm:36487-36492 - Skip if angle is steep or not moving
		// addi.b #$40,d0 / bmi.s return - skip if (angle + 0x40) as signed byte is negative
		// ROM uses BYTE arithmetic which wraps at 256, then checks N flag (bit 7 of BYTE result)
		// tst.w inertia(a0) / beq.s return - skip if gSpeed == 0
		int angleCheck = (angle + ANGLE_WALL_OFFSET) & 0xFF;  // Byte arithmetic with wrap
		if ((angleCheck & ANGLE_WALL_MASK) != 0 || gSpeed == 0) {
			return;
		}

		// ROM: bmi.s Obj01_CheckWallsOnGround_Left - select sensor based on gSpeed direction
		int sensorIndex = gSpeed >= 0 ? 1 : 0;
		Sensor sensor = pushSensors[sensorIndex];

		// ROM s2.asm:43480-43491 (CalcRoomInFront): Velocity prediction
		// The ROM scans at PREDICTED position (current + velocity), not current position.
		// This is critical for the velocity adjustment to work correctly:
		//   predicted_pos = current_pos + velocity
		//   distance = how far predicted_pos is inside wall
		//   adjusted_velocity = velocity + distance (cancels over-penetration)
		//   final_pos = current_pos + adjusted_velocity = exactly at wall
		//
		// ROM uses the FULL position (including subpixels) for prediction:
		//   d3 = x_pos (32-bit: pixel.subpixel)
		//   d1 = x_vel << 8 (shift velocity into position format)
		//   d3 += d1 (predicted full position)
		//   swap d3 (get predicted pixel)
		//
		// Our 8-bit subpixel equivalent: predicted_pixel_delta = (subpixel + velocity) >> 8
		// This accounts for accumulated subpixels that may cause a pixel boundary crossing.
		short predictedDx = (short) (((sprite.getXSubpixel() & 0xFF) + sprite.getXSpeed()) >> 8);
		short predictedDy = (short) (((sprite.getYSubpixel() & 0xFF) + sprite.getYSpeed()) >> 8);

		// ROM: CalcRoomInFront (s2.asm:43517-43519) adds +8 to Y only when (rotatedAngle & 0x38) == 0
		// The angle is ROTATED before this check: +0x40 when moving left, -0x40 (0xC0) when moving right.
		// This must be calculated FRESH each frame, not use the stale sensor offset.
		// The sensor stores a Y offset that only updates when setAir/setGroundMode is called,
		// which doesn't happen when the angle changes on curved terrain.
		int wallRotation = (gSpeed < 0) ? 0x40 : 0xC0;  // +0x40 for left, -0x40 (0xC0) for right
		int wallRotatedAngle = (angle + wallRotation) & 0xFF;
		// Calculate dynamic offset: +8 only when (rotatedAngle & 0x38) == 0
		short dynamicYOffset = (short) (((wallRotatedAngle & 0x38) == 0) ? 8 : 0);

		// ROM doesn't use sensor active state - it checks gSpeed != 0 directly (already done above).
		// Our sensor active state is updated at end of frame, so may be stale here on first frame
		// of movement from standstill. Temporarily enable sensor to match ROM behavior.
		boolean wasActive = sensor.isActive();
		sensor.setActive(true);
		byte savedSensorY = sensor.getY();
		sensor.setOffset(sensor.getX(), (byte) 0);
		SensorResult result = sensor.scan(predictedDx, (short)(predictedDy + dynamicYOffset));
		sensor.setOffset(sensor.getX(), savedSensorY);
		sensor.setActive(wasActive);

		if (result == null || result.distance() >= 0) {
			return;
		}

		// ROM s2.asm:36503-36527: Wall collision response
		// ROM dispatches based on mode from ROTATED angle to handle velocity adjustment.
		// The angle was rotated by +/-0x40 based on direction (lines 36490-36497):
		//   gSpeed < 0 (left):  rotatedAngle = angle + 0x40
		//   gSpeed > 0 (right): rotatedAngle = angle - 0x40 (= angle + 0xC0)
		// Then mode = (rotatedAngle + 0x20) & 0xC0 (lines 36504-36505)
		//
		// Mode dispatch:
		//   Mode 0x00 (floor):     add.w d1,y_vel    - adjust Y, NO pushing, NO inertia=0
		//   Mode 0x40 (left wall): sub.w d1,x_vel    - adjust X + pushing + inertia=0
		//   Mode 0x80 (ceiling):   sub.w d1,y_vel    - adjust Y, NO pushing, NO inertia=0
		//   Mode 0xC0 (right wall): add.w d1,x_vel   - adjust X + pushing + inertia=0
		// ROM: asl.w #8,d1 — Java's byte-to-int sign-extension matches ROM's 16-bit
		// word behavior: negative distances (penetration) correctly produce negative adjustments.
		// The subsequent cast to short at usage sites truncates back to 16-bit range.
		int velocityAdjustment = result.distance() << 8;

		// Calculate rotated angle based on gSpeed direction (ROM s2.asm:36490-36497)
		int rotation = (gSpeed < 0) ? 0x40 : 0xC0;  // +0x40 for left, -0x40 (0xC0) for right
		int rotatedAngle = (angle + rotation) & 0xFF;

		// Calculate mode from rotated angle (ROM s2.asm:36504-36505)
		int mode = (rotatedAngle + 0x20) & 0xC0;

		switch (mode) {
			case 0x00:  // Floor mode: adjust Y velocity only
				// ROM s2.asm:36527 (loc_1A6BA) - add.w d1,y_vel
				sprite.setYSpeed((short) (sprite.getYSpeed() + velocityAdjustment));
				// NO gSpeed=0, NO pushing for floor mode
				break;

			case 0x40:  // Left wall mode: adjust X velocity, zero gSpeed, set pushing
				// ROM s2.asm:36521 (loc_1A6A8) - sub.w d1,x_vel
				sprite.setXSpeed((short) (sprite.getXSpeed() - velocityAdjustment));
				// ROM s2.asm:36522-36524 - bset pushing / move.w #0,inertia
				sprite.setGSpeed((short) 0);
				sprite.setPushing(true);
				break;

			case 0x80:  // Ceiling mode: adjust Y velocity only
				// ROM s2.asm:36517 (loc_1A6A2) - sub.w d1,y_vel
				sprite.setYSpeed((short) (sprite.getYSpeed() - velocityAdjustment));
				// NO gSpeed=0, NO pushing for ceiling mode
				break;

			case 0xC0:  // Right wall mode: adjust X velocity, zero gSpeed, set pushing
				// ROM s2.asm:36511 - add.w d1,x_vel
				sprite.setXSpeed((short) (sprite.getXSpeed() + velocityAdjustment));
				// ROM s2.asm:36512-36513 - bset pushing / move.w #0,inertia
				sprite.setGSpeed((short) 0);
				sprite.setPushing(true);
				break;
		}
	}

	// ========================================
	// LANDING
	// ========================================

	/** Sonic_ResetOnFloor: Clear landing-related flags (s2.asm:37744) */
	private void resetOnFloor() {
		// Don't reset states if player is controlled by an object (e.g., LauncherSpring).
		// The controlling object manages these states directly.
		if (sprite.isObjectControlled()) {
			return;
		}

		if (sprite.getRolling() && !sprite.getPinballMode()) {
			sprite.setRolling(false);
			sprite.setY((short) (sprite.getY() - sprite.getRollHeightAdjustment()));
		}
		sprite.setPinballMode(false);
		sprite.setAir(false);
		sprite.setPushing(false);
		sprite.setRollingJump(false);
		sprite.setJumping(false);
		// ROM: s2.asm:37769-37771 - reset flip/tumble state on landing
		sprite.setFlipAngle(0);
		sprite.setFlipTurned(false);
		sprite.setFlipsRemaining(0);
		// ROM: s2.asm:37772 - reset look delay counter on landing
		sprite.setLookDelayCounter((short) 0);
	}

	/** Landing gSpeed calculation (s2.asm:37584) */
	private void calculateLanding(AbstractPlayableSprite sprite) {
		// ROM: Sonic_HurtStop — when landing from hurt state, zero all velocity.
		// Must check before resetOnFloor() which clears the hurt flag via setAir(false).
		boolean wasHurt = sprite.isHurt();
		// Save doubleJumpFlag BEFORE resetOnFloor() clears it via setAir(false).
		// ROM (s3.asm:21849-21859) tests the flag before clearing.
		int savedDoubleJumpFlag = sprite.getDoubleJumpFlag();
		resetOnFloor();
		if (wasHurt) {
			sprite.setGSpeed((short) 0);
			sprite.setXSpeed((short) 0);
			sprite.setYSpeed((short) 0);
			return;
		}

		short ySpeed = sprite.getYSpeed();
		short xSpeed = sprite.getXSpeed();
		int angle = sprite.getAngle() & 0xFF;

		// ROM processes all landings through angle classification, no early return for ySpeed <= 0
		boolean isSteep = isSteepAngle(angle);

		if (isSteep) {
			// Steep angles: gSpeed from signed Y velocity
			// ROM (s2.asm:37608-37612):
			//   move.w y_vel(a0),inertia(a0)  ; gSpeed = SIGNED y_vel
			//   tst.b  d3
			//   bpl.s  return_1AF8A
			//   neg.w  inertia(a0)            ; Negate if angle >= 0x80
			// Cap check uses signed comparison (ble = branch if less or equal, signed)
			sprite.setXSpeed((short) 0);
			short gSpeed = ySpeed;
			if (gSpeed > YSPEED_LANDING_CAP) {
				gSpeed = YSPEED_LANDING_CAP;
				// ROM (s2.asm:37605-37608) writes capped value back to y_vel:
				//   cmpi.w  #$FC0,y_vel(a0)
				//   ble.s   loc_1AF7C
				//   move.w  #$FC0,y_vel(a0)
				sprite.setYSpeed(gSpeed);
			}
			if ((angle & 0x80) != 0) gSpeed = (short) -gSpeed;
			sprite.setGSpeed(gSpeed);
			// ROM does NOT zero y_vel for steep landings (s2.asm:37612-37615 just returns)
		} else {
			boolean isFlat = isFlatAngle(angle);
			if (isFlat) {
				// Flat angles: gSpeed from X velocity
				sprite.setGSpeed(xSpeed);
				sprite.setYSpeed((short) 0);
			} else {
				// Moderate angles: gSpeed from Y velocity / 2
				// ROM (s2.asm:37592): asr y_vel(a0) - arithmetic shift right preserves sign
				// Then (s2.asm:37609-37612): gSpeed = y_vel, negate if angle >= 0x80
				short halfYSpeed = (short) (ySpeed >> 1);
				short gSpeed = halfYSpeed;
				if ((angle & 0x80) != 0) gSpeed = (short) -gSpeed;
				sprite.setGSpeed(gSpeed);
				// ROM does NOT zero y_vel for moderate angles - it leaves the halved value
				sprite.setYSpeed(halfYSpeed);
			}
		}

		// Bubble shield bounce check (s3.asm:21849-21859 Player_TouchFloor tail)
		// ROM: Only Sonic (character_id 0) can trigger this — Knuckles/Tails have
		// separate jump code that never sets doubleJumpFlag via bubbleShieldBounce.
		PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
		if (fs != null && fs.elementalShieldsEnabled() && savedDoubleJumpFlag != 0
				&& sprite.getSecondaryAbility() == SecondaryAbility.INSTA_SHIELD) {
			if (sprite.hasShield() && sprite.getShieldType() == ShieldType.BUBBLE) {
				applyBubbleShieldBounce(sprite);
			}
			// Flag already cleared by resetOnFloor→setAir(false), no extra clear needed
		}
	}

	/**
	 * BubbleShield_Bounce: Re-launch player perpendicular to surface (s3.asm:21866-21900).
	 * On flat ground (angle 0x00): bounces straight up at 0x780 velocity.
	 * On slopes: bounce direction follows surface normal.
	 * Underwater: reduced velocity (0x400).
	 */
	private void applyBubbleShieldBounce(AbstractPlayableSprite sprite) {
		int velocity = sprite.isInWater() ? 0x400 : 0x780;
		int angle = sprite.getAngle() & 0xFF;
		// Rotate 90° CCW to get surface normal direction (s3.asm:21873: subi.b #$40,d0)
		int rotated = (angle - 0x40) & 0xFF;
		int sin = TrigLookupTable.sinHex(rotated);
		int cos = TrigLookupTable.cosHex(rotated);
		sprite.setXSpeed((short) (sprite.getXSpeed() + ((cos * velocity) >> 8)));
		sprite.setYSpeed((short) (sprite.getYSpeed() + ((sin * velocity) >> 8)));
		// Re-launch into air (s3.asm:21885-21892)
		sprite.setAir(true);
		sprite.setJumping(true);
		sprite.setPushing(false);
		if (!sprite.getRolling()) {
			sprite.setRolling(true);
			sprite.setY((short) (sprite.getY() + sprite.getRollHeightAdjustment()));
		}
		audioManager.playSfx(GameSound.BUBBLE_ATTACK);
		var shield = sprite.getShieldObject();
		if (shield != null) shield.onAbilityActivated(2);
	}

	// ========================================
	// ANGLE CLASSIFICATION HELPERS
	// ========================================

	/** Steep: angles near walls (0x20-0x5F, 0xA0-0xDF) */
	private boolean isSteepAngle(int angle) {
		return ((angle + ANGLE_STEEP_OFFSET) & ANGLE_STEEP_MASK) != 0;
	}

	/** Flat: angles near horizontal (0x00-0x0F, 0xF0-0xFF) */
	private boolean isFlatAngle(int angle) {
		return ((angle + ANGLE_FLAT_OFFSET) & ANGLE_FLAT_MASK) == 0;
	}

	/** On steep surface: angles near vertical (skip slope physics) */
	private boolean isOnSteepSurface(int angle) {
		return ((angle + 0x60) & 0xFF) >= 0xC0;
	}

	/** On flat ground for crouch/skid checks */
	private boolean isOnFlatGround() {
		int angle = sprite.getAngle() & 0xFF;
		return ((angle + ANGLE_SLOPE_OFFSET) & ANGLE_SLOPE_MASK) == 0;
	}

	// ========================================
	// UTILITY HELPERS
	// ========================================

	private void storeInputState(boolean up, boolean down, boolean left, boolean right, boolean jump) {
		inputUp = up;
		inputDown = down;
		inputLeft = left;
		inputRight = right;
		inputJump = sprite.isHurt() ? false : jump;
		boolean suppressJumpPress = sprite.consumeSuppressNextJumpPress();
		inputJumpPress = ((jump && !jumpPrevious) && !suppressJumpPress) || sprite.isForcedJumpPress();
		sprite.setForcedJumpPress(false); // consume one-shot signal
		jumpPrevious = jump;
	}

	private void handleDebugMovement(boolean up, boolean down, boolean left, boolean right, boolean speedUp,
			boolean slowDown) {
		double multiplier = 1.0;
		if (speedUp) {
			multiplier *= 2.0;
		}
		if (slowDown) {
			multiplier *= 0.5;
		}
		int moveSpeed = (int) Math.max(1, Math.round(DEBUG_MOVE_SPEED * multiplier));

		if (left) sprite.setX((short) (sprite.getX() - moveSpeed));
		if (right) sprite.setX((short) (sprite.getX() + moveSpeed));
		if (up) sprite.setY((short) (sprite.getY() - moveSpeed));
		if (down) sprite.setY((short) (sprite.getY() + moveSpeed));
	}

	private void handleTestKey(boolean testKey) {
		if (testKey && !testKeyPressed) {
			testKeyPressed = true;
			sprite.setGroundMode(switch (sprite.getGroundMode()) {
				case GROUND -> GroundMode.RIGHTWALL;
				case RIGHTWALL -> GroundMode.CEILING;
				case CEILING -> GroundMode.LEFTWALL;
				case LEFTWALL -> GroundMode.GROUND;
			});
		}
		if (!testKey) testKeyPressed = false;
	}

	private void updatePushingOnDirectionChange(boolean left, boolean right) {
		if (left && !right && sprite.getDirection() == Direction.RIGHT) {
			sprite.setPushing(false);
		} else if (right && !left && sprite.getDirection() == Direction.LEFT) {
			sprite.setPushing(false);
		}
	}

	private void handleSkid() {
		if (!sprite.getSkidding()) sprite.setSkidding(true);
		audioManager.playSfx(GameSound.SKID);

		int dustTimer = sprite.getSkidDustTimer() - 1;
		if (dustTimer < 0) {
			dustTimer = 3;
			SkidDustObjectInstance.spawn(sprite);
		}
		sprite.setSkidDustTimer(dustTimer);
	}

	private void updateFacingDirection() {
		if (inputLeft && !inputRight) {
			sprite.setDirection(!sprite.getAir() && sprite.getGSpeed() > 0 ? Direction.RIGHT : Direction.LEFT);
		} else if (inputRight && !inputLeft) {
			sprite.setDirection(!sprite.getAir() && sprite.getGSpeed() < 0 ? Direction.LEFT : Direction.RIGHT);
		}
	}

	private void updateCrouchState() {
		// S3K: allow ducking while moving at speeds below the roll threshold.
		// ROM: sonic3k.asm:23223-23240 (SonicKnux_Roll) — down pressed + |gSpeed| < $100
		// + not left/right + not on object → enter duck animation.
		PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
		short movingThreshold = (fs != null) ? fs.movingCrouchThreshold() : 0;
		if (movingThreshold > 0 && inputDown && !inputLeft && !inputRight
				&& !sprite.getAir() && !sprite.getRolling() && !sprite.getSpindash()
				&& Math.abs(sprite.getGSpeed()) < movingThreshold
				&& !sprite.isOnObject()) {
			sprite.setCrouching(true);
			sprite.setBalanceState(0);
			return;
		}

		// ROM s2.asm:36237-36245: Balance/crouch/lookup checks happen when standing still
		// on flat ground (angle + 0x20 & 0xC0 == 0) and not moving (inertia == 0)
		boolean standingStill = sprite.getGSpeed() == 0 && isOnFlatGround()
				&& !sprite.getAir() && !sprite.getRolling() && !sprite.getSpindash();

		// Update balance state (checks for ledge edges)
		// ROM: Balance check happens before crouch/lookup in Obj01_LookUpDown
		if (standingStill && !inputLeft && !inputRight) {
			updateBalanceState();
		} else {
			sprite.setBalanceState(0);
		}

		// Crouch check - only if not balancing
		// ROM: You can't crouch while balancing (balance animation takes priority)
		boolean crouching = inputDown && !inputLeft && !inputRight
				&& standingStill && !sprite.isBalancing();
		sprite.setCrouching(crouching);
	}

    private void applyUpwardVelocityCap() {
        if (sprite.getAir() && !jumpPressed && !sprite.getPinballMode()) {
            if (sprite.getYSpeed() < UPWARD_VELOCITY_CAP) {
                sprite.setYSpeed((short) UPWARD_VELOCITY_CAP);
            }
        }
    }

	private void applyDeathMovement() {
		sprite.setYSpeed((short) (sprite.getYSpeed() + sprite.getGravity()));
		sprite.setGSpeed((short) 0);
		sprite.setXSpeed((short) 0);

		Camera camera = camera();
		if (camera != null && sprite.getY() > camera.getY() + camera.getHeight() + 256) {
			sprite.startDeathCountdown();
		}

		if (sprite.tickDeathCountdown()) {
			if (sprite.isCpuControlled() && sprite.getCpuController() != null) {
				// CPU-controlled sprites (Tails) despawn and respawn near the main player
				// instead of causing a level reset
				sprite.getCpuController().despawn();
			} else {
				gameState().loseLife();
				levelManager().requestRespawn();
			}
		}
	}

	/**
	 * Accelerate left (negative direction) with per-game speed capping.
	 *
	 * S1 (inputAlwaysCapsGroundSpeed=true): Always clamps to -max, even if
	 * speed was already beyond max from slopes/springs.
	 * ROM: s1disasm/_incObj/01 Sonic.asm:507-512 — unconditional clamp.
	 *
	 * S2/S3K (inputAlwaysCapsGroundSpeed=false): Preserves speeds above max.
	 * ROM: s2.asm:36547-36556 — undo accel if was already >= max.
	 */
	private short accelerateLeft(short gSpeed, short runAccel, short max) {
		PhysicsFeatureSet featureSet = sprite.getPhysicsFeatureSet();
		boolean alwaysCap = featureSet != null && featureSet.inputAlwaysCapsGroundSpeed();

		if (alwaysCap) {
			// S1: sub, then unconditional clamp
			gSpeed -= runAccel;
			if (gSpeed < -max) gSpeed = (short) -max;
		} else {
			// S2/S3K: only accelerate if below max — preserve existing high speed
			if (gSpeed > -max) {
				gSpeed -= runAccel;
				if (gSpeed < -max) gSpeed = (short) -max;
			}
		}
		return gSpeed;
	}

	/**
	 * Accelerate right (positive direction) with per-game speed capping.
	 *
	 * S1 (inputAlwaysCapsGroundSpeed=true): Always clamps to max.
	 * ROM: s1disasm/_incObj/01 Sonic.asm:555-558 — unconditional clamp.
	 *
	 * S2/S3K (inputAlwaysCapsGroundSpeed=false): Preserves speeds above max.
	 * ROM: s2.asm:36610-36616 — undo accel if was already >= max.
	 */
	private short accelerateRight(short gSpeed, short runAccel, short max) {
		PhysicsFeatureSet featureSet = sprite.getPhysicsFeatureSet();
		boolean alwaysCap = featureSet != null && featureSet.inputAlwaysCapsGroundSpeed();

		if (alwaysCap) {
			// S1: add, then unconditional clamp
			gSpeed += runAccel;
			if (gSpeed > max) gSpeed = max;
		} else {
			// S2/S3K: only accelerate if below max — preserve existing high speed
			if (gSpeed < max) {
				gSpeed += runAccel;
				if (gSpeed > max) gSpeed = max;
			}
		}
		return gSpeed;
	}

	private short applyFriction(short speed, short friction) {
		if (speed > 0) {
			speed -= friction;
			if (speed < 0) speed = 0;
		} else if (speed < 0) {
			speed += friction;
			if (speed > 0) speed = 0;
		}
		return speed;
	}

	private void calculateXYFromGSpeed() {
		short gSpeed = sprite.getGSpeed();
		int hexAngle = sprite.getAngle() & 0xFF;
		sprite.setXSpeed((short) ((gSpeed * TrigLookupTable.cosHex(hexAngle)) >> 8));
		sprite.setYSpeed((short) ((gSpeed * TrigLookupTable.sinHex(hexAngle)) >> 8));
	}

	// Legacy test hooks now delegate to the authoritative CollisionSystem path.
	private void updateGroundMode() {
		int angle = sprite.getAngle() & 0xFF;
		boolean angleIsNegative = angle >= 0x80;
		int sumWith20 = (angle + 0x20) & 0xFF;
		boolean sumIsNegative = sumWith20 >= 0x80;
		int result = (angleIsNegative == sumIsNegative) ? (angle + 0x1F) & 0xFF : sumWith20;
		int modeBits = result & 0xC0;

		GroundMode newMode = switch (modeBits) {
			case 0x00 -> GroundMode.GROUND;
			case 0x40 -> GroundMode.LEFTWALL;
			case 0x80 -> GroundMode.CEILING;
			default -> GroundMode.RIGHTWALL;
		};

		if (newMode != sprite.getGroundMode()) {
			sprite.setGroundMode(newMode);
		}
	}

	private void doTerrainCollisionAir(SensorResult[] ignored) {
		SensorResult lowestResult = null;
		for (SensorResult result : ignored) {
			if (result != null && (lowestResult == null || result.distance() < lowestResult.distance())) {
				lowestResult = result;
			}
		}
		if (sprite.getYSpeed() < 0 || lowestResult == null || lowestResult.distance() >= 0) {
			return;
		}

		short ySpeedPixels = (short) (sprite.getYSpeed() >> 8);
		short threshold = (short) (-(ySpeedPixels + 8));
		boolean canLand = (ignored[0] != null && ignored[0].distance() >= threshold)
				|| (ignored[1] != null && ignored[1].distance() >= threshold);
		if (!canLand) {
			return;
		}

		// ROM-accurate: collision adjustment uses add.w/sub.w on pixel position,
		// preserving subpixel fraction. Using shiftX/shiftY instead of setX/setY.
		byte distance = lowestResult.distance();
		switch (lowestResult.direction()) {
			case UP -> sprite.shiftY(-distance);
			case DOWN -> sprite.shiftY(distance);
			case LEFT -> sprite.shiftX(-distance);
			case RIGHT -> sprite.shiftX(distance);
		}

		if ((lowestResult.angle() & 0x01) != 0) {
			sprite.setAngle((byte) 0x00);
		} else {
			sprite.setAngle(lowestResult.angle());
		}
		calculateLanding(sprite);
		updateGroundMode();
	}

	/**
	 * ROM-accurate speed threshold for ground attachment (s2.asm:42727, 42794, 42861).
	 * Uses X velocity for GROUND/CEILING modes, Y velocity for wall modes.
	 *
	 * ROM uses mvabs.b which takes the HIGH BYTE (integer pixels) of velocity:
	 *   mvabs.b  x_vel(a0),d0  ; for ceiling
	 *   mvabs.b  y_vel(a0),d0  ; for walls
	 *
	 * No fallback to gSpeed - ROM uses raw velocity bytes directly.
	 */
	private int getSpeedForThreshold() {
		GroundMode groundMode = sprite.getGroundMode();
		// ROM uses x_vel for GROUND/CEILING, y_vel for walls (mvabs.b instruction)
		return (groundMode == GroundMode.LEFTWALL || groundMode == GroundMode.RIGHTWALL)
				? Math.abs(sprite.getYSpeed() >> 8)
				: Math.abs(sprite.getXSpeed() >> 8);
	}

	private boolean hasEnoughHeadroom(int hexAngle) {
		return collisionSystem().hasEnoughHeadroom(sprite, hexAngle);
	}

	private boolean hasObjectSupport() {
		return collisionSystem().hasObjectSupport(sprite);
	}

	private void clearRidingObject() {
		collisionSystem().clearRidingObject(sprite);
	}

	private int getDuckAnimId() {
		SpriteAnimationProfile profile = sprite.getAnimationProfile();
		if (profile instanceof ScriptedVelocityAnimationProfile velocityProfile) {
			return velocityProfile.getDuckAnimId();
		}
		return -1;
	}

	private void setSpindashAnimation() {
		SpriteAnimationProfile profile = sprite.getAnimationProfile();
		if (profile instanceof ScriptedVelocityAnimationProfile velocityProfile) {
			sprite.setAnimationId(velocityProfile.getSpindashAnimId());
			sprite.setAnimationFrameIndex(0);
			sprite.setAnimationTick(0);
		}
	}

	private void setRollAnimation() {
		SpriteAnimationProfile profile = sprite.getAnimationProfile();
		if (profile instanceof ScriptedVelocityAnimationProfile velocityProfile) {
			sprite.setAnimationId(velocityProfile.getRollAnimId());
			sprite.setAnimationFrameIndex(0);
			sprite.setAnimationTick(0);
		}
	}

	/** Common pattern: boundary check + sensor update + angle positioning */
	private void doLevelBoundaryAndAnglePos() {
		doLevelBoundary();
		short originalX = sprite.getX();
		short originalY = sprite.getY();
		sprite.updateSensors(originalX, originalY);
		doAnglePos();
	}

	/** Sensor update + angle positioning */
	private void doAnglePosWithSensorUpdate(short originalX, short originalY) {
		sprite.updateSensors(originalX, originalY);
		doAnglePos();
	}

	// ========================================
	// BALANCE DETECTION (ROM s2.asm:36246-36373)
	// ========================================

	/**
	 * ROM-accurate balance detection for ledge edges.
	 * Sets sprite balance state (0 = not balancing, 1-4 = balance animation level).
	 *
	 * This implements Obj01_LookUpDown balance checks from s2.asm:36246-36373.
	 * Balance is checked when:
	 * - Sprite is standing still (gSpeed == 0)
	 * - Sprite is on flat ground (angle near 0)
	 * - Sprite is not pressing any direction keys
	 *
	 * Two types of edge detection:
	 * 1. Standing on object edge (status.player.on_object set)
	 * 2. Standing on terrain edge (ChkFloorEdge distance >= 12)
	 */
	private void updateBalanceState() {
		// Reset balance state first
		sprite.setBalanceState(0);

		// Balance only applies when standing still on flat ground
		if (sprite.getGSpeed() != 0 || !isOnFlatGround()) {
			return;
		}

		// Check if standing on an object - check object edge first
		if (sprite.isOnObject()) {
			checkObjectEdgeBalance();
			return;
		}

		// Otherwise check terrain edge balance
		checkTerrainEdgeBalance();
	}

	/**
	 * Check balance when standing on an object (platform, monitor, etc.).
	 *
	 * S2/S3K (extendedEdgeBalance): ROM s2.asm:36246-36318
	 * - d2 = (width * 2) - 2, left threshold < 2, right threshold >= d2
	 * - 4 balance states with precarious/facing-away checks
	 *
	 * S1 (!extendedEdgeBalance): ROM s1disasm/_incObj/01 Sonic.asm:340-351
	 * - d2 = (width * 2) - 4, left threshold < 4, right threshold >= d2
	 * - Single balance state, always forces facing toward edge
	 */
	private void checkObjectEdgeBalance() {
		// Get the object the sprite is standing on
		var objectManager = levelManager().getObjectManager();
		if (objectManager == null) {
			return;
		}

		var ridingObject = objectManager.getRidingObject(sprite);
		if (ridingObject == null) {
			return;
		}

		// Object must be a SolidObjectProvider to get width for balance calculation
		if (!(ridingObject instanceof SolidObjectProvider provider)) {
			return;
		}

		// ROM: Check if object allows balancing (status.npc.no_balancing bit)
		// Some objects (like spinning platforms) disable balancing
		// We skip this check for now as most objects allow balancing

		// For multi-piece solid objects (e.g., CPZ Staircase), use the piece-specific
		// position and params rather than the overall object's base position.
		// In the ROM, each piece is effectively a separate SST with its own x_pos and
		// width_pixels, so the balance check naturally uses piece-local coordinates.
		int objectX;
		SolidObjectParams params;
		int ridingPieceIndex = objectManager.getRidingPieceIndex(sprite);
		if (ridingPieceIndex >= 0 && ridingObject instanceof MultiPieceSolidProvider multiPiece) {
			objectX = multiPiece.getPieceX(ridingPieceIndex);
			params = multiPiece.getPieceParams(ridingPieceIndex);
		} else {
			objectX = ridingObject.getX();
			params = provider.getSolidParams();
		}
		if (params == null) {
			return;
		}

		PhysicsFeatureSet featureSet = sprite.getPhysicsFeatureSet();
		boolean extended = featureSet != null && featureSet.extendedEdgeBalance();

		int objectWidth = params.halfWidth(); // Half-width (radius)
		int playerX = sprite.getCentreX();

		// ROM formula: d1 = player_x + width - object_x
		// This gives player position relative to left edge of object
		int d1 = playerX + objectWidth - objectX;

		// S1: d2 = (width * 2) - 4, left threshold 4 (s1.asm:344,347)
		// S2: d2 = (width * 2) - 2, left threshold 2 (s2.asm:36268)
		int leftThreshold = extended ? 2 : 4;
		int d2 = (objectWidth * 2) - (extended ? 2 : 4);

		boolean facingRight = sprite.getDirection() == Direction.RIGHT;

		if (d1 < leftThreshold) {
			// On left edge of object
			if (extended) {
				// S2/S3K: 4-state balance with precarious check
				boolean precarious = d1 < -4;
				boolean facingTowardEdge = !facingRight;
				int balanceState = facingTowardEdge ? (precarious ? 2 : 1) : (precarious ? 4 : 3);
				if (balanceState == 4) {
					sprite.setDirection(Direction.LEFT);
				}
				sprite.setBalanceState(balanceState);
			} else {
				// S1: single state, force facing toward edge (bset #0 → face left)
				// ROM s1.asm:370-371: bset #0,obStatus(a0)
				sprite.setBalanceState(1);
				sprite.setDirection(Direction.LEFT);
			}
		} else if (d1 >= d2) {
			// On right edge of object
			if (extended) {
				// S2/S3K: 4-state balance with precarious check
				boolean precarious = d1 >= d2 + 6;
				boolean facingTowardEdge = facingRight;
				int balanceState = facingTowardEdge ? (precarious ? 2 : 1) : (precarious ? 4 : 3);
				if (balanceState == 4) {
					sprite.setDirection(Direction.RIGHT);
				}
				sprite.setBalanceState(balanceState);
			} else {
				// S1: single state, force facing toward edge (bclr #0 → face right)
				// ROM s1.asm:361-362: bclr #0,obStatus(a0)
				sprite.setBalanceState(1);
				sprite.setDirection(Direction.RIGHT);
			}
		}
	}

	/**
	 * Check balance when standing on terrain (level floor).
	 *
	 * S2/S3K (extendedEdgeBalance): ROM s2.asm:36322-36373 (Sonic_Balance)
	 * - ChkFloorEdge distance >= 12, next_tilt/tilt == 3
	 * - Secondary probe at ±6px for precarious check → 4 balance states
	 *
	 * S1 (!extendedEdgeBalance): ROM s1disasm/_incObj/01 Sonic.asm:354-375
	 * - ObjFloorDist distance >= 12, objoff_36/objoff_37 == 3
	 * - No precarious check, single balance state, always faces edge
	 */
	private void checkTerrainEdgeBalance() {
		// Use ground sensors to check for floor edge
		Sensor[] groundSensors = sprite.getGroundSensors();
		if (groundSensors == null || groundSensors.length < 2) {
			return;
		}

		// Ground sensors are at center ± 9 pixels (for Sonic)
		// Left sensor (index 0) is at center - 9
		// Right sensor (index 1) is at center + 9
		SensorResult leftResult = groundSensors[0].scan();
		SensorResult rightResult = groundSensors[1].scan();

		int leftDist = (leftResult == null) ? 99 : leftResult.distance();
		int rightDist = (rightResult == null) ? 99 : rightResult.distance();

		// Edge threshold - ROM uses 12 ($C) for both S1 and S2
		final int EDGE_THRESHOLD = 12;

		boolean facingRight = sprite.getDirection() == Direction.RIGHT;

		PhysicsFeatureSet featureSet = sprite.getPhysicsFeatureSet();
		boolean extended = featureSet != null && featureSet.extendedEdgeBalance();

		if (!extended) {
			// S1: ObjFloorDist probes at CENTER X, not at the ±9 side sensors.
			// ROM s1.asm:354-356: jsr (ObjFloorDist).l / cmpi.w #$C,d1
			// ObjFloorDist uses obX(a0) = sprite center X.
			// Left sensor is at center - 9; scanning with dx=+9 probes at center.
			SensorResult centerResult = groundSensors[0].scan((short) 9, (short) 0);
			int centerDist = (centerResult == null) ? 99 : centerResult.distance();

			if (centerDist < EDGE_THRESHOLD) {
				return; // Center still has ground — not on edge yet
			}

			// Center is over edge (distance >= 12). Determine which side using
			// the ±9 sensor angles — ROM checks objoff_36/objoff_37 == 3
			// (FLAGGED_ANGLE for empty tile, set during AnglePos).
			// We approximate: whichever side sensor still has ground indicates
			// the opposite side is the edge.
			if (leftDist < EDGE_THRESHOLD && rightDist >= EDGE_THRESHOLD) {
				// Left sensor on ground, right sensor off → right edge → face right
				// ROM s1.asm:358-362: objoff_36 == 3 → bclr #0,obStatus
				sprite.setBalanceState(1);
				sprite.setDirection(Direction.RIGHT);
			} else if (rightDist < EDGE_THRESHOLD && leftDist >= EDGE_THRESHOLD) {
				// Right sensor on ground, left sensor off → left edge → face left
				// ROM s1.asm:367-371: objoff_37 == 3 → bset #0,obStatus
				sprite.setBalanceState(1);
				sprite.setDirection(Direction.LEFT);
			}
			// If both or neither sensor has ground, don't balance
			// (ROM: neither objoff_36 nor objoff_37 == 3 → branch to Sonic_LookUp)
			return;
		}

		// S2/S3K: Edge detection uses ±9 side sensors as the primary gate
		// (ChkFloorEdge probes at center X, but the distance threshold effectively
		// aligns with the side sensor approach for S2/S3K terrain geometry).

		// Right edge: left sensor has ground, right sensor doesn't
		if (leftDist < EDGE_THRESHOLD && rightDist >= EDGE_THRESHOLD) {
			// S2/S3K: precarious check - scan at center - 6 (dx = +3 from left sensor)
			SensorResult precariousResult = groundSensors[0].scan((short) 3, (short) 0);
			int precariousDist = (precariousResult == null) ? 99 : precariousResult.distance();
			boolean precarious = precariousDist >= EDGE_THRESHOLD;
			setBalanceForEdge(false, facingRight, precarious ? 0 : 6);
			return;
		}

		// Left edge: right sensor has ground, left sensor doesn't
		if (rightDist < EDGE_THRESHOLD && leftDist >= EDGE_THRESHOLD) {
			// S2/S3K: precarious check - scan at center + 6 (dx = -3 from right sensor)
			SensorResult precariousResult = groundSensors[1].scan((short) -3, (short) 0);
			int precariousDist = (precariousResult == null) ? 99 : precariousResult.distance();
			boolean precarious = precariousDist >= EDGE_THRESHOLD;
			setBalanceForEdge(true, facingRight, precarious ? 0 : 6);
		}
	}

	/**
	 * Scan floor at player position with X offset.
	 * Returns distance to floor (positive = gap, negative = inside floor).
	 *
	 * ROM: ChkFloorEdge_Part2 (s2.asm:43677)
	 */
	private int scanFloorEdge(int xOffset) {
		Sensor[] groundSensors = sprite.getGroundSensors();
		if (groundSensors == null || groundSensors.length < 2) {
			return 0;
		}

		// Use GroundSensor if available, otherwise use basic sensor
		Sensor sensor = groundSensors[xOffset >= 0 ? 1 : 0]; // Right or left sensor

		// Temporarily offset the scan position
		boolean wasActive = sensor.isActive();
		sensor.setActive(true);

		// Scan at offset position
		SensorResult result = sensor.scan((short) xOffset, (short) 0);
		sensor.setActive(wasActive);

		if (result == null) {
			return 99; // No floor found = edge
		}

		return result.distance();
	}

	/**
	 * Set balance state based on edge position and facing direction.
	 *
	 * ROM balance animation selection (s2.asm:36284-36318):
	 * - Facing toward edge: Balance (0x06), or Balance2 (0x0C) if closer to edge
	 * - Facing away from edge: Balance3 (0x1D), or Balance4 (0x1E) if closer to edge
	 *
	 * ROM behavior for Balance4: sprite is FLIPPED to face the edge!
	 * - Right edge + facing left + precarious → bclr x_flip → face right (toward edge)
	 * - Left edge + facing right + precarious → bset x_flip → face left (toward edge)
	 *
	 * @param isLeftEdge True if standing on left edge (floor drops to the left)
	 * @param facingRight True if sprite is facing right
	 * @param distanceFromPrecarious Distance threshold for precarious balance (< 6 = precarious)
	 */
	private void setBalanceForEdge(boolean isLeftEdge, boolean facingRight, int distanceFromPrecarious) {
		// Determine if facing toward or away from the edge
		boolean facingTowardEdge = (isLeftEdge && !facingRight) || (!isLeftEdge && facingRight);

		// Determine if precarious (closer to falling)
		boolean precarious = distanceFromPrecarious < 6;

		int balanceState;
		if (facingTowardEdge) {
			// Facing toward edge: Balance or Balance2
			balanceState = precarious ? 2 : 1;
		} else {
			// Facing away from edge: Balance3 or Balance4
			balanceState = precarious ? 4 : 3;

			// ROM: Balance4 flips the sprite to face the edge
			// s2.asm:36299 (right edge): bclr #status.player.x_flip → face right
			// s2.asm:36317 (left edge): bset #status.player.x_flip → face left
			if (precarious) {
				// Flip sprite to face the edge
				if (isLeftEdge) {
					// Left edge → face left (toward edge)
					sprite.setDirection(Direction.LEFT);
				} else {
					// Right edge → face right (toward edge)
					sprite.setDirection(Direction.RIGHT);
				}
			}
		}

		sprite.setBalanceState(balanceState);
	}
}
