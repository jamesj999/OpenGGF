package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.events.FireCurtainRenderState;
import com.openggf.game.sonic3k.events.FireCurtainStage;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.objects.AizHollowTreeObjectInstance;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.sprites.playable.GroundMode;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Headless tests for the AIZ1 miniboss fire curtain transition.
 *
 * <p>Exercises the fire transition state machine, fire curtain render state,
 * background tile sampling at the fire source coordinates, and palette state
 * at each phase of the transition.
 *
 * <p>The miniboss fight is bypassed by directly calling
 * {@code setEventsFg5(true)} on the AIZ events, which is equivalent to the
 * boss exit sequence completing.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kAiz1FireCurtainHeadless {

    @ClassRule
    public static RequiresRomRule romRule = new RequiresRomRule();

    private static final int ZONE_AIZ = 0;
    private static final int ACT_1 = 0;
    private static final int FPS = 60;

    // User-provided coordinates for pre-miniboss area (decimal).
    private static final short START_X = (short) 12002;
    private static final short START_Y = (short) 872;

    // ROM constants (mirror Sonic3kAIZEvents).
    private static final int FIRE_SOURCE_X_AIZ1 = 0x1000;
    private static final int FIRE_OVERLAY_TILE_DEST = 0x500;

    private static Object oldSkipIntros;
    private static SharedLevel sharedLevel;

    @BeforeClass
    public static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, ZONE_AIZ, ACT_1);
    }

    @AfterClass
    public static void cleanup() {
        SonicConfigurationService.getInstance().setConfigValue(
                SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        if (sharedLevel != null) sharedLevel.dispose();
    }

    private HeadlessTestFixture fixture;
    private Sonic sprite;

    @Before
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
        sprite = (Sonic) fixture.sprite();
        AizHollowTreeObjectInstance.resetTreeRevealCounter();
        LevelManager.getInstance().getObjectManager().reset(0);
    }

    // ---- Helpers ----

    private void teleportToMinibossArea() {
        sprite.setX(START_X);
        sprite.setY(START_Y);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAngle((byte) 0);
        sprite.setGroundMode(GroundMode.GROUND);
        sprite.setAir(false);
        sprite.setRolling(false);
        sprite.setJumping(false);
        sprite.setControlLocked(false);
        sprite.setObjectControlled(false);
        sprite.setObjectMappingFrameControl(false);
        sprite.setForcedAnimationId(-1);
        Camera camera = fixture.camera();
        camera.updatePosition(true);
        sprite.updateSensors(sprite.getX(), sprite.getY());
    }

    /**
     * Advance sprite rightwards until the camera reaches the boss arena lock
     * area or a frame budget is exhausted. Returns the frame count used.
     */
    private int runRightUntilCameraSettles(int maxFrames) {
        int prevCameraX = fixture.camera().getX();
        int settledFrames = 0;
        for (int frame = 0; frame < maxFrames; frame++) {
            fixture.stepFrame(false, false, false, true, false);
            int cameraX = fixture.camera().getX();
            if (cameraX == prevCameraX) {
                settledFrames++;
                if (settledFrames >= 10) {
                    return frame;
                }
            } else {
                settledFrames = 0;
            }
            prevCameraX = cameraX;
        }
        return maxFrames;
    }

    private Sonic3kAIZEvents getAizEvents() {
        Sonic3kLevelEventManager lem = Sonic3kLevelEventManager.getInstance();
        assertNotNull("Sonic3kLevelEventManager should exist", lem);
        Sonic3kAIZEvents events = lem.getAizEvents();
        assertNotNull("AIZ events should be initialized", events);
        return events;
    }

    // ---- Tests ----

    @Test
    public void backgroundMapAtFireSourceCoordinates_isMostlyEmpty() {
        teleportToMinibossArea();
        fixture.stepIdleFrames(1);

        LevelManager lm = LevelManager.getInstance();
        // Sample the BG sky area: x=0x1000..0x1140, y=0x20..0xE0.
        // The fire tiles live at Y >= 0x100; this range is the empty sky above.
        int emptyCount = 0;
        int totalCount = 0;
        for (int y = 0x20; y < 0xE0; y += 8) {
            for (int x = FIRE_SOURCE_X_AIZ1; x < FIRE_SOURCE_X_AIZ1 + 0x140; x += 8) {
                int desc = lm.getBackgroundTileDescriptorAtWorld(x, y);
                totalCount++;
                if ((desc & 0x7FF) == 0) {
                    emptyCount++;
                }
            }
        }

        // The diagnostic logs showed 142/142 empty at the fire source area (sky tiles).
        // This test documents the current state for regression tracking.
        assertTrue("Expected most BG tiles at fire source (0x1000, 0x20-0xE0) to be empty sky; "
                + "got " + emptyCount + "/" + totalCount + " empty",
                emptyCount > totalCount / 2);
    }

    @Test
    public void backgroundMapAtLowerYCoordinates_hasNonEmptyTiles() {
        teleportToMinibossArea();
        fixture.stepIdleFrames(1);

        LevelManager lm = LevelManager.getInstance();
        // The full-screen capture in the diagnostic showed non-empty tiles at lower Y.
        // The BG has actual fire/terrain tiles below the sky area.
        int nonEmptyCount = 0;
        int totalCount = 0;
        for (int y = 0x100; y < 0x300; y += 8) {
            for (int x = FIRE_SOURCE_X_AIZ1; x < FIRE_SOURCE_X_AIZ1 + 0x140; x += 8) {
                int desc = lm.getBackgroundTileDescriptorAtWorld(x, y);
                totalCount++;
                if ((desc & 0x7FF) != 0) {
                    nonEmptyCount++;
                }
            }
        }

        assertTrue("Expected some non-empty BG tiles at lower Y coords (0x100-0x300); "
                + "got " + nonEmptyCount + "/" + totalCount + " non-empty",
                nonEmptyCount > 0);
    }

    @Test
    public void fireTransitionTriggered_producesActiveRenderState() {
        teleportToMinibossArea();
        runRightUntilCameraSettles(3 * FPS);

        Sonic3kAIZEvents events = getAizEvents();
        events.setEventsFg5(true);
        // First frame starts transition; rise advances on subsequent frames.
        // BG camera must scroll past FIRE_TILE_START_Y (0x100) for visible cover.
        fixture.stepIdleFrames(10);

        assertTrue("Fire transition should be active after Events_fg_5 set",
                events.isFireTransitionActive());

        FireCurtainRenderState state = events.getFireCurtainRenderState(224);
        assertTrue("Render state should be active", state.active());
        assertTrue("Cover height should be > 0, got " + state.coverHeightPx(),
                state.coverHeightPx() > 0);
        assertEquals("Stage should be AIZ1_RISING",
                FireCurtainStage.AIZ1_RISING, state.stage());
    }

    @Test
    public void fireOverlayTilesLoaded_inRenderState() {
        teleportToMinibossArea();
        runRightUntilCameraSettles(3 * FPS);

        Sonic3kAIZEvents events = getAizEvents();
        events.setEventsFg5(true);
        fixture.stepIdleFrames(1);

        FireCurtainRenderState state = events.getFireCurtainRenderState(224);
        assertTrue("Fire overlay tiles should be loaded, got count=" + state.fireOverlayTileCount(),
                state.fireOverlayTileCount() > 0);
        assertEquals("Fire overlay tile base should be 0x500",
                FIRE_OVERLAY_TILE_DEST, state.fireOverlayTileBase());
    }

    @Test
    public void fireCurtainRise_progressesThroughPhases() {
        teleportToMinibossArea();
        runRightUntilCameraSettles(3 * FPS);

        Sonic3kAIZEvents events = getAizEvents();
        events.setEventsFg5(true);

        boolean sawRising = false;
        boolean sawRefresh = false;
        boolean sawFinish = false;
        int risingCoverMax = 0;

        for (int frame = 0; frame < 5 * FPS; frame++) {
            fixture.stepIdleFrames(1);

            FireCurtainRenderState state = events.getFireCurtainRenderState(224);
            if (state.active()) {
                risingCoverMax = Math.max(risingCoverMax, state.coverHeightPx());
                switch (state.stage()) {
                    case AIZ1_RISING -> sawRising = true;
                    case AIZ1_REFRESH -> sawRefresh = true;
                    case AIZ1_FINISH -> sawFinish = true;
                    default -> { }
                }
            }

            if (sawFinish) {
                break;
            }
        }

        assertTrue("Should pass through AIZ1_RISING stage", sawRising);
        assertTrue("Should progress to AIZ1_REFRESH stage", sawRefresh);
        assertTrue("Should reach AIZ1_FINISH stage", sawFinish);
        assertTrue("Cover height should reach full screen (224px), got " + risingCoverMax,
                risingCoverMax >= 224);
    }

    @Test
    public void fireCurtainRise_waveOffsetsArePopulated() {
        teleportToMinibossArea();
        runRightUntilCameraSettles(3 * FPS);

        Sonic3kAIZEvents events = getAizEvents();
        events.setEventsFg5(true);

        // Step enough frames for cover height to grow and wave to animate
        for (int frame = 0; frame < 30; frame++) {
            fixture.stepIdleFrames(1);
        }

        FireCurtainRenderState state = events.getFireCurtainRenderState(224);
        assertTrue("Render state should be active", state.active());
        assertEquals("Should have 20 column wave offsets",
                20, state.columnWaveOffsetsPx().length);

        // Wave offsets should not ALL be zero after some frames of animation
        boolean anyNonZero = false;
        for (int offset : state.columnWaveOffsetsPx()) {
            if (offset != 0) {
                anyNonZero = true;
                break;
            }
        }
        assertTrue("Wave offsets should contain non-zero values for wavy edge effect", anyNonZero);
    }

    @Test
    public void paletteLine4_hasFireColorsAfterTransitionStart() {
        teleportToMinibossArea();
        runRightUntilCameraSettles(3 * FPS);

        Sonic3kAIZEvents events = getAizEvents();
        events.setEventsFg5(true);
        fixture.stepIdleFrames(1);

        // After beginFireTransition(), palette line 4 (index 3) colors 1-6
        // should have fire transition words: 0x004E, 0x006E, 0x00AE, 0x00CE, 0x02EE, 0x0AEE
        Palette pal3 = LevelManager.getInstance().getCurrentLevel().getPalette(3);
        assertNotNull("Palette line 4 (index 3) should exist", pal3);

        // Convert expected fire words to RGB for comparison
        int[] expectedFireWords = {0x004E, 0x006E, 0x00AE, 0x00CE, 0x02EE, 0x0AEE};
        for (int i = 0; i < expectedFireWords.length; i++) {
            Palette.Color color = pal3.getColor(i + 1);
            assertNotNull("Palette color " + (i + 1) + " should exist", color);
            int segaWord = toSegaWord(color);
            assertEquals("Palette line 4 color " + (i + 1) + " should be fire color 0x"
                    + Integer.toHexString(expectedFireWords[i]),
                    expectedFireWords[i], segaWord);
        }
    }

    @Test
    public void paletteLine4_fullFirePaletteAfterMutation() {
        teleportToMinibossArea();
        runRightUntilCameraSettles(3 * FPS);

        Sonic3kAIZEvents events = getAizEvents();
        events.setEventsFg5(true);

        // Step until mutation phase (AIZ1_REFRESH = mutation applied)
        boolean reachedRefresh = false;
        for (int frame = 0; frame < 5 * FPS; frame++) {
            fixture.stepIdleFrames(1);
            FireCurtainRenderState state = events.getFireCurtainRenderState(224);
            if (state.stage() == FireCurtainStage.AIZ1_REFRESH
                    || state.stage() == FireCurtainStage.AIZ1_FINISH) {
                reachedRefresh = true;
                break;
            }
        }
        assertTrue("Should reach post-mutation phase", reachedRefresh);

        // After mutation, PalPointers #$0B should have been loaded, overwriting
        // ALL 16 colors of palette line 4 (index 3) with fire values.
        // Colors 7-15 should no longer have green AIZ1 values.
        Palette pal3 = LevelManager.getInstance().getCurrentLevel().getPalette(3);
        assertNotNull("Palette line 4 should exist after mutation", pal3);

        // Check that colors 7-15 are NOT the typical green AIZ1 waterfall colors.
        // Normal AIZ1 palette line 4 colors 12-14 cycle between green values.
        // After fire palette, none should be pure green (no blue/red component).
        boolean anyGreenOnly = false;
        StringBuilder colorDump = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            Palette.Color c = pal3.getColor(i);
            if (c != null) {
                int sega = toSegaWord(c);
                colorDump.append(String.format(" [%d]=0x%04X", i, sega));
                // "Green-only" = green component set but red and blue are zero
                int r = (sega >> 1) & 0x7;
                int g = (sega >> 5) & 0x7;
                int b = (sega >> 9) & 0x7;
                if (i >= 7 && g > 2 && r == 0 && b == 0) {
                    anyGreenOnly = true;
                }
            }
        }
        assertTrue("After mutation, palette line 4 should not have green-only colors in slots 7-15."
                + " Palette dump:" + colorDump,
                !anyGreenOnly);
    }

    @Test
    public void fireTransitionScrollActive_duringCurtainPhases() {
        teleportToMinibossArea();
        runRightUntilCameraSettles(3 * FPS);

        Sonic3kAIZEvents events = getAizEvents();
        events.setEventsFg5(true);
        fixture.stepIdleFrames(1);

        assertTrue("Fire transition scroll should be active during curtain",
                events.isFireTransitionScrollActive());
    }

    @Test
    public void coverHeightGrowsOverTime_duringRisingPhase() {
        teleportToMinibossArea();
        runRightUntilCameraSettles(3 * FPS);

        Sonic3kAIZEvents events = getAizEvents();
        events.setEventsFg5(true);

        int prevCover = 0;
        int growthCount = 0;
        for (int frame = 0; frame < 3 * FPS; frame++) {
            fixture.stepIdleFrames(1);
            FireCurtainRenderState state = events.getFireCurtainRenderState(224);
            if (state.active() && state.stage() == FireCurtainStage.AIZ1_RISING) {
                int cover = state.coverHeightPx();
                if (cover > prevCover) {
                    growthCount++;
                }
                prevCover = cover;
            }
        }

        assertTrue("Cover height should grow multiple times during rising phase, grew "
                + growthCount + " times", growthCount >= 3);
    }

    @Test
    public void fireOverlayPatterns_haveNonTransparentPixelData() {
        teleportToMinibossArea();
        runRightUntilCameraSettles(3 * FPS);

        // Trigger fire transition to ensure overlay tiles are loaded
        Sonic3kAIZEvents events = getAizEvents();
        events.setEventsFg5(true);
        fixture.stepIdleFrames(1);

        FireCurtainRenderState state = events.getFireCurtainRenderState(224);
        int tileBase = state.fireOverlayTileBase();
        int tileCount = state.fireOverlayTileCount();
        assertTrue("Fire overlay tiles should be loaded", tileCount > 0);

        com.openggf.level.Level level = LevelManager.getInstance().getCurrentLevel();
        int patternCount = level.getPatternCount();
        int patternsChecked = 0;
        int patternsWithContent = 0;
        int totalNonZeroPixels = 0;
        int nullPatterns = 0;
        StringBuilder firstPatternDump = new StringBuilder();

        for (int i = 0; i < Math.min(tileCount, 20); i++) {
            int patternIndex = tileBase + i;
            if (patternIndex >= patternCount) {
                break;
            }
            com.openggf.level.Pattern pattern = level.getPattern(patternIndex);
            if (pattern == null) {
                nullPatterns++;
                continue;
            }
            patternsChecked++;
            boolean hasContent = false;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    byte pixel = pattern.getPixel(x, y);
                    if (pixel != 0) {
                        hasContent = true;
                        totalNonZeroPixels++;
                    }
                    // Dump first pattern's pixel data for debugging
                    if (i == 0) {
                        firstPatternDump.append(String.format("%X", pixel & 0xF));
                    }
                }
                if (i == 0) {
                    firstPatternDump.append("|");
                }
            }
            if (hasContent) {
                patternsWithContent++;
            }
        }

        // Also check patterns BELOW the fire overlay range (regular level art)
        // to verify the pattern array is functional
        int regularContent = 0;
        for (int i = 10; i < Math.min(30, patternCount); i++) {
            com.openggf.level.Pattern p = level.getPattern(i);
            if (p != null) {
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        if (p.getPixel(x, y) != 0) {
                            regularContent++;
                            break;
                        }
                    }
                    if (regularContent > 0) break;
                }
            }
        }

        assertTrue("Should have checked some fire overlay patterns; checked=" + patternsChecked
                + " null=" + nullPatterns
                + " patternCount=" + patternCount
                + " tileBase=0x" + Integer.toHexString(tileBase)
                + " tileCount=" + tileCount
                + " regularArtHasContent=" + (regularContent > 0)
                + " firstPatternPixels=" + firstPatternDump,
                patternsChecked > 0);
        assertTrue("Fire overlay patterns should have non-transparent pixel data; "
                + patternsWithContent + "/" + patternsChecked + " had content, "
                + totalNonZeroPixels + " total non-zero pixels"
                + " null=" + nullPatterns
                + " patternCount=" + patternCount
                + " tileBase=0x" + Integer.toHexString(tileBase)
                + " regularArtHasContent=" + (regularContent > 0)
                + " firstPatternPixels=" + firstPatternDump,
                patternsWithContent > 0 && totalNonZeroPixels > 10);
    }

    /**
     * Traces the fire transition frame by frame using only the public
     * render state and BG sampling to understand timing and coverage.
     */
    @Test
    public void diagnostic_frameByFrameFireTransition() {
        teleportToMinibossArea();
        runRightUntilCameraSettles(3 * FPS);

        Sonic3kAIZEvents events = getAizEvents();
        events.setEventsFg5(true);

        LevelManager lm = LevelManager.getInstance();
        int firstActiveFrame = -1;
        int lastActiveFrame = -1;
        String lastStage = "";

        System.out.println("=== Fire Transition Frame-by-Frame ===");
        for (int frame = 0; frame < 6 * FPS; frame++) {
            fixture.stepIdleFrames(1);

            FireCurtainRenderState state = events.getFireCurtainRenderState(224);
            String stageStr = state.active() ? state.stage().toString() : "INACTIVE";

            if (state.active() && firstActiveFrame < 0) firstActiveFrame = frame;
            if (state.active()) lastActiveFrame = frame;

            // Sample BG at screen bottom using VDP mapping
            int bgY = state.sourceWorldY();
            int bottomSourceY = bgY + 216;
            int bottomDesc = lm.getBackgroundTileDescriptorAtWorld(FIRE_SOURCE_X_AIZ1, bottomSourceY);
            int bottomPat = bottomDesc & 0x7FF;
            boolean bottomHasFire = bottomPat >= 0x500;

            // Count fire tiles in one column using VDP mapping
            int fireTileCount = 0;
            for (int drawY = 216; drawY >= 0; drawY -= 8) {
                int sy = bgY + drawY;
                int d = lm.getBackgroundTileDescriptorAtWorld(FIRE_SOURCE_X_AIZ1, sy);
                if ((d & 0x7FF) >= 0x500) fireTileCount++;
            }

            boolean stageChanged = !stageStr.equals(lastStage);

            if (frame < 5 || frame % 20 == 0 || stageChanged || !state.active()) {
                System.out.printf("  frame %3d: stage=%-14s bgY=0x%04X coverH=%3d wave=%d btmPat=0x%03X fireTiles=%d%n",
                        frame, stageStr, bgY, state.coverHeightPx(),
                        state.columnWaveOffsetsPx().length > 0 ? state.columnWaveOffsetsPx()[0] : 0,
                        bottomPat, fireTileCount);
            }

            if (stageChanged && !stageStr.equals("INACTIVE")) {
                System.out.printf("  >>> STAGE CHANGE at frame %d: %s -> %s%n", frame, lastStage, stageStr);
            }
            lastStage = stageStr;

            if (frame > lastActiveFrame + 5 && lastActiveFrame > 0 && !state.active()) {
                break;
            }
        }

        System.out.println("First active frame: " + firstActiveFrame);
        System.out.println("Last active frame: " + lastActiveFrame);
        assertTrue("Fire curtain should become active", firstActiveFrame >= 0);
    }

    /**
     * With VDP-like source mapping (sourceY = bgY + drawY), the BG sampling
     * finds fire tiles (pattern 0x500+) at the screen bottom once bgY reaches
     * the fire tile area (Y >= 0x100 in the BG layout).
     */
    @Test
    public void bgSamplingWithVdpMapping_findsFireTilesAfterBgYReachesFireArea() {
        teleportToMinibossArea();
        runRightUntilCameraSettles(3 * FPS);

        Sonic3kAIZEvents events = getAizEvents();
        events.setEventsFg5(true);
        fixture.stepIdleFrames(1);

        LevelManager lm = LevelManager.getInstance();
        // With VDP mapping: screen bottom (drawY=216) maps to sourceY = bgY + 216.
        // Fire tiles exist at BG Y >= 0x100. So fire appears when bgY + 216 >= 0x100,
        // i.e., bgY >= 0x100 - 216 ≈ 0x28. Since bgY starts at 0x20 and increases,
        // fire tiles should appear within a few frames.
        int fireTilesFound = 0;
        for (int frame = 0; frame < 30; frame++) {
            fixture.stepIdleFrames(1);
            int bgY = events.getFireTransitionBgY();
            // Sample at screen bottom using VDP mapping
            int sourceY = bgY + 216; // screen bottom
            int desc = lm.getBackgroundTileDescriptorAtWorld(FIRE_SOURCE_X_AIZ1, sourceY);
            int patIdx = desc & 0x7FF;
            if (patIdx >= 0x500) {
                fireTilesFound++;
            }
        }
        assertTrue("BG sampling with VDP mapping should find fire tiles (0x500+) "
                + "at screen bottom within 30 frames; found " + fireTilesFound,
                fireTilesFound > 0);
    }

    /**
     * Renders the fire curtain composition to PNG images at several key frames.
     * Uses the exact same BG sampling logic as AizFireCurtainRenderer to produce
     * a visual diagnostic of what the curtain actually looks like.
     */
    @Test
    public void diagnostic_renderFireCurtainToImage() throws Exception {
        teleportToMinibossArea();
        runRightUntilCameraSettles(3 * FPS);

        Sonic3kAIZEvents events = getAizEvents();
        events.setEventsFg5(true);

        LevelManager lm = LevelManager.getInstance();
        Level level = lm.getCurrentLevel();

        // Capture images at key frames: early rise, mid rise, near-mutation, post-mutation
        // Also dump palette at frames 1-30 to find when it changes
        for (int preFrame = 0; preFrame < 35; preFrame++) {
            fixture.stepIdleFrames(1);
            Palette p3 = level.getPalette(3);
            if (p3 != null) {
                Palette.Color c1 = p3.getColor(1);
                boolean isFire = (c1.r & 0xFF) > 200 && (c1.g & 0xFF) < 100;
                if (preFrame < 5 || preFrame % 5 == 0 || !isFire) {
                    System.out.printf("  pal3-check frame %2d: R=%3d G=%3d B=%3d %s%n",
                            preFrame, c1.r & 0xFF, c1.g & 0xFF, c1.b & 0xFF,
                            isFire ? "FIRE" : "NOT-FIRE");
                }
            }
        }
        int[] captureFrames = {60, 120, 160, 170, 200};
        for (int targetFrame = 0, idx = 0; idx < captureFrames.length; idx++) {
            int target = captureFrames[idx];
            while (targetFrame < target) {
                fixture.stepIdleFrames(1);
                targetFrame++;
            }

            FireCurtainRenderState state = events.getFireCurtainRenderState(224);
            if (!state.active() || state.coverHeightPx() <= 0) {
                System.out.println("Frame " + target + ": curtain inactive, skipping image");
                continue;
            }

            // Dump palette line 3 at this frame
            Palette pal3 = level.getPalette(3);
            StringBuilder palDump = new StringBuilder("  pal3: ");
            if (pal3 != null) {
                for (int c = 0; c < 8; c++) {
                    Palette.Color col = pal3.getColor(c);
                    palDump.append(String.format("[%d]=R%d,G%d,B%d ", c,
                            col.r & 0xFF, col.g & 0xFF, col.b & 0xFF));
                }
            }
            System.out.println(palDump);

            BufferedImage img = renderFireCurtainToImage(lm, level, state, 320, 224);
            File outFile = new File("fire_curtain_frame_" + target + ".png");
            ImageIO.write(img, "PNG", outFile);
            System.out.println("Wrote " + outFile.getAbsolutePath()
                    + " (stage=" + state.stage()
                    + " bgY=0x" + Integer.toHexString(state.sourceWorldY())
                    + " cover=" + state.coverHeightPx() + ")");
        }
    }

    /**
     * Renders the fire curtain to a BufferedImage by sampling the BG tilemap
     * and looking up pattern pixel data + palette colors.
     * Matches AizFireCurtainRenderer.buildBackgroundSampledPlan logic exactly.
     */
    private BufferedImage renderFireCurtainToImage(LevelManager lm, Level level,
                                                    FireCurtainRenderState state,
                                                    int screenWidth, int screenHeight) {
        BufferedImage img = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_ARGB);
        // Fill with transparent black (represents the scene behind the curtain)
        for (int y = 0; y < screenHeight; y++) {
            for (int x = 0; x < screenWidth; x++) {
                img.setRGB(x, y, 0xFF000000); // opaque black background
            }
        }

        int bgY = state.sourceWorldY();
        int baseTop = Math.max(0, Math.min(screenHeight, screenHeight - state.coverHeightPx()));
        int[] waveOffsets = state.columnWaveOffsetsPx();
        int tileBase = state.fireOverlayTileBase();
        int tileCount = state.fireOverlayTileCount();
        boolean fullyOpaque = state.fullyOpaqueToGameplay();

        // Get fire palette (palette line 3 = index 3)
        Palette firePalette = level.getPaletteCount() > 3 ? level.getPalette(3) : null;

        int columnCount = 20;
        for (int col = 0; col < columnCount; col++) {
            int columnLeft = (col * screenWidth) / columnCount;
            int columnRight = ((col + 1) * screenWidth) / columnCount;
            int waveOffset = fullyOpaque ? 0 : (col < waveOffsets.length ? waveOffsets[col] : 0);
            int clipTop = Math.max(0, Math.min(screenHeight, baseTop - waveOffset));

            // Per-column VScroll: iterate over BG tile grid rows (8-aligned in BG
            // space) so adjacent columns with different VScroll values sample the
            // same BG tile row and draw it at the correct sub-tile screen position.
            int columnVScroll = bgY + waveOffset;
            int bgAtBottom = columnVScroll + screenHeight;
            int bgAtClipTop = columnVScroll + clipTop;
            int bgRowBottom = Math.floorDiv(bgAtBottom - 1, 8);
            int bgRowTop = Math.floorDiv(bgAtClipTop - 8, 8);

            for (int bgRow = bgRowBottom; bgRow >= bgRowTop; bgRow--) {
                int bgTileY = bgRow * 8;
                int drawY = bgTileY - columnVScroll;
                if (drawY >= screenHeight || drawY + 8 <= clipTop) continue;

                for (int drawX = columnLeft; drawX < columnRight; drawX += 8) {
                    int sourceX = state.sourceWorldX() + drawX;
                    int descriptor = lm.getBackgroundTileDescriptorAtWorld(sourceX, bgTileY);
                    int patternIndex = descriptor & 0x7FF;

                    // Same logic as AizFireCurtainRenderer
                    int renderPatternIdx;
                    if (patternIndex >= tileBase && patternIndex < tileBase + tileCount) {
                        renderPatternIdx = patternIndex;
                    } else if (tileCount > 0) {
                        int fallbackIdx = ((drawX / 8) + (bgRow & 0x7F)) % tileCount;
                        renderPatternIdx = tileBase + fallbackIdx;
                    } else {
                        continue;
                    }

                    boolean hFlip = (descriptor & 0x800) != 0;
                    boolean vFlip = (descriptor & 0x1000) != 0;

                    // Look up pattern data
                    Pattern pattern = (renderPatternIdx >= 0 && renderPatternIdx < level.getPatternCount())
                            ? level.getPattern(renderPatternIdx) : null;
                    if (pattern == null) continue;

                    // Render 8x8 tile
                    for (int py = 0; py < 8; py++) {
                        for (int px = 0; px < 8; px++) {
                            int screenX = drawX + (hFlip ? 7 - px : px);
                            int screenY = drawY + (vFlip ? 7 - py : py);
                            if (screenX < 0 || screenX >= screenWidth || screenY < 0 || screenY >= screenHeight) {
                                continue;
                            }
                            byte colorIdx = pattern.getPixel(px, py);
                            if (colorIdx == 0) continue; // transparent

                            int rgb;
                            if (firePalette != null && colorIdx < firePalette.getColorCount()) {
                                Palette.Color c = firePalette.getColor(colorIdx);
                                rgb = 0xFF000000 | ((c.r & 0xFF) << 16) | ((c.g & 0xFF) << 8) | (c.b & 0xFF);
                            } else {
                                rgb = 0xFFFF00FF; // magenta = missing palette
                            }
                            img.setRGB(screenX, screenY, rgb);
                        }
                    }
                }
            }
        }
        return img;
    }

    // ---- Utility ----

    @Test
    public void diagnostic_fireTileContentAndBgSampling() {
        teleportToMinibossArea();
        runRightUntilCameraSettles(3 * FPS);

        Sonic3kAIZEvents events = getAizEvents();
        events.setEventsFg5(true);
        fixture.stepIdleFrames(1);

        FireCurtainRenderState state = events.getFireCurtainRenderState(224);
        int tileBase = state.fireOverlayTileBase();
        int tileCount = state.fireOverlayTileCount();
        Level level = LevelManager.getInstance().getCurrentLevel();
        int patternCount = level.getPatternCount();

        System.out.println("=== Fire Overlay Tile Stats ===");
        System.out.println("tileBase=0x" + Integer.toHexString(tileBase)
                + " tileCount=" + tileCount
                + " patternCount=" + patternCount);

        // Dump pixel coverage of first 10 fire overlay patterns
        int totalFirePixels = 0;
        int totalFireTransparent = 0;
        for (int i = 0; i < Math.min(tileCount, 10); i++) {
            int idx = tileBase + i;
            if (idx >= patternCount) break;
            Pattern p = level.getPattern(idx);
            if (p == null) { System.out.println("  pattern " + idx + ": NULL"); continue; }
            int nonZero = 0;
            StringBuilder row = new StringBuilder();
            for (int py = 0; py < 8; py++) {
                for (int px = 0; px < 8; px++) {
                    byte pix = p.getPixel(px, py);
                    row.append(String.format("%X", pix & 0xF));
                    if (pix != 0) nonZero++;
                }
                row.append('|');
            }
            totalFirePixels += nonZero;
            totalFireTransparent += (64 - nonZero);
            System.out.println("  pattern 0x" + Integer.toHexString(idx)
                    + ": " + nonZero + "/64 opaque  " + row);
        }
        System.out.println("Fire overlay total: " + totalFirePixels + " opaque, "
                + totalFireTransparent + " transparent (first " + Math.min(tileCount, 10) + " tiles)");

        // Dump full palette line 3 (all 16 colors)
        Palette pal3 = level.getPalette(3);
        System.out.println("\n=== Palette Line 3 (all 16 colors) ===");
        if (pal3 != null) {
            for (int c = 0; c < 16; c++) {
                Palette.Color col = pal3.getColor(c);
                if (col != null) {
                    System.out.printf("  [%2d] R=%3d G=%3d B=%3d  sega=0x%04X%n",
                            c, col.r & 0xFF, col.g & 0xFF, col.b & 0xFF, toSegaWord(col));
                }
            }
        }

        // Sample BG at fire coords: count fire vs non-fire vs empty descriptors
        System.out.println("\n=== BG Sampling at Fire Coords ===");
        LevelManager lm = LevelManager.getInstance();
        int bgY = state.sourceWorldY();
        System.out.println("sourceWorldX=0x" + Integer.toHexString(state.sourceWorldX())
                + " bgY=0x" + Integer.toHexString(bgY));
        int fireHits = 0, nonFireHits = 0, emptyHits = 0;
        // Sample a 40x28 grid (full screen of tiles)
        for (int drawY = 216; drawY >= 0; drawY -= 8) {
            StringBuilder rowDesc = new StringBuilder();
            for (int drawX = 0; drawX < 320; drawX += 8) {
                int sourceX = state.sourceWorldX() + drawX;
                int sourceY = bgY + drawY;
                int desc = lm.getBackgroundTileDescriptorAtWorld(sourceX, sourceY);
                int patIdx = desc & 0x7FF;
                if (patIdx == 0) {
                    emptyHits++;
                    rowDesc.append("___ ");
                } else if (patIdx >= tileBase && patIdx < tileBase + tileCount) {
                    fireHits++;
                    rowDesc.append(String.format("%03X ", patIdx));
                } else {
                    nonFireHits++;
                    rowDesc.append(String.format("%03x ", patIdx));
                }
            }
            if (drawY == 216 || drawY == 112 || drawY == 0) {
                System.out.println("  drawY=" + drawY + " srcY=0x" + Integer.toHexString(bgY + drawY)
                        + ": " + rowDesc);
            }
        }
        System.out.println("BG grid at bgY=0x20: " + fireHits + " fire, " + nonFireHits + " non-fire, "
                + emptyHits + " empty (out of " + (fireHits + nonFireHits + emptyHits) + " total)");

        // Check BG at higher Y values (fire tiles should be at Y >= 0x100)
        System.out.println("\n=== BG at higher Y (fire region Y >= 0x100) ===");
        System.out.println("(BG dimensions not directly accessible)");

        for (int testBgY : new int[]{0x100, 0x120, 0x180, 0x200, 0x300}) {
            int f = 0, nf = 0, e = 0;
            StringBuilder sample = new StringBuilder();
            for (int dx = 0; dx < 320; dx += 8) {
                int sx = state.sourceWorldX() + dx;
                int sy = testBgY + 216; // screen bottom
                int desc = lm.getBackgroundTileDescriptorAtWorld(sx, sy);
                int patIdx = desc & 0x7FF;
                if (patIdx == 0) e++;
                else if (patIdx >= tileBase && patIdx < tileBase + tileCount) f++;
                else nf++;
                if (dx < 80) sample.append(String.format("%03X ", patIdx));
            }
            System.out.println("  bgY=0x" + Integer.toHexString(testBgY)
                    + " (screenBottom srcY=0x" + Integer.toHexString(testBgY + 216) + ")"
                    + ": fire=" + f + " nonFire=" + nf + " empty=" + e
                    + " first10=" + sample);
        }

        // Scan entire BG for ANY fire tiles (scan 8192x2048)
        int bgFireFound = 0;
        int firstFireX = -1, firstFireY = -1;
        for (int sy = 0; sy < 0x800; sy += 8) {
            for (int sx = 0; sx < 0x2000; sx += 8) {
                int desc = lm.getBackgroundTileDescriptorAtWorld(sx, sy);
                int patIdx = desc & 0x7FF;
                if (patIdx >= tileBase && patIdx < tileBase + tileCount) {
                    bgFireFound++;
                    if (firstFireX < 0) { firstFireX = sx; firstFireY = sy; }
                }
            }
        }
        System.out.println("\n=== Full BG scan for fire tiles (0x" + Integer.toHexString(tileBase)
                + "-0x" + Integer.toHexString(tileBase + tileCount) + ") ===");
        System.out.println("Found: " + bgFireFound + " fire tiles in BG"
                + (firstFireX >= 0 ? " first at (" + firstFireX + "," + firstFireY + ")"
                        + " = (0x" + Integer.toHexString(firstFireX) + ",0x" + Integer.toHexString(firstFireY) + ")" : ""));
    }

    /**
     * Renders the fire curtain with ALL wave offsets forced to zero (flat columns).
     * Isolates whether the staircase is caused by wave offsets or by BG tile data itself.
     * Also dumps BG map dimensions and tile descriptors at key Y positions.
     */
    @Test
    public void diagnostic_flatGridFireCurtain() throws Exception {
        teleportToMinibossArea();
        runRightUntilCameraSettles(3 * FPS);

        Sonic3kAIZEvents events = getAizEvents();
        events.setEventsFg5(true);

        // Step to frame 160 (AIZ1_FINISH stage with full screen cover)
        for (int frame = 0; frame < 160; frame++) {
            fixture.stepIdleFrames(1);
        }

        LevelManager lm = LevelManager.getInstance();
        Level level = lm.getCurrentLevel();
        FireCurtainRenderState state = events.getFireCurtainRenderState(224);

        // Dump BG map dimensions
        int bgWidthBlocks = level.getLayerWidthBlocks(1);
        int bgHeightBlocks = level.getLayerHeightBlocks(1);
        System.out.println("=== BG Map Dimensions ===");
        System.out.println("BG width: " + bgWidthBlocks + " blocks = " + (bgWidthBlocks * 128) + " px");
        System.out.println("BG height: " + bgHeightBlocks + " blocks = " + (bgHeightBlocks * 128) + " px");
        System.out.println("bgY (sourceWorldY) = 0x" + Integer.toHexString(state.sourceWorldY()));
        System.out.println("sourceWorldX = 0x" + Integer.toHexString(state.sourceWorldX()));
        System.out.println("coverHeightPx = " + state.coverHeightPx());
        System.out.println("stage = " + state.stage());

        int bgHeightPx = bgHeightBlocks * 128;
        System.out.println("\n=== BG Y wrap analysis ===");
        int bgY = state.sourceWorldY();
        System.out.println("columnVScroll(wave=0) = bgY = 0x" + Integer.toHexString(bgY));
        System.out.println("Top of screen: BG Y = 0x" + Integer.toHexString(bgY)
                + " wrapped = 0x" + Integer.toHexString(Math.floorMod(bgY, bgHeightPx)));
        System.out.println("Bottom of screen: BG Y = 0x" + Integer.toHexString(bgY + 224)
                + " wrapped = 0x" + Integer.toHexString(Math.floorMod(bgY + 224, bgHeightPx)));

        // Dump all BG tile descriptors for ONE column (column 0) at every 8px row
        System.out.println("\n=== Column 0 tile descriptors (no wave, sourceX=0x"
                + Integer.toHexString(state.sourceWorldX()) + ") ===");
        int tileBase = state.fireOverlayTileBase();
        int tileCount = state.fireOverlayTileCount();
        for (int screenY = 0; screenY < 224; screenY += 8) {
            int sourceBgY = bgY + screenY;
            int wrappedBgY = Math.floorMod(sourceBgY, bgHeightPx);
            int desc = lm.getBackgroundTileDescriptorAtWorld(state.sourceWorldX(), sourceBgY);
            int patIdx = desc & 0x7FF;
            boolean isFire = patIdx >= tileBase && patIdx < tileBase + tileCount;
            System.out.printf("  screenY=%3d  bgY=0x%04X  wrapped=0x%04X  pat=0x%03X  %s  hflip=%b vflip=%b%n",
                    screenY, sourceBgY, wrappedBgY, patIdx,
                    isFire ? "FIRE" : (patIdx == 0 ? "EMPTY" : "other"),
                    (desc & 0x800) != 0, (desc & 0x1000) != 0);
        }

        // Render FLAT fire curtain (all wave offsets = 0)
        Palette firePalette = level.getPaletteCount() > 3 ? level.getPalette(3) : null;
        BufferedImage flatImg = new BufferedImage(320, 224, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                flatImg.setRGB(x, y, 0xFF000000);
            }
        }

        // No columns, no wave - just iterate every 8px tile position
        for (int screenY = 0; screenY < 224; screenY += 8) {
            int sourceBgY = bgY + screenY;
            for (int screenX = 0; screenX < 320; screenX += 8) {
                int sourceX = state.sourceWorldX() + screenX;
                int desc = lm.getBackgroundTileDescriptorAtWorld(sourceX, sourceBgY);
                int patIdx = desc & 0x7FF;

                int renderPatIdx;
                if (patIdx >= tileBase && patIdx < tileBase + tileCount) {
                    renderPatIdx = patIdx;
                } else if (tileCount > 0) {
                    int bgRow = Math.floorDiv(sourceBgY, 8);
                    int fallbackIdx = ((screenX / 8) + (bgRow & 0x7F)) % tileCount;
                    renderPatIdx = tileBase + fallbackIdx;
                } else {
                    continue;
                }

                boolean hFlip = (desc & 0x800) != 0;
                boolean vFlip = (desc & 0x1000) != 0;
                Pattern pattern = (renderPatIdx >= 0 && renderPatIdx < level.getPatternCount())
                        ? level.getPattern(renderPatIdx) : null;
                if (pattern == null) continue;

                for (int py = 0; py < 8; py++) {
                    for (int px = 0; px < 8; px++) {
                        int drawX = screenX + (hFlip ? 7 - px : px);
                        int drawY = screenY + (vFlip ? 7 - py : py);
                        if (drawX < 0 || drawX >= 320 || drawY < 0 || drawY >= 224) continue;
                        byte colorIdx = pattern.getPixel(px, py);
                        if (colorIdx == 0) continue;
                        int rgb;
                        if (firePalette != null && colorIdx < firePalette.getColorCount()) {
                            Palette.Color c = firePalette.getColor(colorIdx);
                            rgb = 0xFF000000 | ((c.r & 0xFF) << 16) | ((c.g & 0xFF) << 8) | (c.b & 0xFF);
                        } else {
                            rgb = 0xFFFF00FF;
                        }
                        flatImg.setRGB(drawX, drawY, rgb);
                    }
                }
            }
        }

        File flatFile = new File("fire_curtain_FLAT.png");
        ImageIO.write(flatImg, "PNG", flatFile);
        System.out.println("\nWrote " + flatFile.getAbsolutePath());

        // Also render with correct VDP-style column VScroll for comparison
        BufferedImage wavyImg = renderFireCurtainToImage(lm, level, state, 320, 224);
        File wavyFile = new File("fire_curtain_WAVY_f160.png");
        ImageIO.write(wavyImg, "PNG", wavyFile);
        System.out.println("Wrote " + wavyFile.getAbsolutePath());
    }

    /**
     * Pixel-by-pixel VDP comparison: renders the fire curtain using VDP-style
     * per-pixel lookup (ground truth) vs our tile-based approach, and reports
     * any differences. This definitively identifies rendering bugs.
     */
    @Test
    public void diagnostic_vdpPixelComparison() throws Exception {
        teleportToMinibossArea();
        runRightUntilCameraSettles(3 * FPS);

        Sonic3kAIZEvents events = getAizEvents();
        events.setEventsFg5(true);

        // Step to frame 160
        for (int frame = 0; frame < 160; frame++) {
            fixture.stepIdleFrames(1);
        }

        LevelManager lm = LevelManager.getInstance();
        Level level = lm.getCurrentLevel();
        FireCurtainRenderState state = events.getFireCurtainRenderState(224);
        assertTrue("State should be active", state.active());

        int bgY = state.sourceWorldY();
        int sourceWorldX = state.sourceWorldX();
        int[] waveOffsets = state.columnWaveOffsetsPx();
        boolean fullyOpaque = state.fullyOpaqueToGameplay();
        int baseTop = Math.max(0, Math.min(224, 224 - state.coverHeightPx()));
        Palette firePalette = level.getPaletteCount() > 3 ? level.getPalette(3) : null;

        // Also compute the correct cycling X that the ROM uses
        int correctBgX = events.getFireTransitionBgX();
        System.out.println("=== VDP Pixel Comparison ===");
        System.out.println("sourceWorldX (fixed) = 0x" + Integer.toHexString(sourceWorldX));
        System.out.println("getFireTransitionBgX (cycling) = 0x" + Integer.toHexString(correctBgX));
        System.out.println("bgY = 0x" + Integer.toHexString(bgY));
        System.out.println("Wave offsets: ");
        StringBuilder waveStr = new StringBuilder();
        for (int i = 0; i < waveOffsets.length; i++) {
            waveStr.append(waveOffsets[i]).append(" ");
        }
        System.out.println("  " + waveStr);

        // === Image 1: VDP-style per-pixel rendering (ground truth) ===
        // For each screen pixel, compute the BG source pixel and look up color
        BufferedImage vdpImg = new BufferedImage(320, 224, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                vdpImg.setRGB(x, y, 0xFF000000);
            }
        }

        for (int screenX = 0; screenX < 320; screenX++) {
            int col = screenX / 16;
            int waveOffset = fullyOpaque ? 0 : (col < waveOffsets.length ? waveOffsets[col] : 0);
            int clipTop = Math.max(0, Math.min(224, baseTop - waveOffset));

            // Per-column VScroll for BG
            int columnVScroll = bgY + waveOffset;

            for (int screenY = clipTop; screenY < 224; screenY++) {
                // VDP pixel lookup: bgSourceY = columnVScroll + screenY
                int bgSourceY = columnVScroll + screenY;
                int bgSourceX = correctBgX + screenX;

                // Get tile descriptor at this BG position
                int descriptor = lm.getBackgroundTileDescriptorAtWorld(bgSourceX, bgSourceY);
                int patternIndex = descriptor & 0x7FF;
                if (patternIndex == 0) continue;

                boolean hFlip = (descriptor & 0x800) != 0;
                boolean vFlip = (descriptor & 0x1000) != 0;

                Pattern pattern = (patternIndex < level.getPatternCount())
                        ? level.getPattern(patternIndex) : null;
                if (pattern == null) continue;

                // Pixel within tile
                int tilePixelX = Math.floorMod(bgSourceX, 8);
                int tilePixelY = Math.floorMod(bgSourceY, 8);
                int readX = hFlip ? (7 - tilePixelX) : tilePixelX;
                int readY = vFlip ? (7 - tilePixelY) : tilePixelY;
                byte colorIdx = pattern.getPixel(readX, readY);
                if (colorIdx == 0) continue;

                int rgb;
                if (firePalette != null && colorIdx < firePalette.getColorCount()) {
                    Palette.Color c = firePalette.getColor(colorIdx);
                    rgb = 0xFF000000 | ((c.r & 0xFF) << 16) | ((c.g & 0xFF) << 8) | (c.b & 0xFF);
                } else {
                    rgb = 0xFFFF00FF;
                }
                vdpImg.setRGB(screenX, screenY, rgb);
            }
        }

        File vdpFile = new File("fire_curtain_VDP_PIXEL.png");
        ImageIO.write(vdpImg, "PNG", vdpFile);
        System.out.println("Wrote " + vdpFile.getAbsolutePath());

        // === Image 2: Our tile-based rendering (using cycling X for fair comparison) ===
        BufferedImage tileImg = renderFireCurtainToImageWithX(lm, level, state, 320, 224, correctBgX);
        File tileFile = new File("fire_curtain_TILE_BASED.png");
        ImageIO.write(tileImg, "PNG", tileFile);
        System.out.println("Wrote " + tileFile.getAbsolutePath());

        // === Compare ===
        int diffCount = 0;
        int totalPixels = 0;
        int maxDiffRow = -1;
        BufferedImage diffImg = new BufferedImage(320, 224, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 224; y++) {
            for (int x = 0; x < 320; x++) {
                diffImg.setRGB(x, y, 0xFF000000);
                int vdpRgb = vdpImg.getRGB(x, y);
                int tileRgb = tileImg.getRGB(x, y);
                if (vdpRgb != 0xFF000000 || tileRgb != 0xFF000000) {
                    totalPixels++;
                }
                if (vdpRgb != tileRgb) {
                    diffCount++;
                    diffImg.setRGB(x, y, 0xFFFF0000); // red = difference
                    if (maxDiffRow < y) maxDiffRow = y;
                }
            }
        }
        File diffFile = new File("fire_curtain_DIFF.png");
        ImageIO.write(diffImg, "PNG", diffFile);
        System.out.println("Wrote " + diffFile.getAbsolutePath());
        System.out.println("Pixel differences: " + diffCount + " / " + totalPixels
                + " (" + (totalPixels > 0 ? (100.0 * diffCount / totalPixels) : 0) + "%)");
        System.out.println("Max diff row: " + maxDiffRow);

        if (diffCount > 0) {
            // Dump first few differences
            int printed = 0;
            for (int y = 0; y < 224 && printed < 20; y++) {
                for (int x = 0; x < 320 && printed < 20; x++) {
                    int vdpRgb = vdpImg.getRGB(x, y);
                    int tileRgb = tileImg.getRGB(x, y);
                    if (vdpRgb != tileRgb) {
                        System.out.printf("  diff at (%3d,%3d): VDP=0x%08X  TILE=0x%08X%n",
                                x, y, vdpRgb, tileRgb);
                        printed++;
                    }
                }
            }
        }
    }

    /**
     * Same as renderFireCurtainToImage but with explicit sourceWorldX parameter.
     */
    private BufferedImage renderFireCurtainToImageWithX(LevelManager lm, Level level,
                                                         FireCurtainRenderState state,
                                                         int screenWidth, int screenHeight,
                                                         int sourceWorldX) {
        BufferedImage img = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < screenHeight; y++) {
            for (int x = 0; x < screenWidth; x++) {
                img.setRGB(x, y, 0xFF000000);
            }
        }

        int bgY = state.sourceWorldY();
        int baseTop = Math.max(0, Math.min(screenHeight, screenHeight - state.coverHeightPx()));
        int[] waveOffsets = state.columnWaveOffsetsPx();
        boolean fullyOpaque = state.fullyOpaqueToGameplay();
        Palette firePalette = level.getPaletteCount() > 3 ? level.getPalette(3) : null;

        int columnCount = 20;
        for (int col = 0; col < columnCount; col++) {
            int columnLeft = (col * screenWidth) / columnCount;
            int columnRight = ((col + 1) * screenWidth) / columnCount;
            int waveOffset = fullyOpaque ? 0 : (col < waveOffsets.length ? waveOffsets[col] : 0);
            int clipTop = Math.max(0, Math.min(screenHeight, baseTop - waveOffset));

            int columnVScroll = bgY + waveOffset;
            int bgAtBottom = columnVScroll + screenHeight;
            int bgAtClipTop = columnVScroll + clipTop;
            int bgRowBottom = Math.floorDiv(bgAtBottom - 1, 8);
            int bgRowTop = Math.floorDiv(bgAtClipTop - 8, 8);

            for (int bgRow = bgRowBottom; bgRow >= bgRowTop; bgRow--) {
                int bgTileY = bgRow * 8;
                int drawY = bgTileY - columnVScroll;
                if (drawY >= screenHeight || drawY + 8 <= clipTop) continue;

                for (int drawX = columnLeft; drawX < columnRight; drawX += 8) {
                    int srcX = sourceWorldX + drawX;
                    int descriptor = lm.getBackgroundTileDescriptorAtWorld(srcX, bgTileY);
                    int patternIndex = descriptor & 0x7FF;
                    if (patternIndex == 0) continue;

                    boolean hFlip = (descriptor & 0x800) != 0;
                    boolean vFlip = (descriptor & 0x1000) != 0;
                    Pattern pattern = (patternIndex < level.getPatternCount())
                            ? level.getPattern(patternIndex) : null;
                    if (pattern == null) continue;

                    for (int py = 0; py < 8; py++) {
                        for (int px = 0; px < 8; px++) {
                            int screenX = drawX + (hFlip ? 7 - px : px);
                            int screenY = drawY + (vFlip ? 7 - py : py);
                            if (screenX < 0 || screenX >= screenWidth || screenY < 0 || screenY >= screenHeight) continue;
                            if (screenY < clipTop) continue;
                            byte colorIdx = pattern.getPixel(px, py);
                            if (colorIdx == 0) continue;
                            int rgb;
                            if (firePalette != null && colorIdx < firePalette.getColorCount()) {
                                Palette.Color c = firePalette.getColor(colorIdx);
                                rgb = 0xFF000000 | ((c.r & 0xFF) << 16) | ((c.g & 0xFF) << 8) | (c.b & 0xFF);
                            } else {
                                rgb = 0xFFFF00FF;
                            }
                            img.setRGB(screenX, screenY, rgb);
                        }
                    }
                }
            }
        }
        return img;
    }

    /** Convert a Palette.Color back to a Mega Drive color word. */
    private static int toSegaWord(Palette.Color color) {
        if (color == null) {
            return 0;
        }
        int r3 = ((color.r & 0xFF) * 7 + 127) / 255;
        int g3 = ((color.g & 0xFF) * 7 + 127) / 255;
        int b3 = ((color.b & 0xFF) * 7 + 127) / 255;
        return ((b3 & 0x7) << 9) | ((g3 & 0x7) << 5) | ((r3 & 0x7) << 1);
    }
}
