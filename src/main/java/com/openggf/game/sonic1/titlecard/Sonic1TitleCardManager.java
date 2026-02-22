package com.openggf.game.sonic1.titlecard;

import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameServices;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.sonic2.titlecard.TitleCardElement;
import com.openggf.game.sonic2.titlecard.TitleCardMappings;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.tools.NemesisReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Manages the Sonic 1 title card display.
 *
 * <p>Sonic 1 title cards are simpler than Sonic 2: 4 sprite elements
 * (zone name, "ZONE", act number, oval decoration) slide in from off-screen
 * over a black background, hold for 60 frames, then slide out at double speed.
 * No background planes (blue/yellow/red) are used.
 *
 * <p>From the disassembly (Object 34 - "34 Title Cards.asm"):
 * <ul>
 *   <li>Routine 0 (Card_CheckSBZ3): Initialize 4 elements with ConData positions</li>
 *   <li>Routine 2 (Card_ChkPos): Slide to card_mainX at 16px/frame</li>
 *   <li>Routine 4 (Card_Wait): Wait 60 frames, then slide to card_finalX at 32px/frame</li>
 * </ul>
 *
 * <p>In the original game, the title card loop (Level_TtlCardLoop) runs until
 * the ACT element reaches its target, then level loading continues. The elements
 * continue running in the background during level load, eventually sliding out
 * and being deleted.
 *
 * <p>State machine:
 * <pre>
 * SLIDE_IN -> DISPLAY -> SLIDE_OUT -> COMPLETE
 * </pre>
 *
 * <p>Control is released at the start of SLIDE_OUT. The elements slide off-screen
 * as an overlay while the player can already move.
 */
public class Sonic1TitleCardManager implements TitleCardProvider {
    private static final Logger LOGGER = Logger.getLogger(Sonic1TitleCardManager.class.getName());

    private static Sonic1TitleCardManager instance;

    /** Display hold duration: 60 frames (~1 second at 60fps), matching obTimeFrame in disassembly */
    private static final int DISPLAY_HOLD_DURATION = 60;

    /** Pattern base ID for S1 title card art (high to avoid conflicts with S2's 0x40000) */
    private static final int PATTERN_BASE = 0x50000;

    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 224;

    /** Duration of PalFadeIn_Alt: palette fades from black to full color over 22 frames */
    private static final int PALETTE_FADE_FRAMES = 22;

    // Current state
    private Sonic1TitleCardState state = Sonic1TitleCardState.COMPLETE;
    private int stateTimer = 0;

    // Current zone/act
    private int currentZone = 0;
    private int currentAct = 0;

    // Elements
    private final List<TitleCardElement> elements = new ArrayList<>();

    // Art data
    private Pattern[] patterns;
    private boolean artLoaded = false;
    private boolean artCached = false;

    private Sonic1TitleCardManager() {}

    public static synchronized Sonic1TitleCardManager getInstance() {
        if (instance == null) {
            instance = new Sonic1TitleCardManager();
        }
        return instance;
    }

    @Override
    public void initialize(int zoneIndex, int actIndex) {
        this.currentZone = zoneIndex;
        this.currentAct = actIndex;
        this.state = Sonic1TitleCardState.SLIDE_IN;
        this.stateTimer = 0;

        if (!artLoaded) {
            loadArt();
        }

        // Force GPU re-upload on each initialize
        artCached = false;

        createElements();

        LOGGER.info("S1 title card initialized for zone " + zoneIndex + " act " + actIndex);
    }

    /**
     * Creates the 4 animated elements based on zone configuration data.
     *
     * <p>From Card_ItemData in the disassembly:
     * <ol>
     *   <li>Zone name: Y=$D0, routine=2, frame=zone-specific</li>
     *   <li>ZONE text: Y=$E4, routine=2, frame=6</li>
     *   <li>Act number: Y=$EA, routine=2, frame=7 (adjusted by act)</li>
     *   <li>Oval: Y=$E0, routine=2, frame=$A (10)</li>
     * </ol>
     */
    private void createElements() {
        elements.clear();

        int configIndex = Sonic1TitleCardMappings.getConfigIndex(currentZone, currentAct);
        int[] conData = Sonic1TitleCardMappings.getConData(configIndex);

        // conData layout: {zoneName_startX, zoneName_targetX,
        //                  zone_startX, zone_targetX,
        //                  act_startX, act_targetX,
        //                  oval_startX, oval_targetX}

        int zoneNameFrame = Sonic1TitleCardMappings.getZoneNameFrame(currentZone, currentAct);

        // Zone name element
        elements.add(new TitleCardElement(
                zoneNameFrame,
                conData[0], conData[1],
                Sonic1TitleCardMappings.Y_ZONE_NAME,
                0, 0x80));

        // "ZONE" text element
        elements.add(new TitleCardElement(
                Sonic1TitleCardMappings.FRAME_ZONE,
                conData[2], conData[3],
                Sonic1TitleCardMappings.Y_ZONE_TEXT,
                0, 0x40));

        // Act number element (hidden for Final Zone where startX == targetX)
        boolean hideAct = Sonic1TitleCardMappings.shouldHideActNumber(currentZone, currentAct);
        if (!hideAct) {
            int actFrame = Sonic1TitleCardMappings.getActFrame(currentAct);
            elements.add(new TitleCardElement(
                    actFrame,
                    conData[4], conData[5],
                    Sonic1TitleCardMappings.Y_ACT,
                    0, 0x30));
        }

        // Oval decoration element
        elements.add(new TitleCardElement(
                Sonic1TitleCardMappings.FRAME_OVAL,
                conData[6], conData[7],
                Sonic1TitleCardMappings.Y_OVAL,
                0, 0x40));
    }

    private void loadArt() {
        try {
            RomManager romManager = GameServices.rom();
            if (!romManager.isRomAvailable()) {
                LOGGER.warning("ROM not available for S1 title card art");
                return;
            }
            Rom rom = romManager.getRom();

            patterns = loadNemesisPatterns(rom,
                    Sonic1Constants.ART_NEM_TITLE_CARD_ADDR, "S1 TitleCard");

            if (patterns == null) {
                patterns = new Pattern[0];
            }

            LOGGER.info("Loaded " + patterns.length + " S1 title card patterns");
            artLoaded = true;

        } catch (Exception e) {
            LOGGER.warning("Failed to load S1 title card art: " + e.getMessage());
            artLoaded = false;
        }
    }

    private Pattern[] loadNemesisPatterns(Rom rom, int address, String name) {
        try {
            byte[] compressed = rom.readBytes(address, 8192);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                 ReadableByteChannel channel = Channels.newChannel(bais)) {
                byte[] decompressed = NemesisReader.decompress(channel);
                int patternCount = decompressed.length / Pattern.PATTERN_SIZE_IN_ROM;
                Pattern[] result = new Pattern[patternCount];
                for (int i = 0; i < patternCount; i++) {
                    result[i] = new Pattern();
                    byte[] subArray = Arrays.copyOfRange(decompressed,
                            i * Pattern.PATTERN_SIZE_IN_ROM,
                            (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
                    result[i].fromSegaFormat(subArray);
                }
                return result;
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load " + name + " patterns: " + e.getMessage());
            return new Pattern[0];
        }
    }

    private void ensureArtCached() {
        if (artCached || !artLoaded || patterns == null) {
            return;
        }

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        if (graphicsManager == null) {
            return;
        }

        for (int i = 0; i < patterns.length; i++) {
            if (patterns[i] != null) {
                graphicsManager.cachePatternTexture(patterns[i], PATTERN_BASE + i);
            }
        }
        LOGGER.info("Cached " + patterns.length + " S1 title card patterns to GPU");

        artCached = true;
    }

    @Override
    public void update() {
        stateTimer++;

        switch (state) {
            case SLIDE_IN -> updateSlideIn();
            case DISPLAY -> updateDisplay();
            case SLIDE_OUT -> updateSlideOut();
            case COMPLETE -> {}
        }
    }

    private void updateSlideIn() {
        for (TitleCardElement element : elements) {
            element.updateSlideIn();
        }

        if (elements.stream().allMatch(TitleCardElement::isAtTarget)) {
            state = Sonic1TitleCardState.DISPLAY;
            stateTimer = 0;
        }
    }

    private void updateDisplay() {
        if (stateTimer >= DISPLAY_HOLD_DURATION) {
            state = Sonic1TitleCardState.SLIDE_OUT;
            stateTimer = 0;
        }
    }

    /**
     * SLIDE_OUT has two phases:
     * <ol>
     *   <li>Fade phase (frames 0–21): PalFadeIn_Alt fades the black RECTI from
     *       opaque to transparent over 22 frames, revealing the level. Elements
     *       remain stationary.</li>
     *   <li>Exit phase (frame 22+): Elements slide back to their start positions
     *       at 32 px/frame, matching Card_ChkPos2 in the disassembly.</li>
     * </ol>
     *
     * <p>During this state, the level is visible behind the elements
     * (shouldReleaseControl() returns true, isOverlayActive() returns true).
     */
    private void updateSlideOut() {
        if (stateTimer == PALETTE_FADE_FRAMES) {
            // Fade just finished — kick off element exit movement
            for (TitleCardElement element : elements) {
                element.startExit();
            }
        }

        if (stateTimer > PALETTE_FADE_FRAMES) {
            for (TitleCardElement element : elements) {
                element.updateSlideOut();
            }

            if (elements.stream().allMatch(TitleCardElement::hasExited)) {
                state = Sonic1TitleCardState.COMPLETE;
                stateTimer = 0;
            }
        }
    }

    @Override
    public void draw() {
        ensureArtCached();

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        if (graphicsManager == null) {
            return;
        }

        // Black background that hides the level during SLIDE_IN and DISPLAY.
        // During SLIDE_OUT, alpha fades from 1.0 to 0.0 over PALETTE_FADE_FRAMES
        // to match PalFadeIn_Alt (22 frames) from the S1 disassembly.
        if (state != Sonic1TitleCardState.COMPLETE) {
            float bgAlpha = 1.0f;
            if (state == Sonic1TitleCardState.SLIDE_OUT) {
                float progress = Math.min(1.0f, stateTimer / (float) PALETTE_FADE_FRAMES);
                bgAlpha = 1.0f - progress;
            }

            if (bgAlpha > 0f) {
                graphicsManager.registerCommand(new GLCommand(
                        GLCommand.CommandType.RECTI, -1,
                        GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                        0.0f, 0.0f, 0.0f, bgAlpha,
                        0, 0, SCREEN_WIDTH, SCREEN_HEIGHT));
            }
        }

        // Render sprite elements in reverse order: VDP sprite priority means earlier
        // elements (lower index) have HIGHER priority and render ON TOP. Since OpenGL
        // uses painter's algorithm (last draw wins), we draw the last element first
        // (oval behind) and the first element last (zone name on top).
        graphicsManager.beginPatternBatch();

        for (int i = elements.size() - 1; i >= 0; i--) {
            TitleCardElement element = elements.get(i);
            if (element.isVisible()) {
                renderElement(graphicsManager, element);
            }
        }

        graphicsManager.flushPatternBatch();
    }

    private void renderElement(GraphicsManager graphicsManager, TitleCardElement element) {
        if (!artLoaded || patterns == null) {
            return;
        }

        int frameIndex = element.getFrameIndex();
        if (frameIndex < 0) {
            return;
        }

        TitleCardMappings.SpritePiece[] pieces = Sonic1TitleCardMappings.getFrame(frameIndex);
        int centerX = element.getCurrentX();
        int centerY = element.getY();

        // Render pieces in reverse order so that earlier pieces (higher VDP sprite
        // priority) are drawn last and appear on top. This matters for the oval
        // decoration where border pieces (indices 0-7) must render over fill
        // pieces (indices 8-12).
        for (int i = pieces.length - 1; i >= 0; i--) {
            renderSpritePiece(graphicsManager, pieces[i], centerX, centerY);
        }
    }

    /**
     * Renders a single sprite piece using column-major tile ordering.
     *
     * <p>Sonic 1 tile indices are 0-based (direct index into the art pattern array),
     * unlike Sonic 2 where tile indices include a VRAM base offset ($580+).
     */
    private void renderSpritePiece(GraphicsManager graphicsManager,
                                    TitleCardMappings.SpritePiece piece,
                                    int originX, int originY) {
        int baseTileIndex = piece.tileIndex();
        int widthTiles = piece.widthTiles();
        int heightTiles = piece.heightTiles();

        for (int tx = 0; tx < widthTiles; tx++) {
            for (int ty = 0; ty < heightTiles; ty++) {
                // Column-major tile ordering (same as VDP)
                int tileOffset = tx * heightTiles + ty;
                int arrayIndex = baseTileIndex + tileOffset;

                if (arrayIndex < 0 || arrayIndex >= patterns.length) {
                    continue;
                }

                int patternId = PATTERN_BASE + arrayIndex;

                // Calculate screen position
                int tileX = originX + piece.xOffset() + (tx * 8);
                int tileY = originY + piece.yOffset() + (ty * 8);

                // Handle flipping - swap column/row order
                if (piece.hFlip()) {
                    tileX = originX + piece.xOffset() + ((widthTiles - 1 - tx) * 8);
                }
                if (piece.vFlip()) {
                    tileY = originY + piece.yOffset() + ((heightTiles - 1 - ty) * 8);
                }

                // Build PatternDesc with flip flags and palette
                int descIndex = patternId & 0x7FF;
                if (piece.hFlip()) {
                    descIndex |= 0x800;
                }
                if (piece.vFlip()) {
                    descIndex |= 0x1000;
                }
                descIndex |= (piece.paletteIndex() & 0x3) << 13;
                PatternDesc desc = new PatternDesc(descIndex);

                graphicsManager.renderPatternWithId(patternId, desc, tileX, tileY);
            }
        }
    }

    /**
     * Returns true if player control should be released.
     * Control is released at the start of SLIDE_OUT, matching S1's behavior
     * where the level becomes playable while title card elements slide off-screen.
     */
    @Override
    public boolean shouldReleaseControl() {
        return state == Sonic1TitleCardState.SLIDE_OUT ||
               state == Sonic1TitleCardState.COMPLETE;
    }

    /**
     * Returns true if the title card overlay should still be drawn.
     * The overlay remains visible during SLIDE_OUT as elements slide off-screen
     * over the visible level.
     */
    @Override
    public boolean isOverlayActive() {
        return state == Sonic1TitleCardState.SLIDE_OUT;
    }

    @Override
    public boolean isComplete() {
        return state == Sonic1TitleCardState.COMPLETE;
    }

    /**
     * S1 ROM: title card is a blocking routine (TitleCard in sonic.asm).
     * Player physics does NOT run until the title card completes.
     */
    @Override
    public boolean shouldRunPlayerPhysics() {
        return false;
    }

    @Override
    public void reset() {
        state = Sonic1TitleCardState.COMPLETE;
        stateTimer = 0;
        elements.clear();
    }

    @Override
    public int getCurrentZone() {
        return currentZone;
    }

    @Override
    public int getCurrentAct() {
        return currentAct;
    }

    public Sonic1TitleCardState getState() {
        return state;
    }
}
