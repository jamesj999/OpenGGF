package uk.co.jamesj999.sonic.game.profile;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads {@link RomProfile} instances from the filesystem (user overrides)
 * and from the classpath (shipped profiles keyed by ROM checksum).
 */
public class ProfileLoader {

    private static final Logger logger = Logger.getLogger(ProfileLoader.class.getName());
    private static final String PROFILES_RESOURCE_DIR = "profiles/";

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Load a profile from a filesystem path.
     *
     * @param path the path to a JSON profile file
     * @return the parsed profile, or null if the file is missing or invalid
     */
    public RomProfile loadFromFile(Path path) {
        if (!Files.exists(path)) {
            logger.fine(() -> "Profile file not found: " + path);
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            return mapper.readValue(bytes, RomProfile.class);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to parse profile from file: " + path, e);
            return null;
        }
    }

    /**
     * Load a shipped profile from the classpath by ROM checksum.
     * Looks for a resource at {@code profiles/<checksum>.profile.json}.
     *
     * @param checksum the SHA-256 checksum of the ROM
     * @return the parsed profile, or null if no matching resource exists or parsing fails
     */
    public RomProfile loadFromClasspath(String checksum) {
        String resourcePath = PROFILES_RESOURCE_DIR + checksum + ".profile.json";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                logger.fine(() -> "No classpath profile for checksum: " + checksum);
                return null;
            }
            return mapper.readValue(is, RomProfile.class);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to parse classpath profile for checksum: " + checksum, e);
            return null;
        }
    }
}
