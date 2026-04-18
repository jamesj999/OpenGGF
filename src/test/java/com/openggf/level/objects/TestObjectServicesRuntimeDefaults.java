package com.openggf.level.objects;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.solid.InertSolidExecutionRegistry;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestObjectServicesRuntimeDefaults {

    @Test
    void stubObjectServicesExposeInertSolidExecutionRegistryWithoutActiveRuntime() {
        StubObjectServices services = new StubObjectServices();

        SolidExecutionRegistry registry = services.solidExecutionRegistry();

        assertInstanceOf(InertSolidExecutionRegistry.class, registry);
    }

    @Test
    void runtimeBackedObjectServicesExposeRuntimeOwnedSolidExecutionRegistry() {
        TestEnvironment.resetAll();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());

        assertSame(GameServices.solidExecutionRegistry(), services.solidExecutionRegistry());
    }
}
