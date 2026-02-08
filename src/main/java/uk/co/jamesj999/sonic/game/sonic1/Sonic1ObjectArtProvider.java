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
        return null;
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
