package com.openggf.graphics;

import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders textured quads using the RGBA texture shader.
 * Used for PNG-based rendering (master title screen, sprite font).
 */
public class TexturedQuadRenderer {

    private static final String VERT_PATH = "shaders/shader_rgba_texture.vert";
    private static final String FRAG_PATH = "shaders/shader_rgba_texture.frag";

    private ShaderProgram shader;
    private int vao;
    private int vbo;
    private int projectionLocation;
    private int textureLocation;
    private int tintLocation;

    public void init() throws IOException {
        shader = new ShaderProgram(VERT_PATH, FRAG_PATH);

        projectionLocation = glGetUniformLocation(shader.getProgramId(), "ProjectionMatrix");
        textureLocation = glGetUniformLocation(shader.getProgramId(), "Texture");
        tintLocation = glGetUniformLocation(shader.getProgramId(), "Tint");

        // Create VAO and VBO for a dynamic quad (2 triangles, 4 floats per vertex: x,y,u,v)
        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        // Allocate space for 6 vertices * 4 floats each
        glBufferData(GL_ARRAY_BUFFER, 6 * 4 * Float.BYTES, GL_DYNAMIC_DRAW);

        // Position: location 0, 2 floats
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);

        // UV: location 1, 2 floats
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);

        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Sets the projection matrix for rendering.
     */
    public void setProjectionMatrix(float[] matrixBuffer) {
        shader.use();
        glUniformMatrix4fv(projectionLocation, false, matrixBuffer);
        shader.stop();
    }

    /**
     * Draws a textured quad with white tint (no color modification).
     */
    public void drawTexture(int textureId, float x, float y, float w, float h) {
        drawTextureRegion(textureId, x, y, w, h, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f);
    }

    /**
     * Draws a textured quad with tint color.
     */
    public void drawTexture(int textureId, float x, float y, float w, float h,
                            float r, float g, float b, float a) {
        drawTextureRegion(textureId, x, y, w, h, 0f, 0f, 1f, 1f, r, g, b, a);
    }

    /**
     * Draws a sub-region of a texture with tint.
     * UV coordinates specify the region within the texture (0-1 range).
     */
    public void drawTextureRegion(int textureId, float x, float y, float w, float h,
                                  float u0, float v0, float u1, float v1,
                                  float r, float g, float b, float a) {
        // Build 6 vertices for 2 triangles (CCW winding)
        // OpenGL screen coords: y=0 at bottom
        float x0 = x;
        float y0 = y;
        float x1 = x + w;
        float y1 = y + h;

        float[] vertices = {
            // Triangle 1
            x0, y1, u0, v1,  // top-left
            x0, y0, u0, v0,  // bottom-left
            x1, y0, u1, v0,  // bottom-right
            // Triangle 2
            x0, y1, u0, v1,  // top-left
            x1, y0, u1, v0,  // bottom-right
            x1, y1, u1, v1,  // top-right
        };

        shader.use();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
        glUniform1i(textureLocation, 0);
        glUniform4f(tintLocation, r, g, b, a);

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = MemoryUtil.memAllocFloat(vertices.length);
        fb.put(vertices).flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, fb);
        MemoryUtil.memFree(fb);

        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindVertexArray(0);
        glBindTexture(GL_TEXTURE_2D, 0);
        shader.stop();
    }

    public void cleanup() {
        if (shader != null) {
            shader.cleanup();
        }
        if (vao != 0) {
            glDeleteVertexArrays(vao);
        }
        if (vbo != 0) {
            glDeleteBuffers(vbo);
        }
    }
}
