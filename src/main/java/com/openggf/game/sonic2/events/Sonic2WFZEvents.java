package com.openggf.game.sonic2.events;

import com.openggf.camera.Camera;

/**
 * Wing Fortress Zone events.
 * ROM: LevEvents_WFZ (s2.asm:21174-21310)
 *
 * WFZ boss spawns from level object layout (subtype $92), NOT from events.
 * Events handle camera boundaries and PLC loading:
 *   Routine 0: Camera X >= $2880 AND Camera Y >= $400 -> load PLC_WFZ_BOSS (62),
 *              set Camera_Min_X = $2880
 *   Routine 2: Camera Y >= $500 -> lock controls, load PLC_Tornado (63)
 *   Routine 4+: Post-boss (boss defeat handler advances this)
 */
public class Sonic2WFZEvents extends Sonic2ZoneEvents {

    public Sonic2WFZEvents(Camera camera) {
        super(camera);
    }

    @Override
    public void update(int act, int frameCounter) {
        switch (eventRoutine) {
            case 0 -> {
                // Routine 0: Boss arena trigger
                // ROM: cmp.w #$2880,(Camera_X_pos).w / bhi.s
                // ROM: cmp.w #$400,(Camera_Y_pos).w / bhi.s
                if (camera.getX() > 0x2880 && camera.getY() > 0x400) {
                    eventRoutine += 2;
                    // Lock camera min X to prevent backtracking
                    camera.setMinX((short) 0x2880);
                    // ROM: moveq #PLCID_WfzBoss,d0 / jmpto JmpTo2_LoadPLC
                    // PLC load handled by art provider system
                }
            }
            case 2 -> {
                // Routine 2: Tornado sequence trigger
                // ROM: cmp.w #$500,(Camera_Y_pos).w / bhi.s
                if (camera.getY() > 0x500) {
                    eventRoutine += 2;
                    // ROM: moveq #PLCID_Tornado,d0 / jmpto JmpTo2_LoadPLC
                    // PLC load handled by art provider system
                }
            }
            default -> {
                // Routine 4+: Post-boss / post-tornado (empty - advanced by boss/sequence)
            }
        }
    }
}
