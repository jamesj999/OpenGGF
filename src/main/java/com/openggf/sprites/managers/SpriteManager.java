package com.openggf.sprites.managers;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;

import com.openggf.control.InputHandler;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CollisionModel;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.RuntimeManager;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.camera.Camera;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.game.GameModuleRegistry;
import com.openggf.level.LevelManager;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.CustomPlayablePhysics;
import com.openggf.sprites.SensorConfiguration;
import com.openggf.sprites.Sprite;
import com.openggf.game.GroundMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages collection of available sprites to be provided to renderer and collision manager.
 * 
 * @author james
 * 
 */
public class SpriteManager {
	private final SonicConfigurationService configService;

	private static SpriteManager bootstrapInstance;

	private Map<String, Sprite> sprites;

	private final List<AbstractPlayableSprite> sidekicks = new ArrayList<>();
	private final Map<AbstractPlayableSprite, String> sidekickCharacterNames = new IdentityHashMap<>();

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
	private int p2UpKey;
	private int p2DownKey;
	private int p2LeftKey;
	private int p2RightKey;
	private int p2JumpKey;
	private int p2StartKey;
	private int testKey;
	private int debugModeKey;
	private int superSonicDebugKey;
	private int giveEmeraldsKey;
	private int frameCounter;
	private boolean inputSuppressed;
	private boolean playbackInputSuppressed;

	public SpriteManager() {
		this(SonicConfigurationService.getInstance());
	}

	public SpriteManager(SonicConfigurationService configService) {
		this.configService = configService;
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
		p2UpKey = configService.getInt(SonicConfiguration.P2_UP);
		p2DownKey = configService.getInt(SonicConfiguration.P2_DOWN);
		p2LeftKey = configService.getInt(SonicConfiguration.P2_LEFT);
		p2RightKey = configService.getInt(SonicConfiguration.P2_RIGHT);
		p2JumpKey = configService.getInt(SonicConfiguration.P2_JUMP);
		p2StartKey = configService.getInt(SonicConfiguration.P2_START);
		testKey = configService.getInt(SonicConfiguration.TEST);
		debugModeKey = configService.getInt(SonicConfiguration.DEBUG_MODE_KEY);
		superSonicDebugKey = configService.getInt(SonicConfiguration.SUPER_SONIC_DEBUG_KEY);
		giveEmeraldsKey = configService.getInt(SonicConfiguration.GIVE_EMERALDS_KEY);
	}

	/**
	 * Adds the given sprite to the SpriteManager. Returns true if we have
	 * overwritten a sprite, false if we are creating a new one.
	 * <p>
	 * If {@code sprite} is a CPU-controlled {@link AbstractPlayableSprite} that is
	 * not already tracked, it is added to the sidekick list so that
	 * {@link #getSidekicks()} returns it. Prefer
	 * {@link #addSprite(AbstractPlayableSprite, String)} when the character name is
	 * known.
	 *
	 * @param sprite
	 * @return true if an existing sprite was replaced
	 */
	public boolean addSprite(Sprite sprite) {
		bucketsDirty = true;
		boolean replaced = (sprites.put(sprite.getCode(), sprite) != null);
		if (!replaced && sprite instanceof AbstractPlayableSprite playable && playable.isCpuControlled()) {
			sidekicks.add(playable);
			// characterName is null when registered via the untyped overload
		}
		return replaced;
	}

	/**
	 * Adds a CPU-controlled sidekick sprite with its character name for art loading.
	 * The character name is stored for later use by art loading and VRAM bank allocation.
	 */
	public void addSprite(AbstractPlayableSprite sprite, String characterName) {
		addSprite((Sprite) sprite);
		if (sprite.isCpuControlled()) {
			// addSprite(Sprite) already added the sprite to the sidekicks list on first
			// insertion. Just record the character name.
			sidekickCharacterNames.put(sprite, characterName);
		}
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
		sidekicks.clear();
		sidekickCharacterNames.clear();
		bucketsDirty = true;
	}

	/**
	 * Resets mutable state without destroying the singleton instance.
	 */
	public void resetState() {
		clearAllSprites();
		levelManager = null;
		frameCounter = 0;
		inputSuppressed = false;
		playbackInputSuppressed = false;
	}

	/**
	 * Forces the cached render buckets to rebuild on the next draw.
	 *
	 * Needed when per-frame state like high priority or bucket index changes
	 * outside add/remove paths (for example, plane switchers or zone events).
	 */
	public void invalidateRenderBuckets() {
		bucketsDirty = true;
	}

	public Collection<Sprite> getAllSprites() {
		return sprites.values();
	}

	public Sprite getSprite(String code) {
		return sprites.get(code);
	}

	/**
	 * Returns all CPU-controlled sidekick sprites in chain order.
	 * Returns empty list when sidekicks are suppressed for the current zone.
	 */
	public List<AbstractPlayableSprite> getSidekicks() {
		if (isCpuSidekickSuppressed()) {
			return List.of();
		}
		return Collections.unmodifiableList(sidekicks);
	}

	/**
	 * Returns the character name for a sidekick (e.g. "tails", "sonic", "knuckles").
	 */
	public String getSidekickCharacterName(AbstractPlayableSprite sidekick) {
		return sidekickCharacterNames.get(sidekick);
	}


	public void update(InputHandler handler) {
		frameCounter++;
		// Note: bucketsDirty is already marked in addSprite()/removeSprite(),
		// no need to unconditionally mark dirty every frame
		Collection<Sprite> sprites = getAllSprites();
		boolean suppressInput = inputSuppressed || playbackInputSuppressed;
		boolean up = !suppressInput && handler.isKeyDown(upKey);
		boolean down = !suppressInput && handler.isKeyDown(downKey);
		boolean left = !suppressInput && handler.isKeyDown(leftKey);
		boolean right = !suppressInput && handler.isKeyDown(rightKey);
		boolean space = !suppressInput && handler.isKeyDown(jumpKey);
		int p2Held = 0;
		if (!suppressInput && handler.isKeyDown(p2UpKey)) p2Held |= AbstractPlayableSprite.INPUT_UP;
		if (!suppressInput && handler.isKeyDown(p2DownKey)) p2Held |= AbstractPlayableSprite.INPUT_DOWN;
		if (!suppressInput && handler.isKeyDown(p2LeftKey)) p2Held |= AbstractPlayableSprite.INPUT_LEFT;
		if (!suppressInput && handler.isKeyDown(p2RightKey)) p2Held |= AbstractPlayableSprite.INPUT_RIGHT;
		if (!suppressInput && handler.isKeyDown(p2JumpKey)) p2Held |= AbstractPlayableSprite.INPUT_JUMP;
		int p2Logical = p2Held;
		if (!suppressInput && handler.isKeyDown(p2StartKey)) p2Logical |= 0x20;
		boolean testButton = !suppressInput && handler.isKeyDown(testKey);
		boolean speedUp = isDebugSpeedUpModifierDown(handler);
		boolean slowDown = isDebugSlowDownModifierDown(handler);
		boolean debugModePressed = handler.isKeyPressed(debugModeKey);
		boolean superSonicDebugPressed = handler.isKeyPressed(superSonicDebugKey);
		boolean giveEmeraldsPressed = handler.isKeyPressed(giveEmeraldsKey);

		// Give all chaos emeralds (debug)
		if (giveEmeraldsPressed) {
			var gsm = currentGameStateManager();
			if (!gsm.hasAllEmeralds()) {
				for (int i = 0; i < gsm.getChaosEmeraldCount(); i++) {
					gsm.markEmeraldCollected(i);
				}
			}
		}

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
					boolean isFirstSidekick = !sidekicks.isEmpty() && sidekicks.getFirst() == playable;
					if (isFirstSidekick) {
						cpuController.setController2Input(p2Held, p2Logical);
					}
					cpuController.update(frameCounter);

					// If approaching (respawn in progress) and the strategy handles movement
					// directly (Tails fly-in, Knuckles glide), skip normal physics.
					// Strategies that need physics (Sonic walk/spindash) fall through.
					if (cpuController.isApproaching()
							&& !cpuController.getRespawnStrategy().requiresPhysics()) {
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

					boolean forcedRight = playable.isForcedInputActive(AbstractPlayableSprite.INPUT_RIGHT)
							|| playable.isForceInputRight();
					boolean forcedLeft = playable.isForcedInputActive(AbstractPlayableSprite.INPUT_LEFT);
					boolean forcedUp = playable.isForcedInputActive(AbstractPlayableSprite.INPUT_UP);
					boolean forcedDown = playable.isForcedInputActive(AbstractPlayableSprite.INPUT_DOWN);
					boolean forcedJump = playable.isForcedInputActive(AbstractPlayableSprite.INPUT_JUMP);
					effectiveRight = aiRight || forcedRight;
					effectiveLeft = (aiLeft || forcedLeft) && !forcedRight;
					effectiveUp = aiUp || forcedUp;
					effectiveDown = aiDown || forcedDown;
					effectiveJump = aiJump || forcedJump;
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

				tickPlayablePhysics(playable, effectiveUp, effectiveDown, effectiveLeft,
						effectiveRight, effectiveJump, effectiveTest, speedUp, slowDown,
						levelManager, frameCounter);
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
				tickPlayablePhysics(playable, false, false, false, false, false, false, false, false,
						levelManager, frameCounter);
			}
		}
	}

	/**
	 * Refreshes playable sprite render state without advancing movement/gameplay.
	 * Used by ending-demo preroll phases so the player sprite is visible as soon
	 * as the fade begins, while physics remain frozen.
	 */
	public void primePlayableVisualState() {
		Collection<Sprite> sprites = getAllSprites();
		for (Sprite sprite : sprites) {
			if (sprite instanceof AbstractPlayableSprite playable) {
				if (playable.isCpuControlled() && isCpuSidekickSuppressed()) {
					continue;
				}
				playable.getAnimationManager().update(frameCounter);
			}
		}
	}

	static boolean isDebugSpeedUpModifierDown(InputHandler handler) {
		return handler.isKeyDown(GLFW_KEY_LEFT_SHIFT) || handler.isKeyDown(GLFW_KEY_RIGHT_SHIFT);
	}

	static boolean isDebugSlowDownModifierDown(InputHandler handler) {
		return handler.isKeyDown(GLFW_KEY_LEFT_CONTROL) || handler.isKeyDown(GLFW_KEY_RIGHT_CONTROL);
	}

	public void draw() {
		boolean wrapEnabled = enableVerticalWrapIfNeeded();
		try {
			Collection<Sprite> sprites = getAllSprites();
			for (Sprite sprite : sprites) {
				if (isSuppressedSidekickSprite(sprite)) {
					continue;
				}
				sprite.draw();
			}
		} finally {
			if (wrapEnabled) disableVerticalWrap();
		}
	}

	public void drawLowPriority() {
		boolean wrapEnabled = enableVerticalWrapIfNeeded();
		try {
			bucketSprites();
			for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
				int idx = bucket - RenderPriority.MIN;
				for (Sprite sprite : lowPriorityBuckets[idx]) {
					sprite.draw();
				}
			}
		} finally {
			if (wrapEnabled) disableVerticalWrap();
		}
	}

	public void drawHighPriority() {
		boolean wrapEnabled = enableVerticalWrapIfNeeded();
		try {
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
		} finally {
			if (wrapEnabled) disableVerticalWrap();
		}
	}

	public void drawPriorityBucket(int bucket, boolean highPriority) {
		boolean wrapEnabled = enableVerticalWrapIfNeeded();
		try {
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
		} finally {
			if (wrapEnabled) disableVerticalWrap();
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
		boolean wrapEnabled = enableVerticalWrapIfNeeded();
		try {
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
		} finally {
			if (wrapEnabled) disableVerticalWrap();
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
		boolean wrapEnabled = enableVerticalWrapIfNeeded();
		try {
			bucketSprites();
			int idx = RenderPriority.clamp(bucket) - RenderPriority.MIN;

			// The BatchedPatternRenderer uses a single global priority uniform —
			// flush and restart the batch at each LOW→HIGH transition so each group
			// gets its own batch with the correct priority.
			if (!lowPriorityBuckets[idx].isEmpty()) {
				gfx.flushPatternBatch();
				gfx.setCurrentSpriteHighPriority(false);
				gfx.beginPatternBatch();
				for (Sprite sprite : lowPriorityBuckets[idx]) {
					sprite.draw();
				}
			}

			if (!highPriorityBuckets[idx].isEmpty()) {
				gfx.flushPatternBatch();
				gfx.setCurrentSpriteHighPriority(true);
				gfx.beginPatternBatch();
				for (Sprite sprite : highPriorityBuckets[idx]) {
					sprite.draw();
				}
			}

			// Handle non-playable sprites at bucket MIN
			if (bucket == RenderPriority.MIN && !nonPlayableSprites.isEmpty()) {
				gfx.flushPatternBatch();
				gfx.setCurrentSpriteHighPriority(false);
				gfx.beginPatternBatch();
				for (Sprite sprite : nonPlayableSprites) {
					sprite.draw();
				}
			}
		} finally {
			if (wrapEnabled) disableVerticalWrap();
		}
	}

	/**
	 * Enables vertical wrap Y adjustment on GraphicsManager when the camera
	 * has vertical wrapping active. Returns true if wrap was enabled (and
	 * caller must disable it after drawing).
	 */
	private boolean enableVerticalWrapIfNeeded() {
		Camera camera = currentCamera();
		if (camera.isVerticalWrapEnabled()) {
			GraphicsManager.getInstance().enableVerticalWrapAdjust(
					Camera.VERTICAL_WRAP_RANGE, camera.getY());
			return true;
		}
		return false;
	}

	private void disableVerticalWrap() {
		GraphicsManager.getInstance().disableVerticalWrapAdjust();
	}

	private boolean removeSprite(Sprite sprite) {
		if (sprite == null) {
			return false;
		}
		bucketsDirty = true;
		if (sprite instanceof AbstractPlayableSprite playable) {
			sidekicks.remove(playable);
			sidekickCharacterNames.remove(playable);
		}
		return (sprites.remove(sprite.getCode()) != null);
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

		// Two-pass bucketing: sidekicks first (drawn behind), then main player
		// (drawn on top). On the VDP, lower sprite indices have higher priority
		// and appear in front. Sonic occupies slot 0 and Tails slot 1, so Sonic
		// must be drawn last in painter's-algorithm order.
		Collection<Sprite> sprites = getAllSprites();
		for (Sprite sprite : sprites) {
			if (isSuppressedSidekickSprite(sprite)) {
				continue;
			}
			if (sprite instanceof AbstractPlayableSprite playable && playable.isCpuControlled()) {
				int bucket = RenderPriority.clamp(playable.getPriorityBucket());
				int idx = bucket - RenderPriority.MIN;
				if (playable.isHighPriority()) {
					highPriorityBuckets[idx].add(sprite);
				} else {
					lowPriorityBuckets[idx].add(sprite);
				}
			}
		}
		for (Sprite sprite : sprites) {
			if (isSuppressedSidekickSprite(sprite)) {
				continue;
			}
			if (sprite instanceof AbstractPlayableSprite playable) {
				if (playable.isCpuControlled()) {
					continue; // already added in first pass
				}
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
			var runtime = RuntimeManager.getCurrent();
			levelManager = runtime != null ? runtime.getLevelManager() : LevelManager.getInstance();
		}
		return levelManager;
	}

	private Camera currentCamera() {
		var runtime = RuntimeManager.getCurrent();
		return runtime != null ? runtime.getCamera() : Camera.getInstance();
	}

	private GameStateManager currentGameStateManager() {
		var runtime = RuntimeManager.getCurrent();
		return runtime != null ? runtime.getGameState() : GameStateManager.getInstance();
	}

	/**
	 * Suppresses all player keyboard input (directional + jump + test).
	 * Forced input masks (demo playback) still apply.
	 */
	public void setInputSuppressed(boolean suppressed) {
		this.inputSuppressed = suppressed;
	}

	/**
	 * Suppresses keyboard-driven gameplay input specifically for playback mode.
	 * This is separate from generic suppression used by title cards/credits.
	 */
	public void setPlaybackInputSuppressed(boolean suppressed) {
		this.playbackInputSuppressed = suppressed;
	}

	private boolean isCpuSidekickSuppressed() {
		LevelManager lm = getLevelManager();
		if (lm == null) return false;
		if (GameModuleRegistry.getCurrent().isSidekickSuppressedForZone(lm.getCurrentZone())) return true;
		if (AizPlaneIntroInstance.isSidekickSuppressed()) return true;
		return false;
	}

	private boolean isSuppressedSidekickSprite(Sprite sprite) {
		return isCpuSidekickSuppressed()
				&& sprite instanceof AbstractPlayableSprite playable
				&& playable.isCpuControlled();
	}

	/**
	 * Runs the canonical per-sprite physics tick: solid contacts, movement,
	 * post-movement solid pass, plane switchers, animation, and status.
	 * <p>
	 * This is the single source of truth for per-sprite update ordering.
	 * Both {@code SpriteManager.update()} and {@code HeadlessTestRunner}
	 * MUST delegate here rather than duplicating the step sequence.
	 *
	 * @param playable     the playable sprite to tick
	 * @param up           effective up input (after control-lock / forced-input filtering)
	 * @param down         effective down input
	 * @param left         effective left input
	 * @param right        effective right input
	 * @param jump         effective jump input
	 * @param test         effective test button input
	 * @param speedUp      debug speed-up modifier
	 * @param slowDown     debug slow-down modifier
	 * @param levelManager the level manager
	 * @param frameCounter the current frame number
	 */
	public static void tickPlayablePhysics(AbstractPlayableSprite playable,
										   boolean up, boolean down, boolean left, boolean right,
										   boolean jump, boolean test, boolean speedUp, boolean slowDown,
										   LevelManager levelManager, int frameCounter) {
		boolean isUnified = requiresPostMovementSolidPass(playable);
		// For S1 UNIFIED: skip pre-movement solid pass. ROM processes all solid
		// objects AFTER Sonic's movement (his slot runs first in ExecuteObjects),
		// so only the post-movement pass is ROM-accurate. Running both creates
		// double-correction artifacts.
		// For S2/S3K: pre-movement solid pass is the only pass. The velocity
		// classification adjustment compensates for the player's position not yet
		// reflecting their velocity.
		if (!isUnified) {
			applySolidContacts(levelManager, playable, false, false);
		}
		if (playable instanceof CustomPlayablePhysics customPhysics) {
			customPhysics.tickCustomPhysics(up, down, left, right, jump, test, speedUp, slowDown,
					levelManager, frameCounter);
		} else {
			playable.getMovementManager().handleMovement(up, down, left, right, jump, test, speedUp, slowDown);
		}
		// ROM order: ReactToItem runs during each player's slot within ExecuteObjects,
		// after their physics but before other objects' solid checks.
		levelManager.applyTouchResponses(playable);
		// S1 (UNIFIED): Post-movement solid pass matches ROM timing — solid objects
		// check Sonic's position after he has moved in the ROM's ExecuteObjects loop.
		// postMovement=true disables velocity classification adjustment.
		if (isUnified) {
			applySolidContacts(levelManager, playable, true, false);
		}
		levelManager.applyPlaneSwitchers(playable);
		playable.getAnimationManager().update(frameCounter);
		playable.tickStatus();
		playable.endOfTick();
	}

	private static void applySolidContacts(LevelManager levelManager, AbstractPlayableSprite playable,
										   boolean postMovement, boolean deferSideToPostMovement) {
		if (levelManager.getObjectManager() != null) {
			levelManager.getObjectManager().updateSolidContacts(playable, postMovement,
					deferSideToPostMovement);
		}
	}


	static boolean requiresPostMovementSolidPass(AbstractPlayableSprite playable) {
		if (playable == null) {
			return false;
		}
		PhysicsFeatureSet featureSet = playable.getPhysicsFeatureSet();
		return featureSet != null && featureSet.collisionModel() == CollisionModel.UNIFIED;
	}


	public static SensorConfiguration[][] createMovementMappingArray() {
		SensorConfiguration[][] output = new SensorConfiguration[GroundMode.values().length][Direction.values().length];
		// Initialize the array with all possible GroundMode and Direction combinations
		// Ground Mode
		output[GroundMode.GROUND.ordinal()][Direction.UP.ordinal()] = new SensorConfiguration((byte) 0, (byte) -16, true, Direction.UP);
		output[GroundMode.GROUND.ordinal()][Direction.DOWN.ordinal()] = new SensorConfiguration((byte) 0, (byte) 16, true, Direction.DOWN);
		output[GroundMode.GROUND.ordinal()][Direction.LEFT.ordinal()] = new SensorConfiguration((byte) -16, (byte) 0, false, Direction.LEFT);
		output[GroundMode.GROUND.ordinal()][Direction.RIGHT.ordinal()] = new SensorConfiguration((byte) 16, (byte) 0, false, Direction.RIGHT);

		// Right Wall (0x40 quadrant): Sonic on right wall, surface to his RIGHT
		// ROM's WalkVertR: probes right (a3=+$10), add.w d1,obX
		output[GroundMode.RIGHTWALL.ordinal()][Direction.UP.ordinal()] = new SensorConfiguration((byte) -16, (byte) 0, false, Direction.LEFT);
		output[GroundMode.RIGHTWALL.ordinal()][Direction.DOWN.ordinal()] = new SensorConfiguration((byte) 16, (byte) 0, false, Direction.RIGHT);
		output[GroundMode.RIGHTWALL.ordinal()][Direction.LEFT.ordinal()] = new SensorConfiguration((byte) 0, (byte) 16, true, Direction.DOWN);
		output[GroundMode.RIGHTWALL.ordinal()][Direction.RIGHT.ordinal()] = new SensorConfiguration((byte) 0, (byte) -16, true, Direction.UP);

		// Ceiling
		output[GroundMode.CEILING.ordinal()][Direction.UP.ordinal()] = new SensorConfiguration((byte) 0, (byte) 16, true, Direction.DOWN);
		output[GroundMode.CEILING.ordinal()][Direction.DOWN.ordinal()] = new SensorConfiguration((byte) 0, (byte) -16, true, Direction.UP);
		output[GroundMode.CEILING.ordinal()][Direction.LEFT.ordinal()] = new SensorConfiguration((byte) 16, (byte) 0, false, Direction.RIGHT);
		output[GroundMode.CEILING.ordinal()][Direction.RIGHT.ordinal()] = new SensorConfiguration((byte) -16, (byte) 0, false, Direction.LEFT);

		// Left Wall (0xC0 quadrant): Sonic on left wall, surface to his LEFT
		// ROM's WalkVertL: probes left (a3=-$10), sub.w d1,obX
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
		var runtime = RuntimeManager.getCurrent();
		if (runtime != null) {
			return runtime.getSpriteManager();
		}
		if (bootstrapInstance == null) {
			bootstrapInstance = new SpriteManager();
		}
		return bootstrapInstance;
	}
}
