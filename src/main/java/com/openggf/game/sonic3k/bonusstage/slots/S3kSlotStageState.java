package com.openggf.game.sonic3k.bonusstage.slots;

public final class S3kSlotStageState {
    private int statTable;
    private int scalarIndex1;
    private int scalarIndex2;
    private int scalarResult0;
    private int scalarResult1;
    private int lastCollisionTileId;
    private int lastCollisionIndex = -1;
    private boolean paletteCycleEnabled;

    public static S3kSlotStageState bootstrap() {
        S3kSlotStageState state = new S3kSlotStageState();
        state.scalarIndex1 = 0x40;
        return state;
    }

    public int statTable() {
        return statTable;
    }

    public void setStatTable(int statTable) {
        this.statTable = statTable;
    }

    public int scalarIndex1() {
        return scalarIndex1;
    }

    public void setScalarIndex1(int scalarIndex1) {
        this.scalarIndex1 = scalarIndex1;
    }

    public int scalarIndex2() {
        return scalarIndex2;
    }

    public void setScalarIndex2(int scalarIndex2) {
        this.scalarIndex2 = scalarIndex2;
    }

    public int scalarResult0() {
        return scalarResult0;
    }

    public void setScalarResult0(int scalarResult0) {
        this.scalarResult0 = scalarResult0;
    }

    public int scalarResult1() {
        return scalarResult1;
    }

    public void setScalarResult1(int scalarResult1) {
        this.scalarResult1 = scalarResult1;
    }

    public int lastCollisionTileId() {
        return lastCollisionTileId;
    }

    public int lastCollisionIndex() {
        return lastCollisionIndex;
    }

    public void setLastCollision(int tileId, int layoutIndex) {
        lastCollisionTileId = tileId;
        lastCollisionIndex = layoutIndex;
    }

    public void clearCollision() {
        lastCollisionTileId = 0;
        lastCollisionIndex = -1;
    }

    public boolean paletteCycleEnabled() {
        return paletteCycleEnabled;
    }

    public void setPaletteCycleEnabled(boolean paletteCycleEnabled) {
        this.paletteCycleEnabled = paletteCycleEnabled;
    }
}
