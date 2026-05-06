package com.openggf.game.rewind;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TestRewindFieldAudit {
    static class WithUnannotatedFinal {
        final Object value = new Object();
    }

    static class WithAnnotatedFinal {
        @RewindTransient(reason = "structural final fixture")
        final Object value = new Object();
    }

    @BeforeEach
    void clearEligibilityBeforeTest() {
        GenericRewindEligibility.clearForTest();
    }

    @AfterEach
    void clearEligibilityAfterTest() {
        GenericRewindEligibility.clearForTest();
    }

    @Test
    void eligibleClassesHaveOnlySupportedGenericFields() {
        List<String> unsupported = unsupportedEligibleFields();
        if (!unsupported.isEmpty()) {
            fail("Generic-eligible classes contain unsupported fields:\n"
                    + String.join("\n", unsupported));
        }
    }

    @Test
    void auditReportsUnannotatedFinalFieldsInEligibleClasses() {
        GenericRewindEligibility.registerForTestOrMigration(WithUnannotatedFinal.class);

        List<String> unsupported = unsupportedEligibleFields();

        assertFalse(unsupported.isEmpty());
        assertTrue(unsupported.stream()
                .anyMatch(line -> line.contains(WithUnannotatedFinal.class.getName() + "#value")));
    }

    @Test
    void auditSkipsAnnotatedFinalFieldsInEligibleClasses() {
        GenericRewindEligibility.registerForTestOrMigration(WithAnnotatedFinal.class);

        List<String> unsupported = unsupportedEligibleFields();

        if (!unsupported.isEmpty()) {
            fail("Annotated final fixture should be skipped:\n" + String.join("\n", unsupported));
        }
    }

    private static List<String> unsupportedEligibleFields() {
        List<String> unsupported = new ArrayList<>();
        for (Class<?> cls : GenericRewindEligibility.eligibleClassesForAudit()) {
            for (Class<?> owner : RewindScanSupport.withNestedRuntimeOwnerClasses(cls)) {
                collectUnsupportedFieldsForCapture(owner, unsupported);
            }
        }
        return unsupported;
    }

    private static void collectUnsupportedFieldsForCapture(Class<?> cls, List<String> unsupported) {
        for (Field field : GenericFieldCapturer.inspectedFieldsForAudit(cls)) {
            if (!GenericFieldCapturer.isSupportedDeclaredTypeForAudit(field)) {
                unsupported.add(field.getDeclaringClass().getName() + "#" + field.getName()
                        + " : " + field.getType().getName());
            }
        }
    }
}
