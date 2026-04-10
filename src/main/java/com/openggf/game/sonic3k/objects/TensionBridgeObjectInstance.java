package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.GravityDebrisChild;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Object 0x6C - Tension Bridge (HCZ, ICZ, LRZ).
 *
 * <p>Multi-segment bridge that sags under player weight using ROM-accurate
 * sine-based depression physics with lookup tables (identical math to S1's
 * GHZ bridge). Three variants selected by zone and subtype sign bit:
 *
 * <ul>
 *   <li>NORMAL: Sine sag toward player position (HCZ, LRZ, ICZ positive subtype)
 *   <li>ICZ_ROPE: Sine sag + 3px/segment staircase (ICZ negative subtype)
 *   <li>TRIGGER_COLLAPSE: Collapses when level trigger fires (non-ICZ negative subtype)
 * </ul>
 *
 * <p>ROM reference: Obj_TensionBridge (sonic3k.asm:75496+)
 */
public class TensionBridgeObjectInstance extends AbstractObjectInstance
        implements SlopedSolidProvider, SolidObjectListener {

    private enum Variant { NORMAL, TRIGGER_COLLAPSE, ICZ_ROPE }

    // --- Constants ---

    // From disasm: priority = $200 (bucket 4)
    private static final int PRIORITY = 4;
    private static final int SEGMENT_WIDTH = 16;
    private static final int SURFACE_OFFSET = 8; // subq.w #8,d0
    private static final int MAX_DEPRESSION_ANGLE = 0x40; // $40
    private static final int DEPRESSION_RATE = 4; // addq.b #4 / subq.b #4
    private static final int ROPE_STAIRCASE_STEP = 3; // addq.w #3,d6 per segment
    private static final int COLLAPSE_TIMER_INIT = 0x0E; // move.b #$E,$34(a0)
    private static final int FRAGMENT_GRAVITY = 0x38;
    private static final int ICZ_ANIM_WRAP = 12; // cmpi.b #$C

    // byte_38A78: staggered delays for collapse fragments
    private static final int[] FRAGMENT_DELAYS = {
            8, 0x10, 0x0C, 0x0E, 6, 0x0A, 4, 2,
            8, 0x10, 0x0C, 0x0E, 6, 0x0A, 4, 2
    };

    // byte_38E2A (full table including rows at byte_38E2A-$80):
    // Maximum depression depth per bridge length and player position.
    // 17 rows x 16 columns. Row = segment count (0-16), column = player segment index.
    // Identical to S1's ghzbend1.bin.
    // @formatter:off
    private static final int[][] MAX_DEPTH_TABLE = {
        { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x02, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x02, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x02, 0x04, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x02, 0x04, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x02, 0x04, 0x06, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x02, 0x04, 0x06, 0x08, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x02, 0x04, 0x06, 0x08, 0x08, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x02, 0x04, 0x06, 0x08, 0x0A, 0x08, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0A, 0x08, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0C, 0x0A, 0x08, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0C, 0x0C, 0x0A, 0x08, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00 },
        { 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0C, 0x0E, 0x0C, 0x0A, 0x08, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00 },
        { 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0C, 0x0E, 0x0E, 0x0C, 0x0A, 0x08, 0x06, 0x04, 0x02, 0x00, 0x00 },
        { 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0C, 0x0E, 0x10, 0x0E, 0x0C, 0x0A, 0x08, 0x06, 0x04, 0x02, 0x00 },
        { 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0C, 0x0E, 0x10, 0x10, 0x0E, 0x0C, 0x0A, 0x08, 0x06, 0x04, 0x02 },
    };
    // @formatter:on

    // BridgeBendData: Per-segment weight curves (sine-like distribution).
    // 16 rows x 16 columns. Row = player segment index (left) or segmentsRight (right mirror).
    // Read forward for segments left of player, backward for segments right.
    // Identical to S1's ghzbend2.bin.
    // @formatter:off
    private static final int[][] BEND_CURVE_TABLE = {
        { 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0xB5, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x7E, 0xDB, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x61, 0xB5, 0xEC, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x4A, 0x93, 0xCD, 0xF3, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x3E, 0x7E, 0xB0, 0xDB, 0xF6, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x38, 0x6D, 0x9D, 0xC5, 0xE4, 0xF8, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x31, 0x61, 0x8E, 0xB5, 0xD4, 0xEC, 0xFB, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x2B, 0x56, 0x7E, 0xA2, 0xC1, 0xDB, 0xEE, 0xFB, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x25, 0x4A, 0x73, 0x93, 0xB0, 0xCD, 0xE1, 0xF3, 0xFC, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x1F, 0x44, 0x67, 0x88, 0xA7, 0xBD, 0xD4, 0xE7, 0xF4, 0xFD, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00 },
        { 0x1F, 0x3E, 0x5C, 0x7E, 0x98, 0xB0, 0xC9, 0xDB, 0xEA, 0xF6, 0xFD, 0xFF, 0x00, 0x00, 0x00, 0x00 },
        { 0x19, 0x38, 0x56, 0x73, 0x8E, 0xA7, 0xBD, 0xD1, 0xE1, 0xEE, 0xF8, 0xFE, 0xFF, 0x00, 0x00, 0x00 },
        { 0x19, 0x38, 0x50, 0x6D, 0x83, 0x9D, 0xB0, 0xC5, 0xD8, 0xE4, 0xF1, 0xF8, 0xFE, 0xFF, 0x00, 0x00 },
        { 0x19, 0x31, 0x4A, 0x67, 0x7E, 0x93, 0xA7, 0xBD, 0xCD, 0xDB, 0xE7, 0xF3, 0xF9, 0xFE, 0xFF, 0x00 },
        { 0x19, 0x31, 0x4A, 0x61, 0x78, 0x8E, 0xA2, 0xB5, 0xC5, 0xD4, 0xE1, 0xEC, 0xF4, 0xFB, 0xFE, 0xFF },
    };
    // @formatter:on

    // --- Instance state ---

    private final boolean negativeSubtype;
    private final int segmentCount;
    private final int triggerIndex;
    private final int baseY;

    // Lazily resolved (can't call services() in constructor)
    private Variant variant;
    private String artKey;
    private int[] segmentFrames; // ICZ per-segment animation, null for non-ICZ

    private int depressionAngle;    // $3E: 0 to MAX_DEPRESSION_ANGLE
    private int playerSegmentIndex; // $3F: which segment the player is on
    private boolean playerOnBridge;
    private int[] segmentYOffsets;
    private byte[] slopeData;

    // Collapse state
    private boolean collapseActive;
    private boolean collapsed;
    private int collapseTimer;
    private PlayableEntity playerAtCollapse; // player standing when collapse starts

    public TensionBridgeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "TensionBridge");
        int raw = spawn.subtype() & 0xFF;
        this.negativeSubtype = (raw & 0x80) != 0;
        // andi.b #$7F,subtype(a0) - clear sign bit for segment count
        int effective = raw & 0x7F;
        this.segmentCount = Math.max(1, Math.min(effective, 16));
        // Trigger index: andi.w #$F,d0 (low 4 bits of effective subtype)
        this.triggerIndex = effective & 0x0F;
        this.baseY = spawn.y(); // move.w y_pos(a0),$3C(a0)
        this.segmentYOffsets = new int[segmentCount];
        this.slopeData = new byte[segmentCount * (SEGMENT_WIDTH / 2) + 1];
    }

    /** Lazy variant resolution - cannot call services() during construction. */
    private Variant resolveVariant() {
        if (variant == null) {
            if (!negativeSubtype) {
                variant = Variant.NORMAL;
            } else if (resolveZoneId() == Sonic3kZoneIds.ZONE_ICZ) {
                variant = Variant.ICZ_ROPE;
            } else {
                variant = Variant.TRIGGER_COLLAPSE;
            }
            // ICZ bridges (any variant) get per-segment animation
            if (resolveZoneId() == Sonic3kZoneIds.ZONE_ICZ) {
                segmentFrames = new int[segmentCount];
            }
        }
        return variant;
    }

    // --- SlopedSolidProvider ---

    @Override
    public SolidObjectParams getSolidParams() {
        // ROM: d1 = segCount*8 + 8 (half-width origin shift), d3 = 8 (half-height)
        // Matching S1 bridge pattern: halfWidth=N*8, offsetX=-8, offsetY=-8
        int halfWidth = segmentCount * 8;
        return new SolidObjectParams(halfWidth, 0, 0, -8, -SURFACE_OFFSET);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public byte[] getSlopeData() {
        return slopeData;
    }

    @Override
    public boolean isSlopeFlipped() {
        return false;
    }

    @Override
    public int getSlopeBaseline() {
        return 0;
    }

    // --- SolidObjectListener ---

    @Override
    public void onSolidContact(PlayableEntity player, SolidContact contact, int frameCounter) {
        // Standing detection handled in update() via isAnyPlayerRiding
    }

    // --- Priority & lifecycle ---

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public boolean isHighPriority() {
        return resolveZoneId() == Sonic3kZoneIds.ZONE_LRZ;
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        return !collapsed && !collapseActive;
    }

    // --- Update ---

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        Variant v = resolveVariant();

        // Trigger-collapse variant: check trigger each frame (loc_387B6)
        if (v == Variant.TRIGGER_COLLAPSE && !collapseActive && !collapsed) {
            if (Sonic3kLevelTriggerManager.testAny(triggerIndex)) {
                startCollapse(playerEntity);
                return;
            }
        }

        // Collapse countdown (loc_3890C)
        if (collapseActive) {
            if (collapseTimer > 0) {
                collapseTimer--;
            } else {
                releasePlayerAtCollapse();
                collapsed = true;
                setDestroyed(true);
            }
            return;
        }

        if (collapsed) return;

        // --- Depression angle update ---
        ObjectManager objectManager = services().objectManager();
        playerOnBridge = playerEntity != null && objectManager != null
                && objectManager.isAnyPlayerRiding(this);

        if (playerOnBridge) {
            // Track which segment the player is on
            // ROM: d0 = (playerX - bridgeX + segCount*8 + 8) >> 4
            int relX = playerEntity.getCentreX() - spawn.x() + segmentCount * 8 + 8;
            int logIdx = relX >> 4;
            if (logIdx < 0) logIdx = 0;
            if (logIdx >= segmentCount) logIdx = segmentCount - 1;
            playerSegmentIndex = logIdx;

            // addq.b #4,$3E(a0); cmpi.b #$40,$3E(a0)
            if (depressionAngle < MAX_DEPRESSION_ANGLE) {
                depressionAngle = Math.min(MAX_DEPRESSION_ANGLE,
                        depressionAngle + DEPRESSION_RATE);
            }
        } else {
            // subq.b #4,$3E(a0)
            if (depressionAngle > 0) {
                depressionAngle = Math.max(0, depressionAngle - DEPRESSION_RATE);
            }
        }

        // Calculate bend (sub_38CC2 / sub_38D74)
        if (depressionAngle > 0) {
            calculateBend(v == Variant.ICZ_ROPE);
        } else {
            clearOffsets();
        }

        // ICZ per-segment animation (sub_38C12)
        if (segmentFrames != null) {
            updateIczAnimation(playerEntity);
        }

        // Update slope data for collision
        updateSlopeData();
    }

    // --- Bend calculation (sub_38CC2 / sub_38D74) ---

    /**
     * ROM-accurate bridge bend using GetSineCosine + lookup tables.
     * Left-of-player segments read BEND_CURVE_TABLE forward,
     * right-of-player segments read backward (mirrored).
     *
     * @param ropeStaircase true for ICZ rope variant (+3px/segment)
     */
    private void calculateBend(boolean ropeStaircase) {
        // d4 = sin(depressionAngle)
        int sinValue = getSine(depressionAngle);

        // d5 = MAX_DEPTH_TABLE[segmentCount][playerSegmentIndex]
        int depthRow = Math.min(segmentCount, MAX_DEPTH_TABLE.length - 1);
        int depthCol = Math.min(playerSegmentIndex, 15);
        int maxDepth = MAX_DEPTH_TABLE[depthRow][depthCol];

        // Left side: segments 0..playerSegmentIndex, read BEND_CURVE_TABLE[playerIdx] forward
        int leftRow = Math.min(playerSegmentIndex, BEND_CURVE_TABLE.length - 1);
        for (int i = 0; i <= playerSegmentIndex && i < segmentCount; i++) {
            int weight = BEND_CURVE_TABLE[leftRow][i];
            // (weight+1) * maxDepth * sinValue >> 16
            long offset = ((long) (weight + 1) * maxDepth * sinValue) >> 16;
            segmentYOffsets[i] = (int) offset;
        }

        // Right side: segments playerSegmentIndex+1..segmentCount-1
        int segmentsRight = segmentCount - 1 - playerSegmentIndex;
        if (segmentsRight > 0) {
            int rightRow = Math.min(segmentsRight, BEND_CURVE_TABLE.length - 1);
            for (int j = 0; j < segmentsRight; j++) {
                // Read backward: BEND_CURVE_TABLE[segmentsRight][segmentsRight-1-j]
                int mirrorIdx = segmentsRight - 1 - j;
                int weight = BEND_CURVE_TABLE[rightRow][mirrorIdx];
                long offset = ((long) (weight + 1) * maxDepth * sinValue) >> 16;
                segmentYOffsets[playerSegmentIndex + 1 + j] = (int) offset;
            }
        }

        // ICZ rope staircase: addq.w #3,d6 per segment in sub_38D74
        if (ropeStaircase) {
            for (int i = 0; i < segmentCount; i++) {
                segmentYOffsets[i] += i * ROPE_STAIRCASE_STEP;
            }
        }
    }

    private void clearOffsets() {
        for (int i = 0; i < segmentCount; i++) {
            segmentYOffsets[i] = 0;
        }
        // ICZ rope: staircase persists even with no depression
        if (resolveVariant() == Variant.ICZ_ROPE) {
            for (int i = 0; i < segmentCount; i++) {
                segmentYOffsets[i] = i * ROPE_STAIRCASE_STEP;
            }
        }
    }

    /** ROM CalcSine for angles 0..$40. Returns 8.8 fixed-point (0..256). */
    private static int getSine(int angle) {
        if (angle <= 0) return 0;
        if (angle > MAX_DEPRESSION_ANGLE) angle = MAX_DEPRESSION_ANGLE;
        return TrigLookupTable.sinHex(angle);
    }

    // --- Slope data (for collision system) ---

    private void updateSlopeData() {
        int samplesPerSegment = SEGMENT_WIDTH / 2; // 8 samples per 16px segment
        for (int k = 0; k < slopeData.length; k++) {
            int segIdx = k / samplesPerSegment;
            if (segIdx >= segmentCount) segIdx = segmentCount - 1;
            // Negative: slope data represents "how much lower" subtracted from surface
            slopeData[k] = (byte) -segmentYOffsets[segIdx];
        }
    }

    // --- ICZ per-segment animation (sub_38C12) ---

    private void updateIczAnimation(PlayableEntity player) {
        // Start animation on player's segment when walking (x_vel != 0)
        if (playerOnBridge && player != null && player.getXSpeed() != 0) {
            if (segmentFrames[playerSegmentIndex] == 0) {
                segmentFrames[playerSegmentIndex] = 1; // move.b #1,(a1,d0.w)
            }
        }

        // Advance all animating segments
        for (int i = 0; i < segmentCount; i++) {
            if (segmentFrames[i] != 0) {
                segmentFrames[i]++;
                if (segmentFrames[i] >= ICZ_ANIM_WRAP) {
                    segmentFrames[i] = 0; // wrap to idle
                }
            }
        }
    }

    // --- Collapse (loc_389C8 / sub_389DE) ---

    private void startCollapse(PlayableEntity player) {
        collapseActive = true;
        collapseTimer = COLLAPSE_TIMER_INIT;

        // Remember player for delayed release
        ObjectManager objectManager = null;
        try {
            objectManager = services().objectManager();
        } catch (Exception ignored) { }

        if (objectManager != null) {
            if (objectManager.isAnyPlayerRiding(this)) {
                playerAtCollapse = player;
            }
            objectManager.markRemembered(spawn);
        }

        // Spawn fragment for each segment
        spawnCollapseFragments();

        // sfx_BridgeCollapse
        try {
            services().playSfx(Sonic3kSfx.BRIDGE_COLLAPSE.id);
        } catch (Exception ignored) { }
    }

    private void spawnCollapseFragments() {
        int startX = spawn.x() - ((segmentCount >> 1) * SEGMENT_WIDTH);
        for (int i = 0; i < segmentCount; i++) {
            int fragX = startX + (i * SEGMENT_WIDTH);
            int fragY = baseY + segmentYOffsets[i];
            int frame = (segmentFrames != null) ? segmentFrames[i] : 0;
            int delay = FRAGMENT_DELAYS[i % FRAGMENT_DELAYS.length];
            String fragArtKey = resolveArtKey();
            boolean highPri = isHighPriority();
            spawnChild(() -> new BridgeFragment(fragX, fragY, frame, delay,
                    fragArtKey, highPri));
        }
    }

    private void releasePlayerAtCollapse() {
        if (playerAtCollapse != null) {
            playerAtCollapse.setOnObject(false);
            playerAtCollapse.setPushing(false);
            playerAtCollapse.setAir(true);
            playerAtCollapse = null;
        }
    }

    // --- Rendering ---

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (collapsed || collapseActive) return;

        PatternSpriteRenderer renderer = getRenderer(resolveArtKey());
        if (renderer == null || !renderer.isReady()) return;

        // Segment X positions: bridgeX - (segCount/2)*16, +16 per segment
        int startX = spawn.x() - ((segmentCount >> 1) * SEGMENT_WIDTH);
        for (int i = 0; i < segmentCount; i++) {
            int x = startX + (i * SEGMENT_WIDTH);
            int y = baseY + segmentYOffsets[i];
            int frame = (segmentFrames != null) ? segmentFrames[i] : 0;
            renderer.drawFrameIndex(frame, x, y, false, false);
        }
    }

    // --- Helpers ---

    private String resolveArtKey() {
        if (artKey != null) return artKey;
        artKey = switch (resolveZoneId()) {
            case Sonic3kZoneIds.ZONE_ICZ -> Sonic3kObjectArtKeys.TENSION_BRIDGE_ICZ;
            case Sonic3kZoneIds.ZONE_LRZ -> Sonic3kObjectArtKeys.TENSION_BRIDGE_LRZ;
            default -> Sonic3kObjectArtKeys.TENSION_BRIDGE_HCZ;
        };
        return artKey;
    }

    private int resolveZoneId() {
        try {
            return services().romZoneId();
        } catch (Exception e) {
            return Sonic3kZoneIds.ZONE_HCZ;
        }
    }

    // --- Collapse fragment (loc_388E4) ---

    /**
     * Individual bridge segment that falls with gravity after a staggered delay.
     * ROM: no initial velocity, just MoveSprite gravity.
     */
    private static final class BridgeFragment extends GravityDebrisChild {
        private final int frameIndex;
        private int delay;
        private final String artKey;
        private final boolean highPri;

        private BridgeFragment(int x, int y, int frameIndex, int delay,
                               String artKey, boolean highPri) {
            super(new ObjectSpawn(x, y, Sonic3kObjectIds.TENSION_BRIDGE, 0, 0, false, 0),
                    "TensionBridgeFragment", 0, 0, FRAGMENT_GRAVITY);
            this.frameIndex = frameIndex;
            this.delay = delay;
            this.artKey = artKey;
            this.highPri = highPri;
        }

        @Override
        public boolean isHighPriority() {
            return highPri;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            // loc_388E4: delay countdown, then fall
            if (delay > 0) {
                delay--;
                return;
            }
            super.update(frameCounter, player);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(artKey);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(frameIndex, motionState.x, motionState.y,
                        false, false);
            }
        }
    }
}
