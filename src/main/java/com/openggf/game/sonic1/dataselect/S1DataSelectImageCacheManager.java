package com.openggf.game.sonic1.dataselect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.LevelManager;
import com.openggf.version.AppVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Owns the runtime-generated Sonic 1 selected-slot preview cache used by donated S3K Data Select.
 *
 * <p>The cache is save-owned rather than asset-owned because the images are generated from the
 * user's ROM at runtime. This manager validates the on-disk manifest, starts generation only when
 * S3K donation is active, and exposes decoded PNG previews back to the donated frontend.</p>
 */
public class S1DataSelectImageCacheManager {
	static final int GENERATOR_FORMAT_VERSION = 5;
	private static final String MANIFEST_FILE_NAME = "manifest.json";
	private static final Set<String> EXPECTED_ZONE_KEYS = Set.of(
			"ghz",
			"mz",
			"syz",
			"lz",
			"slz",
			"sbz",
			"fz");

	private final Path cacheRoot;
	private final SonicConfigurationService config;
	private final Supplier<String> romSha256Supplier;
	private final ObjectMapper mapper;
	private final S1DataSelectImageGenerator generator;

	private volatile CompletableFuture<Void> inFlight;

	public S1DataSelectImageCacheManager(Path cacheRoot, SonicConfigurationService config,
			Supplier<String> romSha256Supplier, ObjectMapper mapper) {
		this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot");
		this.config = Objects.requireNonNull(config, "config");
		this.romSha256Supplier = Objects.requireNonNull(romSha256Supplier, "romSha256Supplier");
		this.mapper = Objects.requireNonNull(mapper, "mapper");
		this.generator = new S1DataSelectImageGenerator(
				cacheRoot,
				this::captureFramebuffer,
				romSha256Supplier);
	}

	public synchronized void ensureGenerationStarted() {
		startGenerationIfEligible();
	}

	/**
	 * Blocks until an in-flight generation job completes, if one is currently running.
	 */
	public void awaitGenerationIfRunning() {
		CompletableFuture<Void> future = inFlight;
		if (future != null) {
			future.join();
		}
	}

	public boolean isGenerationRunning() {
		CompletableFuture<Void> future = inFlight;
		return future != null && !future.isDone();
	}

	/**
	 * Returns {@code true} when the on-disk cache matches the current engine build, ROM fingerprint,
	 * expected zone set, and PNG decode contract.
	 */
	public boolean cacheValid() {
		if (config.getBoolean(SonicConfiguration.CROSS_GAME_S1_DATA_SELECT_IMAGE_GEN_OVERRIDE)) {
			return false;
		}

		S1DataSelectImageManifest manifest = readManifest();
		if (manifest == null) {
			return false;
		}
		if (!AppVersion.get().equals(manifest.engineVersion())) {
			return false;
		}
		if (manifest.generatorFormatVersion() != GENERATOR_FORMAT_VERSION) {
			return false;
		}
		if (!Objects.equals(romSha256Supplier.get(), manifest.romSha256())) {
			return false;
		}

		if (manifest.zones() == null || !EXPECTED_ZONE_KEYS.equals(manifest.zones().keySet())) {
			return false;
		}
		for (String relativePath : manifest.zones().values()) {
			if (relativePath == null || relativePath.isBlank()) {
				return false;
			}
			Path imagePath = cacheRoot.resolve(relativePath);
			if (Files.notExists(imagePath)) {
				return false;
			}
			if (!isDecodablePng(imagePath)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Returns decoded preview images keyed by host zone ID when the cache is valid.
	 */
	public Map<Integer, RgbaImage> loadCachedPreviews() {
		if (!cacheValid()) {
			return Map.of();
		}
		S1DataSelectImageManifest manifest = readManifest();
		if (manifest == null || manifest.zones() == null) {
			return Map.of();
		}
		Map<Integer, RgbaImage> previews = new LinkedHashMap<>();
		for (int zoneId : S1DataSelectImageGenerator.supportedZoneIds()) {
			String zoneKey = S1DataSelectImageGenerator.zoneKeyForZoneId(zoneId);
			if (zoneKey == null) {
				return Map.of();
			}
			String relativePath = manifest.zones().get(zoneKey);
			if (relativePath == null || relativePath.isBlank()) {
				return Map.of();
			}
			try {
				previews.put(zoneId, ScreenshotCapture.loadPNG(cacheRoot.resolve(relativePath)));
			} catch (IOException e) {
				return Map.of();
			}
		}
		return Map.copyOf(previews);
	}

	private synchronized void startGenerationIfEligible() {
		if (!isEligibleForDonatedS3k()) {
			return;
		}
		CompletableFuture<Void> current = inFlight;
		if (current != null && !current.isDone()) {
			return;
		}
		if (cacheValid()) {
			return;
		}

		CompletableFuture<Void> next = CompletableFuture.runAsync(() -> {
			try {
				generator.generateAll();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		inFlight = next;
		next.whenComplete((ignored, ignoredThrowable) -> {
			synchronized (S1DataSelectImageCacheManager.this) {
				if (inFlight == next) {
					inFlight = null;
				}
			}
		});
	}

	private boolean isEligibleForDonatedS3k() {
		return CrossGameFeatureProvider.isS3kDonorActive();
	}

	private RgbaImage captureFramebuffer(int zoneId, S1DataSelectImageGenerator.PreviewCaptureTarget captureTarget) {
		int[] spawnPoint = new com.openggf.game.sonic1.Sonic1ZoneRegistry().getStartPosition(zoneId, 0);
		int cameraLeftX = captureTarget != null ? captureTarget.cameraLeftX() : spawnPoint[0];
		int centreY = captureTarget != null ? captureTarget.centreY() : spawnPoint[1];
		GraphicsManager graphics = RuntimeManager.currentEngineServices().graphics();
		return graphics
				.submitRenderThreadTask(() -> {
					LevelManager levelManager = GameServices.level();
					levelManager.loadZoneAndAct(zoneId, 0, com.openggf.game.LevelLoadMode.PREVIEW_CAPTURE);
					Camera camera = GameServices.camera();
					camera.setX((short) Math.max(camera.getMinX(), cameraLeftX));
					camera.setY((short) Math.max(camera.getMinY(), centreY - 96));
					levelManager.drawWithRenderOptions(null, LevelManager.LevelRenderOptions.previewCapture());
					graphics.flush();
					int viewportX = graphics.getViewportX();
					int viewportY = graphics.getViewportY();
					int viewportWidth = graphics.getViewportWidth();
					int viewportHeight = graphics.getViewportHeight();
					if (viewportWidth <= 0 || viewportHeight <= 0) {
						return ScreenshotCapture.captureFramebuffer(320, 224);
					}
					return ScreenshotCapture.captureFramebufferRegion(
							viewportX,
							viewportY,
							viewportWidth,
							viewportHeight);
				})
				.join();
	}

	private S1DataSelectImageManifest readManifest() {
		Path manifestPath = cacheRoot.resolve(MANIFEST_FILE_NAME);
		if (Files.notExists(manifestPath)) {
			return null;
		}
		try {
			return mapper.readValue(manifestPath.toFile(), S1DataSelectImageManifest.class);
		} catch (IOException e) {
			return null;
		}
	}

	private boolean isDecodablePng(Path imagePath) {
		try {
			RgbaImage image = ScreenshotCapture.loadPNG(imagePath);
			return image.width() == S1DataSelectImageGenerator.PREVIEW_WIDTH
					&& image.height() == S1DataSelectImageGenerator.PREVIEW_HEIGHT;
		} catch (IOException e) {
			return false;
		}
	}
}
