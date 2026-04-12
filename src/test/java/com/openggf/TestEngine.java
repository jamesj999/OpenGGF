package com.openggf;

import com.openggf.game.EngineServices;
import com.openggf.game.GameMode;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
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

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
