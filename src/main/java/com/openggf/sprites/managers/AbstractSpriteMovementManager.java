package com.openggf.sprites.managers;

import com.openggf.sprites.Sprite;

public abstract class AbstractSpriteMovementManager<T extends Sprite> implements
		SpriteMovementManager {
	protected final T sprite;

	protected AbstractSpriteMovementManager(T sprite) {
		this.sprite = sprite;
	}
}
