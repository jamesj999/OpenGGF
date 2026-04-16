package com.openggf.game.sonic3k;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.fail;

class TestS3kRuntimeStateReadGuard {

    @Test
    void zoneEvents_shouldResolvePlayerCharacterThroughSharedRuntimeStateAdapter() throws IOException {
        String file = "src/main/java/com/openggf/game/sonic3k/events/Sonic3kZoneEvents.java";
        String content = Files.readString(Path.of(file));
        List<String> violations = new ArrayList<>();

        if (!content.contains("S3kRuntimeStates.resolvePlayerCharacter(")) {
            violations.add(file + " does not resolve player character through S3kRuntimeStates.resolvePlayerCharacter(...)");
        }
        if (content.contains("getPlayerCharacter()")) {
            violations.add(file + " still calls Sonic3kLevelEventManager.getPlayerCharacter()");
        }

        if (!violations.isEmpty()) {
            fail("S3K zone events should resolve player character through shared runtime state adapters:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void aizFallingLog_shouldReadIntroTriggerFromAizRuntimeState() throws IOException {
        String file = "src/main/java/com/openggf/game/sonic3k/objects/AizFallingLogObjectInstance.java";
        String content = Files.readString(Path.of(file));
        List<String> violations = new ArrayList<>();

        if (content.contains("Sonic3kLevelEventManager")) {
            violations.add(file + " still references Sonic3kLevelEventManager directly");
        }
        if (!content.contains("S3kRuntimeStates.currentAiz(")) {
            violations.add(file + " does not read AIZ state through S3kRuntimeStates.currentAiz(...)");
        }

        if (!violations.isEmpty()) {
            fail("AIZ falling log should read intro/runtime state through the shared zone runtime adapter:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }

    @Test
    void ssEntryRing_shouldSaveResizeRoutineFromZoneRuntimeState() throws IOException {
        String file = "src/main/java/com/openggf/game/sonic3k/objects/Sonic3kSSEntryRingObjectInstance.java";
        String content = Files.readString(Path.of(file));
        List<String> violations = new ArrayList<>();

        if (content.contains("Sonic3kLevelEventManager")) {
            violations.add(file + " still references Sonic3kLevelEventManager directly");
        }
        if (!content.contains("services().zoneRuntimeState()")) {
            violations.add(file + " does not read the resize routine from services().zoneRuntimeState()");
        }

        if (!violations.isEmpty()) {
            fail("S3K special-stage entry rings should save resize state through the runtime registry:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }
}
