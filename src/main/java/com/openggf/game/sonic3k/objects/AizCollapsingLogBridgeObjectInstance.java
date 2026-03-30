package com.openggf.game.sonic3k.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Object 0x2C - AIZ Collapsing Log Bridge (Sonic 3 &amp; Knuckles).
 * <p>
 * A flat bridge made of 6 log segments that collapses progressively when
 * triggered. Two variants selected by subtype bit 7:
 * <ul>
 *   <li><b>Normal bridge</b> (subtype &lt; 0x80): Collapses when a player stands on it.
 *       Uses {@code Map_AIZCollapsingLogBridge} (3 frames). Segments fall with gravity
 *       after staggered delays.</li>
 *   <li><b>Fire bridge</b> (subtype &ge; 0x80): Collapses when the AIZ boss sets the
 *       burn flag. Uses {@code Map_AIZDrawBridgeFire} (8 frames). Segments play fire
 *       animation (frames 3-7) before falling.</li>
 * </ul>
 * <p>
 * Both variants: 6 segments spaced evenly, solid-top collision, staggered collapse
 * timing based on segment index, progressive player knockoff based on position.
 * <p>
 * ROM references: Obj_AIZCollapsingLogBridge (sonic3k.asm:59193), loc_2AE70,
 * loc_2AEE2, loc_2AF70, sub_2AF9C, sub_2AFFE, loc_2B452.
 */
public class AizCollapsingLogBridgeObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // ===== States =====
    private static final int STATE_IDLE = 0;
    private static final int STATE_COLLAPSING = 1;
    private static final int STATE_FINAL = 2;

    // ===== Constants =====
    private static final int SEGMENT_COUNT = 6;

    // Normal bridge: width_pixels=$5A, segment spacing=$1E, first offset=$4B
    private static final int NORMAL_HALF_WIDTH = 0x5A;
    private static final int NORMAL_SEGMENT_SPACING = 0x1E;
    private static final int NORMAL_FIRST_OFFSET = 0x4B;

    // Fire bridge: width_pixels=$60, segment spacing=$20, first offset=$50
    private static final int FIRE_HALF_WIDTH = 0x60;
    private static final int FIRE_SEGMENT_SPACING = 0x20;
    private static final int FIRE_FIRST_OFFSET = 0x50;

    // Height for solid-top collision (ROM: height_pixels = 8)
    private static final int HEIGHT_PIXELS = 8;

    // Priority $200 → bucket 4
    private static final int PRIORITY = 4;

    // Per-segment collapse delay increment (ROM: $37 = 8)
    private static final int COLLAPSE_DELAY_INCREMENT = 8;

    // ===== Boss trigger for fire bridge variant =====
    // Set by the AIZ end boss when fire breath reaches the drawbridge.
    // ROM: _unkFAA2 flag, set with st instruction at boss animation frame 6.
    private static volatile boolean drawBridgeBurnActive;

    /** Called by the AIZ end boss to trigger fire bridge collapse. */
    public static void setDrawBridgeBurnActive(boolean active) {
        drawBridgeBurnActive = active;
    }

    // ===== Instance fields =====
    private final boolean isFireBridge;
    private final int halfWidth;
    private final int subtypeBase;   // ROM: $36
    private final int totalTimer;    // ROM: $35 = subtypeBase + 0x30
    private final boolean hFlip;
    private final String artKey;

    private final int x;
    private final int y;
    private int state = STATE_IDLE;
    private int collapseTimer;
    private boolean segmentsSpawned;
    // ROM tracks p1_standing_bit and p2_standing_bit independently
    private final List<PlayableEntity> standingPlayers = new ArrayList<>(2);
    // Players ejected by progressive knockoff — prevents re-landing via SolidObjectTop
    // (ROM: cleared standing bits are never re-set because SolidObjectTop isn't called)
    private final Set<PlayableEntity> ejectedPlayers = new HashSet<>(2);

    // Segment positions and frames for pre-collapse rendering
    private final int[] segmentX = new int[SEGMENT_COUNT];
    private final int[] segmentFrame = new int[SEGMENT_COUNT];

    public AizCollapsingLogBridgeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "AIZCollapsingLogBridge");
        this.x = spawn.x();
        this.y = spawn.y();
        this.hFlip = (spawn.renderFlags() & 0x01) != 0;

        int subtype = spawn.subtype() & 0xFF;
        this.isFireBridge = (subtype & 0x80) != 0;
        this.subtypeBase = isFireBridge ? (subtype & 0x7F) : subtype;
        this.totalTimer = subtypeBase + 0x30;
        this.collapseTimer = totalTimer;

        if (isFireBridge) {
            this.halfWidth = FIRE_HALF_WIDTH;
            this.artKey = Sonic3kObjectArtKeys.AIZ_DRAW_BRIDGE_FIRE;
            initSegments(FIRE_FIRST_OFFSET, FIRE_SEGMENT_SPACING);
        } else {
            this.halfWidth = NORMAL_HALF_WIDTH;
            this.artKey = Sonic3kObjectArtKeys.AIZ_COLLAPSING_LOG_BRIDGE;
            initSegments(NORMAL_FIRST_OFFSET, NORMAL_SEGMENT_SPACING);
        }
    }

    private void initSegments(int firstOffset, int spacing) {
        // ROM: first segment X = parent_x - firstOffset, each subsequent + spacing
        int startX = x - firstOffset;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segmentX[i] = startX + i * spacing;
            segmentFrame[i] = 0;
        }
        // ROM: first segment frame = 1 (move.w #1,4(a2))
        segmentFrame[0] = 1;
        // ROM: last segment frame = 2 (move.w #2,-2(a2))
        segmentFrame[SEGMENT_COUNT - 1] = 2;
    }

    // ===== SolidObjectProvider =====

    @Override
    public SolidObjectParams getSolidParams() {
        // ROM: d3 = height_pixels; d2 = height_pixels (implicit).
        // Engine pattern (matching FloatingPlatform, CorkFloor, InvisibleBlock):
        // airHalfHeight = height, groundHalfHeight = height + 1
        return new SolidObjectParams(halfWidth, HEIGHT_PIXELS, HEIGHT_PIXELS + 1);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        if (state == STATE_FINAL) return false;
        // ROM: SolidObjectTop is only called during idle (loc_2AE70/loc_2AEE2).
        // During collapse, standing bits persist but are never re-set after knockoff
        // clears them. The engine re-evaluates collision each frame, so we must
        // reject players whose standing bits were already cleared by knockoff.
        return !ejectedPlayers.contains(player);
    }

    // ===== SolidObjectListener =====

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        if (contact.standing()) {
            if (!standingPlayers.contains(player)) {
                standingPlayers.add(player);
            }
            if (state == STATE_IDLE && !isFireBridge) {
                // Normal bridge: player standing triggers collapse
                // ROM: loc_2AE70 checks standing_mask, then transitions to collapse
                startCollapse();
            }
        }
    }

    // ===== Update =====

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        switch (state) {
            case STATE_IDLE -> {
                // Fire bridge: check boss trigger flag each frame
                // ROM: loc_2AEE2 tests _unkFAA2
                if (isFireBridge && drawBridgeBurnActive) {
                    startCollapse();
                }
            }
            case STATE_COLLAPSING -> {
                // ROM: loc_2AF70 — decrement timer, then check knockoff.
                // When timer reaches 0, the ROM sets the code pointer to loc_2B452
                // but falls through to the knockoff check with d3=0 on the same frame.
                collapseTimer--;
                boolean timerExpired = collapseTimer <= 0;
                if (timerExpired) {
                    state = STATE_FINAL;
                }
                // ROM: checks both p1 and p2 independently each frame
                for (int i = standingPlayers.size() - 1; i >= 0; i--) {
                    checkPlayerKnockoff(standingPlayers.get(i));
                }
            }
            case STATE_FINAL -> {
                // ROM: loc_2B452 → loc_2B45E (timer is 0, immediate kick + delete)
                for (int i = standingPlayers.size() - 1; i >= 0; i--) {
                    knockOff(standingPlayers.get(i));
                }
                setDestroyed(true);
            }
        }
    }

    /**
     * Initiates the collapse sequence: spawns 6 segment children with staggered
     * delays and plays the collapse SFX.
     * ROM: sub_2AFFE
     */
    private void startCollapse() {
        if (segmentsSpawned) return;
        segmentsSpawned = true;
        state = STATE_COLLAPSING;
        collapseTimer = totalTimer;

        // Spawn 6 segment child objects with staggered delays
        // ROM: d2 starts at subtypeBase, increments by COLLAPSE_DELAY_INCREMENT each segment
        int delay = subtypeBase;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            CollapsingLogSegment segment = new CollapsingLogSegment(
                    segmentX[i], y, segmentFrame[i], delay, artKey, isFireBridge);
            spawnDynamicObject(segment);
            delay += COLLAPSE_DELAY_INCREMENT;
        }

        // ROM: moveq #signextendB(sfx_Collapse),d0; jmp (Play_SFX).l
        services().playSfx(Sonic3kSfx.COLLAPSE.id);
    }

    /**
     * Checks whether the standing player should be knocked off based on their
     * horizontal position and the current collapse timer.
     * <p>
     * The bridge is divided into segments of 32px width. Each segment has a
     * collapse threshold of {@code 0x30 - segmentIndex * 8}. The player falls
     * when the timer reaches their segment's threshold, synchronised with the
     * visual segment collapse.
     * <p>
     * ROM: sub_2AF9C
     */
    private void checkPlayerKnockoff(PlayableEntity player) {
        // ROM: btst #Status_InAir,status(a1); bne.s loc_2AFE0
        if (player.getAir()) {
            knockOff(player);
            return;
        }

        int fullWidth = halfWidth * 2;
        // ROM: move.w x_pos(a1),d0; sub.w x_pos(a0),d0; add.w d1,d0
        int relativeX = player.getCentreX() - x + halfWidth;

        if (relativeX < 0 || relativeX >= fullWidth) {
            knockOff(player);
            return;
        }

        // ROM: btst #0,status(a0) — mirror if h-flipped
        if (hFlip) {
            relativeX = fullWidth - relativeX;
        }

        // ROM: lsr.w #5,d0 (÷32) then add.w d0,d0; add.w d0,d0; add.w d0,d0 (×8)
        int segmentIndex = relativeX >> 5;
        int segmentOffset = segmentIndex << 3;

        // ROM: add.b $36(a0),d0; move.b $35(a0),d2; sub.b d0,d2
        // threshold = totalTimer - (segmentOffset + subtypeBase) = 0x30 - segmentOffset
        int threshold = 0x30 - segmentOffset;

        // ROM: cmp.b d2,d3; bhi.s locret_2AFFC
        // Player falls when collapseTimer <= threshold
        if (collapseTimer <= threshold) {
            knockOff(player);
        }
    }

    /**
     * Removes the player from this object, setting them airborne.
     * ROM: loc_2AFE0 — bclr standing bits, bclr Status_Push, bset Status_InAir
     */
    private void knockOff(PlayableEntity player) {
        player.setOnObject(false);
        player.setPushing(false);
        player.setAir(true);
        standingPlayers.remove(player);
        ejectedPlayers.add(player);
    }

    // ===== Rendering =====

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // After collapse, the segment children render themselves
        if (segmentsSpawned) return;

        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) return;

        PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
        if (renderer == null || !renderer.isReady()) return;

        for (int i = 0; i < SEGMENT_COUNT; i++) {
            renderer.drawFrameIndex(segmentFrame[i], segmentX[i], y, hFlip, false);
        }
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        float r = state == STATE_COLLAPSING ? 1.0f : 0.0f;
        float g = state == STATE_COLLAPSING ? 1.0f : 0.8f;
        float b = state == STATE_COLLAPSING ? 0.0f : 1.0f;
        int left = x - halfWidth;
        int right = x + halfWidth;
        int top = y - HEIGHT_PIXELS;
        int bottom = y + HEIGHT_PIXELS;
        ctx.drawLine(left, top, right, top, r, g, b);
        ctx.drawLine(right, top, right, bottom, r, g, b);
        ctx.drawLine(right, bottom, left, bottom, r, g, b);
        ctx.drawLine(left, bottom, left, top, r, g, b);
    }

    // =================================================================
    // Segment child class
    // =================================================================

    /**
     * Individual log bridge segment that waits for a staggered delay then
     * falls with gravity. Two animation modes:
     * <ul>
     *   <li><b>Normal</b> (loc_2AEBA): delay → fall with gravity</li>
     *   <li><b>Fire</b> (loc_2AF22): delay → fire animation (frames 3-7,
     *       4-frame cycle) while falling with gravity</li>
     * </ul>
     */
    public static class CollapsingLogSegment extends AbstractObjectInstance {

        private static final int GRAVITY = 0x38;
        private static final int OFF_SCREEN_MARGIN = 128;
        // ROM: move.b #3,anim_frame_timer(a0)
        private static final int FIRE_ANIM_FRAME_DELAY = 3;
        private static final int FIRE_ANIM_FIRST_FRAME = 3;
        private static final int FIRE_ANIM_LAST_FRAME = 7;

        private final String artKey;
        private final boolean isFireVariant;
        private final int fixedX;
        private int mappingFrame;
        private int delayTimer;
        private final SubpixelMotion.State motion;

        // Fire animation timer
        private int animFrameTimer = FIRE_ANIM_FRAME_DELAY;

        public CollapsingLogSegment(int x, int y, int frame, int delay,
                                     String artKey, boolean isFireVariant) {
            super(buildSpawnAt(x, y, Sonic3kObjectIds.AIZ_COLLAPSING_LOG_BRIDGE),
                    "LogSegment");
            this.fixedX = x;
            this.motion = new SubpixelMotion.State(0, y, 0, 0, 0, 0);
            this.mappingFrame = frame;
            this.delayTimer = delay;
            this.artKey = artKey;
            this.isFireVariant = isFireVariant;
        }

        private static ObjectSpawn buildSpawnAt(int x, int y, int objId) {
            return new ObjectSpawn(x, y, objId, 0, 0, false, 0);
        }

        @Override
        public int getX() {
            return fixedX;
        }

        @Override
        public int getY() {
            return motion.y;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (isDestroyed()) return;

            // Delay phase: count down, then start falling
            if (delayTimer > 0) {
                delayTimer--;
                // ROM: fire variant sets frame to 3 when delay expires
                if (isFireVariant && delayTimer == 0) {
                    mappingFrame = FIRE_ANIM_FIRST_FRAME;
                }
                return;
            }

            // Fire variant: cycle through fire animation frames 3-7
            // ROM: loc_2AF3A — 4-frame cycle per mapping_frame advance
            if (isFireVariant) {
                animFrameTimer--;
                if (animFrameTimer < 0) {
                    animFrameTimer = FIRE_ANIM_FRAME_DELAY;
                    mappingFrame++;
                    if (mappingFrame > FIRE_ANIM_LAST_FRAME) {
                        mappingFrame = FIRE_ANIM_FIRST_FRAME;
                    }
                }
            }

            // Apply gravity (ROM: jsr (MoveSprite).l)
            SubpixelMotion.objectFall(motion, GRAVITY);

            if (!isOnScreen(OFF_SCREEN_MARGIN)) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(artKey);
            if (renderer == null) return;
            renderer.drawFrameIndex(mappingFrame, fixedX, motion.y, false, false);
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY);
        }

        @Override
        public boolean isPersistent() {
            return !isDestroyed();
        }
    }
}
