package uk.co.jamesj999.sonic.game.sonic1;

import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.sprites.art.SpriteArtSet;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Loads Sonic 1 player sprite art from ROM.
 * Sonic 1 uses uncompressed art at 0x21AFE with S1-format mappings (no DPLCs).
 */
public class Sonic1PlayerArt {
    private static final Logger LOG = Logger.getLogger(Sonic1PlayerArt.class.getName());

    // ROM addresses (from s1disasm)
    private static final int ART_SONIC_ADDR = 0x21AFE;
    private static final int ART_SONIC_END = 0x2BC1D;
    private static final int ART_SONIC_SIZE = ART_SONIC_END - ART_SONIC_ADDR;

    private final RomByteReader rom;

    public Sonic1PlayerArt(RomByteReader rom) {
        this.rom = rom;
    }

    /**
     * Loads player sprite art for the given character.
     * Currently returns null (stub) - Sonic 1 mapping format parsing not yet implemented.
     * The engine will fall back to default behavior when this returns null.
     */
    public SpriteArtSet loadForCharacter(String characterCode) throws IOException {
        LOG.info("Sonic 1 player art loading not yet implemented - returning null");
        // TODO: Implement S1 mapping format parsing
        // 1. Read raw uncompressed art bytes from ART_SONIC_ADDR
        // 2. Parse S1 mapping format (Map_Sonic label)
        // 3. Create SpriteArtSet with pre-mapped frames (no DPLCs in S1)
        return null;
    }
}
