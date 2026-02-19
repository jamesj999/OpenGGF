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
 * AIZ Miniboss (0x90) - Flame barrel child.
 * Three instances are spawned at different offsets from the parent.
 * Uses a 7-state machine: INIT -> WAIT_ACTIVATE -> OPEN -> SPAWN_FLAMES -> CLOSE -> COOLDOWN -> repeat.
 * Activates when the parent sets custom flag 0x38 bit 1.
 * Each barrel spawns 4 flame projectiles when it fires.
 */
public class AizMinibossFlameBarrelChild extends AbstractBossChild {
    private static final int[][] BARREL_OFFSETS = {
            {0, -0x20}, {9, -0x1C}, {0x12, -0x18}
    };
    private static final int[] FLAME_X_OFFSETS = {-0x64, -0x54, -0x44, -0x2C};
    private static final int[] FLAME_Y_OFFSETS = {4, 4, 4, 3};

    private enum BarrelState {
        INIT, WAIT_ACTIVATE, OPEN, SPAWN_FLAMES, CLOSE, COOLDOWN, IDLE
    }

    private final int barrelIndex;
    private BarrelState barrelState = BarrelState.INIT;
    private int stateTimer;

    public AizMinibossFlameBarrelChild(AbstractBossInstance parent, int barrelIndex) {
        super(parent, "AIZMinibossBarrel" + barrelIndex, 4, 0x90);
        this.barrelIndex = Math.min(barrelIndex, 2);
    }

    @Override
    public void syncPositionWithParent() {
        if (parent != null && !parent.isDestroyed()) {
            this.currentX = parent.getX() + BARREL_OFFSETS[barrelIndex][0];
            this.currentY = parent.getY() + BARREL_OFFSETS[barrelIndex][1];
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!shouldUpdate(frameCounter)) {
            return;
        }
        syncPositionWithParent();

        // Stop firing when parent enters pre-exit or later (routine >= 10)
        if (barrelState != BarrelState.INIT && barrelState != BarrelState.WAIT_ACTIVATE
                && barrelState != BarrelState.IDLE
                && parent.getState().routine >= 10) {
            barrelState = BarrelState.IDLE;
        }

        switch (barrelState) {
            case INIT -> {
                barrelState = BarrelState.WAIT_ACTIVATE;
            }
            case WAIT_ACTIVATE -> {
                // Wait for parent to set activation flag (bit 1 of custom flag 0x38)
                if ((parent.getCustomFlag(0x38) & 0x02) != 0) {
                    barrelState = BarrelState.OPEN;
                    stateTimer = 16;
                }
            }
            case OPEN -> {
                stateTimer--;
                if (stateTimer <= 0) {
                    barrelState = BarrelState.SPAWN_FLAMES;
                }
            }
            case SPAWN_FLAMES -> {
                spawnFlames();
                barrelState = BarrelState.CLOSE;
                stateTimer = 16;
            }
            case CLOSE -> {
                stateTimer--;
                if (stateTimer <= 0) {
                    barrelState = BarrelState.COOLDOWN;
                    stateTimer = 30 + barrelIndex * 10; // stagger re-fire
                }
            }
            case COOLDOWN -> {
                stateTimer--;
                if (stateTimer <= 0) {
                    barrelState = BarrelState.OPEN;
                    stateTimer = 16;
                }
            }
            case IDLE -> { /* do nothing */ }
        }

        updateDynamicSpawn();
    }

    private void spawnFlames() {
        LevelManager lm = LevelManager.getInstance();
        if (lm == null || lm.getObjectManager() == null) {
            return;
        }
        for (int i = 0; i < FLAME_X_OFFSETS.length; i++) {
            int flameX = currentX + FLAME_X_OFFSETS[i];
            int flameY = currentY + FLAME_Y_OFFSETS[i];
            AizMinibossFlameChild flame = new AizMinibossFlameChild(flameX, flameY, i);
            lm.getObjectManager().addDynamicObject(flame);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (barrelState == BarrelState.INIT || barrelState == BarrelState.WAIT_ACTIVATE) {
            return;
        }
        ObjectRenderManager rm = LevelManager.getInstance().getObjectRenderManager();
        if (rm == null) {
            return;
        }
        PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        // Use barrel-related frames from the main mapping (frames 2-5 are barrel animation)
        int frame = switch (barrelState) {
            case OPEN -> 2;
            case SPAWN_FLAMES, CLOSE -> 3;
            case COOLDOWN -> 2;
            default -> 2;
        };
        renderer.drawFrameIndex(frame, currentX, currentY, false, false);
    }
}
