package com.openggf.game.sonic3k.events;

import com.openggf.game.save.SaveReason;
import com.openggf.game.save.SessionSaveRequests;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;

import java.util.logging.Logger;

/**
 * Marble Garden Zone dynamic level events.
 *
 * <p>ROM: MGZ1_BackgroundEvent (sonic3k.asm lines 106269-106345).
 *
 * <h3>Act 1 BG (MGZ1_BackgroundEvent) — seamless act transition:</h3>
 * <ul>
 *   <li>Stage 0 (MGZ1BGE_Normal): normal scrolling; when Events_fg_5 set →
 *       queue MGZ2 art, advance to stage 4</li>
 *   <li>Stage 4 (MGZ1BGE_Transition): wait for Kos queue (engine: for
 *       endOfLevelFlag), then change zone to $201 (MGZ Act 2), reload level,
 *       offset player/camera/objects by (-$2E00, -$600)</li>
 * </ul>
 *
 * <p>The full MGZ2 resize / quake / chunk-event / collapse suite is out of
 * scope for this class; only the Act 1 → Act 2 transition is handled.
 */
public class Sonic3kMGZEvents extends Sonic3kZoneEvents {
    private static final Logger LOG = Logger.getLogger(Sonic3kMGZEvents.class.getName());

    private static final int BG_STAGE_NORMAL = 0;
    private static final int BG_STAGE_DO_TRANSITION = 4;

    /** ROM: MGZ1BGE_Transition applies (-$2E00, -$600) to player/camera/objects. */
    private static final int TRANSITION_OFFSET_X = -0x2E00;
    private static final int TRANSITION_OFFSET_Y = -0x600;

    private int bgRoutine;

    /** ROM: Events_fg_5 — set by Obj_LevelResultsCreate to trigger BG act transition. */
    private boolean eventsFg5;

    /** Prevents requesting the transition more than once. */
    private boolean transitionRequested;

    public Sonic3kMGZEvents() {
        super();
    }

    @Override
    public void init(int act) {
        super.init(act);
        bgRoutine = BG_STAGE_NORMAL;
        eventsFg5 = false;
        transitionRequested = false;
    }

    public void setEventsFg5(boolean value) {
        this.eventsFg5 = value;
    }

    @Override
    public void update(int act, int frameCounter) {
        if (act == 0) {
            updateAct1Bg();
        }
    }

    private void updateAct1Bg() {
        switch (bgRoutine) {
            case BG_STAGE_NORMAL -> {
                if (eventsFg5) {
                    eventsFg5 = false;
                    bgRoutine = BG_STAGE_DO_TRANSITION;
                    LOG.info("MGZ1 BG: Events_fg_5 detected, advancing to transition stage");
                }
            }
            case BG_STAGE_DO_TRANSITION -> {
                // ROM gate: tst.b (Kos_modules_left) — we have no Kos queue, so
                // gate on endOfLevelFlag (results screen has finished exiting)
                // to ensure the tally completes before the reload.
                if (!transitionRequested && gameState().isEndOfLevelFlag()) {
                    requestMgz2Transition();
                }
            }
            default -> { }
        }
    }

    /**
     * Requests the seamless transition from MGZ Act 1 to MGZ Act 2.
     * ROM: MGZ1BGE_Transition (sonic3k.asm lines 106307-106345).
     */
    private void requestMgz2Transition() {
        transitionRequested = true;

        // The player is still in the signpost victory pose (objectControlled).
        // After the seamless reload lands in MGZ Act 2, the level event manager
        // releases them so normal play can resume.
        S3kTransitionWriteSupport.requestMgzPostTransitionRelease(
                module().getLevelEventProvider());

        LevelManager lm = levelManager();
        SessionSaveRequests.requestCurrentSessionSave(SaveReason.PROGRESSION_SAVE);
        lm.requestSeamlessTransition(
                SeamlessLevelTransitionRequest.builder(
                                SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                        .targetZoneAct(Sonic3kZoneIds.ZONE_MGZ, 1)
                        .deactivateLevelNow(false)
                        // Results screen already started act 2 music.
                        .preserveMusic(true)
                        // Title card skipped during the results path for seamless
                        // transitions; show it after the reload completes.
                        .showInLevelTitleCard(true)
                        .playerOffset(TRANSITION_OFFSET_X, TRANSITION_OFFSET_Y)
                        .cameraOffset(TRANSITION_OFFSET_X, TRANSITION_OFFSET_Y)
                        .build());

        LOG.info("MGZ1: requested seamless transition to Act 2 (offset X="
                + Integer.toHexString(TRANSITION_OFFSET_X) + " Y="
                + Integer.toHexString(TRANSITION_OFFSET_Y) + ")");
    }

    public boolean isTransitionRequested() {
        return transitionRequested;
    }
}
