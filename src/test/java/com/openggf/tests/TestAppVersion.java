package com.openggf.tests;

import com.openggf.version.AppVersion;
import org.junit.Test;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

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

    @Test
    public void testBlankVersionContentFallsBackToDev() throws Exception {
        assertEquals("dev", invokeLoadVersion(""));
    }

    @Test
    public void testMalformedVersionContentFallsBackToDev() throws Exception {
        assertEquals("dev", invokeLoadVersion("app.version=bad\\u12"));
    }

    private static String invokeLoadVersion(String content) throws Exception {
        Method method = AppVersion.class.getDeclaredMethod("loadVersion", InputStream.class);
        method.setAccessible(true);
        try (ByteArrayInputStream input = new ByteArrayInputStream(content.getBytes(StandardCharsets.ISO_8859_1))) {
            return (String) method.invoke(null, input);
        }
    }
}
