package com.openggf.camera;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.RuntimeManager;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;

public class Camera {
	private static Camera bootstrapInstance;
	private short x = 0;
	private short y = 0;

	private short minX;
	private short minY;
	private short maxX;
	private short maxY;

	// Screen shake offsets (ROM: applied to Camera_X_pos_copy and Camera_Y_pos_copy)
	// These offsets affect both FG tiles and sprites to create unified screen shake.
	private short shakeOffsetX = 0;
	private short shakeOffsetY = 0;

	// Target boundaries for smooth easing (ROM: Camera_Max_Y_pos_target, etc.)
	private short minXTarget;
	private short minYTarget;
	private short maxXTarget;
	private short maxYTarget;

	// ROM uses 2 pixels per frame for boundary easing
	private static final short BOUNDARY_EASE_STEP = 2;

	// Flag indicating boundary is actively changing (ROM: Camera_Max_Y_Pos_Changing)
	// When true, normal vertical scroll rules may be modified
	private boolean maxYChanging = false;

	// ROM: Horiz_scroll_delay_val - horizontal scroll delay counter
	// When > 0, horizontal scroll uses position history while vertical scroll continues normally
	private int horizScrollDelayFrames = 0;

	// Full camera freeze (both X and Y) - used for death, cutscenes, etc.
	// This is separate from horizScrollDelayFrames which only affects horizontal scroll.
	private boolean frozen = false;

	// ROM: Level_started_flag.
	// Used by HUD/start-state flow and intro/cutscene sequencing.
	// This flag does NOT freeze camera scroll; use `frozen` for camera suppression.
	private boolean levelStarted = true;

	// ROM: LZ3/SBZ2 vertical wrapping (DeformLayers.asm lines 542-580)
	// When top boundary is negative (e.g. 0xFF00 = -256), coordinates wrap modularly
	// to create an infinite-falling effect. The wrap range is 0x800 (2048 pixels).
	private boolean verticalWrapEnabled = false;
	public static final int VERTICAL_WRAP_RANGE = 0x800;  // 2048 pixels
	private static final int VERTICAL_WRAP_MASK = 0x7FF;   // AND mask for camera/player Y
	private static final int VERTICAL_WRAP_BG_MASK = 0x3FF; // AND mask for BG Y
	// Tracks whether a wrap occurred this frame, and the delta applied
	private boolean lastFrameWrapped = false;
	private short wrapDeltaY = 0;

	private AbstractPlayableSprite focusedSprite;

	private short width;
	private short height;

	// ROM: Camera_Y_pos_bias - vertical position target for camera centering
	// Default is (224/2)-16 = 96 (0x60). Used as center point for scroll windows.
	private static final short DEFAULT_Y_BIAS = 96;

	// ROM: Look up target bias: 0xC8 (200) - shifts camera up to show more above Sonic
	private static final short LOOK_UP_BIAS = (short) 0xC8;

	// ROM: Look down target bias: 8 - shifts camera down to show more below Sonic
	private static final short LOOK_DOWN_BIAS = 8;

	// ROM: Camera_Y_pos_bias - dynamic bias that can change during gameplay
	// (looking up/down, spindash, etc). Starts at 96.
	private short yPosBias = DEFAULT_Y_BIAS;

	// ROM: Airborne window is ±0x20 (32) around the bias
	private static final short AIRBORNE_WINDOW_HALF = 32;

	// ROM: Inertia threshold for fast scroll (0x800 = 2048)
	private static final short FAST_SCROLL_INERTIA_THRESHOLD = 0x800;

	public Camera() {
		this(SonicConfigurationService.getInstance());
	}

	public Camera(SonicConfigurationService configService) {
		width = configService.getShort(SonicConfiguration.SCREEN_WIDTH_PIXELS);
		height = configService.getShort(SonicConfiguration.SCREEN_HEIGHT_PIXELS);
	}

	public void updatePosition() {
		updatePosition(false);
	}

	public void updatePosition(boolean force) {
		if (force) {
			// Position camera so sprite is at the standard target position:
			// X: 152 pixels from left edge (midpoint between 144-160 window)
			// Y: 96 pixels from top edge (standard ground camera position)
			x = (short) (focusedSprite.getCentreX() - 152);
			y = (short) (focusedSprite.getCentreY() - 96);

			// Apply bounds clamping.
			// If max < min, treat the upper bound as wrapped/unbounded for this signed domain.
			// SCZ ObjB2 writes Camera_Max_X_pos = Camera_X_pos - $40, which can transiently
			// produce max < min at low X in this engine representation.
			x = clampAxisWithWrap(x, minX, maxX);
			y = clampAxisWithWrap(y, minY, maxY);
			return;
		}

		// Full camera freeze (death, cutscenes) - don't update X or Y at all
		if (frozen) {
			return;
		}

		// ROM behavior: Horiz_scroll_delay_val only affects horizontal scrolling.
		// Vertical scrolling (ScrollVerti) always uses current position and runs normally.
		// See s2.asm ScrollHoriz (line ~18009) vs ScrollVerti (line ~18112).

		// Horizontal scroll - may use position history if delay is active
		short focusedSpriteRealX;
		if (horizScrollDelayFrames > 0) {
			// ROM: ScrollHoriz uses position buffer when Horiz_scroll_delay_val is set
			// Use historical X position, clamped to buffer size (64 entries)
			int historyIndex = Math.min(horizScrollDelayFrames, 63);
			focusedSpriteRealX = (short) (focusedSprite.getCentreX(historyIndex) - x);
			horizScrollDelayFrames--;
		} else {
			focusedSpriteRealX = (short) (focusedSprite.getCentreX() - x);
		}

		// Vertical scroll - always uses current position (ROM: ScrollVerti has no delay)
		// ROM: d0 = (v_player+obY).w - (v_screenposy).w
		// When vertical wrapping is active, compute using modular arithmetic to handle
		// cases where Sonic and camera are in different wrap periods (e.g. Sonic's Y was
		// updated by ground collision across the wrap boundary between frames).
		short focusedSpriteRealY;
		if (verticalWrapEnabled) {
			int diff = (int) focusedSprite.getCentreY() - (int) y;
			diff = ((diff % VERTICAL_WRAP_RANGE) + VERTICAL_WRAP_RANGE) % VERTICAL_WRAP_RANGE;
			if (diff > VERTICAL_WRAP_RANGE / 2) {
				diff -= VERTICAL_WRAP_RANGE;
			}
			focusedSpriteRealY = (short) diff;
		} else {
			focusedSpriteRealY = (short) (focusedSprite.getCentreY() - y);
		}

		// ROM: s2.asm:18121-18132 - Rolling height compensation
		// When rolling, Sonic's center shifts down by ~5px due to height change.
		// Subtract 5 from the Y delta to prevent camera jolt.
		// Tails is 4 pixels shorter, so only subtract 1 for Tails.
		if (focusedSprite.getRolling()) {
			focusedSpriteRealY -= 5;
			if (focusedSprite instanceof Tails) {
				focusedSpriteRealY += 4; // Net: subtract 1 for Tails
			}
		}

		// Horizontal scroll logic (ROM: ScrollHoriz)
		if (focusedSpriteRealX < 144) {
			short difference = (short) (focusedSpriteRealX - 144);
			if (difference < -16) {
				x -= 16;
			} else {
				x += difference;
			}
		} else if (focusedSpriteRealX > 160) {
			short difference = (short) (focusedSpriteRealX - 160);
			if (difference > 16) {
				x += 16;
			} else {
				x += difference;
			}
		}

		// Vertical scroll logic (ROM: ScrollVerti)
		if (focusedSprite.getAir()) {
			// ROM: Airborne uses ±0x20 window around bias
			// Upper bound: bias - 32, Lower bound: bias + 32
			short upperBound = (short) (yPosBias - AIRBORNE_WINDOW_HALF);
			short lowerBound = (short) (yPosBias + AIRBORNE_WINDOW_HALF);
			if (focusedSpriteRealY < upperBound) {
				short difference = (short) (focusedSpriteRealY - upperBound);
				if (difference < -16) {
					y -= 16;
				} else {
					y += difference;
				}
			} else if (focusedSpriteRealY >= lowerBound) {
				short difference = (short) (focusedSpriteRealY - lowerBound);
				if (difference > 16) {
					y += 16;
				} else {
					y += difference;
				}
			}
		} else {
			// ROM: s2.asm:18150-18195 - Grounded vertical scroll
			// Uses bias state and inertia (ground speed), NOT ySpeed
			short difference = (short) (focusedSpriteRealY - yPosBias);

			if (difference != 0) {
				// ROM: .decideScrollType - choose scroll cap based on bias and inertia
				short tolerance;
				if (yPosBias != DEFAULT_Y_BIAS) {
					// ROM: .doScroll_slow - bias is not normal (looking up/down)
					// Use 2px cap
					tolerance = 2;
				} else {
					// Bias is normal (96) - check inertia for medium vs fast
					short absInertia = (short) Math.abs(focusedSprite.getGSpeed());
					if (absInertia >= FAST_SCROLL_INERTIA_THRESHOLD) {
						// ROM: .doScroll_fast - player moving very fast on ground
						// Use 16px cap
						tolerance = 16;
					} else {
						// ROM: .doScroll_medium - normal ground movement
						// Use 6px cap
						tolerance = 6;
					}
				}

				// Apply scroll with capping
				if (difference > 0) {
					// Scroll down
					if (difference > tolerance) {
						y += tolerance;
					} else {
						y += difference;
					}
				} else {
					// Scroll up (difference is negative)
					if (difference < -tolerance) {
						y -= tolerance;
					} else {
						y += difference;
					}
				}
			}
			// else: ROM: .doNotScroll - player is at bias, no scroll needed
		}

		// ROM: LZ3/SBZ2 vertical wrapping (DeformLayers.asm lines 542-580)
		// When wrapping is active, coordinate masking replaces normal Y clamping.
		lastFrameWrapped = false;
		wrapDeltaY = 0;
		if (verticalWrapEnabled) {
			// Upward wrap: camera Y at or below -256 (0xFF00 signed)
			// ROM: cmpi.w #-$100,d1 / bgt.s .noupwrap — wraps when d1 <= -$100
			if (y <= -0x100) {
				short oldY = y;
				y = (short) (y & VERTICAL_WRAP_MASK);
				if (focusedSprite != null) {
					focusedSprite.setCentreY((short) (focusedSprite.getCentreY() & VERTICAL_WRAP_MASK));
				}
				lastFrameWrapped = true;
				wrapDeltaY = (short) (y - oldY);
			}
			// Downward wrap: camera Y reached bottom boundary (0x800)
			// ROM: cmp.w (Camera_Max_Y_pos).w,d1 / blt.s .nodownwrap / sub.w d0,y
			else if (y >= VERTICAL_WRAP_RANGE) {
				short oldY = y;
				y = (short) (y - VERTICAL_WRAP_RANGE);
				if (focusedSprite != null) {
					focusedSprite.setCentreY((short) (focusedSprite.getCentreY() & VERTICAL_WRAP_MASK));
				}
				lastFrameWrapped = true;
				wrapDeltaY = (short) (y - oldY);
			}
		}

		// Clamp to boundaries (ROM: ScrollHoriz lines 18077-18092, ScrollVerti similar)
		x = clampAxisWithWrap(x, minX, maxX);
		// ROM: After a vertical wrap, DeformLayers.asm branches directly to loc_6724
		// (the store), skipping the normal boundary clamp. This is critical because
		// after wrapping from e.g. -260 to 1788, clamping to maxY could force the
		// camera to a different position than Sonic was wrapped to.
		// Normal (non-wrap) frames still clamp, which handles pit death in SBZ2
		// where v_limitbtm2=$510 constrains the camera even though wrapping is active.
		if (!lastFrameWrapped) {
			y = clampAxisWithWrap(y, minY, maxY);
		}
	}

	private short clampAxisWithWrap(short value, short min, short max) {
		if (max < min) {
			return value < min ? min : value;
		}
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}

	/**
	 * Sets horizontal scroll delay frames (ROM: Horiz_scroll_delay_val).
	 * When delay > 0, horizontal scroll uses position history while vertical scroll
	 * continues normally. This matches ROM behavior where ScrollHoriz checks
	 * Horiz_scroll_delay_val but ScrollVerti does not.
	 *
	 * @param delayFrames Number of frames to delay horizontal scroll (0 to clear)
	 */
	public void setHorizScrollDelay(int delayFrames) {
		this.horizScrollDelayFrames = delayFrames;
	}

	/**
	 * @return Current horizontal scroll delay frames remaining
	 */
	public int getHorizScrollDelay() {
		return horizScrollDelayFrames;
	}

	/**
	 * Sets full camera freeze (both X and Y).
	 * Use this for death, cutscenes, boss arenas, etc. where the camera should
	 * completely stop following the player.
	 *
	 * For spindash-style horizontal-only delay, use setHorizScrollDelay() instead.
	 *
	 * @param frozen true to freeze camera, false to unfreeze
	 */
	public void setFrozen(boolean frozen) {
		this.frozen = frozen;
		// When unfreezing, also clear any horizontal delay
		if (!frozen) {
			this.horizScrollDelayFrames = 0;
		}
	}

	/**
	 * @return true if camera is fully frozen (both X and Y)
	 */
	public boolean getFrozen() {
		return frozen;
	}

	/**
	 * Sets the level-started flag (ROM: Level_started_flag).
	 * This flag is used by level/HUD flow and intro state logic; it does not
	 * directly control camera scrolling.
	 *
	 * @param levelStarted true when level is considered started
	 */
	public void setLevelStarted(boolean levelStarted) {
		this.levelStarted = levelStarted;
	}

	/**
	 * @return true if Level_started_flag is set
	 */
	public boolean isLevelStarted() {
		return levelStarted;
	}

	/**
	 * Updates boundary easing - call once per frame.
	 * ROM behavior from RunDynamicLevelEvents (s2.asm:20297-20332):
	 * - Eases maxY toward target at 2px/frame (or 8px if accelerated)
	 * - When decreasing: if camera Y > target, snap maxY to camera Y first, then subtract
	 * - When increasing: if camera Y+8 >= maxY AND player airborne, use 4x speed (8px/frame)
	 * - Sets maxYChanging flag while boundary is transitioning
	 */
	public void updateBoundaryEasing() {
		maxYChanging = false;

		// Ease maxY toward target (ROM: s2.asm:20303-20332)
		if (maxY != maxYTarget) {
			short step = BOUNDARY_EASE_STEP; // d1 = 2
			short diff = (short) (maxYTarget - maxY);

			if (diff < 0) {
				// Decreasing max Y (target < current) - ROM lines 20308-20316
				step = (short) -BOUNDARY_EASE_STEP; // neg.w d1

				// If camera Y > target, snap maxY to camera Y first
				if (y > maxYTarget) {
					maxY = (short) (y & 0xFFFE); // Align to even pixels
				}
				// Always add step (subtract 2) after potential snap
				maxY += step;
			} else {
				// Increasing max Y (target > current) - ROM lines 20320-20331
				// Check for acceleration: camera Y + 8 >= maxY AND player airborne
				if (focusedSprite != null && (y + 8) >= maxY && focusedSprite.getAir()) {
					step = (short) (BOUNDARY_EASE_STEP * 4); // 8 pixels/frame
				}
				maxY += step;
			}

			// Clamp to target if we overshot
			if ((diff > 0 && maxY > maxYTarget) || (diff < 0 && maxY < maxYTarget)) {
				maxY = maxYTarget;
			}

			maxYChanging = true;
		}

		// Ease minY toward target (simple 2px/frame, no acceleration)
		if (minY != minYTarget) {
			short diff = (short) (minYTarget - minY);
			if (diff > 0) {
				minY += Math.min(diff, BOUNDARY_EASE_STEP);
			} else {
				minY += Math.max(diff, -BOUNDARY_EASE_STEP);
			}
		}

		// Ease maxX toward target
		if (maxX != maxXTarget) {
			short diff = (short) (maxXTarget - maxX);
			if (diff > 0) {
				maxX += Math.min(diff, BOUNDARY_EASE_STEP);
			} else {
				maxX += Math.max(diff, -BOUNDARY_EASE_STEP);
			}
		}

		// Ease minX toward target
		if (minX != minXTarget) {
			short diff = (short) (minXTarget - minX);
			if (diff > 0) {
				minX += Math.min(diff, BOUNDARY_EASE_STEP);
			} else {
				minX += Math.max(diff, -BOUNDARY_EASE_STEP);
			}
		}
	}

	/**
	 * Returns true if maxY is currently easing toward its target.
	 * ROM: Camera_Max_Y_Pos_Changing flag
	 */
	public boolean isMaxYChanging() {
		return maxYChanging;
	}

	public boolean isOnScreen(Sprite sprite) {
		int xLower = x;
		int yLower = y;
		int xUpper = x + width;
		int yUpper = y + height;
		int spriteX = sprite.getX();
		int spriteY = sprite.getY();
		return spriteX >= xLower && spriteY >= yLower && spriteX <= xUpper
				&& spriteY <= yUpper;
	}

	public void setFocusedSprite(AbstractPlayableSprite sprite) {
		this.focusedSprite = sprite;
		x = sprite.getX();
		y = sprite.getY();
	}

	public AbstractPlayableSprite getFocusedSprite() {
		return focusedSprite;
	}

	public short getX() {
		return x;
	}

	public void setX(short x) {
		this.x = x;
	}

	public short getY() {
		return y;
	}

	public void setY(short y) {
		this.y = y;
	}

	/**
	 * Sets the screen shake offsets (ROM: applied to Camera_X_pos_copy and Camera_Y_pos_copy).
	 * These offsets are used by the rendering system to shake both foreground tiles and sprites.
	 *
	 * @param x horizontal shake offset in pixels
	 * @param y vertical shake offset in pixels
	 */
	public void setShakeOffsets(int x, int y) {
		this.shakeOffsetX = (short) x;
		this.shakeOffsetY = (short) y;
	}

	/**
	 * @return Current horizontal shake offset in pixels
	 */
	public short getShakeOffsetX() {
		return shakeOffsetX;
	}

	/**
	 * @return Current vertical shake offset in pixels
	 */
	public short getShakeOffsetY() {
		return shakeOffsetY;
	}

	/**
	 * @return Camera X position with shake offset applied (for rendering)
	 */
	public short getXWithShake() {
		return (short) (x + shakeOffsetX);
	}

	/**
	 * @return Camera Y position with shake offset applied (for rendering)
	 */
	public short getYWithShake() {
		return (short) (y + shakeOffsetY);
	}

	public short getWidth() {
		return width;
	}

	public short getHeight() {
		return height;
	}

	public short getMinX() {
		return minX;
	}

	/**
	 * Sets minX immediately (both current and target).
	 * Use setMinXTarget() for smooth easing.
	 */
	public void setMinX(short minX) {
		this.minX = minX;
		this.minXTarget = minX;
	}

	/**
	 * Sets minX target for smooth easing.
	 * Current minX will ease toward this value at 2px/frame.
	 */
	public void setMinXTarget(short minXTarget) {
		this.minXTarget = minXTarget;
	}

	public short getMinXTarget() {
		return minXTarget;
	}

	public short getMinY() {
		return minY;
	}

	/**
	 * Sets minY immediately (both current and target).
	 * Use setMinYTarget() for smooth easing.
	 *
	 * ROM: When minY is negative (e.g. LZ3 top=0xFF00=-256), vertical wrapping
	 * is automatically enabled (DeformLayers.asm lines 542-580).
	 */
	public void setMinY(short minY) {
		this.minY = minY;
		this.minYTarget = minY;
		this.verticalWrapEnabled = (minY < 0);
	}

	/**
	 * Sets minY target for smooth easing.
	 * Current minY will ease toward this value at 2px/frame.
	 */
	public void setMinYTarget(short minYTarget) {
		this.minYTarget = minYTarget;
	}

	public short getMinYTarget() {
		return minYTarget;
	}

	public short getMaxX() {
		return maxX;
	}

	/**
	 * Sets maxX immediately (both current and target).
	 * Use setMaxXTarget() for smooth easing.
	 */
	public void setMaxX(short maxX) {
		this.maxX = maxX;
		this.maxXTarget = maxX;
	}

	/**
	 * Sets maxX target for smooth easing.
	 * Current maxX will ease toward this value at 2px/frame.
	 */
	public void setMaxXTarget(short maxXTarget) {
		this.maxXTarget = maxXTarget;
	}

	public short getMaxXTarget() {
		return maxXTarget;
	}

	public short getMaxY() {
		return maxY;
	}

	/**
	 * Sets maxY immediately (both current and target).
	 * Use setMaxYTarget() for smooth easing.
	 */
	public void setMaxY(short maxY) {
		this.maxY = maxY;
		this.maxYTarget = maxY;
	}

	/**
	 * Sets maxY target for smooth easing.
	 * Current maxY will ease toward this value at 2px/frame.
	 * ROM: Camera_Max_Y_pos_target
	 */
	public void setMaxYTarget(short maxYTarget) {
		this.maxYTarget = maxYTarget;
	}

	public short getMaxYTarget() {
		return maxYTarget;
	}

	public void incrementX(short amount) {
		x += amount;
	}

	public void incrementY(short amount) {
		y += amount;
	}

	/**
	 * Gets the current Y position bias (ROM: Camera_Y_pos_bias).
	 * Default is 96. Used as the vertical target position for camera centering.
	 * @return Current Y position bias value
	 */
	public short getYPosBias() {
		return yPosBias;
	}

	/**
	 * Sets the Y position bias (ROM: Camera_Y_pos_bias).
	 * When bias != 96, grounded vertical scroll uses slower 2px/frame cap.
	 * Used by looking up/down mechanics and spindash release.
	 * @param yPosBias New bias value (default is 96)
	 */
	public void setYPosBias(short yPosBias) {
		this.yPosBias = yPosBias;
	}

	/**
	 * Resets Y position bias to the default value (96).
	 * ROM: Called during Obj01_ResetScr equivalents - rolling, spindash release, jumping.
	 * The bias gradually eases back to 96 at 2px/frame (4 toward, 2 back = net 2).
	 */
	public void resetYBias() {
		// ROM: Obj01_ResetScr_Part2 / Obj01_Jump_ResetScr
		// The actual reset is gradual: if bias < 96, add 4 then subtract 2 (net +2)
		// if bias > 96, just subtract 2
		// This method initiates the reset process - actual easing happens in updateYBiasEasing()
		this.yPosBias = DEFAULT_Y_BIAS;
	}

	/**
	 * Gradually increases bias toward the look-up target (0xC8 = 200).
	 * ROM: s2.asm:36406-36408 - adds 2 to bias each frame until reaching 0xC8.
	 * Call this each frame while looking up AND look delay counter has elapsed.
	 */
	public void incrementLookUpBias() {
		if (yPosBias < LOOK_UP_BIAS) {
			yPosBias += 2;
			if (yPosBias > LOOK_UP_BIAS) {
				yPosBias = LOOK_UP_BIAS;
			}
		}
	}

	/**
	 * Gradually decreases bias toward the look-down target (8).
	 * ROM: s2.asm:36420-36422 - subtracts 2 from bias each frame until reaching 8.
	 * Call this each frame while looking down AND look delay counter has elapsed.
	 */
	public void decrementLookDownBias() {
		if (yPosBias > LOOK_DOWN_BIAS) {
			yPosBias -= 2;
			if (yPosBias < LOOK_DOWN_BIAS) {
				yPosBias = LOOK_DOWN_BIAS;
			}
		}
	}

	/**
	 * Gradually eases bias back toward the default value (96).
	 * ROM: s2.asm:36431-36438 (Obj01_ResetScr_Part2)
	 * - If bias < 96: add 4, then subtract 2 (net +2 per frame)
	 * - If bias > 96: subtract 2
	 * Call this each frame when not actively panning.
	 */
	public void easeYBiasToDefault() {
		if (yPosBias < DEFAULT_Y_BIAS) {
			// ROM: addq.w #4, subq.w #2 = net +2, no intermediate clamp
			// (s2.asm:36431-36438, Obj01_ResetScr_Part2)
			yPosBias += 4;
			yPosBias -= 2;
		} else if (yPosBias > DEFAULT_Y_BIAS) {
			// ROM: subq.w #2
			yPosBias -= 2;
			if (yPosBias < DEFAULT_Y_BIAS) {
				yPosBias = DEFAULT_Y_BIAS;
			}
		}
	}

	/**
	 * @deprecated Use incrementLookUpBias() for ROM-accurate gradual adjustment.
	 * Sets the bias instantly for looking up (ROM target: 0xC8 = 200).
	 */
	@Deprecated
	public void setLookUpBias() {
		this.yPosBias = LOOK_UP_BIAS;
	}

	/**
	 * @deprecated Use decrementLookDownBias() for ROM-accurate gradual adjustment.
	 * Sets the bias instantly for looking down/crouching (ROM target: 8).
	 */
	@Deprecated
	public void setLookDownBias() {
		this.yPosBias = LOOK_DOWN_BIAS;
	}

	/**
	 * Gets the default Y bias value.
	 * @return Default Y bias (96)
	 */
	public static short getDefaultYBias() {
		return DEFAULT_Y_BIAS;
	}

	/**
	 * Gets the look up bias target value.
	 * @return Look up bias (200 / 0xC8)
	 */
	public static short getLookUpBias() {
		return LOOK_UP_BIAS;
	}

	/**
	 * Gets the look down bias target value.
	 * @return Look down bias (8)
	 */
	public static short getLookDownBias() {
		return LOOK_DOWN_BIAS;
	}

	/**
	 * @return true if vertical wrapping is active (LZ3/SBZ2 loop sections)
	 */
	public boolean isVerticalWrapEnabled() {
		return verticalWrapEnabled;
	}

	/**
	 * @return true if a vertical wrap occurred during the last updatePosition() call
	 */
	public boolean didWrapLastFrame() {
		return lastFrameWrapped;
	}

	/**
	 * @return the Y delta applied by wrapping last frame (e.g. -0x800 for downward wrap), or 0
	 */
	public short getWrapDeltaY() {
		return wrapDeltaY;
	}

	/**
	 * @return the vertical wrap range (0x800 = 2048 pixels)
	 */
	public static int getVerticalWrapRange() {
		return VERTICAL_WRAP_RANGE;
	}

	/**
	 * @return the BG Y mask for vertical wrapping (0x3FF)
	 */
	public static int getVerticalWrapBgMask() {
		return VERTICAL_WRAP_BG_MASK;
	}

	public static synchronized Camera getInstance() {
		var runtime = RuntimeManager.getCurrent();
		if (runtime != null) {
			return runtime.getCamera();
		}
		if (bootstrapInstance == null) {
			bootstrapInstance = new Camera();
		}
		return bootstrapInstance;
	}

	/**
	 * Resets mutable state without destroying the singleton instance.
	 * Preserves width/height (configuration), clears all runtime state.
	 */
	public void resetState() {
		x = 0;
		y = 0;
		minX = 0;
		minY = 0;
		maxX = 0;
		maxY = 0;
		shakeOffsetX = 0;
		shakeOffsetY = 0;
		minXTarget = 0;
		minYTarget = 0;
		maxXTarget = 0;
		maxYTarget = 0;
		maxYChanging = false;
		horizScrollDelayFrames = 0;
		frozen = false;
		levelStarted = true;
		focusedSprite = null;
		yPosBias = DEFAULT_Y_BIAS;
		verticalWrapEnabled = false;
		lastFrameWrapped = false;
		wrapDeltaY = 0;
	}

}
