package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.physics.TrigLookupTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCnzCylinderInstance {

    @Test
    void firstUpdateContinuesAfterRomInitFallthroughMotionPass() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawnWithSubtype(0xF1));
        cylinder.setServices(new TestObjectServices());

        cylinder.update(1, null);

        int expected = 0x1BDF + (TrigLookupTable.sinHex(0x03) >> 3);
        assertEquals(expected, cylinder.getX());
    }

    @Test
    void standingContactCaptureRestoresDefaultRadiiAndClearsRolling() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawn());
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1BC6);
        player.setCentreY((short) 0x07AC);
        player.setRolling(true);
        player.applyRollingRadii(false);
        int defaultYRadius = player.getStandYRadius();

        cylinder.onSolidContact(player, new SolidContact(true, false, false, true, false), 4310);
        cylinder.update(4311, player);

        assertTrue(player.isObjectControlled());
        assertTrue(player.isControlLocked());
        assertFalse(player.getRolling());
        assertFalse(player.getAir());
        assertEquals(9, player.getXRadius());
        assertEquals(defaultYRadius, player.getYRadius());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
    }

    @Test
    void jumpReleaseAppliesRomHoldPositionBeforeLaunch() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawn());
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1BC6);
        player.setCentreY((short) 0x07AC);

        cylinder.onSolidContact(player, new SolidContact(true, false, false, true, false), 4310);
        cylinder.update(4311, player);
        int heldCenterY = player.getCentreY();

        player.setJumpInputPressed(true);
        cylinder.update(4312, player);

        int thresholdByte = ((TrigLookupTable.sinHex(0x80) + 0x100) >> 2) & 0xFF;
        int distanceWord = (25 << 8) | thresholdByte;
        int expectedOffset = (TrigLookupTable.cosHex(0x80) * distanceWord) >> 16;
        assertFalse(player.isObjectControlled());
        assertTrue(player.getAir());
        assertTrue(player.getRolling());
        assertEquals(0x1BDF + expectedOffset, player.getCentreX());
        assertEquals(heldCenterY, player.getCentreY());
        assertEquals(7, player.getXRadius());
        assertEquals(14, player.getYRadius());
        assertTrue(player.getYSpeed() < 0);
    }

    @Test
    void holdPreservesPlayerXSubpixelLikeWordXPosWrites() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawn());
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1BC6);
        player.setCentreY((short) 0x07AC);
        player.setSubpixelRaw(0x1200, 0x9200);

        cylinder.onSolidContact(player, new SolidContact(true, false, false, true, false), 4310);
        cylinder.update(4311, player);
        cylinder.update(4312, player);

        assertEquals(0x1200, player.getXSubpixelRaw());
    }

    @Test
    void holdUsesRomCombinedDistanceWordForXOffset() {
        CnzCylinderInstance cylinder = new CnzCylinderInstance(spawn());
        cylinder.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1BE5);
        player.setCentreY((short) 0x07AC);

        cylinder.onSolidContact(player, new SolidContact(true, false, false, true, false), 4310);
        cylinder.update(4311, player);
        for (int frame = 4312; frame <= 4376; frame++) {
            cylinder.update(frame, player);
        }

        int twistAngle = 0x80;
        int thresholdByte = ((TrigLookupTable.sinHex(twistAngle) + 0x100) >> 2) & 0xFF;
        int distanceWord = (6 << 8) | thresholdByte;
        int expectedOffset = (TrigLookupTable.cosHex(twistAngle) * distanceWord) >> 16;
        assertEquals(0x1BDF + expectedOffset, player.getCentreX());
    }

    private static ObjectSpawn spawn() {
        return new ObjectSpawn(0x1BDF, 0x07E0, 0x47, 0, 0, false, 0);
    }

    private static ObjectSpawn spawnWithSubtype(int subtype) {
        return new ObjectSpawn(0x1BDF, 0x07E0, 0x47, subtype, 0, false, 0);
    }
}
