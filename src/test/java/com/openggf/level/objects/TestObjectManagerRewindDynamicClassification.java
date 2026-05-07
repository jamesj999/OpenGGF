package com.openggf.level.objects;

import com.openggf.game.RuntimeManager;
import com.openggf.game.rewind.GenericRewindEligibility;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.session.EngineContext;
import com.openggf.graphics.GLCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestObjectManagerRewindDynamicClassification {

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDown() {
        ObjectManager.clearRewindDynamicObjectCodecsForTest();
        GenericRewindEligibility.clearForTest();
        RuntimeManager.destroyCurrent();
    }

    @Test
    void registeredDynamicRewindCodecCapturesAndRecreatesDynamicObject() {
        GenericRewindEligibility.registerForTestOrMigration(TestDynamicObject.class);
        ObjectManager.registerRewindDynamicObjectCodecForTest(new ObjectManager.RewindDynamicObjectCodec() {
            @Override
            public boolean supports(ObjectInstance instance) {
                return instance instanceof TestDynamicObject;
            }

            @Override
            public String className() {
                return TestDynamicObject.class.getName();
            }

            @Override
            public ObjectInstance recreate(ObjectManager.DynamicObjectRecreateContext context,
                    ObjectManagerSnapshot.DynamicObjectEntry entry) {
                return new TestDynamicObject(entry.spawn());
            }
        });

        ObjectSpawn spawn = new ObjectSpawn(0x100, 0x180, 0x01, 0, 0, false, 0);
        ObjectManager manager = new ObjectManager(List.of(), null, 0, null, null);
        TestDynamicObject object = new TestDynamicObject(spawn);
        object.phase = 7;
        object.moveTo(0x120, 0x1A0);
        manager.addDynamicObject(object);

        var snapshottable = manager.rewindSnapshottable();
        ObjectManagerSnapshot snapshot = snapshottable.capture();

        assertEquals(1, snapshot.dynamicObjects().size());

        object.phase = 2;
        object.moveTo(0x300, 0x400);
        snapshottable.restore(snapshot);

        TestDynamicObject restored = manager.getActiveObjects().stream()
                .filter(TestDynamicObject.class::isInstance)
                .map(TestDynamicObject.class::cast)
                .findFirst()
                .orElse(null);

        assertNotNull(restored);
        assertEquals(7, restored.phase);
        assertEquals(0x120, restored.getX());
        assertEquals(0x1A0, restored.getY());
    }

    private static final class TestDynamicObject extends AbstractObjectInstance {
        private int phase;

        TestDynamicObject(ObjectSpawn spawn) {
            super(spawn, "TestDynamicObject");
        }

        void moveTo(int x, int y) {
            updateDynamicSpawn(x, y);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // no-op
        }
    }
}
