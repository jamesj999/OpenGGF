package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.game.sonic2.objects.ShieldObjectInstance;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

/**
 * Fire Shield visual object for S3K.
 * <p>
 * TODO: Load fire shield art (ArtNem_FireShield), animate with dedicated
 * animation script, and implement fire dash ability on jump button press.
 * Currently uses the basic shield rendering as a placeholder.
 */
public class FireShieldObjectInstance extends ShieldObjectInstance {

    public FireShieldObjectInstance(AbstractPlayableSprite player) {
        super(player);
    }
}
