package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCnzBarberPoleObjectInstance {

    @Test
    void normalRelatchUsesRomUnsignedWordCompareForInnerTrackFlag() {
        CnzBarberPoleObjectInstance pole = new CnzBarberPoleObjectInstance(
                new ObjectSpawn(0x0F70, 0x0810, Sonic3kObjectIds.CNZ_BARBER_POLE, 0, 0, false, 0));
        pole.setServices(new TestObjectServices());

        TestPlayableSprite tails = new TestPlayableSprite();
        tails.setHeight(30);
        tails.applyCustomRadii(9, 15);
        tails.setCentreX((short) 0x0F4B);
        tails.setCentreY((short) 0x07B2);
        tails.setSubpixelRaw(0x8000, 0x8600);
        tails.setOnObject(true);
        tails.setLatchedSolidObjectId(Sonic3kObjectIds.CNZ_BARBER_POLE);
        tails.setAir(false);

        pole.update(0x0638, tails);
        tails.setXSpeed((short) 0x0687);
        tails.setGSpeed((short) 0x093C);
        pole.update(0x0639, tails);

        assertTrue(tails.isOnObject());
        assertFalse(tails.getAir());
        assertEquals(0x0F4E, tails.getCentreX() & 0xFFFF);
        assertEquals(0x07B9, tails.getCentreY() & 0xFFFF);
        assertEquals(0x0E, tails.getFlipAngle() & 0xFF);
    }
}
