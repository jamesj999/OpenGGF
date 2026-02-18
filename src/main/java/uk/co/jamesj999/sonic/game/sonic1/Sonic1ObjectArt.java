package uk.co.jamesj999.sonic.game.sonic1;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;
import uk.co.jamesj999.sonic.tools.NemesisReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Builds object sprite sheets for S1 objects.
 * Provides convenience methods for building sheets from Nemesis-compressed art
 * combined with either ROM-parsed or hardcoded mappings.
 */
public class Sonic1ObjectArt {
    private static final Logger LOG = Logger.getLogger(Sonic1ObjectArt.class.getName());

    private final Rom rom;
    private final RomByteReader reader;

    public Sonic1ObjectArt(Rom rom, RomByteReader reader) {
        this.rom = rom;
        this.reader = reader;
    }

    /**
     * Builds a sprite sheet from Nemesis-compressed art and ROM-parsed S1 mappings.
     *
     * @param artAddr ROM address of Nemesis-compressed art
     * @param mappingAddr ROM address of S1 mapping table
     * @param paletteIndex palette line (0-3)
     * @param bankSize bank size for renderer (typically 1)
     * @return sprite sheet, or null if art decompression fails
     */
    public ObjectSpriteSheet buildArtSheetFromRom(int artAddr, int mappingAddr,
            int paletteIndex, int bankSize) {
        Pattern[] patterns = loadNemesisPatterns(artAddr);
        if (patterns.length == 0) return null;

        List<SpriteMappingFrame> frames = S1SpriteDataLoader.loadMappingFrames(reader, mappingAddr);
        if (frames.isEmpty()) return null;

        return new ObjectSpriteSheet(patterns, frames, paletteIndex, bankSize);
    }

    /**
     * Builds a sprite sheet from Nemesis-compressed art and hardcoded mappings.
     *
     * @param artAddr ROM address of Nemesis-compressed art
     * @param mappings pre-built mapping frames
     * @param paletteIndex palette line (0-3)
     * @param bankSize bank size for renderer (typically 1)
     * @return sprite sheet, or null if art decompression fails
     */
    public ObjectSpriteSheet buildArtSheet(int artAddr, List<SpriteMappingFrame> mappings,
            int paletteIndex, int bankSize) {
        Pattern[] patterns = loadNemesisPatterns(artAddr);
        if (patterns.length == 0) return null;

        return new ObjectSpriteSheet(patterns, mappings, paletteIndex, bankSize);
    }

    /**
     * Builds a sprite sheet from uncompressed art and hardcoded mappings.
     *
     * @param artAddr ROM address of uncompressed art
     * @param artSize size of art in bytes
     * @param mappings pre-built mapping frames
     * @param paletteIndex palette line (0-3)
     * @param bankSize bank size for renderer (typically 1)
     * @return sprite sheet, or null if loading fails
     */
    public ObjectSpriteSheet buildUncompressedArtSheet(int artAddr, int artSize,
            List<SpriteMappingFrame> mappings, int paletteIndex, int bankSize) {
        Pattern[] patterns = loadUncompressedPatterns(artAddr, artSize);
        if (patterns.length == 0) return null;

        return new ObjectSpriteSheet(patterns, mappings, paletteIndex, bankSize);
    }

    /**
     * Loads Nemesis-compressed patterns from ROM.
     */
    public Pattern[] loadNemesisPatterns(int address) {
        try {
            byte[] compressed = rom.readBytes(address, 8192);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                 ReadableByteChannel channel = Channels.newChannel(bais)) {
                byte[] decompressed = NemesisReader.decompress(channel);
                int count = decompressed.length / Pattern.PATTERN_SIZE_IN_ROM;
                Pattern[] patterns = new Pattern[count];
                for (int i = 0; i < count; i++) {
                    patterns[i] = new Pattern();
                    byte[] sub = Arrays.copyOfRange(decompressed,
                            i * Pattern.PATTERN_SIZE_IN_ROM,
                            (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
                    patterns[i].fromSegaFormat(sub);
                }
                return patterns;
            }
        } catch (IOException e) {
            LOG.warning("Failed to decompress Nemesis art at 0x"
                    + Integer.toHexString(address) + ": " + e.getMessage());
            return new Pattern[0];
        }
    }

    /**
     * Loads uncompressed patterns from ROM.
     */
    public Pattern[] loadUncompressedPatterns(int address, int size) {
        try {
            byte[] data = rom.readBytes(address, size);
            if (data.length % Pattern.PATTERN_SIZE_IN_ROM != 0) {
                LOG.warning("Inconsistent uncompressed art size at 0x"
                        + Integer.toHexString(address));
                return new Pattern[0];
            }
            int count = data.length / Pattern.PATTERN_SIZE_IN_ROM;
            Pattern[] patterns = new Pattern[count];
            for (int i = 0; i < count; i++) {
                patterns[i] = new Pattern();
                byte[] sub = Arrays.copyOfRange(data,
                        i * Pattern.PATTERN_SIZE_IN_ROM,
                        (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
                patterns[i].fromSegaFormat(sub);
            }
            return patterns;
        } catch (Exception e) {
            LOG.warning("Failed to load uncompressed art at 0x"
                    + Integer.toHexString(address) + ": " + e.getMessage());
            return new Pattern[0];
        }
    }
}
