package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM object: {@code Obj_CNZRisingPlatform}.
 *
 * <p>The verified CNZ disassembly uses {@code Map_CNZRisingPlatform} and the
 * {@code Anim - Rising Platform.asm} table from the lock-on ROM data set. The
 * object itself owns a two-phase motion state machine: it sits idle until the
 * player stands on it, then rises to a subtype-defined travel height and stops.
 *
 * <p>The subtype split here is intentionally documented in Java rather than
 * pushed into a CNZ-global manager so the platform remains traceable to the
 * ROM routine while still being testable in isolation.
 */
public final class CnzRisingPlatformInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final int HALF_WIDTH = 0x18;
    private static final int HALF_HEIGHT = 0x10;
    private static final int RISE_STEP_PIXELS = 1;
    private static final int PRIORITY_BUCKET = 5;

    private final int originY;
    private final int subtypeTravelPixels;
    private final boolean autoStart;

    private boolean triggered;
    private int ySpeedForTest;

    public CnzRisingPlatformInstance(ObjectSpawn spawn) {
        super(spawn, "CNZRisingPlatform");
        this.originY = spawn.y();
        this.subtypeTravelPixels = decodeTravelPixels(spawn.subtype());
        this.autoStart = (spawn.subtype() & 0x80) != 0;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (!triggered) {
            triggered = autoStart || isPlayerStandingOnTop(playerEntity);
            if (!triggered) {
                ySpeedForTest = 0;
                updateDynamicSpawn(getX(), getY());
                return;
            }
            ySpeedForTest = 0;
            updateDynamicSpawn(getX(), getY());
            return;
        }

        updateMovingPlatform();
        updateDynamicSpawn(getX(), getY());
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY_BUCKET);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.CNZ_RISING_PLATFORM);
        if (renderer == null) {
            return;
        }

        boolean hFlip = (spawn.renderFlags() & 0x01) != 0;
        int frame = triggered ? (getY() <= originY - subtypeTravelPixels ? 2 : 1) : 0;
        renderer.drawFrameIndex(frame, getX(), getY(), hFlip, false);
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(HALF_WIDTH, HALF_HEIGHT, HALF_HEIGHT + 1);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        if (contact.standing()) {
            triggered = true;
        }
    }

    boolean wasTriggeredForTest() {
        return triggered;
    }

    int getSubtypeTravelForTest() {
        return subtypeTravelPixels;
    }

    int getYSpeedForTest() {
        return ySpeedForTest;
    }

    private void updateMovingPlatform() {
        int targetY = originY - subtypeTravelPixels;
        int currentY = getY();
        if (currentY > targetY) {
            int nextY = Math.max(targetY, currentY - RISE_STEP_PIXELS);
            ySpeedForTest = nextY - currentY;
            updateDynamicSpawn(getX(), nextY);
        } else {
            ySpeedForTest = 0;
            updateDynamicSpawn(getX(), targetY);
        }
    }

    private boolean isPlayerStandingOnTop(PlayableEntity playerEntity) {
        if (playerEntity == null) {
            return false;
        }
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        int dx = Math.abs(player.getCentreX() - getX());
        int dy = Math.abs(player.getCentreY() - getY());
        return dx <= HALF_WIDTH + 8 && dy <= HALF_HEIGHT + 16;
    }

    private static int decodeTravelPixels(int subtype) {
        int travelIndex = (subtype >> 4) & 0x03;
        return switch (travelIndex) {
            case 0 -> 0x20;
            case 1 -> 0x30;
            case 2 -> 0x40;
            default -> 0x50;
        };
    }
}
