package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kLifeIconAddresses {

    private static final Path TAILS_LIFE_ICON_BIN =
            Path.of("docs", "skdisasm", "General", "Sprites", "HUD Icon", "Tails Life Icon.bin");
    private static final int TAILS_ICON_BYTE_COUNT = 32;

    @Test
    void tailsLifeIconAddressMatchesBundledDisassemblyData() throws IOException {
        byte[] expected = Arrays.copyOf(Files.readAllBytes(TAILS_LIFE_ICON_BIN), TAILS_ICON_BYTE_COUNT);

        byte[] actual;
        try (Rom rom = new Rom()) {
            assertTrue(rom.open("Sonic and Knuckles & Sonic 3 (W) [!].gen"));
            actual = rom.readBytes(Sonic3kConstants.ART_NEM_TAILS_LIFE_ICON_ADDR, TAILS_ICON_BYTE_COUNT);
        }

        assertArrayEquals(expected, actual,
                "ART_NEM_TAILS_LIFE_ICON_ADDR should point at ArtNem_TailsLifeIcon in the combined S3&K ROM");
    }
}
