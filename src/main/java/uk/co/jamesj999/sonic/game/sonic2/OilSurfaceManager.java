package uk.co.jamesj999.sonic.game.sonic2;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.audio.GameSound;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AnimationIds;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.physics.Direction;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

/**
 * Manages OOZ oil surface collision and oil slides.
 * <p>
 * ROM equivalent: Obj07 (s2.asm:49655-49749) for oil surface,
 * OilSlides (s2.asm:5533-5650) for slide chunks.
 * <p>
 * Oil surface: invisible platform at Y=0x758. When Sonic lands on it,
 * a submersion counter decrements from 0x30 (48) each frame. At 0,
 * Sonic suffocates (instant death). Jumping off lets the counter recover.
 * <p>
 * Oil slides: 32 specific block IDs cause automatic acceleration with
 * direction-based speed targets.
 */
public class OilSurfaceManager {

    // Oil surface constants (ROM: Obj07_Init at s2.asm:49667-49672)
    private final int oilY = Sonic2Constants.OIL_SURFACE_Y;
    private final int submersionMax = Sonic2Constants.OIL_SUBMERSION_MAX;

    // Per-player submersion state
    private int submersion = Sonic2Constants.OIL_SUBMERSION_MAX;
    private boolean standingOnOil = false;

    // Internal frame counter for slide sound timing
    private int frameCounter = 0;

    // OilSlides_Chunks table (ROM: s2.asm:5647-5650)
    // 32 block IDs that trigger oil slide behavior
    private static final int[] OIL_CHUNKS = {
            0x2F, 0x30, 0x31, 0x33, 0x35, 0x38, 0x3A, 0x3C,
            0x63, 0x64, 0x83, 0x90, 0x91, 0x93, 0xA1, 0xA3,
            0xBD, 0xC7, 0xC8, 0xCE, 0xD7, 0xD8, 0xE6, 0xEB,
            0xEC, 0xED, 0xF1, 0xF2, 0xF3, 0xF4, 0xFA, 0xFD
    };

    // OilSlides_Speeds table (ROM: s2.asm:5642-5644)
    // Speed target for each corresponding chunk: -8, 0, or +8
    private static final int[] OIL_SPEEDS = {
            -8, -8, -8,  8,  8,  0,  0,  0, -8, -8,  0,  8,  8,  8,  0,  8,
             8,  8,  0, -8,  0,  0, -8,  8, -8, -8, -8,  8,  8,  8, -8, -8
    };

    /**
     * Called every frame while in OOZ.
     */
    public void update(AbstractPlayableSprite player) {
        frameCounter++;
        // ROM order: OilSlides is called from NonWaterEffects (character processing),
        // Obj07 runs during object processing (after character).
        updateOilSlides(player);
        updateOilSurface(player);
    }

    /**
     * Resets oil state (e.g. on level load or player respawn).
     */
    public void reset() {
        submersion = submersionMax;
        standingOnOil = false;
        frameCounter = 0;
    }

    // =========================================================================
    // Oil Surface (Obj07 equivalent)
    // ROM: s2.asm:49655-49749
    // =========================================================================

    private void updateOilSurface(AbstractPlayableSprite player) {
        if (player.getDead() || player.isDebugMode()) {
            clearOilSupport(player);
            return;
        }

        if (standingOnOil) {
            // Movement runs before this manager and can temporarily set air=true.
            // Only release support when the player is actually moving upward.
            if (shouldExitOilSupport(player)) {
                clearOilSupport(player);
                return;
            }

            // ROM: Obj07_CheckKillChar1 (s2.asm:49691-49694)
            if (submersion <= 0) {
                // Suffocate - instant death (ROM: JmpTo3_KillCharacter)
                clearOilSupport(player);
                player.applyDrownDeath();
                return;
            }

            // Sink 1 pixel per frame
            submersion--;

            // ROM: PlatformObject_SingleCharacter positions player at:
            // oilY - submersion - y_radius
            // As submersion decreases from 0x30 to 0, player sinks into the oil.
            int targetY = oilY - submersion - player.getYRadius();
            player.setAir(false);
            player.setOnObject(true);
            player.setCentreY((short) targetY);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
        } else {
            // Not on oil - recover submersion counter
            // ROM: Obj07_Main (s2.asm:49685-49688)
            if (submersion < submersionMax) {
                submersion++;
            }

            // Check if player should land on oil surface
            // ROM: PlatformObject_SingleCharacter does the landing check
            if (shouldLandOnOil(player)) {
                standingOnOil = true;
                player.setAir(false);
                player.setYSpeed((short) 0);
                player.setOnObject(true);

                // Position player at oil surface with current submersion offset
                int targetY = oilY - submersion - player.getYRadius();
                player.setCentreY((short) targetY);
            }
        }
    }

    /**
     * Check if the player should land on the oil surface.
     * ROM: PlatformObject logic - player must be falling (ySpeed > 0),
     * and their feet must be at or below the oil surface level.
     */
    private boolean shouldLandOnOil(AbstractPlayableSprite player) {
        // Must be in the air and falling
        if (!player.getAir() || player.getYSpeed() <= 0) {
            return false;
        }

        // Already on oil or dead
        if (player.getDead() || player.isOnObject()) {
            return false;
        }

        // Check if player's feet are at or past the oil surface
        // Player centre Y + yRadius = feet position
        int feetY = player.getCentreY() + player.getYRadius();
        int oilSurfaceY = oilY - submersion;

        // Previous frame feet position (approximate: current - ySpeed/256)
        // ROM checks that player was above and is now at/below
        int prevFeetY = feetY - (player.getYSpeed() >> 8);
        if (prevFeetY > oilSurfaceY) {
            // Was already below surface on previous frame - don't snap back up
            return false;
        }

        return feetY >= oilSurfaceY;
    }

    /**
     * End support only when the player is actively moving upward.
     */
    private boolean shouldExitOilSupport(AbstractPlayableSprite player) {
        return player.getYSpeed() < 0 || player.isJumping();
    }

    private void clearOilSupport(AbstractPlayableSprite player) {
        standingOnOil = false;
        player.setOnObject(false);
    }

    // =========================================================================
    // Oil Slides (OilSlides routine equivalent)
    // ROM: s2.asm:5533-5650
    // =========================================================================

    private void updateOilSlides(AbstractPlayableSprite player) {
        if (player.getDead() || player.isDebugMode()) {
            return;
        }

        // ROM: btst #status.player.in_air,status(a1) / bne.s +
        if (player.getAir()) {
            // In air - if was sliding, set move lock and clear flag
            if (player.isSliding()) {
                player.setSliding(false);
                setMoveLock(player, 5);
            }
            return;
        }

        // Look up block ID at player position
        // ROM: uses centre coordinates (x_pos, y_pos)
        LevelManager levelManager = LevelManager.getInstance();
        int blockId = levelManager.getBlockIdAt(player.getCentreX(), player.getCentreY());
        if (blockId < 0) {
            exitSlide(player);
            return;
        }

        // Search for matching chunk in the oil chunks table
        // ROM: searches OilSlides_Chunks backwards with dbeq loop
        int chunkIndex = findChunkIndex(blockId);
        if (chunkIndex < 0) {
            // Not on an oil slide chunk
            exitSlide(player);
            return;
        }

        // Found a match - apply oil slide physics
        int speed = OIL_SPEEDS[chunkIndex];
        if (speed != 0) {
            applyDirectionalSlide(player, speed);
        } else {
            applyFrictionSlide(player);
        }
    }

    /**
     * Search OIL_CHUNKS for a matching block ID.
     * @return index into OIL_CHUNKS/OIL_SPEEDS, or -1 if not found
     */
    private int findChunkIndex(int blockId) {
        for (int i = 0; i < OIL_CHUNKS.length; i++) {
            if (OIL_CHUNKS[i] == blockId) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Apply directional oil slide (speed != 0).
     * ROM: loc_4712 (s2.asm:5566-5596)
     * Accelerates inertia toward target speed at ±0x40/frame.
     */
    private void applyDirectionalSlide(AbstractPlayableSprite player, int targetSpeed) {
        short inertia = player.getGSpeed();
        int inertiaHigh = inertia >> 8; // ROM compares high byte of inertia

        // ROM: Accelerate toward target speed
        // Speed values are in pixels (-8 or +8), compared against inertia high byte
        if (targetSpeed < 0) {
            // ROM: cmp.b d0,d1 / ble.s ++ / subi.w #$40,inertia
            if (inertiaHigh > targetSpeed) {
                player.setGSpeed((short) (inertia - 0x40));
            }
        } else {
            // ROM: cmp.b d0,d1 / bge.s + / addi.w #$40,inertia
            if (inertiaHigh < targetSpeed) {
                player.setGSpeed((short) (inertia + 0x40));
            }
        }

        // ROM: Set facing direction based on inertia sign
        // bclr #status.player.x_flip / tst.b d1 / bpl.s + / bset #status.player.x_flip
        // Note: d1 still holds the original inertia high byte
        if (inertiaHigh < 0) {
            player.setDirection(Direction.LEFT);
        } else {
            player.setDirection(Direction.RIGHT);
        }

        // Set slide animation
        player.setAnimationId(Sonic2AnimationIds.SLIDE);
        player.setSliding(true);

        // ROM: Play oil slide sound every 32 frames
        // andi.b #$1F,d0 / bne.s + (d0 = Vint_runcount low byte)
        if ((frameCounter & 0x1F) == 0) {
            AudioManager.getInstance().playSfx(GameSound.OIL_SLIDE);
        }
    }

    /**
     * Apply friction oil slide (speed == 0).
     * ROM: loc_476E (s2.asm:5599-5638)
     * Applies friction ±4/frame toward 0, allows manual L/R input.
     */
    private void applyFrictionSlide(AbstractPlayableSprite player) {
        int friction = 4;
        int inertia = player.getGSpeed();

        // ROM: Process left input (s2.asm:5602-5609)
        // sub.w d1,d0 / tst.w d0 / bpl.s + / sub.w d1,d0
        // Extra friction only when result is negative (already moving left or crossed zero)
        if (player.isLeftPressed()) {
            player.setAnimationId(Sonic2AnimationIds.WALK);
            player.setDirection(Direction.LEFT);
            inertia -= friction;
            if (inertia < 0) {
                inertia -= friction;
            }
        }

        // ROM: Process right input (s2.asm:5611-5618)
        // add.w d1,d0 / tst.w d0 / bmi.s + / add.w d1,d0
        // Extra acceleration only when result is positive (already moving right or crossed zero)
        if (player.isRightPressed()) {
            player.setAnimationId(Sonic2AnimationIds.WALK);
            player.setDirection(Direction.RIGHT);
            inertia += friction;
            if (inertia >= 0) {
                inertia += friction;
            }
        }

        // ROM: Apply friction toward zero (s2.asm:5620-5635)
        // tst.w d0 / beq.s +++ / bmi.s ++ / sub.w d1,d0 / bhi.s + / move.w #0,d0 / wait anim
        if (inertia > 0) {
            inertia -= friction;
            if (inertia <= 0) {
                inertia = 0;
                player.setAnimationId(Sonic2AnimationIds.WAIT);
            }
        } else if (inertia < 0) {
            inertia += friction;
            if (inertia >= 0) {
                inertia = 0;
                player.setAnimationId(Sonic2AnimationIds.WAIT);
            }
        } else {
            player.setAnimationId(Sonic2AnimationIds.WAIT);
        }

        player.setGSpeed((short) inertia);
        player.setSliding(true);
    }

    /**
     * Exit oil slide state - set move lock and clear sliding flag.
     * ROM: s2.asm:5559-5563
     */
    private void exitSlide(AbstractPlayableSprite player) {
        if (player.isSliding()) {
            setMoveLock(player, 5);
            player.setSliding(false);
        }
    }

    private void setMoveLock(AbstractPlayableSprite player, int frames) {
        // ROM: move.w #5,move_lock(a1)
        // Only set if not already locked (don't override longer locks)
        if (player.getMoveLockTimer() <= 0) {
            player.setMoveLockTimer(frames);
        }
    }

    public boolean isStandingOnOil() {
        return standingOnOil;
    }

    public int getSubmersion() {
        return submersion;
    }
}
