package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.ObjectArtProvider;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kConstants;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.objects.HudRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationSet;
import uk.co.jamesj999.sonic.tools.NemesisReader;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Object art provider for Sonic 3 &amp; Knuckles.
 * <p>
 * Many S3K objects use level patterns rather than dedicated compressed art.
 * This provider builds sprite sheets from the loaded level's pattern data
 * after the level has been loaded.
 */
public class Sonic3kObjectArtProvider implements ObjectArtProvider {
    private static final Logger LOG = Logger.getLogger(Sonic3kObjectArtProvider.class.getName());

    private int currentZoneIndex = -2;

    private final Map<String, PatternSpriteRenderer> renderers = new HashMap<>();
    private final Map<String, ObjectSpriteSheet> sheets = new HashMap<>();
    private final List<String> rendererKeys = new ArrayList<>();
    private final List<ObjectSpriteSheet> sheetOrder = new ArrayList<>();
    private final List<PatternSpriteRenderer> rendererOrder = new ArrayList<>();

    // HUD pattern caches
    private Pattern[] hudDigitPatterns;
    private Pattern[] hudTextPatterns;
    private Pattern[] hudLivesPatterns;
    private Pattern[] hudLivesNumbers;

    @Override
    public void loadArtForZone(int zoneIndex) throws IOException {
        if (zoneIndex == currentZoneIndex) {
            return;
        }
        currentZoneIndex = zoneIndex;

        // Clear previous registrations
        renderers.clear();
        sheets.clear();
        rendererKeys.clear();
        sheetOrder.clear();
        rendererOrder.clear();

        // Load HUD art (same for all zones)
        loadHudArt();

        // Level-art sheets are registered later via registerLevelArtSheets()
        // since the level must be loaded first
        LOG.info("Sonic3kObjectArtProvider initialized for zone " + zoneIndex);
    }

    private void loadHudArt() throws IOException {
        Rom rom = GameServices.rom().getRom();
        if (rom == null) {
            LOG.warning("ROM not available for HUD art loading");
            return;
        }

        // HUD digits (0-9, colon, E) - uncompressed
        hudDigitPatterns = loadUncompressedPatterns(rom,
                Sonic3kConstants.ART_UNC_HUD_DIGITS_ADDR,
                Sonic3kConstants.ART_UNC_HUD_DIGITS_SIZE);
        LOG.info("Loaded " + (hudDigitPatterns != null ? hudDigitPatterns.length : 0) + " HUD digit patterns");

        // HUD text labels (SCORE/RINGS/TIME) - Nemesis compressed, tiles 14+ of ring/HUD blob
        hudTextPatterns = loadHudTextFromNemesis(rom);
        LOG.info("Loaded " + (hudTextPatterns != null ? hudTextPatterns.length : 0) + " HUD text patterns");

        // Lives icon - Nemesis compressed
        hudLivesPatterns = loadNemesisPatterns(rom, Sonic3kConstants.ART_NEM_SONIC_LIFE_ICON_ADDR);
        LOG.info("Loaded " + (hudLivesPatterns != null ? hudLivesPatterns.length : 0) + " HUD lives icon patterns");

        // Lives digits (0-9) - uncompressed
        hudLivesNumbers = loadUncompressedPatterns(rom,
                Sonic3kConstants.ART_UNC_LIVES_DIGITS_ADDR,
                Sonic3kConstants.ART_UNC_LIVES_DIGITS_SIZE);
        LOG.info("Loaded " + (hudLivesNumbers != null ? hudLivesNumbers.length : 0) + " HUD lives digit patterns");
    }

    private Pattern[] loadUncompressedPatterns(Rom rom, int addr, int size) throws IOException {
        byte[] data = rom.readBytes(addr, size);
        int count = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        Pattern[] patterns = new Pattern[count];
        for (int i = 0; i < count; i++) {
            patterns[i] = new Pattern();
            byte[] tile = Arrays.copyOfRange(data, i * Pattern.PATTERN_SIZE_IN_ROM,
                    (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
            patterns[i].fromSegaFormat(tile);
        }
        return patterns;
    }

    private Pattern[] loadNemesisPatterns(Rom rom, int addr) throws IOException {
        FileChannel channel = rom.getFileChannel();
        channel.position(addr);
        byte[] data = NemesisReader.decompress(channel);
        int count = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        Pattern[] patterns = new Pattern[count];
        for (int i = 0; i < count; i++) {
            patterns[i] = new Pattern();
            byte[] tile = Arrays.copyOfRange(data, i * Pattern.PATTERN_SIZE_IN_ROM,
                    (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
            patterns[i].fromSegaFormat(tile);
        }
        return patterns;
    }

    /**
     * Loads HUD text patterns from the shared ring/HUD Nemesis blob.
     * The first 14 tiles are ring art; tiles 14+ are HUD text (S, C, O, R, R, I, N, G, T, I, M, E).
     */
    private Pattern[] loadHudTextFromNemesis(Rom rom) throws IOException {
        FileChannel channel = rom.getFileChannel();
        channel.position(Sonic3kConstants.ART_NEM_RING_HUD_TEXT_ADDR);
        byte[] data = NemesisReader.decompress(channel);

        int totalTiles = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        int ringTiles = 14; // First 14 tiles are ring sprite data
        int textTiles = totalTiles - ringTiles;
        if (textTiles <= 0) {
            LOG.warning("No HUD text tiles found in ring/HUD blob (total=" + totalTiles + ")");
            return null;
        }

        Pattern[] patterns = new Pattern[textTiles];
        for (int i = 0; i < textTiles; i++) {
            int offset = (ringTiles + i) * Pattern.PATTERN_SIZE_IN_ROM;
            patterns[i] = new Pattern();
            byte[] tile = Arrays.copyOfRange(data, offset, offset + Pattern.PATTERN_SIZE_IN_ROM);
            patterns[i].fromSegaFormat(tile);
        }
        return patterns;
    }

    /**
     * Registers object sprite sheets that use level patterns.
     * Must be called AFTER the level is loaded.
     *
     * @param level the loaded level
     * @param zoneIndex the zone index
     */
    public void registerLevelArtSheets(Level level, int zoneIndex) {
        if (level == null) {
            return;
        }

        Sonic3kObjectArt art = new Sonic3kObjectArt(level);

        // AIZ objects (zone index 0 = AIZ in S3K)
        if (zoneIndex == 0x00) {
            registerSheet(Sonic3kObjectArtKeys.AIZ1_TREE, art.buildAiz1TreeSheet());
            registerSheet(Sonic3kObjectArtKeys.AIZ1_ZIPLINE_PEG, art.buildAiz1ZiplinePegSheet());
        }

        LOG.info("Sonic3kObjectArtProvider registered " + rendererKeys.size()
                + " level-art sheets for zone " + zoneIndex);
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
    public HudRenderManager.HudFlashMode getHudFlashMode() {
        return HudRenderManager.HudFlashMode.TEXT_HIDE;
    }

    @Override
    public List<String> getRendererKeys() {
        return new ArrayList<>(rendererKeys);
    }

    @Override
    public int ensurePatternsCached(GraphicsManager graphicsManager, int basePatternIndex) {
        int next = basePatternIndex;
        for (int i = 0; i < rendererOrder.size(); i++) {
            ObjectSpriteSheet sheet = sheetOrder.get(i);
            PatternSpriteRenderer renderer = rendererOrder.get(i);
            renderer.ensurePatternsCached(graphicsManager, next);
            next += sheet.getPatterns().length;
        }
        return next;
    }

    @Override
    public boolean isReady() {
        // Ready if at least one renderer has been cached
        for (PatternSpriteRenderer renderer : renderers.values()) {
            if (renderer.isReady()) {
                return true;
            }
        }
        return false;
    }
}
