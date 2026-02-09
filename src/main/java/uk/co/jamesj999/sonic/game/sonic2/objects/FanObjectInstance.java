package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.game.sonic2.OscillationManager;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AnimationIds;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * OOZ Fan (Object 0x3F).
 * Wind-blowing object that pushes the player horizontally or vertically.
 * The vertical variant causes the player to tumble in the air.
 * <p>
 * Based on Obj3F from the Sonic 2 disassembly (s2.asm lines 57155-57381).
 * <p>
 * Subtype encoding:
 * - Bit 7 (0x80): Vertical fan (upward blowing) if set, horizontal if clear
 * - Bit 0 (0x01): Reverse direction (affects mapping frame offset)
 * <p>
 * Render flag bit 0 controls X-flip for horizontal fans.
 */
public class FanObjectInstance extends AbstractObjectInstance {

    // Timer durations (in frames)
    private static final int ACTIVE_DURATION = 0xB4;  // 180 frames
    private static final int IDLE_DURATION = 0x78;    // 120 frames

    // Number of mapping frames per variant
    private static final int MAPPING_FRAME_COUNT = 11;

    private final boolean isVertical;
    private final boolean reverseDirection;
    private final boolean xFlipped;

    // Timer state machine
    private int timer;
    private boolean active; // stateToggle: true = pushing, false = idle

    // Animation state
    private int animCounter;
    private int animFrameDuration;
    private int mappingFrame;

    public FanObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.isVertical = (spawn.subtype() & 0x80) != 0;
        this.reverseDirection = (spawn.subtype() & 0x01) != 0;
        this.xFlipped = (spawn.renderFlags() & 0x01) != 0;

        // Init: mapping_frame starts at 0 or 5 based on reverse bit
        this.mappingFrame = reverseDirection ? 5 : 0;
        this.timer = 0;
        this.active = false;
        this.animCounter = 0;
        this.animFrameDuration = 0;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Timer state machine
        timer--;
        if (timer <= 0) {
            animCounter = 0;
            active = !active;
            timer = active ? ACTIVE_DURATION : IDLE_DURATION;
        }

        // Animation
        updateAnimation();

        // Apply push to main player
        if (player != null) {
            if (isVertical) {
                applyVerticalPush(player);
            } else {
                applyHorizontalPush(player);
            }
        }

        // Apply push to sidekick
        AbstractPlayableSprite sidekick = SpriteManager.getInstance().getSidekick();
        if (sidekick != null) {
            if (isVertical) {
                applyVerticalPush(sidekick);
            } else {
                applyHorizontalPush(sidekick);
            }
        }
    }

    private void updateAnimation() {
        animFrameDuration--;
        if (animFrameDuration <= 0) {
            if (active) {
                // Active: speed up animation over time
                if (animCounter < 0x400) {
                    animCounter += 0x2A;
                }
                animFrameDuration = animCounter >> 8;
            } else {
                // Idle: no delay between frames
                animFrameDuration = 0;
            }

            // Advance mapping frame (cycles 0-10)
            mappingFrame++;
            if (mappingFrame >= MAPPING_FRAME_COUNT) {
                mappingFrame = 0;
            }
        }
    }

    /**
     * Horizontal fan push subroutine.
     * Based on Obj3F_PushPlayerHoriz from the disassembly.
     */
    private void applyHorizontalPush(AbstractPlayableSprite player) {
        if (player.isHurt() || player.isObjectControlled()) {
            return;
        }

        int objX = spawn.x();
        int objY = spawn.y();
        int playerX = player.getCentreX();
        int playerY = player.getCentreY();

        // X range check
        int dx = playerX - objX;
        if (!xFlipped) {
            dx = -dx;
        }
        dx += 0x50;
        if (dx < 0 || dx >= 0xF0) {
            return;
        }

        // Y range check
        int dy = playerY + 0x60 - objY;
        if (dy < 0 || dy >= 0x70) {
            return;
        }

        // Push calculation
        dx -= 0x50;
        if (dx < 0) {
            // NOT then double (ROM: not.w d0 / add.w d0,d0)
            dx = (~dx & 0xFFFF) * 2;
        }
        dx += 0x60;
        if (xFlipped) {
            dx = -dx;
        }
        // Negate low byte, arithmetic shift right 4
        // ROM: neg.b d0 / ext.w d0 / asr.w #4,d0
        int lowByte = dx & 0xFF;
        int negated = (-lowByte) & 0xFF;
        // Sign-extend byte to word
        if (negated >= 0x80) {
            negated -= 0x100;
        }
        int push = negated >> 4; // arithmetic shift right 4
        if (reverseDirection) {
            push = -push;
        }

        player.setX((short) (player.getX() + push));
    }

    /**
     * Vertical fan push subroutine.
     * Based on Obj3F_PushPlayerVert from the disassembly.
     */
    private void applyVerticalPush(AbstractPlayableSprite player) {
        if (player.isHurt() || player.isObjectControlled()) {
            return;
        }

        int objX = spawn.x();
        int objY = spawn.y();
        int playerX = player.getCentreX();
        int playerY = player.getCentreY();

        // X range check
        int dx = playerX - objX + 0x40;
        if (dx < 0 || dx >= 0x80) {
            return;
        }

        // Y range check with oscillation
        int oscillation = (byte) OscillationManager.getByte(0x14); // sign-extend
        int dy = playerY + oscillation + 0x60 - objY;
        if (dy < 0 || dy >= 0x90) {
            return;
        }

        // Push calculation
        dy -= 0x60;
        if (dy < 0) {
            dy = (~dy & 0xFFFF) * 2;
        }
        dy += 0x60;
        // Negate, arithmetic shift right 4
        int push = (-dy) >> 4;
        player.setY((short) (player.getY() + push));

        // Set player airborne state and tumble
        player.setAir(true);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 1);
        if (player.getFlipAngle() == 0) {
            player.setFlipAngle(1);
            player.setAnimationId(Sonic2AnimationIds.WALK);
            player.setFlipsRemaining(0x7F);
            player.setFlipSpeed(8);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        String artKey = isVertical ? Sonic2ObjectArtKeys.OOZ_FAN_VERT : Sonic2ObjectArtKeys.OOZ_FAN_HORIZ;
        PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean vFlip = (spawn.renderFlags() & 0x02) != 0;
        renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), xFlipped, vFlip);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }
}
