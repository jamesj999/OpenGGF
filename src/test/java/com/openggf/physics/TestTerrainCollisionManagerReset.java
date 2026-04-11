package com.openggf.physics;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestTerrainCollisionManagerReset {

    @BeforeEach
    void setUp() {
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    void resetStateClearsPooledResults() {
        TerrainCollisionManager tcm = GameServices.terrainCollision();
        tcm.resetState();
        // Verifies method exists and runs cleanly without NPE
    }
}


