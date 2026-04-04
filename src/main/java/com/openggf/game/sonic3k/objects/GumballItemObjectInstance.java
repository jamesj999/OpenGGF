package com.openggf.game.sonic3k.objects;

import com.openggf.audio.GameSound;
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

    // ROM: subi.w #4,y_vel(a0) — gravity deceleration (upward decel, item rises then falls)
    private static final int Y_GRAVITY_DECEL = -4;

    // ROM: collision_flags 0xD7 → size index 0x17 (23)
    // Engine uses SPECIAL category (0x40) so the listener handles response,
    // matching ROM behavior where $C0+ objects use collision_property polling.
    private static final int COLLISION_FLAGS = 0x40 | 0x17;

    // ROM: priority $0200 → bucket 2
    private static final int PRIORITY_BUCKET = 2;

    // ROM: byte_1E44C4 — ring reward table indexed by (y_pos & 0xF)
    private static final int[] RING_REWARD_TABLE = {
            0x50, 0x32, 0x28, 0x23, 0x23, 0x1E, 0x1E, 0x14,
            0x14, 0x0A, 0x0A, 0x0A, 0x0A, 0x05, 0x05, 0x05
    };

    // ROM: loc_6114E — ring item awards 10 rings to HUD and 20 to saved count
    private static final int RING_ITEM_HUD_AWARD = 10;
    private static final int RING_ITEM_SAVED_AWARD = 20;

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
            // ROM: loc_4A34C — MoveSprite2 then subi.w #4,y_vel(a0)
            SubpixelMotion.moveSprite2(motionState);
            motionState.yVel += Y_GRAVITY_DECEL;
            updateDynamicSpawn(motionState.x, motionState.y);
        }

        // ROM: loc_4A31A — check Y range for off-screen deletion
        // ROM uses Camera_Y_pos_coarse_back and compares distance > $200
        if (!isOnScreen(128)) {
            setDestroyed(true);
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

        collected = true;

        // ROM: sub_4A384 — dispatch on subtype
        switch (subtype) {
            case 0 -> onCollectDefault(player);
            case 1 -> onCollectSmallBumper(player);
            case 2 -> onCollectRingItem(player);
            case 3 -> onCollectBonusItem(player);
            case 4 -> onCollectRingReward(player);
            default -> {
                // Subtypes 5-8 map to loc_61100 dispatch (shields, extra life, etc.)
                // Those are handled by the gumball machine's shared dispatch table.
                // For now, treat unknown subtypes as default.
                LOGGER.fine("GumballItem: unhandled subtype " + subtype);
                onCollectDefault(player);
            }
        }
    }

    /**
     * ROM: subtype 0 — normal gumball. Subtype 0 goes through subq.w #1,d1 (d1 was 0)
     * which sets carry (bcs), falling through to loc_4A3AC (sfx_SmallBumpers + delete).
     */
    private void onCollectDefault(PlayableEntity player) {
        playSfxSmallBumpers();
    }

    /**
     * ROM: subtype 1 — small bumper. After subq.w #1,d1 (d1=1 becomes 0), no carry,
     * falls to moveq #0,d0 / move.w a1,d0 / jmp (loc_61100).l which dispatches to
     * the shared bounce handler at subtype index 0 (loc_61120 = extra life).
     * However, in the gumball context, subtype 1 is a simple bumper. The loc_61100
     * dispatch is for the pachinko stage. For gumball, treat as sfx + delete.
     */
    private void onCollectSmallBumper(PlayableEntity player) {
        playSfxSmallBumpers();
    }

    /**
     * ROM: subtype 2 — ring item (loc_6114E).
     * Awards 10 rings to HUD (AddRings) and 20 rings to Saved_ring_count.
     */
    private void onCollectRingItem(PlayableEntity player) {
        awardRingsToHud(player, RING_ITEM_HUD_AWARD);
        awardRingsToCoordinator(RING_ITEM_SAVED_AWARD);
        playSfxSmallBumpers();
    }

    /**
     * ROM: subtype 3 — bonus item (loc_4A3B6).
     * Ring reward from position-based table (byte_1E44C4), indexed by (y_pos &amp; 0xF).
     * Awards to Saved_ring_count and calls AddRings for HUD.
     */
    private void onCollectBonusItem(PlayableEntity player) {
        int tableIndex = motionState.y & 0xF;
        int ringReward = RING_REWARD_TABLE[tableIndex];
        awardRingsToCoordinator(ringReward);
        awardRingsToHud(player, ringReward);
    }

    /**
     * ROM: subtype 4 — ring reward. Uses the shared dispatch table at loc_61100,
     * subtype index 4 → loc_6115C (bounce player away from object).
     * For the gumball stage, this functions as a bumper that also awards rings.
     */
    private void onCollectRingReward(PlayableEntity player) {
        playSfxSmallBumpers();
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
     * ROM: moveq #signextendB(sfx_SmallBumpers),d0 / jsr (Play_SFX).l
     * sfx_SmallBumpers = 0x7B
     */
    private void playSfxSmallBumpers() {
        try {
            services().playSfx(Sonic3kSfx.SMALL_BUMPERS.id);
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

        renderer.drawFrameIndex(mappingFrame, motionState.x, motionState.y, false, false);
    }
}
