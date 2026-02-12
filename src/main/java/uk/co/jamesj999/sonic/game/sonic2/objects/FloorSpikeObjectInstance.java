package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.debug.DebugRenderContext;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.awt.Color;
import java.util.List;

/**
 * Floor Spike (Object 0x6D) - Retractable spike from Metropolis Zone.
 * <p>
 * A spike that periodically extends upward from its base position and retracts.
 * Timing is synchronized to the level frame counter via the subtype byte, allowing
 * multiple spikes to extend/retract at different phases.
 * <p>
 * ROM reference: s2.asm Obj6D (lines 53407-53477)
 * <ul>
 *   <li>collision_flags = $84 (harmful spike, size index 4)</li>
 *   <li>Art: ArtNem_MtzSpike, palette line 1</li>
 *   <li>Mappings: frame 0 from Map_obj68 (shared with SpikyBlock)</li>
 *   <li>Movement: 16.8 fixed-point offset, +/-$400 per frame, max $2000</li>
 *   <li>Subtype: timing offset for level frame counter synchronization</li>
 * </ul>
 */
public class FloorSpikeObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider {

    // From disassembly: move.b #$84,collision_flags(a0)
    // Top nibble $8 = harmful/spike, bottom nibble $4 = size index
    private static final int COLLISION_FLAGS = 0x84;

    // From disassembly: addi.w #$400,floorspike_offset(a0)
    private static final int OFFSET_STEP = 0x400;

    // From disassembly: cmpi.w #$2000,floorspike_offset(a0)
    private static final int OFFSET_MAX = 0x2000;

    // From disassembly: move.w #3,floorspike_delay(a0)
    private static final int EXPANDED_DELAY = 3;

    // From disassembly: andi.b #$7F,d0
    private static final int TIMING_MASK = 0x7F;

    // From disassembly: move.b #4,priority(a0)
    private static final int PRIORITY = 4;

    // From disassembly: move.b #4,width_pixels(a0)
    private static final int WIDTH_PIXELS = 4;

    // Initial position (floorspike_initial_x_pos / floorspike_initial_y_pos)
    private final int initialX;
    private final int initialY;

    // Current position (updated each frame from offset)
    private int currentX;
    private int currentY;

    // floorspike_offset (objoff_34) - 16.8 fixed-point Y extension
    private int offset;

    // floorspike_position (objoff_36) - 0 = expanding, 1 = retracting
    private int position;

    // floorspike_waiting (objoff_38) - 0 = moving, 1 = waiting for timer sync
    private int waiting;

    // floorspike_delay (objoff_3A) - frames to wait after full expansion
    private int delay;

    // Dynamic spawn for position updates (used by touch collision system)
    private ObjectSpawn dynamicSpawn;

    public FloorSpikeObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.initialX = spawn.x();
        this.initialY = spawn.y();
        this.currentX = initialX;
        this.currentY = initialY;
        this.dynamicSpawn = spawn;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        updateAction(frameCounter);

        // ROM: moveq #0,d0 / move.b floorspike_offset(a0),d0 / neg.w d0
        //      add.w floorspike_initial_y_pos(a0),d0 / move.w d0,y_pos(a0)
        // Note: offset is 16.8 fixed-point, but only the byte at objoff_34 is read
        // (move.b reads the high byte of the word), so effective pixel offset = offset >> 8
        int pixelOffset = (offset >> 8) & 0xFF;
        currentY = initialY - pixelOffset;
        currentX = initialX;

        updateDynamicSpawn();
    }

    /**
     * ROM: Obj6D_Action (s2.asm line 53442-53477)
     * Handles the expand/retract/wait cycle.
     */
    private void updateAction(int frameCounter) {
        // ROM: tst.w floorspike_delay(a0) / beq.s + / subq.w #1,floorspike_delay(a0) / rts
        if (delay > 0) {
            delay--;
            return;
        }

        // ROM: tst.w floorspike_waiting(a0) / beq.s +
        if (waiting != 0) {
            // ROM: move.b (Level_frame_counter+1).w,d0 / sub.b subtype(a0),d0
            //      andi.b #$7F,d0 / bne.s Obj6D_Action_End
            int timingValue = (frameCounter & 0xFF) - (spawn.subtype() & 0xFF);
            if ((timingValue & TIMING_MASK) != 0) {
                return;
            }
            // ROM: clr.w floorspike_waiting(a0)
            waiting = 0;
        }

        // ROM: tst.w floorspike_position(a0) / beq.s Obj6D_Expanding
        if (position != 0) {
            // Retracting: subi.w #$400,floorspike_offset(a0)
            offset -= OFFSET_STEP;
            if (offset < 0) {
                // ROM: move.w #0,floorspike_offset(a0)
                //      move.w #0,floorspike_position(a0)
                //      move.w #1,floorspike_waiting(a0)
                offset = 0;
                position = 0;
                waiting = 1;
            }
        } else {
            // Expanding: addi.w #$400,floorspike_offset(a0)
            offset += OFFSET_STEP;
            if (offset >= OFFSET_MAX) {
                // ROM: move.w #$2000,floorspike_offset(a0)
                //      move.w #1,floorspike_position(a0)
                //      move.w #3,floorspike_delay(a0)
                offset = OFFSET_MAX;
                position = 1;
                delay = EXPANDED_DELAY;
            }
        }
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
    public ObjectSpawn getSpawn() {
        return dynamicSpawn;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    // TouchResponseProvider - collision_flags $84 (harmful spike)
    @Override
    public int getCollisionFlags() {
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.MTZ_FLOOR_SPIKE);
        if (renderer != null && renderer.isReady()) {
            // Frame 0: vertical spike pointing up
            renderer.drawFrameIndex(0, currentX, currentY, false, false);
        }
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Red rectangle for harmful touch region
        ctx.drawRect(currentX, currentY, WIDTH_PIXELS, 16, 1f, 0f, 0f);

        // Cross at initial position
        ctx.drawCross(initialX, initialY, 3, 0.5f, 0.5f, 0.5f);

        // State label
        String state;
        if (delay > 0) {
            state = "delay=" + delay;
        } else if (waiting != 0) {
            state = "wait";
        } else if (position != 0) {
            state = "retract";
        } else {
            state = "expand";
        }
        int pxOffset = (offset >> 8) & 0xFF;
        String label = name + " " + state + " ofs=" + pxOffset;
        ctx.drawWorldLabel(currentX, currentY, -2, label, Color.RED);
    }

    private void updateDynamicSpawn() {
        if (dynamicSpawn.x() == currentX && dynamicSpawn.y() == currentY) {
            return;
        }
        dynamicSpawn = new ObjectSpawn(
                currentX,
                currentY,
                spawn.objectId(),
                spawn.subtype(),
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
    }
}
