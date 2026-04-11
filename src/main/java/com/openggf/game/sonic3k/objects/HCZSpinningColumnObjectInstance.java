package com.openggf.game.sonic3k.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.OscillationManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x68 - HCZ Spinning Column (Sonic 3 & Knuckles).
 *
 * <p>ROM reference: Obj_HCZSpinningColumn (sonic3k.asm:68108-68179).
 */
public class HCZSpinningColumnObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final String ART_KEY = Sonic3kObjectArtKeys.HCZ_SPINNING_COLUMN;
    private static final int PRIORITY = 5; // ROM: move.w #$280,priority(a0)
    private static final int HALF_WIDTH = 0x10;
    private static final int HALF_HEIGHT = 0x20;
    private static final int ANIM_FRAME_COUNT = 3;
    private static final int ANIM_FRAME_RELOAD = 7;
    private static final int VERTICAL_OSCILLATION_OFFSET = 0x1C; // ROM: Oscillating_table+$1E
    private static final int CAPTURE_SWING_STEP = 2;
    private static final int RELEASE_Y_SPEED = -0x680;
    private static final int PLAYER_PRIORITY = 1; // ROM: move.w #$100,priority(a1)
    private static final int[] PLAYER_TWIST_FRAMES = {
            0x55, 0x59, 0x5A, 0x5B, 0x5A, 0x59, 0x55, 0x56, 0x57, 0x58, 0x57, 0x56
    };
    private static final boolean[] PLAYER_TWIST_FLIPS = {
            false, true, true, false, false, false, true, true, true, false, false, false
    };

    private static final int MOTION_STATIONARY = 0x00;
    private static final int MOTION_HORIZONTAL = 0x02;
    private static final int MOTION_VERTICAL = 0x04;

    private static final class RiderState {
        private AbstractPlayableSprite player;
        private boolean standingLastFrame;
        private boolean active;
        private int swingAngle;
        private int horizontalDistance;
    }

    private final int baseX;
    private final int baseY;
    private final boolean xFlipped;
    private final RiderState[] riders = {new RiderState(), new RiderState()};

    private final int motionMode;
    private int motionOffset;
    private int motionStep;
    private int currentX;
    private int currentY;
    private int currentYVelocity;
    private int mappingFrame;
    private int animFrameTimer;

    public HCZSpinningColumnObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HCZSpinningColumn");
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.xFlipped = (spawn.renderFlags() & 0x01) != 0;

        int subtype = spawn.subtype() & 0xFF;
        this.motionMode = (subtype << 1) & 0x06;
        this.motionOffset = subtype & 0xF0;
        this.motionStep = (motionOffset == 0xE0) ? -1 : 1;
        this.currentX = baseX;
        this.currentY = baseY;
        this.mappingFrame = 0;
        this.animFrameTimer = 0;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        int previousY = currentY;
        switch (motionMode) {
            case MOTION_STATIONARY -> {
                currentX = baseX;
                currentY = baseY;
            }
            case MOTION_HORIZONTAL -> updateHorizontalMotion();
            case MOTION_VERTICAL -> updateVerticalMotion();
            default -> {
                currentX = baseX;
                currentY = baseY;
            }
        }
        currentYVelocity = (currentY - previousY) << 8;

        for (RiderState rider : riders) {
            updateRider(rider, frameCounter);
            rider.standingLastFrame = false;
        }
        updateAnimation();
    }

    private void updateRider(RiderState rider, int frameCounter) {
        AbstractPlayableSprite player = rider.player;
        if (player == null) {
            return;
        }

        if (rider.active) {
            if (player.getDead() || player.isHurt() || !rider.standingLastFrame) {
                releaseRider(rider, frameCounter, false);
                return;
            }
            holdRider(rider, frameCounter);
            return;
        }

        if (!rider.standingLastFrame || player.isObjectControlled() || player.getDead() || player.isHurt()) {
            return;
        }
        captureRider(rider);
    }

    private void captureRider(RiderState rider) {
        AbstractPlayableSprite player = rider.player;
        int deltaX = player.getCentreX() - currentX;
        rider.swingAngle = deltaX < 0 ? 0x80 : 0x00;
        rider.horizontalDistance = Math.min(0xFF, Math.abs(deltaX));
        rider.active = true;

        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setControlLocked(true);
        player.setObjectMappingFrameControl(true);
        player.restoreDefaultRadii();
        player.setRolling(false);
        player.setAir(false);
        player.setPushing(false);
        player.setRollingJump(false);
        player.setJumping(false);
        player.setPriorityBucket(PLAYER_PRIORITY);
        player.setAnimationId(Sonic3kAnimationIds.WALK);
        player.setForcedAnimationId(-1);
        player.setAnimationFrameIndex(0);
        player.setAnimationTick(0);
        applyTwistAnimation(player, rider.swingAngle);
    }

    private void holdRider(RiderState rider, int frameCounter) {
        AbstractPlayableSprite player = rider.player;
        if (rider.horizontalDistance > 0) {
            rider.horizontalDistance--;
        }
        rider.swingAngle = (rider.swingAngle + CAPTURE_SWING_STEP) & 0xFF;
        int xOffset = (TrigLookupTable.cosHex(rider.swingAngle) * rider.horizontalDistance) >> 8;

        player.setCentreX((short) (currentX + xOffset));
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        applyTwistAnimation(player, rider.swingAngle);

        if (player.isJumpPressed()) {
            releaseRider(rider, frameCounter, true);
        }
    }

    private void releaseRider(RiderState rider, int frameCounter, boolean jumpedOff) {
        AbstractPlayableSprite player = rider.player;
        if (player == null) {
            rider.active = false;
            return;
        }

        rider.active = false;
        player.setControlLocked(false);
        player.setObjectMappingFrameControl(false);
        player.setForcedAnimationId(-1);
        player.setOnObject(false);
        player.setPushing(false);
        player.setPriorityBucket(RenderPriority.PLAYER_DEFAULT);

        if (jumpedOff) {
            player.setAir(true);
            player.setJumping(true);
            player.applyRollingRadii(false);
            player.setRolling(true);
            player.setAnimationId(Sonic3kAnimationIds.ROLL);
            player.setYSpeed((short) (currentYVelocity + RELEASE_Y_SPEED));
            player.setXSpeed((short) 0);
            player.setGSpeed((short) 0);
            // Prevent the same held button from re-triggering the normal jump path,
            // which would add the generic jump sound on the release frame.
            player.suppressNextJumpPress();
        } else {
            player.setAnimationId(Sonic3kAnimationIds.WALK);
        }
    }

    private void applyTwistAnimation(AbstractPlayableSprite player, int swingAngle) {
        int frameIndex = ((swingAngle + 0x0B) & 0xFF) / 0x16;
        if (frameIndex < 0 || frameIndex >= PLAYER_TWIST_FRAMES.length) {
            frameIndex = 0;
        }
        player.setMappingFrame(PLAYER_TWIST_FRAMES[frameIndex]);
        // ROM directly writes render_flags (andi.b #$FC / or.b flip), so we must
        // update both the logical direction AND the render flip in the same frame.
        // setDirection alone defers the visual flip to the next animation update,
        // causing a one-frame glitch at the two front-facing transition points.
        boolean flipLeft = PLAYER_TWIST_FLIPS[frameIndex];
        player.setDirection(flipLeft ? Direction.LEFT : Direction.RIGHT);
        player.setRenderFlips(flipLeft, false);
    }

    private void updateHorizontalMotion() {
        int nextOffset = motionOffset + motionStep;
        if (motionStep > 0) {
            if (nextOffset == 0xE0) {
                motionStep = -1;
            }
        } else if (nextOffset == 0x00) {
            motionStep = 1;
        }

        motionOffset = nextOffset;
        int xOffset = motionOffset - 0x70;
        if (xFlipped) {
            xOffset = -xOffset;
        }
        currentX = baseX + xOffset;
        currentY = baseY;
    }

    private void updateVerticalMotion() {
        int oscillation = OscillationManager.getByte(VERTICAL_OSCILLATION_OFFSET) & 0xFF;
        int yOffset = xFlipped ? (0x80 - oscillation) : oscillation;
        currentX = baseX;
        currentY = baseY + yOffset;
    }

    private void updateAnimation() {
        animFrameTimer--;
        if (animFrameTimer >= 0) {
            return;
        }

        animFrameTimer = ANIM_FRAME_RELOAD;
        mappingFrame--;
        if (mappingFrame < 0) {
            mappingFrame = ANIM_FRAME_COUNT - 1;
        }
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(HALF_WIDTH + 0x0B, HALF_HEIGHT, HALF_HEIGHT + 1);
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        if (!contact.standing() || !(playerEntity instanceof AbstractPlayableSprite player)) {
            return;
        }
        RiderState rider = findOrAllocateRider(player);
        if (rider != null) {
            rider.standingLastFrame = true;
        }
    }

    private RiderState findOrAllocateRider(AbstractPlayableSprite player) {
        for (RiderState rider : riders) {
            if (rider.player == player) {
                return rider;
            }
        }
        for (RiderState rider : riders) {
            if (rider.player == null || (!rider.active && !rider.standingLastFrame)) {
                rider.player = player;
                return rider;
            }
        }
        return null;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(ART_KEY);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, false, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        if (ctx == null) {
            return;
        }
        ctx.drawRect(currentX, currentY, HALF_WIDTH, HALF_HEIGHT, 0.2f, 0.8f, 1.0f);
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
        return RenderPriority.clamp(PRIORITY);
    }
}
