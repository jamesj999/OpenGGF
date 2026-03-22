package com.openggf.game.sonic2.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.MultiPieceSolidProvider;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x70 - Giant rotating cog from Metropolis Zone.
 * <p>
 * A large gear mechanism with 8 teeth arranged in a circle. The teeth rotate
 * around the center position, advancing one step every 16 frames. Each tooth
 * provides solid collision and renders using the MtzWheel art.
 * <p>
 * <b>Disassembly Reference:</b> s2.asm lines 54607-54783 (Obj70 code)
 * <p>
 * <b>Rotation direction:</b> Controlled by X-flip (render_flags bit 0):
 * <ul>
 *   <li>Normal (bit 0 clear): Clockwise rotation (phase advances by $18)</li>
 *   <li>X-flip (bit 0 set): Counter-clockwise rotation (phase decreases by $18)</li>
 * </ul>
 * <p>
 * <b>Structure:</b>
 * 8 teeth, each with 3-byte position entries (x_offset, y_offset, mapping_frame).
 * 4 rotation steps × 8 teeth = 32 position entries in Obj70_Positions table.
 * The rotation phase (objoff_36) cycles 0 → $18 → $30 → $48 → 0 (or reverse).
 * Each tooth has an objoff_34 offset (0,3,6,...,21) into the position table.
 * <p>
 * <b>Per-frame collision sizes from byte_28706:</b>
 * Most frames use 16×16, but frames 7/9 use 16×12 and frame 8 uses 16×8
 * (the tooth appears thinner when rotated to top/bottom position).
 */
public class CogObjectInstance extends AbstractObjectInstance
        implements MultiPieceSolidProvider, SolidObjectListener {

    // Number of teeth on the cog
    private static final int NUM_TEETH = 8;

    // Phase advance per rotation step (every 16 frames)
    // From disassembly: addi.w #$18,d1 / subi.w #$18,d1
    private static final int PHASE_STEP = 0x18;

    // Maximum rotation phase before wrapping
    // From disassembly: cmpi.w #$60,d1 / blo.s (for CW); bcc.s (for CCW after sub)
    private static final int PHASE_MAX = 0x60;

    // Maximum position offset before wrapping
    // From disassembly: cmpi.w #$18,objoff_34(a0) / blo.s (for CW)
    private static final int OFFSET_MAX = 0x18;

    // Render priority from disassembly: move.b #4,priority(a1)
    private static final int PRIORITY = 4;

    // Obj70_Positions table (s2.asm lines 54741-54778)
    // 4 rotation steps × 8 teeth × 3 bytes (x_offset, y_offset, mapping_frame)
    // Values are signed bytes for x/y offsets.
    private static final byte[] POSITIONS = {
            // Step 0 (phase offset 0x00)
            0x00, (byte) 0xB8, 0x00,
            0x32, (byte) 0xCE, 0x04,
            0x48, 0x00, 0x08,
            0x32, 0x32, 0x0C,
            0x00, 0x48, 0x10,
            (byte) 0xCE, 0x32, 0x14,
            (byte) 0xB8, 0x00, 0x18,
            (byte) 0xCE, (byte) 0xCE, 0x1C,

            // Step 1 (phase offset 0x18)
            0x0D, (byte) 0xB8, 0x01,
            0x3F, (byte) 0xDA, 0x05,
            0x48, 0x0C, 0x09,
            0x27, 0x3C, 0x0D,
            (byte) 0xF3, 0x48, 0x11,
            (byte) 0xC1, 0x26, 0x15,
            (byte) 0xB8, (byte) 0xF4, 0x19,
            (byte) 0xD9, (byte) 0xC4, 0x1D,

            // Step 2 (phase offset 0x30)
            0x19, (byte) 0xBC, 0x02,
            0x46, (byte) 0xE9, 0x06,
            0x46, 0x17, 0x0A,
            0x19, 0x44, 0x0E,
            (byte) 0xE7, 0x44, 0x12,
            (byte) 0xBA, 0x17, 0x16,
            (byte) 0xBA, (byte) 0xE9, 0x1A,
            (byte) 0xE7, (byte) 0xBC, 0x1E,

            // Step 3 (phase offset 0x48)
            0x27, (byte) 0xC4, 0x03,
            0x48, (byte) 0xF4, 0x07,
            0x3F, 0x26, 0x0B,
            0x0D, 0x48, 0x0F,
            (byte) 0xD9, 0x3C, 0x13,
            (byte) 0xB8, 0x0C, 0x17,
            (byte) 0xC1, (byte) 0xDA, 0x1B,
            (byte) 0xF3, (byte) 0xB8, 0x1F,
    };

    // byte_28706: per-frame collision sizes {halfWidth, yRadius}
    // Indexed by mapping_frame (0-15 visible, mirrored in high frames)
    // From s2.asm lines 54723-54739
    private static final int[][] COLLISION_SIZES = {
            {0x10, 0x10},  // frame 0
            {0x10, 0x10},  // frame 1
            {0x10, 0x10},  // frame 2
            {0x10, 0x10},  // frame 3
            {0x10, 0x10},  // frame 4
            {0x10, 0x10},  // frame 5
            {0x10, 0x10},  // frame 6
            {0x10, 0x0C},  // frame 7
            {0x10, 0x08},  // frame 8
            {0x10, 0x0C},  // frame 9
            {0x10, 0x10},  // frame 10
            {0x10, 0x10},  // frame 11
            {0x10, 0x10},  // frame 12
            {0x10, 0x10},  // frame 13
            {0x10, 0x10},  // frame 14
            {0x10, 0x10},  // frame 15
    };

    // Instance state
    private final int baseX;      // objoff_32 - center X position
    private final int baseY;      // objoff_30 - center Y position
    private final boolean ccw;    // Counter-clockwise rotation (status.npc.x_flip)

    // Rotation state
    private int rotationPhase;    // objoff_36 - current rotation phase (0, $18, $30, $48)

    // Per-tooth state (computed each frame)
    private final int[] toothX = new int[NUM_TEETH];
    private final int[] toothY = new int[NUM_TEETH];
    private final int[] toothFrame = new int[NUM_TEETH];
    // Per-tooth position offset into POSITIONS table (objoff_34 per child)
    private final int[] toothOffset = new int[NUM_TEETH];

    public CogObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.baseX = spawn.x();
        this.baseY = spawn.y();

        // X-flip controls rotation direction (status.npc.x_flip = render_flags bit 0)
        this.ccw = (spawn.renderFlags() & 0x01) != 0;

        // Initialize per-tooth position offsets (objoff_34)
        // From disassembly: d4 starts at 0, increments by 3 per tooth
        for (int i = 0; i < NUM_TEETH; i++) {
            toothOffset[i] = i * 3;
        }

        // Initial rotation phase is 0
        this.rotationPhase = 0;

        // Calculate initial positions
        updateToothPositions();
    }

    @Override
    public int getX() {
        return baseX;
    }

    @Override
    public int getY() {
        return baseY;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (isDestroyed()) {
            return;
        }

        // ROM: move.b (Level_frame_counter+1).w,d0 / andi.w #$F,d0 / bne.s loc_286CA
        // Advance rotation every 16 frames
        if ((frameCounter & 0x0F) == 0) {
            advanceRotation();
        }

        updateToothPositions();
    }

    /**
     * Advances the rotation phase and wraps the position offsets.
     * <p>
     * CW (normal): phase advances by $18, wraps at $60 → 0.
     * When phase wraps, each tooth's offset advances by 3.
     * <p>
     * CCW (x_flip): phase decreases by $18, wraps below 0 → $48.
     * When phase wraps, each tooth's offset decreases by 3.
     * <p>
     * Disassembly: s2.asm lines 54666-54686
     */
    private void advanceRotation() {
        if (ccw) {
            // Counter-clockwise: subi.w #$18,d1
            rotationPhase -= PHASE_STEP;
            if (rotationPhase < 0) {
                rotationPhase = 0x48;
                // Advance each tooth's offset backward: subq.w #3,objoff_34
                for (int i = 0; i < NUM_TEETH; i++) {
                    toothOffset[i] -= 3;
                    if (toothOffset[i] < 0) {
                        // Wrap: move.w #$15,objoff_34 (= 21 = 7*3)
                        toothOffset[i] = 0x15;
                    }
                }
            }
        } else {
            // Clockwise: addi.w #$18,d1
            rotationPhase += PHASE_STEP;
            if (rotationPhase >= PHASE_MAX) {
                rotationPhase = 0;
                // Advance each tooth's offset forward: addq.w #3,objoff_34
                for (int i = 0; i < NUM_TEETH; i++) {
                    toothOffset[i] += 3;
                    if (toothOffset[i] >= OFFSET_MAX) {
                        // Wrap: move.w #0,objoff_34
                        toothOffset[i] = 0;
                    }
                }
            }
        }
    }

    /**
     * Updates all tooth positions from the POSITIONS table.
     * <p>
     * Each tooth's table index = rotationPhase + toothOffset[i].
     * From disassembly: add.w objoff_34(a0),d1 / lea Obj70_Positions(pc,d1.w),a1
     */
    private void updateToothPositions() {
        for (int i = 0; i < NUM_TEETH; i++) {
            int tableIndex = rotationPhase + toothOffset[i];
            int xOff = POSITIONS[tableIndex];      // signed byte
            int yOff = POSITIONS[tableIndex + 1];   // signed byte
            int frame = POSITIONS[tableIndex + 2] & 0xFF;

            toothX[i] = baseX + xOff;
            toothY[i] = baseY + yOff;
            toothFrame[i] = frame;
        }
    }

    /**
     * Returns collision parameters for a specific tooth based on its mapping frame.
     * From byte_28706 table (s2.asm line 54723).
     */
    private SolidObjectParams getParamsForFrame(int mappingFrame) {
        // ROM: mapping_frame * 2, andi.w #$1E - index into 16-entry table
        int index = mappingFrame & 0x0F;
        int halfWidth = COLLISION_SIZES[index][0];
        int yRadius = COLLISION_SIZES[index][1];
        return new SolidObjectParams(halfWidth, yRadius, yRadius);
    }

    // MultiPieceSolidProvider implementation

    @Override
    public int getPieceCount() {
        return NUM_TEETH;
    }

    @Override
    public int getPieceX(int pieceIndex) {
        return toothX[pieceIndex];
    }

    @Override
    public int getPieceY(int pieceIndex) {
        return toothY[pieceIndex];
    }

    @Override
    public SolidObjectParams getPieceParams(int pieceIndex) {
        return getParamsForFrame(toothFrame[pieceIndex]);
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // Default params (used if getPieceParams not called)
        return new SolidObjectParams(0x10, 0x10, 0x10);
    }

    @Override
    public boolean isTopSolidOnly() {
        // Obj70 uses SolidObject (JmpTo16_SolidObject), fully solid from all sides
        return false;
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        return !isDestroyed();
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        // No special handling needed
    }

    // Rendering

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.MTZ_WHEEL);
        if (renderer == null) return;

        for (int i = 0; i < NUM_TEETH; i++) {
            renderer.drawFrameIndex(toothFrame[i], toothX[i], toothY[i], false, false);
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Draw collision bounds for each tooth
        for (int i = 0; i < NUM_TEETH; i++) {
            SolidObjectParams params = getParamsForFrame(toothFrame[i]);
            int left = toothX[i] - params.halfWidth();
            int right = toothX[i] + params.halfWidth();
            int top = toothY[i] - params.airHalfHeight();
            int bottom = toothY[i] + params.groundHalfHeight();

            ctx.drawLine(left, top, right, top, 0.0f, 1.0f, 0.0f);
            ctx.drawLine(right, top, right, bottom, 0.3f, 0.7f, 0.3f);
            ctx.drawLine(right, bottom, left, bottom, 0.3f, 0.7f, 0.3f);
            ctx.drawLine(left, bottom, left, top, 0.3f, 0.7f, 0.3f);
        }

        // Center cross (yellow)
        ctx.drawLine(baseX - 4, baseY, baseX + 4, baseY, 1.0f, 1.0f, 0.0f);
        ctx.drawLine(baseX, baseY - 4, baseX, baseY + 4, 1.0f, 1.0f, 0.0f);
    }

}
