package com.openggf.game.sonic3k;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class TestSonic3kObjectArtProvider {

    private Sonic3kObjectArtProvider provider;
    private Method registerSheet;

    @Before
    public void setUp() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        provider = new Sonic3kObjectArtProvider();
        registerSheet = Sonic3kObjectArtProvider.class.getDeclaredMethod(
                "registerSheet", String.class, ObjectSpriteSheet.class);
        registerSheet.setAccessible(true);
    }

    @After
    public void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    public void registerSheetReplacesExistingRendererOrderInsteadOfAppendingDuplicates() throws Exception {
        ObjectSpriteSheet first = buildSheet(2);
        ObjectSpriteSheet second = buildSheet(3);

        registerSheet.invoke(provider, "slot_test", first);
        registerSheet.invoke(provider, "slot_test", second);

        assertEquals(1, provider.getRendererKeys().size());
        assertEquals("slot_test", provider.getRendererKeys().get(0));
        assertSame(second, provider.getSheet("slot_test"));

        int next = provider.ensurePatternsCached(GraphicsManager.getInstance(), 0x20000);
        assertEquals(0x20000 + second.getPatterns().length, next);
    }

    private static ObjectSpriteSheet buildSheet(int patternCount) {
        Pattern[] patterns = new Pattern[patternCount];
        for (int i = 0; i < patternCount; i++) {
            patterns[i] = new Pattern();
        }
        List<SpriteMappingFrame> frames = List.of(
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0)))
        );
        return new ObjectSpriteSheet(patterns, frames, 0, 1);
    }
}
