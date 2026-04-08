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
                0x80,
                0x20,
                0x20);
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
        stagedPointGrid = pointGrid != null ? pointGrid.clone() : new short[0];
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
                S3kSlotRomData.RING_SPARKLE_DELAY);
    }

    public boolean startBumperAnimationAt(int layoutIndex) {
        return startTransientAnimation(layoutIndex,
                S3kSlotRomData.BUMPER_BOUNCE_FRAMES,
                S3kSlotRomData.BUMPER_BOUNCE_DELAY);
    }

    public boolean startSpikeAnimationAt(int layoutIndex) {
        return startTransientAnimation(layoutIndex,
                S3kSlotRomData.SPIKE_ANIMATION_FRAMES,
                S3kSlotRomData.SPIKE_ANIMATION_DELAY);
    }

    public void tickTransientAnimations() {
        for (TransientAnimationSlot slot : transientAnimationSlots) {
            slot.tick();
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
        int compactIndex = row * layoutColumns + col;
        for (TransientAnimationSlot slot : transientAnimationSlots) {
            if (slot.active && slot.layoutIndex == compactIndex) {
                return slot.currentTileId();
            }
        }
        int expandedIndex = row * layoutStrideBytes + col;
        if (expandedIndex < 0 || expandedIndex >= expandedLayout.length) {
            return 0;
        }
        return expandedLayout[expandedIndex] & 0xFF;
    }

    private boolean startTransientAnimation(int layoutIndex, byte[] frames, int delay) {
        if (layoutIndex < 0 || layoutIndex >= layout.length || frames == null || frames.length == 0) {
            return false;
        }
        for (TransientAnimationSlot slot : transientAnimationSlots) {
            if (!slot.active) {
                slot.start(layoutIndex, frames, delay);
                return true;
            }
        }
        return false;
    }

    private static final class TransientAnimationSlot {
        private boolean active;
        private int layoutIndex;
        private byte[] frames = new byte[0];
        private int delay;
        private int timer;
        private int frameIndex;

        private void start(int layoutIndex, byte[] frames, int delay) {
            active = true;
            this.layoutIndex = layoutIndex;
            this.frames = frames;
            this.delay = delay;
            this.timer = delay;
            this.frameIndex = 0;
        }

        private void tick() {
            if (!active) {
                return;
            }
            if (--timer > 0) {
                return;
            }
            timer = delay;
            frameIndex++;
            if (frameIndex >= frames.length) {
                active = false;
            }
        }

        private int currentTileId() {
            if (!active || frameIndex < 0 || frameIndex >= frames.length) {
                return 0;
            }
            return frames[frameIndex] & 0xFF;
        }
    }
}
