package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0xEB - Gumball Item (Sonic 3 &amp; Knuckles Gumball / Pachinko bonus stage).
 * <p>
 * ROM reference: sonic3k.asm Obj_GumballItem (line 96814).
 * <p>
 * Ejected from the gumball machine dispenser. Physics: MoveSprite2 (subpixel motion)
 * with gravity deceleration of -4 per frame on Y velocity (upward deceleration, causing
 * the item to slow, stop, then fall). Collision flags 0xD7 in ROM; the engine uses
 * SPECIAL category (0x40) so the touch listener handles the response without
 * applying boss/hurt logic.
 * <p>
 * Subtypes determine reward on player contact:
 * <ul>
 *   <li>0: Normal gumball — plays sfx_SmallBumpers, deletes self</li>
 *   <li>1: Small bumper — plays sfx_SmallBumpers, deletes self</li>
 *   <li>2: Ring item — awards 10 rings to HUD + 20 to saved count, plays sfx_SmallBumpers</li>
 *   <li>3: Bonus item — ring reward from position-based table (byte_1E44C4)</li>
 *   <li>4: Ring reward — same collision_flags, bounce player away</li>
 * </ul>
 * <p>
 * ROM art: subtype 0 uses Map_PachinkoFItem (pachinko art), subtypes 1-8 use
 * Map_GumballBonus with mapping_frame = subtype + 7.
 * <p>
 * ROM collision size: 0xD7 &amp; 0x3F = 0x17 (size index 23).
 */
public class GumballItemObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener {

    private static final Logger LOGGER = Logger.getLogger(GumballItemObjectInstance.class.getName());

    // When ejected from the gumball machine, items use ball physics (ROM loc_60ECE):
    //   addi.w #$10,d0  — gravity +$10/frame (downward)
    //   cmpi.w #$200,d0 / bhi.s — terminal velocity $200
    // Note: Obj_GumballItem (Pachinko orb variant) uses subi #4 (anti-gravity float),
    // but the machine-ejected balls in the Gumball stage use standard gravity.
    private static final int Y_GRAVITY = 0x10;
    private static final int Y_TERMINAL_VELOCITY = 0x200;

    // ROM: collision_flags 0xD7 → size index 0x17 (23)
    // Engine uses SPECIAL category (0x40) so the listener handles response,
    // matching ROM behavior where $C0+ objects use collision_property polling.
    private static final int COLLISION_FLAGS = 0x40 | 0x17;

    // ROM: ObjDat3_613E0 priority $0100 → bucket 1
    private static final int PRIORITY_BUCKET = 1;

    // ROM: loc_6114E — ring item awards 10 rings to HUD and 20 to saved count
    private static final int RING_ITEM_HUD_AWARD = 10;
    private static final int RING_ITEM_SAVED_AWARD = 20;

    // ROM: byte_1E44C4 — ring reward table indexed by (y_pos & 0xF)
    private static final int[] RING_REWARD_TABLE = {
            0x50, 0x32, 0x28, 0x23, 0x23, 0x1E, 0x1E, 0x14,
            0x14, 0x0A, 0x0A, 0x0A, 0x0A, 0x05, 0x05, 0x05
    };

    /** Subpixel motion state for MoveSprite2 + gravity deceleration. */
    private final SubpixelMotion.State motionState;

    /** Whether this item uses the moving path (ejected from gumball machine). */
    private final boolean moving;

    /** Subtype determining reward behavior. */
    private final int subtype;

    /** Mapping frame for rendering. */
    private final int mappingFrame;

    /** Whether this item uses GumballBonus mappings (true) or PachinkoFItem (false). */
    private final boolean useGumballMappings;

    /** Set true when player touches this item; triggers deletion next frame. */
    private boolean collected;

    /**
     * Constructs a gumball item.
     *
     * @param spawn the object spawn data
     */
    public GumballItemObjectInstance(ObjectSpawn spawn) {
        this(spawn, 0, false);
    }

    /**
     * Constructs a gumball item with initial Y velocity (for ejection from gumball machine).
     *
     * @param spawn      the object spawn data
     * @param initialYVel initial Y velocity in subpixels (negative = upward)
     * @param moving      true if this item uses the moving path (MoveSprite2 + gravity)
     */
    public GumballItemObjectInstance(ObjectSpawn spawn, int initialYVel, boolean moving) {
        super(spawn, "GumballItem");
        this.moving = moving;
        this.subtype = spawn.subtype() & 0xFF;

        // ROM: Obj_GumballItem init (line 96817-96826)
        // subtype == 0 → Map_PachinkoFItem, frame 0
        // subtype != 0 → Map_GumballBonus, frame = subtype + 7
        if (subtype == 0) {
            this.useGumballMappings = false;
            this.mappingFrame = 0;
        } else {
            this.useGumballMappings = true;
            this.mappingFrame = subtype + 7;
        }

        this.motionState = new SubpixelMotion.State(
                spawn.x(), spawn.y(), 0, 0, 0, initialYVel);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (collected) {
            setDestroyed(true);
            return;
        }

        if (moving) {
            // ROM: loc_60ECE — apply gravity then MoveSprite2, cap at terminal velocity
            motionState.yVel += Y_GRAVITY;
            if (motionState.yVel > Y_TERMINAL_VELOCITY) {
                motionState.yVel = Y_TERMINAL_VELOCITY;
            }
            SubpixelMotion.moveSprite2(motionState);
            updateDynamicSpawn(motionState.x, motionState.y);
        }

        // ROM: loc_4A31A — bottom-only despawn.
        //   move.w  (Camera_Y_pos).w,d0
        //   addi.w  #$240,d0
        //   cmp.w   y_pos(a0),d0
        //   bcs.s   DeleteObject
        // Items only despawn when they fall off the BOTTOM of the screen
        // (y_pos > Camera_Y_pos + $240), not when scrolled off any edge.
        try {
            Camera camera = services().camera();
            int cameraY = camera.getY();
            if (motionState.y > cameraY + 0x240) {
                setDestroyed(true);
            }
        } catch (Exception e) {
            // Camera unavailable (test env): skip despawn check
        }
    }

    @Override
    public int getX() {
        return motionState.x;
    }

    @Override
    public int getY() {
        return motionState.y;
    }

    @Override
    public ObjectSpawn getSpawn() {
        if (moving) {
            return buildSpawnAt(motionState.x, motionState.y);
        }
        return super.getSpawn();
    }

    // --- TouchResponseProvider ---

    @Override
    public int getCollisionFlags() {
        if (collected) {
            return 0; // No collision after collection
        }
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public boolean requiresContinuousTouchCallbacks() {
        // Edge-triggered is fine; item deletes on first touch
        return false;
    }

    // --- TouchResponseListener ---

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        if (collected) {
            return;
        }

        // ROM: Machine-ejected balls use sub_610E0 → loc_61100 dispatch with
        // d1 = subtype DIRECTLY (NOT subtype-1 like sub_4A384 does for Pachinko orbs).
        // Deletion: loc_60F28 deletes the ball UNLESS the handler set d2=0.
        // Only loc_6115C (push, subtype 4) sets d2=0 → ball survives.
        boolean shouldDelete = true;

        switch (subtype) {
            case 0 -> onCollectExtraLife(player);         // loc_61120: +1 life
            case 1 -> onCollectRepairDispenser(player);   // loc_61130: REP (respawn dispenser + springs)
            case 2 -> onCollectRingReward(player);        // loc_6114E: +20 saved, +10 HUD
            case 3 -> { /* locret_6114C: nothing (silent delete) */ }
            case 4 -> { onCollectPush(player); shouldDelete = false; }  // loc_6115C: push, d2=0 → NOT deleted
            case 5 -> onCollectFireShield(player);        // loc_611D6
            case 6 -> onCollectBubbleShield(player);      // loc_61200
            case 7 -> onCollectLightningShield(player);   // loc_6122A
            default -> {
                LOGGER.fine("GumballItem: unhandled subtype " + subtype);
            }
        }

        if (shouldDelete) {
            collected = true;
            // ROM loc_60F28: jmp Delete_Current_Sprite + sfx_SmallBumpers
            playSfx(Sonic3kSfx.SMALL_BUMPERS);
        }
    }

    /**
     * ROM: subtype 0 → off_6110E[0] = loc_61120 — extra life.
     * Grants +1 life and plays mus_ExtraLife.
     */
    private void onCollectExtraLife(PlayableEntity player) {
        try {
            services().gameState().addLife();
        } catch (Exception e) {
            // safe fallback for test env
        }
        try {
            services().playMusic(com.openggf.game.sonic3k.audio.Sonic3kMusic.EXTRA_LIFE.id);
        } catch (Exception e) {
            // safe fallback
        }
    }

    /**
     * ROM: subtype 1 → off_6110E[1] = loc_61130 — REP (respawn dispenser).
     * Checks $FF2022 guard, allocates a new dispenser object, sets $FF2022.
     * In the engine, this respawns the dispenser + springs via the machine.
     */
    private void onCollectRepairDispenser(PlayableEntity player) {
        GumballMachineObjectInstance current = GumballMachineObjectInstance.current();
        if (current != null) {
            current.respawnSprings();
        }
    }

    /**
     * ROM: subtype 3 — position-based ring reward (loc_4A3B6).
     * Ring count from byte_1E44C4 indexed by (y_pos &amp; 0xF).
     */
    /**
     * ROM: subtype 2 → off_6110E[2] = loc_6114E — ring award.
     * addi.w #20,(Saved_ring_count).w / moveq #10,d0 / jmp (AddRings).l
     */
    private void onCollectRingReward(PlayableEntity player) {
        awardRingsToCoordinator(RING_ITEM_SAVED_AWARD);  // +20 to saved
        awardRingsToHud(player, RING_ITEM_HUD_AWARD);    // +10 to HUD
    }

    /**
     * ROM: subtype 4 — locret_6114C (does nothing). Silent delete, no SFX, no reward.
     */
    private void onCollectSilent(PlayableEntity player) {
        // No sound, no reward. Just get deleted (collected=true above).
    }

    /**
     * ROM: subtype 5 — loc_6115C (push handler).
     * Uses arctan/sine/cosine to push player away from the item. No SFX.
     */
    private void onCollectPush(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }
        // Simplified push: nudge player X away from the item (ROM uses sin/cos lookup).
        int dx = sprite.getCentreX() - motionState.x;
        int dir = dx >= 0 ? 1 : -1;
        try {
            sprite.setXSpeed((short) (0x200 * dir));
        } catch (Exception e) {
            // safe fallback
        }
        // ROM: no sound played directly by subtype 5
    }

    /**
     * ROM: subtype 6 — loc_611D6 grants FireShield + plays sfx_FireShield.
     */
    private void onCollectFireShield(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            try {
                sprite.giveShield(com.openggf.game.ShieldType.FIRE);
            } catch (Exception e) {
                // safe fallback
            }
            try {
                services().setBonusStageShield(com.openggf.game.ShieldType.FIRE);
            } catch (Exception e) {
                // ignore
            }
        }
        playSfx(Sonic3kSfx.FIRE_SHIELD);
    }

    /**
     * ROM: subtype 7 — loc_61200 grants BubbleShield + plays sfx_BubbleShield.
     */
    private void onCollectBubbleShield(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            try {
                sprite.giveShield(com.openggf.game.ShieldType.BUBBLE);
            } catch (Exception e) {
                // safe fallback
            }
            try {
                services().setBonusStageShield(com.openggf.game.ShieldType.BUBBLE);
            } catch (Exception e) {
                // ignore
            }
        }
        playSfx(Sonic3kSfx.BUBBLE_SHIELD);
    }

    /**
     * ROM: subtype 8 — loc_6122A grants LightningShield + plays sfx_LightningShield.
     */
    private void onCollectLightningShield(PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            try {
                sprite.giveShield(com.openggf.game.ShieldType.LIGHTNING);
            } catch (Exception e) {
                // safe fallback
            }
            try {
                services().setBonusStageShield(com.openggf.game.ShieldType.LIGHTNING);
            } catch (Exception e) {
                // ignore
            }
        }
        playSfx(Sonic3kSfx.LIGHTNING_SHIELD);
    }

    // --- Ring Award Helpers ---

    /**
     * Awards rings to the player's HUD ring counter via AbstractPlayableSprite.addRings().
     * ROM equivalent: jmp (AddRings).l — updates Ring_count and Total_ring_count.
     */
    private void awardRingsToHud(PlayableEntity player, int count) {
        if (player instanceof AbstractPlayableSprite sprite) {
            sprite.addRings(count);
        }
    }

    /**
     * Awards rings to the bonus stage coordinator's saved ring count.
     * ROM equivalent: add.w d0,(Saved_ring_count).w
     */
    private void awardRingsToCoordinator(int count) {
        try {
            services().addBonusStageRings(count);
        } catch (Exception e) {
            // Safe fallback for test environments
        }
    }

    /**
     * ROM: moveq #signextendB(sfx),d0 / jsr (Play_SFX).l
     * Plays the specified SFX via the audio services.
     */
    private void playSfx(Sonic3kSfx sfx) {
        try {
            services().playSfx(sfx.id);
        } catch (Exception e) {
            // Prevent audio failure from breaking game logic
        }
    }

    // --- Rendering ---

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY_BUCKET);
    }

    @Override
    public boolean isHighPriority() {
        // ROM: ObjDat3_613E0 make_art_tile(ArtTile_BonusStage, 0, 1) — VDP priority 1
        // Balls render in front of high-priority FG tiles (machine body chunks).
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // ROM: subtype 0 uses Map_PachinkoFItem, others use Map_GumballBonus
        // Both share the same art key in the engine (loaded together for the bonus stage)
        String artKey = useGumballMappings
                ? Sonic3kObjectArtKeys.GUMBALL_BONUS
                : Sonic3kObjectArtKeys.GUMBALL_BONUS; // TODO: add PACHINKO_F_ITEM art key when pachinko is implemented
        PatternSpriteRenderer renderer = getRenderer(artKey);
        if (renderer == null) {
            return;
        }

        // ROM: ObjDat3_613E0 uses make_art_tile(ArtTile_BonusStage, 0, 1) — palette 0
        // (sheet default is palette 1 for the main machine/bumpers).
        renderer.drawFrameIndex(mappingFrame, motionState.x, motionState.y, false, false, 0);
    }
}
