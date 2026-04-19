package com.openggf.game.sonic3k.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.scroll.SwScrlMgz;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.util.List;

/**
 * Object 0x57 - MGZ Trigger Platform.
 *
 * <p>ROM: Obj_MGZTriggerPlatform (sonic3k.asm:70910-71029).
 * The high subtype nibble selects one of three table-driven platform shapes:
 * a horizontal escape platform (nibble $0) or vertical trigger platforms
 * (nibbles $1 and $2) that move 1px/frame or 2px/frame once their trigger fires.
 *
 * <p>Subtype bits:
 * <ul>
 *   <li>Bits [7:4]: config index into byte_34568</li>
 *   <li>Bits [3:0]: Level_trigger_array index to monitor</li>
 * </ul>
 *
 * <p>Render/status bit 0 reverses the movement direction for both horizontal
 * and vertical variants.
 */
public class MGZTriggerPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final String ART_KEY = Sonic3kObjectArtKeys.MGZ_TRIGGER_PLATFORM;
    private static final int PRIORITY_BUCKET = 5; // ROM: priority = $280

    private static final int SCREEN_SHAKE_MASK = 0x3F;
    private static final int[] SCREEN_SHAKE_CONTINUOUS = {
            1, 2, 1, 3, 1, 2, 2, 1, 2, 3, 1, 2, 1, 2, 0, 0,
            2, 0, 3, 2, 2, 3, 2, 2, 1, 3, 0, 0, 1, 0, 1, 3,
            1, 2, 1, 3, 1, 2, 2, 1, 2, 3, 1, 2, 1, 2, 0, 0,
            2, 0, 3, 2, 2, 3, 2, 2, 1, 3, 0, 0, 1, 0, 1, 3
    };

    private enum Mode {
        HORIZONTAL_DELETE,
        VERTICAL_MOVE
    }

    private final int triggerIndex;
    private final int frameIndex;
    private final int widthPixels;
    private final int heightPixels;
    private final int totalFrames;
    private final int stepPerFrame;
    private final int direction;
    private final Mode mode;

    private int currentX;
    private int currentY;
    private int remainingFrames;
    private boolean activated;
    private boolean completed;

    public MGZTriggerPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "MGZTriggerPlatform");

        int highNibble = spawn.subtype() & 0xF0;
        int configIndex = highNibble >> 4;
        if (configIndex > 2) {
            configIndex = 2;
        }

        this.widthPixels = switch (configIndex) {
            case 0 -> 0x40;
            case 1, 2 -> 0x20;
            default -> 0x20;
        };
        this.heightPixels = switch (configIndex) {
            case 0 -> 0x1E;
            case 1, 2 -> 0x40;
            default -> 0x40;
        };
        this.frameIndex = (configIndex == 0) ? 0 : 1;
        this.totalFrames = 0x40;
        this.stepPerFrame = configIndex;
        this.mode = (configIndex == 0) ? Mode.HORIZONTAL_DELETE : Mode.VERTICAL_MOVE;

        this.triggerIndex = spawn.subtype() & 0x0F;
        this.direction = ((spawn.renderFlags() & 0x01) != 0) ? -1 : 1;
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.remainingFrames = totalFrames;

        // ROM: vertical variants spawned after the trigger has already fired are
        // immediately fast-forwarded to their final Y and marked complete.
        if (mode == Mode.VERTICAL_MOVE && Sonic3kLevelTriggerManager.testAny(triggerIndex)) {
            currentY += direction * totalFrames * stepPerFrame;
            remainingFrames = 0;
            completed = true;
        }

        updateDynamicSpawn(currentX, currentY);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            clearScreenShake();
            return;
        }

        if (!completed && Sonic3kLevelTriggerManager.testAny(triggerIndex)) {
            activated = true;
        }

        if (!completed && activated) {
            advanceActiveMotion();
            applyScreenShake(frameCounter);
        } else {
            clearScreenShake();
        }

        updateDynamicSpawn(currentX, currentY);
    }

    private void advanceActiveMotion() {
        if (mode == Mode.HORIZONTAL_DELETE) {
            currentX += direction * 2;
        } else {
            currentY += direction * stepPerFrame;
        }

        remainingFrames--;
        if (remainingFrames > 0) {
            return;
        }

        completed = true;
        clearScreenShake();

        if (mode == Mode.HORIZONTAL_DELETE) {
            markRemembered();
            setDestroyed(true);
        }
    }

    private void markRemembered() {
        var svc = tryServices();
        if (svc == null) {
            return;
        }
        ObjectManager objectManager = svc.objectManager();
        if (objectManager != null) {
            objectManager.markRemembered(spawn);
        }
    }

    private void applyScreenShake(int frameCounter) {
        SwScrlMgz mgzHandler = resolveMgzScrollHandler();
        if (mgzHandler == null) {
            return;
        }
        mgzHandler.setScreenShakeOffset(SCREEN_SHAKE_CONTINUOUS[frameCounter & SCREEN_SHAKE_MASK]);
    }

    private void clearScreenShake() {
        SwScrlMgz mgzHandler = resolveMgzScrollHandler();
        if (mgzHandler != null) {
            mgzHandler.setScreenShakeOffset(0);
        }
    }

    private SwScrlMgz resolveMgzScrollHandler() {
        var svc = tryServices();
        if (svc == null || svc.parallaxManager() == null) {
            return null;
        }
        ZoneScrollHandler handler = svc.parallaxManager().getHandler(Sonic3kZoneIds.ZONE_MGZ);
        return (handler instanceof SwScrlMgz mgz) ? mgz : null;
    }

    @Override
    public void onUnload() {
        clearScreenShake();
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(widthPixels + 0x0B, heightPixels, heightPixels + 1);
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        // SolidObjectFull behavior is handled by the shared solid-contact system.
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(ART_KEY);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(frameIndex, currentX, currentY, false, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        if (ctx == null) {
            return;
        }
        ctx.drawRect(currentX, currentY, widthPixels + 0x0B, heightPixels, 0.2f, 0.9f, 0.2f);
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
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY_BUCKET);
    }
}
