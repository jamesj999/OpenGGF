package uk.co.jamesj999.sonic.tools.introspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import uk.co.jamesj999.sonic.game.profile.ProfileMetadata;
import uk.co.jamesj999.sonic.game.profile.RomChecksumUtil;
import uk.co.jamesj999.sonic.game.profile.RomProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Standalone CLI tool that introspects a ROM file by tracing pointer chains
 * and byte patterns to generate a ROM profile JSON file.
 *
 * <p>Unlike the runtime {@link uk.co.jamesj999.sonic.game.profile.scanner.RomPatternScanner},
 * this tool runs offline, takes longer, and produces a complete profile suitable
 * for use with the {@link uk.co.jamesj999.sonic.game.profile.RomAddressResolver}.</p>
 *
 * <p>Usage:
 * <pre>
 * mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.introspector.RomProfileIntrospector" \
 *     -Dexec.args="--rom path/to/rom.gen --output profile.json [--game sonic1|sonic2|sonic3k]"
 * </pre>
 */
public class RomProfileIntrospector {

    private static final Logger LOG = Logger.getLogger(RomProfileIntrospector.class.getName());

    // Mega Drive ROM header offsets (standard layout)
    static final int HEADER_OFFSET = 0x100;
    static final int DOMESTIC_NAME_OFFSET = HEADER_OFFSET + 32;
    static final int DOMESTIC_NAME_LEN = 48;
    static final int INTERNATIONAL_NAME_OFFSET = DOMESTIC_NAME_OFFSET + DOMESTIC_NAME_LEN;
    static final int INTERNATIONAL_NAME_LEN = 48;

    private final List<IntrospectionChain> chains = new ArrayList<>();

    /**
     * Registers an introspection chain to run during profiling.
     *
     * @param chain the chain to register
     */
    public void registerChain(IntrospectionChain chain) {
        if (chain != null) {
            chains.add(chain);
        }
    }

    /**
     * Returns the registered chains (for testing).
     *
     * @return unmodifiable view of registered chains
     */
    public List<IntrospectionChain> getChains() {
        return List.copyOf(chains);
    }

    /**
     * Runs all registered chains against the ROM data and populates the profile.
     *
     * @param rom     the full ROM byte array
     * @param profile the profile to populate
     * @return the number of chains that completed successfully
     */
    public int runChains(byte[] rom, RomProfile profile) {
        int successCount = 0;
        for (IntrospectionChain chain : chains) {
            try {
                LOG.info("Running chain: " + chain.category());
                int beforeCount = profile.addressCount();
                chain.trace(rom, profile);
                int found = profile.addressCount() - beforeCount;
                LOG.info(String.format("Chain '%s' found %d addresses", chain.category(), found));
                successCount++;
            } catch (Exception e) {
                LOG.warning(String.format("Chain '%s' failed: %s", chain.category(), e.getMessage()));
            }
        }
        return successCount;
    }

    /**
     * Detects the game type from ROM header strings.
     * Uses inline detection to avoid depending on the full GameModule system.
     *
     * @param rom the full ROM byte array
     * @return detected game type string ("sonic1", "sonic2", "sonic3k"), or "unknown"
     */
    public static String detectGameType(byte[] rom) {
        if (rom == null || rom.length < INTERNATIONAL_NAME_OFFSET + INTERNATIONAL_NAME_LEN) {
            return "unknown";
        }

        String domesticName = readHeaderString(rom, DOMESTIC_NAME_OFFSET, DOMESTIC_NAME_LEN);
        String internationalName = readHeaderString(rom, INTERNATIONAL_NAME_OFFSET, INTERNATIONAL_NAME_LEN);

        // Normalize for comparison
        String domesticUpper = normalizeWhitespace(domesticName);
        String internationalUpper = normalizeWhitespace(internationalName);

        // Check S3K first (most specific match)
        if (domesticUpper.contains("SONIC AND KNUCKLES") || internationalUpper.contains("SONIC AND KNUCKLES")
                || domesticUpper.contains("SONIC 3") || internationalUpper.contains("SONIC 3")
                || domesticUpper.contains("SONIC THE HEDGEHOG 3") || internationalUpper.contains("SONIC THE HEDGEHOG 3")) {
            return "sonic3k";
        }

        // Check S2 (match "SONIC THE HEDGEHOG 2" before generic S1)
        if (domesticUpper.contains("SONIC THE HEDGEHOG 2") || internationalUpper.contains("SONIC THE HEDGEHOG 2")) {
            return "sonic2";
        }

        // Check S1
        if (domesticUpper.contains("SONIC THE HEDGEHOG") || internationalUpper.contains("SONIC THE HEDGEHOG")) {
            return "sonic1";
        }

        return "unknown";
    }

    /**
     * Reads the domestic name from the ROM header.
     *
     * @param rom the full ROM byte array
     * @return the domestic name string, trimmed
     */
    public static String readDomesticName(byte[] rom) {
        if (rom == null || rom.length < DOMESTIC_NAME_OFFSET + DOMESTIC_NAME_LEN) {
            return "";
        }
        return readHeaderString(rom, DOMESTIC_NAME_OFFSET, DOMESTIC_NAME_LEN);
    }

    /**
     * Reads a string from the ROM at the given offset and length.
     *
     * @param rom    the ROM byte array
     * @param offset start offset
     * @param length number of bytes to read
     * @return the string, trimmed of trailing whitespace
     */
    static String readHeaderString(byte[] rom, int offset, int length) {
        byte[] nameBytes = new byte[length];
        System.arraycopy(rom, offset, nameBytes, 0, length);
        return new String(nameBytes, java.nio.charset.StandardCharsets.US_ASCII).trim();
    }

    /**
     * Normalizes whitespace in a string for comparison:
     * collapses multiple spaces, converts to uppercase, trims.
     */
    static String normalizeWhitespace(String input) {
        if (input == null) {
            return "";
        }
        return input.toUpperCase().replaceAll("\\s+", " ").trim();
    }

    /**
     * Registers the appropriate chains for the detected game type.
     *
     * @param gameType the detected game type ("sonic1", "sonic2", "sonic3k")
     */
    public void registerChainsForGame(String gameType) {
        switch (gameType) {
            case "sonic2" -> {
                registerChain(new Sonic2LevelChain());
                registerChain(new Sonic2AudioChain());
            }
            // Future: sonic1 and sonic3k chains
            default -> LOG.info("No introspection chains registered for game type: " + gameType);
        }
    }

    /**
     * Creates a profile with metadata from the ROM.
     *
     * @param rom      the full ROM byte array
     * @param gameType the detected game type
     * @return a new RomProfile with metadata populated
     */
    public static RomProfile createProfileWithMetadata(byte[] rom, String gameType) {
        String checksum = RomChecksumUtil.sha256(rom);
        String name = readDomesticName(rom);
        if (name.isEmpty()) {
            name = "Unknown ROM";
        }

        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata(name, gameType, checksum, "introspector", false));
        return profile;
    }

    /**
     * Writes a profile to a JSON file with pretty printing.
     *
     * @param profile the profile to write
     * @param output  the output file path
     * @throws IOException if the file cannot be written
     */
    public static void writeProfile(RomProfile profile, Path output) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Ensure parent directory exists
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        mapper.writeValue(output.toFile(), profile);
    }

    /**
     * Parses command-line arguments into a simple options record.
     *
     * @param args command-line arguments
     * @return parsed options
     * @throws IllegalArgumentException if required arguments are missing
     */
    private static final java.util.Set<String> VALID_GAME_TYPES = java.util.Set.of("sonic1", "sonic2", "sonic3k");
    private static final long MAX_ROM_SIZE = 8 * 1024 * 1024; // 8MB - generous upper bound for Genesis ROMs

    static CliOptions parseArgs(String[] args) {
        String romPath = null;
        String outputPath = null;
        String game = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--rom" -> {
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                        throw new IllegalArgumentException("--rom requires a path value");
                    }
                    romPath = args[++i];
                }
                case "--output" -> {
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                        throw new IllegalArgumentException("--output requires a path value");
                    }
                    outputPath = args[++i];
                }
                case "--game" -> {
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                        throw new IllegalArgumentException("--game requires a value (sonic1, sonic2, sonic3k)");
                    }
                    game = args[++i];
                    if (!VALID_GAME_TYPES.contains(game)) {
                        throw new IllegalArgumentException("Unknown game type: " + game + ". Valid: sonic1, sonic2, sonic3k");
                    }
                }
                default -> {
                    // Skip unknown args
                }
            }
        }

        if (romPath == null) {
            throw new IllegalArgumentException("Missing required argument: --rom <path>");
        }
        if (outputPath == null) {
            throw new IllegalArgumentException("Missing required argument: --output <path>");
        }

        return new CliOptions(romPath, outputPath, game);
    }

    /**
     * Parsed CLI options.
     */
    record CliOptions(String romPath, String outputPath, String game) {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: RomProfileIntrospector --rom <path> --output <path> [--game sonic1|sonic2|sonic3k]");
            System.out.println();
            System.out.println("Options:");
            System.out.println("  --rom <path>     Path to the ROM file to introspect");
            System.out.println("  --output <path>  Path to write the generated profile JSON");
            System.out.println("  --game <type>    Force game type (auto-detected from ROM header if omitted)");
            return;
        }

        CliOptions options = parseArgs(args);

        // Read ROM file
        Path romFile = Path.of(options.romPath());
        if (!Files.exists(romFile)) {
            System.err.println("ROM file not found: " + romFile);
            System.exit(1);
        }
        long fileSize = Files.size(romFile);
        if (fileSize > MAX_ROM_SIZE) {
            System.err.println("File too large to be a Mega Drive ROM: " + fileSize + " bytes (max " + MAX_ROM_SIZE + ")");
            System.exit(1);
        }
        byte[] rom = Files.readAllBytes(romFile);
        System.out.printf("Read ROM: %s (%d bytes)%n", romFile.getFileName(), rom.length);

        // Detect game type
        String gameType = options.game() != null ? options.game() : detectGameType(rom);
        System.out.println("Game type: " + gameType);

        if ("unknown".equals(gameType)) {
            System.err.println("Could not detect game type. Use --game to specify.");
            System.exit(1);
        }

        // Create profile with metadata
        RomProfile profile = createProfileWithMetadata(rom, gameType);
        System.out.println("ROM name: " + profile.getMetadata().name());
        System.out.println("SHA-256: " + profile.getMetadata().checksum());

        // Register and run chains
        RomProfileIntrospector introspector = new RomProfileIntrospector();
        introspector.registerChainsForGame(gameType);

        int chainCount = introspector.runChains(rom, profile);

        // Write output
        Path outputFile = Path.of(options.outputPath());
        writeProfile(profile, outputFile);

        // Summary
        int totalAddresses = profile.addressCount();
        int categoryCount = profile.getAddresses().size();
        System.out.printf("%nGenerated profile: %d addresses across %d categories%n", totalAddresses, categoryCount);
        System.out.printf("Chains completed: %d/%d%n", chainCount, introspector.getChains().size());
        System.out.println("Output: " + outputFile.toAbsolutePath());
    }
}
