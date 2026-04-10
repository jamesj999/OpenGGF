package com.openggf.game.sonic1.specialstage;

import com.openggf.audio.GameSound;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.Sonic1PlayerArt;
import com.openggf.game.sonic1.Sonic1RingArt;
import com.openggf.game.sonic1.audio.Sonic1Music;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.game.sonic1.constants.Sonic1AnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.io.IOException;
import java.util.logging.Logger;

import static com.openggf.game.sonic1.constants.Sonic1Constants.*;
import static com.openggf.sprites.playable.AbstractPlayableSprite.*;

/**
 * Sonic 1 Special Stage runtime - rotating maze gameplay.
 *
 * Physics and collision logic faithfully translated from
 * "_incObj/09 Sonic in Special Stage.asm" (Obj09).
 *
 * <p>Coordinate system: 16.16 fixed-point for position (top 16 bits = pixels),
 * 16-bit for velocity and inertia. ssAngle is 16-bit where the top byte
 * is used as a 256-step hex angle for trig lookups.
 */
public final class Sonic1SpecialStageManager {
    private static final Logger LOGGER = Logger.getLogger(Sonic1SpecialStageManager.class.getName());

    // Pattern atlas base for SS art (above normal level art range)
    private static final int SS_PATTERN_BASE = 0x10000;
    private static final int SS_ROLL_SPEED_SWITCH = 0x600;
    private static final int DEBUG_MOVE_SPEED = 3;

    // Collection animation buffer (matches v_ssitembuffer from SS_AniRingSparks)
    private static final int SS_ANIM_BUFFER_SIZE = 8;
    private static final int ANIM_NONE = 0;
    private static final int ANIM_RING_SPARKLE = 1;
    private static final int ANIM_GLASS_BLOCK = 6;
    private static final int[] ANIM_RING_SPARKLE_DATA = {0x42, 0x43, 0x44, 0x45, 0};
    private static final int ANIM_RING_PERIOD = 6; // 5+1 frames per step (ROM: move.b #5,2(a0))
    private static final int[] ANIM_GLASS_DATA = {0x4B, 0x4C, 0x4D, 0x4E, 0x4B, 0x4C, 0x4D, 0x4E, 0};
    private static final int ANIM_GLASS_PERIOD = 2; // 1+1 frames per step (ROM: move.b #1,2(a0))

    // byte_4A3C: SS BG state table (32 entries, 4 fields each)
    // {time, anim, bgPlaneSelect (0=Plane_6, 1=Plane_5), palette-cycle selector byte}
    // Decoded from SSBGData macro in sonic.asm.
    private static final int[][] SS_BG_STATE_TABLE = {
            {3, 0, 0, 0x92}, {3, 0, 0, 0x90}, {3, 0, 0, 0x8E}, {3, 0, 0, 0x8C}, {3, 0, 0, 0x8B},
            {3, 0, 0, 0x80}, {3, 0, 0, 0x82}, {3, 0, 0, 0x84}, {3, 0, 0, 0x86}, {3, 0, 0, 0x88},
            {7, 8, 0, 0x00}, {7, 10, 0, 0x0C}, {-1, 12, 0, 0x18}, {-1, 12, 0, 0x18}, {7, 10, 0, 0x0C}, {7, 8, 0, 0x00},
            {3, 0, 1, 0x88}, {3, 0, 1, 0x86}, {3, 0, 1, 0x84}, {3, 0, 1, 0x82}, {3, 0, 1, 0x81},
            {3, 0, 1, 0x8A}, {3, 0, 1, 0x8C}, {3, 0, 1, 0x8E}, {3, 0, 1, 0x90}, {3, 0, 1, 0x92},
            {7, 2, 1, 0x24}, {7, 4, 1, 0x30}, {-1, 6, 1, 0x3C}, {-1, 6, 1, 0x3C}, {7, 4, 1, 0x30}, {7, 2, 1, 0x24}
    };

    private boolean initialized;
    private boolean finished;
    private boolean emeraldCollected;
    private boolean debugMode;
    private int currentStage;
    private int ringsCollected;

    // Layout
    private byte[] layout;

    // Rotation
    private int ssAngle;       // 16-bit rotation angle (top byte = hex angle)
    private int ssRotate;      // 16-bit rotation speed
    private int debugSavedAngle;
    private int debugSavedRotate;

    // Player position (16.16 fixed-point)
    private long sonicPosX;    // 32-bit: top 16 = pixel X
    private long sonicPosY;    // 32-bit: top 16 = pixel Y
    private int sonicVelX;     // 16-bit velocity X
    private int sonicVelY;     // 16-bit velocity Y
    private int sonicInertia;  // 16-bit inertia (speed along ground)
    private boolean sonicAirborne;  // obStatus bit 1
    private boolean sonicFacingLeft; // obStatus bit 0

    // Camera
    private int cameraX;
    private int cameraY;

    // Item interaction state
    private int ghostState;         // 0=none, 1=passed ghost, 2=solidify ghosts
    private int upDownCooldown;     // UP/DOWN block cooldown timer
    private int reverseCooldown;    // R block cooldown timer
    private int lastCollisionBlockId; // objoff_30 equivalent

    // Collision scratch: address of colliding block (for item interaction)
    private int lastCollisionRow;
    private int lastCollisionCol;

    // Animation
    private int wallRotFrame;       // 0-15, computed from ssAngle
    private int ringAnimFrame;      // 0-7, cycled via timer
    private int ringAnimTimer;
    private int wallVramAnimFrame;  // ani0: 8-frame decrementing cycle
    private int wallVramAnimTimer;
    private int[][] ssAnimBuffer;   // [slot][4: type, timer, frameIndex, layoutIndex]
    private int[] ssAnimGlassFinalBlock; // Final post-animation state for glass hits (Obj09_GlassUpdate)
    private int sonicAnimId;
    private int sonicAnimFrameIndex;
    private int sonicAnimFrameTimer;
    private int palSsTime;
    private int palSsNum;
    private int palSsIndex;
    private int ani2Frame;   // 0-1, period 8 (GOAL/UP/DOWN/emerald animation)
    private int ani2Timer;
    private int ani3Frame;   // 0-3, period 5 (glass block animation)
    private int ani3Timer;

    // Exit sequence
    private boolean exitTriggered;
    private int exitPhase;
    private int exitTimer;
    private boolean exitFadeStarted;
    private int exitFadeTimer;

    // Input state (set by handleInput, consumed by update)
    private int heldButtons;
    private int pressedButtons;

    // BG animation state (SS_BGAnimate from sonic.asm)
    private int bgAnimState;           // v_ssbganim (0,2,4,6,8,10,12)
    private byte[][] fgPlaneTilemaps;  // SS FG namespaces plane1..4 (64x64 each)
    private byte[] bgPlane5Tilemap;    // SS BG namespace plane5 (64x64)
    private byte[] bgPlane6Tilemap;    // SS BG namespace plane6 (64x64)
    private boolean bgUsingPlane6;     // true=Plane_6 active, false=Plane_5 active
    private int fgAnimPlaneIndex;      // 0..3 => FG plane 1..4
    private int fgYScroll;             // v_scrposy_vdp from byte_4ABC (0 or 0x100)
    private int bgYScroll;             // v_bgscreenposy (vertical scroll offset, wraps at 256)
    private int bgExtraScrollX;        // v_bg3screenposx (FG uniform scroll component)
    private int[] bgSineBuffer;        // v_ngfx_buffer: 10 entries × 2 words [scroll, phase]
    private int[] bgBandBuffer;        // v_ssscroll_buffer: 7 entries × 2 words [pos_hi, pos_lo]
    private int[] bgHScrollData;       // 224-entry per-scanline H-scroll output

    // Subsystems
    private Sonic1SpecialStageDataLoader dataLoader;
    private Sonic1SpecialStageRenderer renderer;
    private GraphicsManager graphicsManager;
    private PlayerSpriteRenderer sonicSpriteRenderer;
    private int sonicSpriteFrame;
    private SpriteAnimationScript sonicRollScript;
    private SpriteAnimationScript sonicRoll2Script;
    private Sonic1SpecialStageBackgroundRenderer bgRenderer;
    private Sonic1SpecialStageBackgroundRenderer fgRenderer;
    private int bgCloudBase;
    private int bgFishBase;
    private Palette[] ssPalettes;
    private byte[] ssPaletteCycle1;
    private byte[] ssPaletteCycle2;

    public void initialize(int stageIndex) throws IOException {
        this.currentStage = Math.max(0, Math.min(stageIndex, SS_STAGE_COUNT - 1));
        this.ringsCollected = 0;
        this.emeraldCollected = false;
        this.finished = false;
        this.debugMode = false;

        // Initialize subsystems
        Rom rom = GameServices.rom().getRom();
        this.graphicsManager = com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().graphics();
        this.graphicsManager.setUseWaterShader(false);
        this.graphicsManager.setUseSpritePriorityShader(false);
        this.graphicsManager.setCurrentSpriteHighPriority(false);
        this.graphicsManager.setWaterEnabled(false);
        // Special stages always use their own palette; avoid stale underwater tint state.
        this.graphicsManager.setUseUnderwaterPaletteForBackground(false);
        this.dataLoader = new Sonic1SpecialStageDataLoader(rom);
        this.renderer = new Sonic1SpecialStageRenderer(graphicsManager);

        // Load layout
        layout = dataLoader.getStageLayout(currentStage);

        // Load start position
        int[] startPos = dataLoader.getStartPosition(currentStage);
        sonicPosX = (long) startPos[0] << 16;
        sonicPosY = (long) startPos[1] << 16;

        // Load palette
        loadPalette();

        // Load and cache art patterns
        loadArt();
        loadSonicSprite();

        // Initialize background renderer
        initBgRenderer();

        // Initialize rotation
        ssAngle = 0;
        ssRotate = SS_INIT_ROTATION;
        debugSavedAngle = 0;
        debugSavedRotate = SS_INIT_ROTATION;

        // Initialize physics
        sonicVelX = 0;
        sonicVelY = 0;
        sonicInertia = 0;
        sonicAirborne = true; // Obj09_Main sets obStatus bit 1 before first update
        sonicFacingLeft = false;

        // Initialize camera
        updateCamera();

        // Initialize interaction state
        ghostState = 0;
        upDownCooldown = 0;
        reverseCooldown = 0;
        lastCollisionBlockId = 0;

        // Initialize animation
        wallRotFrame = 0;
        ringAnimFrame = 0;
        ringAnimTimer = 0;
        wallVramAnimFrame = 0;
        wallVramAnimTimer = 0;
        ssAnimBuffer = new int[SS_ANIM_BUFFER_SIZE][4];
        ssAnimGlassFinalBlock = new int[SS_ANIM_BUFFER_SIZE];
        sonicAnimId = Sonic1AnimationIds.ROLL.id();
        sonicAnimFrameIndex = 0;
        sonicAnimFrameTimer = 0;

        // Initialize exit state
        exitTriggered = false;
        exitPhase = 0;
        exitTimer = 0;
        exitFadeStarted = false;
        exitFadeTimer = 0;

        // Initialize palette cycle state (PalCycle_SS)
        palSsTime = 0;
        palSsNum = 0;
        palSsIndex = 0;

        // Initialize GOAL/UP/DOWN/emerald and glass animation counters (SS_AniWallsRings ani2, ani3)
        ani2Frame = 0;
        ani2Timer = 0;
        ani3Frame = 0;
        ani3Timer = 0;

        // Clear input
        heldButtons = 0;
        pressedButtons = 0;

        this.initialized = true;
        LOGGER.info("Special Stage " + (currentStage + 1) + " initialized");
    }

    public void update() {
        if (!initialized || finished) {
            return;
        }

        if (exitTriggered) {
            updateExit();
        } else if (debugMode) {
            processDebugMove();
            updateCamera();
            ssAngle = (ssAngle + ssRotate) & 0xFFFF;
            updateAnimCounters();
            updateBgAnimate();
        } else {
            // Process input
            lastCollisionBlockId = 0;

            // On ground: check jump, then move, then fall
            // In air: move, then fall
            if (!sonicAirborne) {
                processJump();
            }
            processMove();
            processFall();

            // Check items at Sonic's position
            checkItems();
            processItemInteraction();

            // Apply velocity (SpeedToPos)
            sonicPosX += (long) sonicVelX << 8;
            sonicPosY += (long) sonicVelY << 8;

            // Update camera
            updateCamera();

            // Rotate stage
            ssAngle = (ssAngle + ssRotate) & 0xFFFF;

            // Update animation counters
            updateAnimCounters();

            // Update background animation (sine wave / band scroll)
            updateBgAnimate();
        }

        // Clear pressed buttons (held persist until next handleInput call)
        pressedButtons = 0;
    }

    private void processDebugMove() {
        if ((heldButtons & INPUT_LEFT) != 0) {
            sonicPosX -= (long) DEBUG_MOVE_SPEED << 16;
            sonicFacingLeft = true;
        }
        if ((heldButtons & INPUT_RIGHT) != 0) {
            sonicPosX += (long) DEBUG_MOVE_SPEED << 16;
            sonicFacingLeft = false;
        }
        if ((heldButtons & INPUT_UP) != 0) {
            sonicPosY -= (long) DEBUG_MOVE_SPEED << 16;
        }
        if ((heldButtons & INPUT_DOWN) != 0) {
            sonicPosY += (long) DEBUG_MOVE_SPEED << 16;
        }

        // Keep movement deterministic when leaving debug mode.
        sonicVelX = 0;
        sonicVelY = 0;
        sonicInertia = 0;
        sonicAirborne = true;
        lastCollisionBlockId = 0;
    }

    // ---- Physics (from Obj09) ----

    /**
     * Obj09_Jump: check for jump button press while on ground.
     */
    private void processJump() {
        if ((pressedButtons & INPUT_JUMP) == 0) {
            return;
        }

        // angle = -(ssAngle>>8 & 0xFC) - 0x40
        int angle = (-(ssAngle >> 8) & 0xFC) - 0x40;
        int sinVal = TrigLookupTable.sinHex(angle & 0xFF);
        int cosVal = TrigLookupTable.cosHex(angle & 0xFF);

        // velX = cos * 0x680 >> 8, velY = sin * 0x680 >> 8
        sonicVelX = (short) ((cosVal * SS_JUMP_FORCE) >> 8);
        sonicVelY = (short) ((sinVal * SS_JUMP_FORCE) >> 8);
        sonicAirborne = true;
        playSfx(Sonic1Sfx.JUMP);
    }

    /**
     * Obj09_Move: horizontal movement along the maze surface.
     */
    private void processMove() {
        boolean leftHeld = (heldButtons & INPUT_LEFT) != 0;
        boolean rightHeld = (heldButtons & INPUT_RIGHT) != 0;

        if (leftHeld) {
            moveLeft();
        }
        if (rightHeld) {
            moveRight();
        }

        // Decelerate when no input
        if (!leftHeld && !rightHeld) {
            if (sonicInertia != 0) {
                if (sonicInertia > 0) {
                    sonicInertia -= SS_ACCEL;
                    if (sonicInertia < 0) sonicInertia = 0;
                } else {
                    sonicInertia += SS_ACCEL;
                    if (sonicInertia > 0) sonicInertia = 0;
                }
            }
        }

        // Convert inertia to world movement
        // angle = (-(ssAngle>>8) + 0x20) & 0xC0
        int angle = ((-(ssAngle >> 8)) + 0x20) & 0xC0;
        int sinVal = TrigLookupTable.sinHex(angle & 0xFF);
        int cosVal = TrigLookupTable.cosHex(angle & 0xFF);

        long dx = (long) cosVal * sonicInertia;
        long dy = (long) sinVal * sonicInertia;

        // Save position for collision revert
        long savedX = sonicPosX;
        long savedY = sonicPosY;

        sonicPosX += dx;
        sonicPosY += dy;

        // Collision check
        if (checkCollision()) {
            sonicPosX = savedX;
            sonicPosY = savedY;
            sonicInertia = 0;
        }
    }

    private void moveLeft() {
        sonicFacingLeft = true;
        if (sonicInertia > 0) {
            // Braking
            sonicInertia -= SS_BRAKE;
            if (sonicInertia < 0) {
                // nop in original - allows crossing zero
            }
        } else {
            // Accelerate left
            sonicInertia -= SS_ACCEL;
            if (sonicInertia < -SS_MAX_SPEED) {
                sonicInertia = -SS_MAX_SPEED;
            }
        }
    }

    private void moveRight() {
        sonicFacingLeft = false;
        if (sonicInertia < 0) {
            // Braking
            sonicInertia += SS_BRAKE;
            if (sonicInertia > 0) {
                // nop in original - allows crossing zero
            }
        } else {
            // Accelerate right
            sonicInertia += SS_ACCEL;
            if (sonicInertia > SS_MAX_SPEED) {
                sonicInertia = SS_MAX_SPEED;
            }
        }
    }

    /**
     * Obj09_Fall: gravity and velocity-based movement with collision.
     *
     * This is the core physics routine. It applies gravity along the rotated
     * axis, then tests X and Y movement independently for collision.
     */
    private void processFall() {
        long savedX = sonicPosX;
        long savedY = sonicPosY;

        // CalcSine with byte angle from ssAngle (masked to 0xFC)
        int byteAngle = (ssAngle >> 8) & 0xFC;
        int sinVal = TrigLookupTable.sinHex(byteAngle);
        int cosVal = TrigLookupTable.cosHex(byteAngle);

        // Apply gravity to velocity (shifted by 8)
        long velXShifted = (long) sonicVelX << 8;
        long velYShifted = (long) sonicVelY << 8;

        // d0 = sin * 0x2A + velX<<8, d1 = cos * 0x2A + velY<<8
        long d0 = (long) sinVal * SS_GRAVITY + velXShifted;
        long d1 = (long) cosVal * SS_GRAVITY + velYShifted;

        // Try X movement first using temporary probe positions.
        long probeX = savedX + d0;
        long probeY = savedY;

        if (checkCollisionAt(probeX, probeY)) {
            // X blocked: clear X velocity, clear airborne
            d0 = 0;
            sonicVelX = 0;
            sonicAirborne = false;

            // Now try Y
            probeY = savedY + d1;
            if (checkCollisionAt(savedX, probeY)) {
                // Y also blocked: clear Y velocity
                d1 = 0;
                sonicVelY = 0;
            }
            sonicVelX = (short) (d0 >> 8);
            sonicVelY = (short) (d1 >> 8);
        } else {
            // X succeeded: try Y
            probeY = savedY + d1;
            if (checkCollisionAt(probeX, probeY)) {
                // Y blocked: clear Y velocity, clear airborne
                d1 = 0;
                sonicVelY = 0;
                sonicAirborne = false;
                sonicVelX = (short) (d0 >> 8);
                sonicVelY = (short) (d1 >> 8);
            } else {
                // Both succeeded: airborne, extract velocities
                sonicVelX = (short) (d0 >> 8);
                sonicVelY = (short) (d1 >> 8);
                sonicAirborne = true;
            }
        }
    }

    // ---- Collision (from sub_1BCE8) ----

    /**
     * Checks if Sonic's current position collides with solid blocks.
     * Tests a 2x2 grid of cells around Sonic's position.
     *
     * From sub_1BCE8: the position is offset by (+0x44, +0x14) before
     * dividing by block size to get grid coordinates, then checks
     * [row,col], [row,col+1], [row+1,col], [row+1,col+1].
     */
    private boolean checkCollision() {
        return checkCollisionAt(sonicPosX, sonicPosY);
    }

    private boolean checkCollisionAt(long posXFixed, long posYFixed) {
        int posX = (int) (posXFixed >> 16);
        int posY = (int) (posYFixed >> 16);

        int gridCol = (posX + 0x14) / SS_BLOCK_SIZE_PX;
        int gridRow = (posY + 0x44) / SS_BLOCK_SIZE_PX;

        // Check 2x2 cells
        for (int dr = 0; dr < 2; dr++) {
            for (int dc = 0; dc < 2; dc++) {
                int r = gridRow + dr;
                int c = gridCol + dc;
                int bufIndex = r * SS_LAYOUT_STRIDE + c;
                if (bufIndex < 0 || bufIndex >= layout.length) continue;

                int blockId = layout[bufIndex] & 0xFF;
                if (Sonic1SpecialStageBlockType.isSolid(blockId)) {
                    lastCollisionBlockId = blockId;
                    lastCollisionRow = r;
                    lastCollisionCol = c;
                    return true;
                }
            }
        }
        return false;
    }

    // ---- Item Checks (from Obj09_ChkItems / Obj09_ChkItems2) ----

    /**
     * Obj09_ChkItems: checks for collectible items at Sonic's feet.
     * Item check uses a different offset than collision: (+0x50, +0x20).
     */
    private void checkItems() {
        int posX = (int) (sonicPosX >> 16);
        int posY = (int) (sonicPosY >> 16);

        int gridCol = (posX + 0x20) / SS_BLOCK_SIZE_PX;
        int gridRow = (posY + 0x50) / SS_BLOCK_SIZE_PX;
        int bufIndex = gridRow * SS_LAYOUT_STRIDE + gridCol;

        if (bufIndex < 0 || bufIndex >= layout.length) return;
        int blockId = layout[bufIndex] & 0xFF;

        if (blockId == 0) {
            // Empty cell - check ghost solidification
            if (ghostState == 2) {
                makeGhostsSolid();
                ghostState = 0;
            }
            return;
        }

        // Ring (0x3A)
        if (blockId == 0x3A) {
            ringsCollected++;
            playSfx(GameSound.RING);
            startItemAnimation(bufIndex);
            return;
        }

        // 1UP (0x28)
        if (blockId == 0x28) {
            layout[bufIndex] = 0;
            playMusic(Sonic1Music.EXTRA_LIFE);
            return;
        }

        // Emerald (0x3B-0x40)
        if (blockId >= 0x3B && blockId <= 0x40) {
            layout[bufIndex] = 0;
            emeraldCollected = true;
            exitTriggered = true;
            exitPhase = 0;
            exitTimer = 0;
            playMusic(Sonic1Music.CHAOS_EMERALD);
            return;
        }

        // Ghost block (0x41) - mark as passed
        if (blockId == 0x41) {
            ghostState = 1;
            return;
        }

        // Ghost switch (0x4A) - trigger solidification if passed
        if (blockId == 0x4A) {
            if (ghostState == 1) {
                ghostState = 2;
            }
            return;
        }
    }

    /**
     * Obj09_ChkItems2: processes collision-based interactions (bumper, GOAL, UP/DOWN, R, glass).
     * These use lastCollisionBlockId set during collision detection.
     */
    private void processItemInteraction() {
        int blockId = lastCollisionBlockId;
        if (blockId == 0) {
            // Decrement cooldowns
            if (upDownCooldown > 0) upDownCooldown--;
            if (reverseCooldown > 0) reverseCooldown--;
            return;
        }

        // Bumper (0x25)
        if (blockId == 0x25) {
            processBumper();
            return;
        }

        // GOAL (0x27)
        if (blockId == 0x27) {
            exitTriggered = true;
            playSfx(Sonic1Sfx.SS_GOAL);
            return;
        }

        // UP block (0x29)
        if (blockId == 0x29) {
            if (upDownCooldown == 0) {
                upDownCooldown = SS_UP_DOWN_COOLDOWN;
                // If rotation is slow (bit 6 of low byte set), double speed
                if ((ssRotate & 0x40) != 0) {
                    ssRotate <<= 1;
                }
                // Change block to DOWN
                int idx = lastCollisionRow * SS_LAYOUT_STRIDE + lastCollisionCol;
                if (idx >= 0 && idx < layout.length) {
                    layout[idx] = 0x2A;
                }
                playSfx(Sonic1Sfx.SS_ITEM);
            }
            return;
        }

        // DOWN block (0x2A)
        if (blockId == 0x2A) {
            if (upDownCooldown == 0) {
                upDownCooldown = SS_UP_DOWN_COOLDOWN;
                // If rotation is fast (bit 6 not set), halve speed
                if ((ssRotate & 0x40) == 0) {
                    ssRotate >>= 1;
                }
                // Change block to UP
                int idx = lastCollisionRow * SS_LAYOUT_STRIDE + lastCollisionCol;
                if (idx >= 0 && idx < layout.length) {
                    layout[idx] = 0x29;
                }
                playSfx(Sonic1Sfx.SS_ITEM);
            }
            return;
        }

        // R block (0x2B)
        if (blockId == 0x2B) {
            if (reverseCooldown == 0) {
                reverseCooldown = SS_UP_DOWN_COOLDOWN;
                ssRotate = -ssRotate; // Reverse rotation
                playSfx(Sonic1Sfx.SS_ITEM);
            }
            return;
        }

        // Glass blocks (0x2D-0x30)
        if (blockId >= 0x2D && blockId <= 0x30) {
            int idx = lastCollisionRow * SS_LAYOUT_STRIDE + lastCollisionCol;
            if (idx >= 0 && idx < layout.length) {
                int nextState = blockId + 1;
                if (nextState > 0x30) {
                    nextState = 0; // Glass destroyed
                }
                startGlassAnimation(idx, nextState);
            }
            playSfx(Sonic1Sfx.SS_GLASS);
        }
    }

    /**
     * Processes bumper bounce from Obj09_ChkBumper.
     * Calculates angle from bumper center to Sonic, applies outward velocity.
     */
    private void processBumper() {
        // Bumper center: convert grid position back to world coordinates
        int bumperX = lastCollisionCol * SS_BLOCK_SIZE_PX - 0x14;
        int bumperY = lastCollisionRow * SS_BLOCK_SIZE_PX - 0x44;

        int sonicPixelX = (int) (sonicPosX >> 16);
        int sonicPixelY = (int) (sonicPosY >> 16);

        // Calculate direction from bumper to Sonic
        short dx = (short) (bumperX - sonicPixelX);
        short dy = (short) (bumperY - sonicPixelY);

        int angle = TrigLookupTable.calcAngle(dx, dy);
        int sinVal = TrigLookupTable.sinHex(angle);
        int cosVal = TrigLookupTable.cosHex(angle);

        // Apply outward velocity (negative = away from bumper)
        sonicVelX = (short) ((cosVal * -SS_BUMPER_FORCE) >> 8);
        sonicVelY = (short) ((sinVal * -SS_BUMPER_FORCE) >> 8);
        sonicAirborne = true;
        playSfx(Sonic1Sfx.BUMPER);
    }

    /**
     * Replaces all ghost blocks (0x41) with solid blocks (0x2C).
     */
    private void makeGhostsSolid() {
        for (int row = 0; row < SS_BLOCKBUFFER_ROWS; row++) {
            int rowOff = SS_BLOCKBUFFER_OFFSET + row * SS_LAYOUT_STRIDE;
            for (int col = 0; col < SS_LAYOUT_COLS; col++) {
                int idx = rowOff + col;
                if (idx < layout.length && (layout[idx] & 0xFF) == 0x41) {
                    layout[idx] = 0x2C;
                }
            }
        }
    }

    // ---- Item Animation (from SS_AniRingSparks) ----

    private void startItemAnimation(int layoutIndex) {
        if (ssAnimBuffer == null) {
            if (layoutIndex >= 0 && layoutIndex < layout.length) {
                layout[layoutIndex] = 0;
            }
            return;
        }
        for (int i = 0; i < SS_ANIM_BUFFER_SIZE; i++) {
            if (ssAnimBuffer[i][0] == ANIM_NONE) {
                ssAnimBuffer[i][0] = ANIM_RING_SPARKLE;
                ssAnimBuffer[i][1] = ANIM_RING_PERIOD;
                ssAnimBuffer[i][2] = 0;
                ssAnimBuffer[i][3] = layoutIndex;
                // Write first sparkle frame immediately
                if (layoutIndex >= 0 && layoutIndex < layout.length) {
                    layout[layoutIndex] = (byte) ANIM_RING_SPARKLE_DATA[0];
                }
                return;
            }
        }
        // No free slot - just clear the cell
        if (layoutIndex >= 0 && layoutIndex < layout.length) {
            layout[layoutIndex] = 0;
        }
    }

    private void startGlassAnimation(int layoutIndex, int finalBlockId) {
        if (ssAnimBuffer == null || ssAnimGlassFinalBlock == null) {
            if (layoutIndex >= 0 && layoutIndex < layout.length) {
                layout[layoutIndex] = (byte) finalBlockId;
            }
            return;
        }
        for (int i = 0; i < SS_ANIM_BUFFER_SIZE; i++) {
            if (ssAnimBuffer[i][0] == ANIM_NONE) {
                ssAnimBuffer[i][0] = ANIM_GLASS_BLOCK;
                ssAnimBuffer[i][1] = 0; // Trigger first frame immediately on next animation tick
                ssAnimBuffer[i][2] = 0;
                ssAnimBuffer[i][3] = layoutIndex;
                ssAnimGlassFinalBlock[i] = finalBlockId;
                return;
            }
        }
        // ROM behavior when no slot is free: skip the transition this frame.
    }

    private void updateItemAnimations() {
        if (ssAnimBuffer == null) return;
        for (int i = 0; i < SS_ANIM_BUFFER_SIZE; i++) {
            int type = ssAnimBuffer[i][0];
            if (type == ANIM_NONE) continue;
            if (type == ANIM_RING_SPARKLE) {
                updateRingAnimation(i);
                continue;
            }
            if (type == ANIM_GLASS_BLOCK) {
                updateGlassAnimation(i);
                continue;
            }
            ssAnimBuffer[i][0] = ANIM_NONE;
        }
    }

    private void updateRingAnimation(int slot) {
        ssAnimBuffer[slot][1]--;
        if (ssAnimBuffer[slot][1] > 0) return;
        ssAnimBuffer[slot][1] = ANIM_RING_PERIOD;
        ssAnimBuffer[slot][2]++;
        int frameIdx = ssAnimBuffer[slot][2];
        int layoutIndex = ssAnimBuffer[slot][3];

        int nextBlockId = (frameIdx < ANIM_RING_SPARKLE_DATA.length)
                ? ANIM_RING_SPARKLE_DATA[frameIdx] : 0;
        if (nextBlockId == 0) {
            // Animation complete - clear cell and free slot
            if (layoutIndex >= 0 && layoutIndex < layout.length) {
                layout[layoutIndex] = 0;
            }
            ssAnimBuffer[slot][0] = ANIM_NONE;
            return;
        }
        if (layoutIndex >= 0 && layoutIndex < layout.length) {
            layout[layoutIndex] = (byte) nextBlockId;
        }
    }

    private void updateGlassAnimation(int slot) {
        ssAnimBuffer[slot][1]--;
        if (ssAnimBuffer[slot][1] > 0) return;
        ssAnimBuffer[slot][1] = ANIM_GLASS_PERIOD;

        int frameIdx = ssAnimBuffer[slot][2];
        ssAnimBuffer[slot][2] = frameIdx + 1;
        int layoutIndex = ssAnimBuffer[slot][3];
        int nextBlockId = (frameIdx < ANIM_GLASS_DATA.length) ? ANIM_GLASS_DATA[frameIdx] : 0;

        if (nextBlockId != 0) {
            if (layoutIndex >= 0 && layoutIndex < layout.length) {
                layout[layoutIndex] = (byte) nextBlockId;
            }
            return;
        }

        if (layoutIndex >= 0 && layoutIndex < layout.length) {
            layout[layoutIndex] = (byte) ssAnimGlassFinalBlock[slot];
        }
        ssAnimBuffer[slot][0] = ANIM_NONE;
        ssAnimGlassFinalBlock[slot] = 0;
    }

    // ---- Exit Sequence (from Obj09_ExitStage / Obj09_Exit2) ----

    private void updateExit() {
        // Accelerate rotation (Obj09_ExitStage: add $40 to v_ssrotate)
        ssRotate += 0x40;

        // At rotation threshold, start concurrent fade (SS_ChkEnd / SS_Finish)
        // ROM: v_ssrotate == $1800 sets v_gamemode = id_Level, triggering SS_Finish
        // which runs WhiteOut_ToWhite alongside ExecuteObjects for 60 frames.
        if (ssRotate >= 0x1800 && !exitFadeStarted) {
            exitFadeStarted = true;
            exitFadeTimer = 60; // v_generictimer = 60
            GameServices.fade().startFadeToWhite(null, Integer.MAX_VALUE);
        }

        // Count down fade timer (SS_FinLoop: dbf d1,SS_FinLoop)
        if (exitFadeStarted) {
            exitFadeTimer--;
            if (exitFadeTimer <= 0) {
                finished = true;
            }
        }

        // Keep rotating (spin continues even after fade starts)
        ssAngle = (ssAngle + ssRotate) & 0xFFFF;

        // Update camera during exit
        updateCamera();

        // Update animation
        updateAnimCounters();
        updateBgAnimate();
    }

    // ---- Camera (from SS_FixCamera) ----

    private void updateCamera() {
        int sonicPixelX = (int) (sonicPosX >> 16);
        int sonicPixelY = (int) (sonicPosY >> 16);

        // cameraX = sonicX - 0xA0 (but not if negative)
        int newCamX = sonicPixelX - 0xA0;
        if (newCamX >= 0) {
            cameraX = newCamX;
        }

        // cameraY = sonicY - 0x70 (but not if negative)
        int newCamY = sonicPixelY - 0x70;
        if (newCamY >= 0) {
            cameraY = newCamY;
        }
    }

    // ---- Animation ----

    private void updateAnimCounters() {
        // Wall rotation frame from angle
        wallRotFrame = Sonic1SpecialStageBlockType.getWallRotationFrame(ssAngle);

        // Ring animation (ROM ani1): timer counts down, wraps to 7, frame advances 0..3.
        ringAnimTimer--;
        if (ringAnimTimer < 0) {
            ringAnimTimer = 7;
            ringAnimFrame = (ringAnimFrame + 1) & 0x3;
        }

        // Wall VRAM palette animation (SS_AniWallsRings ani0)
        wallVramAnimTimer--;
        if (wallVramAnimTimer < 0) {
            wallVramAnimTimer = 7;
            wallVramAnimFrame = (wallVramAnimFrame - 1) & 0x7;
        }

        // GOAL/UP/DOWN/emerald animation (SS_AniWallsRings ani2): 2-frame cycle, period 8
        ani2Timer--;
        if (ani2Timer < 0) {
            ani2Timer = 7;
            ani2Frame = (ani2Frame + 1) & 0x1;
        }

        // Glass block rotation animation (SS_AniWallsRings ani3): 4-frame cycle, period 5
        ani3Timer--;
        if (ani3Timer < 0) {
            ani3Timer = 4;
            ani3Frame = (ani3Frame + 1) & 0x3;
        }

        // Item collection animations (ring sparkle etc.)
        updateItemAnimations();

        updateSonicAnimation();
        updateSpecialStagePaletteCycle();
    }

    // ---- Art Loading ----

    private void loadPalette() throws IOException {
        // Resolve the Special Stage palette entry in a ROM-revision-safe way.
        // REV00: Sonic=3, Special=10. REV01 shifts both down by 1.
        Rom rom = GameServices.rom().getRom();
        int verifiedAddr = resolveSpecialPaletteAddress(rom);
        if (verifiedAddr != PAL_SS_ADDR) {
            LOGGER.fine("PAL_SS_ADDR mismatch: constant=0x" + Integer.toHexString(PAL_SS_ADDR)
                    + ", palette table says=0x" + Integer.toHexString(verifiedAddr)
                    + " - using verified address");
        }

        byte[] palData = dataLoader.getSSPalette(verifiedAddr);
        ssPaletteCycle1 = dataLoader.getSSPaletteCycle1();
        ssPaletteCycle2 = dataLoader.getSSPaletteCycle2();

        // palData is 128 bytes = 4 palette lines x 16 colors x 2 bytes each (MD format)
        ssPalettes = new Palette[4];
        for (int line = 0; line < 4; line++) {
            ssPalettes[line] = new Palette();
            byte[] lineData = new byte[Palette.PALETTE_SIZE_IN_ROM];
            int srcOffset = line * Palette.PALETTE_SIZE_IN_ROM;
            System.arraycopy(palData, srcOffset, lineData, 0,
                    Math.min(lineData.length, palData.length - srcOffset));
            ssPalettes[line].fromSegaFormat(lineData);
        }
        for (int i = 0; i < ssPalettes.length; i++) {
            graphicsManager.cachePaletteTexture(ssPalettes[i], i);
        }
    }

    private int resolveSpecialPaletteAddress(Rom rom) throws IOException {
        final int disasmSonicPaletteId = 3;
        final int disasmSpecialPaletteId = 10;
        final int specialOffsetFromSonic = disasmSpecialPaletteId - disasmSonicPaletteId;
        int sonicPaletteId = findSonicPaletteId(rom);
        int specialPaletteId = sonicPaletteId + specialOffsetFromSonic;
        return rom.read32BitAddr(PALETTE_TABLE_ADDR + specialPaletteId * 8) & 0x00FFFFFF;
    }

    private int findSonicPaletteId(Rom rom) throws IOException {
        // Scan range that contains Sonic in both REV00 and REV01 tables.
        for (int id = 2; id < 10; id++) {
            int entryAddr = PALETTE_TABLE_ADDR + id * 8;
            int dest = rom.read16BitAddr(entryAddr + 4) & 0xFFFF;
            int countWord = rom.read16BitAddr(entryAddr + 6) & 0xFFFF;
            int byteCount = (countWord + 1) * 4;
            if (dest == 0xFB00 && byteCount == 32) {
                return id;
            }
        }
        return 3;
    }

    private void loadArt() throws IOException {
        int nextBase = SS_PATTERN_BASE;

        // Wall art
        Pattern[] walls = dataLoader.getWallPatterns();
        int wallBase = nextBase;
        for (int i = 0; i < walls.length; i++) {
            graphicsManager.cachePatternTexture(walls[i], nextBase + i);
        }
        nextBase += walls.length;

        // Bumper art
        Pattern[] bumpers = dataLoader.getBumperPatterns();
        int bumperBase = nextBase;
        for (int i = 0; i < bumpers.length; i++) {
            graphicsManager.cachePatternTexture(bumpers[i], nextBase + i);
        }
        nextBase += bumpers.length;

        // GOAL art
        Pattern[] goals = dataLoader.getGoalPatterns();
        int goalBase = nextBase;
        for (int i = 0; i < goals.length; i++) {
            graphicsManager.cachePatternTexture(goals[i], nextBase + i);
        }
        nextBase += goals.length;

        // UP/DOWN art
        Pattern[] upDowns = dataLoader.getUpDownPatterns();
        int upDownBase = nextBase;
        for (int i = 0; i < upDowns.length; i++) {
            graphicsManager.cachePatternTexture(upDowns[i], nextBase + i);
        }
        nextBase += upDowns.length;

        // R block art
        Pattern[] rBlocks = dataLoader.getRBlockPatterns();
        int rBlockBase = nextBase;
        for (int i = 0; i < rBlocks.length; i++) {
            graphicsManager.cachePatternTexture(rBlocks[i], nextBase + i);
        }
        nextBase += rBlocks.length;

        // 1UP art
        Pattern[] oneUps = dataLoader.getOneUpPatterns();
        int oneUpBase = nextBase;
        for (int i = 0; i < oneUps.length; i++) {
            graphicsManager.cachePatternTexture(oneUps[i], nextBase + i);
        }
        nextBase += oneUps.length;

        // Emerald stars art
        Pattern[] emStars = dataLoader.getEmStarsPatterns();
        int emStarsBase = nextBase;
        for (int i = 0; i < emStars.length; i++) {
            graphicsManager.cachePatternTexture(emStars[i], nextBase + i);
        }
        nextBase += emStars.length;

        // Red-white art
        Pattern[] redWhites = dataLoader.getRedWhitePatterns();
        int redWhiteBase = nextBase;
        for (int i = 0; i < redWhites.length; i++) {
            graphicsManager.cachePatternTexture(redWhites[i], nextBase + i);
        }
        nextBase += redWhites.length;

        // Ghost art
        Pattern[] ghosts = dataLoader.getGhostPatterns();
        int ghostBase = nextBase;
        for (int i = 0; i < ghosts.length; i++) {
            graphicsManager.cachePatternTexture(ghosts[i], nextBase + i);
        }
        nextBase += ghosts.length;

        // W block art
        Pattern[] wBlocks = dataLoader.getWBlockPatterns();
        int wBlockBase = nextBase;
        for (int i = 0; i < wBlocks.length; i++) {
            graphicsManager.cachePatternTexture(wBlocks[i], nextBase + i);
        }
        nextBase += wBlocks.length;

        // Glass art
        Pattern[] glasses = dataLoader.getGlassPatterns();
        int glassBase = nextBase;
        for (int i = 0; i < glasses.length; i++) {
            graphicsManager.cachePatternTexture(glasses[i], nextBase + i);
        }
        nextBase += glasses.length;

        // Emerald art
        Pattern[] emeralds = dataLoader.getEmeraldPatterns();
        int emeraldBase = nextBase;
        for (int i = 0; i < emeralds.length; i++) {
            graphicsManager.cachePatternTexture(emeralds[i], nextBase + i);
        }
        nextBase += emeralds.length;

        // Ring art (reuse from normal level ring art already loaded)
        // The ring uses ArtTile_Ring which is already cached by the engine
        int ringBase = nextBase;
        // Load ring art for special stage if not already available
        Pattern[] rings = loadRingPatterns();
        if (rings != null) {
            for (int i = 0; i < rings.length; i++) {
                graphicsManager.cachePatternTexture(rings[i], nextBase + i);
            }
            nextBase += rings.length;
        }

        // Zone number art (1-6)
        int[] zoneBases = new int[6];
        for (int zoneIndex = 0; zoneIndex < 6; zoneIndex++) {
            Pattern[] zonePatterns = dataLoader.getZonePatterns(zoneIndex);
            zoneBases[zoneIndex] = nextBase;
            for (int i = 0; i < zonePatterns.length; i++) {
                graphicsManager.cachePatternTexture(zonePatterns[i], nextBase + i);
            }
            nextBase += zonePatterns.length;
        }

        // BG cloud art
        Pattern[] bgClouds = dataLoader.getBgCloudPatterns();
        bgCloudBase = nextBase;
        for (int i = 0; i < bgClouds.length; i++) {
            graphicsManager.cachePatternTexture(bgClouds[i], nextBase + i);
        }
        nextBase += bgClouds.length;

        // BG fish art
        Pattern[] bgFish = dataLoader.getBgFishPatterns();
        bgFishBase = nextBase;
        for (int i = 0; i < bgFish.length; i++) {
            graphicsManager.cachePatternTexture(bgFish[i], nextBase + i);
        }
        nextBase += bgFish.length;

        // Set pattern bases on renderer
        renderer.setPatternBases(wallBase, bumperBase, goalBase, upDownBase,
                rBlockBase, oneUpBase, emStarsBase, redWhiteBase, ghostBase,
                wBlockBase, glassBase, emeraldBase, ringBase,
                zoneBases[0], zoneBases[1], zoneBases[2],
                zoneBases[3], zoneBases[4], zoneBases[5],
                bgCloudBase, bgFishBase);

        LOGGER.fine("Loaded " + (nextBase - SS_PATTERN_BASE) + " SS art patterns");
    }

    private void loadSonicSprite() throws IOException {
        Rom rom = GameServices.rom().getRom();
        SpriteArtSet sonicArt = new Sonic1PlayerArt(RomByteReader.fromRom(rom)).loadSonic();
        if (sonicArt == null) {
            sonicSpriteRenderer = null;
            sonicSpriteFrame = 0;
            sonicRollScript = null;
            sonicRoll2Script = null;
            return;
        }

        sonicSpriteRenderer = new PlayerSpriteRenderer(sonicArt);
        sonicSpriteRenderer.ensureCached(graphicsManager);
        sonicRollScript = sonicArt.animationSet() != null
                ? sonicArt.animationSet().getScript(Sonic1AnimationIds.ROLL)
                : null;
        sonicRoll2Script = sonicArt.animationSet() != null
                ? sonicArt.animationSet().getScript(Sonic1AnimationIds.ROLL2)
                : null;

        sonicAnimId = Sonic1AnimationIds.ROLL.id();
        sonicAnimFrameIndex = 0;
        sonicAnimFrameTimer = 0;
        sonicSpriteFrame = resolveSpecialStageSonicFrame(sonicArt);
    }

    private int resolveSpecialStageSonicFrame(SpriteArtSet sonicArt) {
        if (sonicArt == null || sonicArt.animationSet() == null) {
            return 0;
        }
        SpriteAnimationScript rollScript = sonicArt.animationSet().getScript(Sonic1AnimationIds.ROLL);
        if (rollScript != null && rollScript.frames() != null && !rollScript.frames().isEmpty()) {
            return rollScript.frames().get(0);
        }
        return 0;
    }

    private void updateSonicAnimation() {
        SpriteAnimationScript rollScript = sonicRollScript;
        if (rollScript == null || rollScript.frames() == null || rollScript.frames().isEmpty()) {
            return;
        }

        int speed = Math.abs(sonicInertia);
        boolean useRoll2 = speed >= SS_ROLL_SPEED_SWITCH
                && sonicRoll2Script != null
                && sonicRoll2Script.frames() != null
                && !sonicRoll2Script.frames().isEmpty();
        int targetAnimId = useRoll2 ? Sonic1AnimationIds.ROLL2.id() : Sonic1AnimationIds.ROLL.id();
        SpriteAnimationScript activeScript = useRoll2 ? sonicRoll2Script : rollScript;

        if (sonicAnimId != targetAnimId) {
            sonicAnimId = targetAnimId;
            sonicAnimFrameIndex = 0;
            sonicAnimFrameTimer = 0;
        }

        if (sonicAnimFrameTimer > 0) {
            sonicAnimFrameTimer--;
            return;
        }

        int delay = ((0x400 - speed) < 0 ? 0 : (0x400 - speed)) >> 8;
        sonicAnimFrameTimer = delay;

        if (sonicAnimFrameIndex < 0 || sonicAnimFrameIndex >= activeScript.frames().size()) {
            sonicAnimFrameIndex = 0;
        }

        sonicSpriteFrame = activeScript.frames().get(sonicAnimFrameIndex);
        advanceSonicFrameIndex(activeScript);
    }

    private void advanceSonicFrameIndex(SpriteAnimationScript script) {
        int next = sonicAnimFrameIndex + 1;
        if (next < script.frames().size()) {
            sonicAnimFrameIndex = next;
            return;
        }

        switch (script.endAction()) {
            case HOLD -> sonicAnimFrameIndex = script.frames().size() - 1;
            case LOOP_BACK -> sonicAnimFrameIndex = resolveLoopBackIndex(script);
            case SWITCH -> {
                int nextAnimId = script.endParam();
                sonicAnimId = nextAnimId;
                sonicAnimFrameIndex = 0;
            }
            case LOOP -> sonicAnimFrameIndex = 0;
            default -> sonicAnimFrameIndex = 0;
        }
    }

    private int resolveLoopBackIndex(SpriteAnimationScript script) {
        int loopBack = script.endParam();
        if (loopBack <= 0) {
            return 0;
        }
        int target = script.frames().size() - loopBack;
        return Math.max(0, target);
    }

    private void updateSpecialStagePaletteCycle() {
        if (ssPalettes == null || ssPaletteCycle1 == null || ssPaletteCycle2 == null) {
            return;
        }

        palSsTime--;
        if (palSsTime >= 0) {
            return;
        }

        int[] entry = SS_BG_STATE_TABLE[palSsNum & 0x1F];
        palSsNum++;
        palSsTime = entry[0] < 0 ? 0x1FF : entry[0];

        // Extract anim and namespace selection fields.
        bgAnimState = entry[1];
        updateFgStateFromAnim(bgAnimState);

        boolean wantPlane5 = entry[2] == 1;
        if (wantPlane5 == bgUsingPlane6) {
            bgUsingPlane6 = !wantPlane5;
            if (bgRenderer != null) {
                bgRenderer.setTilemap(bgUsingPlane6 ? bgPlane6Tilemap : bgPlane5Tilemap);
            }
        }

        int d0 = entry[3] & 0xFF;
        boolean[] touched = new boolean[4];

        if ((d0 & 0x80) == 0) {
            writePaletteBytes(ssPaletteCycle1, d0, 0x4E, 12, touched);
            recacheTouchedPalettes(touched);
            markBackgroundLayersDirtyIfPaletteTouched(touched);
            return;
        }

        int d1 = palSsIndex;
        if (d0 >= 0x8A) {
            d1++;
        }
        int base = d1 * 0x2A;
        int idx = d0 & 0x7F;
        // ROM: bclr #0,d0 / beq.s loc_4A18
        // bclr clears bit 0 and sets Z if bit was already 0.
        // beq skips the write if bit was 0 (even index).
        boolean bit0WasSet = (idx & 1) != 0;
        idx &= ~1;  // bclr #0,d0

        if (bit0WasSet) {
            writePaletteBytes(ssPaletteCycle2, base, 0x6E, 12, touched);
        }

        int src = base + 0x0C;
        int dest = 0x5A;
        if (idx >= 0x0A) {
            idx -= 0x0A;
            dest = 0x7A;
        }
        src += idx * 3;
        writePaletteBytes(ssPaletteCycle2, src, dest, 6, touched);
        recacheTouchedPalettes(touched);
        markBackgroundLayersDirtyIfPaletteTouched(touched);
    }

    private void markBackgroundLayersDirtyIfPaletteTouched(boolean[] touchedLines) {
        if (touchedLines == null) {
            return;
        }
        boolean touched = false;
        for (boolean lineTouched : touchedLines) {
            if (lineTouched) {
                touched = true;
                break;
            }
        }
        if (!touched) {
            return;
        }
        if (bgRenderer != null) {
            bgRenderer.markDirty();
        }
        if (fgRenderer != null) {
            fgRenderer.markDirty();
        }
    }

    /**
     * Mirrors byte_4ABC indirection used by PalCycle_SS:
     * anim values (0,2,4,6,8,10,12) select FG plane namespace and Y scroll.
     */
    private void updateFgStateFromAnim(int animState) {
        int planeIndex;
        int y;
        switch (animState) {
            case 2 -> {
                planeIndex = 1; // Plane 2
                y = 0;
            }
            case 4 -> {
                planeIndex = 1; // Plane 2
                y = 0x100;
            }
            case 6 -> {
                planeIndex = 2; // Plane 3
                y = 0;
            }
            case 8 -> {
                planeIndex = 2; // Plane 3
                y = 0x100;
            }
            case 10 -> {
                planeIndex = 3; // Plane 4
                y = 0;
            }
            case 12 -> {
                planeIndex = 3; // Plane 4
                y = 0x100;
            }
            default -> {
                planeIndex = 0; // Plane 1
                y = 0x100;
            }
        }

        fgAnimPlaneIndex = planeIndex;
        fgYScroll = y;

        if (fgRenderer != null && fgPlaneTilemaps != null && planeIndex >= 0 && planeIndex < fgPlaneTilemaps.length) {
            fgRenderer.setTilemap(fgPlaneTilemaps[planeIndex]);
        }
    }

    private void writePaletteBytes(byte[] source, int sourceOffset, int destByteOffset, int byteCount,
                                   boolean[] touchedLines) {
        if (source == null || byteCount <= 0 || sourceOffset < 0 || sourceOffset + byteCount > source.length) {
            return;
        }
        int colorCount = byteCount / 2;
        int paletteColorIndex = destByteOffset / 2;
        for (int i = 0; i < colorCount; i++) {
            int globalColor = paletteColorIndex + i;
            int line = globalColor / Palette.PALETTE_SIZE;
            int colorInLine = globalColor % Palette.PALETTE_SIZE;
            if (line < 0 || line >= ssPalettes.length) {
                continue;
            }
            ssPalettes[line].getColor(colorInLine).fromSegaFormat(source, sourceOffset + (i * 2));
            if (touchedLines != null && line < touchedLines.length) {
                touchedLines[line] = true;
            }
        }
    }

    private void recacheTouchedPalettes(boolean[] touchedLines) {
        if (touchedLines == null || ssPalettes == null) {
            return;
        }
        for (int i = 0; i < touchedLines.length && i < ssPalettes.length; i++) {
            if (touchedLines[i]) {
                graphicsManager.cachePaletteTexture(ssPalettes[i], i);
            }
        }
    }

    /**
     * SS_BGAnimate (sonic.asm lines 3806-3892): Per-scanline H-scroll animation.
     *
     * Two paths based on bgAnimState:
     * - ANIM 0-7 (sine wave): 10 oscillators with independent amplitude/speed/phase
     * - ANIM 8-12 (band scroll): 7 bands with independent 16.16 fixed-point positions
     *
     * Both paths fill bgHScrollData (224 scanlines) via band widths, wrapping at 256 scanlines.
     */
    private void updateBgAnimate() {
        if (bgHScrollData == null) {
            return;
        }

        if (bgAnimState < 8) {
            // Sine wave path
            if (bgAnimState == 0) {
                bgYScroll = 0;
            }
            if (bgAnimState == 6) {
                bgExtraScrollX++;
                bgYScroll++;
            }
            // Update 10 sine oscillators (v_ngfx_buffer)
            // ROM: CalcSine(phase) → sin * amplitude >> 8 → store scroll; phase += speed
            if (bgSineBuffer != null) {
                for (int i = 0; i < 10; i++) {
                    int phase = bgSineBuffer[i * 2 + 1];
                    int sinVal = TrigLookupTable.sinHex(phase & 0xFF);
                    int amplitude = SS_BG_SINE_AMPLITUDES[i];
                    int scroll = (sinVal * amplitude) >> 8;
                    bgSineBuffer[i * 2] = scroll;
                    bgSineBuffer[i * 2 + 1] = phase + SS_BG_SINE_SPEEDS[i];
                }
                fillHScrollFromBands(bgSineBuffer, SS_SINE_BAND_WIDTHS);
            }
        } else {
            // Band scroll path
            if (bgAnimState == 12 && bgBandBuffer != null) {
                bgExtraScrollX--;
                // Update band speeds: first band gets $18000, each subsequent $2000 less
                int speed = 0x18000;
                for (int i = 0; i < 7; i++) {
                    long val = ((long) bgBandBuffer[i * 2] << 16) | (bgBandBuffer[i * 2 + 1] & 0xFFFF);
                    val -= speed;
                    bgBandBuffer[i * 2] = (int) (val >> 16);
                    bgBandBuffer[i * 2 + 1] = (int) (val & 0xFFFF);
                    speed -= 0x2000;
                }
            }
            if (bgBandBuffer != null) {
                fillHScrollFromBands(bgBandBuffer, SS_SCROLL_BAND_WIDTHS);
            }
        }
    }

    /**
     * Common H-scroll fill routine for both sine and band scroll paths.
     * Mirrors the ROM code at loc_4C7E-loc_4CA4.
     *
     * The ROM writes 32-bit entries (FG|BG) to the H-scroll table. We only
     * need the BG portion (low word), which is the per-band scroll value.
     * The FG scroll (high word = -bg3screenposx) is irrelevant to our rendering.
     *
     * @param scrollBuffer Array of [scroll, phase/frac] pairs (stride 2)
     * @param bandWidths   First element = band count - 1, remaining = scanline heights
     */
    private void fillHScrollFromBands(int[] scrollBuffer, int[] bandWidths) {
        // ROM: scanline offset = (-bgscreenposy & $FF) * 4, wrapping at $3FC
        // We use & 0xFF since our array is indexed by scanline, not by 4-byte stride
        int scanline = (-bgYScroll) & 0xFF;
        int bandCount = bandWidths[0] + 1;
        for (int band = 0; band < bandCount; band++) {
            int scroll = scrollBuffer[band * 2]; // BG scroll value (high word for bands, sine value for sine)
            int height = bandWidths[band + 1];
            for (int j = 0; j < height; j++) {
                int idx = scanline & 0xFF;
                if (idx < 224) {
                    bgHScrollData[idx] = scroll;
                }
                scanline++;
            }
        }
    }

    private Pattern[] loadRingPatterns() throws IOException {
        RingSpriteSheet ringSheet = new Sonic1RingArt(GameServices.rom().getRom()).load();
        if (ringSheet == null || ringSheet.getPatterns() == null) {
            return new Pattern[0];
        }
        return ringSheet.getPatterns();
    }

    // ---- Background Renderer ----

    private void initBgRenderer() {
        // Load disassembly-equivalent FG/BG namespaces regardless of headless mode.
        try {
            fgPlaneTilemaps = new byte[4][];
            for (int i = 0; i < 4; i++) {
                fgPlaneTilemaps[i] = dataLoader.getFgPlaneTilemap(i + 1);
            }
            bgPlane5Tilemap = dataLoader.getBgPlane5Tilemap();
            bgPlane6Tilemap = dataLoader.getBgPlane6Tilemap();
        } catch (Exception e) {
            LOGGER.warning("Failed to load SS BG planes: " + e.getMessage());
            fgPlaneTilemaps = null;
            bgPlane5Tilemap = null;
            bgPlane6Tilemap = null;
        }

        // Initialize BG animation state.
        bgUsingPlane6 = true;
        bgAnimState = 0;
        bgYScroll = 0;
        bgExtraScrollX = 0;
        bgSineBuffer = new int[20]; // 10 entries x 2 words [scroll, phase]
        bgBandBuffer = new int[14]; // 7 entries x 2 words [pos_hi, pos_lo]
        bgHScrollData = new int[224];
        updateFgStateFromAnim(0); // byte_4ABC default for anim 0 => plane 1, y=$100

        if (fgPlaneTilemaps == null || bgPlane5Tilemap == null || bgPlane6Tilemap == null) {
            bgRenderer = null;
            fgRenderer = null;
            return;
        }

        if (graphicsManager.isHeadlessMode()) {
            bgRenderer = null;
            fgRenderer = null;
            return;
        }
        try {
            bgRenderer = new Sonic1SpecialStageBackgroundRenderer(graphicsManager);
            bgRenderer.init();
            bgRenderer.setPatternBases(bgCloudBase, bgFishBase);
            bgRenderer.setTilemap(bgPlane6Tilemap); // BG starts on plane 6.
            bgRenderer.setFillTransparentWithBackdrop(true);

            fgRenderer = new Sonic1SpecialStageBackgroundRenderer(graphicsManager);
            fgRenderer.init();
            fgRenderer.setPatternBases(bgCloudBase, bgFishBase);
            fgRenderer.setFillTransparentWithBackdrop(false);
            if (fgPlaneTilemaps != null && fgPlaneTilemaps.length > 0) {
                fgRenderer.setTilemap(fgPlaneTilemaps[0]); // FG starts on plane 1.
            }
            LOGGER.fine("S1 SS background renderers initialized");
        } catch (Exception e) {
            LOGGER.warning("Failed to init S1 SS background renderer, using fallback: " + e.getMessage());
            bgRenderer = null;
            fgRenderer = null;
        }
    }

    // ---- Drawing ----

    public void draw() {
        if (!initialized || renderer == null || layout == null) {
            return;
        }
        graphicsManager.setUseWaterShader(false);
        graphicsManager.setUseSpritePriorityShader(false);
        graphicsManager.setCurrentSpriteHighPriority(false);
        graphicsManager.setWaterEnabled(false);
        graphicsManager.setUseUnderwaterPaletteForBackground(false);

        // Update backdrop color from palette (CRAM[0] equivalent)
        Palette.Color backdrop = getBackdropColor();
        float bdR = 0, bdG = 0, bdB = 0;
        if (backdrop != null) {
            bdR = backdrop.rFloat();
            bdG = backdrop.gFloat();
            bdB = backdrop.bFloat();
        }
        renderer.setBackdropColor(bdR, bdG, bdB);
        if (bgRenderer != null) {
            bgRenderer.setBackdropColor(bdR, bdG, bdB);
        }
        if (fgRenderer != null) {
            fgRenderer.setBackdropColor(bdR, bdG, bdB);
        }

        if (bgRenderer != null && bgRenderer.isInitialized()
                && fgRenderer != null && fgRenderer.isInitialized()) {
            drawWithBgRenderers();
        } else {
            // Fallback: solid color background + maze
            renderer.render(layout, ssAngle, cameraX, cameraY,
                    (int) (sonicPosX >> 16), (int) (sonicPosY >> 16),
                    wallRotFrame, ringAnimFrame, wallVramAnimFrame,
                    ani2Frame, ani3Frame, sonicFacingLeft);
        }

        if (sonicSpriteRenderer != null) {
            int sonicScreenX = Sonic1SpecialStageRenderer.SCREEN_CENTER_OFFSET +
                    (int) (sonicPosX >> 16) - cameraX;
            int sonicScreenY = (int) (sonicPosY >> 16) - cameraY;
            sonicSpriteRenderer.drawFrame(sonicSpriteFrame, sonicScreenX, sonicScreenY, sonicFacingLeft, false);
        }
    }

    private void drawWithBgRenderers() {
        renderLayerToFbo(bgRenderer);
        renderLayerToFbo(fgRenderer);

        // BG pass: per-scanline H-scroll + BG V-scroll.
        final int[] bgScrollSnapshot = bgHScrollData != null ? bgHScrollData.clone() : new int[224];
        final float bgVScroll = (float) bgYScroll;
        graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
            bgRenderer.setHScrollData(bgScrollSnapshot);
            bgRenderer.renderWithShader(bgVScroll);
        }));

        // FG pass: uniform H-scroll (-v_bg3screenposx) + FG V-scroll from byte_4ABC.
        final int fgUniformHScroll = -bgExtraScrollX;
        final float fgVScroll = (float) fgYScroll;
        graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
            fgRenderer.setUniformHScroll(fgUniformHScroll);
            fgRenderer.renderWithShader(fgVScroll);
        }));

        // Maze pass - every frame.
        renderer.renderMaze(layout, ssAngle, cameraX, cameraY, wallRotFrame, ringAnimFrame, wallVramAnimFrame,
                ani2Frame, ani3Frame);
    }

    private void renderLayerToFbo(Sonic1SpecialStageBackgroundRenderer layerRenderer) {
        if (layerRenderer == null || !layerRenderer.needsRedraw()) {
            return;
        }

        layerRenderer.beginFBOProjection();
        graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
            layerRenderer.beginTilePass(Sonic1SpecialStageRenderer.H32_HEIGHT);
        }));
        graphicsManager.beginPatternBatch();
        layerRenderer.renderTilesToFBO(graphicsManager);
        graphicsManager.flushPatternBatch();
        layerRenderer.endFBOProjection();
        graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
            layerRenderer.endTilePass();
        }));
    }

    // ---- Input ----

    public void handleInput(int heldButtons, int pressedButtons) {
        this.heldButtons = heldButtons;
        this.pressedButtons |= pressedButtons;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void toggleDebugMode() {
        if (!debugMode) {
            debugSavedAngle = ssAngle;
            debugSavedRotate = ssRotate;
            ssAngle = 0;
            ssRotate = 0;
            debugMode = true;
        } else {
            ssAngle = debugSavedAngle;
            ssRotate = debugSavedRotate;
            debugMode = false;
        }
        sonicVelX = 0;
        sonicVelY = 0;
        sonicInertia = 0;
        sonicAirborne = true;
        lastCollisionBlockId = 0;
    }

    // ---- State queries ----

    public boolean isFinished() {
        return finished;
    }

    public void reset() {
        GraphicsManager gm = graphicsManager;
        if (bgRenderer != null) {
            bgRenderer.cleanup();
            bgRenderer = null;
        }
        if (fgRenderer != null) {
            fgRenderer.cleanup();
            fgRenderer = null;
        }
        initialized = false;
        finished = false;
        emeraldCollected = false;
        debugMode = false;
        debugSavedAngle = 0;
        debugSavedRotate = 0;
        ringsCollected = 0;
        currentStage = 0;
        layout = null;
        dataLoader = null;
        renderer = null;
        sonicSpriteRenderer = null;
        sonicSpriteFrame = 0;
        sonicRollScript = null;
        sonicRoll2Script = null;
        ssPalettes = null;
        ssPaletteCycle1 = null;
        ssPaletteCycle2 = null;
        sonicAnimFrameIndex = 0;
        sonicAnimFrameTimer = 0;
        exitFadeStarted = false;
        exitFadeTimer = 0;
        sonicAnimId = Sonic1AnimationIds.ROLL.id();
        wallVramAnimFrame = 0;
        wallVramAnimTimer = 0;
        ssAnimBuffer = null;
        ssAnimGlassFinalBlock = null;
        bgCloudBase = 0;
        bgFishBase = 0;
        bgAnimState = 0;
        fgPlaneTilemaps = null;
        bgPlane5Tilemap = null;
        bgPlane6Tilemap = null;
        bgUsingPlane6 = true;
        fgAnimPlaneIndex = 0;
        fgYScroll = 0;
        bgYScroll = 0;
        bgExtraScrollX = 0;
        bgSineBuffer = null;
        bgBandBuffer = null;
        bgHScrollData = null;
        palSsTime = 0;
        palSsNum = 0;
        palSsIndex = 0;
        ani2Frame = 0;
        ani2Timer = 0;
        ani3Frame = 0;
        ani3Timer = 0;
        heldButtons = 0;
        pressedButtons = 0;
        if (gm != null) {
            gm.setUseWaterShader(false);
            gm.setUseSpritePriorityShader(false);
            gm.setCurrentSpriteHighPriority(false);
            gm.setWaterEnabled(false);
            gm.setUseUnderwaterPaletteForBackground(false);
        }
        graphicsManager = null;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Returns the SS backdrop color (palette line 0, color 0).
     * On the Mega Drive, CRAM[0] fills all unpainted/transparent areas.
     */
    public Palette.Color getBackdropColor() {
        if (ssPalettes != null && ssPalettes.length > 0) {
            return ssPalettes[0].getColor(0);
        }
        return null;
    }

    public int getCurrentStage() {
        return currentStage;
    }

    public boolean isEmeraldCollected() {
        return emeraldCollected;
    }

    public void setEmeraldCollected(boolean collected) {
        this.emeraldCollected = collected;
    }

    public int getRingsCollected() {
        return ringsCollected;
    }

    public void setRingsCollected(int ringsCollected) {
        this.ringsCollected = Math.max(0, ringsCollected);
    }

    public void markFinished() {
        this.finished = true;
    }

    private void playSfx(Sonic1Sfx sfx) {
        if (sfx != null) {
            GameServices.audio().playSfx(sfx.id);
        }
    }

    private void playSfx(GameSound sfx) {
        if (sfx != null) {
            GameServices.audio().playSfx(sfx);
        }
    }

    private void playMusic(Sonic1Music music) {
        if (music != null) {
            GameServices.audio().playMusic(music.id);
        }
    }
}
