package com.openggf.game.rewind;

import com.openggf.game.rewind.snapshot.LevelSnapshot;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reusable per-key diff helper for {@link CompositeSnapshot} comparisons.
 *
 * <p>Walks records, primitive arrays, object arrays, lists, and maps recursively
 * and emits path-based diff strings ("oscillation.lastFrame: A=1140 B=1200") for
 * each differing leaf. Capped at 20 diff lines per key to keep output bounded.
 *
 * <p>Custom comparators for {@code level} and {@code object-manager} keys
 * skip restore-side cosmetic divergence (epoch counter, peak slot count, dirty
 * flags) that is independent of replay correctness. See {@link RewindBenchmark}
 * for the original implementation; this class hoists the helpers so torture
 * tests can reuse them.
 */
final class RewindSnapshotDiff {

    private RewindSnapshotDiff() {}

    /**
     * Returns a list of human-readable mismatch strings for a single key, or
     * an empty list if the values agree under the key's content-equality rule.
     */
    static List<String> diffKey(String key, Object av, Object bv) {
        List<String> diffs = new ArrayList<>();
        if (av == null && bv == null) return diffs;
        if (av == null || bv == null) {
            diffs.add(key + ": A=" + av + " B=" + bv);
            return diffs;
        }
        if (keyEquals(key, av, bv)) return diffs;
        if ("object-manager".equals(key)) {
            collectObjectManagerDiffs(key, av, bv, diffs);
            return diffs;
        }
        collectDiffs(key, av, bv, diffs);
        return diffs;
    }

    /**
     * Object-manager diff: compares slots / childSpawns / dynamicObjects by
     * slot identity rather than list position. The {@code activeObjects}
     * IdentityHashMap that backs {@code slots()} has unspecified iteration
     * order, so two captures of the same logical state may produce slot lists
     * in different order. Bucketing by slotIndex makes the diff order-stable.
     */
    private static void collectObjectManagerDiffs(String path, Object av, Object bv,
                                                    List<String> diffs) {
        if (!(av instanceof ObjectManagerSnapshot oa)
                || !(bv instanceof ObjectManagerSnapshot ob)) {
            collectDiffs(path, av, bv, diffs);
            return;
        }
        if (!Arrays.equals(oa.usedSlotsBits(), ob.usedSlotsBits())) {
            diffs.add(path + ".usedSlotsBits differs");
        }
        if (oa.frameCounter() != ob.frameCounter()) {
            diffs.add(path + ".frameCounter: A=" + oa.frameCounter() + " B=" + ob.frameCounter());
        }
        if (oa.vblaCounter() != ob.vblaCounter()) {
            diffs.add(path + ".vblaCounter: A=" + oa.vblaCounter() + " B=" + ob.vblaCounter());
        }
        Map<Integer, ObjectManagerSnapshot.PerSlotEntry> aBySlot = new HashMap<>();
        for (var e : oa.slots()) aBySlot.put(e.slotIndex(), e);
        Map<Integer, ObjectManagerSnapshot.PerSlotEntry> bBySlot = new HashMap<>();
        for (var e : ob.slots()) bBySlot.put(e.slotIndex(), e);
        if (aBySlot.size() != oa.slots().size() || bBySlot.size() != ob.slots().size()) {
            diffs.add(path + ".slots: duplicate slotIndex detected");
        }
        java.util.Set<Integer> allSlots = new java.util.TreeSet<>();
        allSlots.addAll(aBySlot.keySet());
        allSlots.addAll(bBySlot.keySet());
        for (int slotIdx : allSlots) {
            if (diffs.size() >= 20) break;
            var ea = aBySlot.get(slotIdx);
            var eb = bBySlot.get(slotIdx);
            if (ea == null) {
                diffs.add(path + ".slot[" + slotIdx + "] missing in A (B="
                        + (eb.spawn() == null ? "<null>" : eb.spawn()) + ")");
                continue;
            }
            if (eb == null) {
                diffs.add(path + ".slot[" + slotIdx + "] missing in B (A="
                        + (ea.spawn() == null ? "<null>" : ea.spawn()) + ")");
                continue;
            }
            if (ea.spawn() != eb.spawn()) {
                diffs.add(path + ".slot[" + slotIdx + "].spawn ref differs");
            }
            if (!fieldContentEqual(ea.state(), eb.state())) {
                collectDiffs(path + ".slot[" + slotIdx + "].state",
                        ea.state(), eb.state(), diffs);
            }
        }
        // Compare childSpawns by parentSpawn identity (IdentityHashMap-backed)
        Map<com.openggf.level.objects.ObjectSpawn, int[]> aChild = new java.util.IdentityHashMap<>();
        for (var ce : oa.childSpawns()) aChild.put(ce.parentSpawn(), ce.reservedSlots());
        Map<com.openggf.level.objects.ObjectSpawn, int[]> bChild = new java.util.IdentityHashMap<>();
        for (var ce : ob.childSpawns()) bChild.put(ce.parentSpawn(), ce.reservedSlots());
        if (aChild.size() != bChild.size()) {
            diffs.add(path + ".childSpawns count: A=" + aChild.size() + " B=" + bChild.size());
        }
        for (var p : aChild.keySet()) {
            int[] av2 = aChild.get(p);
            int[] bv2 = bChild.get(p);
            if (bv2 == null) {
                diffs.add(path + ".childSpawns[" + p + "] missing in B");
                continue;
            }
            if (!Arrays.equals(av2, bv2)) {
                diffs.add(path + ".childSpawns[" + p + "].reservedSlots differs");
            }
        }
        // Dynamic objects + placement + solidContacts via path-based deep diff
        if (!fieldContentEqual(oa.dynamicObjects(), ob.dynamicObjects())) {
            collectDiffs(path + ".dynamicObjects", oa.dynamicObjects(),
                    ob.dynamicObjects(), diffs);
        }
        if (!fieldContentEqual(oa.placement(), ob.placement())) {
            collectDiffs(path + ".placement", oa.placement(), ob.placement(), diffs);
        }
        if (!fieldContentEqual(oa.solidContactRiding(), ob.solidContactRiding())) {
            collectDiffs(path + ".solidContactRiding", oa.solidContactRiding(),
                    ob.solidContactRiding(), diffs);
        }
    }

    /**
     * Per-key equality. Custom comparators for keys whose snapshot records
     * contain non-content-equal references; default uses a record-aware deep
     * equality that handles array fields via Arrays.equals (Java records
     * auto-generate .equals() with array reference equality, which makes
     * naive .equals() report false negatives for any record with array fields).
     */
    static boolean keyEquals(String key, Object a, Object b) {
        return switch (key) {
            case "level" -> compareLevel(a, b);
            case "object-manager" -> compareObjectManager(a, b);
            default -> recordsContentEqual(a, b);
        };
    }

    /**
     * Walks two values recursively and collects path-based diff strings for
     * each differing leaf. Caps at 20 diffs to keep output bounded.
     */
    static void collectDiffs(String path, Object av, Object bv, List<String> diffs) {
        if (diffs.size() >= 20) return;
        if (av == bv) return;
        if (av == null || bv == null) {
            diffs.add(path + ": A=" + av + " B=" + bv);
            return;
        }
        if (av.getClass() != bv.getClass()) {
            diffs.add(path + ": class A=" + av.getClass().getSimpleName()
                    + " B=" + bv.getClass().getSimpleName());
            return;
        }
        Class<?> cls = av.getClass();
        if (cls.isRecord()) {
            for (var c : cls.getRecordComponents()) {
                try {
                    Object aV = c.getAccessor().invoke(av);
                    Object bV = c.getAccessor().invoke(bv);
                    if (!fieldContentEqual(aV, bV)) {
                        collectDiffs(path + "." + c.getName(), aV, bV, diffs);
                    }
                } catch (ReflectiveOperationException ignored) {}
            }
            return;
        }
        if (cls.isArray()) {
            int len = java.lang.reflect.Array.getLength(av);
            int blen = java.lang.reflect.Array.getLength(bv);
            if (len != blen) {
                diffs.add(path + ": length A=" + len + " B=" + blen);
                return;
            }
            for (int i = 0; i < len; i++) {
                Object ai = java.lang.reflect.Array.get(av, i);
                Object bi = java.lang.reflect.Array.get(bv, i);
                if (!fieldContentEqual(ai, bi)) {
                    collectDiffs(path + "[" + i + "]", ai, bi, diffs);
                }
            }
            return;
        }
        if (av instanceof List<?> al && bv instanceof List<?> bl) {
            if (al.size() != bl.size()) {
                diffs.add(path + ": list-length A=" + al.size() + " B=" + bl.size());
                return;
            }
            for (int i = 0; i < al.size(); i++) {
                Object ai = al.get(i);
                Object bi = bl.get(i);
                if (!fieldContentEqual(ai, bi)) {
                    collectDiffs(path + "[" + i + "]", ai, bi, diffs);
                }
            }
            return;
        }
        if (av instanceof java.util.Map<?, ?> am && bv instanceof java.util.Map<?, ?> bm) {
            if (!am.keySet().equals(bm.keySet())) {
                diffs.add(path + ": map-keys A=" + am.keySet() + " B=" + bm.keySet());
                return;
            }
            for (Object key : am.keySet()) {
                Object ai = am.get(key);
                Object bi = bm.get(key);
                if (!fieldContentEqual(ai, bi)) {
                    collectDiffs(path + "." + key, ai, bi, diffs);
                }
            }
            return;
        }
        // Leaf scalar / other
        diffs.add(path + ": A=" + av + " B=" + bv);
    }

    /**
     * Deep content-equality for arbitrary records, with proper handling for
     * primitive arrays, object arrays (deep-equals), and nested records.
     */
    private static boolean recordsContentEqual(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.getClass() != b.getClass()) return false;
        Class<?> cls = a.getClass();
        if (!cls.isRecord()) return Objects.equals(a, b);
        for (var component : cls.getRecordComponents()) {
            try {
                Object av = component.getAccessor().invoke(a);
                Object bv = component.getAccessor().invoke(b);
                if (!fieldContentEqual(av, bv)) return false;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(
                        "Failed to read record component " + component.getName(), e);
            }
        }
        return true;
    }

    private static boolean fieldContentEqual(Object av, Object bv) {
        if (av == bv) return true;
        if (av == null || bv == null) return false;
        Class<?> cls = av.getClass();
        if (cls.isArray()) {
            Class<?> elem = cls.getComponentType();
            if (elem == byte.class)    return Arrays.equals((byte[]) av,    (byte[]) bv);
            if (elem == short.class)   return Arrays.equals((short[]) av,   (short[]) bv);
            if (elem == int.class)     return Arrays.equals((int[]) av,     (int[]) bv);
            if (elem == long.class)    return Arrays.equals((long[]) av,    (long[]) bv);
            if (elem == float.class)   return Arrays.equals((float[]) av,   (float[]) bv);
            if (elem == double.class)  return Arrays.equals((double[]) av,  (double[]) bv);
            if (elem == char.class)    return Arrays.equals((char[]) av,    (char[]) bv);
            if (elem == boolean.class) return Arrays.equals((boolean[]) av, (boolean[]) bv);
            Object[] aa = (Object[]) av;
            Object[] ba = (Object[]) bv;
            if (aa.length != ba.length) return false;
            for (int i = 0; i < aa.length; i++) {
                if (!fieldContentEqual(aa[i], ba[i])) return false;
            }
            return true;
        }
        if (cls.isRecord()) return recordsContentEqual(av, bv);
        if (av instanceof List<?> al && bv instanceof List<?> bl) {
            if (al.size() != bl.size()) return false;
            for (int i = 0; i < al.size(); i++) {
                if (!fieldContentEqual(al.get(i), bl.get(i))) return false;
            }
            return true;
        }
        if (av instanceof java.util.Map<?, ?> am && bv instanceof java.util.Map<?, ?> bm) {
            if (!am.keySet().equals(bm.keySet())) return false;
            for (Object key : am.keySet()) {
                if (!fieldContentEqual(am.get(key), bm.get(key))) return false;
            }
            return true;
        }
        return Objects.equals(av, bv);
    }

    private static boolean compareLevel(Object a, Object b) {
        if (!(a instanceof LevelSnapshot la) || !(b instanceof LevelSnapshot lb)) {
            return false;
        }
        // Epoch is a restore-side copy-on-write generation counter. Multiple
        // seeks can legitimately advance it beyond the original forward run
        // while the level content remains identical.
        return Arrays.equals(la.blocks(), lb.blocks())
            && Arrays.equals(la.chunks(), lb.chunks())
            && Arrays.equals(la.mapData(), lb.mapData());
    }

    /**
     * Compare ObjectManager snapshots ignoring slot list ORDER. The
     * {@code activeObjects} map backing the slots list is an IdentityHashMap
     * with unspecified iteration order, so two captures of the same logical
     * state can produce slot lists in different order. We index slots,
     * childSpawns, and dynamicObjects by their identity keys before content
     * comparison.
     */
    private static boolean compareObjectManager(Object a, Object b) {
        if (!(a instanceof ObjectManagerSnapshot oa)
                || !(b instanceof ObjectManagerSnapshot ob)) {
            return false;
        }
        if (!Arrays.equals(oa.usedSlotsBits(), ob.usedSlotsBits())) return false;
        if (oa.frameCounter() != ob.frameCounter()) return false;
        if (oa.vblaCounter() != ob.vblaCounter()) return false;
        Map<Integer, ObjectManagerSnapshot.PerSlotEntry> aBySlot = new HashMap<>();
        for (var e : oa.slots()) aBySlot.put(e.slotIndex(), e);
        Map<Integer, ObjectManagerSnapshot.PerSlotEntry> bBySlot = new HashMap<>();
        for (var e : ob.slots()) bBySlot.put(e.slotIndex(), e);
        if (!aBySlot.keySet().equals(bBySlot.keySet())) return false;
        for (int slot : aBySlot.keySet()) {
            var ea = aBySlot.get(slot);
            var eb = bBySlot.get(slot);
            if (ea.spawn() != eb.spawn()) return false;
            if (!fieldContentEqual(ea.state(), eb.state())) return false;
        }
        // ChildSpawns: identity-map by parentSpawn.
        Map<com.openggf.level.objects.ObjectSpawn, int[]> aChild = new java.util.IdentityHashMap<>();
        for (var ce : oa.childSpawns()) aChild.put(ce.parentSpawn(), ce.reservedSlots());
        Map<com.openggf.level.objects.ObjectSpawn, int[]> bChild = new java.util.IdentityHashMap<>();
        for (var ce : ob.childSpawns()) bChild.put(ce.parentSpawn(), ce.reservedSlots());
        if (aChild.size() != bChild.size()) return false;
        for (var p : aChild.keySet()) {
            int[] av = aChild.get(p);
            int[] bv = bChild.get(p);
            if (!Arrays.equals(av, bv)) return false;
        }
        return fieldContentEqual(oa.dynamicObjects(), ob.dynamicObjects())
                && fieldContentEqual(oa.placement(), ob.placement())
                && fieldContentEqual(oa.solidContactRiding(), ob.solidContactRiding());
    }
}
