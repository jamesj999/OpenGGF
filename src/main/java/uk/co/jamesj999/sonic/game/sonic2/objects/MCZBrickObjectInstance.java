package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.debug.DebugOverlayManager;
import uk.co.jamesj999.sonic.debug.DebugOverlayToggle;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.S2SpriteDataLoader;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;
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
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;
import uk.co.jamesj999.sonic.level.render.SpritePieceRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x75 - MCZ Brick / Spike Ball from Mystic Cave Zone.
 * <p>
 * This is a dual-purpose object with three subtypes:
 * <ul>
 *   <li><b>0x0F (Brick)</b>: Static solid block - player can stand on it</li>
 *   <li><b>0x16 (Small Spike Ball)</b>: 22-segment rotating spike chain - damages player</li>
 *   <li><b>0x17 (Large Spike Ball)</b>: 23-segment rotating spike chain - damages player</li>
 * </ul>
 * <p>
 * <b>Disassembly Reference:</b> s2.asm lines 55036-55198 (Obj75)
 * <p>
 * <b>Subtype encoding:</b>
 * <ul>
 *   <li>Lower nibble (0x0F): Mode/chain count (0x0F = brick, else chain segment count)</li>
 *   <li>Upper nibble (0xF0): Rotation speed (shifted left 3 after sign extension)</li>
 * </ul>
 * <p>
 * <b>Initial angle from render flags:</b>
 * <ul>
 *   <li>Bit 0 (X flip): adds 64 to starting angle (90 degrees)</li>
 *   <li>Bit 1 (Y flip): adds 128 to starting angle (180 degrees)</li>
 * </ul>
 */
public class MCZBrickObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, TouchResponseProvider {

    private static final Logger LOGGER = Logger.getLogger(MCZBrickObjectInstance.class.getName());

    // Mode constants
    private static final int BRICK_SUBTYPE = 0x0F;

    // Brick collision dimensions (from disassembly lines 55183-55187)
    // d1 = width_pixels + 0x0B = 0x10 + 0x0B = 0x1B (27 pixels half-width)
    // d2 = 0x10 (16 pixels - top height)
    // d3 = 0x11 (17 pixels - bottom height)
    private static final int BRICK_HALF_WIDTH = 0x1B;    // 27 pixels
    private static final int BRICK_TOP_HEIGHT = 0x10;    // 16 pixels
    private static final int BRICK_BOTTOM_HEIGHT = 0x11; // 17 pixels

    private static final SolidObjectParams BRICK_PARAMS =
            new SolidObjectParams(BRICK_HALF_WIDTH, BRICK_TOP_HEIGHT, BRICK_BOTTOM_HEIGHT);

    // Spike ball collision flags (from disassembly line 55092)
    // $9A = High nibble 0x90 (HURT category) + Low nibble 0x0A (size index)
    private static final int SPIKE_BALL_COLLISION_FLAGS = 0x9A;

    // ROM-accurate 256-entry sine table (values from -256 to +256)
    // Index 0 = 0 degrees, index 64 = 90 degrees, index 128 = 180 degrees, etc.
    private static final short[] SINE_TABLE = {
            0, 6, 12, 18, 25, 31, 37, 43, 49, 56, 62, 68, 74, 80, 86, 92,
            97, 103, 109, 115, 120, 126, 131, 136, 142, 147, 152, 157, 162, 167, 171, 176,
            181, 185, 189, 193, 197, 201, 205, 209, 212, 216, 219, 222, 225, 228, 231, 234,
            236, 238, 241, 243, 244, 246, 248, 249, 251, 252, 253, 254, 254, 255, 255, 255,
            256, 255, 255, 255, 254, 254, 253, 252, 251, 249, 248, 246, 244, 243, 241, 238,
            236, 234, 231, 228, 225, 222, 219, 216, 212, 209, 205, 201, 197, 193, 189, 185,
            181, 176, 171, 167, 162, 157, 152, 147, 142, 136, 131, 126, 120, 115, 109, 103,
            97, 92, 86, 80, 74, 68, 62, 56, 49, 43, 37, 31, 25, 18, 12, 6,
            0, -6, -12, -18, -25, -31, -37, -43, -49, -56, -62, -68, -74, -80, -86, -92,
            -97, -103, -109, -115, -120, -126, -131, -136, -142, -147, -152, -157, -162, -167, -171, -176,
            -181, -185, -189, -193, -197, -201, -205, -209, -212, -216, -219, -222, -225, -228, -231, -234,
            -236, -238, -241, -243, -244, -246, -248, -249, -251, -252, -253, -254, -254, -255, -255, -255,
            -256, -255, -255, -255, -254, -254, -253, -252, -251, -249, -248, -246, -244, -243, -241, -238,
            -236, -234, -231, -228, -225, -222, -219, -216, -212, -209, -205, -201, -197, -193, -189, -185,
            -181, -176, -171, -167, -162, -157, -152, -147, -142, -136, -131, -126, -120, -115, -109, -103,
            -97, -92, -86, -80, -74, -68, -62, -56, -49, -43, -37, -31, -25, -18, -12, -6,
            0, 6, 12, 18, 25, 31, 37, 43, 49, 56, 62, 68, 74, 80, 86, 92,
            97, 103, 109, 115, 120, 126, 131, 136, 142, 147, 152, 157, 162, 167, 171, 176,
            181, 185, 189, 193, 197, 201, 205, 209, 212, 216, 219, 222, 225, 228, 231, 234,
            236, 238, 241, 243, 244, 246, 248, 249, 251, 252, 253, 254, 254, 255, 255, 255
    };

    // Static mapping data
    private static List<SpriteMappingFrame> mappings;
    private static boolean mappingsLoadAttempted;

    // Debug state
    private static final boolean DEBUG_VIEW_ENABLED = SonicConfigurationService.getInstance()
            .getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
    private static final DebugOverlayManager OVERLAY_MANAGER = GameServices.debugOverlay();

    // Mode
    private enum Mode { BRICK, SPIKE_BALL }
    private final Mode mode;

    // Position state
    private final int initialX;
    private final int initialY;

    // Spike ball state (only used in SPIKE_BALL mode)
    private final int chainCount;
    private final int speed;
    private int angleWord;
    private int[] chainX;
    private int[] chainY;
    private int spikeBallX;
    private int spikeBallY;

    public MCZBrickObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.initialX = spawn.x();
        this.initialY = spawn.y();

        int subtype = spawn.subtype();
        int lowerNibble = subtype & 0x0F;

        // Determine mode from lower nibble
        if (lowerNibble == BRICK_SUBTYPE) {
            this.mode = Mode.BRICK;
            this.chainCount = 0;
            this.speed = 0;
            this.spikeBallX = initialX;
            this.spikeBallY = initialY;
        } else {
            this.mode = Mode.SPIKE_BALL;

            // Validate chain count - ROM only uses 0x16 (22) and 0x17 (23) for spike balls
            // Invalid values (0x00-0x0E) would cause ROM to loop 65536 times (dbf underflow)
            if (lowerNibble < 1) {
                LOGGER.warning(() -> String.format(
                        "Invalid MCZ Brick chain count %d at (%d,%d), defaulting to 22",
                        lowerNibble, spawn.x(), spawn.y()));
                this.chainCount = 22;
            } else {
                this.chainCount = lowerNibble;
            }

            // Speed calculation from disassembly (lines 55073-55076):
            // andi.b #$F0,d0    ; Keep upper nibble
            // ext.w d0          ; Sign extend byte to word
            // asl.w #3,d0       ; Shift left 3 (multiply by 8)
            int speedByte = subtype & 0xF0;
            int speedWord = (speedByte > 127) ? (speedByte | 0xFF00) : speedByte;
            this.speed = (speedWord << 3) & 0xFFFF;

            // Initial angle from render flags (lines 55079-55082):
            // ror.b #2,d0       ; Rotate Y-flip and X-flip into bits 6-7
            // andi.b #$C0,d0    ; Keep only bits 6-7
            // This gives: Y-flip in bit 7 (+128), X-flip in bit 6 (+64)
            int flags = spawn.renderFlags();
            boolean xFlip = (flags & 0x01) != 0;
            boolean yFlip = (flags & 0x02) != 0;
            int initialAngle = ((yFlip ? 0x80 : 0) | (xFlip ? 0x40 : 0)) & 0xFF;
            this.angleWord = initialAngle << 8;

            // Initialize chain position arrays
            this.chainX = new int[chainCount];
            this.chainY = new int[chainCount];

            // Calculate initial positions
            updateRotation();
        }

        LOGGER.fine(() -> String.format(
                "MCZBrick init: pos=(%d,%d), subtype=0x%02X, mode=%s, chainCount=%d, speed=%d",
                initialX, initialY, subtype, mode, chainCount, speed));
    }

    @Override
    public int getX() {
        // For spike ball mode, return the spike ball head position for collision
        if (mode == Mode.SPIKE_BALL) {
            return spikeBallX;
        }
        return initialX;
    }

    @Override
    public int getY() {
        if (mode == Mode.SPIKE_BALL) {
            return spikeBallY;
        }
        return initialY;
    }

    @Override
    public ObjectSpawn getSpawn() {
        // For spike ball mode, return spawn with dynamic position for touch response collision
        if (mode == Mode.SPIKE_BALL) {
            return new ObjectSpawn(
                    spikeBallX,
                    spikeBallY,
                    spawn.objectId(),
                    spawn.subtype(),
                    spawn.renderFlags(),
                    spawn.respawnTracked(),
                    spawn.rawYWord()
            );
        }
        return spawn;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        if (mode == Mode.SPIKE_BALL) {
            // Update rotation angle (16-bit accumulation)
            angleWord = (angleWord + speed) & 0xFFFF;
            updateRotation();
        }
        // Brick mode has no update logic - it's static
    }

    /**
     * Update positions for all chain segments and spike ball head based on current angle.
     * <p>
     * The disassembly uses 16.16 fixed-point math (lines 55141-55152):
     * <pre>
     *   movem.l d4-d5,-(sp)  ; Save accumulators
     *   swap    d4           ; Extract integer part FIRST
     *   swap    d5
     *   add.w   d2,d4        ; Add center position
     *   add.w   d3,d5
     *   move.w  d5,(a2)+     ; STORE position
     *   move.w  d4,(a2)+
     *   movem.l (sp)+,d4-d5  ; Restore accumulators
     *   add.l   d0,d4        ; THEN accumulate for next iteration
     *   add.l   d1,d5
     * </pre>
     * <p>
     * The ROM stores position BEFORE accumulating, so:
     * - Chain segment 0 is at center (0 steps)
     * - Chain segment 1 is at 1 step from center
     * - Spike ball head is at chainCount steps
     */
    private void updateRotation() {
        // Extract effective angle (high byte of 16-bit word)
        int effectiveAngle = (angleWord >> 8) & 0xFF;

        // Get sin/cos (values range from -256 to +256)
        int sin = SINE_TABLE[effectiveAngle & 0xFF];
        int cos = SINE_TABLE[(effectiveAngle + 0x40) & 0xFF];

        // ROM uses 16.16 fixed-point accumulation:
        // swap d0; asr.l #4,d0  =>  ((sin << 16) >> 4) = sin << 12
        long sinStep = ((long) sin << 16) >> 4;
        long cosStep = ((long) cos << 16) >> 4;

        long accX = 0;
        long accY = 0;

        // Position chain segments: store position FIRST, then accumulate (ROM order)
        for (int i = 0; i < chainCount; i++) {
            // Position from current accumulated value (swap d4/d5 extracts high word)
            chainX[i] = initialX + (int) (accX >> 16);
            chainY[i] = initialY + (int) (accY >> 16);

            // Then accumulate for next iteration
            accX += cosStep;  // X uses cosine (d1 in ROM)
            accY += sinStep;  // Y uses sine (d0 in ROM)
        }

        // Spike ball head at end of chain (lines 55154-55159)
        // Final position after all chain link accumulations
        spikeBallX = initialX + (int) (accX >> 16);
        spikeBallY = initialY + (int) (accY >> 16);
    }

    // SolidObjectProvider implementation (brick mode only)

    @Override
    public SolidObjectParams getSolidParams() {
        if (mode == Mode.BRICK) {
            return BRICK_PARAMS;
        }
        return null;  // Spike ball has no solid collision
    }

    @Override
    public boolean isTopSolidOnly() {
        return false;  // Brick is fully solid
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return mode == Mode.BRICK && !isDestroyed();
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // No special handling needed for brick contact
    }

    // TouchResponseProvider implementation (spike ball mode only)

    @Override
    public int getCollisionFlags() {
        if (mode == Mode.SPIKE_BALL) {
            return SPIKE_BALL_COLLISION_FLAGS;
        }
        return 0;  // Brick doesn't use touch response
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ensureMappingsLoaded();

        // Draw debug collision when F1 debug view is enabled
        if (isDebugViewEnabled()) {
            appendDebug(commands);
        }

        if (mappings == null || mappings.isEmpty()) {
            return;
        }

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;

        if (mode == Mode.BRICK) {
            // Render brick (frame 2)
            if (mappings.size() > 2) {
                SpriteMappingFrame brickFrame = mappings.get(2);
                if (brickFrame != null && !brickFrame.pieces().isEmpty()) {
                    renderPieces(graphicsManager, brickFrame.pieces(), initialX, initialY, hFlip, vFlip);
                }
            }
        } else {
            // Render chain segments first (frame 1 = chain link)
            if (mappings.size() > 1) {
                SpriteMappingFrame chainFrame = mappings.get(1);
                if (chainFrame != null && !chainFrame.pieces().isEmpty()) {
                    for (int i = 0; i < chainCount; i++) {
                        renderPieces(graphicsManager, chainFrame.pieces(), chainX[i], chainY[i], false, false);
                    }
                }
            }

            // Render spike ball head (frame 0)
            if (!mappings.isEmpty()) {
                SpriteMappingFrame headFrame = mappings.get(0);
                if (headFrame != null && !headFrame.pieces().isEmpty()) {
                    renderPieces(graphicsManager, headFrame.pieces(), spikeBallX, spikeBallY, false, false);
                }
            }
        }
    }

    private void renderPieces(GraphicsManager graphicsManager, List<SpriteMappingPiece> pieces,
                              int drawX, int drawY, boolean hFlip, boolean vFlip) {
        SpritePieceRenderer.renderPieces(
                pieces,
                drawX,
                drawY,
                0,  // Base pattern index (level art starts at 0)
                -1, // Use palette from piece
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
        // From disassembly: brick = priority 4, spike ball = priority 5
        return RenderPriority.clamp(mode == Mode.BRICK ? 4 : 5);
    }

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
            mappings = S2SpriteDataLoader.loadMappingFrames(reader, Sonic2Constants.MAP_UNC_OBJ75_ADDR);
            LOGGER.fine("Loaded " + mappings.size() + " Obj75 mapping frames");
        } catch (IOException | RuntimeException e) {
            LOGGER.warning("Failed to load Obj75 mappings: " + e.getMessage());
        }
    }

    private void appendDebug(List<GLCommand> commands) {
        if (mode == Mode.BRICK) {
            // Draw brick collision box
            int left = initialX - BRICK_HALF_WIDTH;
            int right = initialX + BRICK_HALF_WIDTH;
            int top = initialY - BRICK_TOP_HEIGHT;
            int bottom = initialY + BRICK_BOTTOM_HEIGHT;

            // Green for solid collision
            appendLine(commands, left, top, right, top, 0.0f, 1.0f, 0.0f);
            appendLine(commands, right, top, right, bottom, 0.0f, 1.0f, 0.0f);
            appendLine(commands, right, bottom, left, bottom, 0.0f, 1.0f, 0.0f);
            appendLine(commands, left, bottom, left, top, 0.0f, 1.0f, 0.0f);

            // Center cross
            appendLine(commands, initialX - 4, initialY, initialX + 4, initialY, 1.0f, 1.0f, 0.0f);
            appendLine(commands, initialX, initialY - 4, initialX, initialY + 4, 1.0f, 1.0f, 0.0f);
        } else {
            // Draw center point (yellow)
            appendLine(commands, initialX - 4, initialY, initialX + 4, initialY, 1.0f, 1.0f, 0.0f);
            appendLine(commands, initialX, initialY - 4, initialX, initialY + 4, 1.0f, 1.0f, 0.0f);

            // Draw chain segment positions (small cyan crosses)
            for (int i = 0; i < chainCount; i++) {
                appendLine(commands, chainX[i] - 2, chainY[i], chainX[i] + 2, chainY[i], 0.0f, 1.0f, 1.0f);
                appendLine(commands, chainX[i], chainY[i] - 2, chainX[i], chainY[i] + 2, 0.0f, 1.0f, 1.0f);
            }

            // Draw spike ball head position (red cross)
            appendLine(commands, spikeBallX - 4, spikeBallY, spikeBallX + 4, spikeBallY, 1.0f, 0.0f, 0.0f);
            appendLine(commands, spikeBallX, spikeBallY - 4, spikeBallX, spikeBallY + 4, 1.0f, 0.0f, 0.0f);
        }
    }

    private void appendLine(List<GLCommand> commands, int x1, int y1, int x2, int y2, float r, float g, float b) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x2, y2, 0, 0));
    }

    private boolean isDebugViewEnabled() {
        return DEBUG_VIEW_ENABLED && OVERLAY_MANAGER.isEnabled(DebugOverlayToggle.OVERLAY);
    }
}
