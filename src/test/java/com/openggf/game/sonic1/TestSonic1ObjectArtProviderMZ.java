package com.openggf.game.sonic1;
import org.junit.jupiter.api.Test;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@RequiresRom(SonicGame.SONIC_1)
public class TestSonic1ObjectArtProviderMZ {
    @Test
    public void loadsChainedStomperSheetInMarbleZone() throws IOException {
        Sonic1ObjectArtProvider provider = new Sonic1ObjectArtProvider();
        provider.loadArtForZone(Sonic1Constants.ZONE_MZ);

        ObjectSpriteSheet sheet = provider.getSheet(ObjectArtKeys.MZ_CHAINED_STOMPER);
        assertNotNull(sheet, "MZ chained stomper sheet should be loaded in Marble Zone");
        assertEquals(11, sheet.getFrameCount(), "Expected 11 mapping frames from Map_CStom_internal");
    }
}


