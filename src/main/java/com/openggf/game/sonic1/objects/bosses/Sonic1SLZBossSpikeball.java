package com.openggf.game.sonic1.objects.bosses;

import com.openggf.game.GameServices;
import com.openggf.game.sonic1.constants.Sonic1AnimationIds;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.game.sonic1.objects.Sonic1SeesawObjectInstance;
import com.openggf.level.objects.ExplosionObjectInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x7B — SLZ Boss Spikeball.
 * ROM: _incObj/7B SLZ Boss Spikeball.asm
 *
 * Dropped by the SLZ boss (0x7A) onto seesaws. Falls under gravity,
 * lands on seesaw surface, waits for tilt, launches when player jumps
 * on the other end. If it hits the boss while flying, the boss takes damage.
 *
 * While resting, a self-destruct countdown runs from $F0 (240) frames.
 * The ball flashes increasingly fast ($78→faster, $3C→fastest) and
 * self-destructs at 0, spawning 4 fragments.
 *
 * Routines (BossSpikeball_Index):
 *   0: Init          — Setup, transitions to FALLING
 *   2: FALLING       — Gravity fall toward target seesaw
 *   4: RESTING       — Sitting on seesaw, self-destruct countdown, flashing
 *   6: FLYING        — Launched by seesaw, check boss collision
 *   8: EXPLODING     — Spawn explosion; fragments only on self-destruct
 *  10: FRAGMENT      — Fragment movement with rotation
 */
public class Sonic1SLZBossSpikeball extends AbstractObjectInstance
        implements TouchResponseProvider {

    // Ball Y offsets per seesaw frame (word_19018 / See_Speeds table)
    // dc.w -8, -$1C, -$2F, -$1C, -8
    private static final int[] BALL_Y_OFFSETS = {-8, -0x1C, -0x2F, -0x1C, -8};

    // Launch velocities (from BossSpikeball code)
    // Angle delta = 1
    private static final int LAUNCH_Y_SMALL = -0x818;
    private static final int LAUNCH_X_SMALL = -0x114;
    // Angle delta = 2, speed < $9C0
    private static final int LAUNCH_Y_MEDIUM = -0x960;
    private static final int LAUNCH_X_MEDIUM = -0xF4;
    // Angle delta = 2, speed >= $9C0
    private static final int LAUNCH_Y_HEAVY = -0xA20;
    private static final int LAUNCH_X_HEAVY = -0x80;
    // Heavy landing threshold
    private static final int HEAVY_LANDING_THRESHOLD = 0x9C0;

    // Boss hitbox for collision detection (BossSpikeball_BossHitbox)
    private static final int BOSS_HITBOX_HALF = 24; // -24 to +24
    // Ball hitbox (BossSpikeball_BallHitbox)
    private static final int BALL_HITBOX_HALF = 8; // -8 to +8

    // ROM: obColType = $8B (enemy type $8, size $B)
    private static final int COLLISION_FLAGS = 0x8B;

    // ROM: move.b #$C,obActWid(a0)
    private static final int WIDTH_PIXELS = 0x0C;

    // ROM: move.b #4,obPriority(a0)
    private static final int PRIORITY = 4;
    // ROM: fragments use priority 3
    private static final int FRAGMENT_PRIORITY = 3;

    // Standard gravity (ObjectFall: addi.w #$38,obVelY(a0))
    private static final int GRAVITY = 0x38;

    // Fragment gravity (lighter: addi.w #$18,obVelY)
    private static final int FRAGMENT_GRAVITY = 0x18;

    // Ball X offset from seesaw center
    private static final int BALL_X_OFFSET = 0x28;

    // Self-destruct timer constants (ROM: obSubtype during RESTING)
    private static final int SELF_DESTRUCT_INITIAL = 0xF0;     // 240 frames
    private static final int SELF_DESTRUCT_FAST_FLASH = 0x78;  // speed up at 120
    private static final int SELF_DESTRUCT_FASTEST_FLASH = 0x3C; // fastest at 60

    // Frame toggle durations for flashing
    private static final int FLASH_DURATION_SLOW = 10;
    private static final int FLASH_DURATION_FAST = 5;
    private static final int FLASH_DURATION_FASTEST = 2;

    // Subtype value that triggers fragment spawning
    private static final int SUBTYPE_SPAWN_FRAGMENTS = 0x20;

    // Fragment velocities (BossSpikeball_FragSpeed)
    private static final int[][] FRAGMENT_SPEEDS = {
            {-0x100, -0x340},  // left-up fast
            {-0xA0,  -0x240},  // left-up slow
            { 0x100, -0x340},  // right-up fast
            { 0xA0,  -0x240},  // right-up slow
    };

    private enum State {
        FALLING,    // Routine 2: gravity fall
        RESTING,    // Routine 4: sitting on seesaw, self-destruct countdown
        FLYING,     // Routine 6: launched, check boss collision
        EXPLODING,  // Routine 8: hit boss or self-destruct, spawn explosion
        FRAGMENT    // Routine 10: fragment movement
    }

    private State currentState;

    // References
    private final Sonic1SLZBossInstance boss;
    private final Sonic1SeesawObjectInstance seesaw;

    // Position (16.16 fixed-point)
    private int xPos;
    private int yPos;
    private int xVel;
    private int yVel;

    // Original seesaw position (see_origX/see_origY)
    private final int seesawX;
    private final int seesawY;

    // Stored seesaw frame when landing (see_frame / objoff_3A)
    private int storedFrame;

    // Display frame for rendering
    private int displayFrame = 1; // ROM: obFrame = 1 (silver ball)

    // Self-destruct countdown (mirrors ROM obSubtype during RESTING)
    private int subtypeCounter;

    // Frame toggle animation (mirrors ROM obTimeFrame/obDelayAni)
    private int frameToggleTimer;
    private int frameToggleDuration;

    // Fragment animation counter
    private int fragmentAnimCounter;

    /**
     * Create a boss spikeball spawned by the SLZ boss above a target seesaw.
     *
     * @param boss   The parent boss (for hit detection)
     * @param seesaw The target seesaw to land on
     * @param startX Boss X position at spawn time
     * @param startY Boss Y position at spawn time (already includes +$20 offset)
     */
    public Sonic1SLZBossSpikeball(
            Sonic1SLZBossInstance boss,
            Sonic1SeesawObjectInstance seesaw,
            int startX,
            int startY) {
        super(new ObjectSpawn(startX, startY,
                Sonic1ObjectIds.SLZ_BOSS_SPIKEBALL, 0, 0, false, 0), "BossSpikeball");

        this.boss = boss;
        this.seesaw = seesaw;
        this.seesawX = seesaw.getSpawn().x();
        this.seesawY = seesaw.getSpawn().y();

        // Start at boss position
        this.xPos = startX << 16;
        this.yPos = startY << 16;
        this.xVel = 0;
        this.yVel = 0;

        this.currentState = State.FALLING;

        // ROM: bset/bclr #0,obStatus(a0) — determine initial side
        if (startX > seesawX) {
            storedFrame = 0;
        } else {
            storedFrame = 2;
        }
    }

    /**
     * Fragment constructor — creates a fragment with specified velocity.
     * ROM: BossSpikeball_MakeFrag — priority 3, obColType = $98.
     */
    private Sonic1SLZBossSpikeball(int x, int y, int fragXVel, int fragYVel) {
        super(new ObjectSpawn(x, y,
                Sonic1ObjectIds.SLZ_BOSS_SPIKEBALL, 0, 0, false, 0), "BossSpikeFrag");
        this.boss = null;
        this.seesaw = null;
        this.seesawX = 0;
        this.seesawY = 0;
        this.xPos = x << 16;
        this.yPos = y << 16;
        this.xVel = fragXVel;
        this.yVel = fragYVel;
        this.currentState = State.FRAGMENT;
        this.displayFrame = 0;
        this.fragmentAnimCounter = 0;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        switch (currentState) {
            case FALLING -> updateFalling();
            case RESTING -> updateResting();
            case FLYING -> updateFlying();
            case EXPLODING -> updateExploding();
            case FRAGMENT -> updateFragment(frameCounter);
        }
    }

    // === FALLING — gravity fall toward seesaw ===
    // ROM: BossSpikeball_Fall (routine 2)
    private void updateFalling() {
        objectFall();

        // Check if reached seesaw surface
        int parentMappingFrame = seesaw.getMappingFrame();
        int offsetIndex = parentMappingFrame;

        int currentX = xPos >> 16;
        if (currentX < seesawX) {
            offsetIndex += 2;
        }
        offsetIndex = clampIndex(offsetIndex);

        int landingY = seesawY + BALL_Y_OFFSETS[offsetIndex];
        int currentY = yPos >> 16;

        if (currentY >= landingY) {
            // Landed on seesaw
            yPos = landingY << 16;
            xVel = 0;
            yVel = 0;

            // Determine landing side and set seesaw tilt
            int landingSide = (currentX >= seesawX) ? 0 : 2;
            storedFrame = landingSide;

            // ROM: move.w #$F0,obSubtype(a0) — self-destruct timer
            subtypeCounter = SELF_DESTRUCT_INITIAL;
            // ROM: move.b #10,obDelayAni(a0) — initial flash duration
            frameToggleDuration = FLASH_DURATION_SLOW;
            frameToggleTimer = frameToggleDuration;

            // Set seesaw tilt and potentially launch player (shared code at loc_18FA2)
            transitionToSeesawResting(landingSide);
        }
    }

    // === RESTING — sitting on seesaw, waiting for tilt change ===
    // ROM: loc_18DC6 (routine 4)
    private void updateResting() {
        int parentFrame = seesaw.getTargetFrame();
        int angleDelta = storedFrame - parentFrame;

        if (angleDelta != 0) {
            // Seesaw tilted — calculate launch velocity and transition to FLYING
            launchFromSeesaw(angleDelta);
            return;
        }

        // No tilt change — position on seesaw surface and run self-destruct countdown
        positionOnSeesaw();

        // ROM: subq.w #1,obSubtype(a0)
        subtypeCounter--;
        if (subtypeCounter <= 0) {
            // Self-destruct! Set subtype to $20 (triggers fragment spawn) and explode
            subtypeCounter = SUBTYPE_SPAWN_FRAGMENTS;
            currentState = State.EXPLODING;
            return;
        }

        // ROM: speed up flashing at thresholds
        if (subtypeCounter == SELF_DESTRUCT_FAST_FLASH) {
            frameToggleDuration = FLASH_DURATION_FAST;
        } else if (subtypeCounter == SELF_DESTRUCT_FASTEST_FLASH) {
            frameToggleDuration = FLASH_DURATION_FASTEST;
        }

        // Frame toggle animation (ROM: loc_18E96)
        updateFlashingAnimation();
    }

    /**
     * Launch ball from seesaw when tilt changes.
     * ROM: loc_18DDA — computes launch velocities from angle delta and player landing speed.
     */
    private void launchFromSeesaw(int angleDelta) {
        int absDelta = Math.abs(angleDelta);

        int launchY, launchX;
        if (absDelta == 1) {
            launchY = LAUNCH_Y_SMALL;
            launchX = LAUNCH_X_SMALL;
        } else {
            int parentStoredVel = seesaw.getStoredPlayerYVel();
            if (parentStoredVel >= HEAVY_LANDING_THRESHOLD) {
                launchY = LAUNCH_Y_HEAVY;
                launchX = LAUNCH_X_HEAVY;
            } else {
                launchY = LAUNCH_Y_MEDIUM;
                launchX = LAUNCH_X_MEDIUM;
            }
        }

        yVel = launchY;
        xVel = launchX;

        // Negate X velocity if ball is on left side of seesaw
        int currentX = xPos >> 16;
        if (currentX < seesawX) {
            xVel = -xVel;
        }

        displayFrame = 1; // ROM: move.b #1,obFrame(a0)
        // ROM: move.w #$20,obSubtype(a0) — set for potential fragment spawn on self-destruct
        subtypeCounter = SUBTYPE_SPAWN_FRAGMENTS;
        currentState = State.FLYING;
    }

    // === FLYING — launched, check boss collision and landing ===
    // ROM: loc_18EAA (routine 6)
    private void updateFlying() {
        boolean hitBoss = false;

        // Check collision with boss
        if (boss != null && !boss.isDestroyed() && !boss.getState().defeated) {
            if (checkBossCollision()) {
                // ROM: addq.b #2,obRoutine(a0) — advance to EXPLODING
                // ROM: clr.w obSubtype(a0) — no fragments on boss hit
                subtypeCounter = 0;
                hitBoss = true;

                // ROM: clr.b obColType(a1) / subq.b #1,obColProp(a1)
                boss.onSpikeballHit();

                // ROM: if boss defeated, clear ball velocity
                if (boss.getState().defeated) {
                    xVel = 0;
                    yVel = 0;
                }
            }
        }

        // Continue flying physics even after hit (ROM continues to loc_18F38)
        if (yVel < 0) {
            // Ascending
            objectFall();

            // Double gravity when below upper bound
            int upperBound = seesawY - 0x2F;
            int currentY = yPos >> 16;
            if (currentY >= upperBound) {
                objectFall();
            }
        } else {
            // Descending
            objectFall();
            if (checkFlyingLanding()) {
                return; // landed — state already changed to EXPLODING
            }
        }

        // Frame toggle flashing animation (ROM: bra.w loc_18E7A at end of flying)
        updateFlashingAnimation();

        // Remove if off-screen (ROM: BossStarLight_Delete check after DisplaySprite)
        if (!isOnScreen()) {
            setDestroyed(true);
            return;
        }

        // Apply deferred state change after all physics for this frame
        if (hitBoss) {
            currentState = State.EXPLODING;
        }
    }

    // === EXPLODING — spawn explosion and optionally fragments ===
    // ROM: BossSpikeball_Explode (routine 8)
    private void updateExploding() {
        int bx = xPos >> 16;
        int by = yPos >> 16;

        // ROM: move.b #id_ExplosionBomb,obID(a0) — replace self with bomb explosion (0x3F)
        // Same explosion type as bomb badnik, uses Map_ExplodeBomb + ArtTile_Explosion
        var objectManager = services().objectManager();
        var renderManager = services().renderManager();
        if (objectManager != null && renderManager != null) {
            ExplosionObjectInstance explosion = new ExplosionObjectInstance(
                    0x3F, bx, by, renderManager);
            objectManager.addDynamicObject(explosion);
            // ROM: sfx_Bomb ($C4)
            services().playSfx(Sonic1Sfx.BOSS_EXPLOSION.id);
        }

        // ROM: cmpi.w #$20,obSubtype(a0) / beq.s BossSpikeball_MakeFrag
        // Fragments only spawn on self-destruct (subtype == $20), not on boss hit (subtype == 0)
        if (subtypeCounter == SUBTYPE_SPAWN_FRAGMENTS) {
            spawnFragments(bx, by);
        }

        setDestroyed(true);
    }

    // === FRAGMENT — fragment movement with gravity and rotation ===
    // ROM: BossSpikeball_MoveFrag (routine 10)
    private void updateFragment(int frameCounter) {
        // Apply velocity (SpeedToPos)
        xPos += (xVel << 8);
        yPos += (yVel << 8);

        // Fragment gravity (lighter than standard)
        yVel += FRAGMENT_GRAVITY;

        // ROM: moveq #4,d0 / and.w (v_vbla_word).w,d0 / lsr.w #2,d0
        // Rotating animation: bit 2 of frame counter, shifted right by 2
        fragmentAnimCounter++;
        displayFrame = (fragmentAnimCounter >> 2) & 1;

        // Remove when off-screen
        if (!isOnScreen()) {
            setDestroyed(true);
        }
    }

    /**
     * ObjectFall: apply velocity and add gravity.
     */
    private void objectFall() {
        xPos += (xVel << 8);
        yPos += (yVel << 8);
        yVel += GRAVITY;
    }

    /**
     * Position ball on seesaw surface based on parent's visual mapping frame.
     * ROM: loc_18E2A pattern (shared with seesaw ball).
     */
    private void positionOnSeesaw() {
        int parentMappingFrame = seesaw.getMappingFrame();

        int offsetIndex = parentMappingFrame;
        int xOffset = BALL_X_OFFSET;

        int currentX = xPos >> 16;
        if (currentX < seesawX) {
            xOffset = -BALL_X_OFFSET;
            offsetIndex += 2;
        }
        offsetIndex = clampIndex(offsetIndex);

        yPos = (seesawY + BALL_Y_OFFSETS[offsetIndex]) << 16;
        xPos = (seesawX + xOffset) << 16;
    }

    /**
     * Check if flying ball has landed on seesaw surface.
     * ROM: loc_18F5C (descending path) — if landed, transitions to EXPLODING (routine += 2).
     * Returns true if landed.
     */
    private boolean checkFlyingLanding() {
        int parentMappingFrame = seesaw.getMappingFrame();
        int offsetIndex = parentMappingFrame;

        int currentX = xPos >> 16;
        if (currentX < seesawX) {
            offsetIndex += 2;
        }
        offsetIndex = clampIndex(offsetIndex);

        int landingY = seesawY + BALL_Y_OFFSETS[offsetIndex];
        int currentY = yPos >> 16;

        if (currentY < landingY) {
            return false; // Haven't reached surface
        }

        // Ball landed — determine side and set seesaw frame
        yPos = landingY << 16;
        int landingAngle = (xVel < 0) ? 2 : 0;

        // ROM: move.w #0,obSubtype(a0) — no fragments on landing
        subtypeCounter = 0;

        // Set seesaw tilt and potentially launch player (shared code at loc_18FA2)
        // ROM: addq.b #2,obRoutine(a0) from loc_19008 — routine 6 → 8 (EXPLODING)
        transitionToSeesawResting(landingAngle);

        // ROM: from FLYING (routine 6), routine += 2 = 8 (EXPLODING), not RESTING
        currentState = State.EXPLODING;
        return true;
    }

    /**
     * Shared seesaw landing logic (ROM: loc_18FA2).
     * Sets seesaw tilt angle, checks if player should be launched, clears velocity.
     */
    private void transitionToSeesawResting(int landingAngle) {
        seesaw.setTargetFrame(landingAngle);
        storedFrame = landingAngle;

        // ROM: cmp.b obFrame(a1),d1 — check if tilt actually changed
        // ROM: bclr #3,obStatus(a1) / beq.s loc_19008 — check if player was standing
        if (seesaw.isPlayerStanding()) {
            launchStandingPlayer();
        }

        // ROM: loc_19008 — clear velocity
        xVel = 0;
        yVel = 0;

        // For FALLING → RESTING, set state here (FLYING → EXPLODING overrides after return)
        if (currentState == State.FALLING) {
            currentState = State.RESTING;
        }
    }

    /**
     * Check AABB overlap between boss and ball for hit detection.
     * ROM: uses BossSpikeball_BossHitbox (-24..+24) and BossSpikeball_BallHitbox (-8..+8).
     */
    private boolean checkBossCollision() {
        int bossX = boss.getX();
        int bossY = boss.getY();
        int ballX = xPos >> 16;
        int ballY = yPos >> 16;

        // AABB overlap check — combined half-widths = 24 + 8 = 32
        int dx = Math.abs(ballX - bossX);
        int dy = Math.abs(ballY - bossY);

        return dx < (BOSS_HITBOX_HALF + BALL_HITBOX_HALF)
                && dy < (BOSS_HITBOX_HALF + BALL_HITBOX_HALF);
    }

    /**
     * Launch any player standing on the seesaw (spring effect).
     * ROM: loc_18FA2 — inverse ball Y velocity applied to player.
     */
    private void launchStandingPlayer() {
        AbstractPlayableSprite player = seesaw.getStandingPlayer();
        if (player == null) {
            return;
        }

        // move.w obVelY(a0),obVelY(a2) / neg.w obVelY(a2)
        player.setYSpeed((short) -yVel);
        player.setAir(true);
        player.setOnObject(false);
        // ROM: Boss seesaw sets roll animation (not spring) so Sonic is in ball form
        // and can damage Robotnik. Regular seesaws (5E) use id_Spring instead.
        player.setRolling(true);
        player.setJumping(true);
        player.setAnimationId(Sonic1AnimationIds.ROLL);
        seesaw.clearPlayerStanding();

        try {
            services().playSfx(Sonic1Sfx.SPRING.id);
        } catch (Exception e) {
            // Prevent audio failure from breaking game logic
        }
    }

    /**
     * Frame toggle flashing animation.
     * ROM: loc_18E96 — decrements obTimeFrame, toggles obFrame between 0 and 1 at zero.
     */
    private void updateFlashingAnimation() {
        frameToggleTimer--;
        if (frameToggleTimer <= 0) {
            displayFrame ^= 1; // ROM: bchg #0,obFrame(a0)
            frameToggleTimer = frameToggleDuration;
        }
    }

    /**
     * Spawn 4 fragments from explosion point.
     * ROM: BossSpikeball_MakeFrag with BossSpikeball_FragSpeed velocities.
     */
    private void spawnFragments(int x, int y) {
        var objectManager = services().objectManager();
        if (objectManager == null) {
            return;
        }

        for (int[] speed : FRAGMENT_SPEEDS) {
            Sonic1SLZBossSpikeball fragment = new Sonic1SLZBossSpikeball(
                    x, y, speed[0], speed[1]);
            objectManager.addDynamicObject(fragment);
        }
    }

    private int clampIndex(int index) {
        if (index < 0) return 0;
        if (index >= BALL_Y_OFFSETS.length) return BALL_Y_OFFSETS.length - 1;
        return index;
    }

    @Override
    protected boolean isOnScreen() {
        var camera = GameServices.camera();
        if (camera == null) {
            return true;
        }
        int screenX = (xPos >> 16) - camera.getX();
        int screenY = (yPos >> 16) - camera.getY();
        return screenX >= -64 && screenX <= 384 && screenY >= -64 && screenY <= 288;
    }

    // ---- TouchResponseProvider ----

    @Override
    public int getCollisionFlags() {
        if (currentState == State.EXPLODING || currentState == State.FRAGMENT) {
            // ROM: fragments use obColType = $98
            return (currentState == State.FRAGMENT) ? 0x98 : 0;
        }
        return COLLISION_FLAGS; // $8B — hurts player
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    // ---- Position ----

    @Override
    public int getX() {
        return (xPos >> 16) - WIDTH_PIXELS;
    }

    @Override
    public int getY() {
        return (yPos >> 16) - 0x0C;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return buildSpawnAt(xPos >> 16, yPos >> 16);
    }

    // ---- Rendering ----

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }

        if (currentState == State.FRAGMENT) {
            // ROM: BossSpikeball_MakeFrag uses bomb shrapnel art (Ani_Bomb .shrapnel: frames $A, $B)
            PatternSpriteRenderer bombRenderer = renderManager.getRenderer(ObjectArtKeys.BOMB);
            if (bombRenderer != null && bombRenderer.isReady()) {
                int shrapnelFrame = 10 + displayFrame; // map 0→10, 1→11
                bombRenderer.drawFrameIndex(shrapnelFrame, xPos >> 16, yPos >> 16, false, false);
            }
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.SLZ_SEESAW_BALL);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        renderer.drawFrameIndex(displayFrame, xPos >> 16, yPos >> 16, false, false);
    }

    @Override
    public int getPriorityBucket() {
        // ROM: fragments use priority 3, ball uses priority 4
        if (currentState == State.FRAGMENT) {
            return RenderPriority.clamp(FRAGMENT_PRIORITY);
        }
        return RenderPriority.clamp(PRIORITY);
    }

    // ---- Persistence ----

    @Override
    public boolean isPersistent() {
        return !isDestroyed() && isOnScreen();
    }
}
