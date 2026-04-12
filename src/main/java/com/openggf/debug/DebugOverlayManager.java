package com.openggf.debug;

import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.logging.Logger;

import static org.lwjgl.glfw.GLFW.*;

public class DebugOverlayManager {
    private static final Logger LOGGER = Logger.getLogger(DebugOverlayManager.class.getName());
    private static DebugOverlayManager debugOverlayManager;

    private final EnumMap<DebugOverlayToggle, Boolean> states = new EnumMap<>(DebugOverlayToggle.class);

    /** GLFW window handle for clipboard operations - set by Engine */
    private long windowHandle;

    /** Reusable list for shortcut lines to avoid per-frame allocations */
    private final List<String> shortcutLines = new ArrayList<>(16);
    private boolean shortcutLinesDirty = true;

    /** Per-frame text entries from object debug rendering, set by LevelManager, read by DebugRenderer */
    private List<DebugRenderContext.DebugTextEntry> pendingObjectDebugText = List.of();
    private final DebugObjectArtViewer objectArtViewer = new DebugObjectArtViewer();

    private DebugOverlayManager() {
        for (DebugOverlayToggle toggle : DebugOverlayToggle.values()) {
            states.put(toggle, toggle.defaultEnabled());
        }
    }

    public static synchronized DebugOverlayManager getInstance() {
        if (debugOverlayManager == null) {
            debugOverlayManager = new DebugOverlayManager();
        }
        return debugOverlayManager;
    }

    public void updateInput(InputHandler handler) {
        if (handler == null) {
            return;
        }
        for (DebugOverlayToggle toggle : DebugOverlayToggle.values()) {
            if (handler.isKeyPressed(toggle.keyCode())) {
                setEnabled(toggle, !isEnabled(toggle));
            }
        }

        // Ctrl+P copies performance stats to clipboard
        if (handler.isKeyDown(GLFW_KEY_LEFT_CONTROL) && handler.isKeyPressed(GLFW_KEY_P)) {
            copyPerformanceStatsToClipboard();
        }
    }

    private void copyPerformanceStatsToClipboard() {
        StringBuilder sb = new StringBuilder();

        // Performance profiler stats
        ProfileSnapshot snapshot = GameServices.profiler().getSnapshot();
        if (snapshot.hasData()) {
            sb.append("=== Performance Stats ===\n");
            sb.append(String.format("Frame Time: %.2fms (%.1f%% of 16.67ms budget)\n",
                    snapshot.totalFrameTimeMs(),
                    (snapshot.totalFrameTimeMs() / 16.67) * 100));
            sb.append(String.format("FPS: %.1f\n\n", snapshot.fps()));

            sb.append("Section Timings:\n");
            for (SectionStats section : snapshot.getSectionsSortedByTime()) {
                sb.append(String.format("  %-12s %6.2fms (%5.1f%%)\n",
                        section.name(), section.timeMs(), section.percentage()));
            }
            sb.append("\n");
        }

        // Memory stats
        MemoryStats.Snapshot memSnapshot = GameServices.profiler().memoryStats().snapshot();
        sb.append("=== Memory Stats ===\n");
        sb.append(String.format("Heap: %.0fMB / %.0fMB (%d%%)\n",
                memSnapshot.heapUsedMB(), memSnapshot.heapMaxMB(), memSnapshot.heapPercentage()));
        sb.append(String.format("GC Collections: %d (total time: %dms)\n",
                memSnapshot.gcCount(), memSnapshot.gcTimeMs()));
        sb.append(String.format("Allocation Rate: %.2fMB/s\n\n", memSnapshot.allocationRateMBPerSec()));

        List<MemoryStats.SectionAllocation> topAllocators = memSnapshot.topAllocators();
        if (!topAllocators.isEmpty()) {
            sb.append("Top Allocators (per frame avg):\n");
            for (MemoryStats.SectionAllocation alloc : topAllocators) {
                sb.append(String.format("  %-12s %8.1fKB\n", alloc.name(), alloc.kbPerFrame()));
            }
        }

        // Copy to clipboard using GLFW (avoids AWT dependency for native images)
        if (windowHandle != 0) {
            glfwSetClipboardString(windowHandle, sb.toString());
            LOGGER.info("Performance stats copied to clipboard");
        } else {
            LOGGER.warning("Cannot copy to clipboard: window handle not set");
        }
    }

    /**
     * Sets the GLFW window handle for clipboard operations.
     * Must be called after window creation.
     */
    public void setWindowHandle(long windowHandle) {
        this.windowHandle = windowHandle;
    }

    public boolean isEnabled(DebugOverlayToggle toggle) {
        return states.getOrDefault(toggle, Boolean.TRUE);
    }

    public void setEnabled(DebugOverlayToggle toggle, boolean enabled) {
        Boolean previous = states.put(toggle, enabled);
        if (previous == null || previous != enabled) {
            shortcutLinesDirty = true;
        }
    }

    public void setObjectDebugTextEntries(List<DebugRenderContext.DebugTextEntry> entries) {
        this.pendingObjectDebugText = entries;
    }

    public List<DebugRenderContext.DebugTextEntry> getObjectDebugTextEntries() {
        return pendingObjectDebugText;
    }

    public DebugObjectArtViewer getObjectArtViewer() {
        return objectArtViewer;
    }

    public void clearObjectDebugTextEntries() {
        pendingObjectDebugText = List.of();
    }

    public void resetState() {
        states.clear();
        for (DebugOverlayToggle toggle : DebugOverlayToggle.values()) {
            states.put(toggle, toggle.defaultEnabled());
        }
        pendingObjectDebugText = List.of();
        shortcutLinesDirty = true;
    }

    public List<String> buildShortcutLines() {
        if (shortcutLinesDirty) {
            shortcutLines.clear();
            for (DebugOverlayToggle toggle : DebugOverlayToggle.values()) {
                String state = isEnabled(toggle) ? "On" : "Off";
                shortcutLines.add(toggle.shortcutLabel() + " " + toggle.label() + ": " + state);
            }
            shortcutLinesDirty = false;
        }
        return shortcutLines;
    }
}
