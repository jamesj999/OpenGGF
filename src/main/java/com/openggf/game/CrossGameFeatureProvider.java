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
import com.openggf.game.sonic3k.Sonic3kPlayerArt;
import com.openggf.game.sonic2.Sonic2DustArt;
import com.openggf.game.sonic2.Sonic2PlayerArt;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Palette;
import com.openggf.sprites.art.SpriteArtSet;

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

    private String donorGameId;
    private RomByteReader donorReader;
    private Sonic2PlayerArt s2PlayerArt;
    private Sonic3kPlayerArt s3kPlayerArt;
    private Sonic2DustArt s2DustArt;
    private SmpsLoader donorSmpsLoader;
    private DacData donorDacData;
    private PhysicsFeatureSet hybridFeatureSet;
    private RenderContext donorRenderContext;
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
    public void initialize(String donorGameId) throws IOException {
        this.donorGameId = donorGameId;
        Rom donorRom = RomManager.getInstance().getSecondaryRom(donorGameId);
        this.donorReader = RomByteReader.fromRom(donorRom);

        if ("s3k".equalsIgnoreCase(donorGameId)) {
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
        GameId donorId = GameId.fromCode(donorGameId);
        donorRenderContext = RenderContext.getOrCreateDonor(donorId);
        Palette charPalette = loadCharacterPalette();
        if (charPalette != null) {
            donorRenderContext.setPalette(0, charPalette);
        }

        initializeDonorAudio();

        active = true;
        LOGGER.info("Cross-game feature provider initialized with donor: " + donorGameId);
    }

    /**
     * Returns true if the cross-game feature provider is initialized and active.
     */
    public static boolean isActive() {
        return instance != null && instance.active;
    }

    @Override
    public SpriteArtSet loadPlayerSpriteArt(String characterCode) throws IOException {
        if (s3kPlayerArt != null) {
            return s3kPlayerArt.loadForCharacter(characterCode);
        }
        if (s2PlayerArt != null) {
            return s2PlayerArt.loadForCharacter(characterCode);
        }
        LOGGER.warning("CrossGameFeatureProvider is active but no art loader available for: " + characterCode);
        return null;
    }

    @Override
    public SpriteArtSet loadSpindashDustArt(String characterCode) throws IOException {
        if (s2DustArt != null) {
            return s2DustArt.loadForCharacter(characterCode);
        }
        return null;
    }

    /**
     * Returns a hybrid PhysicsFeatureSet: spindash from donor, everything else from S1.
     */
    public PhysicsFeatureSet getHybridFeatureSet() {
        return hybridFeatureSet;
    }

    /**
     * Donor games (S2/S3K) always support a sidekick character.
     */
    public boolean supportsSidekick() {
        return true;
    }

    /**
     * Loads the character palette (palette line 0) from the donor ROM.
     * This provides the correct Sonic/Tails colors for donor sprites
     * without interfering with the base game's level palettes (lines 1-3).
     *
     * @return the donor's character palette, or null if unavailable
     */
    public Palette loadCharacterPalette() {
        if (donorReader == null) {
            return null;
        }
        int paletteAddr;
        if ("s3k".equalsIgnoreCase(donorGameId)) {
            paletteAddr = Sonic3kConstants.SONIC_PALETTE_ADDR;
        } else {
            paletteAddr = Sonic2Constants.SONIC_TAILS_PALETTE_ADDR;
        }
        byte[] data = donorReader.slice(paletteAddr, Palette.PALETTE_SIZE_IN_ROM);
        Palette palette = new Palette();
        palette.fromSegaFormat(data);
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
        if ("s3k".equalsIgnoreCase(donorGameId)) {
            donorProfile = new Sonic3kAudioProfile();
        } else {
            donorProfile = new Sonic2AudioProfile();
        }

        try {
            Rom donorRom = RomManager.getInstance().getSecondaryRom(donorGameId);
            donorSmpsLoader = donorProfile.createSmpsLoader(donorRom);
            donorDacData = donorSmpsLoader.loadDacData();

            AudioManager am = AudioManager.getInstance();
            am.registerDonorLoader(donorGameId, donorSmpsLoader, donorDacData,
                    donorProfile.getSequencerConfig());

            Map<GameSound, Integer> donorSounds = donorProfile.getSoundMap();
            for (Map.Entry<GameSound, Integer> entry : donorSounds.entrySet()) {
                am.registerDonorSound(entry.getKey(), donorGameId, entry.getValue());
            }

            LOGGER.info("Donor audio initialized from " + donorGameId
                    + " (" + donorSounds.size() + " sounds registered)");
        } catch (IOException e) {
            LOGGER.warning("Failed to initialize donor audio from " + donorGameId
                    + ": " + e.getMessage());
        }
    }

    public String getDonorGameId() {
        return donorGameId;
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

    public void close() {
        donorReader = null;
        s2PlayerArt = null;
        s3kPlayerArt = null;
        s2DustArt = null;
        donorSmpsLoader = null;
        donorDacData = null;
        hybridFeatureSet = null;
        donorRenderContext = null;
        active = false;
    }

    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    /**
     * Builds a hybrid feature set: spindash enabled with donor speed table,
     * but all other flags remain S1 defaults (UNIFIED collision, fixed angle
     * threshold, no extended edge balance, etc.).
     */
    private PhysicsFeatureSet buildHybridFeatureSet() {
        // Spindash speed table is the same for S2 and S3K
        short[] spindashSpeedTable = new short[]{
                0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00
        };

        return new PhysicsFeatureSet(
                true,                                           // spindashEnabled (from donor)
                spindashSpeedTable,                             // spindashSpeedTable (from donor)
                CollisionModel.UNIFIED,                         // collisionModel (S1)
                true,                                           // fixedAnglePosThreshold (S1)
                PhysicsFeatureSet.LOOK_SCROLL_DELAY_NONE,       // lookScrollDelay (S1)
                true,                                           // waterShimmerEnabled (S1)
                true,                                           // inputAlwaysCapsGroundSpeed (S1)
                false,                                          // elementalShieldsEnabled (S1)
                false,                                          // angleDiffCardinalSnap (S1)
                false                                           // extendedEdgeBalance (S1)
        );
    }
}
