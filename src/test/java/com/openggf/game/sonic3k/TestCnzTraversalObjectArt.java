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
import static org.junit.jupiter.api.Assertions.assertNull;
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
        ObjectSpriteSheet cannon = provider.getSheet("cnz_cannon");
        assertNotNull(cannon);
        assertEquals(10, cannon.getFrameCount());
        assertTrue(cannon.getPatterns().length >= 100,
                "CNZ cannon should expose the dedicated Cannon.bin art, not a placeholder sheet");
        assertNotNull(risingPlatform);
        assertEquals(3, risingPlatform.getFrameCount());
        ObjectSpriteSheet trapDoor = provider.getSheet("cnz_trap_door");
        ObjectSpriteSheet hoverFan = provider.getSheet("cnz_hover_fan");
        assertNotNull(trapDoor);
        assertEquals(3, trapDoor.getFrameCount());
        assertNotNull(hoverFan);
        assertEquals(8, hoverFan.getFrameCount());
        ObjectSpriteSheet cylinder = provider.getSheet("cnz_cylinder");
        assertNotNull(cylinder);
        assertEquals(4, cylinder.getFrameCount());
        assertTrue(cylinder.getPatterns().length > 0,
                "CNZ cylinder should load the ROM-parsed Map - Cylinder.asm sheet");
        ObjectSpriteSheet bumper = provider.getSheet("cnz_bumper");
        assertNotNull(bumper);
        assertEquals(2, bumper.getFrameCount());
        assertNull(provider.getSheet("cnz_vacuum_tube"),
                "Vacuum Tube stays controller-only because Obj_CNZVacuumTube has inline S&K-side logic and no mappings/make_art_tile ownership");
        assertNull(provider.getSheet("cnz_spiral_tube"),
                "Spiral Tube stays controller-only because its S&K-side off_33320 controller routes still have no mappings/make_art_tile ownership");
    }

    private static Sonic3kObjectArtProvider currentCnzObjectArtProvider() {
        return (Sonic3kObjectArtProvider) GameModuleRegistry.getCurrent().getObjectArtProvider();
    }
}
