package com.openggf.sprites;

import com.openggf.physics.Direction;

public record SensorConfiguration(byte xIncrement, byte yIncrement, boolean vertical, Direction direction) {
}
