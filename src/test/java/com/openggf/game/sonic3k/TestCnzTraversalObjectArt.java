package com.openggf.game.sonic3k;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestCnzTraversalObjectArt {

    @Test
    public void carnivalNightTraversalSheetsRegisterForVisibleObjects() {
        HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        Sonic3kObjectArtProvider provider = currentCnzObjectArtProvider();

        ObjectSpriteSheet balloon = provider.getSheet("cnz_balloon");
        ObjectSpriteSheet risingPlatform = provider.getSheet("cnz_rising_platform");

        assertNotNull(balloon);
        assertTrue(balloon.getFrameCount() > 0);
        assertNotNull(provider.getSheet("cnz_cannon"));
        assertNotNull(risingPlatform);
        assertTrue(risingPlatform.getFrameCount() > 0);
        assertNotNull(provider.getSheet("cnz_trap_door"));
        assertNotNull(provider.getSheet("cnz_hover_fan"));
        assertNotNull(provider.getSheet("cnz_cylinder"));
    }

    private static Sonic3kObjectArtProvider currentCnzObjectArtProvider() {
        return (Sonic3kObjectArtProvider) GameModuleRegistry.getCurrent().getObjectArtProvider();
    }
}
