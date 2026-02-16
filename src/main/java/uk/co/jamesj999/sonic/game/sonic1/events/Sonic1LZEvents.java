package uk.co.jamesj999.sonic.game.sonic1.events;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1SwitchManager;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Sfx;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.Map;

/**
 * Labyrinth Zone dynamic level events.
 * ROM: DLE_LZ (DynamicLevelEvents.asm)
 *
 * Acts 1-2: No events.
 * Act 3: Switch-triggered layout change + boss arena.
 *
 * Note: LZ Act 4 (SBZ3) in the ROM is handled by {@code Sonic1SBZEvents}
 * in our engine, since SBZ3 is SBZ zone act 2 rather than LZ act 3.
 */
class Sonic1LZEvents extends Sonic1ZoneEvents {
    // Layout gap position: v_lvllayout + $80*2 + 6 = FG row 2, column 6
    // This is the position modified by switch $F to open the Y-loop exit.
    private static final int LAYOUT_GAP_X = 6;
    private static final int LAYOUT_GAP_Y = 2;
    private static final int CHUNK_ID_GAP = 7;

    // Boss arena position constants (from s1disasm Constants.asm)
    // boss_lz_x = 0x1DE0, boss_lz_y = 0xC0
    private static final int BOSS_LZ_X = 0x1DE0;
    private static final int BOSS_LZ_Y = 0xC0;

    // Camera X threshold for boss spawn: boss_lz_x - 0x140
    private static final int BOSS_CAM_X_THRESHOLD = BOSS_LZ_X - 0x140; // 0x1CA0

    // Camera Y threshold: boss_lz_y + 0x540 = 0x600
    private static final int BOSS_CAM_Y_MAX = BOSS_LZ_Y + 0x540; // 0x600

    Sonic1LZEvents(Camera camera) {
        super(camera);
    }

    @Override
    void update(int act) {
        switch (act) {
            case 0, 1 -> { /* DLE_LZ12: rts */ }
            case 2 -> updateAct3();
        }
    }

    /**
     * DLE_LZ3: Act 3 event logic.
     * Two parts:
     * 1. Check if switch $F has been pressed; if so, modify level layout
     *    and play rumbling sound.
     * 2. Boss spawn check based on camera position and eventRoutine.
     */
    private void updateAct3() {
        checkSwitchF();
        checkBossSpawn();
    }

    /**
     * Switch $F layout modification (DLE_LZ3, first half).
     * When switch $F is pressed, modifies the level layout at
     * (v_lvllayout + 0x80*2 + 6) to chunk ID 7, opening a path,
     * and plays sfx_Rumbling.
     *
     * ROM: DynamicLevelEvents.asm lines 195-204:
     * <pre>
     *   tst.b   (f_switch+$F).w
     *   beq.s   loc_6F28
     *   lea     (v_lvllayout+$80*2+6).w,a1
     *   cmpi.b  #7,(a1)
     *   beq.s   loc_6F28
     *   move.b  #7,(a1)
     *   move.w  #sfx_Rumbling,d0
     *   bsr.w   QueueSound2
     * </pre>
     */
    private void checkSwitchF() {
        // tst.b (f_switch+$F).w / beq.s loc_6F28
        if (!Sonic1SwitchManager.getInstance().isPressed(0xF)) {
            return;
        }

        LevelManager lm = LevelManager.getInstance();
        Level level = lm.getCurrentLevel();
        if (level == null) {
            return;
        }

        Map map = level.getMap();
        if (map == null) {
            return;
        }

        // cmpi.b #7,(v_lvllayout+$80*2+6).w / beq.s loc_6F28
        // Layout position: FG layer (0), column 6, row 2
        int currentChunk = map.getValue(0, LAYOUT_GAP_X, LAYOUT_GAP_Y) & 0xFF;
        if (currentChunk == CHUNK_ID_GAP) {
            return; // already modified
        }

        // move.b #7,(a1)
        map.setValue(0, LAYOUT_GAP_X, LAYOUT_GAP_Y, (byte) CHUNK_ID_GAP);
        lm.invalidateForegroundTilemap();

        // move.w #sfx_Rumbling,d0 / bsr.w QueueSound2
        AudioManager.getInstance().playSfx(Sonic1Sfx.RUMBLING.id);
    }

    /**
     * Boss spawn check (DLE_LZ3, loc_6F28 onward).
     * Uses eventRoutine as a state machine:
     *   0 = waiting for camera to reach boss trigger position
     *   2+ = boss already spawned, skip further checks
     */
    private void checkBossSpawn() {
        // loc_6F28: tst.b (v_dle_routine).w
        if (eventRoutine != 0) {
            return; // locret_6F64: already past boss spawn
        }

        int camX = camera.getX() & 0xFFFF;
        int camY = camera.getY() & 0xFFFF;

        // cmpi.w #boss_lz_x-$140,(v_screenposx).w
        if (camX < BOSS_CAM_X_THRESHOLD) {
            return; // locret_6F62
        }

        // cmpi.w #boss_lz_y+$540,(v_screenposy).w
        if (camY >= BOSS_CAM_Y_MAX) {
            return; // locret_6F62: camera Y too high, skip
        }

        // TODO: Boss spawn not yet implemented
        // ROM: QueueSound1 bgm_Boss (play boss music, MUS_BOSS = 0x8C)

        // f_lockscreen = 1 (lock camera)
        camera.setFrozen(true);

        // addq.b #2,(v_dle_routine).w
        eventRoutine += 2;
    }
}
