package com.openggf.game.palette;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PaletteOwnershipRegistry {
    private static final String NO_OWNER = "none";

    private final List<PaletteWrite> writes = new ArrayList<>();
    private final String[][][] owners = new String[2][4][16];

    public PaletteOwnershipRegistry() {
        resetOwners();
    }

    public void beginFrame() {
        writes.clear();
        resetOwners();
    }

    public void submit(PaletteWrite write) {
        writes.add(write);
    }

    public String ownerAt(PaletteSurface surface, int lineIndex, int colorIndex) {
        return owners[surface.ordinal()][lineIndex][colorIndex];
    }

    public void resolveInto(Palette[] normal, Palette[] underwater,
                            GraphicsManager graphics, Palette normalLine0) {
        boolean[] normalDirty = new boolean[4];
        boolean underwaterDirty = false;

        writes.stream()
                .sorted(Comparator.comparingInt(PaletteWrite::priority))
                .forEach(write -> {
                    applyWrite(surfaceArray(write.surface(), normal, underwater), write, normalDirty);
                    applyOwners(write.surface(), write);
                    if (write.mirrorToUnderwaterEnabled() && underwater != null) {
                        applyWrite(underwater, write, null);
                        applyOwners(PaletteSurface.UNDERWATER, write);
                    }
                });

        if (graphics != null && graphics.isGlInitialized()) {
            for (int line = 0; line < normalDirty.length; line++) {
                if (normalDirty[line]) {
                    graphics.cachePaletteTexture(normal[line], line);
                }
            }
            if (underwater != null) {
                for (int line = 0; line < 4; line++) {
                    for (int color = 0; color < 16; color++) {
                        if (!NO_OWNER.equals(owners[PaletteSurface.UNDERWATER.ordinal()][line][color])) {
                            underwaterDirty = true;
                            break;
                        }
                    }
                }
                if (underwaterDirty && normalLine0 != null) {
                    graphics.cacheUnderwaterPaletteTexture(underwater, normalLine0);
                }
            }
        }
    }

    private void applyWrite(Palette[] palettes, PaletteWrite write, boolean[] normalDirty) {
        if (palettes == null) {
            return;
        }
        Palette palette = palettes[write.lineIndex()];
        if (palette == null) {
            return;
        }
        byte[] data = write.segaData();
        for (int i = 0; i < data.length / 2; i++) {
            palette.getColor(write.startColor() + i).fromSegaFormat(data, i * 2);
        }
        if (normalDirty != null) {
            normalDirty[write.lineIndex()] = true;
        }
    }

    private void applyOwners(PaletteSurface surface, PaletteWrite write) {
        for (int i = 0; i < write.colorCount(); i++) {
            owners[surface.ordinal()][write.lineIndex()][write.startColor() + i] = write.ownerId();
        }
    }

    private Palette[] surfaceArray(PaletteSurface surface, Palette[] normal, Palette[] underwater) {
        return surface == PaletteSurface.NORMAL ? normal : underwater;
    }

    private void resetOwners() {
        for (int surface = 0; surface < owners.length; surface++) {
            for (int line = 0; line < owners[surface].length; line++) {
                for (int color = 0; color < owners[surface][line].length; color++) {
                    owners[surface][line][color] = NO_OWNER;
                }
            }
        }
    }
}
