package com.openggf.game.sonic1.dataselect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.version.AppVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TestS1DataSelectImageCacheManager {
    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private final SonicConfigurationService config = SonicConfigurationService.getInstance();

    @AfterEach
    void resetConfig() {
        config.resetToDefaults();
    }

    @Test
    void manifestContractSerializesExpectedFields() throws Exception {
        S1DataSelectImageManifest manifest = new S1DataSelectImageManifest(
                AppVersion.get(),
                1,
                "0123456789abcdef",
                "2026-04-14T12:34:56Z",
                8,
                Map.of("ghz1", "ghz1.png"));

        JsonNode json = mapper.valueToTree(manifest);

        assertEquals(AppVersion.get(), json.path("engineVersion").asText());
        assertEquals(1, json.path("generatorFormatVersion").asInt());
        assertEquals("0123456789abcdef", json.path("romSha256").asText());
        assertEquals("2026-04-14T12:34:56Z", json.path("generatedAt").asText());
        assertEquals(8, json.path("settleFrames").asInt());
        assertEquals("ghz1.png", json.path("zones").path("ghz1").asText());
    }

    @Test
    void cacheInvalidWhenOverrideEnabled() throws Exception {
        writeManifest("sha-override", Map.of("ghz1", writeZonePng("ghz1.png")));
        config.setConfigValue(SonicConfiguration.CROSS_GAME_S1_DATA_SELECT_IMAGE_GEN_OVERRIDE, true);

        S1DataSelectImageCacheManager manager = new S1DataSelectImageCacheManager(
                tempDir,
                config,
                () -> "sha-override",
                null,
                mapper);

        assertFalse(manager.cacheValid());
    }

    @Test
    void cacheInvalidWhenManifestIsMissing() {
        S1DataSelectImageCacheManager manager = new S1DataSelectImageCacheManager(
                tempDir,
                config,
                () -> "sha-missing",
                null,
                mapper);

        assertFalse(manager.cacheValid());
    }

    @Test
    void cacheInvalidWhenManifestEngineVersionDoesNotMatchAppVersion() throws Exception {
        writeManifest("sha-version", Map.of("ghz1", writeZonePng("ghz1.png")), "0.0.0-test");

        S1DataSelectImageCacheManager manager = new S1DataSelectImageCacheManager(
                tempDir,
                config,
                () -> "sha-version",
                null,
                mapper);

        assertFalse(manager.cacheValid());
    }

    @Test
    void cacheInvalidWhenRomShaDoesNotMatchManifest() throws Exception {
        writeManifest("manifest-sha", Map.of("ghz1", writeZonePng("ghz1.png")));

        S1DataSelectImageCacheManager manager = new S1DataSelectImageCacheManager(
                tempDir,
                config,
                () -> "different-sha",
                null,
                mapper);

        assertFalse(manager.cacheValid());
    }

    @Test
    void cacheInvalidWhenAnyZonePngIsMissing() throws Exception {
        writeManifest("sha-zone-missing", Map.of("ghz1", "missing-ghz1.png"));

        S1DataSelectImageCacheManager manager = new S1DataSelectImageCacheManager(
                tempDir,
                config,
                () -> "sha-zone-missing",
                null,
                mapper);

        assertFalse(manager.cacheValid());
    }

    @Test
    void settleFramesAcceptsConfiguredPositiveValue() {
        config.setConfigValue(SonicConfiguration.CROSS_GAME_S1_DATA_SELECT_IMAGE_GEN_SETTLE_FRAMES, 8);

        S1DataSelectImageCacheManager manager = new S1DataSelectImageCacheManager(
                tempDir,
                config,
                () -> "sha-settle",
                null,
                mapper);

        assertEquals(8, manager.settleFrames());
    }

    @Test
    void settleFramesClampsNegativeConfiguredValueToZero() {
        config.setConfigValue(SonicConfiguration.CROSS_GAME_S1_DATA_SELECT_IMAGE_GEN_SETTLE_FRAMES, -4);

        S1DataSelectImageCacheManager manager = new S1DataSelectImageCacheManager(
                tempDir,
                config,
                () -> "sha-settle-negative",
                null,
                mapper);

        assertEquals(0, manager.settleFrames());
    }

    private void writeManifest(String romSha256, Map<String, String> zones) throws IOException {
        writeManifest(romSha256, zones, AppVersion.get());
    }

    private void writeManifest(String romSha256, Map<String, String> zones, String engineVersion) throws IOException {
        S1DataSelectImageManifest manifest = new S1DataSelectImageManifest(
                engineVersion,
                1,
                romSha256,
                "2026-04-14T12:34:56Z",
                8,
                zones);
        mapper.writerWithDefaultPrettyPrinter().writeValue(tempDir.resolve("manifest.json").toFile(), manifest);
    }

    private String writeZonePng(String fileName) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, "png");
        return file.getFileName().toString();
    }
}
