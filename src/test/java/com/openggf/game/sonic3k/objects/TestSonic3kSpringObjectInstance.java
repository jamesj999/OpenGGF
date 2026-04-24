package com.openggf.game.sonic3k.objects;

import com.openggf.game.EngineServices;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameStateManager;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestSonic3kSpringObjectInstance {

    private static final class TestableSprite extends AbstractPlayableSprite {
        TestableSprite(String code) {
            super(code, (short) 0, (short) 0);
        }

        @Override
        public void draw() {
        }

        @Override
        public void defineSpeeds() {
        }

        @Override
        protected void createSensorLines() {
        }
    }

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        SessionManager.clear();
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void verticalSpringSolidParamsMatchRom() {
        Sonic3kSpringObjectInstance spring = new Sonic3kSpringObjectInstance(
                new ObjectSpawn(0x100, 0x100, Sonic3kObjectIds.SPRING, 0x00, 0, false, 0));

        SolidObjectParams params = spring.getSolidParams();

        assertEquals(27, params.halfWidth());
        assertEquals(8, params.airHalfHeight());
        assertEquals(16, params.groundHalfHeight(),
                "S3K vertical springs use d3=$10 in the ROM standing path");
    }

    @Test
    void upSpringPositionNudgePreservesYSubpixel() throws Exception {
        Sonic3kSpringObjectInstance spring = new Sonic3kSpringObjectInstance(
                new ObjectSpawn(0x100, 0x100, Sonic3kObjectIds.SPRING, 0x00, 0, false, 0));
        spring.setServices(new TestObjectServices().withGameState(new GameStateManager()));
        invoke(spring, "ensureInitialized");

        TestableSprite player = new TestableSprite("sonic");
        player.setCentreY((short) 0x0100);
        player.setSubpixelRaw(0, 0x5200);

        invoke(spring, "applyUpSpring", new Class<?>[]{AbstractPlayableSprite.class}, player);

        assertEquals(0x0108, player.getCentreY() & 0xFFFF);
        assertEquals(0x5200, player.getYSubpixelRaw(),
                "ROM addq.w to y_pos preserves the existing subpixel fraction");
    }

    @Test
    void upSpringPreservesGSpeedWhenSubtypeDoesNotOverrideInertia() throws Exception {
        Sonic3kSpringObjectInstance spring = new Sonic3kSpringObjectInstance(
                new ObjectSpawn(0x100, 0x100, Sonic3kObjectIds.SPRING, 0x00, 0, false, 0));
        spring.setServices(new TestObjectServices().withGameState(new GameStateManager()));
        invoke(spring, "ensureInitialized");

        TestableSprite player = new TestableSprite("sonic");
        player.setXSpeed((short) 0x0240);
        player.setGSpeed((short) 0x0B54);

        invoke(spring, "applyUpSpring", new Class<?>[]{AbstractPlayableSprite.class}, player);

        assertEquals(0x0240, player.getXSpeed() & 0xFFFF,
                "Up spring without subtype bit 7 should leave xSpeed unchanged");
        assertEquals(0x0B54, player.getGSpeed() & 0xFFFF,
                "ROM up spring leaves inertia untouched unless subtype bit 0 overrides it");
    }

    @Test
    void horizontalSpringPositionNudgePreservesXSubpixelAndUsesMoveLock() throws Exception {
        Sonic3kSpringObjectInstance spring = new Sonic3kSpringObjectInstance(
                new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.SPRING, 0x10, 0, false, 0));
        spring.setServices(new TestObjectServices().withGameState(new GameStateManager()));
        invoke(spring, "ensureInitialized");

        TestableSprite player = new TestableSprite("sonic");
        player.setCentreX((short) 0x0200);
        player.setSubpixelRaw(0x3700, 0);

        invoke(spring, "applyHorizontalSpring", new Class<?>[]{AbstractPlayableSprite.class}, player);

        assertEquals(0x01F8, player.getCentreX() & 0xFFFF);
        assertEquals(0x3700, player.getXSubpixelRaw(),
                "ROM addq/subi.w x_pos during horizontal spring launch preserves x_sub");
        assertEquals(0x1000, player.getXSpeed() & 0xFFFF);
        assertEquals(0x1000, player.getGSpeed() & 0xFFFF);
        assertEquals(15, player.getMoveLockTimer());
        assertFalse(player.getAir(), "Horizontal springs keep the player grounded");
    }

    @Test
    void airborneHorizontalSpringSideContactLaunchesWithoutGroundPushingFlag() throws Exception {
        Sonic3kSpringObjectInstance spring = new Sonic3kSpringObjectInstance(
                new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.SPRING, 0x10, 0, false, 0));
        spring.setServices(new TestObjectServices().withGameState(new GameStateManager()));
        invoke(spring, "ensureInitialized");

        TestableSprite player = new TestableSprite("sonic");
        player.setCentreX((short) 0x0208);
        player.setAir(true);
        player.setRolling(true);
        player.setXSpeed((short) -0x05CF);
        player.setGSpeed((short) -0x05CF);

        spring.onSolidContact(player, new SolidContact(false, true, false, false, false, 0, false), 0);

        assertEquals(0x0200, player.getCentreX() & 0xFFFF,
                "sub_23190 applies the horizontal spring nudge even for airborne side contact");
        assertEquals(0x1000, player.getXSpeed() & 0xFFFF);
        assertEquals(0x1000, player.getGSpeed() & 0xFFFF);
    }

    @Test
    void horizontalSpringOptsIntoInclusiveSolidRightEdge() throws Exception {
        Sonic3kSpringObjectInstance horizontal = new Sonic3kSpringObjectInstance(
                new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.SPRING, 0x10, 0, false, 0));
        horizontal.setServices(new TestObjectServices().withGameState(new GameStateManager()));
        invoke(horizontal, "ensureInitialized");

        Sonic3kSpringObjectInstance vertical = new Sonic3kSpringObjectInstance(
                new ObjectSpawn(0x0200, 0x0100, Sonic3kObjectIds.SPRING, 0x00, 0, false, 0));
        vertical.setServices(new TestObjectServices().withGameState(new GameStateManager()));
        invoke(vertical, "ensureInitialized");

        assertEquals(true, horizontal.usesInclusiveRightEdge(),
                "Obj_Spring_Horizontal uses SolidObjectFull2_1P, whose x-window rejects with bhi");
        assertEquals(false, vertical.usesInclusiveRightEdge());
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        return invoke(target, methodName, new Class<?>[0]);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] argTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, argTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
