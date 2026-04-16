package com.openggf.tests;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.AbstractObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CNZ teleporter-route infrastructure canary for Task 6.
 *
 * <p>This intentionally stops short of Task 8 gameplay assertions. It only
 * verifies that the CNZ route has the ROM-backed IDs, explicit registry slots,
 * and object-art registrations needed by the future {@code Obj_CNZTeleporter},
 * {@code Obj_CNZMiniboss}, and {@code Obj_CNZEndBoss} behavior slices.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnzTeleporterRouteHeadless {

    /**
     * ROM anchors:
     * {@code Obj_CNZWaterLevelCorkFloor} / {@code Obj_CNZWaterLevelButton}
     * use IDs $88/$89 in the S3KL object table, while
     * {@code Obj_CNZMiniboss} / {@code Obj_CNZEndBoss} use IDs $A6/$A7.
     *
     * <p>The teleporter route also depends on renderer registrations for
     * {@code ArtKosM_CNZTeleport}, {@code PLC_5C_5D} / {@code Map_CNZMiniboss},
     * and {@code PLC_6E} / {@code Map_CNZEndBoss}. Keeping this test narrow
     * ensures Task 6 only goes green once the infrastructure exists, leaving
     * route logic for Tasks 7 and 8.
     */
    @Test
    void cnzInfrastructureRegistersIdsFactoriesAndRendererKeys() throws Exception {
        HeadlessTestFixture.builder()
                .withZoneAndAct(0x03, 1)
                .build();

        assertEquals(0x88, Sonic3kObjectIds.CNZ_WATER_LEVEL_CORK_FLOOR);
        assertEquals(0x89, Sonic3kObjectIds.CNZ_WATER_LEVEL_BUTTON);
        assertEquals(0xA6, Sonic3kObjectIds.CNZ_MINIBOSS);
        assertEquals(0xA7, Sonic3kObjectIds.CNZ_END_BOSS);

        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();
        ensureLoaded(registry);
        Map<Integer, ?> factories = readFactories(registry);
        assertTrue(factories.containsKey(Sonic3kObjectIds.CNZ_WATER_LEVEL_CORK_FLOOR),
                "Task 6 should claim the CNZ cork-floor water helper ID explicitly");
        assertTrue(factories.containsKey(Sonic3kObjectIds.CNZ_WATER_LEVEL_BUTTON),
                "Task 6 should claim the CNZ water-button helper ID explicitly");
        assertTrue(factories.containsKey(Sonic3kObjectIds.CNZ_MINIBOSS),
                "Task 6 should claim the CNZ miniboss ID explicitly");
        assertTrue(factories.containsKey(Sonic3kObjectIds.CNZ_END_BOSS),
                "Task 6 should claim the CNZ end-boss ID explicitly");
        assertInstanceOf(PlaceholderObjectInstance.class,
                registry.create(new ObjectSpawn(0x4A40, 0x0A38, Sonic3kObjectIds.CNZ_WATER_LEVEL_CORK_FLOOR, 0, 0, false, 0)),
                "Task 6 should reserve the CNZ cork-floor helper slot with a placeholder-backed factory");
        assertInstanceOf(PlaceholderObjectInstance.class,
                registry.create(new ObjectSpawn(0x4A40, 0x0A38, Sonic3kObjectIds.CNZ_WATER_LEVEL_BUTTON, 0, 0, false, 0)),
                "Task 6 should reserve the CNZ water-button helper slot with a placeholder-backed factory");
        assertInstanceOf(PlaceholderObjectInstance.class,
                registry.create(new ObjectSpawn(0x4A40, 0x0A38, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0)),
                "Task 6 should reserve the CNZ miniboss slot with a placeholder-backed factory");
        assertInstanceOf(PlaceholderObjectInstance.class,
                registry.create(new ObjectSpawn(0x4A40, 0x0A38, Sonic3kObjectIds.CNZ_END_BOSS, 0, 0, false, 0)),
                "Task 6 should reserve the CNZ end-boss slot with a placeholder-backed factory");

        ObjectArtProvider provider = GameModuleRegistry.getCurrent().getObjectArtProvider();
        Sonic3kObjectArtProvider s3kProvider = assertInstanceOf(Sonic3kObjectArtProvider.class, provider);
        assertNotNull(s3kProvider.getRenderer(Sonic3kObjectArtKeys.CNZ_TELEPORTER),
                "CNZ teleporter art should be registered from ArtKosM_CNZTeleport");
        assertNotNull(s3kProvider.getRenderer(Sonic3kObjectArtKeys.CNZ_MINIBOSS),
                "CNZ miniboss art should be registered from PLC_5C_5D / Map_CNZMiniboss");
        assertNotNull(s3kProvider.getRenderer(Sonic3kObjectArtKeys.CNZ_END_BOSS),
                "CNZ end-boss art should be registered from PLC_6E / Map_CNZEndBoss");
    }

    private static Map<Integer, ?> readFactories(Sonic3kObjectRegistry registry) throws Exception {
        Field field = AbstractObjectRegistry.class.getDeclaredField("factories");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, ?> factories = (Map<Integer, ?>) field.get(registry);
        return factories;
    }

    private static void ensureLoaded(Sonic3kObjectRegistry registry) throws Exception {
        Method ensureLoaded = AbstractObjectRegistry.class.getDeclaredMethod("ensureLoaded");
        ensureLoaded.setAccessible(true);
        ensureLoaded.invoke(registry);
    }
}
