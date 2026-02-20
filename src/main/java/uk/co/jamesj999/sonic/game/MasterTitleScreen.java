package uk.co.jamesj999.sonic.game;

import uk.co.jamesj999.sonic.Control.InputHandler;
import uk.co.jamesj999.sonic.Engine;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.graphics.PngTextureLoader;
import uk.co.jamesj999.sonic.graphics.PixelFont;
import uk.co.jamesj999.sonic.graphics.TexturedQuadRenderer;

import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.*;

/**
 * Master title screen shown on startup for game selection.
 * Runs before any ROM is loaded, using its own PNG-based rendering path.
 */
public class MasterTitleScreen {

    private static final Logger LOGGER = Logger.getLogger(MasterTitleScreen.class.getName());
    private static final int SCREEN_W = 320;
    private static final int SCREEN_H = 224;

    // Short labels for the menu (fit within 320px when laid out horizontally)
    private static final String[] MENU_LABELS = { "Sonic 1", "Sonic 2", "Sonic 3K" };

    public enum GameEntry {
        SONIC_1("Sonic The Hedgehog", "s1", SonicConfiguration.SONIC_1_ROM),
        SONIC_2("Sonic The Hedgehog 2", "s2", SonicConfiguration.SONIC_2_ROM),
        SONIC_3K("Sonic 3 & Knuckles", "s3k", SonicConfiguration.SONIC_3K_ROM);

        public final String displayName;
        public final String gameId;
        public final SonicConfiguration romConfigKey;

        GameEntry(String displayName, String gameId, SonicConfiguration romConfigKey) {
            this.displayName = displayName;
            this.gameId = gameId;
            this.romConfigKey = romConfigKey;
        }
    }

    public enum State {
        INACTIVE, FADE_IN, ACTIVE, ERROR_DISPLAY, CONFIRMING, EXITING
    }

    // Cloud sprite for parallax animation
    private static class CloudSprite {
        int textureId;
        float x;
        float y;
        float speed;
        int width;
        int height;

        CloudSprite(int textureId, float x, float y, float speed, int width, int height) {
            this.textureId = textureId;
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.width = width;
            this.height = height;
        }

        void update() {
            x += speed;
            // Wrap when fully off the right edge
            if (x > SCREEN_W) {
                x = -width;
            }
            // Wrap when fully off the left edge (negative speed)
            if (x + width < 0) {
                x = SCREEN_W;
            }
        }
    }

    private State state = State.INACTIVE;
    private int selectedIndex = 1; // Default to Sonic 2
    private int frameCounter = 0;
    private int errorFrameCounter = 0;
    private static final int ERROR_DISPLAY_FRAMES = 180; // 3 seconds at 60fps

    private final boolean[] romAvailable = new boolean[GameEntry.values().length];

    // GL resources
    private TexturedQuadRenderer renderer;
    private PixelFont font;
    private int bgTextureId;
    private int solidWhiteTextureId; // 1x1 white texture for solid color overlays
    private int emblemTextureId;
    private int emblemWidth, emblemHeight;
    private int titleTextId;
    private int titleTextWidth, titleTextHeight;
    private int cloudLargeTextureId;
    private int cloudLargeWidth, cloudLargeHeight;
    private int cloudSmallTextureId;
    private int cloudSmallWidth, cloudSmallHeight;

    private final List<CloudSprite> clouds = new ArrayList<>();

    private boolean gameSelected = false;

    public void initialize() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();

        // Check ROM availability
        for (int i = 0; i < GameEntry.values().length; i++) {
            GameEntry entry = GameEntry.values()[i];
            String romPath = config.getString(entry.romConfigKey);
            romAvailable[i] = romPath != null && !romPath.isEmpty() && new File(romPath).exists();
        }

        try {
            // Initialize renderer and font
            renderer = new TexturedQuadRenderer();
            renderer.init();

            font = new PixelFont();
            font.init("pixel-font.png", renderer);

            // Load background
            bgTextureId = PngTextureLoader.loadTexture("titlescreen/bg.png");

            // Create 1x1 solid white texture for overlays
            solidWhiteTextureId = createSolidWhiteTexture();

            // Load emblem
            emblemTextureId = PngTextureLoader.loadTexture("titlescreen/title-emblem.png");
            emblemWidth = PngTextureLoader.getLastWidth();
            emblemHeight = PngTextureLoader.getLastHeight();

            // Load title text
            titleTextId = PngTextureLoader.loadTexture("titlescreen/titletext.png");
            titleTextWidth = PngTextureLoader.getLastWidth();
            titleTextHeight = PngTextureLoader.getLastHeight();

            // Load cloud textures
            cloudLargeTextureId = PngTextureLoader.loadTexture("titlescreen/cloud-l.png");
            cloudLargeWidth = PngTextureLoader.getLastWidth();
            cloudLargeHeight = PngTextureLoader.getLastHeight();

            cloudSmallTextureId = PngTextureLoader.loadTexture("titlescreen/cloud-s.png");
            cloudSmallWidth = PngTextureLoader.getLastWidth();
            cloudSmallHeight = PngTextureLoader.getLastHeight();

            // Create cloud sprites at various positions and speeds
            clouds.add(new CloudSprite(cloudLargeTextureId, 20, 95, 0.3f, cloudLargeWidth, cloudLargeHeight));
            clouds.add(new CloudSprite(cloudSmallTextureId, 180, 80, 0.5f, cloudSmallWidth, cloudSmallHeight));
            clouds.add(new CloudSprite(cloudLargeTextureId, 260, 110, 0.2f, cloudLargeWidth, cloudLargeHeight));
            clouds.add(new CloudSprite(cloudSmallTextureId, 80, 120, 0.4f, cloudSmallWidth, cloudSmallHeight));
            clouds.add(new CloudSprite(cloudSmallTextureId, -30, 90, 0.6f, cloudSmallWidth, cloudSmallHeight));

            state = State.FADE_IN;
            LOGGER.info("Master title screen initialized");

        } catch (IOException e) {
            LOGGER.severe("Failed to initialize master title screen: " + e.getMessage());
            throw new RuntimeException("Failed to initialize master title screen", e);
        }
    }

    /**
     * Creates a 1x1 opaque white texture for use as a solid-color overlay base.
     */
    private static int createSolidWhiteTexture() {
        ByteBuffer pixel = MemoryUtil.memAlloc(4);
        pixel.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).flip();
        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, 0);
        MemoryUtil.memFree(pixel);
        return texId;
    }

    /**
     * Updates the title screen state. Called once per frame from GameLoop.
     */
    public void update(InputHandler inputHandler) {
        frameCounter++;

        // Update cloud animation
        for (CloudSprite cloud : clouds) {
            cloud.update();
        }

        if (state == State.FADE_IN) {
            // Transition to active after a brief delay
            if (frameCounter > 10) {
                state = State.ACTIVE;
            }
            return;
        }

        if (state == State.ERROR_DISPLAY) {
            errorFrameCounter++;
            if (errorFrameCounter >= ERROR_DISPLAY_FRAMES) {
                state = State.ACTIVE;
                errorFrameCounter = 0;
            }
            return;
        }

        if (state == State.CONFIRMING || state == State.EXITING) {
            return; // Waiting for fade
        }

        if (state != State.ACTIVE) {
            return;
        }

        // Handle input
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        int leftKey = config.getInt(SonicConfiguration.LEFT);
        int rightKey = config.getInt(SonicConfiguration.RIGHT);
        int jumpKey = config.getInt(SonicConfiguration.JUMP);

        if (inputHandler.isKeyPressed(leftKey)) {
            selectedIndex = Math.max(0, selectedIndex - 1);
            playNavigateSound();
        }
        if (inputHandler.isKeyPressed(rightKey)) {
            selectedIndex = Math.min(GameEntry.values().length - 1, selectedIndex + 1);
            playNavigateSound();
        }

        // Confirm with Jump or Enter
        if (inputHandler.isKeyPressed(jumpKey) ||
            inputHandler.isKeyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER)) {
            if (!romAvailable[selectedIndex]) {
                state = State.ERROR_DISPLAY;
                errorFrameCounter = 0;
                playErrorSound();
            } else {
                state = State.CONFIRMING;
                playConfirmSound();
                gameSelected = true;
            }
        }
    }

    /**
     * Draws the title screen. Called once per frame from Engine.draw().
     */
    public void draw() {
        if (renderer == null) return;

        // Set projection matrix from Engine
        Engine engine = Engine.getInstance();
        if (engine != null) {
            renderer.setProjectionMatrix(engine.getProjectionMatrixBuffer());
        }

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // 1. Background (full screen)
        renderer.drawTexture(bgTextureId, 0, 0, SCREEN_W, SCREEN_H);

        // 2. Clouds (behind emblem)
        for (CloudSprite cloud : clouds) {
            float glY = SCREEN_H - cloud.y - cloud.height;
            renderer.drawTexture(cloud.textureId, cloud.x, glY, cloud.width, cloud.height,
                1f, 1f, 1f, 0.85f);
        }

        // 3. Compute title text position (needed for emblem placement)
        float titleScale = 0.35f;
        int scaledTitleW = (int)(titleTextWidth * titleScale);
        int scaledTitleH = (int)(titleTextHeight * titleScale);
        float titleX = (SCREEN_W - scaledTitleW) / 2f;
        float titleGlY = SCREEN_H - 10 - scaledTitleH; // 10px from top

        // 4. Emblem (centered, below title text) - drawn before title so title appears in front
        float emblemScale = 0.7f;
        int scaledEmblemW = (int)(emblemWidth * emblemScale);
        int scaledEmblemH = (int)(emblemHeight * emblemScale);
        float emblemX = (SCREEN_W - scaledEmblemW) / 2f;
        float emblemGlY = titleGlY - scaledEmblemH + 28;
        renderer.drawTexture(emblemTextureId, emblemX, emblemGlY, scaledEmblemW, scaledEmblemH);

        // 5. Title text "OpenGGF" (centered, top) - drawn after emblem so it appears in front
        renderer.drawTexture(titleTextId, titleX, titleGlY, scaledTitleW, scaledTitleH);

        // 5. Game selection menu at bottom
        drawGameMenu();

        // 6. Navigation hints
        font.drawTextCentered("< >  Select    Enter  Confirm", SCREEN_W, 210,
            0.6f, 0.6f, 0.7f, 0.8f);

        // 7. Error message overlay
        if (state == State.ERROR_DISPLAY) {
            // Semi-transparent black overlay using solid white texture tinted black
            renderer.drawTexture(solidWhiteTextureId, 0, 0, SCREEN_W, SCREEN_H, 0f, 0f, 0f, 0.5f);

            // Error text
            GameEntry entry = GameEntry.values()[selectedIndex];
            font.drawTextCentered("ROM NOT FOUND", SCREEN_W, 90, 1f, 0.3f, 0.3f, 1f);
            font.drawTextCentered(entry.displayName, SCREEN_W, 105, 0.8f, 0.8f, 0.8f, 1f);

            SonicConfigurationService config = SonicConfigurationService.getInstance();
            String romFile = config.getString(entry.romConfigKey);
            if (romFile == null || romFile.isEmpty()) {
                romFile = "(not configured)";
            } else if (romFile.length() > 35) {
                romFile = "..." + romFile.substring(romFile.length() - 32);
            }
            font.drawTextCentered(romFile, SCREEN_W, 125, 0.5f, 0.5f, 0.5f, 0.8f);
        }
    }

    private void drawGameMenu() {
        GameEntry[] entries = GameEntry.values();
        int totalWidth = 0;
        int[] widths = new int[entries.length];
        int spacing = 20;

        for (int i = 0; i < entries.length; i++) {
            widths[i] = font.measureWidth(MENU_LABELS[i]);
            totalWidth += widths[i];
        }
        totalWidth += spacing * (entries.length - 1);

        int startX = (SCREEN_W - totalWidth) / 2;
        int menuY = 190;
        int cursorX = startX;

        for (int i = 0; i < entries.length; i++) {
            float r, g, b, a;

            if (i == selectedIndex) {
                // Pulsing brightness for selected item
                float pulse = 0.5f + 0.5f * (float) Math.sin(frameCounter * 0.05);
                float brightness = 1.0f + 0.3f * pulse;
                r = brightness;
                g = brightness;
                b = brightness;
                a = 1.0f;
            } else if (!romAvailable[i]) {
                // Greyed out for unavailable
                r = 0.4f;
                g = 0.4f;
                b = 0.4f;
                a = 0.7f;
            } else {
                // Normal unselected
                r = 0.8f;
                g = 0.8f;
                b = 0.8f;
                a = 1.0f;
            }

            font.drawText(MENU_LABELS[i], cursorX, menuY, r, g, b, a);
            cursorX += widths[i] + spacing;
        }
    }

    // Audio stubs
    private void playNavigateSound() { /* TODO: ROM-independent SFX */ }
    private void playConfirmSound()  { /* TODO: ROM-independent SFX */ }
    private void playErrorSound()    { /* TODO: ROM-independent SFX */ }

    /**
     * Returns true when the user has selected a game and confirmed.
     */
    public boolean isGameSelected() {
        return gameSelected;
    }

    /**
     * Returns the game ID ("s1", "s2", "s3k") of the selected entry.
     */
    public String getSelectedGameId() {
        return GameEntry.values()[selectedIndex].gameId;
    }

    /**
     * Cleans up all GL resources.
     */
    public void cleanup() {
        if (font != null) font.cleanup();
        PngTextureLoader.deleteTexture(bgTextureId);
        PngTextureLoader.deleteTexture(solidWhiteTextureId);
        PngTextureLoader.deleteTexture(emblemTextureId);
        PngTextureLoader.deleteTexture(titleTextId);
        PngTextureLoader.deleteTexture(cloudLargeTextureId);
        PngTextureLoader.deleteTexture(cloudSmallTextureId);
        if (renderer != null) renderer.cleanup();
        state = State.INACTIVE;
        LOGGER.info("Master title screen cleaned up");
    }
}
