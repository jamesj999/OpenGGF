package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.game.GameModule;
import uk.co.jamesj999.sonic.game.GameModuleRegistry;
import uk.co.jamesj999.sonic.game.ObjectArtProvider;
import uk.co.jamesj999.sonic.game.sonic2.objects.ShieldObjectInstance;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kObjectArtProvider;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationScript;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationSet;
import uk.co.jamesj999.sonic.sprites.art.SpriteArtSet;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;
import uk.co.jamesj999.sonic.sprites.render.PlayerSpriteRenderer;

import java.util.List;

/**
 * Bubble Shield visual object for S3K.
 * Uses DPLC-driven ROM art with animation scripts from Ani_BubbleShield.
 */
public class BubbleShieldObjectInstance extends ShieldObjectInstance {

    private final PlayerSpriteRenderer dplcRenderer;
    private final SpriteAnimationSet animSet;
    private int currentAnimId;
    private int frameIndex;
    private int delayCounter;
    private int currentMappingFrame;

    public BubbleShieldObjectInstance(AbstractPlayableSprite player) {
        super(player);
        Sonic3kObjectArtProvider artProvider = getS3kArtProvider();
        if (artProvider != null) {
            this.dplcRenderer = artProvider.getShieldDplcRenderer(Sonic3kObjectArtKeys.BUBBLE_SHIELD);
            SpriteArtSet artSet = artProvider.getShieldArtSet(Sonic3kObjectArtKeys.BUBBLE_SHIELD);
            this.animSet = artSet != null ? artSet.animationSet() : null;
        } else {
            this.dplcRenderer = null;
            this.animSet = null;
        }
        currentAnimId = 0;
        frameIndex = 0;
        delayCounter = 0;
        currentMappingFrame = 0;
        initAnimation(0);
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        super.update(frameCounter, player);
        if (isShieldDestroyed()) return;
        stepAnimation();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isShieldDestroyed() || !isShieldVisible()) {
            return;
        }
        if (dplcRenderer != null) {
            AbstractPlayableSprite player = getPlayer();
            if (player == null) return;
            int cx = player.getCentreX();
            int cy = player.getCentreY();
            dplcRenderer.drawFrame(currentMappingFrame, cx, cy, false, false);
            return;
        }
        if (hasRenderer()) {
            super.appendRenderCommands(commands);
            return;
        }
        // Wireframe fallback
        AbstractPlayableSprite player = getPlayer();
        if (player == null) return;
        int cx = player.getCentreX();
        int cy = player.getCentreY();
        boolean expanded = (getSequenceIndex() % 2) == 0;
        int half = expanded ? 18 : 14;
        appendWireDiamond(commands, cx, cy, half, 0.2f, 0.4f, 1.0f);
    }

    /** Sets the current animation and resets playback state. */
    public void setAnimation(int animId) {
        if (animId != currentAnimId) {
            initAnimation(animId);
        }
    }

    private void initAnimation(int animId) {
        currentAnimId = animId;
        frameIndex = 0;
        if (animSet != null) {
            SpriteAnimationScript script = animSet.getScript(animId);
            if (script != null) {
                delayCounter = script.delay();
                if (!script.frames().isEmpty()) {
                    currentMappingFrame = script.frames().get(0);
                }
            }
        }
    }

    private void stepAnimation() {
        if (animSet == null) return;
        SpriteAnimationScript script = animSet.getScript(currentAnimId);
        if (script == null || script.frames().isEmpty()) return;

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }
        delayCounter = script.delay();

        frameIndex++;
        if (frameIndex >= script.frames().size()) {
            switch (script.endAction()) {
                case LOOP -> frameIndex = 0;
                case LOOP_BACK -> frameIndex = Math.max(0, script.frames().size() - script.endParam());
                case SWITCH -> { initAnimation(script.endParam()); return; }
                case HOLD -> frameIndex = script.frames().size() - 1;
            }
        }
        currentMappingFrame = script.frames().get(frameIndex);
    }

    private void appendWireDiamond(List<GLCommand> commands,
            int cx, int cy, int half, float r, float g, float b) {
        int top = cy - half;
        int bottom = cy + half;
        int left = cx - half;
        int right = cx + half;
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, cx, top, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, right, cy, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, right, cy, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, cx, bottom, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, cx, bottom, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, left, cy, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, left, cy, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, cx, top, 0, 0));
    }

    private static Sonic3kObjectArtProvider getS3kArtProvider() {
        GameModule module = GameModuleRegistry.getCurrent();
        if (module == null) return null;
        ObjectArtProvider provider = module.getObjectArtProvider();
        return (provider instanceof Sonic3kObjectArtProvider s3k) ? s3k : null;
    }
}
