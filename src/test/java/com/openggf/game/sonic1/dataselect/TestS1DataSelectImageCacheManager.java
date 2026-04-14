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
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;

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
                mapper);

        assertFalse(manager.cacheValid());
    }

    @Test
    void cacheInvalidWhenManifestIsMissing() {
        S1DataSelectImageCacheManager manager = new S1DataSelectImageCacheManager(
                tempDir,
                config,
                () -> "sha-missing",
                mapper);

        assertFalse(manager.cacheValid());
    }

    @Test
    void cacheValidWhenManifestMatchesAndAllZonePngsDecode() throws Exception {
        Map<String, String> zones = writeFullZoneSet();
        writeManifest("sha-valid", zones);

        S1DataSelectImageCacheManager manager = new S1DataSelectImageCacheManager(
                tempDir,
                config,
                () -> "sha-valid",
                mapper);

        assertTrue(manager.cacheValid());
    }

    @Test
    void cacheInvalidWhenManifestEngineVersionDoesNotMatchAppVersion() throws Exception {
        writeManifest("sha-version", Map.of("ghz1", writeZonePng("ghz1.png")), "0.0.0-test");

        S1DataSelectImageCacheManager manager = new S1DataSelectImageCacheManager(
                tempDir,
                config,
                () -> "sha-version",
                mapper);

        assertFalse(manager.cacheValid());
    }

    @Test
    void cacheInvalidWhenGeneratorFormatVersionDoesNotMatch() throws Exception {
        writeManifest("sha-format", writeFullZoneSet(), AppVersion.get(), 2);

        S1DataSelectImageCacheManager manager = new S1DataSelectImageCacheManager(
                tempDir,
                config,
                () -> "sha-format",
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
                mapper);

        assertFalse(manager.cacheValid());
    }

    @Test
    void cacheInvalidWhenManifestZonesAreEmpty() throws Exception {
        writeManifest("sha-empty", Map.of());

        S1DataSelectImageCacheManager manager = new S1DataSelectImageCacheManager(
                tempDir,
                config,
                () -> "sha-empty",
                mapper);

        assertFalse(manager.cacheValid());
    }

    @Test
    void cacheInvalidWhenManifestZonesArePartial() throws Exception {
        Map<String, String> zones = new TreeMap<>();
        zones.put("ghz1", writeZonePng("ghz1.png"));
        writeManifest("sha-partial", zones);

        S1DataSelectImageCacheManager manager = new S1DataSelectImageCacheManager(
                tempDir,
                config,
                () -> "sha-partial",
                mapper);

        assertFalse(manager.cacheValid());
    }

    @Test
    void cacheInvalidWhenZoneImageIsNotPng() throws Exception {
        Map<String, String> zones = writeFullZoneSet();
        Files.writeString(tempDir.resolve(zones.get("ghz1")), "not a png");
        writeManifest("sha-corrupt", zones);

        S1DataSelectImageCacheManager manager = new S1DataSelectImageCacheManager(
                tempDir,
                config,
                () -> "sha-corrupt",
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
                mapper);

        assertEquals(0, manager.settleFrames());
    }

    private void writeManifest(String romSha256, Map<String, String> zones) throws IOException {
        writeManifest(romSha256, zones, AppVersion.get(), 1);
    }

    private void writeManifest(String romSha256, Map<String, String> zones, String engineVersion) throws IOException {
        writeManifest(romSha256, zones, engineVersion, 1);
    }

    private void writeManifest(String romSha256, Map<String, String> zones, String engineVersion, int generatorFormatVersion) throws IOException {
        S1DataSelectImageManifest manifest = new S1DataSelectImageManifest(
                engineVersion,
                generatorFormatVersion,
                romSha256,
                "2026-04-14T12:34:56Z",
                8,
                zones);
        mapper.writerWithDefaultPrettyPrinter().writeValue(tempDir.resolve("manifest.json").toFile(), manifest);
    }

    private String writeZonePng(String fileName) throws IOException {
        Path file = tempDir.resolve(fileName);
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", file.toFile());
        return file.getFileName().toString();
    }

    private Map<String, String> writeFullZoneSet() throws IOException {
        Map<String, String> zones = new TreeMap<>();
        zones.put("ghz1", writeZonePng("ghz1.png"));
        zones.put("ghz2", writeZonePng("ghz2.png"));
        zones.put("ghz3", writeZonePng("ghz3.png"));
        zones.put("lz1", writeZonePng("lz1.png"));
        zones.put("lz2", writeZonePng("lz2.png"));
        zones.put("lz3", writeZonePng("lz3.png"));
        zones.put("mz1", writeZonePng("mz1.png"));
        zones.put("mz2", writeZonePng("mz2.png"));
        zones.put("mz3", writeZonePng("mz3.png"));
        zones.put("slz1", writeZonePng("slz1.png"));
        zones.put("slz2", writeZonePng("slz2.png"));
        zones.put("slz3", writeZonePng("slz3.png"));
        zones.put("syz1", writeZonePng("syz1.png"));
        zones.put("syz2", writeZonePng("syz2.png"));
        zones.put("syz3", writeZonePng("syz3.png"));
        zones.put("sbz1", writeZonePng("sbz1.png"));
        zones.put("sbz2", writeZonePng("sbz2.png"));
        zones.put("sbz3", writeZonePng("sbz3.png"));
        zones.put("fz1", writeZonePng("fz1.png"));
        return zones;
    }
}
