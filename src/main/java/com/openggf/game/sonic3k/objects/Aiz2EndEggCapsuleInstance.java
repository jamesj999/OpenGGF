package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Floating upside-down egg prison used by the AIZ2 post-boss cutscene.
 *
 * <p>ROM reference: Obj_EggCapsule routine 8 (camera-relative descent/hover).
 */
public class Aiz2EndEggCapsuleInstance extends AbstractObjectInstance {

    private static final int OBJECT_ID = 0x81;
    private static final int X_OFFSET = 0xA0;
    private static final int Y_START_OFFSET = -0x40;
    private static final int Y_TARGET_OFFSET = 0x40;
    private static final int LEFT_BOUND_OFFSET = 0x30;
    private static final int RIGHT_BOUND_OFFSET = 0x110;
    private static final int PRIORITY = 5;
    private static final int ACTIVATION_X_LEFT = -0x1A;
    private static final int ACTIVATION_X_RIGHT = 0x34;
    private static final int ACTIVATION_Y_TOP = 0x08;
    private static final int ACTIVATION_Y_BOTTOM = 0x60;
    private static final int ACTIVATION_TIMER = 0x40;

    private int currentX;
    private int currentY;
    private int verticalAccumulator;
    private int bobAngle;
    private int xDirection = 1;
    private int activationTimer = ACTIVATION_TIMER;
    private int mappingFrame;
    private boolean activated;
    private boolean resultsStarted;
    private boolean releaseTriggered;

    public Aiz2EndEggCapsuleInstance(int initialX, int initialY) {
        super(new ObjectSpawn(initialX, initialY, OBJECT_ID, 0, 0, false, 0), "AIZ2EndEggCapsule");
        this.currentX = initialX;
        this.currentY = initialY;
    }

    public static Aiz2EndEggCapsuleInstance createForCamera(int cameraX, int cameraY) {
        return new Aiz2EndEggCapsuleInstance(cameraX + X_OFFSET, cameraY + Y_START_OFFSET);
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        int cameraX = services().camera().getX();
        int cameraY = services().camera().getY();

        int leftBound = cameraX + LEFT_BOUND_OFFSET;
        int rightBound = cameraX + RIGHT_BOUND_OFFSET;
        currentX += xDirection;
        if (currentX <= leftBound || currentX >= rightBound) {
            currentX = Math.max(leftBound, Math.min(rightBound, currentX));
            xDirection = -xDirection;
        }

        int targetY = cameraY + Y_TARGET_OFFSET;
        if (currentY != targetY) {
            verticalAccumulator += 0x4000;
            int step = verticalAccumulator >> 16;
            verticalAccumulator &= 0xFFFF;
            if (step > 0) {
                if (currentY < targetY) {
                    currentY = Math.min(targetY, currentY + step);
                } else {
                    currentY = Math.max(targetY, currentY - step);
                }
            }
        } else if (!activated) {
            if (playerEntity instanceof AbstractPlayableSprite player && shouldActivate(player)) {
                activate();
            }
            for (PlayableEntity sidekickEntity : services().sidekicks()) {
                if (sidekickEntity instanceof AbstractPlayableSprite sidekick && shouldActivate(sidekick)) {
                    activate();
                    break;
                }
            }
        } else if (!resultsStarted) {
            if (activationTimer > 0) {
                activationTimer--;
            }
            if (activationTimer == 0
                    && playerEntity instanceof AbstractPlayableSprite player
                    && !player.getAir()) {
                startResults(player);
            }
        } else if (!releaseTriggered && services().gameState().isEndOfLevelFlag()) {
            releaseTriggered = true;
            Aiz2BossEndSequenceState.releaseEggCapsule();
            setDestroyed(true);
        }

        bobAngle = (bobAngle + 3) & 0xFF;
    }

    private boolean shouldActivate(AbstractPlayableSprite player) {
        int dx = player.getCentreX() - currentX;
        int dy = player.getCentreY() - currentY;
        return player.getYSpeed() < 0
                && dx >= ACTIVATION_X_LEFT
                && dx < ACTIVATION_X_RIGHT
                && dy >= ACTIVATION_Y_TOP
                && dy < ACTIVATION_Y_BOTTOM;
    }

    private void activate() {
        if (activated) {
            return;
        }
        activated = true;
        mappingFrame = 1;
        activationTimer = ACTIVATION_TIMER;
    }

    private void startResults(AbstractPlayableSprite player) {
        if (resultsStarted) {
            return;
        }
        resultsStarted = true;
        services().gameState().setEndOfLevelActive(true);
        lockForResults(player);
        for (PlayableEntity sidekickEntity : services().sidekicks()) {
            if (sidekickEntity instanceof AbstractPlayableSprite sidekick) {
                lockForResults(sidekick);
            }
        }
        spawnChild(() -> new S3kResultsScreenObjectInstance(getPlayerCharacter(), services().currentAct()));
    }

    private void lockForResults(AbstractPlayableSprite sprite) {
        sprite.setObjectControlled(true);
        sprite.setControlLocked(true);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAnimationId(Sonic3kAnimationIds.VICTORY);
    }

    private PlayerCharacter getPlayerCharacter() {
        try {
            return ((Sonic3kLevelEventManager) services().levelEventProvider()).getPlayerCharacter();
        } catch (Exception e) {
            return PlayerCharacter.SONIC_AND_TAILS;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.EGG_CAPSULE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        int bobY = currentY + (int) Math.round(Math.sin((bobAngle * Math.PI * 2.0) / 256.0) * 3.0);
        renderer.drawFrameIndex(mappingFrame, currentX, bobY, false, true);
    }
}
