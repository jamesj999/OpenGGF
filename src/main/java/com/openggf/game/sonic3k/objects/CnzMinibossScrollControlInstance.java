package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.events.S3kCnzEventWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * Minimal CNZ miniboss scroll-control helper for Task 7.
 *
 * <p>ROM anchor: {@code Obj_CNZMinibossScrollControl}.
 *
 * <p>The disassembly uses two fixed-point words:
 * {@code Events_bg+$08} as the accumulated vertical boss-scroll offset and
 * {@code Events_bg+$0C} as the current 16.16 speed. The helper accelerates,
 * then after the boss-defeat flag arrives it waits until the accumulator reaches
 * {@code $1C0} before setting {@code Events_fg_5}. Task 7 preserves exactly
 * that producer responsibility while deliberately omitting the earlier init and
 * layout-copy routines that are outside the current test scope.
 */
public final class CnzMinibossScrollControlInstance extends AbstractObjectInstance {
    /**
     * ROM: {@code addi.l #$200,d0} in {@code Obj_CNZMinibossScrollMain}.
     */
    private static final int ACCELERATION_STEP_16_16 = 0x200;

    /**
     * ROM: {@code cmpi.l #$40000,d0}.
     */
    private static final int MAX_SPEED_16_16 = 0x40000;

    /**
     * ROM: {@code cmpi.w #$1C0,(Events_bg+$08).w} in
     * {@code Obj_CNZMinibossScrollWait3}.
     */
    private static final int FG_HANDOFF_OFFSET_PIXELS = 0x1C0;

    private int currentVelocity16_16;
    private int accumulatedOffset16_16;
    private boolean bossDefeatSignalConsumed;

    public CnzMinibossScrollControlInstance(ObjectSpawn spawn) {
        super(spawn, "CNZMinibossScrollControl");
    }

    /**
     * Test seam that injects the post-defeat phase transition the ROM normally
     * receives from {@code Events_fg_5}.
     */
    public void forceBossDefeatSignalForTest() {
        bossDefeatSignalConsumed = true;
    }

    /**
     * Test seam for the 16.16 accumulated scroll offset.
     */
    public void forceAccumulatedOffsetForTest(int accumulatedOffset16_16) {
        this.accumulatedOffset16_16 = accumulatedOffset16_16;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        /**
         * ROM: {@code Obj_CNZMinibossScrollMain} accelerates by {@code $200}
         * each frame up to {@code $40000}, then adds the speed to the
         * accumulator. Task 7 keeps that exact threshold/slope so the published
         * values are still traceable to the original helper.
         */
        currentVelocity16_16 = Math.min(currentVelocity16_16 + ACCELERATION_STEP_16_16,
                MAX_SPEED_16_16);
        accumulatedOffset16_16 += currentVelocity16_16;

        /**
         * The bridge publishes the ROM-equivalent scroll values into CNZ event
         * state. Tests then drive the background handler to prove that this
         * object, not a direct event hook, is the producer of the
         * {@code Events_fg_5} handoff.
         */
        S3kCnzEventWriteSupport.setBossScrollState(
                services(), accumulatedOffset16_16 >> 16, currentVelocity16_16);

        if (bossDefeatSignalConsumed && (accumulatedOffset16_16 >> 16) >= FG_HANDOFF_OFFSET_PIXELS) {
            S3kCnzEventWriteSupport.setEventsFg5(services(), true);
            setDestroyed(true);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Task 7 validates the event-production contract only.
    }
}
