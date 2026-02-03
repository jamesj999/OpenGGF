package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.audio.GameSound;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AnimationIds;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.*;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.physics.Direction;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CNZ LauncherSpring Object (Obj85).
 * <p>
 * An interactive pressure spring that launches the player with variable force based on compression.
 * Unlike regular springs, the player lands on this spring, is locked in place (rolling state),
 * and can hold the jump button to compress it. Releasing launches with velocity based on compression.
 * <p>
 * Subtypes:
 * <ul>
 *   <li><b>0x00</b>: Vertical spring (launches straight up)</li>
 *   <li><b>0x01-0x7F</b>: Diagonal spring with normal airborne launch</li>
 *   <li><b>0x80-0xFF</b>: Diagonal spring with "slope running" mode (bit 7 = grounded launch)</li>
 * </ul>
 * <p>
 * Launch velocity formula: (compression + base) * 128
 * <ul>
 *   <li>Vertical: base = 0x10 (16), max compression = 0x20 (32)</li>
 *   <li>Diagonal: base = 0x04 (4), max compression = 0x1C (28)</li>
 * </ul>
 * <p>
 * <b>Disassembly Reference:</b> s2.asm obj85 (loc_2AD26 - loc_2AE76)
 */
public class LauncherSpringObjectInstance extends BoxObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // Subtype constants
    // Note: Bit 7 (0x80) indicates "slope running" mode for diagonal springs,
    // but ANY non-zero subtype is treated as diagonal (ROM: tst.b subtype(a0))
    private static final int SUBTYPE_SLOPE_MODE_FLAG = 0x80;

    // State constants (match ROM objoff_36/objoff_37 values)
    private static final int STATE_EMPTY = 0;
    private static final int STATE_STANDING = 1;
    private static final int STATE_LAUNCH_PENDING = 2;

    // Physics constants from disassembly
    private static final int VERTICAL_BASE_VELOCITY = 0x10;      // Base for vertical launch
    private static final int VERTICAL_MAX_COMPRESSION = 0x20;    // Max compression (32)
    private static final int DIAGONAL_BASE_VELOCITY = 0x04;      // Base for diagonal launch
    private static final int DIAGONAL_MAX_COMPRESSION = 0x1C;    // Max compression (28)
    private static final int COMPRESSION_FRAME_INTERVAL = 4;     // Frames between compression increments

    // Vertical spring position offsets
    private static final int VERTICAL_PLAYER_Y_OFFSET = 0x2E;    // 46 pixels above spring Y

    // Diagonal spring position offsets
    private static final int DIAGONAL_PLAYER_X_OFFSET = 0x13;    // 19 pixels from spring X
    private static final int DIAGONAL_PLAYER_Y_OFFSET = 0x13;    // 19 pixels above spring Y

    /**
     * Per-player state tracking.
     * ROM uses objoff_36 (byte) for Player 1 and objoff_37 (byte) for Player 2,
     * both stored within the same word. This allows both players to interact
     * with the spring simultaneously.
     */
    private static class PlayerState {
        int state = STATE_EMPTY;
        int launchCooldown = 0;
    }

    // Per-player state map (ROM: objoff_36 for P1, objoff_37 for P2)
    private final Map<AbstractPlayableSprite, PlayerState> playerStates = new HashMap<>();

    // Shared spring state (affects all players equally)
    private int compression = 0;
    private int compressionFrameCounter = 0;
    private int mainSpriteFrame = 1;  // Main sprite frame (1 or 5 for vibration toggle)
    private int animationTimer = 0;  // For frame toggling animation
    private int baseX;  // Original X position (for diagonal movement)
    private int baseY;  // Original Y position (for spring movement)

    /**
     * ROM objoff_3A: Flag to prevent multiple compression increments per frame.
     * ROM clears this at the start of each update cycle (lines 57452, 57606),
     * then sets it when ANY player compresses. This prevents both P1 and P2
     * from incrementing compression in the same frame.
     */
    private boolean compressionProcessedThisFrame = false;

    // Rendering state
    private int currentSpriteX;
    private int currentSpriteY;

    public LauncherSpringObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name, 16, 16, 0.8f, 0.4f, 0.6f, false);
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.currentSpriteX = spawn.x();
        this.currentSpriteY = spawn.y();
    }

    /**
     * Checks if this is a diagonal spring.
     * ROM behavior (s2.asm:57411-57421): ANY non-zero subtype is treated as diagonal.
     * The ROM uses "tst.b subtype(a0)" which checks for zero vs non-zero.
     */
    private boolean isDiagonal() {
        return spawn.subtype() != 0;
    }

    /**
     * Checks if this is a left-facing spring (for diagonal mode, uses render flags).
     */
    private boolean isLeftFacing() {
        return (spawn.renderFlags() & 0x1) != 0;
    }

    /**
     * Gets the maximum compression for this spring type.
     */
    private int getMaxCompression() {
        return isDiagonal() ? DIAGONAL_MAX_COMPRESSION : VERTICAL_MAX_COMPRESSION;
    }

    /**
     * Gets the base velocity for this spring type.
     */
    private int getBaseVelocity() {
        return isDiagonal() ? DIAGONAL_BASE_VELOCITY : VERTICAL_BASE_VELOCITY;
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (player == null) {
            return;
        }

        PlayerState ps = playerStates.computeIfAbsent(player, k -> new PlayerState());

        // Skip if player is in cooldown
        if (ps.launchCooldown > 0) {
            return;
        }

        // ROM: cmpi.b #4,routine(a1) - skip if hurt/dead (routine >= 4)
        if (player.isHurt() || player.getDead()) {
            return;
        }

        // Only handle initial capture - compression is handled in update()
        // This avoids issues where moving spring causes inconsistent contact callbacks
        if (ps.state == STATE_EMPTY) {
            // ROM behavior (lines 57673-57679):
            // 1. Check standing bit - if standing, capture player
            // 2. For diagonal springs: if not standing, check pushing bit
            // 3. If pushing into diagonal spring from side, convert to standing (capture)
            boolean shouldCapture = contact.standing() && player.getYSpeed() >= 0;

            // Diagonal springs can also capture players who push into them from the side
            // ROM: btst pushing_bit / bset standing_bit - converts side push to standing
            if (!shouldCapture && isDiagonal() && contact.pushing()) {
                shouldCapture = true;
            }

            if (shouldCapture) {
                enterSpring(player, ps);
            }
        }
    }

    /**
     * Called when player first lands on the spring.
     * Locks controls and sets rolling state (ROM: loc_2AD26).
     */
    private void enterSpring(AbstractPlayableSprite player, PlayerState ps) {
        // Lock player controls (obj_control = 0x81 in ROM)
        // ROM sets obj_control = $81 which has TWO effects:
        // - Bit 0 (0x01): Blocks input (controlLocked)
        // - Bit 7 (0x80): Skips ALL movement/physics (objectControlled)
        player.setControlLocked(true);
        player.setObjectControlled(true);
        player.setPinballMode(true);

        // Snap player to center of spring (use setCentreX/Y for ROM-compatible center coords)
        if (isDiagonal()) {
            // ROM: ALWAYS adds 0x13 to X (player positioned to the RIGHT of head)
            // H-flip only affects visual rendering, not player positioning
            player.setCentreX((short) (currentSpriteX + DIAGONAL_PLAYER_X_OFFSET));
            player.setCentreY((short) (currentSpriteY - DIAGONAL_PLAYER_Y_OFFSET));
        } else {
            player.setCentreX((short) currentSpriteX);
            player.setCentreY((short) (currentSpriteY - VERTICAL_PLAYER_Y_OFFSET));
        }

        // Clear velocities
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);

        // Set rolling state and animation
        // ROM: bset #status.player.rolling,status(a1)
        //      move.b #$E,y_radius(a1)
        //      move.b #7,x_radius(a1)
        //      move.b #AniIDSonAni_Roll,anim(a1)
        boolean wasRolling = player.getRolling();
        player.setRolling(true);
        if (!wasRolling) {
            player.setY((short) (player.getY() + player.getRollHeightAdjustment()));
        }
        // Explicitly set roll animation (ROM: move.b #AniIDSonAni_Roll,anim(a1))
        player.setAnimationId(Sonic2AnimationIds.ROLL);

        ps.state = STATE_STANDING;

        // Reset compression only if no other player is on the spring
        if (!hasAnyPlayerOnSpring()) {
            compression = 0;
            compressionFrameCounter = 0;
        }
        animationTimer = getMaxCompression() / 2;  // Initialize timer for animation
    }

    /**
     * Checks if any player is currently standing on or launching from the spring.
     */
    private boolean hasAnyPlayerOnSpring() {
        for (PlayerState ps : playerStates.values()) {
            if (ps.state != STATE_EMPTY) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if any player in STANDING state is holding the jump button.
     * ROM (lines 57477-57492, 57636-57652): Before transitioning to launch state,
     * the ROM checks if ANY standing player is holding jump. If so, no launch occurs.
     *
     * @return true if any standing player is holding jump
     */
    private boolean isAnyStandingPlayerHoldingJump() {
        // ROM: Checks both P1 and P2 states and inputs, then ORs them together
        // cmpi.b #1,objoff_36(a0) / or.w (Ctrl_1_Logical).w,d0
        // cmpi.b #1,objoff_37(a0) / or.w (Ctrl_2).w,d0
        // andi.w #$7000,d0 / bne.s return (if any jump button held, don't launch)
        for (Map.Entry<AbstractPlayableSprite, PlayerState> entry : playerStates.entrySet()) {
            AbstractPlayableSprite player = entry.getKey();
            PlayerState ps = entry.getValue();
            if (ps.state == STATE_STANDING && player.isJumpPressed()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handles compression while player is standing on spring.
     * Compression increases every 4 frames while jump is held (ROM: loc_2AD96).
     *
     * ROM behavior (lines 57552-57562, 57719-57729):
     * - Check objoff_3A flag - if already set, skip compression increment
     * - Set objoff_3A flag to prevent double-increment when both players are on spring
     * - Decrement objoff_32 timer; if underflows, reset to 3 and increment compression
     *
     * Animation timer (ROM lines 57564-57577):
     * - The ROM only decrements the animation timer (objoff_33) when jump IS pressed
     * - This is handled here to match ROM behavior where animation only updates during compression
     *
     * @return true if this player is holding jump
     */
    private boolean handleCompression(AbstractPlayableSprite player, PlayerState ps) {
        boolean jumpPressed = player.isJumpPressed();

        if (jumpPressed) {
            // ROM: tst.b objoff_3A(a0) / bne.s loc_2ADFE - skip if already processed this frame
            if (!compressionProcessedThisFrame) {
                // ROM: move.b #1,objoff_3A(a0) - mark as processed
                compressionProcessedThisFrame = true;

                // ROM: subq.b #1,objoff_32(a0) / bpl.s loc_2ADDA
                compressionFrameCounter--;
                if (compressionFrameCounter < 0) {
                    // ROM: move.b #3,objoff_32(a0) - reset timer to 3 (4 frames total)
                    compressionFrameCounter = COMPRESSION_FRAME_INTERVAL - 1;
                    // ROM: cmpi.w #$20,objoff_38(a0) / beq.s loc_2ADDA / addq.w #1,objoff_38(a0)
                    if (compression < getMaxCompression()) {
                        compression++;
                    }
                }

                // Animation timer logic - only runs when jump IS pressed (ROM behavior)
                // ROM: subq.b #1,objoff_33(a0) / bpl.s loc_2ADF8
                animationTimer--;
                if (animationTimer < 0) {
                    // Timer underflow - toggle frame and reset interval
                    // ROM: bchg #2,mainspr_mapframe toggles bit 2 (1 <-> 5)
                    mainSpriteFrame = (mainSpriteFrame == 1) ? 5 : 1;
                    // Reset timer: faster at higher compression
                    animationTimer = Math.max(0, (getMaxCompression() - compression) / 2);
                } else {
                    // ROM: loc_2ADF8 - when timer >= 0, unconditionally reset to frame 1
                    mainSpriteFrame = 1;
                }
            }
        }
        return jumpPressed;
    }

    /**
     * Updates the spring's visual position based on compression.
     *
     * ROM behavior: The diagonal spring head ALWAYS moves LEFT and DOWN during compression,
     * regardless of H-flip state. The H-flip flag only affects visual sprite rendering,
     * NOT the physics/position calculations.
     *
     * ROM: sub.w d0,x_pos(a0) - ALWAYS subtracts (moves head LEFT)
     * ROM: add.w d0,y_pos(a0) - ALWAYS adds (moves head DOWN)
     */
    private void updateSpringPosition() {
        if (isDiagonal()) {
            // Diagonal spring moves diagonally when compressed
            // ROM: ALWAYS subtracts from X (head moves LEFT toward base)
            // H-flip only affects visual rendering, not position
            int offset = compression / 2;
            currentSpriteX = baseX - offset;
            currentSpriteY = baseY + offset;
        } else {
            // Vertical spring moves down when compressed
            currentSpriteX = baseX;
            currentSpriteY = baseY + compression;
        }
    }

    /**
     * Updates the animation frame based on compression level.
     *
     * ROM behavior (s2.asm lines 57564-57577):
     * The animation timer only decrements when jump IS pressed (handled in handleCompression).
     * This method only handles the reset case when compression is zero.
     *
     * When compression == 0: Reset to frame 1 and initialize timer.
     * When compression > 0: Animation is handled in handleCompression() when jump is pressed.
     */
    private void updateAnimationFrame() {
        if (compression == 0) {
            mainSpriteFrame = 1;
            animationTimer = getMaxCompression() / 2;  // Initialize timer
        }
        // Animation toggle is now handled in handleCompression() when jump is pressed
    }

    /**
     * Launches the player with velocity based on compression.
     * ROM: loc_2AE0C (vertical) / loc_2B018 (diagonal)
     *
     * Diagonal springs have two modes based on subtype:
     * - Subtype 0x01-0x7F (bit 7 clear): Normal airborne launch
     * - Subtype 0x80-0xFF (bit 7 set): "Slope running" mode - grounded with angle 0xE0
     *
     * Both modes launch UP-RIGHT (positive X, negative Y velocities).
     * ROM: loc_2B018 - x_vel = +magnitude, y_vel = -magnitude
     */
    private void launchPlayer(AbstractPlayableSprite player, PlayerState ps) {
        int launchMagnitude = (compression + getBaseVelocity()) << 7;

        if (isDiagonal()) {
            // ROM: Diagonal spring ALWAYS launches UP-RIGHT regardless of H-flip
            // H-flip only affects visual rendering, not launch physics
            // ROM: loc_2B018 - x_vel = +magnitude, y_vel = -magnitude (ALWAYS)
            int xVel = launchMagnitude;
            int yVel = -launchMagnitude;

            player.setXSpeed((short) xVel);
            player.setYSpeed((short) yVel);

            // ROM: bset #status.player.in_air (set airborne first, may be cleared below)
            player.setAir(true);
            player.setGSpeed((short) 0x800);  // ROM: move.w #$800,inertia(a1)

            // ROM: tst.b subtype(a0) / bpl.s loc_2B068 - check bit 7
            // If bit 7 SET (subtype 0x80+): "slope running" mode
            if ((spawn.subtype() & SUBTYPE_SLOPE_MODE_FLAG) != 0) {
                // ROM: inertia = +magnitude (positive gSpeed = moving right along angle 0xE0)
                // The ROM does neg.w d0 twice, resulting in a positive value
                player.setGSpeed((short) launchMagnitude);
                // ROM: bclr #status.player.in_air - make grounded
                player.setAir(false);
                // ROM: move.b #-$20,angle(a1) - angle 0xE0 (ALWAYS, regardless of H-flip)
                player.setAngle((byte) 0xE0);
                // Keep pinballMode active so rolling is preserved even if physics
                // briefly loses ground contact during the high-speed diagonal launch.
                // This mimics ROM behavior where rolling state persists through the launch.
                player.setPinballMode(true);
                // Set stickToConvex to prevent slope repel from immediately triggering
                // airborne mode when there's no terrain at the angle initially.
                player.setStickToConvex(true);
            }

            // ROM: ALWAYS faces RIGHT after diagonal launch (regardless of H-flip)
            player.setDirection(Direction.RIGHT);
        } else {
            // Vertical launch - straight up, airborne
            // ROM: loc_2AE0C - sets y_vel negative, x_vel=0, sets in_air, inertia=$800
            player.setYSpeed((short) -launchMagnitude);
            player.setXSpeed((short) 0);
            player.setAir(true);
            player.setGSpeed((short) 0x800);  // ROM: move.w #$800,inertia(a1)
        }

        // ROM: bclr #status.player.on_object,status(a1) - clear "standing on object" flag
        // This is essential to prevent the solid object system from re-capturing the player
        player.setOnObject(false);

        // Clear solid object riding state to prevent the object system from
        // continuing to track the player's position relative to the spring.
        var objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager != null) {
            objectManager.clearRidingObject();
        }

        // ROM keeps player rolling after launch (rolling flag is set in enterSpring, never cleared)
        // Ensure rolling state is preserved for proper "rolling jump" animation
        player.setRolling(true);

        // Play launch sound
        playLaunchSound();

        // Set cooldown to prevent immediate re-capture
        ps.launchCooldown = 16;

        // Release player controls
        // For slope-mode launches, pass flag to preserve pinballMode
        boolean preservePinball = isDiagonal() && (spawn.subtype() & SUBTYPE_SLOPE_MODE_FLAG) != 0;
        releasePlayer(player, ps, preservePinball);
    }

    /**
     * Releases player controls and resets per-player spring state.
     *
     * @param player         The player to release
     * @param ps             The player state to reset
     * @param preservePinball If true, keeps pinballMode active (for slope-mode launches)
     */
    private void releasePlayer(AbstractPlayableSprite player, PlayerState ps, boolean preservePinball) {
        // ROM: move.b #0,obj_control(a1) clears all control bits
        player.setControlLocked(false);
        player.setObjectControlled(false);
        if (!preservePinball) {
            player.setPinballMode(false);
        }
        resetPlayerState(ps);
    }

    /**
     * Releases player controls and resets per-player spring state.
     * Clears pinballMode by default.
     */
    private void releasePlayer(AbstractPlayableSprite player, PlayerState ps) {
        releasePlayer(player, ps, false);
    }

    /**
     * Resets per-player state without modifying player or shared spring state.
     * Used when player enters debug mode, goes off-screen, or otherwise leaves unexpectedly.
     * Note: Compression is NOT zeroed here - it decays gradually in update().
     * ROM: loc_2AD14 - subq.w #4,objoff_38(a0) decreases compression by 4 per frame.
     */
    private void resetPlayerState(PlayerState ps) {
        ps.state = STATE_EMPTY;
        // Note: compression is shared and decays in update() when no players are on spring
    }

    /**
     * Resets shared spring animation state.
     * Called when all players have left the spring.
     */
    private void resetAnimationState() {
        compressionFrameCounter = 0;
        mainSpriteFrame = 1;
        animationTimer = getMaxCompression() / 2;
        // Note: currentSpriteX/Y are NOT reset here - they update based on compression in update()
    }

    /**
     * Plays the CNZ launch sound effect.
     */
    private void playLaunchSound() {
        try {
            if (AudioManager.getInstance() != null) {
                AudioManager.getInstance().playSfx(GameSound.CNZ_LAUNCH);
            }
        } catch (Exception e) {
            // Prevent audio failure from breaking game logic
        }
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        // Don't be solid for players already captured on this spring.
        // This prevents SolidContacts from re-detecting us and fighting
        // with our manual positioning during compression.
        PlayerState ps = playerStates.get(player);
        if (ps != null && ps.state != STATE_EMPTY) {
            return false;
        }
        return true;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // ROM values from disassembly:
        // Vertical: halfWidth=0x23(35), halfHeightTop=0x20(32), halfHeightBottom=0x1D(29)
        // Diagonal: halfWidth=0x23(35), halfHeightTop=0x08(8), halfHeightBottom=0x05(5)
        if (isDiagonal()) {
            return new SolidObjectParams(35, 8, 5);
        }
        return new SolidObjectParams(35, 32, 29);
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        Camera camera = Camera.getInstance();

        // ROM: move.b #0,objoff_3A(a0) - clear compression-processed flag at start of update
        // This allows ONE compression increment per frame across all players
        compressionProcessedThisFrame = false;

        // Process all tracked players (supports two-player mode)
        // ROM uses objoff_36 for P1 and objoff_37 for P2
        processPlayer(player, camera);

        // Also process sidekick if present (two-player support)
        AbstractPlayableSprite sidekick = SpriteManager.getInstance().getSidekick();
        if (sidekick != null && sidekick != player) {
            processPlayer(sidekick, camera);
        }

        // ROM behavior (lines 57477-57492, 57636-57652):
        // After processing both players, check if we should transition to launch state.
        // The ROM sets BOTH players to state 2 simultaneously (move.w #$202,objoff_36(a0))
        // if compression > 0 AND no standing player is holding jump.
        checkForLaunchTransition();

        // ROM: loc_2AD14 - When spring is empty, compression decays by 4 per frame
        // This creates a gradual decompression animation instead of instant snap
        if (!hasAnyPlayerOnSpring() && compression > 0) {
            // ROM: move.b #1,mainspr_mapframe(a0) - ALWAYS reset frame to 1 during decompression
            // This ensures smooth coil animation by resetting the head flash state
            mainSpriteFrame = 1;
            compression -= 4;
            if (compression < 0) {
                compression = 0;
            }
            // Reset animation state when spring becomes fully empty
            if (compression == 0) {
                resetAnimationState();
            }
        }

        // Update spring position based on current compression
        // This must happen after handleCompression/decay so player position matches spring
        updateSpringPosition();

        // Update position of all players on the spring
        for (Map.Entry<AbstractPlayableSprite, PlayerState> entry : playerStates.entrySet()) {
            AbstractPlayableSprite p = entry.getKey();
            PlayerState ps = entry.getValue();
            if (ps.state != STATE_EMPTY) {
                if (isDiagonal()) {
                    // ROM: ALWAYS adds 0x13 to X (player positioned to the RIGHT of head)
                    // H-flip only affects visual rendering, not player positioning
                    p.setCentreX((short) (currentSpriteX + DIAGONAL_PLAYER_X_OFFSET));
                    p.setCentreY((short) (currentSpriteY - DIAGONAL_PLAYER_Y_OFFSET));
                } else {
                    p.setCentreX((short) currentSpriteX);
                    p.setCentreY((short) (currentSpriteY - VERTICAL_PLAYER_Y_OFFSET));
                }
            }
        }

        // Update animation
        updateAnimationFrame();
    }

    /**
     * ROM behavior (lines 57477-57492, 57636-57652):
     * After processing both players individually, check if ALL standing players
     * have released the jump button. If so, and compression > 0, transition
     * ALL standing players to launch state simultaneously.
     *
     * ROM: move.w #$202,objoff_36(a0) - sets BOTH bytes to 2 at once
     */
    private void checkForLaunchTransition() {
        // ROM: tst.w objoff_36(a0) / beq.s loc_2AD14 - if no players on spring, skip
        if (!hasAnyPlayerOnSpring()) {
            return;
        }

        // ROM: tst.w objoff_38(a0) / beq.s return - if no compression, skip
        if (compression == 0) {
            return;
        }

        // ROM: Check if ANY standing player is holding jump
        // If any player is holding, don't trigger launch for anyone
        if (isAnyStandingPlayerHoldingJump()) {
            return;
        }

        // ROM: move.w #$202,objoff_36(a0) - set BOTH player states to 2 (launch pending)
        // This ensures both players launch simultaneously
        for (PlayerState ps : playerStates.values()) {
            if (ps.state == STATE_STANDING) {
                ps.state = STATE_LAUNCH_PENDING;
            }
        }
    }

    /**
     * Process a single player's interaction with the spring.
     * Handles state machine, off-screen checks, hurt/dead checks, and compression.
     */
    private void processPlayer(AbstractPlayableSprite player, Camera camera) {
        if (player == null) {
            return;
        }

        PlayerState ps = playerStates.computeIfAbsent(player, k -> new PlayerState());

        // Decrement launch cooldown
        if (ps.launchCooldown > 0) {
            ps.launchCooldown--;
        }

        // Skip processing if player is not on spring
        if (ps.state == STATE_EMPTY) {
            return;
        }

        // ROM: _btst #render_flags.on_screen,render_flags(a1) - release if off-screen
        // This prevents the player from getting stuck if the camera moves away
        if (camera != null && !camera.isOnScreen(player)) {
            player.setAir(true);  // ROM: bset #status.player.in_air
            releasePlayer(player, ps);
            return;
        }

        // ROM: cmpi.b #4,routine(a1) - skip if hurt/dead (routine >= 4)
        if (player.isHurt() || player.getDead()) {
            releasePlayer(player, ps);
            return;
        }

        // ROM state machine: Check for launch pending state FIRST (state 2 -> launch)
        // This ensures the launch happens on the frame AFTER button release was detected
        if (ps.state == STATE_LAUNCH_PENDING) {
            // State 2: Launch pending - ROM triggers launch when it sees this state
            // ROM: loc_2AD7A decrements state, state 2->1 means D0!=0, branches to launch
            launchPlayer(player, ps);
            return;
        }

        // Handle compression logic if player is standing on the spring (state 1)
        if (ps.state == STATE_STANDING) {
            // Check if player entered debug mode
            if (player.isDebugMode()) {
                releasePlayer(player, ps);
                return;
            }

            // Update compression state (tracks if jump is held, but doesn't transition state)
            // The transition to STATE_LAUNCH_PENDING is handled by checkForLaunchTransition()
            handleCompression(player, ps);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            super.appendRenderCommands(commands);
            return;
        }

        String artKey = isDiagonal() ? Sonic2ObjectArtKeys.LAUNCHER_SPRING_DIAG
                                     : Sonic2ObjectArtKeys.LAUNCHER_SPRING_VERT;
        PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
        if (renderer == null || !renderer.isReady()) {
            super.appendRenderCommands(commands);
            return;
        }

        boolean hFlip = isLeftFacing();
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;

        // ROM uses multi-sprite rendering:
        // Main sprite: body (frame 1 or 5 for vibration toggle) - moves with compression
        // Sub-sprite: plunger base (frame 2 or 3 based on compression) - stays at ORIGINAL position
        //
        // ROM behavior (obj85_a.asm / obj85_b.asm):
        // - Init sets sub2_y_pos ONCE to baseY + 0x20 (vertical) or baseY (diagonal)
        // - Only main sprite y_pos updates during compression
        // - Sub-sprite position is NEVER touched after initialization
        //
        // Render order: Sub-sprite FIRST (behind), main sprite SECOND (in front).
        // VDP hardware gives lower sprite indices higher priority - main sprite should be on top.

        // Sub-sprite: plunger base (frame 2 if compression < 0x10, frame 3 otherwise)
        // CRITICAL: Sub-sprite stays at ORIGINAL base position, not compressed position!
        // This creates the visual effect of the main body compressing toward the fixed plunger base.
        int subFrame = (compression >= 0x10) ? 3 : 2;
        if (isDiagonal()) {
            // Diagonal: sub-sprite at original base position
            renderer.drawFrameIndex(subFrame, baseX, baseY, hFlip, vFlip);
        } else {
            // Vertical: sub-sprite at original base position + 32 pixels (ROM: baseY + $20)
            renderer.drawFrameIndex(subFrame, baseX, baseY + 0x20, hFlip, vFlip);
        }

        // Main sprite (head) drawn last - appears on top (higher priority)
        renderer.drawFrameIndex(mainSpriteFrame, currentSpriteX, currentSpriteY, hFlip, vFlip);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }
}
