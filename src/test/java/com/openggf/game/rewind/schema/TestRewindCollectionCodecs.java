package com.openggf.game.rewind.schema;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestRewindCollectionCodecs {
    @AfterEach
    void clearRegistry() {
        RewindSchemaRegistry.clearForTest();
    }

    @Test
    void restoresValueCollectionsAndMaps() {
        CollectionFixture fixture = new CollectionFixture();

        RewindObjectStateBlob blob = CompactFieldCapturer.capture(fixture);
        fixture.mutate();
        CompactFieldCapturer.restore(fixture, blob);

        assertEquals(List.of(3, 1, 4), fixture.values);
        assertEquals(Arrays.asList(Mode.ALPHA, null, Mode.GAMMA), new ArrayList<>(fixture.modes));
        assertEquals(List.of("left", "right", "missing"), new ArrayList<>(fixture.counts.keySet()));
        assertEquals(7, fixture.counts.get("left"));
        assertEquals(9, fixture.counts.get("right"));
        assertEquals(null, fixture.counts.get("missing"));
    }

    @Test
    void restoresFinalCollectionsInPlace() {
        FinalCollectionFixture fixture = new FinalCollectionFixture();
        List<Integer> original = fixture.values;

        RewindObjectStateBlob blob = CompactFieldCapturer.capture(fixture);
        fixture.values.clear();
        fixture.values.add(99);
        CompactFieldCapturer.restore(fixture, blob);

        assertSame(original, fixture.values);
        assertEquals(List.of(1, 2, 3), fixture.values);
    }

    @Test
    void rejectsCollectionWithUnsupportedElementType() {
        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(UnsupportedCollectionFixture.class);

        assertEquals(1, schema.unsupportedFields().size());
        assertEquals("objects", schema.unsupportedFields().getFirst().field().getName());
        assertThrows(IllegalStateException.class,
                () -> CompactFieldCapturer.capture(new UnsupportedCollectionFixture()));
    }

    private enum Mode {
        ALPHA,
        BETA,
        GAMMA
    }

    private static final class CollectionFixture {
        List<Integer> values = new ArrayList<>(List.of(3, 1, 4));
        Set<Mode> modes = linkedSet(Mode.ALPHA, null, Mode.GAMMA);
        Map<String, Integer> counts = linkedMap();

        void mutate() {
            values = new ArrayList<>(List.of(-1));
            modes = linkedSet(Mode.BETA);
            counts = new LinkedHashMap<>();
            counts.put("mutated", -2);
        }
    }

    private static final class FinalCollectionFixture {
        final List<Integer> values = new ArrayList<>(List.of(1, 2, 3));
    }

    private static final class UnsupportedCollectionFixture {
        List<Object> objects = new ArrayList<>(List.of(new Object()));
    }

    @SafeVarargs
    private static <T> Set<T> linkedSet(T... values) {
        Set<T> set = new LinkedHashSet<>();
        for (T value : values) {
            set.add(value);
        }
        return set;
    }

    private static Map<String, Integer> linkedMap() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("left", 7);
        map.put("right", 9);
        map.put("missing", null);
        return map;
    }
}
