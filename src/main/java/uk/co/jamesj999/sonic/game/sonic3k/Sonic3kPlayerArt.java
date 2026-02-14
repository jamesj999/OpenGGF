package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kAnimationIds;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kConstants;

import uk.co.jamesj999.sonic.data.RomByteReader;
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

/**
 * Loads Sonic sprite art, mappings, DPLCs, and animations for S3K.
 *
 * <p>Key differences from S2:
 * <ul>
 *   <li>Mapping pieces are 6 bytes (no 2P tile word)</li>
 *   <li>VRAM base tile is 0x0680 (vs S2's 0x0780)</li>
 *   <li>Extra art tiles for mapping frames &gt;= 0xDA (Super Sonic / special frames)</li>
 *   <li>36 animation scripts (vs S2's 34)</li>
 *   <li>anglePreAdjust is false (S3K does NOT subtract 1 from angle index)</li>
 * </ul>
 */
public class Sonic3kPlayerArt {
    private final RomByteReader reader;
    private SpriteArtSet cachedSonic;

    public Sonic3kPlayerArt(RomByteReader reader) {
        this.reader = reader;
    }

    public SpriteArtSet loadForCharacter(String characterCode) throws IOException {
        if (characterCode == null) {
            return null;
        }
        String normalized = characterCode.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "sonic" -> loadSonic();
            default -> null;
        };
    }

    public SpriteArtSet loadSonic() throws IOException {
        if (cachedSonic != null) {
            return cachedSonic;
        }

        // Load main + extra art tiles and concatenate
        Pattern[] mainTiles = loadArtTiles(
                Sonic3kConstants.ART_UNC_SONIC_ADDR,
                Sonic3kConstants.ART_UNC_SONIC_SIZE);
        Pattern[] extraTiles = loadArtTiles(
                Sonic3kConstants.ART_UNC_SONIC_EXTRA_ADDR,
                Sonic3kConstants.ART_UNC_SONIC_EXTRA_SIZE);
        Pattern[] allTiles = new Pattern[mainTiles.length + extraTiles.length];
        System.arraycopy(mainTiles, 0, allTiles, 0, mainTiles.length);
        System.arraycopy(extraTiles, 0, allTiles, mainTiles.length, extraTiles.length);

        List<SpriteMappingFrame> mappingFrames = loadMappingFrames(Sonic3kConstants.MAP_SONIC_ADDR);
        List<SpriteDplcFrame> dplcFrames = loadDplcFrames(Sonic3kConstants.DPLC_SONIC_ADDR);

        // S3K DPLC tables contain both 1P and 2P offset tables concatenated.
        // The first offset value (offsetTableSize) covers BOTH tables, so
        // loadDplcFrames() returns more frames than needed. Trim to match
        // the mapping frame count (only 1P frames are valid).
        if (dplcFrames.size() > mappingFrames.size()) {
            dplcFrames = new ArrayList<>(dplcFrames.subList(0, mappingFrames.size()));
        }

        // Post-process DPLCs: frames corresponding to mapping frames >= threshold
        // need their startTile values offset by mainTileCount so they index into the
        // concatenated extra art region.
        int mainTileCount = mainTiles.length;
        int threshold = Sonic3kConstants.SONIC_EXTRA_ART_FRAME_THRESHOLD;
        for (int i = threshold; i < dplcFrames.size(); i++) {
            SpriteDplcFrame original = dplcFrames.get(i);
            List<TileLoadRequest> adjusted = new ArrayList<>(original.requests().size());
            for (TileLoadRequest req : original.requests()) {
                adjusted.add(new TileLoadRequest(req.startTile() + mainTileCount, req.count()));
            }
            dplcFrames.set(i, new SpriteDplcFrame(adjusted));
        }

        int bankSize = resolveBankSize(dplcFrames, mappingFrames);
        SpriteAnimationSet animationSet = loadSonicAnimations();

        SpriteAnimationProfile animationProfile = new ScriptedVelocityAnimationProfile(
                Sonic3kAnimationIds.WAIT,      // idleAnimId
                Sonic3kAnimationIds.WALK,      // walkAnimId
                Sonic3kAnimationIds.RUN,       // runAnimId
                Sonic3kAnimationIds.ROLL,      // rollAnimId
                Sonic3kAnimationIds.ROLL2,     // roll2AnimId
                Sonic3kAnimationIds.PUSH,      // pushAnimId
                Sonic3kAnimationIds.DUCK,      // duckAnimId
                Sonic3kAnimationIds.LOOK_UP,   // lookUpAnimId
                Sonic3kAnimationIds.SPINDASH,  // spindashAnimId
                Sonic3kAnimationIds.SPRING,    // springAnimId
                Sonic3kAnimationIds.DEATH,     // deathAnimId
                Sonic3kAnimationIds.HURT,      // hurtAnimId
                Sonic3kAnimationIds.SKID,      // skidAnimId
                Sonic3kAnimationIds.WALK,      // airAnimId
                Sonic3kAnimationIds.BALANCE,   // balanceAnimId
                Sonic3kAnimationIds.BALANCE2,  // balance2AnimId
                Sonic3kAnimationIds.BALANCE3,  // balance3AnimId
                Sonic3kAnimationIds.BALANCE4,  // balance4AnimId
                0x40,                          // walkSpeedThreshold
                0x600,                         // runSpeedThreshold
                0,                             // fallbackFrame
                false);                        // anglePreAdjust (S3K: no subq.b #1,d0)

        cachedSonic = new SpriteArtSet(
                allTiles,
                mappingFrames,
                dplcFrames,
                0,                             // paletteIndex
                Sonic3kConstants.ART_TILE_SONIC,
                1,                             // frameDelay
                bankSize,
                animationProfile,
                animationSet);
        return cachedSonic;
    }

    /**
     * Loads Super Sonic animation set, merged with normal Sonic animations.
     *
     * <p>AniSuperSonic entries that reference unique Super Sonic scripts override
     * the normal animation. Back-references (offsets &gt;= 0x8000) are skipped,
     * keeping the normal Sonic script for that index.
     */
    public SpriteAnimationSet loadSuperSonicAnimationSet() {
        int base = Sonic3kConstants.SUPER_SONIC_ANIM_DATA_ADDR;
        int count = Sonic3kConstants.SUPER_SONIC_ANIM_SCRIPT_COUNT;
        if (base == 0) {
            return null;
        }

        SpriteAnimationSet normalSet = loadSonicAnimations();
        SpriteAnimationSet set = new SpriteAnimationSet();
        for (var entry : normalSet.getAllScripts().entrySet()) {
            set.addScript(entry.getKey(), entry.getValue());
        }

        int ptrTableSize = count * 2;
        for (int i = 0; i < count; i++) {
            int offset = reader.readU16BE(base + i * 2);
            // Back-references to AniSonic are negative offsets (>= 0x8000 unsigned).
            // Also skip offsets that fall within the pointer table itself.
            if (offset >= 0x8000 || offset < ptrTableSize) {
                continue;
            }
            int scriptAddr = base + offset;
            if (scriptAddr + 2 > reader.size()) {
                continue;
            }
            SpriteAnimationScript script = parseAnimationScript(scriptAddr);
            set.addScript(i, script);
        }
        return set;
    }

    // ===== Art tile loading (identical to S2) =====

    private Pattern[] loadArtTiles(int artAddr, int artSize) throws IOException {
        if (artSize % Pattern.PATTERN_SIZE_IN_ROM != 0) {
            throw new IOException("Inconsistent player art tile data");
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

    // ===== Mapping loading (S3K: 6-byte pieces) =====

    private List<SpriteMappingFrame> loadMappingFrames(int mappingAddr) {
        int offsetTableSize = reader.readU16BE(mappingAddr);
        int frameCount = offsetTableSize / 2;
        List<SpriteMappingFrame> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            int frameAddr = mappingAddr + reader.readU16BE(mappingAddr + i * 2);
            int pieceCount = reader.readU16BE(frameAddr);
            frameAddr += 2;
            List<SpriteMappingPiece> pieces = new ArrayList<>(pieceCount);
            for (int p = 0; p < pieceCount; p++) {
                int yOffset = (byte) reader.readU8(frameAddr);
                frameAddr += 1;
                int size = reader.readU8(frameAddr);
                frameAddr += 1;
                int tileWord = reader.readU16BE(frameAddr);
                frameAddr += 2;
                // S3K: NO 2P tile word (S2 has +2 skip here)
                int xOffset = (short) reader.readU16BE(frameAddr);
                frameAddr += 2;

                int widthTiles = ((size >> 2) & 0x3) + 1;
                int heightTiles = (size & 0x3) + 1;

                int tileIndex = tileWord & 0x7FF;
                boolean hFlip = (tileWord & 0x800) != 0;
                boolean vFlip = (tileWord & 0x1000) != 0;
                int paletteIndex = (tileWord >> 13) & 0x3;

                pieces.add(new SpriteMappingPiece(
                        xOffset, yOffset, widthTiles, heightTiles, tileIndex, hFlip, vFlip, paletteIndex));
            }
            frames.add(new SpriteMappingFrame(pieces));
        }
        return frames;
    }

    // ===== DPLC loading (identical to S2) =====

    private List<SpriteDplcFrame> loadDplcFrames(int dplcAddr) {
        int offsetTableSize = reader.readU16BE(dplcAddr);
        int frameCount = offsetTableSize / 2;
        List<SpriteDplcFrame> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            int frameAddr = dplcAddr + reader.readU16BE(dplcAddr + i * 2);
            int requestCount = reader.readU16BE(frameAddr);
            frameAddr += 2;
            List<TileLoadRequest> requests = new ArrayList<>(requestCount);
            for (int r = 0; r < requestCount; r++) {
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

    // ===== Animation loading (identical format to S2) =====

    private SpriteAnimationSet loadSonicAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();
        int base = Sonic3kConstants.SONIC_ANIM_DATA_ADDR;
        int count = Sonic3kConstants.SONIC_ANIM_SCRIPT_COUNT;

        for (int i = 0; i < count; i++) {
            int scriptAddr = base + reader.readU16BE(base + i * 2);
            SpriteAnimationScript script = parseAnimationScript(scriptAddr);
            set.addScript(i, script);
        }
        return set;
    }

    private SpriteAnimationScript parseAnimationScript(int scriptAddr) {
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

        return new SpriteAnimationScript(delay, frames, endAction, endParam);
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
