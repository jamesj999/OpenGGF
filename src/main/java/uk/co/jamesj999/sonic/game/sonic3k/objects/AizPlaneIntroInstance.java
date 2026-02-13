package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Object for the AIZ1 intro cinematic - master state machine.
 *
 * Disassembly reference: sonic3k.asm Obj_intPlane (loc_67490 onward).
 *
 * The ROM uses stride-2 routine dispatch (0x00, 0x02, 0x04, ..., 0x18).
 * We mirror that convention directly so routine values match the disassembly.
 *
 * This is a SKELETON state machine. Art loading, child object spawning,
 * and rendering are handled in later tasks. For now, appendRenderCommands
 * is a no-op and per-routine methods contain only state transition logic.
 *
 * Routine overview:
 *   0x00 - Init: set position, lock player, init palette cycler, start wait timer
 *   0x02 - Descend: plane descends with deceleration, player locked on plane
 *   0x04 - Swing: pendulum swing motion after descent
 *   0x06 - H-decel: horizontal deceleration after swing ends
 *   0x08 - H-stop: horizontal velocity reaches zero, wait period
 *   0x0A - Timer+waves: countdown timer, spawn wave children periodically
 *   0x0C - Walk right: Sonic walks right as Super Sonic
 *   0x0E - Flash: Super Sonic palette flash / transformation visual
 *   0x10 - Walk left: Sonic walks left toward plane
 *   0x12 - Pause: brief pause before Knuckles arrives
 *   0x14 - Monitor Knux: watch for Knuckles X position trigger
 *   0x16 - Monitor adjust: plane adjusts position as Knuckles approaches
 *   0x18 - Monitor explode: explosion trigger, emeralds scatter, cleanup
 */
public class AizPlaneIntroInstance extends AbstractObjectInstance {

    private static final Logger LOG = Logger.getLogger(AizPlaneIntroInstance.class.getName());

    // -----------------------------------------------------------------------
    // Constants from ROM (sonic3k.asm Obj_intPlane)
    // -----------------------------------------------------------------------

    /** Frames between wave child spawns during routine 0x0A. */
    static final int WAVE_SPAWN_INTERVAL = 5;

    /** X coordinate at which Knuckles is spawned (routine 0x14 trigger). */
    static final int KNUCKLES_SPAWN_X = 0x918;

    /** X coordinate for plane horizontal adjustment (routine 0x16). */
    static final int PLANE_ADJUST_X = 0x1240;

    /** X coordinate at which explosion is triggered (routine 0x18). */
    static final int EXPLOSION_TRIGGER_X = 0x13D0;

    /** Target X for Sonic's rightward walk (routine 0x0C). */
    static final int WALK_RIGHT_TARGET = 0x200;

    /** Target X for Sonic's leftward walk (routine 0x10). */
    static final int WALK_LEFT_TARGET = 0x120;

    /** Pixels per frame for Sonic's walk during cutscene. */
    static final int WALK_SPEED = 4;

    /** Y velocity deceleration per frame during descent (subpixels). */
    static final int DESCENT_DECEL = 0x18;

    /** X velocity deceleration per frame during H-decel (subpixels). */
    static final int HORIZ_DECEL = 0x40;

    /** Y coordinate of the ground level. */
    static final int GROUND_Y = 0x130;

    /** Initial wait timer value for routine 0x00. */
    static final int INIT_WAIT_TIMER = 0x40;

    /** Screen scroll speed value ($40 from ROM). */
    static final int SCROLL_SPEED = 8;

    // -----------------------------------------------------------------------
    // Mutable state
    // -----------------------------------------------------------------------

    private int currentX;
    private int currentY;

    /** Routine counter (stride-2: 0, 2, 4, ..., 24). */
    private int routine;

    /** X velocity in subpixels (256 = 1 pixel). */
    private int xVel;

    /** Y velocity in subpixels (256 = 1 pixel). */
    private int yVel;

    /** General-purpose countdown timer. */
    private int waitTimer;

    /** Wave spawn countdown (resets to WAVE_SPAWN_INTERVAL). */
    private int waveTimer;

    /** Secondary timer ($3A in ROM) for swing/flash sequences. */
    private int secondaryTimer;

    /** Swing direction flag for pendulum motion. */
    private boolean swingDirectionDown;

    /** Palette cycler for Super Sonic visual effect (routines 0x0C+). */
    private final AizIntroPaletteCycler paletteCycler;

    /** Whether this object currently owns player control lock. */
    private boolean ownsPlayerControl;

    /** Reference to the plane child sprite (biplane visual). */
    private AizIntroPlaneChild planeChild;

    /** Reference to the Knuckles cutscene object. */
    private CutsceneKnucklesAiz1Instance knuckles;

    /** List of active wave children for cleanup. */
    private final ArrayList<AizIntroWaveChild> activeWaves = new ArrayList<>();

    /** List of scattered emeralds. */
    private final ArrayList<AizEmeraldScatterInstance> emeralds = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public AizPlaneIntroInstance(ObjectSpawn spawn) {
        super(spawn, "AIZPlaneIntro");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.routine = 0;
        this.xVel = 0;
        this.yVel = 0;
        this.waitTimer = 0;
        this.waveTimer = 0;
        this.secondaryTimer = 0;
        this.swingDirectionDown = false;
        this.paletteCycler = new AizIntroPaletteCycler();
        this.ownsPlayerControl = false;
    }

    // -----------------------------------------------------------------------
    // ObjectInstance interface
    // -----------------------------------------------------------------------

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        switch (routine) {
            case 0  -> routine0Init(player);
            case 2  -> routine2Descend(player);
            case 4  -> routine4Swing(player);
            case 6  -> routine6HDecel(player);
            case 8  -> routine8HStop(player);
            case 10 -> routine10TimerWaves(frameCounter, player);
            case 12 -> routine12WalkRight(frameCounter, player);
            case 14 -> routine14Flash(frameCounter, player);
            case 16 -> routine16WalkLeft(frameCounter, player);
            case 18 -> routine18Pause(frameCounter, player);
            case 20 -> routine20MonitorKnux(frameCounter, player);
            case 22 -> routine22MonitorAdjust(frameCounter, player);
            case 24 -> routine24MonitorExplode(frameCounter, player);
            default -> {
                // Invalid routine - no-op
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // No-op: art loading and rendering handled in later tasks.
    }

    @Override
    public void onUnload() {
        // Release player control if we own it.
        // Player reference not available here without Camera singleton,
        // which may not be initialized in test. Guard appropriately.
        ownsPlayerControl = false;
        activeWaves.clear();
        emeralds.clear();
    }

    // -----------------------------------------------------------------------
    // Routine accessors (for test and external use)
    // -----------------------------------------------------------------------

    public int getRoutine() {
        return routine;
    }

    public void advanceRoutine() {
        routine += 2;
    }

    // -----------------------------------------------------------------------
    // Dynamic object spawning helper
    // -----------------------------------------------------------------------

    /**
     * Spawns a dynamic object into the level's object manager.
     * Silently no-ops when LevelManager or ObjectManager is unavailable
     * (e.g. in unit test environments without OpenGL).
     */
    private void spawnDynamicObject(AbstractObjectInstance object) {
        try {
            LevelManager lm = LevelManager.getInstance();
            if (lm != null && lm.getObjectManager() != null) {
                lm.getObjectManager().addDynamicObject(object);
            }
        } catch (Exception e) {
            // LevelManager may not be available in test environments
            LOG.fine("Could not spawn dynamic object (test env?): " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Routine 0x00: Init
    // -----------------------------------------------------------------------

    private void routine0Init(AbstractPlayableSprite player) {
        LOG.fine("Routine 0: initializing intro sequence");

        waitTimer = INIT_WAIT_TIMER;
        waveTimer = WAVE_SPAWN_INTERVAL;
        paletteCycler.init();

        // Lock player control for the duration of the intro.
        if (player != null) {
            player.setControlLocked(true);
            player.setObjectControlled(true);
            ownsPlayerControl = true;
        }

        // Load all intro art (plane, emeralds, waves, Knuckles).
        try {
            AizIntroArtLoader.loadAllIntroArt();
        } catch (Exception e) {
            LOG.fine("Could not load intro art (test env?): " + e.getMessage());
        }

        // Spawn the plane child (biplane visual).
        ObjectSpawn planeSpawn = new ObjectSpawn(
                currentX - 0x22, currentY + 0x2C, 0, 0, 0, false, 0);
        planeChild = new AizIntroPlaneChild(planeSpawn, this);
        spawnDynamicObject(planeChild);

        // Spawn two emerald glow children attached to the plane.
        ObjectSpawn glow1Spawn = new ObjectSpawn(currentX, currentY, 0, 0, 0, false, 0);
        AizIntroEmeraldGlowChild glow1 = new AizIntroEmeraldGlowChild(glow1Spawn, planeChild, -8, -12);
        ObjectSpawn glow2Spawn = new ObjectSpawn(currentX, currentY, 0, 0, 0, false, 0);
        AizIntroEmeraldGlowChild glow2 = new AizIntroEmeraldGlowChild(glow2Spawn, planeChild, 8, -12);
        planeChild.setGlowChildren(glow1, glow2);
        spawnDynamicObject(glow1);
        spawnDynamicObject(glow2);

        // Set initial descent velocity.
        xVel = 0x800;
        yVel = 0x400;

        advanceRoutine();
    }

    // -----------------------------------------------------------------------
    // Routine 0x02: Descend
    // -----------------------------------------------------------------------

    private void routine2Descend(AbstractPlayableSprite player) {
        // Plane descends with decelerating Y velocity.
        if (yVel > 0) {
            yVel -= DESCENT_DECEL;
            if (yVel < 0) {
                yVel = 0;
            }
        }

        // Apply velocity to position (subpixel -> pixel).
        currentY += yVel >> 8;

        // Check if we've reached ground level.
        if (currentY >= GROUND_Y) {
            currentY = GROUND_Y;
            yVel = 0;
            LOG.fine("Routine 2: descent complete, advancing to swing");
            advanceRoutine();
        }

        // TODO: Update plane child position to match
        // TODO: Keep player locked on plane surface
    }

    // -----------------------------------------------------------------------
    // Routine 0x04: Swing (pendulum motion)
    // -----------------------------------------------------------------------

    private void routine4Swing(AbstractPlayableSprite player) {
        // Pendulum swing after landing - oscillates plane visually.
        secondaryTimer++;

        // TODO: Apply SwingMotion utility for pendulum angle calculation
        // TODO: Update plane sprite angle

        // Swing completes after a set number of oscillation frames.
        // The exact threshold comes from SwingMotion convergence.
        if (secondaryTimer >= 0x40) {
            secondaryTimer = 0;
            LOG.fine("Routine 4: swing complete, advancing to H-decel");
            advanceRoutine();
        }
    }

    // -----------------------------------------------------------------------
    // Routine 0x06: Horizontal deceleration
    // -----------------------------------------------------------------------

    private void routine6HDecel(AbstractPlayableSprite player) {
        // Decelerate horizontal velocity toward zero.
        if (xVel > 0) {
            xVel -= HORIZ_DECEL;
            if (xVel < 0) {
                xVel = 0;
            }
        } else if (xVel < 0) {
            xVel += HORIZ_DECEL;
            if (xVel > 0) {
                xVel = 0;
            }
        }

        currentX += xVel >> 8;

        if (xVel == 0) {
            LOG.fine("Routine 6: H-decel complete, advancing to H-stop");
            advanceRoutine();
        }
    }

    // -----------------------------------------------------------------------
    // Routine 0x08: Horizontal stop / brief wait
    // -----------------------------------------------------------------------

    private void routine8HStop(AbstractPlayableSprite player) {
        waitTimer--;
        if (waitTimer <= 0) {
            waitTimer = 0;
            LOG.fine("Routine 8: wait complete, advancing to timer+waves");
            advanceRoutine();
            waveTimer = WAVE_SPAWN_INTERVAL;
        }
    }

    // -----------------------------------------------------------------------
    // Routine 0x0A: Timer countdown + wave spawning
    // -----------------------------------------------------------------------

    private void routine10TimerWaves(int frameCounter, AbstractPlayableSprite player) {
        // Spawn wave children at regular intervals.
        waveTimer--;
        if (waveTimer <= 0) {
            waveTimer = WAVE_SPAWN_INTERVAL;

            // Only spawn waves when the plane is sufficiently on-screen.
            if (currentX >= 0x80) {
                ObjectSpawn waveSpawn = new ObjectSpawn(
                        currentX, currentY + 0x18, 0, 0, 0, false, 0);
                AizIntroWaveChild wave = new AizIntroWaveChild(waveSpawn, SCROLL_SPEED);
                spawnDynamicObject(wave);
                activeWaves.add(wave);
            }
        }

        waitTimer--;
        if (waitTimer <= 0) {
            waitTimer = 0;
            LOG.fine("Routine 10: timer+waves complete, advancing to walk right");
            advanceRoutine();
        }
    }

    // -----------------------------------------------------------------------
    // Routine 0x0C: Sonic walks right (as Super Sonic)
    // -----------------------------------------------------------------------

    private void routine12WalkRight(int frameCounter, AbstractPlayableSprite player) {
        paletteCycler.advance();

        if (player != null) {
            int playerX = player.getCentreX();
            if (playerX < WALK_RIGHT_TARGET) {
                // TODO: Set player walking-right animation
                // TODO: Move player rightward at WALK_SPEED
            } else {
                LOG.fine("Routine 12: walk right complete, advancing to flash");
                advanceRoutine();
            }
        } else {
            // No player in test mode - advance immediately.
            advanceRoutine();
        }
    }

    // -----------------------------------------------------------------------
    // Routine 0x0E: Flash / Super Sonic transformation visual
    // -----------------------------------------------------------------------

    private void routine14Flash(int frameCounter, AbstractPlayableSprite player) {
        paletteCycler.advance();

        secondaryTimer++;

        // TODO: Apply palette flash effect (white-out / fade)
        // TODO: Update Super Sonic mapping frame via paletteCycler.getMappingFrame()

        if (secondaryTimer >= 0x20) {
            secondaryTimer = 0;
            LOG.fine("Routine 14: flash complete, advancing to walk left");
            advanceRoutine();
        }
    }

    // -----------------------------------------------------------------------
    // Routine 0x10: Sonic walks left toward plane
    // -----------------------------------------------------------------------

    private void routine16WalkLeft(int frameCounter, AbstractPlayableSprite player) {
        paletteCycler.advance();

        if (player != null) {
            int playerX = player.getCentreX();
            if (playerX > WALK_LEFT_TARGET) {
                // TODO: Set player walking-left animation
                // TODO: Move player leftward at WALK_SPEED
            } else {
                LOG.fine("Routine 16: walk left complete, advancing to pause");
                advanceRoutine();
            }
        } else {
            advanceRoutine();
        }
    }

    // -----------------------------------------------------------------------
    // Routine 0x12: Brief pause before Knuckles
    // -----------------------------------------------------------------------

    private void routine18Pause(int frameCounter, AbstractPlayableSprite player) {
        paletteCycler.advance();

        waitTimer--;
        if (waitTimer <= 0) {
            waitTimer = 0;
            LOG.fine("Routine 18: pause complete, advancing to monitor Knux");
            advanceRoutine();
        }
    }

    // -----------------------------------------------------------------------
    // Routine 0x14: Monitor for Knuckles spawn trigger
    // -----------------------------------------------------------------------

    private void routine20MonitorKnux(int frameCounter, AbstractPlayableSprite player) {
        paletteCycler.advance();

        if (knuckles == null) {
            // Check if camera has scrolled past the Knuckles spawn X threshold.
            int cameraX = currentX; // fallback if Camera unavailable
            try {
                Camera cam = Camera.getInstance();
                if (cam != null) {
                    cameraX = cam.getX();
                }
            } catch (Exception e) {
                // Camera may not be available in test environments
            }

            if (cameraX >= KNUCKLES_SPAWN_X) {
                ObjectSpawn knuxSpawn = new ObjectSpawn(
                        CutsceneKnucklesAiz1Instance.INIT_X,
                        CutsceneKnucklesAiz1Instance.INIT_Y,
                        0, 0, 0, false, 0);
                knuckles = new CutsceneKnucklesAiz1Instance(knuxSpawn);
                spawnDynamicObject(knuckles);

                // Spawn the rock child for Knuckles.
                ObjectSpawn rockSpawn = new ObjectSpawn(
                        CutsceneKnucklesAiz1Instance.INIT_X,
                        CutsceneKnucklesAiz1Instance.INIT_Y + 0x20,
                        0, 0, 0, false, 0);
                CutsceneKnucklesRockChild rock = new CutsceneKnucklesRockChild(rockSpawn, knuckles);
                spawnDynamicObject(rock);

                LOG.fine("Routine 20: spawned Knuckles and rock child");
                advanceRoutine();
            }
        } else {
            advanceRoutine();
        }
    }

    // -----------------------------------------------------------------------
    // Routine 0x16: Monitor/adjust plane as Knuckles approaches
    // -----------------------------------------------------------------------

    private void routine22MonitorAdjust(int frameCounter, AbstractPlayableSprite player) {
        paletteCycler.advance();

        if (knuckles != null) {
            // Trigger Knuckles when he should start falling.
            knuckles.trigger();
            LOG.fine("Routine 22: triggered Knuckles fall");
            advanceRoutine();
        }
    }

    // -----------------------------------------------------------------------
    // Routine 0x18: Explosion trigger, emerald scatter, cleanup
    // -----------------------------------------------------------------------

    private void routine24MonitorExplode(int frameCounter, AbstractPlayableSprite player) {
        paletteCycler.advance();

        if (emeralds.isEmpty()) {
            // Spawn 7 emeralds at the player's position (or current X if no player).
            int spawnX = currentX;
            int spawnY = currentY;
            if (player != null) {
                spawnX = player.getCentreX();
                spawnY = player.getCentreY();
            }

            for (int i = 0; i < 7; i++) {
                int subtype = i * 2; // CreateChild6_Simple: subtypes 0, 2, 4, 6, 8, 10, 12
                ObjectSpawn emeraldSpawn = new ObjectSpawn(spawnX, spawnY, 0, subtype, 0, false, 0);
                AizEmeraldScatterInstance emerald = new AizEmeraldScatterInstance(emeraldSpawn);
                spawnDynamicObject(emerald);
                emeralds.add(emerald);
            }

            LOG.fine("Routine 24: spawned 7 emeralds");
        }

        // Release player control and clean up.
        if (player != null) {
            player.setControlLocked(false);
            player.setObjectControlled(false);
            ownsPlayerControl = false;
        }

        // Spiral the plane child offscreen.
        if (planeChild != null && !planeChild.isSpiraling()) {
            planeChild.startSpiral(0x200, -0x100);
        }

        setDestroyed(true);
    }
}
