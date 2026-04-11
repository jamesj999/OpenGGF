package com.openggf.level.objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.game.RuntimeManager;
import com.openggf.graphics.GLCommand;
import org.mockito.Mockito;
import com.openggf.game.DamageCause;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.PlayableEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for touch response collision detection logic.
 * Tests the overlap detection algorithm and touch response handling.
 */
public class TestTouchResponseManager {

    private ObjectManager objectManager;
    private TouchResponseTable table;
    private AbstractPlayableSprite player;

    @BeforeEach
    public void setUp() {
        RuntimeManager.createGameplay();
        // Use Mockito to mock TouchResponseTable since its constructor reads from ROM
        table = Mockito.mock(TouchResponseTable.class);
        objectManager = new ObjectManager(List.of(), new ObjectRegistry() {
            @Override
            public ObjectInstance create(ObjectSpawn spawn) {
                return null;
            }

            @Override
            public void reportCoverage(List<ObjectSpawn> spawns) {
            }

            @Override
            public String getPrimaryName(int objectId) {
                return "Test";
            }
        }, 0, null, table);
        objectManager.resetTouchResponses();

        // Create a mock player using Mockito
        player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) 160);
        when(player.getCentreY()).thenReturn((short) 112);
        when(player.getYRadius()).thenReturn((short) 20);
        when(player.getCrouching()).thenReturn(false);
        when(player.getRolling()).thenReturn(false);
        when(player.getSpindash()).thenReturn(false);
        when(player.getInvincibleFrames()).thenReturn(0);
        when(player.getInvulnerable()).thenReturn(false);
        when(player.getDead()).thenReturn(false);
        when(player.getRingCount()).thenReturn(0);
    }

    @AfterEach
    public void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    // ==================== Overlap Detection Tests ====================

    private void setupTableSize(int sizeIndex, int width, int height) {
        when(table.getWidthRadius(sizeIndex)).thenReturn(width);
        when(table.getHeightRadius(sizeIndex)).thenReturn(height);
    }

    @Test
    public void testNoOverlapWhenObjectFarRight() {
        // Object far to the right of player
        MockTouchObject obj = new MockTouchObject(500, 112, 0x08); // Size index 8 = 16x16
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(obj);

        objectManager.update(0, player, List.of(), 1);

        assertFalse(obj.wasTouched, "Should not overlap when object is far to the right");
    }

    @Test
    public void testNoOverlapWhenObjectFarLeft() {
        // Object far to the left of player
        MockTouchObject obj = new MockTouchObject(10, 112, 0x08);
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(obj);

        objectManager.update(0, player, List.of(), 1);

        assertFalse(obj.wasTouched, "Should not overlap when object is far to the left");
    }

    @Test
    public void testNoOverlapWhenObjectFarAbove() {
        // Object far above player
        MockTouchObject obj = new MockTouchObject(160, 10, 0x08);
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(obj);

        objectManager.update(0, player, List.of(), 1);

        assertFalse(obj.wasTouched, "Should not overlap when object is far above");
    }

    @Test
    public void testNoOverlapWhenObjectFarBelow() {
        // Object far below player
        MockTouchObject obj = new MockTouchObject(160, 300, 0x08);
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(obj);

        objectManager.update(0, player, List.of(), 1);

        assertFalse(obj.wasTouched, "Should not overlap when object is far below");
    }

    @Test
    public void testOverlapWhenObjectAtSamePosition() {
        // Object at same position as player
        MockTouchObject obj = new MockTouchObject(160, 112, 0x08);
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(obj);

        objectManager.update(0, player, List.of(), 1);

        assertTrue(obj.wasTouched, "Should overlap when object is at player position");
    }

    @Test
    public void testOverlapWithLargerObject() {
        // Large object that overlaps player
        MockTouchObject obj = new MockTouchObject(180, 112, 0x10); // Size index 16 = 32x32
        setupTableSize(16, 32, 32);
        objectManager.addDynamicObject(obj);

        objectManager.update(0, player, List.of(), 1);

        assertTrue(obj.wasTouched, "Should overlap with large object near player");
    }

    // ==================== Touch Category Tests ====================

    @Test
    public void testEnemyCategoryDecoding() {
        // Flags 0x00-0x3F = ENEMY category
        MockTouchObject obj = new MockTouchObject(160, 112, 0x08); // 0x08 & 0xC0 = 0x00 = ENEMY
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(obj);

        objectManager.update(0, player, List.of(), 1);

        assertEquals(TouchCategory.ENEMY, obj.lastResult.category(), "Category should be ENEMY for flags 0x00-0x3F");
    }

    @Test
    public void testSpecialCategoryDecoding() {
        // Flags 0x40-0x7F = SPECIAL category
        MockTouchObject obj = new MockTouchObject(160, 112, 0x48); // 0x48 & 0xC0 = 0x40 = SPECIAL
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(obj);

        objectManager.update(0, player, List.of(), 1);

        assertEquals(TouchCategory.SPECIAL, obj.lastResult.category(), "Category should be SPECIAL for flags 0x40-0x7F");
    }

    @Test
    public void testHurtCategoryDecoding() {
        // Flags 0x80-0xBF = HURT category
        MockTouchObject obj = new MockTouchObject(160, 112, 0x88); // 0x88 & 0xC0 = 0x80 = HURT
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(obj);

        objectManager.update(0, player, List.of(), 1);

        assertEquals(TouchCategory.HURT, obj.lastResult.category(), "Category should be HURT for flags 0x80-0xBF");
    }

    @Test
    public void testBossCategoryDecoding() {
        // Flags 0xC0-0xFF = BOSS category
        MockTouchObject obj = new MockTouchObject(160, 112, 0xC8); // 0xC8 & 0xC0 = 0xC0 = BOSS
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(obj);

        objectManager.update(0, player, List.of(), 1);

        assertEquals(TouchCategory.BOSS, obj.lastResult.category(), "Category should be BOSS for flags 0xC0-0xFF");
    }

    // ==================== Player State Tests ====================

    @Test
    public void testNoTouchWhenPlayerIsDead() {
        when(player.getDead()).thenReturn(true);
        MockTouchObject obj = new MockTouchObject(160, 112, 0x08);
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(obj);

        objectManager.update(0, player, List.of(), 1);

        assertFalse(obj.wasTouched, "Should not touch objects when player is dead");
    }

    @Test
    public void testCrouchingReducesHitbox() {
        when(player.getCrouching()).thenReturn(true);
        // When crouching, player hitbox is smaller (20 height, shifted down 12px)
        MockTouchObject obj = new MockTouchObject(160, 70, 0x08); // Above player's head
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(obj);

        objectManager.update(0, player, List.of(), 1);

        // Object should NOT touch when player is crouching and object is above normal standing position
        assertFalse(obj.wasTouched, "Crouching should reduce hitbox height");
    }

    // ==================== Enemy Bounce Tests ====================

    @Test
    public void testEnemyAttackedWhenPlayerRolling() {
        when(player.getRolling()).thenReturn(true); // Attacking state
        when(player.getYSpeed()).thenReturn((short) 500); // Falling
        when(player.getCentreY()).thenReturn((short) 100); // Above enemy

        MockAttackableEnemy enemy = new MockAttackableEnemy(160, 120, 0x08);
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(enemy);

        objectManager.update(0, player, List.of(), 1);

        assertTrue(enemy.wasAttacked, "Enemy should have been attacked when player is rolling");
    }

    @Test
    public void testPlayerHurtWhenNotAttacking() {
        when(player.getRolling()).thenReturn(false);
        when(player.getSpindash()).thenReturn(false);
        when(player.getInvincibleFrames()).thenReturn(0);
        when(player.getRingCount()).thenReturn(5);
        when(player.getInvulnerable()).thenReturn(false);
        when(player.hasShield()).thenReturn(false);

        MockTouchObject enemy = new MockTouchObject(160, 112, 0x08); // ENEMY category
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(enemy);

        objectManager.update(0, player, List.of(), 1);

        // Verify applyHurtOrDeath was called (DamageCause overload)
        verify(player).applyHurtOrDeath(anyInt(),
                any(DamageCause.class), anyBoolean());
    }

    @Test
    public void testNoHurtWhenInvulnerable() {
        when(player.getInvulnerable()).thenReturn(true);

        MockTouchObject hurtObject = new MockTouchObject(160, 112, 0x88); // HURT category
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(hurtObject);

        objectManager.update(0, player, List.of(), 1);

        // Verify applyHurtOrDeath was NOT called (DamageCause overload)
        verify(player, never()).applyHurtOrDeath(anyInt(),
                any(DamageCause.class), anyBoolean());
    }

    // ==================== Overlap Persistence Tests ====================

    @Test
    public void testTouchOnlyTriggersOncePerOverlap() {
        MockTouchObject obj = new MockTouchObject(160, 112, 0x48); // SPECIAL category
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(obj);

        // First update - should trigger touch
        objectManager.update(0, player, List.of(), 1);
        assertTrue(obj.wasTouched, "First update should trigger touch");

        // Reset touch flag
        obj.wasTouched = false;

        // Second update - still overlapping but should NOT trigger again
        objectManager.update(0, player, List.of(), 1);
        assertFalse(obj.wasTouched, "Second update should NOT trigger touch for same overlap");
    }

    @Test
    public void testContinuousTouchCallbacksTriggerEveryFrameWhenRequested() {
        MockContinuousTouchObject obj = new MockContinuousTouchObject(160, 112, 0x48);
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(obj);

        objectManager.update(0, player, List.of(), 1);
        assertTrue(obj.wasTouched, "First update should trigger touch");

        obj.wasTouched = false;
        objectManager.update(0, player, List.of(), 2);
        assertTrue(obj.wasTouched, "Continuous callback object should trigger again while still overlapping");
    }

    @Test
    public void testSkipTouchThisFrameSuppressesOverlap() {
        MockSkipTouchObject obj = new MockSkipTouchObject(160, 112, 0x48);
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(obj);

        objectManager.update(0, player, List.of(), 1);

        assertFalse(obj.wasTouched, "Objects flagged skipTouchThisFrame should not trigger touch callbacks");
    }

    @Test
    public void testTouchTriggersAgainAfterExitAndReenter() {
        MockTouchObject obj = new MockTouchObject(160, 112, 0x48);
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(obj);

        // First update - triggers touch
        objectManager.update(0, player, List.of(), 1);
        obj.wasTouched = false;

        // Move player away
        when(player.getCentreX()).thenReturn((short) 500);
        objectManager.update(0, player, List.of(), 1);

        // Move player back
        when(player.getCentreX()).thenReturn((short) 160);
        objectManager.update(0, player, List.of(), 1);

        assertTrue(obj.wasTouched, "Touch should trigger again after exit and re-enter");
    }

    // ==================== Reset Tests ====================

    @Test
    public void testResetClearsOverlappingSet() {
        MockTouchObject obj = new MockTouchObject(160, 112, 0x48);
        setupTableSize(8, 16, 16);
        objectManager.addDynamicObject(obj);

        objectManager.update(0, player, List.of(), 1);
        obj.wasTouched = false;

        // Reset should clear tracking
        objectManager.resetTouchResponses();

        // Now touch should trigger again
        objectManager.update(0, player, List.of(), 1);
        assertTrue(obj.wasTouched, "Touch should trigger after reset even for same overlap");
    }

    // ==================== Helper Classes ====================

    /**
     * Mock object that tracks touch events.
     */
    private static class MockTouchObject implements ObjectInstance, TouchResponseProvider, TouchResponseListener {
        private final ObjectSpawn spawn;
        private final int collisionFlags;
        boolean wasTouched = false;
        TouchResponseResult lastResult;

        public MockTouchObject(int x, int y, int flags) {
            this.spawn = new ObjectSpawn(x, y, 0, 0, 0, false, 0);
            this.collisionFlags = flags;
        }

        @Override
        public int getCollisionFlags() {
            return collisionFlags;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }

        @Override
        public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
            wasTouched = true;
            lastResult = result;
        }

        @Override
        public ObjectSpawn getSpawn() {
            return spawn;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {}

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {}

        @Override
        public boolean isHighPriority() {
            return false;
        }

        @Override
        public boolean isDestroyed() {
            return false;
        }
    }

    /**
     * Mock attackable enemy for testing attack behavior.
     */
    private static class MockAttackableEnemy extends MockTouchObject implements TouchResponseAttackable {
        boolean wasAttacked = false;

        public MockAttackableEnemy(int x, int y, int flags) {
            super(x, y, flags);
        }

        @Override
        public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
            wasAttacked = true;
        }
    }

    private static class MockContinuousTouchObject extends MockTouchObject {
        public MockContinuousTouchObject(int x, int y, int flags) {
            super(x, y, flags);
        }

        @Override
        public boolean requiresContinuousTouchCallbacks() {
            return true;
        }
    }

    private static class MockSkipTouchObject extends MockTouchObject {
        public MockSkipTouchObject(int x, int y, int flags) {
            super(x, y, flags);
        }

        @Override
        public boolean isSkipTouchThisFrame() {
            return true;
        }
    }
}



