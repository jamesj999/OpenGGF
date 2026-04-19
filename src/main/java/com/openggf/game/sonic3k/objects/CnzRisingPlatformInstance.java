package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;

import java.util.List;

/**
 * ROM object: {@code Obj_CNZRisingPlatform}.
 *
 * <p>The verified CNZ disassembly uses {@code Map_CNZRisingPlatform} and the
 * {@code Anim - Rising Platform.asm} table from the lock-on ROM data set. The
 * object is not subtype-driven: it idles on the floor, arms when stood on,
 * compresses downward while carrying the player, then springs back and
 * settles when released.
 */
public final class CnzRisingPlatformInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final int HALF_WIDTH = 0x18;
    private static final int HALF_HEIGHT = 0x10;
    private static final int FLOOR_Y_RADIUS = 6;
    private static final int Y_ACCEL_STANDING = 0x18;
    private static final int Y_ACCEL_SETTLING = 8;
    private static final int Y_VELOCITY_MAX = 0x200;
    private static final int PRIORITY_BUCKET = 5;

    private final SubpixelMotion.State motion;
    private boolean armed;
    private boolean standingThisFrame;
    private int displayFrame;

    public CnzRisingPlatformInstance(ObjectSpawn spawn) {
        super(spawn, "CNZRisingPlatform");
        this.motion = new SubpixelMotion.State(spawn.x(), spawn.y(), 0, 0, 0, 0);
        this.displayFrame = 0;
        updateDynamicSpawn(spawn.x(), spawn.y());
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        boolean standing = standingThisFrame;
        standingThisFrame = false;

        if (!armed) {
            if (standing) {
                armed = true;
                displayFrame = 1;
            }

            if (motion.yVel != 0) {
                moveSprite2();
                motion.yVel += Y_ACCEL_SETTLING;
                if (motion.yVel >= 0) {
                    motion.yVel = 0;
                    displayFrame = 2;
                }
            }
        } else if (standing) {
            moveSprite2();
            if (displayFrame == 2) {
                snapToFloorIfNeeded(true);
                displayFrame = 2;
            } else {
                if (motion.yVel < Y_VELOCITY_MAX) {
                    motion.yVel += Y_ACCEL_STANDING;
                }
                if (snapToFloorIfNeeded(true)) {
                    displayFrame = 2;
                } else {
                    displayFrame = 1;
                }
            }
        } else {
            motion.yVel = -motion.yVel - 0x80;
            armed = false;
            displayFrame = 2;
            try {
                services().playSfx(Sonic3kSfx.BALLOON_PLATFORM.id);
            } catch (Exception ignored) {
                // Headless tests can omit the audio backend; the motion state still updates.
            }
            updateDynamicSpawn(motion.x, motion.y);
            return;
        }

        if (!standing && motion.yVel != 0) {
            snapToFloorIfNeeded(false);
        }

        updateDynamicSpawn(motion.x, motion.y);
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
        renderer.drawFrameIndex(displayFrame, getX(), getY(), hFlip, false);
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
            standingThisFrame = true;
        }
    }

    boolean isArmedForTest() {
        return armed;
    }

    int getRenderFrameForTest() {
        return displayFrame;
    }

    int getYSpeedForTest() {
        return motion.yVel;
    }

    private void moveSprite2() {
        SubpixelMotion.moveSprite2(motion);
    }

    private boolean snapToFloorIfNeeded(boolean preserveVelocity) {
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(motion.x, motion.y, FLOOR_Y_RADIUS);
        if (floor.foundSurface() && floor.distance() < 0) {
            motion.y += floor.distance();
            motion.ySub = 0;
            if (!preserveVelocity) {
                motion.yVel = 0;
            }
            displayFrame = 2;
            return true;
        }
        return false;
    }
}
