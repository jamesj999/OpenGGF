package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.debug.DebugOverlayManager;
import uk.co.jamesj999.sonic.debug.DebugOverlayToggle;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.ButtonVineTriggerManager;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AnimationIds;
import uk.co.jamesj999.sonic.game.sonic2.audio.Sonic2Sfx;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.PatternDesc;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;
import uk.co.jamesj999.sonic.level.render.SpritePieceRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x7F - VineSwitch (MCZ pull switch).
 * <p>
 * A static vine switch that the player hangs from to trigger a ButtonVine event.
 * When grabbed, sets the ButtonVine_Trigger bit and plays a blip sound. The player
 * can release by pressing any action button (A/B/C).
 * <p>
 * <b>Disassembly Reference:</b> s2.asm lines 56022-56132 (Obj7F code)
 * <p>
 * <b>Subtype encoding:</b>
 * <ul>
 *   <li>Bits 0-3 (0x0F): Switch ID for ButtonVine_Trigger (0-15)</li>
 * </ul>
 * <p>
 * <b>Mapping frames:</b>
 * <ul>
 *   <li>Frame 0: Normal state (vine extended)</li>
 *   <li>Frame 1: Grabbed state (vine pulled down slightly)</li>
 * </ul>
 * <p>
 * The mapping frame depends on whether ANY player is grabbed (objoff_30 word):
 * <ul>
 *   <li>objoff_30 == 0: Frame 0 (both bytes clear)</li>
 *   <li>objoff_30 != 0: Frame 1 (either player grabbed)</li>
 * </ul>
 */
public class VineSwitchObjectInstance extends AbstractObjectInstance {

    private static final Logger LOGGER = Logger.getLogger(VineSwitchObjectInstance.class.getName());

    // Debug state
    private static final boolean DEBUG_VIEW_ENABLED = SonicConfigurationService.getInstance()
            .getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
    private static final DebugOverlayManager OVERLAY_MANAGER = GameServices.debugOverlay();

    // Static mapping data (loaded once)
    private static List<SpriteMappingFrame> mappings;
    private static boolean mappingsLoadAttempted;

    // === Object Configuration ===
    private final int switchId;  // subtype & 0x0F: Switch ID for ButtonVine_Trigger

    // === Per-Player Grab State ===
    // ROM uses objoff_30 (player 1 byte) and objoff_31 (player 2 byte)
    // ROM uses objoff_32/objoff_33 as release delay timers (2 bytes after grab flags)
    private boolean player1Grabbed;         // objoff_30
    private boolean player2Grabbed;         // objoff_31
    private int player1ReleaseDelay;        // objoff_32 (byte at a2+2)
    private int player2ReleaseDelay;        // objoff_33 (byte at a2+2 for player 2)

    // === Rendering State ===
    private int mappingFrame;               // 0 = normal, 1 = grabbed

    // === Grab Zone Constants (from disassembly) ===
    // ROM: addi.w #$C,d0 / cmpi.w #$18,d0 -> horizontal range is -0x0C to +0x0B (±12 pixels)
    private static final int GRAB_HALF_WIDTH = 0x0C;  // ±12 pixels horizontal
    // ROM: subi.w #$28,d1 / cmpi.w #$10,d1 -> vertical range is 0x28 to 0x37 below object
    private static final int GRAB_Y_OFFSET = 0x28;    // 40 pixels below object
    private static final int GRAB_Y_RANGE = 0x10;     // 16 pixels range
    // ROM: addi.w #$30,y_pos(a1) -> player hangs 0x30 pixels below object
    private static final int HANG_Y_OFFSET = 0x30;    // 48 pixels below object

    // === Release Constants ===
    // ROM: move.b #18,2(a2) (normal) or move.b #60,2(a2) (if direction held)
    private static final int RELEASE_DELAY_NORMAL = 18;     // Normal release delay
    private static final int RELEASE_DELAY_DIRECTION = 60;  // Delay when direction held

    // === Jump Velocity ===
    // ROM: move.w #-$300,y_vel(a1)
    private static final int RELEASE_Y_VELOCITY = -0x300;

    /**
     * Creates a new VineSwitch object instance.
     *
     * @param spawn Object spawn data from level layout
     * @param name  Object name for debugging
     */
    public VineSwitchObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);

        // Parse subtype - only bits 0-3 used for switch ID
        // ROM: andi.w #$F,d0 / lea (ButtonVine_Trigger).w,a3
        this.switchId = spawn.subtype() & 0x0F;

        LOGGER.fine(() -> String.format("VineSwitch init: pos=(%d,%d), switchId=%d",
                spawn.x(), spawn.y(), switchId));
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        // Process player interactions
        // ROM: Obj7F_Main (loc_2981E) calls Obj7F_Action for each player
        processPlayerInteraction(player, false);  // Player 1 (MainCharacter)
        // Note: Player 2 (Sidekick) support deferred - underlying state fields are in place

        // Update mapping frame based on grab state
        // ROM: tst.w objoff_30(a0) / beq.s + / move.b #1,mapping_frame(a0)
        updateMappingFrame();
    }

    /**
     * Processes player interaction for grab detection and state management.
     * <p>
     * ROM Reference: Obj7F_Action (loc_2983C-29936)
     *
     * @param player    The player sprite to check
     * @param isPlayer2 true if this is player 2 (Sidekick)
     */
    private void processPlayerInteraction(AbstractPlayableSprite player, boolean isPlayer2) {
        if (player == null) {
            return;
        }

        boolean isGrabbed = isPlayer2 ? player2Grabbed : player1Grabbed;
        int releaseDelay = isPlayer2 ? player2ReleaseDelay : player1ReleaseDelay;

        if (isGrabbed) {
            // Player is currently grabbed - check for release
            // ROM: tst.b (a2) / beq.s loc_29890
            handleGrabbedPlayer(player, isPlayer2);
        } else {
            // Check release delay timer
            // ROM: tst.b 2(a2) / beq.s + / subq.b #1,2(a2) / bne.w return_29936
            if (releaseDelay > 0) {
                if (isPlayer2) {
                    player2ReleaseDelay--;
                } else {
                    player1ReleaseDelay--;
                }
                return;  // Still in release delay
            }

            // Check for new grab
            // ROM: loc_29890 onwards
            checkForGrab(player, isPlayer2);
        }
    }

    /**
     * Handles a player currently grabbed on the vine switch.
     * <p>
     * ROM Reference: Obj7F_Action (loc_2983C-29882)
     *
     * @param player    The grabbed player
     * @param isPlayer2 true if this is player 2
     */
    private void handleGrabbedPlayer(AbstractPlayableSprite player, boolean isPlayer2) {
        // Check for A/B/C button press to release
        // ROM: andi.b #button_B_mask|button_C_mask|button_A_mask,d0 / beq.w return_29936
        if (player.isJumpPressed()) {
            releasePlayer(player, isPlayer2);
            return;
        }

        // Keep player attached to vine switch
        // ROM: Player stays at position set during grab (x_pos(a0), y_pos(a0) + 0x30)
        int hangY = spawn.y() + HANG_Y_OFFSET;
        player.setY((short) (hangY - player.getHeight() / 2));
        player.setX((short) (spawn.x() - player.getWidth() / 2));
    }

    /**
     * Checks if player should grab the vine switch.
     * <p>
     * ROM Reference: Obj7F_Action (loc_29890-29934)
     *
     * @param player    The player to check
     * @param isPlayer2 true if this is player 2
     */
    private void checkForGrab(AbstractPlayableSprite player, boolean isPlayer2) {
        // Check horizontal grab zone: ±0x0C pixels from switch center
        // ROM: move.w x_pos(a1),d0 / sub.w x_pos(a0),d0 / addi.w #$C,d0 / cmpi.w #$18,d0
        int dx = player.getCentreX() - spawn.x();
        if (dx < -GRAB_HALF_WIDTH || dx >= GRAB_HALF_WIDTH) {
            return;  // Outside horizontal grab zone
        }

        // Check vertical grab zone: player must be 0x28 to 0x37 pixels BELOW switch Y
        // ROM: move.w y_pos(a1),d1 / sub.w y_pos(a0),d1 / subi.w #$28,d1 / cmpi.w #$10,d1
        int dy = player.getCentreY() - spawn.y() - GRAB_Y_OFFSET;
        if (dy < 0 || dy >= GRAB_Y_RANGE) {
            return;  // Outside vertical grab zone
        }

        // Check if player can be grabbed
        // ROM: tst.b obj_control(a1) / bmi.s return_29936
        if (player.isObjectControlled() && player.isControlLocked()) {
            return;  // Already controlled by another object
        }

        // ROM: cmpi.b #4,routine(a1) / bhs.s return_29936
        if (player.isHurt()) {
            return;  // In hurt state
        }

        // ROM: tst.w (Debug_placement_mode).w / bne.s return_29936
        if (player.isDebugMode()) {
            return;  // In debug mode
        }

        // Grab the player
        grabPlayer(player, isPlayer2);
    }

    /**
     * Grabs the player onto the vine switch.
     * <p>
     * ROM Reference: Obj7F_Action (loc_298E6-29934)
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

        // Position player on vine switch
        // ROM: move.w x_pos(a0),x_pos(a1) / move.w y_pos(a0),y_pos(a1) / addi.w #$30,y_pos(a1)
        player.setX((short) (spawn.x() - player.getWidth() / 2));
        int hangY = spawn.y() + HANG_Y_OFFSET;
        player.setY((short) (hangY - player.getHeight() / 2));

        // Set animation to hanging pose
        // ROM: move.b #AniIDSonAni_Hang2,anim(a1)
        player.setAnimationId(Sonic2AnimationIds.HANG2);

        // Lock player control
        // ROM: move.b #1,obj_control(a1)
        player.setObjectControlled(true);

        // Mark as grabbed
        // ROM: move.b #1,(a2)
        if (isPlayer2) {
            player2Grabbed = true;
        } else {
            player1Grabbed = true;
        }

        // Set button vine trigger
        // ROM: lea (ButtonVine_Trigger).w,a3 / bset #0,(a3,d0.w)
        ButtonVineTriggerManager.setTrigger(switchId, true);

        // Play blip sound
        // ROM: move.w #SndID_Blip,d0 / jsr (PlaySound).l
        AudioManager.getInstance().playSfx(Sonic2Sfx.BLIP.id);

        LOGGER.fine(() -> String.format("Player grabbed vine switch at (%d,%d), switchId=%d",
                spawn.x(), spawn.y(), switchId));
    }

    /**
     * Releases the player from the vine switch.
     * <p>
     * ROM Reference: Obj7F_Action (loc_2985C-29880)
     *
     * @param player    The player to release
     * @param isPlayer2 true if this is player 2
     */
    private void releasePlayer(AbstractPlayableSprite player, boolean isPlayer2) {
        // Clear control lock
        // ROM: clr.b obj_control(a1)
        player.setObjectControlled(false);

        // Clear grab flag
        // ROM: clr.b (a2)
        if (isPlayer2) {
            player2Grabbed = false;
        } else {
            player1Grabbed = false;
        }

        // Set release delay based on directional input
        // ROM: andi.w #(button_up_mask|button_down_mask|button_left_mask|button_right_mask)<<8,d0
        // ROM: beq.s + / move.b #60,2(a2) // else move.b #18,2(a2)
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

        // Set player to in-air state
        player.setAir(true);

        // Clear button vine trigger
        // ROM: bclr #0,(a3)
        ButtonVineTriggerManager.setTrigger(switchId, false);

        LOGGER.fine(() -> String.format("Player released from vine switch, delay=%d", releaseDelayFrames));
    }

    /**
     * Updates the mapping frame based on grab state.
     * <p>
     * ROM Reference: Obj7F_Action (loc_29876-29880, loc_29920-29934)
     * Frame 0 = normal, Frame 1 = grabbed (either player)
     */
    private void updateMappingFrame() {
        // ROM: move.b #0,mapping_frame(a0) / tst.w objoff_30(a0) / beq.s + / move.b #1,mapping_frame(a0)
        // objoff_30 is a word containing both player grab flags
        boolean anyPlayerGrabbed = player1Grabbed || player2Grabbed;
        mappingFrame = anyPlayerGrabbed ? 1 : 0;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ensureMappingsLoaded();

        // Draw debug overlay when enabled
        if (isDebugViewEnabled()) {
            appendDebug(commands);
        }

        // Get mappings
        if (mappings == null || mappings.isEmpty()) {
            return;
        }

        // Clamp frame to available range
        int frame = Math.min(mappingFrame, mappings.size() - 1);
        if (frame < 0) {
            frame = 0;
        }

        SpriteMappingFrame currentFrame = mappings.get(frame);
        if (currentFrame == null || currentFrame.pieces().isEmpty()) {
            return;
        }

        // Render the vine switch at position
        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;

        renderPieces(graphicsManager, currentFrame.pieces(), spawn.x(), spawn.y(), hFlip, vFlip);
    }

    /**
     * Renders sprite pieces using the graphics manager.
     * <p>
     * Art tile base: ArtTile_ArtNem_VineSwitch = 0x040E (palette 3)
     */
    private void renderPieces(GraphicsManager graphicsManager, List<SpriteMappingPiece> pieces,
                              int drawX, int drawY, boolean hFlip, boolean vFlip) {
        SpritePieceRenderer.renderPieces(
                pieces,
                drawX,
                drawY,
                Sonic2Constants.ART_TILE_VINE_SWITCH,  // Base pattern index
                3,  // Palette 3 (MCZ objects)
                hFlip,
                vFlip,
                (patternIndex, pieceHFlip, pieceVFlip, paletteIndex, px, py) -> {
                    int descIndex = patternIndex & 0x7FF;
                    if (pieceHFlip) {
                        descIndex |= 0x800;
                    }
                    if (pieceVFlip) {
                        descIndex |= 0x1000;
                    }
                    descIndex |= (paletteIndex & 0x3) << 13;
                    graphicsManager.renderPattern(new PatternDesc(descIndex), px, py);
                });
    }

    @Override
    public int getPriorityBucket() {
        // ROM: move.b #4,priority(a0)
        return RenderPriority.clamp(4);
    }

    /**
     * Loads mapping frames from ROM if not already loaded.
     */
    private static void ensureMappingsLoaded() {
        if (mappingsLoadAttempted) {
            return;
        }
        mappingsLoadAttempted = true;

        LevelManager manager = LevelManager.getInstance();
        if (manager == null || manager.getGame() == null) {
            return;
        }

        try {
            Rom rom = manager.getGame().getRom();
            RomByteReader reader = RomByteReader.fromRom(rom);

            // Load mappings (2 frames)
            mappings = loadMappingFrames(reader, Sonic2Constants.MAP_UNC_OBJ7F_ADDR);
            LOGGER.fine("Loaded " + mappings.size() + " VineSwitch mapping frames");

        } catch (IOException | RuntimeException e) {
            LOGGER.warning("Failed to load VineSwitch mappings: " + e.getMessage());
        }
    }

    /**
     * Loads mapping frames from ROM.
     * <p>
     * Format: offset table (word per frame) followed by frame data.
     * Each frame: piece count (word), then 8 bytes per piece.
     * <p>
     * ROM Reference: Standard VDP mapping format used throughout Sonic 2
     *
     * @param reader      ROM byte reader
     * @param mappingAddr Base address of mapping table
     * @return List of mapping frames
     */
    private static List<SpriteMappingFrame> loadMappingFrames(RomByteReader reader, int mappingAddr) {
        int firstOffset = reader.readU16BE(mappingAddr);
        if (firstOffset == 0) {
            return List.of(new SpriteMappingFrame(List.of()));
        }
        int frameCount = firstOffset / 2;

        // Sanity check
        if (frameCount > 100) {
            LOGGER.warning("Suspicious frameCount=" + frameCount + " at mapping addr 0x" +
                    Integer.toHexString(mappingAddr));
            return List.of(new SpriteMappingFrame(List.of()));
        }

        List<SpriteMappingFrame> frames = new ArrayList<>(frameCount);

        for (int i = 0; i < frameCount; i++) {
            int offset = reader.readS16BE(mappingAddr + (i * 2));
            int frameAddr = mappingAddr + offset;

            int pieceCount = reader.readU16BE(frameAddr);
            frameAddr += 2;

            // Sanity check
            if (pieceCount > 200) {
                LOGGER.warning("Suspicious pieceCount=" + pieceCount + " at frame " + i);
                pieceCount = 0;
            }

            List<SpriteMappingPiece> pieces = new ArrayList<>(pieceCount);

            for (int p = 0; p < pieceCount; p++) {
                int yOffset = (byte) reader.readU8(frameAddr);
                frameAddr += 1;

                int size = reader.readU8(frameAddr);
                frameAddr += 1;

                int tileWord = reader.readU16BE(frameAddr);
                frameAddr += 2;

                frameAddr += 2;  // Skip 2P tile word

                int xOffset = (short) reader.readU16BE(frameAddr);
                frameAddr += 2;

                int widthTiles = ((size >> 2) & 0x3) + 1;
                int heightTiles = (size & 0x3) + 1;

                int tileIndex = tileWord & 0x7FF;
                boolean pieceHFlip = (tileWord & 0x800) != 0;
                boolean pieceVFlip = (tileWord & 0x1000) != 0;
                int paletteIndex = (tileWord >> 13) & 0x3;

                pieces.add(new SpriteMappingPiece(
                        xOffset,
                        yOffset,
                        widthTiles,
                        heightTiles,
                        tileIndex,
                        pieceHFlip,
                        pieceVFlip,
                        paletteIndex));
            }

            frames.add(new SpriteMappingFrame(pieces));
        }

        return frames;
    }

    /**
     * Appends debug visualization to render commands.
     */
    private void appendDebug(List<GLCommand> commands) {
        int x = spawn.x();
        int y = spawn.y();

        // Draw object center (yellow cross)
        appendLine(commands, x - 4, y, x + 4, y, 1.0f, 1.0f, 0.0f);
        appendLine(commands, x, y - 4, x, y + 4, 1.0f, 1.0f, 0.0f);

        // Draw hang point (cyan cross at y + HANG_Y_OFFSET)
        int hangY = y + HANG_Y_OFFSET;
        appendLine(commands, x - 4, hangY, x + 4, hangY, 0.0f, 1.0f, 1.0f);
        appendLine(commands, x, hangY - 4, x, hangY + 4, 0.0f, 1.0f, 1.0f);

        // Draw grab detection zone (green rectangle)
        int grabTop = y + GRAB_Y_OFFSET;
        int grabBottom = y + GRAB_Y_OFFSET + GRAB_Y_RANGE;
        int left = x - GRAB_HALF_WIDTH;
        int right = x + GRAB_HALF_WIDTH;

        appendLine(commands, left, grabTop, right, grabTop, 0.0f, 1.0f, 0.0f);
        appendLine(commands, right, grabTop, right, grabBottom, 0.0f, 1.0f, 0.0f);
        appendLine(commands, right, grabBottom, left, grabBottom, 0.0f, 1.0f, 0.0f);
        appendLine(commands, left, grabBottom, left, grabTop, 0.0f, 1.0f, 0.0f);

        // Draw grabbed state indicator (red if grabbed, gray if not)
        float grabR = (player1Grabbed || player2Grabbed) ? 1.0f : 0.5f;
        float grabG = (player1Grabbed || player2Grabbed) ? 0.0f : 0.5f;
        appendLine(commands, x - 8, y - 8, x + 8, y + 8, grabR, grabG, 0.0f);
        appendLine(commands, x + 8, y - 8, x - 8, y + 8, grabR, grabG, 0.0f);

        // Draw switch ID indicator (small number display)
        // Just show a label line for now
        int labelY = y - 20;
        appendLine(commands, x - 4, labelY, x + 4, labelY, 0.8f, 0.8f, 0.8f);
    }

    /**
     * Appends a debug line to the render commands.
     */
    private void appendLine(List<GLCommand> commands, int x1, int y1, int x2, int y2, float r, float g, float b) {
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
