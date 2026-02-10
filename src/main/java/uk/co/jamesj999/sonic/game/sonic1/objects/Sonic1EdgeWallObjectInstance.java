package uk.co.jamesj999.sonic.game.sonic1.objects;

import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.SolidContact;
import uk.co.jamesj999.sonic.level.objects.SolidObjectListener;
import uk.co.jamesj999.sonic.level.objects.SolidObjectParams;
import uk.co.jamesj999.sonic.level.objects.SolidObjectProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * GHZ Edge Walls (Object ID 0x44).
 * <p>
 * Solid wall barriers used exclusively in Green Hill Zone. The subtype byte controls
 * both the visual frame and whether the wall provides solid collision:
 * <ul>
 *   <li>Bit 4 = 0: Solid wall (collision active)</li>
 *   <li>Bit 4 = 1: Display-only decoration (no collision)</li>
 * </ul>
 * The frame index is the subtype with bit 4 cleared:
 * <ul>
 *   <li>Frame 0: Light top + shadow body</li>
 *   <li>Frame 1: All light</li>
 *   <li>Frame 2: All dark/shadow</li>
 * </ul>
 * <p>
 * Collision dimensions from disassembly: d1 = $13 (half-width 19px), d2 = $28 (half-height 40px).
 * Uses custom Obj44_SolidWall routine which pushes player sideways (zeroing inertia + X velocity)
 * and blocks upward movement through ceiling.
 * <p>
 * Reference: docs/s1disasm/_incObj/44 GHZ Edge Walls.asm
 */
public class Sonic1EdgeWallObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // From disassembly: move.w #$13,d1
    private static final int HALF_WIDTH = 0x13;

    // From disassembly: move.w #$28,d2
    private static final int HALF_HEIGHT = 0x28;

    private final int frameIndex;
    private final boolean solid;

    public Sonic1EdgeWallObjectInstance(ObjectSpawn spawn) {
        super(spawn, "EdgeWall");

        int subtype = spawn.subtype();
        // From disassembly: bclr #4,obFrame(a0) / beq.s Edge_Solid
        this.solid = (subtype & 0x10) == 0;
        // Frame = subtype with bit 4 cleared
        this.frameIndex = subtype & ~0x10;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.GHZ_EDGE_WALL);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(frameIndex, getX(), getY(), false, false);
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(HALF_WIDTH, HALF_HEIGHT, HALF_HEIGHT);
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return solid;
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // No special behavior beyond standard solid collision.
        // The engine's SolidContacts system handles side pushing (zeroing inertia/velX)
        // and ceiling blocking (zeroing velY), matching Obj44_SolidWall behavior.
    }
}
