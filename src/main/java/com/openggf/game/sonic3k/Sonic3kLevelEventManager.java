package com.openggf.game.sonic3k;

import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.GameRuntime;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.AizObjectEventBridge;
import com.openggf.game.sonic3k.events.CnzObjectEventBridge;
import com.openggf.game.sonic3k.events.HczObjectEventBridge;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.events.Sonic3kHCZEvents;
import com.openggf.game.sonic3k.events.Sonic3kMGZEvents;
import com.openggf.game.sonic3k.events.S3kTransitionEventBridge;
import com.openggf.game.sonic3k.runtime.AizZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.CnzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.HczZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.game.sonic3k.features.HCZWaterTunnelHandler;
import com.openggf.game.sonic3k.objects.AizHollowTreeObjectInstance;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesHcz2Instance;
import com.openggf.game.sonic3k.objects.HCZConveyorBeltObjectInstance;
import com.openggf.camera.Camera;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.logging.Logger;

/**
 * Sonic 3&K implementation of dynamic level events.
 * ROM equivalent: ScreenEvents (sonic3k.asm:102228)
 *
 * S3K uses dual foreground/background event routines (Events_routine_fg
 * and Events_routine_bg) with a stride of 4 per state transition.
 * Each zone has two parallel event handlers:
 * <ul>
 *   <li>ScreenEvent (FG) - terrain changes, object spawning, camera boundaries</li>
 *   <li>BackgroundEvent (BG) - parallax, deformation, water level, boss arenas</li>
 * </ul>
 *
 * S3K also uses Boss_flag to gate FG events during boss fights, and
 * branches on Player_mode for character-specific event paths (Sonic/Tails
 * vs Knuckles take different routes through most zones).
 *
 * Phase 1 implements bootstrap selection for AIZ1 intro-skip parity.
 * Zone event handlers will be added incrementally per zone.
 */
public class Sonic3kLevelEventManager extends AbstractLevelEventManager
        implements AizObjectEventBridge, CnzObjectEventBridge, HczObjectEventBridge, S3kTransitionEventBridge {
    private static final Logger LOG = Logger.getLogger(Sonic3kLevelEventManager.class.getName());
    private static final int PACHINKO_TOP_EXIT_Y = -0x20;

    private Sonic3kLoadBootstrap bootstrap = Sonic3kLoadBootstrap.NORMAL;
    private Sonic3kAIZEvents aizEvents;
    private Sonic3kCNZEvents cnzEvents;
    private Sonic3kHCZEvents hczEvents;
    private Sonic3kMGZEvents mgzEvents;

    // Tracks whether the intro-fall forced animation is active on each player.
    // Cleared per-player when they land (air → ground transition).
    private boolean introFallActiveOnPlayer;
    private boolean introFallActiveOnSidekick;

    // Set by HCZ Act 1 transition: after the seamless reload to Act 2, the
    // whirlpool descent cutscene should play. Consumed on the first onUpdate()
    // after the transition completes.
    private boolean hczPendingPostTransitionCutscene;

    // Set by MGZ Act 1 transition: after the seamless reload to Act 2, the
    // player (still in signpost victory pose) must be released so they can
    // resume playing. Consumed on the first onUpdate() in MGZ Act 2.
    private boolean mgzPendingPostTransitionRelease;


    public Sonic3kLevelEventManager() {
        super();
    }

    // =========================================================================
    // AbstractLevelEventManager contract
    // =========================================================================

    @Override
    protected int getRoutineStride() {
        return 4;
    }

    @Override
    protected int getEventDataFgSize() {
        return 6; // Events_fg_0..5
    }

    @Override
    protected int getEventDataBgSize() {
        return 24; // Events_bg[24]
    }

    @Override
    public PlayerCharacter getPlayerCharacter() {
        return ActiveGameplayTeamResolver.resolvePlayerCharacter(GameServices.configuration());
    }

    @Override
    protected void onInitLevel(int zone, int act) {
        bootstrap = Sonic3kBootstrapResolver.resolve(zone, act);
        introFallActiveOnPlayer = false;
        introFallActiveOnSidekick = false;

        // ROM: Level_FromSavedGame skips intro when Last_star_post_hit != 0.
        // This covers both special stage return (big ring) and bonus stage return.
        if (bootstrap.mode() == Sonic3kLoadBootstrap.Mode.INTRO
                && (GameServices.level().hasBigRingReturn()
                    || GameServices.level().isBonusStageReturn())) {
            bootstrap = new Sonic3kLoadBootstrap(Sonic3kLoadBootstrap.Mode.SKIP_INTRO, null);
            LOG.info("S3K bootstrap: skipping intro (returning from stage)");
        }

        if (bootstrap.isSkipIntro()) {
            LOG.info("S3K bootstrap: skipping intro for zone " + zone + " act " + act);
        }

        // Create zone-specific event handlers after bootstrap resolution
        if (zone == Sonic3kZoneIds.ZONE_AIZ) {
            aizEvents = new Sonic3kAIZEvents(bootstrap);
            aizEvents.init(act);
        } else {
            aizEvents = null;
        }
        if (zone == Sonic3kZoneIds.ZONE_CNZ) {
            cnzEvents = new Sonic3kCNZEvents();
            cnzEvents.init(act);
        } else {
            cnzEvents = null;
        }
        if (zone == Sonic3kZoneIds.ZONE_HCZ) {
            hczEvents = new Sonic3kHCZEvents();
            hczEvents.init(act);
        } else {
            hczEvents = null;
        }
        if (zone == Sonic3kZoneIds.ZONE_MGZ) {
            mgzEvents = new Sonic3kMGZEvents();
            mgzEvents.init(act);
        } else {
            mgzEvents = null;
        }

        // Install typed zone runtime state into the registry.
        // Uses getActiveRuntime() to avoid the mode-checking side effects of
        // getCurrent() which can destroy the runtime during level loading.
        installZoneRuntimeState(zone, act);
    }

    private void installZoneRuntimeState(int zone, int act) {
        GameRuntime runtime = RuntimeManager.getActiveRuntime();
        if (runtime == null) {
            LOG.fine("Skipping S3K zone runtime registration because no active runtime is installed");
            return;
        }
        ZoneRuntimeRegistry registry = runtime.getZoneRuntimeRegistry();
        PlayerCharacter playerCharacter = getPlayerCharacter();
        if (zone == Sonic3kZoneIds.ZONE_AIZ && aizEvents != null) {
            registry.install(new AizZoneRuntimeState(act, playerCharacter, aizEvents));
        } else if (zone == Sonic3kZoneIds.ZONE_CNZ && cnzEvents != null) {
            registry.install(new CnzZoneRuntimeState(act, playerCharacter, cnzEvents));
        } else if (zone == Sonic3kZoneIds.ZONE_HCZ && hczEvents != null) {
            registry.install(new HczZoneRuntimeState(act, playerCharacter, hczEvents));
        } else {
            registry.clear();
        }
    }

    @Override
    protected void onUpdate() {
        handleBonusStageTopExit();
        // After HCZ seamless transition to Act 2: start the whirlpool descent
        // cutscene that spirals Sonic down into the Act 2 starting area.
        if (hczPendingPostTransitionCutscene && hczEvents != null) {
            hczPendingPostTransitionCutscene = false;
            hczEvents.startPostTransitionCutscene();
        }

        // Clear intro-fall forced animation when players land
        updateIntroFallState();

        // ROM: ScreenEvents dispatches to both FG and BG handlers each frame.
        // Boss_flag gates FG events during boss fights.
        if (aizEvents != null && currentZone == Sonic3kZoneIds.ZONE_AIZ) {
            aizEvents.update(currentAct, frameCounter);
        }
        if (cnzEvents != null && currentZone == Sonic3kZoneIds.ZONE_CNZ) {
            cnzEvents.update(currentAct, frameCounter);
        }
        if (hczEvents != null && currentZone == Sonic3kZoneIds.ZONE_HCZ) {
            hczEvents.update(currentAct, frameCounter);
        }
        if (mgzEvents != null && currentZone == Sonic3kZoneIds.ZONE_MGZ) {
            mgzEvents.update(currentAct, frameCounter);
        }
        releasePendingMgzPostTransition();
        syncSidekickBoundsToCamera();
    }

    /**
     * Keep CPU sidekick level bounds aligned with S3K's dynamic camera bounds.
     * Unlike S2, S3K currently has no zone-specific sidekick bound overrides, so
     * mirroring the live camera bounds each frame prevents stale respawn/death
     * limits after resize scripts move the arena.
     */
    private void syncSidekickBoundsToCamera() {
        Camera camera = GameServices.cameraOrNull();
        if (camera == null) {
            return;
        }
        int minX = camera.getMinX();
        int maxX = camera.getMaxX();
        int maxY = Math.max(camera.getMaxY(), camera.getMaxYTarget());
        for (AbstractPlayableSprite sidekick : GameServices.sprites().getSidekicks()) {
            if (sidekick.getCpuController() != null) {
                sidekick.getCpuController().setLevelBounds(minX, maxX, maxY);
            }
        }
    }

    private void handleBonusStageTopExit() {
        if (currentZone != Sonic3kZoneIds.ZONE_GLOWING_SPHERE) {
            return;
        }
        AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
        if (player == null || player.getCentreY() >= PACHINKO_TOP_EXIT_Y) {
            return;
        }
        var provider = GameServices.bonusStageOrNull();
        if (provider != null) {
            provider.requestExit();
        }
    }

    /**
     * Clears the forced intro-fall animation on each player once they land.
     * In the ROM, normal movement code overwrites obAnim on landing; here the
     * profile-based animation system needs the forced override cleared so it
     * can resolve the correct ground animation.
     */
    private void updateIntroFallState() {
        if (introFallActiveOnPlayer) {
            AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
            if (player != null && !player.getAir()) {
                player.setForcedAnimationId(-1);
                introFallActiveOnPlayer = false;
            }
        }
        if (introFallActiveOnSidekick) {
            boolean anySidekickStillFalling = false;
            for (AbstractPlayableSprite sidekick : GameServices.sprites().getSidekicks()) {
                if (sidekick.getAir()) {
                    anySidekickStillFalling = true;
                } else if (sidekick.getForcedAnimationId() >= 0) {
                    sidekick.setForcedAnimationId(-1);
                }
            }
            if (!anySidekickStillFalling) {
                introFallActiveOnSidekick = false;
            }
        }
    }

    // =========================================================================
    // SpawnLevelMainSprites — zone-specific player state
    // =========================================================================

    /**
     * ROM equivalent: SpawnLevelMainSprites zone-specific branches
     * (sonic3k.asm:8132–8178).
     *
     * <p>Sets animation, airborne flag, and jumping state for zones where
     * the player starts mid-air (falling intros). Called after both the main
     * player and sidekicks have been spawned.
     *
     * <p>Zones handled:
     * <ul>
     *   <li>HCZ1 ($0100): Sonic/Tails anim $1B (tumble), Knuckles anim $21 (glide drop)</li>
     *   <li>MGZ1 ($0200): anim $1B, airborne (loc_68A6)</li>
     *   <li>LRZ1 ($0900) Knuckles: anim $1B, airborne (loc_68A6)</li>
     *   <li>SSZ ($1600): anim $1B, airborne (loc_68A6)</li>
     * </ul>
     */
    public void applyZonePlayerState() {
        if (currentZone == Sonic3kZoneIds.ZONE_HCZ && currentAct == 0) {
            applyHcz1IntroState();
        }
        // ROM: sonic3k.asm loc_68A6 — simple falling intro (anim $1B + airborne).
        // Applied to MGZ1, SSZ, and LRZ1 (non-Knuckles only).
        if (currentZone == Sonic3kZoneIds.ZONE_MGZ && currentAct == 0) {
            applySimpleFallingIntro("MGZ1");
        }
        // TODO: LRZ1 non-Knuckles, SSZ falling intros (same loc_68A6 path)
    }

    /**
     * HCZ Act 1 intro: all characters start falling from near the top of the
     * level (Y=$0020). ROM: sonic3k.asm loc_6834–loc_6886.
     *
     * <ul>
     *   <li>Sonic/Tails: animation $1B (HURT_FALL — tumble/flail), airborne</li>
     *   <li>Knuckles: animation $21 (GLIDE_DROP), anim_frame 1, airborne</li>
     *   <li>Tails-alone (Player_mode 2): additionally sets jumping=true</li>
     *   <li>Player 2 (sidekick Tails): animation $1B, airborne, jumping=true</li>
     * </ul>
     */
    private void applyHcz1IntroState() {
        AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
        if (player == null) {
            return;
        }

        PlayerCharacter character = getPlayerCharacter();

        if (character == PlayerCharacter.KNUCKLES) {
            // ROM: move.w #($21<<8)|$21,anim(a1)  — anim=$21, prev_anim=$21
            //      move.b #1,anim_frame(a1)
            player.setForcedAnimationId(Sonic3kAnimationIds.GLIDE_DROP);
        } else {
            // ROM: move.b #$1B,anim(a1)
            player.setForcedAnimationId(Sonic3kAnimationIds.HURT_FALL);
        }
        player.setAir(true);
        introFallActiveOnPlayer = true;

        // ROM: Tails alone (Player_mode == 2) gets jumping=1 so flight is available
        if (character == PlayerCharacter.TAILS_ALONE) {
            player.setJumping(true);
        }

        // Sidekick (Player 2): anim $1B, airborne, jumping=1
        // ROM: sonic3k.asm:8153–8158
        for (AbstractPlayableSprite sidekick : GameServices.sprites().getSidekicks()) {
            sidekick.setForcedAnimationId(Sonic3kAnimationIds.HURT_FALL);
            sidekick.setAir(true);
            sidekick.setJumping(true);
            introFallActiveOnSidekick = true;
        }

        LOG.info("HCZ1 intro: set falling state on player(s)");
    }

    /**
     * Simple falling intro shared by MGZ1, SSZ, and LRZ1 (non-Knuckles).
     * ROM: sonic3k.asm loc_68A6.
     *
     * <p>Sets animation $1B (HURT_FALL) and airborne on both players.
     * Unlike HCZ1, no Knuckles-specific animation or jumping flag.
     */
    private void applySimpleFallingIntro(String zoneName) {
        AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
        if (player == null) {
            return;
        }

        player.setForcedAnimationId(Sonic3kAnimationIds.HURT_FALL);
        player.setAir(true);
        introFallActiveOnPlayer = true;

        for (AbstractPlayableSprite sidekick : GameServices.sprites().getSidekicks()) {
            sidekick.setForcedAnimationId(Sonic3kAnimationIds.HURT_FALL);
            sidekick.setAir(true);
            introFallActiveOnSidekick = true;
        }

        LOG.info(zoneName + " intro: set falling state on player(s)");
    }

    // =========================================================================
    // S3K-specific accessors
    // =========================================================================

    public Sonic3kLoadBootstrap getBootstrap() {
        return bootstrap;
    }

    /** Returns the AIZ zone events handler, or null if not in AIZ. */
    public Sonic3kAIZEvents getAizEvents() {
        return aizEvents;
    }

    @Override
    public void setBossFlag(boolean value) {
        if (aizEvents != null) {
            aizEvents.setBossFlag(value);
        }
        if (cnzEvents != null) {
            cnzEvents.setBossFlag(value);
        }
    }

    @Override
    public void setEventsFg5(boolean value) {
        if (aizEvents != null) {
            aizEvents.setEventsFg5(value);
        }
        if (cnzEvents != null) {
            cnzEvents.setEventsFg5(value);
        }
    }

    @Override
    public void triggerScreenShake(int frames) {
        if (aizEvents != null) {
            aizEvents.triggerScreenShake(frames);
        }
    }

    @Override
    public void onBattleshipComplete() {
        if (aizEvents != null) {
            aizEvents.onBattleshipComplete();
        }
    }

    @Override
    public void onBossSmallComplete() {
        if (aizEvents != null) {
            aizEvents.onBossSmallComplete();
        }
    }

    @Override
    public boolean isFireTransitionActive() {
        return aizEvents != null && aizEvents.isFireTransitionActive();
    }

    public boolean isEventsFg5() {
        return aizEvents != null && aizEvents.isEventsFg5();
    }

    @Override
    public boolean isAct2TransitionRequested() {
        if (aizEvents != null) {
            return aizEvents.isAct2TransitionRequested();
        }
        return cnzEvents != null && cnzEvents.isAct2TransitionRequested();
    }

    /** Returns the CNZ zone events handler, or null if not in CNZ. */
    public Sonic3kCNZEvents getCnzEvents() {
        return cnzEvents;
    }

    @Override
    public void setPendingArenaChunkDestruction(int chunkWorldX, int chunkWorldY) {
        if (cnzEvents != null) {
            cnzEvents.setPendingArenaChunkDestruction(chunkWorldX, chunkWorldY);
        }
    }

    @Override
    public void setBossScrollState(int offsetY, int velocityY) {
        if (cnzEvents != null) {
            cnzEvents.setBossScrollState(offsetY, velocityY);
        }
    }

    @Override
    public void setWallGrabSuppressed(boolean value) {
        if (cnzEvents != null) {
            cnzEvents.setWallGrabSuppressed(value);
        }
    }

    @Override
    public void setWaterButtonArmed(boolean value) {
        if (cnzEvents != null) {
            cnzEvents.setWaterButtonArmed(value);
        }
    }

    @Override
    public boolean isWaterButtonArmed() {
        return cnzEvents != null && cnzEvents.isWaterButtonArmed();
    }

    @Override
    public void setWaterTargetY(int targetY) {
        if (cnzEvents != null) {
            cnzEvents.setWaterTargetY(targetY);
        }
    }

    @Override
    public void beginKnucklesTeleporterRoute() {
        if (cnzEvents != null) {
            cnzEvents.beginKnucklesTeleporterRoute();
        }
    }

    @Override
    public void markTeleporterBeamSpawned() {
        if (cnzEvents != null) {
            cnzEvents.markTeleporterBeamSpawned();
        }
    }

    /** Returns the HCZ zone events handler, or null if not in HCZ. */
    public Sonic3kHCZEvents getHczEvents() {
        return hczEvents;
    }

    @Override
    public void setHczBossFlag(boolean value) {
        if (hczEvents != null) {
            hczEvents.setBossFlag(value);
        }
    }

    /**
     * Sets/clears the pending post-transition cutscene flag for HCZ Act 1→2.
     */
    public void setHczPendingPostTransitionCutscene(boolean pending) {
        this.hczPendingPostTransitionCutscene = pending;
    }

    /**
     * Sets Events_fg_5 on the current zone's event handler.
     * ROM: Obj_LevelResultsCreate sets this for Act 1 zones (except AIZ and ICZ)
     * to trigger the background event act transition.
     */
    public void setEventsFg5ForActTransition() {
        if (cnzEvents != null) {
            cnzEvents.setEventsFg5(true);
        }
        if (hczEvents != null) {
            hczEvents.setEventsFg5(true);
        }
        if (mgzEvents != null) {
            mgzEvents.setEventsFg5(true);
        }
        // Other zones' event handlers will be added here as implemented.
    }

    @Override
    public void signalActTransition() {
        setEventsFg5ForActTransition();
    }

    @Override
    public void requestHczPostTransitionCutscene() {
        setHczPendingPostTransitionCutscene(true);
    }

    @Override
    public void requestMgzPostTransitionRelease() {
        this.mgzPendingPostTransitionRelease = true;
    }

    /**
     * After the MGZ1 → MGZ2 seamless reload, release the player (and sidekicks)
     * from the signpost victory pose so normal play resumes. The ROM's
     * MGZ1BGE_Transition does not run a cutscene; the player simply continues
     * under their own control once the level has reloaded.
     */
    private void releasePendingMgzPostTransition() {
        if (!mgzPendingPostTransitionRelease) {
            return;
        }
        if (currentZone != Sonic3kZoneIds.ZONE_MGZ || currentAct != 1) {
            return;
        }
        mgzPendingPostTransitionRelease = false;

        AbstractPlayableSprite player = GameServices.camera().getFocusedSprite();
        if (player != null) {
            player.setObjectControlled(false);
            player.setControlLocked(false);
            player.setForcedAnimationId(-1);
        }
        for (AbstractPlayableSprite sidekick : GameServices.sprites().getSidekicks()) {
            sidekick.setObjectControlled(false);
            sidekick.setControlLocked(false);
            sidekick.setForcedAnimationId(-1);
        }
        LOG.info("MGZ: released player from victory pose after Act 1 → Act 2 reload");
    }


    /**
     * Returns the current Dynamic_resize_routine value from the active zone
     * events handler. ROM: Saved2_dynamic_resize_routine.
     */
    public int getDynamicResizeRoutine() {
        if (aizEvents != null) {
            return aizEvents.getDynamicResizeRoutine();
        }
        if (cnzEvents != null) {
            return cnzEvents.getDynamicResizeRoutine();
        }
        if (hczEvents != null) {
            return hczEvents.getDynamicResizeRoutine();
        }
        return 0;
    }

    /**
     * Restores the Dynamic_resize_routine after a big ring special stage return.
     * Must be called AFTER initLevel() (which resets it to 0).
     */
    public void setDynamicResizeRoutine(int routine) {
        if (aizEvents != null) {
            aizEvents.setDynamicResizeRoutine(routine);
        }
        if (cnzEvents != null) {
            cnzEvents.setDynamicResizeRoutine(routine);
        }
        if (hczEvents != null) {
            hczEvents.setDynamicResizeRoutine(routine);
        }
    }

    /**
     * S3K zone handlers maintain their own routine counters independently of
     * the base class fields. Override to read from the active zone handler so
     * callers (GameLoop bonus stage capture) get the correct value.
     */
    @Override
    public int getEventRoutineFg() {
        return getDynamicResizeRoutine();
    }

    /**
     * S3K zone handlers maintain their own BG routine counters independently of
     * the base class fields. CNZ now persists a local background routine for
     * save/restore parity, so callers must read from the active zone handler.
     */
    @Override
    public int getEventRoutineBg() {
        if (cnzEvents != null) {
            return cnzEvents.getBackgroundRoutine();
        }
        return super.getEventRoutineBg();
    }

    /**
     * Restores the event routine state after a bonus/special stage return.
     * Propagates to the active zone handler so its internal state machine
     * resumes from the saved position instead of replaying from 0.
     */
    @Override
    public void restoreEventRoutineState(int routineFg, int routineBg) {
        super.restoreEventRoutineState(routineFg, routineBg);
        setDynamicResizeRoutine(routineFg);
        if (cnzEvents != null) {
            cnzEvents.setBackgroundRoutine(routineBg);
        }
    }

    /**
     * Resets mutable state including static/global state in AIZ event helpers.
     * Extends the base {@link AbstractLevelEventManager#resetState()} to also
     * clear fire wall handoff, tree reveal counter, and intro phase state that
     * would otherwise leak across level loads and test iterations.
     */
    @Override
    public void resetState() {
        super.resetState();
        introFallActiveOnPlayer = false;
        introFallActiveOnSidekick = false;
        Sonic3kAIZEvents.resetGlobalState();
        CutsceneKnucklesHcz2Instance.clearActiveInstance();
        HCZWaterTunnelHandler.reset();
        HCZConveyorBeltObjectInstance.resetLoadArray();
        AizHollowTreeObjectInstance.resetTreeRevealCounter();
        AizPlaneIntroInstance.resetIntroPhaseState();
    }
    /**
     * Intercepts pit death in S3K bonus stages (Gumball, Pachinko, Slots).
     * ROM: Obj_GumballMachine init does st (Disable_death_plane).w — bonus
     * stages don't kill the player for falling off the bottom. Instead,
     * falling through triggers the stage exit via the exit trigger child.
     * <p>
     * If the player falls below the exit trigger (past the bottom of the stage),
     * force the stage to end.
     */
    @Override
    public boolean interceptPitDeath(AbstractPlayableSprite player) {
        if (isInBonusStage()) {
            // Trigger bonus stage exit if player has fallen out of the arena
            com.openggf.game.BonusStageProvider provider =
                    com.openggf.game.GameServices.bonusStageOrNull();
            if (provider != null) {
                provider.requestExit();
            }
            return true; // Suppress death
        }
        return false;
    }

    private boolean isInBonusStage() {
        return currentZone == Sonic3kZoneIds.ZONE_GUMBALL
                || currentZone == Sonic3kZoneIds.ZONE_GLOWING_SPHERE
                || currentZone == Sonic3kZoneIds.ZONE_SLOT_MACHINE;
    }
}
