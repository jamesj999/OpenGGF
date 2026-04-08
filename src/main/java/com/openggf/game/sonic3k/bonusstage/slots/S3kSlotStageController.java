package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayDeque;
import java.util.Deque;

public class S3kSlotStageController {
    private int statTable;
    private int scalarIndex;  // SStage_scalar_index_1 — rotation velocity per frame
    private int rewardCounter;
    private int latchedPrize;
    private int latchedPrizeCycleId;
    private boolean reelsFrozen;
    private boolean paletteCycleEnabled;
    private int bounceTimer;           // $3A(a0) — grace period after landing (4 frames)
    private int activeRewardObjects;
    private int pendingRingRewards;
    private int pendingSpikeRewards;

    // Positional data queued alongside each pending reward (spawnX, spawnY, targetX, targetY)
    private byte[] activeLayout;        // layout reference for collision checks during physics
    private int lastCollisionTileId;    // $30(a0) — tile ID from last collision check
    private int lastCollisionIndex = -1; // layout index of last collision
    private final Deque<int[]> pendingRingRewardPositions = new ArrayDeque<>();
    private final Deque<int[]> pendingSpikeRewardPositions = new ArrayDeque<>();

    public void bootstrap() {
        statTable = 0;
        scalarIndex = 0x40;  // ROM line 98732
        rewardCounter = 0;
        latchedPrize = 0;
        latchedPrizeCycleId = -1;
        reelsFrozen = false;
        paletteCycleEnabled = false;
        bounceTimer = 0;
        activeLayout = null;
        lastCollisionTileId = 0;
        lastCollisionIndex = -1;
        activeRewardObjects = 0;
        pendingRingRewards = 0;
        pendingSpikeRewards = 0;
        pendingRingRewardPositions.clear();
        pendingSpikeRewardPositions.clear();
    }

    /** Per-frame rotation: Stat_table += SStage_scalar_index_1 (lines 98776-98778) */
    public void tick() {
        statTable = (statTable + scalarIndex) & 0xFFFF;
    }

    public int scalarIndex() {
        return scalarIndex;
    }

    public void setScalarIndex(int value) {
        scalarIndex = value;
    }

    /** ROM: neg.w (SStage_scalar_index_1).w — used by spike tile and cage release */
    public void negateScalar() {
        scalarIndex = -scalarIndex;
    }

    /** ROM $3A(a0): Set bounce timer on landing (sub_4BCB0 line 99028/99046) */
    public void setBounceTimer(int frames) {
        bounceTimer = frames;
    }

    /** Tick bounce timer. Returns true if timer was active and just expired. */
    public boolean tickBounceTimer() {
        if (bounceTimer > 0) {
            bounceTimer--;
            return bounceTimer == 0;
        }
        return false;
    }

    public int bounceTimer() {
        return bounceTimer;
    }

    /** Set the active layout for collision checks during player physics. */
    public void setActiveLayout(byte[] layout) {
        this.activeLayout = layout;
    }

    public byte[] activeLayout() {
        return activeLayout;
    }

    /** ROM $30(a0): Store tile ID from last collision for tile interaction dispatch */
    public void setLastCollision(int tileId, int layoutIndex) {
        this.lastCollisionTileId = tileId;
        this.lastCollisionIndex = layoutIndex;
    }

    public void clearLastCollision() {
        this.lastCollisionTileId = 0;
        this.lastCollisionIndex = -1;
    }

    public int lastCollisionTileId() {
        return lastCollisionTileId;
    }

    public int lastCollisionIndex() {
        return lastCollisionIndex;
    }

    public void tickPlayer(S3kSlotBonusPlayer player, boolean left, boolean right,
                           boolean jump, int frameCounter) {
        // Input is handled by S3kSlotBonusPlayer.applyGroundMotion()
        // Controller only manages angle and jump
        player.setAngle((byte) statTable);

        if (jump && player instanceof AbstractPlayableSprite sprite
                && sprite.isJumpJustPressed() && !sprite.getAir()) {
            int angle = (-((statTable & 0xFC)) - 0x40) & 0xFF;
            sprite.setXSpeed((short) ((TrigLookupTable.cosHex(angle) * 0x680) >> 8));
            sprite.setYSpeed((short) ((TrigLookupTable.sinHex(angle) * 0x680) >> 8));
            sprite.setAir(true);
            // ROM line 98926-98927: moveq #signextendB(sfx_Jump),d0; jsr (Play_SFX).l
            if (GameServices.audio() != null) GameServices.audio().playSfx(Sonic3kSfx.JUMP.id);
        }
    }

    /** Returns the low byte of Stat_table — ROM uses move.b (Stat_table).w,d0 for angle reads */
    public int angle() {
        return statTable & 0xFF;
    }

    /** Returns the full 16-bit Stat_table value (for exit sequence scalar math) */
    public int rawStatTable() {
        return statTable & 0xFFFF;
    }

    public boolean isReelsFrozen() {
        return reelsFrozen;
    }

    public void setPaletteCycleEnabled(boolean enabled) {
        paletteCycleEnabled = enabled;
    }

    public boolean isPaletteCycleEnabled() {
        return paletteCycleEnabled;
    }

    public void latchResolvedPrize(int prize, int cycleId) {
        if (cycleId == latchedPrizeCycleId) {
            return;
        }
        latchedPrize = prize;
        latchedPrizeCycleId = cycleId;
    }

    public void latchResolvedPrizeForCapture(int prize) {
        latchedPrize = prize;
        latchedPrizeCycleId++;
    }

    public int beginCapturePayout() {
        reelsFrozen = true;
        return latchedPrize;
    }

    public void endCapturePayout() {
        reelsFrozen = false;
        latchedPrize = 0;
    }

    public void onRewardSpawned() {
        activeRewardObjects++;
    }

    public void onRewardExpired() {
        if (activeRewardObjects > 0) {
            activeRewardObjects--;
        }
    }

    public int activeRewardObjects() {
        return activeRewardObjects;
    }

    public void addRewardRing() {
        rewardCounter++;
    }

    public void queueRingReward() {
        pendingRingRewards++;
        pendingRingRewardPositions.offer(new int[0]);
    }

    /**
     * Queues a ring reward with positional data for interpolated movement.
     *
     * <p>ROM reference: cage object stores spawn position at reward allocation time
     * ({@code loc_4C172}, lines 99482-99490).
     */
    public void queueRingRewardAt(int spawnX, int spawnY, int targetX, int targetY) {
        pendingRingRewards++;
        pendingRingRewardPositions.offer(new int[]{spawnX, spawnY, targetX, targetY});
    }

    public void queueSpikeReward() {
        pendingSpikeRewards++;
        pendingSpikeRewardPositions.offer(new int[0]);
    }

    /**
     * Queues a spike reward with positional data for interpolated movement.
     *
     * <p>ROM reference: cage object stores spawn position at reward allocation time
     * ({@code loc_4C0AA}, lines 99438-99447).
     */
    public void queueSpikeRewardAt(int spawnX, int spawnY, int targetX, int targetY) {
        pendingSpikeRewards++;
        pendingSpikeRewardPositions.offer(new int[]{spawnX, spawnY, targetX, targetY});
    }

    /**
     * Consumes one pending ring reward.
     *
     * @return {@code null} if no reward is pending; {@code int[4]} with
     *         {@code {spawnX, spawnY, targetX, targetY}} if positional data was provided via
     *         {@link #queueRingRewardAt}; or {@code int[0]} if queued without position data
     *         (backward-compatible fallback — caller should use its own spawn coordinates).
     */
    public int[] consumePendingRingReward() {
        if (pendingRingRewards <= 0) {
            return null;
        }
        pendingRingRewards--;
        int[] pos = pendingRingRewardPositions.poll();
        return pos != null ? pos : new int[0];
    }

    /**
     * Consumes one pending spike reward.
     *
     * @return {@code null} if no reward is pending; {@code int[4]} with
     *         {@code {spawnX, spawnY, targetX, targetY}} if positional data was provided via
     *         {@link #queueSpikeRewardAt}; or {@code int[0]} if queued without position data
     *         (backward-compatible fallback — caller should use its own spawn coordinates).
     */
    public int[] consumePendingSpikeReward() {
        if (pendingSpikeRewards <= 0) {
            return null;
        }
        pendingSpikeRewards--;
        int[] pos = pendingSpikeRewardPositions.poll();
        return pos != null ? pos : new int[0];
    }

    public boolean consumeRewardRing() {
        if (rewardCounter <= 0) {
            return false;
        }
        rewardCounter--;
        return true;
    }

    public boolean consumeRewardRing(int carriedRingCount) {
        if (carriedRingCount + rewardCounter <= 0) {
            return false;
        }
        rewardCounter--;
        return true;
    }

    public int rewardCount() {
        return rewardCounter;
    }
}
