package com.openggf.game.sonic1.dataselect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.version.AppVersion;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.Objects;
import java.util.function.Supplier;

public class S1DataSelectImageCacheManager {
	static final int GENERATOR_FORMAT_VERSION = 1;
	private static final String MANIFEST_FILE_NAME = "manifest.json";
	private static final Set<String> EXPECTED_ZONE_KEYS = Set.of(
			"ghz1", "ghz2", "ghz3",
			"lz1", "lz2", "lz3",
			"mz1", "mz2", "mz3",
			"slz1", "slz2", "slz3",
			"syz1", "syz2", "syz3",
			"sbz1", "sbz2", "sbz3",
			"fz1");

	private final Path cacheRoot;
	private final SonicConfigurationService config;
	private final Supplier<String> romSha256Supplier;
	private final ObjectMapper mapper;

	public S1DataSelectImageCacheManager(Path cacheRoot, SonicConfigurationService config,
			Supplier<String> romSha256Supplier, ObjectMapper mapper) {
		this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot");
		this.config = Objects.requireNonNull(config, "config");
		this.romSha256Supplier = Objects.requireNonNull(romSha256Supplier, "romSha256Supplier");
		this.mapper = Objects.requireNonNull(mapper, "mapper");
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
