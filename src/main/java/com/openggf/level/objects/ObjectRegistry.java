package com.openggf.level.objects;

import java.util.List;

public interface ObjectRegistry {
    ObjectInstance create(ObjectSpawn spawn);

    void reportCoverage(List<ObjectSpawn> spawns);

    String getPrimaryName(int objectId);

    default ObjectSlotLayout objectSlotLayout() {
        return ObjectSlotLayout.SONIC_1;
    }

    default List<String> getAliases(int objectId) {
        return List.of();
    }
}
