package uk.co.jamesj999.sonic.game.sonic1;

import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1AnimationIds;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.render.SpriteDplcFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;
import uk.co.jamesj999.sonic.level.render.TileLoadRequest;
import uk.co.jamesj999.sonic.sprites.animation.ScriptedVelocityAnimationProfile;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationEndAction;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationProfile;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationScript;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationSet;
import uk.co.jamesj999.sonic.sprites.art.SpriteArtSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Loads Sonic 1 player sprite art, mappings, DPLCs, and animations from ROM.
 *
 * <p>Sonic 1 uses different data formats from Sonic 2:
 * <ul>
 *   <li>Mappings: SonicMappingsVer=1 — byte piece count, 5 bytes per piece
 *       (y:byte, size:byte, tileHi:byte, tileLo:byte, x:byte)</li>
 *   <li>DPLCs: SonicDplcVer=1 — byte entry count, same 2-byte entry format as S2
 *       (bits 12-15: count-1, bits 0-11: start tile)</li>
 *   <li>Art: Uncompressed (same as S2 Sonic art)</li>
 *   <li>Animations: Same script format as S2 (delay byte + frame IDs + end flags)</li>
 * </ul>
 */
public class Sonic1PlayerArt {
    private static final Logger LOG = Logger.getLogger(Sonic1PlayerArt.class.getName());

    private final RomByteReader reader;
    private SpriteArtSet cachedSonic;

    public Sonic1PlayerArt(RomByteReader reader) {
        this.reader = reader;
    }

    public SpriteArtSet loadForCharacter(String characterCode) throws IOException {
        if (characterCode == null) {
            return null;
        }
        String normalized = characterCode.trim().toLowerCase(Locale.ROOT);
        // Sonic 1 only has Sonic as a playable character
        if (!"sonic".equals(normalized)) {
            return null;
        }
        return loadSonic();
    }

    public SpriteArtSet loadSonic() throws IOException {
        if (cachedSonic != null) {
            return cachedSonic;
        }

        Pattern[] artTiles = loadArtTiles(
                Sonic1Constants.ART_UNC_SONIC_ADDR,
                Sonic1Constants.ART_UNC_SONIC_SIZE);

        List<SpriteMappingFrame> mappingFrames = loadS1MappingFrames(
                Sonic1Constants.MAP_SONIC_ADDR,
                Sonic1Constants.SONIC_MAPPING_FRAME_COUNT);

        List<SpriteDplcFrame> dplcFrames = loadS1DplcFrames(
                Sonic1Constants.DPLC_SONIC_ADDR,
                Sonic1Constants.SONIC_MAPPING_FRAME_COUNT);

        int bankSize = resolveBankSize(dplcFrames, mappingFrames);
        int paletteIndex = 0;
        int frameDelay = 1;

        SpriteAnimationSet animationSet = loadSonicAnimations();
        SpriteAnimationProfile animationProfile = new ScriptedVelocityAnimationProfile(
                Sonic1AnimationIds.WAIT,       // idleAnimId
                Sonic1AnimationIds.WALK,       // walkAnimId
                Sonic1AnimationIds.RUN,        // runAnimId
                Sonic1AnimationIds.ROLL,       // rollAnimId
                Sonic1AnimationIds.ROLL2,      // roll2AnimId
                Sonic1AnimationIds.PUSH,       // pushAnimId
                Sonic1AnimationIds.DUCK,       // duckAnimId
                Sonic1AnimationIds.LOOK_UP,    // lookUpAnimId
                -1,                            // spindashAnimId (S1 has no spindash)
                Sonic1AnimationIds.SPRING,     // springAnimId
                Sonic1AnimationIds.DEATH,      // deathAnimId
                Sonic1AnimationIds.HURT,       // hurtAnimId
                Sonic1AnimationIds.STOP,       // skidAnimId
                Sonic1AnimationIds.WALK,       // airAnimId
                Sonic1AnimationIds.BALANCE,    // balanceAnimId
                -1,                            // balance2AnimId (S1 has only 1 balance anim)
                -1,                            // balance3AnimId
                -1,                            // balance4AnimId
                0x40,                          // walkSpeedThreshold
                0x600,                         // runSpeedThreshold
                0);                            // fallbackFrame

        cachedSonic = new SpriteArtSet(
                artTiles,
                mappingFrames,
                dplcFrames,
                paletteIndex,
                Sonic1Constants.ART_TILE_SONIC,
                frameDelay,
                bankSize,
                animationProfile,
                animationSet);

        LOG.info("Sonic 1 player art loaded: " + artTiles.length + " tiles, "
                + mappingFrames.size() + " mapping frames, "
                + dplcFrames.size() + " DPLC frames, "
                + animationSet.getScriptCount() + " animations");
        return cachedSonic;
    }

    private Pattern[] loadArtTiles(int artAddr, int artSize) throws IOException {
        if (artSize % Pattern.PATTERN_SIZE_IN_ROM != 0) {
            throw new IOException("Inconsistent S1 player art tile data");
        }
        int tileCount = artSize / Pattern.PATTERN_SIZE_IN_ROM;
        Pattern[] patterns = new Pattern[tileCount];
        for (int i = 0; i < tileCount; i++) {
            patterns[i] = new Pattern();
            int start = i * Pattern.PATTERN_SIZE_IN_ROM;
            patterns[i].fromSegaFormat(reader.slice(artAddr + start, Pattern.PATTERN_SIZE_IN_ROM));
        }
        return patterns;
    }

    /**
     * Loads S1-format sprite mappings.
     *
     * <p>S1 format (SonicMappingsVer=1):
     * <ul>
     *   <li>Offset table: {@code frameCount} × word (offset from table start to frame data)</li>
     *   <li>Per frame: byte (piece count) + pieces × 5 bytes</li>
     *   <li>Piece format: y:signed_byte, size:byte, tileHi:byte, tileLo:byte, x:signed_byte</li>
     * </ul>
     */
    private List<SpriteMappingFrame> loadS1MappingFrames(int tableAddr, int frameCount) {
        List<SpriteMappingFrame> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            int frameOffset = reader.readU16BE(tableAddr + i * 2);
            int frameAddr = tableAddr + frameOffset;
            int pieceCount = reader.readU8(frameAddr);
            frameAddr += 1;

            List<SpriteMappingPiece> pieces = new ArrayList<>(pieceCount);
            for (int p = 0; p < pieceCount; p++) {
                int yOffset = (byte) reader.readU8(frameAddr);     // signed byte
                int size = reader.readU8(frameAddr + 1);
                int tileHi = reader.readU8(frameAddr + 2);
                int tileLo = reader.readU8(frameAddr + 3);
                int xOffset = (byte) reader.readU8(frameAddr + 4); // signed byte
                frameAddr += 5;

                int widthTiles = ((size >> 2) & 0x3) + 1;
                int heightTiles = (size & 0x3) + 1;

                int tileWord = (tileHi << 8) | tileLo;
                int tileIndex = tileWord & 0x7FF;
                boolean hFlip = (tileWord & 0x800) != 0;
                boolean vFlip = (tileWord & 0x1000) != 0;
                int paletteIndex = (tileWord >> 13) & 0x3;

                pieces.add(new SpriteMappingPiece(
                        xOffset, yOffset, widthTiles, heightTiles,
                        tileIndex, hFlip, vFlip, paletteIndex));
            }
            frames.add(new SpriteMappingFrame(pieces));
        }
        return frames;
    }

    /**
     * Loads S1-format DPLCs.
     *
     * <p>S1 format (SonicDplcVer=1):
     * <ul>
     *   <li>Offset table: {@code frameCount} × word (offset from table start to frame data)</li>
     *   <li>Per frame: byte (entry count) + entries × 2 bytes</li>
     *   <li>Entry format: bits 12-15 = tile_count-1, bits 0-11 = start_tile</li>
     * </ul>
     */
    private List<SpriteDplcFrame> loadS1DplcFrames(int tableAddr, int frameCount) {
        List<SpriteDplcFrame> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            int frameOffset = reader.readU16BE(tableAddr + i * 2);
            int frameAddr = tableAddr + frameOffset;
            int entryCount = reader.readU8(frameAddr);
            frameAddr += 1;

            List<TileLoadRequest> requests = new ArrayList<>(entryCount);
            for (int r = 0; r < entryCount; r++) {
                int entry = reader.readU16BE(frameAddr);
                frameAddr += 2;
                int count = ((entry >> 12) & 0xF) + 1;
                int startTile = entry & 0x0FFF;
                requests.add(new TileLoadRequest(startTile, count));
            }
            frames.add(new SpriteDplcFrame(requests));
        }
        return frames;
    }

    /**
     * Loads Sonic 1 animation scripts from ROM.
     * The format is the same as Sonic 2: a table of word offsets, each pointing to
     * a script with delay byte + frame IDs + end action byte.
     */
    private SpriteAnimationSet loadSonicAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();
        int base = Sonic1Constants.SONIC_ANIM_DATA_ADDR;
        int count = Sonic1Constants.SONIC_ANIM_SCRIPT_COUNT;

        for (int i = 0; i < count; i++) {
            int scriptAddr = base + reader.readU16BE(base + i * 2);
            int delay = reader.readU8(scriptAddr);
            scriptAddr += 1;

            List<Integer> frames = new ArrayList<>();
            SpriteAnimationEndAction endAction = SpriteAnimationEndAction.LOOP;
            int endParam = 0;

            while (true) {
                int value = reader.readU8(scriptAddr);
                scriptAddr += 1;
                if (value >= 0xF0) {
                    if (value == 0xFF) {
                        endAction = SpriteAnimationEndAction.LOOP;
                        break;
                    }
                    if (value == 0xFE) {
                        endAction = SpriteAnimationEndAction.LOOP_BACK;
                        endParam = reader.readU8(scriptAddr);
                        break;
                    }
                    if (value == 0xFD) {
                        endAction = SpriteAnimationEndAction.SWITCH;
                        endParam = reader.readU8(scriptAddr);
                        break;
                    }
                    endAction = SpriteAnimationEndAction.HOLD;
                    break;
                }
                frames.add(value);
            }

            set.addScript(i, new SpriteAnimationScript(delay, frames, endAction, endParam));
        }
        return set;
    }

    private int resolveBankSize(List<SpriteDplcFrame> dplcFrames, List<SpriteMappingFrame> mappingFrames) {
        int maxTiles = 0;
        for (SpriteDplcFrame frame : dplcFrames) {
            int total = 0;
            for (TileLoadRequest request : frame.requests()) {
                total += Math.max(0, request.count());
            }
            maxTiles = Math.max(maxTiles, total);
        }
        if (maxTiles > 0) {
            return maxTiles;
        }
        int maxIndex = 0;
        for (SpriteMappingFrame frame : mappingFrames) {
            for (SpriteMappingPiece piece : frame.pieces()) {
                int tileCount = piece.widthTiles() * piece.heightTiles();
                maxIndex = Math.max(maxIndex, piece.tileIndex() + tileCount);
            }
        }
        return Math.max(0, maxIndex);
    }
}
