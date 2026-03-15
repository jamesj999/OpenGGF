package com.openggf.game.sonic3k.features;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.sonic3k.Sonic3kLoadBootstrap;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.events.FireCurtainRenderState;
import com.openggf.game.sonic3k.events.FireCurtainStage;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.level.LevelManager;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ResourceLoader;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestAizFireCurtainRendererRom {
    @ClassRule
    public static RequiresRomRule romRule = new RequiresRomRule();

    private static SharedLevel sharedLevel;

    @BeforeClass
    public static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
    }

    @AfterClass
    public static void cleanup() {
        if (sharedLevel != null) {
            sharedLevel.dispose();
        }
    }

    @Test
    public void realAizFakeoutProducesNonEmptyCurtainPlan() throws Exception {
        LevelManager levelManager = LevelManager.getInstance();
        levelManager.loadZoneAndAct(0, 0);

        Camera camera = Camera.getInstance();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        Sonic3kAIZEvents events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setEventsFg5(true);

        AizFireCurtainRenderer renderer = new AizFireCurtainRenderer();
        boolean sawRisingCurtain = false;
        boolean sawRefreshCurtain = false;

        for (int frame = 0; frame < 240 && !events.isAct2TransitionRequested(); frame++) {
            events.update(0, frame);
            FireCurtainRenderState state = events.getFireCurtainRenderState(224);
            if (!state.active() || state.coverHeightPx() <= 0) {
                continue;
            }

            AizFireCurtainRenderer.CurtainCompositionPlan plan =
                    renderer.buildCompositionPlan(state, 320, 224);
            int drawCount = 0;
            for (AizFireCurtainRenderer.ColumnRenderPlan column : plan.columns()) {
                drawCount += column.draws().size();
            }

            if (state.stage() == FireCurtainStage.AIZ1_RISING && drawCount > 0) {
                sawRisingCurtain = true;
            }
            if (state.stage() == FireCurtainStage.AIZ1_REFRESH && drawCount > 0) {
                sawRefreshCurtain = true;
            }
        }

        assertTrue("Expected non-empty curtain plan during AIZ1 rising fire", sawRisingCurtain);
        assertTrue("Expected non-empty curtain plan during AIZ1 refresh fire", sawRefreshCurtain);
    }

    @Test
    public void realAizFakeoutSamplesFlameOverlayTileRange() throws Exception {
        LevelManager levelManager = LevelManager.getInstance();
        levelManager.loadZoneAndAct(0, 0);

        Camera camera = Camera.getInstance();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        Sonic3kAIZEvents events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setEventsFg5(true);

        int overlayTileBase = 0x500;
        int overlayTileCount = loadFlameOverlayTileCount();
        int overlayTileEnd = overlayTileBase + overlayTileCount;

        AizFireCurtainRenderer renderer = new AizFireCurtainRenderer();
        boolean sawOverlayBackedCurtain = false;
        boolean sawDenseCurtain = false;

        for (int frame = 0; frame < 240 && !events.isAct2TransitionRequested(); frame++) {
            events.update(0, frame);
            FireCurtainRenderState state = events.getFireCurtainRenderState(224);
            if (!state.active() || state.coverHeightPx() <= 0) {
                continue;
            }

            AizFireCurtainRenderer.CurtainCompositionPlan plan =
                    renderer.buildCompositionPlan(state, 320, 224);
            Set<Integer> palettes = new HashSet<>();
            int overlayTiles = 0;
            int totalTiles = 0;
            for (AizFireCurtainRenderer.ColumnRenderPlan column : plan.columns()) {
                for (AizFireCurtainRenderer.TileDraw draw : column.draws()) {
                    int descriptor = draw.descriptor();
                    int patternIndex = descriptor & 0x7FF;
                    palettes.add((descriptor >> 13) & 0x3);
                    totalTiles++;
                    if (patternIndex >= overlayTileBase && patternIndex < overlayTileEnd) {
                        overlayTiles++;
                    }
                }
            }

            if (overlayTiles > 0) {
                sawOverlayBackedCurtain = true;
            }
            if (totalTiles >= 40 && palettes.size() == 1 && palettes.contains(3)) {
                sawDenseCurtain = true;
            }
            if (sawOverlayBackedCurtain && sawDenseCurtain) {
                break;
            }
        }

        assertTrue("Expected sampled fire curtain tiles to reference the staged flame overlay range",
                sawOverlayBackedCurtain);
        assertTrue("Expected fire curtain to provide a dense visible wall using palette line 4",
                sawDenseCurtain);
    }

    @Test
    public void realAizFakeoutReportsPerPhaseCurtainDescriptorStats() throws Exception {
        LevelManager levelManager = LevelManager.getInstance();
        levelManager.loadZoneAndAct(0, 0);

        Camera camera = Camera.getInstance();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        Sonic3kAIZEvents events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setEventsFg5(true);

        int overlayTileBase = 0x500;
        int overlayTileCount = loadFlameOverlayTileCount();
        int overlayTileEnd = overlayTileBase + overlayTileCount;

        AizFireCurtainRenderer renderer = new AizFireCurtainRenderer();
        EnumMap<FireCurtainStage, PhaseStats> statsByStage = new EnumMap<>(FireCurtainStage.class);

        for (int frame = 0; frame < 240 && !events.isAct2TransitionRequested(); frame++) {
            events.update(0, frame);
            FireCurtainRenderState state = events.getFireCurtainRenderState(224);
            collectStageStats(renderer, state, overlayTileBase, overlayTileEnd, statsByStage);
        }

        if (events.isAct2TransitionRequested()) {
            Sonic3kAIZEvents act2Events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
            act2Events.init(1);
            for (int frame = 0; frame < 240 && act2Events.getFireCurtainRenderState(224).active(); frame++) {
                act2Events.update(1, frame);
                FireCurtainRenderState state = act2Events.getFireCurtainRenderState(224);
                collectStageStats(renderer, state, overlayTileBase, overlayTileEnd, statsByStage);
            }
        }

        for (var entry : statsByStage.entrySet()) {
            FireCurtainStage stage = entry.getKey();
            PhaseStats stats = entry.getValue();
            System.out.println("AIZ fire curtain stage=" + stage
                    + " frames=" + stats.framesSeen
                    + " sourceXs=" + stats.sourceWorldXs
                    + " palettes=" + Arrays.toString(stats.paletteCounts)
                    + " minPattern=0x" + Integer.toHexString(stats.minPatternIndex == Integer.MAX_VALUE ? 0 : stats.minPatternIndex)
                    + " maxPattern=0x" + Integer.toHexString(stats.maxPatternIndex == Integer.MIN_VALUE ? 0 : stats.maxPatternIndex)
                    + " sawOverlayPattern=" + stats.sawOverlayPattern);
        }

        assertTrue("Expected to gather stage stats for the AIZ1 rising curtain",
                statsByStage.containsKey(FireCurtainStage.AIZ1_RISING));
        assertTrue("Expected to gather stage stats for the AIZ1 refresh curtain",
                statsByStage.containsKey(FireCurtainStage.AIZ1_REFRESH));
        assertTrue("Expected to gather stage stats for the AIZ2 redraw curtain",
                statsByStage.containsKey(FireCurtainStage.AIZ2_REDRAW));
        assertTrue("Expected to gather stage stats for the AIZ2 wait-fire curtain",
                statsByStage.containsKey(FireCurtainStage.AIZ2_WAIT_FIRE));
    }

    /**
     * ROM: AnPal_AIZ2 runs unconditionally every frame, even during the fire
     * continuation. The fire BG event (AIZ2BGE_WaitFire) overwrites palette
     * line 4 AFTER AnPal runs, so fire colors are preserved. Palette cycling
     * is allowed to modify line 4; the fire event restores it afterward.
     */
    @Test
    public void aiz2PaletteCyclerRunsDuringFireContinuation() throws Exception {
        LevelManager levelManager = LevelManager.getInstance();
        levelManager.loadZoneAndAct(0, 0);

        Camera camera = Camera.getInstance();
        camera.setX((short) 0x2F10);
        camera.setY((short) 0x0200);

        Sonic3kAIZEvents act1Events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        act1Events.init(0);
        act1Events.setEventsFg5(true);

        for (int frame = 0; frame < 240 && !act1Events.isAct2TransitionRequested(); frame++) {
            act1Events.update(0, frame);
        }

        levelManager.loadZoneAndAct(0, 1);
        Sonic3kAIZEvents act2Events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        act2Events.init(1);
        assertTrue("Expected active fire continuation after the act 1 fake-out reload",
                act2Events.isFireTransitionActive());

        AnimatedPaletteManager paletteManager = levelManager.getAnimatedPaletteManager();
        assertTrue("Expected an animated palette manager for AIZ2", paletteManager != null);
        // Palette cycling should run without error during fire continuation
        for (int i = 0; i < 8; i++) {
            paletteManager.update();
        }
    }

    private static int loadFlameOverlayTileCount() throws Exception {
        Rom rom = GameServices.rom().getRom();
        ResourceLoader loader = new ResourceLoader(rom);
        byte[] fireOverlay8x8 = loader.loadSingle(
                LoadOp.kosinskiMBase(Sonic3kConstants.ART_KOSM_AIZ1_FIRE_OVERLAY_ADDR));
        return fireOverlay8x8.length / Pattern.PATTERN_SIZE_IN_ROM;
    }

    private static int[] snapshotPaletteEntries(Palette palette, int... indices) {
        int[] words = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            Palette.Color color = palette.getColor(indices[i]);
            words[i] = ((color.r & 0xFF) << 16) | ((color.g & 0xFF) << 8) | (color.b & 0xFF);
        }
        return words;
    }

    private static final class PhaseStats {
        private final int[] paletteCounts = new int[4];
        private final Set<Integer> sourceWorldXs = new HashSet<>();
        private int framesSeen;
        private int minPatternIndex = Integer.MAX_VALUE;
        private int maxPatternIndex = Integer.MIN_VALUE;
        private boolean sawOverlayPattern;
    }

    private static void collectStageStats(AizFireCurtainRenderer renderer,
                                          FireCurtainRenderState state,
                                          int overlayTileBase,
                                          int overlayTileEnd,
                                          EnumMap<FireCurtainStage, PhaseStats> statsByStage) {
        if (state == null || !state.active() || state.coverHeightPx() <= 0 || state.stage() == FireCurtainStage.INACTIVE) {
            return;
        }

        AizFireCurtainRenderer.CurtainCompositionPlan plan =
                renderer.buildCompositionPlan(state, 320, 224);
        PhaseStats stats = statsByStage.computeIfAbsent(state.stage(), ignored -> new PhaseStats());
        stats.sourceWorldXs.add(state.sourceWorldX());
        stats.framesSeen++;

        for (AizFireCurtainRenderer.ColumnRenderPlan column : plan.columns()) {
            for (AizFireCurtainRenderer.TileDraw draw : column.draws()) {
                int descriptor = draw.descriptor();
                int paletteIndex = (descriptor >> 13) & 0x3;
                int patternIndex = descriptor & 0x7FF;
                stats.paletteCounts[paletteIndex]++;
                stats.minPatternIndex = Math.min(stats.minPatternIndex, patternIndex);
                stats.maxPatternIndex = Math.max(stats.maxPatternIndex, patternIndex);
                if (patternIndex >= overlayTileBase && patternIndex < overlayTileEnd) {
                    stats.sawOverlayPattern = true;
                }
            }
        }
    }
}
