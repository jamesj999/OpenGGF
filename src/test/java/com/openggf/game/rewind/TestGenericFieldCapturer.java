package com.openggf.game.rewind;

import com.openggf.game.rewind.snapshot.GenericObjectSnapshot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.BitSet;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestGenericFieldCapturer {
    enum Mode {
        IDLE,
        RUNNING
    }

    record Inner(int x, String y) {}

    record NestedRecord(Inner inner, Integer count, Mode mode, int[] samples) {}

    static class Plain {
        int counter;
        boolean flag;
        String name;
        Integer rings;
        Mode mode;
    }

    static class WithTransientFields {
        static int staticValue = 100;
        transient int javaTransient = 11;
        int captured;
        @RewindTransient(reason = "test-only derived value")
        int skipped;
    }

    static class WithUnannotatedFinalRef {
        final String structuralName = "kept-live";
        int gameplay = 7;
    }

    static class WithAnnotatedFinalRef {
        @RewindTransient(reason = "structural final fixture")
        final Object structuralRef = new Object();
        int gameplay = 7;
    }

    static class WithUnsupportedCollection {
        HashMap<String, Integer> map = new HashMap<>();
    }

    static class WithUnsupportedPojo {
        MutablePojo pojo = new MutablePojo();
    }

    static class MutablePojo {
        int value = 3;
    }

    static class WithRecordArrayAndBitSet {
        NestedRecord record = new NestedRecord(new Inner(3, "a"), 4, Mode.RUNNING, new int[] {5, 6});
        int[] primitives = {1, 2, 3};
        String[] strings = {"sonic", "tails"};
        Mode[] modes = {Mode.IDLE, Mode.RUNNING};
        Inner[] records = {new Inner(7, "b")};
        BitSet flags = bitSet(1, 3);
    }

    static class WithDuplicateParentName {
        int value = 1;
    }

    static class WithDuplicateChildName extends WithDuplicateParentName {
        int value = 2;
    }

    static class SubPlain extends Plain {}

    static class WithUnsortedFields {
        int zeta = 1;
        int alpha = 2;
        int middle = 3;
    }

    static class WithUnsupportedArray {
        MutablePojo[] values = {new MutablePojo()};
    }

    @Test
    void roundTripsSupportedScalarFields() {
        Plain p = new Plain();
        p.counter = 42;
        p.flag = true;
        p.name = "sonic";
        p.rings = 50;
        p.mode = Mode.RUNNING;

        GenericObjectSnapshot snap = GenericFieldCapturer.capture(p);
        Plain restored = new Plain();
        GenericFieldCapturer.restore(restored, snap);

        assertEquals(42, restored.counter);
        assertTrue(restored.flag);
        assertEquals("sonic", restored.name);
        assertEquals(50, restored.rings);
        assertEquals(Mode.RUNNING, restored.mode);
    }

    @Test
    void skipsStaticJavaTransientAndAnnotatedFields() {
        WithTransientFields w = new WithTransientFields();
        w.captured = 1;
        w.javaTransient = 2;
        w.skipped = 99;

        GenericObjectSnapshot snap = GenericFieldCapturer.capture(w);
        WithTransientFields restored = new WithTransientFields();
        restored.javaTransient = 8;
        restored.skipped = 5;
        GenericFieldCapturer.restore(restored, snap);

        assertEquals(1, restored.captured);
        assertEquals(8, restored.javaTransient);
        assertEquals(5, restored.skipped);
        assertNull(snap.value(new FieldKey(WithTransientFields.class.getName(), "staticValue")));
        assertNull(snap.value(new FieldKey(WithTransientFields.class.getName(), "javaTransient")));
        assertNull(snap.value(new FieldKey(WithTransientFields.class.getName(), "skipped")));
    }

    @Test
    void rejectsUnannotatedFinalFieldsByDefault() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> GenericFieldCapturer.capture(new WithUnannotatedFinalRef()));
        assertTrue(ex.getMessage().contains("structuralName"));
        assertTrue(ex.getMessage().contains("final"));
    }

    @Test
    void skipsAnnotatedFinalFields() {
        GenericObjectSnapshot snap = GenericFieldCapturer.capture(new WithAnnotatedFinalRef());
        assertNull(snap.value(new FieldKey(WithAnnotatedFinalRef.class.getName(), "structuralRef")));
        assertEquals(7, snap.value(new FieldKey(WithAnnotatedFinalRef.class.getName(), "gameplay")));
    }

    @Test
    void deepClonesRecordsArraysAndBitSets() {
        WithRecordArrayAndBitSet w = new WithRecordArrayAndBitSet();
        GenericObjectSnapshot snap = GenericFieldCapturer.capture(w);
        w.record.samples()[0] = 99;
        w.primitives[0] = 99;
        w.strings[0] = "changed";
        w.modes[0] = Mode.RUNNING;
        w.records[0] = new Inner(99, "changed");
        w.flags.clear(1);

        WithRecordArrayAndBitSet restored = new WithRecordArrayAndBitSet();
        GenericFieldCapturer.restore(restored, snap);

        assertEquals(new Inner(3, "a"), restored.record.inner());
        assertEquals(5, restored.record.samples()[0]);
        assertArrayEquals(new int[] {1, 2, 3}, restored.primitives);
        assertArrayEquals(new String[] {"sonic", "tails"}, restored.strings);
        assertArrayEquals(new Mode[] {Mode.IDLE, Mode.RUNNING}, restored.modes);
        assertArrayEquals(new Inner[] {new Inner(7, "b")}, restored.records);
        assertTrue(restored.flags.get(1));
        assertTrue(restored.flags.get(3));
        assertNotSame(w.flags, restored.flags);
    }

    @Test
    void qualifiedFieldKeysDistinguishDuplicateInheritedNames() {
        GenericObjectSnapshot snap = GenericFieldCapturer.capture(new WithDuplicateChildName());

        assertEquals(1, snap.value(new FieldKey(WithDuplicateParentName.class.getName(), "value")));
        assertEquals(2, snap.value(new FieldKey(WithDuplicateChildName.class.getName(), "value")));
    }

    @Test
    void snapshotDefensivelyCopiesValuesArray() {
        FieldKey key = new FieldKey(Plain.class.getName(), "counter");
        Object[] values = {1};
        GenericObjectSnapshot snap = new GenericObjectSnapshot(Plain.class, java.util.List.of(key), values);

        values[0] = 2;
        Object[] exposed = snap.values();
        exposed[0] = 3;

        assertEquals(1, snap.value(key));
        assertEquals(1, snap.values()[0]);
    }

    @Test
    void snapshotDefensivelyCopiesNestedMutableValues() {
        FieldKey arrayKey = new FieldKey(WithRecordArrayAndBitSet.class.getName(), "primitives");
        FieldKey bitSetKey = new FieldKey(WithRecordArrayAndBitSet.class.getName(), "flags");
        int[] primitives = {1, 2};
        BitSet flags = bitSet(1);
        GenericObjectSnapshot snap = new GenericObjectSnapshot(
                WithRecordArrayAndBitSet.class,
                java.util.List.of(arrayKey, bitSetKey),
                new Object[] {primitives, flags});

        primitives[0] = 99;
        flags.clear(1);
        ((int[]) snap.value(arrayKey))[0] = 88;
        ((BitSet) snap.values()[1]).clear(1);

        assertArrayEquals(new int[] {1, 2}, (int[]) snap.value(arrayKey));
        assertTrue(((BitSet) snap.value(bitSetKey)).get(1));
    }

    @Test
    void rejectsRestoreIntoDifferentRuntimeType() {
        GenericObjectSnapshot snap = GenericFieldCapturer.capture(new Plain());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> GenericFieldCapturer.restore(new SubPlain(), snap));

        assertTrue(ex.getMessage().contains(Plain.class.getName()));
        assertTrue(ex.getMessage().contains(SubPlain.class.getName()));
    }

    @Test
    void ordersFieldsDeterministicallyByHierarchyThenName() {
        GenericObjectSnapshot snap = GenericFieldCapturer.capture(new WithUnsortedFields());

        assertEquals(
                java.util.List.of(
                        new FieldKey(WithUnsortedFields.class.getName(), "alpha"),
                        new FieldKey(WithUnsortedFields.class.getName(), "middle"),
                        new FieldKey(WithUnsortedFields.class.getName(), "zeta")),
                snap.keys());
    }

    @Test
    void rejectsCollectionsInV16() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> GenericFieldCapturer.capture(new WithUnsupportedCollection()));
        assertTrue(ex.getMessage().contains("map"));
        assertTrue(ex.getMessage().contains("@RewindTransient"));
    }

    @Test
    void rejectsMutablePojosInV16() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> GenericFieldCapturer.capture(new WithUnsupportedPojo()));
        assertTrue(ex.getMessage().contains("pojo"));
        assertTrue(ex.getMessage().contains("unsupported"));
    }

    @Test
    void rejectsArraysOfMutablePojosInV16() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> GenericFieldCapturer.capture(new WithUnsupportedArray()));
        assertTrue(ex.getMessage().contains("values"));
        assertTrue(ex.getMessage().contains("unsupported"));
    }

    @Test
    void auditApiReportsEffectiveDeclaredFieldSupport() throws Exception {
        Field scalar = Plain.class.getDeclaredField("counter");
        Field collection = WithUnsupportedCollection.class.getDeclaredField("map");
        Field unannotatedFinal = WithUnannotatedFinalRef.class.getDeclaredField("structuralName");
        Field annotatedFinal = WithAnnotatedFinalRef.class.getDeclaredField("structuralRef");
        Field javaTransient = WithTransientFields.class.getDeclaredField("javaTransient");

        assertTrue(GenericFieldCapturer.isSupportedDeclaredTypeForAudit(scalar));
        assertFalse(GenericFieldCapturer.isSupportedDeclaredTypeForAudit(collection));
        assertFalse(GenericFieldCapturer.isSupportedDeclaredTypeForAudit(unannotatedFinal));
        assertTrue(GenericFieldCapturer.isSupportedDeclaredTypeForAudit(annotatedFinal));
        assertTrue(GenericFieldCapturer.isSupportedDeclaredTypeForAudit(javaTransient));
    }

    private static BitSet bitSet(int... bits) {
        BitSet result = new BitSet();
        for (int bit : bits) {
            result.set(bit);
        }
        return result;
    }
}
