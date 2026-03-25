package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;

public interface TouchResponseListener {
    void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter);
}
