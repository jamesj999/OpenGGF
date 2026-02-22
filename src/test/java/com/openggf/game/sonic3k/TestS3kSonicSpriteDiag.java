package com.openggf.game.sonic3k;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
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

    @Test
    public void verifyRomMappingBytes() {
        int mappingAddr = Sonic3kConstants.MAP_SONIC_ADDR;
        int dplcAddr = Sonic3kConstants.DPLC_SONIC_ADDR;
        int offsetTableSize = reader.readU16BE(mappingAddr);
        int frameCount = offsetTableSize / 2;
        System.out.println("=== ROM Mapping Address Verification ===");
        System.out.println("MAP_SONIC_ADDR: 0x" + Integer.toHexString(mappingAddr));
        System.out.println("DPLC_SONIC_ADDR: 0x" + Integer.toHexString(dplcAddr));
        System.out.println("Offset table size: " + offsetTableSize + " (0x" + Integer.toHexString(offsetTableSize) + ")");
        System.out.println("Frame count: " + frameCount);

        // Dump raw bytes for specific frames
        for (int frameIdx : new int[]{0, 7, 150, 186}) {
            if (frameIdx >= frameCount) continue;
            int offset = reader.readU16BE(mappingAddr + frameIdx * 2);
            int frameAddr = mappingAddr + offset;
            System.out.printf("%nFrame %d: offset=0x%04X, addr=0x%06X%n", frameIdx, offset, frameAddr);

            // Read piece count
            int pieceCount = reader.readU16BE(frameAddr);
            System.out.printf("  pieceCount=%d%n", pieceCount);

            // Dump raw bytes for each piece (6 bytes each)
            for (int p = 0; p < pieceCount && p < 8; p++) {
                int pAddr = frameAddr + 2 + p * 6;
                StringBuilder sb = new StringBuilder();
                for (int b = 0; b < 6; b++) {
                    sb.append(String.format("%02X ", reader.readU8(pAddr + b)));
                }
                int y = (byte) reader.readU8(pAddr);
                int size = reader.readU8(pAddr + 1);
                int tileWord = reader.readU16BE(pAddr + 2);
                int x = (short) reader.readU16BE(pAddr + 4);
                int w = ((size >> 2) & 3) + 1;
                int h = (size & 3) + 1;
                int tileIdx = tileWord & 0x7FF;
                System.out.printf("  Piece %d: raw=[%s] Y=%d size=0x%02X(%dx%d) tile=0x%04X(%d) X=%d%n",
                        p, sb.toString().trim(), y, size, w, h, tileWord, tileIdx, x);
            }

            // Also dump 8 extra raw bytes after last piece to detect alignment issues
            int afterPieces = frameAddr + 2 + pieceCount * 6;
            StringBuilder extra = new StringBuilder();
            for (int b = 0; b < 8 && (afterPieces + b) < reader.size(); b++) {
                extra.append(String.format("%02X ", reader.readU8(afterPieces + b)));
            }
            System.out.printf("  Bytes after pieces: [%s]%n", extra.toString().trim());
        }
    }

    @Test
    public void verifyDisasmAddresses() {
        // Frame 7 mapping data should be at 0x146A94 per disasm
        // Expected: 00 02 FC 0A 00 00 FF F4 EC 0D 00 09 FF EC
        System.out.println("=== Disasm Address Verification ===");
        int[] disasmAddrs = {0x146A0C, 0x146A94, 0x14756A, 0x147828};
        String[] names = {"Frame 0 (disasm)", "Frame 7 (disasm)", "Frame 150 (disasm)", "Frame 186 (disasm)"};
        for (int i = 0; i < disasmAddrs.length; i++) {
            int addr = disasmAddrs[i];
            StringBuilder sb = new StringBuilder();
            for (int b = 0; b < 20 && addr + b < reader.size(); b++) {
                sb.append(String.format("%02X ", reader.readU8(addr + b)));
            }
            System.out.printf("%s at 0x%06X: [%s]%n", names[i], addr, sb.toString().trim());
        }

        // Now check the ACTUAL ROM addresses from our offset table
        int mappingAddr = Sonic3kConstants.MAP_SONIC_ADDR;
        int[] romFrameAddrs = new int[4];
        int[] frameIdxs = {0, 7, 150, 186};
        for (int i = 0; i < frameIdxs.length; i++) {
            int offset = reader.readU16BE(mappingAddr + frameIdxs[i] * 2);
            romFrameAddrs[i] = mappingAddr + offset;
            StringBuilder sb = new StringBuilder();
            for (int b = 0; b < 20 && romFrameAddrs[i] + b < reader.size(); b++) {
                sb.append(String.format("%02X ", reader.readU8(romFrameAddrs[i] + b)));
            }
            System.out.printf("Frame %d (ROM table) at 0x%06X: [%s]%n", frameIdxs[i], romFrameAddrs[i], sb.toString().trim());
        }

        // Check first 10 offset table entries
        System.out.println("\nFirst 10 offset table entries:");
        for (int i = 0; i < 10; i++) {
            int offset = reader.readU16BE(mappingAddr + i * 2);
            System.out.printf("  [%d] offset=0x%04X -> addr=0x%06X%n", i, offset, mappingAddr + offset);
        }
    }

    @Test
    public void verifyTileOffsetSequence() {
        // For each frame, verify that DPLC loads cover exactly the tiles
        // referenced by the mapping pieces (no gaps, no overlaps)
        System.out.println("=== Tile Offset Sequence Verification ===");

        // Check just idle and roll frames
        SpriteAnimationScript waitScript = animSet.getScript(5);
        SpriteAnimationScript rollScript = animSet.getScript(2);

        if (!waitScript.frames().isEmpty()) {
            int idleFrame = waitScript.frames().get(0);
            System.out.println("\n--- Idle Frame " + idleFrame + " ---");
            verifyFrameTileSequence(idleFrame);
        }
        if (!rollScript.frames().isEmpty()) {
            int rollFrame = rollScript.frames().get(0);
            System.out.println("\n--- Roll Frame " + rollFrame + " ---");
            verifyFrameTileSequence(rollFrame);
        }
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

    private void verifyFrameTileSequence(int frameIndex) {
        SpriteMappingFrame mapping = artSet.mappingFrames().get(frameIndex);
        SpriteDplcFrame dplc = artSet.dplcFrames().get(frameIndex);

        // Build a map of which bank positions are loaded by DPLC
        int dplcTotal = 0;
        for (TileLoadRequest req : dplc.requests()) {
            dplcTotal += req.count();
        }
        boolean[] loaded = new boolean[Math.max(dplcTotal, 64)];
        int pos = 0;
        for (TileLoadRequest req : dplc.requests()) {
            for (int i = 0; i < req.count(); i++) {
                if (pos < loaded.length) loaded[pos] = true;
                pos++;
            }
        }

        // Check each mapping piece's tile range
        for (int p = 0; p < mapping.pieces().size(); p++) {
            SpriteMappingPiece piece = mapping.pieces().get(p);
            int w = piece.widthTiles();
            int h = piece.heightTiles();
            System.out.printf("  Piece %d (%dx%d, tileIdx=%d): tiles ", p, w, h, piece.tileIndex());
            for (int tx = 0; tx < w; tx++) {
                for (int ty = 0; ty < h; ty++) {
                    int tileOffset = tx * h + ty;
                    int bankPos = piece.tileIndex() + tileOffset;
                    boolean isLoaded = bankPos < loaded.length && loaded[bankPos];
                    System.out.printf("[%d:%s] ", bankPos, isLoaded ? "OK" : "MISSING");
                }
            }
            System.out.println();
        }
    }
}
