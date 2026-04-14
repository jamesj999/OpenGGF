package com.openggf;

import com.openggf.game.GameId;
import com.openggf.game.GameModule;
import com.openggf.game.EngineServices;
import com.openggf.game.GameMode;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.RuntimeManager;
import com.openggf.game.dataselect.DataSelectAction;
import com.openggf.game.dataselect.DataSelectActionType;
import com.openggf.game.save.SaveManager;
import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class TestEngine {

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
    }

    @Test
    void drawMasterTitleScreenDoesNotRequireGameplayCamera() throws Exception {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        Engine engine = new Engine();
        MasterTitleScreen masterTitleScreen = mock(MasterTitleScreen.class);

        setPrivateField(engine, "masterTitleScreen", masterTitleScreen);
        engine.getGameLoop().setGameMode(GameMode.MASTER_TITLE_SCREEN);

        assertDoesNotThrow(engine::draw);
        verify(masterTitleScreen).setProjectionMatrix(engine.getProjectionMatrixBuffer());
        verify(masterTitleScreen).draw();
    }

    @Test
    void createDataSelectSaveContext_preservesClearSaveStateFromPayload() throws Exception {
        Path saveRoot = Files.createTempDirectory("engine-dataselect-save");
        SaveManager saveManager = new SaveManager(saveRoot);
        saveManager.writeSlot("s3k", 1, Map.of(
                "zone", 6,
                "act", 1,
                "mainCharacter", "sonic",
                "sidekicks", List.of("tails"),
                "lives", 9,
                "continues", 3,
                "chaosEmeralds", List.of(0, 1, 2, 3, 4, 5, 6),
                "superEmeralds", List.of(0, 2),
                "clear", true
        ));

        GameModule module = mock(GameModule.class);
        when(module.getGameId()).thenReturn(GameId.S3K);

        DataSelectAction action = new DataSelectAction(
                DataSelectActionType.CLEAR_RESTART,
                1,
                10,
                0,
                new SelectedTeam("sonic", List.of("tails")));

        SaveSessionContext context = Engine.createDataSelectSaveContext(module, action, saveManager);

        assertEquals(1, context.activeSlot().orElseThrow());
        assertTrue(context.isClear(), "Clear-save launch context should preserve the clear flag");
        assertEquals("sonic", context.selectedTeam().mainCharacter());
        assertEquals(List.of("tails"), context.selectedTeam().sidekicks());
        assertEquals(10, context.startZone());
        assertEquals(0, context.startAct());
    }

    @Test
    void dataSelectLaunchSaveReason_mapsExistingSlotLoad() {
        assertEquals(Optional.of(SaveReason.EXISTING_SLOT_LOAD),
                Engine.dataSelectLaunchSaveReason(DataSelectActionType.LOAD_SLOT));
        assertEquals(Optional.of(SaveReason.NEW_SLOT_START),
                Engine.dataSelectLaunchSaveReason(DataSelectActionType.NEW_SLOT_START));
        assertEquals(Optional.of(SaveReason.CLEAR_RESTART_COMMIT),
                Engine.dataSelectLaunchSaveReason(DataSelectActionType.CLEAR_RESTART));
        assertEquals(Optional.empty(),
                Engine.dataSelectLaunchSaveReason(DataSelectActionType.NO_SAVE_START));
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
