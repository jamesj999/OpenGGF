package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic2.objects.AbstractResultsScreen;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;

import java.util.List;
import java.util.logging.Logger;

/**
 * S3K results screen -- displays "{CHARACTER} GOT THROUGH ACT {N}" with
 * time bonus and ring bonus tally after the signpost lands.
 *
 * <p>ROM: Obj_LevelResults (sonic3k.asm lines 62499-63003).
 *
 * <p>Key differences from S2:
 * <ul>
 *   <li>No fade-to-black -- signals flags and lets level events handle transitions</li>
 *   <li>360-frame pre-tally delay (S2 uses 180)</li>
 *   <li>90-frame post-tally wait (S2 uses 180)</li>
 *   <li>No perfect bonus</li>
 *   <li>Act 1 exit shows act 2 title card; act 2 exit sets End_of_level_flag</li>
 * </ul>
 */
public class S3kResultsScreenObjectInstance extends AbstractResultsScreen {
    private static final Logger LOG = Logger.getLogger(S3kResultsScreenObjectInstance.class.getName());

    // ROM-accurate timing
    private static final int S3K_PRE_TALLY_DELAY = 360;  // 6*60 frames (ROM line 62580)
    private static final int S3K_WAIT_DURATION = 90;      // ROM line 62676
    private static final int MUSIC_TRIGGER_FRAME = 71;    // 360 - 289 = 71 (ROM line 62626)

    // Time bonus table (ROM lines 62910-62918)
    private static final int[] TIME_BONUSES = {5000, 5000, 1000, 500, 400, 300, 100, 10};
    private static final int SPECIAL_TIME_BONUS = 10000;  // 9:59 override (ROM line 62559)
    private static final int MAX_TIMER_SECONDS = 599;     // 9:59 = 9*60 + 59

    // Slide speeds (ROM lines 62847, 62836)
    private static final int SLIDE_IN_SPEED = 16;   // moveq #$10,d1
    private static final int SLIDE_OUT_SPEED = 32;  // move.w #-$20,d0

    // Pattern caching
    private static final int PATTERN_BASE = 0x60000;  // High ID to avoid conflicts

    // State
    private final PlayerCharacter character;
    private final int act;  // 0-indexed: 0=Act 1, 1=Act 2

    // Tally values
    private int timeBonus;
    private int ringBonus;
    private int totalBonusCountUp;

    // Art
    private Pattern[] combinedPatterns;
    private List<SpriteMappingFrame> mappingFrames;
    private boolean artLoaded;
    private boolean artCached;

    // Music flag
    private boolean musicPlayed;

    public S3kResultsScreenObjectInstance(PlayerCharacter character, int act) {
        super("S3kResults");
        this.character = character;
        this.act = act;

        // Calculate bonuses from current game state (ROM lines 62550-62578)
        calculateBonuses();

        // Fade out current music immediately (ROM line 62513)
        fadeOutMusic();

        // Load art (stub -- Task 4-5 will add implementation)
        loadArt();

        // Create elements (stub -- Task 4-5 will add implementation)
        createElements();

        LOG.fine(() -> String.format("S3K results init: character=%s act=%d timeBonus=%d ringBonus=%d",
                character, act, timeBonus, ringBonus));
    }

    // ---- Bonus calculation ----

    private void calculateBonuses() {
        var levelGamestate = LevelManager.getInstance().getLevelGamestate();
        int elapsedSeconds = (levelGamestate != null) ? levelGamestate.getElapsedSeconds() : 0;

        // Pause the timer (ROM line 62550)
        if (levelGamestate != null) {
            levelGamestate.pauseTimer();
        }

        // Special case: 9:59 -> 10000 (ROM lines 62557-62559)
        if (elapsedSeconds >= MAX_TIMER_SECONDS) {
            timeBonus = SPECIAL_TIME_BONUS;
        } else {
            int index = Math.min(elapsedSeconds / 30, TIME_BONUSES.length - 1);
            timeBonus = TIME_BONUSES[index];
        }

        // Ring bonus: rings x 10 (ROM lines 62576-62578)
        int ringCount = (levelGamestate != null) ? levelGamestate.getRings() : 0;
        ringBonus = ringCount * 10;

        totalBonusCountUp = 0;
    }

    // ---- Timing overrides ----

    @Override
    protected int getSlideDuration() {
        return 0;  // Children handle their own sliding; skip SLIDE_IN entirely
    }

    @Override
    protected int getPreTallyDelay() {
        return S3K_PRE_TALLY_DELAY;
    }

    @Override
    protected int getWaitDuration() {
        return S3K_WAIT_DURATION;
    }

    // ---- Pre-tally delay with music trigger ----

    @Override
    protected void updatePreTallyDelay() {
        // Trigger music at frame 71 of the 360-frame countdown
        // ROM: checks counter == 289 (360 - 71) at line 62626
        if (!musicPlayed && stateTimer == MUSIC_TRIGGER_FRAME) {
            musicPlayed = true;
            try {
                AudioManager.getInstance().playMusic(Sonic3kMusic.ACT_CLEAR.id);
            } catch (Exception e) {
                // Ignore audio errors
            }
        }

        super.updatePreTallyDelay();
    }

    // ---- Tally logic ----

    @Override
    protected TallyResult performTallyStep() {
        int totalIncrement = 0;

        // Decrement time bonus by 10 (ROM line 62639)
        int[] timeResult = decrementBonus(timeBonus);
        timeBonus = timeResult[0];
        totalIncrement += timeResult[1];

        // Decrement ring bonus by 10 (ROM line 62645)
        int[] ringResult = decrementBonus(ringBonus);
        ringBonus = ringResult[0];
        totalIncrement += ringResult[1];

        // Track running total (ROM line 62648)
        totalBonusCountUp += totalIncrement;

        boolean anyRemaining = (timeBonus > 0 || ringBonus > 0);
        return tallyResult(anyRemaining, totalIncrement);
    }

    @Override
    protected void updateTally() {
        TallyResult result = performTallyStep();

        if (result.totalIncrement() > 0) {
            GameServices.gameState().addScore(result.totalIncrement());
        }

        // ROM uses global frame counter for tick timing (line 62652-62654)
        // Level_frame_counter & 3 == 0
        // We use the frameCounter field (set by update()) as the global frame source
        if (result.anyRemaining()) {
            if ((this.frameCounter & 3) == 0) {
                playTickSound();
            }
        }

        if (!result.anyRemaining()) {
            playTallyEndSound();
            state = STATE_WAIT;
            stateTimer = 0;
        }
    }

    // ---- Audio overrides ----

    @Override
    protected void playTickSound() {
        try {
            AudioManager.getInstance().playSfx(Sonic3kSfx.SWITCH.id);
        } catch (Exception e) {
            // Ignore audio errors
        }
    }

    @Override
    protected void playTallyEndSound() {
        try {
            AudioManager.getInstance().playSfx(Sonic3kSfx.REGISTER.id);
        } catch (Exception e) {
            // Ignore audio errors
        }
    }

    private void fadeOutMusic() {
        try {
            AudioManager.getInstance().fadeOutMusic();
        } catch (Exception e) {
            // Ignore audio errors
        }
    }

    // ---- Exit behavior ----

    @Override
    protected void onExitReady() {
        int zone = LevelManager.getInstance().getRomZoneId();

        // Act 2, Sky Sanctuary ($A), or LRZ boss ($16): set End_of_level_flag
        // ROM lines 62694-62705
        boolean isAct2OrSpecial = (act != 0) || (zone == 0x0A) || (zone == 0x16);

        GameServices.gameState().setEndOfLevelActive(false);

        if (isAct2OrSpecial) {
            GameServices.gameState().setEndOfLevelFlag(true);
        } else {
            // Act 1: show act 2 title card (ROM lines 62708-62720)
            // ROM sets Apparent_act = 1 -- in our engine the title card handles this visually.
            // The level data continues seamlessly (S3K acts share the same level).

            // Show act 2 title card (except SOZ zone $8 and DEZ zone $B)
            // ROM lines 62713-62720
            boolean skipTitleCard = (zone == 0x08) || (zone == 0x0B);
            if (!skipTitleCard) {
                Sonic3kTitleCardManager.getInstance().initializeInLevel(zone, 1);
            }
        }

        setDestroyed(true);
        LOG.fine(() -> String.format("S3K results exit: zone=%X act=%d isAct2OrSpecial=%b",
                zone, act, isAct2OrSpecial));
    }

    // ---- Persistence ----

    @Override
    public boolean isPersistent() {
        return true;  // Survives screen boundary checks
    }

    // ---- Rendering (stub -- Task 4-5) ----

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Stub: rendering will be added in Task 4-5
    }

    // ---- Art loading (stub -- Task 4-5) ----

    private void loadArt() {
        // Stub: art loading will be added in Task 4-5
    }

    // ---- Element creation (stub -- Task 4-5) ----

    private void createElements() {
        // Stub: element creation will be added in Task 4-5
    }
}
