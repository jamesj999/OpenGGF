package com.openggf.game.sonic3k;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AniPlcParser;
import com.openggf.level.animation.AniPlcScriptState;
import com.openggf.level.animation.AnimatedPatternManager;

import java.util.List;
import java.util.logging.Logger;

/**
 * Animates S3K zone tiles using the ROM's AniPLC script format plus the direct
 * HCZ background DMA updates that sit alongside AniPLC in the original engine.
 */
class Sonic3kPatternAnimator implements AnimatedPatternManager {
    private static final Logger LOG = Logger.getLogger(Sonic3kPatternAnimator.class.getName());

    private static final int HCZ1_WATERLINE_VISIBLE = 0x60;
    private static final int HCZ1_EQUILIBRIUM_Y = 0x610;

    private static final int[] HCZ2_DEFORM_INDEX = {
            3, 0x0A, 0x14, 0x1E, 0x2C,
            2, 0x0C, 0x16, 0x20,
            5, 0x00, 0x08, 0x0E, 0x18, 0x22, 0x2A,
            3, 0x02, 0x10, 0x1A, 0x24,
            1, 0x12, 0x1C,
            1, 0x06, 0x28,
            1, 0x04, 0x26,
            0xFF
    };
    private static final int[] HCZ2_SMALL_BG_LINE_WORD_COUNTS = {
            0x40, 0x00, 0x30, 0x10, 0x20, 0x20, 0x10, 0x30
    };
    private static final int[] HCZ2_ART2_WORD_COUNTS = {
            0x80, 0x00, 0x60, 0x20, 0x40, 0x40, 0x20, 0x60
    };
    private static final int[] HCZ2_ART3_WORD_COUNTS = {
            0x100, 0x00, 0xC0, 0x40, 0x80, 0x80, 0x40, 0xC0
    };
    private static final int[] HCZ2_ART4_WORD_COUNTS = {
            0x300, 0x00, 0x2A0, 0x60, 0x240, 0xC0, 0x1E0, 0x120,
            0x180, 0x180, 0x120, 0x1E0, 0xC0, 0x240, 0x60, 0x2A0
    };

    private final Level level;
    private final GraphicsManager graphicsManager = GraphicsManager.getInstance();
    private final int zoneIndex;
    private final int actIndex;
    private final boolean isSkipIntro;
    private final List<AniPlcScriptState> scripts;

    private final Pattern[] firstTreePatterns;
    private boolean firstTreeApplied;

    private final byte[] hczWaterlineScrollData;
    private final byte[] hcz1DynamicBlockData;
    private final byte[] hcz1WaterlineBelow1Data;
    private final byte[] hcz1WaterlineAbove1Data;
    private final Pattern[] hcz1WaterlineBelow1Patterns;
    private final Pattern[] hcz1UpperBg1Patterns;
    private final Pattern[] hcz1WaterlineAbove1Patterns;
    private final Pattern[] hcz1LowerBg1Patterns;
    private final byte[] hcz1WaterlineBelow2Data;
    private final byte[] hcz1WaterlineAbove2Data;
    private final Pattern[] hcz1WaterlineBelow2Patterns;
    private final Pattern[] hcz1UpperBg2Patterns;
    private final Pattern[] hcz1WaterlineAbove2Patterns;
    private final Pattern[] hcz1LowerBg2Patterns;
    private final byte[] hcz2SmallBgLineData;
    private final byte[] hcz2Art2Data;
    private final byte[] hcz2Art3Data;
    private final byte[] hcz2Art4Data;
    private final Pattern[] hcz2SmallBgLinePatterns;
    private final Pattern[] hcz2Art2Patterns;
    private final Pattern[] hcz2Art3Patterns;
    private final Pattern[] hcz2Art4Patterns;

    private int lastHcz1WaterlineDelta = Integer.MIN_VALUE;
    private int lastHcz2SmallBgLineValue = Integer.MIN_VALUE;
    private int lastHcz2Art2Value = Integer.MIN_VALUE;
    private int lastHcz2Art3Value = Integer.MIN_VALUE;
    private int lastHcz2Art4Value = Integer.MIN_VALUE;

    // Gumball bonus stage: direct DMA of uncompressed art based on BG scroll
    // ROM: AnimateTiles_Gumball (sonic3k.asm:55266)
    private static final int GUMBALL_DEST_TILE = 0x40;
    private static final int GUMBALL_TILE_COUNT = 0x54; // tiles_to_bytes($054) / 0x20
    private static final int GUMBALL_DMA_SIZE = GUMBALL_TILE_COUNT * Pattern.PATTERN_SIZE_IN_ROM;
    private final byte[] gumballAniData;
    private int lastGumballIndex = -1;
    private int gumballFrameCounter;

    Sonic3kPatternAnimator(RomByteReader reader, Level level,
                           int zoneIndex, int actIndex, boolean isSkipIntro) {
        this.level = level;
        this.zoneIndex = zoneIndex;
        this.actIndex = actIndex;
        this.isSkipIntro = isSkipIntro;

        int aniPlcAddr = resolveAniPlcAddr(zoneIndex, actIndex);
        if (aniPlcAddr < 0) {
            this.scripts = List.of();
            this.firstTreePatterns = null;
            this.hczWaterlineScrollData = null;
            this.hcz1DynamicBlockData = null;
            this.hcz1WaterlineBelow1Data = null;
            this.hcz1WaterlineAbove1Data = null;
            this.hcz1WaterlineBelow1Patterns = null;
            this.hcz1UpperBg1Patterns = null;
            this.hcz1WaterlineAbove1Patterns = null;
            this.hcz1LowerBg1Patterns = null;
            this.hcz1WaterlineBelow2Data = null;
            this.hcz1WaterlineAbove2Data = null;
            this.hcz1WaterlineBelow2Patterns = null;
            this.hcz1UpperBg2Patterns = null;
            this.hcz1WaterlineAbove2Patterns = null;
            this.hcz1LowerBg2Patterns = null;
            this.hcz2SmallBgLineData = null;
            this.hcz2Art2Data = null;
            this.hcz2Art3Data = null;
            this.hcz2Art4Data = null;
            this.hcz2SmallBgLinePatterns = null;
            this.hcz2Art2Patterns = null;
            this.hcz2Art3Patterns = null;
            this.hcz2Art4Patterns = null;
            // Gumball uses direct DMA, not AniPLC — still needs data loaded
            if (zoneIndex == 0x13) {
                this.gumballAniData = loadRawBytes(reader,
                        Sonic3kConstants.GUMBALL_ANI_TILES_ADDR, GUMBALL_DMA_SIZE + 0x80);
            } else {
                this.gumballAniData = null;
            }
            return;
        }

        this.scripts = AniPlcParser.parseScripts(reader, aniPlcAddr);
        AniPlcParser.ensurePatternCapacity(scripts, level);

        if (zoneIndex == 0 && actIndex == 1) {
            int firstTreeEnd = Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_DEST_TILE
                    + Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_SIZE / Pattern.PATTERN_SIZE_IN_ROM;
            level.ensurePatternCapacity(firstTreeEnd);
        }
        ensureHczPatternCapacity();

        boolean isAiz1Intro = zoneIndex == 0 && actIndex == 0 && !isSkipIntro;
        if (!isAiz1Intro) {
            AniPlcParser.primeScripts(scripts, level, graphicsManager);
        }

        this.firstTreePatterns = zoneIndex == 0 && actIndex == 1
                ? loadUncompressedPatterns(reader,
                Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_ADDR,
                Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_SIZE)
                : null;
        this.firstTreeApplied = false;

        if (zoneIndex == 1 && actIndex == 0) {
            this.hczWaterlineScrollData = loadRawBytes(reader,
                    Sonic3kConstants.HCZ_WATERLINE_SCROLL_DATA_ADDR,
                    Sonic3kConstants.HCZ_WATERLINE_SCROLL_DATA_SIZE);
            this.hcz1DynamicBlockData = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_BELOW1_ADDR,
                    0x0C00);
            this.hcz1WaterlineBelow1Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_BELOW1_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1WaterlineAbove1Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_ABOVE1_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1WaterlineBelow1Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_BELOW1_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1UpperBg1Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_UPPER_BG1_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1WaterlineAbove1Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_ABOVE1_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1LowerBg1Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_LOWER_BG1_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1WaterlineBelow2Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_BELOW2_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1WaterlineAbove2Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_ABOVE2_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1WaterlineBelow2Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_BELOW2_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1UpperBg2Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_UPPER_BG2_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1WaterlineAbove2Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_ABOVE2_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz1LowerBg2Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_LOWER_BG2_ADDR,
                    Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE);
            this.hcz2SmallBgLineData = null;
            this.hcz2Art2Data = null;
            this.hcz2Art3Data = null;
            this.hcz2Art4Data = null;
            this.hcz2SmallBgLinePatterns = null;
            this.hcz2Art2Patterns = null;
            this.hcz2Art3Patterns = null;
            this.hcz2Art4Patterns = null;

            // HCZ1 starts with the lower repair strips resident before the
            // waterline-specific recomposition path kicks in.
            applyPatternsToLevel(this.hcz1LowerBg1Patterns, 0x2F4);
            applyPatternsToLevel(this.hcz1LowerBg2Patterns, 0x300);
        } else if (zoneIndex == 1 && actIndex == 1) {
            this.hczWaterlineScrollData = null;
            this.hcz1DynamicBlockData = null;
            this.hcz1WaterlineBelow1Data = null;
            this.hcz1WaterlineAbove1Data = null;
            this.hcz1WaterlineBelow1Patterns = null;
            this.hcz1UpperBg1Patterns = null;
            this.hcz1WaterlineAbove1Patterns = null;
            this.hcz1LowerBg1Patterns = null;
            this.hcz1WaterlineBelow2Data = null;
            this.hcz1WaterlineAbove2Data = null;
            this.hcz1WaterlineBelow2Patterns = null;
            this.hcz1UpperBg2Patterns = null;
            this.hcz1WaterlineAbove2Patterns = null;
            this.hcz1LowerBg2Patterns = null;
            this.hcz2SmallBgLineData = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ2_SMALL_BG_LINE_ADDR,
                    Sonic3kConstants.ART_UNC_HCZ2_SMALL_BG_LINE_SIZE);
            this.hcz2Art2Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ2_2_ADDR,
                    Sonic3kConstants.ART_UNC_HCZ2_2_SIZE);
            this.hcz2Art3Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ2_3_ADDR,
                    Sonic3kConstants.ART_UNC_HCZ2_3_SIZE);
            this.hcz2Art4Data = loadRawBytes(reader,
                    Sonic3kConstants.ART_UNC_HCZ2_4_ADDR,
                    Sonic3kConstants.ART_UNC_HCZ2_4_SIZE);
            this.hcz2SmallBgLinePatterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_HCZ2_SMALL_BG_LINE_ADDR,
                    Sonic3kConstants.ART_UNC_HCZ2_SMALL_BG_LINE_SIZE);
            this.hcz2Art2Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_HCZ2_2_ADDR,
                    Sonic3kConstants.ART_UNC_HCZ2_2_SIZE);
            this.hcz2Art3Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_HCZ2_3_ADDR,
                    Sonic3kConstants.ART_UNC_HCZ2_3_SIZE);
            this.hcz2Art4Patterns = loadUncompressedPatterns(reader,
                    Sonic3kConstants.ART_UNC_HCZ2_4_ADDR,
                    Sonic3kConstants.ART_UNC_HCZ2_4_SIZE);
        } else {
            this.hczWaterlineScrollData = null;
            this.hcz1DynamicBlockData = null;
            this.hcz1WaterlineBelow1Data = null;
            this.hcz1WaterlineAbove1Data = null;
            this.hcz1WaterlineBelow1Patterns = null;
            this.hcz1UpperBg1Patterns = null;
            this.hcz1WaterlineAbove1Patterns = null;
            this.hcz1LowerBg1Patterns = null;
            this.hcz1WaterlineBelow2Data = null;
            this.hcz1WaterlineAbove2Data = null;
            this.hcz1WaterlineBelow2Patterns = null;
            this.hcz1UpperBg2Patterns = null;
            this.hcz1WaterlineAbove2Patterns = null;
            this.hcz1LowerBg2Patterns = null;
            this.hcz2SmallBgLineData = null;
            this.hcz2Art2Data = null;
            this.hcz2Art3Data = null;
            this.hcz2Art4Data = null;
            this.hcz2SmallBgLinePatterns = null;
            this.hcz2Art2Patterns = null;
            this.hcz2Art3Patterns = null;
            this.hcz2Art4Patterns = null;
        }

        // Gumball bonus stage animated tiles (zone 0x13)
        if (zoneIndex == 0x13) {
            this.gumballAniData = loadRawBytes(reader,
                    Sonic3kConstants.GUMBALL_ANI_TILES_ADDR, GUMBALL_DMA_SIZE + 0x80);
        } else {
            this.gumballAniData = null;
        }
    }

    @Override
    public void update() {
        if (scripts.isEmpty()) {
            return;
        }

        switch (zoneIndex) {
            case 0 -> {
                if (actIndex == 0) {
                    updateAiz1();
                } else {
                    updateAiz2();
                }
            }
            case 1 -> {
                if (actIndex == 0) {
                    updateHcz1();
                } else {
                    updateHcz2();
                }
            }
            case 0x13 -> updateGumball();
            default -> runAllScripts();
        }
    }

    private void updateAiz1() {
        if (isAizBossActive()) {
            return;
        }
        if (isSkipIntro || AizPlaneIntroInstance.isMainLevelPhaseActive()) {
            runAllScripts();
        }
    }

    private void updateAiz2() {
        if (isAizBossActive()) {
            return;
        }

        int cameraX = getCameraX();
        if (cameraX >= 0x1C0) {
            runAllScripts();
            firstTreeApplied = false;
            return;
        }

        if (!scripts.isEmpty()) {
            scripts.get(0).tick(level, graphicsManager);
        }
        if (!firstTreeApplied && firstTreePatterns != null) {
            applyPatternsToLevel(firstTreePatterns, Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_DEST_TILE);
            firstTreeApplied = true;
        }
    }

    private void updateHcz1() {
        updateHcz1BackgroundStrips();
        runAllScripts();
    }

    private void updateHcz2() {
        updateHcz2BackgroundStrips();
        runAllScripts();
    }

    private void runAllScripts() {
        for (AniPlcScriptState script : scripts) {
            script.tick(level, graphicsManager);
        }
    }

    private boolean isAizBossActive() {
        try {
            Sonic3kLevelEventManager lem = Sonic3kLevelEventManager.getInstance();
            if (lem != null) {
                Sonic3kAIZEvents aizEvents = lem.getAizEvents();
                if (aizEvents != null) {
                    return aizEvents.isBossFlag();
                }
            }
        } catch (Exception e) {
            LOG.fine(() -> "Sonic3kPatternAnimator.isAizBossActive: " + e.getMessage());
        }
        return false;
    }

    private void updateHcz1BackgroundStrips() {
        int waterlineDelta = computeHcz1WaterlineDelta();
        if (waterlineDelta == lastHcz1WaterlineDelta) {
            return;
        }
        lastHcz1WaterlineDelta = waterlineDelta;

        if (waterlineDelta == 0) {
            applyPatternsToLevel(hcz1LowerBg1Patterns, 0x2F4);
            applyPatternsToLevel(hcz1LowerBg2Patterns, 0x300);
            return;
        }

        if (waterlineDelta < 0) {
            if (waterlineDelta > -HCZ1_WATERLINE_VISIBLE) {
                applyDynamicHcz1Strip(0x000, (waterlineDelta + HCZ1_WATERLINE_VISIBLE) * 0x60, 0x2DC);
            } else {
                applyPatternsToLevel(hcz1WaterlineBelow1Patterns, 0x2DC);
                applyPatternsToLevel(hcz1WaterlineBelow2Patterns, 0x2E8);
            }
            applyPatternsToLevel(hcz1LowerBg1Patterns, 0x2F4);
            applyPatternsToLevel(hcz1LowerBg2Patterns, 0x300);
            return;
        }

        applyPatternsToLevel(hcz1UpperBg1Patterns, 0x2DC);
        applyPatternsToLevel(hcz1UpperBg2Patterns, 0x2E8);
        if (waterlineDelta < HCZ1_WATERLINE_VISIBLE) {
            applyDynamicHcz1Strip(0x300, (HCZ1_WATERLINE_VISIBLE - waterlineDelta) * 0x60, 0x2F4);
        } else {
            applyPatternsToLevel(hcz1WaterlineAbove1Patterns, 0x2F4);
            applyPatternsToLevel(hcz1WaterlineAbove2Patterns, 0x300);
        }
    }

    private void updateHcz2BackgroundStrips() {
        int[] hScroll = buildHcz2HScrollValues(getCameraX());
        int eventsBg12 = hScroll[3] - hScroll[9];
        int eventsBg14 = hScroll[2] - hScroll[9];

        int smallBgValue = eventsBg12 & 0x1F;
        if (smallBgValue != lastHcz2SmallBgLineValue) {
            lastHcz2SmallBgLineValue = smallBgValue;
            applyHcz2DmaSection(hcz2SmallBgLineData, smallBgValue, 0x2D2,
                    (smallBgValue & 7) << 7,
                    (smallBgValue & 7) << 7,
                    (smallBgValue & 0x18) << 2,
                    HCZ2_SMALL_BG_LINE_WORD_COUNTS);
        }

        int art2Value = eventsBg12 & 0x1F;
        if (art2Value != lastHcz2Art2Value) {
            lastHcz2Art2Value = art2Value;
            applyHcz2DmaSection(hcz2Art2Data, art2Value, 0x2D6,
                    (art2Value & 7) << 8,
                    (art2Value & 7) << 8,
                    (art2Value & 0x18) << 3,
                    HCZ2_ART2_WORD_COUNTS);
        }

        int art3Value = eventsBg14 & 0x1F;
        if (art3Value != lastHcz2Art3Value) {
            lastHcz2Art3Value = art3Value;
            int baseOffset = ror16(art3Value & 7, 7);
            applyHcz2DmaSection(hcz2Art3Data, art3Value, 0x2DE,
                    baseOffset,
                    baseOffset,
                    (art3Value & 0x18) << 4,
                    HCZ2_ART3_WORD_COUNTS);
        }

        int art4Value = eventsBg14 & 0x3F;
        if (art4Value != lastHcz2Art4Value) {
            lastHcz2Art4Value = art4Value;
            applyHcz2Art4DmaSection(art4Value);
        }
    }

    private void applyHcz2DmaSection(byte[] sourceData, int rawValue, int destTile,
                                     int baseOffset, int secondChunkOffset, int bankOffset,
                                     int[] wordCounts) {
        if (sourceData == null) {
            return;
        }

        int pairIndex = (rawValue & 0x18) >> 2;
        int wordCount1 = wordCounts[pairIndex];
        int wordCount2 = wordCounts[pairIndex + 1];
        int destTile2 = destTile + ((wordCount1 << 1) / Pattern.PATTERN_SIZE_IN_ROM);

        applyRawPatternSliceToLevel(sourceData, baseOffset + bankOffset, wordCount1 << 1, destTile);
        if (wordCount2 != 0) {
            applyRawPatternSliceToLevel(sourceData, secondChunkOffset, wordCount2 << 1, destTile2);
        }
    }

    private void applyHcz2Art4DmaSection(int rawValue) {
        if (hcz2Art4Data == null) {
            return;
        }

        int d1 = negateWord(rawValue);
        int d2 = d1;
        d1 &= 7;
        d1 = ror16(d1, 7);
        int d0 = d1;
        d0 = word(d0 + d0);
        d1 = word(d1 + d0);
        int sourceOffset2 = d1;
        d2 = word(~d2);
        d2 &= 0x38;
        d0 = d2;
        d2 = word(d2 << 3);
        d1 = word(d1 + d2);
        d2 = word(d2 + d2);
        d1 = word(d1 + d2);

        int pairIndex = d0 >> 2;
        int wordCount1 = HCZ2_ART4_WORD_COUNTS[pairIndex];
        int wordCount2 = HCZ2_ART4_WORD_COUNTS[pairIndex + 1];
        int destTile2 = 0x2EE + ((wordCount1 << 1) / Pattern.PATTERN_SIZE_IN_ROM);

        applyRawPatternSliceToLevel(hcz2Art4Data, d1, wordCount1 << 1, 0x2EE);
        if (wordCount2 != 0) {
            applyRawPatternSliceToLevel(hcz2Art4Data, sourceOffset2, wordCount2 << 1, destTile2);
        }
    }

    private void applyDynamicHcz1Strip(int sourceBaseOffset, int tableOffset, int destTile) {
        if (hczWaterlineScrollData == null || hcz1DynamicBlockData == null) {
            return;
        }

        byte[] composed = new byte[0x300];
        for (int i = 0; i < 0x60; i++) {
            int lookupIndex = tableOffset + i;
            if (lookupIndex < 0 || lookupIndex >= hczWaterlineScrollData.length) {
                return;
            }
            int byteIndex = hczWaterlineScrollData[lookupIndex] & 0xFF;
            int sourceByteOffset = byteIndex << 2;
            int sourceIndexA = sourceBaseOffset + sourceByteOffset;
            int sourceIndexB = sourceBaseOffset + 0x600 + sourceByteOffset;
            if (sourceIndexA + 4 > hcz1DynamicBlockData.length
                    || sourceIndexB + 4 > hcz1DynamicBlockData.length) {
                return;
            }
            System.arraycopy(hcz1DynamicBlockData, sourceIndexA, composed, i << 2, 4);
            System.arraycopy(hcz1DynamicBlockData, sourceIndexB, composed, 0x180 + (i << 2), 4);
        }
        applyRawPatternBytesToLevel(composed, destTile);
    }

    private void applyRawPatternBytesToLevel(byte[] data, int destTile) {
        if ((data.length % Pattern.PATTERN_SIZE_IN_ROM) != 0) {
            return;
        }

        Pattern[] patterns = new Pattern[data.length / Pattern.PATTERN_SIZE_IN_ROM];
        for (int i = 0; i < patterns.length; i++) {
            Pattern pattern = new Pattern();
            byte[] tileData = new byte[Pattern.PATTERN_SIZE_IN_ROM];
            System.arraycopy(data, i * Pattern.PATTERN_SIZE_IN_ROM, tileData, 0,
                    Pattern.PATTERN_SIZE_IN_ROM);
            pattern.fromSegaFormat(tileData);
            patterns[i] = pattern;
        }
        applyPatternsToLevel(patterns, destTile);
    }

    private void applyPatternSliceToLevel(Pattern[] sourcePatterns, int sourceByteOffset,
                                          int byteLength, int destTile) {
        if (sourcePatterns == null || byteLength <= 0) {
            return;
        }
        if ((sourceByteOffset % Pattern.PATTERN_SIZE_IN_ROM) != 0
                || (byteLength % Pattern.PATTERN_SIZE_IN_ROM) != 0) {
            return;
        }

        int sourceTileOffset = sourceByteOffset / Pattern.PATTERN_SIZE_IN_ROM;
        int tileCount = byteLength / Pattern.PATTERN_SIZE_IN_ROM;
        int maxPatterns = level.getPatternCount();
        boolean canUpdateTextures = graphicsManager.isGlInitialized();
        for (int i = 0; i < tileCount; i++) {
            int sourceIndex = sourceTileOffset + i;
            int destIndex = destTile + i;
            if (sourceIndex >= sourcePatterns.length || destIndex >= maxPatterns) {
                break;
            }
            Pattern dest = level.getPattern(destIndex);
            dest.copyFrom(sourcePatterns[sourceIndex]);
            if (canUpdateTextures) {
                graphicsManager.updatePatternTexture(dest, destIndex);
            }
        }
    }

    private void applyRawPatternSliceToLevel(byte[] sourceData, int sourceByteOffset,
                                             int byteLength, int destTile) {
        if (sourceData == null || byteLength <= 0) {
            return;
        }
        if ((sourceByteOffset % Pattern.PATTERN_SIZE_IN_ROM) != 0
                || (byteLength % Pattern.PATTERN_SIZE_IN_ROM) != 0
                || sourceByteOffset < 0
                || sourceByteOffset + byteLength > sourceData.length) {
            return;
        }

        byte[] slice = new byte[byteLength];
        System.arraycopy(sourceData, sourceByteOffset, slice, 0, byteLength);
        applyRawPatternBytesToLevel(slice, destTile);
    }

    private void applyPatternsToLevel(Pattern[] sourcePatterns, int destTile) {
        if (sourcePatterns == null || sourcePatterns.length == 0) {
            return;
        }

        int maxPatterns = level.getPatternCount();
        boolean canUpdateTextures = graphicsManager.isGlInitialized();
        for (int i = 0; i < sourcePatterns.length; i++) {
            int destIndex = destTile + i;
            if (destIndex >= maxPatterns) {
                break;
            }
            Pattern dest = level.getPattern(destIndex);
            dest.copyFrom(sourcePatterns[i]);
            if (canUpdateTextures) {
                graphicsManager.updatePatternTexture(dest, destIndex);
            }
        }
    }

    private void ensureHczPatternCapacity() {
        if (zoneIndex == 1 && actIndex == 0) {
            level.ensurePatternCapacity(0x300 + (Sonic3kConstants.ART_UNC_FIX_HCZ1_BG_STRIP_SIZE
                    / Pattern.PATTERN_SIZE_IN_ROM));
        } else if (zoneIndex == 1 && actIndex == 1) {
            level.ensurePatternCapacity(0x2EE + (Sonic3kConstants.ART_UNC_HCZ2_4_SIZE
                    / Pattern.PATTERN_SIZE_IN_ROM));
        }
    }

    private byte[] loadRawBytes(RomByteReader reader, int addr, int size) {
        if (addr + size > reader.size()) {
            return null;
        }
        return reader.slice(addr, size);
    }

    private Pattern[] loadUncompressedPatterns(RomByteReader reader, int addr, int size) {
        if (addr + size > reader.size()) {
            return null;
        }

        byte[] data = reader.slice(addr, size);
        Pattern[] patterns = new Pattern[size / Pattern.PATTERN_SIZE_IN_ROM];
        for (int i = 0; i < patterns.length; i++) {
            Pattern pattern = new Pattern();
            byte[] tileData = new byte[Pattern.PATTERN_SIZE_IN_ROM];
            System.arraycopy(data, i * Pattern.PATTERN_SIZE_IN_ROM, tileData, 0,
                    Pattern.PATTERN_SIZE_IN_ROM);
            pattern.fromSegaFormat(tileData);
            patterns[i] = pattern;
        }
        return patterns;
    }

    private int computeHcz1WaterlineDelta() {
        int delta = (short) (getCameraY() - HCZ1_EQUILIBRIUM_Y);
        int quarterDelta = delta >> 2;
        return (short) (quarterDelta - delta);
    }

    private int[] buildHcz2HScrollValues(int cameraX) {
        int[] hScroll = new int[24];
        int d0 = ((short) cameraX) << 16;
        d0 >>= 1;
        int d1 = d0 >> 3;

        int pos = 0;
        while (pos < HCZ2_DEFORM_INDEX.length) {
            int count = HCZ2_DEFORM_INDEX[pos++];
            if ((count & 0x80) != 0) {
                break;
            }
            int value = (short) (d0 >> 16);
            for (int i = 0; i <= count; i++) {
                int byteOffset = HCZ2_DEFORM_INDEX[pos++];
                hScroll[byteOffset >> 1] = value;
            }
            d0 -= d1;
        }
        return hScroll;
    }

    private int getCameraX() {
        try {
            return GameServices.camera().getX();
        } catch (Exception e) {
            LOG.fine(() -> "Sonic3kPatternAnimator.getCameraX: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Gumball bonus stage animated tiles.
     * ROM: AnimateTiles_Gumball (sonic3k.asm:55266).
     * <p>
     * Computes a scroll index from (Events_bg+$10 - Camera_Y_BG) &amp; $1F,
     * uses it as a byte offset into ArtUnc_AniGumball, then DMA copies
     * 0x54 tiles to VRAM tile $40. Creates a vertical scrolling effect
     * in the background tile art.
     */
    private void updateGumball() {
        if (gumballAniData == null) {
            return;
        }
        // ROM: d1 = (Events_bg+$10) - Camera_Y_pos_BG_copy
        // For the bonus stage, Events_bg+$10 is the BG event Y offset.
        // The camera is locked in the gumball bonus stage, so we drive the
        // animation from a per-frame counter instead of camera Y. This
        // produces constant vertical scrolling of the BG tile art.
        gumballFrameCounter = (gumballFrameCounter + 1) & 0x1F;
        int index = gumballFrameCounter;

        if (index == lastGumballIndex) {
            return; // No change — skip DMA
        }
        lastGumballIndex = index;

        // ROM: d1 = d1 * 4; source = ArtUnc_AniGumball + d1
        int sourceByteOffset = index * 4;
        if (sourceByteOffset + GUMBALL_DMA_SIZE > gumballAniData.length) {
            return; // Bounds check
        }

        // Copy 0x54 tiles from source offset to level patterns at tile 0x40
        level.ensurePatternCapacity(GUMBALL_DEST_TILE + GUMBALL_TILE_COUNT);
        boolean canUpdateTextures = graphicsManager.isGlInitialized();
        for (int i = 0; i < GUMBALL_TILE_COUNT; i++) {
            int srcOffset = sourceByteOffset + i * Pattern.PATTERN_SIZE_IN_ROM;
            if (srcOffset + Pattern.PATTERN_SIZE_IN_ROM > gumballAniData.length) {
                break;
            }
            int destIndex = GUMBALL_DEST_TILE + i;
            if (destIndex >= level.getPatternCount()) {
                break;
            }
            Pattern dest = level.getPattern(destIndex);
            byte[] tileData = new byte[Pattern.PATTERN_SIZE_IN_ROM];
            System.arraycopy(gumballAniData, srcOffset, tileData, 0, Pattern.PATTERN_SIZE_IN_ROM);
            dest.fromSegaFormat(tileData);
            if (canUpdateTextures) {
                graphicsManager.updatePatternTexture(dest, destIndex);
            }
        }
    }

    private int getCameraY() {
        try {
            return GameServices.camera().getY();
        } catch (Exception e) {
            LOG.fine(() -> "Sonic3kPatternAnimator.getCameraY: " + e.getMessage());
            return 0;
        }
    }

    private static int resolveAniPlcAddr(int zoneIndex, int actIndex) {
        return switch (zoneIndex) {
            case 0 -> actIndex == 0
                    ? Sonic3kConstants.ANIPLC_AIZ1_ADDR
                    : Sonic3kConstants.ANIPLC_AIZ2_ADDR;
            case 1 -> actIndex == 0
                    ? Sonic3kConstants.ANIPLC_HCZ1_ADDR
                    : Sonic3kConstants.ANIPLC_HCZ2_ADDR;
            default -> -1;
        };
    }

    private static int word(int value) {
        return value & 0xFFFF;
    }

    private static int negateWord(int value) {
        return word(-value);
    }

    private static int ror16(int value, int bits) {
        int masked = value & 0xFFFF;
        int shift = bits & 15;
        return ((masked >>> shift) | (masked << (16 - shift))) & 0xFFFF;
    }
}
