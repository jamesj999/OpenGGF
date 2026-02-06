package uk.co.jamesj999.sonic.tests.rules;

/**
 * Identifies which Sonic game a test requires.
 * Named {@code SonicGame} to avoid collision with {@link uk.co.jamesj999.sonic.data.Game}.
 */
public enum SonicGame {
    SONIC_1("Sonic 1"),
    SONIC_2("Sonic 2"),
    SONIC_3K("Sonic 3 & Knuckles");

    private final String displayName;

    SonicGame(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
