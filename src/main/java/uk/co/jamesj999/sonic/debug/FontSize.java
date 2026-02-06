package uk.co.jamesj999.sonic.debug;

/**
 * Font sizes for debug text rendering.
 * Different text elements use different sizes for readability:
 * - SMALL (6pt): Sensors, performance panel stats
 * - MEDIUM (7pt): Object labels
 * - LARGE (8pt): Main status panels
 */
public enum FontSize {
    SMALL(6),
    MEDIUM(7),
    LARGE(8);

    private final int points;

    FontSize(int points) {
        this.points = points;
    }

    public int getPoints() {
        return points;
    }
}
