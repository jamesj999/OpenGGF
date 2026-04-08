package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.CustomPlayablePhysics;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;

import java.util.Locale;

public interface S3kSlotBonusPlayer extends CustomPlayablePhysics {

    String getCode();

    void setAngle(byte angle);

    static AbstractPlayableSprite create(String mainCode, short x, short y, S3kSlotStageController controller) {
        return switch (normalize(mainCode)) {
            case "tails" -> new TailsSlotBonusPlayer(mainCode, x, y, controller);
            case "knuckles" -> new KnucklesSlotBonusPlayer(mainCode, x, y, controller);
            default -> new SonicSlotBonusPlayer(mainCode, x, y, controller);
        };
    }

    static void tickController(S3kSlotBonusPlayer player, S3kSlotStageController controller,
                               boolean left, boolean right, boolean jump, int frameCounter) {
        if (controller != null) {
            controller.tickPlayer(player, left, right, jump, frameCounter);
        }
    }

    private static String normalize(String mainCode) {
        return mainCode == null ? "" : mainCode.trim().toLowerCase(Locale.ROOT);
    }

    final class SonicSlotBonusPlayer extends Sonic implements S3kSlotBonusPlayer {
        private final S3kSlotStageController controller;

        SonicSlotBonusPlayer(String code, short x, short y, S3kSlotStageController controller) {
            super(code, x, y);
            this.controller = controller;
            setHighPriority(true);
        }

        @Override
        public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                                      boolean jump, boolean test, boolean speedUp, boolean slowDown,
                                      LevelManager levelManager, int frameCounter) {
            S3kSlotBonusPlayer.tickController(this, controller, left, right, jump, frameCounter);
        }
    }

    final class TailsSlotBonusPlayer extends Tails implements S3kSlotBonusPlayer {
        private final S3kSlotStageController controller;

        TailsSlotBonusPlayer(String code, short x, short y, S3kSlotStageController controller) {
            super(code, x, y);
            this.controller = controller;
            setHighPriority(true);
        }

        @Override
        public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                                      boolean jump, boolean test, boolean speedUp, boolean slowDown,
                                      LevelManager levelManager, int frameCounter) {
            S3kSlotBonusPlayer.tickController(this, controller, left, right, jump, frameCounter);
        }
    }

    final class KnucklesSlotBonusPlayer extends Knuckles implements S3kSlotBonusPlayer {
        private final S3kSlotStageController controller;

        KnucklesSlotBonusPlayer(String code, short x, short y, S3kSlotStageController controller) {
            super(code, x, y);
            this.controller = controller;
            setHighPriority(true);
        }

        @Override
        public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                                      boolean jump, boolean test, boolean speedUp, boolean slowDown,
                                      LevelManager levelManager, int frameCounter) {
            S3kSlotBonusPlayer.tickController(this, controller, left, right, jump, frameCounter);
        }
    }
}
