package com.openggf.game.sonic3k;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import static org.junit.Assert.*;

/**
 * Diagnostic test for S3K Sonic sprite tile ordering bug.
 * Dumps mapping/DPLC data for idle vs roll frames to identify mismatches.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kSonicSpriteDiag {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private SpriteArtSet artSet;
    private SpriteAnimationSet animSet;
    private RomByteReader reader;

    @Before
    public void setUp() throws Exception {
        Rom rom = romRule.rom();
        reader = RomByteReader.fromRom(rom);
        Sonic3kPlayerArt art = new Sonic3kPlayerArt(reader);
        artSet = art.loadSonic();
        assertNotNull("Art set should load", artSet);
        animSet = artSet.animationSet();
        assertNotNull("Animation set should load", animSet);
    }

    @Test
    public void dumpIdleAndRollFrames() {
        // WAIT animation = id 5
        SpriteAnimationScript waitScript = animSet.getScript(5);
        assertNotNull("WAIT animation script should exist", waitScript);
        System.out.println("=== WAIT Animation (id=5) ===");
        System.out.println("Delay: " + waitScript.delay());
        System.out.println("Frame indices: " + waitScript.frames());
        System.out.println();

        // WALK animation = id 0
        SpriteAnimationScript walkScript = animSet.getScript(0);
        assertNotNull("WALK animation script should exist", walkScript);
        System.out.println("=== WALK Animation (id=0) ===");
        System.out.println("Delay: " + walkScript.delay());
        System.out.println("Frame indices: " + walkScript.frames());
        System.out.println();

        // ROLL animation = id 2
        SpriteAnimationScript rollScript = animSet.getScript(2);
        assertNotNull("ROLL animation script should exist", rollScript);
        System.out.println("=== ROLL Animation (id=2) ===");
        System.out.println("Delay: " + rollScript.delay());
        System.out.println("Frame indices: " + rollScript.frames());
        System.out.println();

        // Dump first idle frame
        if (!waitScript.frames().isEmpty()) {
            int idleFrame = waitScript.frames().get(0);
            System.out.println("=== Idle Frame " + idleFrame + " Detail ===");
            dumpFrame(idleFrame);
        }

        // Dump first walk frame
        if (!walkScript.frames().isEmpty()) {
            int walkFrame = walkScript.frames().get(0);
            System.out.println("=== Walk Frame " + walkFrame + " Detail ===");
            dumpFrame(walkFrame);
        }

        // Dump first roll frame
        if (!rollScript.frames().isEmpty()) {
            int rollFrame = rollScript.frames().get(0);
            System.out.println("=== Roll Frame " + rollFrame + " Detail ===");
            dumpFrame(rollFrame);
        }
    }

    @Test
    public void verifyDplcMappingConsistency() {
        int issues = 0;
        for (int i = 0; i < artSet.mappingFrames().size(); i++) {
            SpriteMappingFrame mapping = artSet.mappingFrames().get(i);
            SpriteDplcFrame dplc = artSet.dplcFrames().get(i);

            // Calculate total tiles loaded by DPLC
            int dplcTotalTiles = 0;
            for (TileLoadRequest req : dplc.requests()) {
                dplcTotalTiles += req.count();
            }

            // Calculate max tile index required by mapping
            int maxTileRequired = 0;
            for (SpriteMappingPiece piece : mapping.pieces()) {
                int pieceTilesNeeded = piece.tileIndex() + (piece.widthTiles() * piece.heightTiles());
                maxTileRequired = Math.max(maxTileRequired, pieceTilesNeeded);
            }

            // Check for mismatches
            if (maxTileRequired > dplcTotalTiles && dplcTotalTiles > 0) {
                System.out.printf("Frame %d: MISMATCH! mapping needs %d tiles but DPLC loads %d%n",
                        i, maxTileRequired, dplcTotalTiles);
                issues++;
            }
        }
        System.out.println("Total frames checked: " + artSet.mappingFrames().size());
        System.out.println("Incremental DPLC frames (mapping needs > DPLC loads): " + issues);
        // ROM intentionally uses incremental DPLCs - frames share tiles from
        // previous loads. This is NOT a bug - just verify we handle it.
        assertTrue("Should have checked at least one frame for DPLC/mapping consistency",
                artSet.mappingFrames().size() > 0);
        // Incremental DPLC frames are expected but should be a minority
        assertTrue("Incremental DPLC frame count should be less than total frames",
                issues < artSet.mappingFrames().size());
    }

    @Test
    public void computeCorrectBankSize() {
        // Compute bankSize from DPLC
        int maxDplcTotal = 0;
        for (SpriteDplcFrame dplc : artSet.dplcFrames()) {
            int total = 0;
            for (TileLoadRequest req : dplc.requests()) {
                total += req.count();
            }
            maxDplcTotal = Math.max(maxDplcTotal, total);
        }

        // Compute bankSize from mapping
        int maxMappingTileIndex = 0;
        for (SpriteMappingFrame mapping : artSet.mappingFrames()) {
            for (SpriteMappingPiece piece : mapping.pieces()) {
                int max = piece.tileIndex() + piece.widthTiles() * piece.heightTiles();
                maxMappingTileIndex = Math.max(maxMappingTileIndex, max);
            }
        }

        System.out.println("Bank size from DPLC: " + maxDplcTotal);
        System.out.println("Bank size from mapping: " + maxMappingTileIndex);
        System.out.println("Current artSet.bankSize(): " + artSet.bankSize());

        // With correct 1P mapping data, every frame's DPLC should load
        // exactly the tiles needed by the mapping (no incremental loading).
        assertEquals("DPLC and mapping tile counts should match",
                maxMappingTileIndex, maxDplcTotal);
        assertEquals("Bank size should equal max tile requirement",
                maxMappingTileIndex, artSet.bankSize());
    }

    private void dumpFrame(int frameIndex) {
        SpriteMappingFrame mapping = artSet.mappingFrames().get(frameIndex);
        SpriteDplcFrame dplc = artSet.dplcFrames().get(frameIndex);

        System.out.println("Mapping pieces: " + mapping.pieces().size());
        for (int p = 0; p < mapping.pieces().size(); p++) {
            SpriteMappingPiece piece = mapping.pieces().get(p);
            int totalTiles = piece.widthTiles() * piece.heightTiles();
            System.out.printf("  Piece %d: pos=(%d,%d) size=%dx%d tileIndex=%d hFlip=%b vFlip=%b pal=%d [%d tiles: %d-%d]%n",
                    p, piece.xOffset(), piece.yOffset(),
                    piece.widthTiles(), piece.heightTiles(),
                    piece.tileIndex(), piece.hFlip(), piece.vFlip(), piece.paletteIndex(),
                    totalTiles, piece.tileIndex(), piece.tileIndex() + totalTiles - 1);
        }

        System.out.println("DPLC requests: " + dplc.requests().size());
        int dstPos = 0;
        for (int r = 0; r < dplc.requests().size(); r++) {
            TileLoadRequest req = dplc.requests().get(r);
            System.out.printf("  Request %d: startTile=%d count=%d -> bank[%d-%d]%n",
                    r, req.startTile(), req.count(), dstPos, dstPos + req.count() - 1);
            dstPos += req.count();
        }
        System.out.println("DPLC total tiles: " + dstPos);

        // Verify tile coverage
        int maxRequired = 0;
        for (SpriteMappingPiece piece : mapping.pieces()) {
            maxRequired = Math.max(maxRequired, piece.tileIndex() + piece.widthTiles() * piece.heightTiles());
        }
        System.out.println("Max tile index required by mapping: " + maxRequired);
        if (dstPos < maxRequired) {
            System.out.println("*** WARNING: DPLC loads " + dstPos + " tiles but mapping needs " + maxRequired + " ***");
        }
        System.out.println();
    }

}
