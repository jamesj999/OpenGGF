package com.openggf.game.sonic1.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Sonic 1 Bridge (Object 0x11) - A multi-segment log bridge with ROM-accurate
 * sine-based depression physics.
 * <p>
 * When Sonic stands on the bridge, it sags downward using a sine ramp-up
 * animation and ROM lookup tables for depression distribution. When Sonic
 * leaves, it springs back using a sine ramp-down.
 * <p>
 * Reference: docs/s1disasm/_incObj/11 Bridge (part 1).asm through (part 3).asm
 * <p>
 * Subtype = number of log segments (e.g., 8, 10, 12, 14, 16).
 * Each log is 16 pixels wide. The bridge is centered on the spawn X position.
 */
public class Sonic1BridgeObjectInstance extends AbstractObjectInstance
        implements SlopedSolidProvider, SolidObjectListener {

    private static final int LOG_WIDTH = 16;  // pixels per log segment
    private static final int LOG_HALF_HEIGHT = 8; // platform surface is 8px above segment center

    // From disassembly: depression angle range 0 to $40, changing by 4 per frame
    private static final int MAX_DEPRESSION_ANGLE = 0x40; // 64
    private static final int DEPRESSION_RATE = 4; // +/- per frame

    // From disassembly: obPriority = 3
    private static final int PRIORITY = 3;

    // ghzbend1.bin - Maximum depression depth per bridge length and player position
    // 17 rows x 16 columns. Row = segment count (0-16), column = player position.
    // @formatter:off
    private static final int[][] BEND_DATA_1 = {
        { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, // 0 segs
        { 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, // 1 seg
        { 0x02, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, // 2 segs
        { 0x02, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, // 3
        { 0x02, 0x04, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, // 4
        { 0x02, 0x04, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, // 5
        { 0x02, 0x04, 0x06, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, // 6
        { 0x02, 0x04, 0x06, 0x08, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, // 7
        { 0x02, 0x04, 0x06, 0x08, 0x08, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, // 8
        { 0x02, 0x04, 0x06, 0x08, 0x0A, 0x08, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, // 9
        { 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0A, 0x08, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, // 10
        { 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0C, 0x0A, 0x08, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00 }, // 11
        { 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0C, 0x0C, 0x0A, 0x08, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00, 0x00 }, // 12
        { 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0C, 0x0E, 0x0C, 0x0A, 0x08, 0x06, 0x04, 0x02, 0x00, 0x00, 0x00 }, // 13
        { 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0C, 0x0E, 0x0E, 0x0C, 0x0A, 0x08, 0x06, 0x04, 0x02, 0x00, 0x00 }, // 14
        { 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0C, 0x0E, 0x10, 0x0E, 0x0C, 0x0A, 0x08, 0x06, 0x04, 0x02, 0x00 }, // 15
        { 0x02, 0x04, 0x06, 0x08, 0x0A, 0x0C, 0x0E, 0x10, 0x10, 0x0E, 0x0C, 0x0A, 0x08, 0x06, 0x04, 0x02 }, // 16
    };
    // @formatter:on

    // ghzbend2.bin - Per-segment weight curves (sine-like distribution)
    // 16 rows x 16 columns. Row = Sonic's segment index. Each byte is a weight (0-$FF).
    // Read forward for segments left of Sonic, backward (mirrored) for segments right.
    // Values copied directly from docs/s1disasm/misc/ghzbend2.bin.
    // @formatter:off
    private static final int[][] BEND_DATA_2 = {
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

    // State fields
    private int logCount;            // Number of log segments
    private int depressionAngle;     // 0 to MAX_DEPRESSION_ANGLE, ramps +/- DEPRESSION_RATE/frame
    private int playerLogIndex;      // Which log Sonic is standing on (0-based from left)
    private int[] logYOffsets;       // Current Y offset for each log (pixels below base Y)
    private byte[] slopeData;        // Slope data for collision system
    private boolean playerOnBridge;  // Whether player is currently on the bridge

    public Sonic1BridgeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Bridge");
        this.logCount = Math.max(1, spawn.subtype() & 0xFF);
        if (this.logCount > 16) {
            this.logCount = 16; // Max 16 segments (BEND_DATA table limit)
        }
        this.logYOffsets = new int[logCount];
        this.slopeData = new byte[getHalfWidth() + 1];
    }

    private int getHalfWidth() {
        // Half the visual/collision width: logCount * 16 / 2 = logCount * 8
        return (logCount * LOG_WIDTH) / 2;
    }

    // ---- SolidObjectProvider / SlopedSolidProvider ----

    @Override
    public SolidObjectParams getSolidParams() {
        // ROM Bri_Solid: d1 = subtype*8+8 (origin shift), d2 = subtype*16 (full width).
        // The range is [bridgeX-N*8-8, bridgeX+N*8-8) — asymmetric, centered at bridgeX-8.
        // We replicate this with halfWidth = N*8 (half the full width) and offsetX = -8
        // to shift the collision center 8px left.
        int halfWidth = logCount * 8;
        // ROM Plat_NoXCheck: subq.w #8,d0 — landing/riding surface is at obY-8.
        // offsetY = -8 replicates this for both the landing and riding paths.
        //
        // halfHeight = 0: ROM Platform3 does NOT use the object's half-height for
        // landing position — only the fixed -8 from Plat_NoXCheck and the player's
        // yRadius. Our landing formula includes halfHeight in the position
        // calculation, so we set it to 0 to avoid double-counting the -8.
        // The riding path ignores halfHeight for sloped objects (uses slope sample).
        return new SolidObjectParams(halfWidth, 0, 0, -8, -8);
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
        // Bridge slope offsets are absolute (0 = flat), not relative to first sample
        return 0;
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // Standing detection is handled in update() via isAnyPlayerRiding
    }

    // ---- Update logic ----

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        LevelManager levelManager = LevelManager.getInstance();
        ObjectManager objectManager = levelManager != null ? levelManager.getObjectManager() : null;

        playerOnBridge = false;

        if (player != null && objectManager != null && objectManager.isAnyPlayerRiding(this)) {
            playerOnBridge = true;

            // Determine which log Sonic is standing on
            // From Bri_WalkOff/ExitPlatform2: d0 = (sonicX - bridgeX + logCount*8 + 8) >> 4
            // The +8 offset comes from Bri_Solid: d1 = logCount*8; addq.w #8,d1
            int relX = player.getCentreX() - spawn.x() + (logCount * 8) + 8;
            int logIdx = relX >> 4;
            if (logIdx < 0) logIdx = 0;
            if (logIdx >= logCount) logIdx = logCount - 1;
            playerLogIndex = logIdx;

            // Increase depression angle (ramp up)
            // From disassembly: addq.b #4,objoff_3E(a0); cmpi.b #$40,d0
            if (depressionAngle < MAX_DEPRESSION_ANGLE) {
                depressionAngle += DEPRESSION_RATE;
                if (depressionAngle > MAX_DEPRESSION_ANGLE) {
                    depressionAngle = MAX_DEPRESSION_ANGLE;
                }
            }

            // Calculate bend and move Sonic
            calculateBend();
        } else {
            // Sonic is not on bridge - spring back
            // From Bri_Action: subq.b #4,objoff_3E(a0)
            if (depressionAngle > 0) {
                depressionAngle -= DEPRESSION_RATE;
                if (depressionAngle < 0) {
                    depressionAngle = 0;
                }
                calculateBend();
            } else {
                // Fully flat - clear offsets
                for (int i = 0; i < logCount; i++) {
                    logYOffsets[i] = 0;
                }
            }
        }

        // Update slope data for collision system
        updateSlopeData();
    }

    /**
     * ROM-accurate bridge bend calculation from Bri_Bend.
     * <p>
     * For each log segment, calculates:
     *   offset = ((weight + 1) * maxDepression * sin(depressionAngle)) >> 16
     * <p>
     * Segments left of (and including) the player position read weights forward
     * from BEND_DATA_2[playerLogIndex]. Segments right of the player read weights
     * backward (mirrored) from BEND_DATA_2[segmentsRightCount].
     * <p>
     * Reference: docs/s1disasm/_incObj/11 Bridge (part 3).asm
     */
    private void calculateBend() {
        // CalcSine: d4 = sin(depressionAngle), range 0-256 (8.8 fixed point)
        int sinValue = getSine(depressionAngle);

        // Look up max depression from BEND_DATA_1
        // Index: BEND_DATA_1[logCount][playerLogIndex]
        int bendRow = Math.min(logCount, BEND_DATA_1.length - 1);
        int bendCol = Math.min(playerLogIndex, 15);
        int maxDepression = BEND_DATA_1[bendRow][bendCol];

        // Get weight curve for segments LEFT of Sonic (read forward)
        int weightRow = Math.min(playerLogIndex, BEND_DATA_2.length - 1);

        // Process segments left of and including Sonic's position
        for (int i = 0; i <= playerLogIndex && i < logCount; i++) {
            int weight = BEND_DATA_2[weightRow][i];
            // From disassembly: (weight+1) * maxDepression * sinValue, then swap (>> 16)
            long offset = ((long)(weight + 1) * maxDepression * sinValue) >> 16;
            logYOffsets[i] = (int) offset;
        }

        // Process segments right of Sonic's position (mirrored weights)
        int segmentsRight = logCount - 1 - playerLogIndex;
        if (segmentsRight > 0) {
            int rightWeightRow = Math.min(segmentsRight, BEND_DATA_2.length - 1);
            // Read backwards from BEND_DATA_2[rightWeightRow]
            for (int i = playerLogIndex + 1; i < logCount; i++) {
                // Mirror index: count from right edge
                int mirrorIdx = logCount - 1 - i;
                if (mirrorIdx < 0) mirrorIdx = 0;
                if (mirrorIdx > rightWeightRow) mirrorIdx = rightWeightRow;
                int weight = BEND_DATA_2[rightWeightRow][mirrorIdx];
                long offset = ((long)(weight + 1) * maxDepression * sinValue) >> 16;
                logYOffsets[i] = (int) offset;
            }
        }
    }

    /**
     * Updates slope data array from current log Y offsets.
     * Each entry covers 2 pixels horizontally (halfWidth samples).
     */
    private void updateSlopeData() {
        int halfWidth = getHalfWidth();
        if (slopeData == null || slopeData.length != halfWidth + 1) {
            slopeData = new byte[halfWidth + 1];
        }

        int samplesPerLog = LOG_WIDTH / 2; // 8 samples per log
        for (int k = 0; k < slopeData.length; k++) {
            int logIndex = k / samplesPerLog;
            if (logIndex >= logCount) {
                logIndex = logCount - 1;
            }
            // Negative because slope data represents "how much lower" which is
            // subtracted from the collision surface. Y increases downward, so
            // positive sag needs negative slope values.
            slopeData[k] = (byte) -logYOffsets[logIndex];
        }
    }

    /**
     * ROM-accurate CalcSine for angles 0 to $40 (0 to 90 degrees).
     * Returns 8.8 fixed-point value: 0 at angle 0, 256 ($100) at angle $40.
     * Delegates to TrigLookupTable.sinHex() which uses the ROM SINCOSLIST.
     */
    private static int getSine(int angle) {
        if (angle <= 0) return 0;
        if (angle > MAX_DEPRESSION_ANGLE) angle = MAX_DEPRESSION_ANGLE;
        return TrigLookupTable.sinHex(angle);
    }

    // ---- Rendering ----

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer bridgeRenderer = renderManager.getBridgeRenderer();
        if (bridgeRenderer == null || !bridgeRenderer.isReady()) {
            return;
        }

        // From Bri_Main: d3 = obX - (length/2 * 16)
        int startX = spawn.x() - ((logCount >> 1) * LOG_WIDTH);

        for (int i = 0; i < logCount; i++) {
            int x = startX + (i * LOG_WIDTH);
            int y = spawn.y() + logYOffsets[i];
            bridgeRenderer.drawFrameIndex(0, x, y, false, false);
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }
}
