package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.debug.DebugColor;
import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x3A - HCZ Hand Launcher (Hydrocity Zone).
 * <p>
 * A two-object mechanism: a hand-shaped platform (this object) and a child arm
 * visual ({@link HandLauncherArmChild}). When a player stands on the platform
 * near its centre, the hand grabs them, raises to the top, then launches them
 * horizontally at high speed. The player can escape early by pressing A/B/C,
 * which launches them with reduced speed and an upward arc.
 * <p>
 * State machine (code-pointer-swapping in ROM):
 * <ol>
 *   <li><b>IDLE:</b> Hand waits at rest (Y-offset 0x50). When a player is in
 *       horizontal range, the arm lowers. When the arm reaches 0x18 and the
 *       player is standing close to centre, the hand grabs them — raising to
 *       the top (0x00) then transitioning to LAUNCHING.</li>
 *   <li><b>LAUNCHING:</b> 59-frame pre-launch timer (player can still escape).
 *       After timer expires, the arm descends. At Y-offset 0x18 the player is
 *       released with full horizontal velocity. The arm continues to 0x50,
 *       then returns to IDLE.</li>
 * </ol>
 * <p>
 * Character-specific: Tails gets a smaller Y collision radius (0x0F vs 0x13)
 * when grabbed.
 * <p>
 * ROM references: Obj_HCZHandLauncher (sonic3k.asm:65730), loc_30B58 (IDLE),
 * loc_30C06 (LAUNCH), sub_30CE0/sub_30CF8 (button check / grab handler),
 * sub_30C7C/sub_30C8C (launch release), loc_30DEC (child arm).
 */
public class HCZHandLauncherObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOG = Logger.getLogger(HCZHandLauncherObjectInstance.class.getName());

    // ===== Art / render constants =====
    // ROM: make_art_tile(ArtTile_HCZMisc+$1A, 1, 0)
    private static final String ART_KEY = Sonic3kObjectArtKeys.HCZ_HAND_LAUNCHER;
    private static final int PRIORITY_NORMAL = 4;   // ROM: $200
    private static final int PRIORITY_GRABBED = 1;  // ROM: $80
    private static final int PRIORITY_CHILD = 5;    // ROM: $280

    // ===== Mapping frames =====
    private static final int FRAME_ARM_EXTENDED = 6; // Normal arm/cup (main object)
    private static final int FRAME_GRABBED = 7;       // Compact grabbed frame (main object)
    private static final int CHILD_FRAME_COUNT = 6;   // Child cycles frames 0-5

    // ===== Y-offset positions (from disassembly $30(a0)) =====
    // ROM: move.w #$50,$30(a0) (init), subq.w #8,$30(a0) (lower), addq.w #8,$30(a0) (raise)
    private static final int Y_OFFSET_REST = 0x50;    // 80px below base — fully retracted
    private static final int Y_OFFSET_GRAB = 0x18;    // 24px below base — grab/release threshold
    private static final int Y_OFFSET_TOP = 0x00;     // at base — fully raised
    private static final int ARM_SPEED = 8;            // units per frame

    // ===== Solid object dimensions =====
    // ROM: move.w #$20,d1 / move.w #$11,d3 — SolidObjectTop args
    private static final int SOLID_HALF_WIDTH = 0x20;  // 32px
    private static final int SOLID_HALF_HEIGHT = 0x11; // 17px

    // ===== Player detection / grab =====
    // ROM: subi.w #$20,d1 / cmpi.w #$40,d0 — horizontal proximity window
    private static final int DETECT_HALF_WIDTH = 0x20;  // 32px each side of centre
    // ROM: addi.w #8,d0 / cmpi.w #2*8,d0 — grab X proximity
    private static final int GRAB_X_OFFSET = 8;
    private static final int GRAB_X_RANGE = 16;         // 2*8 = 16px

    // ===== Timers =====
    // ROM: move.w #20-1,$36(a0) — pre-grab delay
    private static final int TIMER_PRE_GRAB = 19;
    // ROM: move.w #60-1,$36(a0) — pre-launch pause at top
    private static final int TIMER_PRE_LAUNCH = 59;

    // ===== Launch velocities (sub_30C8C) =====
    // ROM: move.w #$1000,ground_vel(a1) / move.w #$1000,x_vel(a1) / move.w #0,y_vel(a1)
    private static final int LAUNCH_X_VEL = 0x1000;
    private static final int LAUNCH_GROUND_VEL = 0x1000;

    // ===== Escape velocities (sub_30CF8) =====
    // ROM: move.w #$800,ground_vel(a1) / move.w #$800,x_vel(a1) / move.w #-$400,y_vel(a1)
    private static final int ESCAPE_X_VEL = 0x800;
    private static final int ESCAPE_Y_VEL = -0x400;
    private static final int ESCAPE_GROUND_VEL = 0x800;

    // ===== Grab player setup =====
    // ROM: move.w #$1000,ground_vel(a1)
    private static final int GRAB_GROUND_VEL = 0x1000;
    // ROM: subq.w #2,x_pos(a1)
    private static final int GRAB_X_SNAP_OFFSET = 2;
    // ROM: move.b #$13,y_radius(a1) (Sonic/Knuckles), move.b #$F,y_radius(a1) (Tails)
    private static final int GRAB_Y_RADIUS_DEFAULT = 0x13;
    private static final int GRAB_Y_RADIUS_TAILS = 0x0F;
    // ROM: move.b #9,x_radius(a1)
    private static final int GRAB_X_RADIUS = 9;

    // ===== State machine =====
    private enum State { IDLE, LAUNCHING }

    // ===== Instance state =====
    private final int baseX;
    private final int baseY;
    private final boolean facingLeft; // ROM: status bit 0

    private State state = State.IDLE;
    private int yOffset;              // $30(a0) — current vertical offset from base
    private int timer;                // $36(a0) — countdown timer
    private boolean anyGrabbed;       // $34(a0) — true if any player is grabbed
    private final boolean[] playerGrabbed = new boolean[2]; // $35(a0) per-player bits
    private int currentY;             // computed: baseY + yOffset
    private int mappingFrame = FRAME_ARM_EXTENDED;
    private int priority = PRIORITY_NORMAL;

    // Child object state
    private HandLauncherArmChild armChild;
    private boolean childSpawned;

    // ===== Solid platform state =====
    // Whether the solid top should be active (disabled during launch descent past release point)
    private boolean solidActive = true;

    public HCZHandLauncherObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HCZHandLauncher");
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.facingLeft = (spawn.renderFlags() & 1) != 0;
        this.yOffset = Y_OFFSET_REST;
        this.currentY = baseY + yOffset;
    }

    // ==================================================================
    // Update
    // ==================================================================

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        // Spawn child on first frame (no init() hook on AbstractObjectInstance)
        if (!childSpawned) {
            childSpawned = true;
            try {
                armChild = spawnChild(() -> new HandLauncherArmChild(
                        new ObjectSpawn(baseX, baseY, Sonic3kObjectIds.HCZ_HAND_LAUNCHER,
                                0, spawn.renderFlags(), false, 0),
                        this));
            } catch (Exception e) {
                LOG.warning("Failed to spawn arm child: " + e.getMessage());
            }
        }

        AbstractPlayableSprite player = (playerEntity instanceof AbstractPlayableSprite)
                ? (AbstractPlayableSprite) playerEntity : null;

        int prevY = currentY;

        switch (state) {
            case IDLE -> updateIdle(player);
            case LAUNCHING -> updateLaunching(player);
        }

        // Update position: y_pos = base + offset
        // ROM: move.w $30(a0),d0 / add.w $32(a0),d0 / move.w d0,y_pos(a0)
        currentY = baseY + yOffset;

        // Move grabbed players with the hand as it rises/descends.
        // ROM: SolidObjectTop repositions riding players each frame, but in the engine
        // isSolidFor() returns false for controlled players so the solid contact system
        // won't track them. We apply the Y delta manually to keep them on the hand.
        int deltaY = currentY - prevY;
        if (deltaY != 0 && anyGrabbed) {
            repositionGrabbedPlayers(player, deltaY);
        }
    }

    /**
     * Shifts all grabbed players by the hand's Y movement delta so they
     * ride the hand as it rises (pre-launch) and descends (launch).
     */
    private void repositionGrabbedPlayers(AbstractPlayableSprite player, int deltaY) {
        if (player != null && playerGrabbed[0]) {
            player.setY((short) (player.getY() + deltaY));
        }
        int pi = 1;
        for (PlayableEntity sidekick : services().sidekicks()) {
            if (pi < playerGrabbed.length && playerGrabbed[pi]
                    && sidekick instanceof AbstractPlayableSprite sp) {
                sp.setY((short) (sp.getY() + deltaY));
            }
            pi++;
        }
    }

    // ------------------------------------------------------------------
    // IDLE state (loc_30B58)
    // ------------------------------------------------------------------

    private void updateIdle(AbstractPlayableSprite player) {
        boolean playerInRange = false;

        // ROM: Check P1 X proximity
        // move.w x_pos(a0),d1 / subi.w #$20,d1 / move.w (Player_1+x_pos).w,d0
        // sub.w d1,d0 / cmpi.w #$40,d0 / blo.s loc_30B78
        if (player != null) {
            playerInRange = isPlayerInHorizontalRange(player);
        }

        // ROM: Check P2 if P1 not in range
        if (!playerInRange) {
            for (PlayableEntity sidekick : services().sidekicks()) {
                if (sidekick instanceof AbstractPlayableSprite sp && isPlayerInHorizontalRange(sp)) {
                    playerInRange = true;
                    break;
                }
            }
        }

        if (playerInRange) {
            if (anyGrabbed) {
                // ROM: loc_30B78 — grabbed path
                mappingFrame = FRAME_GRABBED;
                priority = PRIORITY_GRABBED;

                if (timer > 0) {
                    // ROM: tst.w $36(a0) / subq.w #1,$36(a0)
                    timer--;
                    processButtonCheckAllPlayers(player);
                } else if (yOffset > Y_OFFSET_TOP) {
                    // ROM: tst.w $30(a0) / subq.w #8,$30(a0) — rise
                    yOffset -= ARM_SPEED;
                    processButtonCheckAllPlayers(player);
                } else {
                    // ROM: Reached top — transition to LAUNCHING
                    // move.l #loc_30C06,(a0) / move.w #60-1,$36(a0)
                    state = State.LAUNCHING;
                    timer = TIMER_PRE_LAUNCH;
                    processButtonCheckAllPlayers(player);
                }
            } else {
                // ROM: loc_30BB0 — not grabbed, arm lowering/watching
                timer = TIMER_PRE_GRAB;
                mappingFrame = FRAME_ARM_EXTENDED;
                priority = PRIORITY_NORMAL;

                if (yOffset > Y_OFFSET_GRAB) {
                    // ROM: cmpi.w #$18,$30(a0) / bls.s loc_30BD0 / subq.w #8,$30(a0)
                    yOffset -= ARM_SPEED;
                    // Skip grab check while still lowering (ROM branches to loc_30BE2)
                } else {
                    // ROM: bsr.w sub_30CE0 — arm at grab threshold, check for grab
                    processButtonCheckAllPlayers(player);
                }
            }
        } else {
            // ROM: loc_30BD6 — no player in range, retract arm
            if (yOffset < Y_OFFSET_REST) {
                yOffset += ARM_SPEED;
            }
        }

        // Solid top is always active during IDLE
        solidActive = true;
    }

    // ------------------------------------------------------------------
    // LAUNCHING state (loc_30C06)
    // ------------------------------------------------------------------

    private void updateLaunching(AbstractPlayableSprite player) {
        if (timer > 0) {
            // ROM: tst.w $36(a0) / subq.w #1,$36(a0) / bsr.w sub_30CE0
            timer--;
            processButtonCheckAllPlayers(player);
        } else {
            // ROM: Timer expired — descent phase
            if (yOffset == Y_OFFSET_REST) {
                // ROM: cmpi.w #$50,$30(a0) / bne.s — reached bottom (exact match)
                // move.b #0,$34(a0) / move.l #loc_30B58,(a0)
                anyGrabbed = false;
                state = State.IDLE;
                playSfx(Sonic3kSfx.DASH.id);
            } else {
                // ROM: Check midpoint for player release
                if (yOffset == Y_OFFSET_GRAB) {
                    // ROM: cmpi.w #$18,$30(a0) / bsr.w sub_30C7C
                    launchReleaseAllPlayers(player);
                    mappingFrame = FRAME_ARM_EXTENDED;
                    priority = PRIORITY_NORMAL;
                }

                // ROM: addq.w #8,$30(a0) — descend
                yOffset += ARM_SPEED;
            }
        }

        // ROM: SolidObjectTop only called when $30 <= $18
        // cmpi.w #$18,$30(a0) / bhi.s loc_30C76
        solidActive = (yOffset <= Y_OFFSET_GRAB);
    }

    // ==================================================================
    // Player interaction — button check / grab (sub_30CE0 / sub_30CF8)
    // ==================================================================

    /**
     * Processes grab/escape check for all players.
     * ROM: sub_30CE0 calls sub_30CF8 for P1 (d6=p1_standing_bit), then P2 (d6=p2_standing_bit).
     */
    private void processButtonCheckAllPlayers(AbstractPlayableSprite player) {
        if (player != null) {
            processButtonCheckForPlayer(player, 0);
        }
        int pi = 1;
        for (PlayableEntity sidekick : services().sidekicks()) {
            if (sidekick instanceof AbstractPlayableSprite sp) {
                processButtonCheckForPlayer(sp, pi);
            }
            pi++;
        }
    }

    /**
     * Button check / grab handler for a single player.
     * ROM: sub_30CF8
     *
     * @param player the player sprite
     * @param pi     player index (0=P1, 1=P2)
     */
    private void processButtonCheckForPlayer(AbstractPlayableSprite player, int pi) {
        if (pi >= playerGrabbed.length) return;

        if (playerGrabbed[pi]) {
            // ROM: btst d6,$35(a0) — player IS grabbed
            // Check A/B/C for escape
            if (player.isJumpPressed()) {
                escapePlayer(player, pi);
            }
        } else {
            // ROM: loc_30D4E — player NOT grabbed, check for standing → grab
            boolean standing = isPlayerStandingOnThis(player);
            if (!standing) return;

            // ROM: tst.b $34(a0) / bne.s loc_30D6E — skip X check if already grabbed someone
            if (!anyGrabbed) {
                // ROM: X proximity check
                // move.w x_pos(a1),d0 / addi.w #8,d0 / sub.w x_pos(a0),d0
                // cmpi.w #2*8,d0 / bhs.s locret
                int dx = player.getCentreX() + GRAB_X_OFFSET - baseX;
                if (dx < 0 || dx >= GRAB_X_RANGE) return;
            }

            // ROM: loc_30D6E — attempt grab
            // tst.b object_control(a1) / bne.s locret — already controlled?
            if (player.isObjectControlled()) return;
            // ROM: tst.w (Debug_placement_mode).w / bne.s locret
            if (player.isDebugMode()) return;

            grabPlayer(player, pi);
        }
    }

    // ==================================================================
    // Grab sequence (loc_30D6E)
    // ==================================================================

    /**
     * Grabs a player onto the hand launcher.
     * ROM: loc_30D6E through loc_30DE4.
     */
    private void grabPlayer(AbstractPlayableSprite player, int pi) {
        playerGrabbed[pi] = true;
        anyGrabbed = true;

        // ROM: moveq #signextendB(sfx_Roll),d0 / jsr (Play_SFX).l
        playSfx(Sonic3kSfx.ROLL.id);

        // ROM: move.b #0,anim(a1)
        player.setAnimationId(0);

        // ROM: bclr #Status_Roll,status(a1) — clear rolling
        if (player.getRolling()) {
            player.setRolling(false);
        }

        // ROM: move.b #$13,y_radius(a1) / move.b #9,x_radius(a1)
        // cmpi.l #Obj_Tails,(a1) → move.b #$F,y_radius(a1)
        // Tails gets a smaller Y collision radius (0x0F vs 0x13)
        int yRadius = (player instanceof Tails) ? GRAB_Y_RADIUS_TAILS : GRAB_Y_RADIUS_DEFAULT;
        player.applyCustomRadii(GRAB_X_RADIUS, yRadius);

        // ROM: move.b #1,object_control(a1) — lock player (bit 0 = can jump out)
        player.setObjectControlled(true);

        // ROM: bclr #Status_Push,status(a1)
        player.setPushing(false);

        // ROM: Snap player X to hand centre with offset
        // move.w x_pos(a0),x_pos(a1) / subq.w #2,x_pos(a1)
        // btst #0,status(a0) → addq.w #2*2,x_pos(a1)
        int snapX;
        if (facingLeft) {
            // ROM: addq.w #2*2,x_pos(a1) (adds 4 after the subq 2, net +2)
            snapX = baseX + GRAB_X_SNAP_OFFSET;
        } else {
            // ROM: subq.w #2,x_pos(a1)
            snapX = baseX - GRAB_X_SNAP_OFFSET;
        }
        player.setCentreX((short) snapX);

        // ROM: move.w #$1000,ground_vel(a1) — set ground speed
        // btst #0,status(a0) → neg.w ground_vel(a1)
        int gVel = facingLeft ? -GRAB_GROUND_VEL : GRAB_GROUND_VEL;
        player.setGSpeed((short) gVel);

        // ROM: bclr #Status_Facing,status(a1) (face right)
        // btst #0,status(a0) → bset #Status_Facing,status(a1) (face left)
        player.setDirection(facingLeft ? Direction.LEFT : Direction.RIGHT);

        // ROM: move.b #1,$34(a0)
        // (anyGrabbed already set above)
    }

    // ==================================================================
    // Escape sequence (sub_30CF8, A/B/C pressed while grabbed)
    // ==================================================================

    /**
     * Player escapes from grab by pressing A/B/C.
     * ROM: sub_30CF8 lines 65939-65956.
     */
    private void escapePlayer(AbstractPlayableSprite player, int pi) {
        playerGrabbed[pi] = false;

        // ROM: move.w #$800,ground_vel(a1) / move.w #$800,x_vel(a1)
        // move.w #-$400,y_vel(a1)
        // btst #0,status(a0) → neg ground_vel/x_vel
        int xDir = facingLeft ? -1 : 1;
        player.setGSpeed((short) (ESCAPE_GROUND_VEL * xDir));
        player.setXSpeed((short) (ESCAPE_X_VEL * xDir));
        player.setYSpeed((short) ESCAPE_Y_VEL);

        // ROM: move.b #0,object_control(a1)
        player.setObjectControlled(false);

        // ROM: bclr #Status_OnObj,status(a1) / bset #Status_InAir,status(a1)
        player.setOnObject(false);
        player.setAir(true);

        // ROM: tst.b $35(a0) / bne.s locret — if no one else grabbed, clear flag
        if (!playerGrabbed[0] && !playerGrabbed[1]) {
            anyGrabbed = false;
        }
    }

    // ==================================================================
    // Launch release (sub_30C7C / sub_30C8C)
    // ==================================================================

    /**
     * Releases all players with full launch velocity at the midpoint.
     * ROM: sub_30C7C calls sub_30C8C for P1 then P2.
     */
    private void launchReleaseAllPlayers(AbstractPlayableSprite player) {
        if (player != null) {
            launchReleasePlayer(player, 0);
        }
        int pi = 1;
        for (PlayableEntity sidekick : services().sidekicks()) {
            if (sidekick instanceof AbstractPlayableSprite sp) {
                launchReleasePlayer(sp, pi);
            }
            pi++;
        }
    }

    /**
     * Releases a single player with full horizontal launch velocity.
     * ROM: sub_30C8C
     */
    private void launchReleasePlayer(AbstractPlayableSprite player, int pi) {
        if (pi >= playerGrabbed.length) return;

        if (playerGrabbed[pi]) {
            // ROM: bclr d6,$35(a0) — clear grabbed bit
            playerGrabbed[pi] = false;

            // ROM: move.w #$1000,ground_vel(a1) / move.w #$1000,x_vel(a1) / move.w #0,y_vel(a1)
            // btst #0,status(a0) → neg ground_vel/x_vel
            int xDir = facingLeft ? -1 : 1;
            player.setGSpeed((short) (LAUNCH_GROUND_VEL * xDir));
            player.setXSpeed((short) (LAUNCH_X_VEL * xDir));
            player.setYSpeed((short) 0);

            // ROM: move.b #0,anim(a1)
            player.setAnimationId(0);

            // ROM: move.b #0,object_control(a1)
            player.setObjectControlled(false);

            // ROM: bclr #Status_OnObj,status(a1)
            player.setOnObject(false);
        } else {
            // ROM: loc_30CCC — not grabbed but standing → knock off
            // bclr d6,status(a0) / beq locret → bclr #Status_OnObj / bset #Status_InAir
            if (isPlayerStandingOnThis(player)) {
                player.setOnObject(false);
                player.setAir(true);
            }
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Checks if a player is within the horizontal detection range.
     * ROM: subi.w #$20,d1 (x-32) ... cmpi.w #$40,d0 (range 64px)
     * Unsigned comparison: (player_x - (hand_x - 32)) as unsigned < 64
     */
    private boolean isPlayerInHorizontalRange(PlayableEntity player) {
        int leftEdge = baseX - DETECT_HALF_WIDTH;
        int dx = (player.getCentreX() - leftEdge) & 0xFFFF;
        return dx < (DETECT_HALF_WIDTH * 2);
    }

    /**
     * Checks if a player is currently standing on this solid object.
     * ROM: btst d6,status(a0) — checks the standing bit set by SolidObjectTop.
     * In the engine, riding state is tracked by ObjectManager.SolidContacts.
     */
    private boolean isPlayerStandingOnThis(PlayableEntity player) {
        try {
            return services().objectManager().isRidingObject(player, this);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Plays a sound effect safely.
     */
    private void playSfx(int sfxId) {
        if (isOnScreen()) {
            try {
                services().playSfx(sfxId);
            } catch (Exception e) {
                // Prevent audio failure from breaking game logic
            }
        }
    }

    // ==================================================================
    // SolidObjectProvider (SolidObjectTop)
    // ==================================================================

    @Override
    public SolidObjectParams getSolidParams() {
        // ROM: d1=$20 (half-width), d3=$11 (half-height)
        return new SolidObjectParams(SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, SOLID_HALF_HEIGHT + 1);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        // ROM: SolidObjectTop not called in launch state when $30 > $18
        // Also skip for grabbed/controlled players (they're managed manually)
        return solidActive && !player.isObjectControlled();
    }

    // ==================================================================
    // SolidObjectListener
    // ==================================================================

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        // The standing/riding state is tracked by ObjectManager.SolidContacts automatically.
        // We read it in update() via isPlayerStandingOnThis(). No additional tracking needed here.
    }

    // ==================================================================
    // Rendering
    // ==================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(ART_KEY);
        if (renderer != null) {
            renderer.drawFrameIndex(mappingFrame, baseX, currentY, facingLeft, false);
        }
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Draw solid top bounds
        float r = anyGrabbed ? 0.0f : 0.3f;
        float g = anyGrabbed ? 1.0f : 0.8f;
        float b = anyGrabbed ? 0.0f : 1.0f;

        int left = baseX - SOLID_HALF_WIDTH;
        int right = baseX + SOLID_HALF_WIDTH;
        int top = currentY - SOLID_HALF_HEIGHT;
        int bottom = currentY + SOLID_HALF_HEIGHT;
        ctx.drawLine(left, top, right, top, r, g, b);
        ctx.drawLine(left, bottom, right, bottom, r, g, b);
        ctx.drawLine(left, top, left, bottom, r, g, b);
        ctx.drawLine(right, top, right, bottom, r, g, b);

        // Centre cross
        ctx.drawCross(baseX, currentY, 4, 0.5f, 0.5f, 0.5f);

        // Horizontal detection range
        ctx.drawLine(baseX - DETECT_HALF_WIDTH, currentY - 2,
                baseX + DETECT_HALF_WIDTH, currentY - 2, 0.2f, 0.2f, 0.6f);

        // State label
        StringBuilder sb = ctx.getLabelBuilder();
        sb.append("Hand ");
        sb.append(state == State.LAUNCHING ? "LAUNCH" : "IDLE");
        sb.append(" y=").append(yOffset);
        sb.append(" t=").append(timer);
        if (anyGrabbed) sb.append(" [GRAB]");
        if (facingLeft) sb.append(" <L");
        ctx.drawWorldLabel(baseX, currentY - 20, 0, sb.toString(), DebugColor.CYAN);
    }

    // ==================================================================
    // Position / priority
    // ==================================================================

    @Override
    public int getX() { return baseX; }

    @Override
    public int getY() { return currentY; }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(priority);
    }

    /** Expose Y-offset for the child arm object. */
    int getYOffset() { return yOffset; }

    /** Expose facing direction for the child arm object. */
    boolean isFacingLeft() { return facingLeft; }

    // ==================================================================
    // Child arm visual (loc_30DEC)
    // ==================================================================

    /**
     * Visual arm overlay that cycles through frames 0-5 when the hand is in
     * the upper position (parent Y-offset &le; 0x18). Renders behind the main hand.
     * <p>
     * ROM: loc_30DEC. Tracks parent Y, animates only when parent $30(a1) &le; $18.
     * Known bug in original ROM: writes $30 to width_pixels instead of
     * height_pixels for the child (FixBugs conditional at sonic3k.asm:65748).
     */
    public static class HandLauncherArmChild extends AbstractObjectInstance {

        private final HCZHandLauncherObjectInstance parent;
        private int currentFrame;

        public HandLauncherArmChild(ObjectSpawn spawn, HCZHandLauncherObjectInstance parent) {
            super(spawn, "HCZHandLauncherArm");
            this.parent = parent;
            this.currentFrame = 0;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            // ROM: movea.w $3C(a0),a1 / cmpi.w #$18,$30(a1)
            // Only animate when parent arm offset <= 0x18
            if (parent.getYOffset() > Y_OFFSET_GRAB) {
                // ROM: rts — don't animate, effectively invisible
                return;
            }

            // ROM: addq.b #1,mapping_frame(a0) / cmpi.b #6 / blo / move.b #0
            currentFrame++;
            if (currentFrame >= CHILD_FRAME_COUNT) {
                currentFrame = 0;
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // Only render when parent arm is in extended position
            if (parent.getYOffset() > Y_OFFSET_GRAB) return;

            PatternSpriteRenderer renderer = getRenderer(ART_KEY);
            if (renderer != null) {
                // ROM: move.w y_pos(a1),y_pos(a0) — track parent Y
                int renderY = parent.getY();
                renderer.drawFrameIndex(currentFrame, parent.getX(), renderY,
                        parent.isFacingLeft(), false);
            }
        }

        @Override
        public int getX() { return parent.getX(); }

        @Override
        public int getY() { return parent.getY(); }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY_CHILD);
        }

        @Override
        public boolean isPersistent() {
            // Child lives as long as parent
            return !parent.isDestroyed();
        }
    }
}
