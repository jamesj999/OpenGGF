package com.openggf.tests.trace.s1;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.credits.DemoInputPlayer;
import com.openggf.game.sonic1.credits.Sonic1CreditsDemoData;
import com.openggf.game.sonic1.objects.Sonic1JunctionObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.TraceData;
import com.openggf.tests.trace.TraceExecutionPhase;
import com.openggf.tests.trace.TraceEvent;
import com.openggf.tests.trace.TraceFrame;
import com.openggf.tests.trace.TraceReplayBootstrap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;

@RequiresRom(SonicGame.SONIC_1)
public class DebugS1Credits05SbzJunctionProbe {
    private static final Path TRACE_DIR = Path.of("src/test/resources/traces/s1/credits_05_sbz1");

    @Test
    void dumpJunctionWindow() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        int idx = 5;

        Rom rom = GameServices.rom().getRom();
        byte[] demoData = rom.readBytes(
                Sonic1CreditsDemoData.DEMO_DATA_ADDR[idx],
                Sonic1CreditsDemoData.DEMO_DATA_SIZE[idx]);
        DemoInputPlayer demoPlayer = new DemoInputPlayer(demoData);

        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        Object oldSidekickCharacter = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");

        SharedLevel sharedLevel = SharedLevel.load(
                SonicGame.SONIC_1,
                Sonic1CreditsDemoData.DEMO_ZONE[idx],
                Sonic1CreditsDemoData.DEMO_ACT[idx]);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(sharedLevel)
                    .startPosition((short) Sonic1CreditsDemoData.START_X[idx],
                            (short) Sonic1CreditsDemoData.START_Y[idx])
                    .startPositionIsCentre()
                    .build();

            initialiseDemoPlayerState(fixture.sprite());
            resetStreamingWindows(fixture);
            applyFrameZeroPlayerSnapshot(trace, fixture.sprite());
            GameServices.level().updateObjectPositionsWithoutTouches();

            for (int i = 0; i <= 380; i++) {
                TraceFrame expected = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceFrame next = i + 1 < trace.frameCount() ? trace.getFrame(i + 1) : null;
                TraceExecutionPhase basePhase =
                        TraceReplayBootstrap.phaseForReplay(trace, previous, expected);
                TraceExecutionPhase promotedPhase = basePhase;
                if (promotedPhase == TraceExecutionPhase.VBLANK_ONLY
                        && fixture.sprite().isObjectControlled()
                        && findJunction() != null) {
                    promotedPhase = TraceExecutionPhase.FULL_LEVEL_FRAME;
                }

                if (promotedPhase != TraceExecutionPhase.VBLANK_ONLY) {
                    demoPlayer.advanceFrame();
                    int inputMask = demoPlayer.getInputMask();
                    boolean up = (inputMask & AbstractPlayableSprite.INPUT_UP) != 0;
                    boolean down = (inputMask & AbstractPlayableSprite.INPUT_DOWN) != 0;
                    boolean left = (inputMask & AbstractPlayableSprite.INPUT_LEFT) != 0;
                    boolean right = (inputMask & AbstractPlayableSprite.INPUT_RIGHT) != 0;
                    boolean jump = (inputMask & AbstractPlayableSprite.INPUT_JUMP) != 0;
                    fixture.sprite().setForcedJumpPress(jump);
                    fixture.stepFrame(up, down, left, right, jump);
                }

                if ((i < 265 || i > 283) && (i < 360 || i > 380)) {
                    continue;
                }

                Sonic1JunctionObjectInstance junction = findJunction();
                AbstractPlayableSprite player = fixture.sprite();
                System.out.printf(
                        "frame=%03d base=%s run=%s nextDiff=%s input=%04X px=%04X py=%04X xsp=%04X ysp=%04X gsp=%04X objCtrl=%s lock=%s jn=%s%n",
                        i,
                        basePhase,
                        promotedPhase,
                        next != null && !next.stateEquals(expected),
                        expected.input() & 0xFFFF,
                        player.getCentreX() & 0xFFFF,
                        player.getCentreY() & 0xFFFF,
                        player.getXSpeed() & 0xFFFF,
                        player.getYSpeed() & 0xFFFF,
                        player.getGSpeed() & 0xFFFF,
                        player.isObjectControlled(),
                        player.isControlLocked(),
                        junction == null ? "null" : String.format(
                                "routine=%s frame=%d timer=%d dir=%d grab=%d",
                                getPrivateObject(junction, "routine"),
                                getPrivateInt(junction, "mappingFrame"),
                                getPrivateInt(junction, "frameTimer"),
                                getPrivateInt(junction, "frameDirection"),
                                getPrivateInt(junction, "grabFrame")));
            }
        } finally {
            sharedLevel.dispose();
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMainCharacter != null ? oldMainCharacter : "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    oldSidekickCharacter != null ? oldSidekickCharacter : "tails");
        }
    }

    private void initialiseDemoPlayerState(AbstractPlayableSprite player) {
        player.setRingCount(0);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setControlLocked(false);
        player.setForcedInputMask(0);
    }

    private void resetStreamingWindows(HeadlessTestFixture fixture) {
        int cameraX = fixture.camera().getX();
        if (GameServices.level().getObjectManager() != null) {
            GameServices.level().getObjectManager().reset(cameraX);
        }
        if (GameServices.level().getRingManager() != null) {
            GameServices.level().getRingManager().reset(cameraX);
        }
    }

    private void applyFrameZeroPlayerSnapshot(TraceData trace, AbstractPlayableSprite player) {
        TraceEvent.StateSnapshot snapshot = trace.getEventsForFrame(0).stream()
                .filter(TraceEvent.StateSnapshot.class::isInstance)
                .map(TraceEvent.StateSnapshot.class::cast)
                .findFirst()
                .orElse(null);
        if (snapshot == null) {
            return;
        }

        Map<String, Object> fields = snapshot.fields();
        int statusByte = parseInt(fields.get("status_byte"), 0);
        boolean controlLocked = parseBoolean(fields.get("control_locked"), false);
        boolean onObject = parseBoolean(fields.get("on_object"), (statusByte & 0x08) != 0);
        boolean pushing = parseBoolean(fields.get("pushing"), (statusByte & 0x20) != 0);
        boolean underwater = parseBoolean(fields.get("underwater"), (statusByte & 0x40) != 0);
        boolean rollingJump = parseBoolean(fields.get("roll_jumping"), false);

        player.setControlLocked(controlLocked);
        player.setObjectControlled(controlLocked);
        player.setAnimationId(parseInt(fields.get("anim_id"), player.getAnimationId()));
        player.setDirection((statusByte & 0x01) != 0
                ? com.openggf.physics.Direction.LEFT
                : com.openggf.physics.Direction.RIGHT);
        player.setAir((statusByte & 0x02) != 0);
        player.setRolling((statusByte & 0x04) != 0);
        player.setOnObject(onObject);
        player.setPushing(pushing);
        player.setRollingJump(rollingJump);
        player.setInWater(underwater);

        int xRadius = parseInt(fields.get("x_radius"), -1);
        int yRadius = parseInt(fields.get("y_radius"), -1);
        if (xRadius > 0 && yRadius > 0) {
            player.applyCustomRadii(xRadius, yRadius);
        }
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.decode(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean parseBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                return Boolean.parseBoolean(text);
            }
            try {
                return Integer.decode(text) != 0;
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Sonic1JunctionObjectInstance findJunction() {
        for (ObjectInstance instance : GameServices.level().getObjectManager().getActiveObjects()) {
            if (instance instanceof Sonic1JunctionObjectInstance junction && junction.getX() == 0x1490) {
                return junction;
            }
        }
        return null;
    }

    private static int getPrivateInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(target);
        return value instanceof Number number ? number.intValue() : -1;
    }

    private static Object getPrivateObject(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
