package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x2A - Cork Floor (Sonic 3 &amp; Knuckles).
 * <p>
 * A breakable floor platform used in AIZ, CNZ, FBZ, ICZ, and LBZ.
 * When broken, it shatters into fragment pieces that fly off with velocity
 * determined by zone-specific tables.
 * <p>
 * Three behavioral modes selected by subtype and zone:
 * <ul>
 *   <li><b>Mode A (Break-from-below, subtype 0):</b> Saves player's y_vel before
 *       solid check. On bottom hit (player rising into floor from below), restores
 *       the saved y_vel so upward momentum carries player through, then breaks.
 *       Solid from above (player can stand on it). ROM: loc_2A618.
 *       The swapped d6 check (#4|8) catches bottom-hit bits, not top-landing.</li>
 *   <li><b>Mode B (Roll-to-break, subtype != 0):</b> Checks if standing player
 *       is rolling. If so, sets player rolling, y_vel = -0x300, airborne, breaks.
 *       Used in AIZ, LBZ, etc. ROM: loc_2A502.</li>
 *   <li><b>Mode C (ICZ plane-switch, subtype bit 4 clear):</b> Same as Mode B
 *       but also applies plane-switching collision bits to the player.
 *       ROM: loc_2A6D4, uses sub_1DDC6.</li>
 * </ul>
 * <p>
 * Break behavior (shared across modes):
 * <ol>
 *   <li>Increments mapping_frame from intact to broken frame</li>
 *   <li>Plays sfx_Collapse</li>
 *   <li>Spawns fragment children (one per piece of the broken mapping frame)</li>
 *   <li>Each fragment gets x_vel/y_vel from the zone's velocity table</li>
 *   <li>Fragments fall with gravity 0x18/frame, delete when offscreen</li>
 * </ol>
 * <p>
 * Uses SolidObjectFull (full 4-sided collision) with d1=halfWidth+0x0B,
 * d2=halfHeight, d3=halfHeight+1.
 * <p>
 * ROM references: Obj_CorkFloor (sonic3k.asm:58420), loc_2A502, loc_2A618,
 * loc_2A6D4, word_2A884, word_2A8B0, word_2A8E0.
 */
public class CorkFloorObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOG = Logger.getLogger(CorkFloorObjectInstance.class.getName());

    // Fragment gravity: addi.w #$18,y_vel(a0) (ROM: MoveSprite in fragment routine)
    private static final int FRAGMENT_GRAVITY = 0x18;

    // Priority: $280 = bucket 5 (ROM: move.w #$280,priority(a0))
    private static final int PRIORITY = 5;

    // Roll-to-break launch velocity: ROM move.w #-$300,y_vel(a1) (loc_2A578)
    private static final int ROLL_BREAK_LAUNCH_YVEL = -0x300;

    // ===== Fragment velocity tables from ROM =====

    /**
     * word_2A884 (FBZ, ICZ small - 4 pairs = 8 entries).
     * Each pair: {x_vel, y_vel} in subpixels.
     * Used for FBZ all subtypes and ICZ when subtype bit 4 is set.
     */
    private static final int[][] VEL_TABLE_SMALL = {
            {-0x200, -0x200}, {0x200, -0x200},
            {-0x100, -0x100}, {0x100, -0x100},
    };

    /**
     * word_2A8B0 (AIZ - 6 pairs = 12 entries).
     * Used for AIZ1, AIZ2, and ICZ default (subtype bit 4 clear).
     */
    private static final int[][] VEL_TABLE_MEDIUM = {
            {-0x100, -0x200}, {0x100, -0x200},
            {-0x0E0, -0x1C0}, {0x0E0, -0x1C0},
            {-0x0C0, -0x180}, {0x0C0, -0x180},
            {-0x0A0, -0x140}, {0x0A0, -0x140},
            {-0x080, -0x100}, {0x080, -0x100},
            {-0x060, -0x0C0}, {0x060, -0x0C0},
    };

    /**
     * word_2A8E0 (CNZ, LBZ - 16 pairs = 32 entries).
     */
    private static final int[][] VEL_TABLE_LARGE = {
            {-0x400, -0x400}, {-0x200, -0x400}, {0x200, -0x400}, {0x400, -0x400},
            {-0x3C0, -0x3C0}, {-0x1C0, -0x3C0}, {0x1C0, -0x3C0}, {0x3C0, -0x3C0},
            {-0x380, -0x380}, {-0x180, -0x380}, {0x180, -0x380}, {0x380, -0x380},
            {-0x340, -0x340}, {-0x140, -0x340}, {0x140, -0x340}, {0x340, -0x340},
    };

    // ===== Zone-specific configuration =====

    /**
     * Per-zone configuration record.
     *
     * @param artKey       art sheet key from {@link Sonic3kObjectArtKeys}
     * @param halfWidth    half-width for solid collision (d1 base, before +0x0B)
     * @param halfHeight   half-height for solid collision (d2)
     * @param velTable     fragment velocity table (pairs of {xVel, yVel})
     * @param iczPlaneMode true if ICZ plane-switching behavior is active
     */
    private record ZoneConfig(
            String artKey,
            int halfWidth,
            int halfHeight,
            int[][] velTable,
            boolean iczPlaneMode
    ) {}

    // ROM: sonic3k.asm:58420-58487 (Obj_CorkFloor zone table)
    // art_tile values: 1 = level art, or specific VRAM tile offsets for CNZ/FBZ
    // Collision dimensions from ROM: move.w #halfWidth,d1 / move.w #halfHeight,d2

    private static final ZoneConfig AIZ1_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_AIZ1,
            0x10, 0x28, VEL_TABLE_MEDIUM, false);

    private static final ZoneConfig AIZ2_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_AIZ2,
            0x10, 0x2C, VEL_TABLE_MEDIUM, false);

    private static final ZoneConfig CNZ_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_CNZ,
            0x20, 0x20, VEL_TABLE_LARGE, false);

    private static final ZoneConfig FBZ_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_FBZ,
            0x10, 0x10, VEL_TABLE_SMALL, false);

    // ICZ default (subtype bit 4 clear): Mode C plane-switch, halfHeight=0x24
    private static final ZoneConfig ICZ_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_ICZ,
            0x10, 0x24, VEL_TABLE_MEDIUM, true);

    // ICZ small variant (subtype bit 4 set): Mode B roll-to-break, halfHeight=0x10
    // ROM: loc_2A4AE sets height_pixels=$10, art_tile=ArtTile_ICZMisc1, uses word_2A884
    private static final ZoneConfig ICZ_SMALL_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_ICZ,
            0x10, 0x10, VEL_TABLE_SMALL, false);

    private static final ZoneConfig LBZ_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_LBZ,
            0x20, 0x20, VEL_TABLE_LARGE, false);

    // ===== Behavioral modes =====

    private enum Mode {
        /** Mode A: break-from-below (subtype 0). ROM: loc_2A618.
         *  Saves y_vel before solid check, restores on bottom hit, breaks floor.
         *  Solid from above (standing), breakable from below (spring). */
        BREAK_FROM_BELOW,
        /** Mode B: roll-to-break (subtype != 0, non-ICZ or ICZ with bit 4 set). ROM: loc_2A502 */
        ROLL_TO_BREAK,
        /** Mode C: ICZ plane-switch (ICZ with subtype bit 4 clear). ROM: loc_2A6D4 */
        ICZ_PLANE_SWITCH
    }

    // ===== Instance state =====

    private final ZoneConfig config;
    private final Mode mode;
    private final int subtype;
    private final boolean hFlip;

    /** Current mapping frame. 0 = intact, 1 = broken (for most zones).
     *  ICZ: even = intact, odd = broken (per subtype variant). */
    private int mappingFrame;

    private final int x;
    private final int y;

    /** ICZ velocity table override: when subtype bit 4 is set, use VEL_TABLE_SMALL instead. */
    private final int[][] effectiveVelTable;

    private boolean broken;

    // Solid contact tracking
    private boolean playerStanding;

    // Pre-contact velocity snapshot for Mode A (bounce)
    // Captured from ObjectManager's pre-contact snapshot, same pattern as AizLrzRockObjectInstance
    private int savedPreContactYSpeed;
    private boolean savedPreContactRolling;

    public CorkFloorObjectInstance(ObjectSpawn spawn) {
        super(spawn, "CorkFloor");
        this.x = spawn.x();
        this.y = spawn.y();
        this.subtype = spawn.subtype() & 0xFF;
        this.hFlip = (spawn.renderFlags() & 0x01) != 0;
        this.config = resolveConfig(subtype);

        // Determine behavioral mode
        if (subtype == 0) {
            this.mode = Mode.BREAK_FROM_BELOW;
        } else if (config.iczPlaneMode && (subtype & 0x10) == 0) {
            this.mode = Mode.ICZ_PLANE_SWITCH;
        } else {
            this.mode = Mode.ROLL_TO_BREAK;
        }

        // ICZ: subtype bits 0-3 select the initial mapping frame
        // ROM: move.b subtype(a0),d0 / andi.w #$F,d0 / move.b d0,mapping_frame(a0)
        if (config.iczPlaneMode) {
            this.mappingFrame = (subtype & 0x0F) * 2; // Even frames are intact
        } else {
            this.mappingFrame = 0;
        }

        // ICZ with subtype bit 4 set uses small velocity table
        // ROM: btst #4,subtype(a0) / bne.s [use word_2A884]
        if (config.iczPlaneMode && (subtype & 0x10) != 0) {
            this.effectiveVelTable = VEL_TABLE_SMALL;
        } else {
            this.effectiveVelTable = config.velTable;
        }
    }

    // ===== SolidObjectProvider (full 4-sided collision) =====

    @Override
    public SolidObjectParams getSolidParams() {
        // ROM: addi.w #$B,d1 before SolidObjectFull
        // d1 = halfWidth + 0x0B, d2 = halfHeight, d3 = halfHeight + 1
        return new SolidObjectParams(config.halfWidth + 0x0B, config.halfHeight, config.halfHeight + 1);
    }

    @Override
    public boolean isTopSolidOnly() {
        return false; // SolidObjectFull: full 4-sided collision
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        return !broken; // Only solid while intact
    }

    // ===== SolidObjectListener =====

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (player == null || broken) {
            return;
        }

        // Read pre-contact snapshot from ObjectManager (same pattern as AizLrzRockObjectInstance).
        // The ROM saves player velocity BEFORE the solid object call; our engine captures
        // this in SolidContacts.update() before resolving contacts.
        ObjectManager om = getObjectManager();
        if (om != null) {
            savedPreContactYSpeed = om.getPreContactYSpeed();
            savedPreContactRolling = om.getPreContactRolling();
        }

        if (contact.standing()) {
            playerStanding = true;
        }

        // Mode A (Break-from-below): ROM checks swapped d6 bits 2/3 (bottom hit flags).
        // SolidObjectFull sets bit (d6+$F) on bottom collision, which after swap gives
        // bits 2/3 — matching the #4|8 check. Top landing sets bit (d6+$11) = bits 4/5,
        // which does NOT match. So this mode only breaks on impact from below.
        // ROM (loc_2A618): saves y_vel before solid check, restores on bottom hit,
        // so the player's upward momentum carries them through the broken floor.
        if (contact.touchBottom() && mode == Mode.BREAK_FROM_BELOW) {
            player.setYSpeed((short) savedPreContactYSpeed);
            player.setAir(true);
            player.setOnObject(false);
            performBreak(player);
        }
    }

    // ===== Update =====

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (broken) {
            return; // Already broken, nothing to do
        }

        // Mode A (Break-from-below) handled entirely in onSolidContact above.
        // Mode B (Roll-to-break) and Mode C (ICZ plane-switch) check rolling here.
        if (mode == Mode.BREAK_FROM_BELOW) {
            // Reset per-frame contact flag
            playerStanding = false;
            return;
        }

        // Mode B / Mode C: check if standing player is rolling
        if (playerStanding && player != null) {
            // ROM (loc_2A502/loc_2A6D4): checks anim == 2 (rolling animation).
            // Use pre-contact rolling state since landing clears rolling via
            // clearRollingOnLanding in the engine's contact resolution.
            if (savedPreContactRolling) {
                // Mode C (ICZ plane-switch): apply plane-switching collision bits
                // ROM (loc_2A6D4): uses sub_1DDC6 which sets top_solid_bit and lrb_solid_bit
                // based on subtype bits. Bit 4 of subtype selects secondary path (0x0E/0x0F).
                if (mode == Mode.ICZ_PLANE_SWITCH) {
                    applyPlaneSwitch(player);
                }

                // ROM (loc_2A578 / sub_1FBAE equivalent):
                // Set player rolling, y_vel = -0x300, set airborne, detach from object
                player.setRolling(true);
                player.setYSpeed((short) ROLL_BREAK_LAUNCH_YVEL);
                player.setAir(true);
                player.setOnObject(false);

                performBreak(player);
                playerStanding = false;
                return;
            }
        }

        // Reset per-frame contact flag
        playerStanding = false;
    }

    /**
     * Applies ICZ plane-switching collision bits to the player.
     * ROM (loc_2A6D4): sub_1DDC6 uses render_flags bit 0 (x_flip) to determine
     * which collision path to assign. For ICZ cork floors, this switches the
     * player between primary and secondary collision paths.
     * <p>
     * Primary path: top_solid_bit = 0x0C, lrb_solid_bit = 0x0D
     * Secondary path: top_solid_bit = 0x0E, lrb_solid_bit = 0x0F
     */
    private void applyPlaneSwitch(AbstractPlayableSprite player) {
        // ROM: tst.b subtype(a0) / bmi.s loc_2A762 — check subtype bit 7 (MSB)
        // If subtype MSB is set or player's top_solid_bit is already 0x0E,
        // skip the path assignment. Otherwise assign primary path (0x0C/0x0D).
        if ((subtype & 0x80) != 0) {
            // Subtype MSB set: use secondary path
            player.setTopSolidBit((byte) 0x0E);
            player.setLrbSolidBit((byte) 0x0F);
        } else {
            // Default: assign primary path
            player.setTopSolidBit((byte) 0x0C);
            player.setLrbSolidBit((byte) 0x0D);
        }
    }

    /**
     * Breaks the cork floor: advances mapping frame, plays SFX, spawns fragments.
     * ROM: loc_2A59C (shared break routine for all modes).
     */
    private void performBreak(AbstractPlayableSprite player) {
        if (broken) {
            return;
        }
        broken = true;

        // Advance mapping frame from intact to broken
        // ROM: addq.b #1,mapping_frame(a0)
        int brokenFrame = mappingFrame + 1;

        // Play collapse SFX
        // ROM: move.w #sfx_Collapse,d0 / jsr (PlaySfx).l
        if (isOnScreen()) {
            try {
                services().playSfx(Sonic3kSfx.COLLAPSE.id);
            } catch (Exception e) {
                // Prevent audio failure from breaking game logic
            }
        }

        // Spawn fragment children from the broken mapping frame pieces
        spawnFragments(brokenFrame);

        // Mark remembered so the cork floor doesn't respawn when player returns
        markRemembered();

        // Release player from this object
        try {
            ObjectManager om = getObjectManager();
            if (om != null) {
                om.clearRidingObject(null);
            }
        } catch (Exception e) {
            // Safe fallback
        }
    }

    /**
     * Spawns fragment children for each piece in the broken mapping frame.
     * Each fragment gets velocity from the zone's velocity table.
     * ROM: BreakObjectToPieces (sonic3k.asm:45772), uses per-zone velocity tables.
     *
     * @param brokenFrameIndex the broken mapping frame index to use for fragment rendering
     */
    private void spawnFragments(int brokenFrameIndex) {
        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            return;
        }

        com.openggf.level.objects.ObjectSpriteSheet sheet = renderManager.getSheet(config.artKey);
        if (sheet == null || brokenFrameIndex >= sheet.getFrameCount()) {
            LOG.fine(() -> "CorkFloor: broken frame " + brokenFrameIndex
                    + " not found in sheet " + config.artKey);
            return;
        }

        SpriteMappingFrame brokenFrame = sheet.getFrame(brokenFrameIndex);
        int pieceCount = brokenFrame.pieces().size();
        int maxFragments = Math.min(pieceCount, effectiveVelTable.length);

        for (int i = 0; i < maxFragments; i++) {
            int xVel = effectiveVelTable[i][0];
            int yVel = effectiveVelTable[i][1];

            CorkFloorFragment fragment = new CorkFloorFragment(
                    x, y, brokenFrameIndex, i, xVel, yVel, config.artKey, hFlip);
            spawnDynamicObject(fragment);
        }
    }

    private void markRemembered() {
        try {
            ObjectManager om = getObjectManager();
            if (om != null) {
                om.markRemembered(spawn);
            }
        } catch (Exception e) {
            // Safe fallback for test environments
        }
    }

    // ===== Rendering =====

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (broken) {
            return; // Fragments handle their own rendering
        }

        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(config.artKey);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, x, y, hFlip, false);
        }
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    // ===== Helpers =====

    // Uses inherited getRenderManager() from AbstractObjectInstance

    private static ObjectManager getObjectManager() {
        try {
            LevelManager lm = GameServices.level();
            return lm != null ? lm.getObjectManager() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves zone configuration based on current level.
     * ROM: Obj_CorkFloor uses a zone-indexed jump table (sonic3k.asm:58420).
     */
    private static ZoneConfig resolveConfig(int subtype) {
        try {
            LevelManager lm = GameServices.level();
            if (lm != null) {
                int zone = lm.getCurrentZone();
                int act = lm.getCurrentAct();
                return switch (zone) {
                    case Sonic3kZoneIds.ZONE_AIZ -> act == 0 ? AIZ1_CONFIG : AIZ2_CONFIG;
                    case Sonic3kZoneIds.ZONE_CNZ -> CNZ_CONFIG;
                    case Sonic3kZoneIds.ZONE_FBZ -> FBZ_CONFIG;
                    case Sonic3kZoneIds.ZONE_ICZ ->
                        // ROM: btst #4,subtype(a0) selects between two ICZ variants
                        (subtype & 0x10) != 0 ? ICZ_SMALL_CONFIG : ICZ_CONFIG;
                    case Sonic3kZoneIds.ZONE_LBZ -> LBZ_CONFIG;
                    default -> {
                        LOG.warning("CorkFloor: unknown zone 0x" + Integer.toHexString(zone)
                                + ", defaulting to AIZ1 config");
                        yield AIZ1_CONFIG;
                    }
                };
            }
        } catch (Exception e) {
            LOG.fine("Could not resolve zone config: " + e.getMessage());
        }
        return AIZ1_CONFIG; // fallback
    }

    // ===================================================================
    // Fragment inner class
    // ===================================================================

    /**
     * Fragment object spawned when the cork floor breaks apart.
     * Each fragment renders a single piece from the broken mapping frame and
     * flies off with an initial velocity, then falls with gravity until offscreen.
     * <p>
     * ROM: BreakObjectToPieces creates these fragments. Gravity = 0x18 subpixels/frame
     * (standard S3K fragment gravity from MoveSprite).
     */
    public static class CorkFloorFragment extends AbstractObjectInstance {

        private int currentX;
        private int currentY;
        private final int fragmentFrameIndex;
        private final int pieceIndex;
        private final String artKey;
        private final boolean hFlip;

        private final SubpixelMotion.State motionState;

        public CorkFloorFragment(int parentX, int parentY,
                                 int fragmentFrameIndex, int pieceIndex,
                                 int xVel, int yVel,
                                 String artKey, boolean hFlip) {
            super(new ObjectSpawn(parentX, parentY, Sonic3kObjectIds.CORK_FLOOR,
                    0, hFlip ? 1 : 0, false, 0), "CorkFloorFragment");
            this.currentX = parentX;
            this.currentY = parentY;
            this.fragmentFrameIndex = fragmentFrameIndex;
            this.pieceIndex = pieceIndex;
            this.artKey = artKey;
            this.hFlip = hFlip;
            this.motionState = new SubpixelMotion.State(
                    currentX, currentY, 0, 0, xVel, yVel);
        }

        @Override
        public int getX() {
            return currentX;
        }

        @Override
        public int getY() {
            return currentY;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
            // Apply velocity with gravity using SubpixelMotion
            motionState.x = currentX;
            motionState.y = currentY;
            SubpixelMotion.moveSprite(motionState, FRAGMENT_GRAVITY);
            currentX = motionState.x;
            currentY = motionState.y;

            // Destroy when offscreen
            if (!isOnScreen(128)) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = getRenderManager();
            if (renderManager == null) {
                return;
            }

            PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFramePieceByIndex(fragmentFrameIndex, pieceIndex,
                        currentX, currentY, hFlip, false);
            }
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY);
        }

        @Override
        public boolean isPersistent() {
            return !isDestroyed();
        }
    }
}
