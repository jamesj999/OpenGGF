package com.openggf.debug;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.Sonic2ObjectArt;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.objects.ObjectArtData;
import com.openggf.level.Pattern;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DebugArtViewer {

    private static final Logger LOGGER = Logger.getLogger(DebugArtViewer.class.getName());

    public static void main(String[] args) {
        try {
            File romFile = new File("Sonic The Hedgehog 2 (W) (REV01) [!].gen");
            if (!romFile.exists()) {
                System.err.println("ROM not found");
                return;
            }
            Rom rom = new Rom();
            rom.open(romFile.getAbsolutePath());
            RomByteReader reader = RomByteReader.fromRom(rom);

            Sonic2ObjectArt artLoader = new Sonic2ObjectArt(rom, reader);
            ObjectArtData artData = artLoader.load(); // This triggers the build logic

            // Access restricted, maybe we can expose a getter or just use reflection?
            // Better to add a temporary public method to Sonic2ObjectArt or just modify it
            // to dump.
            // Actually, we can just edit Sonic2ObjectArt to dump images during load() for
            // this session.

            System.out.println("Use the modified Sonic2ObjectArt to dump images.");

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load ROM or art data", e);
        }
    }

    public static void dumpPatterns(Pattern[] patterns, String filename) {
        if (patterns == null || patterns.length == 0)
            return;

        int tilesPerRow = 32;
        int rows = (patterns.length + tilesPerRow - 1) / tilesPerRow;

        RgbaImage img = new RgbaImage(tilesPerRow * 8, rows * 8, new int[tilesPerRow * 8 * rows * 8]);

        for (int i = 0; i < patterns.length; i++) {
            Pattern p = patterns[i];
            int tileX = (i % tilesPerRow) * 8;
            int tileY = (i / tilesPerRow) * 8;

            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int colorIndex = p.getPixel(x, y);
                    int rgb = 0;
                    // Simple grayscale for debug
                    if (colorIndex > 0) {
                        int c = colorIndex * 16;
                        rgb = 0xFF000000 | (c << 16) | (c << 8) | c;
                    }
                    img.setArgb(tileX + x, tileY + y, rgb);
                }
            }
        }

        try {
            ScreenshotCapture.savePNG(img, Path.of(filename));
            System.out.println("Dumped " + filename);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to write pattern image", e);
        }
    }
}
