package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.game.sonic2.objects.ShieldObjectInstance;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

/**
 * Lightning Shield visual object for S3K.
 * <p>
 * TODO: Load lightning shield art (ArtNem_LightningShield), animate with
 * dedicated animation script, implement double-jump ability, and ring
 * attraction effect.
 * Currently uses the basic shield rendering as a placeholder.
 */
public class LightningShieldObjectInstance extends ShieldObjectInstance {

    public LightningShieldObjectInstance(AbstractPlayableSprite player) {
        super(player);
    }
}
