package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tools.Sonic3kObjectProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestSinkingMudObjectInstance {

    @Test
    void registryCreatesSinkingMudInstance() {
        ObjectInstance instance = new Sonic3kObjectRegistry().create(
                new ObjectSpawn(0x1200, 0x0600, Sonic3kObjectIds.SINKING_MUD, 0x04, 0x00, false, 0));

        assertInstanceOf(SinkingMudObjectInstance.class, instance);
    }

    @Test
    void subtypeControlsWidthAndSinglePlayerSurfaceHeight() {
        SinkingMudObjectInstance mud = new SinkingMudObjectInstance(
                new ObjectSpawn(0x100, 0x200, Sonic3kObjectIds.SINKING_MUD, 0x04, 0x00, false, 0));

        SolidObjectParams params = mud.getSolidParams();

        assertEquals(0x20, params.halfWidth());
        assertEquals(0x30, params.airHalfHeight());
        assertEquals(0x30, params.groundHalfHeight());
        assertTrue(mud.isTopSolidOnly());
        assertEquals(4, mud.getPriorityBucket());
    }

    @Test
    void competitionZonesUseHalfHeightSurface() {
        SinkingMudObjectInstance mud = new SinkingMudObjectInstance(
                new ObjectSpawn(0x100, 0x200, Sonic3kObjectIds.SINKING_MUD, 0x04, 0x00, false, 0));
        ObjectServices services = mock(ObjectServices.class);
        when(services.romZoneId()).thenReturn(Sonic3kZoneIds.ZONE_DPZ);
        mud.setServices(services);

        SolidObjectParams params = mud.getSolidParams();

        assertEquals(0x20, params.halfWidth());
        assertEquals(0x18, params.airHalfHeight());
        assertEquals(0x18, params.groundHalfHeight());
    }

    @Test
    void standingFramesEventuallyKillThePlayerAndResetTheirSurface() {
        SinkingMudObjectInstance mud = new SinkingMudObjectInstance(
                new ObjectSpawn(0x100, 0x200, Sonic3kObjectIds.SINKING_MUD, 0x04, 0x00, false, 0));
        mud.setServices(new TestObjectServices());
        PlayableEntity player = mock(PlayableEntity.class);
        when(player.getDead()).thenReturn(false);
        when(player.isOnObject()).thenReturn(false);
        when(player.getYRadius()).thenReturn((short) 19);
        when(player.getHeight()).thenReturn(38);

        for (int frame = 0; frame < 50; frame++) {
            mud.update(frame, player);
            mud.onSolidContact(player, new SolidContact(true, false, false, true, false), frame);
        }

        verify(player).applyCrushDeath();
        assertEquals(0x30, mud.rawSurfaceForTest(player));
    }

    @Test
    void profileMarksSinkingMudImplemented() {
        Sonic3kObjectProfile profile = new Sonic3kObjectProfile();
        assertTrue(profile.getImplementedIds().contains(Sonic3kObjectIds.SINKING_MUD));
    }
}
