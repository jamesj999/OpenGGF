package com.openggf.game.sonic3k;

import com.openggf.game.AbstractBonusStageCoordinator;
import com.openggf.game.BonusStageType;

/**
 * S3K-specific bonus stage coordinator.
 * Provides zone ID and music ID mapping for Gumball, Pachinko, and Slots.
 *
 * <p>Ring-based selection formula: {@code remainder = ((rings - 20) / 15) % 3}
 * <p>ROM loc_2D47E (sonic3k.asm lines 61886-61912):
 * <ul>
 *   <li>0 -> SLOT_MACHINE (zone $1500, music $20)</li>
 *   <li>1 -> GLOWING_SPHERE / Pachinko (zone $1400, music $1F)</li>
 *   <li>2 -> GUMBALL (zone $1300, music $1E)</li>
 * </ul>
 * <p>
 * Note: SK_alone_flag handling is not implemented. The only supported S3K ROM
 * is the combined "Sonic and Knuckles &amp; Sonic 3 (W) [!].gen" which always sets
 * SK_alone_flag=0 (3 bonus stages available). For S&amp;K standalone ROMs, the
 * divisor would be 2 and remainder=2 branch would route to Pachinko instead
 * of Gumball (ROM loc_2D47E lines 61897, 61910-61912).
 */
public class Sonic3kBonusStageCoordinator extends AbstractBonusStageCoordinator {

    private static final int ZONE_GUMBALL  = 0x1300;
    private static final int ZONE_PACHINKO = 0x1400;
    private static final int ZONE_SLOTS    = 0x1500;

    private static final int MUS_GUMBALL  = 0x1E;
    private static final int MUS_PACHINKO = 0x1F;
    private static final int MUS_SLOTS    = 0x20;

    private static final int RING_THRESHOLD = 20;
    private static final int RING_DIVISOR   = 15;
    private static final int STAGE_COUNT    = 3;

    @Override
    public BonusStageType selectBonusStage(int ringCount) {
        if (ringCount < RING_THRESHOLD) return BonusStageType.NONE;
        int remainder = ((ringCount - RING_THRESHOLD) / RING_DIVISOR) % STAGE_COUNT;
        return switch (remainder) {
            case 0 -> BonusStageType.SLOT_MACHINE;
            case 1 -> BonusStageType.GLOWING_SPHERE;
            case 2 -> BonusStageType.GUMBALL;
            default -> BonusStageType.NONE;
        };
    }

    @Override
    public int getZoneId(BonusStageType type) {
        return switch (type) {
            case GUMBALL -> ZONE_GUMBALL;
            case GLOWING_SPHERE -> ZONE_PACHINKO;
            case SLOT_MACHINE -> ZONE_SLOTS;
            default -> -1;
        };
    }

    @Override
    public int getMusicId(BonusStageType type) {
        return switch (type) {
            case GUMBALL -> MUS_GUMBALL;
            case GLOWING_SPHERE -> MUS_PACHINKO;
            case SLOT_MACHINE -> MUS_SLOTS;
            default -> -1;
        };
    }
}
