package com.openggf.game.dataselect;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.DebugColor;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.save.SaveManager;
import com.openggf.game.save.SaveSlotState;
import com.openggf.game.save.SaveSlotSummary;
import com.openggf.game.save.SelectedTeam;
import com.openggf.graphics.PixelFontTextRenderer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/**
 * Shared text-driven Data Select implementation used by multiple game modules.
 * It focuses on slot/session correctness first; ROM-accurate art can replace it later.
 */
public class SimpleDataSelectManager extends AbstractDataSelectProvider {

    private static final int TITLE_X = 24;
    private static final int TITLE_Y = 20;
    private static final int ROW_X = 24;
    private static final int FIRST_ROW_Y = 56;
    private static final int FOOTER_Y = 196;

    private final DataSelectHostProfile hostProfile;
    private final SaveManager saveManager;
    private final SonicConfigurationService config;
    private final PixelFontTextRenderer textRenderer = new PixelFontTextRenderer();

    public SimpleDataSelectManager(DataSelectHostProfile hostProfile, DataSelectSessionController controller) {
        this(hostProfile, Path.of("saves"), RuntimeManager.currentEngineServices().configuration(), controller);
    }

    public SimpleDataSelectManager(DataSelectHostProfile hostProfile, Path saveRoot,
                                   SonicConfigurationService config,
                                   DataSelectSessionController controller) {
        this.hostProfile = hostProfile;
        this.saveManager = new SaveManager(saveRoot);
        this.config = config;
        attachSessionController(controller);
    }

    @Override
    public void initialize() {
        sessionController.reset();
        sessionController.loadAvailableTeams(
                config.getString(SonicConfiguration.DATA_SELECT_EXTRA_PLAYER_COMBOS));
        reloadSlotSummaries();
        state = State.FADE_IN;
    }

    @Override
    public void update(InputHandler input) {
        if (state == State.FADE_IN) {
            state = State.ACTIVE;
        }
        if (state != State.ACTIVE) {
            return;
        }

        int upKey = config.getInt(SonicConfiguration.UP);
        int downKey = config.getInt(SonicConfiguration.DOWN);
        int leftKey = config.getInt(SonicConfiguration.LEFT);
        int rightKey = config.getInt(SonicConfiguration.RIGHT);
        int jumpKey = config.getInt(SonicConfiguration.JUMP);

        if (input.isKeyPressed(upKey)) {
            sessionController.moveSelection(-1);
            return;
        }
        if (input.isKeyPressed(downKey)) {
            sessionController.moveSelection(1);
            return;
        }
        if (input.isKeyPressed(leftKey)) {
            if (sessionController.shouldCycleClearRestart()) {
                sessionController.cycleClearRestart(-1);
            } else {
                sessionController.cycleTeam(-1);
            }
            return;
        }
        if (input.isKeyPressed(rightKey)) {
            if (sessionController.shouldCycleClearRestart()) {
                sessionController.cycleClearRestart(1);
            } else {
                sessionController.cycleTeam(1);
            }
            return;
        }
        if (input.isKeyPressed(GLFW_KEY_ESCAPE)) {
            sessionController.dismissDeleteMode();
            return;
        }
        if (input.isKeyPressed(jumpKey)) {
            handleControllerAction(sessionController.confirmSelection());
        }
    }

    @Override
    public void draw() {
        textRenderer.setProjectionMatrix(GameServices.graphics().getProjectionMatrixBuffer());
        textRenderer.drawShadowedText(hostProfile.gameCode().toUpperCase() + " DATA SELECT",
                TITLE_X, TITLE_Y, DebugColor.YELLOW);
        textRenderer.drawShadowedText(headerLabel(),
                TITLE_X, TITLE_Y + textRenderer.lineHeight(), DebugColor.CYAN);

        for (int row = 0; row < sessionController.totalRows(); row++) {
            DebugColor color = row == menuModel().getSelectedRow() ? DebugColor.YELLOW : DebugColor.WHITE;
            textRenderer.drawShadowedText(rowLabel(row), ROW_X,
                    FIRST_ROW_Y + row * textRenderer.lineHeight(), color);
        }

        String footer = menuModel().isDeleteMode()
                ? "DELETE MODE: choose a slot and press jump"
                : sessionController.shouldCycleClearRestart()
                ? "Arrows move, left/right changes clear restart, jump confirms"
                : "Arrows move, left/right changes team, jump confirms";
        textRenderer.drawShadowedText(footer, TITLE_X, FOOTER_Y, DebugColor.LIGHT_GRAY);
    }

    @Override
    public void setClearColor() {
        org.lwjgl.opengl.GL11.glClearColor(0.06f, 0.08f, 0.14f, 1.0f);
    }

    @Override
    public void reset() {
        state = State.INACTIVE;
        if (sessionController != null) {
            sessionController.reset();
        }
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public boolean isExiting() {
        return state == State.EXITING;
    }

    @Override
    public boolean isActive() {
        return state != State.INACTIVE;
    }

    public List<SaveSlotSummary> slotSummaries() {
        return Collections.unmodifiableList(sessionController.slotSummaries());
    }

    protected SelectedTeam currentTeam() {
        return sessionController.currentTeam();
    }

    protected DataSelectHostProfile hostProfile() {
        return hostProfile;
    }

    protected void reloadSlotSummaries() {
        List<SaveSlotSummary> summaries = new ArrayList<>();
        for (int slot = 1; slot <= hostProfile.slotCount(); slot++) {
            try {
                DataSelectGameProfile legacyProfile =
                        hostProfile instanceof DataSelectGameProfile gameProfile ? gameProfile : null;
                summaries.add(saveManager.readSlotSummary(hostProfile.gameCode(), slot, legacyProfile));
            } catch (IOException e) {
                summaries.add(hostProfile.summarizeFreshSlot(slot));
            }
        }
        sessionController.loadSlotSummaries(summaries);
    }

    private void handleControllerAction(DataSelectAction action) {
        if (action.type() == DataSelectActionType.DELETE_SLOT) {
            saveManager.deleteSlot(hostProfile.gameCode(), action.slot());
            reloadSlotSummaries();
            menuModel().setClearRestartIndex(0);
            return;
        }
        if (action.type() != DataSelectActionType.NONE) {
            sessionController.queuePendingAction(action);
            state = State.EXITING;
        }
    }

    private String rowLabel(int row) {
        String prefix = row == menuModel().getSelectedRow() ? "> " : "  ";
        if (row == 0) {
            return prefix + "NO SAVE";
        }
        if (row == sessionController.deleteRowIndex()) {
            return prefix + (menuModel().isDeleteMode() ? "DELETE: ON" : "DELETE");
        }

        SaveSlotSummary summary = sessionController.slotSummaries().get(row - 1);
        return prefix + "SLOT " + row + " " + summarize(summary);
    }

    private String summarize(SaveSlotSummary summary) {
        if (summary.state() == SaveSlotState.EMPTY) {
            return "EMPTY";
        }

        Map<String, Object> payload = summary.payload();
        StringBuilder builder = new StringBuilder();
        if (summary.state() == SaveSlotState.HASH_WARNING) {
            builder.append("[HASH] ");
        }
        if (Boolean.TRUE.equals(payload.get("clear"))) {
            builder.append("[CLEAR] ");
        }
        DataSelectDestination selectedClearDestination =
                summary.slot() == menuModel().getSelectedRow() ? currentClearRestartDestination() : null;
        if (selectedClearDestination != null) {
            builder.append(zoneLabel(selectedClearDestination.zone(), selectedClearDestination.act()));
        } else {
            builder.append(zoneLabel(readInt(payload, "zone", 0), readInt(payload, "act", 0)));
        }
        builder.append(" | ").append(teamLabel(teamFromPayload(payload)));

        Object lives = payload.get("lives");
        if (lives instanceof Number number) {
            builder.append(" | L").append(number.intValue());
        }
        Object emeralds = payload.get("emeraldCount");
        if (emeralds instanceof Number number) {
            builder.append(" | E").append(number.intValue());
        }
        return builder.toString();
    }

    private String headerLabel() {
        DataSelectDestination destination = currentClearRestartDestination();
        if (destination != null) {
            return "CLEAR: " + zoneLabel(destination.zone(), destination.act());
        }
        return "TEAM: " + teamLabel(currentTeam());
    }

    private String zoneLabel(int zone, int act) {
        GameModule module = GameServices.module();
        if (module == null || module.getZoneRegistry() == null) {
            return "ZONE " + zone + " ACT " + (act + 1);
        }
        String name = module.getZoneRegistry().getZoneName(zone);
        if (name == null || name.isBlank()) {
            name = "ZONE " + zone;
        }
        return name + " ACT " + (act + 1);
    }

    @SuppressWarnings("unchecked")
    private SelectedTeam teamFromPayload(Map<String, Object> payload) {
        String main = String.valueOf(payload.getOrDefault("mainCharacter", "sonic"));
        Object sidekicksRaw = payload.get("sidekicks");
        List<String> sidekicks = sidekicksRaw instanceof List<?>
                ? ((List<?>) sidekicksRaw).stream().map(String::valueOf).toList()
                : List.of();
        return new SelectedTeam(main, sidekicks);
    }

    private String teamLabel(SelectedTeam team) {
        if (team == null) {
            return "UNKNOWN";
        }
        if (team.sidekicks().isEmpty()) {
            return team.mainCharacter().toUpperCase();
        }
        return (team.mainCharacter() + "+" + String.join("+", team.sidekicks())).toUpperCase();
    }

    private DataSelectDestination currentClearRestartDestination() {
        return sessionController.currentClearRestartDestination();
    }

    private DataSelectMenuModel menuModel() {
        return sessionController.menuModel();
    }

    private static int readInt(Map<String, Object> payload, String key, int fallback) {
        Object value = payload.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
