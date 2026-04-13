package com.openggf.version;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppVersion {
    private static final String RESOURCE_PATH = "/version.properties";
    private static final String PROPERTY_NAME = "app.version";
    private static final String DEFAULT_VERSION = "dev";
    private static final String VERSION = loadVersion();

    private AppVersion() {
    }

    public static String get() {
        return VERSION;
    }

    private static String loadVersion() {
        return loadVersion(AppVersion.class.getResourceAsStream(RESOURCE_PATH));
    }

    private static String loadVersion(InputStream input) {
        try (InputStream stream = input) {
            if (stream == null) {
                return DEFAULT_VERSION;
            }
            Properties properties = new Properties();
            properties.load(stream);
            String version = properties.getProperty(PROPERTY_NAME);
            if (version == null || version.trim().isEmpty()) {
                return DEFAULT_VERSION;
            }
            return version.trim();
        } catch (IOException | IllegalArgumentException e) {
            return DEFAULT_VERSION;
        }
    }
}
