package com.openggf.game.sonic3k.features;

import com.openggf.game.render.SpecialRenderEffectRegistry;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.game.sonic3k.Sonic3kZoneFeatureProvider;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSpecialRenderEffectRegistration {

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void tearDown() {
        com.openggf.game.RuntimeManager.destroyCurrent();
    }

    @Test
    void aizAct1RegistersOnlyFireCurtainEffect() {
        Sonic3kZoneFeatureProvider provider = new Sonic3kZoneFeatureProvider();
        SpecialRenderEffectRegistry registry = new SpecialRenderEffectRegistry();

        provider.registerSpecialRenderEffects(registry, Sonic3kZoneIds.ZONE_AIZ, 0);

        assertEquals(0, registry.size(SpecialRenderEffectStage.AFTER_BACKGROUND));
        assertEquals(0, registry.size(SpecialRenderEffectStage.AFTER_FOREGROUND));
        assertEquals(1, registry.size(SpecialRenderEffectStage.AFTER_SPRITES));
    }

    @Test
    void aizAct2RegistersBattleshipAndFireCurtainEffects() {
        Sonic3kZoneFeatureProvider provider = new Sonic3kZoneFeatureProvider();
        SpecialRenderEffectRegistry registry = new SpecialRenderEffectRegistry();

        provider.registerSpecialRenderEffects(registry, Sonic3kZoneIds.ZONE_AIZ, 1);

        assertEquals(1, registry.size(SpecialRenderEffectStage.AFTER_BACKGROUND));
        assertEquals(0, registry.size(SpecialRenderEffectStage.AFTER_FOREGROUND));
        assertEquals(1, registry.size(SpecialRenderEffectStage.AFTER_SPRITES));
    }

    @Test
    void nonAizZonesDoNotRegisterAizEffects() {
        Sonic3kZoneFeatureProvider provider = new Sonic3kZoneFeatureProvider();
        SpecialRenderEffectRegistry registry = new SpecialRenderEffectRegistry();

        provider.registerSpecialRenderEffects(registry, Sonic3kZoneIds.ZONE_HCZ, 0);

        assertTrue(registry.isEmpty());
    }
}
