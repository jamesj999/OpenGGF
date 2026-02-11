package uk.co.jamesj999.sonic.game.sonic1.objects;

import uk.co.jamesj999.sonic.debug.DebugRenderContext;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.SolidContact;
import uk.co.jamesj999.sonic.level.objects.SolidObjectListener;
import uk.co.jamesj999.sonic.level.objects.SolidObjectParams;
import uk.co.jamesj999.sonic.level.objects.SolidObjectProvider;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Sonic 1 Invisible Barriers (Object ID 0x71).
 * <p>
 * Invisible solid barriers used across multiple zones (MZ, LZ, SYZ, SBZ).
 * These are purely invisible collision objects with no visual representation
 * during normal gameplay. Only visible in debug mode (displays using monitor art).
 * <p>
 * Subtype byte encoding:
 * <ul>
 *   <li>Upper nibble (bits 7-4): Width control. halfWidth = ((upper & 0xF0) + 0x10) >> 1</li>
 *   <li>Lower nibble (bits 3-0): Height control. height = ((lower & 0x0F) + 1) << 3</li>
 * </ul>
 * <p>
 * Solid collision uses SolidObject71 variant which differs from standard SolidObject
 * in that it skips the obRender on-screen check when first entering collision,
 * going directly to loc_FAD0. The half-width passed to the solid routine includes
 * an extra 11px padding: d1 = obActWid + 0xB.
 * <p>
 * Known subtypes:
 * <ul>
 *   <li>0x00: 8px half-width, 8px height</li>
 *   <li>0x11: 16px half-width, 16px height</li>
 *   <li>0x13: 16px half-width, 32px height</li>
 *   <li>0x31: 32px half-width, 16px height</li>
 * </ul>
 * <p>
 * Reference: docs/s1disasm/_incObj/71 Invisible Barriers.asm
 */
public class Sonic1InvisibleBarrierObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // From disassembly: addi.w #$B,d1 (padding added to obActWid before SolidObject71 call)
    private static final int WIDTH_PADDING = 0x0B;

    private final int halfWidth;
    private final int height;

    public Sonic1InvisibleBarrierObjectInstance(ObjectSpawn spawn) {
        super(spawn, "InvisibleBarrier");

        int subtype = spawn.subtype();

        // From disassembly:
        // move.b obSubtype(a0),d0
        // move.b d0,d1
        // andi.w #$F0,d0   ; upper nibble (already shifted: $10, $20, $30 etc.)
        // addi.w #$10,d0   ; add $10
        // lsr.w  #1,d0     ; divide by 2
        // move.b d0,obActWid(a0)
        int upper = subtype & 0xF0;
        this.halfWidth = (upper + 0x10) >> 1;

        // From disassembly:
        // andi.w #$F,d1    ; lower nibble (0-F)
        // addq.w #1,d1     ; add 1
        // lsl.w  #3,d1     ; multiply by 8
        // move.b d1,obHeight(a0)
        int lower = subtype & 0x0F;
        this.height = (lower + 1) << 3;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // From disassembly: object is only displayed when debug mode is active.
        // tst.w (v_debuguse).w / beq.s .nodisplay
        // Normal gameplay: completely invisible, no rendering.
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // From disassembly:
        // d1 = obActWid + $B   (half-width with padding)
        // d2 = obHeight        (air half-height)
        // d3 = d2 + 1          (ground half-height = air + 1)
        int solidHalfWidth = halfWidth + WIDTH_PADDING;
        int airHalfHeight = height;
        int groundHalfHeight = height + 1;
        return new SolidObjectParams(solidHalfWidth, airHalfHeight, groundHalfHeight);
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // No special behavior - standard solid collision handled by ObjectManager.
        // SolidObject71 handles standing/leaving/side collision identically to SolidObject.
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Draw the solid collision bounds in cyan to distinguish from other solid objects
        int x = getX();
        int y = getY();
        int solidHalfWidth = halfWidth + WIDTH_PADDING;
        ctx.drawRect(x, y, solidHalfWidth, height, 0.0f, 1.0f, 1.0f);
        ctx.drawCross(x, y, 3, 0.0f, 1.0f, 1.0f);
    }
}
