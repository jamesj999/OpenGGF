package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0xE8 - Pachinko energy trap.
 *
 * <p>ROM reference: {@code Obj_PachinkoEnergyTrap}. Spawns the paired column, emits
 * beam particles, captures the player on the beam line, and ends the bonus stage.
 */
public class PachinkoEnergyTrapObjectInstance extends AbstractObjectInstance {

    private static final int COLUMN_SPACING = 0x190;
    private static final int BEAM_SPAWN_X_OFFSET = 0x80;
    private static final int BEAM_DELETE_X_OFFSET = 0x190;
    private static final int BEAM_SPAWN_PERIOD = 8;
    private static final int INITIAL_EXIT_DELAY = 60;
    private static final int INITIAL_RISE_DELAY = 4 * 60;
    private static final int TRANSPORTER_PERIOD = 0x40;
    private static final int CAPTURE_TOP = -0x0C;
    private static final int CAPTURE_BOTTOM_EXCLUSIVE = 0x0C;
    private static final int PLAYER_ESCAPED_Y = -0x20;

    private boolean initialized;
    private int beamAngle;
    private int exitDelayFrames = INITIAL_EXIT_DELAY;
    private int riseDelayFrames = INITIAL_RISE_DELAY;
    private boolean exitArmed;
    private boolean exitRequested;
    private AbstractPlayableSprite capturedPlayer;
    private int currentX;
    private int currentY;

    public PachinkoEnergyTrapObjectInstance(ObjectSpawn spawn) {
        super(spawn, "PachinkoEnergyTrap");
        currentX = spawn.x();
        currentY = spawn.y();
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (!initialized) {
            initialized = true;
            spawnChild(() -> new EnergyTrapColumnChild(createColumnSpawn(), this));
        }

        if ((frameCounter & (BEAM_SPAWN_PERIOD - 1)) == 0) {
            spawnChild(() -> new EnergyTrapBeamChild(createBeamSpawn(), this, beamAngle));
        }
        beamAngle = (beamAngle + 8) & 0xFF;

        if (!exitArmed) {
            if (riseDelayFrames > 0) {
                riseDelayFrames--;
            } else {
                currentY -= 1;
            }
        }

        updateDynamicSpawn(currentX, currentY);

        maybePlayTransporterSfx(frameCounter, playerEntity);
        updateCapture(playerEntity);
    }

    private void updateCapture(PlayableEntity playerEntity) {
        if (!(playerEntity instanceof AbstractPlayableSprite player) || player.isDebugMode()) {
            return;
        }

        int relativeY = player.getY() - currentY;
        boolean inCaptureBand = relativeY >= CAPTURE_TOP && relativeY < CAPTURE_BOTTOM_EXCLUSIVE;
        if (!inCaptureBand && capturedPlayer != player) {
            return;
        }

        capturedPlayer = player;
        player.setY((short) currentY);
        player.setControlLocked(true);
        player.setAir(true);

        if (!exitArmed) {
            playSfx(Sonic3kSfx.BOUNCY);
            exitArmed = true;
        }

        if (player.getY() < PLAYER_ESCAPED_Y) {
            exitDelayFrames = 0;
        }

        if (!exitRequested && --exitDelayFrames < 0) {
            exitRequested = true;
            services().requestBonusStageExit();
        }
    }

    private void maybePlayTransporterSfx(int frameCounter, PlayableEntity playerEntity) {
        if (!(playerEntity instanceof AbstractPlayableSprite player)) {
            return;
        }
        int relativeY = player.getY() - currentY;
        if (relativeY < -0x180 || relativeY >= 0) {
            return;
        }
        if ((frameCounter & (TRANSPORTER_PERIOD - 1)) == 0) {
            playSfx(Sonic3kSfx.TRANSPORTER);
        }
    }

    private void playSfx(Sonic3kSfx sfx) {
        try {
            services().playSfx(sfx.id);
        } catch (Exception e) {
            // Keep gameplay logic independent from audio state.
        }
    }

    private ObjectSpawn createColumnSpawn() {
        return new ObjectSpawn(
                spawn.x() + COLUMN_SPACING,
                spawn.y(),
                spawn.objectId(),
                spawn.subtype(),
                spawn.renderFlags() | 0x1,
                false,
                0,
                spawn.layoutIndex());
    }

    private ObjectSpawn createBeamSpawn() {
        return new ObjectSpawn(
                currentX + BEAM_SPAWN_X_OFFSET,
                currentY,
                spawn.objectId() + 1,
                spawn.subtype(),
                spawn.renderFlags(),
                false,
                0,
                spawn.layoutIndex());
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(0);
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.PACHINKO_ENERGY_TRAP);
        if (renderer == null) {
            return;
        }
        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
        renderer.drawFrameIndex(0, currentX, currentY, hFlip, vFlip);
    }

    private static final class EnergyTrapColumnChild extends AbstractObjectInstance {

        private final PachinkoEnergyTrapObjectInstance parent;
        private int currentX;
        private int currentY;

        private EnergyTrapColumnChild(ObjectSpawn spawn, PachinkoEnergyTrapObjectInstance parent) {
            super(spawn, "PachinkoEnergyTrapColumn");
            this.parent = parent;
            currentX = spawn.x();
            currentY = spawn.y();
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            currentY = parent.getY();
            updateDynamicSpawn(currentX, currentY);
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(0);
        }

        @Override
        public boolean isHighPriority() {
            return true;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.PACHINKO_ENERGY_TRAP);
            if (renderer == null) {
                return;
            }
            boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
            boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
            renderer.drawFrameIndex(0, currentX, currentY, hFlip, vFlip);
        }
    }

    private static final class EnergyTrapBeamChild extends AbstractObjectInstance {

        private static final int[] ANIMATION = {0, 1, 2, 3, 4, 3, 2, 1};

        private final PachinkoEnergyTrapObjectInstance parent;
        private int beamAngle;
        private int currentX;
        private int currentY;

        private EnergyTrapBeamChild(ObjectSpawn spawn, PachinkoEnergyTrapObjectInstance parent, int beamAngle) {
            super(spawn, "PachinkoEnergyBeam");
            this.parent = parent;
            this.beamAngle = beamAngle & 0xFF;
            currentX = spawn.x();
            currentY = spawn.y();
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            currentY = parent.getY() + (TrigLookupTable.sinHex(beamAngle) >> 4);
            currentX += 2;
            beamAngle = (beamAngle + 0x84) & 0xFF;
            updateDynamicSpawn(currentX, currentY);

            if (currentX > parent.spawn.x() + BEAM_DELETE_X_OFFSET) {
                setDestroyed(true);
            }
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(1);
        }

        @Override
        public boolean isHighPriority() {
            return true;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.PACHINKO_INVISIBLE_UNKNOWN);
            if (renderer == null) {
                return;
            }
            int frame = ANIMATION[((beamAngle + 0x10) >> 5) & 0x7];
            renderer.drawFrameIndex(frame, currentX, currentY, false, false);
        }
    }
}
