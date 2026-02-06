package uk.co.jamesj999.sonic.tests;

import org.junit.Test;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArt;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * ROM-backed regression tests for HTZ boss art palette selection.
 */
public class TestHTZBossArtPalette {

    @Test
    public void htzBossAndSmokeSheetsUsePaletteZero() throws Exception {
        File romFile = RomTestUtils.ensureRomAvailable();
        Rom rom = new Rom();
        rom.open(romFile.getAbsolutePath());
        try {
            Sonic2ObjectArt art = new Sonic2ObjectArt(rom, RomByteReader.fromRom(rom));

            ObjectSpriteSheet bossSheet = art.loadHTZBossSheet();
            assertNotNull("HTZ boss sheet should load from ROM", bossSheet);
            assertEquals("HTZ boss sheet palette should be 0", 0, bossSheet.getPaletteIndex());

            ObjectSpriteSheet smokeSheet = art.loadHTZBossSmokeSheet();
            assertNotNull("HTZ boss smoke sheet should load from ROM", smokeSheet);
            assertEquals("HTZ boss smoke palette should be 0", 0, smokeSheet.getPaletteIndex());
        } finally {
            rom.close();
        }
    }
}
