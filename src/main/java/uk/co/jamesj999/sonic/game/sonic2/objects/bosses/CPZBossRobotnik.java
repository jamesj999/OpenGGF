package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.objects.ObjectAnimationState;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * CPZ Boss Robotnik face - Animated Eggman sprite.
 * ROM Reference: s2.asm Obj5D (ROUTINE_ROBOTNIK = 0x16)
 * Follows parent position and shows expressions based on hit state.
 */
public class CPZBossRobotnik extends AbstractObjectInstance {

    private final LevelManager levelManager;
    private final Sonic2CPZBossInstance mainBoss;

    private int x;
    private int y;
    private int renderFlags;
    private int anim;
    private int mappingFrame;

    private ObjectAnimationState animationState;

    public CPZBossRobotnik(ObjectSpawn spawn, LevelManager levelManager, Sonic2CPZBossInstance mainBoss) {
        super(spawn, "CPZ Boss Robotnik");
        this.levelManager = levelManager;
        this.mainBoss = mainBoss;
        this.x = spawn.x();
        this.y = spawn.y();
        this.renderFlags = spawn.renderFlags();
        this.anim = 1;
        this.mappingFrame = 0;
        this.animationState = new ObjectAnimationState(
                CPZBossAnimations.getEggpodAnimations(), anim, mappingFrame);
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        if (mainBoss == null || mainBoss.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        x = mainBoss.getX();
        y = mainBoss.getY();
        renderFlags = mainBoss.getRenderFlags();

        // Check if boss just got hit
        if (mainBoss.isInvulnerable() && mainBoss.getInvulnerabilityTimer() == mainBoss.getInvulnerabilityDuration() - 1) {
            anim = 2; // Hurt face
        }

        // Check if player is hurt (laugh)
        if (player != null && player.isHurt()) {
            anim = 3;
        }

        animate();
    }

    private void animate() {
        animationState.setAnimId(anim);
        animationState.update();
        mappingFrame = animationState.getMappingFrame();
    }

    public void setAnim(int anim) {
        this.anim = anim;
        if (animationState != null) {
            animationState.setAnimId(anim);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager != null ? levelManager.getObjectRenderManager() : null;
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.CPZ_BOSS_EGGPOD);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        if (mappingFrame < 0) {
            return;
        }

        boolean flipped = (renderFlags & 1) != 0;
        renderer.drawFrameIndex(mappingFrame, x, y, flipped, false, 0);
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getPriorityBucket() {
        return 3;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), renderFlags, spawn.respawnTracked(), spawn.rawYWord());
    }
}
