package com.openggf.game.sonic1.objects;

import com.openggf.game.session.EngineContext;
import com.openggf.game.RuntimeManager;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSonic1JunctionObjectInstance {

    @Test
    void constructorMatchesJunMainSeedState() throws Exception {
        RuntimeManager.destroyCurrent();
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());

        Sonic1JunctionObjectInstance junction = new Sonic1JunctionObjectInstance(
                new ObjectSpawn(0x1490, 0x0170, 0x66, 0x00, 0, false, 0));
        try {
            assertEquals(1, getPrivateInt(junction, "frameDirection"));
            assertEquals(0, getPrivateInt(junction, "mappingFrame"));
            assertEquals(0, getPrivateInt(junction, "frameTimer"));
        } finally {
            RuntimeManager.destroyCurrent();
        }
    }

    private static int getPrivateInt(Object instance, String fieldName) throws Exception {
        Field field = Sonic1JunctionObjectInstance.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(instance);
    }
}
