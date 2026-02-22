package com.openggf.level.objects;

import com.openggf.sprites.playable.AbstractPlayableSprite;

public interface TouchResponseListener {
    void onTouchResponse(AbstractPlayableSprite player, TouchResponseResult result, int frameCounter);
}
