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
