package uk.co.jamesj999.sonic.game;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1RomDetector;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2RomDetector;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kRomDetector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Service that orchestrates ROM detection by querying registered detectors.
 * Detectors are checked in priority order (lower priority value = checked first).
 *
 * <p>Usage:
 * <pre>
 * RomDetectionService service = RomDetectionService.getInstance();
 * Optional&lt;GameModule&gt; module = service.detectAndCreateModule(rom);
 * if (module.isPresent()) {
 *     GameModuleRegistry.setCurrent(module.get());
 * }
 * </pre>
 */
public class RomDetectionService {
    private static final Logger LOGGER = Logger.getLogger(RomDetectionService.class.getName());
    private static RomDetectionService instance;

    private final List<RomDetector> detectors = new ArrayList<>();

    private RomDetectionService() {
        // Register built-in detectors
        registerBuiltInDetectors();
    }

    public static synchronized RomDetectionService getInstance() {
        if (instance == null) {
            instance = new RomDetectionService();
        }
        return instance;
    }

    /**
     * Registers the built-in game detectors.
     * Called during initialization.
     */
    private void registerBuiltInDetectors() {
        RomDetector sonic3kDetector = new Sonic3kRomDetector();
        registerDetector(sonic3kDetector);
        LOGGER.fine("Registered Sonic3kRomDetector");

        RomDetector sonic1Detector = new Sonic1RomDetector();
        registerDetector(sonic1Detector);
        LOGGER.fine("Registered Sonic1RomDetector");

        RomDetector sonic2Detector = new Sonic2RomDetector();
        registerDetector(sonic2Detector);
        LOGGER.fine("Registered Sonic2RomDetector");
    }

    /**
     * Registers a custom ROM detector.
     *
     * @param detector the detector to register
     */
    public void registerDetector(RomDetector detector) {
        if (detector != null && !detectors.contains(detector)) {
            detectors.add(detector);
            // Keep sorted by priority
            detectors.sort(Comparator.comparingInt(RomDetector::getPriority));
            LOGGER.fine("Registered ROM detector: " + detector.getGameName() +
                    " (priority " + detector.getPriority() + ")");
        }
    }

    /**
     * Unregisters a ROM detector.
     *
     * @param detector the detector to remove
     */
    public void unregisterDetector(RomDetector detector) {
        detectors.remove(detector);
    }

    /**
     * Detects the game type from the ROM and creates an appropriate GameModule.
     *
     * @param rom the ROM to analyze
     * @return an Optional containing the GameModule if detected, empty otherwise
     */
    public Optional<GameModule> detectAndCreateModule(Rom rom) {
        if (rom == null || !rom.isOpen()) {
            LOGGER.warning("Cannot detect ROM: ROM is null or not open");
            return Optional.empty();
        }

        for (RomDetector detector : detectors) {
            try {
                if (detector.canHandle(rom)) {
                    LOGGER.info("ROM detected as: " + detector.getGameName());
                    return Optional.of(detector.createModule());
                }
            } catch (Exception e) {
                LOGGER.warning("Error in detector " + detector.getGameName() + ": " + e.getMessage());
            }
        }

        LOGGER.warning("No detector matched the ROM");
        return Optional.empty();
    }

    /**
     * Detects the game type from the ROM and automatically sets the current GameModule.
     * This is a convenience method that combines detection with setting the registry.
     *
     * @param rom the ROM to analyze
     * @return true if a module was detected and set, false otherwise
     */
    public boolean detectAndSetModule(Rom rom) {
        Optional<GameModule> module = detectAndCreateModule(rom);
        if (module.isPresent()) {
            GameModuleRegistry.setCurrent(module.get());
            return true;
        }
        return false;
    }

    /**
     * Returns the list of registered detectors (read-only for inspection).
     *
     * @return list of registered detectors
     */
    public List<RomDetector> getRegisteredDetectors() {
        return List.copyOf(detectors);
    }
}
