package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.physics.TrigLookupTable;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * S3K Obj 0x03 - AIZ Hollow Tree.
 *
 * <p>Primary disassembly references:
 * Obj_AIZHollowTree / sub_1F7CE / AIZTree_SetPlayerPos (sonic3k.asm:43601-43820).
 */
public class AizHollowTreeObjectInstance extends AbstractObjectInstance {
    private static final int TREE_CAPTURE_MIN_X = 0x2C99;
    private static final int TREE_CAPTURE_MAX_X = 0x2D66;

    private static final int CAMERA_LOCK_X = 0x2C60;
    private static final int CAMERA_RELEASE_MIN_X = 0x1300;
    private static final int CAMERA_RELEASE_MAX_X = 0x4000;
    private static final int CAMERA_LOCK_TIMER = 0x3C;

    private static final int MIN_CAPTURE_X_SPEED = 0x600;

    // AIZTree_PlayerFrames table.
    private static final int[] PLAYER_FRAMES = {
            0x69, 0x6A, 0x6B, 0x77, 0x6C, 0x6C, 0x6D, 0x6D, 0x6E, 0x6E, 0x6F, 0x6F,
            0x70, 0x70, 0x71, 0x71, 0x72, 0x72, 0x73, 0x73, 0x74, 0x74, 0x75, 0x75,
            0x76, 0x76, 0x77, 0x77, 0x6C, 0x6C, 0x6D, 0x6D, 0x6E, 0x6E, 0x6F, 0x6F,
            0x70, 0x70, 0x71, 0x71, 0x72, 0x72, 0x73, 0x73, 0x74, 0x74, 0x75, 0x75,
            0x6B, 0x6B, 0x6A, 0x6A, 0x69, 0x69
    };

    private static final int PLAYER_SLOT_MAIN = 0;
    private static final int PLAYER_SLOT_SIDEKICK = 1;

    private final int treeX;
    private final int treeY;
    private final int[] progress = new int[2];
    private final boolean[] riding = new boolean[2];

    private int cameraLockTimer;

    public AizHollowTreeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "AIZHollowTree");
        this.treeX = spawn.x();
        this.treeY = spawn.y();
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        updatePlayer(player, PLAYER_SLOT_MAIN, true);
        updatePlayer(SpriteManager.getInstance().getSidekick(), PLAYER_SLOT_SIDEKICK, false);
        updateCameraLock(player);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Object is logic-only in ROM (no mappings/art configured in Obj_AIZHollowTree init).
    }

    private void updatePlayer(AbstractPlayableSprite player,
            int slot,
            boolean mainPlayer) {
        if (player == null) {
            return;
        }

        if (!riding[slot]) {
            tryCapturePlayer(player, slot, mainPlayer);
            return;
        }

        int absGroundSpeed = Math.abs(player.getGSpeed());
        if (absGroundSpeed < MIN_CAPTURE_X_SPEED) {
            if ((progress[slot] & 0xFFFF) >= 0x400) {
                fallOffTree(player, slot);
                return;
            }
            setPlayerOnTree(player, slot);
            fallOffTree(player, slot);
            return;
        }

        if (!player.getAir()) {
            int dy = player.getCentreY() - treeY;
            int check = dy + 0x90;
            if (check < 0 || check > 0x130) {
                fallOffTree(player, slot);
                return;
            }
            setPlayerOnTree(player, slot);
            return;
        }

        if (player.getCentreX() < TREE_CAPTURE_MIN_X) {
            player.setCentreX((short) TREE_CAPTURE_MIN_X);
            player.setXSpeed((short) 0x400);
        }
        if (player.getCentreX() >= TREE_CAPTURE_MAX_X) {
            player.setCentreX((short) TREE_CAPTURE_MAX_X);
            player.setXSpeed((short) -0x400);
        }
        fallOffTree(player, slot);
    }

    private void tryCapturePlayer(AbstractPlayableSprite player, int slot, boolean mainPlayer) {
        if (player.getAir()) {
            return;
        }
        int dx = (player.getCentreX() + 0x10) - treeX;
        if (dx < 0 || dx >= 0x40) {
            return;
        }
        int dy = player.getCentreY() - treeY;
        if (dy < -0x5A || dy > 0xA0) {
            return;
        }
        if (player.getXSpeed() < MIN_CAPTURE_X_SPEED) {
            return;
        }
        if (player.isObjectControlled() || player.isControlLocked()) {
            return;
        }

        riding[slot] = true;
        progress[slot] = 0;

        player.setOnObject(true);
        player.setObjectMappingFrameControl(true);
        player.setForcedAnimationId(0);
        player.setObjectControlled(false);
        player.setControlLocked(true);
        // RideObject_SetRide semantics: preserve horizontal inertia as ground speed.
        player.setGSpeed(player.getXSpeed());
        player.setAnimationId(0);

        if (mainPlayer) {
            Camera camera = Camera.getInstance();
            camera.setMinX((short) CAMERA_LOCK_X);
            camera.setMaxX((short) CAMERA_LOCK_X);
            cameraLockTimer = CAMERA_LOCK_TIMER;
            spawnDynamicObject(new AizTreeRevealControlObjectInstance(treeX, treeY));
        }
    }

    private void setPlayerOnTree(AbstractPlayableSprite player, int slot) {
        // This object is logic-only and not a SolidObjectProvider, so SolidContacts would
        // otherwise clear onObject each frame. Keep this sticky while the tree ride is active.
        player.setOnObject(true);
        player.setObjectMappingFrameControl(true);
        player.setForcedAnimationId(0);

        int progressValue = progress[slot];
        progressValue += player.getGSpeed() << 8;
        progress[slot] = progressValue;
        if (progressValue < 0) {
            fallOffTree(player, slot);
            return;
        }

        if ((progressValue & 0xFFFF) >= 0x400) {
            Camera camera = Camera.getInstance();
            camera.setMinX((short) CAMERA_RELEASE_MIN_X);
            camera.setMaxX((short) CAMERA_RELEASE_MAX_X);
        }

        int oldX = player.getCentreX();
        int angle = ((progressValue & 0xFFFF) >>> 1) & 0xFF;
        int sin = TrigLookupTable.sinHex(angle);
        int xOffset = (sin * 0x7000) >> 16;
        int newX = treeX + xOffset;
        player.setCentreX((short) newX);
        player.setXSpeed((short) ((newX - oldX) << 8));

        int oldY = player.getCentreY();
        int yOffset = 0x90 - ((progressValue & 0xFFFF) >>> 2);
        int newY = treeY + yOffset;
        player.setCentreY((short) newY);
        player.setYSpeed((short) ((newY - oldY) << 8));

        int frameIndex = (((progressValue & 0xFFFF) >>> 1) / 0x0B);
        frameIndex = Math.clamp(frameIndex, 0, PLAYER_FRAMES.length - 1);
        player.setMappingFrame(PLAYER_FRAMES[frameIndex]);
    }

    private void fallOffTree(AbstractPlayableSprite player, int slot) {
        riding[slot] = false;
        progress[slot] = 0;

        player.setAir(true);
        player.setRolling(false);
        player.applyStandingRadii(false);
        player.setAnimationId(1);
        player.setOnObject(false);
        player.setFlipsRemaining(0);
        player.setFlipSpeed(4);
        player.setForcedAnimationId(-1);
        player.setObjectMappingFrameControl(false);
        player.setControlLocked(false);
        player.setObjectControlled(false);
        player.setXSpeed((short) (player.getXSpeed() >> 1));
        player.setYSpeed((short) (player.getYSpeed() >> 1));
    }

    private void updateCameraLock(AbstractPlayableSprite mainPlayer) {
        if (riding[PLAYER_SLOT_MAIN] || riding[PLAYER_SLOT_SIDEKICK] || cameraLockTimer <= 0 || mainPlayer == null) {
            return;
        }

        Camera camera = Camera.getInstance();
        cameraLockTimer--;
        if (cameraLockTimer == 0) {
            camera.setMinX((short) CAMERA_RELEASE_MIN_X);
            camera.setMaxX((short) CAMERA_RELEASE_MAX_X);
            return;
        }

        if (camera.getMinX() != CAMERA_RELEASE_MIN_X) {
            if (mainPlayer.getCentreX() >= 0x2D00) {
                camera.setMinX((short) CAMERA_RELEASE_MIN_X);
            } else {
                camera.setMinX((short) (camera.getMinX() - 4));
            }
        }

        if (camera.getMaxX() != CAMERA_RELEASE_MAX_X) {
            if (mainPlayer.getCentreX() < 0x2D00) {
                camera.setMaxX((short) CAMERA_RELEASE_MAX_X);
            } else {
                camera.setMaxX((short) (camera.getMaxX() + 4));
            }
        }
    }

    /**
     * Obj_AIZ1TreeRevealControl parity shim.
     * Tracks the Events_fg_4 counter used by AIZ terrain reveal scripting.
     */
    private static final class AizTreeRevealControlObjectInstance extends AbstractObjectInstance {
        private static int eventsFg4;
        private int timer2E;
        private boolean forceIncrement;

        private AizTreeRevealControlObjectInstance(int x, int y) {
            super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "AIZ1TreeRevealControl");
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (timer2E != 0 && eventsFg4 == 0) {
                setDestroyed(true);
                return;
            }

            timer2E--;
            if (player == null) {
                return;
            }

            int target = ((0x480 - player.getCentreY()) >> 3) + 3;
            if (eventsFg4 > target && !forceIncrement) {
                return;
            }
            eventsFg4++;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // Logic-only control object.
        }
    }
}
