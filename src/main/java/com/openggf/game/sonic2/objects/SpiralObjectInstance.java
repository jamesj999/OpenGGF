package com.openggf.game.sonic2.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Object 06 - Spiral pathway from EHZ.
 * <p>
 * Invisible object that modifies the player's Y position and rotation angle as
 * they traverse it.
 * Effectively creates a "wave" motion and sprite twisting effect.
 */
public class SpiralObjectInstance extends AbstractObjectInstance {
    private static final Logger LOGGER = Logger.getLogger(SpiralObjectInstance.class.getName());

    // Obj06_FlipAngleTable (sloopdirtbl)
    private static final byte[] FLIP_ANGLE_TABLE = {
            0x00, 0x00, 0x01, 0x01, 0x16, 0x16, 0x16, 0x16, 0x2C, 0x2C,
            0x2C, 0x2C, 0x42, 0x42, 0x42, 0x42, 0x58, 0x58, 0x58, 0x58,
            0x6E, 0x6E, 0x6E, 0x6E, (byte) 0x84, (byte) 0x84, (byte) 0x84, (byte) 0x84, (byte) 0x9A, (byte) 0x9A,
            (byte) 0x9A, (byte) 0x9A, (byte) 0xB0, (byte) 0xB0, (byte) 0xB0, (byte) 0xB0, (byte) 0xC6, (byte) 0xC6,
            (byte) 0xC6, (byte) 0xC6, (byte) 0xDC, (byte) 0xDC, (byte) 0xDC, (byte) 0xDC, (byte) 0xF2, (byte) 0xF2,
            (byte) 0xF2, (byte) 0xF2, 0x01, 0x01, 0x00, 0x00
    };

    // Obj06_CosineTable (slooptbl) - Y-offset curve for spiral path, NOT a trig table
    private static final byte[] SPIRAL_Y_OFFSETS = {
            32, 32, 32, 32, 32, 32, 32, 32,
            32, 32, 32, 32, 32, 32, 32, 32,
            32, 32, 32, 32, 32, 32, 32, 32,
            32, 32, 32, 32, 32, 32, 31, 31,
            31, 31, 31, 31, 31, 31, 31, 31,
            31, 31, 31, 31, 31, 30, 30, 30,
            30, 30, 30, 30, 30, 30, 29, 29,
            29, 29, 29, 28, 28, 28, 28, 27,
            27, 27, 27, 26, 26, 26, 25, 25,
            25, 24, 24, 24, 23, 23, 22, 22,
            21, 21, 20, 20, 19, 18, 18, 17,
            16, 16, 15, 14, 14, 13, 12, 12,
            11, 10, 10, 9, 8, 8, 7, 6,
            6, 5, 4, 4, 3, 2, 2, 1,
            0, -1, -2, -2, -3, -4, -4, -5,
            -6, -7, -7, -8, -9, -9, -10, -10,
            -11, -11, -12, -12, -13, -14, -14, -15,
            -15, -16, -16, -17, -17, -18, -18, -19,
            -19, -19, -20, -21, -21, -22, -22, -23,
            -23, -24, -24, -25, -25, -26, -26, -27,
            -27, -28, -28, -28, -29, -29, -30, -30,
            -30, -31, -31, -31, -32, -32, -32, -33,
            -33, -33, -33, -34, -34, -34, -35, -35,
            -35, -35, -35, -35, -35, -35, -36, -36,
            -36, -36, -36, -36, -36, -36, -36, -37,
            -37, -37, -37, -37, -37, -37, -37, -37,
            -37, -37, -37, -37, -37, -37, -37, -37,
            -37, -37, -37, -37, -37, -37, -37, -37,
            -37, -37, -37, -37, -36, -36, -36, -36,
            -36, -36, -36, -35, -35, -35, -35, -35,
            -35, -35, -35, -34, -34, -34, -33, -33,
            -33, -33, -32, -32, -32, -31, -31, -31,
            -30, -30, -30, -29, -29, -28, -28, -28,
            -27, -27, -26, -26, -25, -25, -24, -24,
            -23, -23, -22, -22, -21, -21, -21, -19,
            -19, -18, -18, -17, -16, -16, -15, -14,
            -14, -13, -12, -11, -11, -10, -9, -8,
            -7, -7, -6, -5, -4, -3, -2, -1,
            0, 1, 2, 3, 4, 5, 6, 7,
            8, 8, 9, 10, 10, 11, 12, 13,
            13, 14, 14, 15, 15, 16, 16, 17,
            17, 18, 18, 19, 19, 20, 20, 21,
            21, 22, 22, 23, 23, 24, 24, 24,
            25, 25, 25, 25, 26, 26, 26, 26,
            27, 27, 27, 27, 28, 28, 28, 28,
            28, 28, 29, 29, 29, 29, 29, 29,
            29, 30, 30, 30, 30, 30, 30, 30,
            31, 31, 31, 31, 31, 31, 31, 31,
            31, 31, 32, 32, 32, 32, 32, 32,
            32, 32, 32, 32, 32, 32, 32, 32,
            32, 32, 32, 32, 32, 32, 32, 32,
            32, 32, 32, 32, 32, 32, 32, 32
    };

    // ROM: status(a0) stores separate standing bits for Sonic and Tails.
    private final Set<AbstractPlayableSprite> ridingPlayers =
            Collections.newSetFromMap(new IdentityHashMap<>(2));

    public SpiralObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (playerEntity instanceof AbstractPlayableSprite player) {
            processPlayer(frameCounter, player);
        }
        for (PlayableEntity sidekickEntity : services().sidekicks()) {
            if (sidekickEntity instanceof AbstractPlayableSprite sidekick) {
                processPlayer(frameCounter, sidekick);
            }
        }
    }

    private void processPlayer(int frameCounter, AbstractPlayableSprite player) {
        if (ridingPlayers.contains(player)) {
            updateRidingPlayer(frameCounter, player);
            return;
        }
        tryActivate(frameCounter, player);
    }

    private void tryActivate(int frameCounter, AbstractPlayableSprite player) {
        int dx = player.getCentreX() - spawn.x();

        // ROM: btst #status.player.in_air,status(a1) / bne.w return_215BE
        if (player.getAir()) {
            return;
        }
        // ROM: tst.b obj_control(a1) / bne.s return_215BE
        if (player.isObjectControlled()) {
            return;
        }

        int vx = player.getXSpeed();
        boolean onObject = player.isOnObject();
        // Debug range
        if (Math.abs(dx) < 250 && frameCounter % 30 == 0) {
            LOGGER.fine("Spiral candidate: dx=" + dx + " vx=" + vx + " air=" + player.getAir());
        }

        // Ranges vary by approach direction:
        // Moving Right (vx >= 0): range -0xD0..-0xC0 (or -0xC0..-0xB0 if on object)
        boolean inXRange = false;
        if (vx >= 0) {
            int min = onObject ? -0xC0 : -0xD0;
            int max = onObject ? -0xB0 : -0xC0;
            if (dx >= min && dx <= max) {
                inXRange = true;
            }
        } else {
            // Moving Left (vx < 0): range 0xC0..0xD0 (or 0xB0..0xC0 if on object)
            int min = onObject ? 0xB0 : 0xC0;
            int max = onObject ? 0xC0 : 0xD0;
            if (dx >= min && dx <= max) {
                inXRange = true;
            }
        }

        if (!inXRange) {
            return;
        }

        // Y check
        // Range: $10 <= (player Y - object Y) < $40
        int dy = player.getCentreY() - spawn.y();
        int diff = dy - 0x10;
        if (diff < 0 || diff >= 0x30) {
            return;
        }

        engagePlayer(frameCounter, player);
    }

    private void engagePlayer(int frameCounter, AbstractPlayableSprite player) {
        ObjectManager objectManager = services().objectManager();
        if (objectManager != null
                && objectManager.isRidingObject(player)
                && !objectManager.isRidingObject(player, this)) {
            objectManager.clearRidingObject(player);
        }
        clearPreviousSpiralOwnership(player, objectManager);

        ridingPlayers.add(player);
        if (objectManager != null) {
            objectManager.markObjectSupportThisFrame(player);
        }
        player.setOnObject(true);
        player.setAir(false);
        player.setLatchedSolidObject(Sonic2ObjectIds.SPIRAL, this);
        player.setAngle((byte) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed(player.getXSpeed());
        player.markSpiralActive(frameCounter);
        LOGGER.fine("Spiral Activated: Player engaged at dx=" + (player.getCentreX() - spawn.x()));
    }

    private void clearPreviousSpiralOwnership(AbstractPlayableSprite player, ObjectManager objectManager) {
        if (objectManager == null || !player.isOnObject()) {
            return;
        }
        // ROM: RideObject_SetRide clears the previous owner's standing bit before
        // assigning the new interact slot. Spirals track that bit in ridingPlayers.
        for (ObjectInstance instance : objectManager.getActiveObjects()) {
            if (instance == this || !(instance instanceof SpiralObjectInstance spiral)) {
                continue;
            }
            spiral.ridingPlayers.remove(player);
        }
    }

    private void updateRidingPlayer(int frameCounter, AbstractPlayableSprite player) {
        boolean latchedToThisSpiral = player.getLatchedSolidObjectId() == Sonic2ObjectIds.SPIRAL
                && player.getLatchedSolidObjectInstance() == this;
        if (latchedToThisSpiral) {
            // ROM Obj06 keeps the player in loc_215C0 while this object's standing
            // bit is set (docs/s2disasm/s2.asm:46688-46764), then clears on_object
            // only from the spiral fall-off path (lines 46767-46772).
            player.setOnObject(true);
        }
        if (latchedToThisSpiral && player.getAir()) {
            player.setAir(false);
        }

        // ROM: loc_215C0
        int inertia = Math.abs(player.getGSpeed());
        if (inertia < 0x600 || player.getAir()) {
            fallOff(player);
            return;
        }

        int offset = player.getCentreX() - spawn.x() + 0xD0;
        if (offset < 0 || offset >= 0x1A0) {
            fallOff(player);
            return;
        }

        // ROM: btst #status.player.on_object,status(a1) / beq.s return_215BE
        if (!player.isOnObject()) {
            return;
        }

        updateMovement(player, frameCounter, offset);
    }

    private void updateMovement(AbstractPlayableSprite player, int frameCounter, int tableIndex) {
        ObjectManager objectManager = services().objectManager();
        if (objectManager != null) {
            objectManager.markObjectSupportThisFrame(player);
        }
        int targetCenterY = spawn.y() + SPIRAL_Y_OFFSETS[tableIndex];
        targetCenterY -= player.getYRadius() - 0x13;
        player.setCentreYPreserveSubpixel((short) targetCenterY);
        player.setAngle((byte) 0);

        int angleIndex = (tableIndex >> 3) & 0x3F;
        if (angleIndex >= 0 && angleIndex < FLIP_ANGLE_TABLE.length) {
            player.setFlipAngle(FLIP_ANGLE_TABLE[angleIndex] & 0xFF);
        }
        player.markSpiralActive(frameCounter);
    }

    private void fallOff(AbstractPlayableSprite player) {
        ridingPlayers.remove(player);
        player.setOnObject(false);
        if (player.getLatchedSolidObjectId() == Sonic2ObjectIds.SPIRAL) {
            player.setLatchedSolidObjectId(0);
        }
        player.setFlipsRemaining(0);
        player.setFlipSpeed(4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Invisible object
    }
}
