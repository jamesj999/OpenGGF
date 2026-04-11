package com.openggf.level.objects;

import java.util.function.Supplier;

/**
 * Helper for non-{@link ObjectManager} call sites that still need object-style
 * construction context during {@code new X(...)}.
 */
public final class ObjectConstructionContext {

    private ObjectConstructionContext() {
    }

    public static <T> T construct(ObjectServices services, Supplier<T> factory) {
        setConstructionContext(services);
        try {
            return factory.get();
        } finally {
            clearConstructionContext();
        }
    }

    public static void setConstructionContext(ObjectServices services) {
        AbstractObjectInstance.setConstructionContext(services);
    }

    public static void clearConstructionContext() {
        AbstractObjectInstance.clearConstructionContext();
    }
}
