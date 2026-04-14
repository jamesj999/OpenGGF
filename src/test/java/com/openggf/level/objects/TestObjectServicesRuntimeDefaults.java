package com.openggf.level.objects;

import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.game.zone.ZoneRuntimeState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestObjectServicesRuntimeDefaults {

    @Test
    void stubObjectServicesExposeInertRuntimeRegistryWithoutActiveRuntime() {
        StubObjectServices services = new StubObjectServices();

        ZoneRuntimeRegistry registry = assertDoesNotThrow(services::zoneRuntimeRegistry);
        ZoneRuntimeState state = assertDoesNotThrow(services::zoneRuntimeState);

        assertNotNull(registry);
        assertNotNull(state);
    }

    @Test
    void testObjectServicesExposeInertRuntimeRegistryWithoutActiveRuntime() {
        TestObjectServices services = new TestObjectServices();

        ZoneRuntimeRegistry registry = assertDoesNotThrow(services::zoneRuntimeRegistry);
        ZoneRuntimeState state = assertDoesNotThrow(services::zoneRuntimeState);

        assertNotNull(registry);
        assertNotNull(state);
    }
}
