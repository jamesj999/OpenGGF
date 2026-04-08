package com.openggf.game.sonic3k.bonusstage.slots;

public final class S3kSlotRenderBuffers {
    private final byte[] layout;
    private final byte[] expandedLayout;
    private final int layoutStrideBytes;
    private final int layoutRows;
    private final int layoutColumns;

    private S3kSlotRenderBuffers(byte[] layout, byte[] expandedLayout,
                                 int layoutStrideBytes, int layoutRows, int layoutColumns) {
        this.layout = layout;
        this.expandedLayout = expandedLayout;
        this.layoutStrideBytes = layoutStrideBytes;
        this.layoutRows = layoutRows;
        this.layoutColumns = layoutColumns;
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
}
