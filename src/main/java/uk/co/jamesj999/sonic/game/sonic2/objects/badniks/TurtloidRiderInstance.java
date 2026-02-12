package uk.co.jamesj999.sonic.game.sonic2.objects.badniks;

import uk.co.jamesj999.sonic.debug.DebugRenderContext;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseAttackable;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.objects.TouchResponseResult;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.awt.Color;
import java.util.List;

/**
 * Turtloid Rider (0x9B) - Rides on the Turtloid turtle in Sky Chase Zone.
 * Follows parent Turtloid at offset (+4, -$18). Has enemy touch response.
 *
 * Based on disassembly Obj9B (s2.asm:74420-74477).
 */
public class TurtloidRiderInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable {

    // Offset from parent: word_37A2C = dc.w 4, dc.w -$18
    private static final int X_OFFSET = 4;
    private static final int Y_OFFSET = -0x18;

    // Collision: Obj9B_SubObjData collision=$1A -> enemy (0x00) + size 0x1A
    private static final int COLLISION_SIZE_INDEX = 0x1A;

    private final TurtloidBadnikInstance parent;
    private int currentX;
    private int currentY;
    private int mappingFrame;
    private boolean destroyed;

    public TurtloidRiderInstance(ObjectSpawn spawn, TurtloidBadnikInstance parent) {
        super(spawn, "TurtloidRider");
        this.parent = parent;
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.mappingFrame = 2; // Initial mapping_frame from disassembly
        this.destroyed = false;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (destroyed || parent.isParentDestroyed()) {
            setDestroyed(true);
            return;
        }

        // Follow parent at fixed offset
        // ROM: loc_37A30 - copy parent position, add offsets from word_37A2C
        currentX = parent.getParentX() + X_OFFSET;
        currentY = parent.getParentY() + Y_OFFSET;
    }

    /** Called by parent when rider should change frame (e.g., shooting pose). */
    public void setMappingFrame(int frame) {
        this.mappingFrame = frame;
    }

    @Override
    public int getCollisionFlags() {
        // ENEMY category (0x00) + size index 0x1A
        return 0x00 | (COLLISION_SIZE_INDEX & 0x3F);
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public void onPlayerAttack(AbstractPlayableSprite player, TouchResponseResult result) {
        if (destroyed) {
            return;
        }
        // ROM parity: destroying the rider should not destroy the turtle base.
        destroyed = true;
        setDestroyed(true);
        parent.onRiderDestroyed(currentX, currentY, player);
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(currentX, currentY, spawn.objectId(),
                spawn.subtype(), spawn.renderFlags(), spawn.respawnTracked(), spawn.rawYWord());
    }

    @Override
    public int getX() { return currentX; }

    @Override
    public int getY() { return currentY; }

    @Override
    public int getPriorityBucket() {
        // ROM: priority = 4 (Obj9B_SubObjData)
        return RenderPriority.clamp(4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (destroyed) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.TURTLOID);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Rider uses frames 2 (normal) and 3 (shooting) from shared Turtloid sheet
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, false, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        ctx.drawRect(currentX, currentY, 12, 12, 1f, 0.5f, 0f);
        ctx.drawWorldLabel(currentX, currentY, -2, "Rider f" + mappingFrame, Color.ORANGE);
    }
}
