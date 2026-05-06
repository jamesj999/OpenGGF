package com.openggf.game.sonic3k.objects;

import com.openggf.game.rewind.RewindTransient;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Shared render scaffold for visible CNZ traversal objects claimed before their
 * ROM-accurate behavior lands.
 *
 * <p>Task 1 intentionally ports registry ownership and ROM-backed sheets ahead
 * of gameplay logic, but visible objects still need to draw their first mapped
 * frame so the level does not regress from placeholder visuals to invisibility.
 * Concrete tasks replace this scaffold with full stateful behavior later.
 */
abstract class AbstractCnzTraversalVisibleStubInstance extends AbstractObjectInstance {

    private final String artKey;
    @RewindTransient(reason = "placeholder renderer fallback; recreated from live object state")
    private PlaceholderObjectInstance placeholder;

    protected AbstractCnzTraversalVisibleStubInstance(ObjectSpawn spawn, String name, String artKey) {
        super(spawn, name);
        this.artKey = artKey;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager != null) {
            PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
            if (renderer != null && renderer.isReady()) {
                boolean hFlip = (spawn.renderFlags() & 0x01) != 0;
                boolean vFlip = (spawn.renderFlags() & 0x02) != 0;
                renderer.drawFrameIndex(initialFrameIndex(), spawn.x(), spawn.y(), hFlip, vFlip);
                return;
            }
        }

        if (placeholder == null) {
            placeholder = new PlaceholderObjectInstance(spawn, name);
        }
        placeholder.appendRenderCommands(commands);
    }

    /**
     * Returns the frame used by the Task 1 visual scaffold.
     *
     * <p>Later tasks replace this with full routine-driven animation, but the
     * stopgap render path should still honor any ROM-defined initial mapping
     * frame or subtype-based starting frame.
     */
    protected int initialFrameIndex() {
        return 0;
    }
}
