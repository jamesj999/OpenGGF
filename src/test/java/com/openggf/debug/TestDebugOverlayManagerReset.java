package com.openggf.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDebugOverlayManagerReset {

    @Test
    public void testResetStateRestoresToggleDefaults() {
        DebugOverlayManager manager = DebugOverlayManager.getInstance();

        for (DebugOverlayToggle toggle : DebugOverlayToggle.values()) {
            manager.setEnabled(toggle, !toggle.defaultEnabled());
        }

        manager.resetState();

        for (DebugOverlayToggle toggle : DebugOverlayToggle.values()) {
            assertEquals(toggle.defaultEnabled(), manager.isEnabled(toggle), "resetState should restore default for " + toggle.name());
        }
    }
}


