package uk.co.jamesj999.sonic.level.objects.boss;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.Palette;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseAttackable;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.objects.TouchResponseResult;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for boss objects with hit handling, palette flashing, and defeat sequences.
 * Supports multi-component bosses with parent-child relationships.
 */
public abstract class AbstractBossInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable {

    protected final LevelManager levelManager;
    protected final BossStateContext state;
    protected final BossHitHandler hitHandler;
    protected final BossPaletteFlasher paletteFlasher;
    protected final BossDefeatSequencer defeatSequencer;
    protected final List<BossChildComponent> childComponents;
    protected final Map<Integer, Integer> customMemory;
    private ObjectSpawn dynamicSpawn;

    public AbstractBossInstance(ObjectSpawn spawn, LevelManager levelManager, String name) {
        super(spawn, name);
        this.levelManager = levelManager;
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
    protected abstract void updateBossLogic(int frameCounter, AbstractPlayableSprite player);

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
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!state.defeated) {
            hitHandler.update();
            paletteFlasher.update();
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

    private void updateChildren(int frameCounter, AbstractPlayableSprite player) {
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

    public void onPlayerAttack(AbstractPlayableSprite player, TouchResponseResult result) {
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

    /**
     * Whether this boss uses the generic defeat sequencer.
     * Subclasses with custom defeat logic should override and return false.
     */
    protected boolean usesDefeatSequencer() {
        return true;
    }

    /**
     * Inner class: Handles hit detection and invulnerability.
     * ROM Reference: s2.asm:63119-63163 (loc_2F4A6 - hit handling routine)
     */
    protected class BossHitHandler {
        // ROM: s2.asm:63128 - move.b #$20,objoff_3E(a0)
        private static final int INVULNERABILITY_DURATION = 32; // Frames

        public void update() {
            if (state.invulnerable) {
                // ROM: s2.asm:63141 - subq.b #1,objoff_3E(a0)
                state.invulnerabilityTimer--;
                if (state.invulnerabilityTimer <= 0) {
                    state.invulnerable = false;
                    // ROM: s2.asm:63143 - move.b #$F,collision_flags(a0)
                    paletteFlasher.stopFlash();
                }
            }
        }

        public void processHit(AbstractPlayableSprite player) {
            // ROM: s2.asm:63124 - tst.b collision_flags(a0)
            if (state.invulnerable || state.defeated) {
                return;
            }

            // ROM: collision_property(a0) is hitcount, decremented by Touch_Enemy_Part2
            state.hitCount--;
            // ROM: s2.asm:63128 - move.b #$20,objoff_3E(a0)
            state.invulnerabilityTimer = INVULNERABILITY_DURATION;
            state.invulnerable = true;

            // ROM: s2.asm:63129 - move.w #SndID_BossHit,d0
            AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_BOSS_HIT);
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
                GameServices.gameState().addScore(1000);
                onDefeatStarted();
            }
        }
    }

    /**
     * Inner class: Handles palette flashing during invulnerability.
     * ROM Reference: s2.asm:63132-63140 (loc_2F4D0 - palette flash routine)
     */
    protected class BossPaletteFlasher {
        // ROM: s2.asm:63134 - moveq #0,d0 (black = 0x0000)
        private static final Palette.Color BLACK = new Palette.Color((byte) 0, (byte) 0, (byte) 0);
        // ROM: s2.asm:63137 - move.w #$EEE,d0 (white = 0x0EEE)
        private static final Palette.Color WHITE = new Palette.Color((byte) 255, (byte) 255, (byte) 255); // 0xEEE scaled to RGB

        private boolean flashing;
        private int flashFrame;
        private Palette.Color originalColor;
        private boolean colorStored;
        private boolean flashWhite; // Toggle state for flashing

        public void startFlash() {
            flashing = true;
            flashFrame = 0;
            flashWhite = false; // Start with black
            colorStored = false;
        }

        public void stopFlash() {
            if (flashing && colorStored) {
                // Restore original color
                int paletteCount = levelManager.getCurrentLevel().getPaletteCount();
                if (paletteCount > 1) {
                    Palette palette = levelManager.getCurrentLevel().getPalette(1);
                    if (palette != null) {
                        palette.setColor(1, originalColor);
                    }
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

            int paletteCount = levelManager.getCurrentLevel().getPaletteCount();
            if (paletteCount <= 1) {
                return;
            }

            // ROM: s2.asm:63133 - lea (Normal_palette_line2+2).w,a1
            Palette palette = levelManager.getCurrentLevel().getPalette(1); // Normal_palette_line2
            if (palette == null) {
                return;
            }

            // Store original color on first flash
            if (!colorStored) {
                originalColor = palette.getColor(1);
                colorStored = true;
            }

            // ROM: s2.asm:63135-63140 - Toggle between black (0x0000) and white (0x0EEE)
            // tst.w (a1) / bne.s loc_2F4DE / move.w #$EEE,d0 / move.w d0,(a1)
            // Use internal toggle state instead of reading palette to prevent external changes from breaking the effect
            Palette.Color newColor = flashWhite ? WHITE : BLACK;
            palette.setColor(1, newColor);
            flashWhite = !flashWhite; // Toggle for next frame

            flashFrame++;
        }
    }

    /**
     * Inner class: Handles defeat sequence (explosions, flee, EggPrison spawn).
     * ROM Reference: s2.asm:62989-63008 (loc_2F336 - SUB6 defeat routine)
     */
    protected class BossDefeatSequencer {
        // ROM: s2.asm:63155 - move.w #$B3,objoff_3C(a0) ($B3 = 179 decimal)
        private static final int EXPLOSION_DURATION = 179; // Frames
        // ROM: s2.asm:62992 - Boss_LoadExplosion spawns every 8 frames
        private static final int EXPLOSION_INTERVAL = 8; // Spawn explosion every 8 frames

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
            GameServices.gameState().addScore(1000);
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
