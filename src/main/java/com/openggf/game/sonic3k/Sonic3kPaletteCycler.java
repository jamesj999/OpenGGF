package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.animation.AnimatedPaletteManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies Sonic 3&amp;K palette cycling for supported zones.
 * Based on the original AnPal_* routines in sonic3k.asm (lines 3173-3282).
 *
 * <p>AIZ1 uses a shared timer controlling two sub-cycles (waterfall + secondary water),
 * matching the ROM's single counter1/counter0 pair. AIZ2 uses two independent timers
 * for water cycling and torch glow, with camera-dependent table switching at X >= 0x3800.
 */
class Sonic3kPaletteCycler implements AnimatedPaletteManager {
    private final Level level;
    private final GraphicsManager graphicsManager = GraphicsManager.getInstance();
    private final List<PaletteCycle> cycles;

    Sonic3kPaletteCycler(RomByteReader reader, Level level, int zoneIndex, int actIndex) {
        this.level = level;
        this.cycles = loadCycles(reader, zoneIndex, actIndex);
    }

    @Override
    public void update() {
        if (cycles == null || cycles.isEmpty()) {
            return;
        }
        if (shouldSuspendAizPaletteCycles()) {
            return;
        }
        for (PaletteCycle cycle : cycles) {
            cycle.tick(level, graphicsManager);
        }
    }

    private boolean shouldSuspendAizPaletteCycles() {
        if (level == null || level.getZoneIndex() != 0) {
            return false;
        }
        Sonic3kLevelEventManager levelEventManager = Sonic3kLevelEventManager.getInstance();
        Sonic3kAIZEvents aizEvents = levelEventManager != null ? levelEventManager.getAizEvents() : null;
        return aizEvents != null && aizEvents.isFireTransitionActive();
    }

    private List<PaletteCycle> loadCycles(RomByteReader reader, int zoneIndex, int actIndex) {
        List<PaletteCycle> list = new ArrayList<>();
        if (zoneIndex == 0) { // AIZ
            if (actIndex == 0) {
                loadAiz1Cycles(reader, list);
            } else {
                loadAiz2Cycles(reader, list);
            }
        }
        return list;
    }

    private void loadAiz1Cycles(RomByteReader reader, List<PaletteCycle> list) {
        byte[] waterfallData = safeSlice(reader, Sonic3kConstants.ANPAL_AIZ1_1_ADDR, Sonic3kConstants.ANPAL_AIZ1_1_SIZE);
        byte[] secondaryData = safeSlice(reader, Sonic3kConstants.ANPAL_AIZ1_2_ADDR, Sonic3kConstants.ANPAL_AIZ1_2_SIZE);
        byte[] introData1 = safeSlice(reader, Sonic3kConstants.ANPAL_AIZ1_3_ADDR, Sonic3kConstants.ANPAL_AIZ1_3_SIZE);
        byte[] introData2 = safeSlice(reader, Sonic3kConstants.ANPAL_AIZ1_4_ADDR, Sonic3kConstants.ANPAL_AIZ1_4_SIZE);

        if (waterfallData.length >= Sonic3kConstants.ANPAL_AIZ1_1_SIZE
                && secondaryData.length >= Sonic3kConstants.ANPAL_AIZ1_2_SIZE
                && introData1.length >= Sonic3kConstants.ANPAL_AIZ1_3_SIZE
                && introData2.length >= Sonic3kConstants.ANPAL_AIZ1_4_SIZE) {
            list.add(new Aiz1Cycle(waterfallData, secondaryData, introData1, introData2));
        }
    }

    private void loadAiz2Cycles(RomByteReader reader, List<PaletteCycle> list) {
        byte[] waterData = safeSlice(reader, Sonic3kConstants.ANPAL_AIZ2_1_ADDR, Sonic3kConstants.ANPAL_AIZ2_1_SIZE);
        byte[] tricklePreData = safeSlice(reader, Sonic3kConstants.ANPAL_AIZ2_2_ADDR, Sonic3kConstants.ANPAL_AIZ2_2_SIZE);
        byte[] tricklePostData = safeSlice(reader, Sonic3kConstants.ANPAL_AIZ2_3_ADDR, Sonic3kConstants.ANPAL_AIZ2_3_SIZE);
        byte[] torchPreData = safeSlice(reader, Sonic3kConstants.ANPAL_AIZ2_4_ADDR, Sonic3kConstants.ANPAL_AIZ2_4_SIZE);
        byte[] torchPostData = safeSlice(reader, Sonic3kConstants.ANPAL_AIZ2_5_ADDR, Sonic3kConstants.ANPAL_AIZ2_5_SIZE);

        if (waterData.length >= Sonic3kConstants.ANPAL_AIZ2_1_SIZE
                && tricklePreData.length >= Sonic3kConstants.ANPAL_AIZ2_2_SIZE
                && tricklePostData.length >= Sonic3kConstants.ANPAL_AIZ2_3_SIZE) {
            list.add(new Aiz2WaterCycle(waterData, tricklePreData, tricklePostData));
        }
        if (torchPreData.length >= Sonic3kConstants.ANPAL_AIZ2_4_SIZE
                && torchPostData.length >= Sonic3kConstants.ANPAL_AIZ2_5_SIZE) {
            list.add(new Aiz2TorchCycle(torchPreData, torchPostData));
        }
    }

    private byte[] safeSlice(RomByteReader reader, int addr, int len) {
        if (addr < 0 || addr + len > reader.size()) {
            return new byte[0];
        }
        return reader.slice(addr, len);
    }

    // ========== Base class ==========
    private static abstract class PaletteCycle {
        abstract void tick(Level level, GraphicsManager gm);
    }

    // ========== AIZ1 Unified Cycle ==========
    // ROM: AnPal_AIZ1 (line 3173) checks AIZ1_palette_cycle_flag (a one-shot RAM flag)
    // set by AIZ1_Resize routine 0 (line 38873-38877):
    //   - Flag starts as 1 (intro mode)
    //   - Set to 0 when Camera X >= 0x1000 (gameplay mode)
    //   - Never set again — flag stays 0 for the rest of the level
    //
    // Intro mode (flag != 0, timer period 10):
    //   PalAIZ1_3 → palette 3 colors 2-5 (10 frames, wraps at 0x50)
    //   PalAIZ1_4 → palette 3 colors 13-15 (10 frames, wraps at 0x3C)
    //
    // Gameplay mode (flag == 0, timer period 8):
    //   PalAIZ1_1 → palette 2 colors 11-14 (4 frames, counter0 & 0x18)
    //   PalAIZ1_2 → palette 3 colors 12-14 (8 frames, wraps at 0x30, gated by isLevelStarted)
    //
    // Timers are shared across both modes (ROM uses same counter1/counter0/counters+$02).
    private static class Aiz1Cycle extends PaletteCycle {
        // Gameplay mode data
        private final byte[] waterfallData;  // PalAIZ1_1: 32 bytes (4 frames × 8 bytes)
        private final byte[] secondaryData;  // PalAIZ1_2: 48 bytes (8 frames × 6 bytes)
        // Intro mode data
        private final byte[] introData1;     // PalAIZ1_3: 80 bytes (10 frames × 8 bytes)
        private final byte[] introData2;     // PalAIZ1_4: 60 bytes (10 frames × 6 bytes)

        // Shared timer/counter state (persists through mode transitions)
        private int timer;    // Palette_cycle_counter1
        private int counter0; // Palette_cycle_counter0
        private int counter2; // Palette_cycle_counters+$02
        private boolean dirty2;
        private boolean dirty3;

        // One-shot flag matching ROM's AIZ1_palette_cycle_flag (sonic3k.constants.asm line 630).
        // Starts true (intro), cleared permanently once Camera X >= 0x1000.
        private boolean introFlag = true;

        Aiz1Cycle(byte[] waterfallData, byte[] secondaryData, byte[] introData1, byte[] introData2) {
            this.waterfallData = waterfallData;
            this.secondaryData = secondaryData;
            this.introData1 = introData1;
            this.introData2 = introData2;
        }

        @Override
        void tick(Level level, GraphicsManager gm) {
            // ROM: AIZ1_Resize routine 0 (line 38873-38877) clears the flag once
            // when Camera X >= 0x1000. It never re-sets it.
            if (introFlag && (Camera.getInstance().getX() & 0xFFFF) >= 0x1000) {
                introFlag = false;
            }

            if (timer > 0) {
                timer--;
            } else {
                if (introFlag) {
                    tickIntro(level);
                } else {
                    tickGameplay(level);
                }
            }

            if (gm.isGlInitialized()) {
                if (dirty2) {
                    gm.cachePaletteTexture(level.getPalette(2), 2);
                    dirty2 = false;
                }
                if (dirty3) {
                    gm.cachePaletteTexture(level.getPalette(3), 3);
                    dirty3 = false;
                }
            }
        }

        private void tickIntro(Level level) {
            timer = 9;
            Palette pal3 = level.getPalette(3);

            // PalAIZ1_3 → palette 3, colors 2-5
            int d0 = counter0;
            counter0 += 8;
            if (counter0 >= 0x50) {
                counter0 = 0;
            }
            pal3.getColor(2).fromSegaFormat(introData1, d0);
            pal3.getColor(3).fromSegaFormat(introData1, d0 + 2);
            pal3.getColor(4).fromSegaFormat(introData1, d0 + 4);
            pal3.getColor(5).fromSegaFormat(introData1, d0 + 6);

            // PalAIZ1_4 → palette 3, colors 13-15
            int d1 = counter2;
            counter2 += 6;
            if (counter2 >= 0x3C) {
                counter2 = 0;
            }
            pal3.getColor(13).fromSegaFormat(introData2, d1);
            pal3.getColor(14).fromSegaFormat(introData2, d1 + 2);
            pal3.getColor(15).fromSegaFormat(introData2, d1 + 4);

            dirty3 = true;
        }

        private void tickGameplay(Level level) {
            timer = 7;

            // PalAIZ1_1 → palette 2, colors 11-14
            // ROM uses byte-width counter (wraps at 256); mask & 0x18 cycles
            // through 0,8,16,24 for waterfallData indexing.
            int d0 = counter0 & 0x18;
            counter0 = (counter0 + 8) & 0xFF;
            Palette pal2 = level.getPalette(2);
            pal2.getColor(11).fromSegaFormat(waterfallData, d0);
            pal2.getColor(12).fromSegaFormat(waterfallData, d0 + 2);
            pal2.getColor(13).fromSegaFormat(waterfallData, d0 + 4);
            pal2.getColor(14).fromSegaFormat(waterfallData, d0 + 6);
            dirty2 = true;

            // PalAIZ1_2 → palette 3, colors 12-14
            // ROM gates this with tst.b (Palette_cycle_counters+$00) / bne.s skip.
            // During intro, byte is non-zero (emerald palette occupies palette 3).
            // We use isLevelStarted() as an equivalent gate.
            if (Camera.getInstance().isLevelStarted()) {
                // counter2 may carry a value from intro mode (wraps at 0x3C)
                // that exceeds secondaryData bounds (0x30). Re-wrap first.
                if (counter2 >= 0x30) {
                    counter2 = 0;
                }
                int d1 = counter2;
                counter2 += 6;
                if (counter2 >= 0x30) {
                    counter2 = 0;
                }
                Palette pal3 = level.getPalette(3);
                pal3.getColor(12).fromSegaFormat(secondaryData, d1);
                pal3.getColor(13).fromSegaFormat(secondaryData, d1 + 2);
                pal3.getColor(14).fromSegaFormat(secondaryData, d1 + 4);
                dirty3 = true;
            }
        }
    }

    // ========== AIZ2 Water Cycle ==========
    // ROM: AnPal_AIZ2 first half (timer period 6)
    // Cycle A: AnPal_PalAIZ2_1 → palette 3 colors 12-15 (4 frames, counter0 & 0x18)
    // Cycle B: AnPal_PalAIZ2_2/3 → pal 2 colors 4,8 + pal 3 color 11 (8 frames, wraps at 0x30)
    //   Table switches from PalAIZ2_2 to PalAIZ2_3 when camera X >= 0x3800
    //   Also: pal 2 color 14 = fixed 0x0A0E when camera X >= 0x1C0, else from data
    private static class Aiz2WaterCycle extends PaletteCycle {
        private final byte[] waterData;       // 32 bytes: 4 frames × 8 bytes
        private final byte[] tricklePreData;  // 48 bytes: 8 frames × 6 bytes
        private final byte[] tricklePostData; // 48 bytes: 8 frames × 6 bytes
        private int timer;
        private int counter0;
        private int counter2;
        private boolean dirty2;
        private boolean dirty3;

        Aiz2WaterCycle(byte[] waterData, byte[] tricklePreData, byte[] tricklePostData) {
            this.waterData = waterData;
            this.tricklePreData = tricklePreData;
            this.tricklePostData = tricklePostData;
        }

        @Override
        void tick(Level level, GraphicsManager gm) {
            if (timer > 0) {
                timer--;
            } else {
                timer = 5;
                int cameraX = Camera.getInstance().getX() & 0xFFFF;

                // Cycle A: water → palette 3, colors 12-15
                int d0 = counter0 & 0x18;
                counter0 += 8;
                Palette pal3 = level.getPalette(3);
                pal3.getColor(12).fromSegaFormat(waterData, d0);
                pal3.getColor(13).fromSegaFormat(waterData, d0 + 2);
                pal3.getColor(14).fromSegaFormat(waterData, d0 + 4);
                pal3.getColor(15).fromSegaFormat(waterData, d0 + 6);
                dirty3 = true;

                // Cycle B: trickle → pal 2 colors 4,8 + pal 3 color 11
                int d1 = counter2;
                counter2 += 6;
                if (counter2 >= 0x30) {
                    counter2 = 0;
                }
                byte[] trickleData = (cameraX >= 0x3800) ? tricklePostData : tricklePreData;
                Palette pal2 = level.getPalette(2);
                pal2.getColor(4).fromSegaFormat(trickleData, d1);
                pal2.getColor(8).fromSegaFormat(trickleData, d1 + 2);
                pal3.getColor(11).fromSegaFormat(trickleData, d1 + 4);

                // Pal 2 color 14: fixed $0A0E when camera >= 0x1C0, else animated from data
                if (cameraX >= 0x1C0) {
                    pal2.getColor(14).fromSegaFormat(new byte[]{0x0A, 0x0E}, 0);
                } else {
                    pal2.getColor(14).fromSegaFormat(trickleData, d1 + 4);
                }
                dirty2 = true;
            }

            if (gm.isGlInitialized()) {
                if (dirty2) {
                    gm.cachePaletteTexture(level.getPalette(2), 2);
                    dirty2 = false;
                }
                if (dirty3) {
                    gm.cachePaletteTexture(level.getPalette(3), 3);
                    dirty3 = false;
                }
            }
        }
    }

    // ========== AIZ2 Torch Glow Cycle ==========
    // ROM: AnPal_AIZ2 second half (timer period 2)
    // AnPal_PalAIZ2_4/5 → palette 3 color 1 (26 frames, wraps at 0x34)
    // Table switches from PalAIZ2_4 to PalAIZ2_5 when camera X >= 0x3800
    private static class Aiz2TorchCycle extends PaletteCycle {
        private final byte[] torchPreData;   // 52 bytes: 26 frames × 2 bytes
        private final byte[] torchPostData;  // 52 bytes: 26 frames × 2 bytes
        private int timer;
        private int counter;
        private boolean dirty;

        Aiz2TorchCycle(byte[] torchPreData, byte[] torchPostData) {
            this.torchPreData = torchPreData;
            this.torchPostData = torchPostData;
        }

        @Override
        void tick(Level level, GraphicsManager gm) {
            if (timer > 0) {
                timer--;
            } else {
                timer = 1;
                int cameraX = Camera.getInstance().getX() & 0xFFFF;

                int d0 = counter;
                counter += 2;
                if (counter >= 0x34) {
                    counter = 0;
                }

                byte[] torchData = (cameraX >= 0x3800) ? torchPostData : torchPreData;
                Palette pal3 = level.getPalette(3);
                pal3.getColor(1).fromSegaFormat(torchData, d0);
                dirty = true;
            }

            if (dirty && gm.isGlInitialized()) {
                gm.cachePaletteTexture(level.getPalette(3), 3);
                dirty = false;
            }
        }
    }
}
