package com.openggf.level.objects;

public record TouchResponseResult(
        int sizeIndex,
        int widthRadius,
        int heightRadius,
        TouchCategory category,
        int shieldReactionFlags) {

    public TouchResponseResult(int sizeIndex, int widthRadius, int heightRadius, TouchCategory category) {
        this(sizeIndex, widthRadius, heightRadius, category, 0);
    }
}
