package com.openggf.game.rewind;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestRewindRegistry {

    private static RewindSnapshottable<Integer> intSnap(String key, AtomicInteger ref) {
        return new RewindSnapshottable<>() {
            @Override public String key() { return key; }
            @Override public Integer capture() { return ref.get(); }
            @Override public void restore(Integer s) { ref.set(s); }
        };
    }

    @Test
    void captureWalksRegistrationOrder() {
        RewindRegistry reg = new RewindRegistry();
        AtomicInteger a = new AtomicInteger(1), b = new AtomicInteger(2);
        reg.register(intSnap("a", a));
        reg.register(intSnap("b", b));
        CompositeSnapshot cs = reg.capture();
        assertEquals(java.util.List.of("a", "b"),
                java.util.List.copyOf(cs.entries().keySet()));
        assertEquals(1, cs.get("a"));
        assertEquals(2, cs.get("b"));
    }

    @Test
    void restoreAppliesEachSnapshot() {
        RewindRegistry reg = new RewindRegistry();
        AtomicInteger a = new AtomicInteger(1);
        reg.register(intSnap("a", a));
        CompositeSnapshot cs = reg.capture();
        a.set(99);
        reg.restore(cs);
        assertEquals(1, a.get());
    }

    @Test
    void deregisterRemovesSubsystem() {
        RewindRegistry reg = new RewindRegistry();
        AtomicInteger a = new AtomicInteger(1);
        reg.register(intSnap("a", a));
        reg.deregister("a");
        CompositeSnapshot cs = reg.capture();
        assertTrue(cs.entries().isEmpty());
    }

    @Test
    void duplicateKeyRejected() {
        RewindRegistry reg = new RewindRegistry();
        AtomicInteger a = new AtomicInteger();
        reg.register(intSnap("dup", a));
        assertThrows(IllegalStateException.class,
                () -> reg.register(intSnap("dup", a)));
    }

    @Test
    void restoreOnUnknownKeyIsTolerated() {
        // If a snapshot has a key that's not registered (e.g. subsystem
        // was removed since capture), restore should silently skip it.
        RewindRegistry reg = new RewindRegistry();
        var entries = new java.util.LinkedHashMap<String, Object>();
        entries.put("ghost", 42);
        reg.restore(new CompositeSnapshot(entries));
        // No exception — pass.
    }
}
