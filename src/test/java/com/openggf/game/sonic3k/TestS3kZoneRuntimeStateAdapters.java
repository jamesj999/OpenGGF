package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.runtime.AizZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.HczZoneRuntimeState;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.events.Sonic3kHCZEvents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kZoneRuntimeStateAdapters {

    @Test
    void aizAdapterMirrorsNamedStateFromExistingEvents() {
        Sonic3kAIZEvents events = new Sonic3kAIZEvents(Sonic3kLoadBootstrap.NORMAL);
        events.init(0);
        events.setBossFlag(true);
        events.setEventsFg5(true);

        AizZoneRuntimeState state = new AizZoneRuntimeState(0, events);

        assertEquals("s3k", state.gameId());
        assertEquals(0, state.zoneIndex());
        assertEquals(0, state.actIndex());
        assertTrue(state.isBossFlagActive());
        assertTrue(state.isActTransitionFlagActive());
        assertFalse(state.isPostFireHazeActive());
    }

    @Test
    void hczAdapterMirrorsTransitionFlagAndRoutine() {
        Sonic3kHCZEvents events = new Sonic3kHCZEvents();
        events.init(0);
        events.setEventsFg5(true);
        events.setDynamicResizeRoutine(8);

        HczZoneRuntimeState state = new HczZoneRuntimeState(0, events);

        assertEquals("s3k", state.gameId());
        assertEquals(1, state.zoneIndex());
        assertEquals(0, state.actIndex());
        assertTrue(state.isActTransitionFlagActive());
        assertEquals(8, state.getDynamicResizeRoutine());
    }
}
