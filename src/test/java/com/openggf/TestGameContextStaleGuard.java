package com.openggf;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestGameContextStaleGuard {

    @Test
    void freshContextAccessorsWork() {
        GameContext ctx = GameContext.forTesting();
        assertNotNull(ctx.camera());
        assertNotNull(ctx.levelManager());
    }

    @Test
    void staleContextThrowsOnAccess() {
        GameContext stale = GameContext.forTesting();
        GameContext.forTesting(); // invalidates 'stale'

        assertThrows(IllegalStateException.class, stale::camera,
                "Accessing stale GameContext should throw");
        assertThrows(IllegalStateException.class, stale::levelManager,
                "Accessing stale GameContext should throw");
    }
}
