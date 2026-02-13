package uk.co.jamesj999.sonic.game.profile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for computing SHA-256 checksums of ROM data.
 * Used to match ROM files to their corresponding profiles.
 */
public final class RomChecksumUtil {

    private RomChecksumUtil() {
        // Static utility class
    }

    /**
     * Compute the SHA-256 hash of the given byte array and return it as a
     * lowercase hex string (64 characters).
     *
     * @param data the bytes to hash
     * @return 64-character lowercase hex digest
     */
    public static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JRE
            throw new AssertionError("SHA-256 algorithm not available", e);
        }
    }
}
