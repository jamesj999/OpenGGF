package uk.co.jamesj999.sonic.game.sonic2;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.LevelEventProvider;
import uk.co.jamesj999.sonic.level.ParallaxManager;
import uk.co.jamesj999.sonic.level.WaterSystem;

/**
 * Sonic 2 implementation of dynamic level events.
 * ROM equivalent: RunDynamicLevelEvents (s2.asm:20297-20340)
 *
 * This system allows levels to dynamically adjust camera boundaries
 * based on player position, triggering boss arenas, vertical section
 * transitions, and other gameplay sequences.
 *
 * Each zone has its own event routine dispatched via the zone index.
 */
public class LevelEventManager implements LevelEventProvider {
    private static LevelEventManager instance;

    private final Camera camera;

    // Current zone and act
    private int currentZone = -1;
    private int currentAct = -1;

    // Per-zone event routine state (ROM: Dynamic_Resize_Routine)
    // Incremented by 2 as each event completes, similar to ROM behavior
    private int eventRoutine = 0;

    // Zone constants (matches ROM zone ordering)
    public static final int ZONE_EHZ = 0;
    public static final int ZONE_UNUSED_1 = 1;
    public static final int ZONE_ARZ = 2;
    public static final int ZONE_CNZ = 3;
    public static final int ZONE_HTZ = 4;
    public static final int ZONE_MCZ = 5;
    public static final int ZONE_OOZ = 6;
    public static final int ZONE_MTZ = 7;
    public static final int ZONE_UNUSED_2 = 8;
    public static final int ZONE_SCZ = 9;
    public static final int ZONE_WFZ = 10;
    public static final int ZONE_DEZ = 11;
    // CPZ uses zone index 1 in level event ordering (ROM zone ID 0x0D)
    public static final int ZONE_CPZ = 1;

    private LevelEventManager() {
        this.camera = Camera.getInstance();
    }

    /**
     * Called when entering a new level to reset event state.
     */
    @Override
    public void initLevel(int zone, int act) {
        this.currentZone = zone;
        this.currentAct = act;
        this.eventRoutine = 0;
        this.bossSpawnDelay = 0;
        this.cpzWaterTriggered = false;
        this.cpzBoss = null;
        this.arzBoss = null;
        this.ehzBoss = null;
        this.cnzBoss = null;
    }

    /**
     * Called every frame to run dynamic level events.
     * Must be called BEFORE camera.updateBoundaryEasing().
     */
    @Override
    public void update() {
        if (currentZone < 0) {
            return;
        }

        // Dispatch to zone-specific event handler
        switch (currentZone) {
            case ZONE_EHZ -> updateEHZ();
            case ZONE_CPZ -> updateCPZ();
            case ZONE_HTZ -> updateHTZ();
            case ZONE_MCZ -> updateMCZ();
            case ZONE_ARZ -> updateARZ();
            case ZONE_CNZ -> updateCNZ();
            case ZONE_OOZ -> updateOOZ();
            case ZONE_MTZ -> updateMTZ();
            case ZONE_WFZ -> updateWFZ();
            case ZONE_DEZ -> updateDEZ();
            case ZONE_SCZ -> updateSCZ();
            default -> {
                // No events for this zone
            }
        }
    }

    // =========================================================================
    // Zone Event Handlers
    // ROM: LevEvents_xxx routines starting at s2.asm:20369
    // =========================================================================

    // Boss spawn delay counter (ROM: Boss_spawn_delay)
    private int bossSpawnDelay = 0;
    private boolean cpzWaterTriggered = false;

    // EHZ Act 2 boss reference
    private uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2EHZBossInstance ehzBoss = null;
    // CPZ Act 2 boss reference
    private uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2CPZBossInstance cpzBoss = null;
    // ARZ Act 2 boss reference
    private uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2ARZBossInstance arzBoss = null;
    // CNZ Act 2 boss reference
    private uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2CNZBossInstance cnzBoss = null;

    /**
     * Emerald Hill Zone events.
     * ROM: LevEvents_EHZ (s2.asm:20357-20503)
     *
     * Act 1: No dynamic events
     * Act 2: Boss arena boundary changes when reaching end of level
     */
    private void updateEHZ() {
        if (currentAct == 0) {
            // Act 1: No dynamic events (ROM: LevEvents_EHZ1 just returns)
            return;
        }

        // Act 2: Boss arena setup
        // ROM: LevEvents_EHZ2 (s2.asm:20363-20503)
        switch (eventRoutine) {
            case 0 -> {
                // Routine 0 (s2.asm:20377-20388): Wait for camera X >= $2780
                if (camera.getX() >= 0x2780) {
                    // ROM: Set minX to current camera X (immediate, prevents backtracking)
                    camera.setMinX(camera.getX());
                    // ROM: Set maxY TARGET to $390 (will ease down to boss arena height)
                    camera.setMaxYTarget((short) 0x390);
                    eventRoutine += 2;
                }
            }
            case 2 -> {
                // Routine 1 (s2.asm:20396-20411): Wait for camera X >= $28F0
                if (camera.getX() >= 0x28F0) {
                    // ROM: Lock X boundaries immediately for boss arena (not eased)
                    camera.setMinX((short) 0x28F0);
                    camera.setMaxX((short) 0x2940);
                    eventRoutine += 2;
                    bossSpawnDelay = 0;
                    // ROM: Start music fade-out (s2.asm:20404)
                    // Fade runs during the 90-frame spawn delay
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance().fadeOutMusic();
                }
            }
            case 4 -> {
                // Routine 2 (s2.asm:20414-20435): Lock floor and spawn boss
                // ROM: Set minY when camera Y reaches $388 (immediate, not eased)
                if (camera.getY() >= 0x388) {
                    camera.setMinY((short) 0x388);
                }
                // ROM: Increment delay every frame, spawn boss at $5A (90) frames
                bossSpawnDelay++;
                if (bossSpawnDelay >= 0x5A) {
                    spawnEHZBoss();
                    eventRoutine += 2;
                    // Start boss music
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance()
                        .playMusic(uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants.MUS_BOSS);
                }
            }
            case 6 -> {
                // Routine 3 (s2.asm:20438+): Wait for boss defeat
                if (ehzBoss != null && ehzBoss.isDefeated()) {
                    // Boss handles camera unlock and EggPrison spawn in its defeat sequence
                    // No additional action needed here
                    eventRoutine += 2;
                }
            }
            default -> {
                // No more routines
            }
        }
    }

    /**
     * Spawns the EHZ Act 2 boss.
     * ROM: Creates Object 0x56 at coordinates (0x29D0, 0x0426) with subtype 0x81
     */
    private void spawnEHZBoss() {
        uk.co.jamesj999.sonic.level.objects.ObjectSpawn bossSpawn =
            new uk.co.jamesj999.sonic.level.objects.ObjectSpawn(
                0x29D0, 0x0426,
                uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds.EHZ_BOSS,
                0x81, 0, false, 0
            );

        ehzBoss = new uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2EHZBossInstance(
            bossSpawn,
            uk.co.jamesj999.sonic.level.LevelManager.getInstance()
        );

        uk.co.jamesj999.sonic.level.LevelManager.getInstance()
            .getObjectManager()
            .addDynamicObject(ehzBoss);
    }

    /**
     * Hill Top Zone events.
     * ROM: LevEvents_HTZ (s2.asm:20543-20670)
     *
     * HTZ has complex earthquake/lava events with screen shake zones.
     * The screen shake is triggered when the player enters certain areas
     * and cleared when they exit those areas.
     *
     * Act 1 shake triggers:
     * - Routine 1: Camera_X >= 0x1800 AND Camera_Y >= 0x400
     * - Routine 3: Camera_X < 0x1F00
     *
     * Act 2 shake triggers:
     * - Routine 1: Camera_X >= 0x14C0
     * - Routine 3: Camera_X < 0x1B00
     * - Routine 5: Camera_X < 0x1B00
     */
    private void updateHTZ() {
        if (currentAct == 0) {
            updateHTZAct1();
        } else {
            updateHTZAct2();
        }
    }

    // HTZ Act 1 screen shake trigger coordinates
    private static final int HTZ1_SHAKE_TRIGGER_X = 0x1800;
    private static final int HTZ1_SHAKE_TRIGGER_Y = 0x400;
    private static final int HTZ1_SHAKE_EXIT_X = 0x1F00;

    // HTZ Act 2 screen shake trigger coordinates
    private static final int HTZ2_SHAKE_TRIGGER_X = 0x14C0;
    private static final int HTZ2_SHAKE_EXIT_X = 0x1B00;

    /**
     * HTZ Act 1 events.
     * ROM: LevEvents_HTZ1 (s2.asm:20674-20838)
     */
    private void updateHTZAct1() {
        switch (eventRoutine) {
            case 0 -> {
                // Routine 0: Wait for shake trigger
                // ROM: LevEvents_HTZ_Routine1 checks Camera_Y >= $400 AND Camera_X >= $1800
                if (camera.getY() >= HTZ1_SHAKE_TRIGGER_Y &&
                    camera.getX() >= HTZ1_SHAKE_TRIGGER_X) {
                    // Enable screen shake
                    ParallaxManager.getInstance().setHtzScreenShake(true);
                    eventRoutine += 2;
                } else {
                    // Routine 1 Part 2: If shake was active but we're out of range, clear it
                    ParallaxManager.getInstance().setHtzScreenShake(false);
                }
            }
            case 2 -> {
                // Routine 1: Shaking area - check for exit at X >= $1E00 to clear shake flag
                if (camera.getX() >= 0x1E00) {
                    // Exit shake area
                    ParallaxManager.getInstance().setHtzScreenShake(false);
                    eventRoutine += 2;
                }
            }
            case 4 -> {
                // Routine 2: Post-shake area
                // Check for re-entry into shake zone (Routine 3)
                if (camera.getX() < HTZ1_SHAKE_EXIT_X) {
                    ParallaxManager.getInstance().setHtzScreenShake(true);
                    eventRoutine -= 2;  // Go back to routine 1
                }
            }
            default -> {
                // Further routines handle boss area etc.
            }
        }
    }

    /**
     * HTZ Act 2 events.
     * ROM: LevEvents_HTZ2 (s2.asm:20920-21193)
     */
    private void updateHTZAct2() {
        switch (eventRoutine) {
            case 0 -> {
                // Routine 0: Wait for shake trigger
                if (camera.getX() >= HTZ2_SHAKE_TRIGGER_X) {
                    ParallaxManager.getInstance().setHtzScreenShake(true);
                    eventRoutine += 2;
                } else {
                    ParallaxManager.getInstance().setHtzScreenShake(false);
                }
            }
            case 2 -> {
                // Routine 1: Shaking area
                if (camera.getX() >= 0x1A00) {
                    ParallaxManager.getInstance().setHtzScreenShake(false);
                    eventRoutine += 2;
                }
            }
            case 4 -> {
                // Routine 2: Post-shake
                if (camera.getX() < HTZ2_SHAKE_EXIT_X) {
                    ParallaxManager.getInstance().setHtzScreenShake(true);
                    eventRoutine -= 2;
                }
            }
            default -> {
                // Further routines handle additional shake zones and boss
            }
        }
    }

    /**
     * Chemical Plant Zone events.
     * ROM: LevEvents_CPZ (s2.asm:20504-20542)
     *
     * Act 1: No dynamic events
     * Act 2: Water (Mega Mack) rises when player reaches trigger X coordinate
     *
     * CPZ2 water rising: When camera X >= trigger point, set water target level
     * and WaterSystem will gradually raise it each frame until target reached.
     */
    private void updateCPZ() {
        if (currentAct != 1) {
            // Only Act 2 has water rise events
            return;
        }
        updateCPZWaterRise();
        updateCPZBossEvents();
    }

    private void updateCPZWaterRise() {
        if (cpzWaterTriggered) {
            return;
        }
        final int ZONE_ID_CPZ_ROM = 0x0D;
        final int WATER_RISE_TRIGGER_X = 0x1E80;
        final int WATER_TARGET_Y = 0x508;
        var player = camera.getFocusedSprite();
        if (player != null && player.getX() >= WATER_RISE_TRIGGER_X) {
            WaterSystem.getInstance().setWaterLevelTarget(
                    ZONE_ID_CPZ_ROM, currentAct, WATER_TARGET_Y);
            cpzWaterTriggered = true;
        }
    }

    private void updateCPZBossEvents() {
        switch (eventRoutine) {
            case 0 -> {
                if (camera.getX() >= 0x2680) {
                    camera.setMinX(camera.getX());
                    camera.setMaxYTarget((short) 0x450);
                    eventRoutine += 2;
                }
            }
            case 2 -> {
                if (camera.getX() >= 0x2A20) {
                    // ROM locks camera completely at X=0x2A20 for the entire fight
                    camera.setMinX((short) 0x2A20);
                    camera.setMaxX((short) 0x2A20);
                    eventRoutine += 2;
                    bossSpawnDelay = 0;
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance().fadeOutMusic();
                    uk.co.jamesj999.sonic.game.GameServices.gameState().setCurrentBossId(1);
                }
            }
            case 4 -> {
                if (camera.getY() >= 0x448) {
                    camera.setMinY((short) 0x448);
                }
                bossSpawnDelay++;
                if (bossSpawnDelay >= 0x5A) {
                    spawnCPZBoss();
                    eventRoutine += 2;
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance()
                            .playMusic(uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants.MUS_BOSS);
                }
            }
            case 6 -> {
                // Prevent backtracking - minX can only increase, never decrease
                // ROM: move.w (Camera_X_pos).w,(Camera_Min_X_pos).w
                short cameraX = camera.getX();
                if (cameraX > camera.getMinX()) {
                    camera.setMinX(cameraX);
                }
            }
            default -> {
            }
        }
    }

    private void spawnCPZBoss() {
        uk.co.jamesj999.sonic.level.objects.ObjectSpawn bossSpawn =
                new uk.co.jamesj999.sonic.level.objects.ObjectSpawn(
                        0x2B80, 0x04B0,
                        uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds.CPZ_BOSS,
                        0, 0, false, 0
                );
        cpzBoss = new uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2CPZBossInstance(
                bossSpawn,
                uk.co.jamesj999.sonic.level.LevelManager.getInstance()
        );
        uk.co.jamesj999.sonic.level.LevelManager.getInstance()
                .getObjectManager()
                .addDynamicObject(cpzBoss);
    }

    /**
     * Mystic Cave Zone events.
     * ROM: LevEvents_MCZ (s2.asm:20777-20851)
     */
    private void updateMCZ() {
        // MCZ has vertical section changes
        // Implement as needed
    }

    /**
     * Aquatic Ruin Zone events.
     * ROM: LevEvents_ARZ (s2.asm:21717-21790)
     *
     * Act 1: No dynamic events
     * Act 2: Boss arena - camera lock and boss spawn
     */
    private void updateARZ() {
        if (currentAct == 0) {
            // Act 1: No dynamic events
            return;
        }

        // Act 2: Boss arena setup
        // ROM: LevEvents_ARZ2 (s2.asm:21723-21790)
        switch (eventRoutine) {
            case 0 -> {
                // Routine 1 (s2.asm:21737-21749): Wait for camera X >= $2810
                if (camera.getX() >= 0x2810) {
                    // ROM: Set minX to current camera X (prevent backtracking)
                    camera.setMinX(camera.getX());
                    // ROM: Set maxY TARGET to $400 (boss arena height)
                    camera.setMaxYTarget((short) 0x400);
                    eventRoutine += 2;
                    // ROM: move.b #4,(Current_Boss_ID).w
                    uk.co.jamesj999.sonic.game.GameServices.gameState().setCurrentBossId(4);
                    // ROM: LoadPLC for ARZ boss art (handled elsewhere)
                }
            }
            case 2 -> {
                // Routine 2 (s2.asm:21752-21767): Wait for camera X >= $2A40, spawn boss
                if (camera.getX() >= 0x2A40) {
                    // ROM: Lock camera X at $2A40 (both min and max)
                    camera.setMinX((short) 0x2A40);
                    camera.setMaxX((short) 0x2A40);
                    eventRoutine += 2;
                    bossSpawnDelay = 0;
                    // ROM: Fade out music
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance().fadeOutMusic();
                    // ROM: Spawn ARZ boss (obj89) - this is where the boss is created!
                    spawnARZBoss();
                }
            }
            case 4 -> {
                // Routine 3 (s2.asm:21770-21783): Lock floor, wait for spawn delay, play boss music
                // ROM: Lock minY when camera Y >= $3F8
                if (camera.getY() >= 0x3F8) {
                    camera.setMinY((short) 0x3F8);
                }
                // ROM: Increment delay every frame, play boss music at $5A (90) frames
                bossSpawnDelay++;
                if (bossSpawnDelay >= 0x5A) {
                    eventRoutine += 2;
                    // Start boss music
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance()
                            .playMusic(uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants.MUS_BOSS);
                }
            }
            case 6 -> {
                // Routine 4 (s2.asm:21786-21790): Prevent backtracking during boss fight
                // ROM: move.w (Camera_X_pos).w,(Camera_Min_X_pos).w
                short cameraX = camera.getX();
                if (cameraX > camera.getMinX()) {
                    camera.setMinX(cameraX);
                }
            }
            default -> {
                // No more routines
            }
        }
    }

    /**
     * Spawns the ARZ Act 2 boss.
     * ROM: Creates Object 0x89 at current location with subtype 0
     * Note: The boss object handles its own positioning in initMain().
     */
    private void spawnARZBoss() {
        // ROM spawns obj89 with no specific coordinates - the object positions itself
        uk.co.jamesj999.sonic.level.objects.ObjectSpawn bossSpawn =
                new uk.co.jamesj999.sonic.level.objects.ObjectSpawn(
                        0x2AE0, 0x388,  // Boss starting position (set in Obj89_Init_RaisePillars)
                        uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds.ARZ_BOSS,
                        0, 0, false, 0
                );

        arzBoss = new uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2ARZBossInstance(
                bossSpawn,
                uk.co.jamesj999.sonic.level.LevelManager.getInstance()
        );

        uk.co.jamesj999.sonic.level.LevelManager.getInstance()
                .getObjectManager()
                .addDynamicObject(arzBoss);
    }

    /**
     * Casino Night Zone events.
     * ROM: LevEvents_CNZ (s2.asm:21479-21570)
     *
     * Act 1: No dynamic events (just SlotMachine call)
     * Act 2: Boss arena - camera lock and boss spawn
     *
     * CNZ2 boss arena triggers:
     * - Routine 0: Camera X >= $27C0 -> lock minX, set maxY target
     * - Routine 2: Camera X >= $2890 -> lock arena, load palette, fade music
     * - Routine 4: Lock minY, spawn boss after delay, play boss music
     * - Routine 6: Prevent backtracking during fight
     */
    private void updateCNZ() {
        if (currentAct == 0) {
            // Act 1: No dynamic events (ROM: LevEvents_CNZ just calls SlotMachine and returns)
            return;
        }

        // Act 2: Boss arena setup
        // ROM: LevEvents_CNZ2 (s2.asm:21486-21570)
        switch (eventRoutine) {
            case 0 -> {
                // Routine 0 (s2.asm:21500-21517): Wait for camera X >= $27C0
                if (camera.getX() >= 0x27C0) {
                    // ROM: Set minX to current camera X (prevent backtracking)
                    camera.setMinX(camera.getX());
                    // ROM: Set maxY TARGET to $5D0 (boss arena height)
                    camera.setMaxYTarget((short) 0x5D0);
                    eventRoutine += 2;
                }
            }
            case 2 -> {
                // Routine 2 (s2.asm:21520-21538): Wait for camera X >= $2890, lock arena
                if (camera.getX() >= 0x2890) {
                    // ROM: Lock camera X boundaries for boss arena
                    camera.setMinX((short) 0x2860);
                    camera.setMaxX((short) 0x28E0);
                    eventRoutine += 2;
                    bossSpawnDelay = 0;
                    // ROM: Fade out music
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance().fadeOutMusic();
                    // ROM: Set Current_Boss_ID to 3 (CNZ boss)
                    uk.co.jamesj999.sonic.game.GameServices.gameState().setCurrentBossId(3);
                    // ROM: Load CNZ boss palette (Pal_CNZ_B, palette $25)
                    // Note: Palette loading is handled by the boss object
                }
            }
            case 4 -> {
                // Routine 4 (s2.asm:21541-21558): Lock floor and spawn boss
                // ROM: Set minY when camera Y reaches $4E0
                if (camera.getY() >= 0x4E0) {
                    camera.setMinY((short) 0x4E0);
                }
                // ROM: Increment delay every frame, spawn boss at $5A (90) frames
                bossSpawnDelay++;
                if (bossSpawnDelay >= 0x5A) {
                    spawnCNZBoss();
                    eventRoutine += 2;
                    // Start boss music
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance()
                            .playMusic(uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants.MUS_BOSS);
                }
            }
            case 6 -> {
                // Routine 6 (s2.asm:21561-21570): Prevent backtracking during boss fight
                // ROM: When camera X >= $2A00, adjust camera bounds
                if (camera.getX() >= 0x2A00) {
                    camera.setMaxYTarget((short) 0x5D0);
                    // ROM: move.w (Camera_X_pos).w,(Camera_Min_X_pos).w
                    short cameraX = camera.getX();
                    if (cameraX > camera.getMinX()) {
                        camera.setMinX(cameraX);
                    }
                }
            }
            default -> {
                // No more routines
            }
        }
    }

    /**
     * Spawns the CNZ Act 2 boss.
     * ROM: Creates Object 0x51 via JmpTo_AllocateObject in LevEvents_CNZ2_Routine3
     * The boss object handles its own initial positioning.
     */
    private void spawnCNZBoss() {
        // ROM spawns obj51 with no specific coordinates - the object positions itself
        // CNZ boss initial position from Sonic2CNZBossInstance: (0x2A46, 0x654)
        uk.co.jamesj999.sonic.level.objects.ObjectSpawn bossSpawn =
                new uk.co.jamesj999.sonic.level.objects.ObjectSpawn(
                        0x2A46, 0x654,  // Boss starting position (set in Obj51_Init)
                        uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds.CNZ_BOSS,
                        0, 0, false, 0
                );

        cnzBoss = new uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2CNZBossInstance(
                bossSpawn,
                uk.co.jamesj999.sonic.level.LevelManager.getInstance()
        );

        uk.co.jamesj999.sonic.level.LevelManager.getInstance()
                .getObjectManager()
                .addDynamicObject(cnzBoss);
    }

    /**
     * Oil Ocean Zone events.
     * ROM: LevEvents_OOZ (s2.asm:20938-21035)
     */
    private void updateOOZ() {
        // OOZ has oil level events
        // Implement as needed
    }

    /**
     * Metropolis Zone events.
     * ROM: LevEvents_MTZ (s2.asm:21036-21173)
     */
    private void updateMTZ() {
        // MTZ has vertical wrapping section events
        // Implement as needed
    }

    /**
     * Wing Fortress Zone events.
     * ROM: LevEvents_WFZ (s2.asm:21174-21310)
     */
    private void updateWFZ() {
        // WFZ has platform ride and boss events
        // Implement as needed
    }

    /**
     * Death Egg Zone events.
     * ROM: LevEvents_DEZ (s2.asm:21311-21395)
     */
    private void updateDEZ() {
        // DEZ is a single boss arena
        // Implement as needed
    }

    /**
     * Sky Chase Zone events.
     * ROM: LevEvents_SCZ (s2.asm:21396-21485)
     */
    private void updateSCZ() {
        // SCZ is auto-scrolling
        // Implement as needed
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public int getEventRoutine() {
        return eventRoutine;
    }

    public void setEventRoutine(int routine) {
        this.eventRoutine = routine;
    }

    public static synchronized LevelEventManager getInstance() {
        if (instance == null) {
            instance = new LevelEventManager();
        }
        return instance;
    }
}
