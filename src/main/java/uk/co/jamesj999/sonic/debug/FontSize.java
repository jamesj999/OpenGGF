package uk.co.jamesj999.sonic.debug;

/**
 * Font sizes for debug text rendering.
 * Different text elements use different sizes for readability:
 * - SMALL (7pt): Sensors, performance panel stats
 * - MEDIUM (8pt): Object labels
 * - LARGE (9pt): Main status panels
 */
public enum FontSize {
    SMALL(7),
    MEDIUM(8),
    LARGE(9);

    private final int points;

    FontSize(int points) {
        this.points = points;
    }

    public int getPoints() {
        return points;
    }
}
