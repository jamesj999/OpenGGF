package com.openggf.level.objects;

import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Shared base class for S2 and S3K spike objects.
 * <p>
 * Extracts identical retract constants, dimension tables, hurt/solid logic,
 * and oscillation movement. Game-specific subclasses override
 * {@link #moveSpikes(AbstractPlayableSprite)} to add behaviors (e.g. S3K push mode)
 * and {@link #playSpikeMoveSfx()} for game-specific audio.
 * <p>
 * Subtype encoding (shared across S2/S3K):
 * <ul>
 *   <li>Upper nibble (bits 7-4): size index (0-3 = upright, 4-7 = sideways)</li>
 *   <li>Lower nibble (bits 3-0): behavior (0=static, 1=vertical, 2=horizontal, ...)</li>
 * </ul>
 */
public abstract class AbstractSpikeObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    protected static final int[] WIDTH_PIXELS = {
            0x10, 0x20, 0x30, 0x40,
            0x10, 0x10, 0x10, 0x10
    };
    protected static final int[] Y_RADIUS = {
            0x10, 0x10, 0x10, 0x10,
            0x10, 0x20, 0x30, 0x40
    };

    protected static final int SPIKE_RETRACT_STEP = 0x800;
    protected static final int SPIKE_RETRACT_MAX = 0x2000;
    protected static final int SPIKE_RETRACT_DELAY = 60;

    protected final int baseX;
    protected final int baseY;
    protected int currentX;
    protected int currentY;
    protected int retractOffset;
    protected int retractState;
    protected int retractTimer;
    protected ObjectSpawn dynamicSpawn;

    protected AbstractSpikeObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.currentX = baseX;
        this.currentY = baseY;
        this.dynamicSpawn = spawn;
    }

    // ---- Solid object contract ----

    @Override
    public SolidObjectParams getSolidParams() {
        int widthPixels = getEntryValue(WIDTH_PIXELS);
        int yRadius = getEntryValue(Y_RADIUS);
        return new SolidObjectParams(widthPixels + 0x0B, yRadius, yRadius + 1);
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (player == null) {
            return;
        }
        if (!shouldHurt(contact)) {
            return;
        }
        if (player.getInvulnerable()) {
            return;
        }
        // ROM: Hurt_Sidekick - CPU Tails only gets knockback, no ring scatter or death
        if (player.isCpuControlled()) {
            player.applyHurt(currentX);
            return;
        }
        boolean hadRings = player.getRingCount() > 0;
        if (hadRings && !player.hasShield()) {
            LevelManager.getInstance().spawnLostRings(player, frameCounter);
        }
        player.applyHurtOrDeath(currentX, true, hadRings);
    }

    // ---- Lifecycle ----

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        moveSpikes(player);
        updateDynamicSpawn();
    }

    @Override
    public ObjectSpawn getSpawn() {
        return dynamicSpawn;
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    // ---- Movement ----

    /**
     * Template method: dispatch spike movement by subtype behavior nibble.
     * S2 handles behaviors 0-2; S3K adds behavior 3 (push mode).
     */
    protected void moveSpikes(AbstractPlayableSprite player) {
        int behavior = spawn.subtype() & 0xF;
        switch (behavior) {
            case 1 -> moveSpikesVertical();
            case 2 -> moveSpikesHorizontal();
            default -> {
                currentX = baseX;
                currentY = baseY;
            }
        }
    }

    protected void moveSpikesVertical() {
        moveSpikesDelay();
        currentX = baseX;
        currentY = baseY + (retractOffset >> 8);
    }

    protected void moveSpikesHorizontal() {
        moveSpikesDelay();
        currentX = baseX + (retractOffset >> 8);
        currentY = baseY;
    }

    protected void moveSpikesDelay() {
        if (retractTimer > 0) {
            retractTimer--;
            if (retractTimer == 0) {
                playSpikeMoveSfx();
            }
            return;
        }

        if (retractState != 0) {
            retractOffset -= SPIKE_RETRACT_STEP;
            if (retractOffset < 0) {
                retractOffset = 0;
                retractState = 0;
                retractTimer = SPIKE_RETRACT_DELAY;
            }
            return;
        }

        retractOffset += SPIKE_RETRACT_STEP;
        if (retractOffset >= SPIKE_RETRACT_MAX) {
            retractOffset = SPIKE_RETRACT_MAX;
            retractState = 1;
            retractTimer = SPIKE_RETRACT_DELAY;
        }
    }

    // ---- Hurt direction helpers ----

    protected boolean shouldHurt(SolidContact contact) {
        if (isSideways()) {
            return contact.touchSide();
        }
        if (isUpsideDown()) {
            return contact.touchBottom();
        }
        return contact.standing();
    }

    protected boolean isSideways() {
        return ((spawn.subtype() >> 4) & 0xF) >= 4;
    }

    protected boolean isUpsideDown() {
        return (spawn.renderFlags() & 0x2) != 0;
    }

    // ---- Dimension lookup ----

    protected int getEntryValue(int[] table) {
        return table[Math.clamp((spawn.subtype() >> 4) & 0xF, 0, table.length - 1)];
    }

    // ---- Internal helpers ----

    protected void updateDynamicSpawn() {
        if (dynamicSpawn.x() == currentX && dynamicSpawn.y() == currentY) {
            return;
        }
        dynamicSpawn = buildSpawnAt(currentX, currentY);
    }

    /**
     * Play the spike-retract sound effect. Subclasses provide game-specific SFX IDs.
     */
    protected abstract void playSpikeMoveSfx();
}
