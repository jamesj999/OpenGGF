package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Breakable rock child spawned by CutsceneKnucklesAiz1Instance.
 * ROM: loc_61F60 (sonic3k.asm:128755)
 *
 * Phase 1 (Init/Wait): Spawned by Knuckles init routine. Draws itself each
 * frame and polls the parent's status bit 7 (triggered flag).
 *
 * Phase 2 (Break): When the parent is triggered, increments mapping_frame,
 * calls BreakObjectToPieces to scatter fragment sprites, and deletes self.
 *
 * The actual BreakObjectToPieces creates fragment sprites with scattered
 * velocities. For now we mark the rock as destroyed when triggered; the
 * fragment visual effect is cosmetic and can be refined later.
 */
public class CutsceneKnucklesRockChild extends AbstractObjectInstance {
    private static final Logger LOG = Logger.getLogger(CutsceneKnucklesRockChild.class.getName());

    /** Parent Knuckles object whose triggered flag we poll. */
    private final CutsceneKnucklesAiz1Instance parent;

    /** Current mapping frame index. Incremented on break. */
    private int mappingFrame;

    /** Whether the rock has already been broken. */
    private boolean broken;

    public CutsceneKnucklesRockChild(ObjectSpawn spawn, CutsceneKnucklesAiz1Instance parent) {
        super(spawn, "CutsceneKnuxRock");
        this.parent = parent;
        this.mappingFrame = 0;
        this.broken = false;
    }

    @Override
    public int getX() {
        return spawn.x();
    }

    @Override
    public int getY() {
        return spawn.y();
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (broken || isDestroyed()) {
            return;
        }

        // Phase 1: poll parent's triggered flag each frame
        if (parent != null && parent.isTriggered()) {
            // Phase 2: break apart
            mappingFrame++;
            broken = true;
            LOG.fine("Rock child: breaking apart (mapping_frame=" + mappingFrame + ")");
            // TODO: Spawn fragment sprites with scattered velocities (BreakObjectToPieces)
            setDestroyed(true);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = AizIntroArtLoader.getKnucklesRenderer();
        if (renderer == null || !renderer.isReady()) return;
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }

    // -----------------------------------------------------------------------
    // Accessors (for testing / external use)
    // -----------------------------------------------------------------------

    public int getMappingFrame() {
        return mappingFrame;
    }

    public boolean isBroken() {
        return broken;
    }
}
