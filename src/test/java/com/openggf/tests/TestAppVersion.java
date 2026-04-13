package com.openggf.tests;

import com.openggf.version.AppVersion;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.*;

public class TestAppVersion {

    @Test
    public void testVersionResourceExistsOnClasspath() throws Exception {
        try (InputStream input = AppVersion.class.getResourceAsStream("/version.properties")) {
            assertNotNull("version.properties should be present on the classpath", input);
        }
    }

    @Test
    public void testResolvedVersionIsNotBlank() {
        assertNotNull("App version should resolve", AppVersion.get());
        assertFalse("App version should not be blank", AppVersion.get().trim().isEmpty());
    }

    @Test
    public void testMavenBuildDoesNotUseDevFallback() {
        assertNotEquals("Maven test runs should resolve the filtered project version", "dev", AppVersion.get());
    }
}
