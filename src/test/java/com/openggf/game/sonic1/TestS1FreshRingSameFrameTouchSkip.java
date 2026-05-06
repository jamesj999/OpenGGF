package com.openggf.game.sonic1;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.credits.DemoInputPlayer;
import com.openggf.game.sonic1.credits.Sonic1CreditsDemoData;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceReplayBootstrap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for the SYZ3 credits demo trace divergence at frame 253
 * (TestS1Credits02Syz3TraceReplay): {@code rings expected=20, actual=21}.
 *
 * <p>ROM behaviour: when a ring is positioned vertically outside the camera's
 * sprite-render Y window, ROM's BuildSprites .assumeHeight branch
 * (docs/s1disasm/_inc/BuildSprites.asm:71-78, taken when obRender bit 4 is
 * clear, the default for rings) leaves obRender bit 7 cleared if
 * {@code obY - cameraY + 0x80} falls outside the {@code [0x60, 0x180)}
 * range -- i.e. the visible 224-line viewport plus a 32-pixel margin above
 * and below. ROM's ReactToItem (docs/s1disasm/_incObj/sub ReactToItem.asm:26-27)
 * tests that flag with {@code tst.b obRender(a1) / bpl.s .next} and skips the
 * ring entirely.
 *
 * <p>In SYZ3 at frame 253 the player passes a ring at (0x186E, 0x0662) while
 * the previous frame's camera was at (0x17C2, 0x0556). The ring's
 * {@code obY - cameraY = 0x10C}, plus 0x80 = 0x18C which is &gt;= 0x180 so
 * ROM's BuildSprites cleared bit 7 and ReactToItem at f253 skipped the ring.
 *
 * <p>Bug reproduced before fix: the engine's
 * {@code AbstractObjectInstance.isOnScreenForTouch()} only checked X distance
 * from the camera, NOT Y, so below-screen rings remained "touchable" in the
 * engine and got collected one frame earlier than ROM. Additionally, the
 * static {@code cameraBounds} cache was stale at touch time (not refreshed
 * since the previous frame's object update), which would have produced a
 * one-frame Y misalignment for rings sitting near the viewport edge once Y
 * was added to the gate; {@link com.openggf.level.objects.ObjectManager#snapshotTouchResponseState()}
 * now refreshes the cache before snapshotting.
 *
 * <p>Test approach: replay the SYZ3 credits demo input through frame 253 and
 * assert the player's ring count matches the ROM trace recording (20). Frames
 * before 253 are also exercised to catch unrelated regressions.
 */
@RequiresRom(SonicGame.SONIC_1)
public class TestS1FreshRingSameFrameTouchSkip {

    private static final int CREDITS_DEMO_IDX = 2; // SYZ Act 3

    /** First trace divergence frame. ROM expected ring count = 20. */
    private static final int FRAME_253 = 253;
    private static final int EXPECTED_RING_COUNT_FRAME_253 = 20;

    private static SharedLevel sharedLevel;

    private HeadlessTestFixture fixture;
    private DemoInputPlayer demoPlayer;
    private TraceData trace;

    @BeforeAll
    public static void loadLevel() throws Exception {
        int zone = Sonic1CreditsDemoData.DEMO_ZONE[CREDITS_DEMO_IDX];
        int act = Sonic1CreditsDemoData.DEMO_ACT[CREDITS_DEMO_IDX];
        sharedLevel = SharedLevel.load(SonicGame.SONIC_1, zone, act);
    }

    @AfterAll
    public static void cleanup() {
        if (sharedLevel != null) sharedLevel.dispose();
    }

    @BeforeEach
    public void setUp() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");

        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .startPosition((short) Sonic1CreditsDemoData.START_X[CREDITS_DEMO_IDX],
                        (short) Sonic1CreditsDemoData.START_Y[CREDITS_DEMO_IDX])
                .startPositionIsCentre()
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        player.setRingCount(0);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setControlLocked(false);
        player.setForcedInputMask(0);

        if (GameServices.level().getObjectManager() != null) {
            GameServices.level().getObjectManager().reset(fixture.camera().getX());
        }
        if (GameServices.level().getRingManager() != null) {
            GameServices.level().getRingManager().reset(fixture.camera().getX());
        }

        Rom rom = GameServices.rom().getRom();
        int demoAddr = Sonic1CreditsDemoData.DEMO_DATA_ADDR[CREDITS_DEMO_IDX];
        int demoSize = Sonic1CreditsDemoData.DEMO_DATA_SIZE[CREDITS_DEMO_IDX];
        byte[] demoData = rom.readBytes(demoAddr, demoSize);
        demoPlayer = new DemoInputPlayer(demoData);

        // Frame-zero priming step (matches AbstractCreditsDemoTraceReplayTest).
        GameServices.level().updateObjectPositionsWithoutTouches();

        trace = TraceData.load(Path.of("src/test/resources/traces/s1/credits_02_syz3"));
    }

    /**
     * Reproduces the SYZ3 credits demo frame 253 divergence directly: replays
     * the demo input through frame 253 and asserts the player ring count
     * matches ROM.
     *
     * <p>Without the fix, this test fails with rings = 21 (engine collected an
     * off-screen ring s43 at (0x186E, 0x0662) that the ROM correctly skipped
     * because BuildSprites had cleared obRender bit 7 for the below-camera
     * ring). With the fix, ring count matches ROM exactly.
     */
    @Test
    public void syz3OffscreenYRingNotCollectedThroughFrame253() {
        AbstractPlayableSprite player = fixture.sprite();

        for (int i = 0; i <= FRAME_253; i++) {
            TraceFrame expected = trace.getFrame(i);
            TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
            TraceExecutionPhase phase =
                    TraceReplayBootstrap.phaseForReplay(trace, previous, expected);

            if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                continue;
            }

            demoPlayer.advanceFrame();
            int input = demoPlayer.getInputMask();
            boolean up = (input & AbstractPlayableSprite.INPUT_UP) != 0;
            boolean down = (input & AbstractPlayableSprite.INPUT_DOWN) != 0;
            boolean left = (input & AbstractPlayableSprite.INPUT_LEFT) != 0;
            boolean right = (input & AbstractPlayableSprite.INPUT_RIGHT) != 0;
            boolean jump = (input & AbstractPlayableSprite.INPUT_JUMP) != 0;
            player.setForcedJumpPress(jump);

            fixture.stepFrame(up, down, left, right, jump);
        }

        int actualRings = player.getRingCount();
        assertEquals(EXPECTED_RING_COUNT_FRAME_253, actualRings,
                "Player ring count at frame " + FRAME_253
                        + " (SYZ3 credits demo first ring divergence) should match"
                        + " ROM = " + EXPECTED_RING_COUNT_FRAME_253
                        + " but got " + actualRings
                        + " (extra ring indicates an off-screen ring s43 was"
                        + " collected even though the ring's Y was below the"
                        + " camera viewport so ROM's BuildSprites would have"
                        + " cleared obRender bit 7 and ROM's ReactToItem would"
                        + " have skipped it).");
    }
}
