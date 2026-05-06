package com.openggf.game.rewind;

import com.openggf.game.sonic2.objects.badniks.MasherBadnikInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGenericRewindEligibility {
    @BeforeEach
    void clearEligibilityBeforeTest() {
        GenericRewindEligibility.clearForTest();
    }

    @AfterEach
    void clearEligibilityAfterTest() {
        GenericRewindEligibility.clearForTest();
    }

    @Test
    void classMustBeExplicitlyEligibleForSidecarCapture() {
        assertFalse(GenericRewindEligibility.isEligible(MasherBadnikInstance.class));
    }

    @Test
    void registeredClassesAreEligibleAndAuditable() {
        GenericRewindEligibility.registerForTestOrMigration(MasherBadnikInstance.class);

        assertTrue(GenericRewindEligibility.isEligible(MasherBadnikInstance.class));
        assertTrue(GenericRewindEligibility.eligibleClassesForAudit().contains(MasherBadnikInstance.class));
    }

    @Test
    void eligibleAuditSetIsACopy() {
        GenericRewindEligibility.registerForTestOrMigration(MasherBadnikInstance.class);

        var copy = GenericRewindEligibility.eligibleClassesForAudit();

        assertThrows(UnsupportedOperationException.class, () -> copy.clear());
        GenericRewindEligibility.clearForTest();
        assertTrue(copy.contains(MasherBadnikInstance.class));
        assertFalse(GenericRewindEligibility.eligibleClassesForAudit().contains(MasherBadnikInstance.class));
    }
}
