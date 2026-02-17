package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.game.sonic2.objects.ShieldObjectInstance;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

/**
 * Bubble Shield visual object for S3K.
 * <p>
 * TODO: Load bubble shield art (ArtNem_BubbleShield), animate with
 * dedicated animation script, implement bounce ability on jump button
 * press, and underwater breathing protection.
 * Currently uses the basic shield rendering as a placeholder.
 */
public class BubbleShieldObjectInstance extends ShieldObjectInstance {

    public BubbleShieldObjectInstance(AbstractPlayableSprite player) {
        super(player);
    }
}
