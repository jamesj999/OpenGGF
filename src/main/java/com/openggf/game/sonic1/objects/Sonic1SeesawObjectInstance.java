package com.openggf.game.sonic1.objects;

import com.openggf.camera.Camera;
import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x5E -- Seesaws (SLZ).
 * <p>
 * A tilting platform that responds to player position. When subtype is 0x00,
 * a spikeball child is spawned that sits on one end. The ball launches when
 * the seesaw tilts, and landing ball launches any standing player.
 * <p>
 * Subtype 0xFF: Seesaw without ball (ball not spawned).
 * Subtype 0x00: Seesaw with spikeball.
 * <p>
 * Disassembly reference: docs/s1disasm/_incObj/5E Seesaw.asm
 */
public class Sonic1SeesawObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SlopedSolidProvider {

    // From disassembly: move.b #$30,obActWid(a0)
    private static final int COLLISION_HALF_WIDTH = 0x30;

    // Slope data surface height used for collision
    private static final int COLLISION_HEIGHT = 8;

    // From disassembly: move.b #4,obPriority(a0)
    private static final int PRIORITY = 4;

    /**
     * Slope data for tilted state (frame 0 or 2).
     * From See_DataSlope = binclude "misc/slzssaw1.bin" (48 bytes).
     */
    private static final byte[] SLOPE_TILTED = {
            0x24, 0x24, 0x26, 0x28, 0x2A, 0x2C, 0x2A, 0x28,
            0x26, 0x24, 0x23, 0x22, 0x21, 0x20, 0x1F, 0x1E,
            0x1D, 0x1C, 0x1B, 0x1A, 0x19, 0x18, 0x17, 0x16,
            0x15, 0x14, 0x13, 0x12, 0x11, 0x10, 0x0F, 0x0E,
            0x0D, 0x0C, 0x0B, 0x0A, 0x09, 0x08, 0x07, 0x06,
            0x05, 0x04, 0x03, 0x02, 0x02, 0x02, 0x02, 0x02
    };

    /**
     * Slope data for flat state (frame 1).
     * From See_DataFlat = binclude "misc/slzssaw2.bin" (48 bytes, all 0x15).
     */
    private static final byte[] SLOPE_FLAT = {
            0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15,
            0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15,
            0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15,
            0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15,
            0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15,
            0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x15
    };

    // Saved original X position (see_origX = objoff_30)
    private final int origX;

    // Current target frame (see_frame = objoff_3A)
    // 0 = tilted left (left side up), 1 = flat, 2 = tilted right (right side up)
    private int targetFrame;

    // Current mapping frame (obFrame)
    // Transitions gradually toward targetFrame
    private int mappingFrame;

    // Stored player Y velocity when landing (see_speed = objoff_38)
    private int storedPlayerYVel;

    // Standing player tracking (obStatus bit 3 = player standing)
    private boolean playerStanding;

    // Child spikeball reference
    private Sonic1SeesawBallObjectInstance ball;
    private boolean ballSpawned;

    public Sonic1SeesawObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Seesaw");

        this.origX = spawn.x();

        // From disassembly See_Main:
        // btst #0,obStatus(a0) — is seesaw flipped?
        // beq.s .noflip
        // move.b #2,obFrame(a0) — use different frame
        boolean flipped = (spawn.renderFlags() & 0x01) != 0;
        if (flipped) {
            mappingFrame = 2;
        } else {
            mappingFrame = 0;
        }
        // move.b obFrame(a0),see_frame(a0)
        targetFrame = mappingFrame;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        ensureBallSpawned();

        // Validate standing player
        validateStandingPlayer(player);

        // See_Slope (routine 2): Store player Y velocity when approaching
        // move.w obVelY(a1),see_speed(a0)
        if (player != null && !playerStanding) {
            storedPlayerYVel = player.getYSpeed();
        }

        // Animate mapping frame toward target (See_ChgFrame)
        updateMappingFrame(targetFrame);
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (player == null) {
            return;
        }

        if (!contact.standing()) {
            // Player left the seesaw
            playerStanding = false;
            return;
        }

        // Player is standing on seesaw - track it
        // From See_Slope2: bsr.w See_ChkSide
        playerStanding = true;

        // Calculate which side the player is on (See_ChkSide)
        int targetAngle = calculateTargetAngle(player);
        targetFrame = targetAngle;
    }

    /**
     * Calculates target frame based on player position relative to seesaw center.
     * From See_ChkSide:
     * <pre>
     *   move.w obX(a0),d0
     *   sub.w  obX(a1),d0   ; d0 = seesaw_x - player_x
     *   bcc.s  .leftside    ; if >= 0, player is on left side -> d1 = 2
     *   neg.w  d0           ; make positive
     *   moveq  #0,d1        ; player is on right side -> d1 = 0
     *   .leftside:
     *   cmpi.w #8,d0
     *   bhs.s  See_ChgFrame ; if distance >= 8, use d1
     *   moveq  #1,d1        ; within dead zone -> flat
     * </pre>
     */
    private int calculateTargetAngle(AbstractPlayableSprite player) {
        // moveq #2,d1 (default: player on left = tilted right)
        int d1 = 2;
        int d0 = spawn.x() - player.getCentreX();
        if (d0 < 0) {
            // Player is on the right side
            d0 = -d0;
            d1 = 0;
        }
        // cmpi.w #8,d0
        if (d0 < 8) {
            d1 = 1; // Dead zone -> flat
        }
        return d1;
    }

    /**
     * Gradual visual transition of mapping frame toward target.
     * From See_ChgFrame:
     * <pre>
     *   move.b obFrame(a0),d0
     *   cmp.b  d1,d0        ; does frame need to change?
     *   beq.s  .noflip      ; if equal, done
     *   bcc.s  .loc_11772   ; if frame > target, go to subtract
     *   addq.b #2,d0        ; frame < target: add 2 first
     *   .loc_11772:
     *   subq.b #1,d0        ; then subtract 1
     *   move.b d0,obFrame(a0)
     *   move.b d1,see_frame(a0)
     *   bclr   #0,obRender(a0)
     *   btst   #1,obFrame(a0)
     *   beq.s  .noflip
     *   bset   #0,obRender(a0)
     * </pre>
     */
    private void updateMappingFrame(int target) {
        if (mappingFrame == target) {
            return;
        }

        int d0 = mappingFrame;
        if (d0 < target) {
            d0 += 2;
        }
        d0 -= 1;
        mappingFrame = d0;
        targetFrame = target;
    }

    /**
     * Gets the current target frame (see_frame / objoff_3A) for the ball to query.
     */
    public int getTargetFrame() {
        return targetFrame;
    }

    /**
     * Gets the current mapping frame (obFrame) for the ball to query.
     * Used for ball Y offset calculation and player launch comparison.
     */
    public int getMappingFrame() {
        return mappingFrame;
    }

    /**
     * Gets the stored player Y velocity for ball launch power (see_speed / objoff_38).
     */
    public int getStoredPlayerYVel() {
        return storedPlayerYVel;
    }

    /**
     * Sets the target frame from the ball when it lands.
     * Also clears the player standing bit and triggers potential player launch.
     */
    public void setTargetFrame(int frame) {
        this.targetFrame = frame;
    }

    /**
     * Checks if a player is standing on this seesaw (obStatus bit 3).
     */
    public boolean isPlayerStanding() {
        return playerStanding;
    }

    /**
     * Clears the player standing tracking after launch.
     */
    public void clearPlayerStanding() {
        this.playerStanding = false;
    }

    /**
     * Gets the standing player reference for the ball to launch.
     */
    public AbstractPlayableSprite getStandingPlayer() {
        if (!playerStanding) {
            return null;
        }
        // Get from SpriteManager - the standing player is always the main character
        var sprites = SpriteManager.getInstance().getAllSprites();
        for (var sprite : sprites) {
            if (sprite instanceof AbstractPlayableSprite aps) {
                return aps;
            }
        }
        return null;
    }

    private void validateStandingPlayer(AbstractPlayableSprite player) {
        if (playerStanding && player != null) {
            if (player.getAir() || !isPlayerInXRange(player)) {
                playerStanding = false;
            }
        }
    }

    private boolean isPlayerInXRange(AbstractPlayableSprite player) {
        int relX = player.getCentreX() - spawn.x() + COLLISION_HALF_WIDTH;
        return relX >= 0 && relX < COLLISION_HALF_WIDTH * 2;
    }

    private void ensureBallSpawned() {
        if (ballSpawned) {
            return;
        }
        ballSpawned = true;

        // From See_Main: tst.b obSubtype(a0) / bne.s .noball
        // Only spawn ball when subtype is 0
        if (spawn.subtype() != 0) {
            return;
        }

        boolean flipped = (spawn.renderFlags() & 0x01) != 0;

        ball = new Sonic1SeesawBallObjectInstance(this, spawn.x(), spawn.y(), flipped);

        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager != null) {
            objectManager.addDynamicObject(ball);
        }
    }

    // ---- SolidObjectProvider ----

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(COLLISION_HALF_WIDTH, COLLISION_HEIGHT, COLLISION_HEIGHT);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return !isDestroyed();
    }

    // ---- SlopedSolidProvider ----

    @Override
    public byte[] getSlopeData() {
        // See_Slope / See_Slope2: btst #0,obFrame(a0) / beq.s .notflat
        // Frame 1 or 3 (bit 0 set) = flat, frame 0 or 2 = tilted
        return ((mappingFrame & 1) != 0) ? SLOPE_FLAT : SLOPE_TILTED;
    }

    @Override
    public int getSlopeBaseline() {
        return COLLISION_HEIGHT;
    }

    @Override
    public boolean isSlopeFlipped() {
        // Slope is flipped when bit 1 of obFrame is set (frame 2)
        // From See_ChgFrame: btst #1,obFrame(a0) / beq.s .noflip / bset #0,obRender(a0)
        return (mappingFrame & 2) != 0;
    }

    // ---- Rendering ----

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.SLZ_SEESAW);
        if (renderer == null) return;

        // From See_ChgFrame:
        // bclr #0,obRender(a0) — clear x-flip
        // btst #1,obFrame(a0) — if bit 1 set (frame 2)
        // bset #0,obRender(a0) — set x-flip
        boolean useHFlip = (mappingFrame & 2) != 0;

        // Mapping table has entries: 0=sloping, 1=flat, 2=sloping, 3=flat
        // Frame 2 uses the same .sloping data as frame 0, rendered with x-flip
        renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), useHFlip, false);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    // ---- Lifecycle ----

    @Override
    public void onUnload() {
        // Destroy ball child so it doesn't persist after the seesaw is unloaded.
        // Without this, the ball survives and a duplicate is created on respawn.
        if (ball != null) {
            ball.setDestroyed(true);
        }
    }

    // ---- Persistence ----

    @Override
    public boolean isPersistent() {
        // From main object loop: uses see_origX for range check
        return !isDestroyed() && isOrigXOnScreen();
    }

    private boolean isOrigXOnScreen() {
        var camera = Camera.getInstance();
        if (camera == null) {
            return true;
        }
        int objRounded = origX & 0xFF80;
        int camRounded = (camera.getX() - 128) & 0xFF80;
        int distance = (objRounded - camRounded) & 0xFFFF;
        return distance <= (128 + 320 + 192);
    }

    // ---- Debug rendering ----

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        int x = spawn.x();
        int y = spawn.y();

        // Draw collision box (green)
        int left = x - COLLISION_HALF_WIDTH;
        int right = x + COLLISION_HALF_WIDTH;
        int top = y - COLLISION_HEIGHT;
        int bottom = y + COLLISION_HEIGHT;
        ctx.drawLine(left, top, right, top, 0.0f, 1.0f, 0.0f);
        ctx.drawLine(right, top, right, bottom, 0.0f, 1.0f, 0.0f);
        ctx.drawLine(right, bottom, left, bottom, 0.0f, 1.0f, 0.0f);
        ctx.drawLine(left, bottom, left, top, 0.0f, 1.0f, 0.0f);

        // Draw center (red cross)
        ctx.drawLine(x - 4, y, x + 4, y, 1.0f, 0.0f, 0.0f);
        ctx.drawLine(x, y - 4, x, y + 4, 1.0f, 0.0f, 0.0f);

        // Draw frame state (yellow text indicator)
        String state = "F" + mappingFrame + "/T" + targetFrame;
        // (state shown as cross color: cyan = flat, yellow = tilted)
        float cr = (mappingFrame == 1) ? 0f : 1f;
        float cg = 1f;
        float cb = (mappingFrame == 1) ? 1f : 0f;
        ctx.drawLine(x - 2, y - 10, x + 2, y - 10, cr, cg, cb);
    }


}
