package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCnzCannonInstance {

    @Test
    void solidParamsMatchRomTopSolidCall() {
        CnzCannonInstance cannon = new CnzCannonInstance(spawn());

        SolidObjectParams params = cannon.getSolidParams();

        assertEquals(0x10, params.halfWidth());
        assertEquals(0x29, params.airHalfHeight());
        assertEquals(0x29, params.groundHalfHeight());
    }

    @Test
    void groundedPlayerInsideCannonBodyDoesNotCaptureWithoutStandingContact() {
        CnzCannonInstance cannon = new CnzCannonInstance(spawn());
        cannon.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1E68);
        player.setCentreY((short) 0x0851);
        player.setXSpeed((short) -0x014A);
        player.setGSpeed((short) -0x014A);
        player.setAir(false);

        cannon.update(3897, player);

        assertFalse(player.isObjectControlled());
        assertFalse(player.isControlLocked());
        assertEquals((short) -0x014A, player.getXSpeed());
        assertEquals((short) -0x014A, player.getGSpeed());
        assertEquals(9, cannon.getRenderFrameForTest());
    }

    @Test
    void standingContactCapturesPlayerLikeRomStandingBitPath() {
        CnzCannonInstance cannon = new CnzCannonInstance(spawn());
        cannon.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1E68);
        player.setCentreY((short) 0x082C);
        player.setSubpixelRaw(0xBF00, 0x5200);
        player.setXSpeed((short) -0x014A);
        player.setGSpeed((short) -0x014A);

        cannon.onSolidContact(player, new SolidContact(true, false, false, true, false), 3898);

        assertTrue(player.isObjectControlled());
        assertTrue(player.isControlLocked());
        assertTrue(player.getRolling());
        assertTrue(player.getAir());
        assertEquals(0x1E68, player.getCentreX() & 0xFFFF);
        assertEquals(0x082C, player.getCentreY() & 0xFFFF);
        assertEquals(0xBF00, player.getXSubpixelRaw());
        assertEquals(0x5200, player.getYSubpixelRaw());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
        assertEquals(7, player.getXRadius());
        assertEquals(14, player.getYRadius());
        assertEquals(2, player.getAnimationId());
    }

    @Test
    void capturedPlayerIsPulledDownWithRomGravityBeforeLaunchReady() {
        CnzCannonInstance cannon = new CnzCannonInstance(spawn());
        cannon.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1E78);
        player.setCentreY((short) 0x082C);
        player.setSubpixelRaw(0xBF00, 0x5200);

        cannon.onSolidContact(player, new SolidContact(true, false, false, true, false), 3966);
        cannon.update(3967, player);
        cannon.update(3968, player);

        assertEquals(0x1E68, player.getCentreX() & 0xFFFF);
        assertEquals(0x082C, player.getCentreY() & 0xFFFF);
        assertEquals(0xBF00, player.getXSubpixelRaw());
        assertEquals(0x8A00, player.getYSubpixelRaw());
        assertEquals(0x0070, player.getYSpeed() & 0xFFFF);
        assertTrue(player.isObjectControlled());
    }

    @Test
    void launchSnapsPlayerToRomReleasePosition() {
        CnzCannonInstance cannon = new CnzCannonInstance(spawn());
        cannon.setServices(new TestObjectServices());
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x1E78);
        player.setCentreY((short) 0x082C);
        player.setSubpixelRaw(0xBF00, 0x5200);

        cannon.onSolidContact(player, new SolidContact(true, false, false, true, false), 3966);
        for (int frame = 3967; frame < 4020 && (player.getCentreY() & 0xFFFF) != 0x0869; frame++) {
            cannon.update(frame, player);
        }
        assertTrue(player.isObjectControlled());
        assertEquals(0x0869, player.getCentreY() & 0xFFFF);
        int ySubpixelAtRelease = player.getYSubpixelRaw();

        player.setJumpInputPressed(true);
        cannon.update(4020, player);

        assertFalse(player.isObjectControlled());
        assertFalse(player.isControlLocked());
        assertEquals(0x1E68, player.getCentreX() & 0xFFFF);
        assertEquals(0x0851, player.getCentreY() & 0xFFFF);
        assertEquals(0xBF00, player.getXSubpixelRaw());
        assertEquals(ySubpixelAtRelease, player.getYSubpixelRaw());
        assertEquals(player.getXSpeed(), player.getGSpeed());
        assertTrue(player.getAir());
        assertTrue(player.getRolling());
    }

    private static ObjectSpawn spawn() {
        return new ObjectSpawn(0x1E68, 0x0869, 0x42, 0, 0, false, 0);
    }
}
