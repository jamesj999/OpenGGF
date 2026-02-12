package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.debug.DebugRenderContext;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.SolidContact;
import uk.co.jamesj999.sonic.level.objects.SolidObjectListener;
import uk.co.jamesj999.sonic.level.objects.SolidObjectParams;
import uk.co.jamesj999.sonic.level.objects.SolidObjectProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.awt.Color;
import java.util.List;

/**
 * CluckerBase (Object 0xAD) - Wall-mounted turret platform from WFZ.
 * A simple solid object that the Clucker badnik sits on.
 *
 * ROM Reference: s2.asm lines 76778-76806 (ObjAD)
 *
 * Disassembly behavior:
 *   - Init: LoadSubObject with subtype 0x42 -> ObjAD_SubObjData
 *     (mappings=ObjAD_Obj98_MapUnc_395B4, art=ArtNem_WfzScratch, priority=4, width_pixels=$18)
 *     Sets mapping_frame = $C (frame 12)
 *   - Main: SolidObject with d1=$1B, d2=8, d3=8, then MarkObjGone
 *
 * Collision: d1=$1B (27 half-width), d2=8 (top half-height), d3=8 (bottom half-height)
 * No collision_flags (not a touchable enemy).
 */
public class CluckerBaseObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // From disassembly ObjAD_Main: move.w #$1B,d1 / move.w #8,d2 / move.w #8,d3
    private static final int SOLID_HALF_WIDTH = 0x1B;
    private static final int SOLID_TOP_HEIGHT = 0x08;
    private static final int SOLID_BOTTOM_HEIGHT = 0x08;

    // Frame 12 (Map_objAE_010C) = CluckerBase platform sprite
    private static final int MAPPING_FRAME = 12;

    private final boolean xFlipped;

    public CluckerBaseObjectInstance(ObjectSpawn spawn) {
        super(spawn, "CluckerBase");
        this.xFlipped = (spawn.renderFlags() & 0x01) != 0;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(SOLID_HALF_WIDTH, SOLID_TOP_HEIGHT, SOLID_BOTTOM_HEIGHT);
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player,
                               SolidContact contact, int frameCounter) {
        // No special behavior - standard solid collision handled by ObjectManager
    }

    @Override
    public int getPriorityBucket() {
        // ObjAD_SubObjData: priority = 4
        return RenderPriority.clamp(4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.CLUCKER);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        renderer.drawFrameIndex(MAPPING_FRAME, spawn.x(), spawn.y(), xFlipped, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        ctx.drawRect(spawn.x(), spawn.y(), SOLID_HALF_WIDTH, SOLID_TOP_HEIGHT, 0.5f, 0.5f, 1f);
        ctx.drawWorldLabel(spawn.x(), spawn.y(), -2, "CluckerBase", Color.CYAN);
    }
}
