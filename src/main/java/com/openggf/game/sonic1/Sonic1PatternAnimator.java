package com.openggf.game.sonic1;

import com.openggf.data.RomByteReader;
import com.openggf.game.OscillationManager;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AnimatedPatternManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Sonic 1 animated tile system.
 *
 * <p>Unlike Sonic 2's data-driven animation scripts, Sonic 1 uses hardcoded
 * per-zone animation routines (see {@code _inc/AnimateLevelGfx.asm} in the
 * Sonic 1 disassembly). This class replicates that behavior using static
 * animation definitions per zone.
 *
 * <p>Supported zones:
 * <ul>
 *   <li>GHZ - Waterfall (2 frames), Big Flower (2 frames), Small Flower (4-step sequence)</li>
 *   <li>MZ - Lava surface (3 frames), Torch (4 frames)</li>
 *   <li>SBZ - Smoke Puff 1 (7 frames + 3s interval), Smoke Puff 2 (7 frames + 2s interval)</li>
 * </ul>
 */
class Sonic1PatternAnimator implements AnimatedPatternManager {

    private final Level level;
    private final GraphicsManager graphicsManager = GraphicsManager.getInstance();
    private final List<AnimHandler> handlers;

    Sonic1PatternAnimator(RomByteReader reader, Level level, int zoneIndex) {
        this.level = level;
        this.handlers = createHandlers(reader, zoneIndex);
        ensurePatternCapacity();
        primeAll();
    }

    @Override
    public void update() {
        for (AnimHandler handler : handlers) {
            handler.tick(level, graphicsManager);
        }
    }

    private List<AnimHandler> createHandlers(RomByteReader reader, int zoneIndex) {
        return switch (zoneIndex) {
            case Sonic1Constants.ZONE_GHZ -> createGhzHandlers(reader);
            case Sonic1Constants.ZONE_MZ -> createMzHandlers(reader);
            case Sonic1Constants.ZONE_SBZ -> createSbzHandlers(reader);
            default -> List.of();
        };
    }

    // ===== GHZ animations =====

    private List<AnimHandler> createGhzHandlers(RomByteReader reader) {
        List<AnimHandler> list = new ArrayList<>(3);

        // Waterfall: 2 frames, 8 tiles each, duration 5
        list.add(new CyclingAnim(
                loadArt(reader, Sonic1Constants.ART_UNC_GHZ_WATER_ADDR, 16),
                Sonic1Constants.ARTTILE_GHZ_WATERFALL,
                8, // tilesPerFrame
                new int[]{0, 8}, // frame 0 → tile 0, frame 1 → tile 8
                5, // duration (6-1)
                null // uniform duration
        ));

        // Big Flower: 2 frames, 16 tiles each, duration 15
        list.add(new CyclingAnim(
                loadArt(reader, Sonic1Constants.ART_UNC_GHZ_FLOWER1_ADDR, 32),
                Sonic1Constants.ARTTILE_GHZ_BIG_FLOWER_1,
                16,
                new int[]{0, 16},
                15, // 0x10-1
                null
        ));

        // Small Flower: sequence {0,1,2,1}, 12 tiles, variable duration
        // Frames 0 and 2 (even sequence values) get long duration (0x7F),
        // frames 1 and 3 (odd sequence values) get short duration (7).
        list.add(new CyclingAnim(
                loadArt(reader, Sonic1Constants.ART_UNC_GHZ_FLOWER2_ADDR, 36),
                Sonic1Constants.ARTTILE_GHZ_SMALL_FLOWER,
                12,
                new int[]{0, 12, 24, 12}, // sequence 0,1,2,1 → tile offsets
                7, // default duration (8-1), overridden per-frame
                new int[]{0x7F, 7, 0x7F, 7}
        ));

        return list;
    }

    // ===== MZ animations =====

    private List<AnimHandler> createMzHandlers(RomByteReader reader) {
        List<AnimHandler> list = new ArrayList<>(3);

        // Lava surface: 3 frames, 8 tiles each, duration 19.
        MzLavaSurfaceAnim lavaSurface = new MzLavaSurfaceAnim(
                loadArt(reader, Sonic1Constants.ART_UNC_MZ_LAVA1_ADDR, 24));
        list.add(lavaSurface);

        // Magma body: 16 tiles derived from Art_MzLava2 using oscillation-based row shifts.
        // Mirrors AniArt_MZ_Magma in the disassembly.
        list.add(new MzMagmaAnim(
                loadArt(reader, Sonic1Constants.ART_UNC_MZ_LAVA2_ADDR, 48),
                lavaSurface));

        // Torch: 4 frames, 6 tiles each, duration 7
        list.add(new CyclingAnim(
                loadArt(reader, Sonic1Constants.ART_UNC_MZ_TORCH_ADDR, 24),
                Sonic1Constants.ARTTILE_MZ_TORCH,
                6,
                new int[]{0, 6, 12, 18},
                7, // 8-1
                null
        ));

        return list;
    }

    // ===== SBZ animations =====

    private List<AnimHandler> createSbzHandlers(RomByteReader reader) {
        Pattern[] smokeArt = loadArt(reader, Sonic1Constants.ART_UNC_SBZ_SMOKE_ADDR, 84);
        List<AnimHandler> list = new ArrayList<>(2);

        // Smoke Puff 1: 7 visible frames, 12 tiles, duration 7, 180-frame interval
        list.add(new SmokePuffAnim(smokeArt, Sonic1Constants.ARTTILE_SBZ_SMOKE_PUFF_1, 12, 7, 180));

        // Smoke Puff 2: 7 visible frames, 12 tiles, duration 7, 120-frame interval
        list.add(new SmokePuffAnim(smokeArt, Sonic1Constants.ARTTILE_SBZ_SMOKE_PUFF_2, 12, 7, 120));

        return list;
    }

    // ===== Art loading =====

    private Pattern[] loadArt(RomByteReader reader, int romAddr, int tileCount) {
        int byteCount = tileCount * Pattern.PATTERN_SIZE_IN_ROM;
        if (romAddr < 0 || romAddr + byteCount > reader.size()) {
            return new Pattern[0];
        }
        byte[] data = reader.slice(romAddr, byteCount);
        Pattern[] patterns = new Pattern[tileCount];
        for (int i = 0; i < tileCount; i++) {
            Pattern pattern = new Pattern();
            int start = i * Pattern.PATTERN_SIZE_IN_ROM;
            byte[] tileBytes = new byte[Pattern.PATTERN_SIZE_IN_ROM];
            System.arraycopy(data, start, tileBytes, 0, Pattern.PATTERN_SIZE_IN_ROM);
            pattern.fromSegaFormat(tileBytes);
            patterns[i] = pattern;
        }
        return patterns;
    }

    // ===== Capacity and priming =====

    private void ensurePatternCapacity() {
        for (AnimHandler handler : handlers) {
            int required = handler.requiredPatternCount();
            if (required > 0) {
                level.ensurePatternCapacity(required);
            }
        }
    }

    private void primeAll() {
        for (AnimHandler handler : handlers) {
            handler.prime(level, graphicsManager);
        }
    }

    // ===== Animation handler interface =====

    private interface AnimHandler {
        void tick(Level level, GraphicsManager gm);
        void prime(Level level, GraphicsManager gm);
        int requiredPatternCount();
    }

    // ===== Simple cycling animation =====

    /**
     * Handles animations that cycle through a sequence of frames at a fixed
     * or per-frame duration. Covers GHZ waterfall, flowers, MZ lava, torch.
     */
    private static class CyclingAnim implements AnimHandler {
        private final Pattern[] artPatterns;
        private final int destTileIndex;
        private final int tilesPerFrame;
        private final int[] frameTileOffsets; // maps frame counter → source tile index
        private final int defaultDuration;
        private final int[] frameDurations; // per-frame durations (null = use defaultDuration)
        private int timer;
        private int frameCounter;

        CyclingAnim(Pattern[] artPatterns, int destTileIndex, int tilesPerFrame,
                     int[] frameTileOffsets, int defaultDuration, int[] frameDurations) {
            this.artPatterns = artPatterns;
            this.destTileIndex = destTileIndex;
            this.tilesPerFrame = tilesPerFrame;
            this.frameTileOffsets = frameTileOffsets;
            this.defaultDuration = defaultDuration;
            this.frameDurations = frameDurations;
            this.timer = 0;
            this.frameCounter = 0;
        }

        @Override
        public void tick(Level level, GraphicsManager gm) {
            if (artPatterns.length == 0 || frameTileOffsets.length == 0) return;

            if (timer > 0) {
                timer--;
                return;
            }

            int frame = frameCounter % frameTileOffsets.length;
            frameCounter = frame + 1;

            // Set timer for this frame
            timer = (frameDurations != null) ? frameDurations[frame] : defaultDuration;

            onFrameApplied(frame);
            applyFrame(level, gm, frameTileOffsets[frame]);
        }

        @Override
        public void prime(Level level, GraphicsManager gm) {
            if (artPatterns.length == 0 || frameTileOffsets.length == 0) return;
            onFrameApplied(0);
            applyFrame(level, gm, frameTileOffsets[0]);
        }

        @Override
        public int requiredPatternCount() {
            return destTileIndex + tilesPerFrame;
        }

        private void applyFrame(Level level, GraphicsManager gm, int srcTileOffset) {
            int maxPatterns = level.getPatternCount();
            boolean canUpdateTextures = gm.isGlInitialized();
            for (int i = 0; i < tilesPerFrame; i++) {
                int srcIndex = srcTileOffset + i;
                int destIndex = destTileIndex + i;
                if (srcIndex < 0 || srcIndex >= artPatterns.length) continue;
                if (destIndex < 0 || destIndex >= maxPatterns) continue;

                Pattern dest = level.getPattern(destIndex);
                dest.copyFrom(artPatterns[srcIndex]);
                if (canUpdateTextures) {
                    gm.updatePatternTexture(dest, destIndex);
                }
            }
        }

        protected void onFrameApplied(int frame) {
            // Optional hook for handlers that need the active frame index.
        }
    }

    /**
     * Lava surface state holder for MZ.
     * Exposes current frame index so magma animation can stay phase-locked.
     */
    private static final class MzLavaSurfaceAnim extends CyclingAnim {
        private int currentFrame;

        MzLavaSurfaceAnim(Pattern[] artPatterns) {
            super(artPatterns, Sonic1Constants.ARTTILE_MZ_ANIMATED_LAVA, 8,
                    new int[]{0, 8, 16}, 19, null);
            currentFrame = 0;
        }

        @Override
        protected void onFrameApplied(int frame) {
            currentFrame = frame;
        }

        int getCurrentFrame() {
            return currentFrame;
        }
    }

    /**
     * MZ magma body animation from AniArt_MZ_Magma.
     *
     * <p>The ROM rebuilds a 16-tile frame every 2 frames using 4 passes of row-wise
     * circular byte shifts driven by {@code v_oscillate+$A}. This reproduces the
     * same data transform on decoded Art_MzLava2 source tiles.
     */
    private static final class MzMagmaAnim implements AnimHandler {
        private static final int UPDATE_INTERVAL = 2; // move.b #2-1,v_lani1_time
        private static final int DEST_TILE_COUNT = 16;
        private static final int FRAME_BYTES = DEST_TILE_COUNT * Pattern.PATTERN_SIZE_IN_ROM; // 512
        private static final int ROW_SEGMENT_BYTES = 16;
        private static final int ROW_COUNT = 32;
        private static final int PASSES = 4;
        // AniArt reads (v_oscillate+$A). OscillationManager offsets are based at +2.
        private static final int MAGMA_OSC_OFFSET = 0x08;

        private final byte[] sourceBytes;
        private final MzLavaSurfaceAnim lavaSurfaceAnim;
        private final byte[] transformedFrame = new byte[FRAME_BYTES];
        private int timer;

        MzMagmaAnim(Pattern[] sourcePatterns, MzLavaSurfaceAnim lavaSurfaceAnim) {
            this.sourceBytes = toSegaBytes(sourcePatterns);
            this.lavaSurfaceAnim = lavaSurfaceAnim;
            this.timer = 0;
        }

        @Override
        public void tick(Level level, GraphicsManager gm) {
            if (sourceBytes.length < FRAME_BYTES) {
                return;
            }
            if (timer > 0) {
                timer--;
                return;
            }

            timer = UPDATE_INTERVAL - 1;
            applyFrame(level, gm);
        }

        @Override
        public void prime(Level level, GraphicsManager gm) {
            if (sourceBytes.length < FRAME_BYTES) {
                return;
            }
            applyFrame(level, gm);
        }

        @Override
        public int requiredPatternCount() {
            return Sonic1Constants.ARTTILE_MZ_ANIMATED_MAGMA + DEST_TILE_COUNT;
        }

        private void applyFrame(Level level, GraphicsManager gm) {
            int sourceFrame = lavaSurfaceAnim != null ? lavaSurfaceAnim.getCurrentFrame() % 3 : 0;
            int frameBase = sourceFrame * FRAME_BYTES;
            if (frameBase + FRAME_BYTES > sourceBytes.length) {
                return;
            }

            int osc = OscillationManager.getByte(MAGMA_OSC_OFFSET);
            int writePos = 0;
            for (int pass = 0; pass < PASSES; pass++) {
                int shift = osc & 0x0F;
                for (int row = 0; row < ROW_COUNT; row++) {
                    int segmentBase = frameBase + row * ROW_SEGMENT_BYTES;
                    transformedFrame[writePos] = sourceBytes[segmentBase + (shift & 0x0F)];
                    transformedFrame[writePos + 1] = sourceBytes[segmentBase + ((shift + 1) & 0x0F)];
                    transformedFrame[writePos + 2] = sourceBytes[segmentBase + ((shift + 2) & 0x0F)];
                    transformedFrame[writePos + 3] = sourceBytes[segmentBase + ((shift + 3) & 0x0F)];
                    writePos += 4;
                }
                osc = (osc + 4) & 0xFF;
            }

            int maxPatterns = level.getPatternCount();
            boolean canUpdateTextures = gm.isGlInitialized();
            byte[] tileBytes = new byte[Pattern.PATTERN_SIZE_IN_ROM];
            for (int tile = 0; tile < DEST_TILE_COUNT; tile++) {
                int destIndex = Sonic1Constants.ARTTILE_MZ_ANIMATED_MAGMA + tile;
                if (destIndex < 0 || destIndex >= maxPatterns) {
                    continue;
                }
                System.arraycopy(transformedFrame, tile * Pattern.PATTERN_SIZE_IN_ROM,
                        tileBytes, 0, Pattern.PATTERN_SIZE_IN_ROM);
                Pattern dest = level.getPattern(destIndex);
                dest.fromSegaFormat(tileBytes);
                if (canUpdateTextures) {
                    gm.updatePatternTexture(dest, destIndex);
                }
            }
        }

        private static byte[] toSegaBytes(Pattern[] patterns) {
            if (patterns == null || patterns.length == 0) {
                return new byte[0];
            }
            byte[] out = new byte[patterns.length * Pattern.PATTERN_SIZE_IN_ROM];
            int write = 0;
            for (Pattern pattern : patterns) {
                if (pattern == null) {
                    write += Pattern.PATTERN_SIZE_IN_ROM;
                    continue;
                }
                for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                    for (int x = 0; x < Pattern.PATTERN_WIDTH; x += 2) {
                        int left = pattern.getPixel(x, y) & 0x0F;
                        int right = pattern.getPixel(x + 1, y) & 0x0F;
                        out[write++] = (byte) ((left << 4) | right);
                    }
                }
            }
            return out;
        }
    }

    // ===== SBZ smoke puff animation =====

    /**
     * Replicates the SBZ smoke puff behavior from AniArt_SBZ.
     *
     * <p>The animation cycle is:
     * <ol>
     *   <li>Show 7 smoke frames (frames 1-7 of the counter, art frames 0-6)</li>
     *   <li>On frame 0, show blank tiles and start a long interval wait</li>
     *   <li>During the interval, no animation occurs</li>
     * </ol>
     *
     * <p>The "blank" state uses the first 6 tiles of the smoke art repeated
     * to fill the 12-tile destination, matching the original behavior.
     */
    private static class SmokePuffAnim implements AnimHandler {
        private final Pattern[] artPatterns;
        private final int destTileIndex;
        private final int tilesPerFrame;
        private final int frameDuration;
        private final int intervalDuration;
        private int frameTimer;
        private int intervalTimer;
        private int frameCounter;

        SmokePuffAnim(Pattern[] artPatterns, int destTileIndex, int tilesPerFrame,
                       int frameDuration, int intervalDuration) {
            this.artPatterns = artPatterns;
            this.destTileIndex = destTileIndex;
            this.tilesPerFrame = tilesPerFrame;
            this.frameDuration = frameDuration;
            this.intervalDuration = intervalDuration;
            // Start in interval wait (smoke not visible initially)
            this.frameTimer = 0;
            this.intervalTimer = intervalDuration;
            this.frameCounter = 0;
        }

        @Override
        public void tick(Level level, GraphicsManager gm) {
            if (artPatterns.length == 0) return;

            // If in interval wait, count down
            if (intervalTimer > 0) {
                intervalTimer--;
                return;
            }

            // Decrement frame timer
            if (frameTimer > 0) {
                frameTimer--;
                return;
            }

            // Advance frame
            int frame = frameCounter & 7; // 8-step counter (0-7)
            frameCounter = frame + 1;
            frameTimer = frameDuration;

            if (frame == 0) {
                // Frame 0 = blank; start interval wait
                intervalTimer = intervalDuration;
                applyClearSky(level, gm);
            } else {
                // Frames 1-7 → art frames 0-6
                int artOffset = (frame - 1) * tilesPerFrame;
                applyFrame(level, gm, artOffset);
            }
        }

        @Override
        public void prime(Level level, GraphicsManager gm) {
            // Start with clear sky
            applyClearSky(level, gm);
        }

        @Override
        public int requiredPatternCount() {
            return destTileIndex + tilesPerFrame;
        }

        private void applyFrame(Level level, GraphicsManager gm, int srcTileOffset) {
            int maxPatterns = level.getPatternCount();
            boolean canUpdateTextures = gm.isGlInitialized();
            for (int i = 0; i < tilesPerFrame; i++) {
                int srcIndex = srcTileOffset + i;
                int destIndex = destTileIndex + i;
                if (srcIndex < 0 || srcIndex >= artPatterns.length) continue;
                if (destIndex < 0 || destIndex >= maxPatterns) continue;

                Pattern dest = level.getPattern(destIndex);
                dest.copyFrom(artPatterns[srcIndex]);
                if (canUpdateTextures) {
                    gm.updatePatternTexture(dest, destIndex);
                }
            }
        }

        /**
         * Fill destination with the first half of the smoke art repeated.
         * This matches the original: first 6 tiles loaded, then a1 reset and
         * another 6 tiles loaded from the start of Art_SbzSmoke.
         */
        private void applyClearSky(Level level, GraphicsManager gm) {
            int maxPatterns = level.getPatternCount();
            boolean canUpdateTextures = gm.isGlInitialized();
            int half = tilesPerFrame / 2;
            for (int i = 0; i < tilesPerFrame; i++) {
                int srcIndex = i % half; // wraps at half to duplicate first 6 tiles
                int destIndex = destTileIndex + i;
                if (srcIndex >= artPatterns.length) continue;
                if (destIndex < 0 || destIndex >= maxPatterns) continue;

                Pattern dest = level.getPattern(destIndex);
                dest.copyFrom(artPatterns[srcIndex]);
                if (canUpdateTextures) {
                    gm.updatePatternTexture(dest, destIndex);
                }
            }
        }
    }
}
