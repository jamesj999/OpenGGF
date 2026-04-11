package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
}


