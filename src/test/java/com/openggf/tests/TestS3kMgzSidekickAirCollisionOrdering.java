package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kMgzSidekickAirCollisionOrdering {

    private static Object oldSkipIntros;
    private static SharedLevel sharedLevel;

    @BeforeAll
    static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, Sonic3kZoneIds.ZONE_MGZ, 0);
    }

    @AfterAll
    static void cleanup() {
        SonicConfigurationService.getInstance().setConfigValue(
                SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        if (sharedLevel != null) {
            sharedLevel.dispose();
        }
        RuntimeManager.destroyCurrent();
    }

    @Test
    void airborneTailsChecksMgzFloorAfterCurrentFrameMovement() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
        AbstractPlayableSprite tails = GameServices.sprites().getSidekicks().getFirst();

        tails.setCentreXPreserveSubpixel((short) 0x035F);
        tails.setCentreYPreserveSubpixel((short) 0x0AAC);
        tails.setSubpixelRaw(0xF500, 0xCC00);
        tails.setXSpeed((short) 0xF026);
        tails.setYSpeed((short) 0x0470);
        tails.setGSpeed((short) 0xEFA6);
        tails.setAngle((byte) 0x00);
        tails.setAir(true);
        tails.setRolling(false);
        tails.setGroundMode(GroundMode.GROUND);

        SpriteManager.tickPlayablePhysics(tails,
                false, false, false, false, false,
                false, false, false,
                GameServices.level(), 754);

        assertEquals(0x0353, tails.getCentreX() & 0xFFFF);
        assertEquals(0x0AA4, tails.getCentreY() & 0xFFFF,
                "ROM sonic3k.asm:27553-27566 and 28871-29117 move Tails before airborne floor collision");
        assertEquals(0x30, tails.getAngle() & 0xFF);
        assertFalse(tails.getAir());
        assertEquals(GroundMode.LEFTWALL, tails.getGroundMode());
        assertEquals(0, tails.getYSpeed());
        assertEquals(0, tails.getGSpeed());
    }
}
