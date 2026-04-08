package com.openggf.game.sonic3k.bonusstage.slots;

/**
 * Match detection and prize multiplier matching ROM sub_4C7A2 (lines 100029-100173).
 */
public final class S3kSlotPrizeCalculator {

    private S3kSlotPrizeCalculator() {
    }

    /**
     * Calculate prize from reel results.
     *
     * @param reelA    symbol index (0-7) for reel A
     * @param packedBC upper nibble=reel B symbol, lower nibble=reel C symbol
     * @return ring reward (positive), spike indicator (negative from symbol 4), or 0
     */
    public static int calculate(int reelA, byte packedBC) {
        int reelB = (packedBC & 0xF0) >>> 4;
        int reelC = packedBC & 0x0F;

        // Build dispatch index matching ROM jump table on d0
        int dispatch = 0;
        if (reelA == reelB) dispatch += 4;
        if (reelA == reelC) dispatch += 8;

        return switch (dispatch) {
            case 12 -> // All three match (A==B && A==C)
                lookupReward(reelA) * 4;
            case 8 ->  // A==C only (B differs)
                handleTwoMatch(reelA, reelC, reelB);
            case 4 ->  // A==B only (C differs)
                handleTwoMatch(reelA, reelB, reelC);
            case 0 ->  // No A matches
                handleNoAMatch(reelA, reelB, reelC);
            default -> 0;
        };
    }

    // ROM loc_4C7E0 (A==C path) and loc_4C80E (A==B path):
    // If the odd-one-out is 0, matched pair × 4
    // If matched symbol is 0, odd-one-out × 2
    // Otherwise fall through to no-match (count sixes)
    private static int handleTwoMatch(int matchedSymbol, int matchedDuplicate, int oddOneOut) {
        if (oddOneOut == 0) {
            return lookupReward(matchedSymbol) * 4;
        }
        if (matchedSymbol == 0) {
            return lookupReward(oddOneOut) * 2;
        }
        return countSixes(matchedSymbol, matchedDuplicate, oddOneOut);
    }

    // ROM loc_4C838: B==C but neither matches A
    // If A==0: B/C reward × 2
    // If B==0(==C): A reward × 4
    // Else: count sixes
    private static int handleNoAMatch(int reelA, int reelB, int reelC) {
        if (reelB == reelC) {
            if (reelA == 0) {
                return lookupReward(reelB) * 2;
            }
            if (reelB == 0) {
                return lookupReward(reelA) * 4;
            }
        }
        return countSixes(reelA, reelB, reelC);
    }

    // ROM loc_4C86C (lines 100127-100145): count symbol 6 occurrences, each adds 2
    private static int countSixes(int a, int b, int c) {
        int bonus = 0;
        if (a == 6) bonus += 2;
        if (b == 6) bonus += 2;
        if (c == 6) bonus += 2;
        return bonus;
    }

    private static int lookupReward(int symbol) {
        if (symbol < 0 || symbol >= S3kSlotRomData.REWARD_VALUES.length) return 0;
        return S3kSlotRomData.REWARD_VALUES[symbol];
    }
}
