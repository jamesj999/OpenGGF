package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectServices;
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
 * Object 0x47 - CNZ Cylinder ({@code Obj_CNZCylinder}).
 *
 * <p>This class ports the ROM motion families from {@code sub_321E2} and the
 * rider-control seam from {@code sub_324C0}.</p>
 */
public final class CnzCylinderInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {
    private static final SolidObjectParams SOLID_PARAMS =
            new SolidObjectParams(0x2B, 0x20, 0x21);
    private static final int PLAYER_CAPTURE_PRIORITY = RenderPriority.PLAYER_DEFAULT;
    private static final int PLAYER_TWIST_PRIORITY = RenderPriority.PLAYER_DEFAULT - 1;
    private static final int PRIORITY_THRESHOLD_SOURCE = 0x60;
    private static final int RECAPTURE_COOLDOWN_FRAMES = 2;
    private static final int RELEASE_Y_SPEED = -0x680;
    private static final int[] MODE0_SPEED_CAPS = {
            0x04E0, 0x06F0, 0x0870, 0x09C0, 0x0AE0, 0x0C00, 0x0CF0, 0x0DE0
    };
    private static final int[] PLAYER_TWIST_FRAMES = {
            0x55, 0x59, 0x5A, 0x5B, 0x5A, 0x59, 0x55, 0x56, 0x57, 0x58, 0x57, 0x56
    };
    private static final boolean[] PLAYER_TWIST_FLIPS = {
            false, true, true, false, false, false, true, true, true, false, false, false
    };

    private static final int CIRCULAR_HALF_EXTENT = 0x20;

    private static final class RiderSlot {
        private boolean active;
        private boolean contactLatched;
        private int twistAngle;
        private int horizontalDistance;
        private int priorityThresholdSource;
        private AbstractPlayableSprite player;
    }

    private final int baseX;
    private final int baseY;
    private final int motionSelector;
    private final int speedCap;
    private final boolean circularRoute;
    private final int angleStep;
    private final RiderSlot playerOneSlot = new RiderSlot();
    private final RiderSlot playerTwoSlot = new RiderSlot();

    private int routeQuadrant;
    private int centerX;
    private int centerY;
    private int standingMaskCache;
    private int standingMask;
    private int nextStandingMask;
    private int heldInputMask;
    private int nextHeldInputMask;
    private int mode0Velocity;
    private int mode0YSubpixel;
    private int currentYVelocity;
    private int angle;
    private int mappingFrame;
    private int animFrameTimer = 0;

    public CnzCylinderInstance(ObjectSpawn spawn) {
        super(spawn, "CNZCylinder");
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.centerX = spawn.x();
        this.centerY = spawn.y();
        int subtype = spawn.subtype() & 0xFF;
        this.motionSelector = (subtype << 1) & 0x1E;
        this.speedCap = MODE0_SPEED_CAPS[((subtype >>> 3) & 0x0E) >>> 1];
        this.circularRoute = motionSelector >= 0x12;
        this.routeQuadrant = ((subtype & 0x0F) - 0x0A) & 0x03;
        int step = (subtype & 0xF0) << 2;
        if ((spawn.renderFlags() & 0x01) != 0) {
            step = -step;
        }
        this.angleStep = step;
        this.mode0Velocity = 0;
        this.mode0YSubpixel = 0;
        this.currentYVelocity = 0;
        // Obj_CNZCylinder init falls through directly into loc_32188, so the
        // first sub_321E2 motion pass has already happened by the first full
        // engine update after spawn.
        updateMotion();
        updateDynamicSpawn(centerX, centerY);
    }

    @Override
    public boolean isSkipSolidContactThisFrame() {
        // ROM: Obj_CNZCylinder falls through from init into loc_32188 and calls
        // SolidObjectFull immediately; it does not guard the first frame on obRender bit 7.
        return false;
    }

    @Override
    public int getOutOfRangeReferenceX() {
        // ROM loc_32188 calls Sprite_OnScreen_Test2 with $2E(a0), the saved
        // placement X, after the cylinder has moved away from its current x_pos.
        return baseX;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        // ROM sub_324C0 / SolidObjectFull (sonic3k.asm:41006-41008): when a
        // rider is offscreen (`tst.b render_flags(a1); bpl.w locret_1DCB4`)
        // the entire SolidObjectFull pass for that rider is skipped, so the
        // cylinder's per-rider standing bit is NOT cleared. The engine's
        // SolidContacts pass also skips offscreen objects (line 4444 gate),
        // so `nextStandingMask` will be 0 even though the rider is still
        // logically anchored to the cylinder. Preserve the previous frame's
        // bits for any rider whose render_flags bit 7 is currently clear so
        // the alternating capture/release cycle in updateRiderSlot can
        // continue to re-capture the offscreen rider each frame.
        //
        int preservedStanding = 0;
        if (playerOneSlot.player != null
                && !riderRenderFlagOnScreen(playerOneSlot.player)) {
            preservedStanding |= (standingMask & 0x01);
        }
        if (playerTwoSlot.player != null
                && !riderRenderFlagOnScreen(playerTwoSlot.player)) {
            preservedStanding |= (standingMask & 0x02);
        }

        standingMask = nextStandingMask | preservedStanding;
        heldInputMask = nextHeldInputMask;
        nextStandingMask = 0;
        nextHeldInputMask = 0;

        primeDefaultRiderSlots(playerEntity);
        int previousCenterY = centerY;
        updateMotion();
        currentYVelocity = motionSelector == 0 ? mode0Velocity : ((centerY - previousCenterY) << 8);
        updateRiderSlots(frameCounter);
        advanceAnimation();
    }

    private static boolean riderRenderFlagOnScreen(AbstractPlayableSprite rider) {
        return !rider.hasRenderFlagOnScreenState() || rider.isRenderFlagOnScreen();
    }

    private void updateMotion() {
        if (circularRoute) {
            updateCircularRoute();
        } else {
            switch (motionSelector) {
                case 0x00 -> updateMode0VerticalController();
                case 0x02 -> updateHorizontalShift(3);
                case 0x04 -> updateHorizontalShift(2);
                case 0x06 -> updateHorizontalThreeEighths();
                case 0x08 -> updateHorizontalShift(1);
                case 0x0A -> updateVerticalShift(3);
                case 0x0C -> updateVerticalShift(2);
                case 0x0E -> updateVerticalThreeEighths();
                case 0x10 -> updateVerticalShift(1);
                default -> updateMode0VerticalController();
            }
        }

        updateDynamicSpawn(centerX, centerY);
    }

    private void updateMode0VerticalController() {
        int standingMask = currentStandingMask();
        if (standingMask != standingMaskCache) {
            int delta = standingMask - standingMaskCache;
            standingMaskCache = standingMask;
            // ROM sonic3k.asm:67725-67729 (loc_32208): only apply the +0x400
            // player-landing boost when the cylinder is within ±0x40 pixels of
            // its base. The ROM check is `addi.w #$40, d0; cmpi.w #$80, d0; bhs`
            // i.e. the boost only fires when (centerY - baseY) is in [-0x40, 0x40).
            // Without this gate the engine over-injects downward velocity when
            // Tails lands during the cylinder's return upswing, which was the
            // root cause of CNZ trace F1685 tails_y_speed mismatch (the cylinder's
            // upward velocity at jump-release was 0x22F too low).
            // Applied BEFORE moveMode0Sprite2() to match ROM ordering at loc_32254.
            int preMoveOffset = (centerY - baseY) + 0x40;
            boolean withinBoostBand = preMoveOffset >= 0 && preMoveOffset < 0x80;
            if (delta > 0 && Math.abs(mode0Velocity) < 0x200 && withinBoostBand) {
                mode0Velocity += 0x400;
                if (mode0Velocity > speedCap) {
                    mode0Velocity = speedCap;
                }
            }
        }

        centerX = baseX;
        moveMode0Sprite2();

        int offset = centerY - baseY;
        if (offset < 0) {
            if (mode0Velocity < speedCap) {
                mode0Velocity += 0x20;
                if (mode0Velocity < 0) {
                    mode0Velocity += 0x10;
                } else if ((collectHeldInputMask() & AbstractPlayableSprite.INPUT_DOWN) != 0) {
                    mode0Velocity += 0x20;
                }
            }
            return;
        }

        if (offset > 0) {
            int negativeCap = -speedCap;
            if (mode0Velocity > negativeCap) {
                mode0Velocity -= 0x20;
                if (mode0Velocity > 0) {
                    mode0Velocity -= 0x10;
                } else if ((collectHeldInputMask() & AbstractPlayableSprite.INPUT_UP) != 0) {
                    mode0Velocity -= 0x20;
                }
            }
            return;
        }

        if (Math.abs(mode0Velocity) < 0x80) {
            mode0Velocity = 0;
        }
    }

    private void moveMode0Sprite2() {
        // ROM loc_32254 calls MoveSprite2, so y_pos carries the low byte of
        // y_vel between frames even though the visible object centre is a word.
        int yTotal = (mode0YSubpixel & 0xFF) + (mode0Velocity & 0xFF);
        centerY += (mode0Velocity >> 8) + (yTotal >> 8);
        mode0YSubpixel = yTotal & 0xFF;
    }

    private void updateHorizontalShift(int shift) {
        int sine = TrigLookupTable.sinHex((angle >> 8) & 0xFF);
        centerX = baseX + (sine >> shift);
        centerY = baseY;
        angle = (angle + angleStep) & 0xFFFF;
    }

    private void updateHorizontalThreeEighths() {
        int sine = TrigLookupTable.sinHex((angle >> 8) & 0xFF);
        int shifted = sine >> 2;
        centerX = baseX + shifted + (shifted >> 1);
        centerY = baseY;
        angle = (angle + angleStep) & 0xFFFF;
    }

    private void updateVerticalShift(int shift) {
        int sine = TrigLookupTable.sinHex((angle >> 8) & 0xFF);
        centerX = baseX;
        centerY = baseY + (sine >> shift);
        angle = (angle + angleStep) & 0xFFFF;
    }

    private void updateVerticalThreeEighths() {
        int sine = TrigLookupTable.sinHex((angle >> 8) & 0xFF);
        int shifted = sine >> 2;
        centerX = baseX;
        centerY = baseY + shifted + (shifted >> 1);
        angle = (angle + angleStep) & 0xFFFF;
    }

    private void updateCircularRoute() {
        angle = (angle + angleStep) & 0xFFFF;
        int angleByte = (angle >> 8) & 0xFF;

        if (angleStep < 0) {
            if (angleByte < 0x80) {
                angleByte = (angleByte & 0x7F) + 0x80;
                angle = (angle & 0x00FF) | (angleByte << 8);
                routeQuadrant = (routeQuadrant - 1) & 0x03;
            }
        } else if (angleStep > 0) {
            if (angleByte < 0x80) {
                angleByte = (angleByte & 0x7F) + 0x80;
                angle = (angle & 0x00FF) | (angleByte << 8);
                routeQuadrant = (routeQuadrant + 1) & 0x03;
            }
        }

        int varying = TrigLookupTable.cosHex(angleByte) >> 3;
        switch (routeQuadrant & 0x03) {
            case 0 -> {
                centerX = baseX + varying;
                centerY = baseY - CIRCULAR_HALF_EXTENT;
            }
            case 1 -> {
                centerX = baseX + CIRCULAR_HALF_EXTENT;
                centerY = baseY + varying;
            }
            case 2 -> {
                centerX = baseX - varying;
                centerY = baseY + CIRCULAR_HALF_EXTENT;
            }
            default -> {
                centerX = baseX - CIRCULAR_HALF_EXTENT;
                centerY = baseY - varying;
            }
        }
    }

    private int currentStandingMask() {
        return standingMask;
    }

    private int collectHeldInputMask() {
        return heldInputMask;
    }

    private void updateRiderSlots(int frameCounter) {
        updateRiderSlot(playerOneSlot, frameCounter);
        updateRiderSlot(playerTwoSlot, frameCounter);
    }

    private void updateRiderSlot(RiderSlot slot, int frameCounter) {
        AbstractPlayableSprite player = slot.player;
        if (player == null || player.getDead() || player.isHurt()) {
            if (slot.active) {
                releaseSlot(slot, frameCounter, false);
            }
            slot.contactLatched = false;
            return;
        }

        boolean latchedContact = slot.contactLatched;
        slot.contactLatched = false;

        // ROM sub_324C0 loc_32538 (sonic3k.asm:68019-68022): when the captured
        // rider is offscreen (`tst.b render_flags(a1); bpl.w loc_325F2`), the
        // cylinder takes the release branch every frame. ROM SolidObjectFull
        // (sonic3k.asm:41006-41008) ALSO skips Player_2 when his render_flags
        // bit 7 is clear, so the cylinder's p2_standing_bit stays set from the
        // last on-screen frame. The next frame's sub_324C0 (a2)==0 path then
        // re-captures from that preserved standing bit, producing the
        // alternating Status_InAir 0/1 pattern observed at CNZ1 F4489+ when
        // Tails (X=0x1BB9) is just past the right edge of the screen.
        boolean playerOnScreen = !player.hasRenderFlagOnScreenState()
                || player.isRenderFlagOnScreen();

        boolean standing = latchedContact || hasStandingBit(player);
        if (slot.active) {
            if (!playerOnScreen) {
                // ROM loc_325F2: bset Status_InAir, object_control=0, (a2)=0.
                releaseSlot(slot, frameCounter, false);
                return;
            }
            standing = standing || !player.getAir();
            if (!standing) {
                releaseSlot(slot, frameCounter, false);
                return;
            }
            holdSlot(slot);
            if (player.isJumpPressed()) {
                releaseSlot(slot, frameCounter, true);
                return;
            }
            return;
        }

        if (!standing || player.isObjectControlled()) {
            return;
        }
        // ROM sub_324C0 (a2)==0 path re-captures immediately - no ROM cooldown.
        // Bypass the engine RECAPTURE_COOLDOWN_FRAMES guard when offscreen so
        // the alternation can complete each frame.
        if (playerOnScreen
                && player.wasRecentlyObjectControlled(frameCounter, RECAPTURE_COOLDOWN_FRAMES)) {
            return;
        }
        captureSlot(slot, player);
    }

    private boolean hasStandingBit(AbstractPlayableSprite player) {
        int bit = standingMaskBitFor(player);
        return bit != 0 && (standingMask & bit) != 0;
    }

    private void primeDefaultRiderSlots(PlayableEntity playerEntity) {
        if (playerOneSlot.player == null && playerEntity instanceof AbstractPlayableSprite sprite) {
            playerOneSlot.player = sprite;
        }

        ObjectServices svc = tryServices();
        AbstractPlayableSprite focused = svc != null && svc.camera() != null
                ? svc.camera().getFocusedSprite()
                : null;
        if (playerOneSlot.player == null && focused != null) {
            playerOneSlot.player = focused;
        }

        if (playerTwoSlot.player == null) {
            AbstractPlayableSprite firstSidekick = getFirstSidekick();
            if (firstSidekick != null && firstSidekick != playerOneSlot.player) {
                playerTwoSlot.player = firstSidekick;
            }
        }
    }

    private AbstractPlayableSprite getFirstSidekick() {
        ObjectServices svc = tryServices();
        if (svc == null) {
            return null;
        }
        var sidekicks = svc.sidekicks();
        if (sidekicks.isEmpty()) {
            return null;
        }
        PlayableEntity first = sidekicks.getFirst();
        return first instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    private void captureSlot(RiderSlot slot, AbstractPlayableSprite player) {
        slot.player = player;
        slot.active = true;
        slot.twistAngle = player.getCentreX() < centerX ? 0x80 : 0x00;
        slot.horizontalDistance = Math.min(0xFF, Math.abs(player.getCentreX() - centerX));
        slot.priorityThresholdSource = getPriorityThresholdSource();
        // ROM Obj_CNZCylinder (sonic3k.asm:67668-67672) calls SolidObjectFull
        // every frame, which sets the cylinder's per-rider standing bit on
        // capture. The engine's SolidObject framework blocks the contact pass
        // for object-controlled players (ObjectManager.java:4120-4131
        // `blocksSolidContacts`), so {@link #onSolidContact} never fires once
        // we mark the rider as objectControlled. Set the standing bit
        // explicitly here so the next frame's update() sees it for
        // preservation across both on-screen and off-screen states. Without
        // this, captureSlot setting objectControlled=true creates a
        // "standing-bit cleared, slot.active=true" inconsistency on the very
        // next frame and breaks the cylinder's alternation semantics when the
        // test reseed preserves objectControlled.
        standingMask |= slotMask(slot);

        ObjectServices svc = tryServices();
        if (svc != null && svc.objectManager() != null) {
            svc.objectManager().clearRidingObject(player);
        }

        player.setObjectControlled(true);
        player.setControlLocked(true);
        player.setObjectMappingFrameControl(true);
        // ROM sub_324C0 restores default_y_radius/default_x_radius and clears
        // Status_Roll while the player is held in the twist animation.
        player.restoreDefaultRadii();
        player.setRolling(false);
        player.setAir(false);
        player.setPushing(false);
        player.setRollingJump(false);
        player.setJumping(false);
        player.setAnimationId(0);
        player.setForcedAnimationId(-1);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setPriorityBucket(PLAYER_CAPTURE_PRIORITY);
        applyTwistFrame(player, slot.twistAngle);
    }

    private void holdSlot(RiderSlot slot) {
        AbstractPlayableSprite player = slot.player;
        if (player == null) {
            return;
        }

        int sine = TrigLookupTable.sinHex(slot.twistAngle);
        int cosine = TrigLookupTable.cosHex(slot.twistAngle);
        int thresholdByte = ((sine + 0x100) >> 2) & 0xFF;
        // ROM loc_32538 stores the threshold byte at 3(a2), then reads the
        // combined word at 2(a2) as the horizontal distance multiplier.
        int distanceWord = ((slot.horizontalDistance & 0xFF) << 8) | thresholdByte;
        int xOffset = (cosine * distanceWord) >> 16;
        player.setCentreXPreserveSubpixel((short) (centerX + xOffset));
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setPushing(false);

        int objectThreshold = slot.priorityThresholdSource & 0xFF;
        player.setPriorityBucket(thresholdByte < objectThreshold
                ? PLAYER_TWIST_PRIORITY
                : PLAYER_CAPTURE_PRIORITY);
        applyTwistFrame(player, slot.twistAngle);
        slot.twistAngle = (slot.twistAngle + 2) & 0xFF;
    }

    private int getPriorityThresholdSource() {
        return PRIORITY_THRESHOLD_SOURCE;
    }

    private void releaseSlot(RiderSlot slot, int frameCounter, boolean jumpedOff) {
        AbstractPlayableSprite player = slot.player;
        if (player == null) {
            slot.active = false;
            return;
        }

        slot.active = false;
        player.setObjectMappingFrameControl(false);
        player.setControlLocked(false);
        player.releaseFromObjectControl(frameCounter);
        player.setPriorityBucket(RenderPriority.PLAYER_DEFAULT);
        player.setForcedAnimationId(-1);
        player.setPushing(false);

        if (jumpedOff) {
            short releaseX = player.getCentreX();
            short releaseY = player.getCentreY();
            player.setAir(true);
            player.setJumping(true);
            player.applyRollingRadii(false);
            player.setRolling(true);
            player.setCentreXPreserveSubpixel(releaseX);
            player.setCentreYPreserveSubpixel(releaseY);
            player.setAnimationId(2);
            player.setYSpeed((short) (currentYVelocity + RELEASE_Y_SPEED));
            player.setXSpeed((short) 0);
            player.setGSpeed((short) 0);
            player.suppressNextJumpPress();
        } else {
            player.setAir(true);
            player.setJumping(false);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
        }
    }

    private void applyTwistFrame(AbstractPlayableSprite player, int twistAngle) {
        int frameIndex = ((twistAngle + 0x0B) & 0xFF) / 0x16;
        if (frameIndex < 0 || frameIndex >= PLAYER_TWIST_FRAMES.length) {
            frameIndex = 0;
        }

        player.setMappingFrame(PLAYER_TWIST_FRAMES[frameIndex]);
        boolean flipLeft = PLAYER_TWIST_FLIPS[frameIndex];
        player.setDirection(flipLeft ? Direction.LEFT : Direction.RIGHT);
        player.setRenderFlips(flipLeft, false);
    }

    private void advanceAnimation() {
        animFrameTimer--;
        if (animFrameTimer >= 0) {
            return;
        }
        animFrameTimer = 1;
        mappingFrame = (mappingFrame + 1) & 0x03;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.CNZ_CYLINDER);
        if (renderer == null) {
            return;
        }

        boolean hFlip = (spawn.renderFlags() & 0x01) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x02) != 0;
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), hFlip, vFlip);
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        return true;
    }

    @Override
    public boolean allowsObjectControlledSolidContacts() {
        // ROM Obj_CNZCylinder writes object_control=$03 on capture
        // (sonic3k.asm:68002), then still calls SolidObjectFull every frame
        // after sub_324C0 (sonic3k.asm:67656-67672). SolidObjectFull's active
        // rider branch can clear the cylinder standing bit when the rider is
        // airborne or leaves bounds (sonic3k.asm:41016-41033), which sub_324C0
        // consumes on the next active-slot check (sonic3k.asm:68019-68025).
        return true;
    }

    @Override
    public boolean isTopSolidOnly() {
        return false;
    }

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (!contact.standing() || !(player instanceof AbstractPlayableSprite sprite)) {
            return;
        }

        RiderSlot slot = resolveContactSlot(sprite);
        if (slot == null) {
            return;
        }

        slot.player = sprite;
        slot.contactLatched = true;

        int mask = slotMask(slot);
        nextStandingMask |= mask;
        nextHeldInputMask |= heldInputMaskFor(sprite);
    }

    private RiderSlot resolveContactSlot(AbstractPlayableSprite sprite) {
        if (playerOneSlot.player == sprite) {
            return playerOneSlot;
        }
        if (playerTwoSlot.player == sprite) {
            return playerTwoSlot;
        }
        ObjectServices svc = tryServices();
        AbstractPlayableSprite focused = svc != null && svc.camera() != null
                ? svc.camera().getFocusedSprite()
                : null;
        if (sprite == focused) {
            return playerOneSlot;
        }
        if (isFirstSidekick(sprite)) {
            return playerTwoSlot;
        }
        if (playerOneSlot.player == null) {
            return playerOneSlot;
        }
        if (playerTwoSlot.player == null) {
            return playerTwoSlot;
        }
        return null;
    }

    private boolean isFirstSidekick(AbstractPlayableSprite sprite) {
        ObjectServices svc = tryServices();
        if (svc == null) {
            return false;
        }
        var sidekicks = svc.sidekicks();
        return !sidekicks.isEmpty() && sidekicks.getFirst() == sprite;
    }

    private int slotMask(RiderSlot slot) {
        return slot == playerOneSlot ? 0x01 : 0x02;
    }

    private int standingMaskBitFor(AbstractPlayableSprite sprite) {
        ObjectServices svc = tryServices();
        AbstractPlayableSprite focused = svc != null && svc.camera() != null
                ? svc.camera().getFocusedSprite()
                : null;
        if (playerOneSlot.player == sprite
                || (sprite == focused && playerTwoSlot.player != sprite)) {
            return 0x01;
        }
        if (playerTwoSlot.player == sprite || isFirstSidekick(sprite)) {
            return 0x02;
        }
        if (svc != null) {
            for (PlayableEntity sidekick : svc.sidekicks()) {
                if (sidekick == sprite) {
                    return 0x02;
                }
            }
        }
        return 0;
    }

    private int heldInputMaskFor(AbstractPlayableSprite sprite) {
        int mask = 0;
        if (sprite.isUpPressed()) {
            mask |= AbstractPlayableSprite.INPUT_UP;
        }
        if (sprite.isDownPressed()) {
            mask |= AbstractPlayableSprite.INPUT_DOWN;
        }
        return mask;
    }
}
