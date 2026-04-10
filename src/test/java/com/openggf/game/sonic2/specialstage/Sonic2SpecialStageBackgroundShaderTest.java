package com.openggf.game.sonic2.specialstage;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Sonic2SpecialStageBackgroundShaderTest {

    private static final Path SHADER_PATH = Path.of("src/main/resources/shaders/shader_ss_background.glsl");
    private static final Path RENDERER_PATH = Path.of(
            "src/main/java/com/openggf/game/sonic2/specialstage/SpecialStageBackgroundRenderer.java");

    @Test
    public void testShaderKeepsSonic2H32SideBordersBlack() throws Exception {
        String shader = Files.readString(SHADER_PATH);
        String renderer = Files.readString(RENDERER_PATH);

        assertTrue("Shader should make the active display width configurable",
                shader.contains("uniform float ActiveDisplayWidth;"));
        assertTrue("Shader should define the left edge of the active output",
                shader.contains("float activeDisplayLeftEdge = (SCREEN_GAME_WIDTH - activeDisplayWidth) * 0.5;"));
        assertTrue("Shader should define the right edge of the active output",
                shader.contains("float activeDisplayRightEdge = activeDisplayLeftEdge + activeDisplayWidth;"));
        assertTrue("Shader should emit black for pixels outside the active viewport",
                shader.contains("gameX < activeDisplayLeftEdge || gameX >= activeDisplayRightEdge"));
        assertTrue("Shader should sample background texture from active-display-local X coordinates",
                shader.contains("float localX = gameX - activeDisplayLeftEdge;"));
        assertTrue("S2 special stage should keep the active display at H32 width",
                renderer.contains("shader.setActiveDisplayWidth((float) H32_WIDTH);"));
        assertFalse("Shader must not sample the 256px background across the full 320px viewport",
                shader.contains("float localX = gameX;"));
    }
}
