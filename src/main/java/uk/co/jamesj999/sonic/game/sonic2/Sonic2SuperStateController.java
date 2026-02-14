package uk.co.jamesj999.sonic.game.sonic2;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.PhysicsProfile;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;
import uk.co.jamesj999.sonic.game.sonic2.objects.SuperSonicStarsObjectInstance;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.Palette;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationSet;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;
import uk.co.jamesj999.sonic.sprites.playable.SuperStateController;

import java.util.logging.Logger;

/**
 * Sonic 2 implementation of Super Sonic state management.
 *
 * <p>Handles transformation SFX, Super Sonic music, palette fade/cycling,
 * and ring drain at the S2-specific rate of 1 ring per second (60 frames).
 *
 * <p>Palette cycling uses ROM data from {@code CyclingPal_SSTransformation} (0x2246).
 * The 128-byte table contains 16 frames of 8 bytes each (4 Mega Drive colors per frame).
 * Colors are written to palette line 1 (index 0), color indices 2-5 (Sonic's blue shades).
 *
 * <p>ROM reference: {@code PalCycle_SuperSonic} in s2.asm (lines 3115-3229).
 *
 * <p>Palette states:
 * <ul>
 *   <li>0 = off (normal palette)</li>
 *   <li>1 = fading in (transformation) - frames 0x00-0x28, timer 3</li>
 *   <li>-1 = cycling (active Super Sonic) - frames 0x30-0x78, timer 7</li>
 *   <li>2 = fading out (reverting to normal) - frames decrement, timer 3</li>
 * </ul>
 */
public class Sonic2SuperStateController extends SuperStateController {
    private static final Logger LOGGER = Logger.getLogger(Sonic2SuperStateController.class.getName());

    /** Palette fade state: 0=off, 1=fading in, -1=cycling, 2=fading out. */
    private int paletteState;
    /** Current palette frame byte offset into the transformation data (increments by 8). */
    private int paletteFrame;
    /** Countdown timer between palette frame advances. */
    private int paletteTimer;
    /** Frames remaining in the transformation animation (30 frame timer). */
    private int transformFramesRemaining;

    /**
     * Raw ROM palette data from CyclingPal_SSTransformation.
     * 128 bytes: 16 frames x 8 bytes (4 MD colors x 2 bytes each).
     * Frames 0-5 (offsets 0x00-0x28): fade from normal to super.
     * Frames 6-15 (offsets 0x30-0x78): cycling animation loop.
     */
    private byte[] paletteData;

    /** Super Sonic animation set (loaded from ROM). */
    private SpriteAnimationSet superAnimSet;
    /** Normal animation set (saved on activation, restored on revert). */
    private SpriteAnimationSet normalAnimSet;
    /** Super Sonic stars sparkle effect object (Obj7E). */
    private SuperSonicStarsObjectInstance starsObject;

    /** Palette line index where Sonic's colors reside (line 1 = index 0). */
    private static final int SONIC_PALETTE_INDEX = 0;
    /** First color index to write (Normal_palette+4 = 2 words from start). */
    private static final int FIRST_COLOR_INDEX = 2;
    /** Number of colors written per frame. */
    private static final int COLORS_PER_FRAME = 4;
    /** Byte offset at which fade-in is complete (frame 6). */
    private static final int FADE_COMPLETE_OFFSET = 0x30;
    /** Byte offset at which cycling wraps (frame 16, with fixBugs: inclusive). */
    private static final int CYCLE_WRAP_OFFSET = 0x78;

    public Sonic2SuperStateController(AbstractPlayableSprite player) {
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
        int addr = Sonic2Constants.CYCLING_PAL_SS_TRANSFORMATION_ADDR;
        int len = Sonic2Constants.CYCLING_PAL_SS_TRANSFORMATION_LEN;
        if (addr == 0 || addr + len > reader.size()) {
            LOGGER.warning("Super Sonic palette data not available at ROM address 0x"
                    + Integer.toHexString(addr));
            return;
        }
        paletteData = reader.slice(addr, len);
        LOGGER.fine("Loaded Super Sonic palette data: " + len + " bytes from ROM 0x"
                + Integer.toHexString(addr));

        // Load Super Sonic animation set
        Sonic2PlayerArt playerArt = new Sonic2PlayerArt(reader);
        superAnimSet = playerArt.loadSuperSonicAnimationSet();
        if (superAnimSet != null) {
            LOGGER.fine("Loaded Super Sonic animation set");
        }
    }

    @Override
    protected int getRingDrainInterval() {
        return Sonic2Constants.SUPER_SONIC_RING_DRAIN_INTERVAL;
    }

    @Override
    protected int getMinRingsToTransform() {
        return Sonic2Constants.SUPER_SONIC_MIN_RINGS;
    }

    @Override
    protected PhysicsProfile getSuperProfile() {
        return PhysicsProfile.SONIC_2_SUPER_SONIC;
    }

    @Override
    protected PhysicsProfile getNormalProfile() {
        return PhysicsProfile.SONIC_2_SONIC;
    }

    @Override
    protected void onTransformationStarted() {
        paletteState = 1;
        paletteFrame = 0;
        paletteTimer = 3;
        transformFramesRemaining = 30;
        // Play transformation SFX
        try {
            AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_SUPER_TRANSFORM);
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
        paletteTimer = 7;
        // Play Super Sonic music (overrides zone music)
        try {
            AudioManager.getInstance().playMusic(Sonic2AudioConstants.MUS_SUPER_SONIC);
        } catch (Exception e) {
            LOGGER.fine("Could not play Super Sonic music: " + e.getMessage());
        }
        // Clear invincibility timer - Super Sonic has inherent invincibility
        player.setInvincibleFrames(0);
        // Swap to Super Sonic animation set
        if (superAnimSet != null) {
            normalAnimSet = player.getAnimationSet();
            player.setAnimationSet(superAnimSet);
        }
        // Hide shield - Super Sonic doesn't display shields
        player.setShieldVisible(false);
        // Spawn Super Sonic stars sparkle effect (Obj7E)
        if (starsObject == null) {
            starsObject = new SuperSonicStarsObjectInstance(player);
            LevelManager.getInstance().getObjectManager().addDynamicObject(starsObject);
        }
        LOGGER.info("Super Sonic activated (S2)");
    }

    @Override
    protected void updateSuperPalette() {
        // ROM: PalCycle_SuperSonic_normal (loc_21E6)
        if (paletteState != -1) return;

        paletteTimer--;
        if (paletteTimer >= 0) return;

        paletteTimer = 7;

        // Read current frame offset, then advance
        int frameOffset = paletteFrame;
        paletteFrame += 8;

        // Wrap: when paletteFrame exceeds 0x78, reset to 0x30
        // ROM (fixBugs): cmpi.w #$78 / bls.s (less or equal -> branch if <= 0x78)
        // So wrap occurs when paletteFrame > 0x78
        if (paletteFrame > CYCLE_WRAP_OFFSET) {
            paletteFrame = FADE_COMPLETE_OFFSET;
        }

        applyPaletteFrame(frameOffset);
    }

    @Override
    protected void onRevertStarted() {
        paletteState = 2;
        // ROM: move.w #$28,(Palette_frame).w on revert
        paletteFrame = 0x28;
        paletteTimer = 3;
        // 1-frame invincibility grace period to prevent instant damage on revert
        player.setInvincibleFrames(1);
        // Restore normal animation set
        if (normalAnimSet != null) {
            player.setAnimationSet(normalAnimSet);
            normalAnimSet = null;
        }
        // Re-show shield
        player.setShieldVisible(true);
        // Destroy Super Sonic stars
        if (starsObject != null) {
            starsObject.destroy();
            starsObject = null;
        }
        // Revert to zone music
        try {
            AudioManager.getInstance().endMusicOverride(Sonic2AudioConstants.MUS_SUPER_SONIC);
        } catch (Exception e) {
            LOGGER.fine("Could not revert Super Sonic music: " + e.getMessage());
        }
        LOGGER.info("Super Sonic deactivated (S2)");
    }

    /**
     * Advances the palette fade animation (used during transformation and revert).
     *
     * <p>ROM reference: {@code PalCycle_SuperSonic} fade-in (state 1) and
     * {@code PalCycle_SuperSonic_revert} (state 2).
     *
     * <p>Fade-in (state 1): reads frame, then advances palette frame forward every 3 frames.
     * When frame reaches 0x30, fade-in is complete (state transitions to -1).
     *
     * <p>Fade-out (state 2): reads frame, then retreats palette frame backward every 3 frames.
     * When frame underflows below 0, palette cycling stops (state transitions to 0).
     */
    private void updatePaletteFade() {
        if (paletteState == 0) return;

        paletteTimer--;
        if (paletteTimer >= 0) return;

        paletteTimer = 3;

        if (paletteState == 1) {
            // ROM: move.w (Palette_frame).w,d0 / addq.w #8,(Palette_frame).w
            // Read current frame before incrementing
            int frameOffset = paletteFrame;
            paletteFrame += 8;

            // ROM: cmpi.w #$30,(Palette_frame).w / blo.s +
            // When paletteFrame reaches 0x30, fade-in is complete
            if (paletteFrame >= FADE_COMPLETE_OFFSET) {
                paletteState = -1;
            }

            applyPaletteFrame(frameOffset);

        } else if (paletteState == 2) {
            // ROM: move.w (Palette_frame).w,d0 / subq.w #8,(Palette_frame).w
            // Read current frame before decrementing
            int frameOffset = paletteFrame;
            paletteFrame -= 8;

            // ROM (fixBugs): bcc.s + (branch if no borrow)
            // If paletteFrame went negative, stop cycling
            if (paletteFrame < 0) {
                paletteFrame = 0;
                paletteState = 0;
            }

            applyPaletteFrame(frameOffset);
        }
    }

    /**
     * Applies a single palette frame from the ROM data to Sonic's palette.
     *
     * <p>ROM: Writes 8 bytes (4 MD colors) from {@code CyclingPal_SSTransformation + frameOffset}
     * to {@code Normal_palette+4} (palette line 1, colors 2-5).
     *
     * @param frameOffset byte offset into paletteData (must be 8-aligned, 0x00-0x78)
     */
    private void applyPaletteFrame(int frameOffset) {
        if (paletteData == null || paletteData.length == 0) return;
        if (frameOffset < 0 || frameOffset + 8 > paletteData.length) return;

        Level level = LevelManager.getInstance().getCurrentLevel();
        if (level == null) return;

        Palette palette = level.getPalette(SONIC_PALETTE_INDEX);
        if (palette == null) return;

        // Write 4 colors at indices 2, 3, 4, 5 from ROM data
        // ROM: move.l (a0,d0.w),(a1)+ / move.l 4(a0,d0.w),(a1)
        // Each color is 2 bytes in Mega Drive format (0BBB0GGG0RRR0)
        for (int i = 0; i < COLORS_PER_FRAME; i++) {
            palette.getColor(FIRST_COLOR_INDEX + i)
                    .fromSegaFormat(paletteData, frameOffset + i * 2);
        }

        // Mark palette dirty for GPU re-upload
        GraphicsManager gfx = GraphicsManager.getInstance();
        if (gfx.isGlInitialized()) {
            gfx.cachePaletteTexture(palette, SONIC_PALETTE_INDEX);
        }
    }

    @Override
    protected void updatePostRevertEffects() {
        // Continue palette fade-out after state returns to NORMAL.
        // Only handle state 2 (fading out) - states 1 and -1 are handled
        // by updateTransformationAnimation() and updateSuperPalette() respectively.
        if (paletteState == 2) {
            updatePaletteFade();
        }
    }

    /** Returns the current palette state (0=off, 1=fading in, -1=cycling, 2=fading out). */
    public int getPaletteState() {
        return paletteState;
    }

    /** Returns the current palette frame byte offset. */
    public int getPaletteFrame() {
        return paletteFrame;
    }
}
