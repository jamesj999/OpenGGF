package uk.co.jamesj999.sonic.game.sonic2;

import uk.co.jamesj999.sonic.game.GameServices;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.ObjectArtProvider;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.objects.ObjectArtData;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Sonic 2-specific implementation of ObjectArtProvider.
 * Wraps Sonic2ObjectArt and provides key-based lookups for renderers, sheets, and animations.
 * <p>
 * This provider lazily initializes the art loader when first needed, obtaining the ROM
 * from RomManager.
 */
public class Sonic2ObjectArtProvider implements ObjectArtProvider {
    private static final Logger LOGGER = Logger.getLogger(Sonic2ObjectArtProvider.class.getName());

    private Sonic2ObjectArt artLoader;
    private ObjectArtData artData;
    private int currentZoneIndex = -2; // Use -2 to distinguish from explicit -1

    private final Map<String, PatternSpriteRenderer> renderers = new HashMap<>();
    private final Map<String, ObjectSpriteSheet> sheets = new HashMap<>();
    private final Map<String, SpriteAnimationSet> animations = new HashMap<>();
    private final List<String> rendererKeys = new ArrayList<>();

    // For pattern caching in order
    private final List<ObjectSpriteSheet> sheetOrder = new ArrayList<>();
    private final List<PatternSpriteRenderer> rendererOrder = new ArrayList<>();

    // Results screen uses separate namespace
    private PatternSpriteRenderer resultsRenderer;
    private ObjectSpriteSheet resultsSheet;
    private Pattern[] hudDigitPatterns;
    private Pattern[] hudTextPatterns;
    private Pattern[] hudLivesPatterns;
    private Pattern[] hudLivesNumbers;

    /**
     * Creates a new Sonic2ObjectArtProvider.
     * The art loader is lazily initialized when loadArtForZone is first called.
     */
    public Sonic2ObjectArtProvider() {
        // Lazy initialization
    }

    /**
     * Creates a new Sonic2ObjectArtProvider with explicit ROM access.
     * Use this constructor when you have direct access to the ROM.
     */
    public Sonic2ObjectArtProvider(Rom rom, RomByteReader reader) {
        this.artLoader = new Sonic2ObjectArt(rom, reader);
    }

    private void ensureArtLoader() throws IOException {
        if (artLoader == null) {
            Rom rom = GameServices.rom().getRom();
            if (rom == null) {
                throw new IllegalStateException("ROM not loaded");
            }
            artLoader = new Sonic2ObjectArt(rom, RomByteReader.fromRom(rom));
        }
    }

    @Override
    public void loadArtForZone(int zoneIndex) throws IOException {
        if (artData != null && zoneIndex == currentZoneIndex) {
            return;
        }

        ensureArtLoader();
        artData = artLoader.loadForZone(zoneIndex);
        currentZoneIndex = zoneIndex;

        // Clear previous registrations
        renderers.clear();
        sheets.clear();
        animations.clear();
        rendererKeys.clear();
        sheetOrder.clear();
        rendererOrder.clear();

        // === Manual art (not in PLCs or needs complex construction) ===
        registerSheet(ObjectArtKeys.MONITOR, artData.monitorSheet());
        registerSheet(Sonic2ObjectArtKeys.WATERFALL, artData.waterfallSheet());
        registerSheet(ObjectArtKeys.ANIMAL, artData.animalSheet());
        registerSheet(Sonic2ObjectArtKeys.BREAKABLE_BLOCK, artData.breakableBlockSheet());
        registerSheet(Sonic2ObjectArtKeys.CPZ_PLATFORM, artData.cpzPlatformSheet());
        registerSheet(Sonic2ObjectArtKeys.SIDEWAYS_PFORM, artData.sidewaysPformSheet());
        // Red spring variants (share base spring art, different mappings — not PLC entries)
        registerSheet(ObjectArtKeys.SPRING_VERTICAL_RED, artData.springVerticalRedSheet());
        registerSheet(ObjectArtKeys.SPRING_HORIZONTAL_RED, artData.springHorizontalRedSheet());
        registerSheet(ObjectArtKeys.SPRING_DIAGONAL_RED, artData.springDiagonalRedSheet());
        // Checkpoint star (same art as checkpoint, different mappings)
        registerSheet(ObjectArtKeys.CHECKPOINT_STAR, artData.checkpointStarSheet());
        // Explosion, boss explosion, and Super Sonic stars are in PLCStdWtr (PLC 2),
        // not zone PLCs, but the old code loaded them unconditionally for all zones.
        registerSheet(ObjectArtKeys.EXPLOSION, artLoader.loadExplosionSheet());
        registerSheet(Sonic2ObjectArtKeys.BOSS_EXPLOSION, artLoader.loadBossExplosionSheet());
        registerSheet(Sonic2ObjectArtKeys.SUPER_SONIC_STARS, artLoader.loadSuperSonicStarsSheet());
        // Signpost (PLC 39) and EggPrison (PLC 64) are loaded on-demand in the ROM
        // (at end-of-level and after boss defeat), but the engine loads them at zone init.
        registerSheet(ObjectArtKeys.SIGNPOST, artLoader.loadSignpostSheet());
        registerSheet(ObjectArtKeys.EGG_PRISON, artLoader.loadEggPrisonSheet());

        // === PLC-driven art loading ===
        Rom rom = GameServices.rom().getRom();
        int[] plcIds = Sonic2PlcLoader.getZonePlcIds(rom, zoneIndex);
        loadPlcEntries(rom, Sonic2Constants.PLC_STD1);
        loadPlcEntries(rom, Sonic2Constants.PLC_STD2);
        loadPlcEntries(rom, plcIds[0]);  // Zone primary PLC
        loadPlcEntries(rom, plcIds[1]);  // Zone secondary PLC

        // === Art derivatives (same ROM art as PLC parent, different mappings) ===
        // Only load when the parent art was loaded by PLCs for this zone.
        if (sheets.containsKey(Sonic2ObjectArtKeys.GRABBER)) {
            registerSheet(Sonic2ObjectArtKeys.GRABBER_STRING, artLoader.loadGrabberStringSheet());
        }
        if (sheets.containsKey(Sonic2ObjectArtKeys.GROUNDER)) {
            registerSheet(Sonic2ObjectArtKeys.GROUNDER_ROCK, artLoader.loadGrounderRockSheet());
        }
        if (sheets.containsKey(Sonic2ObjectArtKeys.OOZ_FAN_HORIZ)) {
            registerSheet(Sonic2ObjectArtKeys.OOZ_FAN_VERT, artLoader.loadOOZFanVertSheet());
        }
        if (sheets.containsKey(Sonic2ObjectArtKeys.SEESAW)) {
            registerSheet(Sonic2ObjectArtKeys.SEESAW_BALL, artLoader.loadSeesawBallSheet());
        }
        if (sheets.containsKey(Sonic2ObjectArtKeys.MCZ_DRAWBRIDGE)) {
            registerSheet(Sonic2ObjectArtKeys.MCZ_BRIDGE, artLoader.loadMCZBridgeSheet());
        }
        if (sheets.containsKey(Sonic2ObjectArtKeys.MTZ_FLOOR_SPIKE)) {
            registerSheet(Sonic2ObjectArtKeys.MTZ_SPIKE, artLoader.loadMTZSpikeSheet());
        }

        // === Zone-specific overrides ===
        // HTZ barrier uses zone-specific art instead of CPZ ConstructionStripes
        if (zoneIndex == uk.co.jamesj999.sonic.game.sonic2.scroll.Sonic2ZoneConstants.ROM_ZONE_HTZ) {
            ObjectSpriteSheet htzBarrierSheet = artLoader.loadHTZBarrierSheet();
            if (htzBarrierSheet != null) {
                registerSheet(Sonic2ObjectArtKeys.BARRIER, htzBarrierSheet);
            }
        }

        // === Boss art (zone-conditional, Phase 3 would use boss PLCs) ===
        loadBossArt(zoneIndex);

        // === Results screen (separate namespace) ===
        resultsSheet = artData.resultsSheet();
        resultsRenderer = new PatternSpriteRenderer(resultsSheet);
        sheets.put(ObjectArtKeys.RESULTS, resultsSheet);
        renderers.put(ObjectArtKeys.RESULTS, resultsRenderer);
        rendererKeys.add(ObjectArtKeys.RESULTS);

        // === Animations ===
        animations.put(ObjectArtKeys.ANIM_MONITOR, artData.monitorAnimations());
        animations.put(ObjectArtKeys.ANIM_SPRING, artData.springAnimations());
        animations.put(ObjectArtKeys.ANIM_CHECKPOINT, artData.checkpointAnimations());
        animations.put(ObjectArtKeys.ANIM_SIGNPOST, artData.signpostAnimations());
        animations.put(Sonic2ObjectArtKeys.ANIM_FLIPPER, artData.flipperAnimations());
        animations.put(Sonic2ObjectArtKeys.ANIM_PIPE_EXIT_SPRING, artData.pipeExitSpringAnimations());
        animations.put(Sonic2ObjectArtKeys.ANIM_TIPPING_FLOOR, artData.tippingFloorAnimations());
        animations.put(Sonic2ObjectArtKeys.ANIM_SPRINGBOARD, artData.springboardAnimations());

        // === HUD patterns ===
        hudDigitPatterns = artData.getHudDigitPatterns();
        hudTextPatterns = artData.getHudTextPatterns();
        hudLivesPatterns = artData.getHudLivesPatterns();
        hudLivesNumbers = artData.getHudLivesNumbers();

        LOGGER.info("Sonic2ObjectArtProvider loaded for zone " + zoneIndex +
                " with " + rendererKeys.size() + " renderers (PLC-driven)");
    }

    /**
     * Loads art entries from a PLC definition, dispatching through the art registry.
     * Skips entries whose art key is already registered (prevents double-loading).
     */
    private void loadPlcEntries(Rom rom, int plcId) throws IOException {
        var plc = Sonic2PlcLoader.parsePlc(rom, plcId);
        for (var entry : plc.entries()) {
            var registration = Sonic2PlcArtRegistry.lookup(entry.romAddr());
            if (registration != null && !sheets.containsKey(registration.key())) {
                ObjectSpriteSheet sheet = registration.builder().build(artLoader);
                registerSheet(registration.key(), sheet);
            }
        }
    }

    /**
     * Registers a sheet if the key is not already present.
     * Used for boss art that may already have been loaded by PLCs.
     */
    private void registerIfAbsent(String key, java.util.function.Supplier<ObjectSpriteSheet> supplier) {
        if (!sheets.containsKey(key)) {
            ObjectSpriteSheet sheet = supplier.get();
            if (sheet != null) {
                registerSheet(key, sheet);
            }
        }
    }

    /**
     * Loads boss art for the given zone. Boss art is currently zone-conditional;
     * a future Phase 3 could load this via boss PLCs instead.
     */
    private void loadBossArt(int zoneIndex) {
        switch (zoneIndex) {
            case 0x00: // ROM_ZONE_EHZ
                registerIfAbsent(Sonic2ObjectArtKeys.EHZ_BOSS, artLoader::loadEHZBossSheet);
                break;
            case 0x0D: // ROM_ZONE_CPZ
                registerIfAbsent(Sonic2ObjectArtKeys.CPZ_BOSS_EGGPOD, artLoader::loadCPZBossEggpodSheet);
                registerIfAbsent(Sonic2ObjectArtKeys.CPZ_BOSS_PARTS, artLoader::loadCPZBossPartsSheet);
                registerIfAbsent(Sonic2ObjectArtKeys.CPZ_BOSS_JETS, artLoader::loadCPZBossJetsSheet);
                registerIfAbsent(Sonic2ObjectArtKeys.CPZ_BOSS_SMOKE, artLoader::loadCPZBossSmokeSheet);
                break;
            case 0x0F: // ROM_ZONE_ARZ
                registerIfAbsent(Sonic2ObjectArtKeys.ARZ_BOSS_MAIN, artLoader::loadARZBossMainSheet);
                registerIfAbsent(Sonic2ObjectArtKeys.ARZ_BOSS_PARTS, artLoader::loadARZBossPartsSheet);
                break;
            case 0x0C: // ROM_ZONE_CNZ
                registerIfAbsent(Sonic2ObjectArtKeys.CNZ_BOSS, artLoader::loadCNZBossSheet);
                break;
            case 0x07: // ROM_ZONE_HTZ
                registerIfAbsent(Sonic2ObjectArtKeys.HTZ_BOSS, artLoader::loadHTZBossSheet);
                registerIfAbsent(Sonic2ObjectArtKeys.HTZ_BOSS_SMOKE, artLoader::loadHTZBossSmokeSheet);
                break;
            case 0x0B: // ROM_ZONE_MCZ
                registerIfAbsent(Sonic2ObjectArtKeys.MCZ_BOSS, artLoader::loadMCZBossSheet);
                registerIfAbsent(Sonic2ObjectArtKeys.MCZ_FALLING_ROCKS, artLoader::loadMCZFallingRocksSheet);
                break;
        }
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
        return animations.get(key);
    }

    @Override
    public int getZoneData(String key, int zoneIndex) {
        if (artData == null) {
            return -1;
        }
        return switch (key) {
            case ObjectArtKeys.ANIMAL_TYPE_A -> artData.getAnimalTypeA();
            case ObjectArtKeys.ANIMAL_TYPE_B -> artData.getAnimalTypeB();
            default -> -1;
        };
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
    public List<String> getRendererKeys() {
        return new ArrayList<>(rendererKeys);
    }

    @Override
    public int ensurePatternsCached(GraphicsManager graphicsManager, int basePatternIndex) {
        int next = basePatternIndex;
        for (int i = 0; i < rendererOrder.size(); i++) {
            ObjectSpriteSheet sheet = sheetOrder.get(i);
            PatternSpriteRenderer renderer = rendererOrder.get(i);
            int count = sheet.getPatterns().length;
            renderer.ensurePatternsCached(graphicsManager, next);
            next += count;
        }

        // Results screen uses a dedicated pattern namespace starting at 0.
        // This ensures its tile indices (0-465) map directly to texture IDs,
        // avoiding issues with high basePatternIndex values.
        // We use a high offset (0x10000) to avoid collision with level/object patterns.
        if (resultsRenderer != null) {
            resultsRenderer.ensurePatternsCached(graphicsManager, 0x10000);
        }

        return next;
    }

    @Override
    public boolean isReady() {
        PatternSpriteRenderer monitorRenderer = renderers.get(ObjectArtKeys.MONITOR);
        PatternSpriteRenderer spikeRenderer = renderers.get(ObjectArtKeys.SPIKE);
        PatternSpriteRenderer springRenderer = renderers.get(ObjectArtKeys.SPRING_VERTICAL);
        return (monitorRenderer != null && monitorRenderer.isReady())
                || (spikeRenderer != null && spikeRenderer.isReady())
                || (springRenderer != null && springRenderer.isReady());
    }

    /**
     * Gets the underlying art data for direct access when needed.
     * Prefer using key-based lookups when possible.
     */
    public ObjectArtData getArtData() {
        return artData;
    }

    /**
     * Registers the SmashableGround sprite sheet using level patterns.
     * This must be called AFTER the level is loaded since SmashableGround uses
     * level art patterns (ArtTile_ArtKos_LevelArt) rather than dedicated object art.
     * <p>
     * Only registers if we're in HTZ (zone 0x07) and the level is available.
     *
     * @param level The loaded level to extract patterns from
     */
    public void registerSmashableGroundSheet(uk.co.jamesj999.sonic.level.Level level) {
        if (level == null || artLoader == null) {
            return;
        }

        // Only register for HTZ
        int zoneIndex = level.getZoneIndex();
        if (zoneIndex != uk.co.jamesj999.sonic.game.sonic2.scroll.Sonic2ZoneConstants.ROM_ZONE_HTZ) {
            return;
        }

        // Load and register the sheet
        ObjectSpriteSheet sheet = artLoader.loadSmashableGroundSheet(level);
        if (sheet != null) {
            registerSheet(Sonic2ObjectArtKeys.SMASHABLE_GROUND, sheet);
        }
    }

    /**
     * Register the SteamSpring piston body sheet for MTZ.
     * The piston body uses level art patterns (ArtTile_ArtKos_LevelArt) with palette line 3.
     * Only registers if we're in MTZ (zone 0x04 or 0x05).
     *
     * @param level The loaded level to extract patterns from
     */
    public void registerSteamSpringPistonSheet(uk.co.jamesj999.sonic.level.Level level) {
        if (level == null || artLoader == null) {
            return;
        }

        int zoneIndex = level.getZoneIndex();
        if (zoneIndex != uk.co.jamesj999.sonic.game.sonic2.scroll.Sonic2ZoneConstants.ROM_ZONE_MTZ
                && zoneIndex != 0x05) {
            return;
        }

        ObjectSpriteSheet sheet = artLoader.loadSteamSpringPistonSheet(level);
        if (sheet != null) {
            registerSheet(Sonic2ObjectArtKeys.MTZ_STEAM_PISTON, sheet);
        }
    }
}
