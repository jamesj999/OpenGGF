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

    @Test
    public void standaloneSharedArtSheetSlicesPatternsWhenMappingsStartInsideSourceArt() throws Exception {
        Pattern[] patterns = new Pattern[16];
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = new Pattern();
        }
        List<SpriteMappingFrame> frames = List.of(
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(0, 0, 1, 1, 0x08, false, false, 0),
                        new SpriteMappingPiece(8, 0, 1, 1, 0x0A, false, false, 0)))
        );

        Method buildSheet = Sonic3kObjectArtProvider.class.getDeclaredMethod(
                "buildSheetFromPatterns", Pattern[].class, List.class, int.class);
        buildSheet.setAccessible(true);

        ObjectSpriteSheet sheet = (ObjectSpriteSheet) buildSheet.invoke(null, patterns, frames, 0);

        assertSame("Mappings that start at source tile $08 should render from pattern $08, not pattern 0",
                patterns[8], sheet.getPatterns()[0]);
        assertEquals(0, sheet.getFrame(0).pieces().get(0).tileIndex());
        assertEquals(2, sheet.getFrame(0).pieces().get(1).tileIndex());
    }

    @Test
    public void aiz2SmallRobotnikCraftUsesSourceTile86FromBombershipArt() throws Exception {
        Pattern[] patterns = new Pattern[176];
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = new Pattern();
        }
        List<SpriteMappingFrame> frames = List.of(
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-16, -28, 4, 3, 0x86, false, false, 1),
                        new SpriteMappingPiece(-24, -12, 1, 1, 0x92, false, false, 1),
                        new SpriteMappingPiece(16, -12, 1, 1, 0x93, false, false, 1),
                        new SpriteMappingPiece(-32, -4, 4, 3, 0x94, false, false, 1),
                        new SpriteMappingPiece(0, -4, 4, 3, 0xA0, false, false, 1),
                        new SpriteMappingPiece(-16, 20, 4, 1, 0xAC, false, false, 1)))
        );

        ObjectSpriteSheet sheet = buildStandaloneSheet(patterns, frames);

        assertSame("AIZ2 small Robotnik craft should start at bombership source tile $86",
                patterns[0x86], sheet.getPatterns()[0]);
        assertEquals(0, sheet.getFrame(0).pieces().get(0).tileIndex());
        assertEquals(0x26, sheet.getFrame(0).pieces().get(5).tileIndex());
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

    private static ObjectSpriteSheet buildStandaloneSheet(Pattern[] patterns,
                                                          List<SpriteMappingFrame> frames) throws Exception {
        Method buildSheet = Sonic3kObjectArtProvider.class.getDeclaredMethod(
                "buildSheetFromPatterns", Pattern[].class, List.class, int.class);
        buildSheet.setAccessible(true);
        return (ObjectSpriteSheet) buildSheet.invoke(null, patterns, frames, 0);
    }
}
