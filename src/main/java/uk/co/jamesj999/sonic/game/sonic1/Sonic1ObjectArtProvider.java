package uk.co.jamesj999.sonic.game.sonic1;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.ObjectArtProvider;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationEndAction;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationScript;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationSet;
import uk.co.jamesj999.sonic.tools.NemesisReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ObjectArtProvider for Sonic 1.
 * Loads HUD art and game object art (lamppost, etc.) from the Sonic 1 ROM.
 */
public class Sonic1ObjectArtProvider implements ObjectArtProvider {
    private static final Logger LOGGER = Logger.getLogger(Sonic1ObjectArtProvider.class.getName());

    private Pattern[] hudDigitPatterns;
    private Pattern[] hudTextPatterns;
    private Pattern[] hudLivesPatterns;
    private Pattern[] hudLivesNumbers;
    private boolean loaded = false;

    private final Map<String, PatternSpriteRenderer> renderers = new HashMap<>();
    private final Map<String, ObjectSpriteSheet> sheets = new HashMap<>();
    private final Map<String, SpriteAnimationSet> animations = new HashMap<>();
    private final List<String> rendererKeys = new ArrayList<>();
    private final List<ObjectSpriteSheet> sheetOrder = new ArrayList<>();
    private final List<PatternSpriteRenderer> rendererOrder = new ArrayList<>();

    @Override
    public void loadArtForZone(int zoneIndex) throws IOException {
        if (loaded) {
            return;
        }

        Rom rom = GameServices.rom().getRom();
        if (rom == null) {
            throw new IllegalStateException("ROM not loaded");
        }

        hudDigitPatterns = loadUncompressedPatterns(rom,
                Sonic1Constants.ART_UNC_HUD_NUMBERS_ADDR,
                Sonic1Constants.ART_UNC_HUD_NUMBERS_SIZE, "HUDNumbers");

        hudTextPatterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_HUD_ADDR, "HUDText");

        hudLivesPatterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_LIFE_ICON_ADDR, "LifeIcon");

        hudLivesNumbers = loadUncompressedPatterns(rom,
                Sonic1Constants.ART_UNC_LIVES_NUMBERS_ADDR,
                Sonic1Constants.ART_UNC_LIVES_NUMBERS_SIZE, "LivesNumbers");

        // Load lamppost art
        loadLamppostArt(rom);

        // Load signpost art
        loadSignpostArt(rom);

        // Load bridge art (GHZ)
        loadBridgeArt(rom);

        // Load purple rock art (GHZ)
        loadPurpleRockArt(rom);

        // Load monitor art
        loadMonitorArt(rom);

        // Load explosion art (used by monitors and badniks)
        loadExplosionArt(rom);

        // Load shield art
        loadShieldArt(rom);

        // Load results screen art (reuses title card + HUD text)
        loadResultsScreenArt(rom);

        loaded = true;
        LOGGER.info("Sonic1ObjectArtProvider loaded: digits=" +
                (hudDigitPatterns != null ? hudDigitPatterns.length : 0) +
                " text=" + (hudTextPatterns != null ? hudTextPatterns.length : 0) +
                " lives=" + (hudLivesPatterns != null ? hudLivesPatterns.length : 0) +
                " livesNums=" + (hudLivesNumbers != null ? hudLivesNumbers.length : 0) +
                " renderers=" + rendererKeys.size());
    }

    /**
     * Loads lamppost art (Nem_Lamp) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Lamppost.asm (Map_Lamp_internal).
     */
    private void loadLamppostArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_LAMPPOST_ADDR, "Lamppost");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load lamppost art");
            return;
        }

        List<SpriteMappingFrame> mappings = createLamppostMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.CHECKPOINT, sheet);
    }

    /**
     * Creates lamppost sprite mappings from S1 disassembly Map_Lamp_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * Frame 0 (.blue):        Pole with blue ball on top (6 pieces)
     * Frame 1 (.poleonly):    Pole without ball (4 pieces)
     * Frame 2 (.redballonly): Red ball only - used for twirl sparkle (2 pieces)
     * Frame 3 (.red):         Pole with red ball on top (6 pieces)
     */
    private List<SpriteMappingFrame> createLamppostMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .blue - pole + blue ball
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x1C, 1, 2, 0, false, false, 0, false),
                new SpriteMappingPiece( 0, -0x1C, 1, 2, 0, true,  false, 0, false),
                new SpriteMappingPiece(-8, -0x0C, 1, 4, 2, false, false, 1, false),
                new SpriteMappingPiece( 0, -0x0C, 1, 4, 2, true,  false, 1, false),
                new SpriteMappingPiece(-8, -0x2C, 1, 2, 6, false, false, 0, false),
                new SpriteMappingPiece( 0, -0x2C, 1, 2, 6, true,  false, 0, false)
        )));

        // Frame 1: .poleonly - pole without ball
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x1C, 1, 2, 0, false, false, 0, false),
                new SpriteMappingPiece( 0, -0x1C, 1, 2, 0, true,  false, 0, false),
                new SpriteMappingPiece(-8, -0x0C, 1, 4, 2, false, false, 1, false),
                new SpriteMappingPiece( 0, -0x0C, 1, 4, 2, true,  false, 1, false)
        )));

        // Frame 2: .redballonly - red ball only (twirl sparkle)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 1, 2, 8, false, false, 0, false),
                new SpriteMappingPiece( 0, -8, 1, 2, 8, true,  false, 0, false)
        )));

        // Frame 3: .red - pole + red ball
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x1C, 1, 2, 0, false, false, 0, false),
                new SpriteMappingPiece( 0, -0x1C, 1, 2, 0, true,  false, 0, false),
                new SpriteMappingPiece(-8, -0x0C, 1, 4, 2, false, false, 1, false),
                new SpriteMappingPiece( 0, -0x0C, 1, 4, 2, true,  false, 1, false),
                new SpriteMappingPiece(-8, -0x2C, 1, 2, 8, false, false, 0, false),
                new SpriteMappingPiece( 0, -0x2C, 1, 2, 8, true,  false, 0, false)
        )));

        return frames;
    }

    /**
     * Loads signpost art (Nem_Sign) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Signpost.asm (Map_Sign_internal).
     */
    private void loadSignpostArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_SIGNPOST_ADDR, "Signpost");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load signpost art");
            return;
        }

        List<SpriteMappingFrame> mappings = createSignpostMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.SIGNPOST, sheet);
    }

    /**
     * Creates signpost sprite mappings from S1 disassembly Map_Sign_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * Frame 0 (.eggman):  Eggman face sign (3 pieces, left+mirrored right+post)
     * Frame 1 (.spin1):   Spinning frame 1 - wide (2 pieces, sign+post)
     * Frame 2 (.spin2):   Spinning frame 2 - thin/edge-on (2 pieces, sign+post)
     * Frame 3 (.spin3):   Spinning frame 3 - wide mirrored (2 pieces, sign+post)
     * Frame 4 (.sonic):   Sonic face sign (3 pieces, left+right+post)
     */
    private List<SpriteMappingFrame> createSignpostMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .eggman - Eggman sign face (left half + mirrored right half + post)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x10, 3, 4, 0x00, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x10, 3, 4, 0x00, true,  false, 0, false),
                new SpriteMappingPiece(   -4,  0x10, 1, 2, 0x38, false, false, 0, false)
        )));

        // Frame 1: .spin1 - Spinning wide (4x4 sign + post)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 4, 4, 0x0C, false, false, 0, false),
                new SpriteMappingPiece(   -4,  0x10, 1, 2, 0x38, false, false, 0, false)
        )));

        // Frame 2: .spin2 - Spinning thin/edge-on (1x4 sign + post with xflip)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-4, -0x10, 1, 4, 0x1C, false, false, 0, false),
                new SpriteMappingPiece(-4,  0x10, 1, 2, 0x38, true,  false, 0, false)
        )));

        // Frame 3: .spin3 - Spinning wide mirrored (4x4 sign xflipped + post with xflip)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 4, 4, 0x0C, true,  false, 0, false),
                new SpriteMappingPiece(   -4,  0x10, 1, 2, 0x38, true,  false, 0, false)
        )));

        // Frame 4: .sonic - Sonic sign face (left half + right half + post)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x10, 3, 4, 0x20, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x10, 3, 4, 0x2C, false, false, 0, false),
                new SpriteMappingPiece(   -4,  0x10, 1, 2, 0x38, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Loads purple rock art (Nem_PplRock) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Purple Rock.asm (Map_PRock_internal).
     */
    private void loadPurpleRockArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_PURPLE_ROCK_ADDR, "PurpleRock");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load purple rock art");
            return;
        }

        List<SpriteMappingFrame> mappings = createPurpleRockMappings();
        // Palette line 3 from disassembly: make_art_tile(ArtTile_GHZ_Purple_Rock,3,0)
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 3, 1);
        registerSheet(ObjectArtKeys.ROCK, sheet);
    }

    /**
     * Creates purple rock sprite mappings from S1 disassembly Map_PRock_internal.
     * <p>
     * Single frame with 2 pieces (left half + right half):
     * <pre>
     * spritePiece -$18, -$10, 3, 4, 0, 0, 0, 0, 0
     * spritePiece    0, -$10, 3, 4, $C, 0, 0, 0, 0
     * </pre>
     */
    private List<SpriteMappingFrame> createPurpleRockMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Full rock (2 pieces, left half + right half)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x10, 3, 4, 0, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x10, 3, 4, 0x0C, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Loads bridge art (Nem_Bridge) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Bridge.asm (Map_Bri).
     * <p>
     * Frame 0: Single log segment (2x2 tiles, 16x16 px)
     * Frame 1: Bridge stump with rope (2 pieces, used by Scenery 0x1C subtype 3)
     * Frame 2: Rope only (2x1 tiles, 16x8 px)
     */
    private void loadBridgeArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_BRIDGE_ADDR, "Bridge");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load bridge art");
            return;
        }

        List<SpriteMappingFrame> mappings = createBridgeMappings();
        // Palette line 2 from disassembly: make_art_tile(ArtTile_GHZ_Bridge,2,0)
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 2, 1);
        registerSheet(ObjectArtKeys.BRIDGE, sheet);
    }

    /**
     * Creates bridge sprite mappings from S1 disassembly Map_Bri.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     */
    private List<SpriteMappingFrame> createBridgeMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: M_Bri_Log - single log (1 piece, 2x2 tiles at -8,-8)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0, false, false, 0, false)
        )));

        // Frame 1: M_Bri_Stump - bridge stump (2 pieces)
        //   spritePiece -$10, -8, 2, 1, 4, 0, 0, 0, 0
        //   spritePiece -$10, 0, 4, 1, 6, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -8, 2, 1, 4, false, false, 0, false),
                new SpriteMappingPiece(-0x10,  0, 4, 1, 6, false, false, 0, false)
        )));

        // Frame 2: M_Bri_Rope - rope only (1 piece, 2x1 tiles at -8,-4)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -4, 2, 1, 8, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Loads monitor art (Nem_Monitors) and creates S1-format sprite mappings and animations.
     * Mappings from docs/s1disasm/_maps/Monitor.asm (Map_Monitor_internal).
     * Animations from docs/s1disasm/_anim/Monitor.asm (Ani_Monitor).
     */
    private void loadMonitorArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_MONITOR_ADDR, "Monitor");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load monitor art");
            return;
        }

        List<SpriteMappingFrame> mappings = createMonitorMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.MONITOR, sheet);

        SpriteAnimationSet animSet = createMonitorAnimations();
        animations.put(ObjectArtKeys.ANIM_MONITOR, animSet);
    }

    /**
     * Creates monitor sprite mappings from S1 disassembly Map_Monitor_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * 12 frames: 0-2 (static variants), 3 (eggman), 4 (sonic/life), 5 (shoes),
     * 6 (shield), 7 (invincible), 8 (rings), 9 (S), 10 (goggles), 11 (broken shell)
     */
    private List<SpriteMappingFrame> createMonitorMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .static0 - plain monitor box (1 piece)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 1: .static1 - monitor with static icon variant 1 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x10, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 2: .static2 - monitor with static icon variant 2 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x14, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 3: .eggman - Eggman monitor (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x18, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 4: .sonic - Sonic/1-up monitor (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x1C, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 5: .shoes - Speed shoes monitor (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x24, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 6: .shield - Shield monitor (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x28, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 7: .invincible - Invincibility monitor (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x2C, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 8: .rings - 10 rings monitor (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x30, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 9: .s - 'S' monitor (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x34, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 10: .goggles - Goggles monitor (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x20, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 11: .broken - Broken monitor shell (1 piece)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -1, 4, 2, 0x38, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Creates monitor animation scripts from S1 disassembly Ani_Monitor.
     * <p>
     * 10 animations (0-9): static, eggman, sonic, shoes, shield,
     * invincible, rings, s, goggles, breaking.
     * <p>
     * Non-static animations cycle: box→icon→icon→box-variant→icon→icon→box-variant→icon→icon
     */
    private SpriteAnimationSet createMonitorAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // Anim 0: .static - dc.b 1, 0, 1, 2, afEnd
        set.addScript(0, new SpriteAnimationScript(1,
                List.of(0, 1, 2), SpriteAnimationEndAction.LOOP, 0));

        // Anim 1: .eggman - dc.b 1, 0, 3, 3, 1, 3, 3, 2, 3, 3, afEnd
        set.addScript(1, new SpriteAnimationScript(1,
                List.of(0, 3, 3, 1, 3, 3, 2, 3, 3), SpriteAnimationEndAction.LOOP, 0));

        // Anim 2: .sonic - dc.b 1, 0, 4, 4, 1, 4, 4, 2, 4, 4, afEnd
        set.addScript(2, new SpriteAnimationScript(1,
                List.of(0, 4, 4, 1, 4, 4, 2, 4, 4), SpriteAnimationEndAction.LOOP, 0));

        // Anim 3: .shoes - dc.b 1, 0, 5, 5, 1, 5, 5, 2, 5, 5, afEnd
        set.addScript(3, new SpriteAnimationScript(1,
                List.of(0, 5, 5, 1, 5, 5, 2, 5, 5), SpriteAnimationEndAction.LOOP, 0));

        // Anim 4: .shield - dc.b 1, 0, 6, 6, 1, 6, 6, 2, 6, 6, afEnd
        set.addScript(4, new SpriteAnimationScript(1,
                List.of(0, 6, 6, 1, 6, 6, 2, 6, 6), SpriteAnimationEndAction.LOOP, 0));

        // Anim 5: .invincible - dc.b 1, 0, 7, 7, 1, 7, 7, 2, 7, 7, afEnd
        set.addScript(5, new SpriteAnimationScript(1,
                List.of(0, 7, 7, 1, 7, 7, 2, 7, 7), SpriteAnimationEndAction.LOOP, 0));

        // Anim 6: .rings - dc.b 1, 0, 8, 8, 1, 8, 8, 2, 8, 8, afEnd
        set.addScript(6, new SpriteAnimationScript(1,
                List.of(0, 8, 8, 1, 8, 8, 2, 8, 8), SpriteAnimationEndAction.LOOP, 0));

        // Anim 7: .s - dc.b 1, 0, 9, 9, 1, 9, 9, 2, 9, 9, afEnd
        set.addScript(7, new SpriteAnimationScript(1,
                List.of(0, 9, 9, 1, 9, 9, 2, 9, 9), SpriteAnimationEndAction.LOOP, 0));

        // Anim 8: .goggles - dc.b 1, 0, $A, $A, 1, $A, $A, 2, $A, $A, afEnd
        set.addScript(8, new SpriteAnimationScript(1,
                List.of(0, 10, 10, 1, 10, 10, 2, 10, 10), SpriteAnimationEndAction.LOOP, 0));

        // Anim 9: .breaking - dc.b 2, 0, 1, 2, $B, afBack, 1
        // Plays frames 0→1→2→11, then holds on frame 11 (broken shell)
        set.addScript(9, new SpriteAnimationScript(2,
                List.of(0, 1, 2, 11), SpriteAnimationEndAction.LOOP_BACK, 1));

        return set;
    }

    /**
     * Loads explosion art (Nem_Explode) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Explosions.asm (Map_ExplodeItem).
     * Used by monitor break, badnik destruction, etc.
     */
    private void loadExplosionArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_EXPLOSION_ADDR, "Explosion");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load explosion art");
            return;
        }

        List<SpriteMappingFrame> mappings = createExplosionMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.EXPLOSION, sheet);
    }

    /**
     * Creates explosion sprite mappings from S1 disassembly Map_ExplodeItem.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * 5 frames: small burst → medium → large → scatter (4 pieces) → scatter (4 pieces)
     */
    private List<SpriteMappingFrame> createExplosionMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: small burst (1 piece, 3x2 tiles)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -8, 3, 2, 0, false, false, 0, false)
        )));

        // Frame 1: medium burst (1 piece, 4x4 tiles)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 4, 4, 6, false, false, 0, false)
        )));

        // Frame 2: large burst (1 piece, 4x4 tiles)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 4, 4, 0x16, false, false, 0, false)
        )));

        // Frame 3: scatter (4 pieces, 3x3 + 2x2 + 2x2 + 3x3 with flips)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, -0x14, 3, 3, 0x26, false, false, 0, false),
                new SpriteMappingPiece(    4, -0x14, 2, 2, 0x2F, false, false, 0, false),
                new SpriteMappingPiece(-0x14,     4, 2, 2, 0x2F, true,  true,  0, false),
                new SpriteMappingPiece(   -4,    -4, 3, 3, 0x26, true,  true,  0, false)
        )));

        // Frame 4: final scatter (4 pieces, 3x3 + 2x2 + 2x2 + 3x3 with flips)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, -0x14, 3, 3, 0x33, false, false, 0, false),
                new SpriteMappingPiece(    4, -0x14, 2, 2, 0x3C, false, false, 0, false),
                new SpriteMappingPiece(-0x14,     4, 2, 2, 0x3C, true,  true,  0, false),
                new SpriteMappingPiece(   -4,    -4, 3, 3, 0x33, true,  true,  0, false)
        )));

        return frames;
    }

    /**
     * Loads shield art (Nem_Shield) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Shield and Invincibility.asm (Map_Shield_internal).
     * <p>
     * S1 shield has 4 frames:
     * <ul>
     *   <li>Frame 0 (.shield1): bottom half only (2 pieces, 3x3 tiles each)</li>
     *   <li>Frame 1 (.shield2): full quad - top + bottom (4 pieces)</li>
     *   <li>Frame 2 (.shield3): full quad, alternate tile ($12) (4 pieces)</li>
     *   <li>Frame 3 (.shield4): full quad, mirrored (4 pieces)</li>
     * </ul>
     */
    private void loadShieldArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_SHIELD_ADDR, "Shield");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load shield art");
            return;
        }

        List<SpriteMappingFrame> mappings = createShieldMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.SHIELD, sheet);
    }

    /**
     * Creates shield sprite mappings from S1 disassembly Map_Shield_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     */
    private List<SpriteMappingFrame> createShieldMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .shield1 - bottom half only (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, 0, 3, 3, 0, false, true, 0, false),
                new SpriteMappingPiece(0, 0, 3, 3, 9, false, true, 0, false)
        )));

        // Frame 1: .shield2 - full quad (4 pieces: top + bottom)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x18, 3, 3, 0, false, false, 0, false),
                new SpriteMappingPiece(0, -0x18, 3, 3, 9, false, false, 0, false),
                new SpriteMappingPiece(-0x18, 0, 3, 3, 0, false, true, 0, false),
                new SpriteMappingPiece(0, 0, 3, 3, 9, false, true, 0, false)
        )));

        // Frame 2: .shield3 - full quad, alternate tile $12 (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x17, -0x18, 3, 3, 0x12, true, false, 0, false),
                new SpriteMappingPiece(0, -0x18, 3, 3, 0x12, false, false, 0, false),
                new SpriteMappingPiece(-0x17, 0, 3, 3, 0x12, true, true, 0, false),
                new SpriteMappingPiece(0, 0, 3, 3, 0x12, false, true, 0, false)
        )));

        // Frame 3: .shield4 - full quad, mirrored (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x18, 3, 3, 9, true, false, 0, false),
                new SpriteMappingPiece(0, -0x18, 3, 3, 0, true, false, 0, false),
                new SpriteMappingPiece(-0x18, 0, 3, 3, 9, true, true, 0, false),
                new SpriteMappingPiece(0, 0, 3, 3, 0, true, true, 0, false)
        )));

        return frames;
    }

    /**
     * Loads results screen art by compositing title card patterns + HUD text patterns
     * into a single VRAM-aligned pattern array, matching the original S1 layout.
     * <p>
     * VRAM layout:
     * <ul>
     *   <li>$570-$57F (indices 0-15): Writable bonus digit slots (time + ring bonus)</li>
     *   <li>$580+ (indices 16+): Title card art (Nem_TitleCard)</li>
     *   <li>$6CA+ (indices 0x15A+): HUD text art (Nem_Hud: SCORE/TIME/RINGS labels)</li>
     * </ul>
     * <p>
     * Mapping tile IDs are relative to ArtTile_Title_Card ($580).
     * Array index = tile_id + RESULTS_TILE_ADJUST (0x10).
     */
    private void loadResultsScreenArt(Rom rom) {
        Pattern[] titleCardPatterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_TITLE_CARD_ADDR, "TitleCard");
        if (titleCardPatterns.length == 0) {
            LOGGER.warning("Failed to load title card art for results screen");
            return;
        }
        if (hudTextPatterns == null || hudTextPatterns.length == 0) {
            LOGGER.warning("HUD text patterns not available for results screen");
            return;
        }

        // Calculate the HUD text start index in our composite array
        // HUD text is at VRAM $6CA; title card at $580; base at $570
        int hudTextStartIndex = Sonic1Constants.VRAM_RESULTS_HUD_TEXT - Sonic1Constants.VRAM_RESULTS_BASE;

        // Total array size: enough to cover HUD text at the end
        int totalSize = hudTextStartIndex + hudTextPatterns.length;

        Pattern[] compositePatterns = new Pattern[totalSize];

        // Fill with blank patterns
        for (int i = 0; i < totalSize; i++) {
            compositePatterns[i] = new Pattern();
        }

        // Copy title card patterns at index RESULTS_TILE_ADJUST (0x10)
        int titleCardStart = Sonic1Constants.RESULTS_TILE_ADJUST;
        for (int i = 0; i < titleCardPatterns.length && (titleCardStart + i) < totalSize; i++) {
            compositePatterns[titleCardStart + i] = titleCardPatterns[i];
        }

        // Copy HUD text patterns at hudTextStartIndex
        for (int i = 0; i < hudTextPatterns.length && (hudTextStartIndex + i) < totalSize; i++) {
            compositePatterns[hudTextStartIndex + i] = hudTextPatterns[i];
        }

        List<SpriteMappingFrame> mappings = createResultsScreenMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(compositePatterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.RESULTS, sheet);

        LOGGER.info("Results screen art loaded: " + totalSize + " composite patterns, "
                + mappings.size() + " frames");
    }

    /**
     * Creates sprite mappings for the Sonic 1 results screen from Map_Got in the disassembly.
     * <p>
     * All tile IDs from the disassembly are relative to ArtTile_Title_Card ($580).
     * We add RESULTS_TILE_ADJUST (0x10) to convert to composite array indices.
     * <p>
     * Frames:
     * <ol start="0">
     *   <li>"SONIC HAS" (8 pieces)</li>
     *   <li>"PASSED" (6 pieces)</li>
     *   <li>"SCORE" + score area (6 pieces)</li>
     *   <li>"TIME BONUS" + digit area (7 pieces)</li>
     *   <li>"RING BONUS" + digit area (7 pieces)</li>
     *   <li>Oval decoration (13 pieces)</li>
     *   <li>"ACT 1" (2 pieces)</li>
     *   <li>"ACT 2" (2 pieces)</li>
     *   <li>"ACT 3" (2 pieces)</li>
     * </ol>
     */
    private List<SpriteMappingFrame> createResultsScreenMappings() {
        final int T = Sonic1Constants.RESULTS_TILE_ADJUST; // 0x10
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: M_Got_SonicHas - "SONIC HAS" (8 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x48, -8, 2, 2, 0x3E + T, false, false, 0, false),
                new SpriteMappingPiece(-0x38, -8, 2, 2, 0x32 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x28, -8, 2, 2, 0x2E + T, false, false, 0, false),
                new SpriteMappingPiece(-0x18, -8, 1, 2, 0x20 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -8, 2, 2, 0x08 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x10, -8, 2, 2, 0x1C + T, false, false, 0, false),
                new SpriteMappingPiece( 0x20, -8, 2, 2, 0x00 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x30, -8, 2, 2, 0x3E + T, false, false, 0, false)
        )));

        // Frame 1: M_Got_Passed - "PASSED" (6 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x30, -8, 2, 2, 0x36 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x20, -8, 2, 2, 0x00 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -8, 2, 2, 0x3E + T, false, false, 0, false),
                new SpriteMappingPiece( 0x00, -8, 2, 2, 0x3E + T, false, false, 0, false),
                new SpriteMappingPiece( 0x10, -8, 2, 2, 0x10 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x20, -8, 2, 2, 0x0C + T, false, false, 0, false)
        )));

        // Frame 2: M_Got_Score - "SCORE" text + score digits (4 pieces)
        // Separator dots split into frame 9 to ensure correct z-ordering:
        // batched rendering can lose within-frame piece priority, so the dots
        // must be in a separate drawFrameIndex call issued before this frame.
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x50, -8, 4, 2, 0x14A + T, false, false, 0, false),
                new SpriteMappingPiece(-0x30, -8, 1, 2, 0x162 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x18, -8, 3, 2, 0x164 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x30, -8, 4, 2, 0x16A + T, false, false, 0, false)
        )));

        // Frame 3: M_Got_TBonus - "TIME BONUS" + digit area (7 pieces)
        // Tile -$10 = time bonus digits at array index 0 (= -0x10 + T = 0)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x50, -8, 4, 2, 0x15A + T, false, false, 0, false),
                new SpriteMappingPiece(-0x27, -8, 4, 2, 0x66 + T,  false, false, 0, false),
                new SpriteMappingPiece(   -7, -8, 1, 2, 0x14A + T, false, false, 0, false),
                new SpriteMappingPiece( -0xA, -9, 2, 1, 0x6E + T,  false, false, 0, false),
                new SpriteMappingPiece( -0xA, -1, 2, 1, 0x6E + T,  true,  true,  0, false),
                new SpriteMappingPiece( 0x28, -8, 4, 2, 0,         false, false, 0, false),
                new SpriteMappingPiece( 0x48, -8, 1, 2, 0x170 + T, false, false, 0, false)
        )));

        // Frame 4: M_Got_RBonus - "RING BONUS" + digit area (7 pieces)
        // Tile -$8 = ring bonus digits at array index 8 (= -0x08 + T = 8)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x50, -8, 4, 2, 0x152 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x27, -8, 4, 2, 0x66 + T,  false, false, 0, false),
                new SpriteMappingPiece(   -7, -8, 1, 2, 0x14A + T, false, false, 0, false),
                new SpriteMappingPiece( -0xA, -9, 2, 1, 0x6E + T,  false, false, 0, false),
                new SpriteMappingPiece( -0xA, -1, 2, 1, 0x6E + T,  true,  true,  0, false),
                new SpriteMappingPiece( 0x28, -8, 4, 2, 8,         false, false, 0, false),
                new SpriteMappingPiece( 0x48, -8, 1, 2, 0x170 + T, false, false, 0, false)
        )));

        // Frame 5: M_Card_Oval - Oval decoration (13 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x1C, 4, 1, 0x70 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x14, -0x1C, 1, 3, 0x74 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x14, -0x14, 2, 1, 0x77 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x1C, -0x0C, 2, 2, 0x79 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x14,  0x14, 4, 1, 0x70 + T, true,  true,  0, false),
                new SpriteMappingPiece(-0x1C,  0x04, 1, 3, 0x74 + T, true,  true,  0, false),
                new SpriteMappingPiece( 0x04,  0x0C, 2, 1, 0x77 + T, true,  true,  0, false),
                new SpriteMappingPiece( 0x0C, -0x04, 2, 2, 0x79 + T, true,  true,  0, false),
                new SpriteMappingPiece(-0x04, -0x14, 3, 1, 0x7D + T, false, false, 0, false),
                new SpriteMappingPiece(-0x0C, -0x0C, 4, 1, 0x7C + T, false, false, 0, false),
                new SpriteMappingPiece(-0x0C, -0x04, 3, 1, 0x7C + T, false, false, 0, false),
                new SpriteMappingPiece(-0x14,  0x04, 4, 1, 0x7C + T, false, false, 0, false),
                new SpriteMappingPiece(-0x14,  0x0C, 3, 1, 0x7C + T, false, false, 0, false)
        )));

        // Frame 6: M_Card_Act1 - "ACT 1" (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, 0x04, 4, 1, 0x53 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x0C, -0x0C, 1, 3, 0x57 + T, false, false, 0, false)
        )));

        // Frame 7: M_Card_Act2 - "ACT 2" (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, 0x04, 4, 1, 0x53 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x08, -0x0C, 2, 3, 0x5A + T, false, false, 0, false)
        )));

        // Frame 8: M_Card_Act3 - "ACT 3" (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, 0x04, 4, 1, 0x53 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x08, -0x0C, 2, 3, 0x60 + T, false, false, 0, false)
        )));

        // Frame 9: SCORE separator dots (split from frame 2)
        // Drawn as a separate drawFrameIndex call before the SCORE text frame
        // so that batched rendering correctly places dots behind text.
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x33, -9, 2, 1, 0x6E + T, false, false, 0, false),
                new SpriteMappingPiece(-0x33, -1, 2, 1, 0x6E + T, true,  true,  0, false)
        )));

        return frames;
    }

    private void registerSheet(String key, ObjectSpriteSheet sheet) {
        if (sheet == null) {
            return;
        }
        sheets.put(key, sheet);
        PatternSpriteRenderer renderer = new PatternSpriteRenderer(sheet);
        renderers.put(key, renderer);
        rendererKeys.add(key);
        sheetOrder.add(sheet);
        rendererOrder.add(renderer);
    }

    private Pattern[] loadUncompressedPatterns(Rom rom, int address, int size, String name) {
        try {
            byte[] data = rom.readBytes(address, size);
            if (data.length % Pattern.PATTERN_SIZE_IN_ROM != 0) {
                LOGGER.warning("Inconsistent uncompressed art size for " + name);
                return new Pattern[0];
            }
            int count = data.length / Pattern.PATTERN_SIZE_IN_ROM;
            Pattern[] patterns = new Pattern[count];
            for (int i = 0; i < count; i++) {
                patterns[i] = new Pattern();
                byte[] sub = Arrays.copyOfRange(data,
                        i * Pattern.PATTERN_SIZE_IN_ROM,
                        (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
                patterns[i].fromSegaFormat(sub);
            }
            return patterns;
        } catch (Exception e) {
            LOGGER.warning("Failed to load " + name + " patterns: " + e.getMessage());
            return new Pattern[0];
        }
    }

    private Pattern[] loadNemesisPatterns(Rom rom, int address, String name) {
        try {
            byte[] compressed = rom.readBytes(address, 8192);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                 ReadableByteChannel channel = Channels.newChannel(bais)) {
                byte[] decompressed = NemesisReader.decompress(channel);
                int count = decompressed.length / Pattern.PATTERN_SIZE_IN_ROM;
                Pattern[] patterns = new Pattern[count];
                for (int i = 0; i < count; i++) {
                    patterns[i] = new Pattern();
                    byte[] sub = Arrays.copyOfRange(decompressed,
                            i * Pattern.PATTERN_SIZE_IN_ROM,
                            (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
                    patterns[i].fromSegaFormat(sub);
                }
                return patterns;
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load " + name + " patterns: " + e.getMessage());
            return new Pattern[0];
        }
    }

    @Override
    public Pattern[] getHudDigitPatterns() {
        return hudDigitPatterns;
    }

    @Override
    public Pattern[] getHudTextPatterns() {
        return hudTextPatterns;
    }

    @Override
    public Pattern[] getHudLivesPatterns() {
        return hudLivesPatterns;
    }

    @Override
    public Pattern[] getHudLivesNumbers() {
        return hudLivesNumbers;
    }

    @Override
    public PatternSpriteRenderer getRenderer(String key) {
        return renderers.get(key);
    }

    @Override
    public ObjectSpriteSheet getSheet(String key) {
        return sheets.get(key);
    }

    @Override
    public SpriteAnimationSet getAnimations(String key) {
        return animations.get(key);
    }

    @Override
    public int getZoneData(String key, int zoneIndex) {
        return -1;
    }

    @Override
    public List<String> getRendererKeys() {
        return new ArrayList<>(rendererKeys);
    }

    @Override
    public int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex) {
        int next = baseIndex;
        for (int i = 0; i < rendererOrder.size(); i++) {
            ObjectSpriteSheet sheet = sheetOrder.get(i);
            PatternSpriteRenderer renderer = rendererOrder.get(i);
            int count = sheet.getPatterns().length;
            renderer.ensurePatternsCached(graphicsManager, next);
            next += count;
        }
        return next;
    }

    @Override
    public boolean isReady() {
        return loaded && hudDigitPatterns != null && hudDigitPatterns.length > 0;
    }

    @Override
    public int getHudTextPaletteLine() {
        return 0; // Sonic 1: yellow HUD text is in palette line 0
    }

    @Override
    public int getHudFlashPaletteLine() {
        return 0; // Sonic 1: life icon and flash both use palette line 0 (Sonic's palette)
    }
}
