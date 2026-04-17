package com.openggf.game.sonic1;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.SessionManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.HudStaticArt;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1LivesHudDonation {

    @BeforeEach
    void setUp() {
        SonicConfigurationService.getInstance().resetToDefaults();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void loadArtForZone_exposesNativeHudStaticArtWithPalette0LivesFrame() throws Exception {
        SessionManager.openGameplaySession(new Sonic1GameModule(),
                SaveSessionContext.noSave("s1", new SelectedTeam("sonic", List.of()), 0, 0));

        Sonic1ObjectArtProvider provider = new Sonic1ObjectArtProvider();
        try (MockedStatic<CrossGameFeatureProvider> donor = mockStatic(CrossGameFeatureProvider.class)) {
            donor.when(CrossGameFeatureProvider::isS3kDonorActive).thenReturn(false);
            provider.loadArtForZone(0);
        }

        HudStaticArt art = provider.getHudStaticArt();

        assertNotNull(art);
        assertEquals(provider.getHudTextPatterns().length + provider.getHudLivesPatterns().length,
                art.patterns().length);
        assertTrue(art.scoreFrame().pieces().stream().allMatch(piece -> piece.paletteIndex() == 0));
        assertTrue(art.debugScoreFrame().pieces().stream().allMatch(piece -> piece.paletteIndex() == 0));
        assertTrue(art.timeFrame().pieces().stream().allMatch(piece -> piece.paletteIndex() == 0));
        assertTrue(art.timeFlashFrame().pieces().stream().allMatch(piece -> piece.paletteIndex() == 0));
        assertTrue(art.ringsFrame().pieces().stream().allMatch(piece -> piece.paletteIndex() == 0));
        assertTrue(art.ringsFlashFrame().pieces().stream().allMatch(piece -> piece.paletteIndex() == 0));
        assertTrue(art.livesFrame().pieces().stream().allMatch(piece -> piece.paletteIndex() == 0));
        assertTrue(art.livesFrame().pieces().stream()
                .anyMatch(piece -> piece.xOffset() == 0 && piece.yOffset() == 0 && piece.tileIndex() >= 0));
    }

    @Test
    void loadArtForZone_rebuildsDonorHudStaticArtThroughProviderPath() throws Exception {
        SessionManager.openGameplaySession(new Sonic1GameModule(),
                SaveSessionContext.noSave("s1", new SelectedTeam("knuckles", List.of()), 0, 0));

        Sonic1ObjectArtProvider provider = spy(new Sonic1ObjectArtProvider());
        Pattern[] donorPatterns = { new Pattern(), new Pattern() };
        doReturn(donorPatterns).when(provider).loadS3kKnucklesLivesPatterns();

        try (MockedStatic<CrossGameFeatureProvider> donor = mockStatic(CrossGameFeatureProvider.class)) {
            donor.when(CrossGameFeatureProvider::isS3kDonorActive).thenReturn(true);
            provider.loadArtForZone(0);
        }

        HudStaticArt art = provider.getHudStaticArt();

        assertSame(donorPatterns, provider.getHudLivesPatterns());
        assertNotNull(art);
        assertEquals(provider.getHudTextPatterns().length + donorPatterns.length, art.patterns().length);
        assertTrue(art.scoreFrame().pieces().stream().allMatch(piece -> piece.paletteIndex() == 0));
        assertTrue(art.debugScoreFrame().pieces().stream().allMatch(piece -> piece.paletteIndex() == 0));
        assertTrue(art.timeFrame().pieces().stream().allMatch(piece -> piece.paletteIndex() == 0));
        assertTrue(art.timeFlashFrame().pieces().stream().allMatch(piece -> piece.paletteIndex() == 0));
        assertTrue(art.ringsFrame().pieces().stream().allMatch(piece -> piece.paletteIndex() == 0));
        assertTrue(art.ringsFlashFrame().pieces().stream().allMatch(piece -> piece.paletteIndex() == 0));
        assertTrue(art.livesFrame().pieces().stream().allMatch(piece -> piece.paletteIndex() == 0));
    }

    @Test
    void remapPaletteIndices_mapsDonorIndicesToHostSlots() throws Exception {
        Sonic1ObjectArtProvider provider = new Sonic1ObjectArtProvider();
        Pattern tile = new Pattern();
        tile.setPixel(0, 0, (byte) 1);
        tile.setPixel(1, 0, (byte) 2);
        tile.setPixel(2, 0, (byte) 3);
        tile.setPixel(3, 0, (byte) 4);
        tile.setPixel(4, 0, (byte) 5);
        tile.setPixel(5, 0, (byte) 10);
        tile.setPixel(6, 0, (byte) 11);
        tile.setPixel(7, 0, (byte) 12);
        tile.setPixel(0, 1, (byte) 13);
        tile.setPixel(1, 1, (byte) 14);
        tile.setPixel(2, 1, (byte) 15);

        Method method = Sonic1ObjectArtProvider.class.getDeclaredMethod("remapPaletteIndices", Pattern[].class);
        method.setAccessible(true);
        method.invoke(provider, (Object) new Pattern[] { tile });

        assertEquals(6, tile.getPixel(0, 0));
        assertEquals(12, tile.getPixel(1, 0));
        assertEquals(13, tile.getPixel(2, 0));
        assertEquals(14, tile.getPixel(3, 0));
        assertEquals(15, tile.getPixel(4, 0));
        assertEquals(10, tile.getPixel(5, 0));
        assertEquals(11, tile.getPixel(6, 0));
        assertEquals(6, tile.getPixel(7, 0));
        assertEquals(7, tile.getPixel(0, 1));
        assertEquals(1, tile.getPixel(1, 1));
        assertEquals(1, tile.getPixel(2, 1));
    }

}
