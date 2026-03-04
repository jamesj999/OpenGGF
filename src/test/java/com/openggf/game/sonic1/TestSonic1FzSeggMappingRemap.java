package com.openggf.game.sonic1;

import com.openggf.game.sonic1.objects.bosses.Sonic1BossMappings;
import org.junit.Test;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TestSonic1FzSeggMappingRemap {

    @Test
    @SuppressWarnings("unchecked")
    public void intubeOverlayUsesWrappedFzBossTilesAfterObjectBaseAdd() throws Exception {
        List<SpriteMappingFrame> raw = Sonic1BossMappings.createSEggMappings();

        Method method = Sonic1ObjectArtProvider.class.getDeclaredMethod(
                "remapMappingsForObjectBase",
                List.class,
                int.class,
                int.class
        );
        method.setAccessible(true);

        List<SpriteMappingFrame> remapped = (List<SpriteMappingFrame>) method.invoke(
                null,
                raw,
                Sonic1Constants.ART_TILE_FZ_EGGMAN_NO_VEHICLE,
                Sonic1Constants.ART_TILE_FZ_BOSS
        );

        // Frame 9 = .intube; piece 4 is one of the four tube overlay strips:
        // spritePiece -$10,-$20,4,2,$6F0,1,1,1,0
        SpriteMappingPiece tubePiece = remapped.get(9).pieces().get(4);
        assertEquals("Tube overlay tile should wrap to FZ boss local tile $60", 0x60, tubePiece.tileIndex());
        assertFalse("H-flip should cancel after add.w overflow", tubePiece.hFlip());
        assertFalse("V-flip should cancel after add.w overflow", tubePiece.vFlip());
        assertEquals("Palette should resolve to line 2 after add.w overflow", 2, tubePiece.paletteIndex());
    }

    @Test
    public void fzLegsAndDamagedMappingsStayWithinFzEggmanPatternRange() {
        // Nem_FzEggman ("Boss - Eggman after FZ Fight") is 0x4C patterns in REV01.
        // Map_FZLegs and Map_FZDamaged must remain within that local tile range.
        int maxLegsTile = maxTileIndex(Sonic1BossMappings.createFZLegsMappings());
        int maxDamagedTile = maxTileIndex(Sonic1BossMappings.createFZDamagedMappings());

        assertEquals("Map_FZLegs max tile should be $1F", 0x1F, maxLegsTile);
        assertEquals("Map_FZDamaged max tile should be $4B", 0x4B, maxDamagedTile);
    }

    private static int maxTileIndex(List<SpriteMappingFrame> frames) {
        int max = 0;
        for (SpriteMappingFrame frame : frames) {
            for (SpriteMappingPiece piece : frame.pieces()) {
                int lastTile = piece.tileIndex() + (piece.widthTiles() * piece.heightTiles()) - 1;
                if (lastTile > max) {
                    max = lastTile;
                }
            }
        }
        return max;
    }

}
