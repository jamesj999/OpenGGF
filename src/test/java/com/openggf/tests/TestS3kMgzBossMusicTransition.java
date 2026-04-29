package com.openggf.tests;

import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.MgzDrillingRobotnikInstance;
import com.openggf.game.sonic3k.objects.MgzEndBossInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static final class RecordingServices extends StubObjectServices {
        private int fadeOutCount;
        private final List<Integer> playedMusic = new ArrayList<>();

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
