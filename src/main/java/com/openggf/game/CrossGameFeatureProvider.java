package com.openggf.game;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.GameSound;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.data.SpindashDustArtProvider;
import com.openggf.game.sonic2.Sonic2SuperStateController;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.Sonic3kPlayerArt;
import com.openggf.game.sonic3k.Sonic3kSuperStateController;
import com.openggf.game.sonic2.Sonic2DustArt;
import com.openggf.game.sonic2.Sonic2PlayerArt;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.animation.AnimationTranslator;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperStateController;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Provides cross-game feature donation: loads player sprites, spindash dust,
 * and physics from a donor game (S2 or S3K) while the base game (e.g., S1)
 * handles levels, collision, objects, and audio.
 *
 * <p>Singleton. Activated via {@code CROSS_GAME_FEATURES_ENABLED} config key.
 * The donor ROM is opened as a secondary ROM (no module detection side-effect).
 */
public class CrossGameFeatureProvider implements PlayerSpriteArtProvider, SpindashDustArtProvider {
    private static final Logger LOGGER = Logger.getLogger(CrossGameFeatureProvider.class.getName());

    private static CrossGameFeatureProvider instance;

    private GameId donorGameId;
    private RomByteReader donorReader;
    private Sonic2PlayerArt s2PlayerArt;
    private Sonic3kPlayerArt s3kPlayerArt;
    private Sonic2DustArt s2DustArt;
    private SmpsLoader donorSmpsLoader;
    private DacData donorDacData;
    private PhysicsFeatureSet hybridFeatureSet;
    private RenderContext donorRenderContext;
    private PlayerSpriteRenderer instaShieldRenderer;
    private SpriteArtSet instaShieldArtSet;
    private DonorCapabilities donorCapabilities;
    private boolean active;

    private CrossGameFeatureProvider() {
    }

    public static synchronized CrossGameFeatureProvider getInstance() {
        if (instance == null) {
            instance = new CrossGameFeatureProvider();
        }
        return instance;
    }

    /**
     * Initializes the provider by opening the donor ROM and creating art loaders.
     *
     * @param donorGameId "s2" or "s3k"
     * @throws IOException if the donor ROM cannot be opened
     */
    public void initialize(String donorGameCode) throws IOException {
        this.donorGameId = GameId.fromCode(donorGameCode);

        // Same-game guard: disable donation when donor == host
        GameId hostId = GameModuleRegistry.getCurrent().getGameId();
        if (donorGameId == hostId) {
            LOGGER.info("Donor same as host (" + donorGameId.code() + "), donation disabled");
            active = false;
            return;
        }

        // Resolve donor capabilities before any ROM access
        this.donorCapabilities = resolveDonorCapabilities(donorGameId);
        if (donorCapabilities == null) {
            LOGGER.warning("No donor capabilities for: " + donorGameId.code());
            active = false;
            return;
        }

        Rom donorRom = RomManager.getInstance().getSecondaryRom(donorGameId.code());
        this.donorReader = RomByteReader.fromRom(donorRom);

        if (donorGameId == GameId.S3K) {
            s3kPlayerArt = new Sonic3kPlayerArt(donorReader);
            // S3K does not have a separate SpindashDustArtProvider
            s2DustArt = null;
            s2PlayerArt = null;
        } else {
            // Default to S2
            s2PlayerArt = new Sonic2PlayerArt(donorReader);
            s2DustArt = new Sonic2DustArt(donorReader);
            s3kPlayerArt = null;
        }

        hybridFeatureSet = buildHybridFeatureSet();

        // Create donor render context for palette isolation
        donorRenderContext = RenderContext.getOrCreateDonor(donorGameId);
        String mainChar = com.openggf.configuration.SonicConfigurationService.getInstance()
                .getString(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE);
        Palette charPalette = loadCharacterPalette(mainChar);
        if (charPalette != null) {
            donorRenderContext.setPalette(0, charPalette);
        }

        initializeDonorAudio();
        loadInstaShieldArt();

        active = true;
        LOGGER.info("Cross-game feature provider initialized with donor: " + donorGameId.code());
    }

    /**
     * Returns true if the cross-game feature provider is initialized and active.
     */
    public static boolean isActive() {
        return instance != null && instance.active;
    }

    @Override
    public SpriteArtSet loadPlayerSpriteArt(String characterCode) throws IOException {
        if (donorCapabilities == null) {
            return null;
        }
        PlayerSpriteArtProvider artProvider = donorCapabilities.getPlayerArtProvider(donorReader);
        SpriteArtSet donorArt = artProvider.loadPlayerSpriteArt(characterCode);
        if (donorArt == null || donorArt.animationProfile() == null) {
            return donorArt;
        }
        // Translate the animation profile for host compatibility
        if (donorArt.animationProfile() instanceof ScriptedVelocityAnimationProfile donorProfile) {
            ScriptedVelocityAnimationProfile translated = AnimationTranslator.translate(
                    donorCapabilities, donorProfile, donorArt.animationSet());
            return new SpriteArtSet(donorArt.artTiles(), donorArt.mappingFrames(),
                    donorArt.dplcFrames(), donorArt.paletteIndex(), donorArt.basePatternIndex(),
                    donorArt.frameDelay(), donorArt.bankSize(), translated, donorArt.animationSet());
        }
        return donorArt;
    }

    @Override
    public SpriteArtSet loadSpindashDustArt(String characterCode) throws IOException {
        if (s2DustArt != null) {
            return s2DustArt.loadForCharacter(characterCode);
        }
        return null;
    }

    /**
     * Returns a hybrid PhysicsFeatureSet: spindash/insta-shield from donor capabilities,
     * everything else from the current (base) game module.
     */
    public PhysicsFeatureSet getHybridFeatureSet() {
        return hybridFeatureSet;
    }

    /**
     * Returns true if the donor game natively includes a sidekick character (e.g., Tails).
     */
    public boolean supportsSidekick() {
        return donorCapabilities != null && donorCapabilities.hasSidekick();
    }

    /**
     * Loads the character palette (palette line 0) from the donor ROM.
     * This provides the correct Sonic/Tails colors for donor sprites
     * without interfering with the base game's level palettes (lines 1-3).
     *
     * @return the donor's character palette, or null if unavailable
     */
    public Palette loadCharacterPalette() {
        return loadCharacterPalette(null);
    }

    /**
     * Loads the character palette from the donor ROM.
     * Knuckles uses a separate palette (Pal_Knuckles); Sonic/Tails share one.
     *
     * @param characterCode the character code ("sonic", "tails", "knuckles"), or null for default
     * @return the donor's character palette, or null if unavailable
     */
    @Override
    public Palette loadCharacterPalette(String characterCode) {
        if (donorReader == null) {
            return null;
        }
        int paletteAddr;
        int paletteSize = Palette.PALETTE_SIZE_IN_ROM;
        if (donorGameId == GameId.S3K) {
            if ("knuckles".equalsIgnoreCase(characterCode)) {
                // Pal_Knuckles: 32 bytes (1 palette line)
                paletteAddr = Sonic3kConstants.KNUCKLES_PALETTE_ADDR;
                paletteSize = 32;
            } else {
                paletteAddr = Sonic3kConstants.SONIC_PALETTE_ADDR;
            }
        } else {
            paletteAddr = Sonic2Constants.SONIC_TAILS_PALETTE_ADDR;
        }
        byte[] data = donorReader.slice(paletteAddr, paletteSize);
        Palette palette = new Palette();
        palette.fromSegaFormat(data);
        return palette;
    }

    /**
     * Returns a palette compatible with the HOST game's palette line 0 layout,
     * but with the donor character's colors. For Knuckles donated from S3K into S2,
     * this returns the S2-compatible Knuckles palette (0x060BEA) which has Knuckles'
     * reds at indices 2-5 but keeps S2's universal colors at indices 6-15.
     *
     * @param characterCode character code
     * @return host-compatible palette, or null if not applicable
     */
    public Palette loadHostCompatiblePalette(String characterCode) {
        if (donorReader == null || donorGameId != GameId.S3K) {
            return null;
        }
        if (!"knuckles".equalsIgnoreCase(characterCode)) {
            return null;
        }
        // Use the S2-compatible Knuckles palette from the S3K ROM
        byte[] data = donorReader.slice(Sonic3kConstants.KNUCKLES_S2_PALETTE_ADDR,
                Palette.PALETTE_SIZE_IN_ROM);
        Palette palette = new Palette();
        palette.fromSegaFormat(data);
        // Index 4 in the S2-compat palette is green (0x0080, Knuckles' shoe detail).
        // The S3K life icon "K.T.E." text pixels remap to this index and show green.
        // Replace with orange/gold (matching S2's index 14 = 0x00AE) so the HUD
        // text is readable. The player sprite uses the donor context palette (not
        // this one) so Knuckles' in-game green is unaffected.
        Palette.Color gold = palette.getColor(14); // 0x00AE orange
        Palette.Color idx4 = palette.getColor(4);
        idx4.r = gold.r;
        idx4.g = gold.g;
        idx4.b = gold.b;
        return palette;
    }

    public RenderContext getDonorRenderContext() {
        return donorRenderContext;
    }

    /**
     * Initializes donor audio: creates a donor SmpsLoader and DacData from
     * the donor ROM, then registers all donor sounds with AudioManager.
     * Base game's sound map always takes priority at playback time, so
     * shared sounds (JUMP, RING) still use the base game's versions.
     */
    private void initializeDonorAudio() {
        GameAudioProfile donorProfile;
        if (donorGameId == GameId.S3K) {
            donorProfile = new Sonic3kAudioProfile();
        } else {
            donorProfile = new Sonic2AudioProfile();
        }

        try {
            Rom donorRom = RomManager.getInstance().getSecondaryRom(donorGameId.code());
            donorSmpsLoader = donorProfile.createSmpsLoader(donorRom);
            donorDacData = donorSmpsLoader.loadDacData();

            AudioManager am = GameServices.audio();
            am.registerDonorLoader(donorGameId.code(), donorSmpsLoader, donorDacData,
                    donorProfile.getSequencerConfig());

            Map<GameSound, Integer> donorSounds = donorProfile.getSoundMap();
            for (Map.Entry<GameSound, Integer> entry : donorSounds.entrySet()) {
                am.registerDonorSound(entry.getKey(), donorGameId.code(), entry.getValue());
            }

            LOGGER.info("Donor audio initialized from " + donorGameId.code()
                    + " (" + donorSounds.size() + " sounds registered)");
        } catch (IOException e) {
            LOGGER.warning("Failed to initialize donor audio from " + donorGameId.code()
                    + ": " + e.getMessage());
        }
    }

    public String getDonorGameId() {
        return donorGameId == null ? null : donorGameId.code();
    }

    /**
     * Returns true if the donor game uses separate art for Tails' tail appendage (Obj05).
     * S3K has separate Map_Tails_Tail / DPLC_Tails_Tail tables; S2 reuses the main body art.
     */
    public boolean hasSeparateTailsTailArt() {
        return s3kPlayerArt != null;
    }

    /**
     * Loads the separate tail appendage art set from the donor game.
     * Only valid when {@link #hasSeparateTailsTailArt()} returns true.
     */
    public SpriteArtSet loadTailsTailArt() throws IOException {
        if (s3kPlayerArt != null) {
            return s3kPlayerArt.loadTailsTail();
        }
        return null;
    }

    /**
     * Creates a Super Sonic state controller using the donor game's implementation
     * and pre-loads ROM data from the donor ROM.
     *
     * @param player the player sprite to attach the controller to
     * @return a donor-game SuperStateController with ROM data pre-loaded, or null
     */
    public SuperStateController createSuperStateController(AbstractPlayableSprite player) {
        if (!active || donorReader == null || donorCapabilities == null) {
            return null;
        }
        if (!donorCapabilities.hasSuperTransform()) {
            return null;  // S1 donor: no super transformation
        }
        // Controller selection stays game-specific (different palette formats)
        SuperStateController ctrl;
        if (donorGameId == GameId.S3K) {
            ctrl = new Sonic3kSuperStateController(player);
        } else {
            ctrl = new Sonic2SuperStateController(player);
        }
        try {
            ctrl.loadRomData(donorReader);
            ctrl.setRomDataPreLoaded(true);
            LOGGER.fine("Created cross-game Super Sonic controller from donor: " + donorGameId.code());
        } catch (Exception e) {
            LOGGER.warning("Failed to load donor Super ROM data: " + e.getMessage());
            return null;
        }
        return ctrl;
    }

    public void resetState() {
        close();
    }

    public void close() {
        donorReader = null;
        s2PlayerArt = null;
        s3kPlayerArt = null;
        s2DustArt = null;
        donorSmpsLoader = null;
        donorDacData = null;
        hybridFeatureSet = null;
        donorRenderContext = null;
        instaShieldRenderer = null;
        instaShieldArtSet = null;
        donorCapabilities = null;
        active = false;
    }

    /**
     * Loads insta-shield art tiles, mappings, DPLCs, and animations from the S3K donor ROM.
     * Only runs when the donor is S3K; silently skips for S2 donors.
     */
    private void loadInstaShieldArt() {
        if (donorGameId != GameId.S3K || donorReader == null) {
            return;
        }
        try {
            Pattern[] tiles = S3kSpriteDataLoader.loadArtTiles(donorReader,
                    Sonic3kConstants.ART_UNC_INSTA_SHIELD_ADDR,
                    Sonic3kConstants.ART_UNC_INSTA_SHIELD_SIZE);
            java.util.List<SpriteMappingFrame> mappings = S3kSpriteDataLoader.loadMappingFrames(
                    donorReader, Sonic3kConstants.MAP_INSTA_SHIELD_ADDR);
            java.util.List<SpriteDplcFrame> dplcs = S3kSpriteDataLoader.loadDplcFrames(
                    donorReader, Sonic3kConstants.DPLC_INSTA_SHIELD_ADDR);

            // Ensure DPLC count doesn't exceed mapping count
            if (dplcs.size() > mappings.size()) {
                dplcs = new java.util.ArrayList<>(dplcs.subList(0, mappings.size()));
            }
            // Pad DPLC list if shorter than mappings (empty DPLC = reuse previous tiles)
            while (dplcs.size() < mappings.size()) {
                dplcs.add(new SpriteDplcFrame(java.util.List.of()));
            }

            int bankSize = S3kSpriteDataLoader.resolveBankSize(dplcs, mappings);
            SpriteAnimationSet animSet = S3kSpriteDataLoader.loadAnimationSet(donorReader,
                    Sonic3kConstants.ANI_INSTA_SHIELD_ADDR,
                    Sonic3kConstants.ANI_INSTA_SHIELD_COUNT);

            instaShieldArtSet = new SpriteArtSet(tiles, mappings, dplcs,
                    0, Sonic3kConstants.ART_TILE_SHIELD, 1, bankSize, null, animSet);
            instaShieldRenderer = new PlayerSpriteRenderer(instaShieldArtSet);

            LOGGER.info("Loaded donor insta-shield art: " + tiles.length + " tiles, "
                    + mappings.size() + " mapping frames");
        } catch (IOException e) {
            LOGGER.warning("Failed to load donor insta-shield art: " + e.getMessage());
        }
    }

    public PlayerSpriteRenderer getInstaShieldRenderer() {
        return instaShieldRenderer;
    }

    public SpriteArtSet getInstaShieldArtSet() {
        return instaShieldArtSet;
    }

    /**
     * Builds a hybrid feature set: spindash/insta-shield enabled based on donor capabilities,
     * collision model and other flags inherited from the current (base) game module.
     * This ensures that S2/S3K levels keep DUAL_PATH collision (required for plane switching)
     * while S1 levels keep UNIFIED collision.
     */
    private PhysicsFeatureSet buildHybridFeatureSet() {
        PhysicsFeatureSet donorFeatureSet = resolveDonorFeatureSet();
        short[] spindashSpeedTable = donorCapabilities.hasSpindash()
                ? donorFeatureSet.spindashSpeedTable()
                : null;

        // Inherit collision model from the base game module so plane switching
        // works correctly in S2/S3K levels with cross-game features enabled
        PhysicsFeatureSet baseFeatureSet = GameModuleRegistry.getCurrent()
                .getPhysicsProvider().getFeatureSet();

        return new PhysicsFeatureSet(
                donorCapabilities.hasSpindash(),                // spindashEnabled (from donor)
                spindashSpeedTable,                             // spindashSpeedTable (from donor)
                baseFeatureSet.collisionModel(),                // collisionModel (from base game)
                baseFeatureSet.fixedAnglePosThreshold(),        // fixedAnglePosThreshold (from base game)
                baseFeatureSet.lookScrollDelay(),               // lookScrollDelay (from base game)
                baseFeatureSet.waterShimmerEnabled(),           // waterShimmerEnabled (from base game)
                baseFeatureSet.inputAlwaysCapsGroundSpeed(),    // inputAlwaysCapsGroundSpeed (from base game)
                donorCapabilities.hasElementalShields(),        // elementalShieldsEnabled (from donor)
                donorCapabilities.hasInstaShield(),             // instaShieldEnabled (from donor)
                baseFeatureSet.angleDiffCardinalSnap(),         // angleDiffCardinalSnap (from base game)
                baseFeatureSet.extendedEdgeBalance(),           // extendedEdgeBalance (from base game)
                baseFeatureSet.ringFloorCheckMask(),            // ringFloorCheckMask (from base game)
                baseFeatureSet.ringCollisionWidth(),            // ringCollisionWidth (from base game)
                baseFeatureSet.ringCollisionHeight(),           // ringCollisionHeight (from base game)
                donorCapabilities.hasElementalShields(),        // lightningShieldEnabled (from donor — lightning requires elemental shields)
                baseFeatureSet.superSpindashSpeedTable(),       // superSpindashSpeedTable (from base game)
                baseFeatureSet.movingCrouchThreshold(),         // movingCrouchThreshold (from base game)
                baseFeatureSet.groundWallCollisionEnabled(),    // groundWallCollisionEnabled (from base game)
                baseFeatureSet.airSuperspeedPreserved(),        // airSuperspeedPreserved (from base game)
                baseFeatureSet.slopeRepelChecksOnObject()       // slopeRepelChecksOnObject (from base game)
        );
    }

    private PhysicsFeatureSet resolveDonorFeatureSet() {
        return switch (donorGameId) {
            case S1 -> PhysicsFeatureSet.SONIC_1;
            case S2 -> PhysicsFeatureSet.SONIC_2;
            case S3K -> PhysicsFeatureSet.SONIC_3K;
        };
    }

    /**
     * Resolves donor capabilities for the given game ID by constructing a
     * temporary GameModule and extracting its DonorCapabilities.
     */
    private static DonorCapabilities resolveDonorCapabilities(GameId gameId) {
        return switch (gameId) {
            case S1 -> new com.openggf.game.sonic1.Sonic1GameModule().getDonorCapabilities();
            case S2 -> new com.openggf.game.sonic2.Sonic2GameModule().getDonorCapabilities();
            case S3K -> new com.openggf.game.sonic3k.Sonic3kGameModule().getDonorCapabilities();
        };
    }
}
