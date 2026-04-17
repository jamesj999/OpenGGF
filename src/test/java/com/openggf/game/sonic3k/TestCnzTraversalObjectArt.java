package com.openggf.game.sonic3k;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(25, balloon.getFrameCount());
        assertNotNull(provider.getSheet("cnz_cannon"));
        assertNotNull(risingPlatform);
        assertEquals(3, risingPlatform.getFrameCount());
        ObjectSpriteSheet trapDoor = provider.getSheet("cnz_trap_door");
        ObjectSpriteSheet hoverFan = provider.getSheet("cnz_hover_fan");
        assertNotNull(trapDoor);
        assertEquals(3, trapDoor.getFrameCount());
        assertNotNull(hoverFan);
        assertEquals(8, hoverFan.getFrameCount());
        assertNotNull(provider.getSheet("cnz_cylinder"));
    }

    private static Sonic3kObjectArtProvider currentCnzObjectArtProvider() {
        return (Sonic3kObjectArtProvider) GameModuleRegistry.getCurrent().getObjectArtProvider();
    }
}
