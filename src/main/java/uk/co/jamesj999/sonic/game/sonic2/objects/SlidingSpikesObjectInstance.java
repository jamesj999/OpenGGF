package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.PatternDesc;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.SolidContact;
import uk.co.jamesj999.sonic.level.objects.SolidObjectListener;
import uk.co.jamesj999.sonic.level.objects.SolidObjectParams;
import uk.co.jamesj999.sonic.level.objects.SolidObjectProvider;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;
import uk.co.jamesj999.sonic.level.render.SpritePieceRenderer;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Sliding Spikes (Object 0x76) - MCZ.
 * <p>
 * A spike block that slides out of the wall when the player approaches.
 * Based on Obj76 in the Sonic 2 disassembly (s2.asm lines 55203-55335).
 * <p>
 * Behavior:
 * - Mode 0 (subtype=0): Waiting - detects player approach
 * - Mode 2 (subtype=2): Sliding - moves 1 pixel/frame for 128 frames
 * <p>
 * Detection triggers when player is on ground and within range:
 * - X distance: |player_x - object_x + 0xC0| < 0x80 (adjusted by x_flip)
 * - Y distance: |player_y - object_y + 0x10| < 0x20
 */
public class SlidingSpikesObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // Constants from disassembly (Obj76_InitData at s2.asm:55221-55225)
    // The ROM supports a table lookup for different subtypes (s2.asm:55236-55242),
    // but all original layouts use subtype 0. Only the first entry is implemented.
    // If ROM hacks use other subtypes, this would need expansion to table lookup.
    private static final int WIDTH_PIXELS = 0x40;  // 64 pixels (half-width for collision)
    private static final int Y_RADIUS = 0x10;      // 16 pixels

    // Off-screen margin for despawn check (MarkObjGone2 tolerance)
    private static final int DESPAWN_MARGIN = 128;

    // Slide parameters
    private static final int SLIDE_DISTANCE = 0x80; // 128 pixels total movement

    // Detection range constants from Obj76_CheckPlayer
    private static final int DETECT_X_OFFSET = 0xC0;     // +192 pixels offset to X delta
    private static final int DETECT_X_FLIP_SUB = 0x100;  // -256 if x_flipped
    private static final int DETECT_X_THRESHOLD = 0x80;  // Must be < 128 to trigger
    private static final int DETECT_Y_OFFSET = 0x10;     // +16 pixels offset to Y delta
    private static final int DETECT_Y_THRESHOLD = 0x20;  // Must be < 32 to trigger

    // State
    private final int baseX;
    private int currentX;
    private int slidingRemainingMovement = 0;
    private int currentSubtype;  // 0 = waiting, 2 = sliding
    private ObjectSpawn dynamicSpawn;

    // Orientation from spawn render_flags
    private final boolean hFlip;

    // Sprite mapping for this object (single frame with 6 pieces)
    // From docs/s2disasm/mappings/sprite/obj76.asm
    // spritePiece format: xpos, ypos, width, height, tile, xflip, yflip, pal, pri
    private static final SpriteMappingFrame MAPPING_FRAME = new SpriteMappingFrame(List.of(
            // Pieces 0-1: End cap pieces (tile 0x42C, palette 1)
            new SpriteMappingPiece(-0x40, -0x10, 2, 2, 0x42C, false, false, 1),
            new SpriteMappingPiece(-0x40, 0x00, 2, 2, 0x42C, false, false, 1),
            // Pieces 2-5: Main spike body (palette 3)
            new SpriteMappingPiece(-0x30, -0x10, 2, 4, 0x40, false, false, 3),
            new SpriteMappingPiece(-0x20, -0x10, 4, 4, 0x48, false, false, 3),
            new SpriteMappingPiece(0x00, -0x10, 4, 4, 0x48, false, false, 3),
            new SpriteMappingPiece(0x20, -0x10, 4, 4, 0x48, false, false, 3)
    ));

    public SlidingSpikesObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.baseX = spawn.x();
        this.currentX = baseX;
        this.dynamicSpawn = spawn;
        // Extract h_flip from render_flags bit 0 (status.npc.x_flip in disassembly)
        this.hFlip = (spawn.renderFlags() & 0x01) != 0;
        // subtype starts at 0 (waiting mode)
        this.currentSubtype = spawn.subtype() & 0x0F;  // Lower nibble only per disassembly
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return spawn.y();
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        // Off-screen despawn check (MarkObjGone2 behavior from s2.asm:55280-55282)
        // Uses baseX (objoff_34) for the check, not currentX - the spike's spawn
        // position determines despawn, not its current sliding position
        if (!isBasePositionOnScreen()) {
            setDestroyed(true);
            return;
        }

        if (currentSubtype == 0) {
            // Mode 0: Waiting - check for player approach
            checkForPlayer(player);
        } else if (currentSubtype == 2) {
            // Mode 2: Sliding out
            slideOut();
        }

        updateDynamicSpawn();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        renderUsingLevelArt();

        // Debug visualization: show collision bounds
        if (isDebugViewEnabled()) {
            renderDebugBounds(commands);
        }
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // From disassembly: d1 = width_pixels + $B, d2 = y_radius, d3 = y_radius + 1
        int d1 = WIDTH_PIXELS + 0x0B;
        int d2 = Y_RADIUS;
        int d3 = Y_RADIUS + 1;
        return new SolidObjectParams(d1, d2, d3);
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (player == null || player.getInvulnerable()) {
            return;
        }

        // Only hurt on side contact (Touch_ChkHurt2 is called when touch_side_mask is set)
        if (!contact.touchSide()) {
            return;
        }

        boolean hadRings = player.getRingCount() > 0;
        if (hadRings && !player.hasShield()) {
            LevelManager.getInstance().spawnLostRings(player, frameCounter);
        }
        player.applyHurtOrDeath(currentX, true, hadRings);
    }

    @Override
    public ObjectSpawn getSpawn() {
        return dynamicSpawn;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    /**
     * Check if any player is within detection range and trigger sliding.
     * <p>
     * From Obj76_CheckPlayers (s2.asm lines 55290-55294):
     * The disassembly checks both MainCharacter and Sidekick:
     *   lea (MainCharacter).w,a1
     *   bsr.s Obj76_CheckPlayer
     *   lea (Sidekick).w,a1
     *   ; fall through to Obj76_CheckPlayer
     */
    private void checkForPlayer(AbstractPlayableSprite player) {
        // Check main character
        checkSinglePlayer(player);

        // Check sidekick (Tails) if present - matches disassembly behavior
        AbstractPlayableSprite sidekick = SpriteManager.getInstance().getSidekick();
        if (sidekick != null) {
            checkSinglePlayer(sidekick);
        }
    }

    /**
     * Check if a single player is within detection range and trigger sliding.
     * <p>
     * From Obj76_CheckPlayer (s2.asm lines 55295-55314):
     * - Player must be on ground (status.player.in_air = 0)
     * - X distance check: (player_x - object_x + 0xC0) must be < 0x80
     *   (subtract 0x100 if object is x_flipped)
     * - Y distance check: (player_y - object_y + 0x10) must be < 0x20
     */
    private void checkSinglePlayer(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }

        // Already triggered? Don't check again
        if (currentSubtype != 0) {
            return;
        }

        // Player must be on ground (btst #status.player.in_air,status(a1); bne.s rts)
        if (player.getAir()) {
            return;
        }

        // Calculate X distance with offset
        int playerX = player.getCentreX();
        int dx = playerX - baseX + DETECT_X_OFFSET;

        // Adjust for x_flip (object facing left)
        if (hFlip) {
            dx -= DETECT_X_FLIP_SUB;
        }

        // Check X threshold (cmpi.w #$80,d0; bhs.s rts)
        // bhs = branch if higher or same (unsigned >= )
        // So we trigger only if dx < 0x80 (unsigned)
        if (dx < 0 || dx >= DETECT_X_THRESHOLD) {
            return;
        }

        // Calculate Y distance with offset
        int playerY = player.getCentreY();
        int dy = playerY - spawn.y() + DETECT_Y_OFFSET;

        // Check Y threshold (cmpi.w #$20,d0; bhs.s rts)
        if (dy < 0 || dy >= DETECT_Y_THRESHOLD) {
            return;
        }

        // Trigger slide: set subtype=2, remaining_movement=0x80
        currentSubtype = 2;
        slidingRemainingMovement = SLIDE_DISTANCE;
    }

    /**
     * Slide the spike block out of the wall.
     * <p>
     * From Obj76_SlideOut (s2.asm lines 55317-55326):
     * - If remaining_movement > 0: decrement by 1, move x_pos by -1 (or +1 if x_flipped)
     */
    private void slideOut() {
        if (slidingRemainingMovement <= 0) {
            return;
        }

        slidingRemainingMovement--;

        // Move direction: -1 normally, +1 if x_flipped
        // (moveq #-1,d0; btst #x_flip; beq +; neg.w d0; add.w d0,x_pos)
        int moveDir = hFlip ? 1 : -1;
        currentX += moveDir;
    }

    /**
     * Update dynamic spawn to reflect current position for collision detection.
     */
    private void updateDynamicSpawn() {
        if (dynamicSpawn.x() == currentX) {
            return;
        }
        dynamicSpawn = new ObjectSpawn(
                currentX,
                spawn.y(),
                spawn.objectId(),
                spawn.subtype(),
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
    }

    /**
     * Render using level art patterns (ArtTile_ArtKos_LevelArt).
     * <p>
     * The tile indices in the mapping (0x40, 0x42C, 0x48) reference MCZ level patterns.
     */
    private void renderUsingLevelArt() {
        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        List<SpriteMappingPiece> pieces = MAPPING_FRAME.pieces();

        // Draw in reverse order (Painter's Algorithm) - first piece in list appears on top
        for (int i = pieces.size() - 1; i >= 0; i--) {
            SpriteMappingPiece piece = pieces.get(i);
            SpritePieceRenderer.renderPieces(
                    List.of(piece),
                    currentX,
                    spawn.y(),
                    0,  // Level patterns start at index 0
                    -1, // Use piece's palette directly (absolute)
                    hFlip,
                    false,  // No vFlip for this object
                    (patternIndex, pieceHFlip, pieceVFlip, paletteIndex, drawX, drawY) -> {
                        int descIndex = patternIndex & 0x7FF;
                        if (pieceHFlip) {
                            descIndex |= 0x800;
                        }
                        if (pieceVFlip) {
                            descIndex |= 0x1000;
                        }
                        descIndex |= (paletteIndex & 0x3) << 13;
                        graphicsManager.renderPattern(new PatternDesc(descIndex), drawX, drawY);
                    });
        }
    }

    /**
     * Render debug collision bounds as a red box.
     */
    private void renderDebugBounds(List<GLCommand> commands) {
        // Use the actual collision dimensions
        int halfWidth = WIDTH_PIXELS;
        int halfHeight = Y_RADIUS;

        int left = currentX - halfWidth;
        int right = currentX + halfWidth;
        int top = spawn.y() - halfHeight;
        int bottom = spawn.y() + halfHeight;

        // Red color for hazardous object
        float r = 1.0f;
        float g = 0.2f;
        float b = 0.2f;

        // Draw collision box outline
        appendDebugLine(commands, left, top, right, top, r, g, b);
        appendDebugLine(commands, right, top, right, bottom, r, g, b);
        appendDebugLine(commands, right, bottom, left, bottom, r, g, b);
        appendDebugLine(commands, left, bottom, left, top, r, g, b);

        // Draw center cross
        int crossSize = 4;
        appendDebugLine(commands, currentX - crossSize, spawn.y(), currentX + crossSize, spawn.y(), r, g, b);
        appendDebugLine(commands, currentX, spawn.y() - crossSize, currentX, spawn.y() + crossSize, r, g, b);
    }

    private void appendDebugLine(List<GLCommand> commands, int x1, int y1, int x2, int y2,
                                  float r, float g, float b) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x2, y2, 0, 0));
    }

    private boolean isDebugViewEnabled() {
        return SonicConfigurationService.getInstance()
                .getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
    }

    /**
     * Check if the BASE position (spawn point) is on screen.
     * ROM uses objoff_34 (original x_pos) for MarkObjGone2, not the current
     * sliding position. This ensures spikes despawn based on their wall
     * anchor point, not their extended spike tip.
     */
    private boolean isBasePositionOnScreen() {
        // Access camera bounds via the parent's mechanism
        // We check baseX (objoff_34 equivalent) instead of currentX
        return isOnScreenAt(baseX, spawn.y(), DESPAWN_MARGIN);
    }

    /**
     * Check if a specific position is on screen with margin.
     * Used for despawn check with base position instead of current position.
     */
    private boolean isOnScreenAt(int x, int y, int margin) {
        // Use the camera bounds from parent class
        // This mirrors isOnScreen(margin) but with explicit coordinates
        uk.co.jamesj999.sonic.camera.Camera camera =
                uk.co.jamesj999.sonic.camera.Camera.getInstance();
        if (camera == null) {
            return true;  // Assume on-screen if no camera
        }
        int camX = camera.getX();
        int camY = camera.getY();
        int screenWidth = 320;
        int screenHeight = 224;

        return x >= camX - margin && x <= camX + screenWidth + margin
            && y >= camY - margin && y <= camY + screenHeight + margin;
    }
}
