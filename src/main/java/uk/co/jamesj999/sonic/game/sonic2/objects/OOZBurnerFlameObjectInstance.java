package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationEndAction;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationScript;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationSet;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * OOZ Burner Flame (Obj33 child, routine 4) - flame beneath the popping platform.
 * <p>
 * The flame is only visible and harmful when the platform has risen at least 20 pixels ($14)
 * above the flame's position. When visible, it animates with a flickering pattern and
 * hurts the player on contact.
 * <p>
 * <b>Disassembly Reference:</b> s2.asm lines 49431-49450 (Obj33_Flame)
 * <p>
 * Collision flags: $9B = HURT + size index $1B (8x4 pixels)
 */
public class OOZBurnerFlameObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider {

    // Collision flags: $9B = hurt (upper 2 bits = $80) + size index $1B
    private static final int COLLISION_FLAGS_ACTIVE = 0x9B;
    private static final int COLLISION_FLAGS_INACTIVE = 0;

    // Flame becomes visible when platform is >= $14 pixels above flame Y
    private static final int VISIBILITY_THRESHOLD = 0x14;

    // Animation: Ani_obj33 = dc.b 2, 2, 0, 2, 0, 2, 0, 1, $FF
    // delay=2, frames=[2, 0, 2, 0, 2, 0, 1], loop
    private static final SpriteAnimationSet FLAME_ANIMATIONS;

    static {
        FLAME_ANIMATIONS = new SpriteAnimationSet();
        FLAME_ANIMATIONS.addScript(0, new SpriteAnimationScript(
                2, // delay (display each frame for 3 VBlanks)
                List.of(2, 0, 2, 0, 2, 0, 1),
                SpriteAnimationEndAction.LOOP,
                0
        ));
    }

    private final OOZPoppingPlatformObjectInstance parent;
    private final ObjectAnimationState animationState;
    private boolean flameActive;

    public OOZBurnerFlameObjectInstance(ObjectSpawn spawn, OOZPoppingPlatformObjectInstance parent) {
        super(spawn, "OOZBurnerFlame");
        this.parent = parent;
        this.animationState = new ObjectAnimationState(FLAME_ANIMATIONS, 0, 0);
        this.flameActive = false;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (parent == null || parent.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        // ROM: d0 = y_pos(flame) - y_pos(parent)
        // If distance < $14: flame off
        // If distance >= $14: flame on, animate
        int distance = spawn.y() - parent.getPlatformY();
        if (distance < VISIBILITY_THRESHOLD) {
            // Flame off (Obj33_FlameOff)
            // ROM: move.b #0,anim_frame(a0) - reset animation frame to 0
            flameActive = false;
            animationState.resetFrameIndex();
            return;
        }

        // Flame on - animate and enable collision
        flameActive = true;
        animationState.update();
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
        return RenderPriority.clamp(4); // Behind platform (priority 3)
    }

    @Override
    public int getCollisionFlags() {
        return flameActive ? COLLISION_FLAGS_ACTIVE : COLLISION_FLAGS_INACTIVE;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!flameActive) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.OOZ_BURN_FLAME);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        int frame = animationState.getMappingFrame();
        renderer.drawFrameIndex(frame, spawn.x(), spawn.y(), false, false);
    }

}
