package com.openggf.game.sonic3k.dataselect;

import java.util.List;

record HostEmeraldLayoutProfile(List<Point> positions, int activeEmeraldCount) {
    private static final List<Point> DEFAULT_SEVEN_POSITIONS = List.of(
            new Point(0, 0),
            new Point(0, 0),
            new Point(0, 0),
            new Point(0, 0),
            new Point(0, 0),
            new Point(0, 0),
            new Point(0, 0)
    );
    private static final List<Point> S1_SIX_RING_POSITIONS = List.of(
            new Point(-24, -18),
            new Point(0, -28),
            new Point(24, -18),
            new Point(24, 14),
            new Point(0, 26),
            new Point(-24, 14)
    );

    HostEmeraldLayoutProfile {
        positions = List.copyOf(positions);
        if (activeEmeraldCount < 0 || activeEmeraldCount > positions.size()) {
            throw new IllegalArgumentException("activeEmeraldCount out of range: " + activeEmeraldCount);
        }
    }

    static HostEmeraldLayoutProfile defaultSeven() {
        return new HostEmeraldLayoutProfile(DEFAULT_SEVEN_POSITIONS, 7);
    }

    static HostEmeraldLayoutProfile s1SixRing() {
        return new HostEmeraldLayoutProfile(S1_SIX_RING_POSITIONS, 6);
    }

    record Point(int x, int y) {
    }
}
