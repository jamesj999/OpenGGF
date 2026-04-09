package com.openggf.game.sonic3k.bonusstage.slots;

/**
 * Match detection and prize multiplier matching ROM sub_4C7A2.
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

        int dispatch = 0;
        if (reelA == reelB) dispatch += 4;
        if (reelA == reelC) dispatch += 8;

        return switch (dispatch) {
            case 12 -> lookupReward(reelA) * 4;
            case 8 -> handleTwoMatch(reelA, reelC, reelB);
            case 4 -> handleTwoMatch(reelA, reelB, reelC);
            case 0 -> handleNoAMatch(reelA, reelB, reelC);
            default -> 0;
        };
    }

    // ROM loc_4C7E0 and loc_4C80E:
    // matched symbol zero pays the odd symbol x4; odd symbol zero pays the matched symbol x2.
    private static int handleTwoMatch(int matchedSymbol, int matchedDuplicate, int oddOneOut) {
        if (matchedSymbol == 0) {
            return lookupReward(oddOneOut) * 4;
        }
        if (oddOneOut == 0) {
            return lookupReward(matchedSymbol) * 2;
        }
        return countSixes(matchedSymbol, matchedDuplicate, oddOneOut);
    }

    // ROM loc_4C838:
    // If B == C and A is zero, B/C pays x2. If B == C == zero, A pays x4.
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

    // ROM loc_4C86C: count symbol 6 occurrences, each adds 2.
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
