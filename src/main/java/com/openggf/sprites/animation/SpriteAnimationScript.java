package com.openggf.sprites.animation;

import java.util.List;

public record SpriteAnimationScript(
        int delay,
        List<Integer> frames,
        SpriteAnimationEndAction endAction,
        int endParam
) {
}
