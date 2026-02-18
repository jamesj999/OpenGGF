package uk.co.jamesj999.sonic.game.sonic1.events;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1ObjectIds;
import uk.co.jamesj999.sonic.game.sonic1.objects.bosses.Sonic1FZBossInstance;
import uk.co.jamesj999.sonic.game.sonic1.scroll.Sonic1ZoneConstants;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;

/**
 * Scrap Brain Zone + Final Zone dynamic level events.
 * ROM: DLE_SBZ + DLE_FZ (DynamicLevelEvents.asm)
 *
 * Act 1 (DLE_SBZ1): Bottom boundary adjustments at three X thresholds.
 * Act 2 (DLE_SBZ2): 4-routine boss sequence with collapsing floor.
 * Act 3 (DLE_SBZ3): Zone transition to Final Zone (ROM: LZ act 3).
 * FZ   (DLE_FZ):    5-routine boss sequence with left boundary locking.
 *
 * TODO: Act 2 boss spawn + collapsing floor - DLE_SBZ lines 323-400 in s1disasm.
 *   Routine 2 spawns collapsing floor object; routine 4 spawns Eggman boss.
 *   Boss objects not yet implemented.
 * Act 3 zone transition to FZ - DLE_SBZ3: implemented.
 *   Locks player, clears checkpoint, restarts into Final Zone.
 * FZ boss spawn: routine 2 spawns FZ boss (Object 0x85).
 *   Defeat sequence and ending handled by Sonic1FZBossInstance.
 *
 * SBZ and FZ are separate zones in our engine (zone 5 and zone 6), but
 * share this event handler. Since each zone triggers a fresh level load
 * (which calls init()), the inherited eventRoutine is safely reused for
 * both the SBZ2 and FZ state machines.
 */
class Sonic1SBZEvents extends Sonic1ZoneEvents {
    // Boss arena constants (from s1disasm Constants.asm)
    private static final int BOSS_SBZ2_X = 0x2050;
    private static final int BOSS_SBZ2_Y = 0x510;
    private static final int BOSS_FZ_X = 0x2450;
    private static final int BOSS_FZ_Y = 0x510;

    // Guard against re-triggering the SBZ3->FZ transition during fade
    private boolean fzTransitionRequested;

    Sonic1SBZEvents(Camera camera) {
        super(camera);
    }

    @Override
    void init() {
        super.init();
        fzTransitionRequested = false;
    }

    @Override
    void update(int act) {
        switch (act) {
            case 0 -> updateAct1();
            case 1 -> updateAct2();
            case 2 -> updateAct3();
        }
    }

    /**
     * Called from Sonic1LevelEventManager for zone 6 (Final Zone).
     * Uses the inherited eventRoutine for its 5-routine state machine,
     * which is safe because init() resets it on each level load.
     */
    void updateFZ() {
        updateFinalZone();
    }

    // ---- SBZ Act 1 (DLE_SBZ1) ----

    /**
     * DLE_SBZ1: Three-zone bottom boundary progression.
     * Default 0x720, drops to 0x620 at X >= 0x1880, then 0x2A0 at X >= 0x2000.
     */
    private void updateAct1() {
        int camX = camera.getX() & 0xFFFF;

        // v_limitbtm1 = 0x720
        camera.setMaxYTarget((short) 0x720);
        if (camX < 0x1880) {
            return; // locret_7242
        }

        // v_limitbtm1 = 0x620
        camera.setMaxYTarget((short) 0x620);
        if (camX < 0x2000) {
            return; // locret_7242
        }

        // v_limitbtm1 = 0x2A0
        camera.setMaxYTarget((short) 0x2A0);
    }

    // ---- SBZ Act 2 (DLE_SBZ2) ----

    /**
     * DLE_SBZ2: 4-routine state machine for Act 2 boss sequence.
     * Dispatches to routines 0, 2, 4, 6 based on eventRoutine.
     */
    private void updateAct2() {
        switch (eventRoutine) {
            case 0 -> updateSBZ2Main();     // DLE_SBZ2main
            case 2 -> updateSBZ2Boss();     // DLE_SBZ2boss
            case 4 -> updateSBZ2Boss2();    // DLE_SBZ2boss2
            case 6 -> updateSBZ2End();      // DLE_SBZ2end
        }
    }

    /**
     * DLE_SBZ2main (routine 0): Pre-boss boundary logic.
     * Bottom boundary starts at 0x800, drops to boss_sbz2_y (0x510) at X >= 0x1800.
     * Advances to routine 2 when X >= 0x1E00.
     */
    private void updateSBZ2Main() {
        int camX = camera.getX() & 0xFFFF;

        // v_limitbtm1 = 0x800
        camera.setMaxYTarget((short) 0x800);
        if (camX < 0x1800) {
            return; // locret_727A
        }

        // v_limitbtm1 = boss_sbz2_y (0x510)
        camera.setMaxYTarget((short) BOSS_SBZ2_Y);
        if (camX < 0x1E00) {
            return; // locret_727A
        }

        // addq.b #2,(v_dle_routine).w
        eventRoutine += 2;
    }

    /**
     * DLE_SBZ2boss (routine 2): Collapsing floor trigger.
     * When camera X reaches boss_sbz2_x - 0x1A0 (0x1EB0), spawns the
     * collapsing floor object and advances to routine 4.
     */
    private void updateSBZ2Boss() {
        int camX = camera.getX() & 0xFFFF;

        // cmpi.w #boss_sbz2_x-$1A0,(v_screenposx).w = 0x1EB0
        if (camX < (BOSS_SBZ2_X - 0x1A0)) {
            return; // locret_7298
        }

        // TODO: Spawn collapsing floor object (not yet implemented)
        // ROM spawns the SBZ2 collapsing floor boss trigger here

        // addq.b #2,(v_dle_routine).w
        eventRoutine += 2;
    }

    /**
     * DLE_SBZ2boss2 (routine 4): Eggman spawn trigger.
     * When camera X reaches boss_sbz2_x - 0xF0 (0x1F60), spawns the boss
     * and locks the camera. Then falls through to lock left boundary.
     */
    private void updateSBZ2Boss2() {
        int camX = camera.getX() & 0xFFFF;

        // cmpi.w #boss_sbz2_x-$F0,(v_screenposx).w = 0x1F60
        if (camX >= (BOSS_SBZ2_X - 0xF0)) {
            // TODO: Spawn Eggman boss object (not yet implemented)
            // ROM spawns the SBZ2 boss (Eggman) here

            // addq.b #2,(v_dle_routine).w
            eventRoutine += 2;

            // loc_72B0: move.b #1,(f_lockscreen).w
            camera.setMaxX(camera.getX());
        }

        // Fall through to loc_72C2: lock left boundary
        lockLeftBoundary();
    }

    /**
     * DLE_SBZ2end (routine 6): Post-boss state.
     * If camera X is past boss_sbz2_x, does nothing (rts).
     * Otherwise, locks left boundary.
     */
    private void updateSBZ2End() {
        int camX = camera.getX() & 0xFFFF;

        // cmpi.w #boss_sbz2_x,(v_screenposx).w
        if (camX >= BOSS_SBZ2_X) {
            return; // past boss X: don't lock left
        }

        // loc_72C2
        lockLeftBoundary();
    }

    // ---- SBZ Act 3 (DLE_SBZ3) ----

    /**
     * DLE_SBZ3: Zone transition from SBZ3 (LZ-themed) to Final Zone.
     * When camera X >= 0xD00 and Sonic's Y position < 0x18 (reached top
     * of the level), triggers a zone transition to FZ.
     *
     * Note: ROM treats SBZ3 as LZ act 3 (id_SBZ << 8 + 2).
     */
    private void updateAct3() {
        // Guard: ROM sets f_restart which immediately restarts on the next
        // frame. Our transition is fade-coordinated (async), so we must
        // prevent re-requesting across the frames until the fade completes.
        if (fzTransitionRequested) {
            return;
        }

        int camX = camera.getX() & 0xFFFF;

        // cmpi.w #$D00,(v_screenposx).w
        if (camX < 0xD00) {
            return; // locret_6F8C
        }

        // cmpi.w #$18,(v_player+obY).w - check if Sonic reached top of level
        short playerY = camera.getFocusedSprite().getCentreY();
        int playerYUnsigned = playerY & 0xFFFF;
        if (playerYUnsigned >= 0x18) {
            return; // locret_6F8C
        }

        fzTransitionRequested = true;

        // ROM: move.b #1,(f_playerctrl).w - lock player controls
        camera.getFocusedSprite().setControlLocked(true);

        // ROM: clr.b (v_lastlamp).w - checkpoint cleared by requestZoneAndAct
        // ROM: move.w #(id_SBZ<<8)+2,(v_zone).w - FZ is zone 6 act 0 in our engine
        // ROM: move.w #1,(f_restart).w - restart level
        LevelManager.getInstance().requestZoneAndAct(
                Sonic1ZoneConstants.ZONE_FZ, 0);
    }

    // ---- Final Zone (DLE_FZ) ----

    /**
     * DLE_FZ: 5-routine state machine for Final Zone boss sequence.
     * Dispatches to routines 0, 2, 4, 6, 8 based on eventRoutine.
     * Routines 0, 2, 4, 8 all fall through to lock left boundary.
     */
    private void updateFinalZone() {
        switch (eventRoutine) {
            case 0 -> updateFZMain();       // DLE_FZmain
            case 2 -> updateFZBoss();       // DLE_FZboss
            case 4 -> updateFZEnd();        // DLE_FZend
            case 6 -> { /* locret_7322: rts - do nothing */ }
            case 8 -> updateFZEnd2();       // DLE_FZend2
        }
    }

    /**
     * DLE_FZmain (routine 0): Pre-boss art loading trigger.
     * When camera X reaches boss_fz_x - 0x308 (0x2148), loads boss
     * patterns and advances to routine 2. Always locks left boundary.
     */
    private void updateFZMain() {
        int camX = camera.getX() & 0xFFFF;

        // cmpi.w #boss_fz_x-$308,(v_screenposx).w = 0x2148
        if (camX >= (BOSS_FZ_X - 0x308)) {
            // addq.b #2,(v_dle_routine).w
            eventRoutine += 2;

            // TODO: Load FZ boss patterns (not yet implemented)
            // ROM loads boss art/patterns here
        }

        // loc_72F4: bra.s loc_72C2 - lock left boundary
        lockLeftBoundary();
    }

    /**
     * DLE_FZboss (routine 2): Boss spawn trigger.
     * When camera X reaches boss_fz_x - 0x150 (0x2300), spawns the FZ
     * boss and locks the camera. Always locks left boundary.
     */
    private void updateFZBoss() {
        int camX = camera.getX() & 0xFFFF;

        // cmpi.w #boss_fz_x-$150,(v_screenposx).w = 0x2300
        if (camX >= (BOSS_FZ_X - 0x150)) {
            // ROM: Spawn FZ boss (Object 0x85)
            LevelManager lm = LevelManager.getInstance();
            ObjectSpawn bossSpawn = new ObjectSpawn(
                    BOSS_FZ_X + 0x160, BOSS_FZ_Y + 0x80,
                    Sonic1ObjectIds.FZ_BOSS, 0, 0, false, 0);
            lm.getObjectManager().addDynamicObject(
                    new Sonic1FZBossInstance(bossSpawn, lm));
            GameServices.gameState().setCurrentBossId(Sonic1ObjectIds.FZ_BOSS);

            // addq.b #2,(v_dle_routine).w
            eventRoutine += 2;

            // ROM: move.b #1,(f_lockscreen).w — prevents right boundary from
            // extending further, but does NOT cap it to current camera X.
            // The lockLeftBoundary() call already creates a one-way rightward
            // scroll, and the level's natural right boundary constrains the camera.
        }

        // bra.s loc_72C2 - lock left boundary
        lockLeftBoundary();
    }

    /**
     * DLE_FZend (routine 4): Post-boss advancement.
     * When camera X passes boss_fz_x, advances to routine 6.
     * Always locks left boundary.
     */
    private void updateFZEnd() {
        int camX = camera.getX() & 0xFFFF;

        // cmpi.w #boss_fz_x,(v_screenposx).w
        if (camX >= BOSS_FZ_X) {
            // addq.b #2,(v_dle_routine).w
            eventRoutine += 2;
        }

        // loc_7320: bra.s loc_72C2 - lock left boundary
        lockLeftBoundary();
    }

    /**
     * DLE_FZend2 (routine 8): Final state - lock left boundary.
     */
    private void updateFZEnd2() {
        // bra.s loc_72C2
        lockLeftBoundary();
    }

    // ---- Shared helpers ----

    /**
     * loc_72C2: Lock left camera boundary to current position.
     * Shared by SBZ2 routines 4/6 and all FZ routines (0, 2, 4, 8).
     * ROM: move.w (v_screenposx).w,(v_limitleft2).w
     */
    private void lockLeftBoundary() {
        camera.setMinX(camera.getX());
    }
}
