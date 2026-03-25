package com.openggf.game.sonic1;

import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RequiresRom(SonicGame.SONIC_1)
public class TestSonic1PaletteCyclerLz {
    private static final byte[] PAL_LZ_CYC1 = {
            0x06, (byte) 0x80, 0x0c, (byte) 0xe6, 0x0a, (byte) 0xc4, 0x08, (byte) 0xa2,
            0x0c, (byte) 0xe6, 0x0a, (byte) 0xc4, 0x08, (byte) 0xa2, 0x06, (byte) 0x80,
            0x0a, (byte) 0xc4, 0x08, (byte) 0xa2, 0x06, (byte) 0x80, 0x0c, (byte) 0xe6,
            0x08, (byte) 0xa2, 0x06, (byte) 0x80, 0x0c, (byte) 0xe6, 0x0a, (byte) 0xc4
    };

    private static final byte[] PAL_LZ_CYC2 = {
            0x0a, (byte) 0xaa, 0x06, 0x66, 0x02, 0x22,
            0x06, 0x66, 0x02, 0x22, 0x0a, (byte) 0xaa,
            0x02, 0x22, 0x0a, (byte) 0xaa, 0x06, 0x66
    };

    private static final byte[] PAL_LZ_CYC3 = {
            0x08, (byte) 0xa8, 0x04, 0x64, 0x00, 0x20,
            0x04, 0x64, 0x00, 0x20, 0x08, (byte) 0xa8,
            0x00, 0x20, 0x08, (byte) 0xa8, 0x04, 0x64
    };

    @Rule
    public final RequiresRomRule romRule = new RequiresRomRule();

    @Before
    public void resetConveyorState() {
        Sonic1ConveyorState.getInstance().resetState();
    }

    @Test
    public void firstUpdateAppliesLzWaterfallAndConveyorToNormalPalettes() {
        TestLevel level = new TestLevel();
        Sonic1PaletteCycler cycler = new Sonic1PaletteCycler(level, 0x01);

        cycler.update();

        assertColorMatches(PAL_LZ_CYC1, 0, level.getPalette(2), 11);
        assertColorMatches(PAL_LZ_CYC1, 2, level.getPalette(2), 12);
        assertColorMatches(PAL_LZ_CYC1, 4, level.getPalette(2), 13);
        assertColorMatches(PAL_LZ_CYC1, 6, level.getPalette(2), 14);

        assertColorMatches(PAL_LZ_CYC2, 6, level.getPalette(3), 11);
        assertColorMatches(PAL_LZ_CYC2, 8, level.getPalette(3), 12);
        assertColorMatches(PAL_LZ_CYC2, 10, level.getPalette(3), 13);
    }

    @Test
    public void firstUpdateAppliesUnderwaterWaterfallAndConveyorPalettes() throws Exception {
        TestLevel level = new TestLevel();
        Sonic1PaletteCycler cycler = new Sonic1PaletteCycler(level, 0x01);

        cycler.update();

        Palette[] underwater = extractUnderwaterPalettes(cycler, 0);

        assertColorMatches(PAL_LZ_CYC1, 0, underwater[2], 11);
        assertColorMatches(PAL_LZ_CYC1, 2, underwater[2], 12);
        assertColorMatches(PAL_LZ_CYC1, 4, underwater[2], 13);
        assertColorMatches(PAL_LZ_CYC1, 6, underwater[2], 14);

        assertColorMatches(PAL_LZ_CYC3, 6, underwater[3], 11);
        assertColorMatches(PAL_LZ_CYC3, 8, underwater[3], 12);
        assertColorMatches(PAL_LZ_CYC3, 10, underwater[3], 13);
    }

    @Test
    public void reversedConveyorUsesOppositeFrameOrder() {
        TestLevel level = new TestLevel();
        Sonic1ConveyorState.getInstance().setReversed(true);
        Sonic1PaletteCycler cycler = new Sonic1PaletteCycler(level, 0x01);

        cycler.update();

        assertColorMatches(PAL_LZ_CYC2, 12, level.getPalette(3), 11);
        assertColorMatches(PAL_LZ_CYC2, 14, level.getPalette(3), 12);
        assertColorMatches(PAL_LZ_CYC2, 16, level.getPalette(3), 13);
    }

    private static void assertColorMatches(byte[] data, int offset, Palette palette, int colorIndex) {
        Palette.Color expected = new Palette.Color();
        expected.fromSegaFormat(data, offset);

        Palette.Color actual = palette.getColor(colorIndex);
        assertEquals(expected.r, actual.r);
        assertEquals(expected.g, actual.g);
        assertEquals(expected.b, actual.b);
    }

    @SuppressWarnings("unchecked")
    private static Palette[] extractUnderwaterPalettes(Sonic1PaletteCycler cycler, int cycleIndex) throws Exception {
        Field cyclesField = Sonic1PaletteCycler.class.getDeclaredField("cycles");
        cyclesField.setAccessible(true);
        List<Object> cycles = (List<Object>) cyclesField.get(cycler);

        Field underwaterField = cycles.get(cycleIndex).getClass().getDeclaredField("underwaterPalettes");
        underwaterField.setAccessible(true);
        return (Palette[]) underwaterField.get(cycles.get(cycleIndex));
    }

    private static final class TestLevel implements Level {
        private final Palette[] palettes = {
                new Palette(), new Palette(), new Palette(), new Palette()
        };

        @Override
        public int getPaletteCount() {
            return palettes.length;
        }

        @Override
        public Palette getPalette(int index) {
            return palettes[index];
        }

        @Override
        public int getPatternCount() {
            return 0;
        }

        @Override
        public Pattern getPattern(int index) {
            return null;
        }

        @Override
        public int getChunkCount() {
            return 0;
        }

        @Override
        public Chunk getChunk(int index) {
            return null;
        }

        @Override
        public int getBlockCount() {
            return 0;
        }

        @Override
        public Block getBlock(int index) {
            return null;
        }

        @Override
        public SolidTile getSolidTile(int index) {
            return null;
        }

        @Override
        public Map getMap() {
            return null;
        }

        @Override
        public List<ObjectSpawn> getObjects() {
            return List.of();
        }

        @Override
        public List<RingSpawn> getRings() {
            return List.of();
        }

        @Override
        public RingSpriteSheet getRingSpriteSheet() {
            return null;
        }

        @Override
        public int getMinX() {
            return 0;
        }

        @Override
        public int getMaxX() {
            return 0;
        }

        @Override
        public int getMinY() {
            return 0;
        }

        @Override
        public int getMaxY() {
            return 0;
        }

        @Override
        public int getZoneIndex() {
            return 0x01;
        }
    }
}
