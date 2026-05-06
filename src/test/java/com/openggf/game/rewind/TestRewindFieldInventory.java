package com.openggf.game.rewind;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

@Disabled("Manual inventory generator; enabled audit is TestRewindFieldAudit")
class TestRewindFieldInventory {
    @Test
    void allRuntimeOwnerFieldsAreSupportedOrCategorized() throws Exception {
        List<String> unsupported = new ArrayList<>();
        for (Class<?> top : RewindScanSupport.discoverRuntimeOwnerClasses()) {
            for (Class<?> cls : RewindScanSupport.withNestedRuntimeOwnerClasses(top)) {
                collectUnsupportedFields(cls, unsupported);
            }
        }
        if (!unsupported.isEmpty()) {
            fail("Unsupported fields inventory:\n" + String.join("\n", unsupported));
        }
    }

    private static void collectUnsupportedFields(Class<?> cls, List<String> unsupported) {
        for (Field field : cls.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods)) {
                continue;
            }
            if (Modifier.isTransient(mods)) {
                continue;
            }
            if (field.isAnnotationPresent(RewindTransient.class)) {
                continue;
            }
            if (!GenericFieldCapturer.isSupportedDeclaredTypeForAudit(field)) {
                unsupported.add(cls.getName() + "#" + field.getName()
                        + " : " + field.getType().getName());
            }
        }
    }
}
