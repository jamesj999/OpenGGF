package uk.co.jamesj999.sonic.game.sonic2.objects.badniks;

import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseAttackable;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.objects.TouchResponseResult;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants;
import uk.co.jamesj999.sonic.game.sonic2.objects.ExplosionObjectInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.PointsObjectInstance;

import java.util.List;

/**
 * Rexon Head (0x97) - Individual head segment of the Rexon lava snake.
 * Based on disassembly Obj97.
 *
 * States:
 * - INIT: Set position, wait timer
 * - INITIAL_WAIT: Wait staggered frames per head index
 * - RAISE_HEAD: Launch upward/outward, decelerate
 * - NORMAL: Oscillate, fire projectiles (last head only)
 * - DEATH_DROP: Fall off screen if parent body destroyed
 */
public class RexonHeadObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable {

    // Collision size from disassembly (first 4 heads: 0x0B, last head: 0x8B with HURT flag)
    private static final int COLLISION_SIZE_NORMAL = 0x0B;

    // Initial wait timers per head index (from disassembly s2.asm:73803-73808)
    // byte_3744E indexed by (headIndex / 2)
    private static final int[] INITIAL_WAIT_TIMES = {
            30,  // head 0 (index 0): byte_3744E[0] = $1E
            24,  // head 1 (index 2): byte_3744E[1] = $18
            18,  // head 2 (index 4): byte_3744E[2] = $12
            12,  // head 3 (index 6): byte_3744E[3] = $C
            6    // head 4 (index 8): byte_3744E[4] = 6
    };

    // Raise phase timers per head index (from disassembly s2.asm:73833-73837)
    // byte_3744E indexed by (8 - headIndex) / 2 - reverse order creates snake unfurl effect
    private static final int[] RAISE_TIMERS = {
            6,   // head 0 (index 0): byte_3744E[(8-0)/2=4] = 6
            12,  // head 1 (index 2): byte_3744E[(8-2)/2=3] = $C
            18,  // head 2 (index 4): byte_3744E[(8-4)/2=2] = $12
            24,  // head 3 (index 6): byte_3744E[(8-6)/2=1] = $18
            30   // head 4 (index 8): byte_3744E[(8-8)/2=0] = $1E
    };

    // Oscillation amplitudes per head (from disassembly s2.asm:73871-73876, byte_374BE)
    // Note: Original game only has 4 entries; head 4 (index 8) reads past array (undefined)
    // We provide a 5th entry matching a reasonable extrapolation
    private static final int[] OSCILLATION_AMPLITUDES = {
            0x24, // head 0 (index 0)
            0x20, // head 1 (index 2) - from byte_374BE+1
            0x1C, // head 2 (index 4) - from byte_374BE+2
            0x1A, // head 3 (index 6) - from byte_374BE+3
            0x18  // head 4 (index 8) - extrapolated (original reads past array)
    };

    // Death drop X velocities per head (from disassembly)
    private static final int[] DEATH_X_VELOCITIES = {
            0x80,   // head 0
            -0x100, // head 1
            0x100,  // head 2
            -0x80,  // head 3
            0x80    // head 4
    };

    // Oscillation lookup table (X, Y pairs, signed bytes)
    // This creates a figure-8 like motion when combined with rotation
    private static final int[] OSCILLATION_TABLE = {
            0x0F, 0x00, 0x0F, 0xFF, 0x0F, 0xFF, 0x0F, 0xFE,
            0x0F, 0xFD, 0x0F, 0xFC, 0x0E, 0xFA, 0x0E, 0xF9,
            0x0D, 0xF7, 0x0D, 0xF6, 0x0C, 0xF5, 0x0B, 0xF4,
            0x0A, 0xF3, 0x09, 0xF2, 0x08, 0xF2, 0x07, 0xF1,
            0x06, 0xF1, 0x05, 0xF1, 0x04, 0xF1, 0x03, 0xF1,
            0x02, 0xF1, 0x01, 0xF1, 0x01, 0xF1, 0x00, 0xF1,
            0xFF, 0xF1, 0xFF, 0xF1, 0xFE, 0xF1, 0xFD, 0xF1,
            0xFD, 0xF1, 0xFC, 0xF1, 0xFB, 0xF1, 0xFB, 0xF1
    };

    // Projectile constants
    private static final int PROJECTILE_INITIAL_DELAY = 32;  // Initial delay before first fire
    private static final int PROJECTILE_FIRE_INTERVAL = 127; // Frames between fires
    private static final int PROJECTILE_X_VELOCITY = 0x100;  // 1 pixel/frame (8.8 fixed)
    private static final int PROJECTILE_Y_VELOCITY = 0x80;   // Initial Y velocity

    private enum State {
        INIT,
        INITIAL_WAIT,
        RAISE_HEAD,
        NORMAL,
        DEATH_DROP
    }

    private final LevelManager levelManager;
    private final RexonBadnikInstance parent;
    private final int headIndex;  // 0, 2, 4, 6, or 8
    private final int headNumber; // 0-4 for array indexing
    private final boolean xFlip;

    private State state;
    private int currentX;
    private int currentY;
    private int baseX;  // Anchor position for oscillation
    private int baseY;
    private int xVelocity;  // 8.8 fixed point
    private int yVelocity;
    private int xSubpixel;
    private int ySubpixel;
    private int waitTimer;
    private int raiseTimer;  // Timer for raise phase duration (s2.asm:73837)
    private int oscillationIndex;
    private int oscillationRotation;  // 0, 1, 2, 3 for 0°, 90°, 180°, 270°
    private int oscillationFrameCounter;
    private int projectileTimer;
    private boolean destroyed;

    public RexonHeadObjectInstance(ObjectSpawn spawn, LevelManager levelManager,
                                   RexonBadnikInstance parent, int x, int y,
                                   int headIndex, boolean xFlip) {
        super(spawn, "RexonHead");
        this.levelManager = levelManager;
        this.parent = parent;
        this.headIndex = headIndex;
        this.headNumber = headIndex / 2;  // Convert 0,2,4,6,8 to 0,1,2,3,4
        this.xFlip = xFlip;

        this.currentX = x;
        this.currentY = y;
        this.baseX = x;
        this.baseY = y;
        this.xVelocity = 0;
        this.yVelocity = 0;
        this.xSubpixel = 0;
        this.ySubpixel = 0;
        this.state = State.INIT;
        this.raiseTimer = 0;
        this.oscillationIndex = 0;
        this.oscillationRotation = 0;
        this.oscillationFrameCounter = 0;
        this.projectileTimer = PROJECTILE_INITIAL_DELAY;
        this.destroyed = false;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (destroyed) {
            return;
        }

        switch (state) {
            case INIT -> initHead();
            case INITIAL_WAIT -> updateInitialWait();
            case RAISE_HEAD -> updateRaiseHead();
            case NORMAL -> updateNormal(frameCounter);
            case DEATH_DROP -> updateDeathDrop();
        }
    }

    private void initHead() {
        waitTimer = INITIAL_WAIT_TIMES[headNumber];
        state = State.INITIAL_WAIT;
    }

    private void updateInitialWait() {
        waitTimer--;
        if (waitTimer <= 0) {
            // Start rising - set initial velocity (s2.asm:73831-73832)
            // Heads rise upward and outward
            int xDir = xFlip ? 1 : -1;
            xVelocity = xDir * 0x120;  // -$120 or +$120 (s2.asm:73831)
            yVelocity = -0x200;        // -$200 upward (s2.asm:73832)

            // Set raise timer (s2.asm:73833-73837)
            raiseTimer = RAISE_TIMERS[headNumber];

            state = State.RAISE_HEAD;
        }
    }

    private void updateRaiseHead() {
        // Decelerate outward X velocity (s2.asm:73845-73852)
        // Original always adds +$10 to x_vel, which decelerates from -$120 toward zero
        // For mirrored direction (xFlip), we need to subtract $10 to decelerate from +$120
        if (xFlip) {
            xVelocity -= 0x10;  // Decelerate rightward motion (+$120 toward 0)
        } else {
            xVelocity += 0x10;  // Decelerate leftward motion (-$120 toward 0)
        }

        // Decrement raise timer (s2.asm:73847)
        raiseTimer--;

        // Apply velocity via ObjectMove (s2.asm:73857)
        int xPos32 = (currentX << 8) | (xSubpixel & 0xFF);
        int yPos32 = (currentY << 8) | (ySubpixel & 0xFF);
        xPos32 += xVelocity;
        yPos32 += yVelocity;
        currentX = xPos32 >> 8;
        currentY = yPos32 >> 8;
        xSubpixel = xPos32 & 0xFF;
        ySubpixel = yPos32 & 0xFF;

        // Transition to NORMAL when timer expires (s2.asm:73848 bmi.s Obj97_StartNormalState)
        if (raiseTimer < 0) {
            // Stop movement via Obj_MoveStop (s2.asm:73864)
            xVelocity = 0;
            yVelocity = 0;
            // Store current position as base for oscillation
            baseX = currentX;
            baseY = currentY;
            state = State.NORMAL;
        }
    }

    private void updateNormal(int frameCounter) {
        // Update oscillation every 4 frames
        oscillationFrameCounter++;
        if (oscillationFrameCounter >= 4) {
            oscillationFrameCounter = 0;
            updateOscillation();
        }

        // Apply oscillation offset to base position
        applyOscillationOffset();

        // Fire projectile (last head only)
        if (headNumber == 4) {
            updateProjectileFiring();
        }
    }

    private void updateOscillation() {
        // Get amplitude for this head
        int amplitude = OSCILLATION_AMPLITUDES[headNumber];

        // Advance index
        oscillationIndex++;
        if (oscillationIndex >= 32) {
            // Reached end of table, rotate and reverse
            oscillationIndex = 0;
            oscillationRotation = (oscillationRotation + 1) & 3;
        }
    }

    private void applyOscillationOffset() {
        int tableIndex = oscillationIndex * 2;
        int rawX = OSCILLATION_TABLE[tableIndex];
        int rawY = OSCILLATION_TABLE[tableIndex + 1];

        // Convert signed bytes
        if (rawX > 127) rawX -= 256;
        if (rawY > 127) rawY -= 256;

        // Apply rotation (0°, 90°, 180°, 270°)
        int rotX = rawX;
        int rotY = rawY;
        switch (oscillationRotation) {
            case 1 -> { rotX = -rawY; rotY = rawX; }     // 90°
            case 2 -> { rotX = -rawX; rotY = -rawY; }    // 180°
            case 3 -> { rotX = rawY; rotY = -rawX; }     // 270°
        }

        // Scale by amplitude
        int amplitude = OSCILLATION_AMPLITUDES[headNumber];
        int offsetX = (rotX * amplitude) >> 4;
        int offsetY = (rotY * amplitude) >> 4;

        // Apply flip
        if (xFlip) {
            offsetX = -offsetX;
        }

        currentX = baseX + offsetX;
        currentY = baseY + offsetY;
    }

    private void updateProjectileFiring() {
        projectileTimer--;
        if (projectileTimer <= 0) {
            fireProjectile();
            projectileTimer = PROJECTILE_FIRE_INTERVAL;
        }
    }

    private void fireProjectile() {
        // Projectile spawns ahead of head
        int xDir = xFlip ? 1 : -1;
        int projX = currentX + (xDir * 16);
        int projY = currentY + 4;

        // Very slow horizontal, with gravity
        int projXVel = xDir * PROJECTILE_X_VELOCITY;
        int projYVel = PROJECTILE_Y_VELOCITY;

        BadnikProjectileInstance projectile = new BadnikProjectileInstance(
                spawn,
                BadnikProjectileInstance.ProjectileType.REXON_FIREBALL,
                projX,
                projY,
                projXVel,
                projYVel,
                true,  // Apply gravity
                xFlip
        );

        levelManager.getObjectManager().addDynamicObject(projectile);
    }

    private void updateDeathDrop() {
        // Apply gravity
        yVelocity += 0x38;

        // Apply velocity
        int xPos32 = (currentX << 8) | (xSubpixel & 0xFF);
        int yPos32 = (currentY << 8) | (ySubpixel & 0xFF);
        xPos32 += xVelocity;
        yPos32 += yVelocity;
        currentX = xPos32 >> 8;
        currentY = yPos32 >> 8;
        xSubpixel = xPos32 & 0xFF;
        ySubpixel = yPos32 & 0xFF;

        // Check if off screen
        if (!isOnScreen(64)) {
            destroyed = true;
            setDestroyed(true);
        }
    }

    /**
     * Trigger death drop state when parent body is destroyed.
     */
    public void triggerDeathDrop() {
        if (state == State.DEATH_DROP || destroyed) {
            return;
        }

        state = State.DEATH_DROP;
        xVelocity = DEATH_X_VELOCITIES[headNumber];
        yVelocity = -0x200;  // Initial upward velocity
    }

    @Override
    public int getCollisionFlags() {
        // Last head (index 8) has HURT flag (0x80), others are normal enemy
        if (headNumber == 4) {
            return 0x80 | (COLLISION_SIZE_NORMAL & 0x3F);  // HURT category
        }
        return 0x00 | (COLLISION_SIZE_NORMAL & 0x3F);  // ENEMY category
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public void onPlayerAttack(AbstractPlayableSprite player, TouchResponseResult result) {
        if (destroyed) {
            return;
        }

        destroyHead(player);
    }

    private void destroyHead(AbstractPlayableSprite player) {
        destroyed = true;
        setDestroyed(true);

        // Spawn explosion
        ExplosionObjectInstance explosion = new ExplosionObjectInstance(0x27, currentX, currentY,
                levelManager.getObjectRenderManager());
        levelManager.getObjectManager().addDynamicObject(explosion);

        // Spawn animal
        AnimalObjectInstance animal = new AnimalObjectInstance(
                new ObjectSpawn(currentX, currentY, 0x28, 0, 0, false, 0), levelManager);
        levelManager.getObjectManager().addDynamicObject(animal);

        // Calculate and award points
        int pointsValue = 100;
        if (player != null) {
            pointsValue = player.incrementBadnikChain();
            GameServices.gameState().addScore(pointsValue);
        }

        // Spawn points display
        PointsObjectInstance points = new PointsObjectInstance(
                new ObjectSpawn(currentX, currentY, 0x29, 0, 0, false, 0), levelManager, pointsValue);
        levelManager.getObjectManager().addDynamicObject(points);

        // Play explosion SFX
        uk.co.jamesj999.sonic.audio.AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_EXPLOSION);

        // Notify parent to trigger death drop for other heads
        if (parent != null) {
            parent.onHeadDestroyed(this);
        }
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(
                currentX,
                currentY,
                spawn.objectId(),
                spawn.subtype(),
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (destroyed) {
            return;
        }

        // Don't render during initial wait
        if (state == State.INIT || state == State.INITIAL_WAIT) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.REXON);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Head segments use frame 1
        renderer.drawFrameIndex(1, currentX, currentY, xFlip, false);
    }
}
