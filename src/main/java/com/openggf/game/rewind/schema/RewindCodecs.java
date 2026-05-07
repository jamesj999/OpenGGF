package com.openggf.game.rewind.schema;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.ObjectRefKind;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.rewind.identity.RewindIdentityTable;
import com.openggf.level.objects.ObjectAnimationState;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.PlatformBobHelper;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.util.AnimationTimer;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class RewindCodecs {
    private static final Set<Class<?>> WRAPPER_TYPES = Set.of(
            Boolean.class,
            Byte.class,
            Character.class,
            Short.class,
            Integer.class,
            Long.class,
            Float.class,
            Double.class
    );

    public static Optional<RewindCodec> codecFor(Field field) {
        Objects.requireNonNull(field, "field");
        Optional<RewindCodec> collectionCodec = collectionCodecFor(field);
        return collectionCodec.isPresent() ? collectionCodec : codecFor(field.getType());
    }

    public static Optional<RewindCodec> codecFor(Class<?> type) {
        Objects.requireNonNull(type, "type");
        if (type.isPrimitive() || WRAPPER_TYPES.contains(type)) {
            return Optional.of(new ScalarCodec(type));
        }
        if (type == String.class) {
            return Optional.of(new OpaqueCodec());
        }
        if (type.isEnum()) {
            return Optional.of(new EnumCodec(type));
        }
        if (isPlayerReferenceType(type)) {
            return Optional.of(new PlayerReferenceCodec(type));
        }
        if (isObjectReferenceType(type)) {
            return Optional.of(new ObjectReferenceCodec(type));
        }
        if (type.isArray() && supportedArrayComponent(type.getComponentType())) {
            return Optional.of(new ArrayCodec(type.getComponentType()));
        }
        if (type == BitSet.class) {
            return Optional.of(new BitSetCodec());
        }
        if (type == SubpixelMotion.State.class) {
            return Optional.of(new SubpixelMotionStateCodec());
        }
        if (type == ObjectAnimationState.class) {
            return Optional.of(new ObjectAnimationStateCodec());
        }
        if (type == PlatformBobHelper.class) {
            return Optional.of(new PlatformBobHelperCodec());
        }
        if (type == AnimationTimer.class) {
            return Optional.of(new AnimationTimerCodec());
        }
        if (type.isRecord() && supportedRecord(type)) {
            return Optional.of(new RecordCodec(type));
        }
        return Optional.empty();
    }

    public static boolean supports(Class<?> type) {
        return codecFor(type).isPresent();
    }

    private static boolean supportedArrayComponent(Class<?> componentType) {
        return componentType.isPrimitive() || componentType.isEnum();
    }

    private static boolean supportedRecord(Class<?> type) {
        for (RecordComponent component : type.getRecordComponents()) {
            if (!isOpaqueRecordComponent(component.getType())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOpaqueRecordComponent(Class<?> type) {
        return type.isPrimitive()
                || WRAPPER_TYPES.contains(type)
                || type == String.class
                || type.isEnum();
    }

    private static Optional<RewindCodec> collectionCodecFor(Field field) {
        Class<?> type = field.getType();
        if (!Collection.class.isAssignableFrom(type) && !Map.class.isAssignableFrom(type)) {
            return Optional.empty();
        }
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return Optional.empty();
        }
        Type[] args = parameterizedType.getActualTypeArguments();
        if (Collection.class.isAssignableFrom(type)) {
            if (!List.class.isAssignableFrom(type) && !Set.class.isAssignableFrom(type)) {
                return Optional.empty();
            }
            if (args.length != 1
                    || !(args[0] instanceof Class<?> elementType)
                    || !isSupportedCollectionElementType(elementType)) {
                return Optional.empty();
            }
            return Optional.of(new CollectionCodec(type, elementType));
        }
        if (Map.class.isAssignableFrom(type)) {
            if (args.length != 2
                    || !(args[0] instanceof Class<?> keyType)
                    || !(args[1] instanceof Class<?> valueType)
                    || !isSupportedCollectionElementType(keyType)
                    || !isSupportedCollectionElementType(valueType)) {
                return Optional.empty();
            }
            return Optional.of(new MapCodec(type, keyType, valueType));
        }
        return Optional.empty();
    }

    private static boolean isCollectionValueType(Class<?> type) {
        return WRAPPER_TYPES.contains(type) || type == String.class || type.isEnum();
    }

    private static boolean isSupportedCollectionElementType(Class<?> type) {
        return isCollectionValueType(type) || isPlayerReferenceType(type) || isObjectReferenceType(type);
    }

    private static boolean isPlayerReferenceType(Class<?> type) {
        return type == PlayableEntity.class || AbstractPlayableSprite.class.isAssignableFrom(type);
    }

    private static boolean isObjectReferenceType(Class<?> type) {
        return ObjectInstance.class.isAssignableFrom(type);
    }

    private static Object get(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read rewind field " + field, e);
        }
    }

    private static void set(Field field, Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot write rewind field " + field, e);
        }
    }

    private static int getInt(Field field, Object target) {
        try {
            return field.getInt(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read rewind field " + field, e);
        }
    }

    private static void setInt(Field field, Object target, int value) {
        try {
            field.setInt(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot write rewind field " + field, e);
        }
    }

    private static Object requireExistingValue(Field field, Object target) {
        Object value = get(field, target);
        if (value == null) {
            throw new IllegalStateException("Cannot restore in-place rewind field " + field
                    + " because the target value is null.");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> mutableCollectionFor(Class<?> declaredType) {
        if (Set.class.isAssignableFrom(declaredType)) {
            return new LinkedHashSet<>();
        }
        return new ArrayList<>();
    }

    private static boolean isFinal(Field field) {
        return Modifier.isFinal(field.getModifiers());
    }

    private static final class ScalarCodec implements RewindCodec {
        private final Class<?> type;

        private ScalarCodec(Class<?> type) {
            this.type = type;
        }

        @Override
        public void capture(Field field, Object target, RewindStateBuffer scalarData, List<Object> opaqueValues) {
            Object value = get(field, target);
            if (!type.isPrimitive()) {
                scalarData.writeBoolean(value != null);
                if (value == null) {
                    return;
                }
            }
            writeScalar(type, value, scalarData);
        }

        @Override
        public void restore(Field field, Object target, RewindStateBuffer.Reader scalarData, Object[] opaqueValues, OpaqueIndex opaqueIndex) {
            if (!type.isPrimitive() && !scalarData.readBoolean()) {
                set(field, target, null);
                return;
            }
            set(field, target, readScalar(type, scalarData));
        }
    }

    private static final class EnumCodec implements RewindCodec {
        private final Object[] constants;

        private EnumCodec(Class<?> type) {
            this.constants = type.getEnumConstants();
        }

        @Override
        public void capture(Field field, Object target, RewindStateBuffer scalarData, List<Object> opaqueValues) {
            Enum<?> value = (Enum<?>) get(field, target);
            scalarData.writeInt(value == null ? -1 : value.ordinal());
        }

        @Override
        public void restore(Field field, Object target, RewindStateBuffer.Reader scalarData, Object[] opaqueValues, OpaqueIndex opaqueIndex) {
            int ordinal = scalarData.readInt();
            set(field, target, ordinal < 0 ? null : constants[ordinal]);
        }
    }

    private static final class ArrayCodec implements RewindCodec {
        private final Class<?> componentType;

        private ArrayCodec(Class<?> componentType) {
            this.componentType = componentType;
        }

        @Override
        public void capture(Field field, Object target, RewindStateBuffer scalarData, List<Object> opaqueValues) {
            Object array = get(field, target);
            scalarData.writeInt(array == null ? -1 : Array.getLength(array));
            if (array == null) {
                return;
            }
            int length = Array.getLength(array);
            for (int i = 0; i < length; i++) {
                if (componentType.isEnum()) {
                    Enum<?> value = (Enum<?>) Array.get(array, i);
                    scalarData.writeInt(value == null ? -1 : value.ordinal());
                } else {
                    writeScalar(componentType, Array.get(array, i), scalarData);
                }
            }
        }

        @Override
        public void restore(Field field, Object target, RewindStateBuffer.Reader scalarData, Object[] opaqueValues, OpaqueIndex opaqueIndex) {
            int length = scalarData.readInt();
            if (length < 0) {
                set(field, target, null);
                return;
            }
            Object array = Array.newInstance(componentType, length);
            Object[] enumConstants = componentType.isEnum() ? componentType.getEnumConstants() : null;
            for (int i = 0; i < length; i++) {
                Object value;
                if (componentType.isEnum()) {
                    int ordinal = scalarData.readInt();
                    value = ordinal < 0 ? null : enumConstants[ordinal];
                } else {
                    value = readScalar(componentType, scalarData);
                }
                Array.set(array, i, value);
            }
            set(field, target, array);
        }
    }

    private static final class BitSetCodec implements RewindCodec {
        @Override
        public void capture(Field field, Object target, RewindStateBuffer scalarData, List<Object> opaqueValues) {
            BitSet value = (BitSet) get(field, target);
            if (value == null) {
                scalarData.writeInt(-1);
                return;
            }
            byte[] bytes = value.toByteArray();
            scalarData.writeInt(bytes.length);
            scalarData.writeBytes(bytes);
        }

        @Override
        public void restore(Field field, Object target, RewindStateBuffer.Reader scalarData, Object[] opaqueValues, OpaqueIndex opaqueIndex) {
            int length = scalarData.readInt();
            set(field, target, length < 0 ? null : BitSet.valueOf(scalarData.readBytes(length)));
        }
    }

    private static final class PlayerReferenceCodec implements RewindCodec {
        private final Class<?> declaredType;

        private PlayerReferenceCodec(Class<?> declaredType) {
            this.declaredType = declaredType;
        }

        @Override
        public void capture(Field field, Object target, RewindStateBuffer scalarData, List<Object> opaqueValues) {
            capture(field, target, scalarData, opaqueValues, RewindCaptureContext.none());
        }

        @Override
        public void capture(
                Field field,
                Object target,
                RewindStateBuffer scalarData,
                List<Object> opaqueValues,
                RewindCaptureContext context) {

            PlayerRefId id = encodePlayerRef((PlayableEntity) get(field, target), context);
            scalarData.writeInt(id.encoded());
        }

        @Override
        public void restore(
                Field field,
                Object target,
                RewindStateBuffer.Reader scalarData,
                Object[] opaqueValues,
                OpaqueIndex opaqueIndex) {

            restore(field, target, scalarData, opaqueValues, opaqueIndex, RewindCaptureContext.none());
        }

        @Override
        public void restore(
                Field field,
                Object target,
                RewindStateBuffer.Reader scalarData,
                Object[] opaqueValues,
                OpaqueIndex opaqueIndex,
                RewindCaptureContext context) {

            set(field, target, resolvePlayerRef(new PlayerRefId(scalarData.readInt()), context, declaredType));
        }
    }

    private static final class ObjectReferenceCodec implements RewindCodec {
        private final Class<?> declaredType;

        private ObjectReferenceCodec(Class<?> declaredType) {
            this.declaredType = declaredType;
        }

        @Override
        public void capture(Field field, Object target, RewindStateBuffer scalarData, List<Object> opaqueValues) {
            capture(field, target, scalarData, opaqueValues, RewindCaptureContext.none());
        }

        @Override
        public void capture(
                Field field,
                Object target,
                RewindStateBuffer scalarData,
                List<Object> opaqueValues,
                RewindCaptureContext context) {

            writeObjectRef((ObjectInstance) get(field, target), scalarData, context);
        }

        @Override
        public void restore(
                Field field,
                Object target,
                RewindStateBuffer.Reader scalarData,
                Object[] opaqueValues,
                OpaqueIndex opaqueIndex) {

            restore(field, target, scalarData, opaqueValues, opaqueIndex, RewindCaptureContext.none());
        }

        @Override
        public void restore(
                Field field,
                Object target,
                RewindStateBuffer.Reader scalarData,
                Object[] opaqueValues,
                OpaqueIndex opaqueIndex,
                RewindCaptureContext context) {

            set(field, target, readObjectRef(scalarData, context, declaredType));
        }
    }

    private static final class CollectionCodec implements RewindCodec {
        private final Class<?> declaredType;
        private final Class<?> elementType;

        private CollectionCodec(Class<?> declaredType, Class<?> elementType) {
            this.declaredType = declaredType;
            this.elementType = elementType;
        }

        @Override
        public void capture(
                Field field,
                Object target,
                RewindStateBuffer scalarData,
                List<Object> opaqueValues,
                RewindCaptureContext context) {

            Collection<?> collection = (Collection<?>) get(field, target);
            scalarData.writeInt(collection == null ? -1 : collection.size());
            if (collection == null) {
                return;
            }
            for (Object element : collection) {
                writeCollectionValue(elementType, element, scalarData, opaqueValues, context);
            }
        }

        @Override
        public void capture(Field field, Object target, RewindStateBuffer scalarData, List<Object> opaqueValues) {
            capture(field, target, scalarData, opaqueValues, RewindCaptureContext.none());
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(
                Field field,
                Object target,
                RewindStateBuffer.Reader scalarData,
                Object[] opaqueValues,
                OpaqueIndex opaqueIndex,
                RewindCaptureContext context) {

            int size = scalarData.readInt();
            if (size < 0) {
                set(field, target, null);
                return;
            }
            Collection<Object> restored;
            if (isFinal(field)) {
                restored = (Collection<Object>) requireExistingValue(field, target);
                restored.clear();
            } else {
                restored = mutableCollectionFor(declaredType);
                set(field, target, restored);
            }
            for (int i = 0; i < size; i++) {
                restored.add(readCollectionValue(elementType, scalarData, opaqueValues, opaqueIndex, context));
            }
        }

        @Override
        public void restore(Field field, Object target, RewindStateBuffer.Reader scalarData, Object[] opaqueValues, OpaqueIndex opaqueIndex) {
            restore(field, target, scalarData, opaqueValues, opaqueIndex, RewindCaptureContext.none());
        }

        @Override
        public boolean capturesFinalFields() {
            return true;
        }

        @Override
        public boolean requiresExistingTargetValue() {
            return true;
        }
    }

    private static final class MapCodec implements RewindCodec {
        private final Class<?> keyType;
        private final Class<?> valueType;

        private MapCodec(Class<?> declaredType, Class<?> keyType, Class<?> valueType) {
            this.keyType = keyType;
            this.valueType = valueType;
        }

        @Override
        public void capture(
                Field field,
                Object target,
                RewindStateBuffer scalarData,
                List<Object> opaqueValues,
                RewindCaptureContext context) {

            Map<?, ?> map = (Map<?, ?>) get(field, target);
            scalarData.writeInt(map == null ? -1 : map.size());
            if (map == null) {
                return;
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                writeCollectionValue(keyType, entry.getKey(), scalarData, opaqueValues, context);
                writeCollectionValue(valueType, entry.getValue(), scalarData, opaqueValues, context);
            }
        }

        @Override
        public void capture(Field field, Object target, RewindStateBuffer scalarData, List<Object> opaqueValues) {
            capture(field, target, scalarData, opaqueValues, RewindCaptureContext.none());
        }

        @Override
        @SuppressWarnings("unchecked")
        public void restore(
                Field field,
                Object target,
                RewindStateBuffer.Reader scalarData,
                Object[] opaqueValues,
                OpaqueIndex opaqueIndex,
                RewindCaptureContext context) {

            int size = scalarData.readInt();
            if (size < 0) {
                set(field, target, null);
                return;
            }
            Map<Object, Object> restored;
            if (isFinal(field)) {
                restored = (Map<Object, Object>) requireExistingValue(field, target);
                restored.clear();
            } else {
                restored = new LinkedHashMap<>();
                set(field, target, restored);
            }
            for (int i = 0; i < size; i++) {
                Object key = readCollectionValue(keyType, scalarData, opaqueValues, opaqueIndex, context);
                Object value = readCollectionValue(valueType, scalarData, opaqueValues, opaqueIndex, context);
                restored.put(key, value);
            }
        }

        @Override
        public void restore(Field field, Object target, RewindStateBuffer.Reader scalarData, Object[] opaqueValues, OpaqueIndex opaqueIndex) {
            restore(field, target, scalarData, opaqueValues, opaqueIndex, RewindCaptureContext.none());
        }

        @Override
        public boolean capturesFinalFields() {
            return true;
        }

        @Override
        public boolean requiresExistingTargetValue() {
            return true;
        }
    }

    private static final class SubpixelMotionStateCodec implements RewindCodec {
        @Override
        public void capture(Field field, Object target, RewindStateBuffer scalarData, List<Object> opaqueValues) {
            SubpixelMotion.State state = (SubpixelMotion.State) get(field, target);
            scalarData.writeBoolean(state != null);
            if (state == null) {
                return;
            }
            scalarData.writeInt(state.x);
            scalarData.writeInt(state.y);
            scalarData.writeInt(state.xSub);
            scalarData.writeInt(state.ySub);
            scalarData.writeInt(state.xVel);
            scalarData.writeInt(state.yVel);
        }

        @Override
        public void restore(Field field, Object target, RewindStateBuffer.Reader scalarData, Object[] opaqueValues, OpaqueIndex opaqueIndex) {
            if (!scalarData.readBoolean()) {
                set(field, target, null);
                return;
            }
            SubpixelMotion.State state = (SubpixelMotion.State) requireExistingValue(field, target);
            state.x = scalarData.readInt();
            state.y = scalarData.readInt();
            state.xSub = scalarData.readInt();
            state.ySub = scalarData.readInt();
            state.xVel = scalarData.readInt();
            state.yVel = scalarData.readInt();
        }

        @Override
        public boolean capturesFinalFields() {
            return true;
        }

        @Override
        public boolean requiresExistingTargetValue() {
            return true;
        }
    }

    private static final class ObjectAnimationStateCodec implements RewindCodec {
        private static final Field ANIM_ID = intField(ObjectAnimationState.class, "animId");
        private static final Field LAST_ANIM_ID = intField(ObjectAnimationState.class, "lastAnimId");
        private static final Field FRAME_INDEX = intField(ObjectAnimationState.class, "frameIndex");
        private static final Field FRAME_TICK = intField(ObjectAnimationState.class, "frameTick");
        private static final Field MAPPING_FRAME = intField(ObjectAnimationState.class, "mappingFrame");

        @Override
        public void capture(Field field, Object target, RewindStateBuffer scalarData, List<Object> opaqueValues) {
            ObjectAnimationState state = (ObjectAnimationState) get(field, target);
            scalarData.writeBoolean(state != null);
            if (state == null) {
                return;
            }
            scalarData.writeInt(getInt(ANIM_ID, state));
            scalarData.writeInt(getInt(LAST_ANIM_ID, state));
            scalarData.writeInt(getInt(FRAME_INDEX, state));
            scalarData.writeInt(getInt(FRAME_TICK, state));
            scalarData.writeInt(getInt(MAPPING_FRAME, state));
        }

        @Override
        public void restore(Field field, Object target, RewindStateBuffer.Reader scalarData, Object[] opaqueValues, OpaqueIndex opaqueIndex) {
            if (!scalarData.readBoolean()) {
                set(field, target, null);
                return;
            }
            ObjectAnimationState state = (ObjectAnimationState) requireExistingValue(field, target);
            setInt(ANIM_ID, state, scalarData.readInt());
            setInt(LAST_ANIM_ID, state, scalarData.readInt());
            setInt(FRAME_INDEX, state, scalarData.readInt());
            setInt(FRAME_TICK, state, scalarData.readInt());
            setInt(MAPPING_FRAME, state, scalarData.readInt());
        }

        @Override
        public boolean capturesFinalFields() {
            return true;
        }

        @Override
        public boolean requiresExistingTargetValue() {
            return true;
        }
    }

    private static final class PlatformBobHelperCodec implements RewindCodec {
        @Override
        public void capture(Field field, Object target, RewindStateBuffer scalarData, List<Object> opaqueValues) {
            PlatformBobHelper helper = (PlatformBobHelper) get(field, target);
            scalarData.writeBoolean(helper != null);
            if (helper != null) {
                scalarData.writeInt(helper.getAngle());
            }
        }

        @Override
        public void restore(Field field, Object target, RewindStateBuffer.Reader scalarData, Object[] opaqueValues, OpaqueIndex opaqueIndex) {
            if (!scalarData.readBoolean()) {
                set(field, target, null);
                return;
            }
            PlatformBobHelper helper = (PlatformBobHelper) requireExistingValue(field, target);
            helper.restoreAngle(scalarData.readInt());
        }

        @Override
        public boolean capturesFinalFields() {
            return true;
        }
    }

    private static final class AnimationTimerCodec implements RewindCodec {
        private static final Field TIMER = intField(AnimationTimer.class, "timer");
        private static final Field FRAME = intField(AnimationTimer.class, "frame");

        @Override
        public void capture(Field field, Object target, RewindStateBuffer scalarData, List<Object> opaqueValues) {
            AnimationTimer timer = (AnimationTimer) get(field, target);
            scalarData.writeBoolean(timer != null);
            if (timer == null) {
                return;
            }
            scalarData.writeInt(getInt(TIMER, timer));
            scalarData.writeInt(getInt(FRAME, timer));
        }

        @Override
        public void restore(Field field, Object target, RewindStateBuffer.Reader scalarData, Object[] opaqueValues, OpaqueIndex opaqueIndex) {
            if (!scalarData.readBoolean()) {
                set(field, target, null);
                return;
            }
            AnimationTimer timer = (AnimationTimer) requireExistingValue(field, target);
            setInt(TIMER, timer, scalarData.readInt());
            setInt(FRAME, timer, scalarData.readInt());
        }

        @Override
        public boolean capturesFinalFields() {
            return true;
        }
    }

    private static final class RecordCodec implements RewindCodec {
        private final Class<?> type;
        private final RecordComponent[] components;
        private final Method[] accessors;
        private final Constructor<?> constructor;

        private RecordCodec(Class<?> type) {
            this.type = type;
            this.components = type.getRecordComponents();
            this.accessors = new Method[components.length];
            Class<?>[] componentTypes = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                accessors[i] = components[i].getAccessor();
                accessors[i].setAccessible(true);
                componentTypes[i] = components[i].getType();
            }
            try {
                this.constructor = type.getDeclaredConstructor(componentTypes);
                this.constructor.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Cannot find canonical record constructor for rewind type "
                        + type.getName(), e);
            }
        }

        @Override
        public void capture(Field field, Object target, RewindStateBuffer scalarData, List<Object> opaqueValues) {
            Object record = get(field, target);
            scalarData.writeBoolean(record != null);
            if (record == null) {
                return;
            }
            for (int i = 0; i < components.length; i++) {
                writeRecordComponent(components[i].getType(), readComponent(i, record), scalarData);
            }
        }

        @Override
        public void restore(Field field, Object target, RewindStateBuffer.Reader scalarData, Object[] opaqueValues, OpaqueIndex opaqueIndex) {
            if (!scalarData.readBoolean()) {
                set(field, target, null);
                return;
            }
            Object[] values = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                values[i] = readRecordComponent(components[i].getType(), scalarData);
            }
            try {
                set(field, target, constructor.newInstance(values));
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Cannot restore rewind record value for field " + field
                        + " using " + type.getName(), e);
            }
        }

        private Object readComponent(int index, Object record) {
            try {
                return accessors[index].invoke(record);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Cannot read rewind record component "
                        + type.getName() + "." + components[index].getName(), e);
            }
        }
    }

    private static Field intField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final class OpaqueCodec implements RewindCodec {
        @Override
        public void capture(Field field, Object target, RewindStateBuffer scalarData, List<Object> opaqueValues) {
            opaqueValues.add(get(field, target));
        }

        @Override
        public void restore(Field field, Object target, RewindStateBuffer.Reader scalarData, Object[] opaqueValues, OpaqueIndex opaqueIndex) {
            set(field, target, opaqueIndex.next(opaqueValues));
        }
    }

    private static void writeCollectionValue(
            Class<?> type,
            Object value,
            RewindStateBuffer scalarData,
            List<Object> opaqueValues,
            RewindCaptureContext context) {

        scalarData.writeBoolean(value != null);
        if (value == null) {
            return;
        }
        if (type == String.class) {
            opaqueValues.add(value);
        } else if (type.isEnum()) {
            scalarData.writeInt(((Enum<?>) value).ordinal());
        } else if (isPlayerReferenceType(type)) {
            scalarData.writeInt(encodePlayerRef((PlayableEntity) value, context).encoded());
        } else if (isObjectReferenceType(type)) {
            writeObjectRefBody(encodeObjectRef((ObjectInstance) value, context), scalarData);
        } else {
            writeScalar(type, value, scalarData);
        }
    }

    private static Object readCollectionValue(
            Class<?> type,
            RewindStateBuffer.Reader scalarData,
            Object[] opaqueValues,
            RewindCodec.OpaqueIndex opaqueIndex,
            RewindCaptureContext context) {

        if (!scalarData.readBoolean()) {
            return null;
        }
        if (type == String.class) {
            return opaqueIndex.next(opaqueValues);
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[scalarData.readInt()];
        }
        if (isPlayerReferenceType(type)) {
            return resolvePlayerRef(new PlayerRefId(scalarData.readInt()), context, type);
        }
        if (isObjectReferenceType(type)) {
            return resolveObjectRef(readObjectRefBody(scalarData), context, type);
        }
        return readScalar(type, scalarData);
    }

    private static void writeRecordComponent(Class<?> type, Object value, RewindStateBuffer scalarData) {
        if (!type.isPrimitive()) {
            scalarData.writeBoolean(value != null);
            if (value == null) {
                return;
            }
        }
        if (type == String.class) {
            writeString(value, scalarData);
        } else if (type.isEnum()) {
            scalarData.writeInt(((Enum<?>) value).ordinal());
        } else {
            writeScalar(type, value, scalarData);
        }
    }

    private static Object readRecordComponent(Class<?> type, RewindStateBuffer.Reader scalarData) {
        if (!type.isPrimitive() && !scalarData.readBoolean()) {
            return null;
        }
        if (type == String.class) {
            return readString(scalarData);
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[scalarData.readInt()];
        }
        return readScalar(type, scalarData);
    }

    private static void writeString(Object value, RewindStateBuffer scalarData) {
        byte[] bytes = ((String) value).getBytes(StandardCharsets.UTF_8);
        scalarData.writeInt(bytes.length);
        scalarData.writeBytes(bytes);
    }

    private static String readString(RewindStateBuffer.Reader scalarData) {
        int length = scalarData.readInt();
        return new String(scalarData.readBytes(length), StandardCharsets.UTF_8);
    }

    private static PlayerRefId encodePlayerRef(PlayableEntity player, RewindCaptureContext context) {
        if (player == null) {
            return PlayerRefId.nullRef();
        }
        RewindIdentityTable identityTable = context.requireIdentityTable();
        PlayerRefId id = identityTable.encodePlayer(player);
        if (id == null) {
            throw new IllegalStateException("RewindIdentityTable has no registered id for player reference " + player + ".");
        }
        return id;
    }

    private static Object resolvePlayerRef(PlayerRefId id, RewindCaptureContext context, Class<?> declaredType) {
        PlayableEntity player = context.requireIdentityTable().resolvePlayer(id, true);
        if (player != null && !declaredType.isInstance(player)) {
            throw new IllegalStateException("Resolved player reference " + id + " to "
                    + player.getClass().getName() + " but field requires " + declaredType.getName() + ".");
        }
        return player;
    }

    private static void writeObjectRef(ObjectInstance object, RewindStateBuffer scalarData, RewindCaptureContext context) {
        scalarData.writeBoolean(object != null);
        if (object != null) {
            writeObjectRefBody(encodeObjectRef(object, context), scalarData);
        }
    }

    private static Object readObjectRef(
            RewindStateBuffer.Reader scalarData,
            RewindCaptureContext context,
            Class<?> declaredType) {

        if (!scalarData.readBoolean()) {
            return null;
        }
        return resolveObjectRef(readObjectRefBody(scalarData), context, declaredType);
    }

    private static ObjectRefId encodeObjectRef(ObjectInstance object, RewindCaptureContext context) {
        RewindIdentityTable identityTable = context.requireIdentityTable();
        ObjectRefId id = identityTable.encodeObject(object);
        if (id == null) {
            throw new IllegalStateException("RewindIdentityTable has no registered id for object reference " + object + ".");
        }
        return id;
    }

    private static void writeObjectRefBody(ObjectRefId id, RewindStateBuffer scalarData) {
        scalarData.writeInt(id.kind().ordinal());
        scalarData.writeInt(id.slotIndex());
        scalarData.writeInt(id.generation());
        scalarData.writeInt(id.spawnId());
        scalarData.writeInt(id.dynamicId());
    }

    private static ObjectRefId readObjectRefBody(RewindStateBuffer.Reader scalarData) {
        ObjectRefKind kind = ObjectRefKind.values()[scalarData.readInt()];
        int slotIndex = scalarData.readInt();
        int generation = scalarData.readInt();
        int spawnId = scalarData.readInt();
        int dynamicId = scalarData.readInt();
        return new ObjectRefId(slotIndex, generation, spawnId, dynamicId, kind);
    }

    private static Object resolveObjectRef(ObjectRefId id, RewindCaptureContext context, Class<?> declaredType) {
        ObjectInstance object = context.requireIdentityTable().resolveObject(id, true);
        if (object != null && !declaredType.isInstance(object)) {
            throw new IllegalStateException("Resolved object reference " + id + " to "
                    + object.getClass().getName() + " but field requires " + declaredType.getName() + ".");
        }
        return object;
    }

    private static void writeScalar(Class<?> type, Object value, RewindStateBuffer scalarData) {
        if (type == boolean.class || type == Boolean.class) {
            scalarData.writeBoolean((Boolean) value);
        } else if (type == byte.class || type == Byte.class) {
            scalarData.writeByte((Byte) value);
        } else if (type == char.class || type == Character.class) {
            scalarData.writeShort((Character) value);
        } else if (type == short.class || type == Short.class) {
            scalarData.writeShort((Short) value);
        } else if (type == int.class || type == Integer.class) {
            scalarData.writeInt((Integer) value);
        } else if (type == long.class || type == Long.class) {
            scalarData.writeLong((Long) value);
        } else if (type == float.class || type == Float.class) {
            scalarData.writeFloat((Float) value);
        } else if (type == double.class || type == Double.class) {
            scalarData.writeDouble((Double) value);
        } else {
            throw new IllegalArgumentException("Unsupported scalar rewind type: " + type.getName());
        }
    }

    private static Object readScalar(Class<?> type, RewindStateBuffer.Reader scalarData) {
        if (type == boolean.class || type == Boolean.class) {
            return scalarData.readBoolean();
        } else if (type == byte.class || type == Byte.class) {
            return scalarData.readByte();
        } else if (type == char.class || type == Character.class) {
            return (char) (scalarData.readShort() & 0xFFFF);
        } else if (type == short.class || type == Short.class) {
            return scalarData.readShort();
        } else if (type == int.class || type == Integer.class) {
            return scalarData.readInt();
        } else if (type == long.class || type == Long.class) {
            return scalarData.readLong();
        } else if (type == float.class || type == Float.class) {
            return scalarData.readFloat();
        } else if (type == double.class || type == Double.class) {
            return scalarData.readDouble();
        }
        throw new IllegalArgumentException("Unsupported scalar rewind type: " + type.getName());
    }

    private RewindCodecs() {}
}
