package com.openggf.tests.trace;

/**
 * Engine-side diagnostic snapshot captured per-frame during trace replay.
 * These values are NOT compared for pass/fail — they appear alongside
 * ROM trace diagnostics in the context window for human cross-referencing.
 *
 * @param routine       Engine's player routine value (S1: 0=init, 2=control, 4=hurt, 6=death)
 * @param standOnSlot   SST slot index of object the player is riding (-1 = none)
 * @param standOnType   Object type ID at that slot (-1 = none)
 * @param rings         Engine's ring count
 * @param statusByte    Engine's player status flags byte
 * @param solidEvent    Description of SolidContacts/touch event this frame, or empty
 */
public record EngineDiagnostics(
    int routine,
    int standOnSlot,
    int standOnType,
    int rings,
    int statusByte,
    String solidEvent
) {
    /** No diagnostics available. */
    public static final EngineDiagnostics EMPTY = new EngineDiagnostics(-1, -1, -1, -1, -1, "");

    /**
     * Format as a compact string for the context window.
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        if (routine >= 0) sb.append(String.format("rtn=%02X", routine));
        if (standOnSlot >= 0) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(String.format("onSlot=%d", standOnSlot));
            if (standOnType >= 0) sb.append(String.format("(0x%02X)", standOnType));
        }
        if (rings >= 0) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(String.format("rings=%d", rings));
        }
        if (statusByte >= 0) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(String.format("st=%02X", statusByte));
        }
        if (solidEvent != null && !solidEvent.isEmpty()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(solidEvent);
        }
        return sb.toString();
    }
}
