package com.openggf.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the {@link DonorCapabilities} interface compiles and exposes
 * all required methods for cross-game sprite donation.
 */
public class TestDonorCapabilities {

    @Test
    void donorCapabilitiesInterfaceHasRequiredMethods() {
        DonorCapabilities caps = new DonorCapabilities() {
            public java.util.Set<PlayerCharacter> getPlayableCharacters() {
                return java.util.Set.of(PlayerCharacter.SONIC_ALONE);
            }
            public boolean hasSpindash() { return false; }
            public boolean hasSuperTransform() { return false; }
            public boolean hasHyperTransform() { return false; }
            public boolean hasInstaShield() { return false; }
            public boolean hasElementalShields() { return false; }
            public boolean hasSidekick() { return false; }
            public java.util.Map<CanonicalAnimation, CanonicalAnimation> getAnimationFallbacks() {
                return java.util.Map.of();
            }
            public int resolveNativeId(CanonicalAnimation canonical) { return -1; }
            public com.openggf.data.PlayerSpriteArtProvider getPlayerArtProvider(
                    com.openggf.data.RomByteReader reader) {
                return null;
            }
        };

        assertEquals(1, caps.getPlayableCharacters().size());
        assertFalse(caps.hasSpindash());
        assertFalse(caps.hasSuperTransform());
        assertFalse(caps.hasHyperTransform());
        assertFalse(caps.hasInstaShield());
        assertFalse(caps.hasElementalShields());
        assertFalse(caps.hasSidekick());
        assertTrue(caps.getAnimationFallbacks().isEmpty());
        assertEquals(-1, caps.resolveNativeId(CanonicalAnimation.WALK));
        assertNull(caps.getPlayerArtProvider(null));
    }
}
