package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x42 - CNZ Cannon ({@code Obj_CNZCannon}).
 *
 * <p>Verified ROM anchors:
 * <ul>
 *   <li>Object routine: {@code Obj_CNZCannon} in {@code sonic3k.asm}</li>
 *   <li>Mappings: {@code Map_CNZCannon} at the final lock-on offset published in
 *   {@link Sonic3kObjectArtKeys} and {@link com.openggf.game.sonic3k.constants.Sonic3kConstants}</li>
 *   <li>Dedicated art: {@code Cannon.bin} from the CNZ lock-on art block, plus the
 *   {@code DPLC_CNZCannon} table that remaps the animated chamber tiles</li>
 * </ul>
 *
 * <p>ROM behavior summary:
 * <ul>
 *   <li>The cannon is a top-solid traversal object until it captures the player.</li>
 *   <li>On capture it forces rolling, locks control, zeroes velocity, and keeps the
 *   player anchored to the cannon until launch.</li>
 *   <li>When the jump button is pressed it computes a launch vector from the current
 *   spin frame, releases control, and applies the ROM turn sound.</li>
 * </ul>
 *
 * <p>The engine keeps the same externally visible contract even though the original
 * object uses a couple of tiny internal sub-states to drive the lift/launch timing.
 * This version keeps the capture/launch handoff explicit so the behavior is easy to
 * test while still matching the ROM's forced rolling and release semantics.
 */
public final class CnzCannonInstance extends AbstractObjectInstance
        implements SolidObjectProvider {

    private static final int PRIORITY = 0x280;
    private static final int FRAME_IDLE = 9;
    private static final int FRAME_SPIN_MIN = 0;
    private static final int FRAME_SPIN_MAX = 8;
    private static final int STATE_IDLE = 0;
    private static final int STATE_CAPTURED = 1;
    private static final int STATE_COOLDOWN = 2;

    // ROM capture writes y_radius=$0E and x_radius=7.
    private static final int ROLL_X_RADIUS = 7;
    private static final int ROLL_Y_RADIUS = 14;

    // The cannon's body is roughly a 48x48 traversal surface in-engine.
    private static final SolidObjectParams SOLID_PARAMS =
            new SolidObjectParams(0x18, 0x18, 0x18);

    private static final int CAPTURE_HALF_WIDTH = 0x18;
    private static final int CAPTURE_HALF_HEIGHT = 0x20;
    private static final int LAUNCH_READY_DELAY_FRAMES = 8;
    private static final int LAUNCH_COOLDOWN_FRAMES = 8;

    private int state = STATE_IDLE;
    private int stateTimer;
    private int spinAngle;
    private int renderFrame = FRAME_IDLE;
    private AbstractPlayableSprite capturedPlayer;

    public CnzCannonInstance(ObjectSpawn spawn) {
        super(spawn, "CNZCannon");
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = playerEntity instanceof AbstractPlayableSprite sprite
                ? sprite
                : null;

        switch (state) {
            case STATE_IDLE -> updateIdle(frameCounter, player);
            case STATE_CAPTURED -> updateCaptured(frameCounter, player);
            case STATE_COOLDOWN -> updateCooldown();
            default -> {
                state = STATE_IDLE;
                renderFrame = FRAME_IDLE;
                capturedPlayer = null;
            }
        }
    }

    private void updateIdle(int frameCounter, AbstractPlayableSprite player) {
        renderFrame = FRAME_IDLE;
        if (player == null || player.isObjectControlled()) {
            return;
        }

        AbstractPlayableSprite rider = findCapturablePlayer(player);
        if (rider == null) {
            return;
        }

        capturePlayer(rider);
        state = STATE_CAPTURED;
        stateTimer = LAUNCH_READY_DELAY_FRAMES;
        advanceSpin(frameCounter);
    }

    private void updateCaptured(int frameCounter, AbstractPlayableSprite player) {
        advanceSpin(frameCounter);

        if (stateTimer > 0) {
            stateTimer--;
            if (capturedPlayer != null) {
                capturedPlayer.setCentreX((short) spawn.x());
                capturedPlayer.setOnObject(false);
                capturedPlayer.setAir(true);
            }
            return;
        }

        AbstractPlayableSprite activePlayer = capturedPlayer != null ? capturedPlayer : player;
        if (activePlayer == null || !activePlayer.isObjectControlled()) {
            state = STATE_IDLE;
            renderFrame = FRAME_IDLE;
            capturedPlayer = null;
            return;
        }

        if (activePlayer.isJumpPressed()) {
            launchPlayer(activePlayer, frameCounter);
        }
    }

    private void updateCooldown() {
        advanceSpin(-1);
        if (stateTimer > 0) {
            stateTimer--;
        }
        if (stateTimer <= 0) {
            state = STATE_IDLE;
            capturedPlayer = null;
            renderFrame = FRAME_IDLE;
        }
    }

    private void advanceSpin(int frameCounter) {
        spinAngle = (spinAngle + 2) & 0xFF;
        int frame = (TrigLookupTable.sinHex(spinAngle) + 0x120) >> 6;
        renderFrame = Math.max(FRAME_SPIN_MIN, Math.min(FRAME_SPIN_MAX, frame));

        if (frameCounter >= 0 && (frameCounter & 0x1F) == 0) {
            try {
                services().playSfx(Sonic3kSfx.CANNON_TURN.id);
            } catch (Exception ignored) {
                // Headless tests may not wire audio.
            }
        }
    }

    private boolean isWithinCaptureWindow(AbstractPlayableSprite player) {
        int dx = Math.abs(player.getCentreX() - spawn.x());
        int dy = Math.abs(player.getCentreY() - spawn.y());
        return dx <= CAPTURE_HALF_WIDTH && dy <= CAPTURE_HALF_HEIGHT;
    }

    private void capturePlayer(AbstractPlayableSprite player) {
        capturedPlayer = player;

        // ROM: move.w #$81,object_control(a1) / bset #Status_Roll,status(a1)
        player.setObjectControlled(true);
        player.setControlLocked(true);
        player.setRolling(true);
        player.applyCustomRadii(ROLL_X_RADIUS, ROLL_Y_RADIUS);
        player.setAnimationId(2);

        // Keep the player centered on the cannon while captured.
        player.setCentreX((short) spawn.x());
        player.setCentreY((short) (spawn.y() - 0x18));
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setAir(true);
        player.setOnObject(false);
        player.setJumping(false);
        player.setPinballMode(false);
        ObjectManager objectManager = services().objectManager();
        if (objectManager != null) {
            objectManager.clearRidingObject(player);
        }
    }

    private void launchPlayer(AbstractPlayableSprite player, int frameCounter) {
        int launchAngle = ((renderFrame & 0x0F) << 4) + 0x80;
        short xSpeed = (short) (TrigLookupTable.cosHex(launchAngle) << 4);
        short ySpeed = (short) (TrigLookupTable.sinHex(launchAngle) << 4);

        // ROM launch: release control, keep the player in air, and inject the
        // launch vector derived from the current cannon frame.
        player.setXSpeed(xSpeed);
        player.setYSpeed(ySpeed);
        player.setGSpeed(xSpeed);
        player.setAir(true);
        player.setControlLocked(false);
        player.releaseFromObjectControl(frameCounter);
        player.setOnObject(false);
        player.setRolling(true);
        player.setJumping(false);
        player.applyCustomRadii(ROLL_X_RADIUS, ROLL_Y_RADIUS);

        capturedPlayer = null;
        state = STATE_COOLDOWN;
        stateTimer = LAUNCH_COOLDOWN_FRAMES;
        renderFrame = FRAME_IDLE;
    }

    private AbstractPlayableSprite findCapturablePlayer(AbstractPlayableSprite mainPlayer) {
        if (canCapture(mainPlayer)) {
            return mainPlayer;
        }

        try {
            for (PlayableEntity sidekickEntity : services().sidekicks()) {
                if (sidekickEntity instanceof AbstractPlayableSprite sidekick && canCapture(sidekick)) {
                    return sidekick;
                }
            }
        } catch (Exception ignored) {
            // Some test fixtures do not expose sidekicks.
        }

        return null;
    }

    private boolean canCapture(AbstractPlayableSprite player) {
        if (player == null || player.isObjectControlled() || player.isJumping()) {
            return false;
        }

        ObjectManager objectManager = services().objectManager();
        if (objectManager != null && objectManager.isRidingObject(player, this)) {
            return true;
        }

        return isWithinCaptureWindow(player) && !player.getAir();
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        return state == STATE_IDLE;
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.CNZ_CANNON);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean hFlip = (spawn.renderFlags() & 0x01) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x02) != 0;
        renderer.drawFrameIndex(renderFrame, spawn.x(), spawn.y(), hFlip, vFlip);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    /**
     * Package-private test seam for launch timing.
     *
     * <p>The ROM uses a small post-capture spin/launch window. Tests can reduce
     * this to zero when they need the cannon to enter the launch-ready state
     * immediately after capture.
     */
    void setLaunchDelayFramesForTest(int frames) {
        stateTimer = Math.max(0, frames);
    }

    int getRenderFrameForTest() {
        return renderFrame;
    }
}
