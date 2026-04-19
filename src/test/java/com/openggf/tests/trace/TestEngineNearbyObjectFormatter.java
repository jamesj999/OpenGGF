package com.openggf.tests.trace;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestEngineNearbyObjectFormatter {

    @Test
    void summarisesNearbyObjectsWithSlotTypeNameAndPosition() {
        String summary = EngineNearbyObjectFormatter.summarise(List.of(
                new EngineNearbyObject(35, 0x40, "Motobug", 0x025B, 0x03B7, 0x08,
                        0x08, 0x025B, 0x03B7, false, false, true),
                new EngineNearbyObject(41, 0x23, "BuzzBomberMissile", 0x0244, 0x038C, 0x87,
                        0x00, 0x0242, 0x038E, false, true, false)));

        assertEquals("eng-near s35 0x40 Motobug @025B,03B7 col=08 | eng-near s41 0x23 BuzzBomberMissile @0244,038C col=87 preCol=00 pre=@0242,038E gate=skipSolid,offscreenTouch",
                summary);
    }

    @Test
    void returnsEmptyStringWhenNoNearbyObjectsExist() {
        assertEquals("", EngineNearbyObjectFormatter.summarise(List.of()));
    }
}
