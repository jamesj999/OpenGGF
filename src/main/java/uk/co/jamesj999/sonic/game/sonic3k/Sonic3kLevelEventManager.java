package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.game.LevelEventProvider;

import java.util.logging.Logger;

/**
 * S3K level events scaffold.
 *
 * <p>Phase 1 implements bootstrap selection for AIZ1 intro-skip parity.
 * Dynamic zone events are intentionally deferred and will be added incrementally.
 */
public class Sonic3kLevelEventManager implements LevelEventProvider {
    private static final Logger LOG = Logger.getLogger(Sonic3kLevelEventManager.class.getName());
    private static Sonic3kLevelEventManager instance;

    private int currentZone = -1;
    private int currentAct = -1;
    private Sonic3kLoadBootstrap bootstrap = Sonic3kLoadBootstrap.NONE;

    private Sonic3kLevelEventManager() {
    }

    @Override
    public void initLevel(int zone, int act) {
        currentZone = zone;
        currentAct = act;
        bootstrap = Sonic3kBootstrapResolver.resolve(zone, act);
        if (bootstrap.isAiz1GameplayAfterIntro()) {
            LOG.info("S3K bootstrap: using AIZ1 gameplay-after-intro profile (intro stub skipped).");
        }
    }

    @Override
    public void update() {
        // ROM-accurate dynamic S3K events (AIZ intro script, resize handlers, etc.)
        // are pending implementation.
    }

    public Sonic3kLoadBootstrap getBootstrap() {
        return bootstrap;
    }

    public int getCurrentZone() {
        return currentZone;
    }

    public int getCurrentAct() {
        return currentAct;
    }

    public static synchronized Sonic3kLevelEventManager getInstance() {
        if (instance == null) {
            instance = new Sonic3kLevelEventManager();
        }
        return instance;
    }
}
