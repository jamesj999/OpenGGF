package uk.co.jamesj999.sonic.game.sonic1.objects.badniks;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.debug.DebugRenderContext;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Sfx;
import uk.co.jamesj999.sonic.game.sonic2.objects.ExplosionObjectInstance;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseAttackable;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.objects.TouchResponseResult;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.awt.Color;
import java.util.List;

/**
 * Bomb Enemy (0x5F) - Walking bomb badnik from Star Light Zone and Scrap Brain Zone.
 * <p>
 * The bomb stands still, periodically walks back and forth, and when Sonic gets close,
 * its fuse ignites and it eventually explodes into shrapnel pieces.
 * The bomb body HURTS Sonic on contact (obColType = $9A, category $80).
 * Shrapnel pieces also hurt Sonic (obColType = $98, category $80).
 * <p>
 * Based on docs/s1disasm/_incObj/5F Bomb Enemy.asm.
 * <p>
 * Main object state machine (ob2ndRout / 2):
 * <ul>
 *   <li>0 (.walk): Standing still, timer counts down. When timer expires, switches
 *       direction and starts walking. Continuously checks if Sonic is within $60px range.</li>
 *   <li>1 (.wait): Walking with obVelX. Timer counts down. When timer expires, stops
 *       and returns to .walk state. Continuously checks if Sonic is within $60px range.</li>
 *   <li>2 (.explode): Fuse lit, countdown to explosion. When timer expires, changes
 *       object to ExplosionBomb (object $3F). The explosion spawns independently.</li>
 * </ul>
 * <p>
 * Sub-objects spawned:
 * <ul>
 *   <li>Fuse (subtype 4): Spawned when Sonic detected. Moves with obVelY = $10 (or -$10 if
 *       upside-down). Renders fuse animation (frames 8-9). After timer expires, creates
 *       4 shrapnel pieces and deletes self.</li>
 *   <li>Shrapnel (subtype 6): 4 pieces with predefined velocities. Fall with gravity ($18/frame).
 *       Render shrapnel animation (frames 10-11). Deleted when off-screen.</li>
 * </ul>
 * <p>
 * Animations (Ani_Bomb):
 * <ul>
 *   <li>0 (.stand):     frames 1, 0 at speed $13 - standing idle</li>
 *   <li>1 (.walk):      frames 5, 4, 3, 2 at speed $13 - walking</li>
 *   <li>2 (.activated): frames 7, 6 at speed $13 - fuse lit, body flashing</li>
 *   <li>3 (.fuse):      frames 8, 9 at speed 3 - fuse sparking</li>
 *   <li>4 (.shrapnel):  frames $A, $B at speed 3 - shrapnel spinning</li>
 * </ul>
 */
public class Sonic1BombBadnikInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable {

    // --- Collision ---
    // From disassembly: move.b #$9A,obColType(a0)
    // $80 = HURT category, $1A = size index (width $C, height $C)
    private static final int COLLISION_SIZE_INDEX = 0x1A;

    // --- Proximity ---
    // From disassembly: cmpi.w #$60,d0 (Sonic proximity check range)
    private static final int PROXIMITY_RANGE = 0x60;

    // --- Timers (in frames) ---
    // From disassembly: move.w #179,bom_time(a0) (standing still timer = 3 seconds)
    private static final int STAND_TIME = 179;
    // From disassembly: move.w #1535,bom_time(a0) (walk timer ~25 seconds)
    private static final int WALK_TIME = 1535;
    // From disassembly: move.w #143,bom_time(a0) (fuse countdown timer)
    private static final int FUSE_TIME = 143;

    // --- Velocities ---
    // From disassembly: move.w #$10,obVelX(a0) (walk speed)
    private static final int WALK_SPEED = 0x10;
    // From disassembly: move.w #$10,obVelY(a1) (fuse rise speed)
    private static final int FUSE_Y_SPEED = 0x10;
    // From disassembly: addi.w #$18,obVelY(a0) (shrapnel gravity)
    private static final int SHRAPNEL_GRAVITY = 0x18;

    // --- Shrapnel velocities from Bom_ShrSpeed ---
    // dc.w -$200, -$300, -$100, -$200, $200, -$300, $100, -$200
    private static final int[][] SHRAPNEL_VELOCITIES = {
            {-0x200, -0x300},
            {-0x100, -0x200},
            { 0x200, -0x300},
            { 0x100, -0x200}
    };

    // --- Animation ---
    // Animation IDs from Ani_Bomb
    private static final int ANIM_STAND = 0;
    private static final int ANIM_WALK = 1;
    private static final int ANIM_ACTIVATED = 2;
    private static final int ANIM_FUSE = 3;
    private static final int ANIM_SHRAPNEL = 4;

    // Animation speed $13 + 1 = 20 ticks per frame
    private static final int ANIM_SPEED_NORMAL = 0x13 + 1;
    // Animation speed 3 + 1 = 4 ticks per frame
    private static final int ANIM_SPEED_FAST = 3 + 1;

    // Stand animation: frames 1, 0
    private static final int[] STAND_FRAMES = {1, 0};
    // Walk animation: frames 5, 4, 3, 2
    private static final int[] WALK_FRAMES = {5, 4, 3, 2};
    // Activated animation: frames 7, 6
    private static final int[] ACTIVATED_FRAMES = {7, 6};
    // Fuse animation: frames 8, 9
    private static final int[] FUSE_FRAMES = {8, 9};
    // Shrapnel animation: frames 10, 11
    private static final int[] SHRAPNEL_FRAMES = {10, 11};

    // --- State machine values (ob2ndRout / 2) ---
    private static final int STATE_WALK = 0;
    private static final int STATE_WAIT = 1;
    private static final int STATE_EXPLODE = 2;

    // From disassembly: move.b #3,obPriority(a0)
    private static final int RENDER_PRIORITY = 3;

    // --- Instance state ---
    private final LevelManager levelManager;
    private int currentX;
    private int currentY;
    private int xVelocity;
    private int yVelocity;
    private int xSubpixel;
    private int ySubpixel;
    private boolean facingLeft;
    private boolean destroyed;

    private int state;          // ob2ndRout / 2
    private int timer;          // bom_time (objoff_30)
    private int currentAnim;    // obAnim
    private int animTickCounter;

    public Sonic1BombBadnikInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, "Bomb");
        this.levelManager = levelManager;
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.xVelocity = 0;
        this.yVelocity = 0;
        this.xSubpixel = 0;
        this.ySubpixel = 0;

        // obStatus bit 0 is initialized from spawn data's render flags.
        // Bom_Main: bchg #0,obStatus(a0) toggles it.
        // facingLeft=true ↔ status bit 0=0; after bchg, new bit = !original.
        // So facingLeft = (new bit == 0) = (original bit == 1) = spawnBit0.
        boolean spawnBit0 = (spawn.renderFlags() & 1) != 0;
        this.facingLeft = spawnBit0;
        this.destroyed = false;

        int subtype = spawn.subtype();
        if (subtype == 0) {
            // Normal bomb body - set collision and start in walk state
            // move.b #$9A,obColType(a0)
            // bom_time starts at 0 from zero-init, so first frame of .walk
            // immediately transitions to .wait (timer -1 < 0).
            this.state = STATE_WALK;
            this.timer = 0;  // ROM: bom_time starts at 0 (zero-init)
            this.currentAnim = ANIM_STAND;
            this.animTickCounter = 0;
        }
        // Subtypes 4 (fuse) and 6 (shrapnel) are handled by dedicated child classes
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (destroyed) {
            return;
        }
        updateMovement(frameCounter, player);
        updateAnimation(frameCounter);
    }

    /**
     * Bom_Action: Main update dispatches to state-specific handler, then
     * AnimateSprite + RememberState.
     */
    private void updateMovement(int frameCounter, AbstractPlayableSprite player) {
        switch (state) {
            case STATE_WALK -> updateWalk(frameCounter, player);
            case STATE_WAIT -> updateWait(frameCounter, player);
            case STATE_EXPLODE -> updateExplode(frameCounter);
        }
    }

    /**
     * State 0 (.walk): Standing still, counting down timer.
     * Checks if Sonic is within range. When timer expires, starts walking.
     * <pre>
     * .walk:
     *     bsr.w   .chksonic
     *     subq.w  #1,bom_time(a0)
     *     bpl.s   .noflip
     *     addq.b  #2,ob2ndRout(a0)
     *     move.w  #1535,bom_time(a0)
     *     move.w  #$10,obVelX(a0)
     *     move.b  #1,obAnim(a0)
     *     bchg    #0,obStatus(a0)
     *     beq.s   .noflip
     *     neg.w   obVelX(a0)
     * </pre>
     */
    private void updateWalk(int frameCounter, AbstractPlayableSprite player) {
        checkSonic(player);
        if (state != STATE_WALK) {
            return; // chksonic may have transitioned to explode
        }

        timer--;
        if (timer < 0) {
            // Timer expired: start walking
            state = STATE_WAIT;
            timer = WALK_TIME;
            xVelocity = WALK_SPEED;
            setAnimation(ANIM_WALK);

            // bchg #0,obStatus(a0): toggle facing direction
            facingLeft = !facingLeft;
            // beq.s .noflip: branches when bit 0 is clear (facing right)
            // neg.w obVelX(a0): negate velocity when bit 0 is set (facing left)
            if (facingLeft) {
                xVelocity = -xVelocity;
            }
        }
    }

    /**
     * State 1 (.wait): Walking, applying velocity, counting down timer.
     * Checks if Sonic is within range. When timer expires, stops walking.
     * <pre>
     * .wait:
     *     bsr.w   .chksonic
     *     subq.w  #1,bom_time(a0)
     *     bmi.s   .stopwalking
     *     bsr.w   SpeedToPos
     *     rts
     *
     * .stopwalking:
     *     subq.b  #2,ob2ndRout(a0)
     *     move.w  #179,bom_time(a0)
     *     clr.w   obVelX(a0)
     *     move.b  #0,obAnim(a0)
     * </pre>
     */
    private void updateWait(int frameCounter, AbstractPlayableSprite player) {
        checkSonic(player);
        if (state != STATE_WAIT) {
            return; // chksonic may have transitioned to explode
        }

        timer--;
        if (timer < 0) {
            // Timer expired: stop walking, return to stand state
            state = STATE_WALK;
            timer = STAND_TIME;
            xVelocity = 0;
            setAnimation(ANIM_STAND);
        } else {
            // SpeedToPos: apply velocity
            applyVelocity();
        }
    }

    /**
     * State 2 (.explode): Fuse lit, counting down to explosion.
     * <pre>
     * .explode:
     *     subq.w  #1,bom_time(a0)
     *     bpl.s   .noexplode
     *     _move.b #id_ExplosionBomb,obID(a0)
     *     move.b  #0,obRoutine(a0)
     * </pre>
     */
    private void updateExplode(int frameCounter) {
        timer--;
        if (timer < 0) {
            // Timer expired: change to explosion (object $3F = ExplosionBomb)
            // In ROM this replaces the object in-place. In our engine, we spawn
            // a bomb explosion and destroy self.
            spawnBombExplosion();
            destroyed = true;
            setDestroyed(true);
            var objectManager = levelManager.getObjectManager();
            if (objectManager != null) {
                if (spawn.respawnTracked()) {
                    objectManager.markRemembered(spawn);
                } else {
                    objectManager.removeFromActiveSpawns(spawn);
                }
            }
        }
    }

    /**
     * .chksonic: Check if Sonic is within $60 pixels in both X and Y.
     * If so, transition to explode state and spawn a fuse child.
     * <pre>
     * .chksonic:
     *     move.w  (v_player+obX).w,d0
     *     sub.w   obX(a0),d0
     *     bcc.s   .isleft
     *     neg.w   d0
     * .isleft:
     *     cmpi.w  #$60,d0
     *     bhs.s   .outofrange
     *     move.w  (v_player+obY).w,d0
     *     sub.w   obY(a0),d0
     *     bcc.s   .isabove
     *     neg.w   d0
     * .isabove:
     *     cmpi.w  #$60,d0
     *     bhs.s   .outofrange
     *     tst.w   (v_debuguse).w
     *     bne.s   .outofrange
     *     [activate bomb, spawn fuse]
     * </pre>
     */
    private void checkSonic(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }

        int dx = player.getCentreX() - currentX;
        if (dx < 0) dx = -dx;
        if (dx >= PROXIMITY_RANGE) {
            return;
        }

        int dy = player.getCentreY() - currentY;
        if (dy < 0) dy = -dy;
        if (dy >= PROXIMITY_RANGE) {
            return;
        }

        // tst.w (v_debuguse).w / bne.s .outofrange
        if (player.isDebugMode()) {
            return;
        }

        // Activate: transition to explode state
        state = STATE_EXPLODE;
        timer = FUSE_TIME;
        xVelocity = 0;
        setAnimation(ANIM_ACTIVATED);

        // Spawn fuse child object
        spawnFuseChild(player);
    }

    /**
     * Spawns the fuse child object (subtype 4) at the bomb's position.
     * <pre>
     *     bsr.w   FindNextFreeObj
     *     bne.s   .outofrange
     *     _move.b #id_Bomb,obID(a1)
     *     move.w  obX(a0),obX(a1)
     *     move.w  obY(a0),obY(a1)
     *     move.w  obY(a0),bom_origY(a1)
     *     move.b  obStatus(a0),obStatus(a1)
     *     move.b  #4,obSubtype(a1)
     *     move.b  #3,obAnim(a1)
     *     move.w  #$10,obVelY(a1)
     *     btst    #1,obStatus(a0)
     *     beq.s   .normal
     *     neg.w   obVelY(a1)
     * .normal:
     *     move.w  #143,bom_time(a1)
     *     move.l  a0,bom_parent(a1)
     * </pre>
     */
    private void spawnFuseChild(AbstractPlayableSprite player) {
        var objectManager = levelManager.getObjectManager();
        if (objectManager == null) {
            return;
        }

        Sonic1BombFuseInstance fuse = new Sonic1BombFuseInstance(
                currentX, currentY, facingLeft, FUSE_TIME, FUSE_Y_SPEED, this, levelManager);
        objectManager.addDynamicObject(fuse);
    }

    /**
     * Spawns a bomb explosion (object $3F = ExplosionBomb).
     * From ExBom_Main: uses Map_ExplodeBomb, ArtTile_Explosion, plays sfx_Bomb (0xC4).
     * The explosion reuses the standard ExItem_Animate with 5 frames at 7 ticks each.
     */
    private void spawnBombExplosion() {
        var objectManager = levelManager.getObjectManager();
        if (objectManager == null) {
            return;
        }

        // Spawn explosion with bomb sound effect
        ExplosionObjectInstance explosion = new ExplosionObjectInstance(
                0x3F, currentX, currentY,
                levelManager.getObjectRenderManager());
        objectManager.addDynamicObject(explosion);

        // sfx_Bomb = $C4 = BOSS_EXPLOSION
        AudioManager.getInstance().playSfx(Sonic1Sfx.BOSS_EXPLOSION.id);
    }

    /**
     * Spawns 4 shrapnel pieces with predefined velocities from Bom_ShrSpeed.
     * Called by the fuse child when its timer expires.
     * <pre>
     *     moveq   #3,d1
     *     movea.l a0,a1
     *     lea     (Bom_ShrSpeed).l,a2
     *     bra.s   .makeshrapnel
     * .loop:
     *     bsr.w   FindNextFreeObj
     *     bne.s   .fail
     * .makeshrapnel:
     *     _move.b #id_Bomb,obID(a1)
     *     move.w  obX(a0),obX(a1)
     *     move.w  obY(a0),obY(a1)
     *     move.b  #6,obSubtype(a1)
     *     move.b  #4,obAnim(a1)
     *     move.w  (a2)+,obVelX(a1)
     *     move.w  (a2)+,obVelY(a1)
     *     move.b  #$98,obColType(a1)
     *     bset    #7,obRender(a1)
     * </pre>
     */
    void spawnShrapnel(int fuseX, int fuseY) {
        var objectManager = levelManager.getObjectManager();
        if (objectManager == null) {
            return;
        }

        for (int i = 0; i < SHRAPNEL_VELOCITIES.length; i++) {
            Sonic1BombShrapnelInstance shrapnel = new Sonic1BombShrapnelInstance(
                    fuseX, fuseY,
                    SHRAPNEL_VELOCITIES[i][0], SHRAPNEL_VELOCITIES[i][1],
                    levelManager);
            objectManager.addDynamicObject(shrapnel);
        }
    }

    private void setAnimation(int newAnim) {
        if (newAnim != currentAnim) {
            currentAnim = newAnim;
            animTickCounter = 0;
        }
    }

    private void updateAnimation(int frameCounter) {
        animTickCounter++;
    }

    /**
     * SpeedToPos: Apply X and Y velocity to position with subpixel precision.
     */
    private void applyVelocity() {
        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        xPos24 += xVelocity;
        currentX = xPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;

        int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
        yPos24 += yVelocity;
        currentY = yPos24 >> 8;
        ySubpixel = yPos24 & 0xFF;
    }

    /**
     * Returns the mapping frame index based on current animation state.
     * From Ani_Bomb:
     * <pre>
     * .stand:     dc.b $13, 1, 0, afEnd
     * .walk:      dc.b $13, 5, 4, 3, 2, afEnd
     * .activated: dc.b $13, 7, 6, afEnd
     * .fuse:      dc.b 3, 8, 9, afEnd
     * .shrapnel:  dc.b 3, $A, $B, afEnd
     * </pre>
     */
    private int getMappingFrame() {
        int[] frames;
        int speed;
        switch (currentAnim) {
            case ANIM_STAND -> { frames = STAND_FRAMES; speed = ANIM_SPEED_NORMAL; }
            case ANIM_WALK -> { frames = WALK_FRAMES; speed = ANIM_SPEED_NORMAL; }
            case ANIM_ACTIVATED -> { frames = ACTIVATED_FRAMES; speed = ANIM_SPEED_NORMAL; }
            case ANIM_FUSE -> { frames = FUSE_FRAMES; speed = ANIM_SPEED_FAST; }
            case ANIM_SHRAPNEL -> { frames = SHRAPNEL_FRAMES; speed = ANIM_SPEED_FAST; }
            default -> { return 0; }
        }
        int step = (animTickCounter / speed) % frames.length;
        return frames[step];
    }

    // --- TouchResponseProvider / TouchResponseAttackable ---

    @Override
    public int getCollisionFlags() {
        if (destroyed) {
            return 0;
        }
        // obColType = $9A: HURT category ($80) + size index $1A
        return 0x80 | (COLLISION_SIZE_INDEX & 0x3F);
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public void onPlayerAttack(AbstractPlayableSprite player, TouchResponseResult result) {
        // The bomb has HURT category ($80), so this should not normally be called.
        // However if the player is invincible, the bomb could be destroyed.
        // In ROM, React_ChkHurt returns without destroying the object when invincible.
        // The bomb only dies through its own explosion timer.
    }

    // --- Rendering ---

    @Override
    public boolean isPersistent() {
        // RememberState: persists while on screen
        return !destroyed && isOnScreenX(160);
    }

    @Override
    public int getPriorityBucket() {
        // obPriority = 3
        return RenderPriority.clamp(RENDER_PRIORITY);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (destroyed) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.BOMB);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        int frame = getMappingFrame();
        // ori.b #4,obRender(a0): bit 2 set = use obStatus bit 0 for X flip
        // facingLeft = true when status bit 0 is clear
        renderer.drawFrameIndex(frame, currentX, currentY, !facingLeft, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Yellow hitbox rectangle (collision size $1A: width $C, height $C)
        ctx.drawRect(currentX, currentY, 12, 12, 1f, 1f, 0f);

        // Cyan velocity arrow if walking
        if (xVelocity != 0 || yVelocity != 0) {
            int endX = currentX + (xVelocity >> 5);
            int endY = currentY + (yVelocity >> 5);
            ctx.drawArrow(currentX, currentY, endX, endY, 0f, 1f, 1f);
        }

        // State label
        String stateStr = switch (state) {
            case STATE_WALK -> "STAND";
            case STATE_WAIT -> "WALK";
            case STATE_EXPLODE -> "FUSE";
            default -> "?";
        };
        String dir = facingLeft ? "L" : "R";
        String label = "Bomb " + stateStr + " t" + timer + " f" + getMappingFrame() + " " + dir;
        ctx.drawWorldLabel(currentX, currentY, -2, label, Color.YELLOW);
    }

    // --- Position accessors ---

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(
                currentX, currentY,
                spawn.objectId(), spawn.subtype(), spawn.renderFlags(),
                spawn.respawnTracked(), spawn.rawYWord());
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }
}
