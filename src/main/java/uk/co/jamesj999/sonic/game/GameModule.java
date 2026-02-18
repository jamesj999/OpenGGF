package uk.co.jamesj999.sonic.game;

import uk.co.jamesj999.sonic.audio.GameAudioProfile;
import uk.co.jamesj999.sonic.data.Game;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.level.objects.ObjectRegistry;
import uk.co.jamesj999.sonic.level.objects.PlaneSwitcherConfig;
import uk.co.jamesj999.sonic.level.objects.TouchResponseTable;

public interface GameModule {
    String getIdentifier();

    Game createGame(Rom rom);

    ObjectRegistry createObjectRegistry();

    GameAudioProfile getAudioProfile();

    TouchResponseTable createTouchResponseTable(RomByteReader romReader);

    int getPlaneSwitcherObjectId();

    /**
     * Returns the object ID used for checkpoints/lampposts in this game.
     * Used by debug teleportation and respawn logic.
     *
     * @return the checkpoint object ID, or 0 if checkpoints are not implemented
     */
    default int getCheckpointObjectId() {
        return 0;
    }

    PlaneSwitcherConfig getPlaneSwitcherConfig();

    /**
     * Returns the level event provider for this game.
     * Level events handle dynamic camera boundary changes, boss arena setup,
     * and other zone-specific runtime behaviors.
     *
     * @return the level event provider, or null if the game has no dynamic level events
     */
    LevelEventProvider getLevelEventProvider();

    /**
     * Creates a new respawn state instance for tracking checkpoint data.
     * Called when loading a new level to manage death/respawn behavior.
     *
     * @return a new RespawnState instance
     */
    RespawnState createRespawnState();

    /**
     * Creates a new level state instance for tracking transient level data.
     * Called when loading a new level to manage rings, time, etc.
     *
     * @return a new LevelState instance
     */
    LevelState createLevelState();

    /**
     * Returns the title card provider for this game.
     * Title cards display zone/act information when entering levels.
     *
     * @return the title card provider
     */
    default TitleCardProvider getTitleCardProvider() {
        return NoOpTitleCardProvider.INSTANCE;
    }

    /**
     * Returns the zone registry for this game.
     * The zone registry provides metadata about zones, acts, and levels.
     *
     * @return the zone registry
     */
    ZoneRegistry getZoneRegistry();

    /**
     * Returns the special stage provider for this game.
     * Special stages award Chaos Emeralds when completed.
     *
     * @return the special stage provider
     */
    default SpecialStageProvider getSpecialStageProvider() {
        return NoOpSpecialStageProvider.INSTANCE;
    }

    /**
     * Returns the number of special stages used for stage index cycling.
     * Sonic 1 uses 6, Sonic 2 uses 7.
     *
     * @return special stage cycle count
     */
    default int getSpecialStageCycleCount() {
        return 7;
    }

    /**
     * Returns the number of Chaos Emeralds required for "all emeralds".
     * Sonic 1 uses 6, Sonic 2/Sonic 3&K use 7.
     *
     * @return chaos emerald target count
     */
    default int getChaosEmeraldCount() {
        return 7;
    }

    /**
     * Returns the bonus stage provider for this game.
     * Bonus stages are accessed via checkpoints and award rings, shields, etc.
     *
     * @return the bonus stage provider
     */
    default BonusStageProvider getBonusStageProvider() {
        return NoOpBonusStageProvider.INSTANCE;
    }

    /**
     * Returns the scroll handler provider for this game.
     * Provides zone-specific parallax scroll handlers.
     *
     * @return the scroll handler provider, or null if using default scrolling
     */
    ScrollHandlerProvider getScrollHandlerProvider();

    /**
     * Returns the zone feature provider for this game.
     * Provides zone-specific mechanics like bumpers, water, etc.
     *
     * @return the zone feature provider, or null if no zone features
     */
    ZoneFeatureProvider getZoneFeatureProvider();

    /**
     * Returns the ROM offset provider for this game.
     * Provides type-safe access to game-specific ROM addresses.
     *
     * @return the ROM offset provider
     */
    RomOffsetProvider getRomOffsetProvider();

    /**
     * Returns the debug mode provider for this game.
     * Provides game-specific debug modes and controls.
     *
     * @return the debug mode provider, or null if no game-specific debug modes
     */
    DebugModeProvider getDebugModeProvider();

    /**
     * Returns the debug overlay provider for this game.
     * Provides game-specific debug overlay content.
     *
     * @return the debug overlay provider, or null if using default overlays
     */
    DebugOverlayProvider getDebugOverlayProvider();

    /**
     * Returns the zone art provider for this game.
     * Provides zone-specific art configurations for objects.
     *
     * @return the zone art provider, or null if no zone-specific art
     */
    ZoneArtProvider getZoneArtProvider();

    /**
     * Returns the object art provider for this game.
     * Provides key-based access to object sprites, animations, and related data.
     * This abstracts away game-specific art loading to support multiple games.
     *
     * @return the object art provider, or null if this game has no object art
     */
    ObjectArtProvider getObjectArtProvider();

    /**
     * Returns the title screen provider for this game.
     * Provides the game-specific title screen with ROM-accurate
     * art, palettes, and scrolling.
     *
     * @return the title screen provider, or null if not implemented
     */
    default TitleScreenProvider getTitleScreenProvider() {
        return NoOpTitleScreenProvider.INSTANCE;
    }

    /**
     * Returns the level select provider for this game.
     * Provides the game-specific level select screen with ROM-accurate
     * menu layout, text, and navigation.
     *
     * @return the level select provider, or null if not implemented
     */
    default LevelSelectProvider getLevelSelectProvider() {
        return NoOpLevelSelectProvider.INSTANCE;
    }

    /**
     * Returns the physics provider for this game.
     * Provides per-character physics profiles, modifier rules (water/speed shoes),
     * and feature flags (spindash availability).
     *
     * @return the physics provider (defaults to Sonic 2 provider for backward compatibility)
     */
    default PhysicsProvider getPhysicsProvider() {
        return new uk.co.jamesj999.sonic.game.sonic2.Sonic2PhysicsProvider();
    }

    /**
     * Creates a Super Sonic state controller for the given player sprite.
     * Returns null if this game does not support Super Sonic.
     *
     * @param player the player sprite
     * @return a new SuperStateController, or null
     */
    default uk.co.jamesj999.sonic.sprites.playable.SuperStateController createSuperStateController(
            uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite player) {
        return null;
    }

    /**
     * Called when a level is loaded to reset any game-specific object state.
     * Use this to clear static state in object classes that persists across
     * object load/unload cycles (e.g., sibling spawn tracking, timing sync).
     * <p>
     * Default implementation does nothing.
     */
    default void onLevelLoad() {
        // Default no-op
    }

    /**
     * Applies game-specific plane switching logic for the given player sprite.
     * Called each frame from LevelManager.applyPlaneSwitchers(), after any
     * object-based plane switching (Sonic 2 style).
     *
     * <p>Sonic 1 uses this for loop-based plane switching (Sonic_Loops).
     * Default implementation does nothing (Sonic 2/3K use object-based switching).
     *
     * @param player the player sprite to apply plane switching to
     */
    default void applyPlaneSwitching(uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite player) {
        // Default no-op
    }

    /**
     * Returns whether the given zone/act/level combination represents a
     * "remapped zone" scenario where the physical level data comes from a
     * different zone than the logical zone.
     * <p>
     * Sonic 1 SBZ act 3 is loaded from LZ zone data, so feature systems
     * (water, palettes) need to be told the logical zone (SBZ) rather than
     * the physical level zone (LZ).
     *
     * @param logicalZone the zone the game considers the player to be in
     * @param act the act index
     * @param levelZoneIndex the zone index stored in the level data
     * @return effective zone index for feature lookups, or -1 if no remapping
     */
    default int getRemappedFeatureZone(int logicalZone, int act, int levelZoneIndex) {
        return -1;
    }

    /**
     * Returns the effective act for the remapped feature zone, or -1 if no remapping.
     *
     * @param logicalZone the zone the game considers the player to be in
     * @param act the act index
     * @param levelZoneIndex the zone index stored in the level data
     * @return effective act for feature lookups, or -1 if no remapping
     */
    default int getRemappedFeatureAct(int logicalZone, int act, int levelZoneIndex) {
        return -1;
    }

    /**
     * Returns whether this game supports separate Tails tail art (Obj05 with
     * independent art/mapping/DPLC). S3K uses a completely separate set;
     * S2 reuses the main Tails art at a different VRAM base.
     *
     * @return true if Tails tail art is loaded from a separate source
     */
    default boolean hasSeparateTailsTailArt() {
        return false;
    }

    /**
     * Returns whether this game uses Sonic 2-style inline parallax scroll handlers.
     * Only Sonic 2 loads zone-specific ParallaxTables-based handlers directly.
     * Other games use the ScrollHandlerProvider path exclusively.
     *
     * @return true if inline parallax handlers should be loaded
     */
    default boolean hasInlineParallaxHandlers() {
        return false;
    }

    /**
     * Returns whether invincibility stars use a trail-based animation pattern
     * (following behind the player) rather than orbital animation.
     *
     * @return true if invincibility stars use trail mode
     */
    default boolean hasTrailInvincibilityStars() {
        return false;
    }
}
