package com.openggf.game.sonic3k.dataselect;

import java.util.List;

record HostEmeraldLayoutProfile(List<Point> positions, int activeEmeraldCount) {
    private static final List<Point> DEFAULT_POSITIONS = List.of(
            new Point(0, 0),
            new Point(0, 0),
            new Point(0, 0),
            new Point(0, 0),
            new Point(0, 0),
            new Point(0, 0),
            new Point(0, 0)
    );

    HostEmeraldLayoutProfile {
        positions = List.copyOf(positions);
    }

    static HostEmeraldLayoutProfile defaultSix() {
        return new HostEmeraldLayoutProfile(DEFAULT_POSITIONS, 6);
    }

    static HostEmeraldLayoutProfile defaultSeven() {
        return new HostEmeraldLayoutProfile(DEFAULT_POSITIONS, 7);
    }

    record Point(int x, int y) {
    }
}
