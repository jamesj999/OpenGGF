package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.SonicGame;

import java.io.IOException;

/**
 * Encapsulates the {@code @BeforeClass} level loading pattern used across
 * headless test classes. Loads a level once and stores the result for reuse.
 * <p>
 * Usage:
 * <pre>
 * {@literal @}BeforeClass
 * public static void loadLevel() throws Exception {
 *     shared = SharedLevel.load(SonicGame.SONIC_2, 0, 0);
 * }
 *
 * {@literal @}AfterClass
 * public static void cleanup() {
 *     if (shared != null) shared.dispose();
 * }
 * </pre>
 */
public final class SharedLevel {

    private final Level level;
    private final SonicGame game;
    private final int zone;
    private final int act;
    private final String mainCharCode;

    private SharedLevel(Level level, SonicGame game, int zone, int act, String mainCharCode) {
        this.level = level;
        this.game = game;
        this.zone = zone;
        this.act = act;
        this.mainCharCode = mainCharCode;
    }

    /**
     * Loads a level in headless mode and returns a {@code SharedLevel} holding
     * the loaded data.
     * <p>
     * This replicates the {@code @BeforeClass} boilerplate found in
     * {@code TestS2Ehz1Headless}, {@code TestHeadlessCNZ1LiftWallStick}, etc.:
     * <ol>
     *   <li>Init headless graphics</li>
     *   <li>Create a temporary Sonic sprite and register it</li>
     *   <li>Set camera focus and unfreeze</li>
     *   <li>Load the requested zone and act (runs full production path
     *       including camera bounds, level events, and player positioning
     *       via profile steps)</li>
     * </ol>
     *
     * @param game the game whose ROM is loaded (for documentation/querying)
     * @param zone zone index (e.g. 0 = EHZ for Sonic 2)
     * @param act  act index (0-based)
     * @return a new {@code SharedLevel} with the loaded level data
     */
    public static SharedLevel load(SonicGame game, int zone, int act) throws IOException {
        GraphicsManager.getInstance().initHeadless();

        SonicConfigurationService cs = SonicConfigurationService.getInstance();
        String mainCharCode = cs.getString(SonicConfiguration.MAIN_CHARACTER_CODE);

        Sonic temp = new Sonic(mainCharCode, (short) 0, (short) 0);
        GameServices.sprites().addSprite(temp);
        Camera camera = GameServices.camera();
        camera.setFocusedSprite(temp);
        camera.setFrozen(false);

        LevelManager lm = GameServices.level();
        lm.loadZoneAndAct(zone, act);

        // loadZoneAndAct → loadCurrentLevel now runs full profile including
        // InitCamera (bounds, snap, vertical wrap) and InitLevelEvents.
        // No manual camera setup needed.

        return new SharedLevel(lm.getCurrentLevel(), game, zone, act, mainCharCode);
    }

    /**
     * Cleans up state after the shared level is no longer needed.
     * Delegates to {@link TestEnvironment#resetAll()} for complete cleanup.
     */
    public void dispose() {
        TestEnvironment.resetAll();
    }

    /** Returns the loaded level, or {@code null} if loading failed. */
    public Level level() {
        return level;
    }

    /** Returns the game type that was used to load this level. */
    public SonicGame game() {
        return game;
    }

    /** Returns the zone index. */
    public int zone() {
        return zone;
    }

    /** Returns the act index. */
    public int act() {
        return act;
    }

    /** Returns the main character code used to create the player sprite. */
    public String mainCharCode() {
        return mainCharCode;
    }
}
