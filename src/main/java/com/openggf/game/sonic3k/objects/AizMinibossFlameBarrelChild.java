package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * AIZ miniboss flame barrel child.
 *
 * ROM:
 * - Cutscene barrel: loc_6872C (ChildObjDat_69072)
 * - Miniboss barrel: loc_68C12 (ChildObjDat_69086)
 */
public class AizMinibossFlameBarrelChild extends AbstractBossChild {
    private static final int FLAG_PARENT_BITS = 0x38;
    private static final int PARENT_BIT_BARREL_ACTIVATE = 1 << 1;

    private static final int[] START_DELAYS = {0, 0x10, 0x20}; // word_68ECE
    private static final int[][] BARREL_OFFSETS = {
            {0, -0x20}, {9, -0x1C}, {0x12, -0x18}
    };

    // ROM byte_6912F (Animate_RawMultiDelay, first pair skipped on initial play):
    // Open: frame 4 (timer 5 = 6t), frame 5 (timer $17 = 24t), $F4
    private static final int[] OPEN_FRAMES = {4, 5};
    private static final int[] OPEN_DURATIONS = {6, 24};

    // ROM byte_69136 (Animate_RawMultiDelay, first pair skipped on initial play):
    // Close: frame 5 (timer $17 = 24t), frame 4 (timer 5 = 6t), frame 3 (timer 5 = 6t), $F4
    private static final int[] CLOSE_FRAMES = {5, 4, 3};
    private static final int[] CLOSE_DURATIONS = {24, 6, 6};

    private enum State {
        INIT,
        WAIT_ACTIVATE,
        START_DELAY,
        OPENING,
        FIRING_CUTSCENE,
        FIRING_MINIBOSS,
        BETWEEN_SHOTS,
        CLOSING,
        IDLE
    }

    private final int barrelIndex;
    private final boolean cutsceneVariant;

    private State state = State.INIT;
    private int timer;
    private int mappingFrame = 3;
    private int cutsceneCounter;
    private int animIndex;

    public AizMinibossFlameBarrelChild(AbstractBossInstance parent, int barrelIndex, boolean cutsceneVariant) {
        super(parent, "AIZMinibossBarrel" + barrelIndex, 4, 0x90);
        this.barrelIndex = Math.max(0, Math.min(2, barrelIndex));
        this.cutsceneVariant = cutsceneVariant;
    }

    @Override
    public void syncPositionWithParent() {
        if (parent == null || parent.isDestroyed()) {
            return;
        }
        int xOffset = BARREL_OFFSETS[barrelIndex][0];
        int yOffset = BARREL_OFFSETS[barrelIndex][1];
        boolean hFlip = (parent.getState().renderFlags & 1) != 0;
        if (hFlip) {
            xOffset = -xOffset;
        }
        this.currentX = parent.getX() + xOffset;
        this.currentY = parent.getY() + yOffset;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (!shouldUpdate(frameCounter)) {
            return;
        }

        syncPositionWithParent();

        if (parent == null || parent.getState().defeated) {
            state = State.IDLE;
            mappingFrame = 3;
            updateDynamicSpawn();
            return;
        }

        switch (state) {
            case INIT -> {
                state = State.WAIT_ACTIVATE;
                mappingFrame = 3;
            }
            case WAIT_ACTIVATE -> {
                mappingFrame = 3;
                if (!isActivatedByParent()) {
                    break;
                }
                timer = START_DELAYS[barrelIndex];
                state = State.START_DELAY;
            }
            case START_DELAY -> {
                if (--timer >= 0) {
                    break;
                }
                // ROM byte_6912F: begin open animation (first pair skipped)
                animIndex = 0;
                mappingFrame = OPEN_FRAMES[0];
                timer = OPEN_DURATIONS[0];
                state = State.OPENING;
            }
            case OPENING -> {
                if (--timer > 0) {
                    break;
                }
                animIndex++;
                if (animIndex < OPEN_FRAMES.length) {
                    mappingFrame = OPEN_FRAMES[animIndex];
                    timer = OPEN_DURATIONS[animIndex];
                    break;
                }
                // Open animation complete — start firing
                mappingFrame = 5;
                if (cutsceneVariant) {
                    cutsceneCounter = 3;
                    state = State.FIRING_CUTSCENE;
                } else {
                    state = State.FIRING_MINIBOSS;
                }
            }
            case FIRING_CUTSCENE -> {
                fireCutsceneShot();
                timer = 0x1C;
                state = State.BETWEEN_SHOTS;
            }
            case FIRING_MINIBOSS -> {
                // ROM loc_68C64: fire shot and immediately begin close animation
                spawnShot(AizMinibossBarrelShotChild.Mode.ADVANCED_COLLIDING);
                enterClosingAnimation();
            }
            case BETWEEN_SHOTS -> {
                // ROM loc_687DC: checks tst.b $39 EVERY frame during wait,
                // exits immediately once counter goes negative after 4th shot
                if (cutsceneCounter < 0) {
                    enterClosingAnimation();
                    break;
                }
                if (--timer >= 0) {
                    break;
                }
                state = State.FIRING_CUTSCENE;
            }
            case CLOSING -> {
                if (--timer > 0) {
                    break;
                }
                animIndex++;
                if (animIndex < CLOSE_FRAMES.length) {
                    mappingFrame = CLOSE_FRAMES[animIndex];
                    timer = CLOSE_DURATIONS[animIndex];
                    break;
                }
                // Close animation complete
                mappingFrame = 3;
                if (cutsceneVariant) {
                    state = State.IDLE;
                } else {
                    clearParentActivationBit();
                    state = State.WAIT_ACTIVATE;
                }
            }
            case IDLE -> mappingFrame = 3;
        }

        updateDynamicSpawn();
    }

    private void fireCutsceneShot() {
        // loc_687B6: counter decremented before child selection.
        cutsceneCounter--;
        if (cutsceneCounter == 1) {
            // loc_68C96 path spawned from ChildObjDat_690A8, but cutscene parent
            // clears collision_flags in loc_68CD0.
            spawnShot(AizMinibossBarrelShotChild.Mode.ADVANCED_NON_COLLIDING);
        } else {
            // loc_68844 path from ChildObjDat_6909A.
            spawnShot(AizMinibossBarrelShotChild.Mode.SIMPLE);
        }
    }

    private void spawnShot(AizMinibossBarrelShotChild.Mode mode) {
        LevelManager levelManager = GameServices.level();
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        // ROM ChildObjDat_6909A / ChildObjDat_690A8 spawn a short muzzle-flare child
        // alongside the main shot object.
        levelManager.getObjectManager().addDynamicObject(
                new AizMinibossBarrelShotFlareChild(this));
        levelManager.getObjectManager().addDynamicObject(
                new AizMinibossBarrelShotChild(parent, barrelIndex << 1, currentX, currentY + 4, mode));
    }

    private void enterClosingAnimation() {
        // ROM byte_69136: close animation (first pair skipped on initial play)
        animIndex = 0;
        mappingFrame = CLOSE_FRAMES[0];
        timer = CLOSE_DURATIONS[0];
        state = State.CLOSING;
    }

    private boolean isActivatedByParent() {
        return (parent.getCustomFlag(FLAG_PARENT_BITS) & PARENT_BIT_BARREL_ACTIVATE) != 0;
    }

    private void clearParentActivationBit() {
        int flags = parent.getCustomFlag(FLAG_PARENT_BITS);
        parent.setCustomFlag(FLAG_PARENT_BITS, flags & ~PARENT_BIT_BARREL_ACTIVATE);
    }

    @Override
    public boolean isHighPriority() {
        // ROM: make_art_tile(ArtTile_AIZMiniboss,1,1) — priority bit = 1
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager rm = services().renderManager();
        if (rm == null) {
            return;
        }
        PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        boolean hFlip = (parent != null) && ((parent.getState().renderFlags & 1) != 0);
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, hFlip, false);
    }
}
