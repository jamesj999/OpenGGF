package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;

public interface SolidObjectListener {
    void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter);
}
