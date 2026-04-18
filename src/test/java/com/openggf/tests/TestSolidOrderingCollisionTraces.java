package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.physics.CollisionEvent;
import com.openggf.physics.RecordingCollisionTrace;
import com.openggf.graphics.GLCommand;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_2)
class TestSolidOrderingCollisionTraces {
    private static SharedLevel sharedLevel;

    @BeforeAll
    static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_2, Sonic2ZoneConstants.ZONE_EHZ, 0);
    }

    @AfterAll
    static void cleanup() {
        if (sharedLevel != null) {
            sharedLevel.dispose();
        }
    }

    private HeadlessTestFixture fixture;
    private RecordingCollisionTrace trace;
    private AbstractPlayableSprite player;
    private AbstractPlayableSprite sidekick;

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
        trace = new RecordingCollisionTrace();
        GameServices.collision().setTrace(trace);
        player = fixture.sprite();
        sidekick = createSidekick();
    }

    @Test
    void sameFrameManualCheckpointEmitsOrderedCheckpointTraceEvents() {
        TraceProbeObject left = new TraceProbeObject(0x120, 0x100);
        TraceProbeObject right = new TraceProbeObject(0x140, 0x100);
        GameServices.level().getObjectManager().addDynamicObject(left);
        GameServices.level().getObjectManager().addDynamicObject(right);

        player.setCentreX((short) 0x120);
        player.setCentreY((short) 0x0F3);
        player.setAir(true);
        player.setYSpeed((short) 0x100);
        sidekick.setCentreX((short) 0x120);
        sidekick.setCentreY((short) 0x0F3);
        sidekick.setAir(true);
        sidekick.setYSpeed((short) 0x100);

        fixture.stepFrame(false, false, false, false, false);

        List<String> checkpointEvents = trace.getEvents().stream()
                .map(CollisionEvent::type)
                .map(Enum::name)
                .filter(name -> name.startsWith("SOLID_CHECKPOINT"))
                .collect(Collectors.toList());

        assertFalse(checkpointEvents.isEmpty(), "The trace should record explicit solid checkpoint events");
        assertEquals(List.of(
                "SOLID_CHECKPOINT_START",
                "SOLID_CHECKPOINT_RESULT",
                "SOLID_CHECKPOINT_RESULT",
                "SOLID_CHECKPOINT_START",
                "SOLID_CHECKPOINT_RESULT",
                "SOLID_CHECKPOINT_RESULT"),
                checkpointEvents,
                "Checkpoint events should stay ordered by object execution");
    }

    private AbstractPlayableSprite createSidekick() {
        Tails tails = new Tails("tails", (short) 0x130, (short) 0x0F3);
        tails.setCpuControlled(true);
        tails.setCpuController(new SidekickCpuController(tails, player));
        GameServices.sprites().addSprite(tails, "tails");
        return tails;
    }

    private static final class TraceProbeObject extends AbstractObjectInstance implements SolidObjectProvider {
        private final SolidObjectParams params = new SolidObjectParams(16, 8, 8);

        private TraceProbeObject(int x, int y) {
            super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "TraceProbe");
        }

        @Override
        public SolidExecutionMode solidExecutionMode() {
            return SolidExecutionMode.MANUAL_CHECKPOINT;
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return params;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            services().solidExecution().resolveSolidNowAll();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }

        @Override
        public boolean isHighPriority() {
            return false;
        }

        @Override
        public boolean isDestroyed() {
            return false;
        }
    }
}
