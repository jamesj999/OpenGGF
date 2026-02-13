package uk.co.jamesj999.sonic.sprites.animation;

import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.Map;

/**
 * Wraps a normal SpriteAnimationProfile and remaps animation IDs when
 * the player is in Super Sonic state.
 *
 * <p>The Super Sonic animation set shares some scripts with normal Sonic
 * (roll, spindash) but has unique ones for walk, run, stand, push, etc.
 * This wrapper uses a lookup table to remap only the IDs that differ.
 */
public class SuperSonicAnimationProfile implements SpriteAnimationProfile {
    private final SpriteAnimationProfile normalProfile;
    private final Map<Integer, Integer> superRemapTable;

    /**
     * @param normalProfile the underlying profile for state-based animation resolution
     * @param superRemapTable maps normal animation IDs to Super Sonic animation IDs.
     *        IDs not in the map are passed through unchanged (shared scripts like roll).
     */
    public SuperSonicAnimationProfile(SpriteAnimationProfile normalProfile,
                                       Map<Integer, Integer> superRemapTable) {
        this.normalProfile = normalProfile;
        this.superRemapTable = superRemapTable;
    }

    @Override
    public Integer resolveAnimationId(AbstractPlayableSprite sprite, int frameCounter, int scriptCount) {
        Integer baseId = normalProfile.resolveAnimationId(sprite, frameCounter, scriptCount);
        if (baseId == null) return null;

        if (sprite.isSuperSonic() && superRemapTable != null) {
            return superRemapTable.getOrDefault(baseId, baseId);
        }
        return baseId;
    }

    @Override
    public int resolveFrame(AbstractPlayableSprite sprite, int frameCounter, int frameCount) {
        return normalProfile.resolveFrame(sprite, frameCounter, frameCount);
    }
}
