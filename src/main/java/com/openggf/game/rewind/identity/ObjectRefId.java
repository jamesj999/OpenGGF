package com.openggf.game.rewind.identity;

import java.util.Objects;

public record ObjectRefId(int slotIndex, int generation, int spawnId, int dynamicId, ObjectRefKind kind) {
    public ObjectRefId {
        Objects.requireNonNull(kind, "kind");
    }

    public static ObjectRefId layout(int slotIndex, int generation, int spawnId) {
        return new ObjectRefId(slotIndex, generation, spawnId, -1, ObjectRefKind.LAYOUT);
    }

    public static ObjectRefId dynamic(int slotIndex, int generation, int dynamicId) {
        return new ObjectRefId(slotIndex, generation, -1, dynamicId, ObjectRefKind.DYNAMIC);
    }

    public static ObjectRefId child(int slotIndex, int generation, int spawnId, int dynamicId) {
        return new ObjectRefId(slotIndex, generation, spawnId, dynamicId, ObjectRefKind.CHILD);
    }
}
