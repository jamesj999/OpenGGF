package com.openggf.tests;

import com.openggf.game.GameRuntime;
import com.openggf.game.sonic3k.runtime.AizZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.HczZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kZoneRuntimeRegistrationHeadless {

    @Test
    void loadingAizInstallsAizRuntimeState() throws Exception {
        SharedLevel level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder().withSharedLevel(level).build();
            GameRuntime runtime = fixture.runtime();
            assertNotNull(runtime, "Runtime should be active after fixture build");
            ZoneRuntimeRegistry registry = runtime.getZoneRuntimeRegistry();
            assertTrue(registry.currentAs(AizZoneRuntimeState.class).isPresent(),
                    "AIZ zone runtime state should be installed after loading AIZ");
            assertEquals(0, registry.current().zoneIndex());
            assertEquals(0, registry.current().actIndex());
            assertEquals("s3k", registry.current().gameId());
        } finally {
            level.dispose();
        }
    }

    @Test
    void loadingHczInstallsHczRuntimeState() throws Exception {
        SharedLevel level = SharedLevel.load(SonicGame.SONIC_3K, 1, 0);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder().withSharedLevel(level).build();
            GameRuntime runtime = fixture.runtime();
            assertNotNull(runtime, "Runtime should be active after fixture build");
            ZoneRuntimeRegistry registry = runtime.getZoneRuntimeRegistry();
            assertTrue(registry.currentAs(HczZoneRuntimeState.class).isPresent(),
                    "HCZ zone runtime state should be installed after loading HCZ");
            assertEquals(1, registry.current().zoneIndex());
            assertEquals(0, registry.current().actIndex());
            assertEquals("s3k", registry.current().gameId());
        } finally {
            level.dispose();
        }
    }
}
