package com.openggf.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.lwjgl.glfw.GLFW.*;

public class SonicConfigurationService {
	private static final Logger LOGGER = Logger.getLogger(SonicConfigurationService.class.getName());
	private static SonicConfigurationService sonicConfigurationService;
	public static String ENGINE_VERSION = "0.5.prerelease";

	private Map<String, Object> config;

	private SonicConfigurationService() {
		ObjectMapper mapper = new ObjectMapper();
		TypeReference<Map<String, Object>> type = new TypeReference<>(){};

		if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
			// Native image: look for config.json next to the executable binary
			File execConfig = findConfigNextToExecutable();
			if (execConfig != null && execConfig.exists()) {
				try {
					config = mapper.readValue(execConfig, type);
				} catch (IOException e) {
					System.err.println("Failed to load config.json from executable directory: " + e.getMessage());
				}
			}
		} else {
			// JAR mode: look for config.json in the working directory
			File file = resolveRelativeFile("config.json");
			if (file.exists()) {
				try {
					config = mapper.readValue(file, type);
				} catch (IOException e) {
					System.err.println("Failed to load config.json from working directory: " + e.getMessage());
				}
			}
		}

		if (config == null) {
			try (InputStream is = getClass().getResourceAsStream("/config.json")) {
				if (is != null) {
					config = mapper.readValue(is, type);
				} else {
					System.err.println("Could not find config.json, using defaults.");
					config = new HashMap<>();
				}
			} catch (IOException e) {
				LOGGER.log(Level.WARNING, "Failed to load config.json from classpath", e);
				config = new HashMap<>();
			}
		}

		// Migrate AWT key codes to GLFW if detected (for users upgrading from JOGL build)
		ConfigMigrationService migrationService = new ConfigMigrationService();
		if (migrationService.detectAwtKeyCodes(config)) {
			migrationService.migrateConfig(config);
			saveConfig(); // Persist migrated config
		}

		applyDefaults();
	}

	public int getInt(SonicConfiguration sonicConfiguration) {
		Object value = getConfigValue(sonicConfiguration);
		if (value instanceof Integer) {
			return ((Integer) value);
		} else {
			try {
				return Integer.parseInt(getString(sonicConfiguration));
			} catch (NumberFormatException e) {
				return -1;
			}
		}
	}

	public short getShort(SonicConfiguration sonicConfiguration) {
		Object value = getConfigValue(sonicConfiguration);
		if (value instanceof Short) {
			return ((Short) value).shortValue();
		} else if (value instanceof Integer) {
			return (short) getInt(sonicConfiguration);
		} else {
			try {
				return Short.parseShort(getString(sonicConfiguration));
			} catch (NumberFormatException e) {
				return -1;
			}
		}
	}

	public String getString(SonicConfiguration sonicConfiguration) {
		Object value = getConfigValue(sonicConfiguration);
		if (value != null) {
			return value.toString();
		} else {
			return StringUtils.EMPTY;
		}
	}

	public double getDouble(SonicConfiguration sonicConfiguration) {
		Object value = getConfigValue(sonicConfiguration);
		if (value instanceof Double) {
			return ((Double) value);
		} else {
			try {
				return Double.parseDouble(getString(sonicConfiguration));
			} catch (NumberFormatException e) {
				return -1.00d;
			}
		}
	}

	public boolean getBoolean(SonicConfiguration sonicConfiguration) {
		Object value = getConfigValue(sonicConfiguration);
		if(value instanceof Boolean) {
			return ((Boolean) value);
		} else if (value instanceof Number) {
			return ((Number) value).intValue() != 0;
		} else {
			return Boolean.parseBoolean(getString(sonicConfiguration));
		}
	}

	public Object getConfigValue(SonicConfiguration sonicConfiguration) {
		if (config != null && config.containsKey(sonicConfiguration.name())) {
			return config.get(sonicConfiguration.name());
		}
		return null;
	}

	public void setConfigValue(SonicConfiguration key, Object value) {
		if (config == null) {
			config = new HashMap<>();
		}
		config.put(key.name(), value);
	}

	public void saveConfig() {
		ObjectMapper mapper = new ObjectMapper();
		File target;
		if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
			File execConfig = findConfigNextToExecutable();
			target = (execConfig != null) ? execConfig : resolveRelativeFile("config.json");
		} else {
			target = resolveRelativeFile("config.json");
		}
		try {
			mapper.writerWithDefaultPrettyPrinter().writeValue(target, config);
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Failed to save config.json", e);
		}
	}

	public synchronized static SonicConfigurationService getInstance() {
		if (sonicConfigurationService == null) {
			sonicConfigurationService = new SonicConfigurationService();
		}
		return sonicConfigurationService;
	}

	private void applyDefaults() {
		if (config == null) {
			config = new HashMap<>();
		}
		// Fill in core defaults if missing to keep tests and headless runs stable.
		putDefault(SonicConfiguration.SCREEN_WIDTH, 640);
		putDefault(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
		putDefault(SonicConfiguration.SCREEN_HEIGHT, 480);
		putDefault(SonicConfiguration.SCREEN_HEIGHT_PIXELS, 240);
		putDefault(SonicConfiguration.SCALE, 1.0);
		// Debug view now eagerly initialized in Engine.init() to avoid macOS freeze
		putDefault(SonicConfiguration.DEBUG_VIEW_ENABLED, true);
		putDefault(SonicConfiguration.DEBUG_COLLISION_VIEW_ENABLED, false);
		putDefault(SonicConfiguration.DAC_INTERPOLATE, true);
		putDefault(SonicConfiguration.FM6_DAC_OFF, true); // Default true for Sonic 2 parity
		putDefault(SonicConfiguration.AUDIO_ENABLED, true);
		putDefault(SonicConfiguration.AUDIO_INTERNAL_RATE_OUTPUT, false);
		putDefault(SonicConfiguration.PSG_NOISE_SHIFT_EVERY_TOGGLE, true);
		putDefault(SonicConfiguration.REGION, "NTSC");
		// Key codes - using GLFW key codes
		putDefault(SonicConfiguration.UP, GLFW_KEY_UP);
		putDefault(SonicConfiguration.DOWN, GLFW_KEY_DOWN);
		putDefault(SonicConfiguration.LEFT, GLFW_KEY_LEFT);
		putDefault(SonicConfiguration.RIGHT, GLFW_KEY_RIGHT);
		putDefault(SonicConfiguration.JUMP, GLFW_KEY_SPACE);
		putDefault(SonicConfiguration.P2_UP, GLFW_KEY_I);
		putDefault(SonicConfiguration.P2_DOWN, GLFW_KEY_K);
		putDefault(SonicConfiguration.P2_LEFT, GLFW_KEY_J);
		putDefault(SonicConfiguration.P2_RIGHT, GLFW_KEY_L);
		putDefault(SonicConfiguration.P2_JUMP, GLFW_KEY_RIGHT_SHIFT);
		putDefault(SonicConfiguration.P2_START, GLFW_KEY_ENTER);
		putDefault(SonicConfiguration.TEST, GLFW_KEY_T);
		putDefault(SonicConfiguration.NEXT_ACT, GLFW_KEY_PAGE_UP);
		putDefault(SonicConfiguration.NEXT_ZONE, GLFW_KEY_PAGE_DOWN);
		putDefault(SonicConfiguration.DEBUG_MODE_KEY, GLFW_KEY_D);
		putDefault(SonicConfiguration.FPS, 60);
		putDefault(SonicConfiguration.SPECIAL_STAGE_KEY, GLFW_KEY_TAB);
		putDefault(SonicConfiguration.SPECIAL_STAGE_COMPLETE_KEY, GLFW_KEY_END);
		putDefault(SonicConfiguration.SPECIAL_STAGE_FAIL_KEY, GLFW_KEY_DELETE);
		putDefault(SonicConfiguration.SPECIAL_STAGE_SPRITE_DEBUG_KEY, GLFW_KEY_F12);
		putDefault(SonicConfiguration.SPECIAL_STAGE_PLANE_DEBUG_KEY, GLFW_KEY_F3);
		putDefault(SonicConfiguration.PAUSE_KEY, GLFW_KEY_ENTER);
		putDefault(SonicConfiguration.FRAME_STEP_KEY, GLFW_KEY_Q);
		putDefault(SonicConfiguration.PLAYBACK_MOVIE_PATH, "playback.bk2");
		putDefault(SonicConfiguration.PLAYBACK_TOGGLE_KEY, GLFW_KEY_B);
		putDefault(SonicConfiguration.PLAYBACK_LOAD_KEY, GLFW_KEY_N);
		putDefault(SonicConfiguration.PLAYBACK_PLAY_PAUSE_KEY, GLFW_KEY_M);
		putDefault(SonicConfiguration.PLAYBACK_STEP_BACK_KEY, GLFW_KEY_COMMA);
		putDefault(SonicConfiguration.PLAYBACK_STEP_FORWARD_KEY, GLFW_KEY_PERIOD);
		putDefault(SonicConfiguration.PLAYBACK_JUMP_BACK_KEY, GLFW_KEY_LEFT_BRACKET);
		putDefault(SonicConfiguration.PLAYBACK_JUMP_FORWARD_KEY, GLFW_KEY_RIGHT_BRACKET);
		putDefault(SonicConfiguration.PLAYBACK_FAST_RATE_KEY, GLFW_KEY_SLASH);
		putDefault(SonicConfiguration.PLAYBACK_RESET_TO_START_KEY, GLFW_KEY_BACKSLASH);
		putDefault(SonicConfiguration.PLAYBACK_START_OFFSET_FRAME, 0);
		putDefault(SonicConfiguration.DEBUG_LAST_CHECKPOINT_KEY, GLFW_KEY_C);
		putDefault(SonicConfiguration.LEVEL_SELECT_KEY, GLFW_KEY_F9);
		putDefault(SonicConfiguration.TITLE_SCREEN_ON_STARTUP, false);
		putDefault(SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);
		putDefault(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
		putDefault(SonicConfiguration.SONIC_1_ROM, "Sonic The Hedgehog (W) (REV01) [!].gen");
		putDefault(SonicConfiguration.SONIC_2_ROM, "Sonic The Hedgehog 2 (W) (REV01) [!].gen");
		putDefault(SonicConfiguration.SONIC_3K_ROM, "Sonic and Knuckles & Sonic 3 (W) [!].gen");
		// Migrate renamed config key: S3K_SKIP_AIZ1_INTRO → S3K_SKIP_INTROS
		if (config.containsKey("S3K_SKIP_AIZ1_INTRO")) {
			if (!config.containsKey(SonicConfiguration.S3K_SKIP_INTROS.name())) {
				config.put(SonicConfiguration.S3K_SKIP_INTROS.name(), config.get("S3K_SKIP_AIZ1_INTRO"));
			}
			config.remove("S3K_SKIP_AIZ1_INTRO");
		}
		putDefault(SonicConfiguration.S3K_SKIP_INTROS, false);
		putDefault(SonicConfiguration.DEFAULT_ROM, "s2");
		putDefault(SonicConfiguration.SUPER_SONIC_DEBUG_KEY, GLFW_KEY_U);
		putDefault(SonicConfiguration.GIVE_EMERALDS_KEY, GLFW_KEY_E);
		putDefault(SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP, true);
		putDefault(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
		putDefault(SonicConfiguration.CROSS_GAME_SOURCE, "s2");
	}

	private void putDefault(SonicConfiguration key, Object value) {
		if (config == null) {
			config = new HashMap<>();
		}
		config.putIfAbsent(key.name(), value);
	}

	/**
	 * Finds config.json next to the native image executable binary.
	 * Uses ProcessHandle to determine the executable path.
	 */
	private static File findConfigNextToExecutable() {
		try {
			String cmd = ProcessHandle.current().info().command().orElse("");
			if (!cmd.isEmpty()) {
				File execDir = new File(cmd).getParentFile();
				if (execDir != null) {
					return new File(execDir, "config.json");
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	/**
	 * Resolves a relative filename against user.dir. In GraalVM native images
	 * launched from macOS Finder, getcwd() is broken so File("relative") may
	 * resolve against the wrong directory. This ensures consistent behavior.
	 */
	private static File resolveRelativeFile(String name) {
		File f = new File(name);
		if (!f.isAbsolute()) {
			String userDir = System.getProperty("user.dir");
			if (userDir != null) {
				return new File(userDir, name);
			}
		}
		return f;
	}
}
