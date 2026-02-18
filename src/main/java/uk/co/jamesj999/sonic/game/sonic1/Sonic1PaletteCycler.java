package uk.co.jamesj999.sonic.game.sonic1;

import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.Palette;
import uk.co.jamesj999.sonic.level.animation.AnimatedPaletteManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Sonic 1 palette cycling (PalCycle_* routines from _inc/PaletteCycle.asm).
 *
 * <p>Supported zones:
 * <ul>
 *   <li>GHZ - Water/waterfall body cycling (4 colors in palette line 2)</li>
 *   <li>LZ - Waterfall cycling (4 colors in palette line 2)</li>
 *   <li>SLZ - Neon light cycling (3 non-contiguous colors in palette line 2)</li>
 *   <li>SYZ - Two synchronized groups (palette line 3)</li>
 *   <li>SBZ - Per-entry script system (Acts 1 &amp; 2) plus conveyor belt rotation</li>
 * </ul>
 *
 * <p>MZ has no palette cycling. LZ conveyor belt cycling (direction-dependent)
 * is not yet implemented.
 *
 * <p>Palette cycle data is embedded directly from the s1disasm binary files
 * (palette/Cycle - *.bin) since the data is small and fixed.
 */
class Sonic1PaletteCycler implements AnimatedPaletteManager {
    private final Level level;
    private final GraphicsManager graphicsManager = GraphicsManager.getInstance();
    private final List<PaletteCycle> cycles;

    Sonic1PaletteCycler(Level level, int zoneIndex) {
        this.level = level;
        this.cycles = createCycles(zoneIndex);
    }

    @Override
    public void update() {
        for (PaletteCycle cycle : cycles) {
            cycle.tick(level, graphicsManager);
        }
    }

    private List<PaletteCycle> createCycles(int zoneIndex) {
        return switch (zoneIndex) {
            case Sonic1Constants.ZONE_GHZ -> List.of(createGhzCycle());
            case Sonic1Constants.ZONE_LZ -> createLzCycles();
            case Sonic1Constants.ZONE_SLZ -> List.of(new SlzCycle());
            case Sonic1Constants.ZONE_SYZ -> List.of(new SyzCycle());
            case Sonic1Constants.ZONE_SBZ -> createSbzCycles();
            default -> List.of();
        };
    }

    // ===== GHZ =====

    /**
     * GHZ water surface cycling.
     * <pre>
     * PalCycle_GHZ: timer 5, 4 frames, 4 colors at v_palette+$50
     * Palette line 2 (index 2), colors 8,9,10,11
     * Data rotates 4 shades of blue to create flowing water effect
     * </pre>
     */
    private PaletteCycle createGhzCycle() {
        return new SimpleCycle(PAL_GHZ_CYC, 4, 8, 5, 2, new int[]{8, 9, 10, 11});
    }

    // ===== LZ =====

    /**
     * LZ waterfall cycling.
     * <pre>
     * PalCycle_LZ: timer 2, 4 frames, 4 colors at v_palette+$56
     * Palette line 2 (index 2), colors 11,12,13,14
     * Also applies to underwater palette (not yet implemented)
     * </pre>
     */
    private List<PaletteCycle> createLzCycles() {
        List<PaletteCycle> list = new ArrayList<>(1);
        list.add(new SimpleCycle(PAL_LZ_CYC1, 4, 8, 2, 2, new int[]{11, 12, 13, 14}));
        return list;
    }

    // ===== SBZ =====

    /**
     * SBZ palette cycling (PalCycle_SBZ from _inc/PaletteCycle.asm:194-266).
     *
     * <p>Two components run each frame:
     * <ol>
     *   <li>Per-entry script: each entry independently cycles one palette color
     *       through a source data table at its own rate. Act 1 has 9 entries,
     *       Act 2 has 7 entries (from Pal_SBZCycList1 / Pal_SBZCycList2).</li>
     *   <li>Conveyor belt: rotates 3 colors at palette line 2, colors 12-14.
     *       Act 1 uses SBZCyc4 every 2 frames, Act 2 uses SBZCyc10 every frame.</li>
     * </ol>
     */
    private List<PaletteCycle> createSbzCycles() {
        int act = LevelManager.getInstance().getCurrentAct();
        List<PaletteCycle> list = new ArrayList<>();

        if (act == 0) {
            // Pal_SBZCycList1: 9 per-entry cycles
            list.add(new SbzColorCycle(PAL_SBZ_CYC1, 0, 8, 7, 2, 8));
            list.add(new SbzColorCycle(PAL_SBZ_CYC2, 0, 8, 13, 2, 9));
            list.add(new SbzColorCycle(PAL_SBZ_CYC3, 0, 8, 14, 3, 7));
            list.add(new SbzColorCycle(PAL_SBZ_CYC5, 0, 8, 11, 3, 8));
            list.add(new SbzColorCycle(PAL_SBZ_CYC6, 0, 8, 7, 3, 9));
            list.add(new SbzColorCycle(PAL_SBZ_CYC7, 0, 16, 28, 3, 15));
            list.add(new SbzColorCycle(PAL_SBZ_CYC8, 0, 3, 3, 3, 12));
            list.add(new SbzColorCycle(PAL_SBZ_CYC8, 2, 3, 3, 3, 13));
            list.add(new SbzColorCycle(PAL_SBZ_CYC8, 4, 3, 3, 3, 14));
            // Conveyor: SBZCyc4, timer reset 1 (every 2 frames)
            list.add(new SbzConveyorCycle(PAL_SBZ_CYC4, 1));
        } else {
            // Pal_SBZCycList2: 7 per-entry cycles
            list.add(new SbzColorCycle(PAL_SBZ_CYC1, 0, 8, 7, 2, 8));
            list.add(new SbzColorCycle(PAL_SBZ_CYC2, 0, 8, 13, 2, 9));
            list.add(new SbzColorCycle(PAL_SBZ_CYC9, 0, 8, 9, 3, 8));
            list.add(new SbzColorCycle(PAL_SBZ_CYC6, 0, 8, 7, 3, 9));
            list.add(new SbzColorCycle(PAL_SBZ_CYC8, 0, 3, 3, 3, 12));
            list.add(new SbzColorCycle(PAL_SBZ_CYC8, 2, 3, 3, 3, 13));
            list.add(new SbzColorCycle(PAL_SBZ_CYC8, 4, 3, 3, 3, 14));
            // Conveyor: SBZCyc10, timer reset 0 (every frame)
            list.add(new SbzConveyorCycle(PAL_SBZ_CYC10, 0));
        }

        return list;
    }

    // ===== Palette cycle interface =====

    private interface PaletteCycle {
        void tick(Level level, GraphicsManager gm);
    }

    // ===== Simple cycling (contiguous colors, uniform frame stride) =====

    /**
     * Covers GHZ, LZ, and SYZ cycle 1 patterns: timer-based, contiguous colors,
     * fixed frame size. Matches the assembly pattern:
     * <pre>
     * subq.w #1,(v_pcyc_time).w
     * bpl.s  skip
     * move.w #N,(v_pcyc_time).w
     * move.w (v_pcyc_num).w,d0
     * addq.w #1,(v_pcyc_num).w
     * andi.w #M,d0
     * </pre>
     */
    private static class SimpleCycle implements PaletteCycle {
        private final byte[] data;
        private final int frameCount;
        private final int frameSize;
        private final int timerReset;
        private final int paletteIndex;
        private final int[] colorIndices;
        private int timer;
        private int frame;

        SimpleCycle(byte[] data, int frameCount, int frameSize,
                    int timerReset, int paletteIndex, int[] colorIndices) {
            this.data = data;
            this.frameCount = frameCount;
            this.frameSize = frameSize;
            this.timerReset = timerReset;
            this.paletteIndex = paletteIndex;
            this.colorIndices = colorIndices;
        }

        @Override
        public void tick(Level level, GraphicsManager gm) {
            if (timer > 0) {
                timer--;
                return;
            }
            timer = timerReset;

            int frameIndex = frame % frameCount;
            frame++;

            Palette palette = level.getPalette(paletteIndex);
            int base = frameIndex * frameSize;
            for (int i = 0; i < colorIndices.length; i++) {
                int dataIndex = base + i * 2;
                if (dataIndex + 1 < data.length) {
                    palette.getColor(colorIndices[i]).fromSegaFormat(data, dataIndex);
                }
            }

            if (gm.isGlInitialized()) {
                gm.cachePaletteTexture(palette, paletteIndex);
            }
        }
    }

    // ===== SLZ cycle (non-contiguous: color 11, skip 12, colors 13,14) =====

    /**
     * SLZ neon light cycling.
     * <pre>
     * PalCycle_SLZ: timer 7, 6 frames, 6 bytes per frame
     * Palette line 2 (index 2)
     * Colors 11, 13, 14 (non-contiguous: skips color 12)
     * Assembly increments frame before applying (frame 1 first)
     * </pre>
     */
    private static class SlzCycle implements PaletteCycle {
        private int timer;
        private int frame;

        @Override
        public void tick(Level level, GraphicsManager gm) {
            if (timer > 0) {
                timer--;
                return;
            }
            timer = 7;

            // SLZ increments frame before applying, wraps at 6
            frame++;
            if (frame >= 6) frame = 0;

            int d0 = frame * 6;
            Palette palette = level.getPalette(2);

            // move.w (a0,d0.w),(a1)       → color 11 from offset 0
            // move.l 2(a0,d0.w),4(a1)     → colors 13,14 from offset 2
            palette.getColor(11).fromSegaFormat(PAL_SLZ_CYC, d0);
            palette.getColor(13).fromSegaFormat(PAL_SLZ_CYC, d0 + 2);
            palette.getColor(14).fromSegaFormat(PAL_SLZ_CYC, d0 + 4);

            if (gm.isGlInitialized()) {
                gm.cachePaletteTexture(palette, 2);
            }
        }
    }

    // ===== SYZ combined cycle (both groups share timer and frame counter) =====

    /**
     * SYZ palette cycling - two synchronized groups.
     * <pre>
     * PalCycle_SYZ: timer 5, 4 frames, shared counter
     * Group 1: palette line 3 (index 3), colors 7,8,9,10 - 8 bytes/frame from SYZCyc1
     * Group 2: palette line 3 (index 3), colors 11,13 - 4 bytes/frame from SYZCyc2
     * Assembly uses d0 = frame*8 for group 1, d1 = frame*4 for group 2
     * </pre>
     */
    private static class SyzCycle implements PaletteCycle {
        private int timer;
        private int frame;

        @Override
        public void tick(Level level, GraphicsManager gm) {
            if (timer > 0) {
                timer--;
                return;
            }
            timer = 5;

            int frameIndex = frame & 3;
            frame = frameIndex + 1;

            Palette palette = level.getPalette(3);

            // Group 1: 4 contiguous colors at 8-byte stride
            int d0 = frameIndex * 8;
            palette.getColor(7).fromSegaFormat(PAL_SYZ_CYC1, d0);
            palette.getColor(8).fromSegaFormat(PAL_SYZ_CYC1, d0 + 2);
            palette.getColor(9).fromSegaFormat(PAL_SYZ_CYC1, d0 + 4);
            palette.getColor(10).fromSegaFormat(PAL_SYZ_CYC1, d0 + 6);

            // Group 2: 2 non-contiguous colors at 4-byte stride
            int d1 = frameIndex * 4;
            palette.getColor(11).fromSegaFormat(PAL_SYZ_CYC2, d1);
            palette.getColor(13).fromSegaFormat(PAL_SYZ_CYC2, d1 + 2);

            if (gm.isGlInitialized()) {
                gm.cachePaletteTexture(palette, 3);
            }
        }
    }

    // ===== SBZ per-entry color cycle (single palette color, own timer/frame) =====

    /**
     * Cycles a single palette color through a source data table.
     * Matches the assembly per-entry script loop at PalCycle_SBZ (loc_1AE0):
     * <pre>
     * v_pal_buffer layout per entry: [timer_byte, frame_byte]
     * On timer underflow: reset timer, increment frame (before use, wrapping
     * at frameCount), write sourceData[dataOffset + frame*2] to palette slot.
     * </pre>
     */
    private static class SbzColorCycle implements PaletteCycle {
        private final byte[] data;
        private final int dataOffset;
        private final int frameCount;
        private final int timerReset;
        private final int paletteIndex;
        private final int colorIndex;
        private int timer;  // starts at 0; first subq underflows to -1 → immediate trigger
        private int frame;  // starts at 0; incremented before use so first displayed is 1

        SbzColorCycle(byte[] data, int dataOffset, int frameCount, int timerReset,
                      int paletteIndex, int colorIndex) {
            this.data = data;
            this.dataOffset = dataOffset;
            this.frameCount = frameCount;
            this.timerReset = timerReset;
            this.paletteIndex = paletteIndex;
            this.colorIndex = colorIndex;
        }

        @Override
        public void tick(Level level, GraphicsManager gm) {
            // subq.b #1,(a1) / bmi.s loc_1AEA
            timer--;
            if (timer >= 0) return;

            // Reset timer to duration
            timer = timerReset;

            // Increment frame before use (addq.b #1,d0), wrap at frameCount
            frame++;
            if (frame >= frameCount) frame = 0;

            // Write single color: move.w (a0,d0.w),(a3)
            Palette palette = level.getPalette(paletteIndex);
            palette.getColor(colorIndex).fromSegaFormat(data, dataOffset + frame * 2);

            if (gm.isGlInitialized()) {
                gm.cachePaletteTexture(palette, paletteIndex);
            }
        }
    }

    // ===== SBZ conveyor belt cycling (3-color rotation) =====

    /**
     * Rotates 3 colors at palette line 2, colors 12-14 using a sliding window
     * over doubled source data (6 words = 3 unique colors repeated).
     * <pre>
     * PalCycle_SBZ conveyor section (loc_1B2E-loc_1B64):
     * Default direction = -1 (frame sequence: 2,1,0,2,1,0...)
     * Act 1: SBZCyc4, timer reset 1 (every 2 frames)
     * Act 2: SBZCyc10, timer reset 0 (every frame)
     * </pre>
     * Conveyor reversal (f_conveyrev) is not yet wired to object system.
     */
    private static class SbzConveyorCycle implements PaletteCycle {
        private final byte[] data;
        private final int timerReset;
        private int timer;  // starts at 0
        private int frame;  // 0-2

        SbzConveyorCycle(byte[] data, int timerReset) {
            this.data = data;
            this.timerReset = timerReset;
        }

        @Override
        public void tick(Level level, GraphicsManager gm) {
            // subq.w #1,(v_pcyc_time).w / bpl.s locret_1B64
            timer--;
            if (timer >= 0) return;
            timer = timerReset;

            // Advance frame with direction -1, wrapping 0-2
            // Assembly: d0 = frame & 3, d0 += direction, wrap if out of range
            int d0 = frame - 1;  // direction = -1
            if (d0 < 0) d0 = 2;
            frame = d0;

            // Sliding window: move.l (a0,d0.w),(a1)+ / move.w 4(a0,d0.w),(a1)
            int offset = frame * 2;
            Palette palette = level.getPalette(2);
            palette.getColor(12).fromSegaFormat(data, offset);
            palette.getColor(13).fromSegaFormat(data, offset + 2);
            palette.getColor(14).fromSegaFormat(data, offset + 4);

            if (gm.isGlInitialized()) {
                gm.cachePaletteTexture(palette, 2);
            }
        }
    }

    // ===== Embedded palette cycle data (from s1disasm/palette/Cycle - *.bin) =====

    /** GHZ: 32 bytes, 4 frames x 8 bytes (4 colors each). Rotation pattern. */
    private static final byte[] PAL_GHZ_CYC = {
            0x0a, (byte) 0x86, 0x0e, (byte) 0x86, 0x0e, (byte) 0xa8, 0x0e, (byte) 0xca,
            0x0e, (byte) 0xca, 0x0a, (byte) 0x86, 0x0e, (byte) 0x86, 0x0e, (byte) 0xa8,
            0x0e, (byte) 0xa8, 0x0e, (byte) 0xca, 0x0a, (byte) 0x86, 0x0e, (byte) 0x86,
            0x0e, (byte) 0x86, 0x0e, (byte) 0xa8, 0x0e, (byte) 0xca, 0x0a, (byte) 0x86
    };

    /** LZ Waterfall: 32 bytes, 4 frames x 8 bytes (4 colors each). */
    private static final byte[] PAL_LZ_CYC1 = {
            0x06, (byte) 0x80, 0x0c, (byte) 0xe6, 0x0a, (byte) 0xc4, 0x08, (byte) 0xa2,
            0x0c, (byte) 0xe6, 0x0a, (byte) 0xc4, 0x08, (byte) 0xa2, 0x06, (byte) 0x80,
            0x0a, (byte) 0xc4, 0x08, (byte) 0xa2, 0x06, (byte) 0x80, 0x0c, (byte) 0xe6,
            0x08, (byte) 0xa2, 0x06, (byte) 0x80, 0x0c, (byte) 0xe6, 0x0a, (byte) 0xc4
    };

    /** SLZ: 36 bytes, 6 frames x 6 bytes (3 non-contiguous colors). */
    private static final byte[] PAL_SLZ_CYC = {
            0x0e, (byte) 0xe0, 0x00, 0x06, 0x00, 0x66,
            0x0a, (byte) 0xa0, 0x00, 0x0a, 0x00, 0x22,
            0x06, 0x60, 0x00, 0x0e, 0x00, 0x66,
            0x02, 0x20, 0x00, 0x0a, 0x00, (byte) 0xaa,
            0x06, 0x60, 0x00, 0x06, 0x00, (byte) 0xee,
            0x0a, (byte) 0xa0, 0x00, 0x02, 0x00, (byte) 0xaa
    };

    /** SYZ cycle 1: 32 bytes, 4 frames x 8 bytes (4 contiguous colors). */
    private static final byte[] PAL_SYZ_CYC1 = {
            0x00, (byte) 0xee, 0x00, (byte) 0xaa, 0x00, 0x66, 0x00, 0x22,
            0x00, (byte) 0xaa, 0x00, 0x66, 0x00, 0x22, 0x00, (byte) 0xee,
            0x00, 0x66, 0x00, 0x22, 0x00, (byte) 0xee, 0x00, (byte) 0xaa,
            0x00, 0x22, 0x00, (byte) 0xee, 0x00, (byte) 0xaa, 0x00, 0x66
    };

    /** SYZ cycle 2: 16 bytes, 4 frames x 4 bytes (2 non-contiguous colors). */
    private static final byte[] PAL_SYZ_CYC2 = {
            0x00, 0x0e, 0x0e, (byte) 0xee, 0x06, 0x6e, 0x0e, (byte) 0x88,
            0x0e, (byte) 0xee, 0x0c, 0x00, 0x06, 0x6e, 0x0e, (byte) 0x88
    };

    // ===== SBZ palette cycle data (from s1disasm/palette/Cycle - SBZ *.bin) =====

    /** SBZCyc1: 16 bytes, 8 frames x 1 color. Palette line 2, color 8. */
    private static final byte[] PAL_SBZ_CYC1 = {
            0x00, 0x44, 0x0e, (byte) 0xa0, 0x00, 0x44, 0x0e, (byte) 0xa0,
            0x00, (byte) 0xe8, 0x00, (byte) 0xe8, 0x00, 0x44, 0x0e, 0x4e
    };

    /** SBZCyc2: 16 bytes, 8 frames x 1 color. Palette line 2, color 9. */
    private static final byte[] PAL_SBZ_CYC2 = {
            0x00, (byte) 0xee, 0x00, (byte) 0x8e, 0x00, 0x0e, 0x00, 0x08,
            0x00, 0x08, 0x00, 0x0e, 0x00, (byte) 0x8e, 0x00, (byte) 0xee
    };

    /** SBZCyc3: 16 bytes, 8 frames x 1 color. Palette line 3, color 7 (Act 1 only). */
    private static final byte[] PAL_SBZ_CYC3 = {
            0x00, 0x0a, 0x00, 0x0a, 0x00, 0x08, 0x00, 0x06,
            0x00, 0x04, 0x00, 0x04, 0x00, 0x06, 0x00, 0x08
    };

    /** SBZCyc4: 12 bytes, 3 unique colors doubled. Conveyor belt for Act 1. */
    private static final byte[] PAL_SBZ_CYC4 = {
            0x0c, (byte) 0xaa, 0x08, 0x66, 0x04, 0x22,
            0x0c, (byte) 0xaa, 0x08, 0x66, 0x04, 0x22
    };

    /** SBZCyc5: 16 bytes, 8 frames x 1 color. Palette line 3, color 8 (Act 1 only). */
    private static final byte[] PAL_SBZ_CYC5 = {
            0x00, 0x0e, 0x00, 0x0e, 0x00, 0x0a, 0x00, 0x08,
            0x00, 0x06, 0x00, 0x06, 0x00, 0x08, 0x00, 0x0a
    };

    /** SBZCyc6: 16 bytes, 8 frames x 1 color. Palette line 3, color 9. */
    private static final byte[] PAL_SBZ_CYC6 = {
            0x0c, (byte) 0xe6, 0x0c, (byte) 0xe6, 0x0a, (byte) 0xc4, 0x08, (byte) 0xa2,
            0x06, (byte) 0x80, 0x06, (byte) 0x80, 0x08, (byte) 0xa2, 0x0a, (byte) 0xc4
    };

    /** SBZCyc7: 32 bytes, 16 frames x 1 color. Palette line 3, color 15 (Act 1 only). */
    private static final byte[] PAL_SBZ_CYC7 = {
            0x0e, (byte) 0xa0, 0x0e, (byte) 0x86, 0x0e, 0x6a, 0x0e, 0x4e,
            0x08, (byte) 0x8e, 0x04, (byte) 0xae, 0x02, (byte) 0xce, 0x00, (byte) 0xee,
            0x00, (byte) 0xee, 0x02, (byte) 0xce, 0x04, (byte) 0xae, 0x08, (byte) 0x8e,
            0x0e, 0x4e, 0x0e, 0x6a, 0x0e, (byte) 0x86, 0x0e, (byte) 0xa0
    };

    /** SBZCyc8: 10 bytes, shared by 3 entries with sliding offset. Palette line 3, colors 12-14. */
    private static final byte[] PAL_SBZ_CYC8 = {
            0x0e, (byte) 0xae, 0x0e, 0x4e, 0x08, 0x08,
            0x0e, (byte) 0xae, 0x0e, 0x4e
    };

    /** SBZCyc9: 16 bytes, 8 frames x 1 color. Palette line 3, color 8 (Act 2 only). */
    private static final byte[] PAL_SBZ_CYC9 = {
            0x0e, (byte) 0xa0, 0x0e, (byte) 0xa0, 0x0a, 0x64, 0x06, 0x4a,
            0x00, 0x0e, 0x00, 0x0e, 0x06, 0x4a, 0x0a, 0x64
    };

    /** SBZCyc10: 12 bytes, 3 unique colors doubled. Conveyor belt for Act 2. */
    private static final byte[] PAL_SBZ_CYC10 = {
            0x0e, (byte) 0xec, 0x0a, (byte) 0xa6, 0x06, 0x60,
            0x0e, (byte) 0xec, 0x0a, (byte) 0xa6, 0x06, 0x60
    };
}
