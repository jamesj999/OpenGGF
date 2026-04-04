package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Tests for Sonic 3&K Carnival Night Zone palette cycling.
 * Verifies that all three channels (bumpers, background, tertiary) animate
 * correctly from ROM data without requiring a full level load.
 *
 * <p>CNZ has three palette animation channels (AnPal_CNZ in sonic3k.asm):
 * <ul>
 *   <li>Channel 1 (bumpers): timer period 3, counter step +6 / wrap 0x60
 *       → palette[3] colors 9-11</li>
 *   <li>Channel 2 (background): runs every frame, counter step +6 / wrap 0xB4
 *       → palette[2] colors 9-11</li>
 *   <li>Channel 3 (tertiary): timer period 2, counter step +4 / wrap 0x40
 *       → palette[2] colors 7-8</li>
 * </ul>
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kCnzPaletteCycling {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic3kPaletteCycler cycler;
    private StubLevel level;

    @Before
    public void setUp() throws IOException {
        GraphicsManager.getInstance().initHeadless();
        Rom rom = romRule.rom();
        RomByteReader reader = RomByteReader.fromRom(rom);
        level = new StubLevel();
        // CNZ zone index = 3, act index = 0
        cycler = new Sonic3kPaletteCycler(reader, level, 3, 0);
    }

    @Test
    public void paletteLine3Color9ChangesAfter30Frames() {
        // Channel 1 fires every 4 frames (timer period 3 → fires on frame 4, 8, ...)
        // After 30 frames, channel 1 should have fired at least 7 times.
        Palette.Color initial = snapshot(level.getPalette(3).getColor(9));

        for (int i = 0; i < 30; i++) {
            cycler.update();
        }

        Palette.Color after = level.getPalette(3).getColor(9);
        // The bumper palette cycles through 16 distinct frames; after 30 ticks
        // (>= 7 channel-1 fires, each advancing by 6 bytes in a 96-byte table),
        // color 9 must differ from its initial value.
        assertFalse("palette[3] color 9 should change after 30 frames",
                colorsEqual(initial, after));
    }

    @Test
    public void paletteLine2Color7ChangesAfter30Frames() {
        // Channel 3 fires every 3 frames (timer period 2 → fires on frame 3, 6, ...).
        // After 30 frames, it fires 10 times, advancing counter4 by 40 (>= 1 full wrap at 0x40).
        Palette.Color initial = snapshot(level.getPalette(2).getColor(7));

        for (int i = 0; i < 30; i++) {
            cycler.update();
        }

        Palette.Color after = level.getPalette(2).getColor(7);
        assertFalse("palette[2] color 7 should change after 30 frames",
                colorsEqual(initial, after));
    }

    @Test
    public void paletteLine2Color9ChangesEachFrame() {
        // Channel 2 (background) runs every frame — color 9 should change on frame 1.
        Palette.Color before = snapshot(level.getPalette(2).getColor(9));
        cycler.update();
        Palette.Color after = level.getPalette(2).getColor(9);
        assertFalse("palette[2] color 9 should change on the very first frame (ch2 always runs)",
                colorsEqual(before, after));
    }

    // ===== helpers =====

    private static Palette.Color snapshot(Palette.Color c) {
        return new Palette.Color(c.r, c.g, c.b);
    }

    private static boolean colorsEqual(Palette.Color a, Palette.Color b) {
        return a.r == b.r && a.g == b.g && a.b == b.b;
    }

    /**
     * Minimal Level stub — 4 palettes, no geometry needed for palette cycling tests.
     */
    private static class StubLevel implements Level {
        private final Palette[] palettes = new Palette[4];

        StubLevel() {
            for (int i = 0; i < palettes.length; i++) {
                palettes[i] = new Palette();
            }
        }

        @Override public int getPaletteCount() { return palettes.length; }
        @Override public Palette getPalette(int index) { return palettes[index]; }
        @Override public int getPatternCount() { return 0; }
        @Override public com.openggf.level.Pattern getPattern(int index) { return null; }
        @Override public int getChunkCount() { return 0; }
        @Override public com.openggf.level.Chunk getChunk(int index) { return null; }
        @Override public int getBlockCount() { return 0; }
        @Override public com.openggf.level.Block getBlock(int index) { return null; }
        @Override public com.openggf.level.SolidTile getSolidTile(int index) { return null; }
        @Override public com.openggf.level.Map getMap() { return null; }
        @Override public java.util.List<com.openggf.level.objects.ObjectSpawn> getObjects() { return java.util.List.of(); }
        @Override public java.util.List<com.openggf.level.rings.RingSpawn> getRings() { return java.util.List.of(); }
        @Override public com.openggf.level.rings.RingSpriteSheet getRingSpriteSheet() { return null; }
        @Override public int getMinX() { return 0; }
        @Override public int getMaxX() { return 0; }
        @Override public int getMinY() { return 0; }
        @Override public int getMaxY() { return 0; }
        @Override public int getZoneIndex() { return 3; }
    }
}
