package com.openggf.physics;

import com.openggf.level.ChunkDesc;
import com.openggf.level.SolidTile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TestObjectTerrainUtils {

    @Test
    void getAngle_ignoresChunkFlipFlagsToPreserveObjectTerrainConvention() throws Exception {
        SolidTile tile = createMirroredTile();
        ChunkDesc mirroredDesc = new ChunkDesc(7 | 0x0400 | 0x0800);

        Method method = ObjectTerrainUtils.class.getDeclaredMethod("getAngle", SolidTile.class, ChunkDesc.class);
        method.setAccessible(true);
        byte angle = (byte) method.invoke(null, tile, mirroredDesc);

        assertEquals(tile.getAngle(), angle);
        assertNotEquals(tile.getAngle(true, true), angle);
    }

    @Test
    void getAngle_flipAwareModeAppliesChunkFlipFlagsForMgzPlatformPath() throws Exception {
        SolidTile tile = createMirroredTile();
        ChunkDesc mirroredDesc = new ChunkDesc(7 | 0x0400 | 0x0800);

        Method method = ObjectTerrainUtils.class.getDeclaredMethod(
                "getAngle", SolidTile.class, ChunkDesc.class, boolean.class);
        method.setAccessible(true);
        byte angle = (byte) method.invoke(null, tile, mirroredDesc, true);

        assertEquals(tile.getAngle(true, true), angle);
        assertNotEquals(tile.getAngle(), angle);
    }

    private static SolidTile createMirroredTile() {
        SolidTile tile = new SolidTile(
                7,
                new byte[SolidTile.TILE_SIZE_IN_ROM],
                new byte[SolidTile.TILE_SIZE_IN_ROM],
                (byte) 0x20);
        return tile;
    }
}
