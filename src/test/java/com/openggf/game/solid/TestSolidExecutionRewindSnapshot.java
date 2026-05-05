package com.openggf.game.solid;

import com.openggf.game.rewind.snapshot.SolidExecutionSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSolidExecutionRewindSnapshot {

    @Test
    void keyIsSolidExecution() {
        assertEquals("solid-execution", new DefaultSolidExecutionRegistry().key());
    }

    @Test
    void captureReturnsEmptyRecord() {
        DefaultSolidExecutionRegistry reg = new DefaultSolidExecutionRegistry();
        SolidExecutionSnapshot snap = reg.capture();
        assertNotNull(snap);
    }

    @Test
    void restoreIsNoOp() {
        // No exception thrown — restore is intentionally a no-op for this registry
        // since object-reference-keyed maps cannot be meaningfully serialised.
        DefaultSolidExecutionRegistry reg = new DefaultSolidExecutionRegistry();
        SolidExecutionSnapshot snap = reg.capture();
        assertDoesNotThrow(() -> reg.restore(snap));
    }
}
