package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic3k.audio.Sonic3kSfx;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.SolidContact;
import uk.co.jamesj999.sonic.level.objects.SolidObjectListener;
import uk.co.jamesj999.sonic.level.objects.SolidObjectParams;
import uk.co.jamesj999.sonic.level.objects.SolidObjectProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x08 - Spikes (Sonic 3 &amp; Knuckles).
 * <p>
 * Functionally identical to S2 object 0x36 with one addition: subtype behavior 3
 * (pushing mode). Uses shared SpikesSprings Nemesis art loaded to VDP tile $049C.
 * <p>
 * Subtype encoding:
 * <ul>
 *   <li>Upper nibble (bits 7-4): size index (0-3 = upright, 4-7 = sideways)</li>
 *   <li>Lower nibble (bits 3-0): behavior (0=static, 1=vertical, 2=horizontal, 3=push)</li>
 * </ul>
 */
public class Sonic3kSpikeObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final int[] WIDTH_PIXELS = {
            0x10, 0x20, 0x30, 0x40,
            0x10, 0x10, 0x10, 0x10
    };
    private static final int[] Y_RADIUS = {
            0x10, 0x10, 0x10, 0x10,
            0x10, 0x20, 0x30, 0x40
    };

    private static final int SPIKE_RETRACT_STEP = 0x800;
    private static final int SPIKE_RETRACT_MAX = 0x2000;
    private static final int SPIKE_RETRACT_DELAY = 60;

    private final int baseX;
    private final int baseY;
    private int currentX;
    private int currentY;
    private int retractOffset;
    private int retractState;
    private int retractTimer;
    private ObjectSpawn dynamicSpawn;

    public Sonic3kSpikeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Spikes");
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.currentX = baseX;
        this.currentY = baseY;
        this.dynamicSpawn = spawn;
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
        // ROM: Hurt_Sidekick - CPU Tails only gets knockback
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

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        moveSpikes();
        updateDynamicSpawn();
    }

    @Override
    public SolidObjectParams getSolidParams() {
        int widthPixels = getEntryValue(WIDTH_PIXELS);
        int yRadius = getEntryValue(Y_RADIUS);
        return new SolidObjectParams(widthPixels + 0x0B, yRadius, yRadius + 1);
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

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        int frameIndex = Math.clamp((spawn.subtype() >> 4) & 0xF, 0, 7);
        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.SPIKES);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(frameIndex, currentX, currentY, hFlip, vFlip);
        }
    }

    private boolean shouldHurt(SolidContact contact) {
        if (isSideways()) {
            return contact.touchSide();
        }
        if (isUpsideDown()) {
            return contact.touchBottom();
        }
        return contact.standing();
    }

    private boolean isSideways() {
        return ((spawn.subtype() >> 4) & 0xF) >= 4;
    }

    private boolean isUpsideDown() {
        return (spawn.renderFlags() & 0x2) != 0;
    }

    private int getEntryValue(int[] table) {
        int entry = Math.clamp((spawn.subtype() >> 4) & 0xF, 0, table.length - 1);
        return table[entry];
    }

    private void moveSpikes() {
        int behavior = spawn.subtype() & 0xF;
        switch (behavior) {
            case 1 -> moveSpikesVertical();
            case 2 -> moveSpikesHorizontal();
            case 3 -> moveSpikesPush();
            default -> {
                currentX = baseX;
                currentY = baseY;
            }
        }
    }

    private void moveSpikesVertical() {
        moveSpikesDelay();
        currentX = baseX;
        currentY = baseY + (retractOffset >> 8);
    }

    private void moveSpikesHorizontal() {
        moveSpikesDelay();
        currentX = baseX + (retractOffset >> 8);
        currentY = baseY;
    }

    /**
     * Behavior 3: pushing mode. Same retract/extend cycle as vertical/horizontal,
     * but pushes the player along as a solid platform.
     */
    private void moveSpikesPush() {
        moveSpikesDelay();
        currentX = baseX;
        currentY = baseY + (retractOffset >> 8);
    }

    private void moveSpikesDelay() {
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

    private void updateDynamicSpawn() {
        if (dynamicSpawn.x() == currentX && dynamicSpawn.y() == currentY) {
            return;
        }
        dynamicSpawn = new ObjectSpawn(
                currentX,
                currentY,
                spawn.objectId(),
                spawn.subtype(),
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
    }

    private void playSpikeMoveSfx() {
        if (!isOnScreen()) {
            return;
        }
        try {
            AudioManager.getInstance().playSfx(Sonic3kSfx.SPIKE_MOVE.id);
        } catch (Exception e) {
            // Prevent audio failure from breaking game logic.
        }
    }
}
