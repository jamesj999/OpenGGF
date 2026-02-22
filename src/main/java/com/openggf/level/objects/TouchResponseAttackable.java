package com.openggf.level.objects;

import com.openggf.sprites.playable.AbstractPlayableSprite;

public interface TouchResponseAttackable {
    void onPlayerAttack(AbstractPlayableSprite player, TouchResponseResult result);
}
