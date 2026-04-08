package com.openggf.game.sonic3k.bonusstage.slots;

/**
 * 32-entry animation slot table matching ROM sub_4B592 (lines 98392-98491).
 * Drives tile animations in the slot stage layout grid.
 */
public final class S3kSlotLayoutAnimator {

    private static final int MAX_SLOTS = 32;

    // Type 1: ring sparkle (byte_4B5EC)
    private static final byte[] RING_SPARKLE_FRAMES = {0x10, 0x11, 0x12, 0x13, 0};
    private static final int RING_SPARKLE_DELAY = 5;
    private static final byte RING_SPARKLE_RESTORE = 0; // ring consumed

    // Type 2: bumper bounce (byte_4B622)
    private static final byte[] BUMPER_BOUNCE_FRAMES = {0x0A, 0x0B, 0};
    private static final int BUMPER_BOUNCE_DELAY = 1;
    private static final byte BUMPER_BOUNCE_RESTORE = 5; // restore bumper tile

    // Type 4: R-label/spike animation (byte_4B656)
    private static final byte[] SPIKE_ANIM_FRAMES = {0x0C, 0x06, 0x0C, 0};
    private static final int SPIKE_ANIM_DELAY = 7;
    private static final byte SPIKE_ANIM_RESTORE = 6; // restore spike tile

    private final AnimSlot[] slots = new AnimSlot[MAX_SLOTS];

    public S3kSlotLayoutAnimator() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            slots[i] = new AnimSlot();
        }
    }

    /** Queue ring sparkle animation at the given layout index */
    public boolean queueRingSparkle(byte[] layout, int layoutIndex) {
        return allocate(layout, layoutIndex, RING_SPARKLE_FRAMES, RING_SPARKLE_DELAY, RING_SPARKLE_RESTORE);
    }

    /** Queue bumper bounce animation at the given layout index */
    public boolean queueBumperBounce(byte[] layout, int layoutIndex) {
        return allocate(layout, layoutIndex, BUMPER_BOUNCE_FRAMES, BUMPER_BOUNCE_DELAY, BUMPER_BOUNCE_RESTORE);
    }

    /** Queue spike/R-label animation at the given layout index */
    public boolean queueSpikeAnimation(byte[] layout, int layoutIndex) {
        return allocate(layout, layoutIndex, SPIKE_ANIM_FRAMES, SPIKE_ANIM_DELAY, SPIKE_ANIM_RESTORE);
    }

    /** Tick all active animation slots, updating layout tiles */
    public void tick(byte[] layout) {
        for (AnimSlot slot : slots) {
            if (!slot.active) continue;
            slot.tick(layout);
        }
    }

    /** Returns number of active animation slots */
    public int activeCount() {
        int count = 0;
        for (AnimSlot slot : slots) {
            if (slot.active) count++;
        }
        return count;
    }

    private boolean allocate(byte[] layout, int layoutIndex, byte[] frames, int delay, byte restoreTile) {
        for (AnimSlot slot : slots) {
            if (!slot.active) {
                slot.start(layoutIndex, frames, delay, restoreTile);
                // Apply first frame immediately
                if (layout != null && layoutIndex >= 0 && layoutIndex < layout.length && frames.length > 0) {
                    layout[layoutIndex] = frames[0];
                }
                return true;
            }
        }
        return false; // all slots full
    }

    private static final class AnimSlot {
        boolean active;
        int layoutIndex;
        byte[] frames;
        int delay;
        int timer;
        int frameIndex;
        byte restoreTile;

        void start(int layoutIndex, byte[] frames, int delay, byte restoreTile) {
            this.active = true;
            this.layoutIndex = layoutIndex;
            this.frames = frames;
            this.delay = delay;
            this.timer = delay;
            this.frameIndex = 0;
            this.restoreTile = restoreTile;
        }

        void tick(byte[] layout) {
            if (--timer > 0) return;
            timer = delay;
            frameIndex++;

            if (frameIndex >= frames.length || frames[frameIndex] == 0) {
                // Animation complete: restore tile and free slot
                if (layout != null && layoutIndex >= 0 && layoutIndex < layout.length) {
                    layout[layoutIndex] = restoreTile;
                }
                active = false;
                return;
            }

            // Apply current frame
            if (layout != null && layoutIndex >= 0 && layoutIndex < layout.length) {
                layout[layoutIndex] = frames[frameIndex];
            }
        }
    }
}
