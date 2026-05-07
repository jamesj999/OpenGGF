package com.openggf.game.rewind.schema;

import com.openggf.game.rewind.FieldKey;
import com.openggf.game.rewind.RewindDeferred;
import com.openggf.game.rewind.RewindTransient;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class RewindSchemaRegistry {
    private static final ConcurrentMap<Class<?>, RewindClassSchema> SCHEMAS = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_SCHEMA_ID = new AtomicInteger(1);

    public static RewindClassSchema schemaFor(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return SCHEMAS.computeIfAbsent(type, RewindSchemaRegistry::buildSchema);
    }

    public static void clearForTest() {
        SCHEMAS.clear();
        NEXT_SCHEMA_ID.set(1);
        RewindPolicyRegistry.clearForTest();
    }

    private static RewindClassSchema buildSchema(Class<?> type) {
        int schemaId = NEXT_SCHEMA_ID.getAndIncrement();
        List<RewindFieldPlan> fields = plannedFields(type);
        return new RewindClassSchema(schemaId, type, fields);
    }

    private static List<RewindFieldPlan> plannedFields(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> cls = type; cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            hierarchy.add(cls);
        }
        Collections.reverse(hierarchy);

        List<RewindFieldPlan> fields = new ArrayList<>();
        for (Class<?> cls : hierarchy) {
            for (Field field : sortedDeclaredFields(cls)) {
                RewindCodec codec = RewindCodecs.codecFor(field).orElse(null);
                fields.add(new RewindFieldPlan(FieldKey.of(field), field, policyFor(field, codec), codec));
            }
        }
        return fields;
    }

    private static List<Field> sortedDeclaredFields(Class<?> cls) {
        List<Field> fields = new ArrayList<>(List.of(cls.getDeclaredFields()));
        fields.sort(Comparator
                .comparing(Field::getName)
                .thenComparing(field -> field.getType().getName()));
        return fields;
    }

    private static RewindFieldPolicy policyFor(Field field, RewindCodec codec) {
        int mods = field.getModifiers();
        if (Modifier.isStatic(mods)
                || Modifier.isTransient(mods)
                || field.isSynthetic()
                || field.isAnnotationPresent(RewindTransient.class)) {
            return RewindFieldPolicy.TRANSIENT;
        }
        if (field.isAnnotationPresent(RewindDeferred.class)) {
            return RewindFieldPolicy.DEFERRED;
        }
        RewindFieldPolicy registeredPolicy = RewindPolicyRegistry.policyFor(field).orElse(null);
        if (registeredPolicy != null) {
            if (registeredPolicy == RewindFieldPolicy.CAPTURED && codec == null) {
                return RewindFieldPolicy.UNSUPPORTED;
            }
            return registeredPolicy;
        }
        if (Modifier.isFinal(mods) && codec != null && codec.capturesFinalFields()) {
            return RewindFieldPolicy.CAPTURED;
        }
        if (Modifier.isFinal(mods) && codec != null) {
            return RewindFieldPolicy.STRUCTURAL;
        }
        if (codec != null) {
            return RewindFieldPolicy.CAPTURED;
        }
        return RewindFieldPolicy.UNSUPPORTED;
    }

    private RewindSchemaRegistry() {}
}
