package com.openggf.game.rewind;

import com.openggf.game.rewind.snapshot.GenericObjectSnapshot;
import com.openggf.game.rewind.schema.CompactFieldCapturer;
import com.openggf.game.rewind.schema.RewindObjectStateBlob;
import com.openggf.level.Pattern;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.ObjectAnimationState;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    private static final ConcurrentMap<Class<?>, List<Field>> CAPTURABLE_FIELDS_CACHE =
            new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, List<Field>> OBJECT_SUBCLASS_VALUE_FIELDS_CACHE =
            new ConcurrentHashMap<>();

    public static GenericObjectSnapshot capture(Object target) {
        Objects.requireNonNull(target, "target");
        return captureFields(target, capturableFields(target.getClass()));
    }

    public static GenericObjectSnapshot captureObjectSubclassScalars(AbstractObjectInstance target) {
        Objects.requireNonNull(target, "target");
        return captureFields(target, objectSubclassValueFields(target.getClass()));
    }

    public static Optional<RewindObjectStateBlob> captureObjectSubclassScalarsCompact(
            AbstractObjectInstance target) {

        Objects.requireNonNull(target, "target");
        if (!CompactFieldCapturer.supportsDefaultObjectSubclassScalars(target.getClass())) {
            return Optional.empty();
        }
        return Optional.of(CompactFieldCapturer.captureDefaultObjectSubclassScalars(target));
    }

    public static void restoreObjectSubclassScalarsCompact(
            AbstractObjectInstance target,
            RewindObjectStateBlob snapshot) {

        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshot, "snapshot");
        CompactFieldCapturer.restoreDefaultObjectSubclassScalars(target, snapshot);
    }

    private static GenericObjectSnapshot captureFields(Object target, List<Field> fields) {
        List<FieldKey> keys = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Field field : fields) {
            validateFieldAccepted(field);
            keys.add(FieldKey.of(field));
            values.add(captureFieldValue(field, target));
        }
        return new GenericObjectSnapshot(target.getClass(), keys, values.toArray());
    }

    private static Object captureFieldValue(Field field, Object target) {
        Object value = readField(field, target);
        if (isStatefulListField(field)) {
            return captureStatefulList((List<?>) value);
        }
        return deepCloneValue(field.getType(), value);
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
            Object value = requiresStatefulRestore(field)
                    ? values[i]
                    : deepCloneValue(field.getType(), values[i]);
            writeField(field, target, value);
        }
    }

    private static boolean requiresStatefulRestore(Field field) {
        Class<?> type = field.getType();
        return RewindStateful.class.isAssignableFrom(type)
                || (type.isArray() && RewindStateful.class.isAssignableFrom(type.getComponentType()))
                || isStatefulListField(field);
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

    public static boolean isCapturedByDefaultObjectScalarPolicy(Field field) {
        Objects.requireNonNull(field, "field");
        return isDefaultObjectValueField(field);
    }

    public static boolean hasDefaultObjectCaptureDecision(Field field) {
        Objects.requireNonNull(field, "field");
        return isDefaultObjectValueField(field) || isKnownStructuralObjectField(field);
    }

    public static List<Field> defaultObjectSubclassCapturedFieldsForAudit(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return List.copyOf(objectSubclassValueFields(type));
    }

    public static List<Field> inspectedFieldsForAudit(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return List.copyOf(capturableFields(type));
    }

    private static List<Field> capturableFields(Class<?> type) {
        return CAPTURABLE_FIELDS_CACHE.computeIfAbsent(type, GenericFieldCapturer::buildCapturableFields);
    }

    private static List<Field> buildCapturableFields(Class<?> type) {
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
        return List.copyOf(fields);
    }

    private static List<Field> objectSubclassValueFields(Class<?> type) {
        return OBJECT_SUBCLASS_VALUE_FIELDS_CACHE.computeIfAbsent(type,
                GenericFieldCapturer::buildObjectSubclassValueFields);
    }

    private static List<Field> buildObjectSubclassValueFields(Class<?> type) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> cls = type;
                cls != null && cls != Object.class && cls != AbstractObjectInstance.class;
                cls = cls.getSuperclass()) {
            hierarchy.add(cls);
        }
        Collections.reverse(hierarchy);

        List<Field> fields = new ArrayList<>();
        for (Class<?> cls : hierarchy) {
            if (cls == AbstractBadnikInstance.class) {
                continue;
            }
            for (Field field : sortedDeclaredFields(cls)) {
                if (isDefaultObjectValueField(field)) {
                    field.setAccessible(true);
                    fields.add(field);
                }
            }
        }
        return List.copyOf(fields);
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
                || field.isAnnotationPresent(RewindTransient.class)
                || field.isAnnotationPresent(RewindDeferred.class);
    }

    private static boolean isDefaultObjectValueField(Field field) {
        int mods = field.getModifiers();
        return !Modifier.isStatic(mods)
                && !Modifier.isTransient(mods)
                && (!Modifier.isFinal(mods)
                || isDefaultObjectArrayFieldValueType(field.getType())
                || RewindStateful.class.isAssignableFrom(field.getType()))
                && !field.isSynthetic()
                && !field.isAnnotationPresent(RewindTransient.class)
                && !field.isAnnotationPresent(RewindDeferred.class)
                && (isDefaultObjectFieldValueType(field.getType()) || isStatefulListField(field));
    }

    private static boolean isKnownStructuralObjectField(Field field) {
        int mods = field.getModifiers();
        if (Modifier.isStatic(mods)
                || Modifier.isTransient(mods)
                || Modifier.isFinal(mods)
                || field.isSynthetic()
                || field.isAnnotationPresent(RewindTransient.class)
                || field.isAnnotationPresent(RewindDeferred.class)) {
            return false;
        }
        Class<?> type = field.getType();
        if (type == SpriteAnimationSet.class
                || type == ObjectSpriteSheet.class
                || type == SpriteMappingPiece.class
                || type.getName().equals("com.openggf.game.sonic3k.objects.AizIntroPaletteCycler")) {
            return true;
        }
        if (type.isArray() && type.getComponentType() == Pattern.class) {
            return true;
        }
        if (field.getName().endsWith("ForTest")) {
            return true;
        }
        return isListOf(field, SpriteMappingFrame.class);
    }

    private static boolean isListOf(Field field, Class<?> elementType) {
        if (!List.class.isAssignableFrom(field.getType())) {
            return false;
        }
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return false;
        }
        Type[] args = parameterizedType.getActualTypeArguments();
        return args.length == 1 && args[0] == elementType;
    }

    private static boolean isStatefulListField(Field field) {
        if (!List.class.isAssignableFrom(field.getType())) {
            return false;
        }
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return false;
        }
        Type[] args = parameterizedType.getActualTypeArguments();
        return args.length == 1
                && args[0] instanceof Class<?> cls
                && RewindStateful.class.isAssignableFrom(cls);
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
        if (!isSupportedValueType(field.getType(), new HashSet<>()) && !isStatefulListField(field)) {
            throw new IllegalStateException("unsupported rewind field "
                    + FieldKey.of(field)
                    + " of declared type " + field.getType().getName()
                    + "; annotate structural fields with @RewindTransient");
        }
    }

    private static boolean isRejectedFinal(Field field) {
        return Modifier.isFinal(field.getModifiers())
                && !FINAL_FIELD_CAPTURE_POLICY.contains(FieldKey.of(field))
                && !isDefaultObjectArrayFieldValueType(field.getType())
                && !RewindStateful.class.isAssignableFrom(field.getType());
    }

    private static boolean isSupportedValueType(Class<?> type, Set<Class<?>> visitingRecords) {
        if (isSmallImmutableValueType(type)
                || type == ObjectAnimationState.class
                || RewindStateful.class.isAssignableFrom(type)) {
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

    private static boolean isSmallImmutableValueType(Class<?> type) {
        return type.isPrimitive() || WRAPPER_TYPES.contains(type) || type == String.class || type.isEnum();
    }

    private static boolean isDefaultObjectFieldValueType(Class<?> type) {
        return isSmallImmutableValueType(type)
                || type == ObjectAnimationState.class
                || RewindStateful.class.isAssignableFrom(type)
                || isDefaultObjectArrayFieldValueType(type)
                || isDefaultObjectRecordFieldValueType(type);
    }

    private static boolean isDefaultObjectArrayFieldValueType(Class<?> type) {
        return type.isArray() && isSupportedArrayType(type.getComponentType(), new HashSet<>());
    }

    private static boolean isDefaultObjectRecordFieldValueType(Class<?> type) {
        return type.isRecord() && isSupportedRecordType(type, new HashSet<>());
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
        if (declaredType == ObjectAnimationState.class) {
            return ((ObjectAnimationState) value).copyForRewind();
        }
        if (declaredType == StatefulValueSnapshot.class) {
            StatefulValueSnapshot snapshot = (StatefulValueSnapshot) value;
            return new StatefulValueSnapshot(snapshot.stateType(),
                    snapshot.state() == null ? null : deepCloneValue(snapshot.stateType(), snapshot.state()));
        }
        if (RewindStateful.class.isAssignableFrom(declaredType)) {
            return captureStatefulValue((RewindStateful<?>) value);
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
        if (RewindStateful.class.isAssignableFrom(componentType)) {
            Object[] clone = new Object[length];
            for (int i = 0; i < length; i++) {
                Object element = Array.get(value, i);
                clone[i] = element == null ? null : captureStatefulValue((RewindStateful<?>) element);
            }
            return clone;
        }
        if (componentType == Object.class) {
            Object[] clone = new Object[length];
            for (int i = 0; i < length; i++) {
                Object element = Array.get(value, i);
                clone[i] = element == null ? null : deepCloneValue(element.getClass(), element);
            }
            return clone;
        }
        Object clone = Array.newInstance(componentType, length);
        for (int i = 0; i < length; i++) {
            Array.set(clone, i, deepCloneValue(componentType, Array.get(value, i)));
        }
        return clone;
    }

    private static StatefulValueSnapshot captureStatefulValue(RewindStateful<?> value) {
        Object state = value.captureRewindStateValue();
        return new StatefulValueSnapshot(state == null ? null : state.getClass(),
                state == null ? null : deepCloneValue(state.getClass(), state));
    }

    private static Object[] captureStatefulList(List<?> value) {
        if (value == null) {
            return null;
        }
        Object[] snapshots = new Object[value.size()];
        for (int i = 0; i < value.size(); i++) {
            Object element = value.get(i);
            snapshots[i] = element == null ? null : captureStatefulValue((RewindStateful<?>) element);
        }
        return snapshots;
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
            if (RewindStateful.class.isAssignableFrom(field.getType())) {
                restoreStatefulValue(field.get(target), value, FieldKey.of(field));
                return;
            }
            if (field.getType().isArray()
                    && RewindStateful.class.isAssignableFrom(field.getType().getComponentType())) {
                restoreStatefulArray(field.get(target), value, FieldKey.of(field));
                return;
            }
            if (isStatefulListField(field)) {
                restoreStatefulList(field.get(target), value, FieldKey.of(field));
                return;
            }
            if (Modifier.isFinal(field.getModifiers()) && field.getType().isArray()) {
                copyArrayIntoExistingFinalField(field, target, value);
                return;
            }
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not restore rewind field " + FieldKey.of(field), e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restoreStatefulValue(Object targetValue, Object value, FieldKey key) {
        if (targetValue == null || value == null) {
            if (targetValue == value) {
                return;
            }
            throw new IllegalStateException("Cannot restore stateful rewind field " + key
                    + " because one side is null");
        }
        StatefulValueSnapshot snapshot = (StatefulValueSnapshot) value;
        Object state = snapshot.state() == null
                ? null
                : deepCloneValue(snapshot.stateType(), snapshot.state());
        ((RewindStateful) targetValue).restoreRewindStateValue(state);
    }

    private static void restoreStatefulArray(Object targetArray, Object value, FieldKey key) {
        if (targetArray == null || value == null) {
            if (targetArray == value) {
                return;
            }
            throw new IllegalStateException("Cannot restore stateful array field " + key
                    + " because one side is null");
        }
        Object[] snapshots = (Object[]) value;
        int length = Array.getLength(targetArray);
        if (length != snapshots.length) {
            throw new IllegalStateException("Cannot restore stateful array field " + key
                    + " with changed length " + length + " -> " + snapshots.length);
        }
        for (int i = 0; i < length; i++) {
            restoreStatefulValue(Array.get(targetArray, i), snapshots[i], key);
        }
    }

    private static void restoreStatefulList(Object targetList, Object value, FieldKey key) {
        if (targetList == null || value == null) {
            if (targetList == value) {
                return;
            }
            throw new IllegalStateException("Cannot restore stateful list field " + key
                    + " because one side is null");
        }
        List<?> list = (List<?>) targetList;
        Object[] snapshots = (Object[]) value;
        if (list.size() != snapshots.length) {
            throw new IllegalStateException("Cannot restore stateful list field " + key
                    + " with changed length " + list.size() + " -> " + snapshots.length);
        }
        for (int i = 0; i < list.size(); i++) {
            restoreStatefulValue(list.get(i), snapshots[i], key);
        }
    }

    private static void copyArrayIntoExistingFinalField(Field field, Object target, Object value)
            throws IllegalAccessException {
        Object existing = field.get(target);
        if (existing == null || value == null) {
            if (existing == value) {
                return;
            }
            throw new IllegalStateException("Cannot restore final array field " + FieldKey.of(field)
                    + " because one side is null");
        }
        copyArrayContents(field.getType().getComponentType(), existing, value, FieldKey.of(field));
    }

    private static void copyArrayContents(Class<?> componentType, Object targetArray, Object sourceArray, FieldKey key) {
        int length = Array.getLength(sourceArray);
        if (Array.getLength(targetArray) != length) {
            throw new IllegalStateException("Cannot restore final array field " + key
                    + " with changed length " + Array.getLength(targetArray) + " -> " + length);
        }
        for (int i = 0; i < length; i++) {
            Object sourceElement = Array.get(sourceArray, i);
            if (componentType.isArray() && sourceElement != null) {
                Object targetElement = Array.get(targetArray, i);
                if (targetElement != null && Array.getLength(targetElement) == Array.getLength(sourceElement)) {
                    copyArrayContents(componentType.getComponentType(), targetElement, sourceElement, key);
                } else {
                    Array.set(targetArray, i, deepCloneValue(componentType, sourceElement));
                }
            } else {
                Array.set(targetArray, i, deepCloneValue(componentType, sourceElement));
            }
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

    private record StatefulValueSnapshot(Class<?> stateType, Object state) {}

    private GenericFieldCapturer() {}
}
