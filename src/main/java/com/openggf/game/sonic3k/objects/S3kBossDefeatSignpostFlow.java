package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Orchestrates the boss-defeat-to-signpost flow.
 *
 * <p>ROM: Obj_EndSignControl (sonic3k.asm) — invisible state machine that
 * manages the transition from boss defeat (explosions, music fade) through
 * signpost spawn, results screen, and act transition.
 *
 * <p>4-phase state machine:
 * <ol>
 *   <li><b>WAIT_FADE</b> — counts down 0x77 frames while music fades</li>
 *   <li><b>SPAWN_SIGNPOST</b> — clears boss flag, spawns signpost, runs cleanup</li>
 *   <li><b>AWAIT_RESULTS</b> — polls until results screen clears endOfLevelActive</li>
 *   <li><b>AWAIT_ACT_TRANSITION</b> — polls until endOfLevelFlag is set, then self-destructs</li>
 * </ol>
 */
public class S3kBossDefeatSignpostFlow extends AbstractObjectInstance {
    private static final Logger LOG = Logger.getLogger(S3kBossDefeatSignpostFlow.class.getName());

    private enum Phase { WAIT_FADE, SPAWN_SIGNPOST, AWAIT_RESULTS, AWAIT_ACT_TRANSITION }

    /** ROM: Obj_EndSignControl timer = $77 (119 frames). */
    private static final int FADE_TIMER = 0x77;

    /** Y offset above camera top for initial signpost spawn position. */
    private static final int SIGNPOST_Y_OFFSET = 0x20;

    private Phase phase;
    private int timer;
    private final int signpostX;
    private final int apparentAct;
    private final Runnable zoneCleanupCallback;
    private boolean initialized;

    /**
     * Creates the defeat-to-signpost flow orchestrator.
     *
     * @param signpostX           world X position where the signpost should appear
     * @param apparentAct         ROM's Apparent_act value (0 = act 1, 1 = act 2 display).
     *                            For mid-act bosses like AIZ1 miniboss, this is 0 even though
     *                            the engine may have reloaded act 2 resources.
     * @param zoneCleanupCallback called after spawning the signpost (e.g. palette restore)
     */
    public S3kBossDefeatSignpostFlow(int signpostX, int apparentAct, Runnable zoneCleanupCallback) {
        super(null, "S3kBossDefeatSignpostFlow");
        this.signpostX = signpostX;
        this.apparentAct = apparentAct;
        this.zoneCleanupCallback = zoneCleanupCallback;
        this.phase = Phase.WAIT_FADE;
        this.timer = FADE_TIMER;
    }

    @Override
    public int getX() {
        return signpostX;
    }

    @Override
    public int getY() {
        return 0;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        // Signal that the end-of-level sequence is active
        services().gameState().setEndOfLevelActive(true);
        LOG.fine("S3K defeat flow started — WAIT_FADE, timer=" + timer);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        ensureInitialized();
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (isDestroyed()) {
            return;
        }

        switch (phase) {
            case WAIT_FADE -> updateWaitFade();
            case SPAWN_SIGNPOST -> updateSpawnSignpost();
            case AWAIT_RESULTS -> updateAwaitResults();
            case AWAIT_ACT_TRANSITION -> updateAwaitActTransition();
        }
    }

    // =========================================================================
    // Phase 1: WAIT_FADE
    // =========================================================================

    private void updateWaitFade() {
        timer--;
        if (timer <= 0) {
            phase = Phase.SPAWN_SIGNPOST;
            LOG.fine("S3K defeat flow WAIT_FADE -> SPAWN_SIGNPOST");
        }
    }

    // =========================================================================
    // Phase 2: SPAWN_SIGNPOST
    // =========================================================================

    private void updateSpawnSignpost() {
        // Clear boss flag and boss ID so level events resume normal behavior
        try {
            Sonic3kAIZEvents events = ((Sonic3kLevelEventManager) services().levelEventProvider()).getAizEvents();
            if (events != null) {
                events.setBossFlag(false);
            }
        } catch (Exception e) {
            LOG.fine("Could not clear boss flag: " + e.getMessage());
        }
        services().gameState().setCurrentBossId(0);

        // Spawn signpost above camera
        S3kSignpostInstance signpost = new S3kSignpostInstance(signpostX, apparentAct);
        spawnDynamicObject(signpost);
        LOG.fine("S3K defeat flow spawned signpost at X=" + signpostX);

        // Run zone-specific cleanup (e.g. palette restore)
        if (zoneCleanupCallback != null) {
            try {
                zoneCleanupCallback.run();
            } catch (Exception e) {
                LOG.fine("Zone cleanup callback failed: " + e.getMessage());
            }
        }

        phase = Phase.AWAIT_RESULTS;
        LOG.fine("S3K defeat flow SPAWN_SIGNPOST -> AWAIT_RESULTS");
    }

    // =========================================================================
    // Phase 3: AWAIT_RESULTS
    // =========================================================================

    private void updateAwaitResults() {
        if (!services().gameState().isEndOfLevelActive()) {
            phase = Phase.AWAIT_ACT_TRANSITION;
            LOG.fine("S3K defeat flow AWAIT_RESULTS -> AWAIT_ACT_TRANSITION");
        }
    }

    // =========================================================================
    // Phase 4: AWAIT_ACT_TRANSITION
    // =========================================================================

    private void updateAwaitActTransition() {
        if (services().gameState().isEndOfLevelFlag()) {
            setDestroyed(true);
            LOG.fine("S3K defeat flow complete — destroyed");
        }
    }

    // =========================================================================
    // Rendering (invisible orchestrator)
    // =========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // No visual rendering — this is an invisible orchestrator object.
    }
}
