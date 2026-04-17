package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
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

        HudStaticArt staticArt = new HudStaticArt(
                new Pattern[] {
                        new Pattern(), new Pattern(), new Pattern(),
                        new Pattern(), new Pattern(), new Pattern(), new Pattern()
                },
                new SpriteMappingFrame(List.of()),
                new SpriteMappingFrame(List.of()),
                new SpriteMappingFrame(List.of(new SpriteMappingPiece(0, 0, 1, 1, 2, false, false, 0))),
                new SpriteMappingFrame(List.of()),
                new SpriteMappingFrame(List.of(new SpriteMappingPiece(0, 0, 1, 1, 6, false, false, 0))),
                new SpriteMappingFrame(List.of()),
                new SpriteMappingFrame(List.of(new SpriteMappingPiece(0, 0, 1, 1, 1, false, false, 0))));

        HudRenderManager hud = new HudRenderManager(graphicsManager, camera, gameState);
        hud.setDigitPatternIndex(200);
        hud.setLivesNumbersPatternIndex(220);
        hud.setStaticHudArt(0x28020, staticArt);
        hud.setBonusStageHudLayout(true);

        hud.draw(levelState, null);

        verify(graphicsManager).renderPatternWithId(eq(0x28026), any(), eq(16), eq(8));
        verify(graphicsManager, never()).renderPatternWithId(eq(0x28022), any(), eq(16), eq(24));
        verify(graphicsManager, never()).renderPatternWithId(eq(0x28026), any(), eq(16), eq(40));
        verify(graphicsManager, never()).renderPatternWithId(intThat(id -> id >= 0x28020 && id < 0x28022),
                any(), anyInt(), anyInt());
    }

    @Test
    void mappingDrivenLivesFramePreservesPiecePalettesWithoutPaletteUpload() {
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

        HudStaticArt staticArt = new HudStaticArt(
                new Pattern[] { new Pattern(), new Pattern(), new Pattern(), new Pattern() },
                new SpriteMappingFrame(List.of()),
                new SpriteMappingFrame(List.of()),
                new SpriteMappingFrame(List.of()),
                new SpriteMappingFrame(List.of()),
                new SpriteMappingFrame(List.of()),
                new SpriteMappingFrame(List.of()),
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0),
                        new SpriteMappingPiece(8, 0, 1, 1, 1, false, false, 1))));

        HudRenderManager hud = new HudRenderManager(graphicsManager, camera, gameState);
        hud.setDigitPatternIndex(200);
        hud.setLivesNumbersPatternIndex(220);
        hud.setStaticHudArt(0x28020, staticArt);

        hud.draw(levelState, null);

        verify(graphicsManager).renderPatternWithId(eq(0x28020), argThat(desc -> desc.getPaletteIndex() == 0), eq(16), eq(200));
        verify(graphicsManager).renderPatternWithId(eq(0x28021), argThat(desc -> desc.getPaletteIndex() == 1), eq(24), eq(200));
        verify(graphicsManager, never()).cachePaletteTexture(any(), anyInt());
    }

    @Test
    void mappingDrivenRingsFrameUsesFlashVariantInsteadOfHudString() {
        GraphicsManager graphicsManager = mock(GraphicsManager.class);
        Camera camera = mock(Camera.class);
        GameStateManager gameState = mock(GameStateManager.class);
        LevelState levelState = mock(LevelState.class);
        when(camera.getXWithShake()).thenReturn((short) 0);
        when(camera.getYWithShake()).thenReturn((short) 0);
        when(levelState.getRings()).thenReturn(0);
        when(levelState.getFlashCycle()).thenReturn(true);
        when(levelState.shouldFlashTimer()).thenReturn(false);
        when(levelState.getDisplayTime()).thenReturn("0:10");
        when(gameState.getScore()).thenReturn(0);
        when(gameState.getLives()).thenReturn(3);

        HudStaticArt staticArt = new HudStaticArt(
                new Pattern[] { new Pattern(), new Pattern() },
                new SpriteMappingFrame(List.of()),
                new SpriteMappingFrame(List.of()),
                new SpriteMappingFrame(List.of()),
                new SpriteMappingFrame(List.of()),
                new SpriteMappingFrame(List.of()),
                new SpriteMappingFrame(List.of(new SpriteMappingPiece(0, 0, 1, 1, 1, false, false, 0))),
                new SpriteMappingFrame(List.of()));

        HudRenderManager hud = new HudRenderManager(graphicsManager, camera, gameState);
        hud.setDigitPatternIndex(200);
        hud.setLivesNumbersPatternIndex(220);
        hud.setStaticHudArt(0x28020, staticArt);

        hud.draw(levelState, null);

        verify(graphicsManager).renderPatternWithId(eq(0x28021), any(), eq(16), eq(40));
        verify(graphicsManager, never()).renderPatternWithId(eq(100), any(), eq(16), eq(40));
    }
}
