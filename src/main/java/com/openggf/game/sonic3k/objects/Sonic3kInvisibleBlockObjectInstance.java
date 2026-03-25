package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.PlayableEntity;

import java.util.List;

/**
 * Object 0x28 - InvisibleBlock (Sonic 3 &amp; Knuckles).
 * <p>
 * Provides solid collision without visual representation (only renders
 * a debug wireframe when the debug view is enabled).
 * <p>
 * ROM: Obj_InvisibleBlock (sonic3k.asm)
 * <p>
 * Subtype encoding:
 * <ul>
 *   <li>Upper nibble (bits 7-4): width = ((subtype &amp; 0xF0) + 0x10) / 2 = ((n + 1) * 8)</li>
 *   <li>Lower nibble (bits 3-0): height = ((subtype &amp; 0x0F) + 1) * 8</li>
 * </ul>
 * <p>
 * Collision detection adds 11 to the half-width (ROM: addi.w #$B,d1),
 * and uses height + 1 for the ground half-height (ROM: addq.w #1,d3).
 * Calls SolidObjectFull2 for full solid object collision.
 */
public class Sonic3kInvisibleBlockObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    /** Gray color for debug wireframe rendering. */
    private static final float DEBUG_R = 0.5f;
    private static final float DEBUG_G = 0.5f;
    private static final float DEBUG_B = 0.5f;

    private final int halfWidth;
    private final int halfHeight;

    public Sonic3kInvisibleBlockObjectInstance(ObjectSpawn spawn) {
        super(spawn, "InvisibleBlock");

        int subtype = spawn.subtype();
        // ROM: andi.w #$F0,d0 / addi.w #$10,d0 / lsr.w #1,d0
        // Simplifies to: ((upperNibble + 1) * 8)
        this.halfWidth = (((subtype >> 4) & 0xF) + 1) * 8;
        // ROM: andi.w #$F,d1 / addq.w #1,d1 / lsl.w #3,d1
        // = ((lowerNibble + 1) * 8)
        this.halfHeight = ((subtype & 0xF) + 1) * 8;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // ROM: addi.w #$B,d1 (width + 11 for collision detection)
        // d2 = height_pixels (air half-height)
        // d3 = height_pixels + 1 (ground half-height)
        int d1 = halfWidth + 11;
        int d2 = halfHeight;
        int d3 = halfHeight + 1;
        return new SolidObjectParams(d1, d2, d3);
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity,
                               SolidContact contact, int frameCounter) {
        // No special behavior - standard collision handled by ObjectManager.
    }

    @Override
    public int getPriorityBucket() {
        // ROM: move.w #$200,priority(a0) -> bucket 4
        return RenderPriority.clamp(4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Only render in debug mode (invisible block).
        if (!isDebugViewEnabled()) {
            return;
        }

        int centerX = spawn.x();
        int centerY = spawn.y();

        int left = centerX - halfWidth;
        int right = centerX + halfWidth;
        int top = centerY - halfHeight;
        int bottom = centerY + halfHeight;

        // Draw wireframe rectangle
        appendLine(commands, left, top, right, top);
        appendLine(commands, right, top, right, bottom);
        appendLine(commands, right, bottom, left, bottom);
        appendLine(commands, left, bottom, left, top);

        // Draw center crosshair
        int crossHalf = Math.min(halfWidth, halfHeight) / 2;
        if (crossHalf > 0) {
            appendLine(commands, centerX - crossHalf, centerY, centerX + crossHalf, centerY);
            appendLine(commands, centerX, centerY - crossHalf, centerX, centerY + crossHalf);
        }
    }

    private void appendLine(List<GLCommand> commands, int x1, int y1, int x2, int y2) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                DEBUG_R, DEBUG_G, DEBUG_B, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                DEBUG_R, DEBUG_G, DEBUG_B, x2, y2, 0, 0));
    }

    private boolean isDebugViewEnabled() {
        return SonicConfigurationService.getInstance()
                .getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
    }
}
