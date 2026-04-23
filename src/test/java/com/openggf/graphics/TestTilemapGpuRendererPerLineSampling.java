package com.openggf.graphics;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestTilemapGpuRendererPerLineSampling {

    @Test
    void perLineTilePassRowsMapBackToVisibleScanlinesAfterVOffset() throws Exception {
        assertEquals(0.0f, invokeResolvePerLineScrollSampleRow(0.0f, 5.0f, 224.0f));
        assertEquals(0.0f, invokeResolvePerLineScrollSampleRow(5.0f, 5.0f, 224.0f));
        assertEquals(1.0f, invokeResolvePerLineScrollSampleRow(6.0f, 5.0f, 224.0f));
        assertEquals(209.0f, invokeResolvePerLineScrollSampleRow(224.0f, 15.0f, 224.0f));
        assertEquals(223.0f, invokeResolvePerLineScrollSampleRow(238.0f, 15.0f, 224.0f));
    }

    private static float invokeResolvePerLineScrollSampleRow(float pixelYFromTop,
                                                             float sampleYOffsetPx,
                                                             float screenHeight) throws Exception {
        Method method = TilemapGpuRenderer.class.getDeclaredMethod(
                "resolvePerLineScrollSampleRow",
                float.class,
                float.class,
                float.class);
        method.setAccessible(true);
        return (float) method.invoke(null, pixelYFromTop, sampleYOffsetPx, screenHeight);
    }
}
