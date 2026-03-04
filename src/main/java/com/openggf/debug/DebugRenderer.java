package com.openggf.debug;

import com.openggf.game.GameServices;
import com.openggf.game.GameModuleRegistry;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.slotmachine.CNZSlotMachineRenderer;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.TouchResponseDebugHit;
import com.openggf.level.objects.TouchResponseDebugState;
import com.openggf.physics.Direction;
import com.openggf.physics.Sensor;
import com.openggf.physics.SensorResult;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.SensorConfiguration;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.debug.playback.PlaybackDebugManager;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DebugRenderer {
	private static DebugRenderer debugRenderer;
	// private final GraphicsManager graphicsManager = GraphicsManager
	// .getInstance();
	private final SpriteManager spriteManager = SpriteManager.getInstance();
	private final LevelManager levelManager = LevelManager.getInstance();
        private final SonicConfigurationService configService = SonicConfigurationService
                        .getInstance();
        private final DebugOverlayManager overlayManager = GameServices.debugOverlay();
        private final PlaybackDebugManager playbackDebugManager = PlaybackDebugManager.getInstance();
        private GlyphBatchRenderer glyphBatch;
        private PerformancePanelRenderer performancePanelRenderer;
        private static final String[] SENSOR_LABELS = {"A", "B", "C", "D", "E", "F"};

        // Font size constants for different debug text categories
        private static final FontSize SENSOR_FONT = FontSize.SMALL;      // 10pt - sensor labels
        private static final FontSize OBJECT_LABEL_FONT = FontSize.MEDIUM; // 11pt - object labels
        private static final FontSize PANEL_FONT = FontSize.LARGE;        // 12pt - main status panels

        // Cached Color objects to avoid per-frame allocations
        private static final Color COLOR_PLANE_SWITCH = new Color(255, 140, 0);
        private static final Color COLOR_TOUCH_PANEL = new Color(180, 255, 180);
        private static final Color COLOR_OBJECT_SUBTYPE = new Color(255, 180, 255);
        private static final Color COLOR_ART_VIEWER = new Color(180, 255, 180);

        // Reusable lists for panel rendering to avoid per-frame allocations
        private final List<String> playerStatusLines = new ArrayList<>(24);
        private final List<String> touchResponseLines = new ArrayList<>(20);
        private final List<String> artViewerLines = new ArrayList<>(16);

        // Reusable StringBuilders for string construction
        private final StringBuilder stateFlagsBuilder = new StringBuilder(64);
        private final StringBuilder objectLabelBuilder = new StringBuilder(32);
        private final StringBuilder sensorLabelBuilder = new StringBuilder(32);
        private final StringBuilder panelLineBuilder = new StringBuilder(64);

        private final int baseWidth = configService
                        .getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
        private final int baseHeight = configService
                        .getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);
        private int viewportWidth = baseWidth;
        private int viewportHeight = baseHeight;
        private double scaleX = 1.0;
        private double scaleY = 1.0;

	private String sonicCode = configService
			.getString(SonicConfiguration.MAIN_CHARACTER_CODE);

	/**
	 * Eagerly initializes the glyph batch renderer.
	 * Call this BEFORE the main loop starts to avoid macOS freeze issues.
	 * The GlyphAtlas uses Java2D which conflicts with GLFW's event loop.
	 */
	public void eagerInit() {
		if (glyphBatch == null) {
			float scale = (float) Math.max(scaleX, scaleY);
			glyphBatch = new GlyphBatchRenderer();
			glyphBatch.init(new Font("SansSerif", Font.PLAIN, 11), scale);
		}
	}

	public void renderDebugInfo() {
                // Lazy initialization of glyph batch renderer
                float scale = (float) Math.max(scaleX, scaleY);
                if (glyphBatch == null) {
                        glyphBatch = new GlyphBatchRenderer();
                        glyphBatch.init(new Font("SansSerif", Font.PLAIN, 11), scale);
                }
                if (!glyphBatch.isInitialized()) {
                        return;
                }
                // Reinitialize if scale changed significantly (e.g., window resize)
                glyphBatch.updateScale(new Font("SansSerif", Font.PLAIN, 11), scale);
                glyphBatch.updateViewport(viewportWidth, viewportHeight);

                glyphBatch.begin();

                boolean showOverlay = overlayManager.isEnabled(DebugOverlayToggle.OVERLAY);
                boolean showShortcuts = overlayManager.isEnabled(DebugOverlayToggle.SHORTCUTS);

                // Always show playback status panel when playback is active/loaded,
                // even if the normal overlay is toggled off.
                renderPlaybackPanel();

                if (!showOverlay) {
                        if (showShortcuts) {
                                renderOverlayShortcuts(true);
                        }
                        glyphBatch.end();
                        return;
                }

                if (showShortcuts) {
                        renderOverlayShortcuts(false);
                }

                Sprite sprite = spriteManager.getSprite(sonicCode);
                AbstractPlayableSprite playable = null;
                if (sprite != null) {
                        int ringCount = 0;
                        if (sprite instanceof AbstractPlayableSprite casted) {
                                playable = casted;
                                ringCount = casted.getRingCount();
                                if (overlayManager.isEnabled(DebugOverlayToggle.PLAYER_PANEL)) {
                                        renderPlayerStatusPanel(casted, ringCount);
                                }
                                if (overlayManager.isEnabled(DebugOverlayToggle.TOUCH_RESPONSE)) {
                                        renderTouchResponsePanel(casted);
                                }
                        }
                }
                if (overlayManager.isEnabled(DebugOverlayToggle.OBJECT_ART_VIEWER)) {
                        renderObjectArtViewerPanel();
                }
                // Render sensor labels
                if (playable != null && overlayManager.isEnabled(DebugOverlayToggle.SENSOR_LABELS)) {
                        Sensor[] sensors = playable.getAllSensors();
                        for (int i = 0; i < sensors.length && i < SENSOR_LABELS.length; i++) {
                                Sensor sensor = sensors[i];
                                if (sensor == null) {
                                        continue;
                                }
                                SensorResult result = sensor.getCurrentResult();
                                if (sensor.isActive() && result != null) {
                                        Camera camera = Camera.getInstance();

                                        SensorConfiguration sensorConfiguration = SpriteManager
                                                        .getSensorConfigurationForGroundModeAndDirection(
                                                                        playable.getGroundMode(),
                                                                        sensor.getDirection());
                                        Direction globalDirection = sensorConfiguration.direction();
                                        sensor.computeRotatedOffset();

                                        short worldX = (short) (playable.getCentreX() + sensor.getRotatedX());
                                        short worldY = (short) (playable.getCentreY() + sensor.getRotatedY());
                                        short xAdjusted = (short) (worldX - camera.getX());
                                        short yAdjusted = (short) (worldY - camera.getY());

                                        sensorLabelBuilder.setLength(0);
                                        sensorLabelBuilder.append(SENSOR_LABELS[i]).append('(')
                                                        .append(globalDirection.name().charAt(0))
                                                        .append(") d:").append(result.distance())
                                                        .append(" a:");
                                        DebugRenderContext.appendHex2(sensorLabelBuilder, result.angle() & 0xFF);
                                        String label = sensorLabelBuilder.toString();

                                        Color sensorColor = DebugOverlayPalette.sensorLabelColor(i, true);
                                        int screenX = toScreenX(xAdjusted);
                                        int screenY = toScreenYFromWorld(yAdjusted);
                                        int offsetX = 0;
                                        int offsetY = 0;
                                        int stackOffset = (i % 2 == 0) ? 0 : uiY(6);
                                        switch (globalDirection) {
                                                case DOWN -> offsetY = uiY(10) + stackOffset;
                                                case UP -> offsetY = -uiY(10) - stackOffset;
                                                case LEFT -> {
                                                        offsetX = -uiX(32);
                                                        offsetY = stackOffset;
                                                }
                                                case RIGHT -> {
                                                        offsetX = uiX(6);
                                                        offsetY = stackOffset;
                                                }
                                        }
                                        glyphBatch.drawTextOutlined(label, screenX + offsetX, screenY + offsetY, sensorColor, SENSOR_FONT);
                                }
                        }
                }

                // Render object labels
                if (overlayManager.isEnabled(DebugOverlayToggle.OBJECT_LABELS)) {
                        renderObjectLabels();
                }

                // Render player plane state
                if (overlayManager.isEnabled(DebugOverlayToggle.PLANE_SWITCHERS)) {
                        renderPlayerPlaneState();
                }

                // Render per-object debug text labels
                if (overlayManager.isEnabled(DebugOverlayToggle.OBJECT_DEBUG)) {
                        renderObjectDebugLabels();
                }

                // End the batch - single draw call for all text
                glyphBatch.end();

                // Render performance panel (requires separate GL calls for pie chart)
                if (overlayManager.isEnabled(DebugOverlayToggle.PERFORMANCE)) {
                        renderPerformancePanel();
                }
        }

        private void renderObjectLabels() {
                if (!glyphBatch.isBatchActive()) {
                        return;
                }
                ObjectRegistry registry = GameModuleRegistry.getCurrent().createObjectRegistry();
                java.util.Collection<ObjectSpawn> spawns = levelManager.getActiveObjectSpawns();
                if (registry == null || spawns.isEmpty()) {
                        return;
                }
                Camera camera = Camera.getInstance();

                for (ObjectSpawn spawn : spawns) {
                        int screenX = spawn.x() - camera.getX();
                        int screenY = spawn.y() - camera.getY();

                        if (screenX < -8 || screenX > baseWidth + 8) {
                                continue;
                        }
                        if (screenY < -8 || screenY > baseHeight + 8) {
                                continue;
                        }

                        String name = registry.getPrimaryName(spawn.objectId());
                        objectLabelBuilder.setLength(0);
                        DebugRenderContext.appendHex2(objectLabelBuilder, spawn.objectId());
                        objectLabelBuilder.append(':');
                        DebugRenderContext.appendHex2(objectLabelBuilder, spawn.subtype());
                        if (spawn.renderFlags() != 0) {
                                objectLabelBuilder.append(" F").append(Integer.toHexString(spawn.renderFlags()).toUpperCase());
                        }
                        if (spawn.respawnTracked()) {
                                objectLabelBuilder.append(" R");
                        }
                        String line1 = objectLabelBuilder.toString();
                        int rawFlags = spawn.rawFlags() >> 12;
                        String line2 = rawFlags != 0
                                        ? ("YF:" + Integer.toHexString(rawFlags).toUpperCase())
                                        : null;

                        int labelX = toScreenX(screenX + 2);
                        int labelY = toScreenYFromWorld(screenY) + uiY(2);
                        int lineHeight = glyphBatch.getLineHeight(OBJECT_LABEL_FONT);
                        glyphBatch.drawTextOutlined(name, labelX, labelY - lineHeight, Color.WHITE, OBJECT_LABEL_FONT);
                        glyphBatch.drawTextOutlined(line1, labelX, labelY, Color.MAGENTA, OBJECT_LABEL_FONT);
                        if (line2 != null) {
                                glyphBatch.drawTextOutlined(line2, labelX, labelY + lineHeight,
                                                COLOR_OBJECT_SUBTYPE, OBJECT_LABEL_FONT);
                        }
                }

                if (!overlayManager.isEnabled(DebugOverlayToggle.PLANE_SWITCHERS)) {
                        return;
                }
                int planeSwitcherObjectId = GameModuleRegistry.getCurrent().getPlaneSwitcherObjectId();
                if (levelManager.getGameModule() != null) {
                        planeSwitcherObjectId = levelManager.getGameModule().getPlaneSwitcherObjectId();
                }
                for (ObjectSpawn spawn : spawns) {
                        if (spawn.objectId() != planeSwitcherObjectId) {
                                continue;
                        }
                        int screenX = spawn.x() - camera.getX();
                        int screenY = spawn.y() - camera.getY();
                        if (screenX < -8 || screenX > baseWidth + 8) {
                                continue;
                        }
                        if (screenY < -8 || screenY > baseHeight + 8) {
                                continue;
                        }

                        drawPlaneSwitcherLabels(spawn, screenX, screenY);
                }
        }

        private void renderOverlayShortcuts(boolean overlayOff) {
                List<String> lines = overlayManager.buildShortcutLines();
                if (overlayOff) {
                        lines.add(0, "Overlay Off (" + DebugOverlayToggle.OVERLAY.shortcutLabel() + ")");
                }
                int startX = uiX(baseWidth - 150);
                int startY = uiY(baseHeight - 18);
                int lineHeight = glyphBatch.getLineHeight(PANEL_FONT);
                int y = startY;
                for (String line : lines) {
                        glyphBatch.drawTextOutlined(line, startX, y, Color.WHITE, PANEL_FONT);
                        y -= lineHeight;
                }
        }

        private void drawPlaneSwitcherLabels(ObjectSpawn spawn, int screenX, int screenY) {
                int subtype = spawn.subtype();
                boolean horizontal = ObjectManager.isPlaneSwitcherHorizontal(subtype);
                String side0 = formatPlaneSwitcherSide(subtype, 0);
                String side1 = formatPlaneSwitcherSide(subtype, 1);

                if (horizontal) {
                        int aboveY = screenY - 6;
                        int belowY = screenY + 6;
                        glyphBatch.drawTextOutlined(side0,
                                        toScreenX(screenX + 2),
                                        toScreenYFromWorld(aboveY),
                                        COLOR_PLANE_SWITCH, OBJECT_LABEL_FONT);
                        glyphBatch.drawTextOutlined(side1,
                                        toScreenX(screenX + 2),
                                        toScreenYFromWorld(belowY),
                                        COLOR_PLANE_SWITCH, OBJECT_LABEL_FONT);
                } else {
                        int leftX = screenX - 16;
                        int rightX = screenX + 6;
                        glyphBatch.drawTextOutlined(side0,
                                        toScreenX(leftX), toScreenYFromWorld(screenY),
                                        COLOR_PLANE_SWITCH, OBJECT_LABEL_FONT);
                        glyphBatch.drawTextOutlined(side1,
                                        toScreenX(rightX), toScreenYFromWorld(screenY),
                                        COLOR_PLANE_SWITCH, OBJECT_LABEL_FONT);
                }
        }

        private void renderPlayerPlaneState() {
                if (!glyphBatch.isBatchActive()) {
                        return;
                }
                Sprite sprite = spriteManager.getSprite(sonicCode);
                if (!(sprite instanceof AbstractPlayableSprite playable)) {
                        return;
                }
                Camera camera = Camera.getInstance();
                int screenX = playable.getCentreX() - camera.getX();
                int screenY = playable.getY() - camera.getY();
                if (screenX < -16 || screenX > baseWidth + 16) {
                        return;
                }
                if (screenY < -16 || screenY > baseHeight + 16) {
                        return;
                }
                String label = formatLayer(playable.getLayer()) + " " + formatPriority(playable.isHighPriority());
                glyphBatch.drawTextOutlined(label,
                                toScreenX(screenX - 6),
                                toScreenYFromWorld(screenY) + uiY(8),
                                COLOR_PLANE_SWITCH, OBJECT_LABEL_FONT);
        }

        private void renderObjectDebugLabels() {
                if (!glyphBatch.isBatchActive()) {
                        return;
                }
                List<DebugRenderContext.DebugTextEntry> entries = overlayManager.getObjectDebugTextEntries();
                if (entries.isEmpty()) {
                        return;
                }
                Camera camera = Camera.getInstance();
                int lineHeight = glyphBatch.getLineHeight(OBJECT_LABEL_FONT);

                for (DebugRenderContext.DebugTextEntry entry : entries) {
                        int screenX = entry.worldX() - camera.getX();
                        int screenY = entry.worldY() - camera.getY();

                        if (screenX < -32 || screenX > baseWidth + 32) {
                                continue;
                        }
                        if (screenY < -32 || screenY > baseHeight + 32) {
                                continue;
                        }

                        int px = toScreenX(screenX + 2);
                        int py = toScreenYFromWorld(screenY) + entry.lineOffset() * lineHeight;
                        glyphBatch.drawTextOutlined(entry.text(), px, py, entry.color(), OBJECT_LABEL_FONT);
                }

                overlayManager.clearObjectDebugTextEntries();
        }

        private void renderPlayerStatusPanel(AbstractPlayableSprite sprite, int ringCount) {
                playerStatusLines.clear();
                List<String> lines = playerStatusLines;
                int angleByte = sprite.getAngle() & 0xFF;
                float angleDeg = ((256 - angleByte) * 360f / 256f) % 360f;

                lines.add("== PLAYER ==");
                StringBuilder pb = panelLineBuilder;

                pb.setLength(0);
                pb.append("Pos: ").append((int) sprite.getX()).append('.');
                DebugRenderContext.appendHex2(pb, sprite.getXSubpixel() & 0xFF);
                pb.append("  ").append((int) sprite.getY()).append('.');
                DebugRenderContext.appendHex2(pb, sprite.getYSubpixel() & 0xFF);
                lines.add(pb.toString());

                pb.setLength(0);
                pb.append("Spd: X ").append(sprite.getXSpeed()).append(" (");
                DebugRenderContext.appendFixed2(pb, sprite.getXSpeed() / 256f);
                pb.append(')');
                lines.add(pb.toString());

                pb.setLength(0);
                pb.append("Spd: Y ").append(sprite.getYSpeed()).append(" (");
                DebugRenderContext.appendFixed2(pb, sprite.getYSpeed() / 256f);
                pb.append(')');
                lines.add(pb.toString());

                pb.setLength(0);
                pb.append("GSpd: ").append(sprite.getGSpeed()).append(" (");
                DebugRenderContext.appendFixed2(pb, sprite.getGSpeed() / 256f);
                pb.append(')');
                lines.add(pb.toString());

                pb.setLength(0);
                pb.append("Angle: ");
                DebugRenderContext.appendHex2(pb, angleByte);
                pb.append(" (");
                DebugRenderContext.appendFixed1(pb, angleDeg);
                pb.append("°)");
                lines.add(pb.toString());

                lines.add("Mode: " + sprite.getGroundMode());
                lines.add("Dir: " + sprite.getDirection());
                lines.add("State: " + formatStateFlags(sprite));

                pb.setLength(0);
                pb.append("Layer: ").append(formatLayer(sprite.getLayer()))
                  .append("  Prio: ").append(formatPriority(sprite.isHighPriority()));
                lines.add(pb.toString());

                pb.setLength(0);
                pb.append("Solidity: top ");
                DebugRenderContext.appendHex2(pb, sprite.getTopSolidBit() & 0xFF);
                pb.append(" lrb ");
                DebugRenderContext.appendHex2(pb, sprite.getLrbSolidBit() & 0xFF);
                lines.add(pb.toString());

                pb.setLength(0);
                pb.append("Radii: x ").append(sprite.getXRadius())
                  .append(" y ").append(sprite.getYRadius());
                lines.add(pb.toString());

                pb.setLength(0);
                pb.append("Anim: id ").append(sprite.getAnimationId())
                  .append(" frame ").append(sprite.getAnimationFrameIndex())
                  .append('/').append(sprite.getAnimationFrameCount())
                  .append(" tick ").append(sprite.getAnimationTick());
                lines.add(pb.toString());

                lines.add("MapFrame: " + sprite.getMappingFrame());
                lines.add("Rings: " + ringCount);
                lines.add("== SENSORS ==");

                Sensor[] sensors = sprite.getAllSensors();
                for (int i = 0; i < sensors.length && i < SENSOR_LABELS.length; i++) {
                        Sensor sensor = sensors[i];
                        if (sensor == null) {
                                lines.add(SENSOR_LABELS[i] + ": --");
                                continue;
                        }
                        SensorConfiguration sensorConfiguration = SpriteManager
                                        .getSensorConfigurationForGroundModeAndDirection(
                                                        sprite.getGroundMode(), sensor.getDirection());
                        String dir = sensorConfiguration.direction().name().substring(0, 1);
                        String prefix = SENSOR_LABELS[i] + "(" + dir + "): ";
                        if (!sensor.isActive()) {
                                lines.add(prefix + "--");
                                continue;
                        }
                        SensorResult result = sensor.getCurrentResult();
                        if (result == null) {
                                lines.add(prefix + "??");
                                continue;
                        }
                        pb.setLength(0);
                        pb.append(prefix).append("d:").append(result.distance()).append(" a:");
                        DebugRenderContext.appendHex2(pb, result.angle() & 0xFF);
                        lines.add(pb.toString());
                }

                int startX = uiX(6);
                int startY = uiY(baseHeight - 18);
                int lineHeight = glyphBatch.getLineHeight(PANEL_FONT);
                int y = startY;
                for (String line : lines) {
                        glyphBatch.drawTextOutlined(line, startX, y, Color.WHITE, PANEL_FONT);
                        y -= lineHeight;
                }
        }

        private void renderTouchResponsePanel(AbstractPlayableSprite sprite) {
                ObjectManager manager = levelManager.getObjectManager();
                if (manager == null || !glyphBatch.isBatchActive()) {
                        return;
                }
                TouchResponseDebugState state = manager.getTouchResponseDebugState();
                if (state == null) {
                        return;
                }

                List<TouchResponseDebugHit> hits = state.getHits();
                int hitCount = 0;
                for (TouchResponseDebugHit hit : hits) {
                        if (hit.overlapping()) {
                                hitCount++;
                        }
                }

                touchResponseLines.clear();
                List<String> lines = touchResponseLines;
                StringBuilder tb = panelLineBuilder;
                lines.add("== TOUCH RESP ==");
                String crouch = state.isCrouching() ? "C" : "-";

                tb.setLength(0);
                tb.append("Player: x ").append(state.getPlayerX())
                  .append(" y ").append(state.getPlayerY())
                  .append(" h ").append(state.getPlayerHeight())
                  .append(" yR ").append(state.getPlayerYRadius())
                  .append(' ').append(crouch);
                lines.add(tb.toString());

                tb.setLength(0);
                tb.append("Objects: ").append(hits.size())
                  .append(" Hits: ").append(hitCount);
                lines.add(tb.toString());

                ObjectRegistry registry = GameModuleRegistry.getCurrent().createObjectRegistry();
                int maxLines = 12;
                int shown = 0;
                for (TouchResponseDebugHit hit : hits) {
                        if (shown >= maxLines) {
                                break;
                        }
                        ObjectSpawn spawn = hit.spawn();
                        String name = registry.getPrimaryName(spawn.objectId());
                        if (name.length() > 12) {
                                name = name.substring(0, 12);
                        }
                        String status = hit.overlapping() ? "HIT" : "--";
                        String category = formatTouchCategory(hit.category());
                        tb.setLength(0);
                        DebugRenderContext.appendHex2(tb, spawn.objectId());
                        tb.append(':');
                        DebugRenderContext.appendHex2(tb, spawn.subtype());
                        tb.append(' ').append(status).append(' ').append(category).append(' ');
                        DebugRenderContext.appendHex2(tb, hit.sizeIndex());
                        tb.append(' ');
                        if (hit.width() < 10) tb.append(' ');
                        tb.append(hit.width()).append(',');
                        if (hit.height() < 10) tb.append(' ');
                        tb.append(hit.height()).append(' ').append(name);
                        lines.add(tb.toString());
                        shown++;
                }

                int startX = uiX(baseWidth - 240);
                int startY = uiY(baseHeight - 140);
                int lineHeight = glyphBatch.getLineHeight(PANEL_FONT);
                int y = startY;
                for (String line : lines) {
                        glyphBatch.drawTextOutlined(line, startX, y, COLOR_TOUCH_PANEL, PANEL_FONT);
                        y -= lineHeight;
                }
        }

        private void renderObjectArtViewerPanel() {
                if (!glyphBatch.isBatchActive()) {
                        return;
                }
                DebugObjectArtViewer viewer = DebugObjectArtViewer.getInstance();
                artViewerLines.clear();
                List<String> lines = artViewerLines;
                lines.add("== ART VIEWER ==");
                lines.add("Target: " + viewer.getTargetLabel());

                // Special display for CNZ slot faces
                if (viewer.isCnzSlotsMode()) {
                        lines.add("Shows all 6 slot faces");
                        lines.add("Row 1: 0-2 (Sonic,Tails,Eggman)");
                        lines.add("Row 2: 3-5 (Jackpot,Ring,Bar)");
                        lines.add("");
                        lines.add("Expected face order:");
                        for (int i = 0; i < 6; i++) {
                                String name = CNZSlotMachineRenderer.getFaceName(i);
                                String reward = CNZSlotMachineRenderer.getFaceReward(i);
                                lines.add(String.format("  %d: %s (%s)", i, name, reward));
                        }
                        lines.add("");
                        lines.add("Keys: PgUp/PgDn to switch target");
                } else {
                        lines.add("Mode: " + viewer.getViewModeLabel());
                        if (viewer.isPatternMode()) {
                                lines.add(String.format("Tile: %d", viewer.getPatternCursor()));
                                lines.add(String.format("Page: %d-%d", viewer.getPatternPageStart(),
                                                viewer.getPatternPageEnd()));
                                lines.add("Palette: " + viewer.getPaletteLabel());
                                lines.add("Keys: Arrows, Tab/M, PgUp/PgDn, 0-4");
                        } else {
                                int maxFrames = viewer.getMaxFrames();
                                if (maxFrames > 0) {
                                        lines.add(String.format("Frame: %d/%d", viewer.getFrameIndex(), maxFrames - 1));
                                } else {
                                        lines.add("Frame: --");
                                }
                                lines.add("Keys: Left/Right, Tab/M, PgUp/PgDn");
                        }
                }

                int startX = uiX(baseWidth - 160);
                int startY = uiY(baseHeight - 120);
                int lineHeight = glyphBatch.getLineHeight(PANEL_FONT);
                int y = startY;
                for (String line : lines) {
                        glyphBatch.drawTextOutlined(line, startX, y, COLOR_ART_VIEWER, PANEL_FONT);
                        y -= lineHeight;
                }
        }

        private void renderPlaybackPanel() {
                if (!glyphBatch.isBatchActive()) {
                        return;
                }
                List<String> lines = playbackDebugManager.buildOverlayLines();
                if (lines.isEmpty()) {
                        return;
                }
                int startX = uiX(baseWidth - 220);
                int startY = uiY(baseHeight - 18);
                int lineHeight = glyphBatch.getLineHeight(PANEL_FONT);
                int y = startY;
                for (String line : lines) {
                        glyphBatch.drawTextOutlined(line, startX, y, COLOR_ART_VIEWER, PANEL_FONT);
                        y -= lineHeight;
                }
        }

        private void renderPerformancePanel() {
                if (performancePanelRenderer == null) {
                        performancePanelRenderer = new PerformancePanelRenderer(baseWidth, baseHeight, glyphBatch);
                }
                performancePanelRenderer.updateViewport(viewportWidth, viewportHeight);

                ProfileSnapshot snapshot = PerformanceProfiler.getInstance().getSnapshot();
                performancePanelRenderer.render(snapshot);
        }

        private String formatStateFlags(AbstractPlayableSprite sprite) {
                stateFlagsBuilder.setLength(0);
                if (sprite.getAir()) {
                        stateFlagsBuilder.append("Air ");
                } else {
                        stateFlagsBuilder.append("Ground ");
                }
                if (sprite.getRolling()) {
                        stateFlagsBuilder.append("Roll ");
                }
                if (sprite.getSpindash()) {
                        stateFlagsBuilder.append("Spindash ");
                }
                if (sprite.getCrouching()) {
                        stateFlagsBuilder.append("Crouch ");
                }
                if (sprite.getPushing()) {
                        stateFlagsBuilder.append("Push ");
                }
                if (stateFlagsBuilder.length() == 0) {
                        return "None";
                }
                // Trim trailing space
                if (stateFlagsBuilder.charAt(stateFlagsBuilder.length() - 1) == ' ') {
                        stateFlagsBuilder.setLength(stateFlagsBuilder.length() - 1);
                }
                return stateFlagsBuilder.toString();
        }

        private String formatPlaneSwitcherSide(int subtype, int side) {
                int path = ObjectManager.decodePlaneSwitcherPath(subtype, side);
                boolean highPriority = ObjectManager.decodePlaneSwitcherPriority(subtype, side);
                return formatLayer((byte) path) + " " + formatPriority(highPriority);
        }

        private char formatLayer(byte layer) {
                return ObjectManager.formatPlaneSwitcherLayer(layer);
        }

        private char formatPriority(boolean highPriority) {
                return ObjectManager.formatPlaneSwitcherPriority(highPriority);
        }

        private String formatTouchCategory(TouchCategory category) {
                if (category == null) {
                        return "?";
                }
                return switch (category) {
                        case ENEMY -> "E";
                        case SPECIAL -> "S";
                        case HURT -> "H";
                        case BOSS -> "B";
                };
        }

        public static synchronized DebugRenderer getInstance() {
                if (debugRenderer == null) {
                        debugRenderer = new DebugRenderer();
                }
                return debugRenderer;
        }

        public void updateViewport(int viewportWidth, int viewportHeight) {
                if (viewportWidth <= 0 || viewportHeight <= 0) {
                        return;
                }
                this.viewportWidth = viewportWidth;
                this.viewportHeight = viewportHeight;
                this.scaleX = viewportWidth / (double) baseWidth;
                this.scaleY = viewportHeight / (double) baseHeight;
        }

        private int uiX(int gameX) {
                return toScreenX(gameX);
        }

        private int uiY(int gameY) {
                return toScreenY(gameY);
        }

        private int toScreenX(int gameX) {
                return (int) Math.round(gameX * scaleX);
        }

        private int toScreenY(int gameY) {
                return (int) Math.round(gameY * scaleY);
        }

        private int toScreenYFromWorld(int worldY) {
                return viewportHeight - toScreenY(worldY);
        }
}
