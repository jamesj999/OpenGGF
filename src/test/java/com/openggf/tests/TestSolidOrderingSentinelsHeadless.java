package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_2)
class TestSolidOrderingSentinelsHeadless {
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
    private AbstractPlayableSprite player;
    private AbstractPlayableSprite sidekick;

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
        player = fixture.sprite();
        sidekick = createSidekick();
    }

    @Test
    void sameFrameStandingUsesTheLatestCheckpointAfterTheProbeMovesAway() {
        SnapshotProbeObject probe = new SnapshotProbeObject(SnapshotProbeObject.Scenario.SAME_FRAME_STANDING, 0x120, 0x100);
        GameServices.level().getObjectManager().addDynamicObject(probe);

        player.setCentreX((short) 0x120);
        player.setCentreY((short) 0x0EE);
        player.setAir(true);
        player.setYSpeed((short) 0x100);

        fixture.stepFrame(false, false, false, false, false);

        assertTrue(probe.firstStandingNow(), "The checkpoint should see the player standing on the platform");
        assertTrue(probe.helperStandingAfterMove(), "Helper queries should read the same checkpoint snapshot");
        assertFalse(probe.firstStandingLastFrame(), "First contact should not report a previous standing frame");

        fixture.stepIdleFrames(2);
        assertTrue(probe.checkpointCallCount() >= 3,
                "Checkpoint handling should still run after bookkeeping refactors");
    }

    @Test
    void noContactClearStaysClearAfterTheProbeMovesBackUnderThePlayer() {
        SnapshotProbeObject probe = new SnapshotProbeObject(SnapshotProbeObject.Scenario.NO_CONTACT_CLEAR, 0x120, 0x100);
        GameServices.level().getObjectManager().addDynamicObject(probe);

        player.setCentreX((short) 0x120);
        player.setCentreY((short) 0x0EE);
        player.setAir(true);
        player.setYSpeed((short) 0x100);

        fixture.stepFrame(false, false, false, false, false);

        player.setCentreX((short) 0x180);
        fixture.stepFrame(false, false, false, false, false);

        assertFalse(probe.helperStandingAfterMoveBack(), "Helper queries should read the latest cleared checkpoint snapshot");
        assertFalse(probe.secondStandingNow(), "The checkpoint should clear when the player leaves the platform");
        assertTrue(probe.secondStandingLastFrame(), "The no-contact result should remember the prior standing frame");
    }

    @Test
    void multiPlayerBatchKeepsBothPlayersOnTheSameCheckpointSnapshot() {
        SnapshotProbeObject probe = new SnapshotProbeObject(SnapshotProbeObject.Scenario.MULTI_PLAYER_BATCH, 0x120, 0x100);
        GameServices.level().getObjectManager().addDynamicObject(probe);

        player.setCentreX((short) 0x120);
        player.setCentreY((short) 0x0EE);
        player.setAir(true);
        player.setYSpeed((short) 0x100);

        sidekick.setCentreX((short) 0x130);
        sidekick.setCentreY((short) (0x100 - 8 - sidekick.getYRadius()));
        sidekick.setAir(true);
        sidekick.setYSpeed((short) 0x100);

        fixture.stepFrame(false, false, false, false, false);

        assertEquals(2, probe.batchPlayerCount(), "The checkpoint batch should include the main player and sidekick");
        assertTrue(probe.batchContainsMain(), "The checkpoint batch should contain the main player");
        assertTrue(probe.batchContainsSidekick(), "The checkpoint batch should contain the sidekick");
        assertTrue(probe.mainStandingAfterMove(), "Main player should remain standing from the published batch");
        assertEquals(probe.sidekickStandingFromBatch(), probe.sidekickStandingAfterMove(),
                "Sidekick helper queries should read the published batch snapshot");
    }

    private AbstractPlayableSprite createSidekick() {
        Tails tails = new Tails("tails", (short) 0x120, (short) 0x0EE);
        tails.setCpuControlled(true);
        tails.setCentreY((short) (0x100 - 8 - tails.getYRadius()));
        GameServices.sprites().addSprite(tails, "tails");
        tails.setCpuControlled(false);
        return tails;
    }

    private static final class SnapshotProbeObject extends AbstractObjectInstance implements SolidObjectProvider {
        enum Scenario {
            SAME_FRAME_STANDING,
            NO_CONTACT_CLEAR,
            MULTI_PLAYER_BATCH
        }

        private final Scenario scenario;
        private final SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        private boolean firstStandingNow;
        private boolean firstStandingLastFrame;
        private boolean secondStandingNow;
        private boolean secondStandingLastFrame;
        private boolean helperStandingAfterMove;
        private boolean helperStandingAfterMoveBack;
        private int batchPlayerCount;
        private boolean batchContainsMain;
        private boolean batchContainsSidekick;
        private boolean sidekickStandingFromBatch;
        private boolean mainStandingAfterMove;
        private boolean sidekickStandingAfterMove;
        private boolean seededNoContactClear;
        private int checkpointCallCount;

        private SnapshotProbeObject(Scenario scenario, int x, int y) {
            super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "SnapshotProbe");
            this.scenario = scenario;
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
            switch (scenario) {
                case SAME_FRAME_STANDING -> handleSameFrameStanding(player);
                case NO_CONTACT_CLEAR -> handleNoContactClear(player);
                case MULTI_PLAYER_BATCH -> handleMultiPlayerBatch(player);
            }
        }

        private void handleSameFrameStanding(PlayableEntity player) {
            SolidCheckpointBatch batch = services().solidExecution().resolveSolidNowAll();
            checkpointCallCount++;
            PlayerSolidContactResult result = batch.perPlayer().get(player);
            firstStandingNow = result != null && result.standingNow();
            firstStandingLastFrame = result != null && result.standingLastFrame();

            updateDynamicSpawn(getX() + 0x80, getY());
            helperStandingAfterMove = GameServices.collision().hasStandingContact((AbstractPlayableSprite) player);
        }

        private void handleNoContactClear(PlayableEntity player) {
            SolidCheckpointBatch batch = services().solidExecution().resolveSolidNowAll();
            checkpointCallCount++;
            PlayerSolidContactResult result = batch.perPlayer().get(player);
            secondStandingNow = result != null && result.standingNow();
            secondStandingLastFrame = result != null && result.standingLastFrame();

            if (!seededNoContactClear) {
                seededNoContactClear = true;
                return;
            }

            helperStandingAfterMoveBack = GameServices.collision().hasStandingContact((AbstractPlayableSprite) player);
        }

        private void handleMultiPlayerBatch(PlayableEntity player) {
            SolidCheckpointBatch batch = services().solidExecution().resolveSolidNowAll();
            checkpointCallCount++;
            batchPlayerCount = batch.perPlayer().size();
            batchContainsMain = batch.perPlayer().containsKey(player);

            AbstractPlayableSprite batchSidekick = GameServices.sprites().getSidekicks().getFirst();
            batchContainsSidekick = batch.perPlayer().containsKey(batchSidekick);
            PlayerSolidContactResult sidekickResult = batch.perPlayer().get(batchSidekick);
            sidekickStandingFromBatch = sidekickResult != null && sidekickResult.standingNow();

            updateDynamicSpawn(getX() + 0x80, getY());
            mainStandingAfterMove = GameServices.collision().hasStandingContact((AbstractPlayableSprite) player);
            sidekickStandingAfterMove = GameServices.collision().hasStandingContact(batchSidekick);
        }

        boolean firstStandingNow() {
            return firstStandingNow;
        }

        boolean firstStandingLastFrame() {
            return firstStandingLastFrame;
        }

        boolean secondStandingNow() {
            return secondStandingNow;
        }

        boolean secondStandingLastFrame() {
            return secondStandingLastFrame;
        }

        boolean helperStandingAfterMove() {
            return helperStandingAfterMove;
        }

        boolean helperStandingAfterMoveBack() {
            return helperStandingAfterMoveBack;
        }

        int batchPlayerCount() {
            return batchPlayerCount;
        }

        boolean batchContainsMain() {
            return batchContainsMain;
        }

        boolean batchContainsSidekick() {
            return batchContainsSidekick;
        }

        boolean sidekickStandingFromBatch() {
            return sidekickStandingFromBatch;
        }

        boolean mainStandingAfterMove() {
            return mainStandingAfterMove;
        }

        boolean sidekickStandingAfterMove() {
            return sidekickStandingAfterMove;
        }

        int checkpointCallCount() {
            return checkpointCallCount;
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
