package com.openggf.game;

import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.sprites.art.SpriteArtSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class TestCrossGameFeatureProviderRefactor {

    @BeforeEach
    void setUp() {
        // Clear lingering session/runtime state from prior tests in the same fork
        // so resolveHostGameId() falls back to the GameModuleRegistry bootstrap
        // default that this fixture configures via setCurrent().
        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
    }

    @AfterEach
    void cleanup() {
        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();
        CrossGameFeatureProvider.getInstance().resetState();
        GameModuleRegistry.reset();
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
    }

    @Test
    void sameGameDonationIsDisabled() {
        GameModuleRegistry.setCurrent(new com.openggf.game.sonic2.Sonic2GameModule());
        try {
            CrossGameFeatureProvider.getInstance().initialize("s2");
        } catch (Exception e) {
            // ROM not available, but guard should fire before ROM access
        }
        assertFalse(CrossGameFeatureProvider.isActive(),
                "Same-game donation should be disabled");
    }

    @Test
    void hybridFeatureSetReflectsDonorCapabilities() {
        DonorCapabilities s1Caps = new com.openggf.game.sonic1.Sonic1GameModule()
                .getDonorCapabilities();
        assertFalse(s1Caps.hasSpindash());
        assertFalse(s1Caps.hasSuperTransform());
        assertFalse(s1Caps.hasInstaShield());
    }

    @Test
    void loadPlayerSpriteArt_refreshesDonorPaletteForRequestedCharacter() throws Exception {
        Palette sonicPalette = paletteWithBlueMarker();
        Palette knucklesPalette = paletteWithRedMarker();
        CrossGameFeatureProvider provider = spy(new CrossGameFeatureProvider(null, null));
        doReturn(sonicPalette).when(provider).loadCharacterPalette(null);
        doReturn(sonicPalette).when(provider).loadCharacterPalette("sonic");
        doReturn(knucklesPalette).when(provider).loadCharacterPalette("knuckles");
        RenderContext context = RenderContext.getOrCreateDonor(GameId.S3K);
        context.setPalette(0, sonicPalette);
        setField(provider, "donorRenderContext", context);
        setField(provider, "donorGameId", GameId.S3K);
        setField(provider, "donorCapabilities", new StubDonorCapabilities());
        setField(provider, "donorReader", new com.openggf.data.RomByteReader(new byte[0]));

        provider.loadPlayerSpriteArt("knuckles");

        assertSame(knucklesPalette, context.getPalette(0));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = CrossGameFeatureProvider.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class StubDonorCapabilities implements DonorCapabilities {
        private static final PlayerSpriteArtProvider PROVIDER = characterCode -> new SpriteArtSet(
                new Pattern[0], List.of(), List.of(), 0, 0, 0, 1, null, null);

        @Override public java.util.Set<PlayerCharacter> getPlayableCharacters() { return java.util.Set.of(PlayerCharacter.SONIC_ALONE, PlayerCharacter.KNUCKLES); }
        @Override public boolean hasSpindash() { return false; }
        @Override public boolean hasSuperTransform() { return false; }
        @Override public boolean hasHyperTransform() { return false; }
        @Override public boolean hasInstaShield() { return false; }
        @Override public boolean hasElementalShields() { return false; }
        @Override public boolean hasSidekick() { return false; }
        @Override public java.util.Map<CanonicalAnimation, CanonicalAnimation> getAnimationFallbacks() { return java.util.Map.of(); }
        @Override public int resolveNativeId(CanonicalAnimation canonical) { return -1; }
        @Override public PlayerSpriteArtProvider getPlayerArtProvider(com.openggf.data.RomByteReader reader) { return PROVIDER; }
    }

    private static Palette paletteWithBlueMarker() {
        Palette palette = new Palette();
        palette.setColor(1, new Palette.Color((byte) 0x22, (byte) 0x44, (byte) 0xEE));
        return palette;
    }

    private static Palette paletteWithRedMarker() {
        Palette palette = new Palette();
        palette.setColor(1, new Palette.Color((byte) 0xEE, (byte) 0x22, (byte) 0x22));
        return palette;
    }
}


