package com.openggf.game.sonic3k;

import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteWrite;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Palette;

public final class S3kPaletteWriteSupport {
    private S3kPaletteWriteSupport() {
    }

    public static void applyLine(PaletteOwnershipRegistry registry,
                                 Level level,
                                 GraphicsManager graphics,
                                 String ownerId,
                                 int priority,
                                 int paletteIndex,
                                 byte[] lineData) {
        if (lineData == null) {
            return;
        }
        if (registry != null) {
            registry.submit(PaletteWrite.normal(ownerId, priority, paletteIndex, 0, lineData.clone()));
            return;
        }
        Palette palette = paletteOrNull(level, paletteIndex);
        if (palette == null) {
            return;
        }
        for (int i = 0; i < lineData.length / 2; i++) {
            palette.getColor(i).fromSegaFormat(lineData, i * 2);
        }
        cachePaletteTextureIfReady(graphics, palette, paletteIndex);
    }

    public static void applyContiguousPatch(PaletteOwnershipRegistry registry,
                                            Level level,
                                            GraphicsManager graphics,
                                            String ownerId,
                                            int priority,
                                            int paletteIndex,
                                            int startColor,
                                            byte[] segaData) {
        if (segaData == null) {
            return;
        }
        if (registry != null) {
            registry.submit(PaletteWrite.normal(ownerId, priority, paletteIndex, startColor, segaData.clone()));
            return;
        }
        Palette palette = paletteOrNull(level, paletteIndex);
        if (palette == null) {
            return;
        }
        for (int i = 0; i < segaData.length / 2; i++) {
            palette.getColor(startColor + i).fromSegaFormat(segaData, i * 2);
        }
        cachePaletteTextureIfReady(graphics, palette, paletteIndex);
    }

    public static void applyColors(PaletteOwnershipRegistry registry,
                                   Level level,
                                   GraphicsManager graphics,
                                   String ownerId,
                                   int priority,
                                   int paletteIndex,
                                   int[] colorIndices,
                                   int[] segaWords) {
        if (colorIndices == null || segaWords == null || colorIndices.length != segaWords.length) {
            return;
        }
        if (registry != null) {
            for (int i = 0; i < colorIndices.length; i++) {
                registry.submit(PaletteWrite.normal(
                        ownerId,
                        priority,
                        paletteIndex,
                        colorIndices[i],
                        segaWordBytes(segaWords[i])));
            }
            return;
        }
        Palette palette = paletteOrNull(level, paletteIndex);
        if (palette == null) {
            return;
        }
        for (int i = 0; i < colorIndices.length; i++) {
            palette.getColor(colorIndices[i]).fromSegaFormat(segaWordBytes(segaWords[i]), 0);
        }
        cachePaletteTextureIfReady(graphics, palette, paletteIndex);
    }

    private static Palette paletteOrNull(Level level, int paletteIndex) {
        if (level == null || paletteIndex < 0 || paletteIndex >= level.getPaletteCount()) {
            return null;
        }
        return level.getPalette(paletteIndex);
    }

    private static void cachePaletteTextureIfReady(GraphicsManager graphics, Palette palette, int paletteIndex) {
        if (graphics != null && graphics.isGlInitialized()) {
            graphics.cachePaletteTexture(palette, paletteIndex);
        }
    }

    private static byte[] segaWordBytes(int segaWord) {
        return new byte[] {
                (byte) ((segaWord >>> 8) & 0xFF),
                (byte) (segaWord & 0xFF)
        };
    }
}
