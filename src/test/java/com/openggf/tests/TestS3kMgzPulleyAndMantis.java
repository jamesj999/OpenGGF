package com.openggf.tests;

import com.openggf.game.EngineServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.MGZPulleyObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.objects.badniks.MantisBadnikInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kMgzPulleyAndMantis {

    @BeforeEach
    void setUp() {
        RuntimeManager.destroyCurrent();
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        AbstractObjectInstance.updateCameraBounds(0, 0, 1024, 1024, 0);
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        clearConstructionContext();
    }

    @Test
    void registryCreatesMgzPulleyAndMantisInstances() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();
        RecordingServices services = new RecordingServices();

        setConstructionContext(services);
        ObjectInstance pulley;
        ObjectInstance mantis;
        try {
            pulley = registry.create(
                    new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.MGZ_PULLEY, 0x00, 0x00, false, 0));
            mantis = registry.create(
                    new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.MANTIS, 0x00, 0x00, false, 0));
        } finally {
            clearConstructionContext();
        }

        assertInstanceOf(MGZPulleyObjectInstance.class, pulley);
        assertInstanceOf(MantisBadnikInstance.class, mantis);
    }

    @Test
    void mgzPulleyCapturesApproachingPlayerAndJumpLaunchesLeft() throws Exception {
        RecordingServices services = new RecordingServices();
        MGZPulleyObjectInstance pulley = createPulley(services,
                new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.MGZ_PULLEY, 0x00, 0x00, false, 0));
        pulley.setServices(services);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x01DA, (short) 0x012E);
        player.setXSpeed((short) -0x100);
        player.setJumpInputPressed(false);

        pulley.update(0, player);

        assertTrue(player.isObjectControlled(), "Pulley should capture a player entering the handle box");
        assertEquals(0x01DA, player.getCentreX());
        assertEquals(0x012E, player.getCentreY());
        assertEquals(Sonic3kAnimationIds.GET_UP.id(), player.getAnimationId());
        assertTrue(services.playedSfx.contains(Sonic3kSfx.PULLEY_GRAB.id));

        player.setJumpInputPressed(true);
        pulley.update(1, player);

        assertFalse(player.isObjectControlled(), "Jump should release object control");
        assertEquals(-0x400, player.getXSpeed());
        assertEquals(-0x600, player.getYSpeed());
        assertTrue(player.getRolling());
        assertEquals(Sonic3kAnimationIds.ROLL.id(), player.getAnimationId());
    }

    @Test
    void mgzPulleyExpandsDuringRecoveryThenRetractsWhileHeld() throws Exception {
        RecordingServices services = new RecordingServices();
        MGZPulleyObjectInstance pulley = createPulley(services,
                new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.MGZ_PULLEY, 0x04, 0x00, false, 0));
        pulley.setServices(services);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x01CA, (short) 0x014E);
        player.setXSpeed((short) -0x100);
        player.setJumpInputPressed(false);

        pulley.update(0, player);
        assertEquals(0x20, readCurrentExtension(pulley));

        for (int frame = 1; frame <= 4; frame++) {
            pulley.update(frame, player);
        }
        assertEquals(0x28, readCurrentExtension(pulley), "Pulley should overshoot by 8px during release recovery");

        for (int frame = 5; frame <= 26; frame++) {
            pulley.update(frame, player);
        }
        assertEquals(0, readCurrentExtension(pulley), "Pulley should fully retract while the player remains hanging");
    }

    @Test
    void mgzPulleyOnUnloadDestroysChainChildAndReleasesGrabbedPlayer() throws Exception {
        RecordingServices services = new RecordingServices();
        MGZPulleyObjectInstance pulley = createPulley(services,
                new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.MGZ_PULLEY, 0x04, 0x00, false, 0));
        pulley.setServices(services);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x01CA, (short) 0x014E);
        player.setXSpeed((short) -0x100);

        pulley.update(0, player);
        assertTrue(player.isObjectControlled(), "Precondition: pulley should own the grabbed player");

        AbstractObjectInstance chainChild = readChainChild(pulley);
        assertFalse(chainChild.isDestroyed(), "Precondition: spawned chain child should still be active");

        pulley.onUnload();

        assertTrue(chainChild.isDestroyed(), "Unloading the parent pulley must destroy its chain child");
        assertFalse(player.isObjectControlled(),
                "Unloading the parent pulley must release the grabbed player from object control");
    }

    @Test
    void mantisNearbyPlayerTriggersLaunchCycle() throws Exception {
        MantisBadnikInstance mantis = new MantisBadnikInstance(
                new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.MANTIS, 0x00, 0x00, false, 0));
        mantis.setServices(new RecordingServices());

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0210, (short) 0x0100);
        player.setCentreX((short) 0x0210);
        player.setCentreY((short) 0x0100);

        mantis.update(0, player); // init
        mantis.update(1, player);
        mantis.update(2, player); // begin prep
        assertEquals("PREPARE", readMantisState(mantis));

        for (int frame = 3; frame <= 9; frame++) {
            mantis.update(frame, player);
        }

        assertEquals("LAUNCH", readMantisState(mantis));
        assertTrue(mantis.getY() < 0x0100, "Mantis should have leapt upward");
    }

    @Test
    void mantisPrepSequenceMatchesDisassemblyTiming() throws Exception {
        MantisBadnikInstance mantis = new MantisBadnikInstance(
                new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.MANTIS, 0x00, 0x00, false, 0));
        mantis.setServices(new RecordingServices());

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0210, (short) 0x0100);
        player.setCentreX((short) 0x0210);
        player.setCentreY((short) 0x0100);

        mantis.update(0, player); // init
        mantis.update(1, player); // detect player, enter prep
        assertEquals("PREPARE", readMantisState(mantis));
        assertEquals(0, readMantisMappingFrame(mantis));
        assertEquals(0x0100, mantis.getY());

        mantis.update(2, player); // first Animate_RawNoSSTMultiDelay tick
        assertEquals(1, readMantisMappingFrame(mantis));
        assertEquals(0x00FB, mantis.getY());

        mantis.update(3, player); // delay 2: hold
        assertEquals(1, readMantisMappingFrame(mantis));
        assertEquals(0x00FB, mantis.getY());

        mantis.update(4, player); // delay 2: hold
        assertEquals(1, readMantisMappingFrame(mantis));
        assertEquals(0x00FB, mantis.getY());

        mantis.update(5, player); // next frame in script
        assertEquals(2, readMantisMappingFrame(mantis));
        assertEquals(0x00E8, mantis.getY());

        mantis.update(6, player); // $F4 callback arms the jump, movement starts next frame
        assertEquals("LAUNCH", readMantisState(mantis));
        assertEquals(0x00E8, mantis.getY());
    }

    @Test
    void mantisContinuesLaunchWhenOnlyVerticallyOffScreen() throws Exception {
        MantisBadnikInstance mantis = new MantisBadnikInstance(
                new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.MANTIS, 0x00, 0x00, false, 0));
        mantis.setServices(new RecordingServices());

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0x0210, (short) 0x0160);
        player.setCentreX((short) 0x0210);
        player.setCentreY((short) 0x0160);

        mantis.update(0, player); // init
        mantis.update(1, player); // detect player, enter prep
        for (int frame = 2; frame <= 6; frame++) {
            mantis.update(frame, player);
        }

        assertEquals("LAUNCH", readMantisState(mantis));
        assertEquals(0x00E8, mantis.getY());

        // Reproduce the MGZ2 regression: Sonic can stay low enough that the
        // mantis leaves the top of the viewport during its leap. The ROM keeps
        // updating the arc because Obj_WaitOffscreen / Sprite_CheckDeleteTouch
        // only gate on X visibility.
        AbstractObjectInstance.updateCameraBounds(0, 0x00E9, 1024, 0x0200, 0);

        mantis.update(7, player);

        assertNotEquals(0x00E8, mantis.getY(),
                "Mantis should keep moving through its jump arc even when only vertically off-screen");
    }

    private static int readCurrentExtension(MGZPulleyObjectInstance pulley) throws Exception {
        Field field = MGZPulleyObjectInstance.class.getDeclaredField("currentExtension");
        field.setAccessible(true);
        return field.getInt(pulley);
    }

    private static AbstractObjectInstance readChainChild(MGZPulleyObjectInstance pulley) throws Exception {
        Field field = MGZPulleyObjectInstance.class.getDeclaredField("chainChild");
        field.setAccessible(true);
        return (AbstractObjectInstance) field.get(pulley);
    }

    private static MGZPulleyObjectInstance createPulley(ObjectServices services, ObjectSpawn spawn) {
        setConstructionContext(services);
        try {
            return new MGZPulleyObjectInstance(spawn);
        } finally {
            clearConstructionContext();
        }
    }

    private static String readMantisState(MantisBadnikInstance mantis) throws Exception {
        Field field = MantisBadnikInstance.class.getDeclaredField("state");
        field.setAccessible(true);
        return String.valueOf(field.get(mantis));
    }

    private static int readMantisMappingFrame(MantisBadnikInstance mantis) throws Exception {
        Field field = mantis.getClass().getSuperclass().getDeclaredField("mappingFrame");
        field.setAccessible(true);
        return field.getInt(mantis);
    }

    @SuppressWarnings("unchecked")
    private static void setConstructionContext(ObjectServices services) {
        try {
            Field field = AbstractObjectInstance.class.getDeclaredField("CONSTRUCTION_CONTEXT");
            field.setAccessible(true);
            ((ThreadLocal<Object>) field.get(null)).set(services);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearConstructionContext() {
        try {
            Field field = AbstractObjectInstance.class.getDeclaredField("CONSTRUCTION_CONTEXT");
            field.setAccessible(true);
            ((ThreadLocal<Object>) field.get(null)).remove();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class RecordingServices extends StubObjectServices {
        private final List<Integer> playedSfx = new ArrayList<>();

        @Override
        public void playSfx(int soundId) {
            playedSfx.add(soundId);
        }
    }
}
