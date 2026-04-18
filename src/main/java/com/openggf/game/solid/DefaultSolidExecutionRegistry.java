package com.openggf.game.solid;

import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectInstance;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class DefaultSolidExecutionRegistry implements SolidExecutionRegistry {
    private final IdentityHashMap<ObjectInstance, IdentityHashMap<PlayableEntity, PlayerStandingState>> previous =
            new IdentityHashMap<>();
    private final IdentityHashMap<ObjectInstance, SolidCheckpointBatch> current =
            new IdentityHashMap<>();
    private ObjectSolidExecutionContext currentContext = ObjectSolidExecutionContext.inert();

    @Override
    public void beginFrame(int frameCounter, List<? extends PlayableEntity> players) {
        current.clear();
        currentContext = ObjectSolidExecutionContext.inert();
    }

    @Override
    public void beginObject(ObjectInstance object, ObjectSolidExecutionContext.Resolver resolver) {
        currentContext = new ObjectSolidExecutionContext(this, object, resolver);
    }

    @Override
    public ObjectSolidExecutionContext currentObject() {
        return currentContext;
    }

    @Override
    public PlayerStandingState previousStanding(ObjectInstance object, PlayableEntity player) {
        IdentityHashMap<PlayableEntity, PlayerStandingState> perPlayer = previous.get(object);
        if (perPlayer == null) {
            return PlayerStandingState.NONE;
        }
        return perPlayer.getOrDefault(player, PlayerStandingState.NONE);
    }

    @Override
    public void publishCheckpoint(SolidCheckpointBatch batch) {
        current.put(batch.object(), batch);
    }

    @Override
    public void endObject(ObjectInstance object) {
        currentContext = ObjectSolidExecutionContext.inert();
    }

    @Override
    public void finishFrame() {
        previous.clear();
        for (Map.Entry<ObjectInstance, SolidCheckpointBatch> entry : current.entrySet()) {
            IdentityHashMap<PlayableEntity, PlayerStandingState> perPlayer = new IdentityHashMap<>();
            for (Map.Entry<PlayableEntity, PlayerSolidContactResult> playerEntry
                    : entry.getValue().perPlayer().entrySet()) {
                PlayerSolidContactResult result = playerEntry.getValue();
                perPlayer.put(playerEntry.getKey(),
                        new PlayerStandingState(result.kind(), result.standingNow(), result.pushingNow()));
            }
            previous.put(entry.getKey(), perPlayer);
        }
    }

    @Override
    public void clearTransientState() {
        current.clear();
        currentContext = ObjectSolidExecutionContext.inert();
    }
}
