package uk.co.jamesj999.sonic.debug;

/**
 * Font sizes for debug text rendering.
 * Different text elements use different sizes for readability:
 * - SMALL (8pt): Sensors, performance panel stats
 * - MEDIUM (9pt): Object labels
 * - LARGE (10pt): Main status panels
 */
public enum FontSize {
    SMALL(8),
    MEDIUM(9),
    LARGE(10);

    private final int points;

    FontSize(int points) {
        this.points = points;
    }

    public int getPoints() {
        return points;
    }
}
