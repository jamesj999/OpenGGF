package uk.co.jamesj999.sonic.game.sonic1.credits;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Music;
import uk.co.jamesj999.sonic.game.sonic1.titlescreen.Sonic1TitleScreenDataLoader;
import uk.co.jamesj999.sonic.game.sonic1.titlescreen.Sonic1TitleScreenManager;
import uk.co.jamesj999.sonic.graphics.FadeManager;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Orchestrates the Sonic 1 ending credits sequence.
 * <p>
 * State machine with two alternating phases:
 * <ol>
 *   <li>TEXT phase: credit text displayed on black screen for 120 frames</li>
 *   <li>DEMO phase: pre-recorded demo playback on a zone (540/510 frames)</li>
 * </ol>
 * <p>
 * Credits 0-7 have both text and demo. Credit 8 ("PRESENTED BY SEGA")
 * is text-only, after which the sequence is finished.
 * <p>
 * Reference: docs/s1disasm/sonic.asm lines 4090-4267 (GM_Credits / EndingDemoLoad)
 */
public class Sonic1CreditsManager {
    private static final Logger LOGGER = Logger.getLogger(Sonic1CreditsManager.class.getName());

    /** Credits state machine phases. */
    public enum State {
        TEXT_FADE_IN,
        TEXT_DISPLAY,
        TEXT_FADE_OUT,
        DEMO_LOADING,
        DEMO_FADE_IN,
        DEMO_PLAYING,
        DEMO_FADING_OUT,
        FINISHED
    }

    private State state;
    private int creditsNum;
    private int timer;
    private boolean scrollFrozen;

    // Demo input playback
    private DemoInputPlayer demoInputPlayer;

    // Credit text rendering
    private final Sonic1CreditsTextRenderer textRenderer = new Sonic1CreditsTextRenderer();

    // Transition flags read by GameLoop
    private boolean requestDemoLoad;
    private boolean requestTextReturn;
    private boolean requestFinished;

    /**
     * Initializes the credits sequence. Plays credits music and starts
     * the first credit text fade-in.
     */
    public void initialize() {
        creditsNum = 0;
        scrollFrozen = false;
        demoInputPlayer = null;
        requestDemoLoad = false;
        requestTextReturn = false;
        requestFinished = false;

        // Initialize text renderer from title screen data
        Sonic1TitleScreenDataLoader dataLoader = Sonic1TitleScreenManager.getInstance().getDataLoader();
        textRenderer.initialize(dataLoader);

        // Play credits music (ROM: move.w #MusID_Credits,d0; jsr PlaySound)
        AudioManager.getInstance().playMusic(Sonic1Music.CREDITS.id);

        // Start with fade from black to reveal first credit text
        state = State.TEXT_FADE_IN;
        FadeManager.getInstance().startFadeFromBlack(() -> {
            state = State.TEXT_DISPLAY;
            timer = Sonic1CreditsDemoData.TEXT_DISPLAY_FRAMES;
        });

        LOGGER.info("Credits sequence initialized, starting credit 0");
    }

    /**
     * Updates the credits state machine. Called once per frame.
     */
    public void update() {
        switch (state) {
            case TEXT_FADE_IN -> {
                // Waiting for FadeManager callback to advance to TEXT_DISPLAY
            }
            case TEXT_DISPLAY -> updateTextDisplay();
            case TEXT_FADE_OUT -> {
                // Waiting for FadeManager callback
            }
            case DEMO_LOADING -> {
                // Waiting for GameLoop to load the demo zone
            }
            case DEMO_FADE_IN -> {
                // Waiting for FadeManager callback to advance to DEMO_PLAYING
            }
            case DEMO_PLAYING -> updateDemoPlaying();
            case DEMO_FADING_OUT -> {
                // Waiting for FadeManager callback; scroll is frozen
            }
            case FINISHED -> { }
        }
    }

    /**
     * TEXT_DISPLAY: Hold credit text on screen for the configured duration.
     */
    private void updateTextDisplay() {
        timer--;
        if (timer <= 0) {
            if (creditsNum >= Sonic1CreditsDemoData.DEMO_CREDITS) {
                // Credit 8 ("PRESENTED BY SEGA"): no demo, go to finished
                state = State.TEXT_FADE_OUT;
                FadeManager.getInstance().startFadeToBlack(() -> {
                    state = State.FINISHED;
                    requestFinished = true;
                });
            } else {
                // Credits 0-7: fade to black, then load demo zone
                state = State.TEXT_FADE_OUT;
                FadeManager.getInstance().startFadeToBlack(() -> {
                    state = State.DEMO_LOADING;
                    requestDemoLoad = true;
                });
            }
        }
    }

    /**
     * DEMO_PLAYING: Run demo input, count down timer.
     */
    private void updateDemoPlaying() {
        if (demoInputPlayer != null) {
            demoInputPlayer.advanceFrame();
        }

        timer--;
        if (timer <= 0 || (demoInputPlayer != null && demoInputPlayer.isComplete())) {
            // Demo time expired — start fadeout
            scrollFrozen = true;
            state = State.DEMO_FADING_OUT;
            FadeManager.getInstance().startFadeToBlack(() -> {
                scrollFrozen = false;
                creditsNum++;
                if (creditsNum >= Sonic1CreditsDemoData.TOTAL_CREDITS) {
                    state = State.FINISHED;
                    requestFinished = true;
                } else {
                    // Return to text phase for next credit
                    requestTextReturn = true;
                }
            });
        }
    }

    /**
     * Called by GameLoop after the demo zone has been loaded.
     * Initializes demo input, positions the player, and starts fade-in.
     */
    public void onDemoZoneLoaded() {
        // Load demo input data from ROM
        int addr = Sonic1CreditsDemoData.DEMO_DATA_ADDR[creditsNum];
        int size = Sonic1CreditsDemoData.DEMO_DATA_SIZE[creditsNum];
        byte[] demoData = new byte[size];
        try {
            var rom = GameServices.rom().getRom();
            for (int i = 0; i < size; i++) {
                demoData[i] = rom.readByte(addr + i);
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to read demo data for credit " + creditsNum + ": " + e.getMessage());
            demoData = new byte[] { 0, 0 }; // Terminator
        }
        demoInputPlayer = new DemoInputPlayer(demoData);

        // Set demo timer
        timer = Sonic1CreditsDemoData.DEMO_TIMER[creditsNum];

        // Start fade from black to reveal the demo zone
        state = State.DEMO_FADE_IN;
        FadeManager.getInstance().startFadeFromBlack(() -> {
            state = State.DEMO_PLAYING;
        });

        LOGGER.info("Demo zone loaded for credit " + creditsNum +
                ", timer=" + timer + " frames");
    }

    /**
     * Called by GameLoop when returning from demo to text phase.
     * Starts the next credit text with fade-in.
     */
    public void onReturnToText() {
        demoInputPlayer = null;

        // Start fade from black to reveal credit text
        state = State.TEXT_FADE_IN;
        FadeManager.getInstance().startFadeFromBlack(() -> {
            state = State.TEXT_DISPLAY;
            timer = Sonic1CreditsDemoData.TEXT_DISPLAY_FRAMES;
        });

        LOGGER.info("Showing credit text " + creditsNum);
    }

    /**
     * Draws the credit text for the current credit number.
     * Called by Engine during CREDITS_TEXT mode.
     */
    public void drawCreditText() {
        textRenderer.draw(creditsNum);
    }

    // ========================================================================
    // Transition flags (consumed by GameLoop)
    // ========================================================================

    public boolean consumeDemoLoadRequest() {
        boolean r = requestDemoLoad;
        requestDemoLoad = false;
        return r;
    }

    public boolean consumeTextReturnRequest() {
        boolean r = requestTextReturn;
        requestTextReturn = false;
        return r;
    }

    public boolean consumeFinishedRequest() {
        boolean r = requestFinished;
        requestFinished = false;
        return r;
    }

    // ========================================================================
    // Accessors for GameLoop
    // ========================================================================

    public State getState() {
        return state;
    }

    public int getCreditsNum() {
        return creditsNum;
    }

    /**
     * Returns the current demo input mask for the player.
     * GameLoop should apply this to the player during CREDITS_DEMO mode.
     */
    public int getDemoInputMask() {
        if (demoInputPlayer != null) {
            return demoInputPlayer.getInputMask();
        }
        return 0;
    }

    /**
     * True during demo fadeout: camera/scroll should freeze but physics continues.
     */
    public boolean isScrollFrozen() {
        return scrollFrozen;
    }

    /**
     * Returns the zone index for the current credit's demo.
     */
    public int getDemoZone() {
        if (creditsNum < Sonic1CreditsDemoData.DEMO_CREDITS) {
            return Sonic1CreditsDemoData.DEMO_ZONE[creditsNum];
        }
        return 0;
    }

    /**
     * Returns the act index for the current credit's demo.
     */
    public int getDemoAct() {
        if (creditsNum < Sonic1CreditsDemoData.DEMO_CREDITS) {
            return Sonic1CreditsDemoData.DEMO_ACT[creditsNum];
        }
        return 0;
    }

    /**
     * Returns the player start X for the current credit's demo.
     */
    public int getDemoStartX() {
        if (creditsNum < Sonic1CreditsDemoData.DEMO_CREDITS) {
            return Sonic1CreditsDemoData.START_X[creditsNum];
        }
        return 0;
    }

    /**
     * Returns the player start Y for the current credit's demo.
     */
    public int getDemoStartY() {
        if (creditsNum < Sonic1CreditsDemoData.DEMO_CREDITS) {
            return Sonic1CreditsDemoData.START_Y[creditsNum];
        }
        return 0;
    }

    /**
     * Returns true if the current credit demo is the SLZ demo (credit 4),
     * which requires special lamppost state setup.
     */
    public boolean isSlzDemo() {
        return creditsNum == 4;
    }
}
