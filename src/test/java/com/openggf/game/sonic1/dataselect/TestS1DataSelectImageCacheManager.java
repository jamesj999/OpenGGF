package com.openggf.game.sonic1.dataselect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic1.Sonic1ZoneRegistry;
import com.openggf.game.sonic1.scroll.Sonic1ZoneConstants;
import com.openggf.graphics.RgbaImage;
import com.openggf.version.AppVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
                Map.of("ghz", "ghz.png"));

        JsonNode json = mapper.valueToTree(manifest);

        assertEquals(AppVersion.get(), json.path("engineVersion").asText());
        assertEquals(1, json.path("generatorFormatVersion").asInt());
        assertEquals("0123456789abcdef", json.path("romSha256").asText());
        assertEquals("2026-04-14T12:34:56Z", json.path("generatedAt").asText());
        assertEquals(8, json.path("settleFrames").asInt());
        assertEquals("ghz.png", json.path("zones").path("ghz").asText());
    }

    @Test
    void cacheInvalidWhenOverrideEnabled() throws Exception {
        writeManifest("sha-override", Map.of("ghz", writeZonePng("ghz.png")));
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
        Map<String, String> zones = writeZoneSet();
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
        writeManifest("sha-version", Map.of("ghz", writeZonePng("ghz.png")), "0.0.0-test");

        S1DataSelectImageCacheManager manager = new S1DataSelectImageCacheManager(
                tempDir,
                config,
                () -> "sha-version",
                mapper);

        assertFalse(manager.cacheValid());
    }

    @Test
    void cacheInvalidWhenGeneratorFormatVersionDoesNotMatch() throws Exception {
        writeManifest("sha-format", writeZoneSet(), AppVersion.get(), 2);

        S1DataSelectImageCacheManager manager = new S1DataSelectImageCacheManager(
                tempDir,
                config,
                () -> "sha-format",
                mapper);

        assertFalse(manager.cacheValid());
    }

    @Test
    void cacheInvalidWhenRomShaDoesNotMatchManifest() throws Exception {
        writeManifest("manifest-sha", Map.of("ghz", writeZonePng("ghz.png")));

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
        zones.put("ghz", writeZonePng("ghz.png"));
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
        Map<String, String> zones = writeZoneSet();
        Files.writeString(tempDir.resolve(zones.get("ghz")), "not a png");
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

    @Test
    void generateAllWritesSevenPngsAndManifestAfterSuccess() throws Exception {
        Path cacheRoot = tempDir.resolve("saves/image-cache/s1");
        FakeCaptureSource captureSource = FakeCaptureSource.solidColourImages(320, 224, 7);
        S1DataSelectImageGenerator generator = new S1DataSelectImageGenerator(
                cacheRoot,
                captureSource,
                () -> "romsha",
                0);

        generator.generateAll();

        assertTrue(Files.isRegularFile(cacheRoot.resolve("manifest.json")));
        try (var stream = Files.list(cacheRoot)) {
            assertEquals(7, stream.filter(path -> path.toString().endsWith(".png")).count());
        }
        assertEquals(List.of(
                Sonic1ZoneConstants.ZONE_GHZ,
                Sonic1ZoneConstants.ZONE_MZ,
                Sonic1ZoneConstants.ZONE_SYZ,
                Sonic1ZoneConstants.ZONE_LZ,
                Sonic1ZoneConstants.ZONE_SLZ,
                Sonic1ZoneConstants.ZONE_SBZ,
                Sonic1ZoneConstants.ZONE_FZ
        ), captureSource.capturedZones());
    }

    @Test
    void captureTargetFallsBackToSpawnWhenOverrideMapIsEmpty() throws Exception {
        Path cacheRoot = tempDir.resolve("saves/image-cache/s1");
        FakeCaptureSource captureSource = FakeCaptureSource.solidColourImages(320, 224, 7);
        S1DataSelectImageGenerator generator = new S1DataSelectImageGenerator(
                cacheRoot,
                captureSource,
                () -> "romsha",
                8);

        generator.generateAll();

        assertFalse(captureSource.capturePoints().isEmpty());
        S1DataSelectImageGenerator.PreviewCapturePoint point = captureSource.capturePoints().getFirst();
        int[] spawn = new Sonic1ZoneRegistry().getStartPosition(Sonic1ZoneConstants.ZONE_GHZ, 0);
        assertEquals(spawn[0], point.centreX());
        assertEquals(spawn[1], point.centreY());
    }

    @Test
    void failedZoneLeavesNoManifestBehind() throws Exception {
        Path cacheRoot = tempDir.resolve("saves/image-cache/s1");
        FakeCaptureSource captureSource = FakeCaptureSource.failOnZone(Sonic1ZoneConstants.ZONE_SYZ);
        S1DataSelectImageGenerator generator = new S1DataSelectImageGenerator(
                cacheRoot,
                captureSource,
                () -> "romsha",
                8);

        assertThrows(IOException.class, generator::generateAll);
        assertFalse(Files.exists(cacheRoot.resolve("manifest.json")));
    }

    private void writeManifest(String romSha256, Map<String, String> zones) throws IOException {
        writeManifest(romSha256, zones, AppVersion.get(), 1);
    }

    private void writeManifest(String romSha256, Map<String, String> zones, String engineVersion) throws IOException {
        writeManifest(romSha256, zones, engineVersion, 1);
    }

    private void writeManifest(String romSha256, Map<String, String> zones, String engineVersion,
            int generatorFormatVersion) throws IOException {
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

    private Map<String, String> writeZoneSet() throws IOException {
        Map<String, String> zones = new TreeMap<>();
        zones.put("ghz", writeZonePng("ghz.png"));
        zones.put("mz", writeZonePng("mz.png"));
        zones.put("syz", writeZonePng("syz.png"));
        zones.put("lz", writeZonePng("lz.png"));
        zones.put("slz", writeZonePng("slz.png"));
        zones.put("sbz", writeZonePng("sbz.png"));
        zones.put("fz", writeZonePng("fz.png"));
        return zones;
    }

    private static final class FakeCaptureSource implements S1DataSelectImageGenerator.CaptureSource {
        private final Map<Integer, RgbaImage> images;
        private final int failZone;
        private final List<Integer> capturedZones = new ArrayList<>();
        private final List<S1DataSelectImageGenerator.PreviewCapturePoint> capturePoints = new ArrayList<>();

        private FakeCaptureSource(Map<Integer, RgbaImage> images, int failZone) {
            this.images = images;
            this.failZone = failZone;
        }

        static FakeCaptureSource solidColourImages(int width, int height, int count) {
            Map<Integer, RgbaImage> images = new TreeMap<>();
            for (int zone = 0; zone < count; zone++) {
                images.put(zone, solidImage(width, height, 0xFF000000 | (zone * 0x00202020)));
            }
            return new FakeCaptureSource(images, -1);
        }

        static FakeCaptureSource failOnZone(int zoneId) {
            return new FakeCaptureSource(Map.of(), zoneId);
        }

        List<Integer> capturedZones() {
            return capturedZones;
        }

        List<S1DataSelectImageGenerator.PreviewCapturePoint> capturePoints() {
            return capturePoints;
        }

        @Override
        public RgbaImage capture(int zoneId, S1DataSelectImageGenerator.PreviewCapturePoint capturePoint, int settleFrames)
                throws IOException {
            capturedZones.add(zoneId);
            capturePoints.add(capturePoint);
            if (zoneId == failZone) {
                throw new IOException("boom");
            }
            RgbaImage image = images.get(zoneId);
            if (image == null) {
                throw new IOException("Missing fake capture for zone " + zoneId);
            }
            return image.copy();
        }

        private static RgbaImage solidImage(int width, int height, int argb) {
            int[] pixels = new int[width * height];
            java.util.Arrays.fill(pixels, argb);
            return new RgbaImage(width, height, pixels);
        }
    }
}