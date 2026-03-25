package com.openggf.debug;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TestDebugOverlayManagerReset {

    @Test
    public void testResetStateRestoresToggleDefaults() {
        DebugOverlayManager manager = DebugOverlayManager.getInstance();

        for (DebugOverlayToggle toggle : DebugOverlayToggle.values()) {
            manager.setEnabled(toggle, !toggle.defaultEnabled());
        }

        manager.resetState();

        for (DebugOverlayToggle toggle : DebugOverlayToggle.values()) {
            assertEquals("resetState should restore default for " + toggle.name(),
                    toggle.defaultEnabled(), manager.isEnabled(toggle));
        }
    }
}
