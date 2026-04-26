package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.GameModule;
import com.openggf.game.GameStateManager;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import org.mockito.MockedStatic;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
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
    void touchRegionsUseOnlyRomCollisionBearingBossParts() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzDrillingRobotnikInstance boss = createBoss(services);
        boss.update(0, null);

        setPrivateInt(boss, "waitTimer", 0);
        boss.getState().routine = staticInt("ROUTINE_DRILL_DROP");
        boss.appendRenderCommands(new ArrayList<>());

        TouchResponseProvider.TouchRegion[] regions = boss.getMultiTouchRegions();

        assertEquals(4, regions.length,
                "ROM exposes the parent body, drill-tip child, and two lower hurt children; pod/head children have collision_flags=0");
        assertEquals(0x08E0, regions[0].x());
        assertEquals(0x0690, regions[0].y());
        assertEquals(0x0F, regions[0].collisionFlags());
        assertEquals(0x08F4, regions[1].x());
        assertEquals(0x065C, regions[1].y());
        assertEquals(0x8B, regions[1].collisionFlags());
        assertEquals(0, regions[1].shieldReactionFlags());
        assertEquals(0x08E8, regions[2].x());
        assertEquals(0x06B8, regions[2].y());
        assertEquals(0x9A, regions[2].collisionFlags());
        assertEquals(0x10, regions[2].shieldReactionFlags());
        assertEquals(0x08D4, regions[3].x());
        assertEquals(0x06B8, regions[3].y());
        assertEquals(0x9A, regions[3].collisionFlags());
        assertEquals(0x10, regions[3].shieldReactionFlags());
    }

    @Test
    void hitFlashDisablesOnlyAttackableBodyCollisionNotChildHazards() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzDrillingRobotnikInstance boss = createBoss(services);
        boss.update(0, null);

        setPrivateInt(boss, "waitTimer", 0);
        boss.getState().routine = staticInt("ROUTINE_HANG");
        boss.appendRenderCommands(new ArrayList<>());

        boss.onPlayerAttack(null, null);

        TouchResponseProvider.TouchRegion[] regions = boss.getMultiTouchRegions();
        assertEquals(0, regions[0].collisionFlags(),
                "MGZ2_SpecialCheckHit leaves the parent body unhittable while its hit flash is active");
        assertEquals(0x8B, regions[1].collisionFlags(),
                "The drill-tip child is an independent hurt object, not Robotnik's attackable body");
        assertEquals(0x9A, regions[2].collisionFlags());
        assertEquals(0x9A, regions[3].collisionFlags());
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

    @Test
    void usesLowTilePrioritySoForegroundTilesCanCoverEncounter() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzDrillingRobotnikInstance boss = createBoss(services);

        assertFalse(boss.isHighPriority(),
                "ObjDat_MGZDrillBoss uses make_art_tile(...,1,0), so MGZ drilling Robotnik must render behind high-priority FG tiles");
    }

    @Test
    void usesBucketSixFromObjDatPriorityWord() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzDrillingRobotnikInstance boss = createBoss(services);

        assertEquals(6, boss.getPriorityBucket(),
                "ObjDat_MGZDrillBoss priority word is $300, which maps to render bucket 6");
    }

    @Test
    void endBossStartsWithEightHitCollisionProperty() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzEndBossInstance boss = createEndBoss(services);

        assertEquals(8, boss.getCollisionProperty(),
                "Obj_MGZEndBoss uses eight hits instead of the drilling mini-event's nonlethal $FF hit property");
    }

    @Test
    void endBossConsumesEightHitsBeforeDefeat() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzEndBossInstance boss = createEndBoss(services);
        setPrivateInt(boss, "waitTimer", 0);
        boss.getState().routine = staticInt("ROUTINE_END_ACTIVE");

        for (int i = 7; i >= 0; i--) {
            boss.getState().invulnerable = false;
            boss.onPlayerAttack(null, null);
            assertEquals(i, boss.getCollisionProperty());
        }

        assertEquals(staticInt("ROUTINE_END_DEFEATED"), boss.getState().routine);
    }

    @Test
    void endBossDefeatClearsGenericBossFlag() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        GameStateManager gameState = mock(GameStateManager.class);
        AbstractLevelEventManager levelEvents = mock(AbstractLevelEventManager.class);
        GameModule module = mock(GameModule.class);
        when(module.getLevelEventProvider()).thenReturn(levelEvents);
        services.withGameState(gameState);
        services.withGameModule(module);
        MgzEndBossInstance boss = createEndBoss(services);
        setPrivateInt(boss, "waitTimer", 0);
        boss.getState().routine = staticInt("ROUTINE_END_ACTIVE");

        for (int i = 0; i < 8; i++) {
            boss.getState().invulnerable = false;
            boss.onPlayerAttack(null, null);
        }
        boss.update(1, null);

        verify(levelEvents).setBossActive(false);
    }

    @Test
    void endBossDescendsVisiblyWhileTimedWaitRuns() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzEndBossInstance boss = createEndBoss(services);
        setPrivateInt(boss, "waitTimer", 1);

        boss.update(0, null);
        assertEquals(staticInt("ROUTINE_END_DESCEND"), boss.getState().routine);
        assertFalse(boss.isHidden(),
                "Obj_MGZEndBoss must draw during the timed descent after End Boss music starts");

        int startY = boss.getY();
        boss.update(1, null);
        boss.update(2, null);

        assertFalse(boss.isHidden());
        assertTrue(boss.getY() > startY,
                "Obj_MGZEndBoss MoveSprite2 runs before Obj_Wait during the descent timer");
    }

    @Test
    void endBossDoesNotDropImmediatelyAfterFirstSwingTimer() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzEndBossInstance boss = createEndBoss(services);
        setPrivateInt(boss, "waitTimer", 0);
        boss.getState().routine = staticInt("ROUTINE_END_SWING");

        boss.update(1, null);

        assertNotEquals(staticInt("ROUTINE_END_FLOOR_DROP"), boss.getState().routine,
                "ROM loc_6C44C/loc_6C45A performs an angle-settle phase before the floor drop");
    }

    @Test
    void endBossFloorDropWaitsForTerrainFloorInsteadOfFixedY700() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzEndBossInstance boss = createEndBoss(services);
        setPrivateInt(boss, "waitTimer", 0);
        setPrivateInt(boss, "yVel", 0x400);
        boss.getState().routine = staticInt("ROUTINE_END_FLOOR_DROP");
        boss.getState().y = 0x0700;

        boss.update(1, null);

        assertEquals(staticInt("ROUTINE_END_FLOOR_DROP"), boss.getState().routine,
                "ObjHitFloor_DoRoutine should not fire from a fixed y=$700 threshold");
        assertEquals(0, services.playedSfxCount,
                "floor-impact SFX should only play after terrain collision reports a floor hit");
    }

    @Test
    void endBossFloorImpactWaitsBeforeStartingUpwardRecovery() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzEndBossInstance boss = createEndBoss(services);
        setPrivateInt(boss, "waitTimer", 0);
        setPrivateInt(boss, "yVel", 0x400);
        boss.getState().routine = staticInt("ROUTINE_END_FLOOR_DROP");
        boss.getState().x = 0x3D20;
        boss.getState().y = 0x0668;

        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), anyInt()))
                    .thenReturn(new TerrainCheckResult(0, (byte) 0, 0));
            boss.update(1, null);
        }

        assertEquals(0x3F, getPrivateInt(boss, "waitTimer"),
                "ROM loc_6C4BE waits $3F frames after floor impact before recovery velocity is set");
        assertEquals(0x400, getPrivateInt(boss, "yVel"),
                "The first impact frame should not immediately start the upward recovery");
        assertNotEquals(staticInt("ROUTINE_END_RECOVER"), boss.getState().routine);
    }

    @Test
    void endBossRecoveryAdvancesIntoAirPhaseSetupAfterCollapseHandoff() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzEndBossInstance boss = createEndBoss(services);
        setPrivateInt(boss, "waitTimer", 0);
        setPrivateInt(boss, "yVel", 0);
        boss.getState().routine = staticInt("ROUTINE_END_RECOVER");
        boss.getState().x = 0x3D20;
        boss.getState().y = 0x0600;

        for (int frame = 0; frame < 320 && boss.getState().x != 0x3E80; frame++) {
            boss.update(frame, null);
        }

        assertEquals(0x3E80, boss.getState().x,
                "ROM loc_6C598 repositions Robotnik to the air-boss entry point after the collapse/recovery waits");
        assertEquals(0x0700, boss.getState().y);
        assertEquals(staticInt("ROUTINE_END_AIR_APPROACH"), boss.getState().routine,
                "Robotnik should enter the post-collapse air sequence instead of swinging forever at center screen");
    }

    @Test
    void airPhaseKeepsDrillParentMappingFrameAndDoesNotUseChildCountAsBodyFrame() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzEndBossInstance boss = createEndBoss(services);
        boss.update(0, null);
        boss.getState().routine = staticInt("ROUTINE_END_AIR_APPROACH");
        boss.getState().x = 0x3E80;
        boss.getState().y = 0x0700;

        boss.appendRenderCommands(new ArrayList<>());

        verify(services.drillRenderer).drawFrameIndex(eq(0), eq(0x3E80), eq(0x0700), anyBoolean(), eq(false));
        verify(services.drillRenderer, never()).drawFrameIndex(eq(6), eq(0x3E80), eq(0x0700), anyBoolean(), eq(false));
    }

    @Test
    void endBossDrillHeadRenderAndTouchFollowRomAngleTables() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzEndBossInstance boss = createEndBoss(services);
        boss.update(0, null);
        setPrivateInt(boss, "waitTimer", 0);
        setPrivateInt(boss, "endBossAngle", 4);
        boss.getState().routine = staticInt("ROUTINE_END_ANGLE_SETTLE");

        boss.appendRenderCommands(new ArrayList<>());
        TouchResponseProvider.TouchRegion[] regions = boss.getMultiTouchRegions();

        verify(services.drillRenderer).drawFrameIndex(eq(0x1E), eq(0x3D2C), eq(0x0684), eq(false), eq(false));
        verify(services.drillRenderer).drawFrameIndex(eq(0x20), eq(0x3D2C), eq(0x06A4), eq(false), eq(false));
        assertEquals(0x3D2C, regions[1].x(),
                "loc_6C948/loc_6C9E8 derive the drill-tip child from angle-indexed ROM tables");
        assertEquals(0x06A4, regions[1].y());
        assertEquals(0x8B, regions[1].collisionFlags());
    }

    @Test
    void enteringAirPhaseUsesRomChildPoseForLowerDrillPiecesAndHurtRegions() throws Exception {
        RecordingServices services = new RecordingServices(camera);
        MgzEndBossInstance boss = createEndBoss(services);
        boss.update(0, null);
        setPrivateInt(boss, "waitTimer", 0);
        setPrivateInt(boss, "yVel", 0);
        boss.getState().routine = staticInt("ROUTINE_END_AIR_RISE");

        boss.update(1, null);
        boss.appendRenderCommands(new ArrayList<>());
        TouchResponseProvider.TouchRegion[] regions = boss.getMultiTouchRegions();

        verify(services.drillRenderer, times(2))
                .drawFrameIndex(eq(0x18), anyInt(), anyInt(), eq(true), eq(false));
        verify(services.drillRenderer, times(2))
                .drawFrameIndex(eq(0x1B), anyInt(), anyInt(), eq(true), eq(false), eq(0));
        assertEquals(0x3E58, regions[2].x(),
                "loc_6D710 sets $3A=6; loc_6CEB0/loc_6CF20 use that pose for flame hurtboxes");
        assertEquals(0x0708, regions[2].y());
        assertEquals(0x3E6C, regions[3].x());
        assertEquals(0x0708, regions[3].y());
    }

    @Test
    void endBossAirAttackWaitRepositionsFromCameraForNextSweep() throws Exception {
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);
        RecordingServices services = new RecordingServices(camera);
        MgzEndBossInstance boss = createEndBoss(services);
        setPrivateInt(boss, "waitTimer", 0);
        boss.getState().routine = staticInt("ROUTINE_END_ATTACK_WAIT");
        boss.getState().x = 0x3E00;
        boss.getState().y = 0x0700;

        boss.update(1, null);

        assertEquals(staticInt("ROUTINE_END_ATTACK_WAIT"), boss.getState().routine,
                "ROM loc_6C646/loc_6D710 keeps Obj_Wait active for the short pre-sweep pause");
        assertEquals(0x3C40, boss.getState().x);
        assertEquals(0x0670, boss.getState().y);
        assertEquals(0x0200, getPrivateInt(boss, "xVel"));
        assertEquals(0, getPrivateInt(boss, "yVel"));
    }

    @Test
    void successiveAirAttackSetupsUseRomPatternSequence() throws Exception {
        camera.setX((short) 0x3C80);
        camera.setY((short) 0x0600);
        RecordingServices services = new RecordingServices(camera);
        MgzEndBossInstance boss = createEndBoss(services);
        setPrivateInt(boss, "waitTimer", 0);
        boss.getState().routine = staticInt("ROUTINE_END_AIR_SWEEP");
        boss.getState().x = 0x3E00;

        boss.update(1, null); // loc_6C614 creates the first scaled warning child
        setPrivateInt(boss, "waitTimer", 0);
        boss.update(2, null); // loc_6D710 uses byte_6D708 entry 0
        assertEquals(0x3C40, boss.getState().x);
        assertEquals(0x0670, boss.getState().y);

        boss.getState().routine = staticInt("ROUTINE_END_AIR_SWEEP");
        boss.getState().x = 0x3E00;
        boss.update(3, null); // next byte_6D708 entry is 8
        setPrivateInt(boss, "waitTimer", 0);
        boss.update(4, null);

        assertEquals(0x3D20, boss.getState().x,
                "byte_6D708's second entry selects word_6D744 offset 8 for the next air attack");
        assertEquals(0x05B0, boss.getState().y);
        assertEquals(0x0080, getPrivateInt(boss, "xVel"));
        assertEquals(0x0200, getPrivateInt(boss, "yVel"));
        assertEquals(4, getPrivateInt(boss, "endBossAngle"));
        assertEquals(8, getPrivateInt(boss, "drillChildPose"));
    }

    private static MgzDrillingRobotnikInstance createBoss(RecordingServices services) {
        MgzDrillingRobotnikInstance boss = new MgzDrillingRobotnikInstance(
                new ObjectSpawn(0x08E0, 0x0690, 0, 0, 0, false, 0), false);
        boss.setServices(services);
        return boss;
    }

    private static MgzEndBossInstance createEndBoss(RecordingServices services) {
        MgzEndBossInstance boss = new MgzEndBossInstance(
                new ObjectSpawn(0x3D20, 0x0668, Sonic3kObjectIds.MGZ_END_BOSS, 0, 0, false, 0));
        boss.setServices(services);
        return boss;
    }

    private static int staticInt(String fieldName) throws Exception {
        Field field = MgzDrillingRobotnikInstance.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static void setPrivateInt(Object target, String fieldName, int value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static int getPrivateInt(Object target, String fieldName) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
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
        private int playedSfxCount;

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

        @Override
        public void playSfx(int soundId) {
            playedSfxCount++;
        }
    }
}
