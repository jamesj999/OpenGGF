package com.openggf.game.sonic3k.objects;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Pattern;
import com.openggf.level.Level;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.util.PatternDecompressor;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public final class IczSnowboardArtLoader {
    private static final Logger LOG = Logger.getLogger(IczSnowboardArtLoader.class.getName());

    private static final int SONIC_SNOWBOARD_PATTERN_BASE = 0x4A000;
    private static final int SNOWBOARD_PATTERN_BASE = 0x4C000;

    private static PatternSpriteRenderer sonicRenderer;
    private static PatternSpriteRenderer snowboardRenderer;
    private static PatternSpriteRenderer dustRenderer;
    private static boolean loaded;

    private IczSnowboardArtLoader() {
    }

    static synchronized PatternSpriteRenderer sonicSnowboardRenderer() {
        loadIfNeeded();
        return sonicRenderer;
    }

    static synchronized PatternSpriteRenderer snowboardRenderer() {
        loadIfNeeded();
        return snowboardRenderer;
    }

    static synchronized PatternSpriteRenderer dustRenderer() {
        loadIfNeeded();
        return dustRenderer;
    }

    public static synchronized void reset() {
        sonicRenderer = null;
        snowboardRenderer = null;
        dustRenderer = null;
        loaded = false;
    }

    private static void loadIfNeeded() {
        if (loaded) {
            return;
        }
        try {
            Rom rom = GameServices.rom().getRom();
            RomByteReader reader = RomByteReader.fromRom(rom);
            sonicRenderer = buildDplcRenderer(
                    rom,
                    reader,
                    Sonic3kConstants.ART_UNC_SONIC_SNOWBOARD_ADDR,
                    Sonic3kConstants.ART_UNC_SONIC_SNOWBOARD_SIZE,
                    Sonic3kConstants.MAP_SONIC_SNOWBOARD_ADDR,
                    Sonic3kConstants.MAP_SONIC_SNOWBOARD_FRAMES,
                    Sonic3kConstants.DPLC_SONIC_SNOWBOARD_ADDR,
                    SONIC_SNOWBOARD_PATTERN_BASE,
                    0);
            snowboardRenderer = buildDplcRenderer(
                    rom,
                    reader,
                    Sonic3kConstants.ART_UNC_SNOWBOARD_ADDR,
                    Sonic3kConstants.ART_UNC_SNOWBOARD_SIZE,
                    Sonic3kConstants.MAP_SNOWBOARD_ADDR,
                    Sonic3kConstants.MAP_SNOWBOARD_FRAMES,
                    Sonic3kConstants.DPLC_SNOWBOARD_ADDR,
                    SNOWBOARD_PATTERN_BASE,
                    0);
            dustRenderer = buildLevelRenderer(
                    reader,
                    Sonic3kConstants.ARTTILE_SNOWBOARD_DUST,
                    4,
                    Sonic3kConstants.MAP_SNOWBOARD_DUST_ADDR,
                    Sonic3kConstants.MAP_SNOWBOARD_DUST_FRAMES,
                    0);
            loaded = true;
        } catch (Exception e) {
            LOG.log(java.util.logging.Level.WARNING, "Failed to load ICZ snowboard intro art", e);
            sonicRenderer = null;
            snowboardRenderer = null;
            dustRenderer = null;
            loaded = false;
        }
    }

    private static PatternSpriteRenderer buildDplcRenderer(
            Rom rom,
            RomByteReader reader,
            int artAddr,
            int artSize,
            int mapAddr,
            int frameCount,
            int dplcAddr,
            int patternBase,
            int palette) throws IOException {
        byte[] artBytes = rom.readBytes(artAddr, artSize);
        Pattern[] sourcePatterns = PatternDecompressor.fromBytes(artBytes);
        List<SpriteMappingFrame> mappings = S3kSpriteDataLoader.loadMappingFrames(reader, mapAddr, frameCount);
        List<SpriteDplcFrame> dplcs = S3kSpriteDataLoader.loadDplcFrames(reader, dplcAddr, frameCount);
        int bankSize = Math.max(1, S3kSpriteDataLoader.resolveBankSize(dplcs, mappings));
        Pattern[] bankPatterns = new Pattern[bankSize];
        for (int i = 0; i < bankPatterns.length; i++) {
            bankPatterns[i] = new Pattern();
        }
        PatternSpriteRenderer renderer = new DplcPatternSpriteRenderer(
                new ObjectSpriteSheet(bankPatterns, mappings, palette, 1),
                sourcePatterns,
                dplcs);
        renderer.ensurePatternsCached(GameServices.graphics(), patternBase);
        return renderer;
    }

    private static PatternSpriteRenderer buildLevelRenderer(
            RomByteReader reader,
            int artTileBase,
            int patternCount,
            int mapAddr,
            int frameCount,
            int palette) {
        Level level = GameServices.level().getCurrentLevel();
        Pattern[] patterns = new Pattern[patternCount];
        for (int i = 0; i < patternCount; i++) {
            int levelIndex = artTileBase + i;
            patterns[i] = level != null && levelIndex < level.getPatternCount()
                    ? level.getPattern(levelIndex)
                    : new Pattern();
        }
        List<SpriteMappingFrame> mappings = S3kSpriteDataLoader.loadMappingFrames(reader, mapAddr, frameCount);
        PatternSpriteRenderer renderer = new PatternSpriteRenderer(
                new ObjectSpriteSheet(patterns, mappings, palette, 1));
        renderer.ensurePatternsCached(GameServices.graphics(), 0x4C100);
        return renderer;
    }

    private static final class DplcPatternSpriteRenderer extends PatternSpriteRenderer {
        private final Pattern[] bankPatterns;
        private final Pattern[] sourcePatterns;
        private final List<SpriteDplcFrame> dplcFrames;
        private int lastDplcFrame = -1;

        private DplcPatternSpriteRenderer(
                ObjectSpriteSheet sheet,
                Pattern[] sourcePatterns,
                List<SpriteDplcFrame> dplcFrames) {
            super(sheet);
            this.bankPatterns = sheet.getPatterns();
            this.sourcePatterns = sourcePatterns;
            this.dplcFrames = dplcFrames;
        }

        @Override
        public void drawFrameIndex(int frameIndex, int originX, int originY,
                boolean hFlip, boolean vFlip, int paletteOverride) {
            applyDplc(frameIndex);
            super.drawFrameIndex(frameIndex, originX, originY, hFlip, vFlip, paletteOverride);
        }

        private void applyDplc(int frameIndex) {
            if (frameIndex == lastDplcFrame || frameIndex < 0 || frameIndex >= dplcFrames.size()) {
                return;
            }
            SpriteDplcFrame frame = dplcFrames.get(frameIndex);
            if (frame == null || frame.requests().isEmpty()) {
                return;
            }
            int destination = 0;
            for (TileLoadRequest request : frame.requests()) {
                int count = Math.max(0, request.count());
                int sourceStart = Math.max(0, request.startTile());
                for (int i = 0; i < count && destination < bankPatterns.length; i++) {
                    int sourceIndex = sourceStart + i;
                    if (sourceIndex >= 0 && sourceIndex < sourcePatterns.length) {
                        bankPatterns[destination].copyFrom(sourcePatterns[sourceIndex]);
                    }
                    destination++;
                }
            }
            updatePatternRange(GameServices.graphics(), 0, Math.min(destination, bankPatterns.length));
            lastDplcFrame = frameIndex;
        }
    }
}
