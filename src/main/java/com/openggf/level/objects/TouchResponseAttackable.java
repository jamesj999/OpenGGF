package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;

public interface TouchResponseAttackable {
    void onPlayerAttack(PlayableEntity player, TouchResponseResult result);
}
