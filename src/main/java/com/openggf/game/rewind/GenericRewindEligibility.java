package com.openggf.game.rewind;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.PerObjectRewindSnapshot;

import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GenericRewindEligibility {
    private static final Set<Class<?>> PRODUCTION_ELIGIBLE = Set.of();
    private static final Set<Class<?>> TEST_OR_MIGRATION_ELIGIBLE = ConcurrentHashMap.newKeySet();

    public static boolean isEligible(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return PRODUCTION_ELIGIBLE.contains(type) || TEST_OR_MIGRATION_ELIGIBLE.contains(type);
    }

    public static boolean usesDefaultObjectSubclassCapture(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return AbstractObjectInstance.class.isAssignableFrom(type)
                && type != AbstractObjectInstance.class
                && !Modifier.isAbstract(type.getModifiers())
                && !declaresConcreteObjectRewindOverride(type);
    }

    public static Set<Class<?>> eligibleClassesForAudit() {
        Set<Class<?>> eligible = ConcurrentHashMap.newKeySet();
        eligible.addAll(PRODUCTION_ELIGIBLE);
        eligible.addAll(TEST_OR_MIGRATION_ELIGIBLE);
        return Set.copyOf(eligible);
    }

    public static void registerForTestOrMigration(Class<?> type) {
        TEST_OR_MIGRATION_ELIGIBLE.add(Objects.requireNonNull(type, "type"));
    }

    public static void clearForTest() {
        TEST_OR_MIGRATION_ELIGIBLE.clear();
    }

    public static boolean declaresConcreteObjectRewindOverride(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return declaresConcreteMethod(type, "captureRewindState")
                || declaresConcreteMethod(type, "restoreRewindState", PerObjectRewindSnapshot.class);
    }

    private static boolean declaresConcreteMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            var method = type.getDeclaredMethod(name, parameterTypes);
            return method.getDeclaringClass() == type
                    && !Modifier.isAbstract(method.getModifiers())
                    && !method.isSynthetic()
                    && !method.isBridge();
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private GenericRewindEligibility() {
    }
}
