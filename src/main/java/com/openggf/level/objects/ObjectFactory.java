package com.openggf.level.objects;

@FunctionalInterface
public interface ObjectFactory {
    ObjectInstance create(ObjectSpawn spawn, ObjectRegistry registry);
}
