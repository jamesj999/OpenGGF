package com.openggf.game.rewind;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

class TestRewindTransientGuard {
    @Test
    void engineReferenceFieldsAreAnnotatedOrJavaTransient() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> top : RewindScanSupport.discoverRuntimeOwnerClasses()) {
            for (Class<?> cls : RewindScanSupport.withNestedRuntimeOwnerClasses(top)) {
                collectEngineReferenceViolations(cls, violations);
            }
        }
        if (!violations.isEmpty()) {
            fail("Engine-ref fields need classification: @RewindTransient(reason=...) only for structural/runtime refs, "
                    + "Java transient for non-state caches, or explicit snapshot/codec/defer decision for gameplay refs:\n"
                    + String.join("\n", violations));
        }
    }

    private static void collectEngineReferenceViolations(Class<?> cls, List<String> violations) {
        for (Field field : cls.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods)) {
                continue;
            }
            if (Modifier.isTransient(mods)) {
                continue;
            }
            if (field.isSynthetic()) {
                continue;
            }
            if (field.isAnnotationPresent(RewindTransient.class)
                    || field.isAnnotationPresent(RewindDeferred.class)) {
                continue;
            }
            if (!EngineRefTypes.isEngineRef(field.getType())) {
                continue;
            }
            violations.add(cls.getName() + "#" + field.getName()
                    + " : " + field.getType().getName());
        }
    }
}
