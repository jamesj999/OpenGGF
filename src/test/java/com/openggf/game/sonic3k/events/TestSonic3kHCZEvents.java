package com.openggf.game.sonic3k.events;

import com.openggf.game.EngineServices;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.ScrollHandlerProvider.ZoneConstants;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.scroll.SwScrlHcz;
import com.openggf.level.ParallaxManager;
import com.openggf.level.scroll.ZoneScrollHandler;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestSonic3kHCZEvents {

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDown() throws IOException {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        deleteRecursively(Path.of("saves").resolve("test_hcz_transition_save"));
    }

    @Test
    void act1TransitionWritesProgressionSaveForActiveSlot() throws Exception {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();

        String gameCode = "test_hcz_transition_save";
        Path saveDir = Path.of("saves").resolve(gameCode);
        deleteRecursively(saveDir);

        GameModule sessionModule = mock(GameModule.class);
        when(sessionModule.getSaveSnapshotProvider()).thenReturn((reason, ctx) -> Map.of("marker", "hcz_transition"));
        when(sessionModule.rngFlavour()).thenReturn(GameRng.Flavour.S3K);

        SaveSessionContext saveContext = SaveSessionContext.forSlot(
                gameCode, 1, new SelectedTeam("sonic", List.of("tails")), 1, 0);
        GameplayModeContext gameplayMode = SessionManager.openGameplaySession(sessionModule, saveContext);
        RuntimeManager.createGameplay(gameplayMode);

        Sonic3kHCZEvents events = new Sonic3kHCZEvents();
        events.init(0);
        events.setEventsFg5(true);
        GameServices.gameState().setEndOfLevelFlag(true);

        events.update(0, 0);
        events.update(0, 1);

        assertTrue(Files.exists(saveDir.resolve("slot1.json")));
    }

    @Test
    void act2WallChasePrimesBgCollisionCameraBeforePhysics() throws Exception {
        SwScrlHcz handler = new SwScrlHcz();
        installParallaxHandler(Sonic3kZoneIds.ZONE_HCZ, handler);

        AbstractPlayableSprite player = placePlayer(0x0B00, 0x0700);
        GameServices.camera().setX((short) 0x0800);
        GameServices.camera().setY((short) 0x0600);

        Sonic3kHCZEvents events = new Sonic3kHCZEvents();
        events.init(1);

        tickAct2(events, 0);
        assertEquals(SwScrlHcz.Hcz2BgPhase.WALL_CHASE, handler.getHcz2BgPhase());
        assertEquals(0x0600, handler.getBgCameraX(),
                "init should expose Camera_X_pos_BG_copy = cameraX - $200");

        tickAct2(events, 1);
        assertTrue(GameServices.gameState().isBackgroundCollisionFlag(),
                "state 4 should gate Background_collision_flag on before physics");
        assertEquals(0x0600, handler.getBgCameraX(),
                "the first moving-state frame arms the chase before the offset advances");

        player.setCentreX((short) 0x0B20);
        tickAct2(events, 2);
        assertEquals(0x05FE, handler.getBgCameraX(),
                "after one fast-speed advance, BG camera X should be cameraX - $200 + Events_bg+$00");
        assertEquals((short) 0x0100, handler.getVscrollFactorBG(),
                "HCZ2 wall-chase BG camera Y should stay at cameraY - $500");
    }

    @Test
    void act2WallChaseConsumesFgEndSignalInSameFrame() throws Exception {
        SwScrlHcz handler = new SwScrlHcz();
        installParallaxHandler(Sonic3kZoneIds.ZONE_HCZ, handler);

        placePlayer(0x0B00, 0x0700);
        GameServices.camera().setX((short) 0x0800);
        GameServices.camera().setY((short) 0x0600);

        Sonic3kHCZEvents events = new Sonic3kHCZEvents();
        events.init(1);

        tickAct2(events, 0);
        tickAct2(events, 1);
        assertTrue(GameServices.gameState().isBackgroundCollisionFlag(),
                "wall chase should be active before the FG end signal frame");

        GameServices.camera().setX((short) 0x0C00);
        events.updatePrePhysics(1, 2);
        events.update(1, 2);

        assertTrue(events.isEventsFg5(), "FG end signal should be raised at Camera X >= $C00");
        assertFalse(GameServices.gameState().isBackgroundCollisionFlag(),
                "BG should consume Events_fg_5 in the same frame and clear background collision");
    }

    private static void tickAct2(Sonic3kHCZEvents events, int frame) {
        events.updatePrePhysics(1, frame);
        events.update(1, frame);
    }

    private static AbstractPlayableSprite placePlayer(int x, int y) {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) x, (short) y);
        GameServices.camera().setFocusedSprite(player);
        return player;
    }

    private static void installParallaxHandler(int zoneId, ZoneScrollHandler handler) throws Exception {
        ScrollHandlerProvider provider = new ScrollHandlerProvider() {
            @Override
            public void load(com.openggf.data.Rom rom) {
            }

            @Override
            public ZoneScrollHandler getHandler(int zoneIndex) {
                return zoneIndex == zoneId ? handler : null;
            }

            @Override
            public ZoneConstants getZoneConstants() {
                return mock(ZoneConstants.class);
            }
        };
        ParallaxManager parallaxManager = GameServices.parallax();
        Field field = ParallaxManager.class.getDeclaredField("scrollProvider");
        field.setAccessible(true);
        field.set(parallaxManager, provider);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }
}
