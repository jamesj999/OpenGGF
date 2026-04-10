package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * S3K Obj $94 — Blastoid (HCZ Act 1).
 *
 * <p>A stationary turret badnik embedded in walls. Detects the nearest player
 * within 128 pixels horizontally ({@code Find_SonicTails}), then fires 3
 * projectiles in a burst before returning to idle. On defeat, sets a
 * {@link Sonic3kLevelTriggerManager} flag that can trigger
 * {@code CollapsingBridge} collapse.
 *
 * <p>Based on {@code Obj_Blastoid} (sonic3k.asm, lines 183566–183674).
 *
 * <h3>Subtype:</h3>
 * Bits 0-3: trigger array index — set to $FF on defeat
 * (ROM: {@code st (a3,d0.w)}). Paired with HCZ CollapsingBridge instances
 * whose TRIGGER mode uses the same index.
 *
 * <h3>State machine:</h3>
 * <ul>
 *   <li>Routine 2 (DETECT): polls nearest player X distance, activates at &lt; $80</li>
 *   <li>Routine 4 (ATTACK): runs Animate_RawMultiDelay, fires on frame 1
 *       transitions while on-screen, resets to DETECT on $F4 end command</li>
 * </ul>
 *
 * <h3>Animation (byte_87A10):</h3>
 * Frame 0 (128f idle) → Frame 1 (5f, FIRE) → Frame 0 (10f) →
 * Frame 1 (5f, FIRE) → Frame 0 (10f) → Frame 1 (5f, FIRE) →
 * Frame 0 (64f idle) → callback: reset to DETECT.
 *
 * <h3>Collision:</h3>
 * Body {@code $D7}: custom category with size 23. Implements both
 * {@code TouchResponseAttackable} (defeatable by attacking player) and
 * standard hurt (damages non-attacking player). On defeat, sets the
 * trigger array entry for the paired CollapsingBridge.
 *
 * <h3>Projectile:</h3>
 * Fires from mouth offset (-20, -7) with velocity (-$200, -$100), no gravity.
 * X offset/velocity negated when parent faces right. Alternates mapping frames
 * 2 and 3 every tick. Shield bounce deflectable (bit 3).
 */
public final class BlastoidBadnikInstance extends AbstractS3kBadnikInstance {

    // --- Constants from ObjDat_Blastoid ---

    // collision_flags = $D7: size = $D7 & $3F = $17 (23)
    private static final int COLLISION_SIZE_INDEX = 0x17;

    // dc.w $280 → priority bucket 5
    private static final int PRIORITY_BUCKET = 5;

    // --- Detection ---

    // loc_87952: cmpi.w #$80,d2
    private static final int DETECT_RANGE = 0x80;

    // --- Projectile constants (ChildObjDat_879F8) ---

    // dc.b -$14,-7 — spawn offset relative to parent
    private static final int PROJECTILE_X_OFFSET = -0x14;
    private static final int PROJECTILE_Y_OFFSET = -7;

    // dc.w -$200,-$100 — initial velocity
    private static final int PROJECTILE_X_VEL = -0x200;
    private static final int PROJECTILE_Y_VEL = -0x100;

    // ObjDat3_879EC: collision_flags = $98 → size = $18 (24)
    private static final int PROJECTILE_COLLISION_SIZE = 0x18;

    // ObjDat3_879EC: dc.w $280 → priority bucket 5
    private static final int PROJECTILE_PRIORITY = 5;

    // --- Animation data (byte_87A10) ---
    // Animate_RawMultiDelay format: (frame, delay) pairs, terminated by $F4.
    // Delay N means the frame displays for N+1 ticks before advancing.

    // dc.b 0,$7F / 1,4 / 0,9 / 1,4 / 0,9 / 1,4 / 0,$3F / $F4
    private static final int[] ANIM_FRAMES = {0, 1, 0, 1, 0, 1, 0};
    private static final int[] ANIM_DELAYS = {0x7F, 4, 9, 4, 9, 4, 0x3F};

    // Frame 1 = mouth open (firing frame)
    private static final int FIRE_FRAME = 1;

    // --- State ---

    private enum State { DETECT, ATTACK }

    private State state = State.DETECT;
    private final int triggerIndex; // subtype & 0x0F

    // Animate_RawMultiDelay state — unsigned byte timer (0-255).
    // The $F4 command handler clears anim_frame_timer to 0 (ROM: clr.b anim_frame_timer),
    // so re-entry to ATTACK always starts with timer=0 → immediate advance.
    private int animTimer;
    private int animIndex;

    public BlastoidBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Blastoid",
                Sonic3kObjectArtKeys.HCZ_BLASTOID, COLLISION_SIZE_INDEX, PRIORITY_BUCKET);
        // move.b subtype(a0),d0 / andi.w #$F,d0
        this.triggerIndex = spawn.subtype() & 0x0F;
        this.mappingFrame = 0;
        // animTimer starts at 0 (matches zeroed object RAM on spawn)
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (destroyed) return;

        // Obj_WaitOffscreen parity: the ROM entry point begins with
        // jsr (Obj_WaitOffscreen).l which suppresses all logic every frame
        // until render_flags bit 7 (on-screen X) is set. Without this guard
        // the ObjectManager's spawn window activates the Blastoid before it
        // is visible, letting it detect the player and enter ATTACK early.
        if (!isOnScreenX()) return;

        switch (state) {
            case DETECT -> updateDetect((AbstractPlayableSprite) playerEntity);
            case ATTACK -> updateAttack();
        }
    }

    // ── Routine 2: Detect ────────────────────────────────────────────────

    /**
     * Wait for nearest player within detection range.
     * <pre>
     * loc_87952:
     *     jsr    Find_SonicTails(pc)     ; d2 = abs X distance to nearest player
     *     cmpi.w #$80,d2
     *     blo.s  loc_8795E               ; if less, activate
     *     rts
     * </pre>
     */
    private void updateDetect(AbstractPlayableSprite player) {
        int distance = findNearestPlayerXDistance(player);
        if (distance >= DETECT_RANGE) return;

        // loc_8795E: transition to ATTACK (routine 4)
        // move.b #4,routine(a0)
        // move.l #byte_87A10,$30(a0)  — reset animation script pointer
        // move.l #loc_879A0,$34(a0)   — set end-of-animation callback
        state = State.ATTACK;
        animIndex = -1; // Will advance to 0 on first tick
        // animTimer is NOT reset — ROM preserves anim_frame_timer across transitions
    }

    // ── Routine 4: Attack animation ──────────────────────────────────────

    /**
     * Run firing animation and spawn projectiles on frame 1 transitions.
     * <pre>
     * loc_87976:
     *     jsr    Animate_RawMultiDelay(pc)
     *     tst.w  d2                       ; 0=timer running, -1=anim ended
     *     beq.s  locret_87974
     *     bmi.s  locret_87974             ; skip fire on end callback
     *     cmpi.b #1,mapping_frame(a0)     ; mouth open?
     *     bne.s  locret_87974
     *     tst.b  render_flags(a0)         ; on screen?
     *     bpl.w  locret_87974
     *     ; fire projectile
     * </pre>
     */
    private void updateAttack() {
        // Animate_RawMultiDelay: subq.b #1,anim_frame_timer(a0) / bpl.s return
        // 68k bpl branches when N flag clear (result 0x00-0x7F); falls through
        // when result is negative in signed byte (0x80-0xFF).
        animTimer = (animTimer - 1) & 0xFF;
        if (animTimer < 0x80) return; // bpl: result positive → timer still running (d2=0)

        // Timer expired (result negative) → advance to next animation entry
        animIndex++;
        if (animIndex >= ANIM_FRAMES.length) {
            // $F4 terminator: loc_84600 → clr.b anim_frame_timer / callback loc_879A0
            animTimer = 0; // ROM: clr.b anim_frame_timer(a0)
            state = State.DETECT;
            mappingFrame = 0;
            return; // d2=-1: skip fire check
        }

        // Load new frame and delay
        int newFrame = ANIM_FRAMES[animIndex];
        mappingFrame = newFrame;
        animTimer = ANIM_DELAYS[animIndex];

        // d2=1: frame changed — check if we should fire
        // cmpi.b #1,mapping_frame(a0) / bne.s skip
        // tst.b render_flags(a0)      / bpl.w skip  (bit 7 = X-on-screen flag)
        if (newFrame == FIRE_FRAME && isOnScreenX()) {
            fireProjectile();
        }
    }

    // ── Projectile spawning ──────────────────────────────────────────────

    /**
     * Spawn a projectile from the Blastoid's mouth.
     * <pre>
     * ChildObjDat_879F8:
     *     dc.b -$14,-7               ; X/Y offset from parent
     *     dc.w -$200,-$100           ; X/Y velocity
     * CreateChild5_ComplexAdjusted negates X offset and X velocity
     * when render_flags bit 0 (H-flip) is set.
     * </pre>
     */
    private void fireProjectile() {
        // moveq #signextendB(sfx_Projectile),d0 / jsr (Play_SFX).l
        services().playSfx(Sonic3kSfx.PROJECTILE.id);

        int xOff = PROJECTILE_X_OFFSET;
        int xVel = PROJECTILE_X_VEL;

        // CreateChild5_ComplexAdjusted: negate X when parent faces right
        if (!facingLeft) {
            xOff = -xOff;
            xVel = -xVel;
        }

        // CreateChild5_ComplexAdjusted does NOT copy render_flags bit 0 to child;
        // SetUp_ObjAttributes only sets bit 2 (world coords). Projectile never H-flips.
        BlastoidProjectile projectile = new BlastoidProjectile(
                spawn,
                currentX + xOff,
                currentY + PROJECTILE_Y_OFFSET,
                xVel,
                PROJECTILE_Y_VEL);

        ObjectServices svc = tryServices();
        if (svc != null && svc.objectManager() != null) {
            svc.objectManager().addDynamicObject(projectile);
        }
    }

    // ── Player proximity ─────────────────────────────────────────────────

    /**
     * Find nearest player (Sonic or Tails) by horizontal distance.
     * ROM: {@code Find_SonicTails} — compares Player_1 and Player_2 X distances,
     * returns d2 = absolute X distance to the closer one.
     */
    private int findNearestPlayerXDistance(AbstractPlayableSprite mainPlayer) {
        int nearest = mainPlayer != null && !mainPlayer.getDead()
                ? Math.abs(currentX - mainPlayer.getCentreX()) : Integer.MAX_VALUE;
        ObjectServices svc = tryServices();
        if (svc == null) return nearest;
        for (PlayableEntity sidekick : svc.sidekicks()) {
            if (!(sidekick instanceof AbstractPlayableSprite s) || s.getDead()) continue;
            int dist = Math.abs(currentX - s.getCentreX());
            if (dist < nearest) nearest = dist;
        }
        return nearest;
    }

    // ── Defeat + trigger ─────────────────────────────────────────────────

    /**
     * Override defeat to set level trigger array before destruction.
     * <pre>
     * loc_879C4:                          ; enemy defeated path
     *     move.b subtype(a0),d0
     *     andi.w #$F,d0
     *     lea    (Level_trigger_array).w,a3
     *     st     (a3,d0.w)               ; set byte to $FF
     *     jsr    EnemyDefeated(pc)
     * </pre>
     */
    @Override
    public void onPlayerAttack(PlayableEntity playerEntity, TouchResponseResult result) {
        // Set trigger flag for paired CollapsingBridge
        Sonic3kLevelTriggerManager.setAll(triggerIndex);
        // Standard defeat: explosion + animal + points
        super.onPlayerAttack(playerEntity, result);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Projectile inner class
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Blastoid projectile — linear velocity with no gravity, animated between
     * mapping frames 2 and 3 every tick. Shield bounce deflectable.
     *
     * <p>ROM: {@code loc_86D4A} child object with {@code Move_AnimateRaw}
     * (MoveSprite2 + Animate_Raw) and {@code byte_87A1F} animation script
     * (speed 0, frames [2, 3], $FC loop).
     *
     * <p>Touch collision: HURT category ({@code $98 = $80 | $18}).
     * Shield reaction: bounce (bit 3) via {@code bset #3,shield_reaction(a0)}.
     */
    private static final class BlastoidProjectile extends AbstractObjectInstance
            implements TouchResponseProvider {

        // loc_86D4A: bset #3,shield_reaction(a0)
        private static final int SHIELD_REACTION_BOUNCE = 1 << 3;
        private static final int DEFLECT_SPEED = 0x800;

        // byte_87A1F: Animate_Raw speed 0, frames [2, 3], $FC loop
        private static final int FRAME_A = 2;
        private static final int FRAME_B = 3;

        // Sprite_CheckDeleteTouchXY uses generous bounds: ~$280 horizontal, ~$200 vertical.
        // With a 320×224 viewport, this gives ~160px margin on each side.
        private static final int OFF_SCREEN_MARGIN = 160;

        private int currentX;
        private int currentY;
        private int xVelocity;
        private int yVelocity;
        private int xSubpixel;
        private int ySubpixel;
        private int animFrame;
        private boolean collisionEnabled = true;

        BlastoidProjectile(ObjectSpawn ownerSpawn, int x, int y,
                           int xVel, int yVel) {
            super(ownerSpawn, "BlastoidProjectile");
            this.currentX = x;
            this.currentY = y;
            this.xVelocity = xVel;
            this.yVelocity = yVel;
            this.animFrame = FRAME_A;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            // Move_AnimateRaw → MoveSprite2: velocity to position, no gravity
            int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
            int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
            xPos24 += xVelocity;
            yPos24 += yVelocity;
            currentX = xPos24 >> 8;
            currentY = yPos24 >> 8;
            xSubpixel = xPos24 & 0xFF;
            ySubpixel = yPos24 & 0xFF;

            // Animate_Raw speed 0: toggle frame every tick
            // byte_87A1F: dc.b 0, 2, 3, $FC
            animFrame = (animFrame == FRAME_A) ? FRAME_B : FRAME_A;

            // Sprite_CheckDeleteTouchXY: generous off-screen deletion
            if (!isOnScreen(OFF_SCREEN_MARGIN)) {
                setDestroyed(true);
            }
        }

        @Override
        public int getCollisionFlags() {
            if (!collisionEnabled) return 0;
            // ObjDat3_879EC: collision_flags = $98 (HURT | size $18)
            return 0x80 | PROJECTILE_COLLISION_SIZE;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }

        @Override
        public int getShieldReactionFlags() {
            return SHIELD_REACTION_BOUNCE;
        }

        @Override
        public boolean onShieldDeflect(PlayableEntity playerEntity) {
            AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
            if (player == null) return false;

            int dx = player.getCentreX() - currentX;
            int dy = player.getCentreY() - currentY;
            int angle = TrigLookupTable.calcAngle(
                    saturateToShort(dx), saturateToShort(dy));

            // ROM: Touch_ChkHurt_Bounce_Projectile / ShieldTouchResponse
            xVelocity = -((TrigLookupTable.cosHex(angle) * DEFLECT_SPEED) >> 8);
            yVelocity = -((TrigLookupTable.sinHex(angle) * DEFLECT_SPEED) >> 8);
            collisionEnabled = false;
            return true;
        }

        @Override
        public ObjectSpawn getSpawn() {
            return buildSpawnAt(currentX, currentY);
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
            return PROJECTILE_PRIORITY;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager rm = services().renderManager();
            if (rm == null) return;
            PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.HCZ_BLASTOID);
            if (renderer == null || !renderer.isReady()) return;
            // ROM: render_flags bit 0 never set on child — no H-flip
            renderer.drawFrameIndex(animFrame, currentX, currentY, false, false);
        }

        private static short saturateToShort(int v) {
            return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
        }
    }
}
