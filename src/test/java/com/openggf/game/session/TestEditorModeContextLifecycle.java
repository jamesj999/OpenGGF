package com.openggf.game.session;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestEditorModeContextLifecycle {

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void resumeGameplayFromEditor_usesCursorAsSpawnAndPreservesResumeStash() {
        SessionManager.openGameplaySession(new Sonic2GameModule());
        EditorPlaytestStash stash = new EditorPlaytestStash(
                100, 200, 0x0400, -0x0080, true, 53, 2);
        EditorModeContext editor = SessionManager.enterEditorMode(new EditorCursorState(320, 640), stash);

        GameplayModeContext gameplay = SessionManager.resumeGameplayFromEditor();

        assertNotNull(gameplay);
        assertSame(editor.getWorldSession(), gameplay.getWorldSession());
        assertEquals(320, gameplay.getSpawnX());
        assertEquals(640, gameplay.getSpawnY());
        assertTrue(gameplay.hasResumeStash());
        assertSame(stash, gameplay.getResumeStash().orElseThrow());
        assertEquals(100, gameplay.getResumeStash().orElseThrow().playerX());
        assertEquals(200, gameplay.getResumeStash().orElseThrow().playerY());
    }

    @Test
    void restartGameplayFromBeginning_discardsStashAndResetsSpawnToOrigin() {
        SessionManager.openGameplaySession(new Sonic2GameModule());
        EditorPlaytestStash stash = new EditorPlaytestStash(
                500, 900, 0x0180, 0x0020, false, 7, 3);
        EditorModeContext editor = SessionManager.enterEditorMode(new EditorCursorState(320, 640), stash);

        GameplayModeContext gameplay = SessionManager.restartGameplayFromBeginning();

        assertNotNull(gameplay);
        assertSame(editor.getWorldSession(), gameplay.getWorldSession());
        assertEquals(0, gameplay.getSpawnX());
        assertEquals(0, gameplay.getSpawnY());
        assertFalse(gameplay.hasResumeStash());
        assertTrue(gameplay.getResumeStash().isEmpty());
    }

    @Test
    void exitEditorMode_remainsCompatibleWithResumeGameplaySemantics() {
        SessionManager.openGameplaySession(new Sonic2GameModule());
        EditorPlaytestStash stash = new EditorPlaytestStash(
                11, 22, 0x0030, 0x0040, true, 5, 0);
        SessionManager.enterEditorMode(new EditorCursorState(77, 88), stash);

        GameplayModeContext gameplay = SessionManager.exitEditorMode();

        assertEquals(77, gameplay.getSpawnX());
        assertEquals(88, gameplay.getSpawnY());
        assertTrue(gameplay.hasResumeStash());
        assertSame(stash, gameplay.getResumeStash().orElseThrow());
    }

    @Test
    void editorModeContext_retainsCursorAndPlaytestStash() {
        WorldSession world = new WorldSession(new Sonic2GameModule());
        EditorPlaytestStash stash = new EditorPlaytestStash(
                100, 200, 0x0400, -0x0080, true, 53, 2);

        EditorModeContext editor = new EditorModeContext(world, new EditorCursorState(320, 640), stash);

        assertSame(world, editor.getWorldSession());
        assertEquals(320, editor.getCursor().x());
        assertEquals(640, editor.getCursor().y());
        assertTrue(editor.hasPlaytestStash());
        assertNotNull(editor.getPlaytestStash());
        assertEquals(100, editor.getPlaytestStash().playerX());
        assertEquals(200, editor.getPlaytestStash().playerY());
        assertEquals(0x0400, editor.getPlaytestStash().xVelocity());
        assertEquals(-0x0080, editor.getPlaytestStash().yVelocity());
        assertEquals(53, editor.getPlaytestStash().rings());
        assertEquals(2, editor.getPlaytestStash().shieldState());
    }

    @Test
    void editorModeContext_cursorCanBeUpdatedAfterEntry() {
        WorldSession world = new WorldSession(new Sonic2GameModule());
        EditorModeContext editor = new EditorModeContext(world, new EditorCursorState(320, 640), null);

        editor.setCursor(new EditorCursorState(400, 768));

        assertEquals(400, editor.getCursor().x());
        assertEquals(768, editor.getCursor().y());
    }

    @Test
    void legacyEditorModeContextConstructorLeavesPlaytestStashEmpty() {
        WorldSession world = new WorldSession(new Sonic2GameModule());

        EditorModeContext editor = new EditorModeContext(world, new EditorCursorState(12, 34));

        assertSame(world, editor.getWorldSession());
        assertEquals(12, editor.getCursor().x());
        assertEquals(34, editor.getCursor().y());
        assertFalse(editor.hasPlaytestStash());
        assertNull(editor.getPlaytestStash());
    }
}
