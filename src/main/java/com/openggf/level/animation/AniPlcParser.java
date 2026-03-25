package com.openggf.level.animation;

import com.openggf.data.RomByteReader;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses AniPLC (animated pattern load cue) binary scripts from ROM.
 *
 * <p>The binary format is shared between Sonic 2 and Sonic 3&K:
 * <pre>
 *   dc.w count-1              ; number of scripts minus 1 (0xFFFF = empty)
 *   ; per script:
 *   dc.l (dur&amp;0xFF)&lt;&lt;24|art   ; duration byte + 24-bit art ROM address
 *   dc.w tiles_to_bytes(dest) ; VRAM destination (tile index * 32)
 *   dc.b frameCount, tilesPerFrame
 *   ; frame data: per-frame pairs (tileId, duration) if dur is negative,
 *   ;             else just tileId bytes with global duration
 * </pre>
 */
public final class AniPlcParser {

    private AniPlcParser() {
    }

    /**
     * Parse all AniPLC scripts at the given ROM address.
     *
     * @param reader ROM byte reader
     * @param addr   absolute ROM address of the script list
     * @return parsed script states (empty list if address is invalid or list is empty)
     */
    public static List<AniPlcScriptState> parseScripts(RomByteReader reader, int addr) {
        int countMinus1 = reader.readU16BE(addr);
        if (countMinus1 == 0xFFFF) {
            return List.of();
        }

        int scriptCount = countMinus1 + 1;
        int pos = addr + 2;
        List<AniPlcScriptState> scripts = new ArrayList<>(scriptCount);

        for (int i = 0; i < scriptCount; i++) {
            int header = reader.readU32BE(pos);
            byte globalDuration = (byte) ((header >> 24) & 0xFF);
            int artAddr = header & 0xFFFFFF;
            int destBytes = reader.readU16BE(pos + 4);
            int destTileIndex = destBytes >> 5;
            int frameCount = reader.readU8(pos + 6);
            int tilesPerFrame = reader.readU8(pos + 7);

            int dataStart = pos + 8;
            boolean perFrame = globalDuration < 0;
            int dataLen = frameCount * (perFrame ? 2 : 1);
            int dataLenAligned = (dataLen + 1) & ~1;

            int[] frameTileIds = new int[frameCount];
            int[] frameDurations = perFrame ? new int[frameCount] : null;
            for (int f = 0; f < frameCount; f++) {
                int offset = dataStart + (perFrame ? (f * 2) : f);
                frameTileIds[f] = reader.readU8(offset);
                if (perFrame) {
                    frameDurations[f] = reader.readU8(offset + 1);
                }
            }

            Pattern[] artPatterns = loadArtPatterns(reader, artAddr, tilesPerFrame, frameTileIds);
            scripts.add(new AniPlcScriptState(globalDuration, destTileIndex, frameTileIds,
                    frameDurations, tilesPerFrame, artPatterns));

            pos = dataStart + dataLenAligned;
        }

        return scripts;
    }

    /**
     * Load uncompressed art patterns referenced by an AniPLC script entry.
     *
     * @param reader        ROM byte reader
     * @param artAddr       absolute ROM address of the art data
     * @param tilesPerFrame number of consecutive tiles per animation frame
     * @param frameTileIds  source tile indices for each frame
     * @return array of parsed patterns
     */
    public static Pattern[] loadArtPatterns(RomByteReader reader, int artAddr,
                                            int tilesPerFrame, int[] frameTileIds) {
        int maxTile = 0;
        for (int tileId : frameTileIds) {
            int frameMax = tileId + Math.max(tilesPerFrame, 1) - 1;
            if (frameMax > maxTile) {
                maxTile = frameMax;
            }
        }
        int tileCount = maxTile + 1;
        int byteCount = tileCount * Pattern.PATTERN_SIZE_IN_ROM;
        if (artAddr < 0 || artAddr + byteCount > reader.size()) {
            int available = Math.max(0, reader.size() - artAddr);
            tileCount = available / Pattern.PATTERN_SIZE_IN_ROM;
            byteCount = tileCount * Pattern.PATTERN_SIZE_IN_ROM;
        }
        if (tileCount <= 0 || byteCount <= 0) {
            return new Pattern[0];
        }

        byte[] data = reader.slice(artAddr, byteCount);
        Pattern[] patterns = new Pattern[tileCount];
        for (int i = 0; i < tileCount; i++) {
            Pattern pattern = new Pattern();
            int start = i * Pattern.PATTERN_SIZE_IN_ROM;
            int end = start + Pattern.PATTERN_SIZE_IN_ROM;
            byte[] tileData = new byte[Pattern.PATTERN_SIZE_IN_ROM];
            System.arraycopy(data, start, tileData, 0, end - start);
            pattern.fromSegaFormat(tileData);
            patterns[i] = pattern;
        }
        return patterns;
    }

    /**
     * Ensure the level's pattern array is large enough for all script destinations.
     *
     * @param scripts parsed script states
     * @param level   the level to ensure capacity on
     */
    public static void ensurePatternCapacity(List<AniPlcScriptState> scripts, Level level) {
        if (scripts == null || scripts.isEmpty()) {
            return;
        }
        for (AniPlcScriptState script : scripts) {
            int required = script.requiredPatternCount();
            if (required > 0) {
                level.ensurePatternCapacity(required);
            }
        }
    }

    /**
     * Prime all scripts by applying their first frame immediately.
     *
     * @param scripts          parsed script states
     * @param level            the level containing pattern data
     * @param graphicsManager  the graphics manager for texture updates
     */
    public static void primeScripts(List<AniPlcScriptState> scripts,
                                    Level level, GraphicsManager graphicsManager) {
        if (scripts == null || scripts.isEmpty()) {
            return;
        }
        for (AniPlcScriptState script : scripts) {
            script.prime(level, graphicsManager);
        }
    }
}
