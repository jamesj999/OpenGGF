package com.openggf.game.rewind;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class TestCompositeSnapshot {

    @Test
    void preservesInsertionOrder() {
        var entries = new LinkedHashMap<String, Object>();
        entries.put("camera", "c1");
        entries.put("rng", "r1");
        entries.put("game-state", "gs1");
        CompositeSnapshot cs = new CompositeSnapshot(entries);
        assertEquals(java.util.List.of("camera", "rng", "game-state"),
                java.util.List.copyOf(cs.entries().keySet()));
    }

    @Test
    void rejectsNullEntries() {
        assertThrows(NullPointerException.class,
                () -> new CompositeSnapshot(null));
    }

    @Test
    void entriesViewIsImmutable() {
        var entries = new LinkedHashMap<String, Object>();
        entries.put("k", "v");
        CompositeSnapshot cs = new CompositeSnapshot(entries);
        assertThrows(UnsupportedOperationException.class,
                () -> cs.entries().put("x", "y"));
    }

    @Test
    void getReturnsTypedValue() {
        var entries = new LinkedHashMap<String, Object>();
        entries.put("camera", new Object());
        CompositeSnapshot cs = new CompositeSnapshot(entries);
        Object v = cs.get("camera");
        assertNotNull(v);
        assertNull(cs.get("missing"));
    }
}
