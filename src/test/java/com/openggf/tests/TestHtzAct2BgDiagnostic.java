package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@RequiresRom(SonicGame.SONIC_2)
public class TestHtzAct2BgDiagnostic {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private static final int HTZ_ZONE = 4;
    private static final int HTZ_ACT = 1;

    private LevelManager levelManager;

    @Before
    public void setUp() throws Exception {
        TestEnvironment.resetAll();
        GraphicsManager.getInstance().initHeadless();

        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        Sonic sprite = new Sonic(mainCode, (short) 0x1800, (short) 0x450);
        SpriteManager.getInstance().addSprite(sprite);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        levelManager = LevelManager.getInstance();
        levelManager.loadZoneAndAct(HTZ_ZONE, HTZ_ACT);
        GroundSensor.setLevelManager(levelManager);
        camera.updatePosition(true);
    }

    @Test
    public void dumpAct2BgCoverageAcrossBaseX() throws Exception {
        Level level = levelManager.getCurrentLevel();
        Map map = level.getMap();
        System.out.println("=== HTZ ACT2 BG MAP SUMMARY ===");
        System.out.println("Map width=" + map.getWidth() + " height=" + map.getHeight());
        Palette pal3 = level.getPalette(3);
        System.out.println("Palette line 3 first 8 colors:");
        for (int i = 0; i < 8; i++) {
            Palette.Color c = pal3.getColor(i);
            System.out.printf("  c%02d = (%d,%d,%d)%n", i, c.r & 0xFF, c.g & 0xFF, c.b & 0xFF);
        }

        System.out.println("Row0 first 24 block IDs:");
        for (int col = 0; col < 24; col++) {
            int b = map.getValue(1, col, 0) & 0xFF;
            System.out.printf("  col %2d -> %d%n", col, b);
        }
        System.out.println("Row0 cols 48-80 block IDs:");
        for (int col = 48; col <= 80 && col < map.getWidth(); col++) {
            int b = map.getValue(1, col, 0) & 0xFF;
            if (b != 0) {
                System.out.printf("  col %2d -> %d%n", col, b);
            }
        }

        Method buildMethod = LevelManager.class.getDeclaredMethod("buildTilemapData", byte.class);
        buildMethod.setAccessible(true);
        Field baseField = LevelManager.class.getDeclaredField("bgTilemapBaseX");
        baseField.setAccessible(true);
        Field zoneField = LevelManager.class.getDeclaredField("currentZone");
        zoneField.setAccessible(true);

        int[] bases = {0, 256, 512, 768, 1024, 1536, 2048, 3072, 4096, 6144, 8192, 10000};
        System.out.println("=== HTZ ACT2 BG Tilemap Coverage vs bgTilemapBaseX ===");
        for (int base : bases) {
            baseField.setInt(levelManager, base);
            Object td = buildMethod.invoke(levelManager, (byte) 1);

            Field dataField = td.getClass().getDeclaredField("data");
            dataField.setAccessible(true);
            byte[] data = (byte[]) dataField.get(td);

            Field widthField = td.getClass().getDeclaredField("widthTiles");
            widthField.setAccessible(true);
            int width = widthField.getInt(td);

            int nonZeroPat = 0;
            int dynRefs = 0;
            int caveCore = 0;
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < width; x++) {
                    int off = (y * width + x) * 4;
                    int r = data[off] & 0xFF;
                    int g = data[off + 1] & 0xFF;
                    int pat = r + ((g & 0x07) << 8);
                    if (pat >= 2) {
                        nonZeroPat++;
                    }
                    if (pat >= 0x500 && pat <= 0x51F) {
                        dynRefs++;
                    }
                    if (pat >= 786 && pat <= 789) {
                        caveCore++;
                    }
                }
            }

            System.out.printf("  base=%5d -> rows0-31 realTiles=%4d dynRefs=%3d caveCore=%3d%n",
                    base, nonZeroPat, dynRefs, caveCore);
        }

        System.out.println("=== HTZ ACT2 Coverage with HTZ wrap disabled (diagnostic) ===");
        int savedZone = zoneField.getInt(levelManager);
        zoneField.setInt(levelManager, 0); // force default source-wrap width path
        for (int base : bases) {
            baseField.setInt(levelManager, base);
            Object td = buildMethod.invoke(levelManager, (byte) 1);

            Field dataField = td.getClass().getDeclaredField("data");
            dataField.setAccessible(true);
            byte[] data = (byte[]) dataField.get(td);

            Field widthField = td.getClass().getDeclaredField("widthTiles");
            widthField.setAccessible(true);
            int width = widthField.getInt(td);

            int nonZeroPat = 0;
            int dynRefs = 0;
            int caveCore = 0;
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < width; x++) {
                    int off = (y * width + x) * 4;
                    int r = data[off] & 0xFF;
                    int g = data[off + 1] & 0xFF;
                    int pat = r + ((g & 0x07) << 8);
                    if (pat >= 2) {
                        nonZeroPat++;
                    }
                    if (pat >= 0x500 && pat <= 0x51F) {
                        dynRefs++;
                    }
                    if (pat >= 786 && pat <= 789) {
                        caveCore++;
                    }
                }
            }
            System.out.printf("  base=%5d -> rows0-31 realTiles=%4d dynRefs=%3d caveCore=%3d%n",
                    base, nonZeroPat, dynRefs, caveCore);
        }
        zoneField.setInt(levelManager, savedZone);
    }
}
