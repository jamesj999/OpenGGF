package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SingletonResetExtension.class)
@FullReset
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class TestMgzDrillingRobotnikInstance {

    private Camera camera;

    @BeforeEach
    void setUp() {
        RuntimeManager.destroyCurrent();
        camera = RuntimeManager.createGameplay().getCamera();
        camera.resetState();
        camera.setX((short) 0);
        camera.setY((short) 0);
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    void hitFlashKeepsRobotnikVisibleAndUsesWhitePaletteFlash() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzDrillingRobotnikInstance boss = createBoss(services);
        boss.update(0, null); // load Pal_MGZEndBoss into line 1

        setPrivateInt(boss, "waitTimer", 0);
        boss.getState().routine = staticInt("ROUTINE_HANG");

        boss.onPlayerAttack(null, null);
        boss.update(1, null);
        boss.appendRenderCommands(new ArrayList<>());

        verify(services.drillRenderer).drawFrameIndex(eq(0), anyInt(), anyInt(), eq(false), eq(false));
        assertColorWord(services.paletteLine1, 11, 0x0EEE);
        assertColorWord(services.paletteLine1, 13, 0x0888);
        assertColorWord(services.paletteLine1, 14, 0x0AAA);
    }

    @Test
    void hitDuringDrillDropDoesNotImmediatelyForceCeilingEscape() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzDrillingRobotnikInstance boss = createBoss(services);
        boss.update(0, null);

        setPrivateInt(boss, "waitTimer", 0);
        boss.getState().routine = staticInt("ROUTINE_DRILL_DROP");

        boss.onPlayerAttack(null, null);

        assertEquals(staticInt("ROUTINE_DRILL_DROP"), boss.getState().routine,
                "Drop phase should keep drilling until the hang state checks the hit flag");
    }

    @Test
    void ceilingEscapeUsesEscapePodFrameAndThrusterFlame() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzDrillingRobotnikInstance boss = createBoss(services);
        boss.update(0, null);

        setPrivateInt(boss, "waitTimer", 0);
        boss.getState().routine = staticInt("ROUTINE_HANG");

        boss.onPlayerAttack(null, null);
        boss.update(1, null);
        boss.appendRenderCommands(new ArrayList<>());

        verify(services.shipRenderer).drawFrameIndex(eq(10), anyInt(), anyInt(), eq(false), eq(false));
        verify(services.shipRenderer).drawFrameIndex(eq(6), anyInt(), anyInt(), eq(false), eq(false), eq(0));
    }

    @Test
    void cleanupRestoresMgzPaletteLine1() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzDrillingRobotnikInstance boss = createBoss(services);
        boss.update(0, null);

        setPrivateInt(boss, "waitTimer", 0);
        setPrivateInt(boss, "escapeTimer", 1);
        boss.getState().routine = staticInt("ROUTINE_ESCAPE_WAIT");

        boss.update(1, null);

        assertColorWord(services.paletteLine1, 0, 0x000E);
        assertColorWord(services.paletteLine1, 1, 0x024A);
    }

    @Test
    void rendersRomDrillChildPiecesAtBaseOffsets() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzDrillingRobotnikInstance boss = createBoss(services);
        boss.update(0, null);

        setPrivateInt(boss, "waitTimer", 0);
        boss.getState().routine = staticInt("ROUTINE_DRILL_DROP");

        boss.appendRenderCommands(new ArrayList<>());

        verify(services.drillRenderer).drawFrameIndex(eq(1), anyInt(), anyInt(), eq(false), eq(false));
        verify(services.drillRenderer).drawFrameIndex(eq(4), anyInt(), anyInt(), eq(false), eq(false));
        verify(services.drillRenderer, times(2))
                .drawFrameIndex(eq(6), anyInt(), anyInt(), eq(false), eq(false));
        verify(services.drillRenderer).drawFrameIndex(eq(0x0F), anyInt(), anyInt(), eq(false), eq(false));
        verify(services.drillRenderer, times(2))
                .drawFrameIndex(eq(0x19), anyInt(), anyInt(), eq(false), eq(false), eq(0));
    }

    @Test
    void thrusterFlamesFlickerOffEveryOtherRender() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzDrillingRobotnikInstance boss = createBoss(services);
        boss.update(0, null);

        setPrivateInt(boss, "waitTimer", 0);
        boss.getState().routine = staticInt("ROUTINE_DRILL_DROP");

        boss.appendRenderCommands(new ArrayList<>());
        boss.appendRenderCommands(new ArrayList<>());

        verify(services.drillRenderer, times(2))
                .drawFrameIndex(eq(0x19), anyInt(), anyInt(), eq(false), eq(false), eq(0));
    }

    @Test
    void spawnFallingDebrisImmediatelyEnablesDebrisRendering() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzDrillingRobotnikInstance boss = createBoss(services);
        boss.update(0, null);
        setPrivateInt(boss, "waitTimer", 0);

        Method spawnDebris = MgzDrillingRobotnikInstance.class.getDeclaredMethod("spawnFallingDebris");
        spawnDebris.setAccessible(true);
        spawnDebris.invoke(boss);

        boss.appendRenderCommands(new ArrayList<>());

        verify(services.debrisRenderer, times(10))
                .drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), eq(false));
    }

    @Test
    void drillDropSpawnsArrivalDebrisAtCameraThreshold() throws Exception {
        camera.setY((short) 0x0590);
        RecordingServices services = new RecordingServices(camera);
        MgzDrillingRobotnikInstance boss = createBoss(services);
        boss.update(0, null);

        setPrivateInt(boss, "waitTimer", 0);
        boss.getState().routine = staticInt("ROUTINE_DRILL_DROP");

        boss.update(1, null);
        boss.appendRenderCommands(new ArrayList<>());

        verify(services.debrisRenderer, times(10))
                .drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), eq(false));
    }

    private static MgzDrillingRobotnikInstance createBoss(RecordingServices services) {
        MgzDrillingRobotnikInstance boss = new MgzDrillingRobotnikInstance(
                new ObjectSpawn(0x08E0, 0x0690, 0, 0, 0, false, 0), false);
        boss.setServices(services);
        return boss;
    }

    private static int staticInt(String fieldName) throws Exception {
        Field field = MgzDrillingRobotnikInstance.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static void setPrivateInt(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void assertColorWord(Palette palette, int colorIndex, int segaWord) {
        byte highByte = (byte) ((segaWord >> 8) & 0xFF);
        byte lowByte = (byte) (segaWord & 0xFF);
        int r3 = (lowByte >> 1) & 0x07;
        int g3 = (lowByte >> 5) & 0x07;
        int b3 = (highByte >> 1) & 0x07;
        int expectedR = (r3 * 255 + 3) / 7;
        int expectedG = (g3 * 255 + 3) / 7;
        int expectedB = (b3 * 255 + 3) / 7;
        assertEquals(expectedR, palette.getColor(colorIndex).r & 0xFF);
        assertEquals(expectedG, palette.getColor(colorIndex).g & 0xFF);
        assertEquals(expectedB, palette.getColor(colorIndex).b & 0xFF);
    }

    private static final class RecordingServices extends TestObjectServices {
        private final Camera camera;
        private final Level level;
        private final Palette paletteLine0 = new Palette();
        private final Palette paletteLine1 = new Palette();
        private final ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        private final com.openggf.level.render.PatternSpriteRenderer drillRenderer =
                mock(com.openggf.level.render.PatternSpriteRenderer.class);
        private final com.openggf.level.render.PatternSpriteRenderer shipRenderer =
                mock(com.openggf.level.render.PatternSpriteRenderer.class);
        private final com.openggf.level.render.PatternSpriteRenderer debrisRenderer =
                mock(com.openggf.level.render.PatternSpriteRenderer.class);
        private final Rom rom = mock(Rom.class);

        RecordingServices(Camera camera) throws Exception {
            this.camera = camera;
            this.level = mock(Level.class);

            byte[] normalLine = new byte[32];
            normalLine[22] = 0x00;
            normalLine[23] = 0x22;
            normalLine[26] = 0x02;
            normalLine[27] = 0x44;
            normalLine[28] = 0x04;
            normalLine[29] = 0x66;
            paletteLine1.fromSegaFormat(normalLine);

            when(level.getPaletteCount()).thenReturn(2);
            when(level.getPalette(0)).thenReturn(paletteLine0);
            when(level.getPalette(1)).thenReturn(paletteLine1);

            when(drillRenderer.isReady()).thenReturn(true);
            when(shipRenderer.isReady()).thenReturn(true);
            when(debrisRenderer.isReady()).thenReturn(true);
            when(renderManager.getRenderer(Sonic3kObjectArtKeys.MGZ_ENDBOSS)).thenReturn(drillRenderer);
            when(renderManager.getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP)).thenReturn(shipRenderer);
            when(renderManager.getRenderer(Sonic3kObjectArtKeys.MGZ_ENDBOSS_DEBRIS)).thenReturn(debrisRenderer);

            byte[] bossLine = new byte[32];
            byte[] mgzLine = new byte[32];
            mgzLine[0] = 0x00;
            mgzLine[1] = 0x0E;
            mgzLine[2] = 0x02;
            mgzLine[3] = 0x4A;
            when(rom.readBytes(Sonic3kConstants.PAL_MGZ_ENDBOSS_ADDR, 32)).thenReturn(bossLine);
            when(rom.readBytes(Sonic3kConstants.PAL_MGZ_ADDR, 32)).thenReturn(mgzLine);
        }

        @Override
        public Camera camera() {
            return camera;
        }

        @Override
        public Level currentLevel() {
            return level;
        }

        @Override
        public ObjectRenderManager renderManager() {
            return renderManager;
        }

        @Override
        public Rom rom() {
            return rom;
        }
    }
}
