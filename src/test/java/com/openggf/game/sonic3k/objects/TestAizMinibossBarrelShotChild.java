package com.openggf.game.sonic3k.objects;

import org.junit.Before;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.graphics.GLCommand;

import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.PlayableEntity;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestAizMinibossBarrelShotChild {

    private DummyBoss parent;

    @Before
    public void setUp() {
        GameServices.camera().resetState();
        Camera camera = GameServices.camera();
        camera.setX((short) 0);
        camera.setY((short) 0);
        parent = new DummyBoss();
    }

    @Test
    public void simpleModeNeverBecomesHazardousAndSelfDeletes() {
        AizMinibossBarrelShotChild shot = new AizMinibossBarrelShotChild(
                parent, 0, 100, 100, AizMinibossBarrelShotChild.Mode.SIMPLE);
        shot.setServices(new DefaultObjectServices());

        for (int i = 0; i < 250 && !shot.isDestroyed(); i++) {
            shot.update(i, null);
            assertEquals(0, shot.getCollisionFlags());
        }

        assertTrue(shot.isDestroyed());
    }

    @Test
    public void advancedNonCollidingModeNeverSetsCollisionFlags() {
        AizMinibossBarrelShotChild shot = new AizMinibossBarrelShotChild(
                parent, 0, 100, 100, AizMinibossBarrelShotChild.Mode.ADVANCED_NON_COLLIDING);
        shot.setServices(new DefaultObjectServices());

        for (int i = 0; i < 220 && !shot.isDestroyed(); i++) {
            shot.update(i, null);
            assertEquals(0, shot.getCollisionFlags());
        }
    }

    @Test
    public void advancedCollidingModeEventuallyEntersHazardPhase() {
        AizMinibossBarrelShotChild shot = new AizMinibossBarrelShotChild(
                parent, 0, 100, 100, AizMinibossBarrelShotChild.Mode.ADVANCED_COLLIDING);
        shot.setServices(new DefaultObjectServices());

        boolean sawCollision = false;
        for (int i = 0; i < 260 && !shot.isDestroyed(); i++) {
            shot.update(i, null);
            if (shot.getCollisionFlags() != 0) {
                sawCollision = true;
                break;
            }
        }

        assertTrue("Expected colliding shot to expose collision flags during top-drop", sawCollision);
    }

    @Test
    public void flameChildTracksParentOffsetsAndFlip() {
        AizMinibossFlameChild flame = new AizMinibossFlameChild(parent, -0x64, 4, 0);
        flame.setServices(new DefaultObjectServices());

        flame.update(0, null);
        assertEquals(parent.getX() - 0x64, flame.getX());

        parent.getState().renderFlags = 1;
        flame.update(1, null);
        assertEquals(parent.getX() + 0x64, flame.getX());
    }

    private static final class DummyBoss extends AbstractBossInstance {
        private DummyBoss() {
            super(new ObjectSpawn(0x1200, 0x300, 0x91, 0, 0, false, 0), "DummyBoss");
            setServices(new DefaultObjectServices());
            state.x = 0x1200;
            state.y = 0x300;
            state.xFixed = state.x << 16;
            state.yFixed = state.y << 16;
        }

        @Override
        protected void initializeBossState() {
            state.routine = 0;
            state.hitCount = 6;
        }

        @Override
        protected void updateBossLogic(int frameCounter, PlayableEntity player) {
            // No-op test stub.
        }

        @Override
        protected int getInitialHitCount() {
            return 6;
        }

        @Override
        protected void onHitTaken(int remainingHits) {
            // No-op test stub.
        }

        @Override
        protected int getCollisionSizeIndex() {
            return 0x0F;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // No-op test stub.
        }

        @Override
        public void onPlayerAttack(PlayableEntity player, TouchResponseResult result) {
            // No-op test stub.
        }

        @Override
        protected int getBossHitSfxId() {
            return 0;
        }

        @Override
        protected int getBossExplosionSfxId() {
            return 0;
        }
    }
}
