package com.openggf.graphics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSpriteSatMaskPostProcessor {

    @Test
    void maskPair_convertsToMaskBand_andClipsLaterPiece() {
        SpriteSatEntry earlierPiece = SpriteSatEntry.of(
                100, 0,
                2, 2,
                0x100, 0,
                false, false,
                false, false);
        SpriteSatEntry maskMarker = SpriteSatEntry.of(
                108, 24,
                2, 2,
                0x7C0, 0,
                false, false,
                false, false);
        SpriteSatEntry maskCompanion = SpriteSatEntry.of(
                100, 24,
                2, 2,
                0x25F, 0,
                false, false,
                false, false);
        SpriteSatEntry laterPiece = SpriteSatEntry.of(
                100, 16,
                4, 4,
                0x200, 0,
                false, false,
                false, false);

        List<SpriteSatEntry> processed = SpriteSatMaskPostProcessor.process(
                List.of(earlierPiece, maskMarker, maskCompanion, laterPiece),
                true);

        assertEquals(4, processed.size());

        assertEquals(0x100, processed.get(0).firstPatternIndex());
        assertEquals(0, processed.get(0).startColTile());
        assertEquals(2, processed.get(0).colCountTiles());
        assertEquals(0, processed.get(0).startRowTile());
        assertEquals(2, processed.get(0).rowCountTiles());

        assertEquals(0x25F, processed.get(1).firstPatternIndex());
        assertEquals(0, processed.get(1).startColTile());
        assertEquals(2, processed.get(1).colCountTiles());
        assertEquals(0, processed.get(1).startRowTile());
        assertEquals(2, processed.get(1).rowCountTiles());

        assertEquals(0x200, processed.get(2).firstPatternIndex());
        assertEquals(0, processed.get(2).startColTile());
        assertEquals(4, processed.get(2).colCountTiles());
        assertEquals(0, processed.get(2).startRowTile());
        assertEquals(1, processed.get(2).rowCountTiles());

        assertEquals(0x200, processed.get(3).firstPatternIndex());
        assertEquals(0, processed.get(3).startColTile());
        assertEquals(4, processed.get(3).colCountTiles());
        assertEquals(3, processed.get(3).startRowTile());
        assertEquals(1, processed.get(3).rowCountTiles());
    }

    @Test
    void disabledMask_keepsEntriesUnchanged() {
        SpriteSatEntry maskMarker = SpriteSatEntry.of(
                108, 24,
                2, 2,
                0x7C0, 0,
                false, false,
                false, false);
        SpriteSatEntry maskCompanion = SpriteSatEntry.of(
                100, 24,
                2, 2,
                0x25F, 0,
                false, false,
                false, false);

        List<SpriteSatEntry> processed = SpriteSatMaskPostProcessor.process(
                List.of(maskMarker, maskCompanion),
                false);

        assertEquals(2, processed.size());
        assertEquals(maskMarker, processed.get(0));
        assertEquals(maskCompanion, processed.get(1));
    }

    @Test
    void maskBand_clipsLaterHighPriorityPieceToo() {
        SpriteSatEntry maskMarker = SpriteSatEntry.of(
                108, 24,
                2, 2,
                0x7C0, 0,
                false, false,
                false, false);
        SpriteSatEntry maskCompanion = SpriteSatEntry.of(
                100, 24,
                2, 2,
                0x25F, 0,
                false, false,
                false, false);
        SpriteSatEntry laterHighPiece = SpriteSatEntry.of(
                100, 16,
                4, 4,
                0x220, 0,
                false, false,
                true, false);

        List<SpriteSatEntry> processed = SpriteSatMaskPostProcessor.process(
                List.of(maskMarker, maskCompanion, laterHighPiece),
                true);

        assertEquals(3, processed.size());
        assertEquals(0x25F, processed.get(0).firstPatternIndex());
        assertEquals(0, processed.get(0).startColTile());
        assertEquals(2, processed.get(0).colCountTiles());
        assertEquals(0, processed.get(0).startRowTile());
        assertEquals(2, processed.get(0).rowCountTiles());
        assertEquals(0x220, processed.get(1).firstPatternIndex());
        assertEquals(0, processed.get(1).startColTile());
        assertEquals(4, processed.get(1).colCountTiles());
        assertEquals(0, processed.get(1).startRowTile());
        assertEquals(1, processed.get(1).rowCountTiles());
        assertEquals(0x220, processed.get(2).firstPatternIndex());
        assertEquals(0, processed.get(2).startColTile());
        assertEquals(4, processed.get(2).colCountTiles());
        assertEquals(3, processed.get(2).startRowTile());
        assertEquals(1, processed.get(2).rowCountTiles());
    }
}
