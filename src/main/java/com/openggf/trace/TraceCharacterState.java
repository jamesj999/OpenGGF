package com.openggf.trace;

import com.openggf.game.GameServices;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Per-character trace state used for optional sidekick tracking in schema v5+.
 */
public record TraceCharacterState(
    boolean present,
    short x,
    short y,
    short xSpeed,
    short ySpeed,
    short gSpeed,
    byte angle,
    boolean air,
    boolean rolling,
    int groundMode,
    int xSub,
    int ySub,
    int routine,
    int statusByte,
    int standOnObj
) {

    public static TraceCharacterState absent() {
        return new TraceCharacterState(false,
            (short) 0, (short) 0, (short) 0, (short) 0, (short) 0,
            (byte) 0, false, false, 0, 0, 0, -1, -1, -1);
    }

    /**
     * Capture the current engine state of a playable sprite in the
     * same shape the recorded CSV uses. Shared by headless and live
     * replay paths so both compare apples-to-apples.
     */
    public static TraceCharacterState fromSprite(AbstractPlayableSprite sprite) {
        if (sprite == null) {
            return absent();
        }
        var level = GameServices.levelOrNull();
        ObjectManager om = level != null ? level.getObjectManager() : null;
        int standOnSlot = -1;
        if (om != null) {
            ObjectInstance ridingObj = om.getRidingObject(sprite);
            if (ridingObj instanceof AbstractObjectInstance aoi && aoi.getSlotIndex() >= 0) {
                standOnSlot = aoi.getSlotIndex();
            }
        }
        int statusByte = 0;
        if (sprite.getDirection() == Direction.LEFT) {
            statusByte |= 0x01;
        }
        if (sprite.getAir()) statusByte |= 0x02;
        if (sprite.getRolling()) statusByte |= 0x04;
        if (sprite.isOnObject()) statusByte |= 0x08;
        if (sprite.isInWater()) statusByte |= 0x40;
        int routine = sprite.isHurt() ? 0x04 : 0x02;
        return new TraceCharacterState(true,
                sprite.getCentreX(),
                sprite.getCentreY(),
                sprite.getXSpeed(),
                sprite.getYSpeed(),
                sprite.getGSpeed(),
                sprite.getAngle(),
                sprite.getAir(),
                sprite.getRolling(),
                sprite.getGroundMode().ordinal(),
                sprite.getXSubpixelRaw(),
                sprite.getYSubpixelRaw(),
                routine,
                statusByte,
                standOnSlot);
    }

    public static TraceCharacterState parseCsvColumns(String[] parts, int offset) {
        boolean present = !parts[offset].trim().equals("0");
        if (!present) {
            return absent();
        }
        return new TraceCharacterState(
            true,
            (short) Integer.parseInt(parts[offset + 1].trim(), 16),
            (short) Integer.parseInt(parts[offset + 2].trim(), 16),
            parseSignedShortHex(parts[offset + 3].trim()),
            parseSignedShortHex(parts[offset + 4].trim()),
            parseSignedShortHex(parts[offset + 5].trim()),
            (byte) Integer.parseInt(parts[offset + 6].trim(), 16),
            !parts[offset + 7].trim().equals("0"),
            !parts[offset + 8].trim().equals("0"),
            Integer.parseInt(parts[offset + 9].trim()),
            Integer.parseInt(parts[offset + 10].trim(), 16),
            Integer.parseInt(parts[offset + 11].trim(), 16),
            Integer.parseInt(parts[offset + 12].trim(), 16),
            Integer.parseInt(parts[offset + 13].trim(), 16),
            Integer.parseInt(parts[offset + 14].trim(), 16));
    }

    public String formatDiagnostics(String label) {
        if (!present) {
            return label + "=absent";
        }
        return String.format(
            "%s=sub=(%04X,%04X) rtn=%02X status=%02X onObj=%02X",
            label, xSub, ySub, routine, statusByte, standOnObj);
    }

    private static short parseSignedShortHex(String hex) {
        int value = Integer.parseInt(hex, 16);
        if (value > 0x7FFF) {
            value -= 0x10000;
        }
        return (short) value;
    }
}
