package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * S3K Obj $93 - Jawz (HCZ Act 2).
 *
 * <p>ROM reference: {@code Obj_Jawz} (sonic3k.asm:183518-183570).
 * The object is intentionally small: it waits until it is on-screen, then
 * sets its initial horizontal velocity toward the player, animates with a
 * two-frame raw loop, and otherwise uses the shared badnik destruction path.
 */
public final class JawzBadnikInstance extends AbstractS3kBadnikInstance {

    // ObjDat_Jawz: collision_flags = $D7 -> size index $17, standard badnik body.
    private static final int COLLISION_SIZE_INDEX = 0x17;

    // ObjDat_Jawz: priority $280
    private static final int PRIORITY_BUCKET = 5;

    // Set_VelocityXTrackSonic (d4 = -$200)
    private static final int TRACK_SPEED = 0x200;

    // byte_87924: Animate_RawNoSST script {0, 0, 1, $FC}
    // The raw animation is effectively a fast two-frame loop.
    private static final int FRAME_A = 0;
    private static final int FRAME_B = 1;
    private static final int ANIM_RESET_DELAY = 0;

    private boolean initialized;
    private int animTimer = ANIM_RESET_DELAY;

    public JawzBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Jawz",
                Sonic3kObjectArtKeys.HCZ_JAWZ, COLLISION_SIZE_INDEX, PRIORITY_BUCKET);
        this.mappingFrame = FRAME_A;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (destroyed || !isOnScreenX()) {
            return;
        }

        AbstractPlayableSprite player = playerEntity instanceof AbstractPlayableSprite sprite
                ? sprite : null;

        if (!initialized) {
            initializeVelocity(player);
            initialized = true;
            return;
        }

        moveWithVelocity();
        advanceAnimation();
    }

    /**
     * ROM: Set_VelocityXTrackSonic.
     * The enemy starts moving toward the player as soon as it becomes active.
     */
    private void initializeVelocity(AbstractPlayableSprite player) {
        if (player == null || player.getDead()) {
            xVelocity = facingLeft ? -TRACK_SPEED : TRACK_SPEED;
            return;
        }

        if (player.getCentreX() <= currentX) {
            facingLeft = true;
            xVelocity = -TRACK_SPEED;
        } else {
            facingLeft = false;
            xVelocity = TRACK_SPEED;
        }
    }

    /**
     * Animate_RawNoSST parity for byte_87924.
     * The ROM's raw animation is a tight two-frame swim loop.
     */
    private void advanceAnimation() {
        animTimer--;
        if (animTimer >= 0) {
            return;
        }

        animTimer = ANIM_RESET_DELAY;
        mappingFrame = (mappingFrame == FRAME_A) ? FRAME_B : FRAME_A;
    }
}
