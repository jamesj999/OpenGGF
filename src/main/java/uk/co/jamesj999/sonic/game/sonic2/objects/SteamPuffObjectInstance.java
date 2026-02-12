package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Steam Puff child object spawned by SteamSpring (Object 0x42, routine 4).
 * <p>
 * Animates through 7 frames (0-6) of steam dissipation, then self-destructs.
 * At frame 3, briefly gains harmful collision (collision_flags = $A6).
 * <p>
 * ROM reference: s2.asm loc_2674C (spawning) and loc_2683A (animation/routine 4)
 * <ul>
 *   <li>Art: ArtNem_MtzSteam, palette line 1</li>
 *   <li>width_pixels = $18</li>
 *   <li>anim_frame_duration = 7 (8 frames per animation step)</li>
 *   <li>collision_flags = $A6 at frame 3 only (harmful, size 0x26)</li>
 * </ul>
 */
public class SteamPuffObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider {

    // ROM: move.b #$18,width_pixels(a1) at loc_2674C (s2.asm line 52111)
    // Used by MarkObjGone for off-screen culling in ROM; not consumed by engine base class
    @SuppressWarnings("unused")
    private static final int WIDTH_PIXELS = 0x18;

    // ROM: move.b #7,anim_frame_duration(a1) at loc_2674C
    private static final int FRAME_DURATION = 7;

    // ROM: cmpi.b #7,mapping_frame(a0) at loc_2683A - delete at frame 7
    private static final int TOTAL_FRAMES = 7;

    // ROM: move.b #$A6,collision_flags(a0) at loc_2683A when frame == 3
    // Category $80 (HURT) + size index $26
    private static final int COLLISION_FLAGS_ACTIVE = 0xA6;

    private final boolean xFlipped;
    private int mappingFrame;
    private int frameDuration;
    private int collisionFlags;

    /**
     * Create a steam puff at the given world position.
     *
     * @param x        world X center
     * @param y        world Y center (baseY of the piston, not current Y)
     * @param xFlipped true for the left-side puff (render_flags.x_flip set)
     */
    public SteamPuffObjectInstance(int x, int y, boolean xFlipped) {
        super(new ObjectSpawn(x, y, Sonic2ObjectIds.STEAM_SPRING, 0, xFlipped ? 1 : 0, false, 0),
                "SteamPuff");
        this.xFlipped = xFlipped;
        this.mappingFrame = 0;
        this.frameDuration = FRAME_DURATION;
        this.collisionFlags = 0; // No collision initially
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // ROM: loc_2683A (routine 4)
        // subq.b #1,anim_frame_duration(a0) / bpl.s ++
        frameDuration--;
        if (frameDuration < 0) {
            // ROM: move.b #7,anim_frame_duration(a0)
            frameDuration = FRAME_DURATION;
            // ROM: move.b #0,collision_flags(a0) - clear collision by default
            collisionFlags = 0;
            // ROM: addq.b #1,mapping_frame(a0)
            mappingFrame++;

            // ROM: cmpi.b #3,mapping_frame(a0) / bne.s +
            // At frame 3, set harmful collision
            if (mappingFrame == 3) {
                collisionFlags = COLLISION_FLAGS_ACTIVE;
            }

            // ROM: cmpi.b #7,mapping_frame(a0) / beq.w JmpTo30_DeleteObject
            if (mappingFrame >= TOTAL_FRAMES) {
                setDestroyed(true);
                return;
            }
        }
    }

    // --- TouchResponseProvider ---

    @Override
    public int getCollisionFlags() {
        return collisionFlags;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    // --- Rendering ---

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager != null) {
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.MTZ_STEAM);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), xFlipped, false);
                return;
            }
        }

        // Fallback: debug marker
        int cx = spawn.x();
        int cy = spawn.y();
        float r = 0.8f, g = 0.8f, b = 0.8f;
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, cx - 4, cy, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, cx + 4, cy, 0, 0));
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }
}
