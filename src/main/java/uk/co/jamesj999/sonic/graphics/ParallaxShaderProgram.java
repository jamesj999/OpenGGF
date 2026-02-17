package uk.co.jamesj999.sonic.graphics;

import java.io.IOException;

import static org.lwjgl.opengl.GL20.*;

/**
 * Shader program specialized for parallax background rendering.
 * Extends the base shader functionality with uniforms for per-scanline
 * scrolling.
 */
public class ParallaxShaderProgram {

    private int programId;
    private boolean uniformsCached = false;

    // Texture sampler locations
    private int backgroundTextureLocation = -1;
    private int hScrollTextureLocation = -1;
    private int paletteLocation = -1;

    // Scroll and dimension uniforms
    private int screenHeightLocation = -1;
    private int screenWidthLocation = -1;
    private int vScrollBGLocation = -1;
    private int bgTextureWidthLocation = -1;
    private int bgTextureHeightLocation = -1;
    private int scrollMidpointLocation = -1;
    private int extraBufferLocation = -1;
    private int vScrollLocation = -1;
    private int viewportOffsetXLocation = -1;
    private int viewportOffsetYLocation = -1;
    private int backdropColorLocation = -1;
    private int fillTransparentWithBackdropLocation = -1;
    private int fboAllocationWidthLocation = -1;
    private int noHScrollLocation = -1;

    private static final String FULLSCREEN_VERTEX_SHADER = "shaders/shader_fullscreen.vert";

    /**
     * Creates and links the parallax shader program.
     *
     * @param fragmentShaderPath Path to the fragment shader file
     * @throws IOException if shader loading fails
     */
    public ParallaxShaderProgram(String fragmentShaderPath) throws IOException {
        int vertexShaderId = ShaderLoader.loadShader(FULLSCREEN_VERTEX_SHADER, GL_VERTEX_SHADER);
        int fragmentShaderId = ShaderLoader.loadShader(fragmentShaderPath, GL_FRAGMENT_SHADER);

        programId = glCreateProgram();
        glAttachShader(programId, vertexShaderId);
        glAttachShader(programId, fragmentShaderId);
        glLinkProgram(programId);

        // Check for linking errors
        int linked = glGetProgrami(programId, GL_LINK_STATUS);
        if (linked == 0) {
            String log = glGetProgramInfoLog(programId);
            System.err.println("Parallax shader linking failed:\n" + log);
        }

        // Detach and delete shader objects - they're no longer needed after linking
        glDetachShader(programId, vertexShaderId);
        glDetachShader(programId, fragmentShaderId);
        glDeleteShader(vertexShaderId);
        glDeleteShader(fragmentShaderId);
    }

    /**
     * Cache all uniform locations for efficient access.
     */
    public void cacheUniformLocations() {
        if (uniformsCached) {
            return;
        }

        // Texture samplers
        backgroundTextureLocation = glGetUniformLocation(programId, "BackgroundTexture");
        hScrollTextureLocation = glGetUniformLocation(programId, "HScrollTexture");
        paletteLocation = glGetUniformLocation(programId, "Palette");

        // Scroll and dimensions
        screenHeightLocation = glGetUniformLocation(programId, "ScreenHeight");
        screenWidthLocation = glGetUniformLocation(programId, "ScreenWidth");
        vScrollBGLocation = glGetUniformLocation(programId, "VScrollBG");
        bgTextureWidthLocation = glGetUniformLocation(programId, "BGTextureWidth");
        bgTextureHeightLocation = glGetUniformLocation(programId, "BGTextureHeight");
        scrollMidpointLocation = glGetUniformLocation(programId, "ScrollMidpoint");
        extraBufferLocation = glGetUniformLocation(programId, "ExtraBuffer");
        vScrollLocation = glGetUniformLocation(programId, "VScroll");
        viewportOffsetXLocation = glGetUniformLocation(programId, "ViewportOffsetX");
        viewportOffsetYLocation = glGetUniformLocation(programId, "ViewportOffsetY");
        backdropColorLocation = glGetUniformLocation(programId, "BackdropColor");
        fillTransparentWithBackdropLocation = glGetUniformLocation(programId, "FillTransparentWithBackdrop");
        fboAllocationWidthLocation = glGetUniformLocation(programId, "FBOAllocationWidth");
        noHScrollLocation = glGetUniformLocation(programId, "NoHScroll");

        uniformsCached = true;
    }

    public void use() {
        glUseProgram(programId);
    }

    public void stop() {
        glUseProgram(0);
    }

    public int getProgramId() {
        return programId;
    }

    // Texture unit setters
    public void setBackgroundTexture(int textureUnit) {
        if (backgroundTextureLocation >= 0) {
            glUniform1i(backgroundTextureLocation, textureUnit);
        }
    }

    public void setHScrollTexture(int textureUnit) {
        if (hScrollTextureLocation >= 0) {
            glUniform1i(hScrollTextureLocation, textureUnit);
        }
    }

    public void setPalette(int textureUnit) {
        if (paletteLocation >= 0) {
            glUniform1i(paletteLocation, textureUnit);
        }
    }

    // Dimension and scroll setters
    public void setScreenDimensions(float width, float height) {
        if (screenWidthLocation >= 0) {
            glUniform1f(screenWidthLocation, width);
        }
        if (screenHeightLocation >= 0) {
            glUniform1f(screenHeightLocation, height);
        }
    }

    public void setVScrollBG(float vScroll) {
        if (vScrollBGLocation >= 0) {
            glUniform1f(vScrollBGLocation, vScroll);
        }
    }

    public void setBGTextureDimensions(float width, float height) {
        if (bgTextureWidthLocation >= 0) {
            glUniform1f(bgTextureWidthLocation, width);
        }
        if (bgTextureHeightLocation >= 0) {
            glUniform1f(bgTextureHeightLocation, height);
        }
    }

    public void setScrollMidpoint(int midpoint) {
        if (scrollMidpointLocation >= 0) {
            glUniform1f(scrollMidpointLocation, (float) midpoint);
        }
    }

    public void setExtraBuffer(int buffer) {
        if (extraBufferLocation >= 0) {
            glUniform1f(extraBufferLocation, (float) buffer);
        }
    }

    public void setVScroll(float vScroll) {
        if (vScrollLocation >= 0) {
            glUniform1f(vScrollLocation, vScroll);
        }
    }

    public void setViewportOffset(float offsetX, float offsetY) {
        if (viewportOffsetXLocation >= 0) {
            glUniform1f(viewportOffsetXLocation, offsetX);
        }
        if (viewportOffsetYLocation >= 0) {
            glUniform1f(viewportOffsetYLocation, offsetY);
        }
    }

    public void setBackdropColor(float r, float g, float b) {
        if (backdropColorLocation >= 0) {
            glUniform3f(backdropColorLocation, r, g, b);
        }
    }

    public void setFillTransparentWithBackdrop(boolean fill) {
        if (fillTransparentWithBackdropLocation >= 0) {
            glUniform1f(fillTransparentWithBackdropLocation, fill ? 1.0f : 0.0f);
        }
    }

    public void setFBOAllocationWidth(float width) {
        if (fboAllocationWidthLocation >= 0) {
            glUniform1f(fboAllocationWidthLocation, width);
        }
    }

    public void setNoHScroll(boolean noHScroll) {
        if (noHScrollLocation >= 0) {
            glUniform1i(noHScrollLocation, noHScroll ? 1 : 0);
        }
    }

    public void cleanup() {
        if (programId != 0) {
            glDeleteProgram(programId);
            programId = 0;
        }
    }
}
