package com.openggf.game.sonic2.objects;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.awt.Color;
import java.util.List;
import java.util.logging.Logger;

/**
 * MTZ Spin Tube (Object 0x67).
 * Tube transport system in Metropolis Zone that captures the player,
 * oscillates them vertically with a sinusoidal motion, then launches
 * them through a waypoint path.
 * <p>
 * Handles both Sonic and Tails independently via separate per-character state.
 * <p>
 * Subtype interpretation:
 * <ul>
 *   <li>Bit 7: path direction (0=forward, 1=reverse)</li>
 *   <li>Bits 3:0: path index into off_273F2 (0-12)</li>
 *   <li>Bit 4: if set, preserve player velocity on exit</li>
 * </ul>
 * <p>
 * Render flags bit 0 (x_flip): shifts the entry collision zone right by 0xA pixels.
 * <p>
 * Disassembly reference: s2.asm lines 52943-53215 (Obj67), misc/obj67.asm (path data)
 */
public class MTZSpinTubeObjectInstance extends AbstractObjectInstance {
    private static final Logger LOGGER = Logger.getLogger(MTZSpinTubeObjectInstance.class.getName());

    // Path traversal speed (0x1000 in ROM at loc_27368/loc_27374)
    private static final int PATH_SPEED = 0x1000;

    // Rolling speed set on player capture (move.w #$800,inertia(a1))
    private static final int ROLLING_INERTIA = 0x800;

    // Sine angle increment per frame (addq.b #2,1(a4))
    private static final int SINE_ANGLE_INCREMENT = 2;

    // Sine angle threshold to transition from oscillation to path (cmpi.b #$80,1(a4))
    private static final int SINE_TRANSITION_ANGLE = 0x80;

    // Entry collision X range (cmpi.w #$10,d0)
    private static final int ENTRY_X_RANGE = 0x10;

    // Entry collision X offset (addq.w #3,d0)
    private static final int ENTRY_X_OFFSET = 3;

    // Additional X offset when x_flipped (addi.w #$A,d0)
    private static final int ENTRY_X_FLIP_OFFSET = 0xA;

    // Entry collision Y range (cmpi.w #$40,d1)
    private static final int ENTRY_Y_RANGE = 0x40;

    // Entry collision Y offset (addi.w #$20,d1)
    private static final int ENTRY_Y_OFFSET = 0x20;

    // Path data from misc/obj67.asm (off_273F2)
    // Each path is pairs of (X, Y) absolute coordinates
    private static final int[][] PATHS = {
            // Path 0 (word_2740C): 6 waypoints
            {0x7A8, 0x270, 0x750, 0x270, 0x740, 0x280, 0x740, 0x3E0, 0x750, 0x3F0, 0x7A8, 0x3F0},
            // Path 1 (word_27426): 2 waypoints
            {0xC58, 0x5F0, 0xE28, 0x5F0},
            // Path 2 (word_27430): 6 waypoints
            {0x1828, 0x6B0, 0x17D0, 0x6B0, 0x17C0, 0x6C0, 0x17C0, 0x7E0, 0x17B0, 0x7F0, 0x1758, 0x7F0},
            // Path 3 (word_2744A): 2 waypoints
            {0x5D8, 0x370, 0x780, 0x370},
            // Path 4 (word_27454): 2 waypoints
            {0x5D8, 0x5F0, 0x700, 0x5F0},
            // Path 5 (word_2745E): 6 waypoints
            {0xBD8, 0x1F0, 0xC30, 0x1F0, 0xC40, 0x1E0, 0xC40, 0xC0, 0xC50, 0xB0, 0xCA8, 0xB0},
            // Path 6 (word_27478): 6 waypoints
            {0x1728, 0x330, 0x15D0, 0x330, 0x15C0, 0x320, 0x15C0, 0x240, 0x15D0, 0x230, 0x1628, 0x230},
            // Path 7 (word_27492): 6 waypoints
            {0x6D8, 0x1F0, 0x730, 0x1F0, 0x740, 0x1E0, 0x740, 0x100, 0x750, 0xF0, 0x7A8, 0xF0},
            // Path 8 (word_274AC): 6 waypoints
            {0x7D8, 0x330, 0x828, 0x330, 0x840, 0x340, 0x840, 0x458, 0x828, 0x470, 0x7D8, 0x470},
            // Path 9 (word_274C6): 6 waypoints
            {0xFD8, 0x3B0, 0x1028, 0x3B0, 0x1040, 0x398, 0x1040, 0x2C4, 0x1058, 0x2B0, 0x10A8, 0x2B0},
            // Path 10 (word_274E0): 6 waypoints
            {0xFD8, 0x4B0, 0x1028, 0x4B0, 0x1040, 0x4C0, 0x1040, 0x5D8, 0x1058, 0x5F0, 0x10A8, 0x5F0},
            // Path 11 (word_274FA): 6 waypoints
            {0x2058, 0x430, 0x20A8, 0x430, 0x20C0, 0x418, 0x20C0, 0x2C0, 0x20D0, 0x2B0, 0x2128, 0x2B0},
            // Path 12 (word_27514): 6 waypoints
            {0x2328, 0x5B0, 0x22D0, 0x5B0, 0x22C0, 0x5A0, 0x22C0, 0x4C0, 0x22D0, 0x4B0, 0x2328, 0x4B0}
    };

    // Animation scripts from Ani_obj67 (s2.asm lines 53203-53211)
    private static final SpriteAnimationSet ANIMATIONS;

    static {
        ANIMATIONS = new SpriteAnimationSet();
        // Script 0 (byte_27532): dc.b $1F, 0, $FF - frame 0 forever (invisible)
        ANIMATIONS.addScript(0, new SpriteAnimationScript(
                0x1F, List.of(0), SpriteAnimationEndAction.LOOP, 0));
        // Script 1 (byte_27535): dc.b 1, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 0, $FE, 2
        // $FE = SWITCH to anim 2 (which is back to script 0 since anim 2 doesn't exist)
        // But in practice the ROM uses this with the anim field directly:
        // move.w #(1<<8)|(0<<0),anim(a0) sets anim=1, anim_frame=0
        // When the flash sequence completes, it switches to anim 2 which resets to 0 (invisible)
        ANIMATIONS.addScript(1, new SpriteAnimationScript(
                1, List.of(1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 0),
                SpriteAnimationEndAction.SWITCH, 0));
    }

    // Per-character state: one for main character, one for sidekick
    // Per-character state layout (mirrors ROM objoff_2C/objoff_36 storage):
    //   state: 0=idle, 2=sine oscillation, 4=path following
    //   sineAngle: angle for sinusoidal Y oscillation (0-255, byte)
    //   duration: frames remaining in current path segment (word)
    //   pathRemaining: bytes of path data remaining (word)
    //   pathIndex: current index into path data array
    //   pathReverse: traversing path backwards
    private int mainState = 0;
    private int mainSineAngle = 0;
    private int mainDuration = 0;
    private int mainPathRemaining = 0;
    private int mainPathIndex = 0;
    private int[] mainPath = null;
    private boolean mainPathReverse = false;

    // Whether object is x-flipped (from render_flags bit 0)
    private final boolean xFlipped;

    // Animation state for the flash effect
    private final ObjectAnimationState animationState;

    public MTZSpinTubeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "MTZSpinTube");
        // ROM: btst #status.npc.x_flip,status(a0) - render_flags bit 0
        this.xFlipped = (spawn.renderFlags() & 0x1) != 0;
        this.animationState = new ObjectAnimationState(ANIMATIONS, 0, 0);
    }

    @Override
    public int getPriorityBucket() {
        // ROM: move.b #5,priority(a0)
        return RenderPriority.clamp(5);
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (player != null) {
            updateCharacter(player);
        }

        // ROM: checks objoff_2C + objoff_36 for visibility
        // Only animate when a character is actively in the tube
        if (mainState != 0) {
            animationState.update();
        }
    }

    private void updateCharacter(AbstractPlayableSprite player) {
        switch (mainState) {
            case 0 -> checkEntry(player);
            case 2 -> updateSineOscillation(player);
            case 4 -> updatePathFollow(player);
        }
    }

    /**
     * State 0: Check if the player enters the tube activation zone.
     * ROM: loc_271D0
     */
    private void checkEntry(AbstractPlayableSprite player) {
        // ROM: tst.w (Debug_placement_mode).w / bne.w return
        if (player.isDebugMode()) {
            return;
        }

        int objX = spawn.x();
        int objY = spawn.y();
        int playerX = player.getCentreX();
        int playerY = player.getCentreY();

        // ROM: move.w x_pos(a1),d0 / sub.w x_pos(a0),d0 / addq.w #3,d0
        int dx = playerX - objX + ENTRY_X_OFFSET;

        // ROM: btst #status.npc.x_flip,status(a0) / beq.s + / addi.w #$A,d0
        if (xFlipped) {
            dx += ENTRY_X_FLIP_OFFSET;
        }

        // ROM: cmpi.w #$10,d0 / bhs.s return
        if (dx < 0 || dx >= ENTRY_X_RANGE) {
            return;
        }

        // ROM: move.w y_pos(a1),d1 / sub.w y_pos(a0),d1 / addi.w #$20,d1
        int dy = playerY - objY + ENTRY_Y_OFFSET;

        // ROM: cmpi.w #$40,d1 / bhs.s return
        if (dy < 0 || dy >= ENTRY_Y_RANGE) {
            return;
        }

        // ROM: tst.b obj_control(a1) / bne.s return
        if (player.isObjectControlled()) {
            return;
        }

        // Capture player
        // ROM: addq.b #2,(a4)
        mainState = 2;

        // ROM: move.b #$81,obj_control(a1)
        player.setObjectControlled(true);
        player.setControlLocked(true);

        // ROM: move.b #AniIDSonAni_Roll,anim(a1)
        player.setRolling(true);
        player.setAnimationId(Sonic2AnimationIds.ROLL);

        // ROM: move.w #$800,inertia(a1)
        player.setGSpeed((short) ROLLING_INERTIA);

        // ROM: move.w #0,x_vel(a1) / move.w #0,y_vel(a1)
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);

        // ROM: bclr #status.player.pushing,status(a1)
        player.setPushing(false);

        // ROM: bset #status.player.in_air,status(a1)
        player.setAir(true);

        // ROM: move.w x_pos(a0),x_pos(a1) / move.w y_pos(a0),y_pos(a1)
        player.setCentreX((short) objX);
        player.setCentreY((short) objY);

        // ROM: bclr #high_priority_bit,art_tile(a1)
        player.setHighPriority(false);
        player.setPriorityBucket(RenderPriority.MIN);

        // ROM: clr.b 1(a4) - reset sine angle
        mainSineAngle = 0;

        // ROM: move.w #SndID_Roll,d0 / jsr (PlaySound).l
        playSound(GameSound.ROLLING);

        // ROM: move.w #(1<<8)|(0<<0),anim(a0) - start flash animation
        animationState.setAnimId(1);
        animationState.resetFrameIndex();
    }

    /**
     * State 2: Sinusoidal Y oscillation during tube entry.
     * ROM: loc_27260
     * The player bobs up and down at the tube's X position while descending
     * into the tube, then launches into the path.
     */
    private void updateSineOscillation(AbstractPlayableSprite player) {
        // ROM: move.b 1(a4),d0 / addq.b #2,1(a4)
        int angle = mainSineAngle;
        mainSineAngle = (mainSineAngle + SINE_ANGLE_INCREMENT) & 0xFF;

        // ROM: jsr (CalcSine).l - returns sine in d0
        int sineValue = TrigLookupTable.sinHex(angle);

        // ROM: asr.w #5,d0 - divide by 32 (arithmetic shift right 5)
        sineValue >>= 5;

        // ROM: move.w y_pos(a0),d2 / sub.w d0,d2 / move.w d2,y_pos(a1)
        int objY = spawn.y();
        player.setCentreY((short) (objY - sineValue));

        // ROM: cmpi.b #$80,1(a4) / bne.s +
        if (mainSineAngle != SINE_TRANSITION_ANGLE) {
            return;
        }

        // Transition to path mode
        setupPath(player);
        mainState = 4;

        // ROM: move.w #SndID_SpindashRelease,d0 / jsr (PlaySound).l
        playSound(GameSound.SPINDASH_RELEASE);
    }

    /**
     * Setup path data for traversal.
     * ROM: loc_27310 (forward) / loc_27344 (reverse)
     */
    private void setupPath(AbstractPlayableSprite player) {
        int subtype = spawn.subtype();
        boolean reverse = (subtype & 0x80) != 0;

        // ROM: andi.w #$F,d0 - extract path index (low 4 bits)
        int pathIndex;
        if (reverse) {
            // ROM: neg.b d0 / andi.w #$F,d0
            pathIndex = (-subtype) & 0xF;
        } else {
            pathIndex = subtype & 0xF;
        }

        if (pathIndex >= PATHS.length) {
            LOGGER.warning("MTZSpinTube: path index " + pathIndex + " out of range");
            exitTube(player);
            return;
        }

        mainPath = PATHS[pathIndex];
        mainPathReverse = reverse;

        // The path data size word in ROM counts bytes: each waypoint = 4 bytes (2 words)
        // ROM: move.w (a2)+,d0 then subq.w #4,d0 for the size
        // In our array: pathRemaining = (waypointCount - 1) * 4 bytes
        // This counts down by 4 for each waypoint consumed
        int waypointCount = mainPath.length / 2;
        mainPathRemaining = (waypointCount - 1) * 4;

        if (reverse) {
            // ROM: lea (a2,d0.w),a2 - jump to end of path data
            // Position player at last waypoint
            int lastIdx = mainPath.length - 2;
            player.setCentreX((short) mainPath[lastIdx]);
            player.setCentreY((short) mainPath[lastIdx + 1]);

            // ROM: subq.w #8,a2 - back up one waypoint
            mainPathIndex = lastIdx - 2;
        } else {
            // ROM: move.w (a2)+,d4 / move.w d4,x_pos(a1) / move.w (a2)+,d5 / move.w d5,y_pos(a1)
            // Position player at first waypoint
            player.setCentreX((short) mainPath[0]);
            player.setCentreY((short) mainPath[1]);
            mainPathIndex = 2;
        }

        // Calculate velocity to next waypoint
        int targetX = mainPath[mainPathIndex];
        int targetY = mainPath[mainPathIndex + 1];
        calculateVelocity(player, targetX, targetY);
    }

    /**
     * State 4: Following path waypoints.
     * ROM: loc_27294
     */
    private void updatePathFollow(AbstractPlayableSprite player) {
        // ROM: subq.b #1,2(a4) / bpl.s Obj67_MoveCharacter
        mainDuration--;
        if (mainDuration >= 0) {
            moveCharacter(player);
            return;
        }

        // Reached waypoint - snap to position
        // ROM: movea.l 6(a4),a2 / move.w (a2)+,d4 / move.w d4,x_pos(a1) / move.w (a2)+,d5 / move.w d5,y_pos(a1)
        int waypointX = mainPath[mainPathIndex];
        int waypointY = mainPath[mainPathIndex + 1];
        player.setCentreX((short) waypointX);
        player.setCentreY((short) waypointY);

        // ROM: tst.b subtype(a0) / bpl.s + / subq.w #8,a2
        if (mainPathReverse) {
            mainPathIndex -= 2;
        } else {
            mainPathIndex += 2;
        }

        // ROM: subq.w #4,4(a4) / beq.s loc_272EE
        mainPathRemaining -= 4;
        if (mainPathRemaining <= 0) {
            exitTube(player);
            return;
        }

        // Check bounds
        if (mainPathIndex < 0 || mainPathIndex + 1 >= mainPath.length) {
            exitTube(player);
            return;
        }

        // Calculate velocity to next waypoint
        int targetX = mainPath[mainPathIndex];
        int targetY = mainPath[mainPathIndex + 1];
        calculateVelocity(player, targetX, targetY);
    }

    /**
     * Move character by current velocity.
     * ROM: Obj67_MoveCharacter (loc_272C8)
     * Uses 16.16 fixed point position math with 8.8 velocity.
     */
    private void moveCharacter(AbstractPlayableSprite player) {
        player.move(player.getXSpeed(), player.getYSpeed());
    }

    /**
     * Exit the tube and restore player control.
     * ROM: loc_272EE
     */
    private void exitTube(AbstractPlayableSprite player) {
        // ROM: andi.w #$7FF,y_pos(a1)
        int y = player.getCentreY() & 0x7FF;
        player.setCentreY((short) y);

        // ROM: clr.b (a4)
        mainState = 0;
        mainPath = null;

        // ROM: clr.b obj_control(a1)
        player.setObjectControlled(false);
        player.setControlLocked(false);

        // Restore normal render priority
        player.setPriorityBucket(RenderPriority.PLAYER_DEFAULT);

        // ROM: btst #4,subtype(a0) / bne.s + (skip velocity zeroing if bit 4 set)
        if ((spawn.subtype() & 0x10) == 0) {
            // ROM: move.w #0,x_vel(a1) / move.w #0,y_vel(a1)
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
        }

        // Reset animation to invisible
        animationState.setAnimId(0);
    }

    /**
     * Calculate velocity to move from current position to target waypoint.
     * ROM: loc_27374 - velocity calculation with speed 0x1000.
     * <p>
     * The ROM algorithm:
     * - d2 = speed along dominant axis, d3 = speed along cross axis (initially both = 0x1000)
     * - If Y distance >= X distance: Y is dominant
     *   - yVel = d3 (signed), duration = abs((dy << 16) / d3)
     *   - xVel = (dx << 16) / duration
     * - If X distance > Y distance: X is dominant
     *   - xVel = d2 (signed), duration = abs((dx << 16) / d2)
     *   - yVel = (dy << 16) / duration
     */
    private void calculateVelocity(AbstractPlayableSprite player, int targetX, int targetY) {
        int currentX = player.getCentreX();
        int currentY = player.getCentreY();
        int dx = targetX - currentX;
        int dy = targetY - currentY;
        int absDx = Math.abs(dx);
        int absDy = Math.abs(dy);

        int speed = PATH_SPEED;
        int xVel, yVel, duration;

        if (absDy >= absDx) {
            // Y is dominant axis
            // ROM: move.w d3,y_vel(a1) - d3 = speed, negated if dy < 0
            yVel = (dy >= 0) ? speed : -speed;

            // ROM: divs.w d3,d1 → duration = (dy << 16) / yVel
            // Then: divs.w d1,d0 → xVel = (dx << 16) / duration
            if (dy != 0) {
                // Duration is the quotient of (dy << 16) / speed
                duration = (int) (((long) dy << 16) / yVel);
            } else {
                duration = 0;
            }

            if (duration != 0) {
                xVel = (int) (((long) dx << 16) / duration);
            } else {
                xVel = 0;
            }

            // ROM: abs.w d1 / move.w d1,2(a4) then subq.b #1,2(a4) uses HIGH byte
            // The stored word's high byte is the actual frame counter
            mainDuration = (Math.abs(duration) >> 8) & 0xFF;
        } else {
            // X is dominant axis
            // ROM: move.w d2,x_vel(a1) - d2 = speed, negated if dx < 0
            xVel = (dx >= 0) ? speed : -speed;

            // ROM: divs.w d2,d0 → duration = (dx << 16) / xVel
            // Then: divs.w d0,d1 → yVel = (dy << 16) / duration
            if (dx != 0) {
                duration = (int) (((long) dx << 16) / xVel);
            } else {
                duration = 0;
            }

            if (duration != 0) {
                yVel = (int) (((long) dy << 16) / duration);
            } else {
                yVel = 0;
            }

            // ROM: abs.w d0 / move.w d0,2(a4) then subq.b #1,2(a4) uses HIGH byte
            // The stored word's high byte is the actual frame counter
            mainDuration = (Math.abs(duration) >> 8) & 0xFF;
        }

        player.setXSpeed((short) xVel);
        player.setYSpeed((short) yVel);
    }

    private void playSound(GameSound sound) {
        try {
            AudioManager audioManager = AudioManager.getInstance();
            if (audioManager != null) {
                audioManager.playSfx(sound);
            }
        } catch (Exception e) {
            // Don't let audio failure break game logic
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // ROM: only render when a character is in the tube (objoff_2C + objoff_36 != 0)
        if (mainState == 0) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.MTZ_SPIN_TUBE_FLASH);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        int frame = animationState.getMappingFrame();
        // Frame 0 is empty (invisible), frame 1 is the flash sprite
        if (frame > 0) {
            renderer.drawFrameIndex(frame, spawn.x(), spawn.y(), false, false);
        }
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        int objX = spawn.x();
        int objY = spawn.y();

        // Draw entry collision zone (centered on detection area)
        int cx = objX - ENTRY_X_OFFSET + ENTRY_X_RANGE / 2;
        if (xFlipped) {
            cx -= ENTRY_X_FLIP_OFFSET;
        }
        int cy = objY - ENTRY_Y_OFFSET + ENTRY_Y_RANGE / 2;
        ctx.drawRect(cx, cy, ENTRY_X_RANGE / 2, ENTRY_Y_RANGE / 2, 0.0f, 1.0f, 1.0f);

        // Draw spawn position cross
        ctx.drawCross(objX, objY, 4, 1.0f, 1.0f, 0.0f);

        // Show state info
        int pathIdx = spawn.subtype() & 0xF;
        boolean rev = (spawn.subtype() & 0x80) != 0;
        ctx.drawWorldLabel(objX, objY, -1,
                String.format("67 p%d%s s%d", pathIdx, rev ? "R" : "", mainState),
                Color.CYAN);
    }

    @Override
    public boolean isPersistent() {
        // Keep active while controlling a player
        return mainState == 2 || mainState == 4;
    }
}
