package com.openggf.game.sonic3k.specialstage;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.PatternDesc;

import java.util.logging.Logger;

import static com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageConstants.*;

/**
 * Renderer for the S3K Blue Ball special stage.
 * <p>
 * Implements Draw_SSSprites (sonic3k.asm:12266) — the pseudo-3D perspective
 * projection that renders spheres, rings, bumpers, and springs from the 32x32
 * grid onto the screen using pre-computed perspective maps.
 * <p>
 * Reference: docs/skdisasm/sonic3k.asm Draw_SSSprites (line 12266)
 */
public class Sonic3kSpecialStageRenderer {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kSpecialStageRenderer.class.getName());

    public static final int SCREEN_WIDTH = 320;
    public static final int SCREEN_HEIGHT = 224;
    public static final int TILE_SIZE = 8;

    private final GraphicsManager graphicsManager;
    private final PatternDesc reusableDesc = new PatternDesc();

    // Pattern base indices for each art type in the atlas
    private int floorPatternBase;
    private int spherePatternBase;
    private int ringPatternBase;
    private int bgPatternBase;
    private int shadowPatternBase;
    private int getBlueSpherePatternBase;
    private int gbsArrowPatternBase;
    private int digitsPatternBase;
    private int iconsPatternBase;
    private int playerPatternBase;
    private int emeraldPatternBase;

    private boolean artLoaded;

    /** Decompressed perspective map data (loaded from SStageKos_PerspectiveMaps). */
    private byte[] perspectiveMaps;


    /** Decompressed Enigma floor map (9 frames of 40x28 tiles). */
    private byte[] floorMapData;

    /** Raw mapping data for Sonic's SS sprite. */
    private byte[] sonicMappingData;
    /** Raw DPLC data for Sonic's SS sprite. */
    private byte[] sonicDplcData;
    /** Number of mapping frames in the header. */
    private int sonicMappingFrameCount;

    /**
     * Direction table for perspective grid traversal.
     * 4 quadrants, 6 fields each: col_start, row_start, col_step, col_mask, row_step, row_mask.
     * From word_98B0 (sonic3k.asm:12229).
     */
    private static final int[][] DIR_TABLE = {
        { 0x18, 6,  1, 0x1F, -1, 0x1F },  // North (0x00-0x3F)
        { 8,    6, -1, 0x1F, -1, 0x1F },  // West  (0x40-0x7F)
        { 8,  0x1A,-1, 0x1F,  1, 0x1F },  // South (0x80-0xBF)
        { 0x18,0x1A, 1, 0x1F,  1, 0x1F }, // East  (0xC0-0xFF)
    };

    public Sonic3kSpecialStageRenderer(GraphicsManager graphicsManager) {
        this.graphicsManager = graphicsManager;
    }

    public void loadArt(Sonic3kSpecialStageDataLoader dataLoader) {
        artLoaded = false;
    }

    public void setPerspectiveMaps(byte[] maps) {
        this.perspectiveMaps = maps;
    }

    public void setFloorMapData(byte[] data) {
        this.floorMapData = data;
    }

    public void setSonicMappingData(byte[] mappingData, byte[] dplcData) {
        this.sonicMappingData = mappingData;
        this.sonicDplcData = dplcData;
        // Count mapping frames from header (12 frames for Sonic SS)
        this.sonicMappingFrameCount = 12;
    }

    // ==================== Main Render ====================

    public void render(Sonic3kSpecialStageManager manager) {
        Sonic3kSpecialStagePlayer player = manager.getPlayer();
        Sonic3kSpecialStageGrid grid = manager.getGrid();

        // Background is black (cleared by engine)

        // Render checkerboard floor (Plane A)
        renderFloor(manager, player);

        // Render grid sprites with perspective projection (VDP sprites)
        renderGridSprites3D(manager, grid, player);
    }

    /**
     * Floor frame lookup matching SS_Pal_Map_Ptrs (sonic3k.asm:11107).
     * <p>
     * The Enigma map data contains 9 frames at offsets in RAM:
     * <pre>
     *   Frame 0: RAM+$5500 (offset 0)
     *   Frame 1: RAM+$5DC0 (offset $8C0 = 2240)
     *   Frame 2: RAM+$6680 (offset $1180)
     *   ...
     *   Frame 8: RAM+$9B00 (offset $4600)
     * </pre>
     * Moving anim frames 0-15 alternate between frames 0 and 1.
     * Turning anim frames 16-22 use frames 8 down to 2 (reverse order).
     */
    private static final int[] FLOOR_FRAME_MAP = {
        0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, // anim 0-15: alternate 0/1
        8, 7, 6, 5, 4, 3, 2                                 // anim 16-22: turning
    };

    /**
     * Render the checkerboard floor using the Enigma-mapped layout tiles.
     */
    private void renderFloor(Sonic3kSpecialStageManager manager, Sonic3kSpecialStagePlayer player) {
        if (!artLoaded || floorMapData == null || floorPatternBase <= 0) return;

        // Compute anim frame (same as sprite renderer)
        int angle = player.getAngle();
        boolean axisBit6 = (angle & 0x40) != 0;
        int d0;
        if (axisBit6) {
            d0 = (player.getXPos() + 0x100 + (player.getYPos() & 0x100)) & 0xFFFF;
        } else {
            d0 = (player.getYPos() + (player.getXPos() & 0x100)) & 0xFFFF;
        }
        if ((angle & 0x80) == 0) d0 = (-d0 + 0x1F) & 0xFFFF;
        int animFrame = (d0 & 0x1E0) >> 5;
        int turnBits = angle & 0x38;
        if (turnBits != 0) animFrame = 0x0F + (turnBits >> 3);

        // Floor frame selection.
        // The ROM uses BOTH palette rotation (Rotate_SSPal) AND map frame swapping
        // together: palette rotation shifts colors on even anim frames, and the map
        // swap between frames 0/1 shifts the tile pattern on odd anim frames. Combined,
        // this gives 16 visual updates per cell crossing instead of 8.
        // Turning uses dedicated pre-rendered perspective frames 2-8.
        int floorFrame;
        if (animFrame >= 16 && animFrame < FLOOR_FRAME_MAP.length) {
            floorFrame = FLOOR_FRAME_MAP[animFrame]; // Turning frames
        } else {
            floorFrame = FLOOR_FRAME_MAP[animFrame & 0xF]; // 0 or 1 alternating
        }
        int maxFrames = floorMapData.length / (40 * 28 * 2);
        if (floorFrame >= maxFrames) floorFrame = 0;

        int frameSize = 40 * 28 * 2; // bytes per frame (40 tiles x 28 rows x 2 bytes/word)
        int frameOffset = floorFrame * frameSize;

        if (frameOffset + frameSize > floorMapData.length) return;

        graphicsManager.beginPatternBatch();

        for (int ty = 0; ty < 28; ty++) {
            for (int tx = 0; tx < 40; tx++) {
                int mapIdx = frameOffset + (ty * 40 + tx) * 2;
                int word = ((floorMapData[mapIdx] & 0xFF) << 8)
                         | (floorMapData[mapIdx + 1] & 0xFF);

                // Enigma map word format: PCCV_HTTT_TTTT_TTTT
                // P=priority, CC=palette, V=vflip, H=hflip, T=pattern index
                int patternIdx = word & 0x7FF;
                boolean hFlip = (word & 0x0800) != 0;
                boolean vFlip = (word & 0x1000) != 0;
                int palette = (word >> 13) & 3;
                boolean priority = (word & 0x8000) != 0;

                int patternId = floorPatternBase + patternIdx;

                reusableDesc.set(0);
                reusableDesc.setPriority(priority);
                reusableDesc.setPaletteIndex(palette);
                reusableDesc.setHFlip(hFlip);
                reusableDesc.setVFlip(vFlip);
                graphicsManager.renderPatternWithId(patternId, reusableDesc,
                        tx * TILE_SIZE, ty * TILE_SIZE);
            }
        }

        graphicsManager.flushPatternBatch();
    }

    // ==================== 3D Perspective Rendering ====================

    /**
     * Render grid sprites using ROM-accurate pseudo-3D perspective.
     * <p>
     * Implements Draw_SSSprites (sonic3k.asm:12266):
     * <ol>
     *   <li>Select direction table based on angle quadrant</li>
     *   <li>Compute animation frame from position/angle</li>
     *   <li>Look up perspective map pointer for this frame</li>
     *   <li>Traverse 16 rows x 15 columns of the grid</li>
     *   <li>For each non-empty cell, read perspective entry (screen pos + size)</li>
     *   <li>Render the sprite at the projected position with scaled size</li>
     * </ol>
     */
    private void renderGridSprites3D(Sonic3kSpecialStageManager manager,
                                     Sonic3kSpecialStageGrid grid,
                                     Sonic3kSpecialStagePlayer player) {
        if (!artLoaded) return;

        int angle = player.getAngle();
        int xPos = player.getXPos();
        int yPos = player.getYPos();
        boolean axisBit6 = (angle & 0x40) != 0;

        // Select direction table
        int quadrant = (angle & 0xC0) >> 6;
        int[] dir = DIR_TABLE[quadrant];

        // Compute d1 and animation frame exactly as the ROM does.
        // ROM lines 12271-12306:
        //   move.b (X_pos).w,d1 → reads HIGH byte of word (grid cell X, 0-31)
        //   move.w (X_pos).w,d0 → reads full word
        //   d0 = X_pos + $100 + (Y_pos & $100)
        //   if bit6=0: d1 = Y_pos high byte (grid cell Y), d0 = Y_pos + (X_pos & $100)
        //   if angle positive: d0 = -d0 + $1F, if (d0 & $E0)!=0 then d1++
        //
        // IMPORTANT: 68000 move.b at a word address reads the HIGH byte (big-endian)
        int d1 = (xPos >> 8) & 0xFF;  // Grid cell X
        int d0 = (xPos + 0x100 + (yPos & 0x100)) & 0xFFFF;

        if (!axisBit6) {
            d1 = (yPos >> 8) & 0xFF;  // Grid cell Y
            d0 = (yPos + (xPos & 0x100)) & 0xFFFF;
        }

        if ((angle & 0x80) == 0) {
            d0 = (-d0 + 0x1F) & 0xFFFF;
            if ((d0 & 0xE0) != 0) {
                d1 = (d1 + 1) & 0xFF;
            }
        }

        int animFrame = (d0 & 0x1E0) >> 5;

        // Turning frames override
        int turnBits = angle & 0x38;
        if (turnBits != 0) {
            animFrame = 0x0F + (turnBits >> 3);
        }

        // Get perspective map data for this animation frame
        int perspFrameOffset = getPerspectiveFrameOffset(animFrame);

        // d5 = row variable: dir[1] (row_start) + d1, masked by dir[5]
        int d5 = (dir[1] + d1) & dir[5];

        graphicsManager.beginPatternBatch();

        int perspIdx = 0; // sequential index into perspective entries

        // 16 rows (outer loop) — ROM: moveq #$10-1,d2 / dbf d2
        for (int rowIdx = 0; rowIdx < 16; rowIdx++) {
            // d4 = column variable: reset each row
            // bit6=0: d4 = dir[0] + X_pos_high_byte (ROM line 12339: move.b (X_pos).w,d0)
            // bit6=1: d4 = dir[0] + Y_pos_high_byte (ROM line 12418: move.b (Y_pos).w,d0)
            // 68000 move.b reads HIGH byte of word = grid cell coordinate
            int d4;
            if (axisBit6) {
                d4 = (dir[0] + ((yPos >> 8) & 0xFF)) & dir[3];
            } else {
                d4 = (dir[0] + ((xPos >> 8) & 0xFF)) & dir[3];
            }

            // 15 columns per row (inner loop) — ROM: moveq #$F-1,d3
            for (int colIdx = 0; colIdx < 15; colIdx++) {
                // Grid index computation:
                // bit6=0: index = (d5 << 5) | d4   (d5=Y row, d4=X col)
                // bit6=1: index = (d4 << 5) | d5   (d4=Y col, d5=X row)
                int gridIndex;
                if (axisBit6) {
                    gridIndex = ((d4 & 0x1F) << 5) | (d5 & 0x1F);
                } else {
                    gridIndex = ((d5 & 0x1F) << 5) | (d4 & 0x1F);
                }

                int cellType = grid.getCellByIndex(gridIndex & 0x3FF);

                // Read perspective entry for this cell (6 bytes)
                if (cellType != CELL_EMPTY && perspFrameOffset >= 0) {
                    int entryOff = perspFrameOffset + perspIdx * 6;
                    if (entryOff + 5 < perspectiveMaps.length) {
                        int perspWord = readWord(perspectiveMaps, entryOff);
                        int scrX = readSignedWord(perspectiveMaps, entryOff + 2);
                        int scrY = readSignedWord(perspectiveMaps, entryOff + 4);

                        // Convert VDP coordinates to screen coordinates
                        // VDP adds 128 to both X and Y; engine uses direct screen coords
                        scrX -= 128;
                        scrY -= 128;

                        int sizeField = (perspWord & 0x7C) >> 2;
                        int sizeIndex = sizeField - 6;

                        if (sizeIndex >= 0 && sizeIndex < 16) {
                            renderPerspectiveSprite(cellType, scrX, scrY, sizeIndex);
                        }
                    }
                }

                perspIdx++;
                d4 = (d4 + dir[2]) & dir[3]; // col_step
            }

            d5 = (d5 + dir[4]) & dir[5]; // row_step
        }

        // Render player at fixed screen position
        // ROM: player object has $30=0xA0 (160), $32=0x70 (112) as center offsets,
        // plus $36=-0x800 which after 3D projection places Sonic near screen bottom.
        // The mapping y-offsets are VDP-relative (include +128 offset).
        // For the on-screen position, use approximately (160, 160) as the sprite origin.
        renderPlayerSprite(player, PLAYER_SCREEN_CENTER_X, 160);

        graphicsManager.flushPatternBatch();
    }

    /**
     * RAM_start address on Mega Drive. Perspective map pointers are absolute
     * RAM addresses; subtracting this gives the offset within our buffer.
     */
    private static final int RAM_START = 0x00FF0000;

    /**
     * Get the byte offset into perspectiveMaps for a given animation frame.
     * <p>
     * The first 96 bytes (24 longwords) are a pointer table. Each entry is
     * an absolute Mega Drive RAM address (e.g. 0x00FF005C). The data was
     * decompressed to RAM_start (0x00FF0000), so the buffer-relative offset
     * is {@code pointer - RAM_START}.
     * <p>
     * Each frame is 1440 bytes (16 rows x 15 columns x 6 bytes per entry).
     */
    private int getPerspectiveFrameOffset(int animFrame) {
        if (perspectiveMaps == null || animFrame < 0 || animFrame >= 24) {
            return -1;
        }
        int tableOffset = animFrame * 4;
        if (tableOffset + 3 >= perspectiveMaps.length) {
            return -1;
        }
        int ptr = readLong(perspectiveMaps, tableOffset);
        int offset = ptr - RAM_START;
        if (offset < 0 || offset >= perspectiveMaps.length) {
            return -1;
        }
        return offset;
    }

    // ==================== Sprite Rendering ====================

    /**
     * Render a sprite at a perspective-projected position with size scaling.
     * Maps sizeIndex to the correct sphere/ring mapping frame.
     */
    private void renderPerspectiveSprite(int cellType, int screenX, int screenY, int sizeIndex) {
        int patternBase;
        int paletteIndex;

        switch (cellType) {
            case CELL_BLUE:  patternBase = spherePatternBase; paletteIndex = 2; break;
            case CELL_RED:
            case CELL_TOUCHED:  // Touched spheres display as red
                             patternBase = spherePatternBase; paletteIndex = 0; break;
            case CELL_BUMPER: patternBase = spherePatternBase; paletteIndex = 1; break;
            case CELL_RING: case CELL_RING_ANIM_1: case CELL_RING_ANIM_2:
            case CELL_RING_ANIM_3: case CELL_RING_ANIM_4:
                patternBase = ringPatternBase; paletteIndex = 2; break;
            case CELL_SPRING: patternBase = spherePatternBase; paletteIndex = 3; break;
            case CELL_CHAOS_EMERALD: case CELL_SUPER_EMERALD:
                patternBase = emeraldPatternBase > 0 ? emeraldPatternBase : ringPatternBase;
                paletteIndex = 3; break;
            default: return;
        }

        // Size index → sprite dimensions and tile offset in sphere art
        int tilesW, tilesH, tileOffset;
        if (sizeIndex < 4) {
            tilesW = 4; tilesH = 4;
            tileOffset = sizeIndex * 0x10;
        } else if (sizeIndex < 8) {
            tilesW = 3; tilesH = 3;
            tileOffset = 0x40 + (sizeIndex - 4) * 9;
        } else if (sizeIndex < 12) {
            tilesW = 2; tilesH = 2;
            tileOffset = 0x64 + (sizeIndex - 8) * 4;
        } else {
            tilesW = 1; tilesH = 1;
            tileOffset = 0x74 + (sizeIndex - 12);
        }

        // Ring art has different frame layout
        boolean isRing = (cellType == CELL_RING || (cellType >= CELL_RING_ANIM_1 && cellType <= CELL_RING_ANIM_4));
        if (isRing) {
            if (sizeIndex < 4) {
                tilesW = 2; tilesH = 3; tileOffset = 0;
            } else if (sizeIndex < 8) {
                tilesW = 2; tilesH = 2; tileOffset = 0x33;
            } else if (sizeIndex < 12) {
                tilesW = 2; tilesH = 1; tileOffset = 0x45;
            } else {
                tilesW = 1; tilesH = 1; tileOffset = 0x48;
            }
        }

        // Apply the mapping piece offsets to center the sprite on the grid position.
        // From Map_SStageSphere: each size has specific y/x offsets:
        //   4x4 (32px): y=-16 (0xF0), x=-16 (0xFFF0)
        //   3x3 (24px): y=-12 (0xF4), x=-12 (0xFFF4)
        //   2x2 (16px): y=-8  (0xF8), x=-8  (0xFFF8)
        //   1x1 (8px):  y=-4  (0xFC), x=-4  (0xFFFC)
        int centerOffX = -(tilesW * TILE_SIZE) / 2;
        int centerOffY = -(tilesH * TILE_SIZE) / 2;

        // Render using column-major VDP ordering
        for (int col = 0; col < tilesW; col++) {
            for (int row = 0; row < tilesH; row++) {
                int tileIdx = col * tilesH + row;
                int patternId = patternBase + tileOffset + tileIdx;
                reusableDesc.set(0);
                reusableDesc.setPriority(true);
                reusableDesc.setPaletteIndex(paletteIndex);
                graphicsManager.renderPatternWithId(patternId, reusableDesc,
                        screenX + centerOffX + col * TILE_SIZE,
                        screenY + centerOffY + row * TILE_SIZE);
            }
        }
    }

    /**
     * Render the player character sprite using ROM mapping + DPLC data.
     * <p>
     * The DPLC (Dynamic Pattern Load Cue) defines which art tiles to load
     * for each mapping frame. Each DPLC entry is a word: high nibble = count-1,
     * low 12 bits = source tile index in the uncompressed art.
     * <p>
     * The mapping references VRAM tile indices 0, 0x10, 0x13, etc. which
     * correspond to the DPLC-loaded tiles. We resolve the actual art tile
     * by walking the DPLC entries to build a VRAM→art tile index map.
     */
    private void renderPlayerSprite(Sonic3kSpecialStagePlayer player, int centerX, int centerY) {
        if (!artLoaded || playerPatternBase <= 0 || sonicMappingData == null || sonicDplcData == null) return;

        int frame = player.getMappingFrame();
        if (frame >= sonicMappingFrameCount) return;

        // --- Step 1: Parse DPLC to build tile remapping table ---
        // DPLC header: word offsets to per-frame entries (same count as mappings)
        // DPLC data starts after the mapping header in the combined file
        // PLC_SStageSonic starts at offset 24 (12 frames * 2 bytes) in the mapping data
        int dplcHeaderOff = sonicMappingFrameCount * 2; // 24 for 12 frames
        if (dplcHeaderOff + frame * 2 + 1 >= sonicMappingData.length) return;

        int dplcFrameOff = dplcHeaderOff + readWord(sonicMappingData, dplcHeaderOff + frame * 2);
        if (dplcFrameOff + 1 >= sonicMappingData.length) return;
        int dplcCount = readWord(sonicMappingData, dplcFrameOff);
        if (dplcCount <= 0 || dplcCount > 20) return;

        // Build VRAM tile → art tile mapping
        // Each DPLC entry loads N tiles from art offset T into sequential VRAM slots
        int[] vramToArt = new int[64]; // Max 64 VRAM tiles
        int vramSlot = 0;
        for (int d = 0; d < dplcCount; d++) {
            int dplcWord = readWord(sonicMappingData, dplcFrameOff + 2 + d * 2);
            int tileCount = ((dplcWord >> 12) & 0xF) + 1;
            int artTileIdx = dplcWord & 0xFFF;
            for (int t = 0; t < tileCount && vramSlot < 64; t++) {
                vramToArt[vramSlot++] = artTileIdx + t;
            }
        }

        // --- Step 2: Parse mapping and render pieces ---
        if (frame * 2 + 1 >= sonicMappingData.length) return;
        int mapFrameOff = readWord(sonicMappingData, frame * 2);
        if (mapFrameOff + 1 >= sonicMappingData.length) return;

        int pieceCount = readWord(sonicMappingData, mapFrameOff);
        if (pieceCount <= 0 || pieceCount > 10) return;

        for (int p = 0; p < pieceCount; p++) {
            int po = mapFrameOff + 2 + p * 6;
            if (po + 5 >= sonicMappingData.length) break;

            int yOff = (byte) sonicMappingData[po];
            int sizeByte = sonicMappingData[po + 1] & 0xFF;
            int patternWord = readWord(sonicMappingData, po + 2);
            int xOff = readSignedWord(sonicMappingData, po + 4);

            int tilesW = ((sizeByte >> 2) & 3) + 1;
            int tilesH = (sizeByte & 3) + 1;
            int vramTileBase = patternWord & 0x07FF;
            boolean hFlip = (patternWord & 0x0800) != 0;

            // Render each tile of this piece using column-major ordering
            for (int col = 0; col < tilesW; col++) {
                for (int row = 0; row < tilesH; row++) {
                    int tileOff = col * tilesH + row;
                    int vramTile = vramTileBase + tileOff;

                    // Resolve VRAM tile to actual art tile via DPLC
                    int artTile = (vramTile < vramSlot) ? vramToArt[vramTile] : vramTile;
                    int patternId = playerPatternBase + artTile;

                    int drawX = centerX + xOff + col * TILE_SIZE;
                    int drawY = centerY + yOff + row * TILE_SIZE;

                    // H-flip reverses column order
                    if (hFlip) {
                        drawX = centerX + xOff + (tilesW - 1 - col) * TILE_SIZE;
                    }

                    reusableDesc.set(0);
                    reusableDesc.setPriority(true);
                    reusableDesc.setPaletteIndex(0);
                    reusableDesc.setHFlip(hFlip);
                    graphicsManager.renderPatternWithId(patternId, reusableDesc, drawX, drawY);
                }
            }
        }
    }

    // ==================== Byte Reading Helpers ====================

    private static int readWord(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static int readSignedWord(byte[] data, int offset) {
        return (short)(((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF));
    }

    private static int readLong(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
             | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    // ==================== Palette Index Mapping ====================

    private static int cellTypeToPaletteIndex(int cellType) {
        switch (cellType) {
            case CELL_EMPTY: return -1;
            case CELL_BLUE: return 2;
            case CELL_RED: return 0;
            case CELL_BUMPER: return 1;
            case CELL_RING: return 2;
            case CELL_SPRING: return 1;
            case CELL_CHAOS_EMERALD: case CELL_SUPER_EMERALD: return 2;
            default:
                if (cellType >= CELL_RING_ANIM_1 && cellType <= CELL_RING_ANIM_4) return 2;
                return -1;
        }
    }

    // ==================== Setters ====================

    public boolean isArtLoaded() { return artLoaded; }
    public void setArtLoaded(boolean loaded) { this.artLoaded = loaded; }
    public void setFloorPatternBase(int base) { this.floorPatternBase = base; }
    public void setSpherePatternBase(int base) { this.spherePatternBase = base; }
    public void setRingPatternBase(int base) { this.ringPatternBase = base; }
    public void setBgPatternBase(int base) { this.bgPatternBase = base; }
    public void setShadowPatternBase(int base) { this.shadowPatternBase = base; }
    public void setGetBlueSpherePatternBase(int base) { this.getBlueSpherePatternBase = base; }
    public void setGbsArrowPatternBase(int base) { this.gbsArrowPatternBase = base; }
    public void setDigitsPatternBase(int base) { this.digitsPatternBase = base; }
    public void setIconsPatternBase(int base) { this.iconsPatternBase = base; }
    public void setPlayerPatternBase(int base) { this.playerPatternBase = base; }
    public void setEmeraldPatternBase(int base) { this.emeraldPatternBase = base; }
}
