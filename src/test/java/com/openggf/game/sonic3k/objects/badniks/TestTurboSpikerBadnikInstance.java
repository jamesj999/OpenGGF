package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class TestTurboSpikerBadnikInstance {

    @org.junit.Before
    public void setUp() {
        RuntimeManager.destroyCurrent();
    }

    @org.junit.After
    public void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    public void approachingPlayerTriggersShellLaunchSequence() throws Exception {
        try (MockedStatic<ObjectTerrainUtils> ignored = mockWalkableFloor()) {
            RecordingServices services = new RecordingServices();
            TurboSpikerBadnikInstance turboSpiker = new TurboSpikerBadnikInstance(
                    new ObjectSpawn(0x120, 0x100, Sonic3kObjectIds.TURBO_SPIKER, 0x20, 0, false, 0));
            turboSpiker.setServices(services);

            TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0xE0, (short) 0x100);

            turboSpiker.update(0, player);
            assertEquals("PATROL", readState(turboSpiker));
            assertEquals(1, services.spawnedChildren.size());

            turboSpiker.update(1, player);
            assertEquals("LAUNCH_PREP", readState(turboSpiker));

            for (int frame = 2; frame <= 18; frame++) {
                turboSpiker.update(frame, player);
            }

            assertEquals("SHELLLESS_RUN", readState(turboSpiker));
            assertTrue("Expected shell launch SFX", services.playedSfx.contains(Sonic3kSfx.FLOOR_LAUNCHER.id));
            assertTrue("Expected shell trail child after launch", services.spawnedChildren.size() >= 2);
        }
    }

    @Test
    public void hiddenVariantSpawnsOverlayThenEmergesWithSplashBurst() throws Exception {
        try (MockedStatic<ObjectTerrainUtils> ignored = mockWalkableFloor()) {
            RecordingServices services = new RecordingServices();
            TurboSpikerBadnikInstance turboSpiker = new TurboSpikerBadnikInstance(
                    new ObjectSpawn(0x120, 0x100, Sonic3kObjectIds.TURBO_SPIKER, 0x30, 0x02, false, 0));
            turboSpiker.setServices(services);

            TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x100, (short) 0x100);

            turboSpiker.update(0, player);
            assertEquals("HIDDEN_WAIT", readState(turboSpiker));
            assertEquals(2, services.spawnedChildren.size());

            turboSpiker.update(1, player);
            assertEquals("EMERGE_DELAY", readState(turboSpiker));
            assertEquals(7, services.spawnedChildren.size());
            assertTrue("Expected splash SFX", services.playedSfx.contains(Sonic3kSfx.SPLASH.id));

            for (int frame = 2; frame <= 5; frame++) {
                turboSpiker.update(frame, player);
            }
            assertEquals("EMERGE_WATERFALL", readState(turboSpiker));
            assertEquals(3, turboSpiker.getPriorityBucket());

            player.setCentreX((short) 0x40);
            for (int frame = 6; frame <= 22; frame++) {
                turboSpiker.update(frame, player);
            }
            assertEquals("PATROL", readState(turboSpiker));
            assertEquals(5, turboSpiker.getPriorityBucket());
        }
    }

    private static String readState(TurboSpikerBadnikInstance turboSpiker) throws Exception {
        Field field = TurboSpikerBadnikInstance.class.getDeclaredField("state");
        field.setAccessible(true);
        return String.valueOf(field.get(turboSpiker));
    }

    private static MockedStatic<ObjectTerrainUtils> mockWalkableFloor() {
        MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class);
        terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), anyInt()))
                .thenReturn(new TerrainCheckResult(0, (byte) 0, 0));
        return terrain;
    }

    private static final class RecordingServices extends StubObjectServices {
        private final List<Integer> playedSfx = new ArrayList<>();
        private final List<ObjectInstance> spawnedChildren = new ArrayList<>();
        private final ObjectManager objectManager;

        private RecordingServices() {
            objectManager = mock(ObjectManager.class);
            doAnswer(invocation -> {
                ObjectInstance child = invocation.getArgument(0);
                if (child instanceof AbstractObjectInstance instance) {
                    instance.setServices(this);
                }
                spawnedChildren.add(child);
                return null;
            }).when(objectManager).addDynamicObjectAfterCurrent(any());
        }

        @Override
        public ObjectManager objectManager() {
            return objectManager;
        }

        @Override
        public void playSfx(int soundId) {
            playedSfx.add(soundId);
        }
    }
}
