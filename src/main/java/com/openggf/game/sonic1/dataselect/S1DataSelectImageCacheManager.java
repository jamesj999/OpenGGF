package com.openggf.game.sonic1.dataselect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.version.AppVersion;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class S1DataSelectImageCacheManager {
	static final int GENERATOR_FORMAT_VERSION = 1;
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

	private CompletableFuture<Void> inFlight;

	public S1DataSelectImageCacheManager(Path cacheRoot, SonicConfigurationService config,
			Supplier<String> romSha256Supplier, ObjectMapper mapper) {
		this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot");
		this.config = Objects.requireNonNull(config, "config");
		this.romSha256Supplier = Objects.requireNonNull(romSha256Supplier, "romSha256Supplier");
		this.mapper = Objects.requireNonNull(mapper, "mapper");
		this.generator = new S1DataSelectImageGenerator(
				cacheRoot,
				this::captureFramebuffer,
				romSha256Supplier,
				settleFrames());
	}

	public synchronized void ensureGenerationStarted() {
		startGenerationIfEligible();
	}

	public void awaitGenerationIfRunning() {
		CompletableFuture<Void> future = inFlight;
		if (future != null) {
			future.join();
		}
	}

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

	public int settleFrames() {
		return Math.max(0, config.getInt(SonicConfiguration.CROSS_GAME_S1_DATA_SELECT_IMAGE_GEN_SETTLE_FRAMES));
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

	private RgbaImage captureFramebuffer(int zoneId, S1DataSelectImageGenerator.PreviewCapturePoint capturePoint,
			int settleFrames) {
		int[] spawnPoint = new com.openggf.game.sonic1.Sonic1ZoneRegistry().getStartPosition(zoneId, 0);
		int centreX = capturePoint != null ? capturePoint.centreX() : spawnPoint[0];
		int centreY = capturePoint != null ? capturePoint.centreY() : spawnPoint[1];
		int framesToSettle = Math.max(0, settleFrames);
		return GraphicsManager.getInstance()
				.submitRenderThreadTask(() -> {
					Camera camera = GameServices.camera();
					camera.setX((short) (centreX - 152));
					camera.setY((short) (centreY - 96));
					for (int i = 0; i < framesToSettle; i++) {
						camera.updatePosition(true);
					}
					return ScreenshotCapture.captureFramebuffer(320, 224);
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
			BufferedImage image = ImageIO.read(imagePath.toFile());
			return image != null;
		} catch (IOException e) {
			return false;
		}
	}
}
