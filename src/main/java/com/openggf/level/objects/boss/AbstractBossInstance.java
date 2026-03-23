package com.openggf.level.objects.boss;

import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseAttackable;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.TrigLookupTable;
import com.openggf.game.PlayableEntity;

import com.openggf.debug.DebugColor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Base class for boss objects with hit handling, palette flashing, and defeat sequences.
 * Supports multi-component bosses with parent-child relationships.
 */
public abstract class AbstractBossInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable {

    // Common boss constants (ROM values)
    /** ROM: s2.asm:63155 - move.w #$B3,objoff_3C(a0) ($B3 = 179 decimal) */
    protected static final int DEFEAT_TIMER_START = 0xB3;
    /** ROM: ObjectMoveAndFall gravity (8.8 fixed-point) */
    protected static final int GRAVITY = 0x38;
    /** Standard Sonic 2 boss hit count */
    protected static final int DEFAULT_HIT_COUNT = 8;
    /** Default invulnerability duration in frames */
    protected static final int DEFAULT_INVULNERABILITY_DURATION = 32;
    /** Boss explosion spawn interval (every 8 frames) */
    protected static final int EXPLOSION_INTERVAL = 8;

    protected final BossStateContext state;
    protected final BossHitHandler hitHandler;
    protected final BossPaletteFlasher paletteFlasher;
    protected final BossDefeatSequencer defeatSequencer;
    protected final List<BossChildComponent> childComponents;
    protected final Map<Integer, Integer> customMemory;
    private ObjectSpawn dynamicSpawn;

    public AbstractBossInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.state = new BossStateContext(spawn.x(), spawn.y(), getInitialHitCount());
        this.hitHandler = new BossHitHandler();
        this.paletteFlasher = new BossPaletteFlasher();
        this.defeatSequencer = new BossDefeatSequencer();
        this.childComponents = new ArrayList<>();
        this.customMemory = new HashMap<>();
        this.dynamicSpawn = spawn;
        initializeBossState();
    }

    /**
     * Initialize boss-specific state and spawn child components.
     */
    protected abstract void initializeBossState();

    /**
     * Update boss-specific logic.
     */
    protected abstract void updateBossLogic(int frameCounter, PlayableEntity player);

    /**
     * Get initial hit count (typically 8 for Sonic 2 bosses).
     */
    protected abstract int getInitialHitCount();

    /**
     * Called when boss takes a hit.
     */
    protected abstract void onHitTaken(int remainingHits);

    /**
     * Get collision size index for touch response.
     */
    protected abstract int getCollisionSizeIndex();

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (!state.defeated) {
            hitHandler.update();
            // Note: paletteFlasher.update() is now called inside hitHandler.update()
            // to match ROM order: flash first, then decrement timer
        }

        if (!state.defeated || !usesDefeatSequencer()) {
            updateBossLogic(frameCounter, player);
        }

        if (state.defeated && usesDefeatSequencer()) {
            defeatSequencer.update(frameCounter);
        }

        state.lastUpdatedFrame = frameCounter;
        updateChildren(frameCounter, player);
        updateDynamicSpawn();
    }

    private void updateChildren(int frameCounter, PlayableEntity player) {
        childComponents.removeIf(BossChildComponent::isDestroyed);
        for (BossChildComponent child : childComponents) {
            child.update(frameCounter, player);
        }
    }

    public int getCollisionFlags() {
        if (state.invulnerable || state.defeated) {
            return 0; // No collision during invulnerability or defeat
        }
        return 0xC0 | (getCollisionSizeIndex() & 0x3F); // Category BOSS (0xC0)
    }

    public int getCollisionProperty() {
        return state.hitCount; // Return hit count for ROM accuracy
    }

    public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
        hitHandler.processHit(player);
    }

    /**
     * Check if boss is defeated.
     */
    public boolean isDefeated() {
        if (usesDefeatSequencer()) {
            return state.defeated && defeatSequencer.isComplete();
        }
        return state.defeated;
    }

    /**
     * Get/set custom memory fields (objoff_XX pattern from ROM).
     */
    public int getCustomFlag(int offset) {
        return customMemory.getOrDefault(offset, 0);
    }

    public void setCustomFlag(int offset, int value) {
        customMemory.put(offset, value);
    }

    // ========================================================================
    // CONFIGURABLE METHODS - Override in subclasses to customize behavior
    // ========================================================================

    /**
     * Get invulnerability duration in frames.
     * Override for bosses with different durations (e.g., ARZ uses 64, CNZ uses 48).
     */
    protected int getInvulnerabilityDuration() {
        return DEFAULT_INVULNERABILITY_DURATION;
    }

    /**
     * Get palette line index for flash effect.
     * Most bosses flash palette line 1, but ARZ flashes palette line 0.
     */
    protected int getPaletteLineForFlash() {
        return 1;
    }

    /**
     * SFX played when the boss takes damage.
     */
    protected abstract int getBossHitSfxId();

    /**
     * SFX played by boss defeat explosions.
     */
    protected abstract int getBossExplosionSfxId();

    // ========================================================================
    // HELPER METHODS - Common functionality for all bosses
    // ========================================================================

    /**
     * Calculate hover offset using sine wave.
     * ROM pattern: Used by EHZ, CPZ, CNZ, ARZ bosses for floating motion.
     * Increments sineCounter by 2 each frame and returns offset >> 6.
     */
    protected int calculateHoverOffset() {
        int sine = TrigLookupTable.sinHex(state.sineCounter & 0xFF);
        state.sineCounter = (state.sineCounter + 2) & 0xFF;
        return sine >> 6;
    }

    /**
     * Spawn defeat explosion with random offset from boss position.
     * ROM: Boss_LoadExplosion (s2.asm lines referenced in each boss)
     * Uses standard random offset calculation: (random >> 2) - 0x20.
     */
    protected void spawnDefeatExplosion() {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null || services().objectManager() == null) {
            return;
        }
        int random = ThreadLocalRandom.current().nextInt(0x10000);
        int xOffset = ((random & 0xFF) >> 2) - 0x20;
        int yOffset = (((random >> 8) & 0xFF) >> 2) - 0x20;
        BossExplosionObjectInstance explosion = new BossExplosionObjectInstance(
                state.x + xOffset,
                state.y + yOffset,
                renderManager,
                getBossExplosionSfxId());
        services().objectManager().addDynamicObject(explosion);
    }

    /**
     * Apply gravity to boss velocity and update position.
     * ROM: ObjectMoveAndFall pattern.
     */
    protected void applyObjectMoveAndFall() {
        state.xFixed += (state.xVel << 8);
        state.yFixed += (state.yVel << 8);
        state.yVel += GRAVITY;
        state.updatePositionFromFixed();
    }

    /**
     * Whether this boss uses the generic defeat sequencer.
     * Subclasses with custom defeat logic should override and return false.
     */
    protected boolean usesDefeatSequencer() {
        return true;
    }

    /**
     * Inner class: Handles hit detection and invulnerability.
     * ROM Reference: s2.asm:60734-60758 (Boss_HandleHits routine)
     */
    protected class BossHitHandler {
        public void update() {
            if (state.invulnerable) {
                // ROM: s2.asm:60748-60754 - Toggle palette FIRST
                paletteFlasher.update();

                // ROM: s2.asm:60755 - subq.b #1,boss_invulnerable_time(a0)
                state.invulnerabilityTimer--;

                // ROM: s2.asm:60756-60757 - Check if done
                if (state.invulnerabilityTimer <= 0) {
                    state.invulnerable = false;
                    // ROM: s2.asm:60758 - move.b #$F,collision_flags(a0)
                    paletteFlasher.stopFlash();
                }
            }
        }

        public void processHit(PlayableEntity player) {
            // ROM: s2.asm:63124 - tst.b collision_flags(a0)
            if (state.invulnerable || state.defeated) {
                return;
            }

            // ROM: collision_property(a0) is hitcount, decremented by Touch_Enemy_Part2
            state.hitCount--;
            // Use configurable invulnerability duration
            state.invulnerabilityTimer = getInvulnerabilityDuration();
            state.invulnerable = true;

            // ROM: s2.asm:63129 - move.w #SndID_BossHit,d0
            services().playSfx(getBossHitSfxId());
            paletteFlasher.startFlash();
            onHitTaken(state.hitCount);

            if (state.hitCount == 0) {
                triggerDefeat();
            }
        }

        private void triggerDefeat() {
            // ROM: s2.asm:63149 - loc_2F4EE (boss defeated)
            state.defeated = true;
            if (usesDefeatSequencer()) {
                defeatSequencer.startDefeat();
            } else {
                services().gameState().addScore(1000);
                onDefeatStarted();
            }
        }
    }

    /**
     * Inner class: Handles palette flashing during invulnerability.
     * ROM Reference: s2.asm:60748-60754 (Boss_HandleHits palette flash)
     */
    protected class BossPaletteFlasher {
        // ROM: s2.asm:60749 - moveq #0,d0 (black = 0x0000)
        private static final Palette.Color BLACK = new Palette.Color((byte) 0, (byte) 0, (byte) 0);
        // ROM: s2.asm:60752 - move.w #$EEE,d0 (white = 0x0EEE)
        private static final Palette.Color WHITE = new Palette.Color((byte) 255, (byte) 255, (byte) 255); // 0xEEE scaled to RGB

        private boolean flashing;
        private int flashFrame;
        private Palette.Color originalColor;
        private boolean colorStored;
        private boolean useWhite; // Internal toggle - immune to external palette modifications

        public void startFlash() {
            flashing = true;
            flashFrame = 0;
            colorStored = false;
            useWhite = false; // Start with black on first frame
        }

        public void stopFlash() {
            if (flashing && colorStored) {
                // Restore original color
                Palette palette = getPaletteForFlash();
                if (palette != null) {
                    palette.setColor(1, originalColor);
                    // Upload restored palette to GPU
                    uploadPaletteToGpu(palette);
                }
            }
            flashing = false;
            flashFrame = 0;
            colorStored = false;
        }

        public void update() {
            if (!flashing) {
                return;
            }

            Palette palette = getPaletteForFlash();
            if (palette == null) {
                return;
            }

            // Store original color on first flash (make a copy to avoid reference issues)
            if (!colorStored) {
                Palette.Color orig = palette.getColor(1);
                originalColor = new Palette.Color(orig.r, orig.g, orig.b);
                colorStored = true;
            }

            // ROM: s2.asm:60749-60754 - Toggle between black (0x0000) and white (0x0EEE)
            // Use internal toggle state to ensure reliable alternation even if
            // palette cyclers or other systems modify the palette between frames
            Palette.Color newColor = useWhite ? WHITE : BLACK;
            palette.setColor(1, newColor);
            useWhite = !useWhite; // Toggle for next frame

            // Upload modified palette to GPU so the change is visible
            uploadPaletteToGpu(palette);

            flashFrame++;
        }

        private void uploadPaletteToGpu(Palette palette) {
            GraphicsManager gm = GraphicsManager.getInstance();
            if (gm.isGlInitialized()) {
                int paletteIndex = getPaletteLineForFlash();
                gm.cachePaletteTexture(palette, paletteIndex);
            }
        }

        private Palette getPaletteForFlash() {
            if (services().currentLevel() == null) {
                return null;
            }
            int paletteIndex = getPaletteLineForFlash();
            if (paletteIndex < 0) {
                return null;
            }
            int paletteCount = services().currentLevel().getPaletteCount();
            if (paletteCount <= paletteIndex) {
                return null;
            }
            return services().currentLevel().getPalette(paletteIndex);
        }
    }

    /**
     * Inner class: Handles defeat sequence (explosions, flee, EggPrison spawn).
     * ROM Reference: s2.asm:62989-63008 (loc_2F336 - SUB6 defeat routine)
     */
    protected class BossDefeatSequencer {
        // ROM: s2.asm:63155 - move.w #$B3,objoff_3C(a0) ($B3 = 179 decimal)
        private static final int EXPLOSION_DURATION = 179; // Frames

        private DefeatState defeatState;
        private int defeatTimer;
        private int fleeTimer;

        private enum DefeatState {
            EXPLODING,
            FLEEING,
            SPAWN_PRISON,
            COMPLETE
        }

        public BossDefeatSequencer() {
            this.defeatState = DefeatState.COMPLETE;
            this.defeatTimer = 0;
            this.fleeTimer = 0;
        }

        public void startDefeat() {
            defeatState = DefeatState.EXPLODING;
            defeatTimer = EXPLOSION_DURATION;
            // ROM: s2.asm:63150-63151 - moveq #100,d0 / jsrto JmpTo3_AddPoints (100 = 1000 points)
            services().gameState().addScore(1000);
            onDefeatStarted();
        }

        public void update(int frameCounter) {
            switch (defeatState) {
                case EXPLODING -> updateExploding(frameCounter);
                case FLEEING -> updateFleeing(frameCounter);
                case SPAWN_PRISON -> spawnEggPrison();
            }
        }

        private void updateExploding(int frameCounter) {
            // ROM: s2.asm:62990 - subq.w #1,objoff_3C(a0)
            defeatTimer--;

            // ROM: s2.asm:62992 - bsr.w Boss_LoadExplosion (spawns every 8 frames)
            // Spawn explosion every 8 frames
            if (defeatTimer % EXPLOSION_INTERVAL == 0) {
                spawnExplosion();
            }

            // ROM: s2.asm:62991 - bmi.s loc_2F35C (timer finished)
            if (defeatTimer <= 0) {
                defeatState = DefeatState.FLEEING;
                fleeTimer = 0;
                onFleeStarted();
            }
        }

        private void updateFleeing(int frameCounter) {
            fleeTimer++;
            updateFleeingMovement();

            // Check if boss is off-screen
            if (isOffScreen()) {
                defeatState = DefeatState.SPAWN_PRISON;
            }
        }

        private void spawnEggPrison() {
            onEggPrisonSpawn();
            defeatState = DefeatState.COMPLETE;
        }

        public boolean isComplete() {
            return defeatState == DefeatState.COMPLETE;
        }

        private void spawnExplosion() {
            // Subclasses can override to spawn explosion effect
        }

        private boolean isOffScreen() {
            // Subclasses can override
            return fleeTimer > 200;
        }
    }

    // Cached debug labels to avoid per-frame String allocation
    private String cachedHpLabel;
    private int cachedDebugHitCount = -1;
    private int cachedDebugRoutine = -1;
    private String cachedInvulnLabel;
    private int cachedInvulnTimer = -1;

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Hitbox rectangle colored by state
        float r, g, b;
        if (state.defeated) {
            r = 0.5f; g = 0.5f; b = 0.5f; // Gray = defeated
        } else if (state.invulnerable) {
            r = 1f; g = 0f; b = 0f; // Red = invulnerable
        } else {
            r = 0f; g = 1f; b = 0f; // Green = hittable
        }
        ctx.drawRect(state.x, state.y, 24, 24, r, g, b);

        // Red text: name + HP + routine (cached until state changes)
        if (state.hitCount != cachedDebugHitCount || state.routine != cachedDebugRoutine) {
            cachedHpLabel = name + " HP:" + state.hitCount + " R:" + state.routine;
            cachedDebugHitCount = state.hitCount;
            cachedDebugRoutine = state.routine;
        }
        ctx.drawWorldLabel(state.x, state.y, -3, cachedHpLabel, DebugColor.RED);

        // Orange invulnerability timer when invulnerable (cached until timer changes)
        if (state.invulnerable) {
            if (state.invulnerabilityTimer != cachedInvulnTimer) {
                cachedInvulnLabel = "Invuln:" + state.invulnerabilityTimer;
                cachedInvulnTimer = state.invulnerabilityTimer;
            }
            ctx.drawWorldLabel(state.x, state.y, -2, cachedInvulnLabel, DebugColor.ORANGE);
        }

        // Blue lines from parent to each child component
        for (BossChildComponent child : childComponents) {
            if (!child.isDestroyed() && child instanceof AbstractBossChild bossChild) {
                ctx.drawLine(state.x, state.y, bossChild.getX(), bossChild.getY(), 0.3f, 0.3f, 1f);
            }
        }
    }

    /**
     * Called when defeat sequence starts. Override to customize behavior.
     */
    protected void onDefeatStarted() {
        // Default: no additional behavior
    }

    /**
     * Called when fleeing phase starts. Override to customize movement.
     */
    protected void onFleeStarted() {
        // Default: no additional behavior
    }

    /**
     * Update boss movement during fleeing phase. Override to customize.
     */
    protected void updateFleeingMovement() {
        // Default: move upward
        state.y--;
    }

    /**
     * Called when EggPrison should spawn. Override to customize.
     */
    protected void onEggPrisonSpawn() {
        // Default: no additional behavior
        // Subclasses should unlock camera and spawn EggPrison
    }

    @Override
    public int getX() {
        return state.x;
    }

    @Override
    public int getY() {
        return state.y;
    }

    public BossStateContext getState() {
        return state;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return dynamicSpawn;
    }

    public List<BossChildComponent> getChildComponents() {
        return childComponents;
    }

    private void updateDynamicSpawn() {
        if (dynamicSpawn.x() == state.x && dynamicSpawn.y() == state.y) {
            return;
        }
        dynamicSpawn = new ObjectSpawn(
                state.x,
                state.y,
                spawn.objectId(),
                spawn.subtype(),
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
    }
}
