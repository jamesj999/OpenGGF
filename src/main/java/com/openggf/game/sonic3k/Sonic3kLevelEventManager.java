package com.openggf.game.sonic3k;

import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.events.Sonic3kHCZEvents;
import com.openggf.game.sonic3k.features.HCZWaterTunnelHandler;
import com.openggf.game.sonic3k.objects.AizHollowTreeObjectInstance;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesHcz2Instance;
import com.openggf.game.sonic3k.objects.HCZConveyorBeltObjectInstance;
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
public class Sonic3kLevelEventManager extends AbstractLevelEventManager {
    private static final Logger LOG = Logger.getLogger(Sonic3kLevelEventManager.class.getName());
    private static final int PACHINKO_TOP_EXIT_Y = -0x20;

    private Sonic3kLoadBootstrap bootstrap = Sonic3kLoadBootstrap.NORMAL;
    private Sonic3kAIZEvents aizEvents;
    private Sonic3kHCZEvents hczEvents;

    // Tracks whether the intro-fall forced animation is active on each player.
    // Cleared per-player when they land (air → ground transition).
    private boolean introFallActiveOnPlayer;
    private boolean introFallActiveOnSidekick;

    // Set by HCZ Act 1 transition: after the seamless reload to Act 2, the
    // whirlpool descent cutscene should play. Consumed on the first onUpdate()
    // after the transition completes.
    private boolean hczPendingPostTransitionCutscene;


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
        // Resolve from config — matches ROM's Player_mode variable
        String mainChar = GameServices.configuration()
                .getString(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE);
        if ("knuckles".equalsIgnoreCase(mainChar)) {
            return PlayerCharacter.KNUCKLES;
        } else if ("tails".equalsIgnoreCase(mainChar)) {
            return PlayerCharacter.TAILS_ALONE;
        }
        // Check for sidekick config to distinguish SONIC_ALONE vs SONIC_AND_TAILS
        String sidekick = GameServices.configuration()
                .getString(com.openggf.configuration.SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        if (sidekick == null || sidekick.isBlank()) {
            return PlayerCharacter.SONIC_ALONE;
        }
        return PlayerCharacter.SONIC_AND_TAILS;
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
        if (zone == Sonic3kZoneIds.ZONE_HCZ) {
            hczEvents = new Sonic3kHCZEvents();
            hczEvents.init(act);
        } else {
            hczEvents = null;
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
        if (hczEvents != null && currentZone == Sonic3kZoneIds.ZONE_HCZ) {
            hczEvents.update(currentAct, frameCounter);
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
        // TODO: MGZ1, LRZ1/Knuckles, SSZ falling intros (loc_68A6)
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

    /** Returns the HCZ zone events handler, or null if not in HCZ. */
    public Sonic3kHCZEvents getHczEvents() {
        return hczEvents;
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
        if (hczEvents != null) {
            hczEvents.setEventsFg5(true);
        }
        // Other zones' event handlers will be added here as implemented.
    }


    /**
     * Returns the current Dynamic_resize_routine value from the active zone
     * events handler. ROM: Saved2_dynamic_resize_routine.
     */
    public int getDynamicResizeRoutine() {
        if (aizEvents != null) {
            return aizEvents.getDynamicResizeRoutine();
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
     * Restores the event routine state after a bonus/special stage return.
     * Propagates to the active zone handler so its internal state machine
     * resumes from the saved position instead of replaying from 0.
     */
    @Override
    public void restoreEventRoutineState(int routineFg, int routineBg) {
        super.restoreEventRoutineState(routineFg, routineBg);
        setDynamicResizeRoutine(routineFg);
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
