package com.openggf.game.rewind;

import com.openggf.game.rewind.snapshot.GenericObjectSnapshot;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class GenericFieldCapturer {
    private static final Set<Class<?>> WRAPPER_TYPES = Set.of(
            Boolean.class,
            Byte.class,
            Character.class,
            Short.class,
            Integer.class,
            Long.class,
            Float.class,
            Double.class,
            Void.class
    );

    private static final Set<FieldKey> FINAL_FIELD_CAPTURE_POLICY = Set.of();

    public static GenericObjectSnapshot capture(Object target) {
        Objects.requireNonNull(target, "target");
        List<FieldKey> keys = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Field field : capturableFields(target.getClass())) {
            validateFieldAccepted(field);
            keys.add(FieldKey.of(field));
            values.add(deepCloneValue(field.getType(), readField(field, target)));
        }
        return new GenericObjectSnapshot(target.getClass(), keys, values.toArray());
    }

    public static void restore(Object target, GenericObjectSnapshot snapshot) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.type() != target.getClass()) {
            throw new IllegalArgumentException("Snapshot type " + snapshot.type().getName()
                    + " cannot restore into " + target.getClass().getName());
        }

        Object[] values = snapshot.values();
        for (int i = 0; i < snapshot.keys().size(); i++) {
            FieldKey key = snapshot.keys().get(i);
            Field field = findField(target.getClass(), key);
            if (field == null) {
                continue;
            }
            validateFieldAccepted(field);
            writeField(field, target, deepCloneValue(field.getType(), values[i]));
        }
    }

    public static boolean isSupportedDeclaredTypeForAudit(Field field) {
        Objects.requireNonNull(field, "field");
        if (isSkipped(field)) {
            return true;
        }
        if (isRejectedFinal(field)) {
            return false;
        }
        return isSupportedValueType(field.getType(), new HashSet<>());
    }

    public static List<Field> inspectedFieldsForAudit(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return List.copyOf(capturableFields(type));
    }

    private static List<Field> capturableFields(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> cls = type; cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            hierarchy.add(cls);
        }
        Collections.reverse(hierarchy);

        List<Field> fields = new ArrayList<>();
        for (Class<?> cls : hierarchy) {
            for (Field field : sortedDeclaredFields(cls)) {
                if (!isSkipped(field)) {
                    fields.add(field);
                }
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

    private static boolean isSkipped(Field field) {
        int mods = field.getModifiers();
        return Modifier.isStatic(mods)
                || Modifier.isTransient(mods)
                || field.isAnnotationPresent(RewindTransient.class);
    }

    private static void validateFieldAccepted(Field field) {
        if (isSkipped(field)) {
            return;
        }
        if (isRejectedFinal(field)) {
            throw new IllegalStateException("Unsupported final rewind field "
                    + FieldKey.of(field)
                    + "; annotate with @RewindTransient or add an exact final-field policy entry");
        }
        if (!isSupportedValueType(field.getType(), new HashSet<>())) {
            throw new IllegalStateException("unsupported rewind field "
                    + FieldKey.of(field)
                    + " of declared type " + field.getType().getName()
                    + "; annotate structural fields with @RewindTransient");
        }
    }

    private static boolean isRejectedFinal(Field field) {
        return Modifier.isFinal(field.getModifiers()) && !FINAL_FIELD_CAPTURE_POLICY.contains(FieldKey.of(field));
    }

    private static boolean isSupportedValueType(Class<?> type, Set<Class<?>> visitingRecords) {
        if (type.isPrimitive() || WRAPPER_TYPES.contains(type) || type == String.class || type.isEnum()) {
            return true;
        }
        if (type == BitSet.class) {
            return true;
        }
        if (type.isArray()) {
            return isSupportedArrayType(type.getComponentType(), visitingRecords);
        }
        if (Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
                || java.util.Queue.class.isAssignableFrom(type)
                || java.util.Deque.class.isAssignableFrom(type)) {
            return false;
        }
        if (type.isRecord()) {
            return isSupportedRecordType(type, visitingRecords);
        }
        return false;
    }

    private static boolean isSupportedArrayType(Class<?> componentType, Set<Class<?>> visitingRecords) {
        if (componentType.isArray()) {
            return isSupportedArrayType(componentType.getComponentType(), visitingRecords);
        }
        return isSupportedValueType(componentType, visitingRecords);
    }

    private static boolean isSupportedRecordType(Class<?> type, Set<Class<?>> visitingRecords) {
        if (!visitingRecords.add(type)) {
            return true;
        }
        try {
            for (RecordComponent component : type.getRecordComponents()) {
                if (!isSupportedValueType(component.getType(), visitingRecords)) {
                    return false;
                }
            }
            return true;
        } finally {
            visitingRecords.remove(type);
        }
    }

    private static Object deepCloneValue(Class<?> declaredType, Object value) {
        if (value == null) {
            return null;
        }
        if (declaredType.isPrimitive()
                || WRAPPER_TYPES.contains(declaredType)
                || declaredType == String.class
                || declaredType.isEnum()) {
            return value;
        }
        if (declaredType == BitSet.class) {
            return ((BitSet) value).clone();
        }
        if (declaredType.isArray()) {
            return deepCloneArray(declaredType.getComponentType(), value);
        }
        if (declaredType.isRecord()) {
            return deepCloneRecord(declaredType, value);
        }
        throw new IllegalStateException("Cannot clone unsupported rewind value type " + declaredType.getName());
    }

    public static Object deepCopySnapshotValue(Object value) {
        return value == null ? null : deepCloneValue(value.getClass(), value);
    }

    private static Object deepCloneArray(Class<?> componentType, Object value) {
        int length = Array.getLength(value);
        Object clone = Array.newInstance(componentType, length);
        for (int i = 0; i < length; i++) {
            Array.set(clone, i, deepCloneValue(componentType, Array.get(value, i)));
        }
        return clone;
    }

    private static Object deepCloneRecord(Class<?> declaredType, Object value) {
        try {
            RecordComponent[] components = declaredType.getRecordComponents();
            Class<?>[] parameterTypes = new Class<?>[components.length];
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                parameterTypes[i] = components[i].getType();
                components[i].getAccessor().setAccessible(true);
                args[i] = deepCloneValue(components[i].getType(), components[i].getAccessor().invoke(value));
            }
            Constructor<?> constructor = declaredType.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not clone record value of type " + declaredType.getName(), e);
        }
    }

    private static Object readField(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not read rewind field " + FieldKey.of(field), e);
        }
    }

    private static void writeField(Field field, Object target, Object value) {
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not restore rewind field " + FieldKey.of(field), e);
        }
    }

    private static Field findField(Class<?> targetType, FieldKey key) {
        for (Class<?> cls = targetType; cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            if (!cls.getName().equals(key.declaringClassName())) {
                continue;
            }
            try {
                return cls.getDeclaredField(key.fieldName());
            } catch (NoSuchFieldException e) {
                return null;
            }
        }
        return null;
    }

    private GenericFieldCapturer() {}
}
