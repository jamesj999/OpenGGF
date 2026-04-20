package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;

/**
 * Narrow seam for carry states that need selected solid callbacks while the player is
 * otherwise object-controlled.
 */
public interface ObjectControlledSolidContactController {
    boolean allowsObjectControlledSolidContact(PlayableEntity player, ObjectInstance candidate);
}
