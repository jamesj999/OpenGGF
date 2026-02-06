package uk.co.jamesj999.sonic.tests;

import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2HTZBossInstance;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRegistry;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseTable;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for HTZ boss collision and touch-response damage behavior.
 */
public class TestHTZBossTouchResponse {

    private static final int HTZ_BOSS_X = 0x3040;
    private static final int HTZ_BOSS_Y = 0x0580;
    private static final int HTZ_BOSS_SIZE_INDEX = 0x32;

    private TouchResponseTable touchTable;
    private ObjectManager objectManager;
    private AbstractPlayableSprite player;
    private Sonic2HTZBossInstance boss;

    @Before
    public void setUp() throws Exception {
        touchTable = mock(TouchResponseTable.class);
        when(touchTable.getWidthRadius(HTZ_BOSS_SIZE_INDEX)).thenReturn(32);
        when(touchTable.getHeightRadius(HTZ_BOSS_SIZE_INDEX)).thenReturn(32);

        objectManager = new ObjectManager(List.of(), new NoOpObjectRegistry(), 0, null, touchTable);

        LevelManager levelManager = mock(LevelManager.class);
        boss = new Sonic2HTZBossInstance(
                new ObjectSpawn(HTZ_BOSS_X, HTZ_BOSS_Y, Sonic2ObjectIds.HTZ_BOSS, 0, 0, false, 0),
                levelManager
        );

        player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) (HTZ_BOSS_X + 8));
        when(player.getCentreY()).thenReturn((short) HTZ_BOSS_Y);
        when(player.getYRadius()).thenReturn((short) 20);
        when(player.getCrouching()).thenReturn(false);
        when(player.getRolling()).thenReturn(true);
        when(player.getSpindash()).thenReturn(false);
        when(player.getInvincibleFrames()).thenReturn(0);
        when(player.getInvulnerable()).thenReturn(false);
        when(player.getDead()).thenReturn(false);
        when(player.getRingCount()).thenReturn(0);
        when(player.getXSpeed()).thenReturn((short) 0x200);
        when(player.getYSpeed()).thenReturn((short) -0x300);
    }

    @Test
    public void bossCollisionUsesRomCollisionSizeIndex() {
        int flags = boss.getCollisionFlags();
        assertEquals(0xC0 | HTZ_BOSS_SIZE_INDEX, flags);
        assertEquals(HTZ_BOSS_SIZE_INDEX, flags & 0x3F);
    }

    @Test
    public void attackingTouchResponseDamagesBossAndAppliesBossBounce() {
        assertEquals(8, boss.getState().hitCount);
        objectManager.addDynamicObject(boss);

        objectManager.update(0, player, null, 1);

        assertEquals(7, boss.getState().hitCount);
        assertTrue(boss.getState().invulnerable);
        verify(player).setAir(true);
        verify(player).setXSpeed(eq((short) -0x200));
        verify(player).setYSpeed(eq((short) 0x300));
    }

    @Test
    public void bossCanBeDamagedAfterOverlapStartsWhenAttackBeginsLater() {
        AtomicBoolean rolling = new AtomicBoolean(false);
        AbstractPlayableSprite dynamicPlayer = mock(AbstractPlayableSprite.class);
        when(dynamicPlayer.getCentreX()).thenReturn((short) (HTZ_BOSS_X + 8));
        when(dynamicPlayer.getCentreY()).thenReturn((short) HTZ_BOSS_Y);
        when(dynamicPlayer.getYRadius()).thenReturn((short) 20);
        when(dynamicPlayer.getCrouching()).thenReturn(false);
        when(dynamicPlayer.getRolling()).thenAnswer(invocation -> rolling.get());
        when(dynamicPlayer.getSpindash()).thenReturn(false);
        when(dynamicPlayer.getInvincibleFrames()).thenReturn(0);
        when(dynamicPlayer.getInvulnerable()).thenReturn(false);
        when(dynamicPlayer.getDead()).thenReturn(false);
        when(dynamicPlayer.getRingCount()).thenReturn(0);
        when(dynamicPlayer.getXSpeed()).thenReturn((short) 0x200);
        when(dynamicPlayer.getYSpeed()).thenReturn((short) -0x300);

        objectManager.addDynamicObject(boss);

        // First frame: overlap begins while not attacking.
        objectManager.update(0, dynamicPlayer, null, 1);
        assertEquals(8, boss.getState().hitCount);

        // Second frame: still overlapping, now attacking.
        rolling.set(true);
        objectManager.update(0, dynamicPlayer, null, 2);
        assertEquals(7, boss.getState().hitCount);
    }

    private static final class NoOpObjectRegistry implements ObjectRegistry {
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
    }
}
