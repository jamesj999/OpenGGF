package com.openggf.game.solid;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectInstance;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public record SolidCheckpointBatch(
        ObjectInstance object,
        Map<PlayableEntity, PlayerSolidContactResult> perPlayer) {

    public SolidCheckpointBatch {
        IdentityHashMap<PlayableEntity, PlayerSolidContactResult> copy =
                new IdentityHashMap<>(perPlayer);
        perPlayer = Collections.unmodifiableMap(copy);
    }
}
