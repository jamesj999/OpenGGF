package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;

import java.util.List;

/**
 * Object 0x09 - AIZ Act 1 Tree.
 * <p>
 * A static decorative terrain filler in Angel Island Zone Act 1.
 * Uses level primary art tiles 0x39-0x3C (art_tile base 1 + mapping tile 0x38),
 * which are solid terrain-fill patterns with no transparent pixels.
 * <p>
 * In the real Mega Drive, this sprite renders ON TOP of low-priority Plane A
 * terrain tiles (VDP compositing rule: low-pri sprites above low-pri planes).
 * The sprite's solid fill is identical to the underlying terrain pixels, making
 * it invisible. Where no FG terrain exists, the fill extends the canopy.
 * <p>
 * In our GPU tilemap renderer, the FG terrain already renders all tile data.
 * The tree sprite is redundant and creates visible colored rectangles where
 * the sprite's terrain-fill tiles don't exactly match the FG tiles at that
 * position. Rendering is suppressed to avoid this artifact.
 */
public class Aiz1TreeObjectInstance extends AbstractObjectInstance {

    public Aiz1TreeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "AIZ1Tree");
    }

    @Override
    public int getPriorityBucket() {
        // ROM priority 0x180 / 0x80 = 3
        return 3;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // No-op: terrain filler sprite suppressed (see class javadoc)
    }
}
