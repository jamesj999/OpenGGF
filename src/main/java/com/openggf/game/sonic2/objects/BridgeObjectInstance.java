package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.BoxObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.Arrays;
import java.util.List;

/**
 * Bridge object (0x11) - EHZ/HPZ log bridge.
 *
 * <p>ROM reference: {@code Obj11}, {@code Obj11_Depress}, and
 * {@code PlatformObject11_cont} in {@code docs/s2disasm/s2.asm}. The Sonic 2
 * bridge keeps separate standing state for Sonic and Tails, uses the main
 * player's stored log index as the depression centre, and pulls that centre
 * one log at a time toward the sidekick index while Tails is standing.
 */
public class BridgeObjectInstance extends BoxObjectInstance
        implements SlopedSolidProvider, SolidObjectListener {

    private static final int LOG_WIDTH = 16;
    private static final int LOG_HALF_HEIGHT = 8;
    private static final int COLLISION_X_OFFSET = -8;
    private static final int MAX_LOGS = 16;
    private static final int MAX_DEPRESSION_ANGLE = 0x40;
    private static final int DEPRESSION_RATE = 4;

    // S2's shipped table only retains rows 8..16, but the disassembly comment
    // notes it is the same bridge data as Sonic 1 with the short rows removed.
    // Keep the full table so the lookup remains total; retail EHZ bridge
    // content still uses the original 8+ log rows.
    // @formatter:off
    private static final int[][] DEPRESSION_MAX_DEPTHS = {
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

    private static final int[][] DEPRESSION_WEIGHTS = {
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

    private final int logCount;
    private final int[] logYOffsets;

    private byte[] slopeData;
    private int depressionAngle;
    private int mainLogIndex;
    private int sidekickLogIndex;
    private boolean mainStanding;
    private boolean sidekickStanding;

    public BridgeObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name, 32, LOG_HALF_HEIGHT, 0.6f, 0.4f, 0.2f, false);
        this.logCount = Math.max(1, Math.min(MAX_LOGS, spawn.subtype() & 0x1F));
        this.logYOffsets = new int[logCount];
        this.slopeData = new byte[getHalfWidth() + 1];
    }

    @Override
    protected int getHalfWidth() {
        return (logCount * LOG_WIDTH) / 2;
    }

    @Override
    protected int getHalfHeight() {
        return LOG_HALF_HEIGHT;
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        return true;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // ROM Obj11 passes d1 = subtype*8 + 8 and d2 = subtype*16 into
        // PlatformObject11_cont, with the collision span centered 8px left of
        // the object origin and the surface fixed at obY-8.
        return new SolidObjectParams(getHalfWidth(), 0, 0, COLLISION_X_OFFSET, -8);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean forceAirOnRideExit() {
        return false;
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

    @Override
    public boolean usesSlopeForNewLanding() {
        // ROM parity: Obj11_EHZ computes/depresses the log child Y values before
        // sub_F872 (docs/s2disasm/s2.asm:21995-22032), but non-standing players
        // enter PlatformObject11_cont (22160-22172). That helper lands against
        // y_pos(a0)-d3 (35692-35712), not the child log Y table. The depressed
        // child Y is used only after the standing bit is already set (22120-22155).
        return false;
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        // Standing state is latched from the explicit checkpoint batch, matching
        // the bridge's in-object PlatformObject11_cont flow.
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        updateDepressionState();
        rebuildBridgeShape();
        updateSlopeData();

        AbstractPlayableSprite mainPlayer = playerEntity instanceof AbstractPlayableSprite playable
                ? playable
                : null;
        latchStandingState(mainPlayer, checkpointAll());
    }

    private void updateDepressionState() {
        if (!mainStanding && !sidekickStanding) {
            depressionAngle = Math.max(0, depressionAngle - DEPRESSION_RATE);
            return;
        }

        if (sidekickStanding && mainLogIndex != sidekickLogIndex) {
            if (mainLogIndex < sidekickLogIndex) {
                mainLogIndex++;
            } else {
                mainLogIndex--;
            }
        }

        depressionAngle = Math.min(MAX_DEPRESSION_ANGLE, depressionAngle + DEPRESSION_RATE);
    }

    private void rebuildBridgeShape() {
        Arrays.fill(logYOffsets, 0);
        if (depressionAngle <= 0) {
            return;
        }

        int depressionCentre = clampLogIndex(mainLogIndex);
        int maxDepth = DEPRESSION_MAX_DEPTHS[Math.min(logCount, MAX_LOGS)][depressionCentre];
        int sinValue = TrigLookupTable.sinHex(depressionAngle);

        int leftRow = Math.min(depressionCentre, MAX_LOGS - 1);
        for (int i = 0; i <= depressionCentre && i < logCount; i++) {
            logYOffsets[i] = weightedOffset(DEPRESSION_WEIGHTS[leftRow][i], maxDepth, sinValue);
        }

        int logsRight = logCount - 1 - depressionCentre;
        if (logsRight <= 0) {
            return;
        }

        int rightRow = Math.min(logsRight, MAX_LOGS - 1);
        for (int i = depressionCentre + 1; i < logCount; i++) {
            int mirrorIndex = logCount - 1 - i;
            logYOffsets[i] = weightedOffset(DEPRESSION_WEIGHTS[rightRow][mirrorIndex], maxDepth, sinValue);
        }
    }

    private static int weightedOffset(int weight, int maxDepth, int sinValue) {
        return (int) ((((long) weight + 1L) * maxDepth * sinValue) >> 16);
    }

    private void updateSlopeData() {
        if (slopeData == null || slopeData.length != getHalfWidth() + 1) {
            slopeData = new byte[getHalfWidth() + 1];
        }

        int samplesPerLog = LOG_WIDTH / 2;
        for (int i = 0; i < slopeData.length; i++) {
            int logIndex = i / samplesPerLog;
            if (logIndex >= logCount) {
                logIndex = logCount - 1;
            }
            slopeData[i] = (byte) -logYOffsets[logIndex];
        }
    }

    private void latchStandingState(AbstractPlayableSprite mainPlayer, SolidCheckpointBatch batch) {
        mainStanding = false;
        sidekickStanding = false;

        PlayerSolidContactResult mainResult = mainPlayer != null ? batch.perPlayer().get(mainPlayer) : null;
        if (mainResult != null && mainResult.standingNow()) {
            mainStanding = true;
            mainLogIndex = computeLogIndex(mainPlayer);
        }

        AbstractPlayableSprite sidekick = firstTrackedSidekick();
        PlayerSolidContactResult sidekickResult = sidekick != null ? batch.perPlayer().get(sidekick) : null;
        if (sidekickResult != null && sidekickResult.standingNow()) {
            sidekickStanding = true;
            sidekickLogIndex = computeLogIndex(sidekick);
        }
    }

    private AbstractPlayableSprite firstTrackedSidekick() {
        SpriteManager spriteManager = services().spriteManager();
        if (spriteManager == null || spriteManager.getSidekicks().isEmpty()) {
            return null;
        }
        return spriteManager.getSidekicks().getFirst();
    }

    private int computeLogIndex(AbstractPlayableSprite player) {
        int relX = player.getCentreX() - spawn.x() + (logCount * 8) + 8;
        return clampLogIndex(relX >> 4);
    }

    private int clampLogIndex(int index) {
        if (index < 0) {
            return 0;
        }
        return Math.min(index, logCount - 1);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            super.appendRenderCommands(commands);
            return;
        }
        PatternSpriteRenderer bridgeRenderer = renderManager.getBridgeRenderer();
        ObjectSpriteSheet bridgeSheet = renderManager.getBridgeSheet();

        if (bridgeRenderer != null && bridgeSheet != null && bridgeRenderer.isReady()) {
            int startX = spawn.x() - ((logCount >> 1) * LOG_WIDTH);
            for (int i = 0; i < logCount; i++) {
                int x = startX + (i * LOG_WIDTH);
                int y = spawn.y() + logYOffsets[i];
                bridgeRenderer.drawFrameIndex(0, x, y, false, false);
            }
        } else {
            super.appendRenderCommands(commands);
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(3);
    }
}
