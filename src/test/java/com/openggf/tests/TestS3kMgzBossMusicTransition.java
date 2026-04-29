package com.openggf.tests;

import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.MgzDrillingRobotnikInstance;
import com.openggf.game.sonic3k.objects.MgzEndBossInstance;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestS3kMgzBossMusicTransition {

    @Test
    void drillingRobotnikFadesZoneMusicBeforeDelayedBossMusic() {
        RecordingServices services = new RecordingServices();
        MgzDrillingRobotnikInstance robotnik = new MgzDrillingRobotnikInstance(
                new ObjectSpawn(0x08E0, 0x0690, 0, 0, 0, false, 0),
                false);
        robotnik.setServices(services);

        robotnik.update(0, null);

        assertEquals(1, services.fadeOutCount,
                "MGZ2 drilling Robotnik should issue the ROM init-time music fade-out");
        assertTrue(services.playedMusic.isEmpty(),
                "Boss music should wait for the ROM 2-second Obj_Wait delay");

        for (int frame = 1; frame < 120; frame++) {
            robotnik.update(frame, null);
        }

        assertEquals(List.of(Sonic3kMusic.BOSS.id), services.playedMusic,
                "Boss music should start after the ROM 120-frame wait");
    }

    @Test
    void endBossFadesZoneMusicBeforeDelayedBossMusic() {
        RecordingServices services = new RecordingServices();
        MgzEndBossInstance boss = new MgzEndBossInstance(
                new ObjectSpawn(0x3D20, 0x0668, Sonic3kObjectIds.MGZ_END_BOSS, 0, 0, false, 0));
        boss.setServices(services);

        boss.update(0, null);

        assertEquals(1, services.fadeOutCount,
                "MGZ2 end boss should issue the ROM init-time music fade-out");
        assertTrue(services.playedMusic.isEmpty(),
                "End-boss music should wait for the ROM 2-second Obj_Wait delay");

        for (int frame = 1; frame < 120; frame++) {
            boss.update(frame, null);
        }

        assertEquals(List.of(Sonic3kMusic.BOSS.id), services.playedMusic,
                "End-boss music should start after the ROM 120-frame wait");
    }

    @Test
    void endBossDrawsDrillPieceBehindMainBodyWhenItAppears() {
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        PatternSpriteRenderer drillRenderer = mock(PatternSpriteRenderer.class);
        PatternSpriteRenderer shipRenderer = mock(PatternSpriteRenderer.class);
        when(renderManager.getRenderer(Sonic3kObjectArtKeys.MGZ_ENDBOSS)).thenReturn(drillRenderer);
        when(renderManager.getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP)).thenReturn(shipRenderer);
        when(drillRenderer.isReady()).thenReturn(true);
        when(shipRenderer.isReady()).thenReturn(true);

        RecordingServices services = new RecordingServices(renderManager);
        MgzEndBossInstance boss = new MgzEndBossInstance(
                new ObjectSpawn(0x3D20, 0x0668, Sonic3kObjectIds.MGZ_END_BOSS, 0, 0, false, 0));
        boss.setServices(services);

        for (int frame = 0; frame < 120; frame++) {
            boss.update(frame, null);
        }
        boss.appendRenderCommands(new ArrayList<>());

        InOrder order = inOrder(drillRenderer);
        order.verify(drillRenderer).drawFrameIndex(1, 0x3D0C, 0x0677, false, false);
        order.verify(drillRenderer).drawFrameIndex(0, 0x3D20, 0x0668, false, false);
    }

    private static final class RecordingServices extends StubObjectServices {
        private final ObjectRenderManager renderManager;
        private int fadeOutCount;
        private final List<Integer> playedMusic = new ArrayList<>();

        private RecordingServices() {
            this(null);
        }

        private RecordingServices(ObjectRenderManager renderManager) {
            this.renderManager = renderManager;
        }

        @Override
        public ObjectRenderManager renderManager() {
            return renderManager;
        }

        @Override
        public void fadeOutMusic() {
            fadeOutCount++;
        }

        @Override
        public void playMusic(int musicId) {
            playedMusic.add(musicId);
        }
    }
}
