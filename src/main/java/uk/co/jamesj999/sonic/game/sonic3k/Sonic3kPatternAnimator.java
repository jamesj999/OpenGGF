package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kConstants;
import uk.co.jamesj999.sonic.game.sonic3k.objects.AizPlaneIntroInstance;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.animation.AnimatedPatternManager;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Animates S3K zone tiles using the ROM's AniPLC script format.
 *
 * <p>The binary format is identical to Sonic 2's Dynamic_Normal scripts
 * ({@code zoneanimstart}/{@code zoneanimdecl} macros):
 * <pre>
 *   dc.w count-1              ; number of scripts minus 1
 *   ; per script:
 *   dc.l (dur&0xFF)<<24|art   ; duration byte + 24-bit art ROM address
 *   dc.w tiles_to_bytes(dest) ; VRAM destination (tile * 32)
 *   dc.b frameCount, tilesPerFrame
 *   ; frame data (per-frame if dur=0xFF, else global duration)
 * </pre>
 *
 * <p>Each zone+act has per-zone trigger logic that gates whether the scripts
 * actually run (boss active, camera position, intro state, etc.).
 */
class Sonic3kPatternAnimator implements AnimatedPatternManager {
    private static final Logger LOG = Logger.getLogger(Sonic3kPatternAnimator.class.getName());

    private final Level level;
    private final GraphicsManager graphicsManager = GraphicsManager.getInstance();
    private final int zoneIndex;
    private final int actIndex;
    private final boolean isSkipIntro;
    private final List<ScriptState> scripts;

    // AIZ2 FirstTree one-shot state
    private final Pattern[] firstTreePatterns;
    private boolean firstTreeApplied;

    Sonic3kPatternAnimator(RomByteReader reader, Level level,
                           int zoneIndex, int actIndex, boolean isSkipIntro) {
        this.level = level;
        this.zoneIndex = zoneIndex;
        this.actIndex = actIndex;
        this.isSkipIntro = isSkipIntro;

        int aniPlcAddr = resolveAniPlcAddr(zoneIndex, actIndex);
        if (aniPlcAddr < 0) {
            this.scripts = List.of();
            this.firstTreePatterns = null;
            return;
        }

        this.scripts = parseAniPlc(reader, aniPlcAddr);
        ensurePatternCapacity(scripts);
        // ROM parity: AnimateTiles_AIZ1 gates animation behind Dynamic_resize_routine != 0.
        // During intro, tiles $2E6-$303 retain KosinskiM art (beach shoreline).
        // Priming would overwrite this with animation frame 0 data.
        boolean isAiz1Intro = zoneIndex == 0 && actIndex == 0 && !isSkipIntro;
        if (!isAiz1Intro) {
            primeScripts(scripts);
        }

        // Pre-load FirstTree art for AIZ2
        if (zoneIndex == 0 && actIndex == 1) {
            this.firstTreePatterns = loadFirstTreeArt(reader);
        } else {
            this.firstTreePatterns = null;
        }
        this.firstTreeApplied = false;
    }

    @Override
    public void update() {
        if (scripts.isEmpty()) {
            return;
        }
        switch (zoneIndex) {
            case 0 -> {
                if (actIndex == 0) {
                    updateAiz1();
                } else {
                    updateAiz2();
                }
            }
            default -> runAllScripts();
        }
    }

    // ===== Zone-specific trigger logic =====

    /**
     * AIZ1: Boss_flag == 0 AND Dynamic_resize_routine != 0.
     * In skip-intro mode, Dynamic_resize_routine is always non-zero.
     * In intro mode, it becomes non-zero once the terrain swap fires
     * (tracked by AizPlaneIntroInstance.isMainLevelPhaseActive()).
     */
    private void updateAiz1() {
        // TODO: check boss flag when boss system is implemented
        if (isSkipIntro || AizPlaneIntroInstance.isMainLevelPhaseActive()) {
            runAllScripts();
        }
    }

    /**
     * AIZ2: Boss_flag == 0, then:
     * - Camera X >= 0x1C0: run all 5 scripts
     * - Camera X < 0x1C0: run only script #0 (fire), show FirstTree art at tile 0xCA
     */
    private void updateAiz2() {
        // TODO: check boss flag when boss system is implemented
        int cameraX = 0;
        try {
            cameraX = Camera.getInstance().getX();
        } catch (Exception ignored) {
        }

        if (cameraX >= 0x1C0) {
            runAllScripts();
            firstTreeApplied = false;
        } else {
            // Run only script #0 (fire/explosion animation)
            if (!scripts.isEmpty()) {
                scripts.get(0).tick(level, graphicsManager);
            }
            // Apply FirstTree static art once
            if (!firstTreeApplied && firstTreePatterns != null) {
                applyFirstTreeArt();
                firstTreeApplied = true;
            }
        }
    }

    private void runAllScripts() {
        for (ScriptState script : scripts) {
            script.tick(level, graphicsManager);
        }
    }

    // ===== AniPLC address resolution =====

    private static int resolveAniPlcAddr(int zoneIndex, int actIndex) {
        if (zoneIndex == 0) {
            return actIndex == 0
                    ? Sonic3kConstants.ANIPLC_AIZ1_ADDR
                    : Sonic3kConstants.ANIPLC_AIZ2_ADDR;
        }
        // Future zones: add cases here
        return -1;
    }

    // ===== Binary parsing =====

    private List<ScriptState> parseAniPlc(RomByteReader reader, int addr) {
        int countMinus1 = reader.readU16BE(addr);
        if (countMinus1 == 0xFFFF) {
            return List.of();
        }

        int scriptCount = countMinus1 + 1;
        int pos = addr + 2;
        List<ScriptState> result = new ArrayList<>(scriptCount);

        for (int i = 0; i < scriptCount; i++) {
            int header = readU32BE(reader, pos);
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
            result.add(new ScriptState(globalDuration, destTileIndex, frameTileIds,
                    frameDurations, tilesPerFrame, artPatterns));

            pos = dataStart + dataLenAligned;
        }

        LOG.info(String.format("Parsed %d AniPLC scripts from 0x%06X", result.size(), addr));
        return result;
    }

    private Pattern[] loadArtPatterns(RomByteReader reader, int artAddr,
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
            byte[] tileData = new byte[Pattern.PATTERN_SIZE_IN_ROM];
            System.arraycopy(data, start, tileData, 0, Pattern.PATTERN_SIZE_IN_ROM);
            pattern.fromSegaFormat(tileData);
            patterns[i] = pattern;
        }
        return patterns;
    }

    // ===== FirstTree art =====

    private Pattern[] loadFirstTreeArt(RomByteReader reader) {
        int addr = Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_ADDR;
        int size = Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_SIZE;
        int tileCount = size / Pattern.PATTERN_SIZE_IN_ROM;

        if (addr + size > reader.size()) {
            return null;
        }

        byte[] data = reader.slice(addr, size);
        Pattern[] patterns = new Pattern[tileCount];
        for (int i = 0; i < tileCount; i++) {
            Pattern pattern = new Pattern();
            int start = i * Pattern.PATTERN_SIZE_IN_ROM;
            byte[] tileData = new byte[Pattern.PATTERN_SIZE_IN_ROM];
            System.arraycopy(data, start, tileData, 0, Pattern.PATTERN_SIZE_IN_ROM);
            pattern.fromSegaFormat(tileData);
            patterns[i] = pattern;
        }
        return patterns;
    }

    private void applyFirstTreeArt() {
        int destTile = Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_DEST_TILE;
        int maxPatterns = level.getPatternCount();
        boolean canUpdateTextures = graphicsManager.isGlInitialized();
        for (int i = 0; i < firstTreePatterns.length; i++) {
            int destIndex = destTile + i;
            if (destIndex >= maxPatterns) {
                break;
            }
            Pattern dest = level.getPattern(destIndex);
            dest.copyFrom(firstTreePatterns[i]);
            if (canUpdateTextures) {
                graphicsManager.updatePatternTexture(dest, destIndex);
            }
        }
    }

    // ===== Helpers =====

    private void ensurePatternCapacity(List<ScriptState> scripts) {
        for (ScriptState script : scripts) {
            int required = script.requiredPatternCount();
            if (required > 0) {
                level.ensurePatternCapacity(required);
            }
        }
        // Also ensure capacity for FirstTree destination
        if (zoneIndex == 0 && actIndex == 1) {
            int firstTreeEnd = Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_DEST_TILE
                    + Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_SIZE / Pattern.PATTERN_SIZE_IN_ROM;
            level.ensurePatternCapacity(firstTreeEnd);
        }
    }

    private void primeScripts(List<ScriptState> scripts) {
        for (ScriptState script : scripts) {
            script.prime(level, graphicsManager);
        }
    }

    private int readU32BE(RomByteReader reader, int addr) {
        int upper = reader.readU16BE(addr);
        int lower = reader.readU16BE(addr + 2);
        return (upper << 16) | lower;
    }

    // ===== ScriptState (identical logic to Sonic2PatternAnimator.ScriptState) =====

    private static class ScriptState {
        private final byte globalDuration;
        private final int destTileIndex;
        private final int[] frameTileIds;
        private final int[] frameDurations;
        private final int tilesPerFrame;
        private final Pattern[] artPatterns;
        private int timer;
        private int frameIndex;

        private ScriptState(byte globalDuration,
                            int destTileIndex,
                            int[] frameTileIds,
                            int[] frameDurations,
                            int tilesPerFrame,
                            Pattern[] artPatterns) {
            this.globalDuration = globalDuration;
            this.destTileIndex = destTileIndex;
            this.frameTileIds = frameTileIds;
            this.frameDurations = frameDurations;
            this.tilesPerFrame = tilesPerFrame;
            this.artPatterns = artPatterns;
            this.timer = 0;
            this.frameIndex = 0;
        }

        private void tick(Level level, GraphicsManager graphicsManager) {
            if (frameTileIds.length == 0 || artPatterns.length == 0) {
                return;
            }
            if (timer > 0) {
                timer = (timer - 1) & 0xFF;
                return;
            }

            int currentFrame = frameIndex;
            if (currentFrame >= frameTileIds.length) {
                currentFrame = 0;
                frameIndex = 0;
            }
            frameIndex = currentFrame + 1;

            int duration = globalDuration & 0xFF;
            if (globalDuration < 0 && frameDurations != null) {
                duration = frameDurations[currentFrame];
            }
            timer = duration & 0xFF;

            int tileId = frameTileIds[currentFrame];
            applyFrame(level, graphicsManager, tileId);
        }

        private int requiredPatternCount() {
            return destTileIndex + Math.max(tilesPerFrame, 1);
        }

        private void prime(Level level, GraphicsManager graphicsManager) {
            if (frameTileIds.length == 0 || artPatterns.length == 0) {
                return;
            }
            applyFrame(level, graphicsManager, frameTileIds[0]);
        }

        private void applyFrame(Level level, GraphicsManager graphicsManager, int tileId) {
            int maxPatterns = level.getPatternCount();
            boolean canUpdateTextures = graphicsManager.isGlInitialized();
            for (int i = 0; i < tilesPerFrame; i++) {
                int srcIndex = tileId + i;
                int destIndex = destTileIndex + i;
                if (srcIndex < 0 || srcIndex >= artPatterns.length) {
                    continue;
                }
                if (destIndex < 0 || destIndex >= maxPatterns) {
                    continue;
                }
                Pattern dest = level.getPattern(destIndex);
                dest.copyFrom(artPatterns[srcIndex]);
                if (canUpdateTextures) {
                    graphicsManager.updatePatternTexture(dest, destIndex);
                }
            }
        }
    }
}
