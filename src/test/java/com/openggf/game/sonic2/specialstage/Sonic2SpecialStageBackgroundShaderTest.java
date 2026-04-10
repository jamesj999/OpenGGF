package com.openggf.game.sonic2.specialstage;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Sonic2SpecialStageBackgroundShaderTest {

    private static final Path SHADER_PATH = Path.of("src/main/resources/shaders/shader_ss_background.glsl");

    @Test
    public void testShaderKeepsSonic2H32SideBordersBlack() throws Exception {
        String shader = Files.readString(SHADER_PATH);

        assertTrue("Shader should define the left edge of Sonic 2 H32 output",
                shader.contains("H32_LEFT_EDGE"));
        assertTrue("Shader should define the right edge of Sonic 2 H32 output",
                shader.contains("H32_RIGHT_EDGE"));
        assertTrue("Shader should emit black for pixels outside the active H32 viewport",
                shader.contains("gameX < H32_LEFT_EDGE || gameX >= H32_RIGHT_EDGE"));
        assertTrue("Shader should sample background texture from H32-local X coordinates",
                shader.contains("float localX = gameX - H32_LEFT_EDGE;"));
        assertFalse("Shader must not sample the 256px background across the full 320px viewport",
                shader.contains("float localX = gameX;"));
    }
}
