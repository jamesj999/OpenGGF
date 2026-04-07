package com.openggf.game.session;

import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSessionManager {

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void openGameplaySession_setsCurrentWorldAndGameplayMode() {
        GameModule module = new Sonic2GameModule();
        GameplayModeContext gameplay = SessionManager.openGameplaySession(module);

        assertNotNull(gameplay);
        assertNotNull(SessionManager.getCurrentWorldSession());
        assertSame(gameplay, SessionManager.getCurrentGameplayMode());
        assertEquals(GameMode.LEVEL, gameplay.getGameMode());
        assertSame(module, SessionManager.getCurrentWorldSession().getGameModule());
    }

    @Test
    void openEditorStub_replacesModeButPreservesWorld() {
        GameplayModeContext gameplay = SessionManager.openGameplaySession(new Sonic2GameModule());
        WorldSession world = SessionManager.getCurrentWorldSession();

        EditorModeContext editor = SessionManager.enterEditorMode(
                new EditorCursorState(128, 256));

        assertNotNull(editor);
        assertSame(world, SessionManager.getCurrentWorldSession());
        assertSame(editor, SessionManager.getCurrentEditorMode());
        assertNull(SessionManager.getCurrentGameplayMode());
        assertNotSame(gameplay, editor);
    }

    @Test
    void openGameplaySession_replacesExistingEditorModeWithFreshWorld() {
        SessionManager.openGameplaySession(new Sonic2GameModule());
        EditorModeContext editor = SessionManager.enterEditorMode(new EditorCursorState(128, 256));

        GameplayModeContext gameplay = SessionManager.openGameplaySession(new Sonic2GameModule());

        assertNotNull(gameplay);
        assertSame(gameplay, SessionManager.getCurrentGameplayMode());
        assertNull(SessionManager.getCurrentEditorMode());
        assertNotSame(editor.getWorldSession(), SessionManager.getCurrentWorldSession());
    }

    @Test
    void enterEditorMode_withoutWorldSessionThrows() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> SessionManager.enterEditorMode(new EditorCursorState(1, 2)));

        assertEquals("Cannot enter editor mode without an active world session.", exception.getMessage());
    }

    @Test
    void openGameplaySession_rejectsNullModule() {
        assertThrows(NullPointerException.class, () -> SessionManager.openGameplaySession(null));
    }

    @Test
    void enterEditorMode_rejectsNullCursor() {
        SessionManager.openGameplaySession(new Sonic2GameModule());

        assertThrows(NullPointerException.class, () -> SessionManager.enterEditorMode(null));
    }
}
