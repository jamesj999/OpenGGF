package com.openggf.tests;

import org.junit.Test;

/**
 * Previously guarded against calling AbstractObjectInstance.services() inside constructors.
 * <p>
 * As of the CONSTRUCTION_CONTEXT ThreadLocal introduction, services() is now safe to call
 * during construction when the object is created through ObjectManager (which sets the
 * ThreadLocal) or when tests explicitly set it via reflection. The original source-scanning
 * assertion is no longer applicable.
 *
 * @see com.openggf.level.objects.AbstractObjectInstance#services()
 * @see com.openggf.level.objects.TestObjectServicesConstructionContext
 */
public class TestNoServicesInObjectConstructors {

    @Test
    public void constructorServicesCallsSafeViaThreadLocal() {
        // Constructor-time services() calls are now supported through the
        // CONSTRUCTION_CONTEXT ThreadLocal set by ObjectManager before factory
        // invocation. See TestObjectServicesConstructionContext for coverage
        // of that mechanism.
    }
}
