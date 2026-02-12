package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.debug.DebugRenderContext;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
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
 * SpikyBlock (Object 0x68) - Block with a spike that extends from each side
 * sequentially, from Metropolis Zone.
 * <p>
 * ROM reference: s2.asm Obj68 (lines 53233-53395)
 * <p>
 * Init (routine 0) sets up the solid block and spawns one spike child
 * (SpikyBlockSpikeInstance) via AllocateObjectAfterCurrent. The spike child
 * runs independently with its own update cycle.
 * <p>
 * The block itself is stationary and solid:
 * <ul>
 *   <li>SolidObject: d1=$1B (half-width=27), d2=$10 (y_radius=16), d3=$11 (ground y_radius=17)</li>
 *   <li>Art: ArtNem_MtzSpikeBlock, palette line 3</li>
 *   <li>Mapping frame 4 from Map_obj68 (32x32 block)</li>
 *   <li>Subtypes 0-3: initial spike direction offset</li>
 * </ul>
 */
public class SpikyBlockObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // From disassembly: move.w #$1B,d1 / move.w #$10,d2 / move.w #$11,d3
    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x1B, 0x10, 0x11);

    // From disassembly: move.b #$10,width_pixels(a0)
    private static final int WIDTH_PIXELS = 0x10;

    // From disassembly: move.b #4,priority(a0)
    private static final int PRIORITY = 4;

    private boolean childSpawned;

    public SpikyBlockObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!childSpawned) {
            spawnSpikeChild(frameCounter);
            childSpawned = true;
        }
    }

    /**
     * ROM: Obj68_Init (s2.asm lines 53246-53279)
     * Spawns the spike child object with initial direction synchronized to
     * the level frame counter and subtype.
     */
    private void spawnSpikeChild(int frameCounter) {
        // ROM: move.w (Level_frame_counter).w,d0 / lsr.w #6,d0
        int d0 = frameCounter >> 6;
        int d1 = d0;

        // ROM: andi.w #1,d0 / move.w d0,spikearoundblock_position(a1)
        int initialPosition = d0 & 1;

        // ROM: lsr.w #1,d1 / add.b subtype(a0),d1 / andi.w #3,d1
        d1 = (d1 >> 1) + spawn.subtype();
        int initialDirection = d1 & 3;

        // Create child spawn at same position as parent
        ObjectSpawn childSpawn = new ObjectSpawn(
                spawn.x(), spawn.y(),
                Sonic2ObjectIds.SPIKY_BLOCK,
                spawn.subtype(),
                spawn.renderFlags(),
                false,
                spawn.rawYWord());

        SpikyBlockSpikeInstance spike = new SpikyBlockSpikeInstance(
                childSpawn, "SpikyBlock-Spike", initialDirection, initialPosition);

        LevelManager.getInstance().getObjectManager().addDynamicObject(spike);
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
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    // SolidObjectProvider
    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    // SolidObjectListener
    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // Block is purely passive solid - no special contact behavior
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.MTZ_SPIKE_BLOCK);
        if (renderer != null && renderer.isReady()) {
            // Frame 0 in the block sheet (corresponds to mapping frame 4 in shared Map_obj68)
            renderer.drawFrameIndex(0, spawn.x(), spawn.y(), false, false);
        }
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Solid block - green rectangle
        ctx.drawRect(spawn.x(), spawn.y(), SOLID_PARAMS.halfWidth(), SOLID_PARAMS.airHalfHeight(), 0f, 1f, 0f);
        String label = name + " sub=" + spawn.subtype();
        ctx.drawWorldLabel(spawn.x(), spawn.y(), -2, label, Color.GREEN);
    }
}
