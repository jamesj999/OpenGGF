package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestMgzEndBossHandoffHeadless {

    private static SharedLevel sharedLevel;
    private static Object oldSkipIntros;
    private static Object oldMainCharacter;
    private static Object oldSidekickCharacter;

    private HeadlessTestFixture fixture;

    @BeforeAll
    static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        oldSidekickCharacter = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_MGZ, 1);
    }

    @AfterAll
    static void cleanup() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                oldSidekickCharacter != null ? oldSidekickCharacter : "tails");
        if (sharedLevel != null) {
            sharedLevel.dispose();
            sharedLevel = null;
        }
    }

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder().withSharedLevel(sharedLevel).build();
        fixture.camera().setX((short) 0x3C80);
        fixture.camera().setY((short) 0x0600);
        fixture.camera().setMinX((short) 0x3C80);
        fixture.camera().setMaxX((short) 0x3C80);
        fixture.camera().setMinY((short) 0x06A0);
        fixture.camera().setMaxY((short) 0x06A0);
        fixture.sprite().setCentreX((short) 0x3CC0);
        fixture.sprite().setCentreY((short) 0x0700);
        fixture.sprite().setAir(true);
    }

    @Test
    void liveEndBossFloorImpactStartsRealTailsRescueForSonicAlone() throws Exception {
        MgzEndBossInstance boss = new MgzEndBossInstance(new ObjectSpawn(
                0x3D20, 0x0668, Sonic3kObjectIds.MGZ_END_BOSS, 0, 0, false, 0));
        GameServices.level().getObjectManager().addDynamicObject(boss);
        setPrivateInt(boss, "waitTimer", 0);
        setPrivateInt(boss, "yVel", 0x400);
        boss.getState().routine = staticInt("ROUTINE_END_FLOOR_DROP");
        boss.getState().x = 0x3D20;
        boss.getState().y = 0x0668;

        for (int frame = 0; frame < 240 && !isBossTransitionActive(); frame++) {
            fixture.stepIdleFrames(1);
        }

        assertTrue(isBossTransitionActive(),
                "Real Obj_MGZEndBoss floor impact should activate Obj_MGZ2_BossTransition through the production frame loop");
        assertEquals(1, GameServices.sprites().getSidekicks().size(),
                "Sonic-alone MGZ2 boss transition must add rescue Tails to the real SpriteManager");
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().getFirst();
        assertEquals("tails", GameServices.sprites().getSidekickCharacterName(tails));
        assertNotNull(tails.getSpriteRenderer(),
                "Runtime-spawned rescue Tails must have real S3K sidekick art loaded");
        assertEquals(SidekickCpuController.State.MGZ_RESCUE_WAIT, tails.getCpuController().getState());
        assertTrue(mgzEvents().isBossTransitionDeathPlaneDisabled(),
                "The real boss handoff must disable the death plane before Sonic falls below the arena");
        assertFalse(fixture.sprite().getDead(),
                "Sonic should not die during the boss-transition rescue wait");

        String badStateDiagnostic = null;
        StringBuilder carrySamples = new StringBuilder();
        for (int frame = 0; frame < 0x168 + 4; frame++) {
            fixture.stepIdleFrames(1);
            SidekickCpuController.State state = tails.getCpuController().getState();
            if (state == SidekickCpuController.State.CARRY_INIT
                    || state == SidekickCpuController.State.CARRYING
                    || frame % 60 == 0) {
                if (carrySamples.length() < 1200) {
                    carrySamples.append(String.format(
                            " f=%d state=%s tails=(%04X,%04X topY=%04X yv=%04X flag=%02X prop=%02X) sonic=(%04X,%04X topY=%04X yv=%04X ctrl=%s);",
                            frame,
                            state,
                            tails.getCentreX() & 0xFFFF,
                            tails.getCentreY() & 0xFFFF,
                            tails.getY() & 0xFFFF,
                            tails.getYSpeed() & 0xFFFF,
                            tails.getDoubleJumpFlag() & 0xFF,
                            tails.getDoubleJumpProperty() & 0xFF,
                            fixture.sprite().getCentreX() & 0xFFFF,
                            fixture.sprite().getCentreY() & 0xFFFF,
                            fixture.sprite().getY() & 0xFFFF,
                            fixture.sprite().getYSpeed() & 0xFFFF,
                            fixture.sprite().isObjectControlled()));
                }
            }
            if (state == SidekickCpuController.State.SPAWNING && badStateDiagnostic == null) {
                badStateDiagnostic = String.format(
                        "frame=%d tails=(%04X,%04X topY=%04X) sonic=(%04X,%04X topY=%04X) "
                                + "camera=(%04X,%04X maxY=%04X targetMaxY=%04X) sonicControlled=%s sonicDead=%s",
                        frame,
                        tails.getCentreX() & 0xFFFF,
                        tails.getCentreY() & 0xFFFF,
                        tails.getY() & 0xFFFF,
                        fixture.sprite().getCentreX() & 0xFFFF,
                        fixture.sprite().getCentreY() & 0xFFFF,
                        fixture.sprite().getY() & 0xFFFF,
                        fixture.camera().getX() & 0xFFFF,
                        fixture.camera().getY() & 0xFFFF,
                        fixture.camera().getMaxY() & 0xFFFF,
                        fixture.camera().getMaxYTarget() & 0xFFFF,
                        fixture.sprite().isObjectControlled(),
                        fixture.sprite().getDead());
            }
        }

        assertTrue(fixture.sprite().isObjectControlled(),
                "After the ROM $168-frame wait, rescue Tails must pick up Sonic instead of leaving him falling");
        assertEquals(SidekickCpuController.State.CARRYING, tails.getCpuController().getState(),
                "The real sidekick update loop should advance MGZ rescue Tails from routine $14 into carrying"
                        + (badStateDiagnostic != null ? ": " + badStateDiagnostic : "")
                        + " samples:" + carrySamples);
        assertEquals(0x22, fixture.sprite().resolveAnimationId(CanonicalAnimation.TAILS_CARRIED),
                "ROM sub_1459E writes Sonic anim=$22 when Tails picks him up");
        assertEquals(0x22, fixture.sprite().getForcedAnimationId(),
                "Carried Sonic should keep the native $22 carried pose while parented to Tails");
        assertEquals(tails.getCentreX(), fixture.sprite().getCentreX(),
                "Carried Sonic should be parented to Tails on X");
        assertEquals(tails.getCentreY() + 0x1C, fixture.sprite().getCentreY(),
                "Carried Sonic should be parented to Tails.y+$1C");
    }

    @Test
    void bossTransitionRoutine12LeavesTailsFreeToFallDuringWait() {
        mgzEvents().triggerBossCollapseHandoff();

        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().getFirst();
        assertEquals(SidekickCpuController.State.MGZ_RESCUE_WAIT, tails.getCpuController().getState(),
                "Obj_MGZ2_BossTransition leaves rescue Tails at CPU routine $12 during the $168-frame wait");
        assertFalse(tails.isObjectControlled(),
                "ROM routine $12 only clears Ctrl_2_logical; it does not hold Tails with object_control");

        int startY = tails.getCentreY() & 0xFFFF;
        fixture.stepIdleFrames(16);

        assertTrue((tails.getCentreY() & 0xFFFF) > startY,
                "Routine $12 Tails should fall under normal freespace physics while waiting for the handoff timer");
        assertEquals(SidekickCpuController.State.MGZ_RESCUE_WAIT, tails.getCpuController().getState(),
                "Tails should remain in routine $12 until Obj_MGZ2_BossTransition promotes him");
    }

    @Test
    void bossTransitionInitialPickupUsesCameraAnchoredTransitionX() {
        mgzEvents().triggerBossCollapseHandoff();

        AbstractPlayableSprite player = fixture.sprite();
        player.setCentreX((short) 0x3D40);
        player.setCentreY((short) 0x06F0);
        player.setAir(true);

        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().getFirst();
        for (int frame = 0; frame < 0x168 + 8
                && tails.getCpuController().getState() != SidekickCpuController.State.CARRYING; frame++) {
            fixture.stepIdleFrames(1);
        }

        assertEquals(SidekickCpuController.State.CARRYING, tails.getCpuController().getState(),
                "After the $168-frame wait and Tails falling below the transition object, CPU routine $14 should pick up Sonic");
        assertEquals(0x3CC0, tails.getCentreX() & 0xFFFF,
                "Before Tails CPU routine reaches $14, Obj_MGZ2_BossTransition keeps x_pos at Camera_X+$40");
        assertEquals(0x3CC0, player.getCentreX() & 0xFFFF,
                "sub_1459E should parent Sonic to the transition object's Camera_X+$40 anchor on initial pickup");
    }

    private boolean isBossTransitionActive() {
        return mgzEvents().isBossTransitionDeathPlaneDisabled();
    }

    private com.openggf.game.sonic3k.events.Sonic3kMGZEvents mgzEvents() {
        var provider = GameServices.module().getLevelEventProvider();
        assertTrue(provider instanceof Sonic3kLevelEventManager,
                "S3K event provider must be Sonic3kLevelEventManager");
        var events = ((Sonic3kLevelEventManager) provider).getMgzEvents();
        assertNotNull(events, "MGZ events should be initialized for MGZ act 2");
        return events;
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
}
