package com.openggf.game.sonic2.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Collapsing Platform (Object 0x1F) - OOZ, MCZ, and ARZ.
 * <p>
 * A platform that collapses into falling fragments when the player stands on it
 * for 7 frames. Each zone has different art, fragment counts, and delay patterns.
 * <p>
 * Based on Obj1F in the Sonic 2 disassembly (s2.asm).
 * <p>
 * Zone-specific behavior:
 * - OOZ: 7 fragments, dedicated Nemesis art at 0x809D0
 * - MCZ: 6 fragments, dedicated Nemesis art at 0xF1ABA
 * - ARZ: 8 fragments, uses level art tiles (0x55, 0x59, 0xA3, 0xA7)
 */
public class CollapsingPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOGGER = Logger.getLogger(CollapsingPlatformObjectInstance.class.getName());

    /**
     * Zone-specific configuration for collapsing platforms.
     * <p>
     * Note: Fragment visual offsets are stored in the sprite mapping data,
     * not here. Each fragment spawns at the parent's exact x/y position,
     * and the sprite piece's offsets provide the visual displacement.
     */
    private record ZoneConfig(
            int halfWidth,
            int halfHeight,
            int[] delayData,
            String artKey,
            int palette,
            boolean usesLevelArt,
            int[][] pieceOffsets  // For debug rendering only - actual offsets come from mappings
    ) {}

    // OOZ: 7 fragments from obj1F_b.asm
    // Delay values: $1A, $12, $0A, $02, $16, $0E, $06
    // Piece offsets from mapping data (for debug rendering)
    private static final ZoneConfig OOZ_CONFIG = new ZoneConfig(
            0x40,  // width_pixels from disassembly (half-width, collision extends ±0x40 from center)
            0x10,  // 16px half-height
            new int[]{0x1A, 0x12, 0x0A, 0x02, 0x16, 0x0E, 0x06},
            Sonic2ObjectArtKeys.OOZ_COLLAPSING_PLATFORM,
            3,
            false,
            new int[][]{  // Piece offsets from obj1F_b.asm for debug rendering
                    {-0x40, -0x10},  // Piece 0
                    {-0x20, -0x10},  // Piece 1
                    {0x00, -0x10},   // Piece 2
                    {0x20, -0x10},   // Piece 3
                    {-0x40, 0x10},   // Piece 4
                    {-0x20, 0x10},   // Piece 5
                    {0x00, 0x10}     // Piece 6
            }
    );

    // MCZ: 6 fragments from obj1F_c.asm
    // Delay values: $1A, $16, $12, $0E, $0A, $02
    private static final ZoneConfig MCZ_CONFIG = new ZoneConfig(
            0x20,  // width_pixels from disassembly (half-width, collision extends ±0x20 from center)
            0x10,  // 16px half-height
            new int[]{0x1A, 0x16, 0x12, 0x0E, 0x0A, 0x02},
            Sonic2ObjectArtKeys.MCZ_COLLAPSING_PLATFORM,
            3,
            false,
            new int[][]{  // Piece offsets from obj1F_c.asm frame 1 for debug rendering
                    {-0x20, -0x10},  // Piece 0
                    {-0x10, -0x10},  // Piece 1
                    {0x00, -0x10},   // Piece 2
                    {0x10, -0x10},   // Piece 3
                    {-0x10, 0x00},   // Piece 4
                    {0x08, 0x00}     // Piece 5
            }
    );

    // ARZ: 8 fragments from obj1F_d.asm
    // Delay values: $16, $1A, $18, $12, $06, $0E, $0A, $02
    private static final ZoneConfig ARZ_CONFIG = new ZoneConfig(
            0x20,  // width_pixels from disassembly (half-width, collision extends ±0x20 from center)
            0x10,  // 16px half-height
            new int[]{0x16, 0x1A, 0x18, 0x12, 0x06, 0x0E, 0x0A, 0x02},
            null,  // Uses level art
            2,
            true,
            new int[][]{  // Piece offsets from obj1F_d.asm frame 1 for debug rendering
                    {-0x20, -0x10},  // Piece 0
                    {-0x10, -0x10},  // Piece 1
                    {0x00, -0x10},   // Piece 2
                    {0x10, -0x10},   // Piece 3
                    {-0x20, 0x00},   // Piece 4
                    {-0x10, 0x00},   // Piece 5
                    {0x00, 0x00},    // Piece 6
                    {0x10, 0x00}     // Piece 7
            }
    );

    // Default config for unknown zones (uses OOZ config)
    private static final ZoneConfig DEFAULT_CONFIG = OOZ_CONFIG;

    // ARZ uses level art tiles at specific indices from obj1F_d.asm
    // Palette line 2 as per disassembly: make_art_tile(ArtTile_ArtKos_LevelArt,2,0)
    private static final int ARZ_PALETTE = 2;

    // ARZ Frame 0 (intact) - 4 pieces from obj1F_d.asm Map_obj1F_d_0004
    // Note: piece palette is 0 so that (0 + ARZ_PALETTE) & 3 = 2 (correct palette)
    private static final SpriteMappingFrame ARZ_FRAME_INTACT = new SpriteMappingFrame(List.of(
            new SpriteMappingPiece(-0x20, -0x10, 4, 2, 0x55, false, false, 0),  // Top-left
            new SpriteMappingPiece(0x00, -0x10, 4, 2, 0x55, true, false, 0),   // Top-right (H-flip)
            new SpriteMappingPiece(-0x20, 0x00, 4, 2, 0xA3, false, false, 0),  // Bottom-left
            new SpriteMappingPiece(0x00, 0x00, 4, 2, 0xA3, true, false, 0)     // Bottom-right (H-flip)
    ));

    // ARZ Frame 1 (collapsed) - 8 pieces from obj1F_d.asm Map_obj1F_d_0026
    // Note: piece palette is 0 so that (0 + ARZ_PALETTE) & 3 = 2 (correct palette)
    private static final SpriteMappingFrame ARZ_FRAME_COLLAPSED = new SpriteMappingFrame(List.of(
            new SpriteMappingPiece(-0x20, -0x10, 2, 2, 0x55, false, false, 0),  // Piece 0
            new SpriteMappingPiece(-0x10, -0x10, 2, 2, 0x59, false, false, 0),  // Piece 1
            new SpriteMappingPiece(0x00, -0x10, 2, 2, 0x59, true, false, 0),   // Piece 2 (H-flip)
            new SpriteMappingPiece(0x10, -0x10, 2, 2, 0x55, true, false, 0),   // Piece 3 (H-flip)
            new SpriteMappingPiece(-0x20, 0x00, 2, 2, 0xA3, false, false, 0),  // Piece 4
            new SpriteMappingPiece(-0x10, 0x00, 2, 2, 0xA7, false, false, 0),  // Piece 5
            new SpriteMappingPiece(0x00, 0x00, 2, 2, 0xA7, true, false, 0),    // Piece 6 (H-flip)
            new SpriteMappingPiece(0x10, 0x00, 2, 2, 0xA3, true, false, 0)     // Piece 7 (H-flip)
    ));

    // State
    private static final int INITIAL_DELAY = 7;  // 7 frames before collapse starts

    // Gravity from ObjectMoveAndFall (s2.asm line 29950)
    private static final int GRAVITY = 0x38;

    private ZoneConfig config;
    private int delayCounter = INITIAL_DELAY;
    private boolean stoodOnFlag = false;
    private boolean collapsed = false;
    private boolean inFragmentPhase = false;  // ROM: parent becomes fragment 0, stays solid during delay
    private int fragmentPhaseDelay = 0;       // ROM: delay_counter for the parent-as-fragment-0
    private int mappingFrame = 0;  // 0 = intact, 1 = collapsed appearance

    // Post-fragment parent fall state (ROM: Obj1F_FragmentFall)
    private int parentVelY;
    private int parentY;
    private int parentYFrac;

    // Orientation from spawn render_flags (inherited by fragments per disassembly)
    private final boolean hFlip;
    private final boolean vFlip;

    public CollapsingPlatformObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        // Extract flip flags from spawn renderFlags (bit 0 = hFlip, bit 1 = vFlip)
        this.hFlip = (spawn.renderFlags() & 1) != 0;
        this.vFlip = (spawn.renderFlags() & 2) != 0;
        initZoneConfig();
    }

    @Override
    public int getX() {
        return spawn.x();
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

        // ROM: Obj1F_FragmentFall — collapsed parent falls with gravity, delete when offscreen.
        if (collapsed) {
            parentVelY += GRAVITY;
            int y32 = (parentY << 16) | (parentYFrac & 0xFFFF);
            y32 += ((int) (short) parentVelY) << 8;
            parentY = y32 >> 16;
            parentYFrac = y32 & 0xFFFF;
            if (!isOnScreen(128)) {
                setDestroyed(true);
            }
            return;
        }

        // ROM: Obj1F_Fragment phase — parent stays solid at its original position
        // until its fragment delay expires, then detaches the player.
        // In the ROM, the parent object IS fragment 0 (routine changed to Obj1F_Fragment).
        // Only fragment 0 has stood_on_flag=1 so only it calls PlatformObject.
        if (inFragmentPhase) {
            if (fragmentPhaseDelay > 0) {
                fragmentPhaseDelay--;
                // ROM: sub_10B36 — when delay reaches zero, detach both players.
                // bclr #status.player.on_object / bset #status.player.in_air
                if (fragmentPhaseDelay <= 0) {
                    collapsed = true;
                    parentY = spawn.y();
                    if (player != null) {
                        if (GameServices.level() != null && services().objectManager() != null) {
                            services().objectManager().clearRidingObject(player);
                        }
                        player.setAir(true);
                        player.setOnObject(false);
                    }
                }
            }
            return;
        }

        // ROM: Obj1F_Main — check stood_on_flag and delay_counter
        if (stoodOnFlag) {
            if (delayCounter <= 0) {
                collapse();
                return;
            }
            delayCounter--;
        }

        // ROM: check status standing_mask bits — set stood_on_flag when player is on platform
        boolean isStanding = isPlayerStanding();
        if (isStanding) {
            stoodOnFlag = true;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || collapsed) {
            return; // ROM: parent is invisible during fragment-fall; fragments handle rendering
        }

        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null || config == null) {
            return;
        }

        if (config.usesLevelArt()) {
            // ARZ uses level patterns - render using level tiles
            renderUsingLevelArt(commands);
        } else {
            // OOZ/MCZ use dedicated art
            PatternSpriteRenderer renderer = renderManager.getRenderer(config.artKey());
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), hFlip, vFlip);
        }
    }

    @Override
    public SolidObjectParams getSolidParams() {
        if (config == null) {
            return new SolidObjectParams(0x40, 0x10, 0x10);
        }
        return new SolidObjectParams(config.halfWidth(), config.halfHeight(), config.halfHeight());
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // ROM: Obj1F_Main — standing_mask bits set stood_on_flag.
        // Using the callback (like S1/S3K) instead of polling ensures the flag
        // is set on the same frame the player lands, matching ROM timing.
        if (contact.standing() && !inFragmentPhase && !collapsed) {
            stoodOnFlag = true;
        }
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return !collapsed && !isDestroyed();
    }

    @Override
    public boolean shouldStayActiveWhenRemembered() {
        // Platform must remain in the active set during the fragment phase so
        // SolidContacts can continue repositioning the player on the invisible
        // parent. Matches S1 pattern where markRemembered is deferred to destroy.
        return !collapsed;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    private void initZoneConfig() {
        final int zoneIndex = (GameServices.level() != null && services().currentLevel() != null)
                ? services().currentLevel().getZoneIndex()
                : -1;

        config = switch (zoneIndex) {
            case Sonic2Constants.ZONE_OIL_OCEAN -> OOZ_CONFIG;
            case Sonic2Constants.ZONE_MYSTIC_CAVE -> MCZ_CONFIG;
            case Sonic2Constants.ZONE_ARZ -> ARZ_CONFIG;
            default -> DEFAULT_CONFIG;
        };

        LOGGER.fine(() -> String.format("CollapsingPlatform at (%d,%d) using %s config",
                spawn.x(), spawn.y(),
                zoneIndex == Sonic2Constants.ZONE_OIL_OCEAN ? "OOZ" :
                        zoneIndex == Sonic2Constants.ZONE_MYSTIC_CAVE ? "MCZ" :
                                zoneIndex == Sonic2Constants.ZONE_ARZ ? "ARZ" : "DEFAULT"));
    }

    private boolean isPlayerStanding() {
        if (GameServices.level() == null || services().objectManager() == null) {
            return false;
        }
        return services().objectManager().isAnyPlayerRiding(this);
    }

    private void collapse() {
        if (inFragmentPhase || collapsed) {
            return;
        }

        // ROM: Obj1F_CreateFragments — parent becomes fragment 0
        // The parent stays solid at its original position during the fragment delay.
        // Only fragment 0 (the parent) provides collision; other fragments are visual only.
        inFragmentPhase = true;
        fragmentPhaseDelay = config.delayData()[0];  // Parent gets first delay value
        mappingFrame = 1;  // Show collapsed appearance

        // Play collapse sound
        services().playSfx(Sonic2Sfx.SMASH.id);

        // Spawn visual-only fragments (they do NOT provide collision)
        spawnFragments();

        // Mark as remembered to prevent respawn (ROM-accurate behavior)
        if (GameServices.level() != null && services().objectManager() != null) {
            services().objectManager().markRemembered(spawn);
        }

        LOGGER.fine(() -> String.format("CollapsingPlatform at (%d,%d) collapsed, spawning %d fragments",
                spawn.x(), spawn.y(), config.delayData().length));
    }

    private void spawnFragments() {
        ObjectManager objectManager = services().objectManager();
        ObjectRenderManager renderManager = services().renderManager();
        if (objectManager == null) {
            return;
        }

        int[] delayData = config.delayData();

        // Fragments spawn at parent's exact position - sprite piece offsets handle visual displacement
        // (This matches the disassembly where fragments inherit parent x_pos/y_pos and render_flags)
        for (int i = 0; i < delayData.length; i++) {
            int delay = delayData[i];

            CollapsingPlatformFragmentInstance fragment = new CollapsingPlatformFragmentInstance(
                    spawn.x(), spawn.y(), delay, i, config, renderManager, hFlip, vFlip);
            objectManager.addDynamicObject(fragment);
        }
    }

    private void renderUsingLevelArt(List<GLCommand> commands) {
        // ARZ uses level art tiles at specific indices from obj1F_d.asm
        // basePatternIndex = 0 because level patterns start at index 0
        SpriteMappingFrame frame = (mappingFrame == 0) ? ARZ_FRAME_INTACT : ARZ_FRAME_COLLAPSED;
        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        List<SpriteMappingPiece> pieces = frame.pieces();

        // Draw in reverse order (Painter's Algorithm) - first piece in list appears on top
        for (int i = pieces.size() - 1; i >= 0; i--) {
            SpriteMappingPiece piece = pieces.get(i);
            SpritePieceRenderer.renderPieces(
                    List.of(piece),
                    spawn.x(),
                    spawn.y(),
                    0,  // Level patterns start at index 0
                    ARZ_PALETTE,
                    hFlip,  // Frame H-flip from spawn render_flags
                    vFlip,  // Frame V-flip from spawn render_flags
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

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        if (config == null) {
            return;
        }
        int halfWidth = config.halfWidth();
        int halfHeight = config.halfHeight();
        int x = spawn.x();
        int y = spawn.y();
        int left = x - halfWidth;
        int right = x + halfWidth;
        int top = y - halfHeight;
        int bottom = y + halfHeight;

        float r = collapsed ? 0.5f : 0.8f;
        float g = 0.4f;
        float b = collapsed ? 0.2f : 0.6f;

        ctx.drawLine(left, top, right, top, r, g, b);
        ctx.drawLine(right, top, right, bottom, r, g, b);
        ctx.drawLine(right, bottom, left, bottom, r, g, b);
        ctx.drawLine(left, bottom, left, top, r, g, b);
    }

    /**
     * Visual-only fragment that falls after the platform collapses.
     * Each fragment has a staggered delay before it starts falling with gravity.
     * <p>
     * In the ROM, only fragment 0 (the parent object itself, reusing its SST) provides
     * collision via PlatformObject. All other fragments have stood_on_flag=0 and never
     * call PlatformObject — they are purely visual. The parent class handles the collision
     * during the fragment phase via {@code inFragmentPhase} / {@code fragmentPhaseDelay}.
     * <p>
     * Each fragment uses static_mappings mode where the mappings pointer points to a
     * single sprite piece. The piece's x/y offsets provide visual displacement.
     */
    public static class CollapsingPlatformFragmentInstance extends AbstractFallingFragment {

        private final int fragmentIndex;
        private final ZoneConfig config;
        private final ObjectRenderManager renderManager;

        // Inherited from parent (per disassembly: render_flags copied from parent to fragment)
        private final boolean hFlip;
        private final boolean vFlip;

        // Piece offset from parent (for rendering positioning)
        private final int pieceOffsetX;
        private final int pieceOffsetY;

        public CollapsingPlatformFragmentInstance(int parentX, int parentY, int delay, int fragmentIndex,
                                                   ZoneConfig config, ObjectRenderManager renderManager,
                                                   boolean hFlip, boolean vFlip) {
            super(new ObjectSpawn(parentX + computeOffsetX(config, fragmentIndex, hFlip),
                            parentY + computeOffsetY(config, fragmentIndex, vFlip),
                            0x1F, 0, 0, false, 0),
                    "CollapsingPlatformFragment", delay, 4);

            this.pieceOffsetX = computeOffsetX(config, fragmentIndex, hFlip);
            this.pieceOffsetY = computeOffsetY(config, fragmentIndex, vFlip);
            this.fragmentIndex = fragmentIndex;
            this.config = config;
            this.renderManager = renderManager;
            this.hFlip = hFlip;
            this.vFlip = vFlip;
        }

        private static int computeOffsetX(ZoneConfig config, int fragmentIndex, boolean hFlip) {
            int offsetX = 0;
            if (config != null && config.pieceOffsets() != null &&
                    fragmentIndex < config.pieceOffsets().length) {
                offsetX = config.pieceOffsets()[fragmentIndex][0];
            }
            return hFlip ? -offsetX : offsetX;
        }

        private static int computeOffsetY(ZoneConfig config, int fragmentIndex, boolean vFlip) {
            int offsetY = 0;
            if (config != null && config.pieceOffsets() != null &&
                    fragmentIndex < config.pieceOffsets().length) {
                offsetY = config.pieceOffsets()[fragmentIndex][1];
            }
            return vFlip ? -offsetY : offsetY;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }

            if (renderManager == null || config == null) {
                appendDebugFragment(commands);
                return;
            }

            if (config.usesLevelArt()) {
                // ARZ fragments use level art
                renderArzFragment();
                return;
            }

            PatternSpriteRenderer renderer = renderManager.getRenderer(config.artKey());
            if (renderer == null || !renderer.isReady()) {
                appendDebugFragment(commands);
                return;
            }

            // Fragment frame index depends on zone - frame 1 is the collapsed/fragment frame
            // Each fragment draws only its corresponding piece from that frame
            int frameIndex = 1;

            // Render at parent position (getX()/getY() minus offset) because
            // the piece renderer will add the piece offset
            int renderX = getX() - pieceOffsetX;
            int renderY = getY() - pieceOffsetY;

            renderer.drawFramePieceByIndex(frameIndex, fragmentIndex, renderX, renderY, hFlip, vFlip);
        }

        /**
         * Render an ARZ fragment using level patterns.
         * Each fragment renders its corresponding piece from ARZ_FRAME_COLLAPSED.
         * Since getX()/getY() already include the piece offset, we need to
         * subtract it before passing to the renderer (which will add it back).
         */
        private void renderArzFragment() {
            if (fragmentIndex < 0 || fragmentIndex >= ARZ_FRAME_COLLAPSED.pieces().size()) {
                return;
            }

            SpriteMappingPiece piece = ARZ_FRAME_COLLAPSED.pieces().get(fragmentIndex);
            GraphicsManager graphicsManager = GraphicsManager.getInstance();

            // Render at parent position (getX()/getY() minus offset) because
            // SpritePieceRenderer will add the piece offset
            int renderX = getX() - pieceOffsetX;
            int renderY = getY() - pieceOffsetY;

            SpritePieceRenderer.renderPieces(
                    List.of(piece),
                    renderX,
                    renderY,
                    0,  // Level patterns start at index 0
                    ARZ_PALETTE,
                    hFlip,  // Frame H-flip inherited from parent
                    vFlip,  // Frame V-flip inherited from parent
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

        private void appendDebugFragment(List<GLCommand> commands) {
            int renderX = getX();
            int renderY = getY();

            int size = 12;  // Approximate piece size for debug
            int left = renderX - size;
            int right = renderX + size;
            int top = renderY - size;
            int bottom = renderY + size;

            float r = 0.42f;
            float g = 0.21f;
            float b = 0.07f;

            // Draw a small square for the fragment
            commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                    r, g, b, left, top, 0, 0));
            commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                    r, g, b, right, top, 0, 0));
            commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                    r, g, b, right, top, 0, 0));
            commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                    r, g, b, right, bottom, 0, 0));
            commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                    r, g, b, right, bottom, 0, 0));
            commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                    r, g, b, left, bottom, 0, 0));
            commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                    r, g, b, left, bottom, 0, 0));
            commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                    r, g, b, left, top, 0, 0));
        }
    }
}
