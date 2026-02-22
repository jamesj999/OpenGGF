package com.openggf.game.sonic1.objects;

import com.openggf.game.GameServices;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 87 - Sonic on ending sequence.
 * <p>
 * Replaces the player sprite during the ending to play the celebration
 * cutscene. Manages the full sequence: emerald display (if all collected),
 * white flash, STH logo spawn, and leaping animation.
 * <p>
 * State machine via {@code routine} (ob2ndRout):
 * <pre>
 *   0x00: ESon_Main       - check emerald count, branch
 *   0x02: ESon_MakeEmeralds - wait 80 frames then spawn emeralds
 *   0x04: Obj87_Animate   - hold animation (anim 0: frames 1,0,...,2)
 *   0x06: Obj87_LookUp    - wait for emerald radius to reach max
 *   0x08: Obj87_ClrObjRam - flash, clear emeralds, wait 90 frames
 *   0x0A: Obj87_Animate   - confused animation (anim 1)
 *   0x0C: Obj87_MakeLogo  - wait 60 frames, spawn STH text
 *   0x0E: Obj87_Animate   - looking up (anim 2)
 *   0x10: Obj87_Leap      - no-emeralds path: wait 216 frames
 *   0x12: Obj87_Animate   - leaping animation (anim 2)
 * </pre>
 * Reference: docs/s1disasm/_incObj/87 Ending Sequence Sonic.asm
 */
public class Sonic1EndingSonicObjectInstance extends AbstractObjectInstance {
    private static final Logger LOGGER = Logger.getLogger(Sonic1EndingSonicObjectInstance.class.getName());

    // ========================================================================
    // ROM Constants
    // ========================================================================

    /** Render priority: 2 (from ESon_Main2: move.b #2,obPriority). */
    private static final int PRIORITY = 2;

    /** Timer values from disassembly. */
    private static final int TIMER_HOLD = 80;       // ESon_Main2: wait before spawning emeralds
    private static final int TIMER_FLASH_1 = 90;    // Obj87_LookUp: wait after emerald max radius
    private static final int TIMER_CLEAR = 60;      // Obj87_ClrObjRam: wait after clearing emeralds
    private static final int TIMER_LOGO = 180;      // Obj87_MakeLogo: wait after spawning logo
    private static final int TIMER_NO_EMERALDS = 216; // ESon_Main: skip to leap

    /** Animation script data from docs/s1disasm/_anim/Ending Sequence Sonic.asm. */
    // Anim 0: dc.b 3, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 2, $FA
    private static final int[] ANIM_0_FRAMES = {1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 2};
    private static final int ANIM_0_DELAY = 4; // delay value 3 = show each frame for 4 ticks
    // Anim 1: dc.b 5, 3, 4, 3, 4, 3, 4, 3, $FA
    private static final int[] ANIM_1_FRAMES = {3, 4, 3, 4, 3, 4, 3};
    private static final int ANIM_1_DELAY = 6;
    // Anim 2: dc.b 3, 5, 5, 5, 6, 7, $FE, 1
    // $FE,1 = loop back 1 entry (AnimateSprite.Anim_End_FE) -> repeats frame 7 indefinitely
    private static final int[] ANIM_2_FRAMES = {5, 5, 5, 6, 7};
    private static final int ANIM_2_DELAY = 4;

    /**
     * AnimateSprite end command codes (from sub AnimateSprite.asm):
     *   $FA = advance ob2ndRout by 2 (state machine transition)
     *   $FE,N = loop back N entries (repeat)
     */
    private static final int ANIM_CMD_FA = 0xFA;
    private static final int ANIM_CMD_FE = 0xFE;
    private static final int[] ANIM_END_CMD = {ANIM_CMD_FA, ANIM_CMD_FA, ANIM_CMD_FE};
    private static final int[] ANIM_END_PARAM = {0, 0, 1};

    /** Required emerald count for the full sequence. */
    private static final int EMERALD_COUNT_FULL = 6;

    // ========================================================================
    // State
    // ========================================================================

    private final PatternSpriteRenderer renderer;
    private int currentX;
    private int currentY;
    private int routine;
    private int timer;
    private int currentFrame;
    private int animId = -1;
    private int animFrameIndex;
    private int animTimer;
    private boolean emeraldsSpawned;
    private boolean sthSpawned;
    private boolean emeraldsCleared;

    /** Reference to the spawned emerald master for radius checking. */
    private Sonic1EndingEmeraldsObjectInstance emeraldMaster;

    public Sonic1EndingSonicObjectInstance(int x, int y) {
        super(null, "EndSonic");
        this.currentX = x;
        this.currentY = y;
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        this.renderer = renderManager != null ? renderManager.getRenderer(ObjectArtKeys.END_SONIC) : null;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        switch (routine) {
            case 0x00 -> updateMain();
            case 0x02 -> updateMakeEmeralds();
            case 0x04, 0x0A, 0x0E, 0x12 -> updateAnimate();
            case 0x06 -> updateLookUp();
            case 0x08 -> updateClrObjRam();
            case 0x0C -> updateMakeLogo();
            case 0x10 -> updateLeap();
            default -> { }
        }
    }

    // ========================================================================
    // Routine handlers
    // ========================================================================

    /** Routine 0x00: Check emerald count and branch. */
    private void updateMain() {
        int emeraldCount = GameServices.gameState().getEmeraldCount();
        if (emeraldCount >= EMERALD_COUNT_FULL) {
            // Full emerald sequence
            routine = 0x02;
            currentFrame = 0;
            timer = TIMER_HOLD;
            // Fall through to ESon_MakeEmeralds on same frame (ROM parity)
            updateMakeEmeralds();
        } else {
            // Skip to leap sequence
            routine = 0x10;
            timer = TIMER_NO_EMERALDS;
        }
    }

    /** Routine 0x02: Wait then start animation 0 and spawn emerald master. */
    private void updateMakeEmeralds() {
        timer--;
        if (timer > 0) {
            return;
        }
        routine = 0x04;
        // ROM: move.w #1,obAnim → word write sets obAnim=0 (high byte), forces reset
        setAnimation(0);
        // ROM: move.b #id_EndChaos,(v_endemeralds).w — spawns emerald master
        // Emeralds are actually created when animation reaches frame 2 (ECha_Main gate)
    }

    /** Routine 0x06: Wait for emeralds to reach max expansion radius. */
    private void updateLookUp() {
        if (emeraldMaster != null && emeraldMaster.hasReachedMaxRadius()) {
            // ROM: move.w #1,(f_restart).w — triggers white flash
            triggerFlash();
            timer = TIMER_FLASH_1;
            routine = 0x08;
        }
    }

    /** Routine 0x08: Clear emerald object RAM after flash, advance to 0x0A. */
    private void updateClrObjRam() {
        timer--;
        if (timer > 0) {
            return;
        }
        // Clear all emerald objects
        if (!emeraldsCleared) {
            clearEmeralds();
            emeraldsCleared = true;
        }
        // ROM: move.w #1,(f_restart).w — second flash/palette reload
        triggerFlash();
        routine = 0x0A;
        // ROM: move.b #1,obAnim(a0) — byte write sets anim to 1 (confused)
        setAnimation(1);
        // ROM: move.w #60,eson_time(a0) — timer used later by routine 0x0C (Obj87_MakeLogo)
        // Timer sits idle during routine 0x0A; $FA in anim 1 advances to 0x0C naturally
        timer = TIMER_CLEAR;
    }

    /** Routine 0x0C: Wait then spawn STH text and start leaping animation. */
    private void updateMakeLogo() {
        timer--;
        if (timer > 0) {
            // ROM: ESon_Wait3: rts — no animation during countdown
            return;
        }
        routine = 0x0E;
        timer = TIMER_LOGO;
        // ROM: move.b #2,obAnim(a0)
        setAnimation(2);
        // ROM: move.b #id_EndSTH,(v_endlogo).w
        spawnSTH();
    }

    /** Routine 0x10: No-emeralds path — wait then leap. */
    private void updateLeap() {
        timer--;
        if (timer > 0) {
            return;
        }
        routine = 0x12;
        currentFrame = 5;
        setAnimation(2);
        spawnSTH();
    }

    /** Routines 0x04, 0x0A, 0x0E, 0x12: Advance animation (Obj87_Animate). */
    private void updateAnimate() {
        animTimer--;
        if (animTimer <= 0) {
            advanceAnimFrame();
        }
        // ROM: ECha_Main gate — emeralds spawn when Sonic's frame reaches 2
        if (routine == 0x04 && currentFrame == 2 && !emeraldsSpawned) {
            spawnEmeralds();
        }
    }

    // ========================================================================
    // Animation
    // ========================================================================

    private void setAnimation(int id) {
        if (id == animId) {
            return;
        }
        animId = id;
        animFrameIndex = 0;
        int[] frames = getAnimFrames(id);
        int delay = getAnimDelay(id);
        if (frames.length > 0) {
            currentFrame = frames[0];
        }
        animTimer = delay;
    }

    private void advanceAnimFrame() {
        int[] frames = getAnimFrames(animId);
        int delay = getAnimDelay(animId);
        animFrameIndex++;
        if (animFrameIndex >= frames.length) {
            int cmd = ANIM_END_CMD[animId];
            if (cmd == ANIM_CMD_FA) {
                // $FA: advance ob2ndRout by 2 (AnimateSprite.Anim_End_FA)
                routine += 2;
                return;
            } else if (cmd == ANIM_CMD_FE) {
                // $FE,N: loop back N entries (AnimateSprite.Anim_End_FE)
                int loopBack = ANIM_END_PARAM[animId];
                animFrameIndex -= loopBack;
            } else {
                // $FF: loop from start
                animFrameIndex = 0;
            }
        }
        currentFrame = frames[animFrameIndex];
        animTimer = delay;
    }

    private int[] getAnimFrames(int id) {
        return switch (id) {
            case 0 -> ANIM_0_FRAMES;
            case 1 -> ANIM_1_FRAMES;
            case 2 -> ANIM_2_FRAMES;
            default -> ANIM_0_FRAMES;
        };
    }

    private int getAnimDelay(int id) {
        return switch (id) {
            case 0 -> ANIM_0_DELAY;
            case 1 -> ANIM_1_DELAY;
            case 2 -> ANIM_2_DELAY;
            default -> ANIM_0_DELAY;
        };
    }

    // ========================================================================
    // Sub-object spawning
    // ========================================================================

    private void spawnEmeralds() {
        if (emeraldsSpawned) {
            return;
        }
        emeraldsSpawned = true;

        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager == null) {
            return;
        }

        // ROM: ECha_CreateEms spawns 6 emeralds at player position
        // with angle offsets spaced by $100/6 = $2A
        int angleStep = 0x100 / 6; // $2A
        for (int i = 0; i < 6; i++) {
            int angleOffset = (angleStep * i) & 0xFF;
            int emeraldFrame = i + 1; // frames 1-6
            Sonic1EndingEmeraldsObjectInstance emerald =
                    new Sonic1EndingEmeraldsObjectInstance(currentX, currentY, angleOffset, emeraldFrame);
            objectManager.addDynamicObject(emerald);
            if (i == 0) {
                emeraldMaster = emerald;
            }
        }
    }

    private void clearEmeralds() {
        // Signal all emerald instances to remove themselves
        if (emeraldMaster != null) {
            emeraldMaster = null;
        }
        // Emeralds are cleared via ObjectManager — they check a flag or we mark them destroyed
        // The ROM clears object RAM directly. We use the destroy mechanism.
        Sonic1EndingEmeraldsObjectInstance.destroyAllEmeralds();
    }

    private void spawnSTH() {
        if (sthSpawned) {
            return;
        }
        sthSpawned = true;

        Sonic1EndingSTHObjectInstance sth = new Sonic1EndingSTHObjectInstance();
        spawnDynamicObject(sth);
    }

    private void triggerFlash() {
        try {
            var fadeManager = FadeManager.getInstance();
            if (!fadeManager.isActive()) {
                fadeManager.startFadeToWhite(() ->
                        fadeManager.startFadeFromWhite(null));
            }
        } catch (Exception e) {
            LOGGER.fine("Flash not available: " + e.getMessage());
        }
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    @Override
    public int getPriorityBucket() {
        return PRIORITY;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(currentFrame, currentX, currentY, false, false);
    }

    /** Returns current mapping frame index (used by emerald spawn trigger). */
    public int getCurrentFrame() {
        return currentFrame;
    }
}
