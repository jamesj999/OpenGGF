package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.PhysicsProfile;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kConstants;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.Palette;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationSet;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;
import uk.co.jamesj999.sonic.sprites.playable.SuperStateController;

import java.util.logging.Logger;

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
        return PhysicsProfile.SONIC_3K_SUPER_SONIC;
    }

    @Override
    protected PhysicsProfile getNormalProfile() {
        return PhysicsProfile.SONIC_2_SONIC;
    }

    @Override
    protected void onTransformationStarted() {
        paletteState = 1;
        paletteFrame = 0;
        paletteTimer = 1;
        transformFramesRemaining = 30;
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
        player.setInvincibleFrames(0);
        if (superAnimSet != null) {
            normalAnimSet = player.getAnimationSet();
            player.setAnimationSet(superAnimSet);
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
        player.setShieldVisible(true);
        LOGGER.info("Super Sonic deactivated (S3K)");
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

        Level level = LevelManager.getInstance().getCurrentLevel();
        if (level == null) return;

        Palette palette = level.getPalette(SONIC_PALETTE_INDEX);
        if (palette == null) return;

        for (int i = 0; i < COLORS_PER_FRAME; i++) {
            palette.getColor(FIRST_COLOR_INDEX + i)
                    .fromSegaFormat(paletteData, frameOffset + i * 2);
        }

        GraphicsManager gfx = GraphicsManager.getInstance();
        if (gfx.isGlInitialized()) {
            gfx.cachePaletteTexture(palette, SONIC_PALETTE_INDEX);
        }
    }
}
