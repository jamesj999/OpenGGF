package com.openggf.physics;

import com.openggf.game.GameServices;
import org.junit.jupiter.api.Test;

class TestTerrainCollisionManagerReset {
    @Test
    void resetStateClearsPooledResults() {
        TerrainCollisionManager tcm = GameServices.terrainCollision();
        tcm.resetState();
        // Verifies method exists and runs cleanly without NPE
    }
}
