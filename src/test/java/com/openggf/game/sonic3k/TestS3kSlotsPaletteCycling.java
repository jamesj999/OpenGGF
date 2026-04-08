package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Level;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotBonusStageRuntime;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kSlotsPaletteCycling {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic3kPaletteCycler cycler;
    private StubLevel level;
    private GameModule previousModule;

    @Before
    public void setUp() throws IOException {
        GraphicsManager.getInstance().initHeadless();
        previousModule = GameModuleRegistry.getCurrent();
        Rom rom = romRule.rom();
        RomByteReader reader = RomByteReader.fromRom(rom);
        level = new StubLevel();
        cycler = new Sonic3kPaletteCycler(reader, level, 0x15, 0);
    }

    @After
    public void tearDown() {
        GameModuleRegistry.setCurrent(previousModule);
    }

    @Test
    public void idleCycleMutatesSlotsGlowColors() {
        Palette.Color beforeLine3 = snapshot(level.getPalette(2).getColor(10));
        Palette.Color beforeLine4 = snapshot(level.getPalette(3).getColor(14));

        cycler.update();

        assertFalse(colorsEqual(beforeLine3, level.getPalette(2).getColor(10)));
        assertFalse(colorsEqual(beforeLine4, level.getPalette(3).getColor(14)));
    }

    @Test
    public void idleCycleProducesMultipleDistinctStates() {
        int distinctCount = 0;
        int prevR = -1;
        int prevG = -1;
        int prevB = -1;

        for (int frame = 0; frame < 24; frame++) {
            cycler.update();
            Palette.Color color = level.getPalette(2).getColor(10);
            int r = color.r & 0xFF;
            int g = color.g & 0xFF;
            int b = color.b & 0xFF;
            if (r != prevR || g != prevG || b != prevB) {
                distinctCount++;
                prevR = r;
                prevG = g;
                prevB = b;
            }
        }

        assertTrue("Expected multiple idle slot palette states over 24 frames, got " + distinctCount,
                distinctCount >= 3);
    }

    @Test
    public void slotModeTracksAuthoritativeStageState() {
        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        assertEquals(0, Sonic3kPaletteCycler.resolveSlotsModeForTest(runtime));

        runtime.setPaletteCycleEnabledForTest(true);

        assertEquals(1, Sonic3kPaletteCycler.resolveSlotsModeForTest(runtime));
    }

    @Test
    public void slotModeFollowsRegistryCoordinatorLookupPath() throws Exception {
        Sonic3kBonusStageCoordinator coordinator = new Sonic3kBonusStageCoordinator();
        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();
        runtime.setPaletteCycleEnabledForTest(true);

        Field slotRuntimeField = Sonic3kBonusStageCoordinator.class.getDeclaredField("slotRuntime");
        slotRuntimeField.setAccessible(true);
        slotRuntimeField.set(coordinator, runtime);

        GameModuleRegistry.setCurrent(new TestSonic3kModule(coordinator));

        assertEquals(1, Sonic3kPaletteCycler.resolveSlotsModeFromRegistryForTest());
    }

    private static Palette.Color snapshot(Palette.Color c) {
        return new Palette.Color(c.r, c.g, c.b);
    }

    private static boolean colorsEqual(Palette.Color a, Palette.Color b) {
        return a.r == b.r && a.g == b.g && a.b == b.b;
    }

    private static final class StubLevel implements Level {
        private final Palette[] palettes = new Palette[4];

        StubLevel() {
            for (int i = 0; i < palettes.length; i++) {
                palettes[i] = new Palette();
            }
        }

        @Override public int getPaletteCount() { return palettes.length; }
        @Override public Palette getPalette(int index) { return palettes[index]; }
        @Override public int getPatternCount() { return 0; }
        @Override public Pattern getPattern(int index) { throw new UnsupportedOperationException(); }
        @Override public int getChunkCount() { return 0; }
        @Override public Chunk getChunk(int index) { throw new UnsupportedOperationException(); }
        @Override public int getBlockCount() { return 0; }
        @Override public Block getBlock(int index) { throw new UnsupportedOperationException(); }
        @Override public SolidTile getSolidTile(int index) { throw new UnsupportedOperationException(); }
        @Override public Map getMap() { return null; }
        @Override public List<ObjectSpawn> getObjects() { return List.of(); }
        @Override public List<RingSpawn> getRings() { return List.of(); }
        @Override public RingSpriteSheet getRingSpriteSheet() { return null; }
        @Override public int getMinX() { return 0; }
        @Override public int getMaxX() { return 0; }
        @Override public int getMinY() { return 0; }
        @Override public int getMaxY() { return 0; }
        @Override public int getZoneIndex() { return 0x15; }
    }

    private static final class TestSonic3kModule extends Sonic3kGameModule {
        private final Sonic3kBonusStageCoordinator coordinator;

        private TestSonic3kModule(Sonic3kBonusStageCoordinator coordinator) {
            this.coordinator = coordinator;
        }

        @Override
        public com.openggf.game.BonusStageProvider getBonusStageProvider() {
            return coordinator;
        }
    }
}
