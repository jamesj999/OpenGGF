package com.openggf.game.session;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestEditorModeContextLifecycle {

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void exitEditorMode_rebuildsFreshGameplayAtCursor() {
        SessionManager.openGameplaySession(new Sonic2GameModule());
        EditorModeContext editor = SessionManager.enterEditorMode(new EditorCursorState(320, 640));

        GameplayModeContext gameplay = SessionManager.exitEditorMode();

        assertNotNull(gameplay);
        assertSame(editor.getWorldSession(), gameplay.getWorldSession());
        assertEquals(320, gameplay.getSpawnX());
        assertEquals(640, gameplay.getSpawnY());
    }
}
