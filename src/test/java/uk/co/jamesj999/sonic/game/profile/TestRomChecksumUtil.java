package uk.co.jamesj999.sonic.game.profile;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link RomChecksumUtil}: SHA-256 checksum from byte arrays.
 */
public class TestRomChecksumUtil {

    @Test
    public void testSha256_Returns64CharHexString() {
        byte[] data = {0x01, 0x02, 0x03, 0x04};
        String hash = RomChecksumUtil.sha256(data);

        assertNotNull("hash should not be null", hash);
        assertEquals("SHA-256 hex string should be 64 characters", 64, hash.length());
        assertTrue("hash should be lowercase hex",
                hash.matches("[0-9a-f]{64}"));
    }

    @Test
    public void testSha256_Deterministic() {
        byte[] data = "hello world".getBytes();
        String hash1 = RomChecksumUtil.sha256(data);
        String hash2 = RomChecksumUtil.sha256(data);

        assertEquals("same input should produce same hash", hash1, hash2);
    }

    @Test
    public void testSha256_DifferentDataProducesDifferentChecksums() {
        byte[] data1 = {0x00, 0x01, 0x02};
        byte[] data2 = {0x03, 0x04, 0x05};

        String hash1 = RomChecksumUtil.sha256(data1);
        String hash2 = RomChecksumUtil.sha256(data2);

        assertNotEquals("different input should produce different hash", hash1, hash2);
    }

    @Test
    public void testSha256_EmptyArray() {
        byte[] data = new byte[0];
        String hash = RomChecksumUtil.sha256(data);

        assertNotNull("empty array should produce a hash", hash);
        assertEquals("SHA-256 hex string should be 64 characters", 64, hash.length());
        // SHA-256 of empty input is a well-known constant
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    public void testSha256_KnownValue() {
        // SHA-256("hello world") is well-known
        byte[] data = "hello world".getBytes();
        String hash = RomChecksumUtil.sha256(data);
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", hash);
    }
}
