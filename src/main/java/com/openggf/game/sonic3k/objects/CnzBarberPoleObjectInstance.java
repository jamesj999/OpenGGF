package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Object 0x4D - CNZ barber pole / curved ride sprite.
 *
 * <p>Ports the S&K-side {@code Obj_CNZBarberPoleSprite} interaction paths at
 * {@code loc_33376} and {@code loc_335A8}. The object is gameplay logic: it
 * latches each player via {@code Status_OnObj}, stores their path offset in the
 * object's per-player work area, then positions them along the pole using
 * {@code GetSineCosine}.
 */
public final class CnzBarberPoleObjectInstance extends AbstractObjectInstance {

    private static final int TRACK_LIMIT = 0xA0;
    private static final int TRACK_FRACTION_MASK = 0xFFFF;
    private static final int MIN_GROUND_SPEED_TO_STAY_ATTACHED = 0x118;

    private final boolean mirrored;
    private final Map<AbstractPlayableSprite, RiderState> riders = new IdentityHashMap<>();

    public CnzBarberPoleObjectInstance(ObjectSpawn spawn) {
        super(spawn, "CNZBarberPoleSprite");
        this.mirrored = spawn.subtype() != 0;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (playerEntity instanceof AbstractPlayableSprite player) {
            updatePlayer(player);
        }

        try {
            for (PlayableEntity sidekick : services().sidekicks()) {
                if (sidekick instanceof AbstractPlayableSprite sprite) {
                    updatePlayer(sprite);
                }
            }
        } catch (Exception ignored) {
            // Some tests provide only the main player.
        }
    }

    private void updatePlayer(AbstractPlayableSprite player) {
        RiderState state = riders.computeIfAbsent(player, ignored -> new RiderState());
        if (state.latched) {
            continueRide(player, state);
        } else if (mirrored) {
            tryLatchMirrored(player, state);
        } else {
            tryLatchNormal(player, state);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // The visible CNZ pole is level art; this object owns only the ride logic.
    }

    private void tryLatchNormal(AbstractPlayableSprite player, RiderState state) {
        if (player.isOnObject() && player.getLatchedSolidObjectId() != Sonic3kObjectIds.CNZ_BARBER_POLE) {
            return;
        }

        int d0 = -player.getXRadius() + player.getCentreX() - spawn.x() + (player.isOnObject() ? 0x30 : 0x40);
        if (d0 < 0 || d0 >= (player.isOnObject() ? 0x80 : 0xA0)) {
            return;
        }
        d0 -= player.isOnObject() ? 0x51 : 0x61;

        int d1 = player.getYRadius() + player.getCentreY() - spawn.y() - d0;
        if (d1 < 0 || d1 >= 0x10 || !canLatch(player)) {
            return;
        }

        int track = player.getCentreX() - spawn.x() + 0x40;
        boolean inner = (track - (player.isOnObject() ? 0x20 : 0x18)) < (player.isOnObject() ? 0x60 : 0x70);
        latch(player, state, track, inner, 0x20);
    }

    private void tryLatchMirrored(AbstractPlayableSprite player, RiderState state) {
        if (player.isOnObject() && player.getLatchedSolidObjectId() != Sonic3kObjectIds.CNZ_BARBER_POLE) {
            return;
        }

        int d0 = player.getXRadius() + player.getCentreX() - spawn.x() + (player.isOnObject() ? 0x50 : 0x60);
        if (d0 < 0 || d0 >= (player.isOnObject() ? 0x80 : 0xA0)) {
            return;
        }
        d0 -= player.isOnObject() ? 0x2E : 0x3E;

        int d1 = player.getYRadius() + player.getCentreY() - spawn.y() + d0;
        if (d1 < 0 || d1 >= 0x10 || !canLatch(player)) {
            return;
        }

        int track = player.getCentreX() - spawn.x() + 0x60;
        boolean inner = (track - (player.isOnObject() ? 0x20 : 0x18)) < (player.isOnObject() ? 0x60 : 0x70);
        latch(player, state, track, inner, 0xE0);
    }

    private boolean canLatch(AbstractPlayableSprite player) {
        if (player.isObjectControlled() || player.isHurt() || player.getDead()) {
            return false;
        }
        return !player.getAir() || player.getYSpeed() >= 0;
    }

    private void latch(AbstractPlayableSprite player, RiderState state, int track, boolean inner, int angle) {
        state.latched = true;
        state.trackFixed = ((long) (track & 0xFFFF) << 16) | (player.getXSubpixelRaw() & TRACK_FRACTION_MASK);
        state.innerTrack = inner;

        if (player.getAir()) {
            player.setYSpeed((short) 0);
            player.setGSpeed(player.getXSpeed());
        }
        player.setOnObject(true);
        player.setAir(false);
        player.setLatchedSolidObjectId(Sonic3kObjectIds.CNZ_BARBER_POLE);
        player.setAngle((byte) angle);
    }

    private void continueRide(AbstractPlayableSprite player, RiderState state) {
        if (player.getLatchedSolidObjectId() != Sonic3kObjectIds.CNZ_BARBER_POLE) {
            state.latched = false;
            return;
        }

        if (player.getAir()) {
            if (player.isJumping()) {
                release(player, state);
                return;
            }
            /*
             * ROM sub_337D8 stores this object's address in interact(a1) and
             * sets Status_OnObj/clears Status_InAir as the authoritative latch.
             * The engine's generic terrain pass runs before this object update
             * and can mark the player airborne for a frame because this object
             * is not represented in SolidContacts. If the latch id is still
             * ours and no jump was taken, restore the object-owned status before
             * executing loc_334B6/loc_336E4.
             */
            player.setAir(false);
            player.setOnObject(true);
        }

        if (!state.innerTrack && Math.abs(player.getGSpeed()) < MIN_GROUND_SPEED_TO_STAY_ATTACHED) {
            player.setAir(true);
            player.setAngle((byte) rideAngle());
            release(player, state);
            return;
        }

        state.trackFixed += (long) player.getXSpeed() * 0xC0L;

        int trackPosition = (int) (state.trackFixed >> 16);
        int d0 = mirrored
                ? player.getXRadius() + trackPosition
                : -player.getXRadius() + trackPosition;
        if (d0 < 0 || d0 >= TRACK_LIMIT) {
            player.setAngle((byte) rideAngle());
            release(player, state);
            return;
        }

        if (mirrored) {
            positionMirrored(player, state, d0);
        } else {
            positionNormal(player, state, d0);
        }
    }

    private void positionNormal(AbstractPlayableSprite player, RiderState state, int trackRel) {
        int curve = clampCurveByte(trackRel - 0x10, state);
        int visibleCurve = state.innerTrack ? 0 : curve;
        int angle = (visibleCurve + visibleCurve) & 0xFF;
        player.setFlipAngle(angle);

        int cos = TrigLookupTable.cosHex(angle);
        int cosShift = cos >> 4;
        int x = spawn.x() + trackRel + cosShift - 0x50 + ((player.getXRadius() * cos) >> 8);
        int y = spawn.y() - cosShift + (trackRel - 0x51) - ((player.getYRadius() * cos) >> 8);
        player.setCentreXPreserveSubpixel((short) x);
        player.setCentreYPreserveSubpixel((short) y);
        player.setAngle((byte) 0x20);
        player.setOnObject(true);
    }

    private void positionMirrored(AbstractPlayableSprite player, RiderState state, int trackRel) {
        int curve = clampCurveByte(trackRel - 0x10, state);
        int visibleCurve = state.innerTrack ? 0 : curve;
        int angle = (-(visibleCurve) + -(visibleCurve)) & 0xFF;
        player.setFlipAngle(angle);

        int cos = TrigLookupTable.cosHex(angle);
        int cosShift = cos >> 4;
        int x = spawn.x() + trackRel - cosShift - 0x50 - ((player.getXRadius() * cos) >> 8);
        int y = spawn.y() - cosShift - (trackRel - 0x4E) - ((player.getYRadius() * cos) >> 8);
        player.setCentreXPreserveSubpixel((short) x);
        player.setCentreYPreserveSubpixel((short) y);
        player.setAngle((byte) 0xE0);
        player.setOnObject(true);
    }

    private int rideAngle() {
        return mirrored ? 0xE0 : 0x20;
    }

    private int clampCurveByte(int curve, RiderState state) {
        if (curve < 0) {
            state.innerTrack = false;
            return 0;
        }
        if (curve >= 0x80) {
            state.innerTrack = false;
            return 0x80;
        }
        return curve;
    }

    private void release(AbstractPlayableSprite player, RiderState state) {
        state.latched = false;
        player.setOnObject(false);
        player.setFlipsRemaining(0);
        player.setFlipSpeed(4);
    }

    private static final class RiderState {
        private boolean latched;
        private long trackFixed;
        private boolean innerTrack;
    }
}
