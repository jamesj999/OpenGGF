package com.openggf.game.sonic3k.scroll;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.CnzZoneRuntimeState;
import com.openggf.level.scroll.AbstractZoneScrollHandler;

import java.util.Arrays;

import static com.openggf.level.scroll.M68KMath.VISIBLE_LINES;
import static com.openggf.level.scroll.M68KMath.asrWord;
import static com.openggf.level.scroll.M68KMath.negWord;
import static com.openggf.level.scroll.M68KMath.packScrollWords;

/**
 * Carnival Night Zone scroll handler for S3K.
 *
 * <p>Ports the shared CNZ1 deform logic used by both acts and switches to the
 * boss/background scroll path when the CNZ event layer reports a boss refresh
 * phase.
 */
public class SwScrlCnz extends AbstractZoneScrollHandler {

    /** Boss arena BG X offset from CNZ1_BossLevelScroll2. */
    private static final int BOSS_BG_X_OFFSET = 0x2F80;

    /** Boss arena BG Y offset from CNZ1_BossLevelScroll2. */
    private static final int BOSS_BG_Y_OFFSET = 0x100;

    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter,
                       int actId) {
        resetScrollTracking();

        short fgScroll = negWord(cameraX);
        int shakeY = resolveShakeOffsetY();

        if (shouldUseBossScroll()) {
            writeBossScroll(horizScrollBuf, fgScroll, cameraX, cameraY, shakeY);
            return;
        }

        vscrollFactorBG = cnzBgY(cameraY, shakeY);
        applyDeformation(horizScrollBuf, fgScroll, cnzBgX(cameraX));
        publishNormalDeformOutputs(cameraX);
    }

    private boolean shouldUseBossScroll() {
        CnzZoneRuntimeState state = cnzRuntimeState();
        if (state == null) {
            return false;
        }

        return switch (state.bossBackgroundMode()) {
            case ACT1_MINIBOSS_PATH, ACT1_POST_BOSS -> true;
            case NORMAL, ACT2_KNUCKLES_TELEPORTER -> false;
        };
    }

    private void writeBossScroll(int[] horizScrollBuf,
                                 short fgScroll,
                                 int cameraX,
                                 int cameraY,
                                 int shakeY) {
        short bgScroll = negWord(cameraX - BOSS_BG_X_OFFSET);
        vscrollFactorBG = (short) (cameraY - BOSS_BG_Y_OFFSET + shakeY);

        int packed = packScrollWords(fgScroll, bgScroll);
        trackOffset(fgScroll, bgScroll);
        Arrays.fill(horizScrollBuf, 0, VISIBLE_LINES, packed);
    }

    /**
     * CNZ1_Deform BG Y is approximately 13/128 of camera Y with screen shake
     * folded in.
     */
    private short cnzBgY(int cameraY, int shakeY) {
        int adjusted = cameraY - shakeY;
        return (short) (((long) adjusted * 13 >> 7) + shakeY);
    }

    private short cnzBgX(int cameraX) {
        return negWord((short) (asrWord(cameraX, 1) - asrWord(cameraX, 4)));
    }

    /**
     * Publishes the CNZ normal-deform outputs that later tile animation reads.
     *
     * <p>ROM-side CNZ derives the phase source from {@code Events_bg+$10} and
     * the BG X copy from {@code Camera_X_pos_BG_copy}. The current engine keeps
     * the same values in the zone runtime state so animated tiles can consume
     * them without reopening the event object boundary.
     */
    private void publishNormalDeformOutputs(int cameraX) {
        CnzZoneRuntimeState state = cnzRuntimeState();
        if (state == null) {
            return;
        }

        state.publishDeformOutputs(cnzPhaseSource(cameraX), cnzPublishedBgCameraX(cameraX));
    }

    /**
     * ROM-equivalent CNZ phase source used by AnimateTiles_CNZ.
     *
     * <p>This is the 5/16 camera-X fraction the disassembly reads from
     * {@code Events_bg+$10} when deriving the animated tile phase.
     */
    private int cnzPhaseSource(int cameraX) {
        return asrWord(cameraX, 2) + asrWord(cameraX, 4);
    }

    /**
     * ROM-equivalent published BG camera X copy.
     *
     * <p>CNZ keeps this as the 7/16 camera-X fraction that later animation
     * logic treats as {@code Camera_X_pos_BG_copy}.
     */
    private int cnzPublishedBgCameraX(int cameraX) {
        return asrWord(cameraX, 1) - asrWord(cameraX, 4);
    }

    private void applyDeformation(int[] horizScrollBuf, short fgScroll, short bgScroll) {
        int packed = packScrollWords(fgScroll, bgScroll);
        trackOffset(fgScroll, bgScroll);
        for (int i = 0; i < VISIBLE_LINES; i++) {
            horizScrollBuf[i] = packed;
        }
    }

    private CnzZoneRuntimeState cnzRuntimeState() {
        if (!GameServices.hasRuntime()) {
            return null;
        }
        return GameServices.zoneRuntimeRegistry().currentAs(CnzZoneRuntimeState.class).orElse(null);
    }

    private int resolveShakeOffsetY() {
        Camera camera = GameServices.cameraOrNull();
        return camera != null ? camera.getShakeOffsetY() : 0;
    }
}
