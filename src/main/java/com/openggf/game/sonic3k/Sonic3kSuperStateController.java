package com.openggf.game.sonic3k;

import com.openggf.data.RomByteReader;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.PhysicsProfile;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperStateController;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.util.logging.Logger;
import com.openggf.game.GameServices;

/**
 * S3K-specific Super Sonic state controller.
 *
 * <p>Palette cycling uses ROM data from {@code PalCycle_SuperSonic} (0x398E).
 * The 60-byte table contains 10 frames of 6 bytes each (3 Mega Drive colors per frame).
 * Colors are written to palette line 0, color indices 2-4 (Sonic's body colors).
 *
 * <p>Palette states:
 * <ul>
 *   <li>0 = off (normal palette)</li>
 *   <li>1 = fading in (transformation) - timer 1, advance 6 bytes, complete at offset $24</li>
 *   <li>-1 = cycling (active Super Sonic) - timer 6, advance 6 bytes, wrap at $36 back to $24</li>
 *   <li>2 = fading out (reverting) - timer 3, retreat 6 bytes, stop at 0</li>
 * </ul>
 */
public class Sonic3kSuperStateController extends SuperStateController {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kSuperStateController.class.getName());

    /** Palette fade state: 0=off, 1=fading in, -1=cycling, 2=fading out. */
    private int paletteState;
    /** Current palette frame byte offset into the cycling data (increments by 6). */
    private int paletteFrame;
    /** Countdown timer between palette frame advances. */
    private int paletteTimer;
    /** Frames remaining in the transformation animation. */
    private int transformFramesRemaining;

    /** Raw ROM palette data (60 bytes: 10 frames x 3 colors x 2 bytes). */
    private byte[] paletteData;

    /** Super Sonic animation set (loaded from ROM). */
    private SpriteAnimationSet superAnimSet;
    /** Normal animation set (saved on activation, restored on revert). */
    private SpriteAnimationSet normalAnimSet;

    /** Super Sonic sprite renderer (loaded from ROM, uses Map_SuperSonic / PLC_SuperSonic). */
    private PlayerSpriteRenderer superRenderer;
    /** Normal sprite renderer (saved on activation, restored on revert). */
    private PlayerSpriteRenderer normalRenderer;

    /** Palette line index where Sonic's colors reside. */
    private static final int SONIC_PALETTE_INDEX = 0;
    /** First color index to write (palette+4 bytes = color index 2). */
    private static final int FIRST_COLOR_INDEX = 2;
    /** Number of colors written per frame (S3K uses 3, vs S2's 4). */
    private static final int COLORS_PER_FRAME = 3;
    /** Bytes per palette frame (3 colors x 2 bytes). */
    private static final int BYTES_PER_FRAME = 6;
    /** Byte offset at which fade-in is complete (6 frames x 6 bytes = $24). */
    private static final int FADE_COMPLETE_OFFSET = 0x24;
    /** Byte offset at which cycling wraps ($36 = 9 frames x 6 bytes). */
    private static final int CYCLE_WRAP_OFFSET = 0x36;

    public Sonic3kSuperStateController(AbstractPlayableSprite player) {
        super(player);
    }

    @Override
    public void reset() {
        super.reset();
        paletteState = 0;
        paletteFrame = 0;
        paletteTimer = 0;
        transformFramesRemaining = 0;
    }

    @Override
    public void loadRomData(RomByteReader reader) {
        int addr = Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_ADDR;
        int len = Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_ENTRY_COUNT
                * Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_ENTRY_SIZE;
        if (addr == 0 || addr + len > reader.size()) {
            LOGGER.warning("S3K Super Sonic palette data not available at ROM address 0x"
                    + Integer.toHexString(addr));
            return;
        }
        paletteData = reader.slice(addr, len);
        LOGGER.fine("Loaded S3K Super Sonic palette data: " + len + " bytes from ROM 0x"
                + Integer.toHexString(addr));

        Sonic3kPlayerArt playerArt = new Sonic3kPlayerArt(reader);
        superAnimSet = playerArt.loadSuperSonicAnimationSet();
        if (superAnimSet != null) {
            LOGGER.fine("Loaded S3K Super Sonic animation set");
        }

        try {
            SpriteArtSet superArtSet = playerArt.loadSuperSonicArtSet();
            if (superArtSet != null) {
                superRenderer = new PlayerSpriteRenderer(superArtSet);
                if (CrossGameFeatureProvider.isActive()) {
                    superRenderer.setRenderContext(
                            com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().crossGameFeatures().getDonorRenderContext());
                }
                LOGGER.fine("Loaded S3K Super Sonic sprite renderer");
            }
        } catch (Exception e) {
            LOGGER.warning("Could not load Super Sonic art set: " + e.getMessage());
        }
    }

    @Override
    protected int getRingDrainInterval() {
        return Sonic3kConstants.SUPER_SONIC_RING_DRAIN_INTERVAL;
    }

    @Override
    protected int getMinRingsToTransform() {
        return Sonic3kConstants.SUPER_SONIC_MIN_RINGS;
    }

    @Override
    protected PhysicsProfile getSuperProfile() {
        // S3K Super Tails: max=$800, accel=$18, decel=$C0 (sonic3k.asm:26325-26327)
        // S3K Super Sonic: max=$A00, accel=$30, decel=$100 (sonic3k.asm:22084-22086)
        if (player instanceof Tails) {
            return PhysicsProfile.SONIC_3K_SUPER_TAILS;
        }
        return PhysicsProfile.SONIC_3K_SUPER_SONIC;
    }

    @Override
    protected PhysicsProfile getNormalProfile() {
        // On revert, use canonical "reset" values — NOT init (Character_Speeds) values.
        // ROM: speed shoes expire code sets $600/$C/$80 for all characters.
        if (player instanceof Tails) {
            return PhysicsProfile.SONIC_2_TAILS;
        }
        return PhysicsProfile.SONIC_2_SONIC;
    }

    @Override
    protected void onTransformationStarted() {
        paletteState = 1;
        paletteFrame = 0;
        paletteTimer = 1;
        transformFramesRemaining = 30;
        // Play transformation SFX
        try {
            if (CrossGameFeatureProvider.isActive()) {
                GameServices.audio().playDonorSfx(
                        com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().crossGameFeatures().getDonorGameId(),
                        Sonic3kSfx.SUPER_TRANSFORM.id);
            } else {
                GameServices.audio().playSfx(Sonic3kSfx.SUPER_TRANSFORM.id);
            }
        } catch (Exception e) {
            LOGGER.fine("Could not play transformation SFX: " + e.getMessage());
        }
    }

    @Override
    protected boolean updateTransformationAnimation() {
        updatePaletteFade();
        transformFramesRemaining--;
        return transformFramesRemaining <= 0;
    }

    @Override
    protected void onSuperActivated() {
        paletteState = -1;
        paletteTimer = 6;
        // Play invincibility music (S3K Super Sonic uses mus_Invincibility)
        try {
            if (CrossGameFeatureProvider.isActive()) {
                GameServices.audio().playDonorMusic(
                        com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().crossGameFeatures().getDonorGameId(),
                        Sonic3kMusic.INVINCIBILITY.id);
            } else {
                GameServices.audio().playMusic(Sonic3kMusic.INVINCIBILITY.id);
            }
        } catch (Exception e) {
            LOGGER.fine("Could not play Super Sonic music: " + e.getMessage());
        }
        player.setInvincibleFrames(0);
        if (superAnimSet != null) {
            normalAnimSet = player.getAnimationSet();
            player.setAnimationSet(superAnimSet);
        }
        // Swap to Super Sonic sprite renderer (different mappings/DPLCs)
        if (superRenderer != null) {
            normalRenderer = player.getSpriteRenderer();
            player.setSpriteRenderer(superRenderer);
        }
        player.setShieldVisible(false);
        LOGGER.info("Super Sonic activated (S3K)");
    }

    @Override
    protected void updateSuperPalette() {
        if (paletteState != -1) return;

        paletteTimer--;
        if (paletteTimer >= 0) return;

        paletteTimer = 6;

        int frameOffset = paletteFrame;
        paletteFrame += BYTES_PER_FRAME;

        if (paletteFrame > CYCLE_WRAP_OFFSET) {
            paletteFrame = FADE_COMPLETE_OFFSET;
        }

        applyPaletteFrame(frameOffset);
    }

    @Override
    protected void onRevertStarted() {
        paletteState = 2;
        paletteFrame = FADE_COMPLETE_OFFSET - BYTES_PER_FRAME;
        paletteTimer = 3;
        player.setInvincibleFrames(1);
        if (normalAnimSet != null) {
            player.setAnimationSet(normalAnimSet);
            normalAnimSet = null;
        }
        // Restore normal sprite renderer
        if (normalRenderer != null) {
            player.setSpriteRenderer(normalRenderer);
            normalRenderer = null;
        }
        player.setShieldVisible(true);
        // Revert to zone music
        try {
            GameServices.audio().endMusicOverride(Sonic3kMusic.INVINCIBILITY.id);
        } catch (Exception e) {
            LOGGER.fine("Could not revert Super Sonic music: " + e.getMessage());
        }
        LOGGER.info("Super Sonic deactivated (S3K)");
    }

    /**
     * Begin a palette fade-out from an external trigger (e.g. AIZ intro Knuckles hit).
     * Matches ROM: {@code move.b #2,(Super_palette_status)} + {@code move.w #$1E,(Palette_frame)}.
     *
     * @param startFrame byte offset into PalCycle_SuperSonic to start the backwards fade from
     */
    public void beginPaletteRevert(int startFrame) {
        paletteState = 2;
        paletteFrame = startFrame;
        paletteTimer = 3;
    }

    @Override
    protected void updatePostRevertEffects() {
        if (paletteState == 2) {
            updatePaletteFade();
        }
    }

    private void updatePaletteFade() {
        if (paletteState == 0) return;

        paletteTimer--;
        if (paletteTimer >= 0) return;

        if (paletteState == 1) {
            paletteTimer = 1;
            int frameOffset = paletteFrame;
            paletteFrame += BYTES_PER_FRAME;

            if (paletteFrame >= FADE_COMPLETE_OFFSET) {
                paletteState = -1;
            }

            applyPaletteFrame(frameOffset);

        } else if (paletteState == 2) {
            paletteTimer = 3;
            int frameOffset = paletteFrame;
            paletteFrame -= BYTES_PER_FRAME;

            if (paletteFrame < 0) {
                paletteFrame = 0;
                paletteState = 0;
            }

            applyPaletteFrame(frameOffset);
        }
    }

    private void applyPaletteFrame(int frameOffset) {
        if (paletteData == null || paletteData.length == 0) return;
        if (frameOffset < 0 || frameOffset + BYTES_PER_FRAME > paletteData.length) return;

        PaletteTarget target = resolvePaletteTarget(SONIC_PALETTE_INDEX);
        if (target == null) return;

        Palette palette = target.palette();

        for (int i = 0; i < COLORS_PER_FRAME; i++) {
            palette.getColor(FIRST_COLOR_INDEX + i)
                    .fromSegaFormat(paletteData, frameOffset + i * 2);
        }

        GraphicsManager gfx = com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().graphics();
        if (gfx.isGlInitialized()) {
            gfx.cachePaletteTexture(palette, target.gpuLine());
        }
    }
}
