package uk.co.jamesj999.sonic.tools.introspector;

import org.junit.Test;
import uk.co.jamesj999.sonic.game.profile.RomProfile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Tests for the RomProfileIntrospector CLI framework.
 * Focuses on game detection, CLI argument parsing, metadata creation,
 * and chain registration/execution.
 */
public class TestRomProfileIntrospector {

    // ========================================
    // Game type detection
    // ========================================

    @Test
    public void testDetectGameType_Sonic2() {
        byte[] rom = createMockRomHeader("SONIC THE HEDGEHOG 2");
        assertEquals("sonic2", RomProfileIntrospector.detectGameType(rom));
    }

    @Test
    public void testDetectGameType_Sonic1() {
        byte[] rom = createMockRomHeader("SONIC THE HEDGEHOG");
        assertEquals("sonic1", RomProfileIntrospector.detectGameType(rom));
    }

    @Test
    public void testDetectGameType_Sonic3k() {
        byte[] rom = createMockRomHeader("SONIC AND KNUCKLES");
        assertEquals("sonic3k", RomProfileIntrospector.detectGameType(rom));
    }

    @Test
    public void testDetectGameType_Sonic3() {
        byte[] rom = createMockRomHeader("SONIC THE HEDGEHOG 3");
        assertEquals("sonic3k", RomProfileIntrospector.detectGameType(rom));
    }

    @Test
    public void testDetectGameType_Unknown() {
        byte[] rom = createMockRomHeader("SOME OTHER GAME");
        assertEquals("unknown", RomProfileIntrospector.detectGameType(rom));
    }

    @Test
    public void testDetectGameType_NullRom() {
        assertEquals("unknown", RomProfileIntrospector.detectGameType(null));
    }

    @Test
    public void testDetectGameType_TooShort() {
        byte[] rom = new byte[100];
        assertEquals("unknown", RomProfileIntrospector.detectGameType(rom));
    }

    @Test
    public void testDetectGameType_ExtraWhitespace() {
        byte[] rom = createMockRomHeader("SONIC THE  HEDGEHOG  2");
        assertEquals("sonic2", RomProfileIntrospector.detectGameType(rom));
    }

    // ========================================
    // Header string reading
    // ========================================

    @Test
    public void testReadDomesticName() {
        byte[] rom = createMockRomHeader("SONIC THE HEDGEHOG 2");
        String name = RomProfileIntrospector.readDomesticName(rom);
        assertEquals("SONIC THE HEDGEHOG 2", name);
    }

    @Test
    public void testReadDomesticName_TooShort() {
        byte[] rom = new byte[100];
        assertEquals("", RomProfileIntrospector.readDomesticName(rom));
    }

    @Test
    public void testReadDomesticName_Null() {
        assertEquals("", RomProfileIntrospector.readDomesticName(null));
    }

    // ========================================
    // CLI argument parsing
    // ========================================

    @Test
    public void testParseArgs_AllOptions() {
        String[] args = {"--rom", "test.gen", "--output", "out.json", "--game", "sonic2"};
        RomProfileIntrospector.CliOptions opts = RomProfileIntrospector.parseArgs(args);
        assertEquals("test.gen", opts.romPath());
        assertEquals("out.json", opts.outputPath());
        assertEquals("sonic2", opts.game());
    }

    @Test
    public void testParseArgs_RequiredOnly() {
        String[] args = {"--rom", "test.gen", "--output", "out.json"};
        RomProfileIntrospector.CliOptions opts = RomProfileIntrospector.parseArgs(args);
        assertEquals("test.gen", opts.romPath());
        assertEquals("out.json", opts.outputPath());
        assertNull(opts.game());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseArgs_MissingRom() {
        String[] args = {"--output", "out.json"};
        RomProfileIntrospector.parseArgs(args);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseArgs_MissingOutput() {
        String[] args = {"--rom", "test.gen"};
        RomProfileIntrospector.parseArgs(args);
    }

    @Test
    public void testParseArgs_UnknownArgsIgnored() {
        String[] args = {"--rom", "test.gen", "--output", "out.json", "--verbose", "--debug"};
        RomProfileIntrospector.CliOptions opts = RomProfileIntrospector.parseArgs(args);
        assertEquals("test.gen", opts.romPath());
        assertEquals("out.json", opts.outputPath());
    }

    // ========================================
    // Profile metadata creation
    // ========================================

    @Test
    public void testCreateProfileWithMetadata() {
        byte[] rom = createMockRomHeader("SONIC THE HEDGEHOG 2");
        RomProfile profile = RomProfileIntrospector.createProfileWithMetadata(rom, "sonic2");

        assertNotNull(profile.getMetadata());
        assertEquals("sonic2", profile.getMetadata().game());
        assertEquals("introspector", profile.getMetadata().generatedBy());
        assertFalse(profile.getMetadata().complete());
        assertEquals("SONIC THE HEDGEHOG 2", profile.getMetadata().name());
        assertNotNull(profile.getMetadata().checksum());
        assertEquals(64, profile.getMetadata().checksum().length()); // SHA-256 hex
    }

    // ========================================
    // Chain registration and execution
    // ========================================

    @Test
    public void testRegisterChain() {
        RomProfileIntrospector introspector = new RomProfileIntrospector();
        assertEquals(0, introspector.getChains().size());

        introspector.registerChain(new TestChain("test"));
        assertEquals(1, introspector.getChains().size());
    }

    @Test
    public void testRegisterChain_NullIgnored() {
        RomProfileIntrospector introspector = new RomProfileIntrospector();
        introspector.registerChain(null);
        assertEquals(0, introspector.getChains().size());
    }

    @Test
    public void testRunChains_Success() {
        RomProfileIntrospector introspector = new RomProfileIntrospector();
        introspector.registerChain(new TestChain("chain1"));
        introspector.registerChain(new TestChain("chain2"));

        byte[] rom = new byte[1024];
        RomProfile profile = new RomProfile();
        int successCount = introspector.runChains(rom, profile);

        assertEquals(2, successCount);
        assertEquals(2, profile.addressCount()); // Each TestChain adds 1 address
    }

    @Test
    public void testRunChains_PartialFailure() {
        RomProfileIntrospector introspector = new RomProfileIntrospector();
        introspector.registerChain(new TestChain("good"));
        introspector.registerChain(new FailingChain());
        introspector.registerChain(new TestChain("also_good"));

        byte[] rom = new byte[1024];
        RomProfile profile = new RomProfile();
        int successCount = introspector.runChains(rom, profile);

        // The failing chain doesn't stop others
        assertEquals(2, successCount);
        assertEquals(2, profile.addressCount());
    }

    @Test
    public void testRegisterChainsForGame_Sonic2() {
        RomProfileIntrospector introspector = new RomProfileIntrospector();
        introspector.registerChainsForGame("sonic2");

        assertEquals(2, introspector.getChains().size());
        assertEquals("level", introspector.getChains().get(0).category());
        assertEquals("audio", introspector.getChains().get(1).category());
    }

    @Test
    public void testRegisterChainsForGame_Unknown() {
        RomProfileIntrospector introspector = new RomProfileIntrospector();
        introspector.registerChainsForGame("unknown");

        assertEquals(0, introspector.getChains().size());
    }

    // ========================================
    // Utility methods
    // ========================================

    @Test
    public void testNormalizeWhitespace() {
        assertEquals("HELLO WORLD", RomProfileIntrospector.normalizeWhitespace("  hello  world  "));
        assertEquals("", RomProfileIntrospector.normalizeWhitespace(null));
        assertEquals("", RomProfileIntrospector.normalizeWhitespace(""));
    }

    // ========================================
    // Helpers
    // ========================================

    /**
     * Creates a minimal ROM with a domestic name set in the header.
     * The ROM is large enough to contain the full header area.
     */
    private byte[] createMockRomHeader(String domesticName) {
        byte[] rom = new byte[0x200]; // Large enough for header
        byte[] nameBytes = domesticName.getBytes(StandardCharsets.US_ASCII);

        // Write domestic name at offset 0x120 (HEADER_OFFSET + 32)
        int offset = RomProfileIntrospector.DOMESTIC_NAME_OFFSET;
        // Pad with spaces first
        Arrays.fill(rom, offset, offset + RomProfileIntrospector.DOMESTIC_NAME_LEN, (byte) 0x20);
        System.arraycopy(nameBytes, 0, rom, offset,
                Math.min(nameBytes.length, RomProfileIntrospector.DOMESTIC_NAME_LEN));

        // Also write international name at offset 0x150
        int intlOffset = RomProfileIntrospector.INTERNATIONAL_NAME_OFFSET;
        Arrays.fill(rom, intlOffset, intlOffset + RomProfileIntrospector.INTERNATIONAL_NAME_LEN, (byte) 0x20);
        System.arraycopy(nameBytes, 0, rom, intlOffset,
                Math.min(nameBytes.length, RomProfileIntrospector.INTERNATIONAL_NAME_LEN));

        return rom;
    }

    /**
     * Test chain that adds one address entry.
     */
    private static class TestChain implements IntrospectionChain {
        private final String name;

        TestChain(String name) {
            this.name = name;
        }

        @Override
        public String category() {
            return name;
        }

        @Override
        public void trace(byte[] rom, RomProfile profile) {
            profile.putAddress(name, "TEST_ADDR",
                    new uk.co.jamesj999.sonic.game.profile.AddressEntry(0x1000, "test"));
        }
    }

    /**
     * Chain that always throws an exception (for testing partial failure).
     */
    private static class FailingChain implements IntrospectionChain {
        @Override
        public String category() {
            return "failing";
        }

        @Override
        public void trace(byte[] rom, RomProfile profile) {
            throw new RuntimeException("Simulated chain failure");
        }
    }
}
