package com.openggf.game.sonic1.dataselect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.version.AppVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class S1DataSelectImageCacheManager {
	static final int GENERATOR_FORMAT_VERSION = 1;
	private static final String MANIFEST_FILE_NAME = "manifest.json";

	private final Path cacheRoot;
	private final SonicConfigurationService config;
	private final Supplier<String> romSha256Supplier;
	private final S1DataSelectImageGenerator generator;
	private final ObjectMapper mapper;

	public S1DataSelectImageCacheManager(Path cacheRoot, SonicConfigurationService config,
			Supplier<String> romSha256Supplier, S1DataSelectImageGenerator generator, ObjectMapper mapper) {
		this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot");
		this.config = Objects.requireNonNull(config, "config");
		this.romSha256Supplier = Objects.requireNonNull(romSha256Supplier, "romSha256Supplier");
		this.generator = generator;
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

		Map<String, String> zones = manifest.zones();
		if (zones == null) {
			return false;
		}
		for (String relativePath : zones.values()) {
			if (relativePath == null || relativePath.isBlank()) {
				return false;
			}
			if (Files.notExists(cacheRoot.resolve(relativePath))) {
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
}

interface S1DataSelectImageGenerator {
}
