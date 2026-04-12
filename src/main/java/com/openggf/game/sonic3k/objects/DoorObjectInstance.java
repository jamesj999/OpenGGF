package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x3C - Door (Sonic 3 &amp; Knuckles).
 * <p>
 * A solid door that slides open when a player enters its trigger zone.
 * Two variants selected by the subtype sign bit:
 * <ul>
 *   <li><b>Vertical door</b> (subtype &ge; 0): Door slides up when the player enters
 *       from the trigger side. Subtype low bits select the HCZ/CNZ/DEZ variant.</li>
 *   <li><b>Horizontal door</b> (subtype &lt; 0): Door slides left/right when player
 *       enters the trigger band above or below it. Used in CNZ.</li>
 * </ul>
 * <p>
 * Art is loaded from the level's pattern buffer (zone-specific tiles). The door does not
 * animate its mappings in the ROM; opening is represented only by moving the solid object.
 * <p>
 * ROM reference: Obj_Door (sonic3k.asm:66036), loc_30FD2 (horizontal variant).
 */
public class DoorObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final int VERTICAL_HEIGHT = 0x20;
    private static final int VERTICAL_PRIORITY = 3;

    private static final int HORIZONTAL_WIDTH = 0x20;
    private static final int HORIZONTAL_HEIGHT = 8;
    private static final int HORIZONTAL_PRIORITY = 2;

    private static final int SLIDE_SPEED = 0x08;
    private static final int SLIDE_MAX = 0x40;

    private static final int VERTICAL_TRIGGER_NEAR = 0x18;
    private static final int VERTICAL_TRIGGER_FAR = 0x200;
    private static final int VERTICAL_TRIGGER_HALF_HEIGHT = 0x20;

    private static final int HORIZONTAL_TRIGGER_NEAR = 0x18;
    private static final int HORIZONTAL_TRIGGER_FAR = 0x100;
    private static final int HORIZONTAL_TRIGGER_HALF_WIDTH = 0x20;

    private final boolean horizontal;
    private final boolean xFlipped;
    private final boolean yFlipped;
    private final int baseX;
    private final int baseY;
    private final int halfWidth;
    private final int halfHeight;
    private final int priority;
    private final String artKey;
    private final int triggerMin;
    private final int triggerMax;

    private int slideOffset;
    private boolean playerInTriggerPreviousFrame;

    public DoorObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Door");
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.horizontal = (spawn.subtype() & 0x80) != 0;
        this.xFlipped = (spawn.renderFlags() & 0x01) != 0;
        this.yFlipped = (spawn.renderFlags() & 0x02) != 0;

        int actualSubtype = spawn.subtype() & 0x7F;

        if (horizontal) {
            this.halfWidth = HORIZONTAL_WIDTH;
            this.halfHeight = HORIZONTAL_HEIGHT;
            this.priority = HORIZONTAL_PRIORITY;
            this.artKey = Sonic3kObjectArtKeys.DOOR_HORIZONTAL;
            int top = baseY - HORIZONTAL_TRIGGER_FAR;
            int bottom = baseY + HORIZONTAL_TRIGGER_NEAR;
            if (yFlipped) {
                top += HORIZONTAL_TRIGGER_FAR - HORIZONTAL_TRIGGER_NEAR;
                bottom += HORIZONTAL_TRIGGER_FAR - HORIZONTAL_TRIGGER_NEAR;
            }
            this.triggerMin = top;
            this.triggerMax = bottom;
        } else {
            VerticalDoorVariant variant = resolveVerticalVariant(actualSubtype);
            this.halfWidth = variant.halfWidth();
            this.halfHeight = VERTICAL_HEIGHT;
            this.priority = VERTICAL_PRIORITY;
            this.artKey = variant.artKey();
            this.triggerMin = xFlipped ? baseX - VERTICAL_TRIGGER_NEAR : baseX - VERTICAL_TRIGGER_FAR;
            this.triggerMax = xFlipped ? baseX + VERTICAL_TRIGGER_FAR : baseX + VERTICAL_TRIGGER_NEAR;
        }

        this.slideOffset = 0;
        this.playerInTriggerPreviousFrame = false;
    }

    private VerticalDoorVariant resolveVerticalVariant(int subtype) {
        return switch (subtype) {
            case 1 -> new VerticalDoorVariant(Sonic3kObjectArtKeys.DOOR_VERTICAL_CNZ, 8);
            case 2 -> new VerticalDoorVariant(Sonic3kObjectArtKeys.DOOR_VERTICAL_DEZ, 0x10);
            default -> new VerticalDoorVariant(Sonic3kObjectArtKeys.DOOR_VERTICAL_HCZ, 0x10);
        };
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(halfWidth + 0x0B, halfHeight, halfHeight + 1);
    }

    @Override
    public boolean isTopSolidOnly() {
        return false;
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        return true;
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite mainPlayer = (AbstractPlayableSprite) playerEntity;

        if (horizontal) {
            updateHorizontalDoor(mainPlayer);
        } else {
            updateVerticalDoor(mainPlayer);
        }
    }

    private void updateVerticalDoor(AbstractPlayableSprite mainPlayer) {
        int left = xFlipped
                ? (playerInTriggerPreviousFrame ? triggerMin : getX())
                : triggerMin;
        int right = xFlipped
                ? triggerMax
                : (playerInTriggerPreviousFrame ? triggerMax : getX());

        int top = baseY - VERTICAL_TRIGGER_HALF_HEIGHT;
        int bottom = baseY + VERTICAL_TRIGGER_HALF_HEIGHT;

        boolean playerInTrigger = isPlayerInTrigger(mainPlayer, left, right, top, bottom)
                || isAnySidekickInTrigger(left, right, top, bottom);

        playerInTriggerPreviousFrame = playerInTrigger;
        updateSlideOffset(playerInTrigger);
    }

    private void updateHorizontalDoor(AbstractPlayableSprite mainPlayer) {
        int top = yFlipped
                ? (playerInTriggerPreviousFrame ? triggerMin : getY())
                : triggerMin;
        int bottom = yFlipped
                ? triggerMax
                : (playerInTriggerPreviousFrame ? triggerMax : getY());

        int left = baseX - HORIZONTAL_TRIGGER_HALF_WIDTH;
        int right = baseX + HORIZONTAL_TRIGGER_HALF_WIDTH;

        boolean playerInTrigger = isPlayerInTrigger(mainPlayer, left, right, top, bottom)
                || isAnySidekickInTrigger(left, right, top, bottom);

        playerInTriggerPreviousFrame = playerInTrigger;
        updateSlideOffset(playerInTrigger);
    }

    private boolean isPlayerInTrigger(AbstractPlayableSprite player, int left, int right, int top, int bottom) {
        if (player == null || player.isObjectControlled()) {
            return false;
        }

        int px = player.getCentreX();
        int py = player.getCentreY();
        return px >= left && px < right && py >= top && py < bottom;
    }

    private boolean isAnySidekickInTrigger(int left, int right, int top, int bottom) {
        try {
            for (PlayableEntity sidekick : services().sidekicks()) {
                if (!(sidekick instanceof AbstractPlayableSprite sprite) || sprite.isObjectControlled()) {
                    continue;
                }
                int px = sprite.getCentreX();
                int py = sprite.getCentreY();
                if (px >= left && px < right && py >= top && py < bottom) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // Unit tests may instantiate this object without injected services.
        }
        return false;
    }

    private void updateSlideOffset(boolean playerInZone) {
        if (playerInZone) {
            if (slideOffset < SLIDE_MAX) {
                slideOffset += SLIDE_SPEED;
                if (slideOffset > SLIDE_MAX) {
                    slideOffset = SLIDE_MAX;
                }
                if (slideOffset == SLIDE_MAX) {
                    playDoorSound();
                }
            }
        } else {
            if (slideOffset > 0) {
                slideOffset -= SLIDE_SPEED;
                if (slideOffset < 0) {
                    slideOffset = 0;
                }
                if (slideOffset == 0) {
                    playDoorSound();
                }
            }
        }
    }

    private void playDoorSound() {
        // ROM: horizontal door variant (loc_31034) has no Play_SFX calls;
        // only vertical doors (loc_30E8C) play sfx_FanLatch at endpoints.
        if (horizontal) {
            return;
        }
        try {
            services().playSfx(Sonic3kSfx.FAN_LATCH.id);
        } catch (Exception e) {
            // Ignore audio errors
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) return;

        PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(0, getX(), getY(), false, false);
        }
    }

    @Override
    public int getX() {
        if (!horizontal) {
            return baseX;
        }
        return xFlipped ? baseX - slideOffset : baseX + slideOffset;
    }

    @Override
    public int getY() {
        if (horizontal) {
            return baseY;
        }
        return baseY - slideOffset;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(priority);
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    private record VerticalDoorVariant(String artKey, int halfWidth) {
    }
}
