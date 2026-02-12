package uk.co.jamesj999.sonic.game.sonic2;

import uk.co.jamesj999.sonic.game.AbstractLevelEventManager;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.PlayerCharacter;
import uk.co.jamesj999.sonic.game.sonic2.audio.Sonic2Music;
import uk.co.jamesj999.sonic.game.sonic2.audio.Sonic2Sfx;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.ParallaxManager;
import uk.co.jamesj999.sonic.level.WaterSystem;

import java.util.logging.Logger;

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
public class Sonic2LevelEventManager extends AbstractLevelEventManager {
    private static Sonic2LevelEventManager instance;

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

    private static final Logger LOGGER = Logger.getLogger(Sonic2LevelEventManager.class.getName());

    /** VDP palette line size: 16 colors × 2 bytes each = 32 bytes */
    private static final int PALETTE_LINE_SIZE = 32;

    private Sonic2LevelEventManager() {
        super();
    }

    // =========================================================================
    // AbstractLevelEventManager contract
    // =========================================================================

    @Override
    protected int getRoutineStride() {
        return 2;
    }

    @Override
    protected int getEventDataFgSize() {
        return 6;
    }

    @Override
    protected int getEventDataBgSize() {
        return 0;
    }

    @Override
    public PlayerCharacter getPlayerCharacter() {
        return PlayerCharacter.SONIC_AND_TAILS;
    }

    @Override
    protected void onInitLevel(int zone, int act) {
        this.bossSpawnDelay = 0;
        this.cpzWaterTriggered = false;
        this.cpzBoss = null;
        this.arzBoss = null;
        this.ehzBoss = null;
        this.cnzBoss = null;
        this.cnzLeftWallX = -1;
        this.cnzLeftWallY = -1;
        this.cnzRightWallX = -1;
        this.cnzRightWallY = -1;
        this.htzBoss = null;
        this.mczBoss = null;
        // Reset OOZ oil state
        if (zone == ZONE_OOZ) {
            this.oilManager = new OilSurfaceManager();
        } else {
            this.oilManager = null;
        }
        // Reset HTZ earthquake state
        this.cameraBgYOffset = 0;
        this.htzTerrainSinking = false;
        this.htzTerrainDelay = 0;
        this.htzCurrentRisenLimit = (act == 0) ? HTZ1_LAVA_OFFSET_RISEN : HTZ2_LAVA_OFFSET_RISEN_TOP;
        this.htzCurrentSunkenLimit = (act == 0) ? HTZ1_LAVA_OFFSET_SUNKEN : HTZ2_LAVA_OFFSET_SUNKEN;
        this.htzCurrentBgXOffset = HTZ_BG_X_OFFSET_TOP;
    }

    @Override
    protected void onUpdate() {
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

    // =========================================================================
    // HTZ Earthquake State (ROM: Camera_BG_Y_offset, HTZ_Terrain_Direction, HTZ_Terrain_Delay)
    // =========================================================================

    /**
     * Camera_BG_Y_offset for HTZ earthquake lava positioning.
     * ROM: $FFFFF72E - Controls vertical offset of rising lava platforms.
     * Range: 224 (sinking limit) to 320 (risen limit).
     * When screen shake starts, initialized to 320.
     */
    private int cameraBgYOffset = 0;

    /**
     * HTZ terrain direction flag.
     * ROM: $FFFFF7C7 - When 0, lava is rising (offset goes toward 320).
     * When 1, lava is sinking (offset goes toward 224).
     */
    private boolean htzTerrainSinking = false;

    /**
     * HTZ terrain delay counter.
     * ROM: $FFFFF7C8 (word) - Counts down from $78 (120 frames) before direction toggles.
     */
    private int htzTerrainDelay = 0;

    /**
     * Active HTZ oscillation limits for the currently-entered earthquake mode.
     * These vary by act and by top/bottom route in Act 2.
     */
    private int htzCurrentRisenLimit = 0;
    private int htzCurrentSunkenLimit = 0;
    private int htzCurrentBgXOffset = 0;

    /**
     * Gets the current Camera_BG_Y_offset for HTZ rising lava.
     * Used by RisingLavaObjectInstance to calculate Y position.
     *
     * @return current BG Y offset (0 when not in earthquake, 224-320 during earthquake)
     */
    public int getCameraBgYOffset() {
        return cameraBgYOffset;
    }

    /**
     * Gets the current Camera_BG_X_offset used by HTZ earthquake BG scrolling.
     * Top route / Act 1 use 0; Act 2 bottom route uses -$680.
     */
    public int getHtzBgXOffset() {
        return htzCurrentBgXOffset;
    }

    /**
     * Returns the relative BG vertical shift for HTZ earthquake.
     * 0 = normal/risen position, positive = BG scrolled up (more lava visible).
     * This is used by SwScrlHtz to offset vscrollFactorBG without modifying bgCamera.bgYPos.
     */
    public int getHtzBgVerticalShift() {
        if (!GameServices.gameState().isHtzScreenShakeActive()) {
            return 0;
        }
        return htzCurrentRisenLimit - cameraBgYOffset;
    }

    // EHZ Act 2 boss reference
    private uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2EHZBossInstance ehzBoss = null;
    // CPZ Act 2 boss reference
    private uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2CPZBossInstance cpzBoss = null;
    // ARZ Act 2 boss reference
    private uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2ARZBossInstance arzBoss = null;
    // CNZ Act 2 boss reference
    private uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2CNZBossInstance cnzBoss = null;
    // HTZ Act 2 boss reference
    private uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2HTZBossInstance htzBoss = null;
    // MCZ Act 2 boss reference
    private uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2MCZBossInstance mczBoss = null;
    // CNZ Act 2 boss arena wall positions (for removal after defeat)
    // Layout offset calculation: offset / layoutWidth = y, offset % layoutWidth = x
    // ROM uses 256-wide layout, but we store the calculated x,y for flexibility
    // Note: Names reflect actual X positions (lower X = left, higher X = right)
    // - $C50 (x=80) is the LEFT wall (lower pixel position: 80 * 128 = 0x2800)
    // - $C54 (x=84) is the RIGHT wall (higher pixel position: 84 * 128 = 0x2A00)
    private int cnzLeftWallX = -1;   // x=80 from offset $C50
    private int cnzLeftWallY = -1;
    private int cnzRightWallX = -1;  // x=84 from offset $C54
    private int cnzRightWallY = -1;

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
        switch (eventRoutineFg) {
            case 0 -> {
                // Routine 0 (s2.asm:20377-20388): Wait for camera X >= $2780
                if (camera.getX() >= 0x2780) {
                    // ROM: Set minX to current camera X (immediate, prevents backtracking)
                    camera.setMinX(camera.getX());
                    // ROM: Set maxY TARGET to $390 (will ease down to boss arena height)
                    camera.setMaxYTarget((short) 0x390);
                    eventRoutineFg += 2;
                }
            }
            case 2 -> {
                // Routine 1 (s2.asm:20396-20411): Wait for camera X >= $28F0
                if (camera.getX() >= 0x28F0) {
                    // ROM: Lock X boundaries immediately for boss arena (not eased)
                    camera.setMinX((short) 0x28F0);
                    camera.setMaxX((short) 0x2940);
                    // Mark boss fight active when camera locks (enables tight boundary)
                    uk.co.jamesj999.sonic.game.GameServices.gameState().setCurrentBossId(
                        uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds.EHZ_BOSS);
                    eventRoutineFg += 2;
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
                    eventRoutineFg += 2;
                    // Start boss music
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance()
                        .playMusic(Sonic2Music.BOSS.id);
                }
            }
            case 6 -> {
                // Routine 3 (s2.asm:20438+): Wait for boss defeat
                if (ehzBoss != null && ehzBoss.isDefeated()) {
                    // Boss handles camera unlock and EggPrison spawn in its defeat sequence
                    // No additional action needed here
                    eventRoutineFg += 2;
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
        switch (eventRoutineFg) {
            case 0 -> {
                // Routine 0: Wait for shake trigger
                // ROM: LevEvents_HTZ_Routine1 checks Camera_Y >= $400 AND Camera_X >= $1800
                int cameraX0 = camera.getX();
                if (camera.getY() >= HTZ1_SHAKE_TRIGGER_Y &&
                    cameraX0 >= HTZ1_SHAKE_TRIGGER_X &&
                    cameraX0 < HTZ1_SHAKE_EXIT_X) {
                    // Enable screen shake and initialize earthquake
                    ParallaxManager.getInstance().setHtzScreenShake(true);
                    initHtzEarthquake(HTZ1_LAVA_OFFSET_RISEN, HTZ1_LAVA_OFFSET_SUNKEN, HTZ_BG_X_OFFSET_TOP);
                    eventRoutineFg += 2;
                } else if (cameraX0 >= HTZ1_SHAKE_EXIT_X) {
                    // Already past earthquake zone (e.g. teleported) - skip to post-shake
                    eventRoutineFg = 4;
                } else {
                    // Before earthquake zone or Y not met
                    if (uk.co.jamesj999.sonic.game.GameServices.gameState().isHtzScreenShakeActive()) {
                        exitHtzEarthquakeArea();
                    }
                }
            }
            case 2 -> {
                int cameraX = camera.getX();

                // ROM: LevEvents_HTZ_Routine2 only updates oscillation in [$1978, $1E00).
                if (cameraX >= HTZ1_OSCILLATE_START_X && cameraX < HTZ1_SHAKE_DISABLE_X) {
                    updateHtzLavaOscillation();
                } else if (cameraX >= HTZ1_SHAKE_DISABLE_X) {
                    // ROM: move.b #0,(Screen_Shaking_Flag).w
                    GameServices.gameState().setScreenShakeActive(false);
                }

                // Keep routine 2 active while in [$1800, $1F00), matching disassembly.
                if (cameraX < HTZ1_SHAKE_TRIGGER_X) {
                    exitHtzEarthquakeArea();
                    eventRoutineFg -= 2;
                } else if (cameraX >= HTZ1_SHAKE_EXIT_X) {
                    exitHtzEarthquakeArea();
                    eventRoutineFg += 2;
                }
            }
            case 4 -> {
                // Routine 3: Post-shake area
                // Check for re-entry into shake zone
                if (camera.getX() < HTZ1_SHAKE_EXIT_X) {
                    ParallaxManager.getInstance().setHtzScreenShake(true);
                    initHtzEarthquake(HTZ1_LAVA_OFFSET_RISEN, HTZ1_LAVA_OFFSET_SUNKEN, HTZ_BG_X_OFFSET_TOP);
                    eventRoutineFg -= 2;  // Go back to routine 2
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
     *
     * Act 2 has multiple earthquake zones based on Camera Y position:
     * - Top area (Camera_Y < $380): Routines 0-4
     * - Bottom area (Camera_Y >= $380): Routines 6-8
     *
     * When entering earthquake zone in bottom area, ROM jumps directly to routine 8
     * (line 20946-20951: cmpi.w #$380,(Camera_Y_pos).w / blo.s + / addq.w #4,d0)
     */
    private void updateHTZAct2() {
        switch (eventRoutineFg) {
            case 0 -> {
                // Routine 0: Wait for shake trigger
                int cameraX0 = camera.getX();
                if (cameraX0 >= HTZ2_SHAKE_TRIGGER_X && cameraX0 < HTZ2_SHAKE_EXIT_X) {
                    // ROM: Check Y position to determine which earthquake zone
                    // If Camera_Y >= $380, jump to bottom area routines
                    if (camera.getY() >= HTZ2_Y_ZONE_THRESHOLD) {
                        // Bottom area
                        ParallaxManager.getInstance().setHtzScreenShake(true);
                        initHtzEarthquake(HTZ2_LAVA_OFFSET_RISEN_BOTTOM, HTZ2_LAVA_OFFSET_SUNKEN,
                                HTZ_BG_X_OFFSET_BOTTOM);
                        eventRoutineFg = 8;
                    } else {
                        // Top area
                        ParallaxManager.getInstance().setHtzScreenShake(true);
                        initHtzEarthquake(HTZ2_LAVA_OFFSET_RISEN_TOP, HTZ2_LAVA_OFFSET_SUNKEN, HTZ_BG_X_OFFSET_TOP);
                        eventRoutineFg = 2;
                    }
                } else if (cameraX0 >= HTZ2_SHAKE_EXIT_X) {
                    // Already past earthquake zone (e.g. teleported to last checkpoint)
                    // Skip to appropriate post-shake routine based on Y position
                    if (camera.getY() >= HTZ2_Y_ZONE_THRESHOLD) {
                        // Bottom zone
                        if (cameraX0 >= HTZ2_BOSS_CUTOFF_X) {
                            eventRoutineFg = 12;  // Skip to boss prep
                        } else {
                            eventRoutineFg = 10;  // Skip to post-shake (bottom)
                        }
                    } else {
                        eventRoutineFg = 4;  // Skip to post-shake (top)
                    }
                } else {
                    // Before earthquake zone
                    if (uk.co.jamesj999.sonic.game.GameServices.gameState().isHtzScreenShakeActive()) {
                        exitHtzEarthquakeArea();
                    }
                }
            }
            case 2 -> {
                int cameraX = camera.getX();

                // ROM: LevEvents_HTZ2_Routine2 oscillates only in [$1678, $1A00).
                if (cameraX >= HTZ2_TOP_OSCILLATE_START_X && cameraX < HTZ2_TOP_SHAKE_DISABLE_X) {
                    updateHtzLavaOscillation();
                } else if (cameraX >= HTZ2_TOP_SHAKE_DISABLE_X) {
                    // ROM: move.b #0,(Screen_Shaking_Flag).w
                    GameServices.gameState().setScreenShakeActive(false);
                }

                // Keep routine 2 active while in [$14C0, $1B00).
                if (cameraX < HTZ2_SHAKE_TRIGGER_X) {
                    exitHtzEarthquakeArea();
                    eventRoutineFg = 0;
                } else if (cameraX >= HTZ2_SHAKE_EXIT_X) {
                    exitHtzEarthquakeArea();
                    eventRoutineFg += 2;
                }
            }
            case 4 -> {
                // Routine 2: Post-shake (top zone)
                if (camera.getX() < HTZ2_SHAKE_EXIT_X) {
                    ParallaxManager.getInstance().setHtzScreenShake(true);
                    initHtzEarthquake(HTZ2_LAVA_OFFSET_RISEN_TOP, HTZ2_LAVA_OFFSET_SUNKEN, HTZ_BG_X_OFFSET_TOP);
                    eventRoutineFg -= 2;
                }
            }
            case 6 -> {
                // Routine 3: Wait for bottom area shake trigger
                // This handles re-entry to earthquake zone in bottom area
                if (camera.getX() >= HTZ2_SHAKE_TRIGGER_X) {
                    ParallaxManager.getInstance().setHtzScreenShake(true);
                    initHtzEarthquake(HTZ2_LAVA_OFFSET_RISEN_BOTTOM, HTZ2_LAVA_OFFSET_SUNKEN,
                            HTZ_BG_X_OFFSET_BOTTOM);
                    eventRoutineFg += 2;
                } else {
                    if (uk.co.jamesj999.sonic.game.GameServices.gameState().isHtzScreenShakeActive()) {
                        exitHtzEarthquakeArea();
                    }
                }
            }
            case 8 -> {
                int cameraX = camera.getX();

                // ROM: LevEvents_HTZ2_Routine4 oscillates only in [$15F0, $1AC0).
                if (cameraX >= HTZ2_BOTTOM_OSCILLATE_START_X && cameraX < HTZ2_BOTTOM_SHAKE_DISABLE_X) {
                    updateHtzLavaOscillation();
                } else if (cameraX >= HTZ2_BOTTOM_SHAKE_DISABLE_X) {
                    // ROM: move.b #0,(Screen_Shaking_Flag).w
                    GameServices.gameState().setScreenShakeActive(false);
                }

                // Keep routine 8 active while in [$14C0, $1B00).
                if (cameraX < HTZ2_SHAKE_TRIGGER_X) {
                    exitHtzEarthquakeArea();
                    eventRoutineFg = 6;
                } else if (cameraX >= HTZ2_SHAKE_EXIT_X) {
                    exitHtzEarthquakeArea();
                    eventRoutineFg = 10;
                }
            }
            case 10 -> {
                // Routine 5: Post-shake (bottom zone)
                if (camera.getX() < HTZ2_SHAKE_EXIT_X) {
                    ParallaxManager.getInstance().setHtzScreenShake(true);
                    initHtzEarthquake(HTZ2_LAVA_OFFSET_RISEN_BOTTOM, HTZ2_LAVA_OFFSET_SUNKEN,
                            HTZ_BG_X_OFFSET_BOTTOM);
                    eventRoutineFg -= 2;
                } else if (camera.getX() >= HTZ2_BOSS_CUTOFF_X) {
                    // Approaching boss area - advance to boss routines
                    exitHtzEarthquakeArea();
                    eventRoutineFg = 12;
                }
            }
            case 12 -> {
                // Routine 6: Boss area cutoff (LevEvents_HTZ2_Routine6)
                // ROM: s2.asm:21197-21206
                if (camera.getX() >= HTZ2_BOSS_ARENA_TRIGGER_X) {
                    // ROM: Set minX to lock arena left boundary
                    camera.setMinX(camera.getX());
                    // ROM: Set maxY TARGET to allow boss area access
                    camera.setMaxYTarget((short) HTZ2_BOSS_ARENA_MAX_Y);
                    eventRoutineFg += 2;
                }
            }
            case 14 -> {
                // Routine 7: Boss arena camera shift (LevEvents_HTZ2_Routine7)
                // ROM: s2.asm:21222-21238
                if (camera.getX() >= HTZ2_BOSS_ARENA_LEFT) {
                    // ROM: Lock camera X boundaries
                    camera.setMinX((short) HTZ2_BOSS_ARENA_LEFT);
                    camera.setMaxX((short) HTZ2_BOSS_ARENA_RIGHT);
                    eventRoutineFg += 2;
                    bossSpawnDelay = 0;
                    // ROM: Fade out music
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance().fadeOutMusic();
                    // ROM: Set Current_Boss_ID to 3 (HTZ boss)
                    uk.co.jamesj999.sonic.game.GameServices.gameState().setCurrentBossId(3);
                }
            }
            case 16 -> {
                // Routine 8: Boss spawn (LevEvents_HTZ2_Routine8)
                // ROM: s2.asm:21241-21258
                // ROM: Lock minY when camera Y reaches boss floor
                if (camera.getY() >= HTZ2_BOSS_FLOOR_Y) {
                    camera.setMinY((short) HTZ2_BOSS_FLOOR_Y);
                }
                // ROM: Increment delay every frame, spawn boss at $5A (90) frames
                bossSpawnDelay++;
                if (bossSpawnDelay >= 0x5A) {
                    spawnHTZBoss();
                    eventRoutineFg += 2;
                    // Start boss music
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance()
                            .playMusic(Sonic2Music.BOSS.id);
                }
            }
            case 18 -> {
                // Routine 9: Boss end / camera extend (LevEvents_HTZ2_Routine9)
                // ROM: s2.asm:21261-21277
                if (uk.co.jamesj999.sonic.game.GameServices.gameState().getCurrentBossId() != 0) {
                    // ROM: does nothing until Boss_defeated_flag is set.
                    return;
                }

                // ROM: Camera_Min_X_pos follows Camera_X_pos after boss defeat.
                short cameraX = camera.getX();
                camera.setMinX(cameraX);

                // ROM: once camera reaches $30E0, ease Y bounds up toward $428/$430.
                if (cameraX >= 0x30E0) {
                    if (camera.getMinY() >= 0x428) {
                        camera.setMinY((short) (camera.getMinY() - 2));
                    }
                    if (camera.getMaxYTarget() >= 0x430) {
                        camera.setMaxYTarget((short) (camera.getMaxYTarget() - 2));
                    }
                }
            }
            default -> {
                // Post-boss: no more routines
            }
        }
    }

    // HTZ Act 2 boss arena constants (from disassembly LevEvents_HTZ2)
    /** Cutoff trigger X to start boss preparation (ROM: $2B00) */
    private static final int HTZ2_BOSS_CUTOFF_X = 0x2B00;
    /** Boss prep trigger X (ROM: $2C50) */
    private static final int HTZ2_BOSS_ARENA_TRIGGER_X = 0x2C50;
    /** Boss arena left camera lock X (ROM: $2EE0) */
    private static final int HTZ2_BOSS_ARENA_LEFT = 0x2EE0;
    /** Boss arena right camera lock X (ROM: $2F5E) */
    private static final int HTZ2_BOSS_ARENA_RIGHT = 0x2F5E;
    /** Boss arena max Y target (ROM: $480) */
    private static final int HTZ2_BOSS_ARENA_MAX_Y = 0x480;
    /** Boss arena floor Y lock (ROM: $478) */
    private static final int HTZ2_BOSS_FLOOR_Y = 0x478;

    /**
     * Spawns the HTZ Act 2 boss.
     * ROM: Creates Object 0x52 at arena position
     */
    private void spawnHTZBoss() {
        // HTZ boss initial position from Obj52_Init
        uk.co.jamesj999.sonic.level.objects.ObjectSpawn bossSpawn =
                new uk.co.jamesj999.sonic.level.objects.ObjectSpawn(
                        0x3040, 0x0580,  // Boss starting position (set in Obj52_Init)
                        uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds.HTZ_BOSS,
                        0, 0, false, 0
                );

        htzBoss = new uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2HTZBossInstance(
                bossSpawn,
                uk.co.jamesj999.sonic.level.LevelManager.getInstance()
        );

        uk.co.jamesj999.sonic.level.LevelManager.getInstance()
                .getObjectManager()
                .addDynamicObject(htzBoss);
    }

    // =========================================================================
    // HTZ Earthquake Helper Methods
    // =========================================================================

    // HTZ Act 1 oscillation constants
    /** Initial Camera_BG_Y_offset when earthquake starts (ROM: 320 = $140) */
    private static final int HTZ1_LAVA_OFFSET_RISEN = 320;
    /** Lower limit of Camera_BG_Y_offset when sinking (ROM: 224 = $E0) */
    private static final int HTZ1_LAVA_OFFSET_SUNKEN = 224;

    // HTZ Act 2 oscillation constants (from s2.asm LevEvents_HTZ2)
    /** Act 2 top route risen limit (ROM: 0x2C0 = 704) */
    private static final int HTZ2_LAVA_OFFSET_RISEN_TOP = 0x2C0;
    /** Act 2 bottom route risen limit (ROM: 0x300 = 768) */
    private static final int HTZ2_LAVA_OFFSET_RISEN_BOTTOM = 0x300;
    /** Act 2 sunken limit (ROM: 0) */
    private static final int HTZ2_LAVA_OFFSET_SUNKEN = 0;
    /** Camera_BG_X_offset for top route / Act 1 (ROM: 0). */
    private static final int HTZ_BG_X_OFFSET_TOP = 0;
    /** Camera_BG_X_offset for Act 2 bottom route (ROM: -$680). */
    private static final int HTZ_BG_X_OFFSET_BOTTOM = -0x680;

    /** Delay frames before direction toggle (ROM: $78 = 120 frames) */
    private static final int HTZ_TERRAIN_DELAY_INIT = 0x78;

    /** Y threshold for HTZ Act 2 zone detection (ROM: $380) */
    private static final int HTZ2_Y_ZONE_THRESHOLD = 0x380;

    /** Act 1: oscillation begins at X >= $1978 */
    private static final int HTZ1_OSCILLATE_START_X = 0x1978;
    /** Act 1: general screen shake disabled at X >= $1E00 */
    private static final int HTZ1_SHAKE_DISABLE_X = 0x1E00;
    /** Act 2 top: oscillation begins at X >= $1678 */
    private static final int HTZ2_TOP_OSCILLATE_START_X = 0x1678;
    /** Act 2 top: general screen shake disabled at X >= $1A00 */
    private static final int HTZ2_TOP_SHAKE_DISABLE_X = 0x1A00;
    /** Act 2 bottom: oscillation begins at X >= $15F0 */
    private static final int HTZ2_BOTTOM_OSCILLATE_START_X = 0x15F0;
    /** Act 2 bottom: general screen shake disabled at X >= $1AC0 */
    private static final int HTZ2_BOTTOM_SHAKE_DISABLE_X = 0x1AC0;

    /**
     * Initialize HTZ earthquake state when entering shake zone.
     * ROM: LevEvents_HTZ_Routine1 lines 20698-20707
     * Uses different oscillation limits for Act 1 vs Act 2 (and top/bottom route).
     *
     * @param risenLimit risen Camera_BG_Y_offset limit for this earthquake zone
     * @param sunkenLimit sunken Camera_BG_Y_offset limit for this earthquake zone
     * @param bgXOffset Camera_BG_X_offset for this earthquake zone
     */
    private void initHtzEarthquake(int risenLimit, int sunkenLimit, int bgXOffset) {
        cameraBgYOffset = risenLimit;
        htzCurrentRisenLimit = risenLimit;
        htzCurrentSunkenLimit = sunkenLimit;
        htzCurrentBgXOffset = bgXOffset;
        htzTerrainSinking = false;
        htzTerrainDelay = 0;
    }

    /**
     * Exit HTZ earthquake area - clear screen shake and reset offset.
     * ROM: LevEvents_HTZ_Routine1_Part2 lines 20714-20724
     */
    private void exitHtzEarthquakeArea() {
        ParallaxManager.getInstance().setHtzScreenShake(false);
        cameraBgYOffset = 0;
        htzTerrainSinking = false;
        htzTerrainDelay = 0;
        htzCurrentBgXOffset = HTZ_BG_X_OFFSET_TOP;
    }

    /**
     * Update HTZ lava oscillation each frame while in earthquake zone.
     * ROM: LevEvents_HTZ_Routine2 lines 20726-20771
     *
     * The lava moves 1 pixel every 4 frames (when frameCounter & 3 == 0).
     * When it reaches the limit, delay counter decrements until 0,
     * then direction toggles and delay resets to 120 frames.
     *
     * Act 1: oscillates between 224-320 (96px range)
     * Act 2 top: oscillates between 0-704
     * Act 2 bottom: oscillates between 0-768
     */
    private void updateHtzLavaOscillation() {
        // Use the limits set by initHtzEarthquake for the current earthquake zone
        int risenLimit = htzCurrentRisenLimit;
        int sunkenLimit = htzCurrentSunkenLimit;

        // ROM: tst.b (HTZ_Terrain_Direction).w / bne.s .sinking
        if (!htzTerrainSinking) {
            // Rising: offset goes toward risen limit
            if (cameraBgYOffset >= risenLimit) {
                // At limit - check delay for direction change
                handleHtzTerrainDelayAndToggle();
            } else {
                // Move every 4 frames: andi.w #3,d0 / bne.s continue
                if ((frameCounter & 3) == 0) {
                    cameraBgYOffset++;
                    // Play rumbling sound every 64 frames
                    if ((frameCounter & 0x3F) == 0) {
                        uk.co.jamesj999.sonic.audio.AudioManager.getInstance().playSfx(
                                Sonic2Sfx.RUMBLING_2.id);
                    }
                }
            }
        } else {
            // Sinking: offset goes toward sunken limit
            if (cameraBgYOffset <= sunkenLimit) {
                // At limit - check delay for direction change
                handleHtzTerrainDelayAndToggle();
            } else {
                // Move every 4 frames
                if ((frameCounter & 3) == 0) {
                    cameraBgYOffset--;
                    // Play rumbling sound every 64 frames
                    if ((frameCounter & 0x3F) == 0) {
                        uk.co.jamesj999.sonic.audio.AudioManager.getInstance().playSfx(
                                Sonic2Sfx.RUMBLING_2.id);
                    }
                }
            }
        }
    }

    /**
     * Handle delay countdown and direction toggle at oscillation limits.
     * ROM: .flip_delay lines 20765-20771
     */
    private void handleHtzTerrainDelayAndToggle() {
        // Disable general screen shaking while at limit (waiting)
        uk.co.jamesj999.sonic.game.GameServices.gameState().setScreenShakeActive(false);
        htzTerrainDelay--;
        if (htzTerrainDelay < 0) {
            // Toggle direction and reset delay
            htzTerrainDelay = HTZ_TERRAIN_DELAY_INIT;
            htzTerrainSinking = !htzTerrainSinking;
            // Re-enable screen shake for movement
            uk.co.jamesj999.sonic.game.GameServices.gameState().setScreenShakeActive(true);
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
        switch (eventRoutineFg) {
            case 0 -> {
                if (camera.getX() >= 0x2680) {
                    camera.setMinX(camera.getX());
                    camera.setMaxYTarget((short) 0x450);
                    eventRoutineFg += 2;
                }
            }
            case 2 -> {
                if (camera.getX() >= 0x2A20) {
                    // ROM locks camera completely at X=0x2A20 for the entire fight
                    camera.setMinX((short) 0x2A20);
                    camera.setMaxX((short) 0x2A20);
                    eventRoutineFg += 2;
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
                    eventRoutineFg += 2;
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance()
                            .playMusic(Sonic2Music.BOSS.id);
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
     *
     * Act 1: No dynamic events
     * Act 2: Boss arena - camera lock, art load, boss spawn
     */
    private void updateMCZ() {
        if (currentAct == 0) {
            // Act 1: No dynamic events (ROM: LevEvents_MCZ1 just returns)
            return;
        }

        // Act 2: Boss arena setup
        // ROM: LevEvents_MCZ2 (s2.asm:21384-21483)
        switch (eventRoutineFg) {
            case 0 -> {
                // Routine 0 (s2.asm LevEvents_MCZ2_Routine1): Wait for camera X >= $2080
                if (camera.getX() >= 0x2080) {
                    // ROM: Set minX to current camera X (prevents backtracking)
                    camera.setMinX(camera.getX());
                    // ROM: Set maxY TARGET to $5D0
                    camera.setMaxYTarget((short) 0x5D0);
                    eventRoutineFg += 2;
                }
            }
            case 2 -> {
                // Routine 1 (s2.asm LevEvents_MCZ2_Routine2): Wait for camera X >= $20F0
                if (camera.getX() >= 0x20F0) {
                    // ROM: Lock camera X boundaries for boss arena
                    camera.setMinX((short) 0x20F0);
                    camera.setMaxX((short) 0x20F0);
                    // Mark boss fight active
                    uk.co.jamesj999.sonic.game.GameServices.gameState().setCurrentBossId(
                        uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds.MCZ_BOSS);
                    eventRoutineFg += 2;
                    bossSpawnDelay = 0;
                    // ROM: Fade out music
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance().fadeOutMusic();
                    // ROM: Set Current_Boss_ID to 5
                    // ROM: Load falling rocks art and MCZ boss PLC/palette
                    // (art loading is handled by Sonic2ObjectArtProvider)
                    // ROM: PalLoad_Now Pal_MCZ_B -> palette line 1 (s2.asm:21447-21448)
                    loadBossPalette(1, Sonic2Constants.PAL_MCZ_BOSS_ADDR);
                }
            }
            case 4 -> {
                // Routine 2 (s2.asm LevEvents_MCZ2_Routine3): Lock floor and spawn boss
                // ROM: Set minY when camera Y reaches $5C8
                if (camera.getY() >= 0x5C8) {
                    camera.setMinY((short) 0x5C8);
                }
                // ROM: Increment delay every frame, spawn boss at $5A (90) frames
                bossSpawnDelay++;
                if (bossSpawnDelay >= 0x5A) {
                    spawnMCZBoss();
                    eventRoutineFg += 2;
                    // Start boss music
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance()
                        .playMusic(Sonic2Music.BOSS.id);
                }
            }
            case 6 -> {
                // Routine 3 (s2.asm LevEvents_MCZ2_Routine4): Boss fight area
                // ROM: Play rumble SFX every $20 frames during screen shake
                if (mczBoss != null && mczBoss.isScreenShaking()) {
                    if ((frameCounter & 0x1F) == 0) {
                        uk.co.jamesj999.sonic.audio.AudioManager.getInstance()
                            .playSfx(Sonic2Sfx.RUMBLING_2.id);
                    }
                }
                // ROM: Update minX to camera X (prevent backtracking)
                camera.setMinX(camera.getX());

                if (mczBoss != null && mczBoss.isDefeated()) {
                    eventRoutineFg += 2;
                }
            }
            default -> {
                // No more routines
            }
        }
    }

    /**
     * Spawns the MCZ Act 2 boss.
     * ROM: Creates Object 0x57 at coordinates (0x21A0, 0x0560)
     */
    private void spawnMCZBoss() {
        uk.co.jamesj999.sonic.level.objects.ObjectSpawn bossSpawn =
            new uk.co.jamesj999.sonic.level.objects.ObjectSpawn(
                0x21A0, 0x0560,
                uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds.MCZ_BOSS,
                0, 0, false, 0
            );
        mczBoss = new uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2MCZBossInstance(
                bossSpawn,
                uk.co.jamesj999.sonic.level.LevelManager.getInstance()
        );
        uk.co.jamesj999.sonic.level.LevelManager.getInstance()
                .getObjectManager()
                .addDynamicObject(mczBoss);
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
        switch (eventRoutineFg) {
            case 0 -> {
                // Routine 1 (s2.asm:21737-21749): Wait for camera X >= $2810
                if (camera.getX() >= 0x2810) {
                    // ROM: Set minX to current camera X (prevent backtracking)
                    camera.setMinX(camera.getX());
                    // ROM: Set maxY TARGET to $400 (boss arena height)
                    camera.setMaxYTarget((short) 0x400);
                    eventRoutineFg += 2;
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
                    eventRoutineFg += 2;
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
                    eventRoutineFg += 2;
                    // Start boss music
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance()
                            .playMusic(Sonic2Music.BOSS.id);
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
        switch (eventRoutineFg) {
            case 0 -> {
                // Routine 1 (s2.asm:21500-21517): Wait for camera X >= $27C0
                if (camera.getX() >= 0x27C0) {
                    // ROM: Set minX to current camera X (prevent backtracking)
                    camera.setMinX(camera.getX());
                    // ROM: Set maxY TARGET to $62E (initial arena height - allows access to floor)
                    // This gets tightened to $5D0 later in routine 4 once fight starts
                    camera.setMaxYTarget((short) 0x62E);
                    eventRoutineFg += 2;
                }
            }
            case 2 -> {
                // Routine 2 (s2.asm:21520-21538): Wait for camera X >= $2890, lock arena
                if (camera.getX() >= 0x2890) {
                    // ROM: Lock camera X boundaries for boss arena
                    camera.setMinX((short) 0x2860);
                    camera.setMaxX((short) 0x28E0);
                    eventRoutineFg += 2;
                    bossSpawnDelay = 0;
                    // ROM: Fade out music
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance().fadeOutMusic();
                    // ROM: Set Current_Boss_ID to 6 (CNZ boss ID in BossCollision_Index)
                    uk.co.jamesj999.sonic.game.GameServices.gameState().setCurrentBossId(6);

                    // ROM: Load CNZ boss palette (Pal_CNZ_B to palette line 1)
                    // This palette contains the electricity effect colors
                    loadBossPalette(1, Sonic2Constants.PAL_CNZ_BOSS_ADDR);

                    // Place boss arena walls by modifying level layout (ROM-accurate)
                    // ROM writes block $F9 (solid wall) to layout offsets $C54 and $C50
                    placeCNZArenaWalls();
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
                    eventRoutineFg += 2;
                    // Start boss music
                    uk.co.jamesj999.sonic.audio.AudioManager.getInstance()
                            .playMusic(Sonic2Music.BOSS.id);
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
     * Places CNZ Act 2 boss arena walls by modifying the level layout.
     * ROM: LevEvents_CNZ2 modifies Level_Layout directly with block $F9 (solid wall).
     * <p>
     * From docs/s2disasm/s2.asm (LevEvents_CNZ2):
     * - Routine 1 (line 21509): move.b #$F9,(Level_Layout+$C54).w  ; left wall
     * - Routine 2 (line 21523): move.b #$F9,(Level_Layout+$C50).w  ; right wall
     * <p>
     * Layout offset calculation (256 tiles wide):
     * - Offset $C54 (3156): x = 3156 % 256 = 84, y = 3156 / 256 = 12
     * - Offset $C50 (3152): x = 3152 % 256 = 80, y = 3152 / 256 = 12
     */
    private void placeCNZArenaWalls() {
        uk.co.jamesj999.sonic.level.LevelManager levelManager =
                uk.co.jamesj999.sonic.level.LevelManager.getInstance();
        Level level = levelManager.getCurrentLevel();
        if (level == null) {
            return;
        }
        uk.co.jamesj999.sonic.level.Map map = level.getMap();
        if (map == null) {
            return;
        }

        // ROM layout offsets for CNZ boss arena walls
        // Block $F9 = solid wall block
        final int WALL_BLOCK = 0xF9;
        final int LAYOUT_WIDTH = 256;  // ROM layout width

        // Left wall: offset $C50 = 3152 (x=80, lower X position)
        final int LEFT_OFFSET = 0xC50;
        cnzLeftWallX = LEFT_OFFSET % LAYOUT_WIDTH;   // 80
        cnzLeftWallY = LEFT_OFFSET / LAYOUT_WIDTH;   // 12

        // Right wall: offset $C54 = 3156 (x=84, higher X position)
        final int RIGHT_OFFSET = 0xC54;
        cnzRightWallX = RIGHT_OFFSET % LAYOUT_WIDTH; // 84
        cnzRightWallY = RIGHT_OFFSET / LAYOUT_WIDTH; // 12

        // Place wall blocks in the layout (layer 0 = foreground)
        try {
            map.setValue(0, cnzLeftWallX, cnzLeftWallY, (byte) WALL_BLOCK);
            map.setValue(0, cnzRightWallX, cnzRightWallY, (byte) WALL_BLOCK);
            // Invalidate the foreground tilemap so changes are rendered
            // This is equivalent to setting Screen_redraw_flag in the original ROM
            levelManager.invalidateForegroundTilemap();
        } catch (IllegalArgumentException e) {
            // Layout dimensions may differ - log and continue
            System.err.println("CNZ wall placement failed: " + e.getMessage());
        }
    }

    /**
     * Removes CNZ Act 2 boss arena wall after defeat.
     * ROM: After boss defeat, removes the RIGHT wall (offset $C54) with block $DD (empty).
     * <p>
     * From docs/s2disasm/s2.asm (line 66296):
     * - move.b #$DD,(Level_Layout+$C54).w
     * <p>
     * This allows Sonic to exit the arena to the right after defeating the boss.
     */
    private void removeCNZArenaWalls() {
        uk.co.jamesj999.sonic.level.LevelManager levelManager =
                uk.co.jamesj999.sonic.level.LevelManager.getInstance();
        Level level = levelManager.getCurrentLevel();
        if (level == null || cnzRightWallX < 0) {
            return;
        }
        uk.co.jamesj999.sonic.level.Map map = level.getMap();
        if (map == null) {
            return;
        }

        // ROM: Removes right wall (offset $C54, x=84) with block $DD after defeat
        final int EMPTY_BLOCK = 0xDD;

        try {
            map.setValue(0, cnzRightWallX, cnzRightWallY, (byte) EMPTY_BLOCK);
            // Invalidate the foreground tilemap so changes are rendered
            // ROM sets Screen_redraw_flag at line 66297
            levelManager.invalidateForegroundTilemap();
        } catch (IllegalArgumentException e) {
            // Layout dimensions may differ - ignore
        }
    }

    /**
     * Called by Sonic2CNZBossInstance when the CNZ boss is defeated.
     * ROM timing: Called during transition from defeat exploding to defeat bounce phase.
     */
    public void onCNZBossDefeated() {
        removeCNZArenaWalls();
    }

    // =========================================================================
    // OOZ Oil Surface State
    // =========================================================================
    private OilSurfaceManager oilManager;

    /**
     * Oil Ocean Zone events.
     * ROM: LevEvents_OOZ (s2.asm:20938-21035)
     * Also handles oil surface (Obj07) and oil slides (OilSlides routine).
     */
    private void updateOOZ() {
        if (oilManager != null) {
            var player = camera.getFocusedSprite();
            if (player != null) {
                oilManager.update(player);
            }
        }
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
        return eventRoutineFg;
    }

    public void setEventRoutine(int routine) {
        this.eventRoutineFg = routine;
    }

    /**
     * Resets mutable state without destroying the singleton instance.
     */
    public void resetState() {
        initLevel(-1, -1);
    }

    /**
     * Loads a boss palette from ROM and applies it to the specified palette line.
     * ROM equivalent: PalLoad_Now (s2.asm)
     */
    private void loadBossPalette(int paletteLine, int romAddr) {
        try {
            byte[] paletteData = GameServices.rom().getRom().readBytes(romAddr, PALETTE_LINE_SIZE);
            uk.co.jamesj999.sonic.level.LevelManager.getInstance().updatePalette(paletteLine, paletteData);
        } catch (Exception e) {
            LOGGER.warning("Failed to load boss palette from ROM offset 0x" +
                    Integer.toHexString(romAddr) + ": " + e.getMessage());
        }
    }

    public static synchronized Sonic2LevelEventManager getInstance() {
        if (instance == null) {
            instance = new Sonic2LevelEventManager();
        }
        return instance;
    }
}
