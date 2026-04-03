package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.graphics.GLCommand;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;
import java.util.logging.Logger;

/**
 * Small Eggman craft that flies across the screen after the AIZ2 bombing sequence.
 *
 * <p>ROM: Obj_AIZ2BossSmall (sonic3k.asm).
 * Movement arc:
 * <ol>
 *   <li>Wait until Camera_X_pos &gt;= $4670</li>
 *   <li>Move right with initial xVel = 5.0 px/frame ($50000 in 16:16)</li>
 *   <li>Decelerate by $E80/frame until velocity reaches -$10000</li>
 *   <li>Then accelerate by $E80/frame (velocity goes positive again and keeps increasing)</li>
 *   <li>When x_pos &gt;= $240 screen-relative: clear scroll lock, stop auto-scroll, delete self</li>
 * </ol>
 *
 * <p>Every 16 frames plays {@code cfx_RobotnikSiren}.
 * Rendered using {@link Sonic3kObjectArtKeys#AIZ2_BOSS_SMALL} mapping frame 0.
 */
public class AizBossSmallInstance extends AbstractObjectInstance {
    private static final Logger LOG = Logger.getLogger(AizBossSmallInstance.class.getName());

    /** Camera X threshold to start movement. ROM: cmpi.w #$4670,(Camera_X_pos).w */
    private static final int CAMERA_WAIT_X = 0x4670;

    /** Initial screen-relative X position. ROM: VDP X $30 → screen $30-$80 = -$50 (off-screen left). */
    private static final int INITIAL_SCREEN_X = 0x30 - 0x80; // -80: slides in from left

    /** Initial screen-relative Y position. ROM: VDP Y $D8 → screen $D8-$80 = $58. */
    private static final int INITIAL_SCREEN_Y = 0xD8 - 0x80; // 88: vertically centered

    /** Initial X velocity in 16:16 fixed-point (5.0 px/frame). */
    private static final int INITIAL_X_VEL = 0x50000;

    /** Deceleration/acceleration per frame in 16:16 fixed-point. */
    private static final int ACCEL = 0xE80;

    /** Minimum velocity before switching from decelerate to accelerate. */
    private static final int MIN_VEL = -0x10000;

    /** Screen X threshold to exit. ROM: VDP X $240 → screen $240-$80 = $1C0 (past right edge). */
    private static final int EXIT_SCREEN_X = 0x240 - 0x80; // 448: fully off-screen right

    /** Camera max X to set on exit. */
    private static final int EXIT_CAMERA_MAX_X = 0x6000;

    private int screenX;
    private int screenY;
    private int xSub; // 16-bit fractional part
    private int xVel; // 16:16 fixed-point
    private int frameCounter;
    private boolean movementStarted;
    private boolean decelerating;
    private boolean paletteLoaded;

    public AizBossSmallInstance() {
        super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "AIZBossSmall");
        this.screenX = INITIAL_SCREEN_X;
        this.screenY = INITIAL_SCREEN_Y;
        this.xSub = 0;
        this.xVel = INITIAL_X_VEL;
        this.frameCounter = 0;
        this.movementStarted = false;
        this.decelerating = true;
        this.paletteLoaded = false;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (isDestroyed()) return;

        this.frameCounter++;

        // ROM patches Normal_palette_line_2+$2 with Pal_AIZBossSmall.
        if (!paletteLoaded) {
            paletteLoaded = true;
            try {
                byte[] palData = services().rom().readBytes(
                        Sonic3kConstants.PAL_AIZ_BOSS_SMALL_ADDR, 28);
                applyPalettePatch(1, 2, palData);
            } catch (Exception e) {
                // Non-fatal
            }
        }

        // Play siren SFX every 16 frames
        if ((this.frameCounter & 0xF) == 0) {
            services().playSfx(Sonic3kSfx.ROBOTNIK_SIREN.id);
        }

        // Wait for camera to reach the trigger point
        if (!movementStarted) {
            int cameraX = services().camera().getX();
            if (cameraX < CAMERA_WAIT_X) {
                return;
            }
            movementStarted = true;
            LOG.info("AIZ2 BossSmall: movement started at cameraX=0x"
                    + Integer.toHexString(cameraX));
        }

        // Apply deceleration / acceleration arc
        if (decelerating) {
            xVel -= ACCEL;
            if (xVel <= MIN_VEL) {
                decelerating = false;
            }
        } else {
            xVel += ACCEL;
        }

        // Apply velocity to screen position (16:16 fixed-point)
        int xPos32 = (screenX << 16) | (xSub & 0xFFFF);
        xPos32 += xVel;
        screenX = xPos32 >> 16;
        xSub = xPos32 & 0xFFFF;

        // Check exit condition
        if (screenX >= EXIT_SCREEN_X) {
            onExitScreen();
        }
    }

    private void onExitScreen() {
        // Signal the event system to unlock camera and stop auto-scroll
        Sonic3kAIZEvents events = getAizEvents();
        if (events != null) {
            events.onBossSmallComplete();
        }

        LOG.info("AIZ2 BossSmall: exited screen at screenX=" + screenX
                + " after " + frameCounter + " frames");
        setDestroyed(true);
    }

    private Sonic3kAIZEvents getAizEvents() {
        try {
            return ((Sonic3kLevelEventManager) services().levelEventProvider()).getAizEvents();
        } catch (Exception e) {
            return null;
        }
    }

    private void applyPalettePatch(int paletteIndex, int byteOffset, byte[] patchData) {
        Level level = services().currentLevel();
        if (level == null || patchData == null || patchData.length == 0) {
            return;
        }
        if (paletteIndex < 0 || paletteIndex >= level.getPaletteCount()) {
            return;
        }

        byte[] lineData = toSegaLine(level.getPalette(paletteIndex));
        if (byteOffset < 0 || byteOffset + patchData.length > lineData.length) {
            return;
        }

        System.arraycopy(patchData, 0, lineData, byteOffset, patchData.length);
        services().updatePalette(paletteIndex, lineData);
    }

    private static byte[] toSegaLine(Palette palette) {
        byte[] lineData = new byte[Palette.PALETTE_SIZE_IN_ROM];
        for (int i = 0; i < Palette.PALETTE_SIZE; i++) {
            int sega = toSegaColorWord(palette.getColor(i));
            int offset = i * 2;
            lineData[offset] = (byte) ((sega >>> 8) & 0xFF);
            lineData[offset + 1] = (byte) (sega & 0xFF);
        }
        return lineData;
    }

    private static int toSegaColorWord(Palette.Color color) {
        if (color == null) {
            return 0;
        }
        int r3 = ((color.r & 0xFF) * 7 + 127) / 255;
        int g3 = ((color.g & 0xFF) * 7 + 127) / 255;
        int b3 = ((color.b & 0xFF) * 7 + 127) / 255;
        return ((b3 & 0x7) << 9) | ((g3 & 0x7) << 5) | ((r3 & 0x7) << 1);
    }

    /**
     * Returns world X position (screen-relative + camera offset).
     */
    @Override
    public int getX() {
        try {
            return services().camera().getX() + screenX;
        } catch (Exception e) {
            return screenX;
        }
    }

    /**
     * Returns world Y position (screen-relative + camera offset).
     */
    @Override
    public int getY() {
        try {
            return services().camera().getY() + screenY;
        } catch (Exception e) {
            return screenY;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;

        ObjectRenderManager rm = services().renderManager();
        if (rm == null) return;

        PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.AIZ2_BOSS_SMALL);
        if (renderer == null || !renderer.isReady()) return;

        renderer.drawFrameIndex(0, getX(), getY(), false, false);
    }

    /**
     * ROM: make_art_tile(ArtTile_AIZ2Bombership,0,0) — NO priority bit.
     * Robotnik renders BEHIND the high-priority tree sprites (bucket 3 high-pri),
     * creating the depth effect where the craft flies between BG and tree silhouettes.
     */
    @Override
    public boolean isHighPriority() { return false; }

    @Override
    public int getPriorityBucket() { return 2; }
}
