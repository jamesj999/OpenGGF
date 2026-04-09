package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.physics.TrigLookupTable;

public final class S3kSlotCollisionSystem {
    static final int LAYOUT_STRIDE = 0x20;
    static final int EXPANDED_STRIDE = 0x80;
    static final int CELL_SIZE = 0x18;
    static final int COLLISION_Y_OFFSET = 0x44;
    static final int COLLISION_X_OFFSET = 0x14;
    static final int RING_Y_OFFSET = 0x50;
    static final int RING_X_OFFSET = 0x20;
    private static final int BUMPER_LAUNCH_SPEED = 0x700;
    private static final int SPIKE_THROTTLE_FRAMES = 0x1E;

    private final S3kSlotRenderBuffers renderBuffers;
    private final S3kSlotStageState stageState;

    public S3kSlotCollisionSystem(S3kSlotRenderBuffers renderBuffers, S3kSlotStageState stageState) {
        this.renderBuffers = renderBuffers;
        this.stageState = stageState;
    }

    public S3kSlotRenderBuffers renderBuffers() {
        return renderBuffers;
    }

    public void tickFrameState() {
        stageState.tickSpikeThrottleTimer();
    }

    public Collision checkCollision(int xPixel, int yPixel) {
        // Collision state is cleared once per frame by the player runtime. Multiple
        // collision probes can run in a single frame, so later plain-solid hits must
        // not erase an earlier special tile that still needs dispatch.
        byte[] expandedLayout = renderBuffers.expandedLayout();
        if (expandedLayout == null || expandedLayout.length < renderBuffers.layoutRows() * renderBuffers.layoutStrideBytes()) {
            return Collision.NONE;
        }

        int baseRow = Math.floorDiv(yPixel + COLLISION_Y_OFFSET, CELL_SIZE);
        int baseCol = Math.floorDiv(xPixel + COLLISION_X_OFFSET, CELL_SIZE);
        for (int dr = 0; dr <= 1; dr++) {
            for (int dc = 0; dc <= 1; dc++) {
                int row = baseRow + dr;
                int col = baseCol + dc;
                if (row < 0 || row >= renderBuffers.layoutRows()
                        || col < 0 || col >= renderBuffers.layoutColumns()) {
                    continue;
                }

                int expandedIndex = row * renderBuffers.layoutStrideBytes() + col;
                int compactIndex = renderBuffers.expandedToCompactIndex(expandedIndex);
                int tileId = expandedLayout[expandedIndex] & 0xFF;
                if (!isSolid(tileId)) {
                    continue;
                }

                if (isSpecial(tileId) && compactIndex >= 0) {
                    stageState.setLastCollision(tileId, compactIndex);
                }
                return new Collision(true, isSpecial(tileId), tileId, compactIndex, expandedIndex);
            }
        }

        return Collision.NONE;
    }

    public RingCheck checkRingPickup(int xPixel, int yPixel) {
        byte[] layout = renderBuffers.layout();
        byte[] expandedLayout = renderBuffers.expandedLayout();
        if (layout == null || expandedLayout == null
                || layout.length < S3kSlotRomData.SLOT_LAYOUT_SIZE * S3kSlotRomData.SLOT_LAYOUT_SIZE
                || expandedLayout.length < renderBuffers.layoutRows() * renderBuffers.layoutStrideBytes()) {
            return RingCheck.NONE;
        }

        int row = Math.floorDiv(yPixel + RING_Y_OFFSET, CELL_SIZE);
        int col = Math.floorDiv(xPixel + RING_X_OFFSET, CELL_SIZE);
        if (row < 0 || row >= renderBuffers.layoutRows()
                || col < 0 || col >= renderBuffers.layoutColumns()) {
            return RingCheck.NONE;
        }

        int compactIndex = renderBuffers.expandedToCompactIndex(row, col);
        int expandedIndex = row * renderBuffers.layoutStrideBytes() + col;
        int tileId = expandedLayout[expandedIndex] & 0xFF;
        if (tileId == 8 && compactIndex >= 0) {
            return new RingCheck(true, compactIndex, expandedIndex, tileId);
        }
        if (tileId != 0) {
            return new RingCheck(false, compactIndex, expandedIndex, tileId);
        }
        return RingCheck.NONE;
    }

    public void consumeRing(RingCheck ring) {
        if (ring == null || !ring.foundRing()) {
            return;
        }
        byte[] layout = renderBuffers.layout();
        byte[] expandedLayout = renderBuffers.expandedLayout();
        if (layout == null || expandedLayout == null) {
            return;
        }
        if (ring.layoutIndex() >= 0 && ring.layoutIndex() < layout.length) {
            layout[ring.layoutIndex()] = 0;
        }
        if (ring.expandedLayoutIndex() >= 0 && ring.expandedLayoutIndex() < expandedLayout.length) {
            expandedLayout[ring.expandedLayoutIndex()] = 0;
        }
    }

    public TileResponse resolveTileResponse(int tileId, short playerX, short playerY,
                                            short tileCenterX, short tileCenterY) {
        return switch (tileId) {
            case 5 -> bumperResponse(playerX, playerY, tileCenterX, tileCenterY);
            case 4 -> new TileResponse(Effect.GOAL_EXIT, (short) 0, (short) 0);
            case 6 -> spikeResponse();
            case 1, 2, 3 -> slotReelResponse();
            default -> TileResponse.NONE;
        };
    }

    private TileResponse bumperResponse(short playerX, short playerY, short tileCenterX, short tileCenterY) {
        int dx = tileCenterX - playerX;
        int dy = tileCenterY - playerY;
        if (dx == 0 && dy == 0) {
            return new TileResponse(Effect.BUMPER_LAUNCH, (short) 0, (short) -BUMPER_LAUNCH_SPEED);
        }
        int angle = TrigLookupTable.calcAngle((short) dx, (short) dy);
        int sin = TrigLookupTable.sinHex(angle);
        int cos = TrigLookupTable.cosHex(angle);
        short launchX = (short) ((cos * -BUMPER_LAUNCH_SPEED) >> 8);
        short launchY = (short) ((sin * -BUMPER_LAUNCH_SPEED) >> 8);
        return new TileResponse(Effect.BUMPER_LAUNCH, launchX, launchY);
    }

    private TileResponse spikeResponse() {
        if (stageState.spikeThrottleTimer() > 0) {
            return TileResponse.NONE;
        }
        stageState.setSpikeThrottleTimer(SPIKE_THROTTLE_FRAMES);
        stageState.negateScalarIndex1();
        return new TileResponse(Effect.SPIKE_REVERSAL, (short) 0, (short) 0);
    }

    private TileResponse slotReelResponse() {
        stageState.incrementSlotValue();
        return new TileResponse(Effect.SLOT_REEL_INCREMENT, (short) 0, (short) 0);
    }

    static boolean isSolid(int tileId) {
        if (tileId == 0 || tileId == 8) {
            return false;
        }
        return tileId >= 1 && tileId <= 15;
    }

    static boolean isSpecial(int tileId) {
        return tileId >= 1 && tileId <= 6;
    }

    public enum Effect {
        NONE, BUMPER_LAUNCH, GOAL_EXIT, SPIKE_REVERSAL, SLOT_REEL_INCREMENT
    }

    public record Collision(boolean solid, boolean special, int tileId,
                             int layoutIndex, int expandedLayoutIndex) {
        public static final Collision NONE = new Collision(false, false, 0, -1, -1);
    }

    public record RingCheck(boolean foundRing, int layoutIndex, int expandedLayoutIndex, int tileId) {
        public static final RingCheck NONE = new RingCheck(false, -1, -1, 0);
    }

    public record TileResponse(Effect effect, short launchXVel, short launchYVel) {
        public static final TileResponse NONE = new TileResponse(Effect.NONE, (short) 0, (short) 0);
    }
}
