package com.openggf.game.sonic3k.events;

/**
 * Deterministic AIZ fire curtain state emitted by {@link Sonic3kAIZEvents}.
 *
 * @param active active flag for the screen-space curtain overlay
 * @param coverHeightPx covered gameplay height in pixels, measured from the bottom of the screen
 * @param wavePhase current fire-transition wave phase
 * @param frameCounter deterministic animation tick for internal flame motion
 * @param sourceWorldX world X of the dedicated flame source strip
 * @param sourceWorldY world Y of the dedicated flame source strip
 * @param columnWaveOffsetsPx 20-column wave offsets in pixels
 * @param stage visual curtain stage
 * @param fireOverlayTileBase first pattern index of the fire overlay tiles (VRAM $500+)
 * @param fireOverlayTileCount number of fire overlay tiles loaded
 */
public record FireCurtainRenderState(
        boolean active,
        int coverHeightPx,
        int wavePhase,
        int frameCounter,
        int sourceWorldX,
        int sourceWorldY,
        int[] columnWaveOffsetsPx,
        FireCurtainStage stage,
        int fireOverlayTileBase,
        int fireOverlayTileCount) {

    public FireCurtainRenderState(
            boolean active,
            int coverHeightPx,
            int wavePhase,
            int frameCounter,
            int sourceWorldX,
            int sourceWorldY,
            int[] columnWaveOffsetsPx,
            FireCurtainStage stage) {
        this(active, coverHeightPx, wavePhase, frameCounter, sourceWorldX, sourceWorldY,
                columnWaveOffsetsPx, stage, 0, 0);
    }

    public FireCurtainRenderState {
        coverHeightPx = Math.max(0, coverHeightPx);
        frameCounter = Math.max(0, frameCounter);
        columnWaveOffsetsPx = columnWaveOffsetsPx != null ? columnWaveOffsetsPx.clone() : new int[0];
        stage = stage != null ? stage : FireCurtainStage.INACTIVE;
        fireOverlayTileBase = Math.max(0, fireOverlayTileBase);
        fireOverlayTileCount = Math.max(0, fireOverlayTileCount);
    }

    public static FireCurtainRenderState inactive() {
        return new FireCurtainRenderState(false, 0, 0, 0, 0, 0, new int[0], FireCurtainStage.INACTIVE);
    }

    public boolean fullyOpaqueToGameplay() {
        return active && coverHeightPx > 0 && stage != FireCurtainStage.AIZ1_RISING;
    }
}
