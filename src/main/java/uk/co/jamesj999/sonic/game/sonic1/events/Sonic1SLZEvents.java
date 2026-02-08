package uk.co.jamesj999.sonic.game.sonic1.events;

import uk.co.jamesj999.sonic.camera.Camera;

/**
 * Star Light Zone dynamic level events.
 * ROM: DLE_SLZ (DynamicLevelEvents.asm)
 *
 * Acts 1-2: No events.
 * Act 3: Boss arena with 3-routine state machine.
 */
class Sonic1SLZEvents extends Sonic1ZoneEvents {
    // Boss constants
    private static final int BOSS_SLZ_X = 0x2000;
    private static final int BOSS_SLZ_Y = 0x210;

    Sonic1SLZEvents(Camera camera) {
        super(camera);
    }

    @Override
    void update(int act) {
        switch (act) {
            case 0, 1 -> { /* DLE_SLZ12: rts */ }
            case 2 -> updateAct3();
        }
    }

    /**
     * DLE_SLZ3: State machine for Act 3 boss sequence.
     * Dispatches to main/boss/end based on eventRoutine.
     */
    private void updateAct3() {
        switch (eventRoutine) {
            case 0 -> updateAct3Main();     // DLE_SLZ3main
            case 2 -> updateAct3Boss();     // DLE_SLZ3boss
            case 4 -> updateAct3End();      // DLE_SLZ3end
        }
    }

    /**
     * DLE_SLZ3main: Pre-boss boundary trigger.
     * When camera X reaches boss_slz_x - 0x190 (0x1E70),
     * sets bottom boundary to boss_slz_y and advances to boss phase.
     */
    private void updateAct3Main() {
        int camX = camera.getX() & 0xFFFF;

        if (camX < (BOSS_SLZ_X - 0x190)) {
            return; // locret_7130
        }

        // v_limitbtm1 = boss_slz_y
        camera.setMaxYTarget((short) BOSS_SLZ_Y);
        eventRoutine += 2; // advance to DLE_SLZ3boss
    }

    /**
     * DLE_SLZ3boss: Boss encounter phase.
     * When camera X reaches boss_slz_x, locks the camera and spawns the boss.
     */
    private void updateAct3Boss() {
        int camX = camera.getX() & 0xFFFF;

        if (camX < BOSS_SLZ_X) {
            return; // locret_715C
        }

        // TODO: Boss spawn code - objects not yet implemented
        // ROM loads boss object (Obj3D - Orbinaut/boss) and sets up arena here

        // TODO: QueueSound1 bgm_Boss - play boss music
        // AudioManager.getInstance().queueSound(bgm_Boss);

        // f_lockscreen = 1 (lock camera)
        camera.setFrozen(true);
        eventRoutine += 2; // advance to DLE_SLZ3end
    }

    /**
     * DLE_SLZ3end: Post-boss lock.
     * Locks left boundary to current camera X position.
     */
    private void updateAct3End() {
        // v_limitleft2 = v_screenposx
        camera.setMinX(camera.getX());
    }
}
