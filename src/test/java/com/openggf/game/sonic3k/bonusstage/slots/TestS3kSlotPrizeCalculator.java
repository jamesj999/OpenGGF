package com.openggf.game.sonic3k.bonusstage.slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kSlotPrizeCalculator {

    @Test
    void allThreeMatchSymbol0Gives400() {
        // Symbol 0 = 100 rings, ×4 = 400
        assertEquals(400, S3kSlotPrizeCalculator.calculate(0, (byte) 0x00));
    }

    @Test
    void allThreeMatchSymbol7Gives800() {
        // Symbol 7 = 200 rings, ×4 = 800
        assertEquals(800, S3kSlotPrizeCalculator.calculate(7, (byte) 0x77));
    }

    @Test
    void allThreeMatchSymbol4GivesNegative() {
        // Symbol 4 = -1, ×4 = -4 (spike trigger)
        assertEquals(-4, S3kSlotPrizeCalculator.calculate(4, (byte) 0x44));
    }

    @Test
    void twoMatchABWithCZeroGivesQuadruple() {
        // A=1, B=1, C=0 → matched pair=1, odd=0 → symbol 1 (30) × 4 = 120
        assertEquals(120, S3kSlotPrizeCalculator.calculate(1, (byte) 0x10));
    }

    @Test
    void twoMatchACWithBZeroGivesQuadruple() {
        // A=2, B=0, C=2 → matched=2, odd=0 → symbol 2 (20) × 4 = 80
        assertEquals(80, S3kSlotPrizeCalculator.calculate(2, (byte) 0x02));
    }

    @Test
    void twoMatchWithMatchedSymbolZeroGivesDouble() {
        // A=0, B=0, C=3 → A==B, matched=0, odd=3 → symbol 3 (25) × 2 = 50
        assertEquals(50, S3kSlotPrizeCalculator.calculate(0, (byte) 0x03));
    }

    @Test
    void bcMatchWithAZeroGivesDouble() {
        // A=0, B=5, C=5 → no A match, B==C, A==0 → symbol 5 (10) × 2 = 20
        assertEquals(20, S3kSlotPrizeCalculator.calculate(0, (byte) 0x55));
    }

    @Test
    void bcMatchWithBCZeroGivesATimesQuadruple() {
        // A=3, B=0, C=0 → no A match, B==C==0, B==0 → symbol 3 (25) × 4 = 100
        assertEquals(100, S3kSlotPrizeCalculator.calculate(3, (byte) 0x00));
    }

    @Test
    void noMatchNoSixesGivesZero() {
        // A=1, B=2, C=3 → no matches, no 6s → 0
        assertEquals(0, S3kSlotPrizeCalculator.calculate(1, (byte) 0x23));
    }

    @Test
    void noMatchWithSixesCountsBonus() {
        // A=6, B=1, C=6 → no A matches (B≠6, C==6 but A==C triggers dispatch=8)
        // Wait: A=6, C=6 → A==C → dispatch=8 (A==C only path)
        // handleTwoMatch(6, 6, 1): oddOneOut=1 ≠ 0, matchedSymbol=6 ≠ 0 → countSixes(6,6,1) = 2+2=4
        assertEquals(4, S3kSlotPrizeCalculator.calculate(6, (byte) 0x16));
    }

    @Test
    void allThreeSixesGiveTripleMatchNotSixCount() {
        // A=6, B=6, C=6 → all three match → symbol 6 (8) × 4 = 32
        assertEquals(32, S3kSlotPrizeCalculator.calculate(6, (byte) 0x66));
    }
}
