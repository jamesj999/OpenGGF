package uk.co.jamesj999.sonic.debug;

/**
 * Font sizes for debug text rendering.
 * Different text elements use different sizes for readability:
 * - SMALL (10pt): Sensors, performance panel stats
 * - MEDIUM (11pt): Object labels
 * - LARGE (12pt): Main status panels
 */
public enum FontSize {
    SMALL(10),
    MEDIUM(11),
    LARGE(12);

    private final int points;

    FontSize(int points) {
        this.points = points;
    }

    public int getPoints() {
        return points;
    }
}
