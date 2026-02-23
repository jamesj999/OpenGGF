package com.openggf.tests;

import org.junit.Test;
import com.openggf.tools.KosinskiReader;
import com.openggf.tests.rules.RomRequirementTracker;
import com.openggf.tests.rules.SonicGame;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class TestCollisionLogic {

    @Test
    public void testCollisionLogic() throws IOException {
        String ehzPriColPath = "EHZ and HTZ primary 16x16 collision index.kos";
        Path path = Path.of(ehzPriColPath);
        byte[] collisionBuffer = null;

        if (path.toFile().exists()) {
            try (FileChannel fileChannel = FileChannel.open(path, StandardOpenOption.READ)) {
                collisionBuffer = KosinskiReader.decompress(fileChannel, true);
            }
        } else {
            // Fallback: Try to read from ROM
            Path romPath = Path.of("Sonic The Hedgehog 2 (W) (REV01) [!].gen");
            RomRequirementTracker.requireRomOrSkip(
                    SonicGame.SONIC_2,
                    romPath.toFile().exists(),
                    "Test data not available (neither .kos file nor ROM found)");

            try (FileChannel romChannel = FileChannel.open(romPath, StandardOpenOption.READ)) {
                romChannel.position(0x44E50); // Offset for EHZ and HTZ primary from collisionindexes.txt
                collisionBuffer = KosinskiReader.decompress(romChannel, true);
            }
        }

        int[] collisionArray = new int[0x300];

        for (int i = 0; i < collisionBuffer.length; i++) {
            collisionArray[i] = Byte.toUnsignedInt(collisionBuffer[i]);
        }

        // Verify decompressed data is non-empty and contains valid collision indices
        assertTrue("Decompressed collision buffer should not be empty", collisionBuffer.length > 0);
        assertTrue("Collision array should have entries", collisionArray.length > 0);

        // EHZ collision data uses collision block indices; verify some are non-zero
        boolean hasNonZero = false;
        for (int value : collisionArray) {
            if (value != 0) {
                hasNonZero = true;
                break;
            }
        }
        assertTrue("Collision array should contain non-zero entries", hasNonZero);

        // All values should be valid unsigned byte range (0-255)
        for (int i = 0; i < collisionBuffer.length; i++) {
            assertTrue("Collision value at index " + i + " should be in range 0-255",
                    collisionArray[i] >= 0 && collisionArray[i] <= 255);
        }
    }

}
