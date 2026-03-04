package com.openggf.level.objects;

public record SolidObjectParams(int halfWidth, int airHalfHeight, int groundHalfHeight, int offsetX, int offsetY) {
    public SolidObjectParams(int halfWidth, int airHalfHeight, int groundHalfHeight) {
        this(halfWidth, airHalfHeight, groundHalfHeight, 0, 0);
    }
}
