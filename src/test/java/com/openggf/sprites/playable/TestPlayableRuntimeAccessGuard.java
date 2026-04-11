package com.openggf.sprites.playable;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.fail;

class TestPlayableRuntimeAccessGuard {

    private static final String[] GUARDED_FILES = {
            "src/main/java/com/openggf/sprites/playable/SidekickCpuController.java",
            "src/main/java/com/openggf/sprites/playable/DrowningController.java",
            "src/main/java/com/openggf/sprites/playable/SuperStateController.java",
            "src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java"
    };

    @Test
    void playableRuntimeSlice_shouldNotReferenceGameServicesDirectly() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String file : GUARDED_FILES) {
            Path path = Path.of(file);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            String content = Files.readString(path);
            if (content.contains("GameServices.")) {
                violations.add(file);
            }
        }

        if (!violations.isEmpty()) {
            fail("Playable runtime access should flow through sprite/controller helpers, not GameServices directly:\n  "
                    + String.join("\n  ", new TreeSet<>(violations)));
        }
    }
}


