package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Shared handle/ride logic for AIZ ride-vine objects (Obj06/Obj0C).
 * Mirrors sub_220C2 and related frame/offset tables from sonic3k.asm.
 */
final class AizVineHandleLogic {
    private static final int RELEASE_DELAY = 0x3C;
    private static final int PLAYER_HANG_Y_OFFSET = 0x14;
    private static final int GRAB_HALF_WIDTH = 0x10;
    private static final int GRAB_HEIGHT = 0x18;

    // byte_22248 / byte_22A4C
    private static final int[] MODE0_PLAYER_FRAMES = {
            0x91, 0x91, 0x90, 0x90, 0x90, 0x90, 0x90, 0x90,
            0x92, 0x92, 0x92, 0x92, 0x92, 0x92, 0x91, 0x91
    };

    // byte_222D4
    private static final int[] MODE1_PLAYER_FRAMES = {
            0x78, 0x78, 0x7F, 0x7F, 0x7E, 0x7E, 0x7D, 0x7D,
            0x7C, 0x7C, 0x7B, 0x7B, 0x7A, 0x7A, 0x79, 0x79
    };

    // byte_222E4 (x, y pairs)
    private static final int[] MODE1_PLAYER_OFFSETS = {
            0, 0x18,
            -0x12, 0x13,
            -0x18, 0,
            -0x12, -0x13,
            0, -0x18,
            0x12, -0x13,
            0x18, 0,
            0x12, 0x13
    };

    static final class PlayerState {
        int grabFlag;
        int releaseDelay;
    }

    static final class State {
        int mode;
        int x;
        int y;
        int prevX;
        int prevY;
        final PlayerState p1 = new PlayerState();
        final PlayerState p2 = new PlayerState();
    }

    private AizVineHandleLogic() {
    }

    static void positionFromParent(State state, int parentX, int parentY, int parentAngle) {
        int oldX = state.x;
        int oldY = state.y;
        int angle = (angleByte(parentAngle) + 4) & 0xF8;
        int sin = TrigLookupTable.sinHex(angle);
        int cos = TrigLookupTable.cosHex(angle);

        int xOffset = (-sin + 8) >> 4;
        int yOffset = (cos + 8) >> 4;

        state.x = parentX + xOffset;
        state.y = parentY + yOffset;

        if (state.x != oldX) {
            state.prevX = oldX;
        }
        if (state.y != oldY) {
            state.prevY = oldY;
        }
    }

    static boolean anyGrabbed(State state) {
        return state.p1.grabFlag != 0 || state.p2.grabFlag != 0;
    }

    static boolean shouldRender(State state) {
        return !anyGrabbed(state) || state.mode == 0;
    }

    static void markGrabbedAsFastEject(State state) {
        if (state.p1.grabFlag != 0) {
            state.p1.grabFlag = (byte) 0x81;
        }
        if (state.p2.grabFlag != 0) {
            state.p2.grabFlag = (byte) 0x81;
        }
    }

    static void updatePlayers(State state,
            AbstractPlayableSprite player1,
            AbstractPlayableSprite player2,
            int parentAngle) {
        updatePlayer(state, state.p1, player1, parentAngle);
        updatePlayer(state, state.p2, player2, parentAngle);
    }

    private static void updatePlayer(State handle,
            PlayerState playerState,
            AbstractPlayableSprite player,
            int parentAngle) {
        if (playerState.grabFlag != 0) {
            updateGrabbedPlayer(handle, playerState, player, parentAngle);
            return;
        }

        if (playerState.releaseDelay > 0) {
            playerState.releaseDelay--;
            if (playerState.releaseDelay > 0) {
                return;
            }
        }

        if (player == null || !canGrab(handle, player)) {
            return;
        }

        // sub_220C2 capture path (loc_22302)
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setCentreX((short) handle.x);
        player.setCentreY((short) (handle.y + PLAYER_HANG_Y_OFFSET));
        player.setAnimationId(Sonic3kAnimationIds.HANG2);
        player.setForcedAnimationId(Sonic3kAnimationIds.HANG2);
        player.setObjectMappingFrameControl(true);
        player.setSpindash(false);
        player.setObjectControlled(true);
        player.setControlLocked(true);
        player.setRenderFlips(player.getDirection() == Direction.LEFT, false);
        playerState.grabFlag = 1;
        GameServices.audio().playSfx(Sonic3kSfx.GRAB.id);
    }

    private static boolean canGrab(State handle, AbstractPlayableSprite player) {
        int dx = player.getCentreX() - handle.x;
        if (dx < -GRAB_HALF_WIDTH || dx >= GRAB_HALF_WIDTH) {
            return false;
        }
        int dy = player.getCentreY() - handle.y;
        if (dy < 0 || dy >= GRAB_HEIGHT) {
            return false;
        }
        if (player.isObjectControlled() || player.isControlLocked()) {
            return false;
        }
        if (player.isHurt() || player.getDead() || player.isDebugMode()) {
            return false;
        }
        return true;
    }

    private static void updateGrabbedPlayer(State handle,
            PlayerState playerState,
            AbstractPlayableSprite player,
            int parentAngle) {
        // loc_2217E: forced eject state ($81)
        if (playerState.grabFlag < 0) {
            if (player != null) {
                player.setXSpeed((short) 0x300);
                player.setYSpeed((short) 0x200);
                player.setAir(true);
                clearPlayerControl(player);
            }
            playerState.grabFlag = 0;
            playerState.releaseDelay = RELEASE_DELAY;
            return;
        }

        if (player == null || player.isHurt() || player.getDead() || player.isDebugMode()) {
            if (player != null) {
                clearPlayerControl(player);
            }
            playerState.grabFlag = 0;
            playerState.releaseDelay = RELEASE_DELAY;
            return;
        }

        if (player.isJumpPressed()) {
            clearPlayerControl(player);
            playerState.grabFlag = 0;
            playerState.releaseDelay = RELEASE_DELAY;
            launchPlayer(handle, player, parentAngle);
            return;
        }

        // loc_221EC / loc_22258 hold-position paths.
        if (handle.mode == 0) {
            setPlayerHeldMode0(handle, player, parentAngle);
        } else {
            setPlayerHeldMode1(handle, player, parentAngle);
        }
        player.setRenderFlips(player.getDirection() == Direction.LEFT, false);
    }

    private static void launchPlayer(State handle, AbstractPlayableSprite player, int parentAngle) {
        if (handle.mode == 1) {
            int angle = angleByte(parentAngle);
            int sin = TrigLookupTable.sinHex(angle);
            int cos = TrigLookupTable.cosHex(angle);
            player.setXSpeed((short) (cos << 3));
            player.setYSpeed((short) (sin << 3));
        } else {
            player.setXSpeed((short) ((handle.x - handle.prevX) << 7));
            player.setYSpeed((short) ((handle.y - handle.prevY) << 7));
            if (player.isLeftPressed()) {
                player.setXSpeed((short) -0x200);
            }
            if (player.isRightPressed()) {
                player.setXSpeed((short) 0x200);
            }
            player.setYSpeed((short) (player.getYSpeed() - 0x380));
        }

        player.setAir(true);
        player.setJumping(true);
        player.applyRollingRadii(false);
        player.setRolling(true);
        player.setAnimationId(Sonic3kAnimationIds.ROLL);
    }

    private static void setPlayerHeldMode0(State handle, AbstractPlayableSprite player, int parentAngle) {
        player.setCentreX((short) handle.x);
        player.setCentreY((short) (handle.y + PLAYER_HANG_Y_OFFSET));

        int angle = angleByte(parentAngle);
        if (player.getDirection() == Direction.LEFT) {
            angle = (-angle) & 0xFF;
        }
        player.setForcedAnimationId(Sonic3kAnimationIds.HANG2);
        int index = ((angle + 8) & 0xFF) >> 4;
        player.setMappingFrame(MODE0_PLAYER_FRAMES[index]);
    }

    private static void setPlayerHeldMode1(State handle, AbstractPlayableSprite player, int parentAngle) {
        int angle = angleByte(parentAngle);
        if (player.getDirection() == Direction.LEFT) {
            angle = (-angle) & 0xFF;
        }
        int index = (((angle + 0x10) & 0xFF) >> 5) & 0x7;
        int frameIndex = index << 1;

        int frame = MODE1_PLAYER_FRAMES[frameIndex];
        int offsetX = MODE1_PLAYER_OFFSETS[frameIndex];
        int offsetY = MODE1_PLAYER_OFFSETS[frameIndex + 1];
        if (player.getDirection() == Direction.LEFT) {
            offsetX = -offsetX;
        }

        player.setAnimationId(Sonic3kAnimationIds.WALK);
        player.setForcedAnimationId(Sonic3kAnimationIds.WALK);
        player.setMappingFrame(frame);
        player.setCentreX((short) (handle.x + offsetX));
        player.setCentreY((short) (handle.y + offsetY));
    }

    static void clearPlayerControl(AbstractPlayableSprite player) {
        player.setObjectMappingFrameControl(false);
        player.setForcedAnimationId(-1);
        player.setControlLocked(false);
        player.setObjectControlled(false);
    }

    private static int angleByte(int angleWord) {
        return (angleWord >> 8) & 0xFF;
    }
}
