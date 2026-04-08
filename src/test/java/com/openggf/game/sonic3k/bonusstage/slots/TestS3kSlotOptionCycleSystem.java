package com.openggf.game.sonic3k.bonusstage.slots;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestS3kSlotOptionCycleSystem {

    @Test
    void bootstrapSeedsRomShapedOptionCycleState() throws Exception {
        Class<?> systemClass = Class.forName(
                "com.openggf.game.sonic3k.bonusstage.slots.S3kSlotOptionCycleSystem");
        Object system = systemClass.getConstructor().newInstance();
        S3kSlotStageState state = S3kSlotStageState.bootstrap();

        systemClass.getMethod("bootstrap", S3kSlotStageState.class).invoke(system, state);

        assertEquals(0, readIntField(state, "optionCycleState"));
        assertEquals(0, readIntField(state, "optionCycleCountdown"));
        assertEquals(0, readIntField(state, "optionCycleTargetReelA"));
        assertEquals(0, readIntField(state, "optionCycleTargetPackedBC"));
        assertEquals(Integer.MIN_VALUE, readIntField(state, "optionCycleLastPrize"));
        assertEquals(0, readIntField(state, "optionCycleCompletedCycles"));
        int[] displaySymbols = (int[]) readField(state, "optionCycleDisplaySymbols");
        assertNotNull(displaySymbols);
        assertEquals(3, displaySymbols.length);
        assertEquals(0, displaySymbols[0]);
        assertEquals(0, displaySymbols[1]);
        assertEquals(0, displaySymbols[2]);
    }

    @Test
    void sourceOffsetMatchesSub_4C77C() throws Exception {
        Class<?> systemClass = Class.forName(
                "com.openggf.game.sonic3k.bonusstage.slots.S3kSlotOptionCycleSystem");
        Method sourceOffsetFor = systemClass.getMethod("sourceOffsetFor", int.class, int.class);

        assertEquals(0x0000, sourceOffsetFor.invoke(null, 0, 0x0000));
        assertEquals(0x0240, sourceOffsetFor.invoke(null, 1, 0x0080));
        assertEquals(0x0C7C, sourceOffsetFor.invoke(null, 6, 0x00F8));
    }

    @Test
    void firstTickAdvancesFromBootstrapToInitState() throws Exception {
        Class<?> systemClass = Class.forName(
                "com.openggf.game.sonic3k.bonusstage.slots.S3kSlotOptionCycleSystem");
        Object system = systemClass.getConstructor().newInstance();
        S3kSlotStageState state = S3kSlotStageState.bootstrap();

        systemClass.getMethod("bootstrap", S3kSlotStageState.class).invoke(system, state);
        systemClass.getMethod("tick", S3kSlotStageState.class, int.class).invoke(system, state, 0);

        assertEquals(4, readIntField(state, "optionCycleState"));
        assertEquals(1, readIntField(state, "optionCycleCountdown"));
    }

    private static Object readField(S3kSlotStageState state, String fieldName) throws Exception {
        Field field = S3kSlotStageState.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(state);
    }

    private static int readIntField(S3kSlotStageState state, String fieldName) throws Exception {
        return (int) readField(state, fieldName);
    }
}
