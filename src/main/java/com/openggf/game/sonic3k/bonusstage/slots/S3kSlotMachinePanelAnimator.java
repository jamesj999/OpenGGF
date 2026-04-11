package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Streams the slot reel windows into the level pattern atlas so the panel uses
 * the same palette and tile path as the surrounding foreground art.
 */
public final class S3kSlotMachinePanelAnimator {
    private static final Logger LOGGER = Logger.getLogger(S3kSlotMachinePanelAnimator.class.getName());

    private static final int FACE_WIDTH = 32;
    private static final int FACE_HEIGHT = 32;
    private static final int NUM_FACES = 8;
    private static final int TILES_PER_FACE = 16;
    private static final int BYTES_PER_TILE = 32;
    private static final int[] DEST_PATTERN_BASES = {0x200, 0x210, 0x220};

    private final byte[][] facePixels = new byte[NUM_FACES][FACE_WIDTH * FACE_HEIGHT];
    private final int[] lastFaces = {-1, -1, -1};
    private final int[] lastNextFaces = {-1, -1, -1};
    private final int[] lastOffsetPixels = {-1, -1, -1};
    private boolean initialized;

    public void init(Rom rom) {
        if (initialized || rom == null) {
            return;
        }
        try {
            byte[] slotData = rom.readBytes(
                    Sonic3kConstants.ART_UNC_SLOT_OPTIONS_ADDR,
                    Sonic3kConstants.ART_UNC_SLOT_OPTIONS_SIZE
            );
            decodeFaces(slotData, facePixels);
            initialized = true;
        } catch (Exception e) {
            LOGGER.warning("Failed to initialize S3K slot panel animator: " + e.getMessage());
        }
    }

    public void cleanup() {
        initialized = false;
        Arrays.fill(lastFaces, -1);
        Arrays.fill(lastNextFaces, -1);
        Arrays.fill(lastOffsetPixels, -1);
        for (byte[] face : facePixels) {
            Arrays.fill(face, (byte) 0);
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void syncPanelPatterns(S3kSlotMachineDisplayState displayState) {
        if (!initialized || displayState == null) {
            return;
        }
        int[] faces = displayState.faces();
        int[] nextFaces = displayState.nextFaces();
        float[] offsets = displayState.offsets();
        int[] offsetPixels = {
                offsetPixels(offsets[0]),
                offsetPixels(offsets[1]),
                offsetPixels(offsets[2])
        };
        if (Arrays.equals(lastFaces, faces)
                && Arrays.equals(lastNextFaces, nextFaces)
                && Arrays.equals(lastOffsetPixels, offsetPixels)) {
            return;
        }

        GraphicsManager graphicsManager = GameServices.graphics();
        graphicsManager.beginPatternAtlasBatch();
        for (int reel = 0; reel < 3; reel++) {
            Pattern[] patterns = buildVisibleWindowPatterns(
                    facePixels, faces[reel], nextFaces[reel], offsets[reel]);
            int destBase = DEST_PATTERN_BASES[reel];
            for (int i = 0; i < patterns.length; i++) {
                graphicsManager.updatePatternTexture(patterns[i], destBase + i);
            }
        }
        graphicsManager.endPatternAtlasBatch();

        System.arraycopy(faces, 0, lastFaces, 0, 3);
        System.arraycopy(nextFaces, 0, lastNextFaces, 0, 3);
        System.arraycopy(offsetPixels, 0, lastOffsetPixels, 0, 3);
    }

    static int[] destinationPatternBasesForTest() {
        return DEST_PATTERN_BASES.clone();
    }

    static Pattern[] buildVisibleWindowPatternsForTest(
            byte[][] faces, int face, int nextFace, float offset) {
        return buildVisibleWindowPatterns(faces, face, nextFace, offset);
    }

    private static void decodeFaces(byte[] romData, byte[][] outputFaces) {
        for (int face = 0; face < NUM_FACES; face++) {
            int faceOffset = face * TILES_PER_FACE * BYTES_PER_TILE;
            decodeFaceToPixels(romData, faceOffset, outputFaces[face]);
        }
    }

    private static void decodeFaceToPixels(byte[] romData, int faceOffset, byte[] facePixels) {
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
                        int faceX = tileCol * 8 + x;
                        int faceY = tileRow * 8 + y;
                        facePixels[faceY * FACE_WIDTH + faceX] = (byte) nibble;
                    }
                }
            }
        }
    }

    private static Pattern[] buildVisibleWindowPatterns(
            byte[][] faces, int face, int nextFace, float offset) {
        Pattern[] patterns = new Pattern[16];
        byte[] currentFace = faces[Math.floorMod(face, NUM_FACES)];
        byte[] followingFace = faces[Math.floorMod(nextFace, NUM_FACES)];
        int offsetPixels = offsetPixels(offset);

        for (int tileRow = 0; tileRow < 4; tileRow++) {
            for (int tileCol = 0; tileCol < 4; tileCol++) {
                Pattern pattern = new Pattern();
                int patternIndex = tileCol * 4 + tileRow;
                for (int pixelY = 0; pixelY < 8; pixelY++) {
                    for (int pixelX = 0; pixelX < 8; pixelX++) {
                        int globalX = tileCol * 8 + pixelX;
                        int globalY = tileRow * 8 + pixelY;
                        int scrolledY = globalY + offsetPixels;
                        byte[] sourceFace = currentFace;
                        if (scrolledY >= FACE_HEIGHT) {
                            scrolledY -= FACE_HEIGHT;
                            sourceFace = followingFace;
                        }
                        pattern.setPixel(pixelX, pixelY, sourceFace[scrolledY * FACE_WIDTH + globalX]);
                    }
                }
                patterns[patternIndex] = pattern;
            }
        }
        return patterns;
    }

    private static int offsetPixels(float offset) {
        int pixels = Math.round(offset * FACE_HEIGHT);
        return Math.floorMod(pixels, FACE_HEIGHT);
    }
}
