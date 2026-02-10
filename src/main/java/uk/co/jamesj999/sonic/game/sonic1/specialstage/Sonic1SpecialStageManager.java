package uk.co.jamesj999.sonic.game.sonic1.specialstage;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1PlayerArt;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1RingArt;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Music;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Sfx;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1AnimationIds;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Palette;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.rings.RingSpriteSheet;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationEndAction;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationScript;
import uk.co.jamesj999.sonic.sprites.art.SpriteArtSet;
import uk.co.jamesj999.sonic.sprites.render.PlayerSpriteRenderer;
import uk.co.jamesj999.sonic.physics.TrigLookupTable;

import java.io.IOException;
import java.util.logging.Logger;

import static uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants.*;
import static uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite.*;

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

    // PalCycle_SS script (time, palette-cycle selector byte)
    private static final int[][] SS_PALETTE_CYCLE_SCRIPT = {
            {3, 0x92}, {3, 0x90}, {3, 0x8E}, {3, 0x8C}, {3, 0x8B},
            {3, 0x80}, {3, 0x82}, {3, 0x84}, {3, 0x86}, {3, 0x88},
            {7, 0x00}, {7, 0x0C}, {-1, 0x18}, {-1, 0x18}, {7, 0x0C}, {7, 0x00},
            {3, 0x88}, {3, 0x86}, {3, 0x84}, {3, 0x82}, {3, 0x81},
            {3, 0x8A}, {3, 0x8C}, {3, 0x8E}, {3, 0x90}, {3, 0x92},
            {7, 0x24}, {7, 0x30}, {-1, 0x3C}, {-1, 0x3C}, {7, 0x30}, {7, 0x24}
    };

    private boolean initialized;
    private boolean finished;
    private boolean emeraldCollected;
    private int currentStage;
    private int ringsCollected;

    // Layout
    private byte[] layout;

    // Rotation
    private int ssAngle;       // 16-bit rotation angle (top byte = hex angle)
    private int ssRotate;      // 16-bit rotation speed

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
    private int sonicAnimId;
    private int sonicAnimFrameIndex;
    private int sonicAnimFrameTimer;
    private int palSsTime;
    private int palSsNum;
    private int palSsIndex;

    // Exit sequence
    private boolean exitTriggered;
    private int exitPhase;
    private int exitTimer;

    // Input state (set by handleInput, consumed by update)
    private int heldButtons;
    private int pressedButtons;

    // Subsystems
    private Sonic1SpecialStageDataLoader dataLoader;
    private Sonic1SpecialStageRenderer renderer;
    private GraphicsManager graphicsManager;
    private PlayerSpriteRenderer sonicSpriteRenderer;
    private int sonicSpriteFrame;
    private SpriteAnimationScript sonicRollScript;
    private SpriteAnimationScript sonicRoll2Script;
    private Palette[] ssPalettes;
    private byte[] ssPaletteCycle1;
    private byte[] ssPaletteCycle2;

    public void initialize(int stageIndex) throws IOException {
        this.currentStage = Math.max(0, Math.min(stageIndex, SS_STAGE_COUNT - 1));
        this.ringsCollected = 0;
        this.emeraldCollected = false;
        this.finished = false;

        // Initialize subsystems
        Rom rom = GameServices.rom().getRom();
        this.graphicsManager = GraphicsManager.getInstance();
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

        // Initialize rotation
        ssAngle = 0;
        ssRotate = SS_INIT_ROTATION;

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
        sonicAnimId = Sonic1AnimationIds.ROLL;
        sonicAnimFrameIndex = 0;
        sonicAnimFrameTimer = 0;

        // Initialize exit state
        exitTriggered = false;
        exitPhase = 0;
        exitTimer = 0;

        // Initialize palette cycle state (PalCycle_SS)
        palSsTime = 0;
        palSsNum = 0;
        palSsIndex = 0;

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
        }

        // Clear pressed buttons (held persist until next handleInput call)
        pressedButtons = 0;
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
            layout[bufIndex] = 0;
            ringsCollected++;
            playSfx(Sonic1Sfx.RING);
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
                layout[idx] = (byte) nextState;
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

    // ---- Exit Sequence (from Obj09_ExitStage / Obj09_Exit2) ----

    private void updateExit() {
        if (exitPhase == 0) {
            // Phase 0: speed up rotation until 0x1800, then continue to 0x3000
            ssRotate += 0x40;

            if (ssRotate == 0x1800) {
                // Signal game mode change (handled by provider checking isFinished)
            }
            if (ssRotate >= 0x3000) {
                ssRotate = 0;
                ssAngle = 0x4000;
                exitPhase = 1;
                exitTimer = 60; // 0x3C frames
            }

            // Keep rotating
            ssAngle = (ssAngle + ssRotate) & 0xFFFF;
        } else {
            // Phase 1: countdown timer
            exitTimer--;
            if (exitTimer <= 0) {
                finished = true;
            }
        }

        // Update camera during exit
        updateCamera();

        // Update animation
        updateAnimCounters();
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

        // Ring animation: 8-frame cycle at 8 frames per step
        ringAnimTimer++;
        if (ringAnimTimer >= 8) {
            ringAnimTimer = 0;
            ringAnimFrame = (ringAnimFrame + 1) & 0x7;
        }

        updateSonicAnimation();
        updateSpecialStagePaletteCycle();
    }

    // ---- Art Loading ----

    private void loadPalette() throws IOException {
        byte[] palData = dataLoader.getSSPalette();
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
        int bgCloudBase = nextBase;
        for (int i = 0; i < bgClouds.length; i++) {
            graphicsManager.cachePatternTexture(bgClouds[i], nextBase + i);
        }
        nextBase += bgClouds.length;

        // BG fish art
        Pattern[] bgFish = dataLoader.getBgFishPatterns();
        int bgFishBase = nextBase;
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

        sonicAnimId = Sonic1AnimationIds.ROLL;
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
        int targetAnimId = useRoll2 ? Sonic1AnimationIds.ROLL2 : Sonic1AnimationIds.ROLL;
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

        int[] entry = SS_PALETTE_CYCLE_SCRIPT[palSsNum & 0x1F];
        palSsNum++;
        palSsTime = entry[0] < 0 ? 0x1FF : entry[0];

        int d0 = entry[1] & 0xFF;
        boolean[] touched = new boolean[4];

        if ((d0 & 0x80) == 0) {
            writePaletteBytes(ssPaletteCycle1, d0, 0x4E, 12, touched);
            recacheTouchedPalettes(touched);
            return;
        }

        int d1 = palSsIndex;
        if (d0 >= 0x8A) {
            d1++;
        }
        int base = d1 * 0x2A;
        int idx = d0 & 0x7F;
        idx &= ~1;

        if (idx != 0) {
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

    private Pattern[] loadRingPatterns() throws IOException {
        RingSpriteSheet ringSheet = new Sonic1RingArt(GameServices.rom().getRom()).load();
        if (ringSheet == null || ringSheet.getPatterns() == null) {
            return new Pattern[0];
        }
        return ringSheet.getPatterns();
    }

    // ---- Drawing ----

    public void draw() {
        if (!initialized || renderer == null || layout == null) {
            return;
        }

        renderer.render(layout, ssAngle, cameraX, cameraY,
                (int) (sonicPosX >> 16), (int) (sonicPosY >> 16),
                wallRotFrame, ringAnimFrame, sonicFacingLeft);

        if (sonicSpriteRenderer != null) {
            int sonicScreenX = Sonic1SpecialStageRenderer.SCREEN_CENTER_OFFSET +
                    (int) (sonicPosX >> 16) - cameraX;
            int sonicScreenY = (int) (sonicPosY >> 16) - cameraY;
            sonicSpriteRenderer.drawFrame(sonicSpriteFrame, sonicScreenX, sonicScreenY, sonicFacingLeft, false);
        }
    }

    // ---- Input ----

    public void handleInput(int heldButtons, int pressedButtons) {
        this.heldButtons = heldButtons;
        this.pressedButtons |= pressedButtons;
    }

    // ---- State queries ----

    public boolean isFinished() {
        return finished;
    }

    public void reset() {
        initialized = false;
        finished = false;
        emeraldCollected = false;
        ringsCollected = 0;
        currentStage = 0;
        layout = null;
        dataLoader = null;
        renderer = null;
        graphicsManager = null;
        sonicSpriteRenderer = null;
        sonicSpriteFrame = 0;
        sonicRollScript = null;
        sonicRoll2Script = null;
        ssPalettes = null;
        ssPaletteCycle1 = null;
        ssPaletteCycle2 = null;
        sonicAnimFrameIndex = 0;
        sonicAnimFrameTimer = 0;
        sonicAnimId = Sonic1AnimationIds.ROLL;
        palSsTime = 0;
        palSsNum = 0;
        palSsIndex = 0;
        heldButtons = 0;
        pressedButtons = 0;
    }

    public boolean isInitialized() {
        return initialized;
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
            AudioManager.getInstance().playSfx(sfx.id);
        }
    }

    private void playMusic(Sonic1Music music) {
        if (music != null) {
            AudioManager.getInstance().playMusic(music.id);
        }
    }
}
