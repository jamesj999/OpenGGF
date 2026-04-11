package com.openggf.game.sonic3k.features;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HCZ Water Skim handler — implements the mechanic where the player runs
 * across the water surface at high speed with a splash trail behind them.
 * <p>
 * Port of Obj_HCZWaterSplash (subtype 1) and sub_3857E from sonic3k.asm.
 * <p>
 * Activation conditions (sub_3857E, sonic3k.asm:75393):
 * <ul>
 *   <li>Player feet at water level (y_pos + y_radius + 1 == Water_level)</li>
 *   <li>Zero vertical velocity (y_vel == 0)</li>
 *   <li>Horizontal speed >= $700 (abs(x_vel) >= 0x700)</li>
 * </ul>
 * <p>
 * While skimming:
 * <ul>
 *   <li>Player Y pinned to water surface</li>
 *   <li>Friction of $C/frame when airborne and no directional input</li>
 *   <li>Jump exit (A/B/C): y_vel = -$680, roll state</li>
 *   <li>Speed exit: below $700 threshold, player falls into water</li>
 *   <li>sfx_WaterSkid every 16 frames</li>
 * </ul>
 */
public final class HCZWaterSkimHandler {

    private static final Logger LOGGER = Logger.getLogger(HCZWaterSkimHandler.class.getName());

    // ===== Physics constants from sub_3857E =====
    /** Minimum |x_vel| to start/sustain skimming (sonic3k.asm:75409) */
    private static final int SPEED_THRESHOLD = 0x700;
    /** Friction per frame when airborne with no directional input (sonic3k.asm:75450) */
    private static final int SKIM_FRICTION = 0xC;
    /** Y velocity on jump exit (sonic3k.asm:75484) */
    private static final short JUMP_EXIT_Y_VEL = -0x680;
    // Jump exit radii ($0E/$07) match standard rolling radii — use applyRollingRadii()

    // ===== Splash animation constants (loc_384B2, sonic3k.asm:75314) =====
    /** Animation timer: 3 frames per step (sonic3k.asm:75330) */
    private static final int SPLASH_ANIM_DELAY = 3;
    /** Number of animation frames to cycle through (0-4) (sonic3k.asm:75332) */
    private static final int SPLASH_ANIM_FRAMES = 5;
    /** Frame index for "exit" splash (sonic3k.asm:75475) */
    private static final int SPLASH_EXIT_FRAME = 5;
    /** SFX plays when (Level_frame_counter+1+2) & 0xF == 0, i.e. every 16 frames */
    private static final int SFX_INTERVAL_MASK = 0xF;

    // ===== Per-player skim state =====
    private static boolean skimActiveP1;
    private static boolean skimActiveP2;
    private static int splashAnimFrameP1;
    private static int splashAnimFrameP2;
    private static int splashAnimTimerP1;
    private static int splashAnimTimerP2;

    // ===== Frame counter (for SFX timing) =====
    private static int frameCounter;

    // ===== Rendering =====
    private static PatternSpriteRenderer splashRenderer;
    private static boolean artLoaded;
    private static int actId;

    private HCZWaterSkimHandler() {}

    /**
     * Initialize the skim handler for a given HCZ act.
     * Loads the ArtUnc_HCZWaterSplash2 art from ROM and builds the sprite renderer.
     */
    public static void init(Rom rom, int act) {
        reset();
        actId = act;
        try {
            Pattern[] patterns = loadSplashPatterns(rom);
            List<SpriteMappingFrame> frames = buildSplashMappings();
            splashRenderer = new PatternSpriteRenderer(
                    new ObjectSpriteSheet(patterns, frames, 0, 0));
            LOGGER.info(String.format("HCZ water skim: loaded %d patterns, %d mapping frames",
                    patterns.length, frames.size()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load HCZ water skim art", e);
            splashRenderer = null;
        }
    }

    /**
     * Main per-frame update. Called from zone feature provider's pre-physics hook.
     * Processes both P1 and P2 skim state.
     */
    public static void update() {
        frameCounter++;
        AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
        if (player == null || player.getDead() || player.isDebugMode()) {
            if (skimActiveP1) {
                skimActiveP1 = false;
                splashAnimFrameP1 = SPLASH_EXIT_FRAME;
            }
            return;
        }

        int waterLevel = getWaterLevel();
        if (waterLevel == 0) {
            return;
        }

        skimActiveP1 = processSkimPhysics(player, skimActiveP1, waterLevel, frameCounter);

        // Process sidekick (P2) if present
        List<? extends AbstractPlayableSprite> sidekicks = GameServices.sprites().getSidekicks();
        if (sidekicks != null && !sidekicks.isEmpty()) {
            AbstractPlayableSprite p2 = sidekicks.getFirst();
            if (p2 != null && !p2.getDead()) {
                skimActiveP2 = processSkimPhysics(p2, skimActiveP2, waterLevel, frameCounter);
            } else if (skimActiveP2) {
                skimActiveP2 = false;
                splashAnimFrameP2 = SPLASH_EXIT_FRAME;
            }
        }
    }

    /**
     * Port of sub_3857E — the core skim physics check and update.
     *
     * @param player     the player sprite
     * @param wasActive  whether skim was active last frame
     * @param waterLevel the water surface Y coordinate
     * @param frameCounter global frame counter
     * @return true if skim is active after this frame
     */
    private static boolean processSkimPhysics(AbstractPlayableSprite player,
                                               boolean wasActive,
                                               int waterLevel,
                                               int frameCounter) {
        if (!wasActive) {
            // === Activation check (sonic3k.asm:75393-75421) ===
            // Condition 1: y_vel must be zero
            if (player.getYSpeed() != 0) {
                return false;
            }

            // Condition 2: player feet at water level
            // ROM: y_radius(a1) + y_pos(a1) + 1 == Water_level
            // Engine: getCentreY() + getYRadius() gives feet Y (centre + half-height)
            int feetY = player.getCentreY() + player.getYRadius() + 1;
            if (feetY != waterLevel) {
                return false;
            }

            // Condition 3: |x_vel| >= $700
            int absXSpeed = Math.abs(player.getXSpeed());
            if (absXSpeed < SPEED_THRESHOLD) {
                return false;
            }

            // Activate! Set facing direction based on x_vel sign
            // ROM: bclr/bset #Status_Facing based on x_vel sign
            if (player.getXSpeed() < 0) {
                player.setDirection(Direction.LEFT);
            } else {
                player.setDirection(Direction.RIGHT);
            }

            // Start splash animation
            if (player == GameServices.camera().getFocusedSprite()) {
                splashAnimFrameP1 = 0;
                splashAnimTimerP1 = SPLASH_ANIM_DELAY;
            } else {
                splashAnimFrameP2 = 0;
                splashAnimTimerP2 = SPLASH_ANIM_DELAY;
            }

            // Mark player as skimming (prevents water entry)
            player.setWaterSkimActive(true);
            return true;
        }

        // === Sustain / exit logic (sonic3k.asm:75424-75491) ===

        // Check jump exit (A/B/C pressed)
        // ROM: andi.w #button_A_mask|button_B_mask|button_C_mask,d0
        if (player.isJumpPressed()) {
            return exitWithJump(player);
        }

        // Calculate pin position BEFORE checking/applying it
        // ROM: d0 = Water_level - y_radius - 1 (sonic3k.asm:75428-75432)
        int pinnedY = waterLevel - player.getYRadius() - 1;

        // ROM: cmp.w y_pos(a1),d0 / bhi.s loc_38646 (sonic3k.asm:75433-75434)
        // Exit skim if terrain has pushed the player ABOVE the water pin position.
        // In Y-down coordinates, pinnedY > centreY means the pin is below the player,
        // i.e. terrain raised the player above the water surface (e.g. running into a curve).
        // Using unsigned comparison to match ROM's bhi (branch if higher, unsigned).
        if (Integer.compareUnsigned(pinnedY, player.getCentreY()) > 0) {
            return exitBySpeedLoss(player);
        }

        // Check speed threshold — exit if too slow
        // ROM: cmpi.w #$700,d1 / blo.s loc_38646 (sonic3k.asm:75440-75441)
        int absXSpeed = Math.abs(player.getXSpeed());
        if (absXSpeed < SPEED_THRESHOLD) {
            return exitBySpeedLoss(player);
        }

        // NOW pin player Y to water surface (only if still skimming)
        // ROM: move.w d0,y_pos(a1) / move.w #0,y_vel(a1) (sonic3k.asm:75442-75443)
        player.setCentreY((short) pinnedY);
        player.setYSpeed((short) 0);

        // Apply friction when airborne and no directional input
        // ROM: btst #Status_InAir,status(a1) / andi.w #(left|right)<<8,d5
        if (player.getAir() && !player.isLeftPressed() && !player.isRightPressed()) {
            applySkimFriction(player);
            // ROM: move.w x_vel(a1),d0 / beq.s loc_38646 (sonic3k.asm:75452)
            // If friction reduced x_vel to zero, exit skim immediately
            if (player.getXSpeed() == 0) {
                return exitBySpeedLoss(player);
            }
        }

        // Play SFX periodically
        // ROM: move.b (Level_frame_counter+1).w,d0 / addq.b #2,d0 / andi.b #$F,d0
        if (((frameCounter + 2) & SFX_INTERVAL_MASK) == 0) {
            GameServices.audio().playSfx(Sonic3kSfx.WATER_SKID.id);
        }

        // Advance splash animation
        boolean isP1 = (player == GameServices.camera().getFocusedSprite());
        if (isP1) {
            advanceSplashAnim(true);
        } else {
            advanceSplashAnim(false);
        }

        return true;
    }

    /**
     * Apply friction to horizontal velocity while skimming.
     * ROM: sub.w/add.w d1 ($C) to x_vel, clamped to zero (sonic3k.asm:75450-75470).
     */
    private static void applySkimFriction(AbstractPlayableSprite player) {
        short xSpeed = player.getXSpeed();
        if (xSpeed == 0) return;

        if (xSpeed > 0) {
            xSpeed = (short) Math.max(0, xSpeed - SKIM_FRICTION);
        } else {
            xSpeed = (short) Math.min(0, xSpeed + SKIM_FRICTION);
        }
        player.setXSpeed(xSpeed);
    }

    /**
     * Exit skim by jumping (A/B/C pressed).
     * ROM: loc_38652 (sonic3k.asm:75481-75491).
     */
    private static boolean exitWithJump(AbstractPlayableSprite player) {
        player.setYSpeed(JUMP_EXIT_Y_VEL);
        player.setAir(true);
        player.setJumping(true);
        // ROM: y_radius=$0E, x_radius=$07 — standard rolling radii
        player.applyRollingRadii(false);
        player.setAnimationId(Sonic3kAnimationIds.ROLL);
        player.setRolling(true);

        boolean isP1 = (player == GameServices.camera().getFocusedSprite());
        if (isP1) {
            splashAnimFrameP1 = SPLASH_EXIT_FRAME;
        } else {
            splashAnimFrameP2 = SPLASH_EXIT_FRAME;
        }

        player.setWaterSkimActive(false);
        return false;
    }

    /**
     * Exit skim by speed dropping below threshold.
     * ROM: loc_38646 (sonic3k.asm:75473-75476).
     */
    private static boolean exitBySpeedLoss(AbstractPlayableSprite player) {
        boolean isP1 = (player == GameServices.camera().getFocusedSprite());
        if (isP1) {
            splashAnimFrameP1 = SPLASH_EXIT_FRAME;
        } else {
            splashAnimFrameP2 = SPLASH_EXIT_FRAME;
        }

        player.setWaterSkimActive(false);
        return false;
    }

    /**
     * Advance the splash animation timer and frame.
     * ROM: loc_384DA (sonic3k.asm:75328-75334) — 3 frames per step, cycles 0-4.
     */
    private static void advanceSplashAnim(boolean isP1) {
        if (isP1) {
            splashAnimTimerP1--;
            if (splashAnimTimerP1 < 0) {
                splashAnimTimerP1 = SPLASH_ANIM_DELAY - 1;
                splashAnimFrameP1 = (splashAnimFrameP1 + 1) % SPLASH_ANIM_FRAMES;
            }
        } else {
            splashAnimTimerP2--;
            if (splashAnimTimerP2 < 0) {
                splashAnimTimerP2 = SPLASH_ANIM_DELAY - 1;
                splashAnimFrameP2 = (splashAnimFrameP2 + 1) % SPLASH_ANIM_FRAMES;
            }
        }
    }

    // ===== Rendering =====

    /**
     * Cache splash patterns in the graphics manager's pattern atlas.
     */
    public static int ensurePatternsCached(GraphicsManager gfx, int baseIndex) {
        if (splashRenderer != null) {
            splashRenderer.ensurePatternsCached(gfx, baseIndex);
            artLoaded = true;
            return baseIndex + (Sonic3kConstants.ART_UNC_HCZ_WATER_SPLASH2_SIZE / Pattern.PATTERN_SIZE_IN_ROM);
        }
        return baseIndex;
    }

    /**
     * Render splash sprites for active skim players.
     * ROM: loc_384B2 (sonic3k.asm:75314-75357) — splash follows player X at water level.
     */
    public static void render(Camera camera) {
        if (splashRenderer == null || !artLoaded) return;

        int waterLevel = getWaterLevel();
        if (waterLevel == 0) return;

        // P1 splash
        if (skimActiveP1 && splashAnimFrameP1 < SPLASH_EXIT_FRAME) {
            AbstractPlayableSprite p1 = GameServices.camera().getFocusedSprite();
            if (p1 != null) {
                boolean hFlip = p1.getDirection() == Direction.LEFT;
                splashRenderer.drawFrameIndex(splashAnimFrameP1,
                        p1.getCentreX(), waterLevel, hFlip, false);
            }
        }

        // P2 splash
        if (skimActiveP2 && splashAnimFrameP2 < SPLASH_EXIT_FRAME) {
            List<? extends AbstractPlayableSprite> sidekicks = GameServices.sprites().getSidekicks();
            if (sidekicks != null && !sidekicks.isEmpty()) {
                AbstractPlayableSprite p2 = sidekicks.getFirst();
                if (p2 != null) {
                    boolean hFlip = p2.getDirection() == Direction.LEFT;
                    splashRenderer.drawFrameIndex(splashAnimFrameP2,
                            p2.getCentreX(), waterLevel, hFlip, false);
                }
            }
        }
    }

    /** Returns true if P1 is currently skimming across water. */
    public static boolean isSkimActiveP1() {
        return skimActiveP1;
    }

    /** Returns true if P2 is currently skimming across water. */
    public static boolean isSkimActiveP2() {
        return skimActiveP2;
    }

    public static void reset() {
        skimActiveP1 = false;
        skimActiveP2 = false;
        splashAnimFrameP1 = 0;
        splashAnimFrameP2 = 0;
        splashAnimTimerP1 = 0;
        splashAnimTimerP2 = 0;
        splashRenderer = null;
        artLoaded = false;
        actId = 0;
        frameCounter = 0;
    }

    // ===== Art loading =====

    /**
     * Load ArtUnc_HCZWaterSplash2 from ROM — uncompressed, 1920 bytes (60 tiles).
     * 5 animation frames × 12 tiles per frame.
     */
    private static Pattern[] loadSplashPatterns(Rom rom) throws IOException {
        byte[] data = new byte[Sonic3kConstants.ART_UNC_HCZ_WATER_SPLASH2_SIZE];
        synchronized (rom) {
            rom.getFileChannel().position(Sonic3kConstants.ART_UNC_HCZ_WATER_SPLASH2_ADDR);
            rom.getFileChannel().read(java.nio.ByteBuffer.wrap(data));
        }
        int count = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        Pattern[] patterns = new Pattern[count];
        for (int i = 0; i < count; i++) {
            patterns[i] = new Pattern();
            patterns[i].fromSegaFormat(Arrays.copyOfRange(data,
                    i * Pattern.PATTERN_SIZE_IN_ROM,
                    (i + 1) * Pattern.PATTERN_SIZE_IN_ROM));
        }
        return patterns;
    }

    /**
     * Build mapping frames from Map_HCZWaterSplash2 assembly data.
     * <p>
     * From the disassembly (Map - Water Splash 2.asm):
     * <pre>
     * Frame 0-4: all point to Frame_237C86 (2 pieces):
     *   piece 0: y=$F0(-16), size=$0D(4w×2h), tile=$0000, x=$FFC8(-56)
     *   piece 1: y=$F0(-16), size=$05(2w×2h), tile=$0008, x=$FFE8(-24)
     * Frame 5: points to Frame_247C78 from Map_HCZWaterSplash (0 pieces = empty)
     * </pre>
     */
    private static List<SpriteMappingFrame> buildSplashMappings() {
        // All 5 active frames share the same mapping layout (different art via DMA in ROM;
        // we pre-load all 5 frames' art and offset tile indices per frame).
        // Frame 5 = exit frame (empty).

        // Each frame uses 12 tiles of art data. Frame N starts at tile N*12.
        java.util.List<SpriteMappingFrame> frames = new java.util.ArrayList<>();
        for (int f = 0; f < SPLASH_ANIM_FRAMES; f++) {
            int tileBase = f * 12;
            List<SpriteMappingPiece> pieces = List.of(
                    // Piece 0: size $0D = 4 wide × 2 tall (8 tiles), at (-56, -16)
                    new SpriteMappingPiece(-56, -16, 4, 2, tileBase, false, false, 0, false),
                    // Piece 1: size $05 = 2 wide × 2 tall (4 tiles), at (-24, -16)
                    // Tile $08 in original is relative to DMA base; we offset per frame.
                    new SpriteMappingPiece(-24, -16, 2, 2, tileBase + 8, false, false, 0, false)
            );
            frames.add(new SpriteMappingFrame(pieces));
        }
        // Frame 5: empty (exit splash — no sprites drawn)
        frames.add(new SpriteMappingFrame(List.of()));

        return frames;
    }

    private static int getWaterLevel() {
        WaterSystem ws = GameServices.water();
        return ws != null ? ws.getWaterLevelY(
                com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_HCZ, actId) : 0;
    }
}
