package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.debug.DebugOverlayManager;
import uk.co.jamesj999.sonic.debug.DebugOverlayToggle;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.OscillationManager;
import uk.co.jamesj999.sonic.game.sonic2.S2SpriteDataLoader;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;
import uk.co.jamesj999.sonic.game.sonic2.scroll.Sonic2ZoneConstants;
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
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x15 - SwingingPlatform from OOZ, ARZ, MCZ.
 * <p>
 * A platform that swings from a pivot point, connected by chain links.
 * Uses global oscillation data to drive the swing motion.
 * <p>
 * <b>Disassembly Reference:</b> s2.asm lines 22408-22950 (Obj15 code)
 * <p>
 * <b>Subtype encoding:</b>
 * <ul>
 *   <li>Bits 0-3 (0x0F): Number of chain links (1-7)</li>
 *   <li>Bits 4-6 (0x70): Behavior mode</li>
 *   <li>Bit 7 (0x80): Display-only mode (no chain creation)</li>
 * </ul>
 * <p>
 * <b>Behavior modes (bits 4-6):</b>
 * <ul>
 *   <li>0x00: Normal swinging (uses oscillation data offset 0x18)</li>
 *   <li>0x10: Bounce Left - triggers swing on player proximity</li>
 *   <li>0x20: Static platform - no swing effect</li>
 *   <li>0x30: Bounce Right - mirror of bounce left</li>
 *   <li>0x40: MCZ Trap - pressure plate with rotation</li>
 * </ul>
 * <p>
 * <b>Zone-specific configuration:</b>
 * <ul>
 *   <li>OOZ: Uses dedicated Nemesis art (ArtNem_OOZSwingPlat), palette 2, width=0x20, yRadius=0x10</li>
 *   <li>MCZ: Uses level art (ArtKos_LevelArt), palette 0, width=0x18, yRadius=0x08</li>
 *   <li>ARZ: Uses level art (ArtKos_LevelArt), palette 0, width=0x20, yRadius=0x08</li>
 * </ul>
 */
public class SwingingPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOGGER = Logger.getLogger(SwingingPlatformObjectInstance.class.getName());

    // ROM-accurate 256-entry sine table (values from -256 to +256)
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
            -97, -103, -109, -117, -120, -126, -131, -136, -142, -147, -152, -157, -162, -167, -171, -176,
            -181, -185, -189, -193, -197, -201, -205, -209, -212, -216, -219, -222, -225, -228, -231, -234,
            -236, -238, -241, -243, -244, -246, -248, -249, -251, -252, -253, -254, -254, -255, -255, -255,
            -256, -255, -255, -255, -254, -254, -253, -252, -251, -249, -248, -246, -244, -243, -241, -238,
            -236, -234, -231, -228, -225, -222, -219, -216, -212, -209, -205, -201, -197, -193, -189, -185,
            -181, -176, -171, -167, -162, -157, -152, -147, -142, -136, -131, -126, -120, -117, -109, -103,
            -97, -92, -86, -80, -74, -68, -62, -56, -49, -43, -37, -31, -25, -18, -12, -6
    };

    // Zone configuration enum
    private enum ZoneConfig {
        OOZ(0x20, 0x10, 2),   // OOZ: width=32, yRadius=16, palette=2
        MCZ(0x18, 0x08, 0),   // MCZ: width=24, yRadius=8, palette=0
        ARZ(0x20, 0x08, 0);   // ARZ: width=32, yRadius=8, palette=0

        final int widthPixels;
        final int yRadius;
        final int paletteIndex;

        ZoneConfig(int widthPixels, int yRadius, int paletteIndex) {
            this.widthPixels = widthPixels;
            this.yRadius = yRadius;
            this.paletteIndex = paletteIndex;
        }
    }

    // Behavior mode enum
    private enum BehaviorMode {
        NORMAL,         // 0x00: Normal swinging
        BOUNCE_LEFT,    // 0x10: Triggers swing on player proximity (left)
        STATIC,         // 0x20: Static platform (no swing)
        BOUNCE_RIGHT,   // 0x30: Triggers swing on player proximity (right)
        TRAP            // 0x40: MCZ trap with rotation
    }

    // Trap mode constants
    private static final int TRAP_COOLDOWN = 60;       // Frames to wait after rotation
    private static final int TRAP_ROTATION_STEP = 8;   // Angle change per frame
    private static final int TRAP_ROTATION_MAX = 0x200; // Maximum rotation accumulator

    // Static mapping data (loaded per-zone)
    private static List<SpriteMappingFrame> oozMappings;
    private static List<SpriteMappingFrame> mczArzMappings;
    private static List<SpriteMappingFrame> trapMappings;
    private static boolean mappingsLoadAttempted;

    // Debug state
    private static final boolean DEBUG_VIEW_ENABLED = SonicConfigurationService.getInstance()
            .getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
    private static final DebugOverlayManager OVERLAY_MANAGER = GameServices.debugOverlay();

    // Position state
    private final int baseX;
    private final int baseY;
    private int x;
    private int y;

    // Configuration
    private final ZoneConfig zoneConfig;
    private final BehaviorMode behaviorMode;
    private final int chainCount;
    private final boolean displayOnly;

    // Chain link positions
    private final int[] chainX;
    private final int[] chainY;

    // Trap mode state
    private int trapCooldown;
    private int trapRotationAccum;
    private boolean trapRotatingClockwise;
    private int trapAngle;  // 16-bit angle word

    // Player tracking
    private boolean playerStanding;
    private ObjectSpawn dynamicSpawn;

    public SwingingPlatformObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.x = spawn.x();
        this.y = spawn.y();

        // Parse subtype
        int subtype = spawn.subtype();
        this.chainCount = Math.max(1, Math.min(7, subtype & 0x0F));
        this.displayOnly = (subtype & 0x80) != 0;

        // Determine behavior mode from bits 4-6
        int modeValue = (subtype & 0x70) >> 4;
        this.behaviorMode = switch (modeValue) {
            case 1 -> BehaviorMode.BOUNCE_LEFT;
            case 2 -> BehaviorMode.STATIC;
            case 3 -> BehaviorMode.BOUNCE_RIGHT;
            case 4 -> BehaviorMode.TRAP;
            default -> BehaviorMode.NORMAL;
        };

        // Determine zone configuration
        this.zoneConfig = determineZoneConfig();

        // Initialize chain position arrays
        this.chainX = new int[chainCount];
        this.chainY = new int[chainCount];

        // Initialize trap mode state
        this.trapAngle = 0x8000;  // Start at top position
        this.trapRotationAccum = 0;
        this.trapRotatingClockwise = false;
        this.trapCooldown = 0;

        // Calculate initial positions
        updatePositions(0);
        refreshDynamicSpawn();

        LOGGER.fine(() -> String.format(
                "SwingingPlatform init: pos=(%d,%d), subtype=0x%02X, chains=%d, mode=%s, zone=%s",
                baseX, baseY, subtype, chainCount, behaviorMode, zoneConfig));
    }

    private ZoneConfig determineZoneConfig() {
        LevelManager manager = LevelManager.getInstance();
        if (manager != null && manager.getCurrentLevel() != null) {
            int zoneId = manager.getCurrentLevel().getZoneIndex();
            if (zoneId == Sonic2ZoneConstants.ROM_ZONE_MCZ) {
                return ZoneConfig.MCZ;
            } else if (zoneId == Sonic2ZoneConstants.ROM_ZONE_ARZ) {
                return ZoneConfig.ARZ;
            }
        }
        // Default to OOZ
        return ZoneConfig.OOZ;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return dynamicSpawn != null ? dynamicSpawn : spawn;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        // Update based on behavior mode
        switch (behaviorMode) {
            case NORMAL -> updateNormalSwing(frameCounter);
            case BOUNCE_LEFT -> updateBounceSwing(player, true);
            case BOUNCE_RIGHT -> updateBounceSwing(player, false);
            case TRAP -> updateTrapMode(player);
            case STATIC -> { /* No update needed */ }
        }

        refreshDynamicSpawn();
    }

    /**
     * Normal swing mode: Uses global oscillation data at offset 0x18.
     */
    private void updateNormalSwing(int frameCounter) {
        // Get oscillation value from offset 0x18 (Oscillating_Data+0x18)
        int oscValue = OscillationManager.getByte(0x18);
        updatePositions(oscValue);
    }

    /**
     * Bounce swing mode: Triggers swing on player proximity.
     */
    private void updateBounceSwing(AbstractPlayableSprite player, boolean bounceLeft) {
        // Check player proximity
        int oscValue = OscillationManager.getByte(0x18);

        if (player != null) {
            int playerX = player.getCentreX();
            int dx = playerX - baseX;

            // Proximity check: within ±0x20 pixels
            if (Math.abs(dx) < 0x20) {
                // Player is close - use oscillation value
                if (bounceLeft) {
                    // Bounce left: use oscillation when < 0x40
                    if (oscValue < 0x40) {
                        oscValue = 0x40;  // Clamp to middle position
                    }
                } else {
                    // Bounce right: use oscillation when > 0x40
                    if (oscValue > 0x40) {
                        oscValue = 0x40;  // Clamp to middle position
                    }
                }
            }
        }

        updatePositions(oscValue);
    }

    /**
     * Trap mode: Pressure plate with rotation when player is nearby.
     */
    private void updateTrapMode(AbstractPlayableSprite player) {
        // Handle cooldown
        if (trapCooldown > 0) {
            trapCooldown--;
            updatePositionsFromAngle();
            return;
        }

        // Check if player is nearby
        boolean playerNearby = false;
        if (player != null) {
            int playerX = player.getCentreX();
            int dx = playerX - baseX;
            if (Math.abs(dx) < 0x20) {
                playerNearby = true;
            }
        }

        if (playerNearby || trapRotationAccum != 0) {
            // Continue or start rotation
            if (trapRotatingClockwise) {
                // Rotating clockwise (angle increasing)
                trapRotationAccum += TRAP_ROTATION_STEP;
                trapAngle = (trapAngle + TRAP_ROTATION_STEP) & 0xFFFF;

                if (trapRotationAccum >= TRAP_ROTATION_MAX) {
                    // Reached max - reset to top and start cooldown
                    trapRotationAccum = 0;
                    trapAngle = 0x8000;
                    trapRotatingClockwise = false;
                    trapCooldown = TRAP_COOLDOWN;
                }
            } else {
                // Rotating counter-clockwise (angle decreasing)
                trapRotationAccum -= TRAP_ROTATION_STEP;
                trapAngle = (trapAngle - TRAP_ROTATION_STEP) & 0xFFFF;

                if (trapRotationAccum <= -TRAP_ROTATION_MAX) {
                    // Reached min - set to bottom and flip direction
                    trapRotationAccum = 0;
                    trapAngle = 0x4000;
                    trapRotatingClockwise = true;
                    trapCooldown = TRAP_COOLDOWN;
                }
            }
        }

        updatePositionsFromAngle();
    }

    /**
     * Update platform and chain positions based on oscillation value.
     * <p>
     * Uses CalcSine to convert oscillation value to positional offset.
     * The oscillation value is centered at 0x40 (64) for proper pendulum motion:
     * <ul>
     *   <li>oscValue 0x00: swing far left</li>
     *   <li>oscValue 0x40: center position (hanging straight down)</li>
     *   <li>oscValue 0x80: swing far right</li>
     * </ul>
     */
    private void updatePositions(int oscValue) {
        // Center oscillation at 0x40 for pendulum motion
        // This converts the oscillation range to a signed swing angle
        // where 0 = hanging down, negative = left, positive = right
        int swingAngle = (oscValue - 0x40) & 0xFF;

        // Get sin/cos for the swing angle (values from -256 to +256)
        // sin gives horizontal offset (negative = left, positive = right)
        // cos gives vertical offset (always positive for |angle| <= 90°)
        int sin = calcSine(swingAngle);
        int cos = calcCosine(swingAngle);

        // Calculate platform position (at end of chain)
        // Chain length factor: 0x10 pixels per chain segment
        int chainLength = chainCount * 0x10;
        int xOffset = (sin * chainLength) >> 8;  // Divide by 256
        int yOffset = (cos * chainLength) >> 8;

        // Platform hangs down from pivot point
        this.x = baseX + xOffset;
        this.y = baseY + yOffset;

        // Calculate chain link positions (evenly distributed along arc)
        for (int i = 0; i < chainCount; i++) {
            int linkLength = (i + 1) * 0x10;
            int linkXOffset = (sin * linkLength) >> 8;
            int linkYOffset = (cos * linkLength) >> 8;
            chainX[i] = baseX + linkXOffset;
            chainY[i] = baseY + linkYOffset;
        }
    }

    /**
     * Update positions based on trap angle (16-bit angle word).
     */
    private void updatePositionsFromAngle() {
        // Extract effective angle (high byte)
        int effectiveAngle = (trapAngle >> 8) & 0xFF;
        updatePositions(effectiveAngle);
    }

    /**
     * Calculate sine value for angle (0-255 maps to 0-360 degrees).
     */
    private int calcSine(int angle) {
        return SINE_TABLE[angle & 0xFF];
    }

    /**
     * Calculate cosine value for angle.
     * Cosine = sine(angle + 64) where 64 = 90 degrees.
     */
    private int calcCosine(int angle) {
        return SINE_TABLE[(angle + 0x40) & 0xFF];
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ensureMappingsLoaded();

        // Draw debug collision box when F1 debug view is enabled
        if (isDebugViewEnabled()) {
            appendDebug(commands);
        }

        // Get appropriate mappings for this zone
        List<SpriteMappingFrame> mappings = getMappingsForZone();
        if (mappings == null || mappings.isEmpty()) {
            return;
        }

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;

        // Render anchor/base at pivot point (frame 2)
        if (mappings.size() > 2) {
            SpriteMappingFrame anchorFrame = mappings.get(2);
            if (anchorFrame != null && !anchorFrame.pieces().isEmpty()) {
                renderPieces(graphicsManager, anchorFrame.pieces(), baseX, baseY, hFlip, vFlip);
            }
        }

        // Render chain links (frame 1) at calculated positions
        // Note: displayOnly flag affects behavior state, not rendering - chains still render
        if (mappings.size() > 1) {
            SpriteMappingFrame chainFrame = mappings.get(1);
            if (chainFrame != null && !chainFrame.pieces().isEmpty()) {
                for (int i = 0; i < chainCount; i++) {
                    renderPieces(graphicsManager, chainFrame.pieces(), chainX[i], chainY[i], hFlip, vFlip);
                }
            }
        }

        // Render platform at end of chain (frame 0)
        if (mappings.size() > 0) {
            SpriteMappingFrame platformFrame = mappings.get(0);
            if (platformFrame != null && !platformFrame.pieces().isEmpty()) {
                renderPieces(graphicsManager, platformFrame.pieces(), x, y, hFlip, vFlip);
            }
        }
    }

    private List<SpriteMappingFrame> getMappingsForZone() {
        if (behaviorMode == BehaviorMode.TRAP) {
            return trapMappings;
        }
        return (zoneConfig == ZoneConfig.OOZ) ? oozMappings : mczArzMappings;
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
        return RenderPriority.clamp(3);  // Priority 3 from disassembly
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(
                zoneConfig.widthPixels,
                zoneConfig.yRadius,
                zoneConfig.yRadius + 1);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;  // Platform is only solid from the top
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (contact.standing()) {
            playerStanding = true;
        }
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return !isDestroyed();
    }

    private void refreshDynamicSpawn() {
        if (dynamicSpawn == null || dynamicSpawn.x() != x || dynamicSpawn.y() != y) {
            dynamicSpawn = new ObjectSpawn(
                    x,
                    y,
                    spawn.objectId(),
                    spawn.subtype(),
                    spawn.renderFlags(),
                    spawn.respawnTracked(),
                    spawn.rawYWord());
        }
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

            // Load OOZ mappings
            oozMappings = S2SpriteDataLoader.loadMappingFrames(reader, Sonic2Constants.MAP_UNC_OBJ15_A_ADDR);
            LOGGER.fine("Loaded " + oozMappings.size() + " Obj15 OOZ mapping frames");

            // Load MCZ/ARZ mappings - use dedicated MCZ address, not Obj83
            mczArzMappings = S2SpriteDataLoader.loadMappingFrames(reader, Sonic2Constants.MAP_UNC_OBJ15_MCZ_ADDR);
            LOGGER.fine("Loaded " + mczArzMappings.size() + " Obj15 MCZ/ARZ mapping frames");

            // Load trap mode mappings
            trapMappings = S2SpriteDataLoader.loadMappingFrames(reader, Sonic2Constants.MAP_UNC_OBJ15_TRAP_ADDR);
            LOGGER.fine("Loaded " + trapMappings.size() + " Obj15 trap mapping frames");

        } catch (IOException | RuntimeException e) {
            LOGGER.warning("Failed to load Obj15 mappings: " + e.getMessage());
        }
    }

    private void appendDebug(List<GLCommand> commands) {
        // Draw pivot point (yellow cross)
        appendLine(commands, baseX - 4, baseY, baseX + 4, baseY, 1.0f, 1.0f, 0.0f);
        appendLine(commands, baseX, baseY - 4, baseX, baseY + 4, 1.0f, 1.0f, 0.0f);

        // Draw chain link positions (cyan crosses)
        for (int i = 0; i < chainCount; i++) {
            appendLine(commands, chainX[i] - 2, chainY[i], chainX[i] + 2, chainY[i], 0.0f, 1.0f, 1.0f);
            appendLine(commands, chainX[i], chainY[i] - 2, chainX[i], chainY[i] + 2, 0.0f, 1.0f, 1.0f);
        }

        // Draw platform collision box (green)
        int halfWidth = zoneConfig.widthPixels;
        int halfHeight = zoneConfig.yRadius;
        int left = x - halfWidth;
        int right = x + halfWidth;
        int top = y - halfHeight;
        int bottom = y + halfHeight;

        appendLine(commands, left, top, right, top, 0.0f, 1.0f, 0.0f);      // Top (standing surface)
        appendLine(commands, right, top, right, bottom, 0.3f, 0.7f, 0.3f);
        appendLine(commands, right, bottom, left, bottom, 0.3f, 0.7f, 0.3f);
        appendLine(commands, left, bottom, left, top, 0.3f, 0.7f, 0.3f);

        // Draw platform center (red cross)
        appendLine(commands, x - 4, y, x + 4, y, 1.0f, 0.0f, 0.0f);
        appendLine(commands, x, y - 4, x, y + 4, 1.0f, 0.0f, 0.0f);
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
