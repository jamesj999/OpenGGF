package uk.co.jamesj999.sonic.game.sonic1;

import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RequiresRom(SonicGame.SONIC_1)
public class TestSonic1ObjectArtProviderLZPole {
    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    @Test
    public void loadsBreakablePoleSheetInLabyrinthZone() throws IOException {
        Sonic1ObjectArtProvider provider = new Sonic1ObjectArtProvider();
        provider.loadArtForZone(Sonic1Constants.ZONE_LZ);

        ObjectSpriteSheet sheet = provider.getSheet(ObjectArtKeys.LZ_BREAKABLE_POLE);
        assertNotNull("LZ breakable pole sheet should be loaded in Labyrinth Zone", sheet);
        assertEquals("Expected 2 mapping frames from Map_Pole_internal", 2, sheet.getFrameCount());
    }
}
