package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.CustomPlayablePhysics;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;

import java.util.Locale;

public interface S3kSlotBonusPlayer extends CustomPlayablePhysics {
    short GROUND_ACCEL = 0x0C;
    short GROUND_DECEL = 0x0C;
    short GROUND_REVERSAL_DECEL = 0x40;
    short GROUND_MAX_SPEED = 0x800;
    short AIR_ACCEL = 0x18;
    short AIR_MAX_SPEED = 0x300;

    String getCode();

    void setAngle(byte angle);

    static AbstractPlayableSprite create(String mainCode, short x, short y, S3kSlotPlayerRuntime runtime) {
        return switch (normalize(mainCode)) {
            case "tails" -> new TailsSlotBonusPlayer(mainCode, x, y, runtime);
            case "knuckles" -> new KnucklesSlotBonusPlayer(mainCode, x, y, runtime);
            default -> new SonicSlotBonusPlayer(mainCode, x, y, runtime);
        };
    }

    static AbstractPlayableSprite create(String mainCode, short x, short y, S3kSlotStageController controller) {
        S3kSlotPlayerRuntime runtime = S3kSlotPlayerRuntime.fromRomData();
        if (controller != null) {
            runtime.syncFromController(controller);
        }
        return switch (normalize(mainCode)) {
            case "tails" -> new TailsSlotBonusPlayer(mainCode, x, y, runtime, controller);
            case "knuckles" -> new KnucklesSlotBonusPlayer(mainCode, x, y, runtime, controller);
            default -> new SonicSlotBonusPlayer(mainCode, x, y, runtime, controller);
        };
    }

    private static String normalize(String mainCode) {
        return mainCode == null ? "" : mainCode.trim().toLowerCase(Locale.ROOT);
    }

    final class SonicSlotBonusPlayer extends Sonic implements S3kSlotBonusPlayer {
        private final S3kSlotPlayerRuntime runtime;
        private final S3kSlotStageController controller;

        SonicSlotBonusPlayer(String code, short x, short y, S3kSlotPlayerRuntime runtime) {
            this(code, x, y, runtime, null);
        }

        SonicSlotBonusPlayer(String code, short x, short y, S3kSlotPlayerRuntime runtime,
                             S3kSlotStageController controller) {
            super(code, x, y);
            this.runtime = runtime;
            this.controller = controller;
            setHighPriority(true);
        }

        @Override
        public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                                      boolean jump, boolean test, boolean speedUp, boolean slowDown,
                                      LevelManager levelManager, int frameCounter) {
            if (controller != null) {
                runtime.syncFromController(controller);
            }
            runtime.tick(this, left, right, jump, frameCounter);
        }

        @Override
        public void setAngle(byte angle) {
            this.angle = angle;
        }
    }

    final class TailsSlotBonusPlayer extends Tails implements S3kSlotBonusPlayer {
        private final S3kSlotPlayerRuntime runtime;
        private final S3kSlotStageController controller;

        TailsSlotBonusPlayer(String code, short x, short y, S3kSlotPlayerRuntime runtime) {
            this(code, x, y, runtime, null);
        }

        TailsSlotBonusPlayer(String code, short x, short y, S3kSlotPlayerRuntime runtime,
                             S3kSlotStageController controller) {
            super(code, x, y);
            this.runtime = runtime;
            this.controller = controller;
            setHighPriority(true);
        }

        @Override
        public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                                      boolean jump, boolean test, boolean speedUp, boolean slowDown,
                                      LevelManager levelManager, int frameCounter) {
            if (controller != null) {
                runtime.syncFromController(controller);
            }
            runtime.tick(this, left, right, jump, frameCounter);
        }

        @Override
        public void setAngle(byte angle) {
            this.angle = angle;
        }
    }

    final class KnucklesSlotBonusPlayer extends Knuckles implements S3kSlotBonusPlayer {
        private final S3kSlotPlayerRuntime runtime;
        private final S3kSlotStageController controller;

        KnucklesSlotBonusPlayer(String code, short x, short y, S3kSlotPlayerRuntime runtime) {
            this(code, x, y, runtime, null);
        }

        KnucklesSlotBonusPlayer(String code, short x, short y, S3kSlotPlayerRuntime runtime,
                                S3kSlotStageController controller) {
            super(code, x, y);
            this.runtime = runtime;
            this.controller = controller;
            setHighPriority(true);
        }

        @Override
        public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                                      boolean jump, boolean test, boolean speedUp, boolean slowDown,
                                      LevelManager levelManager, int frameCounter) {
            if (controller != null) {
                runtime.syncFromController(controller);
            }
            runtime.tick(this, left, right, jump, frameCounter);
        }

        @Override
        public void setAngle(byte angle) {
            this.angle = angle;
        }
    }
}
