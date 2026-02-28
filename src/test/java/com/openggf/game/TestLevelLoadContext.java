package com.openggf.game;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestLevelLoadContext {

    @Test
    public void contextStartsEmpty() {
        var ctx = new LevelLoadContext();
        assertNull(ctx.getRom());
        assertEquals(-1, ctx.getLevelIndex());
        assertEquals(-1, ctx.getZone());
        assertEquals(-1, ctx.getAct());
        assertNull(ctx.getLevel());
        assertNull(ctx.getGameModule());
    }

    @Test
    public void contextAccumulatesState() {
        var ctx = new LevelLoadContext();
        ctx.setLevelIndex(5);
        assertEquals(5, ctx.getLevelIndex());
        ctx.setZone(3);
        assertEquals(3, ctx.getZone());
        ctx.setAct(1);
        assertEquals(1, ctx.getAct());
    }
}
