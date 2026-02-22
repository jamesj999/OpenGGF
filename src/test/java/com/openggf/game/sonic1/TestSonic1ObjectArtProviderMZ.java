package com.openggf.game.sonic1;

import org.junit.Rule;
import org.junit.Test;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RequiresRom(SonicGame.SONIC_1)
public class TestSonic1ObjectArtProviderMZ {
    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    @Test
    public void loadsChainedStomperSheetInMarbleZone() throws IOException {
        Sonic1ObjectArtProvider provider = new Sonic1ObjectArtProvider();
        provider.loadArtForZone(Sonic1Constants.ZONE_MZ);

        ObjectSpriteSheet sheet = provider.getSheet(ObjectArtKeys.MZ_CHAINED_STOMPER);
        assertNotNull("MZ chained stomper sheet should be loaded in Marble Zone", sheet);
        assertEquals("Expected 11 mapping frames from Map_CStom_internal", 11, sheet.getFrameCount());
    }
}
