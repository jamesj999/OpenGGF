package uk.co.jamesj999.sonic.sprites.managers;

import uk.co.jamesj999.sonic.Control.InputHandler;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.game.CollisionModel;
import uk.co.jamesj999.sonic.game.PhysicsFeatureSet;
import uk.co.jamesj999.sonic.game.sonic3k.objects.AizPlaneIntroInstance;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.game.sonic2.scroll.Sonic2ZoneConstants;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.physics.Direction;
import uk.co.jamesj999.sonic.sprites.SensorConfiguration;
import uk.co.jamesj999.sonic.sprites.Sprite;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;
import uk.co.jamesj999.sonic.sprites.playable.GroundMode;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages collection of available sprites to be provided to renderer and collision manager.
 * 
 * @author james
 * 
 */
public class SpriteManager {
	private final SonicConfigurationService configService = SonicConfigurationService
			.getInstance();

	private static SpriteManager spriteManager;

	private Map<String, Sprite> sprites;

	private static final SensorConfiguration[][] MOVEMENT_MAPPING_ARRAY = createMovementMappingArray();

	private static final int BUCKET_COUNT = RenderPriority.MAX - RenderPriority.MIN + 1;
	@SuppressWarnings("unchecked")
	private final List<Sprite>[] lowPriorityBuckets = new ArrayList[BUCKET_COUNT];
	@SuppressWarnings("unchecked")
	private final List<Sprite>[] highPriorityBuckets = new ArrayList[BUCKET_COUNT];
	private final List<Sprite> nonPlayableSprites = new ArrayList<>();
	private boolean bucketsDirty = true;
	private boolean lastSidekickSuppressed = false;

	private LevelManager levelManager;

	private int upKey;
	private int downKey;
	private int leftKey;
	private int rightKey;
	private int jumpKey;
	private int testKey;
	private int debugModeKey;
	private int superSonicDebugKey;
	private int frameCounter;

	private SpriteManager() {
		sprites = new HashMap<String, Sprite>();
		for (int i = 0; i < BUCKET_COUNT; i++) {
			lowPriorityBuckets[i] = new ArrayList<>();
			highPriorityBuckets[i] = new ArrayList<>();
		}
		upKey = configService.getInt(SonicConfiguration.UP);
		downKey = configService.getInt(SonicConfiguration.DOWN);
		leftKey = configService.getInt(SonicConfiguration.LEFT);
		rightKey = configService.getInt(SonicConfiguration.RIGHT);
		jumpKey = configService.getInt(SonicConfiguration.JUMP);
		testKey = configService.getInt(SonicConfiguration.TEST);
		debugModeKey = configService.getInt(SonicConfiguration.DEBUG_MODE_KEY);
		superSonicDebugKey = configService.getInt(SonicConfiguration.SUPER_SONIC_DEBUG_KEY);
	}

	/**
	 * Adds the given sprite to the SpriteManager. Returns true if we have
	 * overwritten a sprite, false if we are creating a new one.
	 *
	 * @param sprite
	 * @return
	 */
	public boolean addSprite(Sprite sprite) {
		bucketsDirty = true;
		return (sprites.put(sprite.getCode(), sprite) != null);
	}

	/**
	 * Removes the Sprite with provided code from the SpriteManager. Returns
	 * true if a Sprite was removed and false if none could be found.
	 * 
	 * @param code
	 * @return
	 */
	public boolean removeSprite(String code) {
		return removeSprite(getSprite(code));
	}

	/**
	 * Removes all sprites from the manager.
	 * Useful for test cleanup to ensure a clean state.
	 */
	public void clearAllSprites() {
		sprites.clear();
		bucketsDirty = true;
	}

	/**
	 * Resets mutable state without destroying the singleton instance.
	 */
	public void resetState() {
		clearAllSprites();
		levelManager = null;
		frameCounter = 0;
	}

	public Collection<Sprite> getAllSprites() {
		return sprites.values();
	}

	public Sprite getSprite(String code) {
		return sprites.get(code);
	}

	/**
	 * Returns the sidekick sprite (Tails in 2-player or AI mode).
	 * Currently returns null as sidekick AI is not implemented.
	 * <p>
	 * In the original Sonic 2, the sidekick is stored at RAM $FFFFB040
	 * (Sidekick) vs the main character at $FFFFB000 (MainCharacter).
	 *
	 * @return the sidekick sprite, or null if no sidekick is active
	 */
	public AbstractPlayableSprite getSidekick() {
		if (isCpuSidekickSuppressed()) {
			return null;
		}
		for (Sprite sprite : sprites.values()) {
			if (sprite instanceof AbstractPlayableSprite playable && playable.isCpuControlled()) {
				return playable;
			}
		}
		return null;
	}

	public void update(InputHandler handler) {
		frameCounter++;
		// Note: bucketsDirty is already marked in addSprite()/removeSprite(),
		// no need to unconditionally mark dirty every frame
		Collection<Sprite> sprites = getAllSprites();
		boolean up = handler.isKeyDown(upKey);
		boolean down = handler.isKeyDown(downKey);
		boolean left = handler.isKeyDown(leftKey);
		boolean right = handler.isKeyDown(rightKey);
		boolean space = handler.isKeyDown(jumpKey);
		boolean testButton = handler.isKeyDown(testKey);
		boolean speedUp = handler.isKeyDown(KeyEvent.VK_SHIFT);
		boolean slowDown = handler.isKeyDown(KeyEvent.VK_CONTROL);
		boolean debugModePressed = handler.isKeyPressed(debugModeKey);
		boolean superSonicDebugPressed = handler.isKeyPressed(superSonicDebugKey);

		LevelManager levelManager = getLevelManager();
		for (Sprite sprite : sprites) {
			if (sprite instanceof AbstractPlayableSprite playable) {
				if (playable.isCpuControlled() && isCpuSidekickSuppressed()) {
					continue;
				}
				if (debugModePressed) {
					playable.toggleDebugMode();
				}
				// Super Sonic debug toggle (only for player 1, not CPU sidekicks)
				if (superSonicDebugPressed && !playable.isCpuControlled()) {
					var superCtrl = playable.getSuperStateController();
					if (superCtrl != null) {
						if (superCtrl.isSuper()) {
							superCtrl.debugDeactivate();
						} else {
							superCtrl.debugActivate();
						}
					}
				}

				boolean effectiveUp, effectiveDown, effectiveLeft, effectiveRight, effectiveJump, effectiveTest;

				if (playable.isCpuControlled() && playable.getCpuController() != null) {
					// CPU-controlled sprite: run AI to generate virtual input
					var cpuController = playable.getCpuController();
					cpuController.update(frameCounter);

					// If flying, the AI moves Tails directly - skip normal physics
					if (cpuController.isFlying()) {
						playable.getAnimationManager().update(frameCounter);
						playable.tickStatus();
						playable.endOfTick();
						continue;
					}

					boolean aiUp = cpuController.getInputUp();
					boolean aiDown = cpuController.getInputDown();
					boolean aiLeft = cpuController.getInputLeft();
					boolean aiRight = cpuController.getInputRight();
					boolean aiJump = cpuController.getInputJump();

					boolean controlLocked = playable.isControlLocked();
					boolean forcedRight = playable.isForcedInputActive(AbstractPlayableSprite.INPUT_RIGHT)
							|| playable.isForceInputRight();
					boolean forcedLeft = playable.isForcedInputActive(AbstractPlayableSprite.INPUT_LEFT);
					boolean forcedUp = playable.isForcedInputActive(AbstractPlayableSprite.INPUT_UP);
					boolean forcedDown = playable.isForcedInputActive(AbstractPlayableSprite.INPUT_DOWN);
					boolean forcedJump = playable.isForcedInputActive(AbstractPlayableSprite.INPUT_JUMP);
					effectiveRight = (!controlLocked && aiRight) || forcedRight;
					effectiveLeft = ((!controlLocked && aiLeft) || forcedLeft) && !forcedRight;
					effectiveUp = (!controlLocked && aiUp) || forcedUp;
					effectiveDown = (!controlLocked && aiDown) || forcedDown;
					effectiveJump = (!controlLocked && aiJump) || forcedJump;
					effectiveTest = false;

					playable.setJumpInputPressed(aiJump);
					playable.setDirectionalInputPressed(aiUp, aiDown, aiLeft, aiRight);
				} else {
					// Player-controlled sprite: use keyboard input
					boolean controlLocked = playable.isControlLocked();
					boolean forcedRight = playable.isForcedInputActive(AbstractPlayableSprite.INPUT_RIGHT)
							|| playable.isForceInputRight();
					boolean forcedLeft = playable.isForcedInputActive(AbstractPlayableSprite.INPUT_LEFT);
					boolean forcedUp = playable.isForcedInputActive(AbstractPlayableSprite.INPUT_UP);
					boolean forcedDown = playable.isForcedInputActive(AbstractPlayableSprite.INPUT_DOWN);
					boolean forcedJump = playable.isForcedInputActive(AbstractPlayableSprite.INPUT_JUMP);
					effectiveRight = (!controlLocked && right) || forcedRight;
					effectiveLeft = ((!controlLocked && left) || forcedLeft) && !forcedRight;
					effectiveUp = (!controlLocked && up) || forcedUp;
					effectiveDown = (!controlLocked && down) || forcedDown;
					effectiveJump = (!controlLocked && space) || forcedJump;
					effectiveTest = !controlLocked && testButton;

					// Store RAW input state for objects (like flippers) that need to query
					// button state even when control is locked. This matches ROM behavior
					// where obj_control locks movement but objects can still read button state.
					playable.setJumpInputPressed(space);
					playable.setDirectionalInputPressed(up, down, left, right);
				}

				// Pre-movement solid pass keeps riding/platform delta handling stable.
				boolean wasRidingObject = levelManager.getObjectManager() != null
						&& levelManager.getObjectManager().isRidingObject(playable);
				updateSolidContacts(levelManager, playable);
				playable.getMovementManager().handleMovement(effectiveUp, effectiveDown, effectiveLeft,
						effectiveRight, effectiveJump, effectiveTest, speedUp, slowDown);
				// Sonic 1 runs object SolidObject checks after Sonic movement in the object loop
				// (ExecuteObjects iterates player first, then solid objects). Run a second pass for
				// UNIFIED collision so top-landing resolves in the same frame.
				if (shouldRunPostMovementSolidPass(playable, wasRidingObject)) {
					updateSolidContacts(levelManager, playable);
				}
				// ROM order: Sonic moves first (Obj01), THEN plane switchers run (Obj03).
				// This ensures plane switchers check the current frame's position/air state.
				levelManager.applyPlaneSwitchers(playable);
				playable.getAnimationManager().update(frameCounter);
				playable.tickStatus();
				playable.endOfTick();
			}
		}
	}

	public void updateWithoutInput() {
		frameCounter++;
		Collection<Sprite> sprites = getAllSprites();
		LevelManager levelManager = getLevelManager();

		for (Sprite sprite : sprites) {
			if (sprite instanceof AbstractPlayableSprite playable) {
				if (playable.isCpuControlled() && isCpuSidekickSuppressed()) {
					continue;
				}
				boolean wasRidingObject = levelManager.getObjectManager() != null
						&& levelManager.getObjectManager().isRidingObject(playable);
				updateSolidContacts(levelManager, playable);
				playable.getMovementManager().handleMovement(false, false, false, false, false, false, false, false);
				if (shouldRunPostMovementSolidPass(playable, wasRidingObject)) {
					updateSolidContacts(levelManager, playable);
				}
				// ROM order: Sonic moves first (Obj01), THEN plane switchers run (Obj03).
				levelManager.applyPlaneSwitchers(playable);
				playable.getAnimationManager().update(frameCounter);
				playable.tickStatus();
				playable.endOfTick();
			}
		}
	}

	public void draw() {
		Collection<Sprite> sprites = getAllSprites();
		for (Sprite sprite : sprites) {
			if (isSuppressedSidekickSprite(sprite)) {
				continue;
			}
			sprite.draw();
		}
	}

	public void drawLowPriority() {
		bucketSprites();
		for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
			int idx = bucket - RenderPriority.MIN;
			for (Sprite sprite : lowPriorityBuckets[idx]) {
				sprite.draw();
			}
		}
	}

	public void drawHighPriority() {
		for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
			int idx = bucket - RenderPriority.MIN;
			for (Sprite sprite : highPriorityBuckets[idx]) {
				sprite.draw();
			}
			if (bucket == RenderPriority.MIN) {
				for (Sprite sprite : nonPlayableSprites) {
					sprite.draw();
				}
			}
		}
	}

	public void drawPriorityBucket(int bucket, boolean highPriority) {
		bucketSprites(); // Ensure sprites are bucketed
		int targetBucket = RenderPriority.clamp(bucket);
		int idx = targetBucket - RenderPriority.MIN;

		List<Sprite>[] buckets = highPriority ? highPriorityBuckets : lowPriorityBuckets;
		for (Sprite sprite : buckets[idx]) {
			sprite.draw();
		}

		// Non-playable sprites are only drawn once at the minimum high-priority bucket
		if (highPriority && targetBucket == RenderPriority.MIN) {
			for (Sprite sprite : nonPlayableSprites) {
				sprite.draw();
			}
		}
	}

	/**
	 * Draw all sprites in a single unified bucket, regardless of their isHighPriority flag.
	 * Calls the provided callback before drawing each sprite with its high priority status.
	 *
	 * This supports ROM-accurate sprite-to-sprite ordering where bucket number determines
	 * draw order independently of the sprite-to-tile priority (isHighPriority flag).
	 *
	 * @param bucket   The priority bucket to draw (0-7)
	 * @param callback Called before each sprite draw with (sprite, isHighPriority)
	 */
	public void drawUnifiedBucket(int bucket, SpriteDrawCallback callback) {
		bucketSprites(); // Ensure sprites are bucketed
		int targetBucket = RenderPriority.clamp(bucket);
		int idx = targetBucket - RenderPriority.MIN;

		// Draw low-priority sprites first (they appear behind)
		for (Sprite sprite : lowPriorityBuckets[idx]) {
			if (callback != null) {
				callback.beforeDraw(sprite, false);
			}
			sprite.draw();
		}

		// Draw high-priority sprites second (they appear in front)
		for (Sprite sprite : highPriorityBuckets[idx]) {
			if (callback != null) {
				callback.beforeDraw(sprite, true);
			}
			sprite.draw();
		}

		// Non-playable sprites are drawn at the minimum bucket
		if (targetBucket == RenderPriority.MIN) {
			for (Sprite sprite : nonPlayableSprites) {
				// Non-playable sprites default to low priority for tile layering
				if (callback != null) {
					callback.beforeDraw(sprite, false);
				}
				sprite.draw();
			}
		}
	}

	/**
	 * Callback interface for unified sprite drawing.
	 * Called before each sprite is drawn to allow setting up shader uniforms.
	 */
	public interface SpriteDrawCallback {
		/**
		 * Called before drawing a sprite.
		 *
		 * @param sprite       The sprite about to be drawn
		 * @param highPriority True if sprite should appear above high-priority tiles
		 */
		void beforeDraw(Sprite sprite, boolean highPriority);
	}

	/**
	 * Draw all sprites in a single unified bucket with per-instance priority.
	 * Priority is now handled per-instance in the shader, so no batch flushing
	 * is needed when switching between low and high priority sprites.
	 *
	 * @param bucket The priority bucket to draw (0-7)
	 * @param gfx    The graphics manager to use for priority state
	 */
	public void drawUnifiedBucketWithPriority(int bucket, GraphicsManager gfx) {
		bucketSprites();
		int idx = RenderPriority.clamp(bucket) - RenderPriority.MIN;

		// Draw low-priority sprites first (sets priority per-instance in shader)
		if (!lowPriorityBuckets[idx].isEmpty()) {
			gfx.setCurrentSpriteHighPriority(false);
			for (Sprite sprite : lowPriorityBuckets[idx]) {
				sprite.draw();
			}
		}

		// Draw high-priority sprites (sets priority per-instance in shader)
		// No batch flush needed - priority is baked into instance data
		if (!highPriorityBuckets[idx].isEmpty()) {
			gfx.setCurrentSpriteHighPriority(true);
			for (Sprite sprite : highPriorityBuckets[idx]) {
				sprite.draw();
			}
		}

		// Handle non-playable sprites at bucket MIN
		if (bucket == RenderPriority.MIN && !nonPlayableSprites.isEmpty()) {
			gfx.setCurrentSpriteHighPriority(false);
			for (Sprite sprite : nonPlayableSprites) {
				sprite.draw();
			}
		}
	}

	private boolean removeSprite(Sprite sprite) {
		bucketsDirty = true;
		return (sprites.remove(sprite) != null);
	}

	private void bucketSprites() {
		boolean currentSuppressed = isCpuSidekickSuppressed();
		if (currentSuppressed != lastSidekickSuppressed) {
			bucketsDirty = true;
			lastSidekickSuppressed = currentSuppressed;
		}
		if (!bucketsDirty) {
			return;
		}
		bucketsDirty = false;

		for (int i = 0; i < BUCKET_COUNT; i++) {
			lowPriorityBuckets[i].clear();
			highPriorityBuckets[i].clear();
		}
		nonPlayableSprites.clear();

		Collection<Sprite> sprites = getAllSprites();
		for (Sprite sprite : sprites) {
			if (isSuppressedSidekickSprite(sprite)) {
				continue;
			}
			if (sprite instanceof AbstractPlayableSprite playable) {
				int bucket = RenderPriority.clamp(playable.getPriorityBucket());
				int idx = bucket - RenderPriority.MIN;
				if (playable.isHighPriority()) {
					highPriorityBuckets[idx].add(sprite);
				} else {
					lowPriorityBuckets[idx].add(sprite);
				}
			} else {
				nonPlayableSprites.add(sprite);
			}
		}
	}

	private LevelManager getLevelManager() {
		if (levelManager == null) {
			levelManager = LevelManager.getInstance();
		}
		return levelManager;
	}

	private boolean isCpuSidekickSuppressed() {
		LevelManager lm = getLevelManager();
		if (lm == null) return false;
		if (lm.getCurrentZone() == Sonic2ZoneConstants.ZONE_SCZ) return true;
		if (AizPlaneIntroInstance.isSidekickSuppressed()) return true;
		return false;
	}

	private boolean isSuppressedSidekickSprite(Sprite sprite) {
		return isCpuSidekickSuppressed()
				&& sprite instanceof AbstractPlayableSprite playable
				&& playable.isCpuControlled();
	}

	private void updateSolidContacts(LevelManager levelManager, AbstractPlayableSprite playable) {
		if (levelManager.getObjectManager() != null) {
			levelManager.getObjectManager().updateSolidContacts(playable);
		}
	}

	static boolean requiresPostMovementSolidPass(AbstractPlayableSprite playable) {
		if (playable == null) {
			return false;
		}
		PhysicsFeatureSet featureSet = playable.getPhysicsFeatureSet();
		return featureSet != null && featureSet.collisionModel() == CollisionModel.UNIFIED;
	}

	static boolean shouldRunPostMovementSolidPass(AbstractPlayableSprite playable, boolean wasRidingObject) {
		return requiresPostMovementSolidPass(playable) && playable.getAir() && !wasRidingObject;
	}

	public static SensorConfiguration[][] createMovementMappingArray() {
		SensorConfiguration[][] output = new SensorConfiguration[GroundMode.values().length][Direction.values().length];
		// Initialize the array with all possible GroundMode and Direction combinations
		// Ground Mode
		output[GroundMode.GROUND.ordinal()][Direction.UP.ordinal()] = new SensorConfiguration((byte) 0, (byte) -16, true, Direction.UP);
		output[GroundMode.GROUND.ordinal()][Direction.DOWN.ordinal()] = new SensorConfiguration((byte) 0, (byte) 16, true, Direction.DOWN);
		output[GroundMode.GROUND.ordinal()][Direction.LEFT.ordinal()] = new SensorConfiguration((byte) -16, (byte) 0, false, Direction.LEFT);
		output[GroundMode.GROUND.ordinal()][Direction.RIGHT.ordinal()] = new SensorConfiguration((byte) 16, (byte) 0, false, Direction.RIGHT);

		// Right Wall
		output[GroundMode.RIGHTWALL.ordinal()][Direction.UP.ordinal()] = new SensorConfiguration((byte) -16, (byte) 0, false, Direction.LEFT);
		output[GroundMode.RIGHTWALL.ordinal()][Direction.DOWN.ordinal()] = new SensorConfiguration((byte) 16, (byte) 0, false, Direction.RIGHT);
		output[GroundMode.RIGHTWALL.ordinal()][Direction.LEFT.ordinal()] = new SensorConfiguration((byte) 0, (byte) 16, true, Direction.DOWN);
		output[GroundMode.RIGHTWALL.ordinal()][Direction.RIGHT.ordinal()] = new SensorConfiguration((byte) 0, (byte) -16, true, Direction.UP);

		// Ceiling
		output[GroundMode.CEILING.ordinal()][Direction.UP.ordinal()] = new SensorConfiguration((byte) 0, (byte) 16, true, Direction.DOWN);
		output[GroundMode.CEILING.ordinal()][Direction.DOWN.ordinal()] = new SensorConfiguration((byte) 0, (byte) -16, true, Direction.UP);
		output[GroundMode.CEILING.ordinal()][Direction.LEFT.ordinal()] = new SensorConfiguration((byte) 16, (byte) 0, false, Direction.RIGHT);
		output[GroundMode.CEILING.ordinal()][Direction.RIGHT.ordinal()] = new SensorConfiguration((byte) -16, (byte) 0, false, Direction.LEFT);

		// Left Wall
		output[GroundMode.LEFTWALL.ordinal()][Direction.UP.ordinal()] = new SensorConfiguration((byte) 16, (byte) 0, false, Direction.RIGHT);
		output[GroundMode.LEFTWALL.ordinal()][Direction.DOWN.ordinal()] = new SensorConfiguration((byte) -16, (byte) 0, false, Direction.LEFT);
		output[GroundMode.LEFTWALL.ordinal()][Direction.LEFT.ordinal()] = new SensorConfiguration((byte) 0, (byte) -16, true, Direction.UP);
		output[GroundMode.LEFTWALL.ordinal()][Direction.RIGHT.ordinal()] = new SensorConfiguration((byte) 0, (byte) 16, true, Direction.DOWN);

		return output;
	}

	public static SensorConfiguration getSensorConfigurationForGroundModeAndDirection(GroundMode groundMode, Direction direction) {
		return MOVEMENT_MAPPING_ARRAY[groundMode.ordinal()][direction.ordinal()];
	}

	public synchronized static SpriteManager getInstance() {
		if (spriteManager == null) {
			spriteManager = new SpriteManager();
		}
		return spriteManager;
	}
}
