package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.S2SpriteDataLoader;
import com.openggf.game.sonic2.constants.Sonic2Constants;

import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.level.WaterSystem;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.util.LazyMappingHolder;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x82 - Swinging Platform from ARZ.
 * <p>
 * A platform that swings down when the player stands on it, returning to its
 * rest position when the player leaves. Multiple subtypes control different
 * behaviors like falling, rising, or tracking water level.
 * <p>
 * <b>Disassembly Reference:</b> s2.asm lines 56632-56876 (Obj82 code)
 * <p>
 * <b>Subtype encoding:</b>
 * <ul>
 *   <li>Bits 0-3: Behavior type (0-7)</li>
 *   <li>Bits 4-5: Property index / frame select</li>
 * </ul>
 * <p>
 * <b>Behavior types:</b>
 * <ul>
 *   <li>0: Static platform (no behavior)</li>
 *   <li>1, 3: Wait for player contact, then fall after 30 frames</li>
 *   <li>2, 6: Fall with gravity</li>
 *   <li>4: Rise with anti-gravity</li>
 *   <li>5: Check terrain collision, then fall</li>
 *   <li>7: Follow water level</li>
 * </ul>
 * <p>
 * <b>Swinging:</b>
 * When enabled (subtype bits 0-3 not 0 and not 7), the platform swings
 * based on player contact. Standing on it increases the angle, leaving
 * decreases it. Max angle is 0x40 (90 degrees), giving ~4 pixels of Y offset.
 */
public class SwingingPformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOGGER = Logger.getLogger(SwingingPformObjectInstance.class.getName());

    // art_tile palette from disassembly: make_art_tile(ArtTile_ArtKos_LevelArt,0,0) for frame 0 (palette 3)
    // Actually, from obj82.asm: frame 0 uses palette 3, frame 1 uses palette 1
    // The palette is part of the mapping piece data, not added separately

    // Property table from disassembly (Obj82_Properties at line 56647)
    // Format: [width_pixels, y_radius]
    private static final int[][] PROPERTIES = {
            {0x20, 0x08},  // Property 0: 32px wide, 8px radius
            {0x1C, 0x32},  // Property 1: 28px wide, 50px radius (buggy: should be 0x30)
            {0x10, 0x10},  // Property 2: 16px wide, 16px radius (unused)
            {0x10, 0x10}   // Property 3: 16px wide, 16px radius (unused)
    };

    // Swinging constants
    private static final int MAX_SWING_ANGLE = 0x40;  // 90 degrees
    private static final int ANGLE_CHANGE_RATE = 4;   // Angle change per frame
    private static final int SWING_SCALE = 0x400;     // Scale factor for sine calculation

    // Physics constants
    private static final int GRAVITY = 8;             // Gravity acceleration (Type 2/6)
    private static final int WATER_SPEED = 2;         // Max speed toward water level (Type 7)
    private static final int CONTACT_DELAY = 0x1E;    // 30 frames delay before falling (Type 1/3)

    private static final LazyMappingHolder MAPPINGS = new LazyMappingHolder();

    // Position state
    private int x;
    private int y;
    private int baseY;          // Original Y position (objoff_30)
    private int subY;           // 8.8 fixed point Y for smooth movement
    private int yVel;           // 8.8 fixed point Y velocity

    // Object state
    private int widthPixels;
    private int yRadius;
    private int mappingFrame;
    private int behaviorType;   // Extracted from subtype bits 0-3
    private int delayCounter;   // objoff_36 - countdown for Type 1/3
    private int swingAngle;     // objoff_3E - current swing angle (0 to MAX_SWING_ANGLE)
    private boolean swingEnabled; // objoff_38 - whether swinging is active
    private boolean playerStanding; // Tracks if player is currently standing on platform
    public SwingingPformObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.x = spawn.x();
        this.y = spawn.y();
        this.baseY = spawn.y();
        this.subY = y << 8;
        this.yVel = 0;
        this.delayCounter = 0;
        this.swingAngle = 0;
        this.playerStanding = false;

        initFromSubtype();
        updateDynamicSpawn(x, y);
    }

    private void initFromSubtype() {
        int subtype = spawn.subtype();

        // Extract behavior type (bits 0-3)
        behaviorType = subtype & 0x0F;

        // Extract property index from subtype
        // Disassembly (lines 56675-56681):
        //   lsr.w   #3,d0           ; Shift right 3 bits
        //   andi.w  #$E,d0          ; Mask with 0x0E (binary: 1110) - gets bits 3-5 of original
        //   ... use for array indexing ...
        //   lsr.w   #1,d0           ; Divide by 2 to get actual index
        int propIndex = (subtype >> 3) & 0x0E;  // Gets 0, 2, 4, 6, 8, 10, 12, or 14
        propIndex = propIndex >> 1;              // Convert to 0, 1, 2, 3, 4, 5, 6, or 7

        // Clamp to valid property range (only 4 entries defined)
        if (propIndex >= PROPERTIES.length) {
            propIndex = 0;
        }

        widthPixels = PROPERTIES[propIndex][0];
        yRadius = PROPERTIES[propIndex][1];

        // Determine mapping frame based on property index
        // Disassembly (line 56681): move.b d0,mapping_frame(a0)
        // The frame is the propIndex directly, but we only have 2 frames in practice
        // Clamp to available frames (the mappings file only has 2 frames)
        mappingFrame = Math.min(propIndex, 1);

        // Enable swinging if behavior type is 1-6 (not 0 or 7)
        // From disassembly: objoff_38 is set to 1 for types 1-6
        // Note: When type 1/3/5 transition to 2/4/6, they clear swingEnabled
        // If a platform starts at type 2/4/6, swinging will be enabled but the Y movement
        // will override it (matching original behavior)
        swingEnabled = (behaviorType >= 1 && behaviorType <= 6);

        LOGGER.fine(() -> String.format(
                "SwingingPform init: subtype=0x%02X, behavior=%d, width=%d, yRadius=%d, frame=%d, swing=%s",
                subtype, behaviorType, widthPixels, yRadius, mappingFrame, swingEnabled));
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
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (isDestroyed()) {
            return;
        }

        // Update behavior based on type
        updateBehavior(player);

        // Update swinging animation
        updateSwinging();

        updateDynamicSpawn(x, y);
    }

    /**
     * Update behavior based on subtype.
     * Corresponds to Obj82_Types jump table in disassembly.
     */
    private void updateBehavior(AbstractPlayableSprite player) {
        switch (behaviorType) {
            case 0 -> {
                // Type 0: Static - no behavior
            }
            case 1, 3 -> updateWaitForContact();
            case 2, 6 -> updateFalling();
            case 4 -> updateRising();
            case 5 -> updateCheckCollision();
            case 7 -> updateWaterLevel();
            default -> {
                // Unknown type - treat as static
            }
        }
    }

    /**
     * Type 1/3: Wait for player contact, then fall after delay.
     * Corresponds to loc_2A36A in disassembly.
     */
    private void updateWaitForContact() {
        if (delayCounter > 0) {
            delayCounter--;
            if (delayCounter == 0) {
                // Delay finished - transition to falling
                behaviorType++;  // 1 -> 2, 3 -> 4
                swingEnabled = false;
            }
        } else if (playerStanding) {
            // Player just landed - start delay
            delayCounter = CONTACT_DELAY;
        }
    }

    /**
     * Type 2/6: Fall with gravity until hitting floor.
     * Corresponds to loc_2A392 in disassembly.
     */
    private void updateFalling() {
        // Apply gravity
        yVel += GRAVITY;

        // Move
        subY += yVel;
        y = subY >> 8;

        // Check floor collision using ObjectTerrainUtils (mirrors ROM's ObjCheckFloorDist)
        TerrainCheckResult result = ObjectTerrainUtils.checkFloorDist(x, y, yRadius);
        if (result.hasCollision()) {
            // Hit floor - snap to surface
            y = y + result.distance();
            subY = y << 8;
            yVel = 0;
            behaviorType = 0;  // Return to idle
            baseY = y;         // Update base position
        }
    }

    /**
     * Type 4: Rise with anti-gravity until hitting ceiling.
     * Corresponds to loc_2A3B6 in disassembly.
     */
    private void updateRising() {
        // Apply anti-gravity
        yVel -= GRAVITY;

        // Move
        subY += yVel;
        y = subY >> 8;

        // Check ceiling collision using ObjectTerrainUtils (mirrors ROM's ObjCheckCeilingDist)
        TerrainCheckResult result = ObjectTerrainUtils.checkCeilingDist(x, y, yRadius);
        if (result.hasCollision()) {
            // Hit ceiling - snap to surface
            y = y - result.distance();
            subY = y << 8;
            yVel = 0;
            behaviorType = 0;  // Return to idle
            baseY = y;         // Update base position
        }
    }

    /**
     * Type 5: Check terrain collision status, transition if colliding.
     * Corresponds to loc_2A3D8 in disassembly.
     */
    private void updateCheckCollision() {
        // In the original game, this checks objoff_3F collision bits
        // For simplicity, check if we're near terrain
        TerrainCheckResult result = ObjectTerrainUtils.checkFloorDist(x, y, yRadius);
        if (result.hasCollision() || result.distance() < 8) {
            // Near or touching floor - transition to falling
            behaviorType++;  // 5 -> 6
            swingEnabled = false;
        }
    }

    /**
     * Type 7: Move platform toward water level.
     * Corresponds to loc_2A3EC in disassembly.
     */
    private void updateWaterLevel() {
        if (services().currentLevel() == null) {
            return;
        }

        // Get water level from WaterSystem
        int zoneId = services().currentLevel().getZoneIndex();
        int actId = services().currentAct();
        WaterSystem waterSystem = WaterSystem.getInstance();

        if (!waterSystem.hasWater(zoneId, actId)) {
            return;  // No water in this level
        }

        int waterLevel = waterSystem.getWaterLevelY(zoneId, actId);
        if (waterLevel <= 0) {
            return;  // No water level
        }

        int delta = waterLevel - y;
        if (delta == 0) {
            return;  // At water level
        }

        // Clamp movement to WATER_SPEED
        if (delta > WATER_SPEED) {
            delta = WATER_SPEED;
        } else if (delta < -WATER_SPEED) {
            delta = -WATER_SPEED;
        }

        y += delta;
        subY = y << 8;

        // Check collision based on movement direction
        if (delta < 0) {
            // Moving up - check ceiling
            TerrainCheckResult result = ObjectTerrainUtils.checkCeilingDist(x, y, yRadius);
            if (result.hasCollision()) {
                y = y - result.distance();
                subY = y << 8;
            }
        } else {
            // Moving down - check floor
            TerrainCheckResult result = ObjectTerrainUtils.checkFloorDist(x, y, yRadius);
            if (result.hasCollision()) {
                y = y + result.distance();
                subY = y << 8;
            }
        }
    }

    /**
     * Update the swinging animation.
     * Corresponds to loc_2A432 in disassembly.
     */
    private void updateSwinging() {
        if (!swingEnabled) {
            return;
        }

        // Check if player is standing on platform
        boolean standing = services().objectManager() != null &&
                services().objectManager().isAnyPlayerRiding(this);
        playerStanding = standing;

        if (standing) {
            // Player is standing - increase swing angle
            if (swingAngle < MAX_SWING_ANGLE) {
                swingAngle += ANGLE_CHANGE_RATE;
                if (swingAngle > MAX_SWING_ANGLE) {
                    swingAngle = MAX_SWING_ANGLE;
                }
            }
        } else {
            // Player not standing - return to center
            if (swingAngle > 0) {
                swingAngle -= ANGLE_CHANGE_RATE;
                if (swingAngle < 0) {
                    swingAngle = 0;
                }
            }
        }

        // Calculate Y offset from swing angle
        // Formula: y_offset = sin(angle) * SWING_SCALE / 65536
        if (swingAngle > 0) {
            int sineValue = getSine(swingAngle);
            int yOffset = (sineValue * SWING_SCALE) >> 16;
            y = baseY + yOffset;
            subY = y << 8;
        } else {
            y = baseY;
            subY = y << 8;
        }
    }

    /**
     * Get sine value for angle (0-64 maps to 0-90 degrees).
     * Returns value in 8-bit format (0 to 256 for 0 to 1).
     * Delegates to TrigLookupTable.sinHex() which uses the ROM-accurate SINCOSLIST.
     */
    private int getSine(int angle) {
        if (angle <= 0) {
            return 0;
        }
        if (angle > MAX_SWING_ANGLE) {
            angle = MAX_SWING_ANGLE;
        }
        return TrigLookupTable.sinHex(angle);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        List<SpriteMappingFrame> mappings = MAPPINGS.get(
                Sonic2Constants.MAP_UNC_OBJ82_ADDR, S2SpriteDataLoader::loadMappingFrames, "Obj82");
        if (mappings.isEmpty()) {
            return;
        }

        int frame = mappingFrame;
        if (frame < 0 || frame >= mappings.size()) {
            frame = 0;
        }

        SpriteMappingFrame mapping = mappings.get(frame);
        if (mapping == null || mapping.pieces().isEmpty()) {
            return;
        }

        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        List<SpriteMappingPiece> pieces = mapping.pieces();

        // Render pieces in reverse order (painter's algorithm)
        for (int i = pieces.size() - 1; i >= 0; i--) {
            SpriteMappingPiece piece = pieces.get(i);
            renderPiece(graphicsManager, piece, x, y, hFlip, vFlip);
        }
    }

    private void renderPiece(GraphicsManager graphicsManager, SpriteMappingPiece piece,
                             int drawX, int drawY, boolean hFlip, boolean vFlip) {
        SpritePieceRenderer.renderPieces(
                List.of(piece),
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
        // Use widthPixels as half-width and yRadius as half-height
        // For property 0: widthPixels=32, yRadius=8 (small platform)
        // For property 1: widthPixels=28, yRadius=50 (tall pillar)
        return new SolidObjectParams(widthPixels, yRadius, yRadius + 1);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;  // Platform is only solid from the top
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        // Track standing for swinging logic
        if (contact.standing()) {
            playerStanding = true;
        }
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        return !isDestroyed();
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        int halfWidth = widthPixels;
        int halfHeight = yRadius;
        int left = x - halfWidth;
        int right = x + halfWidth;
        int top = y - halfHeight;
        int bottom = y + halfHeight;

        // Draw collision box in green
        ctx.drawLine(left, top, right, top, 0.0f, 1.0f, 0.0f);      // Top (standing surface)
        ctx.drawLine(right, top, right, bottom, 0.3f, 0.7f, 0.3f);
        ctx.drawLine(right, bottom, left, bottom, 0.3f, 0.7f, 0.3f);
        ctx.drawLine(left, bottom, left, top, 0.3f, 0.7f, 0.3f);

        // Draw center cross in red to show object origin
        ctx.drawLine(x - 4, y, x + 4, y, 1.0f, 0.0f, 0.0f);
        ctx.drawLine(x, y - 4, x, y + 4, 1.0f, 0.0f, 0.0f);
    }

}

