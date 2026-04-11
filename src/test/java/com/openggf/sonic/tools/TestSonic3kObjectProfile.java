package com.openggf.sonic.tools;

import org.junit.jupiter.api.Test;
import com.openggf.level.LevelData;
import com.openggf.tools.ObjectDiscoveryTool.LevelConfig;
import com.openggf.tools.Sonic3kObjectProfile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSonic3kObjectProfile {

    @Test
    public void aizMinibossIsMarkedImplementedForS3klLevelsOnly() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();
        List<LevelConfig> levels = profile.getLevels();

        LevelConfig aiz1 = levels.stream()
                .filter(level -> level.levelData() == LevelData.S3K_ANGEL_ISLAND_1)
                .findFirst()
                .orElseThrow();
        LevelConfig mhz1 = levels.stream()
                .filter(level -> level.levelData() == LevelData.S3K_MUSHROOM_HILL_1)
                .findFirst()
                .orElseThrow();

        assertTrue(profile.getImplementedIds().contains(0x91));
        assertTrue(profile.getImplementedIds(aiz1).contains(0x91));
        assertFalse(profile.getImplementedIds(mhz1).contains(0x91));
    }

    @Test
    public void hczMinibossIsMarkedImplementedForS3klLevelsOnly() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();
        List<LevelConfig> levels = profile.getLevels();

        LevelConfig hcz1 = levels.stream()
                .filter(level -> level.levelData() == LevelData.S3K_HYDROCITY_1)
                .findFirst()
                .orElseThrow();
        LevelConfig mhz1 = levels.stream()
                .filter(level -> level.levelData() == LevelData.S3K_MUSHROOM_HILL_1)
                .findFirst()
                .orElseThrow();

        assertTrue(profile.getImplementedIds().contains(0x99));
        assertTrue(profile.getImplementedIds(hcz1).contains(0x99));
        assertFalse(profile.getImplementedIds(mhz1).contains(0x99));
    }
}


