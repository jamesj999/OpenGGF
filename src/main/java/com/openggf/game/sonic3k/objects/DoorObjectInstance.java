package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
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
 *   <li><b>Vertical door</b> (subtype &ge; 0): Door slides up when player enters
 *       the left or right trigger zones. Used in HCZ, CNZ, DEZ.</li>
 *   <li><b>Horizontal door</b> (subtype &lt; 0): Door slides left/right when player
 *       enters top/bottom trigger zones. Used in CNZ.</li>
 * </ul>
 * <p>
 * Art is loaded from the level's pattern buffer (zone-specific tiles).
 * The vertical door uses 3 animation frames: closed, half-open, open.
 * The horizontal door uses 1 frame (no animation).
 * <p>
 * ROM reference: Obj_Door (sonic3k.asm:66036), loc_30FD2 (horizontal variant).
 */
public class DoorObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final int VERTICAL_WIDTH = 0x10;
    private static final int VERTICAL_HEIGHT = 0x20;
    private static final int VERTICAL_PRIORITY = 3;

    private static final int HORIZONTAL_WIDTH = 8;
    private static final int HORIZONTAL_HEIGHT = 0x20;
    private static final int HORIZONTAL_PRIORITY = 2;

    private static final int SLIDE_SPEED = 0x08;
    private static final int SLIDE_MAX = 0x40;

    private static final int VERTICAL_TRIGGER_HALF_WIDTH = 0x200;
    private static final int VERTICAL_TRIGGER_INSIDE = 0x18;
    private static final int VERTICAL_TRIGGER_VERTICAL = 0x20;
    private static final int HORIZONTAL_TRIGGER_TOP = -0xE8;
    private static final int HORIZONTAL_TRIGGER_BOTTOM = 0x100;

    private final int subtype;
    private final boolean isHorizontal;
    private final int x;
    private final int y;
    private final int widthPixels;
    private final int heightPixels;
    private final int priority;
    private final String artKey;

    private int currentFrame;
    private int slideOffset;

    public DoorObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Door");
        this.x = spawn.x();
        this.y = spawn.y();
        this.subtype = spawn.subtype() & 0xFF;

        this.isHorizontal = (subtype & 0x80) != 0;
        int actualSubtype = subtype & 0x7F;

        if (isHorizontal) {
            this.widthPixels = HORIZONTAL_WIDTH;
            this.heightPixels = HORIZONTAL_HEIGHT;
            this.priority = HORIZONTAL_PRIORITY;
            this.currentFrame = actualSubtype;
            this.artKey = Sonic3kObjectArtKeys.DOOR_HORIZONTAL;
        } else {
            this.widthPixels = VERTICAL_WIDTH;
            this.heightPixels = VERTICAL_HEIGHT;
            this.priority = VERTICAL_PRIORITY;
            this.currentFrame = actualSubtype;
            this.artKey = resolveVerticalArtKey();
        }

        this.slideOffset = 0;
    }

    private String resolveVerticalArtKey() {
        try {
            int zone = services().romZoneId();
            return switch (zone) {
                case Sonic3kZoneIds.ZONE_HCZ -> Sonic3kObjectArtKeys.DOOR_VERTICAL_HCZ;
                case Sonic3kZoneIds.ZONE_CNZ -> Sonic3kObjectArtKeys.DOOR_VERTICAL_CNZ;
                case Sonic3kZoneIds.ZONE_DEZ -> Sonic3kObjectArtKeys.DOOR_VERTICAL_DEZ;
                default -> Sonic3kObjectArtKeys.DOOR_VERTICAL_HCZ;
            };
        } catch (Exception e) {
            return Sonic3kObjectArtKeys.DOOR_VERTICAL_HCZ;
        }
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(widthPixels + 0x0B, heightPixels, heightPixels + 1);
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

        if (isHorizontal) {
            updateHorizontalDoor(mainPlayer);
        } else {
            updateVerticalDoor(mainPlayer);
        }
    }

    private void updateVerticalDoor(AbstractPlayableSprite mainPlayer) {
        boolean flipped = (spawn.rawFlags() & 0x1000) != 0;

        int triggerLeft, triggerRight;
        if (flipped) {
            triggerLeft = x;
            triggerRight = x + VERTICAL_TRIGGER_HALF_WIDTH;
        } else {
            triggerLeft = x - VERTICAL_TRIGGER_HALF_WIDTH;
            triggerRight = x;
        }

        int triggerTop = y - VERTICAL_TRIGGER_VERTICAL;
        int triggerBottom = y + VERTICAL_TRIGGER_VERTICAL;

        boolean inZone = isInVerticalZone(mainPlayer, triggerLeft, triggerRight, triggerTop, triggerBottom);
        inZone |= isSidekickInVerticalZone(triggerLeft, triggerRight, triggerTop, triggerBottom);

        if (inZone) {
            if (flipped) {
                triggerLeft = x - VERTICAL_TRIGGER_INSIDE;
            } else {
                triggerRight = x + VERTICAL_TRIGGER_INSIDE;
            }
        }

        updateSlideOffset(inZone);

        currentFrame = switch (slideOffset) {
            case 0 -> subtype & 0x7F;
            case SLIDE_MAX -> 2;
            default -> 1;
        };
    }

    private boolean isInVerticalZone(AbstractPlayableSprite player,
                                   int left, int right, int top, int bottom) {
        if (player == null) return false;
        if (player.isOnObject()) return false;

        int px = player.getCentreX();
        int py = player.getCentreY();
        return px >= left && px <= right && py >= top && py <= bottom;
    }

    private boolean isSidekickInVerticalZone(int left, int right, int top, int bottom) {
        try {
            var sidekicks = services().spriteManager().getSidekicks();
            for (AbstractPlayableSprite sprite : sidekicks) {
                if (sprite.isOnObject()) continue;
                int px = sprite.getCentreX();
                int py = sprite.getCentreY();
                if (px >= left && px <= right && py >= top && py <= bottom) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }

    private void updateHorizontalDoor(AbstractPlayableSprite mainPlayer) {
        boolean flipped = (spawn.rawFlags() & 0x2000) != 0;

        int triggerTop = y + HORIZONTAL_TRIGGER_TOP;
        int triggerBottom = y + HORIZONTAL_TRIGGER_BOTTOM;
        int triggerLeft, triggerRight;

        if (flipped) {
            triggerLeft = x - HORIZONTAL_TRIGGER_BOTTOM;
            triggerRight = x + HORIZONTAL_TRIGGER_TOP;
        } else {
            triggerLeft = x + HORIZONTAL_TRIGGER_TOP;
            triggerRight = x + HORIZONTAL_TRIGGER_BOTTOM;
        }

        boolean inZone = isInHorizontalZone(mainPlayer, triggerLeft, triggerRight, triggerTop, triggerBottom);
        inZone |= isSidekickInHorizontalZone(triggerLeft, triggerRight, triggerTop, triggerBottom);

        updateSlideOffset(inZone);

        currentFrame = subtype & 0x7F;
    }

    private boolean isInHorizontalZone(AbstractPlayableSprite player,
                                      int left, int right, int top, int bottom) {
        if (player == null) return false;
        if (player.isOnObject()) return false;

        int px = player.getCentreX();
        int py = player.getCentreY();
        return px >= left && px <= right && py >= top && py <= bottom;
    }

    private boolean isSidekickInHorizontalZone(int left, int right, int top, int bottom) {
        try {
            var sidekicks = services().spriteManager().getSidekicks();
            for (AbstractPlayableSprite sprite : sidekicks) {
                if (sprite.isOnObject()) continue;
                int px = sprite.getCentreX();
                int py = sprite.getCentreY();
                if (px >= left && px <= right && py >= top && py <= bottom) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Ignore
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
            int drawX = x;
            int drawY = y;

            if (isHorizontal) {
                boolean flipped = (spawn.rawFlags() & 0x2000) != 0;
                drawX = flipped ? x - slideOffset : x + slideOffset;
            } else {
                drawY = y - slideOffset;
            }

            renderer.drawFrameIndex(currentFrame, drawX, drawY, false, false);
        }
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        if (isHorizontal) {
            return y;
        }
        return y - slideOffset;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(priority);
    }

    @Override
    public boolean isPersistent() {
        return false;
    }
}
