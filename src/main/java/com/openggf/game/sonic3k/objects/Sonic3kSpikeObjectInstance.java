package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

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

    // Push mode constants (ROM: sub_2438A)
    private static final int PUSH_RATE_PERIOD = 0x10;   // $3A reset value: every 17 frames
    private static final int PUSH_MAX_DISTANCE = 0x20;  // $3C init: 32 pixels total

    private final int baseX;
    private final int baseY;
    private int currentX;
    private int currentY;
    private int retractOffset;
    private int retractState;
    private int retractTimer;
    private ObjectSpawn dynamicSpawn;

    // Push mode state (ROM: $3A rate limiter, $3C distance remaining, $3E/$3F prev status)
    private boolean contactPushingActive;   // Set by onSolidContact, consumed by next update()
    private int pushRateTimer;              // $3A: frames until next push allowed
    private int pushDistanceRemaining = PUSH_MAX_DISTANCE; // $3C: remaining 1px pushes

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
        // Track push contact for push mode (before hurt check, since side contact
        // doesn't trigger shouldHurt for upright spikes but does drive push logic).
        // ROM: status(a0) pushing bits are set by SolidObjectFull, read next frame.
        if (isPushMode() && contact.pushing()) {
            contactPushingActive = true;
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
        moveSpikes(player);
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

    private boolean isPushMode() {
        return (spawn.subtype() & 0xF) == 3;
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

    private void moveSpikes(AbstractPlayableSprite player) {
        int behavior = spawn.subtype() & 0xF;
        switch (behavior) {
            case 1 -> moveSpikesVertical();
            case 2 -> moveSpikesHorizontal();
            case 3 -> moveSpikesPush(player);
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
     * Behavior 3: player-driven pushing mode (ROM: loc_24356 / sub_2438A).
     * <p>
     * Unlike vertical/horizontal oscillation, push spikes only move when a player
     * actively pushes against them from the left. Movement is rate-limited to 1 pixel
     * every 17 frames, with a maximum total displacement of 32 pixels.
     */
    private void moveSpikesPush(AbstractPlayableSprite player) {
        // contactPushingActive is set by onSolidContact (previous frame's solid resolution).
        // Consume it now; will be re-set by this frame's onSolidContact if still pushing.
        boolean wasPushing = contactPushingActive;
        contactPushingActive = false;

        if (!wasPushing || player == null) {
            return;
        }
        // ROM: btst #5,d0 - player must have been in pushing state (from previous solid resolution)
        if (!player.getPushing()) {
            return;
        }
        // ROM: cmp.w x_pos(a1),d2 / blo.s - spike must be at or to the right of the player
        int playerX = player.getCentreX();
        if (currentX < playerX) {
            return;
        }
        // ROM: subq.w #1,$3A(a0) / bpl.s - rate limiter (first push is immediate, then every 17 frames)
        pushRateTimer--;
        if (pushRateTimer >= 0) {
            return;
        }
        pushRateTimer = PUSH_RATE_PERIOD;
        // ROM: tst.w $3C(a0) / beq.s - check remaining push distance
        if (pushDistanceRemaining <= 0) {
            return;
        }
        // ROM: subq.w #1,$3C(a0) / addq.w #1,x_pos(a0) / addq.w #1,x_pos(a1)
        pushDistanceRemaining--;
        currentX++;
        player.setCentreX((short) (playerX + 1));
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
