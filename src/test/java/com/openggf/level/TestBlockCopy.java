package com.openggf.level;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestBlockCopy {

    @Test
    public void copyCreatesIndependentBlock() {
        Block original = new Block(8);
        // Set a known chunk desc at position (2,3)
        original.getChunkDesc(2, 3).set(0x1234);

        Block copy = original.copy();

        // Copy has the same value
        assertEquals(0x1234, copy.getChunkDesc(2, 3).get());

        // Modifying copy does not affect original
        copy.getChunkDesc(2, 3).set(0x5678);
        assertEquals(0x1234, original.getChunkDesc(2, 3).get());
        assertEquals(0x5678, copy.getChunkDesc(2, 3).get());
    }

    @Test
    public void copiedBlockPreservesGridSide() {
        Block original = new Block(16); // S1-style 16x16 grid
        Block copy = original.copy();
        // Should not throw for valid S1 coordinates
        assertNotNull(copy.getChunkDesc(15, 15));
    }
}
