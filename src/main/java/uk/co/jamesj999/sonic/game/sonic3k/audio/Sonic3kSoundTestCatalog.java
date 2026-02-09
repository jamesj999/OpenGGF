package uk.co.jamesj999.sonic.game.sonic3k.audio;

import uk.co.jamesj999.sonic.audio.debug.SoundTestCatalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Sound test catalog for Sonic 3 &amp; Knuckles. Music titles from S3K disassembly,
 * SFX names from known S3K SFX labels.
 */
public final class Sonic3kSoundTestCatalog implements SoundTestCatalog {
    private static final Sonic3kSoundTestCatalog INSTANCE = new Sonic3kSoundTestCatalog();
    private static final Map<Integer, String> TITLE_MAP = buildTitleMap();
    private static final NavigableSet<Integer> VALID_SONGS = new TreeSet<>(TITLE_MAP.keySet());
    private static final Map<Integer, String> SFX_NAMES = buildSfxNames();

    private Sonic3kSoundTestCatalog() {
    }

    public static Sonic3kSoundTestCatalog getInstance() {
        return INSTANCE;
    }

    @Override
    public String lookupTitle(int songId) {
        return TITLE_MAP.get(songId);
    }

    @Override
    public NavigableSet<Integer> getValidSongs() {
        return Collections.unmodifiableNavigableSet(VALID_SONGS);
    }

    @Override
    public Map<Integer, String> getSfxNames() {
        return Collections.unmodifiableMap(SFX_NAMES);
    }

    @Override
    public int getDefaultSongId() {
        return Sonic3kAudioConstants.MUS_AIZ1;
    }

    @Override
    public int getSfxIdBase() {
        return Sonic3kAudioConstants.SFX_ID_BASE;
    }

    @Override
    public int getSfxIdMax() {
        return Sonic3kAudioConstants.SFX_ID_MAX;
    }

    @Override
    public String getGameName() {
        return "Sonic 3&K";
    }

    private static Map<Integer, String> buildTitleMap() {
        Map<Integer, String> m = new LinkedHashMap<>();
        m.put(Sonic3kAudioConstants.MUS_AIZ1, "Angel Island Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_AIZ2, "Angel Island Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_HCZ1, "Hydrocity Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_HCZ2, "Hydrocity Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_MGZ1, "Marble Garden Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_MGZ2, "Marble Garden Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_CNZ1, "Carnival Night Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_CNZ2, "Carnival Night Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_FBZ1, "Flying Battery Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_FBZ2, "Flying Battery Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_ICZ1, "IceCap Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_ICZ2, "IceCap Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_LBZ1, "Launch Base Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_LBZ2, "Launch Base Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_MHZ1, "Mushroom Hill Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_MHZ2, "Mushroom Hill Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_SOZ1, "Sandopolis Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_SOZ2, "Sandopolis Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_LRZ1, "Lava Reef Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_LRZ2, "Lava Reef Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_SSZ, "Sky Sanctuary Zone");
        m.put(Sonic3kAudioConstants.MUS_DEZ1, "Death Egg Zone Act 1");
        m.put(Sonic3kAudioConstants.MUS_DEZ2, "Death Egg Zone Act 2");
        m.put(Sonic3kAudioConstants.MUS_MINIBOSS, "Mini-Boss");
        m.put(Sonic3kAudioConstants.MUS_BOSS, "Boss");
        m.put(Sonic3kAudioConstants.MUS_DDZ, "Doomsday Zone");
        m.put(Sonic3kAudioConstants.MUS_PACHINKO, "Bonus Stage - Pachinko");
        m.put(Sonic3kAudioConstants.MUS_SPECIAL_STAGE, "Special Stage");
        m.put(Sonic3kAudioConstants.MUS_SLOTS, "Bonus Stage - Slots");
        m.put(Sonic3kAudioConstants.MUS_GUMBALL, "Bonus Stage - Gumball");
        m.put(Sonic3kAudioConstants.MUS_KNUCKLES, "Knuckles' Theme");
        m.put(Sonic3kAudioConstants.MUS_AZURE_LAKE, "Azure Lake (Competition)");
        m.put(Sonic3kAudioConstants.MUS_BALLOON_PARK, "Balloon Park (Competition)");
        m.put(Sonic3kAudioConstants.MUS_DESERT_PALACE, "Desert Palace (Competition)");
        m.put(Sonic3kAudioConstants.MUS_CHROME_GADGET, "Chrome Gadget (Competition)");
        m.put(Sonic3kAudioConstants.MUS_ENDLESS_MINE, "Endless Mine (Competition)");
        m.put(Sonic3kAudioConstants.MUS_TITLE, "Title Screen");
        m.put(Sonic3kAudioConstants.MUS_CREDITS_S3, "Credits (Sonic 3)");
        m.put(Sonic3kAudioConstants.MUS_GAME_OVER, "Game Over");
        m.put(Sonic3kAudioConstants.MUS_CONTINUE, "Continue");
        m.put(Sonic3kAudioConstants.MUS_ACT_CLEAR, "Act Clear");
        m.put(Sonic3kAudioConstants.MUS_EXTRA_LIFE, "Extra Life");
        m.put(Sonic3kAudioConstants.MUS_EMERALD, "Chaos Emerald");
        m.put(Sonic3kAudioConstants.MUS_INVINCIBILITY, "Invincibility");
        m.put(Sonic3kAudioConstants.MUS_COMPETITION_MENU, "Competition Menu");
        m.put(Sonic3kAudioConstants.MUS_MINIBOSS_S3, "Mini-Boss (Sonic 3)");
        m.put(Sonic3kAudioConstants.MUS_DATA_SELECT, "Data Select");
        m.put(Sonic3kAudioConstants.MUS_FINAL_BOSS, "Final Boss");
        m.put(Sonic3kAudioConstants.MUS_DROWNING, "Drowning");
        m.put(Sonic3kAudioConstants.MUS_ENDING, "Ending");
        m.put(Sonic3kAudioConstants.MUS_CREDITS_SK, "Credits (S&K)");

        // S3-specific track variants (from the S3 driver in the combined ROM)
        m.put(Sonic3kAudioConstants.MUS_ICZ1_S3, "IceCap Zone Act 1 (S3)");
        m.put(Sonic3kAudioConstants.MUS_ICZ2_S3, "IceCap Zone Act 2 (S3)");
        m.put(Sonic3kAudioConstants.MUS_LBZ1_S3, "Launch Base Zone Act 1 (S3)");
        m.put(Sonic3kAudioConstants.MUS_LBZ2_S3, "Launch Base Zone Act 2 (S3)");
        m.put(Sonic3kAudioConstants.MUS_KNUCKLES_S3, "Knuckles' Theme (S3)");
        m.put(Sonic3kAudioConstants.MUS_TITLE_S3, "Title Screen (S3)");
        m.put(Sonic3kAudioConstants.MUS_CREDITS_S3_ALT, "Credits (S3)");
        m.put(Sonic3kAudioConstants.MUS_ACT_CLEAR_S3, "Act Clear (S3)");
        m.put(Sonic3kAudioConstants.MUS_EXTRA_LIFE_S3, "Extra Life (S3)");
        m.put(Sonic3kAudioConstants.MUS_INVINCIBILITY_S3, "Invincibility (S3)");
        m.put(Sonic3kAudioConstants.MUS_COMPETITION_MENU_S3, "Competition Menu (S3)");
        m.put(Sonic3kAudioConstants.MUS_MINIBOSS_S3_ALT, "Mini-Boss (S3)");
        m.put(Sonic3kAudioConstants.MUS_FINAL_BOSS_S3, "Final Boss (S3)");
        return m;
    }

    private static Map<Integer, String> buildSfxNames() {
        // Native 68K queue IDs 0x33-0xDB (Z80 SFX table indices 0-168).
        // Names from sonic3k.constants.asm sfx labels.
        Map<Integer, String> m = new LinkedHashMap<>();
        m.put(0x33, "Ring (Right)");         // sfx_RingRight
        m.put(0x34, "Ring (Left)");          // sfx_RingLeft
        m.put(0x35, "Death");                // sfx_Death
        m.put(0x36, "Skid");                 // sfx_Skid
        m.put(0x37, "Spike Hit");            // sfx_SpikeHit
        m.put(0x38, "Bubble");               // sfx_Bubble
        m.put(0x39, "Splash");               // sfx_Splash
        m.put(0x3A, "Shield");               // sfx_Shield
        m.put(0x3B, "Drown");                // sfx_Drown
        m.put(0x3C, "Roll");                 // sfx_Roll
        m.put(0x3D, "Break");                // sfx_Break
        m.put(0x3E, "Fire Shield");          // sfx_FireShield
        m.put(0x3F, "Bubble Shield");        // sfx_BubbleShield
        m.put(0x40, "Unknown Shield");       // sfx_UnknownShield
        m.put(0x41, "Lightning Shield");     // sfx_LightningShield
        m.put(0x42, "Insta-Shield");         // sfx_InstaAttack
        m.put(0x43, "Fire Attack");          // sfx_FireAttack
        m.put(0x44, "Bubble Attack");        // sfx_BubbleAttack
        m.put(0x45, "Electric Attack");      // sfx_ElectricAttack
        m.put(0x46, "Whistle");              // sfx_Whistle
        m.put(0x47, "Sandwall Rise");        // sfx_SandwallRise
        m.put(0x48, "Blast");                // sfx_Blast
        m.put(0x49, "Thump");                // sfx_Thump
        m.put(0x4A, "Grab");                 // sfx_Grab
        m.put(0x4B, "Waterfall Splash");     // sfx_WaterfallSplash
        m.put(0x4C, "Glide Land");           // sfx_GlideLand
        m.put(0x4D, "Projectile");           // sfx_Projectile
        m.put(0x4E, "Missile Explode");      // sfx_MissileExplode
        m.put(0x4F, "Flamethrower (Quiet)"); // sfx_FlamethrowerQuiet
        m.put(0x50, "Boss Activate");        // sfx_BossActivate
        m.put(0x51, "Missile Throw");        // sfx_MissileThrow
        m.put(0x52, "Spike Move");           // sfx_SpikeMove
        m.put(0x53, "Charging");             // sfx_Charging
        m.put(0x54, "Boss Laser");           // sfx_BossLaser
        m.put(0x55, "Block Conveyor");       // sfx_BlockConveyor
        m.put(0x56, "Flip Bridge");          // sfx_FlipBridge
        m.put(0x57, "Geyser");               // sfx_Geyser
        m.put(0x58, "Fan Latch");            // sfx_FanLatch
        m.put(0x59, "Collapse");             // sfx_Collapse
        m.put(0x5A, "Unknown Charge");       // sfx_UnknownCharge
        m.put(0x5B, "Switch");               // sfx_Switch
        m.put(0x5C, "Mecha Spark");          // sfx_MechaSpark
        m.put(0x5D, "Floor Thump");          // sfx_FloorThump
        m.put(0x5E, "Laser");                // sfx_Laser
        m.put(0x5F, "Crash");                // sfx_Crash
        m.put(0x60, "Boss Zoom");            // sfx_BossZoom
        m.put(0x61, "Boss Hit Floor");       // sfx_BossHitFloor
        m.put(0x62, "Jump");                 // sfx_Jump
        m.put(0x63, "Starpost");             // sfx_Starpost
        m.put(0x64, "Pulley Grab");          // sfx_PulleyGrab
        m.put(0x65, "Blue Sphere");          // sfx_BlueSphere
        m.put(0x66, "All Spheres");          // sfx_AllSpheres
        m.put(0x67, "Level Projectile");     // sfx_LevelProjectile
        m.put(0x68, "Perfect");              // sfx_Perfect
        m.put(0x69, "Push Block");           // sfx_PushBlock
        m.put(0x6A, "Goal");                 // sfx_Goal
        m.put(0x6B, "Action Block");         // sfx_ActionBlock
        m.put(0x6C, "Splash 2");             // sfx_Splash2
        m.put(0x6D, "Unknown Shift");        // sfx_UnknownShift
        m.put(0x6E, "Boss Hit");             // sfx_BossHit
        m.put(0x6F, "Rumble 2");             // sfx_Rumble2
        m.put(0x70, "Lava Ball");            // sfx_LavaBall
        m.put(0x71, "Shield 2");             // sfx_Shield2
        m.put(0x72, "Hoverpad");             // sfx_Hoverpad
        m.put(0x73, "Transporter");          // sfx_Transporter
        m.put(0x74, "Tunnel Booster");       // sfx_TunnelBooster
        m.put(0x75, "Balloon Platform");     // sfx_BalloonPlatform
        m.put(0x76, "Trap Door");            // sfx_TrapDoor
        m.put(0x77, "Balloon");              // sfx_Balloon
        m.put(0x78, "Gravity Machine");      // sfx_GravityMachine
        m.put(0x79, "Lightning");            // sfx_Lightning
        m.put(0x7A, "Boss Magma");           // sfx_BossMagma
        m.put(0x7B, "Small Bumpers");        // sfx_SmallBumpers
        m.put(0x7C, "Chain Tension");        // sfx_ChainTension
        m.put(0x7D, "Unknown Pump");         // sfx_UnknownPump
        m.put(0x7E, "Ground Slide");         // sfx_GroundSlide
        m.put(0x7F, "Frost Puff");           // sfx_FrostPuff
        m.put(0x80, "Ice Spikes");           // sfx_IceSpikes
        m.put(0x81, "Tube Launcher");        // sfx_TubeLauncher
        m.put(0x82, "Sand Splash");          // sfx_SandSplash
        m.put(0x83, "Bridge Collapse");      // sfx_BridgeCollapse
        m.put(0x84, "Unknown Power Up");     // sfx_UnknownPowerUp
        m.put(0x85, "Unknown Power Down");   // sfx_UnknownPowerDown
        m.put(0x86, "Alarm");                // sfx_Alarm
        m.put(0x87, "Mushroom Bounce");      // sfx_MushroomBounce
        m.put(0x88, "Pulley Move");          // sfx_PulleyMove
        m.put(0x89, "Weather Machine");      // sfx_WeatherMachine
        m.put(0x8A, "Bouncy");               // sfx_Bouncy
        m.put(0x8B, "Chop Tree");            // sfx_ChopTree
        m.put(0x8C, "Chop Stuck");           // sfx_ChopStuck
        m.put(0x8D, "Unknown Flutter");      // sfx_UnknownFlutter
        m.put(0x8E, "Unknown Revving");      // sfx_UnknownRevving
        m.put(0x8F, "Door Open");            // sfx_DoorOpen
        m.put(0x90, "Door Move");            // sfx_DoorMove
        m.put(0x91, "Door Close");           // sfx_DoorClose
        m.put(0x92, "Ghost Appear");         // sfx_GhostAppear
        m.put(0x93, "Boss Recovery");        // sfx_BossRecovery
        m.put(0x94, "Chain Tick");           // sfx_ChainTick
        m.put(0x95, "Boss Hand");            // sfx_BossHand
        m.put(0x96, "Mecha Land");           // sfx_MechaLand
        m.put(0x97, "Enemy Breath");         // sfx_EnemyBreath
        m.put(0x98, "Boss Projectile");      // sfx_BossProjectile
        m.put(0x99, "Unknown Plink");        // sfx_UnknownPlink
        m.put(0x9A, "Spring Latch");         // sfx_SpringLatch
        m.put(0x9B, "Thump Boss");           // sfx_ThumpBoss
        m.put(0x9C, "Super Emerald");        // sfx_SuperEmerald
        m.put(0x9D, "Targeting");            // sfx_Targeting
        m.put(0x9E, "Clank");                // sfx_Clank
        m.put(0x9F, "Super Transform");      // sfx_SuperTransform
        m.put(0xA0, "Missile Shoot");        // sfx_MissileShoot
        m.put(0xA1, "Unknown Ominous");      // sfx_UnknownOminous
        m.put(0xA2, "Floor Launcher");       // sfx_FloorLauncher
        m.put(0xA3, "Gravity Lift");         // sfx_GravityLift
        m.put(0xA4, "Mecha Transform");      // sfx_MechaTransform
        m.put(0xA5, "Unknown Rise");         // sfx_UnknownRise
        m.put(0xA6, "Launch Grab");          // sfx_LaunchGrab
        m.put(0xA7, "Launch Ready");         // sfx_LaunchReady
        m.put(0xA8, "Energy Zap");           // sfx_EnergyZap
        m.put(0xA9, "Air Ding");             // sfx_AirDing
        m.put(0xAA, "Bumper");               // sfx_Bumper
        m.put(0xAB, "Spindash");             // sfx_Spindash
        m.put(0xAC, "Continue");             // sfx_Continue
        m.put(0xAD, "Launch Go");            // sfx_LaunchGo
        m.put(0xAE, "Flipper");              // sfx_Flipper
        m.put(0xAF, "Enter SS");             // sfx_EnterSS
        m.put(0xB0, "Register");             // sfx_Register
        m.put(0xB1, "Spring");               // sfx_Spring
        m.put(0xB2, "Error");                // sfx_Error
        m.put(0xB3, "Big Ring");             // sfx_BigRing
        m.put(0xB4, "Explode");              // sfx_Explode
        m.put(0xB5, "Diamonds");             // sfx_Diamonds
        m.put(0xB6, "Dash");                 // sfx_Dash
        m.put(0xB7, "Slot Machine");         // sfx_SlotMachine
        m.put(0xB8, "Signpost");             // sfx_Signpost
        m.put(0xB9, "Ring Loss");            // sfx_RingLoss
        m.put(0xBA, "Flying");               // sfx_Flying
        m.put(0xBB, "Fly Tired");            // sfx_FlyTired
        // --- Continuous SFX (cfx_*) ---
        m.put(0xBC, "Slide Skid (Loud)");    // sfx_SlideSkidLoud
        m.put(0xBD, "Large Ship");           // sfx_LargeShip
        m.put(0xBE, "Robotnik Siren");       // sfx_RobotnikSiren
        m.put(0xBF, "Boss Rotate");          // sfx_BossRotate
        m.put(0xC0, "Fan (Big)");            // sfx_FanBig
        m.put(0xC1, "Fan (Small)");          // sfx_FanSmall
        m.put(0xC2, "Flamethrower (Loud)");  // sfx_FlamethrowerLoud
        m.put(0xC3, "Gravity Tunnel");       // sfx_GravityTunnel
        m.put(0xC4, "Boss Panic");           // sfx_BossPanic
        m.put(0xC5, "Unknown Spin");         // sfx_UnknownSpin
        m.put(0xC6, "Wave Hover");           // sfx_WaveHover
        m.put(0xC7, "Cannon Turn");          // sfx_CannonTurn
        m.put(0xC8, "Slide Skid (Quiet)");   // sfx_SlideSkidQuiet
        m.put(0xC9, "Spike Balls");          // sfx_SpikeBalls
        m.put(0xCA, "Light Tunnel");         // sfx_LightTunnel
        m.put(0xCB, "Rumble");               // sfx_Rumble
        m.put(0xCC, "Big Rumble");           // sfx_BigRumble
        m.put(0xCD, "Death Egg Rise (Loud)"); // sfx_DeathEggRiseLoud
        m.put(0xCE, "Wind (Quiet)");         // sfx_WindQuiet
        m.put(0xCF, "Wind (Loud)");          // sfx_WindLoud
        m.put(0xD0, "Rising");               // sfx_Rising
        m.put(0xD1, "Unknown Flutter 2");    // sfx_UnknownFlutter2
        m.put(0xD2, "Gumball Tab");          // sfx_GumballTab
        m.put(0xD3, "Death Egg Rise (Quiet)"); // sfx_DeathEggRiseQuiet
        m.put(0xD4, "Turbine Hum");          // sfx_TurbineHum
        m.put(0xD5, "Lava Fall");            // sfx_LavaFall
        m.put(0xD6, "Unknown Zap");          // sfx_UnknownZap
        m.put(0xD7, "Conveyor Platform");    // sfx_ConveyorPlatform
        m.put(0xD8, "Unknown Saw");          // sfx_UnknownSaw
        m.put(0xD9, "Magnetic Spike");       // sfx_MagneticSpike
        m.put(0xDA, "Leaf Blower");          // sfx_LeafBlower
        m.put(0xDB, "Water Skid");           // sfx_WaterSkid
        return m;
    }
}
