package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.animation.AniPlcParser;
import com.openggf.level.animation.AniPlcScriptState;

import java.util.List;
import java.util.logging.Logger;
import com.openggf.game.GameServices;

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
    private final List<AniPlcScriptState> scripts;

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

        this.scripts = AniPlcParser.parseScripts(reader, aniPlcAddr);
        AniPlcParser.ensurePatternCapacity(scripts, level);
        // Also ensure capacity for FirstTree destination
        if (zoneIndex == 0 && actIndex == 1) {
            int firstTreeEnd = Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_DEST_TILE
                    + Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_SIZE / Pattern.PATTERN_SIZE_IN_ROM;
            level.ensurePatternCapacity(firstTreeEnd);
        }
        // ROM parity: AnimateTiles_AIZ1 gates animation behind Dynamic_resize_routine != 0.
        // During intro, tiles $2E6-$303 retain KosinskiM art (beach shoreline).
        // Priming would overwrite this with animation frame 0 data.
        boolean isAiz1Intro = zoneIndex == 0 && actIndex == 0 && !isSkipIntro;
        if (!isAiz1Intro) {
            AniPlcParser.primeScripts(scripts, level, graphicsManager);
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
        // ROM: tst.b (Boss_flag).w / bne.s locret_27848 (sonic3k.asm:53939)
        if (isAizBossActive()) {
            return;
        }
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
        // ROM: tst.b (Boss_flag).w / bne.s locret_2787E (sonic3k.asm:53949)
        if (isAizBossActive()) {
            return;
        }
        int cameraX = 0;
        try {
            cameraX = GameServices.camera().getX();
        } catch (Exception e) {
            LOG.fine(() -> "Sonic3kPatternAnimator.updateAiz2: " + e.getMessage());
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
        for (AniPlcScriptState script : scripts) {
            script.tick(level, graphicsManager);
        }
    }

    /**
     * Check if the AIZ boss fight is active.
     * ROM: tst.b (Boss_flag).w / bne.s locret_27848
     */
    private boolean isAizBossActive() {
        try {
            Sonic3kLevelEventManager lem = Sonic3kLevelEventManager.getInstance();
            if (lem != null) {
                Sonic3kAIZEvents aizEvents = lem.getAizEvents();
                if (aizEvents != null) {
                    return aizEvents.isBossFlag();
                }
            }
        } catch (Exception e) {
            LOG.fine(() -> "Sonic3kPatternAnimator.isAizBossActive: " + e.getMessage());
        }
        return false;
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

}
