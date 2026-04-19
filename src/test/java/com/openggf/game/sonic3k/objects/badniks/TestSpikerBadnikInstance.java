package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@ExtendWith(SingletonResetExtension.class)
@FullReset
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class TestSpikerBadnikInstance {

    @BeforeEach
    void setUp() {
        RuntimeManager.destroyCurrent();
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    void registryCreatesSpikerInstance() {
        ObjectInstance instance = new com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry().create(
                new ObjectSpawn(0x120, 0x100, Sonic3kObjectIds.SPIKER, 0, 0, false, 0));
        assertInstanceOf(SpikerBadnikInstance.class, instance);
    }

    @Test
    void nearbyPlayerOpensBodyAndLeftLauncherFiresProjectile() throws Exception {
        RecordingServices services = new RecordingServices();
        SpikerBadnikInstance spiker = new SpikerBadnikInstance(
                new ObjectSpawn(0x120, 0x100, Sonic3kObjectIds.SPIKER, 0, 0, false, 0));
        spiker.setServices(services);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x100, (short) 0x100);

        spiker.update(0, player);
        assertEquals(3, services.spawnedChildren.size(), "Spiker should create two launchers and the top spike");

        for (int frame = 1; frame <= 10; frame++) {
            spiker.update(frame, player);
        }

        assertEquals("OPEN", readState(spiker));
        assertEquals(0x0F8, spiker.getY(), "Spiker should rise 8 pixels while opening");

        AbstractObjectInstance leftLauncher = findChild(services.spawnedChildren, 0x110, 0x104);
        leftLauncher.update(11, player);
        assertFalse(services.playedSfx.contains(Sonic3kSfx.PROJECTILE.id),
                "Launcher should not fire on the trigger frame");

        for (int frame = 12; frame <= 30; frame++) {
            leftLauncher.update(frame, player);
        }
        assertFalse(services.playedSfx.contains(Sonic3kSfx.PROJECTILE.id),
                "Launcher should not reach frame 4 before the ROM delay elapses");

        leftLauncher.update(31, player);

        assertTrue(services.playedSfx.contains(Sonic3kSfx.PROJECTILE.id), "Expected projectile SFX");
        assertTrue(services.spawnedChildren.size() >= 4, "Expected launcher to spawn a spike projectile");
    }

    @Test
    void topSpikeTouchStartsCompressionThenLaunchesPlayerUpward() throws Exception {
        RecordingServices services = new RecordingServices();
        SpikerBadnikInstance spiker = new SpikerBadnikInstance(
                new ObjectSpawn(0x120, 0x100, Sonic3kObjectIds.SPIKER, 0, 0, false, 0));
        spiker.setServices(services);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x100, (short) 0x100);

        spiker.update(0, player);
        for (int frame = 1; frame <= 10; frame++) {
            spiker.update(frame, player);
        }

        player.setCentreX((short) 0x120);
        player.setCentreY((short) 0x0F0);
        AbstractObjectInstance topSpike = findChild(services.spawnedChildren, 0x120, 0x0EC);
        TouchResponseListener listener = (TouchResponseListener) topSpike;
        TouchResponseProvider provider = (TouchResponseProvider) topSpike;
        TouchResponseResult result = new TouchResponseResult(0x0A, 0, 0, TouchCategory.SPECIAL);

        listener.onTouchResponse(player, result, 11);
        assertEquals(0, player.getYSpeed());
        assertEquals(0x0F6, player.getCentreY());
        assertEquals(0, spiker.getCollisionFlags(), "Parent hurtbox should disable during compression");
        assertTrue(services.playedSfx.contains(Sonic3kSfx.SPRING.id), "Expected spring SFX");

        for (int frame = 12; frame <= 26; frame++) {
            spiker.update(frame, player);
        }

        assertEquals(-0x600, player.getYSpeed());
        assertEquals("OPEN", readState(spiker));
        assertEquals(0x0A, spiker.getCollisionFlags(), "Parent hurtbox should restore after the launch anim");
        assertEquals(0, provider.getCollisionFlags(), "Top spike should still be in cooldown");
    }

    @Test
    void unloadDestroysChildrenSoLaunchersCannotKeepRunningOffscreen() {
        RecordingServices services = new RecordingServices();
        SpikerBadnikInstance spiker = new SpikerBadnikInstance(
                new ObjectSpawn(0x120, 0x100, Sonic3kObjectIds.SPIKER, 0, 0, false, 0));
        spiker.setServices(services);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x100, (short) 0x100);

        spiker.update(0, player);
        assertEquals(3, services.spawnedChildren.size(), "Expected launcher and top-spike children");

        spiker.onUnload();
        for (ObjectInstance child : services.spawnedChildren) {
            assertTrue(child.isDestroyed(), "Unload should mark child objects destroyed");
            child.update(1, player);
        }

        assertTrue(services.playedSfx.isEmpty(), "Destroyed children must not keep firing after unload");
        assertEquals(3, services.spawnedChildren.size(), "Unload should not spawn replacement children");
    }

    private static String readState(SpikerBadnikInstance spiker) throws Exception {
        Field field = SpikerBadnikInstance.class.getDeclaredField("state");
        field.setAccessible(true);
        return String.valueOf(field.get(spiker));
    }

    private static AbstractObjectInstance findChild(List<ObjectInstance> children, int x, int y) {
        return children.stream()
                .filter(AbstractObjectInstance.class::isInstance)
                .map(AbstractObjectInstance.class::cast)
                .filter(child -> child.getX() == x && child.getY() == y)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing child at (" + x + ", " + y + ")"));
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
