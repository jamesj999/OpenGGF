package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class S3kSlotLayoutRenderer {

    private static final int GRID_SIZE = 16;
    private static final int LAYOUT_SIZE = 32;
    private static final int CELL_SIZE = 0x18;
    private static final int GRID_OFFSET = 0xB4;
    private static final int SCREEN_X_OFFSET = 0x120;
    private static final int SCREEN_Y_OFFSET = 0xF0;
    private static final int MIN_SCREEN_X = 0x70;
    private static final int MAX_SCREEN_X = 0x1D0;
    private static final int MIN_SCREEN_Y = 0x70;
    private static final int MAX_SCREEN_Y = 0x170;

    // ROM sub_4B4C4: goal sprite animation (6-frame cycle, 1-frame hold)
    private int goalFrame;
    private int goalFrameTimer;
    // ROM sub_4B4C4: peppermint animation (4-frame cycle, 3-frame hold)
    private int peppermintFrame;
    private int peppermintTimer;

    private static final LayoutPieceDef[] PIECE_DEFS = new LayoutPieceDef[0x14];

    static {
        PIECE_DEFS[0x01] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_COLORED_WALL, 0, 3);
        PIECE_DEFS[0x02] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_COLORED_WALL, 0, 1);
        PIECE_DEFS[0x03] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_COLORED_WALL, 0, 2);
        PIECE_DEFS[0x04] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_GOAL, 0, 0);
        PIECE_DEFS[0x05] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_BUMPER, 0, 1);
        PIECE_DEFS[0x06] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_R_LABEL, 0, 1);
        PIECE_DEFS[0x07] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_PEPPERMINT, 0, 2);
        PIECE_DEFS[0x08] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_RING_STAGE, 0, 1);
        PIECE_DEFS[0x09] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_MACHINE_FACE, 0, 0);
        PIECE_DEFS[0x0A] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_BUMPER, 1, 1);
        PIECE_DEFS[0x0B] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_BUMPER, 2, 2);
        PIECE_DEFS[0x0C] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_R_LABEL, 0, 2);
        PIECE_DEFS[0x0D] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_COLORED_WALL, 0, 3);
        PIECE_DEFS[0x0E] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_COLORED_WALL, 0, 1);
        PIECE_DEFS[0x0F] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_COLORED_WALL, 0, 2);
        PIECE_DEFS[0x10] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_RING_STAGE, 4, 1);
        PIECE_DEFS[0x11] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_RING_STAGE, 5, 1);
        PIECE_DEFS[0x12] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_RING_STAGE, 6, 1);
        PIECE_DEFS[0x13] = new LayoutPieceDef(Sonic3kObjectArtKeys.SLOT_RING_STAGE, 7, 1);
    }

    /**
     * ROM sub_4B4C4 (lines 98307-98352): Updates goal and peppermint frame animations.
     * Called once per frame before rendering.
     */
    public void updateAnimations() {
        if (--goalFrameTimer < 0) {
            goalFrameTimer = 1;
            goalFrame = (goalFrame + 1) % 6;
        }
        if (--peppermintTimer < 0) {
            peppermintTimer = 3;
            peppermintFrame = (peppermintFrame + 1) & 3;
        }
    }

    public int goalFrame() {
        return goalFrame;
    }

    public int peppermintFrame() {
        return peppermintFrame;
    }

    public void tickTransientAnimations(S3kSlotRenderBuffers buffers) {
        if (buffers != null) {
            buffers.tickTransientAnimations();
        }
    }

    public short[] buildPointGrid(int angle, int cameraX, int cameraY) {
        short[] points = new short[GRID_SIZE * GRID_SIZE * 2];
        int byteAngle = angle & 0xFC;
        int sin = TrigLookupTable.sinHex(byteAngle);
        int cos = TrigLookupTable.cosHex(byteAngle);
        int offsetX = -(cameraX % CELL_SIZE) - GRID_OFFSET;
        int offsetY = -(cameraY % CELL_SIZE) - GRID_OFFSET;

        int index = 0;
        int currentOffsetY = offsetY;
        for (int row = 0; row < GRID_SIZE; row++) {
            long rowBaseX = (long) cos * offsetX + (long) (-sin) * currentOffsetY;
            long rowBaseY = (long) sin * offsetX + (long) cos * currentOffsetY;
            for (int col = 0; col < GRID_SIZE; col++) {
                points[index++] = (short) (rowBaseX >> 8);
                points[index++] = (short) (rowBaseY >> 8);
                rowBaseX += (long) cos * CELL_SIZE;
                rowBaseY += (long) sin * CELL_SIZE;
            }
            currentOffsetY += CELL_SIZE;
        }
        return points;
    }

    public List<VisibleCell> buildVisibleCells(S3kSlotRenderBuffers buffers) {
        if (buffers == null) {
            return List.of();
        }
        short[] points = buffers.stagedPointGrid();
        if (points == null || points.length < GRID_SIZE * GRID_SIZE * 2) {
            return List.of();
        }

        int layoutRow = Math.floorDiv(buffers.stagedCameraY(), CELL_SIZE);
        int layoutCol = Math.floorDiv(buffers.stagedCameraX(), CELL_SIZE);
        List<VisibleCell> visible = new ArrayList<>();

        int pointIndex = 0;
        for (int row = 0; row < GRID_SIZE; row++) {
            int sourceRow = layoutRow + row;
            for (int col = 0; col < GRID_SIZE; col++) {
                int sourceCol = layoutCol + col;
                int cellId = buffers.renderCellIdAt(sourceRow, sourceCol);

                int pointX = points[pointIndex++];
                int pointY = points[pointIndex++];
                int screenX = pointX + SCREEN_X_OFFSET;
                int screenY = pointY + SCREEN_Y_OFFSET;

                if (cellId == 0 || cellId >= PIECE_DEFS.length || PIECE_DEFS[cellId] == null) {
                    continue;
                }
                if (screenX < MIN_SCREEN_X || screenX >= MAX_SCREEN_X
                        || screenY < MIN_SCREEN_Y || screenY >= MAX_SCREEN_Y) {
                    continue;
                }
                visible.add(new VisibleCell((byte) cellId, screenX, screenY));
            }
        }

        return Collections.unmodifiableList(visible);
    }

    public List<VisibleCell> buildVisibleCells(byte[] layout, int angle, int cameraX, int cameraY) {
        if (layout == null || layout.length < LAYOUT_SIZE * LAYOUT_SIZE) {
            return List.of();
        }

        short[] points = buildPointGrid(angle, cameraX, cameraY);
        int layoutRow = Math.floorDiv(cameraY, CELL_SIZE);
        int layoutCol = Math.floorDiv(cameraX, CELL_SIZE);
        List<VisibleCell> visible = new ArrayList<>();

        int pointIndex = 0;
        for (int row = 0; row < GRID_SIZE; row++) {
            int sourceRow = layoutRow + row;
            for (int col = 0; col < GRID_SIZE; col++) {
                int sourceCol = layoutCol + col;
                int cellId = 0;
                if (sourceRow >= 0 && sourceRow < LAYOUT_SIZE && sourceCol >= 0 && sourceCol < LAYOUT_SIZE) {
                    cellId = layout[sourceRow * LAYOUT_SIZE + sourceCol] & 0xFF;
                }

                int pointX = points[pointIndex++];
                int pointY = points[pointIndex++];
                int screenX = pointX + SCREEN_X_OFFSET;
                int screenY = pointY + SCREEN_Y_OFFSET;

                if (cellId == 0 || cellId >= PIECE_DEFS.length || PIECE_DEFS[cellId] == null) {
                    continue;
                }
                if (screenX < MIN_SCREEN_X || screenX >= MAX_SCREEN_X
                        || screenY < MIN_SCREEN_Y || screenY >= MAX_SCREEN_Y) {
                    continue;
                }
                visible.add(new VisibleCell((byte) cellId, screenX, screenY));
            }
        }

        return Collections.unmodifiableList(visible);
    }

    public void render(S3kSlotStageState state, S3kSlotRenderBuffers buffers,
                       Camera camera, ObjectRenderManager renderManager) {
        if (state == null || buffers == null || camera == null || renderManager == null) {
            return;
        }
        updateAnimations();
        tickTransientAnimations(buffers);
        renderVisibleCells(buildVisibleCells(buffers), camera, renderManager);
    }

    public void renderVisibleCells(List<VisibleCell> visibleCells, Camera camera, ObjectRenderManager renderManager) {
        if (visibleCells == null || visibleCells.isEmpty() || camera == null || renderManager == null) {
            return;
        }
        int cameraX = camera.getX();
        int cameraY = camera.getY();
        for (VisibleCell cell : visibleCells) {
            int cellId = cell.cellId() & 0xFF;
            if (cellId <= 0 || cellId >= PIECE_DEFS.length) {
                continue;
            }
            LayoutPieceDef def = PIECE_DEFS[cellId];
            if (def == null) {
                continue;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(def.artKey());
            if (renderer == null) {
                continue;
            }
            // ROM sub_4B4C4: dynamic frame override for animated pieces
            int frameIdx = def.frameIndex();
            if (cellId == 0x04) {
                frameIdx = goalFrame;
            } else if (cellId == 0x07) {
                frameIdx = peppermintFrame;
            }
            renderer.drawFrameIndex(frameIdx, cameraX + cell.screenX(), cameraY + cell.screenY(),
                    false, false, def.palette());
        }
    }

    public record VisibleCell(byte cellId, int screenX, int screenY) {
    }

    private record LayoutPieceDef(String artKey, int frameIndex, int palette) {
    }
}
