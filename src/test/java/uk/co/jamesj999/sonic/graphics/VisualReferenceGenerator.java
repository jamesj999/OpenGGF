package uk.co.jamesj999.sonic.graphics;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.glu.GLU;
import uk.co.jamesj999.sonic.Engine;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.GameModuleRegistry;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;
import uk.co.jamesj999.sonic.sprites.playable.Sonic;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static uk.co.jamesj999.sonic.tests.RomTestUtils.ensureRomAvailable;

/**
 * CLI tool to generate baseline PNG screenshots for visual regression testing.
 *
 * Usage:
 * <pre>
 * mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.graphics.VisualReferenceGenerator" -q
 * </pre>
 */
public class VisualReferenceGenerator {

    private static final String REFERENCE_DIR = "src/test/resources/visual-reference";
    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 224;

    /**
     * Test positions for visual regression testing.
     * Uses checkpoint positions to capture interesting areas with objects/badniks.
     * Each entry: { zone, act, playerX, playerY, "filename" }
     */
    private static final Object[][] TEST_POSITIONS = {
            {0, 0, 3568, 170, "ehz_a1_cp1.png"},      // EHZ Act 1 checkpoint 1
            {0, 0, 1806, 697, "ehz_a1_tunnel.png"},   // EHZ Act 1 tunnel (priority system test)
            {0, 1, 4224, 200, "ehz_a2_cp1.png"},      // EHZ Act 2 checkpoint 1
            {1, 0, 4272, 1512, "cpz_a1_cp1.png"},     // CPZ Act 1 checkpoint 1
            {1, 1, 3136, 616, "cpz_a2_cp1.png"},      // CPZ Act 2 checkpoint 1
            {3, 0, 5944, 296, "cnz_a1_cp1.png"},      // CNZ Act 1 checkpoint 1
            {4, 0, 4160, 204, "htz_a1_cp1.png"},      // HTZ Act 1 checkpoint 1 (overlay tileset)
            {5, 0, 1400, 1128, "mcz_a1_cp1.png"},     // MCZ Act 1 checkpoint 1
    };

    private GLOffscreenAutoDrawable drawable;
    private GL2 gl;
    private GLU glu;
    private Rom rom;

    public static void main(String[] args) {
        VisualReferenceGenerator generator = new VisualReferenceGenerator();
        try {
            generator.initialize();
            generator.generateAll();
            System.out.println("Reference generation complete.");
        } catch (Exception e) {
            System.err.println("Error generating reference files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            generator.cleanup();
        }
    }

    /**
     * Initialize the offscreen OpenGL context and load the ROM.
     */
    public void initialize() throws IOException {
        System.out.println("Initializing offscreen OpenGL context...");

        // Disable debug view to avoid rendering debug markers
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.DEBUG_VIEW_ENABLED, false);

        // Create offscreen drawable
        GLProfile profile = GLProfile.get(GLProfile.GL2);
        GLCapabilities caps = new GLCapabilities(profile);
        caps.setOnscreen(false);
        caps.setDoubleBuffered(false); // Single-buffered for offscreen
        caps.setPBuffer(true);

        GLDrawableFactory factory = GLDrawableFactory.getFactory(profile);
        drawable = factory.createOffscreenAutoDrawable(
                factory.getDefaultDevice(),
                caps,
                null,
                SCREEN_WIDTH,
                SCREEN_HEIGHT
        );

        // Force context creation
        drawable.display();

        // Get GL context
        drawable.getContext().makeCurrent();
        gl = drawable.getGL().getGL2();
        glu = new GLU();

        // Initialize graphics manager with shader
        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        graphicsManager.init(gl, Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);

        // Set viewport
        gl.glViewport(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        // Set up projection matrix
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        glu.gluOrtho2D(0, SCREEN_WIDTH, 0, SCREEN_HEIGHT);

        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();

        // Enable blending for transparency
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);

        // Load ROM
        System.out.println("Loading ROM...");
        File romFile = ensureRomAvailable();
        rom = new Rom();
        if (!rom.open(romFile.getAbsolutePath())) {
            throw new IOException("Failed to open ROM file: " + romFile.getAbsolutePath());
        }

        // Register game module
        GameModuleRegistry.detectAndSetModule(rom);
        System.out.println("ROM loaded and game module registered.");

        // Create player sprite (required by LevelManager.loadZoneAndAct)
        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        AbstractPlayableSprite mainSprite = new Sonic(mainCode, (short) 100, (short) 624);
        SpriteManager.getInstance().addSprite(mainSprite);

        // Set up camera to follow player
        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(mainSprite);
        camera.updatePosition(true);
    }

    /**
     * Generate all reference images.
     */
    public void generateAll() throws IOException {
        Path refDir = Paths.get(REFERENCE_DIR);
        Files.createDirectories(refDir);

        System.out.println("Generating visual reference files to: " + refDir.toAbsolutePath());

        LevelManager levelManager = LevelManager.getInstance();
        Camera camera = Camera.getInstance();
        GraphicsManager graphicsManager = GraphicsManager.getInstance();

        // Update viewport dimensions in GraphicsManager
        graphicsManager.setViewport(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        SpriteManager spriteManager = SpriteManager.getInstance();

        for (Object[] position : TEST_POSITIONS) {
            int zone = (int) position[0];
            int act = (int) position[1];
            int playerX = (int) position[2];
            int playerY = (int) position[3];
            String filename = (String) position[4];

            System.out.println("Generating " + filename + " (zone=" + zone + ", act=" + act +
                    ", x=" + playerX + ", y=" + playerY + ")...");

            try {
                // Load level
                levelManager.loadZoneAndAct(zone, act);

                // Move player to checkpoint position
                var player = camera.getFocusedSprite();
                if (player != null) {
                    player.setX((short) playerX);
                    player.setY((short) playerY);
                }

                // Force camera to snap to player position (no smooth scrolling)
                camera.updatePosition(true);

                // Spawn objects within camera view
                levelManager.updateObjectPositions();

                // Update ring spawning within camera view
                if (levelManager.getRingManager() != null) {
                    levelManager.getRingManager().update(camera.getX(), player, 0);
                }

                // Update sprite animation to set correct mapping frame
                // (default frame 0 is intentionally empty; WAIT animation starts at frame 15)
                spriteManager.updateWithoutInput();

                // Clear screen with level's background color
                levelManager.setClearColor(gl);
                gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

                // Render level with sprites
                levelManager.drawWithSpritePriority(spriteManager);

                // Flush all queued commands
                graphicsManager.flush();

                // Ensure all GL commands are complete
                gl.glFinish();

                // Capture framebuffer
                BufferedImage screenshot = ScreenshotCapture.captureFramebuffer(gl, SCREEN_WIDTH, SCREEN_HEIGHT);

                // Save to file
                Path filePath = refDir.resolve(filename);
                ScreenshotCapture.savePNG(screenshot, filePath);

                System.out.println("  Written: " + filename);

            } catch (Exception e) {
                System.err.println("  WARNING: Failed to generate " + filename + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Cleanup OpenGL resources.
     */
    public void cleanup() {
        if (drawable != null) {
            try {
                drawable.getContext().release();
                drawable.destroy();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }

        // Reset graphics singleton for clean state
        GraphicsManager.resetInstance();
    }
}
