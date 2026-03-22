package com.openggf.game.sonic2.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
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
 * SmallMetalPform (Object 0xBD) - Ascending/descending metal platform from Wing Fortress Zone.
 * <p>
 * A conveyor belt mechanism that periodically spawns small metal platforms.
 * The parent object is invisible and sits at the conveyor belt position,
 * spawning child platform objects every 64 frames. Each child platform
 * unfolds (animation), moves vertically for a set duration, folds back up,
 * then deletes itself. Players can ride the platform while it moves.
 * <p>
 * <b>Disassembly Reference:</b> s2.asm lines 79938-80074 (ObjBD)
 * <p>
 * <h3>Subtypes</h3>
 * <table border="1">
 *   <tr><th>Subtype</th><th>SubObjData Index</th><th>Y Velocity</th><th>Direction</th></tr>
 *   <tr><td>0x7E</td><td>$7E (word_3BCA8[0])</td><td>-$100</td><td>Ascending</td></tr>
 *   <tr><td>0x80</td><td>$80 (word_3BCA8[1])</td><td>$100</td><td>Descending</td></tr>
 * </table>
 * <p>
 * <h3>Architecture</h3>
 * <ul>
 *   <li>Parent (routine 0/2): Invisible spawner, creates children every $40 (64) frames</li>
 *   <li>Child (routine 4): Visible platform with 5-state lifecycle:
 *     <ol>
 *       <li>State 0: Init - load art/mappings, set velocity</li>
 *       <li>State 2: Unfold animation (frames 2,1,0) - $FA advances routine_secondary</li>
 *       <li>State 4: Moving platform with solid collision, timer countdown</li>
 *       <li>State 6: Fold animation (frames 0,1,2) - $FA advances routine_secondary</li>
 *       <li>State 8: Detach riders, delete object</li>
 *     </ol>
 *   </li>
 * </ul>
 */
public class SmallMetalPformObjectInstance extends AbstractObjectInstance {

    // ========================================================================
    // ROM Constants
    // ========================================================================

    // Spawner timer: move.w #$40,objoff_2A(a0) (64 frames between spawns)
    private static final int SPAWN_INTERVAL = 0x40;

    // Initial spawn delay: move.w #1,objoff_2A(a0)
    private static final int INITIAL_SPAWN_DELAY = 1;

    public SmallMetalPformObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.spawnTimer = INITIAL_SPAWN_DELAY;
    }

    // ========================================================================
    // Parent State (Spawner)
    // ========================================================================

    private int spawnTimer;

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // ROM: loc_3BC3C (routine 2)
        // subq.w #1,objoff_2A(a0)
        spawnTimer--;
        if (spawnTimer > 0) {
            return;
        }
        // Timer expired: reset and spawn child
        // move.w #$40,objoff_2A(a0)
        spawnTimer = SPAWN_INTERVAL;
        spawnChild();
    }

    /**
     * Spawn a child platform object.
     * ROM: loc_3BCF8 - AllocateObjectAfterCurrent
     * <pre>
     * _move.b #ObjID_SmallMetalPform,id(a1)
     * move.w  x_pos(a0),x_pos(a1)
     * move.w  y_pos(a0),y_pos(a1)
     * move.b  #4,routine(a1)        ; child starts at routine 4
     * move.b  subtype(a0),subtype(a1)
     * move.b  render_flags(a0),render_flags(a1)
     * </pre>
     */
    private void spawnChild() {
        ObjectManager manager = services().objectManager();
        ObjectSpawn childSpawn = new ObjectSpawn(
                spawn.x(), spawn.y(),
                Sonic2ObjectIds.SMALL_METAL_PFORM,
                spawn.subtype(),
                spawn.renderFlags(),
                false,
                spawn.rawYWord());
        SmallMetalPformChildInstance child = new SmallMetalPformChildInstance(
                childSpawn, (spawn.renderFlags() & 0x01) != 0);
        manager.addDynamicObject(child);
    }

    // ========================================================================
    // Rendering (Parent is invisible)
    // ========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Parent is invisible (ROM uses MarkObjGone3 which only checks despawn, no display)
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        ctx.drawCross(spawn.x(), spawn.y(), 4, 0.6f, 0.9f, 0.6f);
        ctx.drawWorldLabel(spawn.x(), spawn.y(), -1,
                String.format("BD spawner sub%02X t%d", spawn.subtype() & 0xFF, spawnTimer),
                DebugColor.GREEN);
    }

    // ========================================================================
    // Child Platform (Inner Class)
    // ========================================================================

    /**
     * Child platform spawned by the SmallMetalPform parent.
     * This is the actual visible, rideable platform that unfolds, moves, folds, and deletes.
     */
    public static class SmallMetalPformChildInstance extends AbstractObjectInstance
            implements SolidObjectProvider, SolidObjectListener {

        // ====================================================================
        // ROM Constants (Child)
        // ====================================================================

        // Platform collision: d1=$23, d2=4, d3=5
        // move.w #$23,d1  (width_pixels + $B = $18 + $B)
        private static final int SOLID_HALF_WIDTH = 0x23;
        // move.w #4,d2
        private static final int SOLID_HALF_HEIGHT_AIR = 4;
        // move.w #5,d3
        private static final int SOLID_HALF_HEIGHT_GND = 5;

        // Timer values based on x_flip:
        // move.w #$C7,objoff_2A(a0)  (no x_flip: 199 frames)
        private static final int MOVE_TIMER_NORMAL = 0xC7;
        // move.w #$1C7,objoff_2A(a0) (x_flip: 455 frames)
        private static final int MOVE_TIMER_FLIPPED = 0x1C7;

        // Y velocities from word_3BCA8:
        // dc.w -$100  (ascending, subtype $7E -> index 0)
        private static final int Y_VEL_ASCENDING = -0x100;
        // dc.w  $100  (descending, subtype $80 -> index 2)
        private static final int Y_VEL_DESCENDING = 0x100;

        // Animation delays from Ani_objBD:
        // byte_3BD32: dc.b 3, ...  (unfold animation delay)
        private static final int UNFOLD_ANIM_DELAY = 3;
        // byte_3BD38: dc.b 1, ...  (fold animation delay)
        private static final int FOLD_ANIM_DELAY = 1;

        // Unfold animation frames: 2, 1, 0 (folded -> mid -> unfolded)
        private static final int[] UNFOLD_FRAMES = {2, 1, 0};
        // Fold animation frames: 0, 1, 2 (unfolded -> mid -> folded)
        private static final int[] FOLD_FRAMES = {0, 1, 2};

        // ====================================================================
        // State
        // ====================================================================

        private enum ChildState {
            UNFOLD,     // routine_secondary 2: unfold animation
            MOVE,       // routine_secondary 4: moving with solid collision
            FOLD,       // routine_secondary 6: fold animation
            DELETE      // routine_secondary 8: detach riders, delete
        }

        private ChildState state;
        private final boolean xFlipped;
        private int yVelocity;       // 8.8 fixed-point (e.g., $100 = 1 pixel/frame)
        private int currentX;
        private int currentY;
        private int ySubpixel;       // fractional Y position (lower 16 bits of 16.16 format)
        private int moveTimer;
        private int mappingFrame;

        // Animation state
        private int animFrameIndex;  // index into UNFOLD_FRAMES or FOLD_FRAMES
        private int animDelayCounter;

        /**
         * Create a child platform instance.
         *
         * @param spawn    spawn data (position, subtype from parent)
         * @param xFlipped whether the parent had x_flip set (affects timer duration)
         */
        public SmallMetalPformChildInstance(ObjectSpawn spawn, boolean xFlipped) {
            super(spawn, "SmallMetalPformChild");
            this.xFlipped = xFlipped;
            this.currentX = spawn.x();
            this.currentY = spawn.y();
            this.ySubpixel = 0;

            // ROM: loc_3BC6C (state 0 init)
            // move.b #2,mapping_frame(a0) - start folded
            this.mappingFrame = 2;

            // Set timer based on x_flip
            // btst #render_flags.x_flip,render_flags(a0) / beq.s loc_3BC92
            this.moveTimer = xFlipped ? MOVE_TIMER_FLIPPED : MOVE_TIMER_NORMAL;

            // Set Y velocity based on subtype
            // subi.b #$7E,d0 / move.w word_3BCA8(pc,d0.w),y_vel(a0)
            int subtypeIndex = (spawn.subtype() & 0xFF) - 0x7E;
            this.yVelocity = (subtypeIndex == 0) ? Y_VEL_ASCENDING : Y_VEL_DESCENDING;

            // Start unfold animation (routine_secondary = 2)
            // ROM: AnimateSprite resets anim_frame_duration to 0 on animation change,
            // so on the first tick it immediately decrements to -1 and loads the first frame.
            this.state = ChildState.UNFOLD;
            this.animFrameIndex = -1;
            this.animDelayCounter = 0;

            updateDynamicSpawn(currentX, currentY);
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
        public int getPriorityBucket() {
            // subObjData: priority = 4
            return RenderPriority.clamp(4);
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            switch (state) {
                case UNFOLD -> updateUnfold();
                case MOVE -> updateMove(frameCounter);
                case FOLD -> updateFold();
                case DELETE -> updateDelete();
            }
            updateDynamicSpawn(currentX, currentY);
        }

        // ================================================================
        // State 2: Unfold Animation (loc_3BCAC)
        // ROM: Ani_objBD animation 0 (byte_3BD32: delay 3, frames 2,1,0, $FA)
        // $FA = addq.b #2,routine_secondary(a0) -> advance to state 4
        // ================================================================

        private void updateUnfold() {
            // ROM: AnimateSprite pattern - subq.b #1,anim_frame_duration / bpl Anim_Wait
            animDelayCounter--;
            if (animDelayCounter >= 0) {
                return;
            }
            // Duration expired: load next frame and reset counter
            animFrameIndex++;
            if (animFrameIndex >= UNFOLD_FRAMES.length) {
                // $FA: animation complete, advance routine_secondary to state 4
                state = ChildState.MOVE;
                return;
            }
            animDelayCounter = UNFOLD_ANIM_DELAY;
            mappingFrame = UNFOLD_FRAMES[animFrameIndex];
        }

        // ================================================================
        // State 4: Moving Platform (loc_3BCB6 + loc_3BCDE)
        // ROM: Timer countdown, ObjectMove (Y velocity only), PlatformObject
        // ================================================================

        private void updateMove(int frameCounter) {
            // subq.w #1,objoff_2A(a0) / bmi.s loc_3BCC0
            moveTimer--;
            if (moveTimer < 0) {
                // Timer expired: advance to fold state
                // addq.b #2,routine_secondary(a0)
                // move.b #1,anim(a0) - switch to fold animation
                // ROM: AnimateSprite detects anim change, resets anim_frame_duration to 0
                state = ChildState.FOLD;
                animFrameIndex = -1;
                animDelayCounter = 0;
                return;
            }

            // ROM: loc_3BCDE - ObjectMove + PlatformObject
            // ObjectMove: y_vel is sign-extended to 32-bit, shifted left 8 bits,
            // then added to 32-bit position (16.16 format: upper 16 = integer, lower 16 = subpixel).
            // ext.l d0 / asl.l #8,d0 / add.l d0,d3
            long yPos32 = ((long) currentY << 16) | (ySubpixel & 0xFFFF);
            yPos32 += ((long) yVelocity << 8);
            currentY = (int) (yPos32 >> 16);
            ySubpixel = (int) (yPos32 & 0xFFFF);

            // PlatformObject collision is handled by the engine's SolidObjectProvider
        }

        // ================================================================
        // State 6: Fold Animation (loc_3BCCC)
        // ROM: Ani_objBD animation 1 (byte_3BD38: delay 1, frames 0,1,2, $FA)
        // $FA = addq.b #2,routine_secondary(a0) -> advance to state 8
        // ================================================================

        private void updateFold() {
            // ROM: AnimateSprite pattern - subq.b #1,anim_frame_duration / bpl Anim_Wait
            animDelayCounter--;
            if (animDelayCounter >= 0) {
                return;
            }
            // Duration expired: load next frame and reset counter
            animFrameIndex++;
            if (animFrameIndex >= FOLD_FRAMES.length) {
                // $FA: animation complete, advance routine_secondary to state 8
                state = ChildState.DELETE;
                return;
            }
            animDelayCounter = FOLD_ANIM_DELAY;
            mappingFrame = FOLD_FRAMES[animFrameIndex];
        }

        // ================================================================
        // State 8: Delete (loc_3BCD6)
        // ROM: bsr.w loc_3B7BC (detach standing players) then DeleteObject
        // ================================================================

        private void updateDelete() {
            // loc_3B7BC: detach any riding players (engine handles this via SolidContacts)
            // The engine's ObjectManager automatically detaches riders when an object is destroyed.
            setDestroyed(true);
        }

        // ================================================================
        // Solid Object Interface
        // ================================================================

        @Override
        public SolidObjectParams getSolidParams() {
            // ROM: move.w #$23,d1 / move.w #4,d2 / move.w #5,d3
            return new SolidObjectParams(SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT_AIR, SOLID_HALF_HEIGHT_GND);
        }

        @Override
        public boolean isTopSolidOnly() {
            // PlatformObject = top-solid only
            return true;
        }

        @Override
        public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
            // Solid collision handled by ObjectManager
        }

        // Only provide solid collision during the MOVE state
        @Override
        public boolean isSolidFor(AbstractPlayableSprite player) {
            return state == ChildState.MOVE;
        }

        // ================================================================
        // Rendering
        // ================================================================

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.WFZ_BELT_PLATFORM);
            if (renderer == null) return;
            // ROM: render_flags.x_flip determines horizontal flip
            renderer.drawFrameIndex(mappingFrame, currentX, currentY, xFlipped, false);
        }

        @Override
        public void appendDebugRenderCommands(DebugRenderContext ctx) {
            ctx.drawRect(currentX, currentY, SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT_AIR,
                    0.6f, 0.9f, 0.6f);
            ctx.drawCross(currentX, currentY, 3, 0.6f, 0.9f, 0.6f);
            ctx.drawWorldLabel(currentX, currentY, -1,
                    String.format("BD %s f%d t%d yv%d",
                            state.name(), mappingFrame, moveTimer, yVelocity),
                    DebugColor.GREEN);
        }
}

}
