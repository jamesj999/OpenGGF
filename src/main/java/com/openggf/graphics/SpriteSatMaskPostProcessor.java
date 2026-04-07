package com.openggf.graphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a narrow ROM-style sprite-mask post-pass to SAT-like mapping-piece
 * entries before they are expanded into 8x8 tiles.
 *
 * <p>The S3K post-pass scans built SAT entries for tile {@code 0x7C0}, then
 * converts that pair into a hardware sprite mask at SAT X={@code 1}/{@code 0}.
 * In the software renderer we model the same effect as a vertical scanline band
 * that suppresses later SAT entries on overlapping rows. The mask pair itself is
 * not drawn.</p>
 */
public final class SpriteSatMaskPostProcessor {

    private static final int MASK_TILE_WORD = 0x7C0;
    private static final int TILE_HEIGHT = 8;

    private SpriteSatMaskPostProcessor() {
    }

    public static List<SpriteSatEntry> process(List<SpriteSatEntry> entries, boolean spriteMaskEnabled) {
        if (!spriteMaskEnabled || entries == null || entries.isEmpty()) {
            return entries == null ? List.of() : new ArrayList<>(entries);
        }

        List<VerticalBand> activeBands = new ArrayList<>();
        List<SpriteSatEntry> processed = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            SpriteSatEntry entry = entries.get(i);
            if (isMaskMarker(entries, i)) {
                SpriteSatEntry companion = entries.get(i + 1);
                VerticalBand band = createMaskBand(entry, companion);
                if (band != null) {
                    activeBands.add(band);
                }
                processed.add(companion);
                i++; // The marker becomes the mask; the companion remains visible.
                continue;
            }

            processed.addAll(clipAgainstBands(entry, activeBands));
        }

        return processed;
    }

    private static boolean isMaskMarker(List<SpriteSatEntry> entries, int index) {
        return index + 1 < entries.size() && entries.get(index).tileWordLow11() == MASK_TILE_WORD;
    }

    private static VerticalBand createMaskBand(SpriteSatEntry marker, SpriteSatEntry companion) {
        int startY = Math.max(marker.y(), companion.y());
        int endYExclusive = Math.min(marker.endYExclusive(), companion.endYExclusive());
        if (startY >= endYExclusive) {
            return null;
        }
        return new VerticalBand(startY, endYExclusive);
    }

    private static List<SpriteSatEntry> clipAgainstBands(SpriteSatEntry entry, List<VerticalBand> activeBands) {
        List<RowRange> remaining = new ArrayList<>();
        remaining.add(new RowRange(entry.startRowTile(), entry.startRowTile() + entry.rowCountTiles()));

        for (VerticalBand band : activeBands) {
            int removeStartRow = Math.max(entry.startRowTile(), floorDiv(band.startY() - entry.y(), TILE_HEIGHT));
            int removeEndRow = Math.min(
                    entry.startRowTile() + entry.rowCountTiles(),
                    ceilDiv(band.endYExclusive() - entry.y(), TILE_HEIGHT));
            if (removeStartRow >= removeEndRow) {
                continue;
            }

            List<RowRange> next = new ArrayList<>();
            for (RowRange range : remaining) {
                if (removeEndRow <= range.startRow() || removeStartRow >= range.endRowExclusive()) {
                    next.add(range);
                    continue;
                }
                if (removeStartRow > range.startRow()) {
                    next.add(new RowRange(range.startRow(), removeStartRow));
                }
                if (removeEndRow < range.endRowExclusive()) {
                    next.add(new RowRange(removeEndRow, range.endRowExclusive()));
                }
            }
            remaining = next;
            if (remaining.isEmpty()) {
                break;
            }
        }

        List<SpriteSatEntry> clipped = new ArrayList<>();
        for (RowRange range : remaining) {
            int rowCount = range.endRowExclusive() - range.startRow();
            if (rowCount > 0) {
                clipped.add(entry.clipRows(range.startRow(), rowCount));
            }
        }
        return clipped;
    }

    private static int floorDiv(int numerator, int denominator) {
        return Math.floorDiv(numerator, denominator);
    }

    private static int ceilDiv(int numerator, int denominator) {
        return -Math.floorDiv(-numerator, denominator);
    }

    private record VerticalBand(int startY, int endYExclusive) {
    }

    private record RowRange(int startRow, int endRowExclusive) {
    }
}
