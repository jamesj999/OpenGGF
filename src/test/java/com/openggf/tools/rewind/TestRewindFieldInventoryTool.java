package com.openggf.tools.rewind;

import com.openggf.game.rewind.GenericRewindEligibility;
import com.openggf.game.rewind.RewindDeferred;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRewindFieldInventoryTool {
    private static class DeferredStructuralFixture {
        @RewindDeferred(reason = "fixture structural dependency")
        private final Object structural = new Object();
    }

    @Test
    void unsupportedInventorySkipsDeferredFields() {
        List<String> unsupported = RewindFieldInventoryTool.unsupportedFieldsForClass(DeferredStructuralFixture.class);

        assertTrue(unsupported.isEmpty());
    }

    @Test
    void objectRolloutCandidatesAreDefaultCapturedObjects() throws Exception {
        List<String> candidates = RewindFieldInventoryTool.objectRolloutCandidates();

        assertFalse(candidates.isEmpty());
        for (String candidate : candidates) {
            String className = candidate.substring(0, candidate.indexOf(" : "));
            Class<?> type = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            assertTrue(GenericRewindEligibility.usesDefaultObjectSubclassCapture(type), candidate);
        }
    }
}
