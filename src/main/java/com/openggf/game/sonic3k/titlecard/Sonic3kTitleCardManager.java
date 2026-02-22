package com.openggf.game.sonic3k.titlecard;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.sonic2.titlecard.TitleCardMappings;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.tools.KosinskiReader;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Manages the Sonic 3&K title card display.
 *
 * <p>Key differences from S2 title card:
 * <ul>
 *   <li>Art uses KosinskiM compression (not Nemesis)</li>
 *   <li>4 art blocks loaded to VRAM offsets $500, $510, $53D, $54D</li>
 *   <li>Staggered exit via priority values (not cascading states)</li>
 *   <li>Supports in-level mode (no background) for AIZ intro</li>
 * </ul>
 *
 * <p>Elements from ObjArray_TtlCard (disasm):
 * <table>
 *   <tr><th>Element</th><th>Start</th><th>Target</th><th>Direction</th><th>Exit Priority</th></tr>
 *   <tr><td>Red Banner</td><td>(96,-112)</td><td>(96,64)</td><td>Vertical</td><td>1</td></tr>
 *   <tr><td>Zone Name</td><td>(480,96)</td><td>(160,96)</td><td>Horizontal</td><td>3</td></tr>
 *   <tr><td>"ZONE"</td><td>(636,128)</td><td>(252,128)</td><td>Horizontal</td><td>5</td></tr>
 *   <tr><td>Act Number</td><td>(708,160)</td><td>(260,160)</td><td>Horizontal</td><td>7</td></tr>
 * </table>
 */
public class Sonic3kTitleCardManager implements TitleCardProvider {
    private static final Logger LOG = Logger.getLogger(Sonic3kTitleCardManager.class.getName());

    private static Sonic3kTitleCardManager instance;

    // Animation speeds (pixels per frame, matching disasm $10 / $20)
    private static final int SLIDE_SPEED_IN = 16;
    private static final int SLIDE_SPEED_OUT = 32;

    // Display hold duration (frames). ROM holds while loading; we use 90 frames.
    private static final int DISPLAY_HOLD_FRAMES = 90;

    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 224;

    // Pattern base ID for GPU caching (high to avoid conflicts)
    private static final int PATTERN_BASE = 0x50000;

    // VRAM base tile for title card art
    private static final int VRAM_BASE = Sonic3kConstants.VRAM_TITLE_CARD_BASE;

    // Max VRAM extent (zone art can extend to ~$57D)
    private static final int VRAM_ARRAY_SIZE = 0x100;  // 256 tiles covers $500-$5FF

    // ---- Element definitions ----
    // Each element: frameIndex, startX, startY, targetX, targetY, isVertical, exitPriority

    private static final int ELEM_BANNER = 0;
    private static final int ELEM_ZONE_NAME = 1;
    private static final int ELEM_ZONE_TEXT = 2;
    private static final int ELEM_ACT_NUM = 3;
    private static final int ELEMENT_COUNT = 4;

    // Element positions (screen coordinates, derived from disasm ObjArray_TtlCard)
    private static final int[] START_X = {96, 480, 636, 708};
    private static final int[] START_Y = {-112, 96, 128, 160};
    private static final int[] TARGET_X = {96, 160, 252, 260};
    private static final int[] TARGET_Y = {64, 96, 128, 160};
    private static final boolean[] IS_VERTICAL = {true, false, false, false};
    private static final int[] EXIT_PRIORITY = {1, 3, 5, 7};

    // ---- State ----
    private Sonic3kTitleCardState state = Sonic3kTitleCardState.COMPLETE;
    private int stateTimer;
    private int phaseCounter;  // Exit phase counter for staggered exit
    private boolean inLevelMode;  // No black background, control released immediately

    private int currentZone;
    private int currentAct;

    // Per-element animation state
    private final int[] elemX = new int[ELEMENT_COUNT];
    private final int[] elemY = new int[ELEMENT_COUNT];
    private final int[] elemFrame = new int[ELEMENT_COUNT];
    private final boolean[] elemAtTarget = new boolean[ELEMENT_COUNT];
    private final boolean[] elemExiting = new boolean[ELEMENT_COUNT];
    private final boolean[] elemExited = new boolean[ELEMENT_COUNT];
    private boolean actNumberVisible;  // False for single-act zones

    // Art data
    private Pattern[] combinedPatterns;
    private boolean artLoaded;
    private boolean artCached;
    private int lastLoadedZone = -1;
    private int lastLoadedAct = -1;

    private Sonic3kTitleCardManager() {}

    public static synchronized Sonic3kTitleCardManager getInstance() {
        if (instance == null) {
            instance = new Sonic3kTitleCardManager();
        }
        return instance;
    }

    // ---- TitleCardProvider interface ----

    @Override
    public void initialize(int zoneIndex, int actIndex) {
        initInternal(zoneIndex, actIndex, false);
    }

    /**
     * Initializes for in-level mode (no black background, control released immediately).
     * Used during AIZ intro when the level is already visible.
     */
    public void initializeInLevel(int zoneIndex, int actIndex) {
        initInternal(zoneIndex, actIndex, true);
    }

    private void initInternal(int zoneIndex, int actIndex, boolean inLevel) {
        this.currentZone = zoneIndex;
        this.currentAct = actIndex;
        this.inLevelMode = inLevel;
        this.state = Sonic3kTitleCardState.SLIDE_IN;
        this.stateTimer = 0;
        this.phaseCounter = 0;

        // Load art if needed
        if (!artLoaded || lastLoadedZone != zoneIndex || lastLoadedAct != actIndex) {
            loadAllArt(zoneIndex, actIndex);
        }

        // Set up elements
        actNumberVisible = !Sonic3kTitleCardMappings.isSingleActZone(zoneIndex);
        int zoneFrame = Sonic3kTitleCardMappings.getZoneFrame(zoneIndex);

        elemFrame[ELEM_BANNER] = Sonic3kTitleCardMappings.FRAME_BANNER;
        elemFrame[ELEM_ZONE_NAME] = zoneFrame;
        elemFrame[ELEM_ZONE_TEXT] = Sonic3kTitleCardMappings.FRAME_ZONE;
        elemFrame[ELEM_ACT_NUM] = Sonic3kTitleCardMappings.FRAME_ACT;

        for (int i = 0; i < ELEMENT_COUNT; i++) {
            elemX[i] = START_X[i];
            elemY[i] = START_Y[i];
            elemAtTarget[i] = false;
            elemExiting[i] = false;
            elemExited[i] = false;
        }

        artCached = false;
        LOG.info("S3K title card initialized: zone=" + zoneIndex + " act=" + actIndex
                + " inLevel=" + inLevel);
    }

    @Override
    public void update() {
        switch (state) {
            case SLIDE_IN -> updateSlideIn();
            case DISPLAY -> updateDisplay();
            case EXIT -> updateExit();
            case COMPLETE -> {}
        }
    }

    @Override
    public boolean shouldReleaseControl() {
        if (inLevelMode) {
            return true;  // Player already has control during in-level mode
        }
        return state == Sonic3kTitleCardState.EXIT
                || state == Sonic3kTitleCardState.COMPLETE;
    }

    @Override
    public boolean isOverlayActive() {
        if (inLevelMode) {
            return state != Sonic3kTitleCardState.COMPLETE;
        }
        return state == Sonic3kTitleCardState.EXIT;
    }

    @Override
    public boolean isComplete() {
        return state == Sonic3kTitleCardState.COMPLETE;
    }

    @Override
    public void draw() {
        ensureArtCached();

        GraphicsManager gm = GraphicsManager.getInstance();
        if (gm == null) return;

        // Black background during SLIDE_IN and DISPLAY (normal mode only)
        if (!inLevelMode &&
                (state == Sonic3kTitleCardState.SLIDE_IN || state == Sonic3kTitleCardState.DISPLAY)) {
            gm.registerCommand(new GLCommand(
                    GLCommand.CommandType.RECTI, -1,
                    0.0f, 0.0f, 0.0f,
                    0, 0, SCREEN_WIDTH, SCREEN_HEIGHT
            ));
        }

        gm.beginPatternBatch();

        // Draw banner
        renderElement(gm, ELEM_BANNER);

        // Draw zone name
        renderElement(gm, ELEM_ZONE_NAME);

        // Draw "ZONE" text
        renderElement(gm, ELEM_ZONE_TEXT);

        // Draw act number (if visible)
        if (actNumberVisible) {
            renderElement(gm, ELEM_ACT_NUM);
        }

        gm.flushPatternBatch();
    }

    @Override
    public void reset() {
        state = Sonic3kTitleCardState.COMPLETE;
        stateTimer = 0;
        phaseCounter = 0;
    }

    @Override
    public int getCurrentZone() {
        return currentZone;
    }

    @Override
    public int getCurrentAct() {
        return currentAct;
    }

    // ---- State machine ----

    private void updateSlideIn() {
        boolean allAtTarget = true;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            if (!actNumberVisible && i == ELEM_ACT_NUM) continue;
            if (!elemAtTarget[i]) {
                slideElement(i, true);
                if (!elemAtTarget[i]) allAtTarget = false;
            }
        }
        if (allAtTarget) {
            state = Sonic3kTitleCardState.DISPLAY;
            stateTimer = 0;
            LOG.fine("S3K title card: DISPLAY");
        }
    }

    private void updateDisplay() {
        stateTimer++;
        if (stateTimer >= DISPLAY_HOLD_FRAMES) {
            state = Sonic3kTitleCardState.EXIT;
            phaseCounter = 0;
            LOG.fine("S3K title card: EXIT");
        }
    }

    private void updateExit() {
        phaseCounter++;

        boolean allExited = true;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            if (!actNumberVisible && i == ELEM_ACT_NUM) continue;
            if (elemExited[i]) continue;

            // Start exiting when phase counter reaches element's priority
            if (phaseCounter >= EXIT_PRIORITY[i]) {
                elemExiting[i] = true;
            }

            if (elemExiting[i]) {
                slideElement(i, false);
            }

            if (!elemExited[i]) {
                allExited = false;
            }
        }

        if (allExited) {
            state = Sonic3kTitleCardState.COMPLETE;
            LOG.fine("S3K title card: COMPLETE");
        }
    }

    /**
     * Slides an element toward its target (slideIn=true) or back to start (slideIn=false).
     */
    private void slideElement(int idx, boolean slideIn) {
        int speed = slideIn ? SLIDE_SPEED_IN : SLIDE_SPEED_OUT;
        int goalX = slideIn ? TARGET_X[idx] : START_X[idx];
        int goalY = slideIn ? TARGET_Y[idx] : START_Y[idx];

        if (IS_VERTICAL[idx]) {
            int dir = Integer.compare(goalY, elemY[idx]);
            if (dir == 0) {
                if (slideIn) elemAtTarget[idx] = true;
                else elemExited[idx] = true;
                return;
            }
            elemY[idx] += dir * speed;
            if ((dir > 0 && elemY[idx] >= goalY) || (dir < 0 && elemY[idx] <= goalY)) {
                elemY[idx] = goalY;
                if (slideIn) elemAtTarget[idx] = true;
                else elemExited[idx] = true;
            }
        } else {
            int dir = Integer.compare(goalX, elemX[idx]);
            if (dir == 0) {
                if (slideIn) elemAtTarget[idx] = true;
                else elemExited[idx] = true;
                return;
            }
            elemX[idx] += dir * speed;
            if ((dir > 0 && elemX[idx] >= goalX) || (dir < 0 && elemX[idx] <= goalX)) {
                elemX[idx] = goalX;
                if (slideIn) elemAtTarget[idx] = true;
                else elemExited[idx] = true;
            }
        }
    }

    // ---- Art loading ----

    private void loadAllArt(int zoneIndex, int actIndex) {
        try {
            if (!GameServices.rom().isRomAvailable()) {
                LOG.warning("ROM not available for title card art");
                return;
            }
            Rom rom = GameServices.rom().getRom();

            combinedPatterns = new Pattern[VRAM_ARRAY_SIZE];
            Pattern empty = new Pattern();
            Arrays.fill(combinedPatterns, empty);

            // 1. Load RedAct art → VRAM $500 (index 0)
            loadKosmArt(rom, Sonic3kConstants.ART_KOSM_TITLE_CARD_RED_ACT_ADDR, 0);

            // 2. Load S3KZone text → VRAM $510 (index $10), overwrites part of RedAct
            loadKosmArt(rom, Sonic3kConstants.ART_KOSM_TITLE_CARD_S3K_ZONE_ADDR,
                    Sonic3kConstants.VRAM_TITLE_CARD_ZONE_TEXT - VRAM_BASE);

            // 3. Load act number art → VRAM $53D (index $3D)
            int actArtAddr = (actIndex == 0)
                    ? Sonic3kConstants.ART_KOSM_TITLE_CARD_NUM1_ADDR
                    : Sonic3kConstants.ART_KOSM_TITLE_CARD_NUM2_ADDR;
            loadKosmArt(rom, actArtAddr,
                    Sonic3kConstants.VRAM_TITLE_CARD_ACT_NUM - VRAM_BASE);

            // 4. Load zone-specific art → VRAM $54D (index $4D)
            // Zone 22 (HPZ) maps to art array index 13
            int artIndex = (zoneIndex == 22) ? 13 : zoneIndex;
            if (artIndex >= 0 && artIndex < Sonic3kConstants.TITLE_CARD_ZONE_ART_ADDRS.length) {
                loadKosmArt(rom, Sonic3kConstants.TITLE_CARD_ZONE_ART_ADDRS[artIndex],
                        Sonic3kConstants.VRAM_TITLE_CARD_ZONE_ART - VRAM_BASE);
            }

            artLoaded = true;
            artCached = false;
            lastLoadedZone = zoneIndex;
            lastLoadedAct = actIndex;
            LOG.info("S3K title card art loaded for zone " + zoneIndex);
        } catch (Exception e) {
            LOG.warning("Failed to load S3K title card art: " + e.getMessage());
            artLoaded = false;
        }
    }

    /**
     * Decompresses KosinskiM art and places patterns into the combined array.
     */
    private void loadKosmArt(Rom rom, int romAddr, int destIndex) throws IOException {
        byte[] header = rom.readBytes(romAddr, 2);
        int fullSize = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
        int inputSize = Math.min(Math.max(fullSize + 256, 0x10000), 0x40000);
        long romSize = rom.getSize();
        if (romAddr + inputSize > romSize) {
            inputSize = (int) (romSize - romAddr);
        }

        byte[] romData = rom.readBytes(romAddr, inputSize);
        byte[] decompressed = KosinskiReader.decompressModuled(romData, 0);

        int tileCount = decompressed.length / Pattern.PATTERN_SIZE_IN_ROM;
        for (int i = 0; i < tileCount; i++) {
            int idx = destIndex + i;
            if (idx >= 0 && idx < combinedPatterns.length) {
                byte[] tileData = Arrays.copyOfRange(decompressed,
                        i * Pattern.PATTERN_SIZE_IN_ROM,
                        (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
                Pattern pat = new Pattern();
                pat.fromSegaFormat(tileData);
                combinedPatterns[idx] = pat;
            }
        }
    }

    private void ensureArtCached() {
        if (artCached || !artLoaded || combinedPatterns == null) return;

        GraphicsManager gm = GraphicsManager.getInstance();
        if (gm == null) return;

        for (int i = 0; i < combinedPatterns.length; i++) {
            if (combinedPatterns[i] != null) {
                gm.cachePatternTexture(combinedPatterns[i], PATTERN_BASE + i);
            }
        }
        artCached = true;
    }

    // ---- Rendering ----

    private void renderElement(GraphicsManager gm, int elemIdx) {
        if (!artLoaded || combinedPatterns == null) return;
        if (elemExited[elemIdx]) return;

        int frameIndex = elemFrame[elemIdx];
        TitleCardMappings.SpritePiece[] pieces = Sonic3kTitleCardMappings.getFrame(frameIndex);
        int centerX = elemX[elemIdx];
        int centerY = elemY[elemIdx];

        // Render back-to-front: VDP sprites earlier in the mapping have higher
        // priority (appear in front). Drawing in reverse ensures the highest-priority
        // pieces (e.g. game name text on the banner) end up on top of lower-priority
        // pieces (e.g. the red fill blocks).
        for (int i = pieces.length - 1; i >= 0; i--) {
            renderSpritePiece(gm, pieces[i], centerX, centerY);
        }
    }

    private void renderSpritePiece(GraphicsManager gm, TitleCardMappings.SpritePiece piece,
                                   int originX, int originY) {
        int baseTile = piece.tileIndex();
        int arrIndex = baseTile - VRAM_BASE;
        if (arrIndex < 0) arrIndex = 0;

        int w = piece.widthTiles();
        int h = piece.heightTiles();

        for (int tx = 0; tx < w; tx++) {
            for (int ty = 0; ty < h; ty++) {
                int tileOff = tx * h + ty;  // Column-major VDP order
                int idx = arrIndex + tileOff;
                if (idx < 0 || idx >= VRAM_ARRAY_SIZE) continue;

                int patId = PATTERN_BASE + idx;
                int tileX = originX + piece.xOffset() + (tx * 8);
                int tileY = originY + piece.yOffset() + (ty * 8);

                if (piece.hFlip()) {
                    tileX = originX + piece.xOffset() + ((w - 1 - tx) * 8);
                }
                if (piece.vFlip()) {
                    tileY = originY + piece.yOffset() + ((h - 1 - ty) * 8);
                }

                int descBits = patId & 0x7FF;
                if (piece.hFlip()) descBits |= 0x800;
                if (piece.vFlip()) descBits |= 0x1000;
                descBits |= (piece.paletteIndex() & 0x3) << 13;
                PatternDesc desc = new PatternDesc(descBits);

                gm.renderPatternWithId(patId, desc, tileX, tileY);
            }
        }
    }
}
