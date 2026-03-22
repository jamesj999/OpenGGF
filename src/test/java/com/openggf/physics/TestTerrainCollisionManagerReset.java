package com.openggf.physics;

import org.junit.jupiter.api.Test;

class TestTerrainCollisionManagerReset {
    @Test
    void resetStateClearsPooledResults() {
        TerrainCollisionManager tcm = TerrainCollisionManager.getInstance();
        tcm.resetState();
        // Verifies method exists and runs cleanly without NPE
    }
}
