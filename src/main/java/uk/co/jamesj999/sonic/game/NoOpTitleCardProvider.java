package uk.co.jamesj999.sonic.game;

/**
 * No-op implementation of {@link TitleCardProvider} for games without title cards.
 * Used as the default implementation to avoid null checks.
 * Reports as immediately complete so the player gets control right away.
 */
public final class NoOpTitleCardProvider implements TitleCardProvider {
    public static final NoOpTitleCardProvider INSTANCE = new NoOpTitleCardProvider();

    private int currentZone;
    private int currentAct;

    private NoOpTitleCardProvider() {}

    @Override
    public void initialize(int zoneIndex, int actIndex) {
        this.currentZone = zoneIndex;
        this.currentAct = actIndex;
    }

    @Override
    public void update() {
        // No-op
    }

    @Override
    public boolean shouldReleaseControl() {
        return true;
    }

    @Override
    public boolean isOverlayActive() {
        return false;
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void draw() {
        // No-op
    }

    @Override
    public void reset() {
        // No-op
    }

    @Override
    public int getCurrentZone() {
        return currentZone;
    }

    @Override
    public int getCurrentAct() {
        return currentAct;
    }
}
