package com.openggf.editor.render;

import com.openggf.editor.EditorHierarchyDepth;

import java.util.Objects;

public final class EditorOverlayRenderer {
    private final EditorToolbarRenderer toolbar;
    private final EditorCommandStripRenderer commandStrip;
    private final EditorWorldOverlayRenderer worldOverlay;
    private final FocusedEditorPaneRenderer focusedPane;
    private EditorHierarchyDepth hierarchyDepth = EditorHierarchyDepth.WORLD;

    public EditorOverlayRenderer() {
        this(new EditorToolbarRenderer(), new EditorCommandStripRenderer(),
                new EditorWorldOverlayRenderer(), new FocusedEditorPaneRenderer());
    }

    public EditorOverlayRenderer(EditorToolbarRenderer toolbar,
                                 EditorCommandStripRenderer commandStrip,
                                 EditorWorldOverlayRenderer worldOverlay,
                                 FocusedEditorPaneRenderer focusedPane) {
        this.toolbar = Objects.requireNonNull(toolbar, "toolbar");
        this.commandStrip = Objects.requireNonNull(commandStrip, "commandStrip");
        this.worldOverlay = Objects.requireNonNull(worldOverlay, "worldOverlay");
        this.focusedPane = Objects.requireNonNull(focusedPane, "focusedPane");
    }

    public void setHierarchyDepth(EditorHierarchyDepth hierarchyDepth) {
        this.hierarchyDepth = Objects.requireNonNull(hierarchyDepth, "hierarchyDepth");
    }

    public void render() {
        renderWorldSpaceOverlay();
        renderScreenSpaceOverlay();
    }

    public void renderWorldSpaceOverlay() {
        if (hierarchyDepth == EditorHierarchyDepth.WORLD) {
            worldOverlay.render();
        }
    }

    public void renderScreenSpaceOverlay() {
        switch (hierarchyDepth) {
            case WORLD -> renderWorldPlacementUi();
            case BLOCK -> renderFocusedBlockEdit();
            case CHUNK -> renderFocusedChunkEdit();
        }
    }

    public void renderWorldPlacementUi() {
        toolbar.render();
        commandStrip.render();
    }

    public void renderFocusedBlockEdit() {
        toolbar.render();
        focusedPane.renderBlockEditorPane();
        commandStrip.render();
    }

    public void renderFocusedChunkEdit() {
        toolbar.render();
        focusedPane.renderChunkEditorPane();
        commandStrip.render();
    }
}
