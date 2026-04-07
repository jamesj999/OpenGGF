package com.openggf.game.session;

import com.openggf.game.GameMode;
import com.openggf.game.GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestSessionManager {

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
}
