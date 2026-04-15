package com.openggf.game.sonic1.dataselect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.sonic1.Sonic1ZoneRegistry;
import com.openggf.game.sonic1.scroll.Sonic1ZoneConstants;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.version.AppVersion;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class S1DataSelectImageGenerator {
    static final int PREVIEW_WIDTH = 80;
    static final int PREVIEW_HEIGHT = 56;
    private static final int CAMERA_TARGET_X_BIAS = 152;
    private static final int CAMERA_TARGET_Y_BIAS = 96;
    private static final Sonic1ZoneRegistry CAPTURE_ZONE_REGISTRY = new Sonic1ZoneRegistry();

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<ZoneCaptureSpec> ZONES = List.of(
            new ZoneCaptureSpec(Sonic1ZoneConstants.ZONE_GHZ, "ghz", "ghz.png"),
            new ZoneCaptureSpec(Sonic1ZoneConstants.ZONE_MZ, "mz", "mz.png"),
            new ZoneCaptureSpec(Sonic1ZoneConstants.ZONE_SYZ, "syz", "syz.png"),
            new ZoneCaptureSpec(Sonic1ZoneConstants.ZONE_LZ, "lz", "lz.png"),
            new ZoneCaptureSpec(Sonic1ZoneConstants.ZONE_SLZ, "slz", "slz.png"),
            new ZoneCaptureSpec(Sonic1ZoneConstants.ZONE_SBZ, "sbz", "sbz.png"),
            new ZoneCaptureSpec(Sonic1ZoneConstants.ZONE_FZ, "fz", "fz.png")
    );
    private static final Map<Integer, PreviewCapturePoint> CAPTURE_OVERRIDES = buildCaptureOverrides();

    private final Path cacheRoot;
    private final CaptureSource captureSource;
    private final Supplier<String> romSha256Supplier;
    private final int settleFrames;
    private final Sonic1ZoneRegistry zoneRegistry = CAPTURE_ZONE_REGISTRY;

    public S1DataSelectImageGenerator(Path cacheRoot,
                                      CaptureSource captureSource,
                                      Supplier<String> romSha256Supplier,
                                      int settleFrames) {
        this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot");
        this.captureSource = Objects.requireNonNull(captureSource, "captureSource");
        this.romSha256Supplier = Objects.requireNonNull(romSha256Supplier, "romSha256Supplier");
        this.settleFrames = Math.max(0, settleFrames);
    }

    public void generateAll() throws IOException {
        Files.createDirectories(cacheRoot);

        Map<String, String> zoneFiles = new LinkedHashMap<>();
        for (ZoneCaptureSpec spec : ZONES) {
            PreviewCaptureTarget captureTarget = resolveCaptureTarget(spec.zoneId());
            RgbaImage capture = captureSource.capture(spec.zoneId(), captureTarget, settleFrames);
            RgbaImage preview = scaleToPreview(capture);

            Path tempPng = Files.createTempFile(cacheRoot, spec.fileStem() + "-", ".tmp");
            Path finalPng = cacheRoot.resolve(spec.fileName());
            try {
                ScreenshotCapture.savePNG(preview, tempPng);
                moveAtomically(tempPng, finalPng);
            } finally {
                Files.deleteIfExists(tempPng);
            }
            zoneFiles.put(spec.zoneKey(), spec.fileName());
        }

        writeManifest(zoneFiles);
    }

    public PreviewCaptureTarget resolveCaptureTarget(int zoneId) {
        PreviewCapturePoint override = CAPTURE_OVERRIDES.get(zoneId);
        if (override != null) {
            return new PreviewCaptureTarget(override.centreX() - CAMERA_TARGET_X_BIAS, override.centreY());
        }
        int[] spawn = zoneRegistry.getStartPosition(zoneId, 0);
        return new PreviewCaptureTarget(spawn[0], spawn[1]);
    }

    public static PreviewCapturePoint previewCapturePointFromCamera(int cameraLeftX, int cameraTopY) {
        return new PreviewCapturePoint(cameraLeftX + CAMERA_TARGET_X_BIAS, cameraTopY + CAMERA_TARGET_Y_BIAS);
    }

    static List<Integer> supportedZoneIds() {
        return ZONES.stream().map(ZoneCaptureSpec::zoneId).toList();
    }

    static String zoneKeyForZoneId(int zoneId) {
        for (ZoneCaptureSpec spec : ZONES) {
            if (spec.zoneId() == zoneId) {
                return spec.zoneKey();
            }
        }
        return null;
    }

    private void writeManifest(Map<String, String> zoneFiles) throws IOException {
        Path tempManifest = Files.createTempFile(cacheRoot, "manifest-", ".tmp");
        Path finalManifest = cacheRoot.resolve("manifest.json");
        try {
            S1DataSelectImageManifest manifest = new S1DataSelectImageManifest(
                    AppVersion.get(),
                    S1DataSelectImageCacheManager.GENERATOR_FORMAT_VERSION,
                    romSha256Supplier.get(),
                    Instant.now().toString(),
                    settleFrames,
                    zoneFiles);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tempManifest.toFile(), manifest);
            moveAtomically(tempManifest, finalManifest);
        } finally {
            Files.deleteIfExists(tempManifest);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static RgbaImage scaleToPreview(RgbaImage source) {
        if (source.width() == PREVIEW_WIDTH && source.height() == PREVIEW_HEIGHT) {
            return source.copy();
        }
        int[] pixels = new int[PREVIEW_WIDTH * PREVIEW_HEIGHT];
        for (int y = 0; y < PREVIEW_HEIGHT; y++) {
            int sourceY = Math.min(source.height() - 1, y * source.height() / PREVIEW_HEIGHT);
            for (int x = 0; x < PREVIEW_WIDTH; x++) {
                int sourceX = Math.min(source.width() - 1, x * source.width() / PREVIEW_WIDTH);
                pixels[y * PREVIEW_WIDTH + x] = source.argb(sourceX, sourceY);
            }
        }
        return new RgbaImage(PREVIEW_WIDTH, PREVIEW_HEIGHT, pixels);
    }

    public interface CaptureSource {
        RgbaImage capture(int zoneId, PreviewCaptureTarget captureTarget, int settleFrames) throws IOException;
    }

    public record PreviewCapturePoint(int centreX, int centreY) {
    }

    public record PreviewCaptureTarget(int cameraLeftX, int centreY) {
    }

    private record ZoneCaptureSpec(int zoneId, String zoneKey, String fileName) {
        private String fileStem() {
            int dot = fileName.indexOf('.');
            return dot == -1 ? fileName : fileName.substring(0, dot);
        }
    }

    private static Map<Integer, PreviewCapturePoint> buildCaptureOverrides() {
        return Map.of(
                Sonic1ZoneConstants.ZONE_GHZ, offsetFromSpawn(Sonic1ZoneConstants.ZONE_GHZ, 0x160, 0),
                Sonic1ZoneConstants.ZONE_SLZ, offsetFromSpawn(Sonic1ZoneConstants.ZONE_SLZ, 0x180, 0),
                Sonic1ZoneConstants.ZONE_FZ, offsetFromSpawn(Sonic1ZoneConstants.ZONE_FZ, 0x100, 0)
        );
    }

    private static PreviewCapturePoint offsetFromSpawn(int zoneId, int dx, int dy) {
        int[] spawn = CAPTURE_ZONE_REGISTRY.getStartPosition(zoneId, 0);
        return new PreviewCapturePoint(spawn[0] + dx, spawn[1] + dy);
    }
}
