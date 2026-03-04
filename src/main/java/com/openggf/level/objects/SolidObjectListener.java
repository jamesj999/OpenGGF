package com.openggf.level.objects;

import com.openggf.sprites.playable.AbstractPlayableSprite;

public interface SolidObjectListener {
    void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter);
}
