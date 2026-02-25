package com.openggf.tests;

import org.junit.Test;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

import static org.junit.Assert.*;

/**
 * TODO #29 -- SCALE configuration property documentation.
 *
 * <p>The {@code SonicConfiguration.SCALE} enum value has a TODO comment:
 * "Scale used with BufferedImage TODO: Work out what this does"
 * (SonicConfiguration.java:36).
 *
 * <p>Investigation findings:
 * <ul>
 *   <li>SCALE is defined in {@code SonicConfiguration.java:38} as an enum constant</li>
 *   <li>Default value is 1.0, set in {@code SonicConfigurationService.java:158}:
 *       {@code putDefault(SonicConfiguration.SCALE, 1.0)}</li>
 *   <li>SCALE is NOT used anywhere in the rendering pipeline or game logic.
 *       A search for {@code getDouble(.*SCALE} and similar patterns shows it is
 *       only set as a default and read in tests.</li>
 *   <li>The "BufferedImage" reference in the TODO comment suggests this was
 *       originally intended for a Java2D (AWT) rendering path that has since
 *       been replaced by the OpenGL/GLFW rendering system.</li>
 *   <li>The SCALE property appears to be a legacy artifact that is no longer
 *       functional. It defaults to 1.0 and has no effect on rendering.</li>
 * </ul>
 *
 * <p>Since SCALE does not affect rendering output, this test documents the
 * current state and verifies the default value is sane.
 */
public class TestTodo29_ScaleDocumentation {

    @Test
    public void testScaleEnumExists() {
        // Verify the SCALE enum constant exists in SonicConfiguration.
        SonicConfiguration scale = SonicConfiguration.SCALE;
        assertNotNull("SCALE should exist in SonicConfiguration", scale);
        assertEquals("SCALE enum name should match", "SCALE", scale.name());
    }

    @Test
    public void testScaleDefaultIsOne() {
        // The default SCALE value is 1.0 (SonicConfigurationService.java:158).
        // A scale of 1.0 means "no scaling" which is the expected no-op value
        // for a legacy property.
        SonicConfigurationService svc = SonicConfigurationService.getInstance();
        double scale = svc.getDouble(SonicConfiguration.SCALE);
        assertEquals("SCALE default should be 1.0 (no scaling)", 1.0, scale, 0.001);
    }

    @Test
    public void testScaleIsLegacyProperty() {
        // Document that SCALE is a legacy artifact from a removed BufferedImage
        // rendering path. The current engine uses OpenGL via GLFW
        // (GraphicsManager, BatchedPatternRenderer, etc.) and does not read SCALE.
        //
        // Evidence:
        // 1. No Java source file calls getDouble/getFloat/getInt with SCALE
        //    (other than the default setter and this test)
        // 2. The TODO comment references "BufferedImage" which is java.awt,
        //    not the current GLFW/OpenGL rendering system
        // 3. The rendering pipeline (GraphicsManager.initHeadless(),
        //    BatchedPatternRenderer, TilemapGpuRenderer) uses SCREEN_WIDTH,
        //    SCREEN_HEIGHT, SCREEN_WIDTH_PIXELS, SCREEN_HEIGHT_PIXELS for sizing,
        //    never SCALE
        //
        // Recommendation: Remove the SCALE enum constant and its default value
        // since it has no functional purpose. The TODO is answered: it does nothing.
        SonicConfigurationService svc = SonicConfigurationService.getInstance();
        // Verify it can be read without error (it exists but is unused)
        double scale = svc.getDouble(SonicConfiguration.SCALE);
        assertTrue("SCALE should be a positive number", scale > 0);
    }
}
