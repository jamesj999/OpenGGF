package com.openggf.game.sonic3k.objects;

import org.junit.Test;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestAizMinibossRegistry {

    @Test
    public void registryCreatesAizMinibossForId0x91InS3klZoneSet() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();
        ObjectSpawn spawn = new ObjectSpawn(0x11F0, 0x289, Sonic3kObjectIds.AIZ_MINIBOSS, 0, 0, false, 0);

        ObjectInstance instance = registry.create(spawn);

        assertTrue(instance instanceof AizMinibossInstance);
    }

    @Test
    public void primaryNameFor0x91MatchesDisassemblyLabel() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();
        assertEquals("AIZMiniboss", registry.getPrimaryName(Sonic3kObjectIds.AIZ_MINIBOSS));
    }
}

