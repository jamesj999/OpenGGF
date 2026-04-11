package com.openggf.game.sonic3k.objects;

import org.junit.jupiter.api.Test;
import com.openggf.level.objects.ObjectSpawn;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestAizIntroPlaneChild {

    @Test
    public void attachedChildStaysAtParentOffset() {
        AizPlaneIntroInstance parent = new AizPlaneIntroInstance(
                new ObjectSpawn(0x60, 0x30, 0, 0, 0, false, 0));
        AizIntroPlaneChild child = new AizIntroPlaneChild(
                new ObjectSpawn(0x60 - 0x22, 0x30 + 0x2C, 0, 0, 0, false, 0),
                parent);

        for (int frame = 0; frame < 64; frame++) {
            child.update(frame, null);
            assertEquals(parent.getX() - 0x22, child.getX());
            assertEquals(parent.getY() + 0x2C, child.getY());
        }
    }
}


