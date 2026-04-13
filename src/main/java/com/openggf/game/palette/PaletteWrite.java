package com.openggf.game.palette;

import java.util.Arrays;
import java.util.Objects;

public final class PaletteWrite {
    private final PaletteSurface surface;
    private final String ownerId;
    private final int priority;
    private final int lineIndex;
    private final int startColor;
    private final byte[] segaData;
    private final boolean mirrorToUnderwater;

    private PaletteWrite(PaletteSurface surface, String ownerId, int priority,
                         int lineIndex, int startColor, byte[] segaData,
                         boolean mirrorToUnderwater) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(segaData, "segaData");
        if (lineIndex < 0 || lineIndex >= 4) {
            throw new IllegalArgumentException("lineIndex must be 0-3, was: " + lineIndex);
        }
        if (startColor < 0) {
            throw new IllegalArgumentException("startColor must be >= 0, was: " + startColor);
        }
        if (segaData.length % 2 != 0) {
            throw new IllegalArgumentException("segaData length must be even (2 bytes per color), was: " + segaData.length);
        }
        if (startColor + segaData.length / 2 > 16) {
            throw new IllegalArgumentException("write overflows palette line: startColor=" + startColor + ", colors=" + segaData.length / 2);
        }
        this.priority = priority;
        this.lineIndex = lineIndex;
        this.startColor = startColor;
        this.segaData = Arrays.copyOf(segaData, segaData.length);
        this.mirrorToUnderwater = mirrorToUnderwater;
    }

    public static PaletteWrite normal(String ownerId, int priority,
                                      int lineIndex, int startColor, byte[] segaData) {
        return new PaletteWrite(PaletteSurface.NORMAL, ownerId, priority, lineIndex, startColor, segaData, false);
    }

    public PaletteWrite mirrorToUnderwater() {
        return new PaletteWrite(surface, ownerId, priority, lineIndex, startColor, segaData, true);
    }

    public PaletteSurface surface() { return surface; }
    public String ownerId() { return ownerId; }
    public int priority() { return priority; }
    public int lineIndex() { return lineIndex; }
    public int startColor() { return startColor; }
    public byte[] segaData() { return Arrays.copyOf(segaData, segaData.length); }
    public int colorCount() { return segaData.length / 2; }
    public boolean mirrorToUnderwaterEnabled() { return mirrorToUnderwater; }
}
