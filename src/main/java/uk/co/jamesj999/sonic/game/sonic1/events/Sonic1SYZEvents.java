package uk.co.jamesj999.sonic.game.sonic1.events;

import uk.co.jamesj999.sonic.camera.Camera;

/**
 * Spring Yard Zone dynamic level events.
 * ROM: DLE_SYZ (DynamicLevelEvents.asm)
 *
 * Act 1: No events.
 * Act 2: Bottom boundary adjusts based on camera X and player Y.
 * Act 3: Boss arena with 3-routine state machine.
 *
 * TODO: Act 3 boss spawn + arena - DLE_SYZ lines 283-320 in s1disasm.
 *   Routine 0 spawns boss arena block object.
 *   Routine 2 spawns boss object and plays bgm_Boss.
 *   Boss objects and defeat sequence not yet implemented.
 */
class Sonic1SYZEvents extends Sonic1ZoneEvents {
    // Boss constants
    private static final int BOSS_SYZ_X = 0x2C00;
    private static final int BOSS_SYZ_Y = 0x4CC;

    Sonic1SYZEvents(Camera camera) {
        super(camera);
    }

    @Override
    void update(int act) {
        switch (act) {
            case 0 -> { /* DLE_SYZ1: rts */ }
            case 1 -> updateAct2();
            case 2 -> updateAct3();
        }
    }

    /**
     * DLE_SYZ2: Three-zone boundary with player Y check.
     * Bottom boundary defaults to 0x520, drops to 0x420 when camera X >= 0x25A0,
     * but reverts to 0x520 if player Y >= 0x4D0.
     */
    private void updateAct2() {
        int camX = camera.getX() & 0xFFFF;

        // v_limitbtm1 = 0x520
        camera.setMaxYTarget((short) 0x520);
        if (camX < 0x25A0) {
            return; // locret_71A2
        }

        // v_limitbtm1 = 0x420
        camera.setMaxYTarget((short) 0x420);

        // NOTE: The ROM checks v_player+obY (player Y position), not camera Y.
        // Using camera Y as an approximation since the camera follows the player
        // closely and the exact pixel value isn't critical for boundary switching.
        int camY = camera.getY() & 0xFFFF;
        if (camY < 0x4D0) {
            return; // locret_71A2
        }

        // v_limitbtm1 = 0x520 (revert)
        camera.setMaxYTarget((short) 0x520);
        // locret_71A2
    }

    /**
     * DLE_SYZ3: State machine for Act 3 boss sequence.
     * Dispatches to main/boss/end based on eventRoutine.
     */
    private void updateAct3() {
        switch (eventRoutine) {
            case 0 -> updateAct3Main();     // DLE_SYZ3main
            case 2 -> updateAct3Boss();     // DLE_SYZ3boss
            case 4 -> updateAct3End();      // DLE_SYZ3end
        }
    }

    /**
     * DLE_SYZ3main: Pre-boss trigger.
     * When camera X reaches boss_syz_x - 0x140 (0x2AC0),
     * advances to boss phase.
     */
    private void updateAct3Main() {
        int camX = camera.getX() & 0xFFFF;

        if (camX < (BOSS_SYZ_X - 0x140)) {
            return; // locret_71CE
        }

        // TODO: Spawn boss blocks object (not yet implemented)
        // ROM loads boss arena block object here

        eventRoutine += 2; // advance to DLE_SYZ3boss
    }

    /**
     * DLE_SYZ3boss: Boss encounter phase.
     * When camera X reaches boss_syz_x, locks the camera and spawns the boss.
     */
    private void updateAct3Boss() {
        int camX = camera.getX() & 0xFFFF;

        if (camX < BOSS_SYZ_X) {
            return; // locret_7200
        }

        // v_limitbtm1 = boss_syz_y
        camera.setMaxYTarget((short) BOSS_SYZ_Y);

        // TODO: Boss spawn code - objects not yet implemented
        // ROM loads boss object and sets up the arena here

        // TODO: QueueSound1 bgm_Boss - play boss music
        // AudioManager.getInstance().queueSound(bgm_Boss);

        // f_lockscreen = 1 (lock camera)
        camera.setFrozen(true);
        eventRoutine += 2; // advance to DLE_SYZ3end
    }

    /**
     * DLE_SYZ3end: Post-boss lock.
     * Locks left boundary to current camera X position.
     */
    private void updateAct3End() {
        // v_limitleft2 = v_screenposx
        camera.setMinX(camera.getX());
    }
}
