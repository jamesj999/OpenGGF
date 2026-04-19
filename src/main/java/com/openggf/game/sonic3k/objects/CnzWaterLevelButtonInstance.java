package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.events.S3kCnzEventWriteSupport;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * CNZ Act 2 water-helper wrapper for the armed button path.
 *
 * <p>ROM anchor: {@code Obj_CNZWaterLevelButton}.
 *
 * <p>The real object uses button collision, checks the shared arm flag, then
 * performs a one-shot write of {@code $0A58} to {@code Target_water_level},
 * clears the arm flag, and plays {@code sfx_Geyser}. Task 7 keeps exactly
 * those side effects and exposes a narrow test seam for the pressed state.
 */
public final class CnzWaterLevelButtonInstance extends AbstractObjectInstance {
    /**
     * ROM: {@code move.w #$A58,(Target_water_level).w}.
     */
    private static final int SECOND_WATER_TARGET_Y = 0x0A58;

    private boolean pressedForTest;

    public CnzWaterLevelButtonInstance(ObjectSpawn spawn) {
        super(spawn, "CNZWaterLevelButton");
    }

    /**
     * Test seam for the pressed state.
     */
    public void forcePressedForTest() {
        pressedForTest = true;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (!pressedForTest || !S3kCnzEventWriteSupport.isWaterButtonArmed(services())) {
            return;
        }
        pressedForTest = false;

        /**
         * ROM: the arm flag is consumed on the same frame as the second water
         * target write, ensuring the button can only fire once per route.
         */
        S3kCnzEventWriteSupport.setWaterButtonArmed(services(), false);
        S3kCnzEventWriteSupport.setWaterTargetY(services(), SECOND_WATER_TARGET_Y);
        services().playSfx(Sonic3kSfx.GEYSER.id);
        setDestroyed(true);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Task 7 validates the one-shot event write only.
    }
}
