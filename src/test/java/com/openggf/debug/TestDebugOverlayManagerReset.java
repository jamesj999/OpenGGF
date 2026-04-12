package com.openggf.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    public void buildShortcutLines_reusesCachedStringsUntilToggleStateChanges() {
        DebugOverlayManager manager = DebugOverlayManager.getInstance();
        manager.resetState();

        String initialOverlayLine = manager.buildShortcutLines().get(DebugOverlayToggle.OVERLAY.ordinal());
        String repeatedOverlayLine = manager.buildShortcutLines().get(DebugOverlayToggle.OVERLAY.ordinal());

        assertSame(initialOverlayLine, repeatedOverlayLine,
                "shortcut text should be reused when overlay state is unchanged");

        manager.setEnabled(DebugOverlayToggle.OVERLAY, !DebugOverlayToggle.OVERLAY.defaultEnabled());
        String updatedOverlayLine = manager.buildShortcutLines().get(DebugOverlayToggle.OVERLAY.ordinal());

        assertNotSame(initialOverlayLine, updatedOverlayLine,
                "changing a toggle should invalidate the cached shortcut text");
        String expectedState = DebugOverlayToggle.OVERLAY.defaultEnabled() ? "Off" : "On";
        assertTrue(updatedOverlayLine.endsWith(": " + expectedState));

        String repeatedUpdatedLine = manager.buildShortcutLines().get(DebugOverlayToggle.OVERLAY.ordinal());
        assertSame(updatedOverlayLine, repeatedUpdatedLine,
                "updated shortcut text should also be reused until the next toggle change");

        manager.resetState();
    }
}


