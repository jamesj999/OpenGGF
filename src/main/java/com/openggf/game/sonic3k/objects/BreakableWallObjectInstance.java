package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.game.PlayerCharacter;
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
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ShieldType;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x0D - Breakable Wall (Sonic 3 &amp; Knuckles).
 * <p>
 * A solid wall that shatters into debris when broken. Used across multiple zones
 * with zone-specific art, dimensions, and behavior.
 * <p>
 * Three behavioral modes selected by zone and subtype:
 * <ul>
 *   <li><b>STANDARD</b> (loc_21568): Player must be rolling with speed &ge; $480,
 *       OR be Super, OR be Knuckles, OR have Fire Shield + rolling + speed &ge; $480.</li>
 *   <li><b>KNUCKLES_ONLY</b> (loc_21818): Only Knuckles can break by pushing.
 *       Used in HCZ(frame 2), CNZ(frame 2), LBZ, MHZ, SOZ, MGZ(bit 4 set).</li>
 *   <li><b>MGZ_SPIN_BREAK</b> (loc_2172E): MGZ frame 2 only. Breaks on
 *       status_tertiary bit 6 (wall-cling contact, effectively Knuckles-only).</li>
 * </ul>
 * <p>
 * Subtype byte:
 * <ul>
 *   <li>Bit 7: Trigger-controlled (delete when Level_trigger_array[0] is active)</li>
 *   <li>Bit 4: MGZ-only: force Knuckles-only mode</li>
 *   <li>Bits 3-0: mapping_frame (visual variant)</li>
 * </ul>
 * <p>
 * On break, the wall spawns debris fragments (one per mapping piece of the broken frame).
 * Each fragment gets directional velocity from left/right velocity tables based on
 * which side of the wall the player is on.
 * <p>
 * ROM references: Obj_BreakableWall (sonic3k.asm:45519), loc_21568 (standard),
 * loc_2172E (MGZ spin-break), loc_21818 (Knuckles-only),
 * BreakObjectToPieces (sonic3k.asm:45772).
 */
public class BreakableWallObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOG = Logger.getLogger(BreakableWallObjectInstance.class.getName());

    // Fragment gravity: addi.w #$70,y_vel(a0) (ROM: loc_21692)
    private static final int FRAGMENT_GRAVITY = 0x70;

    // Priority: $280 = bucket 5 (ROM: move.w #$280,priority(a0))
    private static final int PRIORITY = 5;

    // Speed threshold for standard break: cmpi.w #$480,d0 (ROM: loc_215EE)
    private static final int BREAK_SPEED_THRESHOLD = 0x480;

    // Knuckles glide state values (ROM: double_jump_flag)
    private static final int GLIDE_ACTIVE = 1;
    private static final int GLIDE_FALLING = 2;
    // Knuckles fall-from-glide animation (ROM: move.b #$21,anim(a1))
    private static final int KNUCKLES_FALL_FROM_GLIDE_ANIM = 0x21;

    // ===== Debris velocity tables from ROM =====

    // word_2193A / word_2196A (AIZ, SOZ frame 4 - 12 pieces each)
    private static final int[][] VEL_RIGHT_12 = {
            {0x400, -0x500}, {0x600, -0x600}, {0x600, -0x100}, {0x800, -0x200},
            {0x680, 0}, {0x880, 0}, {0x600, 0x100}, {0x800, 0x200},
            {0x400, 0x500}, {0x600, 0x600}, {0x300, 0x600}, {0x500, 0x700},
    };
    private static final int[][] VEL_LEFT_12 = {
            {-0x600, -0x600}, {-0x400, -0x500}, {-0x800, -0x200}, {-0x600, -0x100},
            {-0x880, 0}, {-0x680, 0}, {-0x800, 0x200}, {-0x600, 0x100},
            {-0x600, 0x600}, {-0x400, 0x500}, {-0x500, 0x700}, {-0x300, 0x600},
    };

    // word_2199A / word_219BA (HCZ, LBZ - 8 pieces each)
    private static final int[][] VEL_RIGHT_8A = {
            {0x400, -0x500}, {0x600, -0x600}, {0x600, -0x100}, {0x800, -0x200},
            {0x600, 0x100}, {0x800, 0x200}, {0x400, 0x500}, {0x600, 0x600},
    };
    private static final int[][] VEL_LEFT_8A = {
            {-0x600, -0x600}, {-0x400, -0x500}, {-0x800, -0x200}, {-0x600, -0x100},
            {-0x800, 0x200}, {-0x600, 0x100}, {-0x600, 0x600}, {-0x400, 0x500},
    };

    // word_219DA / word_21A2A (MGZ - 20 pieces each)
    private static final int[][] VEL_RIGHT_20 = {
            {0x400, -0x500}, {0x500, -0x580}, {0x600, -0x600}, {0x700, -0x680},
            {0x600, -0x100}, {0x700, -0x180}, {0x800, -0x200}, {0x900, -0x280},
            {0x680, 0}, {0x780, 0}, {0x880, 0}, {0x980, 0},
            {0x600, 0x100}, {0x700, 0x180}, {0x800, 0x200}, {0x900, 0x280},
            {0x400, 0x500}, {0x500, 0x580}, {0x600, 0x600}, {0x700, 0x680},
    };
    private static final int[][] VEL_LEFT_20 = {
            {-0x700, -0x680}, {-0x600, -0x600}, {-0x500, -0x580}, {-0x400, -0x500},
            {-0x900, -0x280}, {-0x800, -0x200}, {-0x700, -0x180}, {-0x600, -0x100},
            {-0x980, 0}, {-0x880, 0}, {-0x780, 0}, {-0x680, 0},
            {-0x900, 0x280}, {-0x800, 0x200}, {-0x700, 0x180}, {-0x600, 0x100},
            {-0x700, 0x680}, {-0x600, 0x600}, {-0x500, 0x580}, {-0x400, 0x500},
    };

    // word_21A7A / word_21A9A (CNZ, MHZ, LRZ, SOZ standard - 8 pieces each)
    private static final int[][] VEL_RIGHT_8B = {
            {0x400, -0x500}, {0x600, -0x600}, {0x600, -0x100}, {0x800, -0x200},
            {0x600, 0x100}, {0x800, 0x200}, {0x400, 0x500}, {0x600, 0x600},
    };
    private static final int[][] VEL_LEFT_8B = {
            {-0x600, -0x600}, {-0x400, -0x500}, {-0x800, -0x200}, {-0x600, -0x100},
            {-0x800, 0x200}, {-0x600, 0x100}, {-0x600, 0x600}, {-0x400, 0x500},
    };

    // ===== Behavioral modes =====

    private enum BreakMode {
        /** loc_21568: rolling+speed, Super, Knuckles, or Fire Shield+rolling+speed */
        STANDARD,
        /** loc_21818: only Knuckles can break */
        KNUCKLES_ONLY,
        /** loc_2172E: MGZ spin-break (status_tertiary bit 6 / wall-cling) */
        MGZ_SPIN_BREAK
    }

    // ===== Zone configuration =====

    /**
     * Per-zone configuration.
     * @param artKey           art sheet key
     * @param halfWidth        width_pixels (collision half-width before +0x0B)
     * @param halfHeight       height_pixels (collision half-height)
     * @param velRight         right-side debris velocity table ($34)
     * @param velLeft          left-side debris velocity table ($38)
     * @param breakMode        which break behavior to use
     * @param sheetFrameOffset offset to subtract from mapping_frame for filtered sheets
     *                         (e.g., HCZ Knux uses filter [2,3] → offset=2 so frame 2→0, 3→1)
     */
    private record ZoneConfig(
            String artKey,
            int halfWidth,
            int halfHeight,
            int[][] velRight,
            int[][] velLeft,
            BreakMode breakMode,
            int sheetFrameOffset
    ) {
        ZoneConfig(String artKey, int halfWidth, int halfHeight,
                   int[][] velRight, int[][] velLeft, BreakMode breakMode) {
            this(artKey, halfWidth, halfHeight, velRight, velLeft, breakMode, 0);
        }
    }

    // ===== Instance state =====

    private final ZoneConfig config;
    private final int subtype;
    private final boolean triggerControlled;
    private final int x;
    private final int y;
    private int mappingFrame;
    private boolean broken;

    // Pre-contact velocity snapshot (ROM: $30(a0) = saved P1 x_vel)
    private short savedPreContactXSpeed;

    public BreakableWallObjectInstance(ObjectSpawn spawn) {
        super(spawn, "BreakableWall");
        this.x = spawn.x();
        this.y = spawn.y();
        this.subtype = spawn.subtype() & 0xFF;
        this.triggerControlled = (subtype & 0x80) != 0;

        // ROM: andi.b #$F,d0 / move.b d0,mapping_frame(a0)
        this.mappingFrame = subtype & 0x0F;

        this.config = resolveConfig(subtype, mappingFrame);
    }

    // ===== SolidObjectProvider =====

    @Override
    public SolidObjectParams getSolidParams() {
        // ROM: d1=width_pixels+$B, d2=height_pixels, d3=height_pixels+1
        return new SolidObjectParams(config.halfWidth + 0x0B, config.halfHeight, config.halfHeight + 1);
    }

    @Override
    public boolean isTopSolidOnly() {
        return false; // SolidObjectFull: full 4-sided collision
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return !broken;
    }

    // ===== SolidObjectListener =====

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (player == null || broken) return;

        // Capture pre-contact X velocity (ROM saves before SolidObjectFull)
        ObjectManager om = getObjectManager();
        if (om != null) {
            savedPreContactXSpeed = om.getPreContactXSpeed();
        }

        // Only interested in pushing contacts (ROM: d6 bits 16-17 = pushing flags)
        if (!contact.pushing()) return;

        boolean shouldBreak = switch (config.breakMode) {
            case STANDARD -> checkStandardBreak(player);
            case KNUCKLES_ONLY -> isKnuckles();
            case MGZ_SPIN_BREAK -> checkMgzSpinBreak(player);
        };

        if (shouldBreak) {
            performBreak(player);
        }
    }

    /**
     * Standard break check (loc_215B2).
     * Super Sonic/Knuckles → always break.
     * Knuckles → always break.
     * Fire Shield + rolling + speed ≥ $480 → break.
     * Normal pushing + rolling + speed ≥ $480 → break.
     */
    private boolean checkStandardBreak(AbstractPlayableSprite player) {
        // ROM: tst.b (Super_Sonic_Knux_flag).w / bne.s loc_215F4
        if (player.isSuperSonic()) return true;

        // ROM: cmpi.b #2,character_id(a1) / beq.s loc_215F4
        if (isKnuckles()) return true;

        // Must be rolling (ROM: cmpi.b #2,anim(a1))
        if (!player.getRolling()) return false;

        // Speed check: abs(x_vel) >= $480
        int absSpeed = Math.abs(savedPreContactXSpeed);
        if (absSpeed < BREAK_SPEED_THRESHOLD) return false;

        // ROM: btst #Status_FireShield,status_secondary(a1) — fire shield skips push check
        // Both fire shield + rolling + speed and normal push + rolling + speed break the wall
        return true;
    }

    /**
     * MGZ spin-break check (loc_2172E).
     * ROM: bclr #6,$37(a1) — tests status_tertiary bit 6 (wall-cling contact).
     * Effectively only Knuckles can trigger this via gliding into the wall.
     */
    private boolean checkMgzSpinBreak(AbstractPlayableSprite player) {
        // In the ROM, bit 6 of status_tertiary is set by SolidObjectFull when bit 7
        // (wall-cling) is already set. Practically only Knuckles triggers this.
        // Approximate: Knuckles gliding or pushing triggers break.
        if (!isKnuckles()) return false;
        // ROM: the actual check is on a contact bit, not glide state specifically.
        // Any Knuckles contact with this wall type breaks it.
        return true;
    }

    // ===== Break logic =====

    private void performBreak(AbstractPlayableSprite player) {
        if (broken) return;
        broken = true;

        // ROM: move.w d1,x_vel(a1) — restore player's saved X velocity
        player.setXSpeed(savedPreContactXSpeed);
        player.setGSpeed(savedPreContactXSpeed);

        // Position adjustment and debris direction differ by break mode.
        // sub_2165A / sub_218CE: addq.w #4 first, then cmp.w x_pos(a0),d0 / blo.s
        //   If wall_x < player_x_shifted (player right): keep +4, use $34 right debris
        //   Else (player left): subi.w #8 (net -4 from original), use $38 left debris
        // sub_217EE (MGZ spin-break): no initial +4, only subi.w #8 if player left
        int[][] velTable;
        if (config.breakMode == BreakMode.MGZ_SPIN_BREAK) {
            // ROM sub_217EE: compare without +4 adjustment
            boolean playerIsRight = player.getCentreX() > x;
            velTable = playerIsRight ? config.velRight : config.velLeft;
            if (!playerIsRight) {
                player.setX((short) (player.getX() - 8));
            }
        } else {
            // ROM: apply +4 first, then compare wall center vs shifted player center
            player.setX((short) (player.getX() + 4));
            boolean playerIsRight = player.getCentreX() > x;
            if (playerIsRight) {
                velTable = config.velRight;
            } else {
                // ROM: subi.w #8 → net -4 from original position
                player.setX((short) (player.getX() - 8));
                velTable = config.velLeft;
            }
        }

        // ROM: bclr #Status_Push,status(a1)
        player.setPushing(false);

        // Knuckles glide cancel: ROM only does this in sub_218CE (KNUCKLES_ONLY mode),
        // NOT in sub_2165A (STANDARD) or sub_217EE (MGZ_SPIN_BREAK).
        if (config.breakMode == BreakMode.KNUCKLES_ONLY
                && isKnuckles() && player.getDoubleJumpFlag() == GLIDE_ACTIVE) {
            player.setDoubleJumpFlag(GLIDE_FALLING);
            player.setAnimationId(KNUCKLES_FALL_FROM_GLIDE_ANIM);
            if (player.getXSpeed() >= 0) {
                player.setDirection(Direction.RIGHT);
            } else {
                player.setDirection(Direction.LEFT);
            }
        }

        // Play collapse SFX (ROM: sfx_Collapse = $59)
        if (isOnScreen()) {
            try {
                AudioManager.getInstance().playSfx(Sonic3kSfx.COLLAPSE.id);
            } catch (Exception e) {
                // Prevent audio failure from breaking game logic
            }
        }

        // Advance to broken frame and spawn debris
        // ROM: addq.b #1,mapping_frame(a0)
        // Adjust for filtered sheets (e.g., HCZ Knux filter [2,3] → offset=2)
        int brokenFrame = (mappingFrame + 1) - config.sheetFrameOffset;
        spawnFragments(brokenFrame, velTable);

        markRemembered();
    }

    // ===== Update =====

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (broken) return;

        // Trigger-controlled deletion (ROM: tst.b subtype(a0) / bpl.s)
        if (triggerControlled && isTriggerActive()) {
            setDestroyed(true);
        }
    }

    /**
     * Checks if Level_trigger_array[0] is active.
     * ROM: tst.b (Level_trigger_array).w / beq.s
     * Approximated via event routine progression (same as AizFallingLog).
     */
    private boolean isTriggerActive() {
        try {
            Sonic3kLevelEventManager lem = Sonic3kLevelEventManager.getInstance();
            return lem != null && lem.getEventRoutineFg() >= 2;
        } catch (Exception e) {
            return false;
        }
    }

    // ===== Fragment spawning =====

    private void spawnFragments(int brokenFrameIndex, int[][] velTable) {
        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) return;

        ObjectSpriteSheet sheet = renderManager.getSheet(config.artKey);
        if (sheet == null || brokenFrameIndex >= sheet.getFrameCount()) {
            LOG.fine(() -> "BreakableWall: broken frame " + brokenFrameIndex
                    + " not found in sheet " + config.artKey);
            return;
        }

        SpriteMappingFrame brokenFrame = sheet.getFrame(brokenFrameIndex);
        int pieceCount = brokenFrame.pieces().size();
        int maxFragments = Math.min(pieceCount, velTable.length);

        for (int i = 0; i < maxFragments; i++) {
            int xVel = velTable[i][0];
            int yVel = velTable[i][1];

            BreakableWallFragment fragment = new BreakableWallFragment(
                    x, y, brokenFrameIndex, i, xVel, yVel, config.artKey);
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
            // Safe fallback
        }
    }

    // ===== Rendering =====

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (broken) return;

        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) return;

        PatternSpriteRenderer renderer = renderManager.getRenderer(config.artKey);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame - config.sheetFrameOffset, x, y, false, false);
        }
    }

    @Override
    public int getX() { return x; }

    @Override
    public int getY() { return y; }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    // ===== Helpers =====

    // Uses inherited getRenderManager() from AbstractObjectInstance

    private static ObjectManager getObjectManager() {
        try {
            LevelManager lm = LevelManager.getInstance();
            return lm != null ? lm.getObjectManager() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isKnuckles() {
        try {
            Sonic3kLevelEventManager lem = Sonic3kLevelEventManager.getInstance();
            return lem != null && lem.getPlayerCharacter() == PlayerCharacter.KNUCKLES;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Resolves zone-specific configuration.
     * ROM: Obj_BreakableWall zone table (sonic3k.asm:45530-45518).
     */
    private static ZoneConfig resolveConfig(int subtype, int frame) {
        try {
            LevelManager lm = LevelManager.getInstance();
            if (lm != null) {
                int zone = lm.getCurrentZone();
                return resolveForZone(zone, subtype, frame);
            }
        } catch (Exception e) {
            LOG.fine("Could not resolve zone config: " + e.getMessage());
        }
        // Default fallback: AIZ standard
        return new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_AIZ,
                0x10, 0x28, VEL_RIGHT_12, VEL_LEFT_12, BreakMode.STANDARD);
    }

    private static ZoneConfig resolveForZone(int zone, int subtype, int frame) {
        return switch (zone) {
            case Sonic3kZoneIds.ZONE_AIZ ->
                    // ROM: width=$10, height=$28, standard mode
                    new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_AIZ,
                            0x10, 0x28, VEL_RIGHT_12, VEL_LEFT_12, BreakMode.STANDARD);
            case Sonic3kZoneIds.ZONE_HCZ -> {
                if (frame == 2) {
                    // ROM: HCZ frame 2 = Knuckles-only with different art
                    // Filtered sheet [2,3] → sheetFrameOffset=2 so frame 2→0, 3→1
                    yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_HCZ_KNUX,
                            0x18, 0x20, VEL_RIGHT_8A, VEL_LEFT_8A, BreakMode.KNUCKLES_ONLY, 2);
                }
                // ROM: width=$10, height=$20, standard mode
                yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_HCZ,
                        0x10, 0x20, VEL_RIGHT_8A, VEL_LEFT_8A, BreakMode.STANDARD);
            }
            case Sonic3kZoneIds.ZONE_MGZ -> {
                // ROM: MGZ reverses the $34/$38 table convention vs other zones.
                // $34 = word_21A2A (negative X = left), $38 = word_219DA (positive X = right).
                // Since performBreak uses velRight when player is right, velLeft when left,
                // and the player pushes TOWARD the wall, we swap to match ROM behavior.
                if ((subtype & 0x10) != 0) {
                    // ROM: MGZ bit 4 set = Knuckles-only
                    yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_MGZ,
                            0x20, 0x28, VEL_LEFT_20, VEL_RIGHT_20, BreakMode.KNUCKLES_ONLY);
                }
                if (frame == 2) {
                    // ROM: MGZ frame 2 = spin-break (loc_2172E)
                    yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_MGZ,
                            0x20, 0x28, VEL_LEFT_20, VEL_RIGHT_20, BreakMode.MGZ_SPIN_BREAK);
                }
                // ROM: width=$20, height=$28, standard mode
                yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_MGZ,
                        0x20, 0x28, VEL_LEFT_20, VEL_RIGHT_20, BreakMode.STANDARD);
            }
            case Sonic3kZoneIds.ZONE_CNZ -> {
                if (frame == 2) {
                    // ROM: CNZ frame 2 = Knuckles-only
                    yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_CNZ,
                            0x10, 0x20, VEL_RIGHT_8B, VEL_LEFT_8B, BreakMode.KNUCKLES_ONLY);
                }
                yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_CNZ,
                        0x10, 0x20, VEL_RIGHT_8B, VEL_LEFT_8B, BreakMode.STANDARD);
            }
            case Sonic3kZoneIds.ZONE_LBZ ->
                    // ROM: all LBZ breakable walls are Knuckles-only
                    new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_LBZ,
                            0x10, 0x20, VEL_RIGHT_8A, VEL_LEFT_8A, BreakMode.KNUCKLES_ONLY);
            case Sonic3kZoneIds.ZONE_MHZ ->
                    // ROM: all MHZ breakable walls are Knuckles-only
                    new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_MHZ,
                            0x10, 0x20, VEL_RIGHT_8B, VEL_LEFT_8B, BreakMode.KNUCKLES_ONLY);
            case Sonic3kZoneIds.ZONE_SOZ -> {
                if (frame == 4) {
                    // ROM: SOZ frame 4 = tall variant, height=$30
                    yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_SOZ,
                            0x10, 0x30, VEL_RIGHT_12, VEL_LEFT_12, BreakMode.KNUCKLES_ONLY);
                }
                yield new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_SOZ,
                        0x10, 0x20, VEL_RIGHT_8B, VEL_LEFT_8B, BreakMode.KNUCKLES_ONLY);
            }
            case Sonic3kZoneIds.ZONE_LRZ ->
                    // ROM: LRZ uses standard mode (falls through to default in disasm)
                    new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_LRZ,
                            0x10, 0x20, VEL_RIGHT_8B, VEL_LEFT_8B, BreakMode.STANDARD);
            default ->
                    // Fallthrough zones (FBZ, ICZ, SSZ, DEZ, DDZ) use AIZ defaults
                    new ZoneConfig(Sonic3kObjectArtKeys.BREAKABLE_WALL_AIZ,
                            0x10, 0x28, VEL_RIGHT_12, VEL_LEFT_12, BreakMode.STANDARD);
        };
    }

    // ===================================================================
    // Fragment inner class
    // ===================================================================

    /**
     * Debris fragment spawned when the wall breaks apart.
     * Each fragment renders a single piece from the broken mapping frame and
     * flies off with initial velocity, falling with gravity until offscreen.
     * <p>
     * ROM: BreakObjectToPieces creates fragments. Gravity = $70 subpixels/frame
     * (from loc_21692: addi.w #$70,y_vel(a0)).
     */
    public static class BreakableWallFragment extends AbstractObjectInstance {

        private int currentX;
        private int currentY;
        private final int fragmentFrameIndex;
        private final int pieceIndex;
        private final String artKey;

        private final SubpixelMotion.State motionState;

        public BreakableWallFragment(int parentX, int parentY,
                                     int fragmentFrameIndex, int pieceIndex,
                                     int xVel, int yVel, String artKey) {
            super(new ObjectSpawn(parentX, parentY, Sonic3kObjectIds.BREAKABLE_WALL,
                    0, 0, false, 0), "BreakableWallFragment");
            this.currentX = parentX;
            this.currentY = parentY;
            this.fragmentFrameIndex = fragmentFrameIndex;
            this.pieceIndex = pieceIndex;
            this.artKey = artKey;
            this.motionState = new SubpixelMotion.State(
                    currentX, currentY, 0, 0, xVel, yVel);
        }

        @Override
        public int getX() { return currentX; }

        @Override
        public int getY() { return currentY; }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            // ROM: jsr (MoveSprite2).l / addi.w #$70,y_vel(a0)
            motionState.x = currentX;
            motionState.y = currentY;
            SubpixelMotion.moveSprite(motionState, FRAGMENT_GRAVITY);
            currentX = motionState.x;
            currentY = motionState.y;

            // ROM: tst.b render_flags(a0) / bpl.s loc_216AA — delete when offscreen
            if (!isOnScreen(128)) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = getRenderManager();
            if (renderManager == null) return;

            PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFramePieceByIndex(fragmentFrameIndex, pieceIndex,
                        currentX, currentY, false, false);
            }
        }

        @Override
        public int getPriorityBucket() {
            // ROM: move.w #$80,priority(a0) — debris gets priority $80 = bucket 1
            return RenderPriority.clamp(1);
        }

        @Override
        public boolean isPersistent() {
            return !isDestroyed();
        }

        // Uses inherited getRenderManager() from AbstractObjectInstance
    }
}
