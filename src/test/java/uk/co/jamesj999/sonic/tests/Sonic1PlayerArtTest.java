package uk.co.jamesj999.sonic.tests;

import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1PlayerArt;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.sprites.art.SpriteArtSet;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Tests for Sonic 1 player sprite art loading.
 * Requires the Sonic 1 ROM to be present in the working directory.
 * Uses a local ROM instance to avoid contaminating singletons
 * (GameModuleRegistry, AudioManager) that affect other tests.
 */
public class Sonic1PlayerArtTest {

    private RomByteReader reader;

    @Before
    public void setUp() throws Exception {
        File romFile = RomTestUtils.ensureSonic1RomAvailable();
        Rom rom = new Rom();
        rom.open(romFile.getAbsolutePath());
        reader = RomByteReader.fromRom(rom);
    }

    @Test
    public void sonicArtLoadsCorrectTileCount() throws Exception {
        Sonic1PlayerArt artLoader = new Sonic1PlayerArt(reader);
        SpriteArtSet sonic = artLoader.loadForCharacter("sonic");

        assertNotNull("SpriteArtSet should not be null", sonic);
        int expectedTiles = Sonic1Constants.ART_UNC_SONIC_SIZE / Pattern.PATTERN_SIZE_IN_ROM;
        assertEquals("Tile count should match ROM art size", expectedTiles, sonic.artTiles().length);
    }

    @Test
    public void sonicMappingFrameCount() throws Exception {

        Sonic1PlayerArt artLoader = new Sonic1PlayerArt(reader);
        SpriteArtSet sonic = artLoader.loadForCharacter("sonic");

        assertNotNull(sonic);
        assertEquals("Should have 88 mapping frames",
                Sonic1Constants.SONIC_MAPPING_FRAME_COUNT, sonic.mappingFrames().size());
    }

    @Test
    public void sonicDplcFrameCountMatchesMappings() throws Exception {

        Sonic1PlayerArt artLoader = new Sonic1PlayerArt(reader);
        SpriteArtSet sonic = artLoader.loadForCharacter("sonic");

        assertNotNull(sonic);
        assertEquals("DPLC and mapping frame counts should match",
                sonic.mappingFrames().size(), sonic.dplcFrames().size());
    }

    @Test
    public void sonicAnimationScriptCount() throws Exception {

        Sonic1PlayerArt artLoader = new Sonic1PlayerArt(reader);
        SpriteArtSet sonic = artLoader.loadForCharacter("sonic");

        assertNotNull(sonic);
        assertNotNull("Animation set should not be null", sonic.animationSet());
        assertEquals("Should have 31 animation scripts",
                Sonic1Constants.SONIC_ANIM_SCRIPT_COUNT, sonic.animationSet().getScriptCount());
    }

    @Test
    public void standingFrameHasFourPieces() throws Exception {

        Sonic1PlayerArt artLoader = new Sonic1PlayerArt(reader);
        SpriteArtSet sonic = artLoader.loadForCharacter("sonic");

        assertNotNull(sonic);
        // Frame 1 = MS_Stand (from s1disasm _maps/Sonic.asm)
        SpriteMappingFrame standFrame = sonic.mappingFrames().get(1);
        assertEquals("Standing frame should have 4 pieces", 4, standFrame.pieces().size());

        // First piece of standing frame: y=-0x14, w=3, h=1, tile=0, x=-0x10
        var firstPiece = standFrame.pieces().get(0);
        assertEquals(-0x14, firstPiece.yOffset());
        assertEquals(3, firstPiece.widthTiles());
        assertEquals(1, firstPiece.heightTiles());
        assertEquals(0, firstPiece.tileIndex());
        assertEquals(-0x10, firstPiece.xOffset());
    }

    @Test
    public void walkAnimationHasSixFrames() throws Exception {

        Sonic1PlayerArt artLoader = new Sonic1PlayerArt(reader);
        SpriteArtSet sonic = artLoader.loadForCharacter("sonic");

        assertNotNull(sonic);
        // Walk animation (id 0) has 6 frames: Walk13,14,15,16,11,12
        var walkScript = sonic.animationSet().getScript(0);
        assertNotNull("Walk animation script should exist", walkScript);
        assertEquals("Walk should have 6 frames", 6, walkScript.frames().size());
        // fr_Walk13=8, fr_Walk14=9, fr_Walk15=0xA, fr_Walk16=0xB, fr_Walk11=6, fr_Walk12=7
        assertEquals(8, (int) walkScript.frames().get(0));
        assertEquals(9, (int) walkScript.frames().get(1));
        assertEquals(0x0A, (int) walkScript.frames().get(2));
        assertEquals(0x0B, (int) walkScript.frames().get(3));
        assertEquals(6, (int) walkScript.frames().get(4));
        assertEquals(7, (int) walkScript.frames().get(5));
    }

    @Test
    public void nullFrameHasZeroPieces() throws Exception {

        Sonic1PlayerArt artLoader = new Sonic1PlayerArt(reader);
        SpriteArtSet sonic = artLoader.loadForCharacter("sonic");

        assertNotNull(sonic);
        // Frame 0 = MS_Null (empty frame)
        SpriteMappingFrame nullFrame = sonic.mappingFrames().get(0);
        assertEquals("Null frame should have 0 pieces", 0, nullFrame.pieces().size());
    }

    @Test
    public void unknownCharacterReturnsNull() throws Exception {

        Sonic1PlayerArt artLoader = new Sonic1PlayerArt(reader);

        assertNull("Tails should return null for S1", artLoader.loadForCharacter("tails"));
        assertNull("Unknown character should return null", artLoader.loadForCharacter("knuckles"));
    }

    @Test
    public void bankSizeIsPositive() throws Exception {

        Sonic1PlayerArt artLoader = new Sonic1PlayerArt(reader);
        SpriteArtSet sonic = artLoader.loadForCharacter("sonic");

        assertNotNull(sonic);
        assertTrue("Bank size should be positive", sonic.bankSize() > 0);
    }

    @Test
    public void animationProfileIsSet() throws Exception {

        Sonic1PlayerArt artLoader = new Sonic1PlayerArt(reader);
        SpriteArtSet sonic = artLoader.loadForCharacter("sonic");

        assertNotNull(sonic);
        assertNotNull("Animation profile should be set", sonic.animationProfile());
    }
}
