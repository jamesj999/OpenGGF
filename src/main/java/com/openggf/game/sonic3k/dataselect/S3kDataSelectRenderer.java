package com.openggf.game.sonic3k.dataselect;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;

import java.util.List;
import java.util.Arrays;

import static org.lwjgl.opengl.GL11.glClearColor;

interface S3kDataSelectAssetSource {
    void loadData() throws java.io.IOException;
    boolean isLoaded();
    int getMusicId();
    int[] getLayoutWords();
    default int[] getPlaneALayoutWords() {
        return getLayoutWords();
    }
    int[] getNewLayoutWords();
    int[][] getStaticLayouts();
    int[] getMenuBackgroundLayoutWords();
    Pattern[] getMenuBackgroundPatterns();
    Pattern[] getMiscPatterns();
    Pattern[] getExtraPatterns();
    default Pattern[] getTextPatterns() {
        return new Pattern[0];
    }
    default Pattern[] getSlotIconPatterns(int iconIndex) {
        return new Pattern[0];
    }
    Pattern[] getSkZonePatterns();
    Pattern[] getPortraitPatterns();
    Pattern[] getS3ZonePatterns();
    byte[] getMenuBackgroundPaletteBytes();
    byte[] getCharacterPaletteBytes();
    byte[] getEmeraldPaletteBytes();
    byte[][] getFinishCardPalettes();
    byte[][] getZoneCardPalettes();
    byte[] getS3ZoneCard8PaletteBytes();
    List<SpriteMappingFrame> getSaveScreenMappings();
    S3kSaveScreenLayoutObjects getSaveScreenLayoutObjects();
}

public class S3kDataSelectRenderer {
    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 224;
    private static final int SCREEN_TILE_WIDTH = 40;
    private static final int SCREEN_TILE_HEIGHT = 28;
    private static final int PLANE_WIDTH_TILES = 128;
    private static final int PLANE_HEIGHT_TILES = 32;
    private static final int CARD_TILE_WIDTH = 10;
    private static final int CARD_TILE_HEIGHT = 7;
    private static final int SCREEN_SPACE_WORLD_ORIGIN = 128;
    private static final int SAVE_SLOT_PLANE_OFFSET = 0x021A;
    private static final int SAVE_SLOT_PLANE_STEP = 0x001A;
    private static final int SAVE_SLOT_LABEL_OFFSET = 0x0A20;
    private static final int SAVE_SLOT_LIVES_OFFSET = 0x1220;
    private static final int SAVE_SLOT_LABEL_PREFIX_OFFSET = 0x0A1E;
    private static final int NO_SAVE_TEXT_OFFSET = 0x0C06;
    private static final int SAVE_TEXT_OFFSET = 0x0C0C;
    private static final int DELETE_TEXT_OFFSET = 0x0CEC;
    private static final int SELECTED_ICON_DMA_TILE_COUNT = (0x460 * 2) / Pattern.PATTERN_SIZE_IN_ROM;

    private static final int DATA_SELECT_PATTERN_BASE = 0x50000;
    private static final int TILE_WORD_FLAGS = 0xA000;
    private static final int SAVE_TEXT_WORD_BASE = Sonic3kConstants.ARTTILE_SAVE_TEXT - 0x10 + TILE_WORD_FLAGS;
    private static final int SAVE_SCREEN_OBJECT_BASE_DESC = 0x8000;

    private final PatternDesc reusableDesc = new PatternDesc();

    private boolean cached;

    public void draw(S3kDataSelectAssetSource assets,
                     S3kSaveScreenObjectState objectState) {
        draw(GameServices.graphics(), assets, objectState);
    }

    void draw(GraphicsManager graphics,
              S3kDataSelectAssetSource assets,
              S3kSaveScreenObjectState objectState) {
        if (assets == null || objectState == null || !assets.isLoaded()) {
            return;
        }

        if (graphics == null || graphics.isHeadlessMode()) {
            return;
        }

        ensureCached(graphics, assets);
        cacheSelectedSlotIcon(graphics, assets, objectState.selectedSlotIcon());
        int cameraX = objectState.selectorState().cameraX();
        boolean batchingEnabled = graphics.isBatchingEnabled();
        boolean instancedBatchingEnabled = graphics.isInstancedBatchingEnabled();
        graphics.setBatchingEnabled(false);
        graphics.setInstancedBatchingEnabled(false);
        try {
            drawLayer(graphics, () -> renderScreenTilemap(graphics, assets.getMenuBackgroundLayoutWords(),
                    SCREEN_TILE_WIDTH, SCREEN_TILE_HEIGHT));
            drawLayer(graphics, () -> renderPlaneABase(graphics, assets.getPlaneALayoutWords(), cameraX, false));
            drawLayer(graphics, () -> renderCardsPlaneLayer(graphics, assets, objectState, cameraX, false));
            drawLayer(graphics, () -> renderStaticPlaneTextOverlays(graphics, cameraX, false));
            drawLayer(graphics, () -> renderTitle(graphics, assets, objectState, cameraX));
            drawLayer(graphics, () -> renderCardsSpriteBaseLayer(graphics, assets, objectState, cameraX));
            drawLayer(graphics, () -> renderPlaneABase(graphics, assets.getPlaneALayoutWords(), cameraX, true));
            drawLayer(graphics, () -> renderCardsPlaneLayer(graphics, assets, objectState, cameraX, true));
            drawLayer(graphics, () -> renderStaticPlaneTextOverlays(graphics, cameraX, true));
            drawLayer(graphics, () -> renderCardsSpriteEmeraldLayer(graphics, assets, objectState, cameraX));
            drawLayer(graphics, () -> renderSelectedSlotIcon(graphics, assets, objectState.selectedSlotIcon(), cameraX));
            drawLayer(graphics, () -> renderCardsSpriteOverlayLayer(graphics, assets, objectState, cameraX));
            drawLayer(graphics, () -> renderDelete(graphics, assets, objectState, cameraX));
            drawLayer(graphics, () -> renderSelector(graphics, assets, objectState, cameraX));
        } finally {
            graphics.setBatchingEnabled(batchingEnabled);
            graphics.setInstancedBatchingEnabled(instancedBatchingEnabled);
        }
    }

    public void setClearColor(S3kDataSelectAssetSource assets) {
        Palette backdrop = paletteFromBytes(assets != null ? assets.getMenuBackgroundPaletteBytes() : null, 0);
        if (backdrop == null) {
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            return;
        }
        Palette.Color color = backdrop.getColor(0);
        glClearColor(color.rFloat(), color.gFloat(), color.bFloat(), 1.0f);
    }

    public void reset() {
        cached = false;
    }

    private void ensureCached(GraphicsManager graphics, S3kDataSelectAssetSource assets) {
        if (cached) {
            return;
        }

        graphics.cachePatternTexture(new Pattern(), DATA_SELECT_PATTERN_BASE);
        cachePatterns(graphics, assets.getMenuBackgroundPatterns(),
                DATA_SELECT_PATTERN_BASE + Sonic3kConstants.ARTTILE_S3_MENU_BG);
        cachePatterns(graphics, assets.getMiscPatterns(),
                DATA_SELECT_PATTERN_BASE + Sonic3kConstants.ARTTILE_SAVE_MISC);
        cachePatterns(graphics, assets.getExtraPatterns(),
                DATA_SELECT_PATTERN_BASE + Sonic3kConstants.ARTTILE_SAVE_EXTRA);
        cachePatterns(graphics, assets.getTextPatterns(),
                DATA_SELECT_PATTERN_BASE + Sonic3kConstants.ARTTILE_SAVE_TEXT);

        cachePalette(graphics, assets.getMenuBackgroundPaletteBytes(), 0);
        cacheCharacterAndEmeraldPalettes(graphics,
                assets.getCharacterPaletteBytes(),
                assets.getEmeraldPaletteBytes());

        byte[][] finishPalettes = assets.getFinishCardPalettes();
        if (finishPalettes.length > 0) {
            cachePalette(graphics, finishPalettes[0], 3);
        }

        cached = true;
    }

    private void drawLayer(GraphicsManager graphics, Runnable layerRenderer) {
        graphics.beginPatternBatch();
        layerRenderer.run();
        graphics.flushPatternBatch();
    }

    private void cacheSelectedSlotIcon(GraphicsManager graphics,
                                       S3kDataSelectAssetSource assets,
                                       S3kSaveScreenObjectState.SelectedSlotIcon selectedSlotIcon) {
        if (selectedSlotIcon == null) {
            return;
        }
        Pattern[] patterns = assets.getSlotIconPatterns(selectedSlotIcon.iconIndex());
        if (patterns.length == 0) {
            return;
        }
        int patternBase = DATA_SELECT_PATTERN_BASE + Sonic3kConstants.ARTTILE_SAVE_MISC
                + (selectedSlotIcon.finishCard() ? 0x27D : 0x31B);
        cachePatterns(graphics,
                java.util.Arrays.copyOf(patterns, Math.min(patterns.length, SELECTED_ICON_DMA_TILE_COUNT)),
                patternBase);
        byte[] paletteBytes = selectedSlotIcon.finishCard()
                ? paletteAt(assets.getFinishCardPalettes(), selectedSlotIcon.paletteIndex())
                : selectedIconZonePaletteBytes(assets, selectedSlotIcon.paletteIndex());
        cachePalette(graphics, paletteBytes, 3);
    }

    private byte[] selectedIconZonePaletteBytes(S3kDataSelectAssetSource assets, int paletteIndex) {
        if (paletteIndex == 7) {
            byte[] special = assets.getS3ZoneCard8PaletteBytes();
            if (special != null && special.length > 0) {
                return special;
            }
        }
        return paletteAt(assets.getZoneCardPalettes(), paletteIndex);
    }

    private void renderCardsPlaneLayer(GraphicsManager graphics,
                                       S3kDataSelectAssetSource assets,
                                       S3kSaveScreenObjectState objectState,
                                       int cameraX,
                                       boolean highPriority) {
        S3kSaveScreenLayoutObjects layoutObjects = objectState.layoutObjects();
        S3kSaveScreenObjectState.VisualState visualState = objectState.visualState();
        List<S3kSaveScreenObjectState.SlotVisualState> slotStates = visualState.slotStates();
        for (int slotIndex = 0; slotIndex < slotStates.size(); slotIndex++) {
            S3kSaveScreenObjectState.SlotVisualState slotState = slotStates.get(slotIndex);
            int[] words = selectSlotLayout(assets, visualState, slotState);
            renderPlaneOverlayTilemap(graphics, words, CARD_TILE_WIDTH, CARD_TILE_HEIGHT,
                    SAVE_SLOT_PLANE_OFFSET + (slotIndex * SAVE_SLOT_PLANE_STEP), cameraX, highPriority);
            renderSlotMetadata(graphics, slotState, slotIndex, cameraX, highPriority);
        }
    }

    private void renderCardsSpriteBaseLayer(GraphicsManager graphics,
                                            S3kDataSelectAssetSource assets,
                                            S3kSaveScreenObjectState objectState,
                                            int cameraX) {
        S3kSaveScreenLayoutObjects layoutObjects = objectState.layoutObjects();
        S3kSaveScreenObjectState.VisualState visualState = objectState.visualState();
        renderNoSaveBase(graphics, assets, layoutObjects, visualState, cameraX);

        List<S3kSaveScreenObjectState.SlotVisualState> slotStates = visualState.slotStates();
        for (int slotIndex = 0; slotIndex < slotStates.size(); slotIndex++) {
            S3kSaveScreenObjectState.SlotVisualState slotState = slotStates.get(slotIndex);
            S3kSaveScreenLayoutObjects.SaveSlotObject slotObject = layoutObjects.slots().get(slotIndex);
            renderSlotBase(graphics, assets, slotObject, slotState, cameraX);
        }
    }

    private void renderCardsSpriteOverlayLayer(GraphicsManager graphics,
                                               S3kDataSelectAssetSource assets,
                                               S3kSaveScreenObjectState objectState,
                                               int cameraX) {
        S3kSaveScreenLayoutObjects layoutObjects = objectState.layoutObjects();
        S3kSaveScreenObjectState.VisualState visualState = objectState.visualState();
        renderNoSaveOverlay(graphics, assets, layoutObjects, visualState, cameraX);

        List<S3kSaveScreenObjectState.SlotVisualState> slotStates = visualState.slotStates();
        for (int slotIndex = 0; slotIndex < slotStates.size(); slotIndex++) {
            S3kSaveScreenObjectState.SlotVisualState slotState = slotStates.get(slotIndex);
            S3kSaveScreenLayoutObjects.SaveSlotObject slotObject = layoutObjects.slots().get(slotIndex);
            renderSlotOverlay(graphics, assets, slotObject, slotState, cameraX);
        }
    }

    private void renderCardsSpriteEmeraldLayer(GraphicsManager graphics,
                                               S3kDataSelectAssetSource assets,
                                               S3kSaveScreenObjectState objectState,
                                               int cameraX) {
        S3kSaveScreenLayoutObjects layoutObjects = objectState.layoutObjects();
        List<S3kSaveScreenObjectState.SlotVisualState> slotStates = objectState.visualState().slotStates();
        for (int slotIndex = 0; slotIndex < slotStates.size(); slotIndex++) {
            S3kSaveScreenObjectState.SlotVisualState slotState = slotStates.get(slotIndex);
            S3kSaveScreenLayoutObjects.SaveSlotObject slotObject = layoutObjects.slots().get(slotIndex);
            int slotWorldX = slotObject.worldX() - cameraX;
            int slotWorldY = slotObject.worldY();
            for (int emeraldFrame : slotState.emeraldMappingFrames()) {
                renderObjectFrame(graphics, assets, emeraldFrame, slotWorldX, slotWorldY,
                        SAVE_SCREEN_OBJECT_BASE_DESC);
            }
        }
    }

    private void renderTitle(GraphicsManager graphics,
                             S3kDataSelectAssetSource assets,
                             S3kSaveScreenObjectState objectState,
                             int cameraX) {
        S3kSaveScreenLayoutObjects layoutObjects = objectState.layoutObjects();
        renderObjectFrame(graphics, assets, layoutObjects.titleText().mappingFrame(),
                layoutObjects.titleText().worldX(), layoutObjects.titleText().worldY(),
                SAVE_SCREEN_OBJECT_BASE_DESC);
    }

    private void renderSelector(GraphicsManager graphics,
                                S3kDataSelectAssetSource assets,
                                S3kSaveScreenObjectState objectState,
                                int cameraX) {
        if (!objectState.selectorState().visible()) {
            return;
        }
        S3kSaveScreenLayoutObjects layoutObjects = objectState.layoutObjects();
        int frameIndex = objectState.selectorState().mappingFrame();
        List<SpriteMappingFrame> mappings = assets.getSaveScreenMappings();
        if (frameIndex < 0 || frameIndex >= mappings.size()) {
            return;
        }
        int selectorWorldY = layoutObjects.selector().worldY();
        renderMappingFrame(graphics,
                mappings.get(frameIndex),
                objectState.selectorState().selectorBiasedX() - 128,
                selectorWorldY - 128,
                DATA_SELECT_PATTERN_BASE + Sonic3kConstants.ARTTILE_SAVE_MISC,
                SAVE_SCREEN_OBJECT_BASE_DESC);
    }

    private void renderSelectedSlotIcon(GraphicsManager graphics,
                                        S3kDataSelectAssetSource assets,
                                        S3kSaveScreenObjectState.SelectedSlotIcon selectedSlotIcon,
                                        int cameraX) {
        if (selectedSlotIcon == null) {
            return;
        }
        renderObjectFrame(graphics, assets, selectedSlotIcon.mappingFrame(),
                selectedSlotIcon.worldX() - cameraX, selectedSlotIcon.worldY(),
                SAVE_SCREEN_OBJECT_BASE_DESC);
    }

    private void renderNoSaveBase(GraphicsManager graphics,
                                  S3kDataSelectAssetSource assets,
                                  S3kSaveScreenLayoutObjects layoutObjects,
                                  S3kSaveScreenObjectState.VisualState visualState,
                                  int cameraX) {
        if (visualState.noSaveCustomFrame() != null) {
            renderMappingFrame(graphics, visualState.noSaveCustomFrame(),
                    layoutObjects.noSave().worldX() - cameraX - 128,
                    layoutObjects.noSave().worldY() - 128,
                    DATA_SELECT_PATTERN_BASE + Sonic3kConstants.ARTTILE_SAVE_MISC,
                    SAVE_SCREEN_OBJECT_BASE_DESC);
        } else {
            renderObjectFrame(graphics, assets, visualState.noSaveMappingFrame(),
                    layoutObjects.noSave().worldX() - cameraX, layoutObjects.noSave().worldY(),
                    SAVE_SCREEN_OBJECT_BASE_DESC);
        }
    }

    private void renderNoSaveOverlay(GraphicsManager graphics,
                                     S3kDataSelectAssetSource assets,
                                     S3kSaveScreenLayoutObjects layoutObjects,
                                     S3kSaveScreenObjectState.VisualState visualState,
                                     int cameraX) {
        renderObjectFrame(graphics, assets, visualState.noSaveChildMappingFrame(),
                layoutObjects.noSave().worldX() - cameraX, layoutObjects.noSave().worldY(),
                SAVE_SCREEN_OBJECT_BASE_DESC);
    }

    private void renderDelete(GraphicsManager graphics,
                              S3kDataSelectAssetSource assets,
                              S3kSaveScreenObjectState objectState,
                              int cameraX) {
        S3kSaveScreenLayoutObjects layoutObjects = objectState.layoutObjects();
        S3kSaveScreenObjectState.VisualState visualState = objectState.visualState();
        renderObjectFrame(graphics, assets, visualState.deleteMappingFrame(),
                objectState.deleteWorldX() - cameraX, layoutObjects.deleteIcon().worldY(),
                SAVE_SCREEN_OBJECT_BASE_DESC);
        renderObjectFrame(graphics, assets, visualState.deleteChildMappingFrame(),
                objectState.deleteWorldX() - cameraX, layoutObjects.deleteIcon().worldY(),
                SAVE_SCREEN_OBJECT_BASE_DESC);
    }

    private void renderSlotBase(GraphicsManager graphics,
                                S3kDataSelectAssetSource assets,
                                S3kSaveScreenLayoutObjects.SaveSlotObject slotObject,
                                S3kSaveScreenObjectState.SlotVisualState slotState,
                                int cameraX) {
        int slotWorldX = slotObject.worldX() - cameraX;
        int slotWorldY = slotObject.worldY();
        if (slotState.customObjectFrame() != null) {
            renderMappingFrame(graphics, slotState.customObjectFrame(), slotWorldX - 128, slotWorldY - 128,
                    DATA_SELECT_PATTERN_BASE + Sonic3kConstants.ARTTILE_SAVE_MISC,
                    SAVE_SCREEN_OBJECT_BASE_DESC);
        } else {
            renderObjectFrame(graphics, assets, slotState.objectMappingFrame(), slotWorldX, slotWorldY,
                    SAVE_SCREEN_OBJECT_BASE_DESC);
        }
    }

    private void renderSlotOverlay(GraphicsManager graphics,
                                   S3kDataSelectAssetSource assets,
                                   S3kSaveScreenLayoutObjects.SaveSlotObject slotObject,
                                   S3kSaveScreenObjectState.SlotVisualState slotState,
                                   int cameraX) {
        if (slotState.sub2MappingFrame() < 0) {
            return;
        }
        int slotWorldX = slotObject.worldX() - cameraX;
        int slotWorldY = slotObject.worldY();
        int sub2WorldY = slotState.sub2MappingFrame() == 0x1A ? slotWorldY - 8 : slotWorldY;
        renderObjectFrame(graphics, assets, slotState.sub2MappingFrame(), slotWorldX, sub2WorldY,
                SAVE_SCREEN_OBJECT_BASE_DESC);
    }

    private void renderSlotMetadata(GraphicsManager graphics,
                                    S3kSaveScreenObjectState.SlotVisualState slotState,
                                    int slotIndex,
                                    int cameraX,
                                    boolean highPriority) {
        int labelPrefixOffset = SAVE_SLOT_LABEL_PREFIX_OFFSET + (slotIndex * SAVE_SLOT_PLANE_STEP);
        int labelOffset = SAVE_SLOT_LABEL_OFFSET + (slotIndex * SAVE_SLOT_PLANE_STEP);
        switch (slotState.labelKind()) {
            case BLANK -> {
                renderPlaneOverlayTilemap(graphics, saveMiscLabelPrefixWord(), 1, 1,
                        labelPrefixOffset, cameraX, highPriority);
                renderPlaneOverlayTilemap(graphics, blankLabelWords(), 5, 1, labelOffset, cameraX, highPriority);
            }
            case CLEAR -> {
                renderPlaneOverlayTilemap(graphics, saveMiscLabelPrefixWord(), 1, 1,
                        labelPrefixOffset, cameraX, highPriority);
                renderPlaneOverlayTilemap(graphics, clearLabelWords(), 5, 1, labelOffset, cameraX, highPriority);
            }
            case ZONE -> {
                if (slotState.hostPreview() != null
                        && slotState.hostPreview().zoneLabelText() != null) {
                    renderPlaneOverlayTilemap(graphics,
                            hostZoneLabelWords(slotState.hostPreview().zoneLabelText()),
                            6, 1, labelOffset - 2, cameraX, highPriority);
                } else {
                    renderPlaneOverlayTilemap(graphics, zoneLabelWords(slotState.zoneDisplayNumber()),
                            6, 1, labelOffset - 2, cameraX, highPriority);
                }
            }
        }

        int livesOffset = SAVE_SLOT_LIVES_OFFSET + (slotIndex * SAVE_SLOT_PLANE_STEP);
        renderPlaneOverlayTilemap(graphics, livesContinueHeaderWords(slotState.headerStyleIndex()),
                3, 5, livesOffset, cameraX, highPriority);
        if (slotState.headerStyleIndex() == 0) {
            renderPlaneOverlayTilemap(graphics, blankStatWords(), 2, 5, livesOffset + 6, cameraX, highPriority);
        } else {
            renderPlaneOverlayTilemap(graphics, lifeContinueDigitsWords(slotState.lives()), 2, 2, livesOffset + 6,
                    cameraX, highPriority);
            renderPlaneOverlayTilemap(graphics, lifeContinueDigitsWords(slotState.continuesCount()), 2, 2,
                    livesOffset + 0x306, cameraX, highPriority);
        }
    }

    private void renderObjectFrame(GraphicsManager graphics,
                                   S3kDataSelectAssetSource assets,
                                   int frameIndex,
                                   int worldX,
                                   int worldY,
                                   int baseDescBits) {
        List<SpriteMappingFrame> mappings = assets.getSaveScreenMappings();
        if (frameIndex < 0 || frameIndex >= mappings.size()) {
            return;
        }
        renderMappingFrame(graphics, mappings.get(frameIndex), worldX - 128, worldY - 128,
                DATA_SELECT_PATTERN_BASE + Sonic3kConstants.ARTTILE_SAVE_MISC,
                baseDescBits);
    }

    private int[] selectSlotLayout(S3kDataSelectAssetSource assets,
                                   S3kSaveScreenObjectState.VisualState visualState,
                                   S3kSaveScreenObjectState.SlotVisualState slotState) {
        if (slotState == null || slotState.kind() == S3kSaveScreenObjectState.SlotVisualKind.EMPTY) {
            return assets.getNewLayoutWords();
        }
        int[][] staticLayouts = assets.getStaticLayouts();
        if (staticLayouts.length == 0) {
            return assets.getNewLayoutWords();
        }
        int frame = Math.max(0, Math.min(staticLayouts.length - 1, visualState.activeHeaderAnimationFrame()));
        return staticLayouts[frame];
    }

    private void renderScreenTilemap(GraphicsManager graphics, int[] words, int width, int height) {
        renderTilemap(graphics, words, width, height,
                SCREEN_SPACE_WORLD_ORIGIN, SCREEN_SPACE_WORLD_ORIGIN);
    }

    private void renderPlaneOverlayTilemap(GraphicsManager graphics, int[] words, int width, int height,
                                           int planeByteOffset,
                                           int cameraX,
                                           boolean highPriority) {
        int tileIndex = planeByteOffset / 2;
        int tileX = tileIndex % PLANE_WIDTH_TILES;
        int tileY = tileIndex / PLANE_WIDTH_TILES;
        renderTilemap(graphics, words, width, height,
                SCREEN_SPACE_WORLD_ORIGIN - cameraX + (tileX * 8),
                SCREEN_SPACE_WORLD_ORIGIN + (tileY * 8),
                highPriority ? PriorityFilter.HIGH : PriorityFilter.LOW);
    }

    private void renderStaticPlaneTextOverlays(GraphicsManager graphics, int cameraX, boolean highPriority) {
        renderPlaneOverlayTilemap(graphics, textWords("NO"), 2, 1, NO_SAVE_TEXT_OFFSET, cameraX, highPriority);
        renderPlaneOverlayTilemap(graphics, textWords("SAVE"), 4, 1, SAVE_TEXT_OFFSET, cameraX, highPriority);
        renderPlaneOverlayTilemap(graphics, textWords("DELETE"), 6, 1, DELETE_TEXT_OFFSET, cameraX, highPriority);
    }

    private void renderPlaneABase(GraphicsManager graphics, int[] words, int cameraX, boolean highPriority) {
        renderTilemap(graphics, words, PLANE_WIDTH_TILES, PLANE_HEIGHT_TILES,
                SCREEN_SPACE_WORLD_ORIGIN - cameraX, SCREEN_SPACE_WORLD_ORIGIN,
                highPriority ? PriorityFilter.HIGH : PriorityFilter.LOW);
    }

    private int[] blankLabelWords() {
        return new int[]{blankPriorityWord(), blankPriorityWord(), blankPriorityWord(),
                blankPriorityWord(), blankPriorityWord()};
    }

    private int[] clearLabelWords() {
        return textWords("CLEAR");
    }

    private int[] zoneLabelWords(int zoneDisplayNumber) {
        int tens = Math.max(0, Math.min(99, zoneDisplayNumber)) / 10;
        int ones = Math.max(0, Math.min(99, zoneDisplayNumber)) % 10;
        return new int[]{
                saveTextWord('Z'),
                saveTextWord('O'),
                saveTextWord('N'),
                saveTextWord('E'),
                tens == 0 ? highPriorityWord() : saveTextDigitWord(tens),
                saveTextDigitWord(ones)
        };
    }

    /**
     * Renders a host zone label (e.g. "GHZ", "EHZ") left-justified in the
     * 6-tile label area, padded with blank high-priority tiles.
     */
    private int[] hostZoneLabelWords(String label) {
        int[] words = new int[6];
        int len = Math.min(label.length(), 6);
        for (int i = 0; i < len; i++) {
            words[i] = saveTextWord(label.charAt(i));
        }
        for (int i = len; i < 6; i++) {
            words[i] = blankPriorityWord();
        }
        return words;
    }

    private int[] livesContinueHeaderWords(int headerStyleIndex) {
        return switch (headerStyleIndex) {
            case 1 -> new int[]{
                    saveExtraWord(0x6E), saveExtraWord(0x70), saveExtraWord(0x5A),
                    saveExtraWord(0x6F), saveExtraWord(0x71), saveExtraWord(0x5B),
                    saveExtraWord(0x5C), saveExtraWord(0x5F), blankPriorityWord(),
                    saveExtraWord(0x5D), saveExtraWord(0x60), saveExtraWord(0x5A),
                    saveExtraWord(0x5E), saveExtraWord(0x61), saveExtraWord(0x5B)
            };
            case 2 -> new int[]{
                    saveExtraWord(0x72), saveExtraWord(0x74), saveExtraWord(0x5A),
                    saveExtraWord(0x73), saveExtraWord(0x75), saveExtraWord(0x5B),
                    saveExtraWord(0x62), saveExtraWord(0x65), blankPriorityWord(),
                    saveExtraWord(0x63), saveExtraWord(0x66), saveExtraWord(0x5A),
                    saveExtraWord(0x64), saveExtraWord(0x67), saveExtraWord(0x5B)
            };
            case 3 -> new int[]{
                    saveExtraWord(0x76), saveExtraWord(0x78), saveExtraWord(0x5A),
                    saveExtraWord(0x77), saveExtraWord(0x79), saveExtraWord(0x5B),
                    saveExtraWord(0x68), saveExtraWord(0x6B), blankPriorityWord(),
                    saveExtraWord(0x69), saveExtraWord(0x6C), saveExtraWord(0x5A),
                    saveExtraWord(0x6A), saveExtraWord(0x6D), saveExtraWord(0x5B)
            };
            default -> new int[15];
        };
    }

    private int[] blankStatWords() {
        return new int[]{
                blankPriorityWord(), blankPriorityWord(),
                blankPriorityWord(), blankPriorityWord(),
                blankPriorityWord(), blankPriorityWord(),
                blankPriorityWord(), blankPriorityWord(),
                blankPriorityWord(), blankPriorityWord()
        };
    }

    private int[] lifeContinueDigitsWords(int value) {
        int clamped = Math.max(0, Math.min(99, value));
        int tens = clamped / 10;
        int ones = clamped % 10;
        int tensLeft = tens == 0 ? blankPriorityWord() : saveExtraWord(0x46 + (tens * 2));
        int tensRight = tens == 0 ? blankPriorityWord() : saveExtraWord(0x47 + (tens * 2));
        return new int[]{
                tensLeft,
                saveExtraWord(0x46 + (ones * 2)),
                tensRight,
                saveExtraWord(0x47 + (ones * 2))
        };
    }

    private int saveTextWord(char c) {
        int encoded = S3kSaveTextCodec.encode(c);
        return encoded == 0 ? blankPriorityWord() : SAVE_TEXT_WORD_BASE + encoded;
    }

    private int[] textWords(String text) {
        int[] words = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            words[i] = saveTextWord(text.charAt(i));
        }
        return words;
    }

    private int saveTextDigitWord(int digit) {
        return Sonic3kConstants.ARTTILE_SAVE_TEXT + digit + TILE_WORD_FLAGS;
    }

    private int saveExtraWord(int tileOffset) {
        return Sonic3kConstants.ARTTILE_SAVE_EXTRA + tileOffset + TILE_WORD_FLAGS;
    }

    private int blankPriorityWord() {
        return 0x8000;
    }

    private int highPriorityWord() {
        return 0x8000;
    }

    private int[] saveMiscLabelPrefixWord() {
        return new int[]{saveMiscWord(0x12)};
    }

    private int saveMiscWord(int tileOffset) {
        return Sonic3kConstants.ARTTILE_SAVE_MISC + tileOffset + highPriorityWord();
    }

    private void renderTilemap(GraphicsManager graphics, int[] words, int width, int height, int worldX, int worldY) {
        renderTilemap(graphics, words, width, height, worldX, worldY, PriorityFilter.ALL);
    }

    private void renderTilemap(GraphicsManager graphics, int[] words, int width, int height, int worldX, int worldY,
                               PriorityFilter priorityFilter) {
        if (words == null || words.length == 0) {
            return;
        }

        int originX = worldX - 128;
        int originY = worldY - 128;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int index = row * width + col;
                if (index >= words.length) {
                    return;
                }
                int word = words[index];
                if (word == 0) {
                    continue;
                }
                if (!priorityFilter.matches(word)) {
                    continue;
                }
                reusableDesc.set(word);
                if (reusableDesc.getPatternIndex() == 0) {
                    continue;
                }
                graphics.renderPatternWithId(DATA_SELECT_PATTERN_BASE + reusableDesc.getPatternIndex(),
                        reusableDesc, originX + col * 8, originY + row * 8);
            }
        }
    }

    private void renderMappingFrame(GraphicsManager graphics, SpriteMappingFrame frame, int worldX, int worldY,
                                    int patternBase,
                                    int baseDescBits) {
        if (frame == null || frame.pieces().isEmpty()) {
            return;
        }
        List<SpriteMappingPiece> pieces = frame.pieces();
        for (int i = pieces.size() - 1; i >= 0; i--) {
            renderMappingPiece(graphics, pieces.get(i), worldX, worldY, patternBase, baseDescBits);
        }
    }

    private void renderMappingPiece(GraphicsManager graphics, SpriteMappingPiece piece, int worldX, int worldY,
                                    int patternBase,
                                    int baseDescBits) {
        for (int tx = 0; tx < piece.widthTiles(); tx++) {
            for (int ty = 0; ty < piece.heightTiles(); ty++) {
                int tileOffset = tx * piece.heightTiles() + ty;
                int patternId = patternBase + piece.tileIndex() + tileOffset;
                int tileX = worldX + piece.xOffset() + (piece.hFlip() ? ((piece.widthTiles() - 1 - tx) * 8) : (tx * 8));
                int tileY = worldY + piece.yOffset() + (piece.vFlip() ? ((piece.heightTiles() - 1 - ty) * 8) : (ty * 8));
                int descBits = patternId & 0x7FF;
                if (piece.hFlip()) {
                    descBits |= 0x800;
                }
                if (piece.vFlip()) {
                    descBits |= 0x1000;
                }
                descBits |= (piece.paletteIndex() & 0x3) << 13;
                if (piece.priority()) {
                    descBits |= 0x8000;
                }
                descBits |= baseDescBits;
                graphics.renderPatternWithId(patternId, new PatternDesc(descBits), tileX, tileY);
            }
        }
    }

    private void cachePatterns(GraphicsManager graphics, Pattern[] patterns, int basePatternId) {
        for (int i = 0; i < patterns.length; i++) {
            graphics.cachePatternTexture(patterns[i], basePatternId + i);
        }
    }

    private void cachePalette(GraphicsManager graphics, byte[] segaBytes, int paletteLine) {
        cachePalette(graphics, segaBytes, paletteLine, 0);
    }

    private void cachePalette(GraphicsManager graphics, byte[] segaBytes, int paletteLine, int startColorIndex) {
        Palette palette = paletteFromBytes(segaBytes, startColorIndex);
        if (palette != null) {
            graphics.cachePaletteTexture(palette, paletteLine);
        }
    }

    private void cacheCharacterAndEmeraldPalettes(GraphicsManager graphics,
                                                  byte[] characterPaletteBytes,
                                                  byte[] emeraldPaletteBytes) {
        if (characterPaletteBytes == null || characterPaletteBytes.length == 0) {
            cachePalette(graphics, emeraldPaletteBytes, 2, 1);
            return;
        }

        byte[] line1Bytes = slicePaletteLine(characterPaletteBytes, 0);
        if (line1Bytes.length > 0) {
            cachePalette(graphics, line1Bytes, 1);
        }

        Palette line2Palette = paletteFromBytes(slicePaletteLine(characterPaletteBytes, Palette.PALETTE_SIZE * 2), 0);
        if (line2Palette == null) {
            line2Palette = new Palette();
        }
        overlayPaletteBytes(line2Palette, emeraldPaletteBytes, 1);
        graphics.cachePaletteTexture(line2Palette, 2);
    }

    private byte[] slicePaletteLine(byte[] segaBytes, int startByte) {
        if (segaBytes == null || startByte >= segaBytes.length) {
            return new byte[0];
        }
        int end = Math.min(segaBytes.length, startByte + (Palette.PALETTE_SIZE * 2));
        return Arrays.copyOfRange(segaBytes, startByte, end);
    }

    private void overlayPaletteBytes(Palette palette, byte[] segaBytes, int startColorIndex) {
        if (palette == null || segaBytes == null || segaBytes.length == 0) {
            return;
        }
        for (int i = 0; i + 1 < segaBytes.length && (startColorIndex + (i / 2)) < Palette.PALETTE_SIZE; i += 2) {
            palette.getColor(startColorIndex + (i / 2)).fromSegaFormat(segaBytes, i);
        }
    }

    private Palette paletteFromBytes(byte[] segaBytes, int startColorIndex) {
        if (segaBytes == null || segaBytes.length == 0) {
            return null;
        }
        Palette palette = new Palette();
        if (startColorIndex <= 0) {
            palette.fromSegaFormat(segaBytes);
            return palette;
        }
        for (int i = 0; i + 1 < segaBytes.length && (startColorIndex + (i / 2)) < Palette.PALETTE_SIZE; i += 2) {
            palette.getColor(startColorIndex + (i / 2)).fromSegaFormat(segaBytes, i);
        }
        return palette;
    }

    private byte[] paletteAt(byte[][] palettes, int index) {
        if (palettes == null || palettes.length == 0) {
            return new byte[0];
        }
        int clamped = Math.max(0, Math.min(palettes.length - 1, index));
        return palettes[clamped];
    }

    private enum PriorityFilter {
        ALL,
        LOW,
        HIGH;

        private boolean matches(int word) {
            boolean highPriority = (word & 0x8000) != 0;
            return switch (this) {
                case ALL -> true;
                case LOW -> !highPriority;
                case HIGH -> highPriority;
            };
        }
    }
}
