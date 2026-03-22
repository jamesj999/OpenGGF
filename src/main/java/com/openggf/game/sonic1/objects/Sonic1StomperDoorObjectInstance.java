package com.openggf.game.sonic1.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic1.Sonic1SwitchManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import com.openggf.debug.DebugColor;
import java.util.List;

/**
 * Sonic 1 Object 0x6B - Stomper and Sliding Door (SBZ).
 * <p>
 * A multi-purpose solid object used in Scrap Brain Zone with 5 visual/behavioral
 * subtypes controlled by the Sto_Var table and the subtype byte:
 * <p>
 * <b>Sto_Var table</b> (indexed by subtype >> 4, masked to 5 entries):
 * <pre>
 *   Entry 0: width=$40, height=$0C, moveDist=$80, type=1  (horizontal sliding door)
 *   Entry 1: width=$1C, height=$20, moveDist=$38, type=3  (short stomper)
 *   Entry 2: width=$1C, height=$20, moveDist=$40, type=4  (medium stomper)
 *   Entry 3: width=$1C, height=$20, moveDist=$60, type=4  (long stomper)
 *   Entry 4: width=$80, height=$40, moveDist=$00, type=5  (SBZ3 big diagonal door)
 * </pre>
 * <p>
 * <b>Movement types</b> (from .index jump table):
 * <ul>
 *   <li>Type 0: Stationary (no movement)</li>
 *   <li>Type 1: Switch-activated horizontal slide, advances to type 2 when done</li>
 *   <li>Type 2: Timer-based horizontal retract, returns to type 1 when done</li>
 *   <li>Type 3: Vertical stomper - extends downward, then retracts</li>
 *   <li>Type 4: Vertical stomper - retracts upward, then extends</li>
 *   <li>Type 5: Switch-activated diagonal slide (SBZ3 big door)</li>
 * </ul>
 * <p>
 * When the high bit of the original subtype is set, the low nybble specifies a
 * switch index for switch-activated types (1 and 5). The subtype is then
 * replaced with the type number from Sto_Var.
 * <p>
 * SBZ3 detection: When zone == LZ (id_LZ), the object is in SBZ3. A duplicate
 * check via v_obj6B prevents multiple SBZ3 instances. SBZ3 uses different art
 * (ArtTile_Level+$1F0, palette 2) and the .bigdoor mapping frame.
 * <p>
 * Solid object: The object calls SolidObject with d1=obActWid+$B, d2=obHeight,
 * d3=obHeight+1 (standard solid parameters).
 * <p>
 * Persistence: Uses out_of_range against sto_origX (the base position, not the
 * current animated position) to decide when to unload.
 * <p>
 * Reference: docs/s1disasm/_incObj/6B SBZ Stomper and Door.asm
 */
public class Sonic1StomperDoorObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // ---- v_obj6B: singleton slot for SBZ3 instances (lines 38-65 in disasm) ----
    // Only one SBZ3 StomperDoor may exist at a time. In the ROM the spawn window
    // naturally loads the switch-activated door (at X=$A80) before the static one
    // (at X~$980) because the camera starts on the right side of SBZ3. Our engine
    // may spawn them in ascending-X order instead, so we give priority to the
    // switch-activated (type 5) instance over the static (type 0) one.
    private static Sonic1StomperDoorObjectInstance sbz3Instance = null;

    /**
     * Resets the v_obj6B singleton slot. Call on level load / zone transition.
     */
    public static void resetSbz3Flag() {
        sbz3Instance = null;
    }

    // ---- Sto_Var table entries ----
    // Each entry: {width, height, moveDist, typeNumber}
    private static final int[][] STO_VAR = {
            {0x40, 0x0C, 0x80, 1}, // Entry 0: horizontal sliding door
            {0x1C, 0x20, 0x38, 3}, // Entry 1: short stomper
            {0x1C, 0x20, 0x40, 4}, // Entry 2: medium stomper
            {0x1C, 0x20, 0x60, 4}, // Entry 3: long stomper
            {0x80, 0x40, 0x00, 5}, // Entry 4: SBZ3 big diagonal door
    };

    // Type 1 -> Type 2 transition delay: move.w #$B4,objoff_36(a0) = 180 frames
    private static final int TYPE1_TO_TYPE2_DELAY = 0xB4;

    // Type 3/4: pause duration at endpoints: move.w #$3C,objoff_36(a0) = 60 frames
    private static final int STOMPER_PAUSE = 0x3C;

    // Type 3/4: vertical movement speed per frame: addq.w #8 / subq.w #8
    private static final int STOMPER_SPEED = 8;

    // Type 1/2: horizontal movement speed per frame: addq.w #2 / subq.w #2
    private static final int DOOR_SPEED = 2;

    // Type 5: diagonal movement per frame: subi.l #$10000,obX / addi.l #$8000,obY
    // $10000 = 1 pixel X, $8000 = 0.5 pixel Y (in 16.16 fixed point, but in practice
    // these are word-level operations: X decreases by 1, Y sub-pixel by $8000)
    // Actually: subi.l #$10000,obX means subtract $10000 from the 32-bit X (16.16 fixed),
    // = subtract 1.0 pixel from X. addi.l #$8000,obY = add 0.5 pixel to Y.
    private static final int TYPE5_X_SPEED = 1;         // 1 pixel/frame X
    private static final int TYPE5_Y_SUBPIXEL = 0x8000; // 0.5 pixel/frame Y (in 16.16)
    private static final int TYPE5_TARGET_X = 0x980;    // cmpi.w #$980,obX(a0)

    // Type 1 flip offset: addi.w #$80,d0 when x-flipped
    private static final int TYPE1_FLIP_OFFSET = 0x80;

    // Type 3/4 flip offset: addi.w #$38,d0 when x-flipped
    private static final int TYPE3_FLIP_OFFSET = 0x38;

    // obPriority from ROM: move.b #4,obPriority(a0)
    private static final int PRIORITY = 4;

    /** Debug color (steel blue for SBZ machinery). */
    private static final DebugColor DEBUG_COLOR = new DebugColor(100, 140, 200);

    // ---- Instance state ----

    // Visual properties from Sto_Var
    private final int actWidth;       // obActWid
    private final int height;         // obHeight
    private final int moveDistance;    // objoff_3C - maximum travel distance
    private final int mappingFrame;   // obFrame

    // Position (mutable - this object moves)
    private int x;
    private int y;

    // Original positions saved for movement and range checks
    private final int origX;          // sto_origX = objoff_34
    private final int origY;          // sto_origY = objoff_30

    // Movement state
    private int moveType;             // Current movement type (obSubtype low nybble, may change)
    private int currentOffset;        // objoff_3A - current displacement from origin
    private int timer;                // objoff_36 - countdown timer
    private boolean active;           // sto_active = objoff_38

    // Switch index for type 1 and type 5 (from original subtype low nybble when bit 7 set)
    private final int switchIndex;    // objoff_3E

    // obStatus bit 0 for flip direction
    private final boolean xFlipped;

    // Whether this is an SBZ3 instance (zone == LZ)
    private final boolean isSbz3;

    // Type 5: sub-pixel Y accumulator for 0.5px/frame movement
    private int ySubPixel;

    // Whether render flags bit 4 is set (SBZ3 bigdoor uses bg render)
    private final boolean bgRender;

    // SolidObject params computed from width/height
    private final SolidObjectParams solidParams;

    public Sonic1StomperDoorObjectInstance(ObjectSpawn spawn, int zoneIndex) {
        super(spawn, "StomperDoor");

        this.isSbz3 = (zoneIndex == Sonic1Constants.ZONE_LZ);

        // Parse subtype to select Sto_Var entry
        int subtype = spawn.subtype() & 0xFF;

        // moveq #0,d0 / move.b obSubtype(a0),d0 / lsr.w #2,d0 / andi.w #$1C,d0
        int varIndex = (subtype >> 2) & 0x1C;
        // Index into 4-byte entries: varIndex / 4
        int entryIndex = varIndex >> 2;
        if (entryIndex >= STO_VAR.length) {
            entryIndex = STO_VAR.length - 1;
        }
        int[] entry = STO_VAR[entryIndex];

        this.actWidth = entry[0];
        this.height = entry[1];

        // lsr.w #2,d0 -> frame = entry index
        this.mappingFrame = entryIndex;

        // Store origX/origY from spawn position
        this.origX = spawn.x();
        this.origY = spawn.y();
        this.x = origX;
        this.y = origY;

        // move.w d0,objoff_3C(a0) - moveDist from Sto_Var (byte extended to word)
        this.moveDistance = entry[2];

        // obStatus bit 0 from spawn renderFlags x-flip bit
        this.xFlipped = (spawn.renderFlags() & 0x1) != 0;

        // Check if subtype has bit 7 set (switch-activated)
        // moveq #0,d0 / move.b obSubtype(a0),d0 / bpl.s Sto_Action
        // andi.b #$F,d0 / move.b d0,objoff_3E(a0) / move.b (a3),obSubtype(a0)
        if ((subtype & 0x80) != 0) {
            this.switchIndex = subtype & 0x0F;
            this.moveType = entry[3]; // Replace subtype with type number from Sto_Var
        } else {
            this.switchIndex = -1; // No switch
            this.moveType = subtype & 0x0F;
        }

        // ---- v_obj6B singleton check (lines 38-65) ----
        // In SBZ3 (zone == LZ), only one StomperDoor may exist at a time.
        // The ROM relies on spawn-window ordering (camera starts right, so the
        // switch-activated door at X=$A80 loads first). Our engine may load both
        // simultaneously in ascending-X order, so we give the switch-activated
        // instance (type 5) priority over the static one (type 0).
        if (isSbz3) {
            if (sbz3Instance != null) {
                boolean existingIsSwitchActivated = (sbz3Instance.switchIndex >= 0);
                boolean newIsSwitchActivated = (this.switchIndex >= 0);
                if (newIsSwitchActivated && !existingIsSwitchActivated) {
                    // New switch-activated door replaces existing static door
                    sbz3Instance.setDestroyed(true);
                    sbz3Instance = this;
                } else {
                    // Block duplicate (lines 43-51)
                    setDestroyed(true);
                }
            } else {
                // First SBZ3 instance: claim the singleton slot
                sbz3Instance = this;
            }
        }

        // SBZ3 big door: bset #4,obRender(a0) when type == 5
        this.bgRender = (this.moveType == 5);

        // Initialize movement state
        this.currentOffset = 0;
        this.timer = 0;
        this.active = false;
        this.ySubPixel = 0;

        // SolidObject params: d1=obActWid+$B, d2=obHeight, d3=obHeight+1
        int halfWidth = actWidth + 0x0B;
        int airHalfHeight = height;
        int groundHalfHeight = height + 1;
        this.solidParams = new SolidObjectParams(halfWidth, airHalfHeight, groundHalfHeight);
    }

    /**
     * Returns true if this is an SBZ3 instance that should be immediately deleted
     * because another SBZ3 instance already exists (the v_obj6B check), or because
     * this specific object at X=$A80 was already completed.
     * <p>
     * The engine's object manager handles spawn filtering, so we implement the
     * deletion check in the first update frame.
     */
    public boolean isSbz3() {
        return isSbz3;
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
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        // Save X before movement for SolidObject platform delta
        int prevX = x;

        // Execute movement type
        switch (moveType) {
            case 0 -> updateType0();
            case 1 -> updateType1();
            case 2 -> updateType2();
            case 3 -> updateType3();
            case 4 -> updateType4();
            case 5 -> updateType5();
        }
    }

    // ---- Movement Type Implementations ----

    /**
     * Type 0: Stationary. No movement.
     * <pre>.type00: rts</pre>
     */
    private void updateType0() {
        // No-op
    }

    /**
     * Type 1: Switch-activated horizontal slide.
     * <p>
     * When the switch is pressed, slides horizontally by moveDistance pixels
     * at 2px/frame. When target is reached, advances to type 2 with a delay timer.
     * Position is always applied via .loc_15DC2 (even after type transition).
     * <p>
     * Reference: .type01 in disassembly
     */
    private void updateType1() {
        if (!active) {
            // lea (f_switch).w,a2 / btst #0,(a2,d0.w) / beq.s .loc_15DC2
            if (switchIndex >= 0) {
                Sonic1SwitchManager switches = Sonic1SwitchManager.getInstance();
                if ((switches.getRaw(switchIndex) & 0x01) != 0) {
                    // move.b #1,sto_active(a0)
                    active = true;
                } else {
                    // Switch not pressed: skip to apply position
                    applyHorizontalOffset();
                    return;
                }
            } else {
                applyHorizontalOffset();
                return;
            }
        }

        // .isactive01:
        // move.w objoff_3C(a0),d0 / cmp.w objoff_3A(a0),d0 / beq.s .loc_15DE0
        if (moveDistance == currentOffset) {
            // .loc_15DE0: Reached target, transition to type 2
            // addq.b #1,obSubtype(a0)
            moveType = 2;
            // move.w #$B4,objoff_36(a0)
            timer = TYPE1_TO_TYPE2_DELAY;
            // clr.b sto_active(a0)
            active = false;
            // Falls through to .loc_15DC2 to apply position
        } else {
            // addq.w #2,objoff_3A(a0)
            currentOffset += DOOR_SPEED;
        }

        // .loc_15DC2: Apply horizontal offset
        applyHorizontalOffset();
    }

    /**
     * Type 2: Timer-based horizontal retract.
     * <p>
     * Counts down from $B4 frames. When timer reaches 0, begins retracting
     * at 2px/frame. When fully retracted (offset == 0), returns to type 1.
     * Position is always applied via .loc_15E1E (even after type transition).
     * <p>
     * Reference: .type02 in disassembly
     */
    private void updateType2() {
        if (!active) {
            // subq.w #1,objoff_36(a0) / bne.s .loc_15E1E
            timer--;
            if (timer != 0) {
                // Timer still running: apply position and return
                applyHorizontalOffset();
                return;
            }
            // Timer hit 0: activate retract
            // move.b #1,sto_active(a0)
            active = true;
        }

        // .isactive02:
        // tst.w objoff_3A(a0) / beq.s .loc_15E3C
        if (currentOffset == 0) {
            // .loc_15E3C: Fully retracted, return to type 1
            // subq.b #1,obSubtype(a0)
            moveType = 1;
            // clr.b sto_active(a0)
            active = false;
            // Falls through to .loc_15E1E to apply position
        } else {
            // subq.w #2,objoff_3A(a0)
            currentOffset -= DOOR_SPEED;
        }

        // .loc_15E1E: Apply horizontal offset
        applyHorizontalOffset();
    }

    /**
     * Type 3: Vertical stomper - extends downward at 8px/frame, then retracts
     * at 1px/frame. Pauses at top for 60 frames before next cycle.
     * <p>
     * Flow: retract slowly -> pause at top -> extend fast -> deactivate at bottom
     * -> retract slowly -> ...
     * <p>
     * Key: when pause timer expires, activation and first extension happen in
     * the same frame (falls through from .loc_15E6A to .isactive03).
     * <p>
     * Reference: .type03 in disassembly
     */
    private void updateType3() {
        if (!active) {
            // tst.w objoff_3A(a0) / beq.s .loc_15E6A
            if (currentOffset != 0) {
                // Retract by 1: subq.w #1,objoff_3A(a0)
                currentOffset--;
                // bra.s .loc_15E8E -> apply position
                applyVerticalOffset();
                return;
            }
            // .loc_15E6A: At top, check pause timer
            // subq.w #1,objoff_36(a0) / bpl.s .loc_15E8E
            timer--;
            if (timer >= 0) {
                // Still pausing, apply position
                applyVerticalOffset();
                return;
            }
            // Pause expired: set new timer and activate
            // move.w #$3C,objoff_36(a0) / move.b #1,sto_active(a0)
            timer = STOMPER_PAUSE;
            active = true;
            // FALLS THROUGH to .isactive03 (extend in same frame)
        }

        // .isactive03: Extend by 8
        // addq.w #8,objoff_3A(a0)
        currentOffset += STOMPER_SPEED;
        // move.w objoff_3A(a0),d0 / cmp.w objoff_3C(a0),d0 / bne.s .loc_15E8E
        if (currentOffset == moveDistance) {
            // clr.b sto_active(a0)
            active = false;
        }

        // .loc_15E8E: Apply vertical position
        applyVerticalOffset();
    }

    /**
     * Type 4: Vertical stomper - retracts at 8px/frame, pauses at top for 60
     * frames, extends at 8px/frame, pauses at bottom for 60 frames. Cycles.
     * <p>
     * Flow: retract fast -> pause at top -> extend fast -> pause at bottom -> ...
     * <p>
     * Key: when the top pause timer expires, activation and first extension happen
     * in the same frame (falls through from .loc_15EBE to .isactive04).
     * <p>
     * Reference: .type04 in disassembly
     */
    private void updateType4() {
        if (!active) {
            // tst.w objoff_3A(a0) / beq.s .loc_15EBE
            if (currentOffset != 0) {
                // subq.w #8,objoff_3A(a0) - fast retract
                currentOffset -= STOMPER_SPEED;
                // bra.s .loc_15EF0 -> apply position
                applyVerticalOffset();
                return;
            }
            // .loc_15EBE: At top, check pause timer
            // subq.w #1,objoff_36(a0) / bpl.s .loc_15EF0
            timer--;
            if (timer >= 0) {
                // Still pausing, apply position
                applyVerticalOffset();
                return;
            }
            // Pause expired: set new timer and activate
            // move.w #$3C,objoff_36(a0) / move.b #1,sto_active(a0)
            timer = STOMPER_PAUSE;
            active = true;
            // FALLS THROUGH to .isactive04 (extend in same frame)
        }

        // .isactive04: Extend phase
        // move.w objoff_3A(a0),d0 / cmp.w objoff_3C(a0),d0 / beq.s .loc_15EE0
        if (currentOffset == moveDistance) {
            // .loc_15EE0: At bottom, check pause timer
            // subq.w #1,objoff_36(a0) / bpl.s .loc_15EF0
            timer--;
            if (timer >= 0) {
                applyVerticalOffset();
                return;
            }
            // Pause expired: set new timer and deactivate
            // move.w #$3C,objoff_36(a0) / clr.b sto_active(a0)
            timer = STOMPER_PAUSE;
            active = false;
        } else {
            // addq.w #8,objoff_3A(a0)
            currentOffset += STOMPER_SPEED;
        }

        // .loc_15EF0: Apply vertical position
        applyVerticalOffset();
    }

    /**
     * Type 5: Switch-activated diagonal slide (SBZ3 big door).
     * When switch is pressed, the door slides diagonally: X decreases by 1px/frame,
     * Y increases by 0.5px/frame. Stops when X reaches $980, then becomes type 0.
     * <p>
     * Reference: .type05 in disassembly
     */
    private void updateType5() {
        if (!active) {
            // Check switch
            if (switchIndex >= 0) {
                Sonic1SwitchManager switches = Sonic1SwitchManager.getInstance();
                if ((switches.getRaw(switchIndex) & 0x01) != 0) {
                    active = true;
                    // bset #0,2(a2,d0.w) — set respawn flag so this door won't
                    // reappear at its original position if the player leaves and
                    // returns. Equivalent to ROM v_objstate respawn bit.
                    ObjectManager objectManager = services().objectManager();
                    if (objectManager != null) {
                        objectManager.markRemembered(getSpawn());
                    }
                }
            }
            if (!active) {
                return;
            }
        }

        // subi.l #$10000,obX(a0) -> X -= 1.0 pixel (in 16.16 fixed point)
        x -= TYPE5_X_SPEED;

        // addi.l #$8000,obY(a0) -> Y += 0.5 pixel (sub-pixel accumulation)
        ySubPixel += TYPE5_Y_SUBPIXEL;
        if (ySubPixel >= 0x10000) {
            y += ySubPixel >> 16;
            ySubPixel &= 0xFFFF;
        }

        // Update origX for range checking
        // move.w obX(a0),sto_origX(a0)
        // (origX is final, but the range check uses the actual x position for type 5)

        // cmpi.w #$980,obX(a0) / beq.s .loc_15F5E
        if (x == TYPE5_TARGET_X) {
            // clr.b obSubtype(a0) / clr.b sto_active(a0)
            moveType = 0;
            active = false;
        }
    }

    /**
     * Applies horizontal displacement to X position.
     * Handles x-flip: when flipped, offset is negated and $80 is added.
     * <p>
     * Reference: .noflip01 / .noflip02 in disassembly
     */
    private void applyHorizontalOffset() {
        int offset = currentOffset;
        if (xFlipped) {
            // neg.w d0 / addi.w #$80,d0
            offset = -offset + TYPE1_FLIP_OFFSET;
        }
        // move.w sto_origX(a0),d1 / sub.w d0,d1 / move.w d1,obX(a0)
        x = origX - offset;
    }

    /**
     * Applies vertical displacement to Y position.
     * Handles x-flip: when flipped, offset is negated and $38 is added.
     * <p>
     * Reference: .noflip03 / .noflip04 in disassembly
     */
    private void applyVerticalOffset() {
        int offset = currentOffset;
        if (xFlipped) {
            // neg.w d0 / addi.w #$38,d0
            offset = -offset + TYPE3_FLIP_OFFSET;
        }
        // move.w sto_origY(a0),d1 / add.w d0,d1 / move.w d1,obY(a0)
        y = origY + offset;
    }

    // ---- Rendering ----

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }

        // Select renderer based on frame type
        PatternSpriteRenderer renderer;
        if (isSbz3 && mappingFrame == 4) {
            // SBZ3 big door uses level tile art
            renderer = renderManager.getRenderer(ObjectArtKeys.SBZ3_BIG_DOOR);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            // .bigdoor is the only frame in SBZ3_BIG_DOOR sheet (index 0)
            renderer.drawFrameIndex(0, x, y, false, false);
        } else {
            // SBZ1/SBZ2: use combined stomper/door sheet
            renderer = renderManager.getRenderer(ObjectArtKeys.SBZ_STOMPER_DOOR);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            // mappingFrame 0 = door, 1-3 = stomper (all present in stomper/door sheet)
            int frame = Math.min(mappingFrame, 3);
            renderer.drawFrameIndex(frame, x, y, false, false);
        }
    }

    // ---- SolidObjectProvider ----

    @Override
    public SolidObjectParams getSolidParams() {
        return solidParams;
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        // The object is always solid when visible/active
        return true;
    }

    // ---- SolidObjectListener ----

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        // SolidObject handles the collision response; no extra per-contact behavior needed.
    }

    // ---- Persistence ----

    @Override
    public boolean shouldStayActiveWhenRemembered() {
        // Type 5 marks itself as remembered when the switch is pressed, but must
        // stay active to complete the diagonal slide animation. Returning false
        // removes the spawn from the active set (so it won't be re-created on
        // respawn), but the instance stays alive via isPersistent() while on-screen.
        // This avoids setting the stayActive bit, which would cause the top-position
        // door to incorrectly respawn instead of the bottom-position static door.
        return false;
    }

    @Override
    public boolean isPersistent() {
        // out_of_range.s .chkgone, sto_origX(a0)
        // Uses origX for range checking (the base position, not the animated position)
        // For type 5 (SBZ3 big door), the origX is updated to current X during movement
        if (isDestroyed()) {
            // .chkgone (lines 118-120): clr.b (v_obj6B).w when zone == LZ
            if (isSbz3 && sbz3Instance == this) {
                sbz3Instance = null;
            }
            return false;
        }
        boolean onScreen;
        if (moveType == 5 && active) {
            // Type 5 updates sto_origX to current X, so use x for range check
            onScreen = isOnScreenXFromPos(x);
        } else {
            onScreen = isOnScreenXFromPos(origX);
        }
        if (!onScreen && isSbz3 && sbz3Instance == this) {
            // .chkgone (lines 118-120): clr.b (v_obj6B).w when zone == LZ
            sbz3Instance = null;
        }
        return onScreen;
    }

    /**
     * Checks if the given X position is within screen range.
     * Mirrors the out_of_range macro behavior (checks against camera + margin).
     */
    private boolean isOnScreenXFromPos(int posX) {
        // out_of_range uses a standard margin (default ~$180 = 384 pixels)
        // We use the engine's built-in margin check
        return isOnScreenX(160);
    }

    // ---- Debug Rendering ----

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Draw solid bounds
        ctx.drawRect(x, y, solidParams.halfWidth(), solidParams.airHalfHeight(),
                0.4f, 0.6f, 0.8f);

        // Draw origin marker
        ctx.drawCross(origX, origY, 4, 0.8f, 0.8f, 0.2f);

        String typeStr = switch (moveType) {
            case 0 -> "STATIC";
            case 1 -> "H-SLIDE";
            case 2 -> "H-RETRACT";
            case 3 -> "V-STOMP-DN";
            case 4 -> "V-STOMP-UP";
            case 5 -> "DIAG";
            default -> "?";
        };

        ctx.drawWorldLabel(x, y, -1,
                String.format("Stomp t=%s off=%d/%d act=%b",
                        typeStr, currentOffset, moveDistance, active),
                DEBUG_COLOR);
    }
}
