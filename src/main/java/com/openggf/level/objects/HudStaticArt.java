package com.openggf.level.objects;

import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;

public record HudStaticArt(
        Pattern[] patterns,
        SpriteMappingFrame scoreFrame,
        SpriteMappingFrame debugScoreFrame,
        SpriteMappingFrame timeFrame,
        SpriteMappingFrame timeFlashFrame,
        SpriteMappingFrame ringsFrame,
        SpriteMappingFrame ringsFlashFrame,
        SpriteMappingFrame livesFrame
) {
}
