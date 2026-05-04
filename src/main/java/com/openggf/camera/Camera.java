package com.openggf.camera;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;

public class Camera {
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

	// ROM: Vertical wrapping — coordinates wrap modularly when top boundary is negative.
	// S1 LZ3/SBZ2: range 0x800 (DeformLayers.asm lines 542-580)
	// S3K zones with negative minY: range = level height (e.g. 0x1000 for MGZ1's 32-row map)
	private boolean verticalWrapEnabled = false;
	private int verticalWrapRange = 0x800;     // Default S1 range; overridden per-level
	private int verticalWrapMask = 0x7FF;      // Range - 1
	public static final int VERTICAL_WRAP_RANGE = 0x800;  // S1 default; referenced by LevelManager and GraphicsManager
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

	// ROM: Maximum per-frame camera step used by the fast vertical paths and by
	// the horizontal catch-up clamp in ScrollHoriz / MoveCameraX.
	// S1/S2: 16 (0x10) pixels/frame.
	// S3K:   24 (0x18) pixels/frame.
	// Set per-game via setFastScrollCap().
	private static final short DEFAULT_FAST_SCROLL_CAP = 16;
	private short fastScrollCap = DEFAULT_FAST_SCROLL_CAP;

	public Camera() {
		this(GameServices.configuration());
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
			// Position camera using ROM's level-load formula:
			//   v_screenposx = MainCharacter.x_pos - $A0  (subi.w #160,d1)
			//   v_screenposy = MainCharacter.y_pos - $60  (subi.w  #96,d0)
			// then clamp to the level bounds. References: s1disasm
			// _inc/LevelSizeLoad & BgScrollSpeed.asm:111,124; s2.asm:14787,14798;
			// sonic3k.asm:38241. ROM places the sprite at screen-x=160 (right edge
			// of the 144-160 horizontal scroll deadzone), not the deadzone
			// midpoint at 152.
			x = (short) (focusedSprite.getCentreX() - 160);
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
		x = computeNextHorizontalCameraX(true);

		// Vertical scroll - always uses current position (ROM: ScrollVerti has no delay)
		// ROM: d0 = (v_player+obY).w - (v_screenposy).w
		// When vertical wrapping is active, compute using modular arithmetic to handle
		// cases where Sonic and camera are in different wrap periods (e.g. Sonic's Y was
		// updated by ground collision across the wrap boundary between frames).
		short focusedSpriteRealY;
		if (verticalWrapEnabled) {
			int diff = (int) focusedSprite.getCentreY() - (int) y;
			diff = ((diff % verticalWrapRange) + verticalWrapRange) % verticalWrapRange;
			if (diff > verticalWrapRange / 2) {
				diff -= verticalWrapRange;
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

		// Vertical scroll logic (ROM: ScrollVerti)
		if (focusedSprite.getAir()) {
			// ROM: Airborne uses ±0x20 window around bias
			// Upper bound: bias - 32, Lower bound: bias + 32
			short upperBound = (short) (yPosBias - AIRBORNE_WINDOW_HALF);
			short lowerBound = (short) (yPosBias + AIRBORNE_WINDOW_HALF);
			if (focusedSpriteRealY < upperBound) {
				short difference = (short) (focusedSpriteRealY - upperBound);
				if (difference < -fastScrollCap) {
					y -= fastScrollCap;
				} else {
					y += difference;
				}
			} else if (focusedSpriteRealY >= lowerBound) {
				short difference = (short) (focusedSpriteRealY - lowerBound);
				if (difference > fastScrollCap) {
					y += fastScrollCap;
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
						// S2: 16px cap, S3K: 24px cap
						tolerance = fastScrollCap;
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
				y = (short) (y & verticalWrapMask);
				if (focusedSprite != null) {
					focusedSprite.setCentreY((short) (focusedSprite.getCentreY() & verticalWrapMask));
				}
				lastFrameWrapped = true;
				wrapDeltaY = (short) (y - oldY);
			}
			// Downward wrap: camera Y reached bottom boundary
			// ROM: cmp.w (Camera_Max_Y_pos).w,d1 / blt.s .nodownwrap / sub.w d0,y
			else if (y >= verticalWrapRange) {
				short oldY = y;
				y = (short) (y - verticalWrapRange);
				if (focusedSprite != null) {
					focusedSprite.setCentreY((short) (focusedSprite.getCentreY() & verticalWrapMask));
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

	/**
	 * Predicts the horizontal camera position that {@link #updatePosition()} will
	 * commit on this frame without consuming scroll-delay history.
	 * This lets event scripts reason about end-of-frame camera thresholds while
	 * preserving the actual camera state for the later camera step.
	 */
	public short previewNextX() {
		if (focusedSprite == null || frozen) {
			return x;
		}
		return computeNextHorizontalCameraX(false);
	}

	private short computeNextHorizontalCameraX(boolean consumeDelayState) {
		short nextX = x;
		short focusedSpriteRealX;
		if (horizScrollDelayFrames > 0) {
			// ROM: MoveCameraX stores the delay count in the high byte of
			// H_scroll_frame_offset and subtracts $100 before sampling Pos_table.
			// Our history buffer is also one frame behind by the time camera scroll
			// runs, so delay N maps to the buffered position from N-1 frames ago.
			int historyIndex = Math.max(0, Math.min(horizScrollDelayFrames - 1, 63));
			focusedSpriteRealX = (short) (focusedSprite.getCentreX(historyIndex) - nextX);
			if (consumeDelayState) {
				horizScrollDelayFrames--;
			}
		} else {
			focusedSpriteRealX = (short) (focusedSprite.getCentreX() - nextX);
		}

		short cameraStepCap = fastScrollCap;

		// Horizontal scroll logic (ROM: ScrollHoriz / MoveCameraX).
		if (focusedSpriteRealX < 144) {
			short difference = (short) (focusedSpriteRealX - 144);
			if (difference < -cameraStepCap) {
				nextX -= cameraStepCap;
			} else {
				nextX += difference;
			}
		} else if (focusedSpriteRealX > 160) {
			short difference = (short) (focusedSpriteRealX - 160);
			if (difference > cameraStepCap) {
				nextX += cameraStepCap;
			} else {
				nextX += difference;
			}
		}

		return clampAxisWithWrap(nextX, minX, maxX);
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

	/**
	 * Computes the current-frame BuildSprites visibility that feeds
	 * {@code render_flags.on_screen}.
	 * <p>S3K {@code Render_Sprites} (sonic3k.asm:36336) does:
	 * <pre>
	 *   d1 = (y_pos - Camera_Y) + height_pixels  ; height_pixels = 0x18 = 24
	 *   d1 &= Screen_Y_wrap_value                ; default 0xFFFF (no mask)
	 *   if d1 &gt;= 2*height_pixels + 224:           ; threshold = 272
	 *       off-screen
	 * </pre>
	 * With the default {@code Screen_Y_wrap_value = 0xFFFF}, this is equivalent
	 * to {@code relY in [-24, 248)} — i.e., Y margin = {@code height_pixels = 24}
	 * symmetrically, NOT 32.
	 * <p>S1/S2 don't have a {@code Screen_Y_wrap_value} mechanism and the ROM
	 * routines use slightly different margins. Gate the S3K-specific 24-margin
	 * via {@link com.openggf.game.PhysicsFeatureSet#useScreenYWrapValueForVisibility()}
	 * so existing S1/S2 traces keep their 32-margin behaviour.
	 */
	public boolean isVisibleForRenderFlag(AbstractPlayableSprite sprite) {
		int widthPixels = sprite.getRenderFlagWidthPixels();
		int relX = sprite.getRenderCentreX() - x;
		if (relX + widthPixels < 0 || relX - widthPixels >= width) {
			return false;
		}
		int relY = sprite.getRenderCentreY() - y;
		com.openggf.game.PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
		boolean useS3kMargin = fs != null && fs.useScreenYWrapValueForVisibility();
		int yMargin = useS3kMargin ? widthPixels : 32;
		return relY >= -yMargin && relY < height + yMargin;
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
	 * ROM: In S1, negative minY (e.g. LZ3 top=0xFF00=-256) indicates vertical
	 * wrapping (DeformLayers.asm lines 542-580). S3K zones can have negative
	 * minY without wrapping (e.g. MGZ1 minY=-$100 for falling intro headroom).
	 * Use {@link #setVerticalWrapEnabled(boolean)} to control wrapping explicitly.
	 */
	public void setMinY(short minY) {
		this.minY = minY;
		this.minYTarget = minY;
	}

	/**
	 * Explicitly enables or disables vertical wrapping.
	 * When enabled, the camera and player Y coordinates wrap at the given range.
	 *
	 * @param enabled whether to enable vertical wrapping
	 * @param range   wrap range in pixels (must be a power of 2; e.g. 0x800 for S1, 0x1000 for S3K 32-row levels)
	 */
	public void setVerticalWrapEnabled(boolean enabled, int range) {
		this.verticalWrapEnabled = enabled;
		if (enabled && range > 0) {
			this.verticalWrapRange = range;
			this.verticalWrapMask = range - 1;
		}
	}

	/**
	 * Convenience overload that uses the default S1 range (0x800).
	 */
	public void setVerticalWrapEnabled(boolean enabled) {
		setVerticalWrapEnabled(enabled, VERTICAL_WRAP_RANGE);
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
	 * @return the current vertical wrap range for this camera instance
	 */
	public int getVerticalWrapRange() {
		return verticalWrapRange;
	}

	/**
	 * @return the BG Y mask for vertical wrapping (0x3FF)
	 */
	public static int getVerticalWrapBgMask() {
		return VERTICAL_WRAP_BG_MASK;
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
		fastScrollCap = DEFAULT_FAST_SCROLL_CAP;
		verticalWrapEnabled = false;
		verticalWrapRange = VERTICAL_WRAP_RANGE;
		verticalWrapMask = VERTICAL_WRAP_RANGE - 1;
		lastFrameWrapped = false;
		wrapDeltaY = 0;
	}

	/**
	 * Sets the maximum vertical scroll speed for airborne and fast-ground paths.
	 * ROM: S2 uses 16 (0x10), S3K uses 24 (0x18).
	 * (s2.asm:18189-18190 ".doScroll_fast"; sonic3k.asm:loc_1C1B0)
	 *
	 * @param cap scroll cap in pixels per frame (16 for S1/S2, 24 for S3K)
	 */
	public void setFastScrollCap(int cap) {
		this.fastScrollCap = (short) cap;
	}

	/** Returns the current fast vertical scroll cap in pixels/frame. */
	public int getFastScrollCap() {
		return fastScrollCap;
	}

}
