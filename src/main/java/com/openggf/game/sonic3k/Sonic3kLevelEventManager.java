package com.openggf.game.sonic3k;

import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.AizObjectEventBridge;
import com.openggf.game.sonic3k.events.CnzObjectEventBridge;
import com.openggf.game.sonic3k.events.HczObjectEventBridge;
import com.openggf.game.sonic3k.events.MgzObjectEventBridge;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.events.Sonic3kHCZEvents;
import com.openggf.game.sonic3k.events.Sonic3kICZEvents;
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
import com.openggf.game.sonic3k.objects.IczSnowboardArtLoader;
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
        implements AizObjectEventBridge, CnzObjectEventBridge, HczObjectEventBridge, MgzObjectEventBridge,
        S3kTransitionEventBridge {
    private static final Logger LOG = Logger.getLogger(Sonic3kLevelEventManager.class.getName());
    private static final int PACHINKO_TOP_EXIT_Y = -0x20;

    private Sonic3kLoadBootstrap bootstrap = Sonic3kLoadBootstrap.NORMAL;
    private Sonic3kAIZEvents aizEvents;
    private Sonic3kCNZEvents cnzEvents;
    private Sonic3kHCZEvents hczEvents;
    private Sonic3kICZEvents iczEvents;
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
        // Guard with runtimeOrNull() so initLevel can be called from unit tests
        // or snapshot-restore paths that have no active gameplay mode.
        if (bootstrap.mode() == Sonic3kLoadBootstrap.Mode.INTRO
                && GameServices.runtimeOrNull() != null
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
        if (zone == Sonic3kZoneIds.ZONE_ICZ) {
            iczEvents = new Sonic3kICZEvents();
            iczEvents.init(act);
        } else {
            iczEvents = null;
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
        if (GameServices.runtimeOrNull() == null) {
            LOG.fine("Skipping S3K zone runtime registration because no active runtime is installed");
            return;
        }
        ZoneRuntimeRegistry registry = GameServices.zoneRuntimeRegistry();
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
        if (iczEvents != null && currentZone == Sonic3kZoneIds.ZONE_ICZ) {
            iczEvents.update(currentAct, frameCounter);
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
        if (iczEvents != null) {
            iczEvents.setEventsFg5(value);
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
        if (aizEvents != null) {
            return aizEvents.isEventsFg5();
        }
        return iczEvents != null && iczEvents.isEventsFg5();
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
    public Sonic3kMGZEvents getMgzEvents() {
        return mgzEvents;
    }

    @Override
    public void triggerBossCollapseHandoff() {
        if (mgzEvents != null) {
            mgzEvents.triggerBossCollapseHandoff();
        }
    }

    public Sonic3kHCZEvents getHczEvents() {
        return hczEvents;
    }

    public Sonic3kICZEvents getIczEvents() {
        return iczEvents;
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
        if (iczEvents != null) {
            return iczEvents.getDynamicResizeRoutine();
        }
        if (mgzEvents != null) {
            return mgzEvents.getDynamicResizeRoutine();
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
        if (iczEvents != null) {
            iczEvents.setDynamicResizeRoutine(routine);
        }
        if (mgzEvents != null) {
            mgzEvents.setDynamicResizeRoutine(routine);
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
        IczSnowboardArtLoader.reset();
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
        if (mgzEvents != null && mgzEvents.isBossTransitionDeathPlaneDisabled()) {
            return true;
        }
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

    // =========================================================================
    // RewindSnapshottable extra-state hooks (C.4)
    // =========================================================================

    /** Accessor for test/diagnostic use — returns the S3K zone event handler for AIZ. */
    public Sonic3kAIZEvents getAizEventsForTest()  { return aizEvents; }
    /** Accessor for test/diagnostic use — returns the S3K zone event handler for HCZ. */
    public Sonic3kHCZEvents getHczEventsForTest()  { return hczEvents; }
    /** Accessor for test/diagnostic use — returns the S3K zone event handler for CNZ. */
    public Sonic3kCNZEvents getCnzEventsForTest()  { return cnzEvents; }
    /** Accessor for test/diagnostic use — returns the S3K zone event handler for MGZ. */
    public Sonic3kMGZEvents getMgzEventsForTest()  { return mgzEvents; }

    @Override
    protected byte[] captureExtra() {
        // Layout:
        //   5 bytes   manager-level (bootstrap mode ordinal + 4 booleans)
        //   1 byte    aiz handler present flag
        //   87 bytes  aiz state (19 booleans + 15 ints + 1 ordinal = 19+64 = 83; adjust to actual 87)
        //   1 byte    hcz handler present flag
        //   43 bytes  hcz state (7 booleans + 9 ints = 7+36)
        //   1 byte    cnz handler present flag
        //   86 bytes  cnz state (4 shorts + 10 booleans + 15 ints + 1 ordinal = 8+10+60+4+4)
        //   1 byte    mgz handler present flag
        //   228 bytes mgz state (16 booleans + 23 ints + 30 ints = 16+92+120)
        // AIZ: 19 booleans + 15 ints + 1 ordinal = 19 + 60 + 4 = 83 bytes
        // (original comment said 87 but actual is 83: 19 bools + 15*4 ints + 1*4 ordinal)
        int aizSize = 19 + 15 * 4 + 4; // 83
        // HCZ: 7 booleans + 9 ints = 43
        int hczSize = 7 + 9 * 4; // 43
        // CNZ: 4 shorts + 10 booleans + 15 ints + 1 ordinal = 8+10+60+4 = 82
        int cnzSize = 4 * 2 + 10 + 15 * 4 + 4; // 82
        // MGZ: 16 booleans + 23 ints + 3*10 ints = 16 + 92 + 120 = 228
        int mgzSize = 16 + 23 * 4 + 3 * 10 * 4; // 228
        int size = 5;
        size += 1 + (aizEvents != null ? aizSize : 0);
        size += 1 + (hczEvents != null ? hczSize : 0);
        size += 1 + (cnzEvents != null ? cnzSize : 0);
        size += 1 + (mgzEvents != null ? mgzSize : 0);
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(size);
        // Manager-level
        buf.put((byte) bootstrap.mode().ordinal());
        buf.put((byte) (introFallActiveOnPlayer  ? 1 : 0));
        buf.put((byte) (introFallActiveOnSidekick ? 1 : 0));
        buf.put((byte) (hczPendingPostTransitionCutscene ? 1 : 0));
        buf.put((byte) (mgzPendingPostTransitionRelease  ? 1 : 0));
        // AIZ
        if (aizEvents != null) {
            buf.put((byte) 1);
            writeAizState(buf, aizEvents);
        } else {
            buf.put((byte) 0);
        }
        // HCZ
        if (hczEvents != null) {
            buf.put((byte) 1);
            writeHczState(buf, hczEvents);
        } else {
            buf.put((byte) 0);
        }
        // CNZ
        if (cnzEvents != null) {
            buf.put((byte) 1);
            writeCnzState(buf, cnzEvents);
        } else {
            buf.put((byte) 0);
        }
        // MGZ
        if (mgzEvents != null) {
            buf.put((byte) 1);
            writeMgzState(buf, mgzEvents);
        } else {
            buf.put((byte) 0);
        }
        return buf.array();
    }

    @Override
    protected void restoreExtra(byte[] extra) {
        if (extra == null || extra.length < 5) {
            return;
        }
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(extra);
        // Manager-level
        int modeOrdinal = buf.get() & 0xFF;
        Sonic3kLoadBootstrap.Mode[] modes = Sonic3kLoadBootstrap.Mode.values();
        Sonic3kLoadBootstrap.Mode mode = (modeOrdinal < modes.length) ? modes[modeOrdinal] : Sonic3kLoadBootstrap.Mode.NORMAL;
        bootstrap = new Sonic3kLoadBootstrap(mode, bootstrap.introStartPosition());
        introFallActiveOnPlayer           = buf.get() != 0;
        introFallActiveOnSidekick         = buf.get() != 0;
        hczPendingPostTransitionCutscene  = buf.get() != 0;
        mgzPendingPostTransitionRelease   = buf.get() != 0;
        // Size constants must match the write methods
        final int aizBytes = 19 + 15 * 4 + 4; // 83
        final int hczBytes = 7 + 9 * 4;        // 43
        final int cnzBytes = 4 * 2 + 10 + 15 * 4 + 4; // 82
        final int mgzBytes = 16 + 23 * 4 + 3 * 10 * 4; // 228
        // AIZ
        if (buf.remaining() >= 1) {
            boolean aizPresent = buf.get() != 0;
            if (aizPresent && aizEvents != null && buf.remaining() >= aizBytes) {
                readAizState(buf, aizEvents);
            } else if (aizPresent && buf.remaining() >= aizBytes) {
                buf.position(buf.position() + aizBytes);
            }
        }
        // HCZ
        if (buf.remaining() >= 1) {
            boolean hczPresent = buf.get() != 0;
            if (hczPresent && hczEvents != null && buf.remaining() >= hczBytes) {
                readHczState(buf, hczEvents);
            } else if (hczPresent && buf.remaining() >= hczBytes) {
                buf.position(buf.position() + hczBytes);
            }
        }
        // CNZ
        if (buf.remaining() >= 1) {
            boolean cnzPresent = buf.get() != 0;
            if (cnzPresent && cnzEvents != null && buf.remaining() >= cnzBytes) {
                readCnzState(buf, cnzEvents);
            } else if (cnzPresent && buf.remaining() >= cnzBytes) {
                buf.position(buf.position() + cnzBytes);
            }
        }
        // MGZ
        if (buf.remaining() >= 1) {
            boolean mgzPresent = buf.get() != 0;
            if (mgzPresent && mgzEvents != null && buf.remaining() >= mgzBytes) {
                readMgzState(buf, mgzEvents);
            } else if (mgzPresent && buf.remaining() >= mgzBytes) {
                buf.position(buf.position() + mgzBytes);
            }
        }
    }

    // --- AIZ write/read (87 bytes) ---

    private static void writeAizState(java.nio.ByteBuffer buf, Sonic3kAIZEvents a) {
        // 19 booleans (1 byte each) = 19
        buf.put((byte)(a.isIntroSpawned()                    ? 1 : 0));
        buf.put((byte)(a.isIntroMinXLocked()                 ? 1 : 0));
        buf.put((byte)(a.isIntroNormalRefreshPending()       ? 1 : 0));
        buf.put((byte)(a.isPaletteSwapped()                  ? 1 : 0));
        buf.put((byte)(a.isBoundariesUnlocked()              ? 1 : 0));
        buf.put((byte)(a.isFireMinXLockReached()             ? 1 : 0));
        buf.put((byte)(a.isMinibossSpawned()                 ? 1 : 0));
        buf.put((byte)(a.isEventsFg4Raw()                    ? 1 : 0));
        buf.put((byte)(a.isEventsFg5()                       ? 1 : 0));
        buf.put((byte)(a.isBossFlag()                        ? 1 : 0));
        buf.put((byte)(a.isBattleshipAutoScrollActiveRaw()   ? 1 : 0));
        buf.put((byte)(a.isBattleshipSpawned()               ? 1 : 0));
        buf.put((byte)(a.isEndBossSpawned()                  ? 1 : 0));
        buf.put((byte)(a.isBattleshipTerrainLoaded()         ? 1 : 0));
        buf.put((byte)(a.isAct2TransitionRequestedRaw()      ? 1 : 0));
        buf.put((byte)(a.isFireTransitionMutationRequested() ? 1 : 0));
        buf.put((byte)(a.isPostFireHazeActiveRaw()           ? 1 : 0));
        buf.put((byte)(a.isFireOverlayTilesLoaded()          ? 1 : 0));
        buf.put((byte)(a.isAct2WaitFireDrawActive()          ? 1 : 0));
        // 16 ints = 64 bytes
        buf.putInt(a.getAppliedTreeRevealChunkCopiesMask());
        buf.putInt(a.getAiz2ResizeRoutine());
        buf.putInt(a.getBattleshipWrapX());
        buf.putInt(a.getScreenShakeTimer());
        buf.putInt(a.getLevelRepeatOffsetRaw());
        buf.putInt(a.getBattleshipBgYOffsetRaw());
        buf.putInt(a.getBattleshipSmoothScrollXRaw());
        buf.putInt(a.getBattleshipPostScrollCameraX());
        buf.putInt(a.getScreenShakeOffsetYRaw());
        buf.putInt(a.getFireBgCopyFixed());
        buf.putInt(a.getFireRiseSpeed());
        buf.putInt(a.getFireWavePhase());
        buf.putInt(a.getFireTransitionFrames());
        buf.putInt(a.getFirePhaseFrames());
        buf.putInt(a.getFireOverlayTileCount());
        // 1 enum ordinal = 4 bytes
        buf.putInt(a.getFireSequencePhaseOrdinal());
    }

    private static void readAizState(java.nio.ByteBuffer buf, Sonic3kAIZEvents a) {
        a.setIntroSpawned(buf.get() != 0);
        a.setIntroMinXLocked(buf.get() != 0);
        a.setIntroNormalRefreshPending(buf.get() != 0);
        a.setPaletteSwapped(buf.get() != 0);
        a.setBoundariesUnlocked(buf.get() != 0);
        a.setFireMinXLockReached(buf.get() != 0);
        a.setMinibossSpawned(buf.get() != 0);
        a.setEventsFg4Raw(buf.get() != 0);
        a.setEventsFg5(buf.get() != 0);
        a.setBossFlag(buf.get() != 0);
        a.setBattleshipAutoScrollActiveRaw(buf.get() != 0);
        a.setBattleshipSpawned(buf.get() != 0);
        a.setEndBossSpawned(buf.get() != 0);
        a.setBattleshipTerrainLoaded(buf.get() != 0);
        a.setAct2TransitionRequestedRaw(buf.get() != 0);
        a.setFireTransitionMutationRequested(buf.get() != 0);
        a.setPostFireHazeActiveRaw(buf.get() != 0);
        a.setFireOverlayTilesLoaded(buf.get() != 0);
        a.setAct2WaitFireDrawActive(buf.get() != 0);
        a.setAppliedTreeRevealChunkCopiesMask(buf.getInt());
        a.setAiz2ResizeRoutine(buf.getInt());
        a.setBattleshipWrapX(buf.getInt());
        a.setScreenShakeTimer(buf.getInt());
        a.setLevelRepeatOffsetRaw(buf.getInt());
        a.setBattleshipBgYOffsetRaw(buf.getInt());
        a.setBattleshipSmoothScrollXRaw(buf.getInt());
        a.setBattleshipPostScrollCameraX(buf.getInt());
        a.setScreenShakeOffsetYRaw(buf.getInt());
        a.setFireBgCopyFixed(buf.getInt());
        a.setFireRiseSpeed(buf.getInt());
        a.setFireWavePhase(buf.getInt());
        a.setFireTransitionFrames(buf.getInt());
        a.setFirePhaseFrames(buf.getInt());
        a.setFireOverlayTileCount(buf.getInt());
        a.setFireSequencePhaseOrdinal(buf.getInt());
    }

    // --- HCZ write/read (43 bytes: 7 booleans + 9 ints) ---

    private static void writeHczState(java.nio.ByteBuffer buf, Sonic3kHCZEvents h) {
        // 7 booleans = 7 bytes
        buf.put((byte)(h.isEventsFg5()                ? 1 : 0));
        buf.put((byte)(h.isBossFlag()                 ? 1 : 0));
        buf.put((byte)(h.isTransitionRequested()      ? 1 : 0));
        buf.put((byte)(h.isWallMoving()               ? 1 : 0));
        buf.put((byte)(h.isWallStopped()              ? 1 : 0));
        buf.put((byte)(h.isWallChaseBgOverlayActive() ? 1 : 0));
        buf.put((byte)(h.isCutsceneActive()           ? 1 : 0));
        // 9 ints = 36 bytes
        buf.putInt(h.getDynamicResizeRoutine()); // fgRoutine
        buf.putInt(h.getBgRoutine());
        buf.putInt(h.getAct2BgRoutine());
        buf.putInt(h.getWallOffsetFixed());
        buf.putInt(h.getWallOffsetPixels());
        buf.putInt(h.getShakeTimer());
        buf.putInt(h.getCutsceneFrame());
        buf.putInt(h.getCutsceneCenterX());
        buf.putInt(h.getCutsceneCurrentY());
    }

    private static void readHczState(java.nio.ByteBuffer buf, Sonic3kHCZEvents h) {
        h.setEventsFg5(buf.get() != 0);
        h.setBossFlag(buf.get() != 0);
        h.setTransitionRequested(buf.get() != 0);
        h.setWallMoving(buf.get() != 0);
        h.setWallStopped(buf.get() != 0);
        h.setWallChaseBgOverlayActiveRaw(buf.get() != 0);
        h.setCutsceneActive(buf.get() != 0);
        h.setDynamicResizeRoutine(buf.getInt());
        h.setBgRoutine(buf.getInt());
        h.setAct2BgRoutine(buf.getInt());
        h.setWallOffsetFixed(buf.getInt());
        h.setWallOffsetPixels(buf.getInt());
        h.setShakeTimer(buf.getInt());
        h.setCutsceneFrame(buf.getInt());
        h.setCutsceneCenterX(buf.getInt());
        h.setCutsceneCurrentY(buf.getInt());
    }

    // --- CNZ write/read (82 bytes) ---

    private static void writeCnzState(java.nio.ByteBuffer buf, Sonic3kCNZEvents c) {
        // 4 shorts = 8 bytes
        buf.putShort(c.getCameraStoredMaxXPos());
        buf.putShort(c.getCameraStoredMinXPos());
        buf.putShort(c.getCameraStoredMinYPos());
        buf.putShort(c.getCameraStoredMaxYPos());
        // 10 booleans = 10 bytes
        buf.put((byte)(c.isCameraClampsActive()               ? 1 : 0));
        buf.put((byte)(c.isBossFlagPrev()                     ? 1 : 0));
        buf.put((byte)(c.isEventsFg5()                        ? 1 : 0));
        buf.put((byte)(c.isBossFlag()                         ? 1 : 0));
        buf.put((byte)(c.isWallGrabSuppressed()               ? 1 : 0));
        buf.put((byte)(c.isWaterButtonArmed()                 ? 1 : 0));
        buf.put((byte)(c.isKnucklesTeleporterRouteActive()    ? 1 : 0));
        buf.put((byte)(c.isTeleporterBeamSpawned()            ? 1 : 0));
        buf.put((byte)(c.isAct2TransitionRequested()          ? 1 : 0));
        buf.put((byte)(c.isArenaChunkDestructionQueued()      ? 1 : 0));
        // 16 ints = 64 bytes
        buf.putInt(c.getForegroundRoutine());
        buf.putInt(c.getBackgroundRoutine());
        buf.putInt(c.getDeformPhaseBgX());
        buf.putInt(c.getPublishedBgCameraX());
        buf.putInt(c.getBossScrollOffsetY());
        buf.putInt(c.getBossScrollVelocityY());
        buf.putInt(c.getWaterTargetY());
        buf.putInt(c.getPendingZoneActWord());
        buf.putInt(c.getTransitionWorldOffsetX());
        buf.putInt(c.getTransitionWorldOffsetY());
        buf.putInt(c.getCameraMinXClamp());
        buf.putInt(c.getCameraMaxXClamp());
        buf.putInt(c.getArenaChunkWorldX());
        buf.putInt(c.getArenaChunkWorldY());
        buf.putInt(c.getDestroyedArenaRows());
        // 1 enum ordinal = 4 bytes (total = 8+10+64+4 = 86 bytes; not 82)
        buf.putInt(c.getBossBackgroundMode().ordinal());
    }

    private static void readCnzState(java.nio.ByteBuffer buf, Sonic3kCNZEvents c) {
        c.setCameraStoredMaxXPos(buf.getShort());
        c.setCameraStoredMinXPos(buf.getShort());
        c.setCameraStoredMinYPos(buf.getShort());
        c.setCameraStoredMaxYPos(buf.getShort());
        c.setCameraClampsActive(buf.get() != 0);
        c.setBossFlagPrev(buf.get() != 0);
        c.setEventsFg5(buf.get() != 0);
        c.setBossFlag(buf.get() != 0);
        c.setWallGrabSuppressed(buf.get() != 0);
        c.setWaterButtonArmed(buf.get() != 0);
        c.setKnucklesTeleporterRouteActive(buf.get() != 0);
        c.setTeleporterBeamSpawned(buf.get() != 0);
        c.setAct2TransitionRequested(buf.get() != 0);
        c.setArenaChunkDestructionQueued(buf.get() != 0);
        c.setForegroundRoutine(buf.getInt());
        c.setBackgroundRoutine(buf.getInt());
        c.setPublishedDeformInputs(buf.getInt(), buf.getInt());
        c.setBossScrollState(buf.getInt(), buf.getInt());
        c.setWaterTargetYRaw(buf.getInt());
        c.setPendingZoneActWordRaw(buf.getInt());
        c.setTransitionWorldOffsetX(buf.getInt());
        c.setTransitionWorldOffsetY(buf.getInt());
        c.setCameraMinXClamp(buf.getInt());
        c.setCameraMaxXClamp(buf.getInt());
        c.setArenaChunkWorldX(buf.getInt());
        c.setArenaChunkWorldY(buf.getInt());
        c.setDestroyedArenaRows(buf.getInt());
        int modeOrd = buf.getInt();
        Sonic3kCNZEvents.BossBackgroundMode[] modes = Sonic3kCNZEvents.BossBackgroundMode.values();
        c.setBossBackgroundMode(modeOrd >= 0 && modeOrd < modes.length ? modes[modeOrd] : Sonic3kCNZEvents.BossBackgroundMode.NORMAL);
    }

    // --- MGZ write/read (232 bytes) ---

    private static void writeMgzState(java.nio.ByteBuffer buf, Sonic3kMGZEvents m) {
        // 16 booleans = 16
        buf.put((byte)(m.isEventsFg5Raw()                  ? 1 : 0));
        buf.put((byte)(m.isTransitionRequested()           ? 1 : 0));
        buf.put((byte)(m.isCollapseRequested()             ? 1 : 0));
        buf.put((byte)(m.isCollapseInitialized()           ? 1 : 0));
        buf.put((byte)(m.isCollapseFinished()              ? 1 : 0));
        buf.put((byte)(m.isScreenShakeActiveRaw()          ? 1 : 0));
        buf.put((byte)(m.isBossTransitionActiveRaw()       ? 1 : 0));
        buf.put((byte)(m.isBossTransitionDeathPlaneDisabled() ? 1 : 0));
        buf.put((byte)(m.isBgRiseMotionStarted()           ? 1 : 0));
        buf.put((byte)(m.isBgRiseAccelLatched()            ? 1 : 0));
        buf.put((byte)(m.isBgRiseLoadStateInitialised()    ? 1 : 0));
        buf.put((byte)(m.isBossSpawned()                   ? 1 : 0));
        buf.put((byte)(m.isAppearance1Complete()           ? 1 : 0));
        buf.put((byte)(m.isAppearance2Complete()           ? 1 : 0));
        buf.put((byte)(m.isAppearance3Complete()           ? 1 : 0));
        buf.put((byte)(m.isPostFleeUnlockDone()            ? 1 : 0));
        // 23 ints = 92
        buf.putInt(m.getBgRoutine());
        buf.putInt(m.getQuakeEventRoutine());
        buf.putInt(m.getChunkEventRoutine());
        buf.putInt(m.getChunkReplaceIndex());
        buf.putInt(m.getChunkEventDelay());
        buf.putInt(m.getScreenEventRoutine());
        buf.putInt(m.getCollapseMutationCount());
        buf.putInt(m.getCollapseFrameCounter());
        buf.putInt(m.getCollapseStartupShakeTimer());
        buf.putInt(m.getCollapseRenderHoldFrames());
        buf.putInt(m.getBossBgScrollVelocity());
        buf.putInt(m.getBossBgScrollOffset());
        buf.putInt(m.getBossTransitionTimer());
        buf.putInt(m.getBossTransitionX());
        buf.putInt(m.getBossTransitionY());
        buf.putInt(m.getBossTransitionCameraX());
        buf.putInt(m.getBossTransitionCameraY());
        buf.putInt(m.getBgRiseRoutine());
        buf.putInt(m.getBgRiseOffset());
        buf.putInt(m.getBgRiseSubpixelAccum());
        buf.putInt(m.getBgRiseFinalShakeTimerRaw());
        buf.putInt(m.getBossArenaRoutine());
        buf.putInt(m.getGradualUnlockDirection());
        // 3 × 10 ints = 120
        int[] sv = m.getCollapseScrollVelocityCopy();
        int[] sf = m.getCollapseScrollFixedPositionCopy();
        int[] sp = m.getCollapseScrollPositionCopy();
        for (int i = 0; i < 10; i++) buf.putInt(sv[i]);
        for (int i = 0; i < 10; i++) buf.putInt(sf[i]);
        for (int i = 0; i < 10; i++) buf.putInt(sp[i]);
        // Total: 16 + 92 + 120 = 228
    }

    private static void readMgzState(java.nio.ByteBuffer buf, Sonic3kMGZEvents m) {
        m.setEventsFg5Raw(buf.get() != 0);
        m.setTransitionRequestedRaw(buf.get() != 0);
        m.setCollapseRequested(buf.get() != 0);
        m.setCollapseInitialized(buf.get() != 0);
        m.setCollapseFinished(buf.get() != 0);
        m.setScreenShakeActiveRaw(buf.get() != 0);
        m.setBossTransitionActiveRaw(buf.get() != 0);
        m.setBossTransitionDeathPlaneDisabled(buf.get() != 0);
        m.setBgRiseMotionStarted(buf.get() != 0);
        m.setBgRiseAccelLatched(buf.get() != 0);
        m.setBgRiseLoadStateInitialised(buf.get() != 0);
        m.setBossSpawned(buf.get() != 0);
        m.setAppearance1Complete(buf.get() != 0);
        m.setAppearance2Complete(buf.get() != 0);
        m.setAppearance3Complete(buf.get() != 0);
        m.setPostFleeUnlockDone(buf.get() != 0);
        m.setBgRoutine(buf.getInt());
        m.setQuakeEventRoutine(buf.getInt());
        m.setChunkEventRoutine(buf.getInt());
        m.setChunkReplaceIndex(buf.getInt());
        m.setChunkEventDelay(buf.getInt());
        m.setScreenEventRoutine(buf.getInt());
        m.setCollapseMutationCount(buf.getInt());
        m.setCollapseFrameCounter(buf.getInt());
        m.setCollapseStartupShakeTimer(buf.getInt());
        m.setCollapseRenderHoldFrames(buf.getInt());
        m.setBossBgScrollVelocity(buf.getInt());
        m.setBossBgScrollOffset(buf.getInt());
        m.setBossTransitionTimer(buf.getInt());
        m.setBossTransitionX(buf.getInt());
        m.setBossTransitionY(buf.getInt());
        m.setBossTransitionCameraX(buf.getInt());
        m.setBossTransitionCameraY(buf.getInt());
        m.setBgRiseRoutine(buf.getInt());
        m.setBgRiseOffset(buf.getInt());
        m.setBgRiseSubpixelAccum(buf.getInt());
        m.setBgRiseFinalShakeTimer(buf.getInt());
        m.setBossArenaRoutine(buf.getInt());
        m.setGradualUnlockDirection(buf.getInt());
        int[] sv = new int[10];
        int[] sf = new int[10];
        int[] sp = new int[10];
        for (int i = 0; i < 10; i++) sv[i] = buf.getInt();
        for (int i = 0; i < 10; i++) sf[i] = buf.getInt();
        for (int i = 0; i < 10; i++) sp[i] = buf.getInt();
        m.setCollapseScrollVelocity(sv);
        m.setCollapseScrollFixedPosition(sf);
        m.setCollapseScrollPosition(sp);
    }
}
