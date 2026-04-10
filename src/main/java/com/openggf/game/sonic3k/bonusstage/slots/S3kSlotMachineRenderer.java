package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderContext;
import com.openggf.graphics.ShaderProgram;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

public final class S3kSlotMachineRenderer {
    private static final Logger LOGGER = Logger.getLogger(S3kSlotMachineRenderer.class.getName());

    private static final int TEXTURE_WIDTH = 32;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int FACE_HEIGHT = 32;
    private static final int NUM_FACES = 8;
    private static final int TILES_PER_FACE = 16;
    private static final int BYTES_PER_TILE = 32;
    private static final float SLOT_PALETTE_LINE = 0.0f;

    private static final String SHADER_PATH = "shaders/shader_s3k_slots.glsl";

    private ShaderProgram shader;
    private int textureId;
    private boolean initialized;
    private int vaoId;
    private int vboId;
    private int vertexPosLocation = -1;

    private int locSlotFaceTexture = -1;
    private int locPalette = -1;
    private int locSlotFace0 = -1;
    private int locSlotFace1 = -1;
    private int locSlotFace2 = -1;
    private int locSlotNextFace0 = -1;
    private int locSlotNextFace1 = -1;
    private int locSlotNextFace2 = -1;
    private int locSlotOffset0 = -1;
    private int locSlotOffset1 = -1;
    private int locSlotOffset2 = -1;
    private int locScreenX = -1;
    private int locScreenY = -1;
    private int locScreenWidth = -1;
    private int locScreenHeight = -1;
    private int locPaletteLine = -1;
    private int locTotalPaletteLines = -1;
    private int locViewportWidth = -1;
    private int locViewportHeight = -1;

    public void init(Rom rom) {
        if (initialized || rom == null) {
            return;
        }
        if (shader == null) {
            try {
                shader = new ShaderProgram(ShaderProgram.FULLSCREEN_VERTEX_SHADER, SHADER_PATH);
            } catch (Exception e) {
                LOGGER.warning("Failed to create S3K slot machine shader: " + e.getMessage());
                return;
            }
        }
        textureId = createSlotTexture(rom);
        if (textureId == 0) {
            LOGGER.warning("Failed to create S3K slot options texture");
            return;
        }
        initQuadVao();
        cacheUniformLocations();
        initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public GLCommand createRenderCommand(S3kSlotMachineDisplayState state, int cameraX, int cameraY, int paletteTextureId) {
        if (!initialized || shader == null || state == null) {
            return null;
        }
        int screenX = computeDisplayScreenX(state.worldX(), cameraX);
        int screenY = computeDisplayScreenY(state.worldY(), cameraY);
        int[] faces = state.faces().clone();
        int[] nextFaces = state.nextFaces().clone();
        float[] offsets = state.offsets().clone();
        return new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) ->
                executeRender(screenX, screenY, paletteTextureId, faces, nextFaces, offsets));
    }

    static int computeDisplayScreenX(int stageAnchorX, int cameraX) {
        return (stageAnchorX - cameraX) + S3kSlotRomData.SLOT_MACHINE_DISPLAY_OFFSET_X;
    }

    static int computeDisplayScreenY(int stageAnchorY, int cameraY) {
        return (stageAnchorY - cameraY) + S3kSlotRomData.SLOT_MACHINE_DISPLAY_OFFSET_Y;
    }

    static float paletteLineForTest() {
        return SLOT_PALETTE_LINE;
    }

    public void cleanup() {
        initialized = false;
        if (textureId != 0) {
            glDeleteTextures(textureId);
            textureId = 0;
        }
        if (vboId != 0) {
            glDeleteBuffers(vboId);
            vboId = 0;
        }
        if (vaoId != 0) {
            glDeleteVertexArrays(vaoId);
            vaoId = 0;
        }
        if (shader != null) {
            shader.cleanup();
            shader = null;
        }
        resetUniformLocations();
    }

    private int createSlotTexture(Rom rom) {
        byte[] slotData;
        try {
            slotData = rom.readBytes(
                    Sonic3kConstants.ART_UNC_SLOT_OPTIONS_ADDR,
                    Sonic3kConstants.ART_UNC_SLOT_OPTIONS_SIZE
            );
        } catch (Exception e) {
            LOGGER.warning("Failed reading S3K slot options art: " + e.getMessage());
            return 0;
        }

        ByteBuffer textureData = MemoryUtil.memAlloc(TEXTURE_WIDTH * TEXTURE_HEIGHT);
        for (int face = 0; face < NUM_FACES; face++) {
            decodeFaceToTexture(slotData, face * TILES_PER_FACE * BYTES_PER_TILE, textureData, face);
        }
        textureData.position(textureData.capacity());
        textureData.flip();

        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, TEXTURE_WIDTH, TEXTURE_HEIGHT,
                0, GL_RED, GL_UNSIGNED_BYTE, textureData);
        glBindTexture(GL_TEXTURE_2D, 0);
        MemoryUtil.memFree(textureData);
        return texId;
    }

    private void decodeFaceToTexture(byte[] romData, int faceOffset, ByteBuffer textureData, int faceIndex) {
        int baseY = faceIndex * FACE_HEIGHT;
        for (int tileCol = 0; tileCol < 4; tileCol++) {
            for (int tileRow = 0; tileRow < 4; tileRow++) {
                int tileIndex = tileCol * 4 + tileRow;
                int tileOffset = faceOffset + tileIndex * BYTES_PER_TILE;
                for (int y = 0; y < 8; y++) {
                    int rowOffset = tileOffset + y * 4;
                    for (int x = 0; x < 8; x++) {
                        int byteIndex = rowOffset + (x / 2);
                        int nibble = ((x & 1) == 0)
                                ? ((romData[byteIndex] >> 4) & 0x0F)
                                : (romData[byteIndex] & 0x0F);
                        int texX = tileCol * 8 + x;
                        int texY = baseY + tileRow * 8 + y;
                        textureData.put(texY * TEXTURE_WIDTH + texX, (byte) nibble);
                    }
                }
            }
        }
    }

    private void initQuadVao() {
        if (vaoId != 0) {
            return;
        }
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);
        FloatBuffer quadBuffer = MemoryUtil.memAllocFloat(8);
        quadBuffer.put(-1f).put(-1f);
        quadBuffer.put(1f).put(-1f);
        quadBuffer.put(-1f).put(1f);
        quadBuffer.put(1f).put(1f);
        quadBuffer.flip();

        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, quadBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(quadBuffer);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private void executeRender(int screenX, int screenY, int paletteTextureId,
                               int[] faces, int[] nextFaces, float[] offsets) {
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        int viewportWidth = viewport[2];
        int viewportHeight = viewport[3];

        boolean blendWasEnabled = glIsEnabled(GL_BLEND);
        boolean depthWasEnabled = glIsEnabled(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);

        shader.use();
        if (locSlotFaceTexture < 0) {
            cacheUniformLocations();
        }

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
        glUniform1i(locSlotFaceTexture, 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, paletteTextureId);
        glUniform1i(locPalette, 1);

        glUniform1i(locSlotFace0, faces[0]);
        glUniform1i(locSlotFace1, faces[1]);
        glUniform1i(locSlotFace2, faces[2]);
        glUniform1i(locSlotNextFace0, nextFaces[0]);
        glUniform1i(locSlotNextFace1, nextFaces[1]);
        glUniform1i(locSlotNextFace2, nextFaces[2]);
        glUniform1f(locSlotOffset0, offsets[0]);
        glUniform1f(locSlotOffset1, offsets[1]);
        glUniform1f(locSlotOffset2, offsets[2]);

        glUniform1f(locScreenX, screenX);
        glUniform1f(locScreenY, screenY);
        glUniform1f(locScreenWidth, 320f);
        glUniform1f(locScreenHeight, 224f);
        glUniform1f(locPaletteLine, SLOT_PALETTE_LINE);
        glUniform1f(locTotalPaletteLines, (float) RenderContext.getTotalPaletteLines());
        glUniform1f(locViewportWidth, viewportWidth);
        glUniform1f(locViewportHeight, viewportHeight);

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        if (vertexPosLocation >= 0) {
            glEnableVertexAttribArray(vertexPosLocation);
            glVertexAttribPointer(vertexPosLocation, 2, GL_FLOAT, false, 0, 0L);
        }
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        if (vertexPosLocation >= 0) {
            glDisableVertexAttribArray(vertexPosLocation);
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        shader.stop();
        glActiveTexture(GL_TEXTURE0);

        if (!blendWasEnabled) {
            glDisable(GL_BLEND);
        }
        if (depthWasEnabled) {
            glEnable(GL_DEPTH_TEST);
        }
    }

    private void cacheUniformLocations() {
        int programId = shader.getProgramId();
        locSlotFaceTexture = glGetUniformLocation(programId, "SlotFaceTexture");
        locPalette = glGetUniformLocation(programId, "Palette");
        locSlotFace0 = glGetUniformLocation(programId, "SlotFace0");
        locSlotFace1 = glGetUniformLocation(programId, "SlotFace1");
        locSlotFace2 = glGetUniformLocation(programId, "SlotFace2");
        locSlotNextFace0 = glGetUniformLocation(programId, "SlotNextFace0");
        locSlotNextFace1 = glGetUniformLocation(programId, "SlotNextFace1");
        locSlotNextFace2 = glGetUniformLocation(programId, "SlotNextFace2");
        locSlotOffset0 = glGetUniformLocation(programId, "SlotOffset0");
        locSlotOffset1 = glGetUniformLocation(programId, "SlotOffset1");
        locSlotOffset2 = glGetUniformLocation(programId, "SlotOffset2");
        locScreenX = glGetUniformLocation(programId, "ScreenX");
        locScreenY = glGetUniformLocation(programId, "ScreenY");
        locScreenWidth = glGetUniformLocation(programId, "ScreenWidth");
        locScreenHeight = glGetUniformLocation(programId, "ScreenHeight");
        locPaletteLine = glGetUniformLocation(programId, "PaletteLine");
        locTotalPaletteLines = glGetUniformLocation(programId, "TotalPaletteLines");
        locViewportWidth = glGetUniformLocation(programId, "ViewportWidth");
        locViewportHeight = glGetUniformLocation(programId, "ViewportHeight");
        vertexPosLocation = glGetAttribLocation(programId, "position");
    }

    private void resetUniformLocations() {
        locSlotFaceTexture = -1;
        locPalette = -1;
        locSlotFace0 = -1;
        locSlotFace1 = -1;
        locSlotFace2 = -1;
        locSlotNextFace0 = -1;
        locSlotNextFace1 = -1;
        locSlotNextFace2 = -1;
        locSlotOffset0 = -1;
        locSlotOffset1 = -1;
        locSlotOffset2 = -1;
        locScreenX = -1;
        locScreenY = -1;
        locScreenWidth = -1;
        locScreenHeight = -1;
        locPaletteLine = -1;
        locTotalPaletteLines = -1;
        locViewportWidth = -1;
        locViewportHeight = -1;
        vertexPosLocation = -1;
    }
}
