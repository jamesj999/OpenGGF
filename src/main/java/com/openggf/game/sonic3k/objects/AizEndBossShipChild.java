package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Shared Robotnik ship + head overlay used by the AIZ end boss.
 *
 * <p>ROM composition:
 * <ul>
 *   <li>{@code Child1_MakeRoboShip}: ship offset (0, -$14), mapping frame 8</li>
 *   <li>{@code Child1_MakeRoboHead2}: head offset (0, -$1C) from the ship, mapping frame 0</li>
 * </ul>
 *
 * <p>The engine only needs the layered render composition during the active fight,
 * so this child draws both shared pieces together and hides whenever the parent
 * is submerged or defeated.
 */
public class AizEndBossShipChild extends AbstractBossChild {
    private static final int SHIP_Y_OFFSET = -0x14;
    private static final int HEAD_Y_OFFSET = -0x1C;
    private static final int SHIP_FRAME = 0x08;
    private static final int HEAD_FRAME_HURT = 0x02;
    private static final int HEAD_FRAME_DEFEATED = 0x03;

    private final AizEndBossInstance boss;
    private int headAnimTimer;
    private int headFrame;

    public AizEndBossShipChild(AizEndBossInstance boss) {
        super(boss, "AIZEndBossShip", 5, 0);
        this.boss = boss;
        this.headAnimTimer = 0;
        this.headFrame = 0;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (!beginUpdate(frameCounter)) {
            return;
        }
        setPosition(boss.getX(), boss.getY() + SHIP_Y_OFFSET);
        updateHeadFrame();
        if (boss.isDefeatSignal()) {
            setDestroyed(true);
        }
    }

    private void updateHeadFrame() {
        if (boss.getState().defeated) {
            headFrame = HEAD_FRAME_DEFEATED;
            return;
        }
        if (boss.getState().invulnerable) {
            headFrame = HEAD_FRAME_HURT;
            return;
        }
        headAnimTimer++;
        headFrame = (headAnimTimer / 6) & 1;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || boss.isHidden()) {
            return;
        }

        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.ROBOTNIK_SHIP);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean hFlip = boss.isFacingRight();
        renderer.drawFrameIndex(SHIP_FRAME, getX(), getY(), hFlip, false);
        renderer.drawFrameIndex(headFrame, getX(), getY() + HEAD_Y_OFFSET, hFlip, false);
    }

    @Override
    public boolean isHighPriority() {
        return boss.isHighPriority();
    }
}
