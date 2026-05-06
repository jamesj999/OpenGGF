package com.openggf.game.solid;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.SolidExecutionSnapshot;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.managers.SpriteManager;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class DefaultSolidExecutionRegistry
        implements SolidExecutionRegistry, RewindSnapshottable<SolidExecutionSnapshot> {
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
        ObjectInstance executingObject = currentContext.object();
        if (executingObject != null && (batch == null || batch.object() != executingObject)) {
            throw new IllegalStateException(
                    "Published checkpoint batch must match the currently executing object.");
        }
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

    @Override
    public String key() {
        return "solid-execution";
    }

    @Override
    public SolidExecutionSnapshot capture() {
        LevelManager levelManager = GameServices.levelOrNull();
        ObjectManager objectManager = levelManager != null ? levelManager.getObjectManager() : null;
        if (objectManager == null) {
            return new SolidExecutionSnapshot(List.of());
        }
        List<ObjectSpawn> spawns = objectManager.getAllSpawns();
        List<SolidExecutionSnapshot.PreviousStandingEntry> entries = new ArrayList<>();
        for (Map.Entry<ObjectInstance, IdentityHashMap<PlayableEntity, PlayerStandingState>> objectEntry
                : previous.entrySet()) {
            ObjectSpawn spawn = objectEntry.getKey().getSpawn();
            int spawnIndex = indexOfIdentity(spawns, spawn);
            if (spawnIndex < 0) {
                continue;
            }
            for (Map.Entry<PlayableEntity, PlayerStandingState> playerEntry
                    : objectEntry.getValue().entrySet()) {
                String playerCode = playerCode(playerEntry.getKey());
                if (playerCode == null) {
                    continue;
                }
                PlayerStandingState state = playerEntry.getValue();
                entries.add(new SolidExecutionSnapshot.PreviousStandingEntry(
                        spawnIndex,
                        playerCode,
                        state.kind(),
                        state.standing(),
                        state.pushing()));
            }
        }
        entries.sort((a, b) -> {
            int bySpawn = Integer.compare(a.spawnIndex(), b.spawnIndex());
            if (bySpawn != 0) {
                return bySpawn;
            }
            int byPlayer = a.playerCode().compareTo(b.playerCode());
            if (byPlayer != 0) {
                return byPlayer;
            }
            return a.kind().compareTo(b.kind());
        });
        return new SolidExecutionSnapshot(entries);
    }

    @Override
    public void restore(SolidExecutionSnapshot s) {
        previous.clear();
        current.clear();
        currentContext = ObjectSolidExecutionContext.inert();
        LevelManager levelManager = GameServices.levelOrNull();
        ObjectManager objectManager = levelManager != null ? levelManager.getObjectManager() : null;
        SpriteManager spriteManager = GameServices.spritesOrNull();
        if (objectManager == null || spriteManager == null) {
            return;
        }
        List<ObjectSpawn> spawns = objectManager.getAllSpawns();
        for (SolidExecutionSnapshot.PreviousStandingEntry entry : s.previousStanding()) {
            if (entry.spawnIndex() < 0 || entry.spawnIndex() >= spawns.size()) {
                continue;
            }
            ObjectInstance object = objectManager.getActiveObjectForRewind(spawns.get(entry.spawnIndex()));
            Sprite sprite = spriteManager.getSprite(entry.playerCode());
            if (object == null || !(sprite instanceof PlayableEntity player)) {
                continue;
            }
            previous.computeIfAbsent(object, ignored -> new IdentityHashMap<>())
                    .put(player, new PlayerStandingState(
                            entry.kind(), entry.standing(), entry.pushing()));
        }
    }

    private static int indexOfIdentity(List<ObjectSpawn> spawns, ObjectSpawn target) {
        if (target == null) {
            return -1;
        }
        for (int i = 0; i < spawns.size(); i++) {
            if (spawns.get(i) == target) {
                return i;
            }
        }
        return -1;
    }

    private static String playerCode(PlayableEntity player) {
        if (player instanceof Sprite sprite) {
            return sprite.getCode();
        }
        return null;
    }
}
