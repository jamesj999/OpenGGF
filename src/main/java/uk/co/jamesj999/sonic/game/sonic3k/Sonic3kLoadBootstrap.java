package uk.co.jamesj999.sonic.game.sonic3k;

/**
 * Represents level-load bootstrap behavior used by the S3K loader/event pipeline.
 *
 * <p>This exists to support ROM-structured transitions where the level starts with one
 * resource profile and then pivots to another profile after scripted events.
 */
public record Sonic3kLoadBootstrap(Mode mode) {
    public static final Sonic3kLoadBootstrap NONE = new Sonic3kLoadBootstrap(Mode.NONE);

    public enum Mode {
        NONE,
        AIZ1_GAMEPLAY_AFTER_INTRO
    }

    public boolean isAiz1GameplayAfterIntro() {
        return mode == Mode.AIZ1_GAMEPLAY_AFTER_INTRO;
    }
}
