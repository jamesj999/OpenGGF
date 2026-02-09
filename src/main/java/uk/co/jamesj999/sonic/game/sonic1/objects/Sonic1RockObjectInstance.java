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
 * Sonic 1 Purple Rock (GHZ) - Object ID 0x3B.
 * <p>
 * A static solid object found in Green Hill Zone. No subtypes, no movement,
 * no animation - just a solid rock that the player can stand on and collide with.
 * <p>
 * From disassembly: d1 = $1B (halfWidth), d2 = $10 (airHalfHeight),
 * d3 = $10 (groundHalfHeight), calls SolidObject.
 * <p>
 * Reference: docs/s1disasm/_incObj/3B Purple Rock.asm
 */
public class Sonic1RockObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // From disassembly: move.w #$1B,d1
    private static final int HALF_WIDTH = 0x1B;

    // From disassembly: move.w #$10,d2 / move.w #$10,d3
    private static final int HALF_HEIGHT = 0x10;

    // From disassembly: move.b #4,obPriority(a0)
    private static final int PRIORITY = 4;

    public Sonic1RockObjectInstance(ObjectSpawn spawn) {
        super(spawn, "PurpleRock");
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.ROCK);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(0, getX(), getY(), false, false);
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(HALF_WIDTH, HALF_HEIGHT, HALF_HEIGHT);
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // No special behavior - standard solid collision handled by ObjectManager
    }
}
