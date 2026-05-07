package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.events.S3kAizEventWriteSupport;
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

    public enum CleanupAction {
        NONE,
        RESTORE_AIZ_FIRE_PALETTE
    }

    /** ROM: Obj_EndSignControl timer = $77 (119 frames). */
    private static final int FADE_TIMER = 0x77;

    /** Y offset above camera top for initial signpost spawn position. */
    private static final int SIGNPOST_Y_OFFSET = 0x20;

    private Phase phase;
    private int timer;
    private final int signpostX;
    private final int apparentAct;
    private final CleanupAction cleanupAction;
    private boolean initialized;

    /**
     * Creates the defeat-to-signpost flow orchestrator.
     *
     * @param signpostX           world X position where the signpost should appear
     * @param apparentAct         ROM's Apparent_act value (0 = act 1, 1 = act 2 display).
     *                            For mid-act bosses like AIZ1 miniboss, this is 0 even though
     *                            the engine may have reloaded act 2 resources.
     * @param cleanupAction action to run after spawning the signpost (e.g. palette restore)
     */
    public S3kBossDefeatSignpostFlow(int signpostX, int apparentAct, CleanupAction cleanupAction) {
        super(null, "S3kBossDefeatSignpostFlow");
        this.signpostX = signpostX;
        this.apparentAct = apparentAct;
        this.cleanupAction = cleanupAction == null ? CleanupAction.NONE : cleanupAction;
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
            // ROM: boss_saved_mus played when timer expires (sonic3k.asm:180484-180486).
            // Restore the zone music before the signpost spawns.
            resumeZoneMusic();
            phase = Phase.SPAWN_SIGNPOST;
            LOG.fine("S3K defeat flow WAIT_FADE -> SPAWN_SIGNPOST");
        }
    }

    /**
     * Plays the current zone's act music (ROM: boss_saved_mus).
     * Each boss saves the level music ID on spawn; after defeat the same
     * track is restored before the signpost appears.
     */
    private void resumeZoneMusic() {
        try {
            int zone = services().romZoneId();
            int act = services().currentAct();
            var zoneRegistry = services().gameModule().getZoneRegistry();
            int musicId = zoneRegistry.getMusicId(zone, act);
            if (musicId >= 0) {
                services().playMusic(musicId);
            }
        } catch (Exception e) {
            LOG.fine("Could not resume zone music: " + e.getMessage());
        }
    }

    // =========================================================================
    // Phase 2: SPAWN_SIGNPOST
    // =========================================================================

    private void updateSpawnSignpost() {
        // Clear boss flag and boss ID so level events resume normal behavior
        S3kAizEventWriteSupport.setBossFlag(services(), false);
        services().gameState().setCurrentBossId(0);

        // Spawn signpost above camera
        S3kSignpostInstance signpost = new S3kSignpostInstance(signpostX, apparentAct);
        spawnDynamicObject(signpost);
        LOG.fine("S3K defeat flow spawned signpost at X=" + signpostX);

        runCleanupAction();

        phase = Phase.AWAIT_RESULTS;
        LOG.fine("S3K defeat flow SPAWN_SIGNPOST -> AWAIT_RESULTS");
    }

    private void runCleanupAction() {
        try {
            switch (cleanupAction) {
                case NONE -> {
                    // No zone-specific cleanup.
                }
                case RESTORE_AIZ_FIRE_PALETTE -> restoreAizFirePalette();
            }
        } catch (Exception e) {
            LOG.fine("Zone cleanup action failed: " + e.getMessage());
        }
    }

    private void restoreAizFirePalette() throws Exception {
        // AfterBoss_AIZ2: restore fire palette to palette line 1.
        // ROM: lea (Pal_AIZFire).l,a1 / jsr (PalLoad_Line1).l.
        byte[] palData = services().rom().readBytes(Sonic3kConstants.PAL_AIZ_FIRE_ADDR, 32);
        S3kPaletteWriteSupport.applyLine(
                services().paletteOwnershipRegistryOrNull(),
                services().currentLevel(),
                services().graphicsManager(),
                S3kPaletteOwners.AIZ_MINIBOSS,
                S3kPaletteOwners.PRIORITY_CUTSCENE_OVERRIDE,
                1,
                palData);
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
