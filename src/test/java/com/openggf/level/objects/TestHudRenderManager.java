package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.Test;

import com.openggf.level.Palette;

import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestHudRenderManager {

    @Test
    public void bonusStageLayoutShowsOnlyRingsOnTopRow() {
        GraphicsManager graphicsManager = mock(GraphicsManager.class);
        Camera camera = mock(Camera.class);
        GameStateManager gameState = mock(GameStateManager.class);
        LevelState levelState = mock(LevelState.class);
        when(camera.getXWithShake()).thenReturn((short) 0);
        when(camera.getYWithShake()).thenReturn((short) 0);
        when(levelState.getRings()).thenReturn(23);
        when(levelState.getFlashCycle()).thenReturn(false);

        HudRenderManager hud = new HudRenderManager(graphicsManager, camera, gameState);
        hud.setTextPatternIndex(100, 32);
        hud.setDigitPatternIndex(200);
        hud.setLivesPatternIndex(300, 12);
        hud.setLivesNumbersPatternIndex(320);
        hud.setBonusStageHudLayout(true);

        hud.draw(levelState, null);

        verify(graphicsManager).renderPatternWithId(eq(106), any(), eq(16), eq(8));
        verify(graphicsManager, never()).renderPatternWithId(eq(100), any(), eq(16), eq(8));
        verify(graphicsManager, never()).renderPatternWithId(eq(116), any(), eq(16), eq(24));
        verify(graphicsManager, never()).renderPatternWithId(eq(106), any(), eq(16), eq(40));
        verify(graphicsManager, never()).renderPatternWithId(intThat(id -> id >= 300 && id < 332),
                any(), anyInt(), anyInt());
    }

    @Test
    public void livesPaletteOverrideSupplierRefreshesBetweenDraws() {
        GraphicsManager graphicsManager = mock(GraphicsManager.class);
        Camera camera = mock(Camera.class);
        GameStateManager gameState = mock(GameStateManager.class);
        LevelState levelState = mock(LevelState.class);
        when(camera.getXWithShake()).thenReturn((short) 0);
        when(camera.getYWithShake()).thenReturn((short) 0);
        when(levelState.getRings()).thenReturn(10);
        when(levelState.getFlashCycle()).thenReturn(false);
        when(levelState.shouldFlashTimer()).thenReturn(false);
        when(levelState.getDisplayTime()).thenReturn("0:10");
        when(gameState.getScore()).thenReturn(0);
        when(gameState.getLives()).thenReturn(3);

        HudRenderManager hud = new HudRenderManager(graphicsManager, camera, gameState);
        hud.setTextPatternIndex(100, 32);
        hud.setDigitPatternIndex(200);
        hud.setLivesPatternIndex(300, 12);
        hud.setLivesNumbersPatternIndex(320);
        hud.setLivesNameUsesIconPalette(true);

        Palette first = new Palette();
        setColor(first, 5, 0, 146, 0);
        Palette second = new Palette();
        setColor(second, 5, 255, 182, 0);
        AtomicReference<Palette> current = new AtomicReference<>(first);
        hud.setLivesPaletteOverrideSupplier(current::get);

        hud.draw(levelState, null);
        current.set(second);
        hud.draw(levelState, null);

        verify(graphicsManager, times(1)).cachePaletteTexture(argThat(paletteMatches(0, 146, 0)), eq(0));
        verify(graphicsManager, times(1)).cachePaletteTexture(argThat(paletteMatches(255, 182, 0)), eq(0));
    }

    private static org.mockito.ArgumentMatcher<Palette> paletteMatches(int r, int g, int b) {
        return palette -> {
            if (palette == null) {
                return false;
            }
            Palette.Color color = palette.getColor(5);
            return (color.r & 0xFF) == r
                    && (color.g & 0xFF) == g
                    && (color.b & 0xFF) == b;
        };
    }

    private static void setColor(Palette palette, int index, int r, int g, int b) {
        Palette.Color color = palette.getColor(index);
        color.r = (byte) r;
        color.g = (byte) g;
        color.b = (byte) b;
    }
}

