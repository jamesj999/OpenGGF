package com.openggf.game.sonic3k.dataselect;

import java.util.List;

record HostEmeraldLayoutProfile(List<Point> positions, int activeEmeraldCount) {
    HostEmeraldLayoutProfile {
        positions = List.copyOf(positions);
    }

    static HostEmeraldLayoutProfile defaultSeven() {
        return new HostEmeraldLayoutProfile(List.of(
                new Point(0, 0),
                new Point(0, 0),
                new Point(0, 0),
                new Point(0, 0),
                new Point(0, 0),
                new Point(0, 0),
                new Point(0, 0)
        ), 7);
    }

    record Point(int x, int y) {
    }
}
