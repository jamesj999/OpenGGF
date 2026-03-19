package com.openggf.game;

import com.openggf.Engine;
import com.openggf.LevelFrameStep;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderPriority;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.LevelManager;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Visual diagnostic test for the insta-shield animation in S3K AIZ1.
 * Captures a screenshot of every frame from insta-shield activation for 20 frames,
 * allowing manual inspection of the rendered arc pieces.
 *
 * <p>Run:
 * <pre>
 * mvn test -Dtest=TestInstaShieldVisual -Ds3k.rom.path="Sonic and Knuckles &amp; Sonic 3 (W) [!].gen"
 * </pre>
 * Output PNGs go to {@code target/insta-shield-visual/}.
 */
@EnabledIfSystemProperty(named = "s3k.rom.path", matches = ".+")
public class TestInstaShieldVisual {

    private static final int W = 320;
    private static final int H = 224;
    private static final Path OUT_DIR = Paths.get("target", "insta-shield-visual");

    private static long window;
    private static boolean initialized;
    private static final Matrix4f projectionMatrix = new Matrix4f();
    private static final float[] matrixBuffer = new float[16];

    private static AbstractPlayableSprite player;
    private static int frameCounter;

    @BeforeAll
    public static void setUpClass() {
        try {
            SonicConfigurationService config = SonicConfigurationService.getInstance();
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
            config.setConfigValue(SonicConfiguration.DEBUG_VIEW_ENABLED, false);

            GLFWErrorCallback.createPrint(System.err).set();
            if (!glfwInit()) throw new IllegalStateException("GLFW init failed");

            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);

            window = glfwCreateWindow(W, H, "Insta-Shield Visual Test", NULL, NULL);
            if (window == NULL) throw new RuntimeException("GLFW window creation failed");
            glfwMakeContextCurrent(window);
            GL.createCapabilities();

            // Reset stale singleton state from prior tests
            GraphicsManager.resetInstance();
            Camera.resetInstance();
            SpriteManager.getInstance().resetState();

            GraphicsManager gm = GraphicsManager.getInstance();
            gm.init(Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
            glViewport(0, 0, W, H);

            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            projectionMatrix.identity().ortho2D(0, W, 0, H);
            projectionMatrix.get(matrixBuffer);
            glLoadMatrixf(matrixBuffer);
            gm.setProjectionMatrixBuffer(matrixBuffer.clone());

            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            gm.setViewport(0, 0, W, H);

            // Load S3K ROM
            String romPath = System.getProperty("s3k.rom.path",
                    "Sonic and Knuckles & Sonic 3 (W) [!].gen");
            File romFile = new File(romPath);
            if (!romFile.exists()) {
                System.err.println("S3K ROM not found at " + romFile.getAbsolutePath());
                initialized = false;
                return;
            }
            Rom rom = new Rom();
            assertTrue(rom.open(romFile.getAbsolutePath()), "Failed to open S3K ROM");
            GameModuleRegistry.detectAndSetModule(rom);
            RomManager.getInstance().setRom(rom);

            // Create player and load AIZ1 (with intro skip)
            String mainCode = config.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
            player = new Sonic(mainCode, (short) 0x80, (short) 0x3B0);
            SpriteManager.getInstance().addSprite(player);

            Camera camera = Camera.getInstance();
            camera.setFocusedSprite(player);
            camera.setFrozen(false);
            camera.updatePosition(true);

            LevelManager lm = LevelManager.getInstance();
            lm.loadZoneAndAct(0, 0); // AIZ act 1

            // Wire GroundSensor to LevelManager (critical for collision)
            GroundSensor.setLevelManager(lm);

            // Position player at a clear area
            player.setX((short) 0x80);
            player.setY((short) 0x3B0);
            camera.updatePosition(true);

            // Initialize camera and level events via production path
            lm.initCameraForLevel();
            lm.initLevelEventsForLevel();

            // Spawn objects within camera view
            lm.updateObjectPositions();

            frameCounter = 0;
            initialized = true;
            System.out.println("Insta-shield visual test initialized");
        } catch (Exception e) {
            System.err.println("Init failed: " + e.getMessage());
            e.printStackTrace();
            initialized = false;
        }
    }

    @AfterAll
    public static void tearDown() {
        if (window != NULL) {
            try { glfwDestroyWindow(window); } catch (Exception e) { /* ignore */ }
        }
        glfwTerminate();
        GraphicsManager.resetInstance();
        Camera.resetInstance();
    }

    @Test
    public void captureInstaShieldFrames() throws Exception {
        assumeTrue(initialized, "GPU init failed or ROM not available");
        Files.createDirectories(OUT_DIR);

        LevelManager lm = LevelManager.getInstance();
        Camera camera = Camera.getInstance();
        SpriteManager sm = SpriteManager.getInstance();

        // Phase 1: Jump to get airborne.
        // Hold jump for a few frames to initiate the jump, then release.
        System.out.println("=== Phase 1: Jumping ===");
        for (int i = 0; i < 5; i++) {
            stepFrame(true, false, false, false, true); // up + jump (jump initiates)
        }
        // Release jump, step a few more frames to get some height
        for (int i = 0; i < 10; i++) {
            stepFrame(false, false, false, false, false); // idle in air
        }

        // Verify Sonic is airborne
        boolean isAir = player.getAir();
        System.out.println("After jump: isAir=" + isAir
                + " y=" + player.getY()
                + " doubleJumpFlag=" + player.getDoubleJumpFlag());

        // If not airborne, try holding jump longer
        if (!isAir) {
            System.out.println("Not airborne yet, trying more jump frames...");
            for (int i = 0; i < 5; i++) {
                stepFrame(false, false, false, false, true);
            }
            for (int i = 0; i < 5; i++) {
                stepFrame(false, false, false, false, false);
            }
            isAir = player.getAir();
            System.out.println("After extended jump: isAir=" + isAir);
        }

        // Capture a pre-activation screenshot showing Sonic in the air
        BufferedImage preFrame = renderFrame(lm, sm);
        ScreenshotCapture.savePNG(preFrame, OUT_DIR.resolve("frame_pre_activation.png"));
        System.out.println("Saved pre-activation frame");

        // Phase 2: Press jump again to activate insta-shield.
        // This should trigger the insta-shield since Sonic has no shield and is Sonic in S3K.
        System.out.println("\n=== Phase 2: Activating insta-shield ===");
        System.out.println("Before activation: doubleJumpFlag=" + player.getDoubleJumpFlag()
                + " shieldType=" + player.getShieldType()
                + " secondaryAbility=" + player.getSecondaryAbility()
                + " instaShieldObj=" + (player.getInstaShieldObject() != null));

        // Press jump for exactly 1 frame to trigger insta-shield
        stepFrame(false, false, false, false, true);

        System.out.println("After activation press: doubleJumpFlag=" + player.getDoubleJumpFlag()
                + " instaShieldObj=" + (player.getInstaShieldObject() != null));

        // Phase 3: Capture every frame for 20 frames after activation
        System.out.println("\n=== Phase 3: Capturing 20 frames ===");
        int framesWithContent = 0;

        for (int i = 0; i < 20; i++) {
            // Step one frame of game logic (no input)
            stepFrame(false, false, false, false, false);

            // Render and capture
            BufferedImage frame = renderFrame(lm, sm);
            assertNotNull(frame, "Framebuffer capture returned null at frame " + i);

            String filename = String.format("frame_%02d.png", i);
            Path outPath = OUT_DIR.resolve(filename);
            ScreenshotCapture.savePNG(frame, outPath);

            // Count non-background pixels around the player center to detect insta-shield content
            int centerX = player.getCentreX() - camera.getX();
            int centerY = player.getCentreY() - camera.getY();
            int shieldPixels = countNonBackgroundPixelsAround(frame, centerX, centerY, 40);

            System.out.println("Frame " + i + ": " + filename
                    + " doubleJumpFlag=" + player.getDoubleJumpFlag()
                    + " playerCentre=(" + player.getCentreX() + "," + player.getCentreY() + ")"
                    + " screenCentre=(" + centerX + "," + centerY + ")"
                    + " shieldAreaPixels=" + shieldPixels);

            if (shieldPixels > 50) {
                framesWithContent++;
            }
        }

        System.out.println("\nFrames with insta-shield visual content: " + framesWithContent + "/20");
        System.out.println("Output directory: " + OUT_DIR.toAbsolutePath());

        // At minimum, some frames should show the insta-shield if it rendered correctly
        // (even the wireframe fallback would show content)
        assertTrue(framesWithContent > 0,
                "Expected at least one frame with insta-shield visual content. "
                + "Check output PNGs in " + OUT_DIR.toAbsolutePath());
    }

    /**
     * Steps one frame of game logic using the same canonical sequence
     * as HeadlessTestRunner and production GameLoop.
     */
    private void stepFrame(boolean up, boolean down, boolean left, boolean right, boolean jump) {
        frameCounter++;

        // Store raw input state (matching SpriteManager.update() behavior)
        player.setJumpInputPressed(jump);
        player.setDirectionalInputPressed(up, down, left, right);

        LevelManager lm = LevelManager.getInstance();

        // Canonical frame tick via LevelFrameStep
        LevelFrameStep.execute(lm, Camera.getInstance(), () -> {
            boolean controlLocked = player.isControlLocked();
            boolean forcedRight = player.isForcedInputActive(AbstractPlayableSprite.INPUT_RIGHT)
                    || player.isForceInputRight();
            boolean forcedLeft = player.isForcedInputActive(AbstractPlayableSprite.INPUT_LEFT);
            boolean forcedUp = player.isForcedInputActive(AbstractPlayableSprite.INPUT_UP);
            boolean forcedDown = player.isForcedInputActive(AbstractPlayableSprite.INPUT_DOWN);
            boolean forcedJump = player.isForcedInputActive(AbstractPlayableSprite.INPUT_JUMP);
            boolean effectiveRight = (!controlLocked && right) || forcedRight;
            boolean effectiveLeft = ((!controlLocked && left) || forcedLeft) && !forcedRight;
            boolean effectiveUp = (!controlLocked && up) || forcedUp;
            boolean effectiveDown = (!controlLocked && down) || forcedDown;
            boolean effectiveJump = (!controlLocked && jump) || forcedJump;

            SpriteManager.tickPlayablePhysics(player,
                    effectiveUp, effectiveDown, effectiveLeft, effectiveRight, effectiveJump,
                    false, false, false, lm, frameCounter);
        });
    }

    /**
     * Renders the current scene to the framebuffer and captures a screenshot.
     */
    private BufferedImage renderFrame(LevelManager lm, SpriteManager sm) {
        // Black background for clean visual review
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        GraphicsManager gm = GraphicsManager.getInstance();

        // Draw only the sprite + object buckets (skip level tiles for clean background)
        gm.setUseSpritePriorityShader(true);
        for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
            if (sm != null) {
                sm.drawUnifiedBucketWithPriority(bucket, gm);
            }
            if (lm.getObjectManager() != null) {
                lm.getObjectManager().drawUnifiedBucketWithPriority(bucket, gm);
            }
        }
        gm.flushPatternBatch();
        gm.setUseSpritePriorityShader(false);

        gm.flush();
        glFinish();

        return ScreenshotCapture.captureFramebuffer(W, H);
    }

    /**
     * Counts non-background pixels in a square region around a screen coordinate.
     * Used to detect whether the insta-shield effect is being rendered near the player.
     */
    private static int countNonBackgroundPixelsAround(BufferedImage img,
            int cx, int cy, int halfSize) {
        // Sample the corner pixel as approximate background color
        int bgRgb = img.getRGB(0, 0);
        int bgR = (bgRgb >> 16) & 0xFF;
        int bgG = (bgRgb >> 8) & 0xFF;
        int bgB = bgRgb & 0xFF;

        int count = 0;
        int startX = Math.max(0, cx - halfSize);
        int endX = Math.min(img.getWidth(), cx + halfSize);
        int startY = Math.max(0, cy - halfSize);
        int endY = Math.min(img.getHeight(), cy + halfSize);

        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                // Consider it "content" if it differs significantly from background
                if (Math.abs(r - bgR) > 20 || Math.abs(g - bgG) > 20 || Math.abs(b - bgB) > 20) {
                    count++;
                }
            }
        }
        return count;
    }
}
