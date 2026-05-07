package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kIcz1SnowboardIntroHeadless {
    private static final int ZONE_ICZ = 0x05;
    private static final int ACT_1 = 0;

    private Object oldSkipIntros;
    private Object oldMainCharacter;
    private Object oldSidekickCharacters;

    @BeforeEach
    public void setUpConfig() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        oldSidekickCharacters = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
    }

    @AfterEach
    public void restoreConfig() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                oldSidekickCharacters != null ? oldSidekickCharacters : "");
    }

    @Test
    public void sonicAloneRunsSnowboardIntroAndReleasesControlAfterCrash() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(ZONE_ICZ, ACT_1)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();

        assertTrue(hasSnowboardIntroObject(), "ICZ1 should spawn the Sonic snowboard intro object");
        assertTrue(sonic.isControlLocked(), "Intro should lock Sonic input");
        assertTrue(sonic.isObjectControlled(), "Intro should hold Sonic under object control initially");
        assertTrue(sonic.getAir(), "Intro should start Sonic airborne");
        assertTrue(sonic.getRolling(), "Intro should force Sonic into rolling/snowboard posture");
        renderSnowboardIntroObjects();

        int startX = sonic.getCentreX();
        for (int frame = 0; frame < 45; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        assertTrue(sonic.getCentreX() > startX + 0x10,
                "Sonic should start rolling right after the ROM startup lock expires");
        assertFalse(sonic.isObjectControlled(),
                "Initial object-control hold should be released after the ROM startup lock");

        boolean sawSlopeRegion = false;
        boolean sawReleaseAfterCrash = false;

        for (int frame = 0; frame < 1800; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if (sonic.getCentreX() >= 0x184 && hasSnowboardIntroObject()) {
                sawSlopeRegion = true;
                renderSnowboardIntroObjects();
            }
            if (sonic.getCentreX() >= 0x3880
                    && !sonic.isControlLocked()
                    && !sonic.isObjectControlled()
                    && !hasSnowboardIntroObject()) {
                sawReleaseAfterCrash = true;
                break;
            }
        }

        assertTrue(sonic.getCentreX() > startX + 0x1000,
                "Snowboard intro should carry Sonic far down ICZ1");
        assertTrue(sawSlopeRegion, "Sonic should reach the snowboard slope handoff region");
        assertTrue(sawReleaseAfterCrash, "Sonic should crash off the snowboard and regain control");
        assertFalse(sonic.isControlLocked(), "Sonic input should be unlocked after the intro");
        assertFalse(sonic.isObjectControlled(), "Normal player physics should resume after the intro");
    }

    @Test
    public void introRecoversIfSonicLandsBeforeReachingSnowboard() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(ZONE_ICZ, ACT_1)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();

        for (int frame = 0; frame < 35; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        sonic.setAir(false);
        sonic.setXSpeed((short) 0);
        sonic.setYSpeed((short) 0);
        sonic.setGSpeed((short) 0);

        for (int frame = 0; frame < 90; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }

        assertTrue(sonic.getCentreX() >= 0x00C0,
                "ICZ intro should keep Sonic moving into the snowboard even if he lands early");
    }

    private boolean hasSnowboardIntroObject() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .map(ObjectInstance::getClass)
                .map(Class::getSimpleName)
                .anyMatch("IczSnowboardIntroInstance"::equals);
    }

    private void renderSnowboardIntroObjects() {
        List<GLCommand> commands = new ArrayList<>();
        for (ObjectInstance object : GameServices.level().getObjectManager().getActiveObjects()) {
            if (object.getClass().getSimpleName().contains("Snowboard")) {
                object.appendRenderCommands(commands);
            }
        }
    }
}
