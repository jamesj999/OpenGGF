package com.openggf.tests;

import org.junit.Rule;
import org.junit.Test;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.Sonic2ObjectArt;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * ROM-backed regression tests for HTZ boss art palette selection.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestHTZBossArtPalette {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    @Test
    public void htzBossAndSmokeSheetsUsePaletteZero() throws Exception {
        Rom rom = romRule.rom();
        Sonic2ObjectArt art = new Sonic2ObjectArt(rom, RomByteReader.fromRom(rom));

        ObjectSpriteSheet bossSheet = art.loadHTZBossSheet();
        assertNotNull("HTZ boss sheet should load from ROM", bossSheet);
        assertEquals("HTZ boss sheet palette should be 0", 0, bossSheet.getPaletteIndex());

        ObjectSpriteSheet smokeSheet = art.loadHTZBossSmokeSheet();
        assertNotNull("HTZ boss smoke sheet should load from ROM", smokeSheet);
        assertEquals("HTZ boss smoke palette should be 0", 0, smokeSheet.getPaletteIndex());
    }
}
