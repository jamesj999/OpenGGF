package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.boss.AbstractBossChild;
import uk.co.jamesj999.sonic.level.objects.boss.AbstractBossInstance;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

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
    public void update(int frameCounter, AbstractPlayableSprite player) {
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
                timer = 8;
                mappingFrame = 4;
                state = State.OPENING;
            }
            case OPENING -> {
                if (--timer > 0) {
                    mappingFrame = 4;
                    break;
                }
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
                spawnShot(AizMinibossBarrelShotChild.Mode.ADVANCED_COLLIDING);
                timer = 10;
                state = State.CLOSING;
            }
            case BETWEEN_SHOTS -> {
                if (--timer >= 0) {
                    break;
                }
                if (cutsceneCounter < 0) {
                    timer = 10;
                    state = State.CLOSING;
                } else {
                    state = State.FIRING_CUTSCENE;
                }
            }
            case CLOSING -> {
                mappingFrame = 4;
                if (--timer > 0) {
                    break;
                }
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
        LevelManager levelManager = LevelManager.getInstance();
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        levelManager.getObjectManager().addDynamicObject(
                new AizMinibossBarrelShotChild(parent, barrelIndex, currentX, currentY + 4, mode));
    }

    private boolean isActivatedByParent() {
        return (parent.getCustomFlag(FLAG_PARENT_BITS) & PARENT_BIT_BARREL_ACTIVATE) != 0;
    }

    private void clearParentActivationBit() {
        int flags = parent.getCustomFlag(FLAG_PARENT_BITS);
        parent.setCustomFlag(FLAG_PARENT_BITS, flags & ~PARENT_BIT_BARREL_ACTIVATE);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager rm = LevelManager.getInstance().getObjectRenderManager();
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
