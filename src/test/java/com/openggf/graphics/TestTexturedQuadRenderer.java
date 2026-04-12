package com.openggf.graphics;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TestTexturedQuadRenderer {

    @Test
    void writeQuadVertices_writesExpectedTriangleDataWithoutIntermediateArrays() {
        FloatBuffer buffer = MemoryUtil.memAllocFloat(24);
        try {
            TexturedQuadRenderer.writeQuadVertices(buffer,
                    10f, 20f, 30f, 40f,
                    0.1f, 0.2f, 0.3f, 0.4f);

            float[] actual = new float[24];
            buffer.get(actual);

            assertArrayEquals(new float[] {
                    10f, 60f, 0.1f, 0.4f,
                    10f, 20f, 0.1f, 0.2f,
                    40f, 20f, 0.3f, 0.2f,
                    10f, 60f, 0.1f, 0.4f,
                    40f, 20f, 0.3f, 0.2f,
                    40f, 60f, 0.3f, 0.4f
            }, actual);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }
}
