package com.openggf.game.sonic3k;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that HCZ AniPLC scripts are loaded and mutate the level pattern buffer.
 *
 * <p>Hydrocity uses animated background tiles in both acts:
 * HCZ1 script 0 writes to tile $30C and HCZ2 script 0 writes to tile $25E.
 * Without the HCZ AniPLC hookup, those destination tiles remain static.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kHczPatternAnimation {
    private HeadlessTestFixture fixture;

    @BeforeAll
    public static void configure() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
    }

    @BeforeEach
    public void setUp() throws Exception {
        fixture = null;
    }

    @Test
    public void hcz1AnimatedTilesAdvanceAtBackgroundDestination() {
        fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(1, 0)
                .build();
        assertAnimatedTileChanges(0x30C, 12);
    }

    @Test
    public void hcz2AnimatedTilesAdvanceAtBackgroundDestination() {
        fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(1, 1)
                .build();
        assertAnimatedTileChanges(0x25E, 20);
    }

    private void assertAnimatedTileChanges(int tileIndex, int maxFrames) {
        Level level = GameServices.level().getCurrentLevel();
        assertNotNull(level, "Level must be loaded");

        Pattern pattern = level.getPattern(tileIndex);
        assertNotNull(pattern, "Pattern tile must exist at $" + Integer.toHexString(tileIndex));

        byte[] initial = snapshot(pattern);
        AnimatedPatternManager apm = GameServices.level().getAnimatedPatternManager();
        assertNotNull(apm, "AnimatedPatternManager must be present");

        boolean changed = false;
        for (int frame = 0; frame < maxFrames; frame++) {
            fixture.stepIdleFrames(1);
            apm.update();
            if (!Arrays.equals(initial, snapshot(level.getPattern(tileIndex)))) {
                changed = true;
                break;
            }
        }

        assertFalse(!changed, "Expected animated HCZ tile $" + Integer.toHexString(tileIndex)
                + " to remain static over " + maxFrames + " frames");
    }

    private static byte[] snapshot(Pattern pattern) {
        byte[] data = new byte[Pattern.PATTERN_SIZE_IN_MEM];
        int index = 0;
        for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
            for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                data[index++] = pattern.getPixel(x, y);
            }
        }
        return data;
    }
}


