package com.openggf.tests.graphics;

import org.junit.jupiter.api.Test;
import com.openggf.graphics.PatternAtlas;
import com.openggf.level.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PatternAtlasFallbackTest {

    @Test
    public void allocatesSecondAtlasWhenCapacityExceeded() {
        PatternAtlas atlas = new PatternAtlas(8, 8); // 1 slot per atlas
        Pattern patternA = new Pattern();
        Pattern patternB = new Pattern();
        Pattern patternC = new Pattern();

        // Use headless caching since we don't have GL context in tests
        PatternAtlas.Entry first = atlas.cachePatternHeadless(patternA, 0);
        assertNotNull(first);
        assertEquals(0, first.atlasIndex());

        PatternAtlas.Entry second = atlas.cachePatternHeadless(patternB, 1);
        assertNotNull(second);
        assertEquals(1, second.atlasIndex());
        assertEquals(2, atlas.getAtlasCount());

        PatternAtlas.Entry third = atlas.cachePatternHeadless(patternC, 2);
        assertNull(third);
    }
}


