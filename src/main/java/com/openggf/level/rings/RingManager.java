package com.openggf.level.rings;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.spawn.AbstractPlacementManager;
import com.openggf.level.ChunkDesc;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.ShieldType;
import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.PhysicsProvider;
import com.openggf.physics.TrigLookupTable;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Handles ring collection state, sparkle animation, rendering, and lost-ring behavior.
 */
public class RingManager {
    private static final int MAX_ATTRACTED_RINGS = 32;
    // ROM: AttractedRing_Move — base acceleration is $30 subpixels/frame²
    private static final int ATTRACT_ACCEL = 0x30;
    // ROM: attraction detection uses a 128×128 box (±$40 from player centre)
    private static final int ATTRACT_BOX_HALF = 0x40;
    // ROM: ring collision half-width (d1=6 in Test_Ring_Collisions)
    private static final int RING_COLLISION_HALF = 6;

    private final RingPlacement placement;
    private final RingRenderer renderer;
    private final LostRingPool lostRings;
    private PatternSpriteRenderer.FrameBounds spinBounds;
    private final AttractedRing[] attractedRings;


    public RingManager(List<RingSpawn> spawns, RingSpriteSheet spriteSheet,
                       LevelManager levelManager, TouchResponseTable touchResponseTable) {
        this.placement = new RingPlacement(spawns);
        this.renderer = (spriteSheet != null && spriteSheet.getFrameCount() > 0)
                ? new RingRenderer(spriteSheet)
                : null;
        this.lostRings = new LostRingPool(levelManager, this.renderer, touchResponseTable);
        this.attractedRings = new AttractedRing[MAX_ATTRACTED_RINGS];
        for (int i = 0; i < MAX_ATTRACTED_RINGS; i++) {
            attractedRings[i] = new AttractedRing();
        }
    }

    public void reset(int cameraX) {
        placement.reset(cameraX);
        lostRings.reset();
        spinBounds = null;
        for (AttractedRing ar : attractedRings) {
            ar.active = false;
        }
    }

    /**
     * Replaces the ring spawn list with a new one from the editor.
     * Resets all collection state (collected BitSet, sparkle frames).
     * In editor mode this is acceptable -- all rings become uncollected.
     */
    public void resyncSpawnList(List<RingSpawn> newSpawns) {
        placement.replaceSpawnsAndReset(newSpawns);
        lostRings.reset();
    }

    public void ensurePatternsCached(GraphicsManager graphicsManager, int basePatternIndex) {
        if (renderer != null) {
            renderer.ensurePatternsCached(graphicsManager, basePatternIndex);
        }
    }

    public void update(int cameraX, AbstractPlayableSprite player, int frameCounter) {
        placement.update(cameraX);
        if (player == null || player.getDead() || renderer == null) {
            return;
        }

        PatternSpriteRenderer.FrameBounds bounds = getSpinBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }

        Collection<RingSpawn> active = placement.getActiveSpawns();
        if (active.isEmpty()) {
            return;
        }

        int playerLeft = player.getX();
        int playerTop = player.getY();
        int playerRight = playerLeft + player.getWidth();
        int playerBottom = playerTop + player.getHeight();

        for (RingSpawn ring : active) {
            int index = placement.getSpawnIndex(ring);
            if (index < 0 || placement.isCollected(index)) {
                continue;
            }

            int ringLeft = ring.x() + bounds.minX();
            int ringRight = ring.x() + bounds.maxX();
            int ringTop = ring.y() + bounds.minY();
            int ringBottom = ring.y() + bounds.maxY();

            if (playerRight < ringLeft || playerLeft > ringRight || playerBottom < ringTop || playerTop > ringBottom) {
                continue;
            }

            placement.markCollected(index);
            if (renderer.getSparkleFrameCount() > 0) {
                placement.setSparkleStartFrame(index, frameCounter);
            }
            AudioManager.getInstance().playSfx(GameSound.RING);
            player.addRings(1);
        }

        // Lightning shield ring attraction — S3K only
        PhysicsFeatureSet featureSet = null;
        PhysicsProvider physProvider = GameServices.module().getPhysicsProvider();
        if (physProvider != null) {
            featureSet = physProvider.getFeatureSet();
        }
        if (featureSet != null && featureSet.lightningShieldEnabled()
                && player.getShieldType() == ShieldType.LIGHTNING) {
            int pcx = player.getCentreX();
            int pcy = player.getCentreY();
            for (RingSpawn ring : active) {
                int index = placement.getSpawnIndex(ring);
                if (index < 0 || placement.isCollected(index)) {
                    continue;
                }
                int dx = pcx - ring.x();
                int dy = pcy - ring.y();
                // ROM: box check — ±$40 from player centre, extended by ring half-width
                int ringHalf = featureSet != null ? featureSet.ringCollisionWidth() : RING_COLLISION_HALF;
                int effectiveHalf = ATTRACT_BOX_HALF + ringHalf;
                if (Math.abs(dx) <= effectiveHalf && Math.abs(dy) <= effectiveHalf) {
                    placement.markCollected(index);
                    addAttractedRing(index, ring.x(), ring.y());
                }
            }
            updateAttractedRings(player, frameCounter);
        }
    }

    public void updateLostRingPhysics(int frameCounter) {
        lostRings.updatePhysics(frameCounter);
    }

    public void checkLostRingCollection(AbstractPlayableSprite player) {
        lostRings.checkCollection(player);
    }

    /** @deprecated Use {@link #updateLostRingPhysics} + {@link #checkLostRingCollection} instead. */
    @Deprecated
    public void updateLostRings(AbstractPlayableSprite player, int frameCounter) {
        lostRings.updatePhysics(frameCounter);
        lostRings.checkCollection(player);
    }

    public void draw(int frameCounter) {
        if (renderer == null) {
            return;
        }
        Collection<RingSpawn> active = placement.getActiveSpawns();
        if (active == null || active.isEmpty()) {
            return;
        }

        int spinFrameIndex = renderer.getSpinFrameIndex(frameCounter);
        for (RingSpawn ring : active) {
            int index = placement.getSpawnIndex(ring);
            if (index < 0) {
                continue;
            }
            if (!placement.isCollected(index)) {
                renderer.drawFrameIndex(spinFrameIndex, ring.x(), ring.y());
                continue;
            }

            int sparkleStartFrame = placement.getSparkleStartFrame(index);
            if (sparkleStartFrame < 0 || renderer.getSparkleFrameCount() <= 0) {
                continue;
            }

            int elapsed = frameCounter - sparkleStartFrame;
            if (elapsed < 0) {
                elapsed = 0;
            }
            int sparkleFrameOffset = elapsed / renderer.getSparkleFrameDelay();
            if (sparkleFrameOffset >= renderer.getSparkleFrameCount()) {
                placement.clearSparkle(index);
                continue;
            }
            int sparkleFrameIndex = renderer.getSparkleStartIndex() + sparkleFrameOffset;
            renderer.drawFrameIndex(sparkleFrameIndex, ring.x(), ring.y());
        }

        // Draw attracted rings (being pulled toward player)
        int attractSpinFrame = renderer.getSpinFrameIndex(frameCounter);
        for (AttractedRing ar : attractedRings) {
            if (ar.active) {
                renderer.drawFrameIndex(attractSpinFrame, ar.x, ar.y);
            }
        }
    }

    public void drawLostRings(int frameCounter) {
        lostRings.draw(frameCounter);
    }

    /**
     * Draw a ring sprite at a specific position (for prize rings, etc.).
     * Uses the animated spin frame based on the frame counter.
     *
     * @param x            Screen X position (center of ring)
     * @param y            Screen Y position (center of ring)
     * @param frameCounter Current frame counter for animation
     */
    public void drawRingAt(int x, int y, int frameCounter) {
        if (renderer == null) {
            return;
        }
        int spinFrameIndex = renderer.getSpinFrameIndex(frameCounter);
        renderer.drawFrameIndex(spinFrameIndex, x, y);
    }

    /**
     * Draw a sparkle animation at a specific position (for collected prize rings, etc.).
     * Calculates the sparkle frame based on elapsed frames since sparkle started.
     *
     * @param x                  Screen X position (center of sparkle)
     * @param y                  Screen Y position (center of sparkle)
     * @param sparkleFrameOffset Frame offset into sparkle animation (0 = first sparkle frame)
     */
    public void drawSparkleAt(int x, int y, int sparkleFrameOffset) {
        if (renderer == null || renderer.getSparkleFrameCount() <= 0) {
            return;
        }
        int frameIndex = renderer.getSparkleStartIndex() +
                (sparkleFrameOffset % renderer.getSparkleFrameCount());
        renderer.drawFrameIndex(frameIndex, x, y);
    }

    /**
     * Check if ring rendering is available.
     */
    public boolean canRenderRings() {
        return renderer != null;
    }

    public void spawnLostRings(AbstractPlayableSprite player, int ringCount, int frameCounter) {
        lostRings.spawnLostRings(player, ringCount, frameCounter);
    }

    public boolean areAllCollected() {
        return placement.areAllCollected();
    }

    public boolean isRenderable(RingSpawn ring, int frameCounter) {
        if (ring == null) {
            return false;
        }
        int index = placement.getSpawnIndex(ring);
        if (index < 0) {
            return false;
        }
        if (!placement.isCollected(index)) {
            return true;
        }
        int sparkleStartFrame = placement.getSparkleStartFrame(index);
        if (sparkleStartFrame < 0 || renderer == null || renderer.getSparkleFrameCount() <= 0) {
            return false;
        }
        int elapsed = frameCounter - sparkleStartFrame;
        if (elapsed < 0) {
            return true;
        }
        int sparkleFrameOffset = elapsed / renderer.getSparkleFrameDelay();
        if (sparkleFrameOffset >= renderer.getSparkleFrameCount()) {
            placement.clearSparkle(index);
            return false;
        }
        return true;
    }

    public boolean isCollected(RingSpawn ring) {
        if (ring == null) {
            return false;
        }
        int index = placement.getSpawnIndex(ring);
        return placement.isCollected(index);
    }

    /**
     * Checks whether a ring at the given position has been collected.
     * Used by Sonic1RingInstance to detect collection
     * without any frame counter dependency.
     */
    public boolean isRingCollected(int x, int y) {
        RingSpawn probe = new RingSpawn(x, y);
        int index = placement.getSpawnIndex(probe);
        return index >= 0 && placement.isCollected(index);
    }

    public int getSparkleStartFrame(RingSpawn ring) {
        if (ring == null) {
            return -1;
        }
        int index = placement.getSpawnIndex(ring);
        return placement.getSparkleStartFrame(index);
    }

    /**
     * ROM parity: checks whether a ring at the given position has been collected
     * AND its sparkle animation has finished (equivalent to ROM's DeleteObject
     * call at the end of Ring_Sparkle).
     * <p>
     * Used by Sonic1RingInstance to free SST slots at the correct time,
     * matching the ROM's slot lifecycle where a ring's slot is freed only after
     * the sparkle animation completes.
     *
     * @param x            ring X position
     * @param y            ring Y position
     * @param frameCounter current frame counter
     * @return true if the ring was collected and sparkle has finished
     */
    public boolean isCollectedAndSparkleDone(int x, int y, int frameCounter) {
        RingSpawn probe = new RingSpawn(x, y);
        int index = placement.getSpawnIndex(probe);
        if (index < 0 || !placement.isCollected(index)) {
            return false;
        }
        int sparkleStart = placement.getSparkleStartFrame(index);
        if (sparkleStart < 0) {
            // Sparkle already cleared (or no sparkle) — collection is done
            return true;
        }
        if (renderer == null || renderer.getSparkleFrameCount() <= 0) {
            return true;
        }
        int elapsed = frameCounter - sparkleStart;
        // ROM parity: Ani_Ring sparkle uses its own delay byte (5 in S1 = 6 VBlanks/frame
        // via AnimateSprite), distinct from SynchroAnimate's spin rate (8 VBlanks/frame).
        // After sparkleFrameCount frames × sparkleDelay VBlanks, the afRoutine command
        // fires but the ring still displays for one more frame (DisplaySprite runs).
        // Ring_Delete runs on the NEXT frame, calling DeleteObject to free the SST slot.
        // Total duration: sparkleFrameCount * sparkleDelay + 1.
        int sparkleDelay = renderer.getSparkleFrameDelay();
        int totalDuration = renderer.getSparkleFrameCount() * sparkleDelay + 1;
        return elapsed >= totalDuration;
    }

    public PatternSpriteRenderer.FrameBounds getSpinBounds() {
        if (spinBounds == null) {
            spinBounds = renderer != null ? renderer.getSpinBounds() : new PatternSpriteRenderer.FrameBounds(0, 0, 0, 0);
        }
        return spinBounds;
    }

    public PatternSpriteRenderer.FrameBounds getFrameBounds(int frameCounter) {
        if (renderer == null) {
            return new PatternSpriteRenderer.FrameBounds(0, 0, 0, 0);
        }
        return renderer.getFrameBounds(frameCounter);
    }

    public int getSparkleStartIndex() {
        return renderer != null ? renderer.getSparkleStartIndex() : 0;
    }

    public int getSparkleFrameCount() {
        return renderer != null ? renderer.getSparkleFrameCount() : 0;
    }

    public int getFrameDelay() {
        return renderer != null ? renderer.getFrameDelay() : 1;
    }

    public int getSparkleFrameDelay() {
        return renderer != null ? renderer.getSparkleFrameDelay() : 1;
    }

    public void drawFrameIndex(int frameIndex, int originX, int originY) {
        if (renderer != null) {
            renderer.drawFrameIndex(frameIndex, originX, originY);
        }
    }

    public boolean hasRenderer() {
        return renderer != null;
    }

    public Collection<RingSpawn> getActiveSpawns() {
        return placement.getActiveSpawns();
    }

    private void addAttractedRing(int sourceIndex, int x, int y) {
        for (AttractedRing ar : attractedRings) {
            if (!ar.active) {
                ar.sourceIndex = sourceIndex;
                ar.x = x;
                ar.y = y;
                ar.xSub = 0;
                ar.ySub = 0;
                ar.xVel = 0;
                ar.yVel = 0;
                ar.active = true;
                return;
            }
        }
    }

    /**
     * ROM: AttractedRing_Move (sonic3k.asm:35795).
     * Per-axis acceleration of $30 subpixels/frame². When the ring's velocity
     * opposes the direction to the player, acceleration is 4× stronger to
     * reverse quickly. Position updated via MoveSprite2 (velocity→subpixel).
     */
    private void updateAttractedRings(AbstractPlayableSprite player, int frameCounter) {
        int pcx = player.getCentreX();
        int pcy = player.getCentreY();
        for (AttractedRing ar : attractedRings) {
            if (!ar.active) continue;

            // --- X axis acceleration (AttractedRing_Move) ---
            int accelX = ATTRACT_ACCEL;
            if (pcx >= ar.x) {
                // Player is right of ring: accelerate right (+)
                if (ar.xVel < 0) {
                    // Moving wrong way: 4× to reverse
                    accelX *= 4;
                }
            } else {
                // Player is left of ring: accelerate left (-)
                accelX = -accelX;
                if (ar.xVel >= 0) {
                    accelX *= 4;
                }
            }
            ar.xVel = (short) (ar.xVel + accelX);

            // --- Y axis acceleration ---
            int accelY = ATTRACT_ACCEL;
            if (pcy >= ar.y) {
                if (ar.yVel < 0) {
                    accelY *= 4;
                }
            } else {
                accelY = -accelY;
                if (ar.yVel >= 0) {
                    accelY *= 4;
                }
            }
            ar.yVel = (short) (ar.yVel + accelY);

            // --- MoveSprite2: apply velocity to position (subpixel precision) ---
            int xLong = (ar.x << 16) | (ar.xSub & 0xFFFF);
            xLong += ar.xVel << 8;
            ar.x = xLong >> 16;
            ar.xSub = xLong & 0xFFFF;

            int yLong = (ar.y << 16) | (ar.ySub & 0xFFFF);
            yLong += ar.yVel << 8;
            ar.y = yLong >> 16;
            ar.ySub = yLong & 0xFFFF;

            // --- Collection: ROM uses collision_flags $47 (touch response) ---
            // Check overlap between ring (8×8) and player hitbox
            int dx = Math.abs(pcx - ar.x);
            int dy = Math.abs(pcy - ar.y);
            if (dx < 8 + player.getXRadius() && dy < 8 + player.getYRadius()) {
                player.addRings(1);
                AudioManager.getInstance().playSfx(GameSound.RING);
                ar.active = false;
            }
        }
    }

    private static final class AttractedRing {
        int sourceIndex;
        int x, y;
        int xSub, ySub;    // subpixel fraction (ROM: x_sub/y_sub, lower word of position long)
        int xVel, yVel;    // velocity in subpixels/frame (ROM: x_vel/y_vel, 16-bit signed)
        boolean active;
    }

    private static final class RingPlacement extends AbstractPlacementManager<RingSpawn> {
        private static final int LOAD_AHEAD = 0x280;
        private static final int UNLOAD_BEHIND = 0x300;
        private static final int NO_SPARKLE = -1;

        private final BitSet collected = new BitSet();
        private int[] sparkleStartFrames;
        private int cursorIndex = 0;
        private int lastCameraX = Integer.MIN_VALUE;

        private RingPlacement(List<RingSpawn> spawns) {
            super(spawns, LOAD_AHEAD, UNLOAD_BEHIND);
            this.sparkleStartFrames = new int[this.spawns.size()];
            Arrays.fill(this.sparkleStartFrames, NO_SPARKLE);
        }

        /** Replaces spawns and resets all collection/sparkle state. */
        private void replaceSpawnsAndReset(List<RingSpawn> newSpawns) {
            replaceSpawns(newSpawns);
            collected.clear();
            sparkleStartFrames = new int[this.spawns.size()];
            Arrays.fill(sparkleStartFrames, NO_SPARKLE);
            cursorIndex = 0;
            lastCameraX = Integer.MIN_VALUE;
        }

        private void reset(int cameraX) {
            active.clear();
            collected.clear();
            Arrays.fill(sparkleStartFrames, NO_SPARKLE);
            cursorIndex = 0;
            lastCameraX = cameraX;
            refreshWindow(cameraX);
        }

        private boolean isCollected(int index) {
            return index >= 0 && collected.get(index);
        }

        private void markCollected(int index) {
            if (index >= 0) {
                collected.set(index);
            }
        }

        private int getSparkleStartFrame(int index) {
            if (index < 0 || index >= sparkleStartFrames.length) {
                return NO_SPARKLE;
            }
            return sparkleStartFrames[index];
        }

        private void setSparkleStartFrame(int index, int startFrame) {
            if (index < 0 || index >= sparkleStartFrames.length) {
                return;
            }
            sparkleStartFrames[index] = startFrame;
        }

        private void clearSparkle(int index) {
            if (index < 0 || index >= sparkleStartFrames.length) {
                return;
            }
            sparkleStartFrames[index] = NO_SPARKLE;
        }

        private void update(int cameraX) {
            if (spawns.isEmpty()) {
                return;
            }
            if (lastCameraX == Integer.MIN_VALUE) {
                reset(cameraX);
                return;
            }

            int delta = cameraX - lastCameraX;
            if (delta < 0 || delta > (getLoadAhead() + getUnloadBehind())) {
                refreshWindow(cameraX);
            } else {
                spawnForward(cameraX);
                trimActive(cameraX);
            }

            lastCameraX = cameraX;
        }

        private void spawnForward(int cameraX) {
            int spawnLimit = getWindowEnd(cameraX);
            while (cursorIndex < spawns.size() && spawns.get(cursorIndex).x() <= spawnLimit) {
                active.add(spawns.get(cursorIndex));
                cursorIndex++;
            }
        }

        private void trimActive(int cameraX) {
            int windowStart = getWindowStart(cameraX);
            int windowEnd = getWindowEnd(cameraX);
            Iterator<RingSpawn> iterator = active.iterator();
            while (iterator.hasNext()) {
                RingSpawn spawn = iterator.next();
                if (spawn.x() < windowStart || spawn.x() > windowEnd) {
                    iterator.remove();
                }
            }
        }

        private void refreshWindow(int cameraX) {
            int windowStart = getWindowStart(cameraX);
            int windowEnd = getWindowEnd(cameraX);
            int start = lowerBound(windowStart);
            int end = upperBound(windowEnd);
            cursorIndex = end;
            active.clear();
            for (int i = start; i < end; i++) {
                active.add(spawns.get(i));
            }
        }

        private boolean areAllCollected() {
            return !spawns.isEmpty() && collected.cardinality() >= spawns.size();
        }
    }

    private static final class RingRenderer {
        private final RingSpriteSheet spriteSheet;
        private final PatternSpriteRenderer renderer;
        private PatternSpriteRenderer.FrameBounds spinBoundsCache;

        private RingRenderer(RingSpriteSheet spriteSheet) {
            this.spriteSheet = spriteSheet;
            this.renderer = new PatternSpriteRenderer(spriteSheet);
        }

        private void ensurePatternsCached(GraphicsManager graphicsManager, int basePatternIndex) {
            renderer.ensurePatternsCached(graphicsManager, basePatternIndex);
        }

        private int getSpinFrameCount() {
            int count = spriteSheet.getSpinFrameCount();
            return (count > 0) ? count : spriteSheet.getFrameCount();
        }

        private int getSpinFrameIndex(int frameCounter) {
            int frameCount = getSpinFrameCount();
            if (frameCount <= 0) {
                return 0;
            }
            int delay = Math.max(1, spriteSheet.getFrameDelay());
            return (frameCounter / delay) % frameCount;
        }

        private PatternSpriteRenderer.FrameBounds getFrameBounds(int frameCounter) {
            return renderer.getFrameBoundsForIndex(getSpinFrameIndex(frameCounter));
        }

        private PatternSpriteRenderer.FrameBounds getSpinBounds() {
            if (spinBoundsCache != null) {
                return spinBoundsCache;
            }
            int spinCount = spriteSheet.getSpinFrameCount();
            if (spinCount <= 0) {
                spinCount = spriteSheet.getFrameCount();
            }
            if (spinCount <= 0) {
                spinBoundsCache = new PatternSpriteRenderer.FrameBounds(0, 0, 0, 0);
                return spinBoundsCache;
            }
            boolean first = true;
            int minX = 0;
            int minY = 0;
            int maxX = 0;
            int maxY = 0;
            for (int i = 0; i < spinCount; i++) {
                PatternSpriteRenderer.FrameBounds bounds = renderer.getFrameBoundsForIndex(i);
                if (bounds.width() <= 0 || bounds.height() <= 0) {
                    continue;
                }
                if (first) {
                    minX = bounds.minX();
                    minY = bounds.minY();
                    maxX = bounds.maxX();
                    maxY = bounds.maxY();
                    first = false;
                } else {
                    minX = Math.min(minX, bounds.minX());
                    minY = Math.min(minY, bounds.minY());
                    maxX = Math.max(maxX, bounds.maxX());
                    maxY = Math.max(maxY, bounds.maxY());
                }
            }
            spinBoundsCache = first ? new PatternSpriteRenderer.FrameBounds(0, 0, 0, 0)
                    : new PatternSpriteRenderer.FrameBounds(minX, minY, maxX, maxY);
            return spinBoundsCache;
        }

        private void drawFrameIndex(int frameIndex, int originX, int originY) {
            renderer.drawFrameIndex(frameIndex, originX, originY);
        }

        private int getSparkleStartIndex() {
            return spriteSheet.getSparkleStartIndex();
        }

        private int getSparkleFrameCount() {
            return spriteSheet.getSparkleFrameCount();
        }

        private int getFrameDelay() {
            return Math.max(1, spriteSheet.getFrameDelay());
        }

        private int getSparkleFrameDelay() {
            return Math.max(1, spriteSheet.getSparkleFrameDelay());
        }
    }

    private static final class LostRingPool {
        private static final int MAX_LOST_RINGS = 0x20;
        private static final int GRAVITY = 0x18;
        private static final int LIFETIME_FRAMES = 0xFF;
        private static final int RING_TOUCH_SIZE_INDEX = 0x07;
        private static final int SOLIDITY_TOP = 0x0C;
        // ROM: Obj37_Init sets y_radius(a1) = 8. RingCheckFloorDist adds y_radius
        // to y_pos before probing, so it checks from the ring's bottom edge.
        private static final int RING_Y_RADIUS = 8;

        private final LevelManager levelManager;
        private final RingRenderer renderer;
        private final TouchResponseTable touchResponseTable;
        private final AudioManager audioManager = AudioManager.getInstance();
        private final Camera camera = Camera.getInstance();
        private final LostRing[] ringPool = new LostRing[MAX_LOST_RINGS];
        private int activeRingCount = 0;
        private int nextId;
        // ROM-accurate shared animation state (Ring_spill_anim_counter/accum/frame).
        // The counter doubles as both lifetime and animation speed input:
        // accumulator increases by counter each frame, producing a decelerating spin.
        private int spillAnimCounter;
        private int spillAnimAccum;
        private int spillAnimFrame;
        private int frameCounter;

        private LostRingPool(LevelManager levelManager, RingRenderer renderer, TouchResponseTable touchResponseTable) {
            this.levelManager = levelManager;
            this.renderer = renderer;
            this.touchResponseTable = touchResponseTable;
            for (int i = 0; i < MAX_LOST_RINGS; i++) {
                ringPool[i] = new LostRing();
            }
        }

        private void reset() {
            activeRingCount = 0;
        }

        private void spawnLostRings(AbstractPlayableSprite player, int ringCount, int frameCounter) {
            if (player == null || renderer == null) {
                return;
            }
            if (ringCount <= 0) {
                return;
            }
            int count = Math.min(ringCount, MAX_LOST_RINGS);
            int angle = 0x288;
            int xVel = 0;
            int yVel = 0;

            activeRingCount = 0;
            // ROM: Ring_spill_anim_counter = $FF, accumulator reset
            spillAnimCounter = LIFETIME_FRAMES;
            spillAnimAccum = 0;
            spillAnimFrame = 0;

            for (int i = 0; i < count; i++) {
                if (angle >= 0) {
                    int sin = calcSine(angle & 0xFF);
                    int cos = calcCosine(angle & 0xFF);
                    int scale = (angle >> 8) & 0xFF;
                    xVel = sin << scale;
                    yVel = cos << scale;
                    // ROM: addi.b #$10,d4 — byte-only add, high byte (scale) unchanged.
                    // Only the low byte increments; carry from the byte add triggers
                    // the $80 subtraction which eventually drops the scale 2→1→0.
                    int lowByte = (angle & 0xFF) + 0x10;
                    boolean carry = lowByte > 0xFF;
                    angle = (angle & 0xFF00) | (lowByte & 0xFF);
                    if (carry) {
                        angle -= 0x80;
                        if (angle < 0) {
                            angle = 0x288;
                        }
                    }
                }

                ringPool[activeRingCount].reset(nextId++, player.getCentreX(), player.getCentreY(),
                        xVel, yVel, LIFETIME_FRAMES);
                activeRingCount++;
                xVel = -xVel;
                angle = -angle;
            }

            player.setRingCount(0);
            audioManager.playSfx(GameSound.RING_SPILL);
        }

        private void updatePhysics(int frameCounter) {
            this.frameCounter = frameCounter;
            if (renderer == null || activeRingCount == 0) {
                return;
            }

            PatternSpriteRenderer.FrameBounds bounds = renderer.getSpinBounds();
            if (bounds.width() <= 0 || bounds.height() <= 0) {
                return;
            }

            // ROM: ChangeRingFrame — shared animation driven by countdown counter.
            // Accumulator adds the counter value each frame, producing a decelerating
            // spin (fast when counter is high, slow as it approaches 0).
            if (spillAnimCounter > 0) {
                spillAnimAccum = (spillAnimAccum + spillAnimCounter) & 0xFFFF;
                // ROM: rol.w #7,d0 / andi.w #3,d0 → extracts bits 10:9
                spillAnimFrame = (spillAnimAccum >> 9) & 3;
                spillAnimCounter--;
            }

            // Per-game floor check frequency: S1 every 4 frames (#3), S2/S3K every 8 (#7).
            int floorCheckMask = PhysicsFeatureSet.RING_FLOOR_CHECK_MASK_S2; // default S2
            PhysicsProvider physProvider = GameServices.module().getPhysicsProvider();
            PhysicsFeatureSet featureSet = physProvider != null ? physProvider.getFeatureSet() : null;
            if (featureSet != null) {
                floorCheckMask = featureSet.ringFloorCheckMask();
            }

            // S3K: Reverse_gravity_flag gates Obj_Bouncing_Ring_Reverse_Gravity variant.
            boolean reverseGravity = GameServices.gameState().isReverseGravityActive();
            int gravity = reverseGravity ? -GRAVITY : GRAVITY;

            int cameraBottom = camera.getMaxY() + 224;

            for (int i = 0; i < activeRingCount; i++) {
                LostRing ring = ringPool[i];
                if (!ring.isActive()) {
                    continue;
                }

                if (!ring.isCollected()) {
                    ring.addXSubpixel(ring.getXVel());
                    ring.addYSubpixel(ring.getYVel());
                    ring.addYVel(gravity);

                    if (((frameCounter + ring.getId()) & floorCheckMask) == 0) {
                        if (reverseGravity) {
                            // S3K reverse gravity: check ceiling (probe from top edge, y - y_radius).
                            // ROM: RingCheckFloorDist_ReverseGravity subtracts y_radius, probes upward.
                            if (ring.getYVel() <= 0) {
                                int dist = ringCheckCeilingDist(ring.getX(), ring.getY() - RING_Y_RADIUS);
                                if (dist < 0) {
                                    ring.addYSubpixel(-dist << 8);
                                    int yVel = ring.getYVel();
                                    yVel -= (yVel >> 2);
                                    ring.setYVel(-yVel);
                                }
                            }
                        } else {
                            // Normal gravity: check floor (probe from bottom edge, y + y_radius).
                            if (ring.getYVel() >= 0) {
                                int dist = ringCheckFloorDist(ring.getX(), ring.getY() + RING_Y_RADIUS);
                                if (dist < 0) {
                                    ring.addYSubpixel(dist << 8);
                                    int yVel = ring.getYVel();
                                    yVel -= (yVel >> 2);
                                    ring.setYVel(-yVel);
                                }
                            }
                        }
                    }
                }

                ring.decLifetime();
                // ROM: tst.b (Ring_spill_anim_counter).w / beq.s Obj37_Delete
                if (ring.getLifetime() <= 0 || ring.getY() > cameraBottom) {
                    ring.deactivate();
                }
            }
        }

        private void checkCollection(AbstractPlayableSprite player) {
            if (activeRingCount == 0 || player == null || player.getDead()) {
                return;
            }

            int baseYRadius = Math.max(1, player.getYRadius() - 3);
            int playerX = player.getCentreX() - 8;
            // ROM: d3 = y_pos - (y_radius - 3) (s2.asm:84487-84493)
            int playerY = player.getCentreY() - baseYRadius;
            int playerHeight = baseYRadius * 2;
            if (player.getCrouching()) {
                playerY += 12;
                playerHeight = 20;
            }

            for (int i = 0; i < activeRingCount; i++) {
                LostRing ring = ringPool[i];
                if (!ring.isActive() || ring.isCollected()) {
                    continue;
                }

                // ROM: Touch_ChkValue (s2.asm:84750-84756) — collection gated only
                // by invulnerable_time >= 90 (0x5A). No per-ring age delay exists.
                if (player.getInvulnerableFrames() < 90
                        && ringOverlapsPlayer(playerX, playerY, playerHeight, ring)) {
                    ring.markCollected(frameCounter);
                    player.addRings(1);
                    audioManager.playSfx(GameSound.RING);
                }
            }
        }

        private void draw(int frameCounter) {
            if (renderer == null || activeRingCount == 0) {
                return;
            }

            // ROM: scattered rings use shared spillAnimFrame (0-3) driven by the
            // decelerating accumulator, NOT the constant-speed placed-ring animation.
            // Clamp to available spin frames in case sprite sheet differs.
            int spinCount = renderer.getSpinFrameCount();
            int spinFrameIndex = (spinCount > 0) ? (spillAnimFrame % spinCount) : 0;

            for (int i = 0; i < activeRingCount; i++) {
                LostRing ring = ringPool[i];
                if (!ring.isActive()) {
                    continue;
                }

                if (!ring.isCollected()) {
                    // ROM: Obj37_Main always calls DisplaySprite — no blink effect.
                    // Rings display every frame until the counter hits 0, then delete.
                    renderer.drawFrameIndex(spinFrameIndex, ring.getX(), ring.getY());
                    continue;
                }

                int sparkleStartFrame = ring.getSparkleStartFrame();
                if (sparkleStartFrame < 0 || renderer.getSparkleFrameCount() <= 0) {
                    continue;
                }

                int elapsed = frameCounter - sparkleStartFrame;
                if (elapsed < 0) {
                    elapsed = 0;
                }
                int sparkleFrameOffset = elapsed / renderer.getSparkleFrameDelay();
                if (sparkleFrameOffset >= renderer.getSparkleFrameCount()) {
                    ring.deactivate();
                    continue;
                }
                int sparkleFrameIndex = renderer.getSparkleStartIndex() + sparkleFrameOffset;
                renderer.drawFrameIndex(sparkleFrameIndex, ring.getX(), ring.getY());
            }
        }

        private boolean ringOverlapsPlayer(int playerX, int playerY, int playerHeight, LostRing ring) {
            int width = touchResponseTable != null ? touchResponseTable.getWidthRadius(RING_TOUCH_SIZE_INDEX) : 6;
            int height = touchResponseTable != null ? touchResponseTable.getHeightRadius(RING_TOUCH_SIZE_INDEX) : 6;
            int dx = ring.getX() - width - playerX;
            if (dx < 0) {
                int sum = (dx & 0xFFFF) + ((width * 2) & 0xFFFF);
                if (sum <= 0xFFFF) {
                    return false;
                }
            } else if (dx > 0x10) {
                return false;
            }

            int dy = ring.getY() - height - playerY;
            if (dy < 0) {
                int sum = (dy & 0xFFFF) + ((height * 2) & 0xFFFF);
                if (sum <= 0xFFFF) {
                    return false;
                }
            } else if (dy > playerHeight) {
                return false;
            }

            return true;
        }

        private int ringCheckFloorDist(int x, int y) {
            if (levelManager == null) {
                return 0;
            }
            ChunkDesc chunkDesc = levelManager.getChunkDescAt((byte) 0, x, y);
            SolidTile tile = getSolidTile(chunkDesc, SOLIDITY_TOP);
            SensorMetric metric = getMetric(tile, chunkDesc, x, y);
            if (metric.metric == 0) {
                return 0;
            }
            if (metric.metric == 16) {
                // ROM: sub.w a3,d2 with a3=$10 → check tile above
                int prevY = y - 16;
                ChunkDesc prevDesc = levelManager.getChunkDescAt((byte) 0, x, prevY);
                SolidTile prevTile = getSolidTile(prevDesc, SOLIDITY_TOP);
                SensorMetric prevMetric = getMetric(prevTile, prevDesc, x, prevY);
                if (prevMetric.metric > 0 && prevMetric.metric < 16) {
                    return calculateDistance(prevMetric.metric, x, y, prevY);
                }
                return calculateDistance(metric.metric, x, y, y);
            }
            return calculateDistance(metric.metric, x, y, y);
        }

        /**
         * Ceiling distance check for S3K reverse gravity rings.
         * ROM: RingCheckFloorDist_ReverseGravity — same as floor check but probes
         * upward (stride -$10 → check tile below when fully solid).
         */
        private int ringCheckCeilingDist(int x, int y) {
            if (levelManager == null) {
                return 0;
            }
            ChunkDesc chunkDesc = levelManager.getChunkDescAt((byte) 0, x, y);
            SolidTile tile = getSolidTile(chunkDesc, SOLIDITY_TOP);
            SensorMetric metric = getMetric(tile, chunkDesc, x, y);
            if (metric.metric == 0) {
                return 0;
            }
            if (metric.metric == 16) {
                // ROM: sub.w a3,d2 with a3=-$10 → check tile below
                int nextY = y + 16;
                ChunkDesc nextDesc = levelManager.getChunkDescAt((byte) 0, x, nextY);
                SolidTile nextTile = getSolidTile(nextDesc, SOLIDITY_TOP);
                SensorMetric nextMetric = getMetric(nextTile, nextDesc, x, nextY);
                if (nextMetric.metric > 0 && nextMetric.metric < 16) {
                    return calculateDistance(nextMetric.metric, x, y, nextY);
                }
                return calculateDistance(metric.metric, x, y, y);
            }
            return calculateDistance(metric.metric, x, y, y);
        }

        private SolidTile getSolidTile(ChunkDesc chunkDesc, int solidityBitIndex) {
            if (chunkDesc == null || !chunkDesc.isSolidityBitSet(solidityBitIndex)) {
                return null;
            }
            return levelManager.getSolidTileForChunkDesc(chunkDesc, solidityBitIndex);
        }

        private SensorMetric getMetric(SolidTile tile, ChunkDesc desc, int x, int y) {
            if (tile == null) {
                return new SensorMetric((byte) 0);
            }
            int index = x & 0x0F;
            if (desc != null && desc.getHFlip()) {
                index = 15 - index;
            }
            byte metric = tile.getHeightAt((byte) index);
            if (metric != 0 && metric != 16) {
                boolean invert = (desc != null && desc.getVFlip());
                if (invert) {
                    metric = (byte) (16 - metric);
                }
            }
            return new SensorMetric(metric);
        }

        private int calculateDistance(byte metric, int x, int y, int checkY) {
            int tileY = checkY & ~0x0F;
            return (tileY + 16 - metric) - y;
        }

        private int calcSine(int angle) {
            return TrigLookupTable.sinHex(angle);
        }

        private int calcCosine(int angle) {
            return TrigLookupTable.cosHex(angle);
        }

        private record SensorMetric(byte metric) {
        }
    }
}
