package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;

import com.openggf.data.RomByteReader;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.art.SpriteArtSet;

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
 *   <li>anglePreAdjust is true (S3K subtracts 1 from angle index: sonic3k.asm:24816)</li>
 * </ul>
 */
public class Sonic3kPlayerArt {
    private final RomByteReader reader;
    private SpriteArtSet cachedSonic;
    private SpriteArtSet cachedSuperSonic;
    private SpriteArtSet cachedTails;
    private SpriteArtSet cachedTailsTail;

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
            case "tails" -> loadTails();
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

        // S3K mapping/DPLC tables are combined 1P+2P: the offset table contains
        // N entries for 1P followed by N entries for 2P (total 2N). The first word
        // covers both halves, so the parser returns 2N frames. Only the first half
        // (1P frames) is valid for single-player mode.
        int onePlayerCount = mappingFrames.size() / 2;
        if (onePlayerCount > 0 && mappingFrames.size() > onePlayerCount) {
            mappingFrames = new ArrayList<>(mappingFrames.subList(0, onePlayerCount));
        }
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

        SpriteAnimationProfile animationProfile = new ScriptedVelocityAnimationProfile()
                .setIdleAnimId(Sonic3kAnimationIds.WAIT)
                .setWalkAnimId(Sonic3kAnimationIds.WALK)
                .setRunAnimId(Sonic3kAnimationIds.RUN)
                .setRollAnimId(Sonic3kAnimationIds.ROLL)
                .setRoll2AnimId(Sonic3kAnimationIds.ROLL2)
                .setPushAnimId(Sonic3kAnimationIds.PUSH)
                .setDuckAnimId(Sonic3kAnimationIds.DUCK)
                .setLookUpAnimId(Sonic3kAnimationIds.LOOK_UP)
                .setSpindashAnimId(Sonic3kAnimationIds.SPINDASH)
                .setSpringAnimId(Sonic3kAnimationIds.SPRING)
                .setDeathAnimId(Sonic3kAnimationIds.DEATH)
                .setDrownAnimId(Sonic3kAnimationIds.DROWN)
                .setHurtAnimId(Sonic3kAnimationIds.HURT)
                .setSkidAnimId(Sonic3kAnimationIds.SKID)
                .setAirAnimId(Sonic3kAnimationIds.WALK)
                .setBalanceAnimId(Sonic3kAnimationIds.BALANCE)
                .setBalance2AnimId(Sonic3kAnimationIds.BALANCE2)
                .setBalance3AnimId(Sonic3kAnimationIds.BALANCE3)
                .setBalance4AnimId(Sonic3kAnimationIds.BALANCE4)
                .setWalkSpeedThreshold(0x40)
                .setRunSpeedThreshold(0x600)
                .setFallbackFrame(0)
                .setAnglePreAdjust(true)       // sonic3k.asm:24816 — subq.b #1,d0
                .setTumbleFrameBase(0x31);     // sonic3k.asm:24955 — addi.b #$31,d0

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

    public SpriteArtSet loadTails() throws IOException {
        if (cachedTails != null) {
            return cachedTails;
        }

        // Load main + extra art tiles and concatenate
        Pattern[] mainTiles = loadArtTiles(
                Sonic3kConstants.ART_UNC_TAILS_ADDR,
                Sonic3kConstants.ART_UNC_TAILS_SIZE);
        Pattern[] extraTiles = loadArtTiles(
                Sonic3kConstants.ART_UNC_TAILS_EXTRA_ADDR,
                Sonic3kConstants.ART_UNC_TAILS_EXTRA_SIZE);
        Pattern[] allTiles = new Pattern[mainTiles.length + extraTiles.length];
        System.arraycopy(mainTiles, 0, allTiles, 0, mainTiles.length);
        System.arraycopy(extraTiles, 0, allTiles, mainTiles.length, extraTiles.length);

        List<SpriteMappingFrame> mappingFrames = loadMappingFrames(Sonic3kConstants.MAP_TAILS_ADDR);
        List<SpriteDplcFrame> dplcFrames = loadDplcFrames(Sonic3kConstants.DPLC_TAILS_ADDR);

        // Post-process DPLCs: frames >= threshold use Extra art tiles
        int mainTileCount = mainTiles.length;
        int threshold = Sonic3kConstants.TAILS_EXTRA_ART_FRAME_THRESHOLD;
        for (int i = threshold; i < dplcFrames.size(); i++) {
            SpriteDplcFrame original = dplcFrames.get(i);
            List<TileLoadRequest> adjusted = new ArrayList<>(original.requests().size());
            for (TileLoadRequest req : original.requests()) {
                adjusted.add(new TileLoadRequest(req.startTile() + mainTileCount, req.count()));
            }
            dplcFrames.set(i, new SpriteDplcFrame(adjusted));
        }

        int bankSize = resolveBankSize(dplcFrames, mappingFrames);
        SpriteAnimationSet animationSet = loadTailsAnimations();

        SpriteAnimationProfile animationProfile = new ScriptedVelocityAnimationProfile()
                .setIdleAnimId(Sonic3kAnimationIds.WAIT)
                .setWalkAnimId(Sonic3kAnimationIds.WALK)
                .setRunAnimId(Sonic3kAnimationIds.RUN)
                .setRollAnimId(Sonic3kAnimationIds.ROLL)
                .setRoll2AnimId(Sonic3kAnimationIds.ROLL2)
                .setPushAnimId(Sonic3kAnimationIds.PUSH)
                .setDuckAnimId(Sonic3kAnimationIds.DUCK)
                .setLookUpAnimId(Sonic3kAnimationIds.LOOK_UP)
                .setSpindashAnimId(Sonic3kAnimationIds.SPINDASH)
                .setSpringAnimId(Sonic3kAnimationIds.SPRING)
                .setDeathAnimId(Sonic3kAnimationIds.DEATH)
                .setDrownAnimId(Sonic3kAnimationIds.DROWN)
                .setHurtAnimId(Sonic3kAnimationIds.HURT)
                .setSkidAnimId(Sonic3kAnimationIds.SKID)
                .setAirAnimId(Sonic3kAnimationIds.WALK)
                .setBalanceAnimId(Sonic3kAnimationIds.BALANCE)
                .setBalance2AnimId(Sonic3kAnimationIds.BALANCE2)
                .setBalance3AnimId(Sonic3kAnimationIds.BALANCE3)
                .setBalance4AnimId(Sonic3kAnimationIds.BALANCE4)
                .setWalkSpeedThreshold(0x40)
                .setRunSpeedThreshold(0x600)
                .setFallbackFrame(0)
                .setAnglePreAdjust(true)       // sonic3k.asm:29358 — Tails uses same subq.b #1,d0
                .setTumbleFrameBase(0x31);     // sonic3k.asm:24955 — shared Anim_Tumble

        cachedTails = new SpriteArtSet(
                allTiles,
                mappingFrames,
                dplcFrames,
                0,                             // paletteIndex
                Sonic3kConstants.ART_TILE_TAILS,
                1,                             // frameDelay
                bankSize,
                animationProfile,
                animationSet);
        return cachedTails;
    }

    /**
     * Loads the separate Tails tail appendage art (Obj05 in S3K).
     *
     * <p>S3K uses a completely separate art/mapping/DPLC set for the tail overlay,
     * unlike S2 which reuses the main Tails body art. The tail art is uncompressed
     * at {@code ART_UNC_TAILS_TAIL_ADDR} (139 tiles) with its own
     * {@code Map_Tails_Tail} and {@code DPLC_Tails_Tail} tables.
     */
    public SpriteArtSet loadTailsTail() throws IOException {
        if (cachedTailsTail != null) {
            return cachedTailsTail;
        }

        Pattern[] tiles = loadArtTiles(
                Sonic3kConstants.ART_UNC_TAILS_TAIL_ADDR,
                Sonic3kConstants.ART_UNC_TAILS_TAIL_SIZE);

        // Map_Tails_Tail / DPLC_Tails_Tail are standalone tables (NOT combined 1P+2P).
        // A separate Map_Tails_tail_2P exists for 2P mode. No trimming needed.
        List<SpriteMappingFrame> mappingFrames = loadMappingFrames(Sonic3kConstants.MAP_TAILS_TAIL_ADDR);
        List<SpriteDplcFrame> dplcFrames = loadDplcFrames(Sonic3kConstants.DPLC_TAILS_TAIL_ADDR);

        int bankSize = resolveBankSize(dplcFrames, mappingFrames);

        cachedTailsTail = new SpriteArtSet(
                tiles,
                mappingFrames,
                dplcFrames,
                0,                                      // paletteIndex
                Sonic3kConstants.ART_TILE_TAILS_TAIL,
                1,                                      // frameDelay
                bankSize,
                null,                                   // no animation profile needed
                null);                                  // no animation set needed
        return cachedTailsTail;
    }

    /**
     * Loads the complete Super Sonic art set (mappings, DPLCs, animations).
     *
     * <p>Super Sonic uses the same concatenated main+extra art tiles as normal Sonic,
     * but has its own mapping and DPLC tables ({@code Map_SuperSonic} / {@code PLC_SuperSonic}).
     * These are standalone 251-entry tables (not combined 1P+2P), so no trimming is needed.
     */
    public SpriteArtSet loadSuperSonicArtSet() throws IOException {
        if (cachedSuperSonic != null) {
            return cachedSuperSonic;
        }

        // Reuse the same concatenated art tiles as normal Sonic
        Pattern[] mainTiles = loadArtTiles(
                Sonic3kConstants.ART_UNC_SONIC_ADDR,
                Sonic3kConstants.ART_UNC_SONIC_SIZE);
        Pattern[] extraTiles = loadArtTiles(
                Sonic3kConstants.ART_UNC_SONIC_EXTRA_ADDR,
                Sonic3kConstants.ART_UNC_SONIC_EXTRA_SIZE);
        Pattern[] allTiles = new Pattern[mainTiles.length + extraTiles.length];
        System.arraycopy(mainTiles, 0, allTiles, 0, mainTiles.length);
        System.arraycopy(extraTiles, 0, allTiles, mainTiles.length, extraTiles.length);

        // Load Super Sonic mappings - standalone 251-entry table, standard parser works
        List<SpriteMappingFrame> mappingFrames = loadMappingFrames(Sonic3kConstants.MAP_SUPER_SONIC_ADDR);

        // Load Super Sonic DPLCs with explicit count (first word is NOT entry count)
        List<SpriteDplcFrame> dplcFrames = loadDplcFrames(
                Sonic3kConstants.DPLC_SUPER_SONIC_ADDR,
                Sonic3kConstants.SUPER_SONIC_FRAME_COUNT);

        // Post-process DPLCs: frames >= threshold use Extra art tiles
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
        SpriteAnimationSet animationSet = loadSuperSonicAnimationSet();

        SpriteAnimationProfile animationProfile = new ScriptedVelocityAnimationProfile()
                .setIdleAnimId(Sonic3kAnimationIds.WAIT)
                .setWalkAnimId(Sonic3kAnimationIds.WALK)
                .setRunAnimId(Sonic3kAnimationIds.RUN)
                .setRollAnimId(Sonic3kAnimationIds.ROLL)
                .setRoll2AnimId(Sonic3kAnimationIds.ROLL2)
                .setPushAnimId(Sonic3kAnimationIds.PUSH)
                .setDuckAnimId(Sonic3kAnimationIds.DUCK)
                .setLookUpAnimId(Sonic3kAnimationIds.LOOK_UP)
                .setSpindashAnimId(Sonic3kAnimationIds.SPINDASH)
                .setSpringAnimId(Sonic3kAnimationIds.SPRING)
                .setDeathAnimId(Sonic3kAnimationIds.DEATH)
                .setDrownAnimId(Sonic3kAnimationIds.DROWN)
                .setHurtAnimId(Sonic3kAnimationIds.HURT)
                .setSkidAnimId(Sonic3kAnimationIds.SKID)
                .setAirAnimId(Sonic3kAnimationIds.WALK)
                .setBalanceAnimId(Sonic3kAnimationIds.BALANCE)
                .setBalance2AnimId(Sonic3kAnimationIds.BALANCE2)
                .setBalance3AnimId(Sonic3kAnimationIds.BALANCE3)
                .setBalance4AnimId(Sonic3kAnimationIds.BALANCE4)
                .setWalkSpeedThreshold(0x40)
                .setRunSpeedThreshold(0x600)
                .setFallbackFrame(0)
                .setAnglePreAdjust(true)       // sonic3k.asm:24816 — same subq.b #1,d0
                .setTumbleFrameBase(0x31);     // sonic3k.asm:24955 — shared Anim_Tumble

        cachedSuperSonic = new SpriteArtSet(
                allTiles,
                mappingFrames,
                dplcFrames,
                0,                             // paletteIndex
                Sonic3kConstants.ART_TILE_SONIC,
                1,                             // frameDelay
                bankSize,
                animationProfile,
                animationSet);
        return cachedSuperSonic;
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

    // ===== Delegating loaders (implementations in S3kSpriteDataLoader) =====

    private Pattern[] loadArtTiles(int artAddr, int artSize) throws IOException {
        return S3kSpriteDataLoader.loadArtTiles(reader, artAddr, artSize);
    }

    private List<SpriteMappingFrame> loadMappingFrames(int mappingAddr) {
        return S3kSpriteDataLoader.loadMappingFrames(reader, mappingAddr);
    }

    private List<SpriteDplcFrame> loadDplcFrames(int dplcAddr) {
        return S3kSpriteDataLoader.loadDplcFrames(reader, dplcAddr);
    }

    private List<SpriteDplcFrame> loadDplcFrames(int dplcAddr, int frameCount) {
        return S3kSpriteDataLoader.loadDplcFrames(reader, dplcAddr, frameCount);
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

    private SpriteAnimationSet loadTailsAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();
        int base = Sonic3kConstants.TAILS_ANIM_DATA_ADDR;
        int count = Sonic3kConstants.TAILS_ANIM_SCRIPT_COUNT;

        for (int i = 0; i < count; i++) {
            int scriptAddr = base + reader.readU16BE(base + i * 2);
            SpriteAnimationScript script = parseAnimationScript(scriptAddr);
            set.addScript(i, script);
        }
        return set;
    }

    private SpriteAnimationScript parseAnimationScript(int scriptAddr) {
        return S3kSpriteDataLoader.parseAnimationScript(reader, scriptAddr);
    }

    private int resolveBankSize(List<SpriteDplcFrame> dplcFrames, List<SpriteMappingFrame> mappingFrames) {
        return S3kSpriteDataLoader.resolveBankSize(dplcFrames, mappingFrames);
    }
}
