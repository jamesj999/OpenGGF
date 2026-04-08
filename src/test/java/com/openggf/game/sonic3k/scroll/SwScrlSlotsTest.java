package com.openggf.game.sonic3k.scroll;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotBonusStageRuntime;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.scroll.ZoneScrollHandler;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static com.openggf.level.scroll.M68KMath.unpackBG;
import static com.openggf.level.scroll.M68KMath.unpackFG;

public class SwScrlSlotsTest {

    @Test
    public void providerRoutesSlotsToDedicatedHandler() throws Exception {
        Sonic3kScrollHandlerProvider provider = new Sonic3kScrollHandlerProvider();
        provider.load(new Rom());

        ZoneScrollHandler handler = provider.getHandler(Sonic3kZoneIds.ZONE_SLOT_MACHINE);
        assertNotNull(handler);
        assertTrue(handler instanceof SwScrlSlots);
        assertNotSame(provider.getHandler(Sonic3kZoneConstants.ZONE_MGZ), handler);
    }

    @Test
    public void slotScrollUsesRuntimeAnchorsInsteadOfCameraOnlyApproximation() {
        SwScrlSlots scroll = new SwScrlSlots();
        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();
        var visual = runtime.slotVisualState();

        scroll.updateForTest(runtime, 0x300, 0x200, 12);

        assertEquals((0x400 - visual.eventsBgX()) + 0x300, scroll.lastForegroundOriginXForTest());
        assertEquals((0x400 - visual.eventsBgY()) + 0x200, scroll.lastForegroundOriginYForTest());
        assertEquals(0, scroll.lastBackgroundOriginYForTest());
        assertTrue(scroll.lastBackgroundOriginXForTest() != scroll.lastForegroundOriginXForTest());
    }

    @Test
    public void slotScrollOwnsBackgroundAccumulatorAndRespondsToScalarDirectionChanges() {
        SwScrlSlots scroll = new SwScrlSlots();
        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();

        for (int i = 0; i < 40; i++) {
            scroll.updateForTest(runtime, 0x300, 0x200, i);
        }

        int positiveVelocity = scroll.backgroundVelocityForTest();

        runtime.stageController().setScalarIndex(-0x40);
        scroll.updateForTest(runtime, 0x300, 0x200, 40);
        int postFlipVelocity = scroll.backgroundVelocityForTest();

        for (int i = 41; i < 80; i++) {
            scroll.updateForTest(runtime, 0x300, 0x200, i);
        }

        assertTrue("positiveVelocity=" + positiveVelocity, positiveVelocity >= 0x7C00);
        assertTrue("positiveVelocity=" + positiveVelocity, positiveVelocity <= 0x8000);
        assertTrue("positiveVelocity=" + positiveVelocity + " postFlipVelocity=" + postFlipVelocity,
                postFlipVelocity < positiveVelocity);
        assertTrue("finalVelocity=" + scroll.backgroundVelocityForTest(), scroll.backgroundVelocityForTest() < 0);
        assertEquals(0, scroll.getVscrollFactorBG() & 0xFF);
    }

    @Test
    public void slotScrollProducesSegmentedPackedBufferWithSharedForegroundAndVaryingBackgroundBands() {
        SwScrlSlots scroll = new SwScrlSlots();
        S3kSlotBonusStageRuntime runtime = new S3kSlotBonusStageRuntime();
        runtime.bootstrap();
        int[] horizScrollBuf = new int[224];

        scroll.updateForTest(runtime, horizScrollBuf, 0x300, 0x200, 12);

        short expectedFg = unpackFG(horizScrollBuf[0]);
        Set<Short> distinctBg = new HashSet<>();
        for (int packed : horizScrollBuf) {
            assertEquals(expectedFg, unpackFG(packed));
            distinctBg.add(unpackBG(packed));
        }

        assertEquals(horizScrollBuf[0], horizScrollBuf[0x1F]);
        assertEquals(horizScrollBuf[0x20], horizScrollBuf[0x3F]);
        assertTrue("Expected multiple slot background bands, got " + distinctBg.size(),
                distinctBg.size() > 1);
    }
}
