package com.openggf.game.sonic1.specialstage;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class Sonic1SpecialStageBackgroundShaderTest {

    private static final Path SHADER_PATH = Path.of("src/main/resources/shaders/shader_ss_background.glsl");
    private static final Path RENDERER_PATH = Path.of(
            "src/main/java/com/openggf/game/sonic1/specialstage/Sonic1SpecialStageBackgroundRenderer.java");

    @Test
    public void testSonic1BackgroundShaderUsesFullWidthActiveDisplay() throws Exception {
        String shader = Files.readString(SHADER_PATH);
        String renderer = Files.readString(RENDERER_PATH);

        assertTrue("Shared special stage shader should make the active display width configurable",
                shader.contains("uniform float ActiveDisplayWidth;"));
        assertTrue("Shader should center the configured active display width inside the 320px output",
                shader.contains("(SCREEN_GAME_WIDTH - activeDisplayWidth) * 0.5"));
        assertTrue("S1 special stage should keep the full 320px active display",
                renderer.contains("shader.setActiveDisplayWidth((float) SCREEN_WIDTH);"));
    }
}
