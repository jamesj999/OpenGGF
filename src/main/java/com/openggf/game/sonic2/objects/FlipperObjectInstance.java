package com.openggf.game.sonic2.objects;
import com.openggf.game.GameServices;
import com.openggf.level.objects.BoxObjectInstance;
import com.openggf.level.objects.ObjectAnimationState;

import com.openggf.audio.GameSound;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * CNZ Flipper Object (Obj86).
 * <p>
 * Launches the player when activated. Two types exist:
 * <ul>
 *   <li><b>Vertical Flipper (subtype 0x00)</b>: Player stands on it, launches upward with angle-based velocity</li>
 *   <li><b>Horizontal Flipper (subtype 0x01)</b>: Player pushes against it, launches horizontally</li>
 * </ul>
 * <p>
 * <b>Disassembly Reference:</b> s2.asm lines 57800-58058
 */
public class FlipperObjectInstance extends BoxObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SlopedSolidProvider {

    private static final int TYPE_VERTICAL = 0;
    private static final int TYPE_HORIZONTAL = 1;

    // Slope curves from s2.asm byte_2B3C6, byte_2B3EA, byte_2B40E
    private static final byte[] SLOPE_CURVE_0 = {
            7, 7, 7, 7, 7, 7, 7, 8, 9, 10, 11, 10, 9, 8, 7, 6,
            5, 4, 3, 2, 1, 0, -1, -2, -3, -4, -5, -6, -7, -8, -9, -10,
            -11, -12, -13, -14
    };

    private static final byte[] SLOPE_CURVE_1 = {
            6, 6, 6, 6, 6, 6, 7, 8, 9, 9, 9, 9, 9, 9, 8, 8,
            8, 8, 8, 8, 7, 7, 7, 7, 6, 6, 6, 6, 5, 5, 4, 4,
            4, 4, 4, 4
    };

    private static final byte[] SLOPE_CURVE_2 = {
            5, 5, 5, 5, 5, 6, 7, 8, 9, 10, 11, 11, 12, 12, 13, 13,
            14, 14, 15, 15, 16, 16, 17, 17, 18, 18, 17, 17, 16, 16, 16, 16,
            16, 16, 16, 16
    };

    private static final int ANIM_VERTICAL_IDLE = 0;
    private static final int ANIM_VERTICAL_TRIGGER = 1;
    private static final int ANIM_HORIZONTAL_IDLE = 2;
    private static final int ANIM_HORIZONTAL_TRIGGER_LEFT = 3;
    private static final int ANIM_HORIZONTAL_TRIGGER_RIGHT = 4;

    private final ObjectAnimationState animationState;
    private final int idleAnimId;
    private int mappingFrame;
    private int launchCooldown = 0;

    // Vertical flipper state tracking (per loc_2B20A in s2.asm)
    // 0 = not standing, 1 = standing/rolling on flipper
    private int playerFlipperState = 0;
    private boolean launchPending = false;

    // Track the player currently locked by this flipper.
    // ROM: loc_2B20A runs every frame and checks the standing bit even when the player
    // has moved away. Our onSolidContact callback only fires when there IS a contact,
    // so we must check in update() whether the player has left and release the lock.
    private AbstractPlayableSprite lockedPlayer = null;
    private boolean contactThisFrame = false;

    public FlipperObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name, 8, 8, 0.8f, 0.4f, 0.2f, false);
        this.idleAnimId = isHorizontal() ? ANIM_HORIZONTAL_IDLE : ANIM_VERTICAL_IDLE;
        this.mappingFrame = isHorizontal() ? 4 : 0;

        ObjectRenderManager renderManager = GameServices.level().getObjectRenderManager();
        this.animationState = new ObjectAnimationState(
                renderManager != null ? renderManager.getAnimations(Sonic2ObjectArtKeys.ANIM_FLIPPER) : null,
                idleAnimId,
                mappingFrame);
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (player == null || launchCooldown > 0) {
            return;
        }

        // If player entered debug mode while on the flipper, reset flipper state
        if (player.isDebugMode() && playerFlipperState != 0) {
            releaseLockedPlayer();
            playerFlipperState = 0;
            launchPending = false;
            return;
        }

        if (isHorizontal()) {
            // Horizontal flipper: launch on push (loc_2B35C)
            if (contact.pushing()) {
                applyHorizontalLaunch(player);
            }
        } else {
            // Mark that we received a contact callback this frame - used by update()
            // to detect when the player has moved out of range entirely (no callback).
            contactThisFrame = true;

            // Vertical flipper state machine (loc_2B20A - loc_2B288)
            if (contact.standing()) {
                // ROM: move.b #1,obj_control(a1) - locks ALL player input including jumping
                // This is set every frame while standing on the flipper
                player.setControlLocked(true);
                lockedPlayer = player;

                if (playerFlipperState == 0) {
                    // First frame standing: enter rolling state (loc_2B20A)
                    // We use pinball_mode to prevent rolling from being cleared
                    player.setPinballMode(true);
                    // ROM: bset #status.player.rolling / bne.s loc_2B238 / addq.w #5,y_pos
                    // Only adjust Y if not already rolling (the bne.s skips adjustment if already rolling)
                    if (!player.getRolling()) {
                        player.setRolling(true);
                        player.setY((short) (player.getY() + player.getRollHeightAdjustment()));
                    }
                    playerFlipperState = 1;
                } else {
                    // Already on flipper: check for jump button (loc_2B23C)
                    if (player.isJumpPressed()) {
                        launchPending = true;
                    } else {
                        // Slide player based on animation frame (loc_2B254)
                        applyFlipperSlide(player);
                    }
                }
            } else {
                // Player left flipper without jumping (loc_2B23C branch to clear)
                // ROM: move.b #0,obj_control(a1)
                if (playerFlipperState != 0) {
                    releaseLockedPlayer();
                }
                playerFlipperState = 0;
            }

            // Process pending launch (loc_2B290)
            if (launchPending) {
                launchPending = false;
                applyVerticalLaunch(player);
            }
        }
    }

    /**
     * Slides the player along the flipper surface based on animation frame.
     * ROM: loc_2B254 - applies small X velocity based on mapping_frame
     */
    private void applyFlipperSlide(AbstractPlayableSprite player) {
        int slideAmount = mappingFrame - 1;
        if (!isFlippedHorizontal()) {
            slideAmount = -slideAmount;
            player.setDirection(Direction.LEFT);
        } else {
            player.setDirection(Direction.RIGHT);
        }
        player.setX((short)(player.getX() + slideAmount));
        player.setXSpeed((short)(slideAmount << 8));
        player.setGSpeed((short)(slideAmount << 8));
        player.setYSpeed((short) 0);
    }

    private void applyVerticalLaunch(AbstractPlayableSprite player) {
        int dx = player.getCentreX() - spawn.x();
        if (isFlippedHorizontal()) {
            dx = -dx;
        }

        int adjustedDistance = dx + 0x23;
        int cappedDistance = Math.min(adjustedDistance, 0x40);

        int velocityMagnitude = -(0x800 + (cappedDistance << 5));

        int angle = (adjustedDistance >> 2) + 0x40;

        // Convert Mega Drive angle (0x00-0xFF, where 0x40 = up) to radians
        double radians = (angle & 0xFF) * 2.0 * Math.PI / 256.0;

        // ROM uses CalcSine which returns sin/cos scaled by ~256, then multiplies
        // by magnitude and divides by 256 (asr.l #8). Since Math.sin/cos return
        // -1.0 to 1.0 (not scaled), we just multiply directly without dividing.
        int yVel = (int) (velocityMagnitude * Math.sin(radians));
        int xVel = (int) (velocityMagnitude * Math.cos(radians));

        if (isFlippedHorizontal()) {
            xVel = -xVel;
        }

        player.setYSpeed((short) yVel);
        player.setXSpeed((short) xVel);
        player.setAir(true);
        player.setPushing(false);  // Clear pushing state - matches BumperObjectInstance pattern
        player.setGSpeed((short) 0);

        // ROM: move.b #0,obj_control(a1) at loc_2B2E2 - release control lock
        player.setControlLocked(false);
        player.setPinballMode(false);
        lockedPlayer = null;

        // Clear solid object riding state to prevent the object system from
        // continuing to track the player's position relative to the flipper.
        // This matches the ROM behavior of clearing status.player.on_object (loc_2B2E2).
        var objectManager = services().objectManager();
        if (objectManager != null) {
            objectManager.clearRidingObject(player);
        }

        // Reset flipper state
        playerFlipperState = 0;

        triggerVerticalAnimation();
        playFlipperSound();
        launchCooldown = 16;
    }

    private void applyHorizontalLaunch(AbstractPlayableSprite player) {
        // ROM default: xVel = -0x1000 (LEFT) at loc_2B35C
        int xVel = -0x1000;

        int newX = player.getX() + 8;

        // ROM: If player is RIGHT of flipper (flipper.x - player.x < 0), negate velocity
        // This ensures player is always launched AWAY from the flipper
        boolean playerIsRightOfFlipper = spawn.x() - player.getCentreX() < 0;

        if (playerIsRightOfFlipper) {
            // Player is RIGHT of flipper: launch them RIGHT (away)
            newX -= 16;
            xVel = -xVel;  // +0x1000 (RIGHT)
            player.setDirection(Direction.RIGHT);
        } else {
            // Player is LEFT of flipper: keep xVel = -0x1000 (LEFT, away)
            player.setDirection(Direction.LEFT);
        }

        player.setX((short) newX);
        player.setXSpeed((short) xVel);
        player.setGSpeed((short) xVel);
        // NOTE: y_vel is NOT cleared in the ROM for horizontal flippers (loc_2B35C-loc_2B3BC)
        // The player stays grounded and rolls at high speed - y_vel is handled by the movement system
        player.setPushing(false);  // Clear pushing state - matches BumperObjectInstance pattern

        // ROM: move.w #$F,move_lock(a1) - lock player input for 15 frames
        player.setMoveLockTimer(15);
        // ROM: bset #status.player.rolling / bne.s loc_2B3BC / addq.w #5,y_pos
        // Only adjust Y if not already rolling
        if (!player.getRolling()) {
            player.setRolling(true);
            player.setY((short) (player.getY() + player.getRollHeightAdjustment()));
        }
        // ROM always explicitly sets collision radii (y=14, x=7) at loc_2B3BC
        player.applyRollingRadii(false);

        triggerHorizontalAnimation(playerIsRightOfFlipper);
        playFlipperSound();
        launchCooldown = 16;
    }

    private void triggerVerticalAnimation() {
        animationState.setAnimId(ANIM_VERTICAL_TRIGGER);
    }

    private void triggerHorizontalAnimation(boolean launchRight) {
        animationState.setAnimId(launchRight ? ANIM_HORIZONTAL_TRIGGER_RIGHT : ANIM_HORIZONTAL_TRIGGER_LEFT);
    }

    private void playFlipperSound() {
        try {
            if (GameServices.audio() != null) {
                GameServices.audio().playSfx(GameSound.FLIPPER);
            }
        } catch (Exception e) {
            // Prevent audio failure from breaking game logic
        }
    }

    private boolean isHorizontal() {
        return (spawn.subtype() & 0x01) != 0;
    }

    private boolean isFlippedHorizontal() {
        return (spawn.renderFlags() & 0x1) != 0;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return true;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        if (isHorizontal()) {
            // ROM: d1=#$13 (19), d2=#$18 (24), d3=#$19 (25) at loc_2B312
            return new SolidObjectParams(19, 24, 25);
        }
        // ROM: d1=#$23 (35), d2=#6 at loc_2B1B6
        return new SolidObjectParams(35, 6, 6);
    }

    @Override
    public byte[] getSlopeData() {
        if (isHorizontal()) {
            return null;
        }
        int frame = mappingFrame % 3;
        return switch (frame) {
            case 1 -> SLOPE_CURVE_1;
            case 2 -> SLOPE_CURVE_2;
            default -> SLOPE_CURVE_0;
        };
    }

    @Override
    public boolean isSlopeFlipped() {
        return isFlippedHorizontal();
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (launchCooldown > 0) {
            launchCooldown--;
        }

        // ROM: loc_2B20A runs every frame as part of the object's main routine.
        // It checks the standing bit (set/cleared by SlopedSolid) and clears
        // obj_control when the player is no longer standing. Our onSolidContact
        // callback only fires when collision is detected, so if the player has
        // moved out of range entirely, we never get a callback. Detect that here.
        if (!isHorizontal() && playerFlipperState != 0 && !contactThisFrame) {
            releaseLockedPlayer();
            playerFlipperState = 0;
        }
        contactThisFrame = false;

        animationState.update();
        mappingFrame = animationState.getMappingFrame();
    }

    /**
     * Release the control lock on the tracked player.
     * ROM: move.b #0,obj_control(a1) at loc_2B23C when player leaves flipper.
     */
    private void releaseLockedPlayer() {
        if (lockedPlayer != null) {
            lockedPlayer.setControlLocked(false);
            lockedPlayer.setPinballMode(false);
            lockedPlayer = null;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            super.appendRenderCommands(commands);
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.FLIPPER);
        if (renderer == null || !renderer.isReady()) {
            super.appendRenderCommands(commands);
            return;
        }
        boolean hFlip = isFlippedHorizontal();
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
        renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), hFlip, vFlip);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }
}
