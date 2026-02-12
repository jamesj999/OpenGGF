package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.debug.DebugOverlayManager;
import uk.co.jamesj999.sonic.debug.DebugOverlayToggle;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AnimationIds;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0xD9 - Grab (invisible hang-on point from WFZ).
 * <p>
 * An invisible sprite that the player can hang on to, used on the blocks in
 * Wing Fortress Zone. When the player enters the grab zone, they are locked
 * in place with the hanging animation. Pressing any action button (A/B/C)
 * releases them with an upward velocity boost.
 * <p>
 * <b>Disassembly Reference:</b> s2.asm lines 59767-59849 (ObjD9 code)
 * <p>
 * <b>Key properties:</b>
 * <ul>
 *   <li>Invisible - no art, mappings, or animation</li>
 *   <li>render_flags = level_fg only (no on_screen bit)</li>
 *   <li>width_pixels = 0x18 (24) for despawn detection</li>
 *   <li>priority = 4</li>
 * </ul>
 * <p>
 * <b>Grab zone:</b> Player center must be within +-0x18 (24px) horizontally
 * and 0 to 0x10 (16px) vertically below the object center.
 * <p>
 * <b>When grabbed:</b> Velocity/inertia zeroed, player Y snapped to object Y,
 * animation set to HANG2, obj_control set to 1. Player X is NOT modified.
 * <p>
 * <b>When released:</b> obj_control cleared, -0x300 upward velocity applied,
 * release delay set (18 frames normally, 60 if direction held).
 * <p>
 * <b>Per-player state:</b> Uses objoff_30 (P1 grab flag), objoff_31 (P2 grab flag),
 * objoff_32 (P1 release delay), objoff_33 (P2 release delay).
 */
public class GrabObjectInstance extends AbstractObjectInstance {

    private static final Logger LOGGER = Logger.getLogger(GrabObjectInstance.class.getName());

    // Debug state
    private static final boolean DEBUG_VIEW_ENABLED = SonicConfigurationService.getInstance()
            .getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
    private static final DebugOverlayManager OVERLAY_MANAGER = GameServices.debugOverlay();

    // === Grab Zone Constants (from disassembly) ===
    // ROM: addi.w #$18,d0 / cmpi.w #$30,d0 -> horizontal range is -0x18 to +0x17 (+-24 pixels)
    private static final int GRAB_HALF_WIDTH = 0x18;

    // ROM: cmpi.w #$10,d1 -> vertical range is 0 to 0x0F (16 pixels below object Y)
    private static final int GRAB_Y_RANGE = 0x10;

    // === Release Constants ===
    // ROM: move.b #18,2(a2) (normal) or move.b #60,2(a2) (if direction held)
    private static final int RELEASE_DELAY_NORMAL = 18;
    private static final int RELEASE_DELAY_DIRECTION = 60;

    // === Jump Velocity ===
    // ROM: move.w #-$300,y_vel(a1)
    private static final int RELEASE_Y_VELOCITY = -0x300;

    // === Per-Player Grab State ===
    // ROM uses objoff_30 (player 1 byte) and objoff_31 (player 2 byte)
    // ROM uses objoff_32/objoff_33 as release delay timers
    private boolean player1Grabbed;         // objoff_30
    private boolean player2Grabbed;         // objoff_31
    private int player1ReleaseDelay;        // objoff_32
    private int player2ReleaseDelay;        // objoff_33

    /**
     * Creates a new Grab object instance.
     *
     * @param spawn Object spawn data from level layout
     * @param name  Object name for debugging
     */
    public GrabObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);

        LOGGER.fine(() -> String.format("Grab init: pos=(%d,%d), subtype=0x%02X",
                spawn.x(), spawn.y(), spawn.subtype()));
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        // ROM: ObjD9_Main processes both players then calls MarkObjGone3
        // Process player 1 (MainCharacter)
        processPlayerInteraction(player, false);
        // Note: Player 2 (Sidekick) state fields are in place for future support
    }

    /**
     * Processes player interaction for grab detection and release.
     * <p>
     * ROM Reference: ObjD9_CheckCharacter (loc_2C972-2CA08)
     * <p>
     * Flow:
     * <ol>
     *   <li>If grabbed: check for A/B/C to release</li>
     *   <li>If not grabbed: decrement release delay, then check grab zone</li>
     * </ol>
     *
     * @param player    The player sprite to check
     * @param isPlayer2 true if this is player 2 (Sidekick)
     */
    private void processPlayerInteraction(AbstractPlayableSprite player, boolean isPlayer2) {
        if (player == null) {
            return;
        }

        boolean isGrabbed = isPlayer2 ? player2Grabbed : player1Grabbed;

        if (isGrabbed) {
            // Player is currently grabbed - check for release
            // ROM: tst.b (a2) / beq.s loc_2C9A0
            handleGrabbedPlayer(player, isPlayer2);
        } else {
            // Not grabbed - check release delay timer then try to grab
            // ROM: tst.b 2(a2) / beq.s + / subq.b #1,2(a2) / bne.w ObjD9_CheckCharacter_End
            int releaseDelay = isPlayer2 ? player2ReleaseDelay : player1ReleaseDelay;
            if (releaseDelay > 0) {
                // Decrement timer
                releaseDelay--;
                if (isPlayer2) {
                    player2ReleaseDelay = releaseDelay;
                } else {
                    player1ReleaseDelay = releaseDelay;
                }
                // ROM: bne.w ObjD9_CheckCharacter_End - if still > 0, return
                if (releaseDelay > 0) {
                    return;
                }
                // Timer just reached 0 - fall through to grab check
            }

            // Check for new grab
            checkForGrab(player, isPlayer2);
        }
    }

    /**
     * Handles a player currently grabbed - checks for release via A/B/C press.
     * <p>
     * ROM Reference: ObjD9_CheckCharacter (loc_2C972-2C99E)
     * <p>
     * When A/B/C is pressed:
     * <ul>
     *   <li>Clears obj_control</li>
     *   <li>Clears grab flag</li>
     *   <li>Sets release delay (18 normally, 60 if direction held)</li>
     *   <li>Applies -0x300 upward velocity</li>
     * </ul>
     *
     * @param player    The grabbed player
     * @param isPlayer2 true if this is player 2
     */
    private void handleGrabbedPlayer(AbstractPlayableSprite player, boolean isPlayer2) {
        // ROM: andi.b #button_B_mask|button_C_mask|button_A_mask,d0
        // ROM: beq.w ObjD9_CheckCharacter_End
        if (!player.isJumpPressed()) {
            return;  // No action button pressed - stay grabbed
        }

        // Release the player
        // ROM: clr.b obj_control(a1)
        player.setObjectControlled(false);

        // ROM: clr.b (a2)
        if (isPlayer2) {
            player2Grabbed = false;
        } else {
            player1Grabbed = false;
        }

        // Set release delay based on directional input
        // ROM: move.b #18,2(a2)
        // ROM: andi.w #(button_up_mask|button_down_mask|button_left_mask|button_right_mask)<<8,d0
        // ROM: beq.s + / move.b #60,2(a2)
        boolean directionHeld = player.isUpPressed() || player.isDownPressed()
                || player.isLeftPressed() || player.isRightPressed();
        int releaseDelayFrames = directionHeld ? RELEASE_DELAY_DIRECTION : RELEASE_DELAY_NORMAL;

        if (isPlayer2) {
            player2ReleaseDelay = releaseDelayFrames;
        } else {
            player1ReleaseDelay = releaseDelayFrames;
        }

        // Apply upward velocity
        // ROM: move.w #-$300,y_vel(a1)
        player.setYSpeed((short) RELEASE_Y_VELOCITY);

        // Set player to airborne state (physics will handle gravity from here)
        player.setAir(true);

        LOGGER.fine(() -> String.format("Player released from Grab at (%d,%d), delay=%d",
                spawn.x(), spawn.y(), releaseDelayFrames));
    }

    /**
     * Checks if player should be grabbed.
     * <p>
     * ROM Reference: ObjD9_CheckCharacter (loc_2C9A0-2CA08)
     * <p>
     * Guard checks:
     * <ul>
     *   <li>Player must be within horizontal grab zone (+-0x18 pixels)</li>
     *   <li>Player must be within vertical grab zone (0 to 0x10 pixels below object)</li>
     *   <li>obj_control bit 7 must not be set (not under full lock control)</li>
     *   <li>routine must be less than 6 (not dead/dying)</li>
     *   <li>Debug placement mode must be off</li>
     * </ul>
     *
     * @param player    The player to check
     * @param isPlayer2 true if this is player 2
     */
    private void checkForGrab(AbstractPlayableSprite player, boolean isPlayer2) {
        // Check horizontal grab zone: +-0x18 pixels from object center
        // ROM: move.w x_pos(a1),d0 / sub.w x_pos(a0),d0 / addi.w #$18,d0 / cmpi.w #$30,d0
        // ROM: bhs.w ObjD9_CheckCharacter_End
        int dx = player.getCentreX() - spawn.x();
        if (dx + GRAB_HALF_WIDTH < 0 || dx + GRAB_HALF_WIDTH >= GRAB_HALF_WIDTH * 2) {
            return;  // Outside horizontal grab zone
        }

        // Check vertical grab zone: player must be 0 to 0x0F pixels below object Y
        // ROM: move.w y_pos(a1),d1 / sub.w y_pos(a0),d1 / cmpi.w #$10,d1
        // ROM: bhs.w ObjD9_CheckCharacter_End
        int dy = player.getCentreY() - spawn.y();
        if (dy < 0 || dy >= GRAB_Y_RANGE) {
            return;  // Outside vertical grab zone
        }

        // Check if player is under full object lock (bit 7 of obj_control)
        // ROM: tst.b obj_control(a1) / bmi.s ObjD9_CheckCharacter_End
        // bmi checks sign bit (bit 7) - blocks only when obj_control >= 0x80
        if (player.isControlLocked()) {
            return;  // Under full lock (spin tube, etc.)
        }

        // Check if player is dead/dying (routine >= 6)
        // ROM: cmpi.b #6,routine(a1) / bhs.s ObjD9_CheckCharacter_End
        if (player.getDead()) {
            return;  // Player is dead
        }

        // Check debug placement mode
        // ROM: tst.w (Debug_placement_mode).w / bne.s ObjD9_CheckCharacter_End
        if (player.isDebugMode()) {
            return;  // In debug mode
        }

        // Grab the player
        grabPlayer(player, isPlayer2);
    }

    /**
     * Grabs the player onto this hang point.
     * <p>
     * ROM Reference: ObjD9_CheckCharacter (loc_2C9EA-2CA06)
     * <p>
     * Actions:
     * <ul>
     *   <li>Zero x_vel, y_vel, inertia</li>
     *   <li>Snap player Y to object Y (X is NOT modified)</li>
     *   <li>Set animation to HANG2</li>
     *   <li>Set obj_control to 1</li>
     *   <li>Set grab flag</li>
     * </ul>
     *
     * @param player    The player to grab
     * @param isPlayer2 true if this is player 2
     */
    private void grabPlayer(AbstractPlayableSprite player, boolean isPlayer2) {
        // Zero velocity and inertia
        // ROM: clr.w x_vel(a1) / clr.w y_vel(a1) / clr.w inertia(a1)
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);

        // Snap player Y to object Y (X is left alone)
        // ROM: move.w y_pos(a0),y_pos(a1)
        // Note: ROM sets center Y directly; engine uses top-left, so subtract half height
        player.setY((short) (spawn.y() - player.getHeight() / 2));

        // Set animation to hanging pose
        // ROM: move.b #AniIDSonAni_Hang2,anim(a1)
        player.setAnimationId(Sonic2AnimationIds.HANG2);

        // Lock player control (obj_control = 1, not full lock)
        // ROM: move.b #1,obj_control(a1)
        player.setObjectControlled(true);

        // Mark as grabbed
        // ROM: move.b #1,(a2)
        if (isPlayer2) {
            player2Grabbed = true;
        } else {
            player1Grabbed = true;
        }

        LOGGER.fine(() -> String.format("Player grabbed Grab at (%d,%d)",
                spawn.x(), spawn.y()));
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // This object is invisible - no art, no mappings, no rendering
        // ROM: render_flags = #1<<render_flags.level_fg (no on_screen bit set)
        // Only render debug visualization when enabled
        if (isDebugViewEnabled()) {
            appendDebug(commands);
        }
    }

    @Override
    public int getPriorityBucket() {
        // ROM: move.b #4,priority(a0)
        return RenderPriority.clamp(4);
    }

    /**
     * Appends debug visualization showing the grab zone and state.
     */
    private void appendDebug(List<GLCommand> commands) {
        int x = spawn.x();
        int y = spawn.y();

        // Draw object center (yellow cross)
        appendLine(commands, x - 4, y, x + 4, y, 1.0f, 1.0f, 0.0f);
        appendLine(commands, x, y - 4, x, y + 4, 1.0f, 1.0f, 0.0f);

        // Draw grab detection zone (green rectangle)
        // Horizontal: x +- GRAB_HALF_WIDTH, Vertical: y to y + GRAB_Y_RANGE
        int left = x - GRAB_HALF_WIDTH;
        int right = x + GRAB_HALF_WIDTH;
        int top = y;
        int bottom = y + GRAB_Y_RANGE;

        appendLine(commands, left, top, right, top, 0.0f, 1.0f, 0.0f);
        appendLine(commands, right, top, right, bottom, 0.0f, 1.0f, 0.0f);
        appendLine(commands, right, bottom, left, bottom, 0.0f, 1.0f, 0.0f);
        appendLine(commands, left, bottom, left, top, 0.0f, 1.0f, 0.0f);

        // Draw grabbed state indicator (red X if grabbed, gray X if not)
        float grabR = (player1Grabbed || player2Grabbed) ? 1.0f : 0.5f;
        float grabG = (player1Grabbed || player2Grabbed) ? 0.0f : 0.5f;
        appendLine(commands, x - 6, y - 6, x + 6, y + 6, grabR, grabG, 0.0f);
        appendLine(commands, x + 6, y - 6, x - 6, y + 6, grabR, grabG, 0.0f);
    }

    /**
     * Appends a debug line to the render commands.
     */
    private void appendLine(List<GLCommand> commands, int x1, int y1, int x2, int y2,
                            float r, float g, float b) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x2, y2, 0, 0));
    }

    /**
     * Checks if debug view is currently enabled.
     */
    private boolean isDebugViewEnabled() {
        return DEBUG_VIEW_ENABLED && OVERLAY_MANAGER.isEnabled(DebugOverlayToggle.OVERLAY);
    }
}
