package com.openggf.game.sonic2.specialstage;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Sonic2SpecialStageBackgroundShaderTest {

    private static final Path SHADER_PATH = Path.of("src/main/resources/shaders/shader_ss_background.glsl");
    private static final Path RENDERER_PATH = Path.of(
            "src/main/java/com/openggf/game/sonic2/specialstage/SpecialStageBackgroundRenderer.java");

    @Test
    public void testShaderKeepsSonic2H32SideBordersBlack() throws Exception {
        String shader = Files.readString(SHADER_PATH);
        String renderer = Files.readString(RENDERER_PATH);

        assertTrue(shader.contains("uniform float ActiveDisplayWidth;"), "Shader should make the active display width configurable");
        assertTrue(shader.contains("float activeDisplayLeftEdge = (SCREEN_GAME_WIDTH - activeDisplayWidth) * 0.5;"), "Shader should define the left edge of the active output");
        assertTrue(shader.contains("float activeDisplayRightEdge = activeDisplayLeftEdge + activeDisplayWidth;"), "Shader should define the right edge of the active output");
        assertTrue(shader.contains("gameX < activeDisplayLeftEdge || gameX >= activeDisplayRightEdge"), "Shader should emit black for pixels outside the active viewport");
        assertTrue(shader.contains("float localX = gameX - activeDisplayLeftEdge;"), "Shader should sample background texture from active-display-local X coordinates");
        assertTrue(renderer.contains("shader.setActiveDisplayWidth((float) H32_WIDTH);"), "S2 special stage should keep the active display at H32 width");
        assertFalse(shader.contains("float localX = gameX;"), "Shader must not sample the 256px background across the full 320px viewport");
    }
}


