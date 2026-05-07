package com.openggf.tools.rewind;

import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.rewind.GenericRewindEligibility;
import com.openggf.game.rewind.RewindDeferred;
import com.openggf.game.rewind.RewindScanSupport;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.level.objects.AbstractObjectInstance;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class RewindFieldInventoryTool {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--object-rollout-candidates".equals(args[0])) {
            List<String> candidates = objectRolloutCandidates();
            if (candidates.isEmpty()) {
                System.out.println("No default object rewind rollout candidates found.");
                return;
            }
            System.out.println("Default object rewind rollout candidates:");
            candidates.forEach(System.out::println);
            return;
        }

        List<String> unsupported = unsupportedFields();
        if (unsupported.isEmpty()) {
            System.out.println("No unsupported rewind fields found.");
            return;
        }

        System.err.println("Unsupported rewind fields:");
        unsupported.forEach(System.err::println);
        System.exit(1);
    }

    static List<String> unsupportedFields() throws Exception {
        List<String> unsupported = new ArrayList<>();
        for (Class<?> top : RewindScanSupport.discoverRuntimeOwnerClasses()) {
            for (Class<?> cls : RewindScanSupport.withNestedRuntimeOwnerClasses(top)) {
                unsupported.addAll(unsupportedFieldsForClass(cls));
            }
        }
        unsupported.sort(String::compareTo);
        return unsupported;
    }

    static List<String> objectRolloutCandidates() throws Exception {
        List<String> candidates = new ArrayList<>();
        for (Class<?> top : RewindScanSupport.discoverRuntimeOwnerClasses()) {
            for (Class<?> cls : RewindScanSupport.withNestedRuntimeOwnerClasses(top)) {
                if (!AbstractObjectInstance.class.isAssignableFrom(cls)
                        || !GenericRewindEligibility.usesDefaultObjectSubclassCapture(cls)) {
                    continue;
                }
                List<Field> fields = GenericFieldCapturer.defaultObjectSubclassCapturedFieldsForAudit(cls);
                if (!fields.isEmpty()) {
                    candidates.add(cls.getName() + " : " + fields.size() + " default fields");
                }
            }
        }
        candidates.sort(String::compareTo);
        return candidates;
    }

    static List<String> unsupportedFieldsForClass(Class<?> cls) {
        List<String> unsupported = new ArrayList<>();
        for (Field field : cls.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods)
                    || Modifier.isTransient(mods)
                    || field.isSynthetic()
                    || field.isAnnotationPresent(RewindTransient.class)
                    || field.isAnnotationPresent(RewindDeferred.class)) {
                continue;
            }
            if (!GenericFieldCapturer.isSupportedDeclaredTypeForAudit(field)) {
                unsupported.add(cls.getName() + "#" + field.getName()
                        + " : " + field.getType().getName());
            }
        }
        return unsupported;
    }

    private RewindFieldInventoryTool() {
    }
}
