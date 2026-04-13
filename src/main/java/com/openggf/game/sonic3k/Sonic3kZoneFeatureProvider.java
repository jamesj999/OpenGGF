package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.features.AizBattleshipRenderFeature;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.features.AizTransitionRenderFeature;
import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotMachinePanelAnimator;
import com.openggf.game.sonic3k.features.HCZWaterSkimHandler;
import com.openggf.game.sonic3k.features.HCZWaterTunnelHandler;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.WaterSystem;
import com.openggf.level.scroll.M68KMath;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Zone feature provider for Sonic 3 &amp; Knuckles.
 * Handles AIZ intro ocean phase detection, title card suppression,
 * and other S3K-specific zone features.
 */
public class Sonic3kZoneFeatureProvider implements ZoneFeatureProvider {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kZoneFeatureProvider.class.getName());

    private final AizBattleshipRenderFeature aizBattleshipRenderFeature = new AizBattleshipRenderFeature();
    private final AizTransitionRenderFeature aizTransitionRenderFeature = new AizTransitionRenderFeature();
    private Sonic3kWaterSurfaceManager waterSurfaceManager;
    private boolean forcedAizForestFrontPriority;
    private S3kSlotMachinePanelAnimator slotMachinePanelAnimator;

    @Override
    public void initZoneFeatures(Rom rom, int zoneIndex, int actIndex, int cameraX) throws IOException {
        aizTransitionRenderFeature.onZoneInit(zoneIndex, actIndex);

        // Initialize water surface manager for HCZ (zone 1)
        // From sonic3k.asm:7777-7787: only HCZ loads Obj_HCZWaveSplash
        // Use a static zone check (not hasWater()) because the WaterSystem
        // is loaded in a later init phase (InitWater runs after InitZoneFeatures).
        if (zoneHasWaterSurface(zoneIndex)) {
            initWaterSurfaceManager(rom, zoneIndex, actIndex);
            // Init water skim handler (Obj_HCZWaterSplash subtype 1)
            // ROM: sonic3k.asm:7786-7787 — spawned alongside Obj_HCZWaveSplash at HCZ init
            HCZWaterSkimHandler.init(rom, actIndex);
        }
        if (zoneIndex == Sonic3kZoneIds.ZONE_SLOT_MACHINE) {
            initSlotMachineRenderer(rom);
        }
    }

    private void initSlotMachineRenderer(Rom rom) {
        if (slotMachinePanelAnimator == null) {
            slotMachinePanelAnimator = new S3kSlotMachinePanelAnimator();
        }
        slotMachinePanelAnimator.init(rom);
    }

    @Override
    public void update(AbstractPlayableSprite player, int cameraX, int zoneIndex) {
        updateAizForestFrontPriority(player, zoneIndex);
    }

    /**
     * Pre-physics update for HCZ water tunnels.
     * ROM: {@code sub_6F4A} runs before {@code ExecuteObjects}, so the tunnel
     * velocity and position overrides are applied before player physics.
     */
    @Override
    public void updatePrePhysics(AbstractPlayableSprite player, int cameraX, int zoneIndex) {
        var levelManager = GameServices.levelOrNull();
        if (zoneIndex == Sonic3kZoneIds.ZONE_HCZ && player != null && !player.getDead()) {
            int act = levelManager != null ? levelManager.getFeatureActId() : 0;
            HCZWaterTunnelHandler.update(act);
            // Water skim runs after tunnels (ROM: Obj_HCZWaterSplash runs in ExecuteObjects
            // which is after sub_6F4A/water tunnels)
            HCZWaterSkimHandler.update();
        }
    }

    private void updateAizForestFrontPriority(AbstractPlayableSprite player, int zoneIndex) {
        if (zoneIndex != Sonic3kZoneIds.ZONE_AIZ || getFeatureActId() != 1 || player == null) {
            return;
        }

        Sonic3kAIZEvents aizEvents = getAizEvents();
        boolean forestFrontPhaseActive = aizEvents != null && aizEvents.isBattleshipForestFrontPhaseActive();
        boolean bossArenaFrontPriority = aizEvents != null && aizEvents.isBossFlag();

        // ROM: During the post-boss cutscene (egg capsule, results, walk-right,
        // bridge collapse) the player's art_tile high-priority bit stays set.
        // Restore_PlayerControl (called at loc_694D4) does NOT clear it.
        // High priority is only lost when the next zone loads.
        boolean postBossCutsceneActive = com.openggf.game.sonic3k.objects
                .Aiz2BossEndSequenceState.isCutsceneOverrideObjectsActive();

        if (forestFrontPhaseActive || bossArenaFrontPriority || postBossCutsceneActive) {
            player.setHighPriority(true);
            player.setPriorityBucket(RenderPriority.MIN);
            forcedAizForestFrontPriority = true;
            return;
        }

        if (forcedAizForestFrontPriority && canReleaseAizForestFrontPriority(player)) {
            player.setHighPriority(false);
            player.setPriorityBucket(RenderPriority.PLAYER_DEFAULT);
            forcedAizForestFrontPriority = false;
        }
    }

    private boolean canReleaseAizForestFrontPriority(AbstractPlayableSprite player) {
        return !player.getDead()
                && !player.isHurt()
                && !player.isDrowningPreDeath()
                && !player.isDrowningDeath();
    }

    private void initWaterSurfaceManager(Rom rom, int zoneIndex, int actIndex) {
        try {
            RomByteReader reader = RomByteReader.fromRom(rom);
            waterSurfaceManager = new Sonic3kWaterSurfaceManager(rom, reader, zoneIndex, actIndex);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize S3K water surface manager", e);
            waterSurfaceManager = null;
        }
    }

    @Override
    public void reset() {
        aizBattleshipRenderFeature.reset();
        aizTransitionRenderFeature.reset();
        waterSurfaceManager = null;
        forcedAizForestFrontPriority = false;
        if (slotMachinePanelAnimator != null) {
            slotMachinePanelAnimator.cleanup();
            slotMachinePanelAnimator = null;
        }
        HCZWaterTunnelHandler.reset();
        HCZWaterSkimHandler.reset();
    }

    @Override
    public boolean hasCollisionFeatures(int zoneIndex) {
        return false;
    }

    @Override
    public boolean hasWater(int zoneIndex) {
        // Static check: HCZ is the only S3K zone with water surface sprites.
        // Must not query WaterSystem here because this method may be called
        // before InitWater has run (e.g. from initZoneFeatures).
        // ROM: CheckLevelForWater (sonic3k.asm:9754) — zone 1 (HCZ) has water.
        return zoneHasWaterSurface(zoneIndex);
    }

    /**
     * Static check for zones that have water surface rendering.
     * Unlike {@link #hasWater(int)}, this never queries runtime state,
     * so it is safe to call during any init phase.
     */
    private static boolean zoneHasWaterSurface(int zoneIndex) {
        return zoneIndex == Sonic3kZoneIds.ZONE_HCZ;
    }

    @Override
    public int getWaterLevel(int zoneIndex, int actIndex) {
        WaterSystem waterSystem = GameServices.water();
        return waterSystem.getWaterLevelY(zoneIndex, actIndex);
    }

    @Override
    public void render(Camera camera, int frameCounter) {
        if (waterSurfaceManager != null && waterSurfaceManager.isInitialized()) {
            waterSurfaceManager.render(camera, frameCounter);
        }
        // Render water skim splash sprites (at water level, following player)
        HCZWaterSkimHandler.render(camera);
        aizTransitionRenderFeature.renderFlameOverlay(camera, frameCounter);
        var levelManager = GameServices.levelOrNull();
        if (levelManager == null || levelManager.getCurrentZone() != Sonic3kZoneIds.ZONE_SLOT_MACHINE) {
            return;
        }
        if (!(GameServices.module().getBonusStageProvider() instanceof Sonic3kBonusStageCoordinator coordinator)) {
            return;
        }
        if (coordinator.activeSlotRuntime() == null) {
            return;
        }
        if (slotMachinePanelAnimator != null && slotMachinePanelAnimator.isInitialized()) {
            slotMachinePanelAnimator.syncPanelPatterns(
                    coordinator.activeSlotRuntime().slotMachineDisplayState());
        }
        coordinator.activeSlotRuntime().renderSlotLayout(camera);
    }

    @Override
    public void renderAfterForeground(Camera camera) {
        var levelManager = GameServices.levelOrNull();
        if (levelManager == null || levelManager.getCurrentZone() != Sonic3kZoneIds.ZONE_SLOT_MACHINE) {
            return;
        }
        if (!(GameServices.module().getBonusStageProvider() instanceof Sonic3kBonusStageCoordinator coordinator)) {
            return;
        }
        if (coordinator.activeSlotRuntime() == null) {
            return;
        }
        coordinator.activeSlotRuntime().renderSlotMachineFaceForeground();
    }

    protected int resolveSlotDisplayOriginX(Camera camera) {
        var parallax = GameServices.parallaxOrNull();
        int[] hScroll = parallax != null ? parallax.getHScroll() : null;
        if (hScroll != null && hScroll.length > 0) {
            return -M68KMath.unpackFG(hScroll[0]);
        }
        return camera != null ? camera.getX() : 0;
    }

    protected int resolveSlotDisplayOriginY(Camera camera) {
        var parallax = GameServices.parallaxOrNull();
        if (parallax != null) {
            return parallax.getVscrollFactorFG();
        }
        return camera != null ? camera.getY() : 0;
    }

    @Override
    public void renderAfterBackground(Camera camera, int frameCounter) {
        var levelManager = GameServices.levelOrNull();
        if (levelManager != null
                && levelManager.getCurrentZone() == Sonic3kZoneIds.ZONE_SLOT_MACHINE
                && RuntimeManager.resolveCurrentOrBootstrapGameModule().getBonusStageProvider()
                instanceof Sonic3kBonusStageCoordinator coordinator
                && coordinator.activeSlotRuntime() != null) {
            coordinator.activeSlotRuntime().ensureForegroundGlassPriority();
        }
        aizBattleshipRenderFeature.renderAfterBackground(camera, frameCounter);
    }

    @Override
    public int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex) {
        if (waterSurfaceManager != null) {
            baseIndex = waterSurfaceManager.ensurePatternsCached(graphicsManager, baseIndex);
        }
        baseIndex = HCZWaterSkimHandler.ensurePatternsCached(graphicsManager, baseIndex);
        return baseIndex;
    }

    @Override
    public boolean shouldEnableForegroundHeatHaze(int zoneIndex, int actIndex, int cameraX) {
        return aizTransitionRenderFeature.shouldEnableForegroundHeatHaze(zoneIndex, actIndex, cameraX);
    }

    @Override
    public boolean shouldEnablePerLineForegroundScroll(int zoneIndex, int actIndex, int cameraX) {
        return zoneIndex == Sonic3kZoneIds.ZONE_SLOT_MACHINE;
    }

    @Override
    public boolean isIntroOceanPhaseActive(int zoneIndex, int actIndex) {
        if (zoneIndex != 0 || actIndex != 0) {
            return false;
        }
        if (AizPlaneIntroInstance.isMainLevelPhaseActive()) {
            return false;
        }
        return !GameServices.camera().isLevelStarted();
    }

    @Override
    public float getVdpNametableBase(int zoneIndex, int actIndex, int cameraX, int tilemapWidthTiles) {
        if (zoneIndex != 0 || actIndex != 0) {
            return 0.0f;
        }
        int introOffset = AizPlaneIntroInstance.getIntroScrollOffset();
        if (introOffset < 0) {
            return 0.0f;  // Pure ocean phase: no positions overwritten
        }
        // Overflow = total number of nametable column overwrites since scrolling began.
        // BG tile = cameraX / 16 (BG at half speed, 8px/tile).
        // First overwrite when bgTile = VDP_WRAP(64) - SCREEN_TILES(40) + 1 = 25.
        // NOT clamped: the shader decomposes overflow into gen/partial to handle
        // multiple wrap cycles (each position gets overwritten every 64 scroll steps).
        int bgTile = Math.floorDiv(cameraX, 16);
        return Math.max(0.0f, (float) (bgTile - 24));
    }

    @Override
    public boolean shouldSuppressHud(int zoneIndex, int actIndex) {
        if (zoneIndex != 0 || actIndex != 0) {
            return false;
        }
        // Hide HUD during AIZ intro until Camera marks level as started
        return !GameServices.camera().isLevelStarted();
    }

    @Override
    public boolean shouldSuppressInitialTitleCard(int zoneIndex, int actIndex) {
        if (zoneIndex != 0 || actIndex != 0) {
            return false;
        }
        SonicConfigurationService configService = GameServices.configuration();
        if (configService.getBoolean(SonicConfiguration.S3K_SKIP_INTROS)) {
            return false;
        }
        String mainCharacter = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        return mainCharacter != null && "sonic".equalsIgnoreCase(mainCharacter.trim());
    }

    @Override
    public boolean shouldSuppressUnderwaterPalette(int zoneIndex, int actIndex) {
        if (zoneIndex != Sonic3kZoneIds.ZONE_HCZ) {
            return false;
        }
        // HCZ1 keeps real water gameplay and a visible surface from the start, but the
        // underwater palette split does not engage until the first water-height switch.
        WaterSystem ws = GameServices.water();
        return actIndex == 0 && ws != null && ws.getWaterLevelY(zoneIndex, actIndex) == 0x0500;
    }

    @Override
    public float getWaterlineOffset(int zoneIndex, int actIndex) {
        // HCZ uses ROM-driven background strip updates and explicit wave-splash
        // sprites at Water_level itself; keeping the generic S2-style -8 split
        // creates a visible seam between the shader boundary and HCZ's art.
        if (zoneIndex == Sonic3kZoneIds.ZONE_HCZ) {
            return 0.0f;
        }
        return -8.0f;
    }

    @Override
    public boolean useSpriteSatMasking(int zoneIndex) {
        return zoneIndex == Sonic3kZoneIds.ZONE_GUMBALL;
    }

    protected Sonic3kAIZEvents getAizEvents() {
        Sonic3kLevelEventManager levelEventManager = resolveLevelEventManager();
        return levelEventManager != null ? levelEventManager.getAizEvents() : null;
    }

    protected int getFeatureActId() {
        var levelManager = GameServices.levelOrNull();
        return levelManager != null ? levelManager.getFeatureActId() : 0;
    }

    private Sonic3kLevelEventManager resolveLevelEventManager() {
        return GameServices.hasRuntime()
                ? (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()
                : null;
    }

}
