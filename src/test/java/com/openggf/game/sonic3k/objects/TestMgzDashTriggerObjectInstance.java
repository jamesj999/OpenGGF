package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TestMgzDashTriggerObjectInstance {

    @Test
    void appendRenderCommandsWhileArmedDrawsMainAndChildSpriteFrames() {
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        MGZDashTriggerObjectInstance trigger = new TestDashTrigger(renderer,
                new ObjectSpawn(0x1200, 0x0340, 0x59, 0, 0, false, 0));
        trigger.setServices(new TestObjectServices());

        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        doReturn(Sonic3kAnimationIds.SPINDASH.id()).when(player).getAnimationId();
        doReturn(true).when(player).getSpindash();
        doReturn((short) 0x1200).when(player).getCentreX();
        doReturn((short) 0x0340).when(player).getCentreY();
        doReturn((short) 9).when(player).getXRadius();
        doReturn((short) 19).when(player).getYRadius();

        trigger.update(1, player);
        trigger.appendRenderCommands(new ArrayList<>());

        ArgumentCaptor<Integer> frameCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(renderer, times(2)).drawFrameIndex(frameCaptor.capture(),
                eq(0x1200), eq(0x0340), eq(false), eq(false));

        assertEquals(2, frameCaptor.getAllValues().size());
        assertTrue(frameCaptor.getAllValues().stream().anyMatch(frame -> frame >= 4),
                "Armed trigger should render its main sprite frame");
        assertTrue(frameCaptor.getAllValues().stream().anyMatch(frame -> frame >= 0 && frame < 4),
                "Armed trigger should also render the child shine frame");
    }

    private static final class TestDashTrigger extends MGZDashTriggerObjectInstance {
        private final PatternSpriteRenderer renderer;

        private TestDashTrigger(PatternSpriteRenderer renderer, ObjectSpawn spawn) {
            super(spawn);
            this.renderer = renderer;
        }

        @Override
        protected PatternSpriteRenderer getRenderer(String artKey) {
            return renderer;
        }
    }
}
