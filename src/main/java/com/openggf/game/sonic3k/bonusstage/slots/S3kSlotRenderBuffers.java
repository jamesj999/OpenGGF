package com.openggf.game.sonic3k.bonusstage.slots;

public final class S3kSlotRenderBuffers {
    private final byte[] layout;
    private final byte[] expandedLayout;
    private final int layoutStrideBytes;
    private final int layoutRows;
    private final int layoutColumns;
    private final TransientAnimationSlot[] transientAnimationSlots;
    private short[] stagedPointGrid = new short[0];
    private int stagedCameraX;
    private int stagedCameraY;

    private S3kSlotRenderBuffers(byte[] layout, byte[] expandedLayout,
                                 int layoutStrideBytes, int layoutRows, int layoutColumns) {
        this.layout = layout;
        this.expandedLayout = expandedLayout;
        this.layoutStrideBytes = layoutStrideBytes;
        this.layoutRows = layoutRows;
        this.layoutColumns = layoutColumns;
        this.transientAnimationSlots = new TransientAnimationSlot[S3kSlotRomData.TRANSIENT_SLOT_COUNT];
        for (int i = 0; i < transientAnimationSlots.length; i++) {
            transientAnimationSlots[i] = new TransientAnimationSlot();
        }
    }

    public static S3kSlotRenderBuffers fromRomData() {
        return new S3kSlotRenderBuffers(
                S3kSlotRomData.SLOT_BONUS_LAYOUT.clone(),
                S3kSlotRomData.buildExpandedLayoutBuffer(),
                S3kSlotRomData.SLOT_EXPANDED_STRIDE,
                S3kSlotRomData.SLOT_EXPANDED_STRIDE,
                S3kSlotRomData.SLOT_EXPANDED_STRIDE);
    }

    public byte[] layout() {
        return layout;
    }

    public byte[] expandedLayout() {
        return expandedLayout;
    }

    public int layoutStrideBytes() {
        return layoutStrideBytes;
    }

    public int layoutRows() {
        return layoutRows;
    }

    public int layoutColumns() {
        return layoutColumns;
    }

    public void stagePointGrid(short[] pointGrid) {
        stagedPointGrid = pointGrid != null ? pointGrid : new short[0];
    }

    public short[] stagedPointGrid() {
        return stagedPointGrid;
    }

    public void stageViewport(int cameraX, int cameraY) {
        stagedCameraX = cameraX;
        stagedCameraY = cameraY;
    }

    public int stagedCameraX() {
        return stagedCameraX;
    }

    public int stagedCameraY() {
        return stagedCameraY;
    }

    public boolean startRingAnimationAt(int layoutIndex) {
        return startTransientAnimation(layoutIndex,
                S3kSlotRomData.RING_SPARKLE_FRAMES,
                S3kSlotRomData.RING_SPARKLE_DELAY,
                (byte) 0x00);
    }

    public boolean startBumperAnimationAt(int layoutIndex) {
        return startTransientAnimation(layoutIndex,
                S3kSlotRomData.BUMPER_BOUNCE_FRAMES,
                S3kSlotRomData.BUMPER_BOUNCE_DELAY,
                (byte) 0x05);
    }

    public boolean startSpikeAnimationAt(int layoutIndex) {
        return startTransientAnimation(layoutIndex,
                S3kSlotRomData.SPIKE_ANIMATION_FRAMES,
                S3kSlotRomData.SPIKE_ANIMATION_DELAY,
                (byte) 0x06);
    }

    public boolean startSlotWallAnimationAt(int layoutIndex, int finalTileId) {
        return startTransientAnimation(layoutIndex,
                S3kSlotRomData.SLOT_WALL_COLOR_FRAMES,
                S3kSlotRomData.SLOT_WALL_COLOR_DELAY,
                (byte) finalTileId);
    }

    public void tickTransientAnimations() {
        for (TransientAnimationSlot slot : transientAnimationSlots) {
            slot.tick(this);
        }
    }

    public boolean hasActiveTransientAnimationAt(int layoutIndex) {
        for (TransientAnimationSlot slot : transientAnimationSlots) {
            if (slot.active && slot.layoutIndex == layoutIndex) {
                return true;
            }
        }
        return false;
    }

    public int renderCellIdAt(int row, int col) {
        if (row < 0 || row >= layoutRows || col < 0 || col >= layoutColumns) {
            return 0;
        }
        int expandedIndex = row * layoutStrideBytes + col;
        if (expandedIndex < 0 || expandedIndex >= expandedLayout.length) {
            return 0;
        }
        return expandedLayout[expandedIndex] & 0xFF;
    }

    public int compactToExpandedIndex(int compactIndex) {
        if (compactIndex < 0 || compactIndex >= layout.length) {
            return -1;
        }
        int compactRow = compactIndex / S3kSlotRomData.SLOT_LAYOUT_SIZE;
        int compactCol = compactIndex % S3kSlotRomData.SLOT_LAYOUT_SIZE;
        int expandedRow = compactRow + S3kSlotRomData.SLOT_LAYOUT_WORLD_OFFSET;
        int expandedCol = compactCol + S3kSlotRomData.SLOT_LAYOUT_WORLD_OFFSET;
        return expandedRow * layoutStrideBytes + expandedCol;
    }

    public int expandedToCompactIndex(int expandedIndex) {
        if (expandedIndex < 0 || expandedIndex >= expandedLayout.length) {
            return -1;
        }
        return expandedToCompactIndex(expandedIndex / layoutStrideBytes, expandedIndex % layoutStrideBytes);
    }

    public int expandedToCompactIndex(int expandedRow, int expandedCol) {
        int compactRow = expandedRow - S3kSlotRomData.SLOT_LAYOUT_WORLD_OFFSET;
        int compactCol = expandedCol - S3kSlotRomData.SLOT_LAYOUT_WORLD_OFFSET;
        if (compactRow < 0 || compactRow >= S3kSlotRomData.SLOT_LAYOUT_SIZE
                || compactCol < 0 || compactCol >= S3kSlotRomData.SLOT_LAYOUT_SIZE) {
            return -1;
        }
        return compactRow * S3kSlotRomData.SLOT_LAYOUT_SIZE + compactCol;
    }

    private boolean startTransientAnimation(int layoutIndex, byte[] frames, int delay, byte restoreTile) {
        if (layoutIndex < 0 || layoutIndex >= layout.length || frames == null || frames.length == 0) {
            return false;
        }
        for (TransientAnimationSlot slot : transientAnimationSlots) {
            if (!slot.active) {
                slot.start(this, layoutIndex, frames, delay, restoreTile);
                return true;
            }
        }
        return false;
    }

    private void setCompactTile(int compactIndex, byte tileId) {
        if (compactIndex >= 0 && compactIndex < layout.length) {
            layout[compactIndex] = tileId;
        }
        int expandedIndex = compactToExpandedIndex(compactIndex);
        if (expandedIndex >= 0 && expandedIndex < expandedLayout.length) {
            expandedLayout[expandedIndex] = tileId;
        }
    }

    private static final class TransientAnimationSlot {
        private boolean active;
        private int layoutIndex = -1;
        private byte[] frames = new byte[0];
        private int delay;
        private int timer;
        private int frameIndex;
        private byte restoreTile;

        private void start(S3kSlotRenderBuffers buffers, int layoutIndex, byte[] frames, int delay, byte restoreTile) {
            active = true;
            this.layoutIndex = layoutIndex;
            this.frames = frames;
            this.delay = delay;
            this.timer = delay;
            this.frameIndex = 0;
            this.restoreTile = restoreTile;
            buffers.setCompactTile(layoutIndex, frames[0]);
        }

        private void tick(S3kSlotRenderBuffers buffers) {
            if (!active) {
                return;
            }
            if (--timer > 0) {
                return;
            }
            timer = delay;
            frameIndex++;
            if (frameIndex >= frames.length) {
                buffers.setCompactTile(layoutIndex, restoreTile);
                active = false;
                layoutIndex = -1;
                frames = new byte[0];
                return;
            }
            buffers.setCompactTile(layoutIndex, frames[frameIndex]);
        }
    }
}
