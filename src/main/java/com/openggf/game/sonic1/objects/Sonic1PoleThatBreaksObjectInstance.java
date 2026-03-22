package com.openggf.game.sonic1.objects;
import com.openggf.game.GameServices;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic1.Sonic1ZoneFeatureProvider;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.sonic1.constants.Sonic1AnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import com.openggf.debug.DebugColor;
import java.util.List;

/**
 * Object 0x0B - Pole that breaks (LZ).
 * <p>
 * Disassembly reference: docs/s1disasm/_incObj/0B Pole that Breaks.asm
 */
public class Sonic1PoleThatBreaksObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener {

    // move.w #make_art_tile(ArtTile_LZ_Pole,2,0),obGfx(a0)
    private static final int DISPLAY_PRIORITY = 4;

    // move.b #$E1,obColType(a0), but SPECIAL category is used in-engine to
    // emulate Touch_Special/obColProp polling without automatic hurt response.
    private static final int COLLISION_FLAGS = 0x40 | 0x21;

    // move.w obX(a0),d0 / addi.w #$14,d0
    private static final int GRAB_X_OFFSET = 0x14;

    // subi.w #$18,d0
    private static final int CLIMB_MIN_Y_OFFSET = 0x18;
    // addi.w #$24,d0 (after -$18), so max is obY + $0C
    private static final int CLIMB_RANGE = 0x24;

    // move.b #1,obFrame(a0) when pole breaks.
    private static final int FRAME_NORMAL = 0;
    private static final int FRAME_BROKEN = 1;

    private enum Routine {
        ACTION,   // routine 2
        DISPLAY   // routine 4
    }

    private Routine routine = Routine.ACTION;
    private int collisionFlags = COLLISION_FLAGS;
    private int mappingFrame = FRAME_NORMAL;

    // objoff_30 / objoff_32
    private int poleTime;
    private boolean poleGrabbed;

    // obColProp emulation signal from touch callback.
    private boolean touchSignal;

    // v_jpadpress2 edge detection.
    private boolean jumpPressedPrevious;

    public Sonic1PoleThatBreaksObjectInstance(ObjectSpawn spawn) {
        super(spawn, "PoleThatBreaks");
        int subtype = spawn.subtype() & 0xFF;
        this.poleTime = subtype * 60;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (routine != Routine.ACTION) {
            return;
        }

        if (poleGrabbed) {
            updateGrabbedPlayer(player);
            return;
        }

        tryGrabPlayer(player);
    }

    private void tryGrabPlayer(AbstractPlayableSprite player) {
        if (!touchSignal || player == null || player.isCpuControlled()) {
            return;
        }

        int grabX = getX() + GRAB_X_OFFSET;
        if (player.getCentreX() <= grabX) {
            return;
        }

        // ROM clears obColProp after passing the X-side check.
        touchSignal = false;

        // cmpi.b #4,obRoutine(a1) / bhs.s Pole_Display
        if (player.isHurt() || player.getDead()) {
            return;
        }

        // clr.w obVelX(a1) / clr.w obVelY(a1)
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);

        // move.w d0,obX(a1)
        player.setCentreX((short) grabX);

        // bclr #0,obStatus(a1)
        player.setDirection(Direction.RIGHT);

        // ROM: move.b #id_Hang,obAnim(a1)
        // Clear any forcedAnimationId left by wind tunnels (FLOAT2) since the ROM
        // has no separate forced field — obAnim is simply overwritten by the pole.
        player.setForcedAnimationId(-1);
        player.setAnimationId(Sonic1AnimationIds.HANG);

        // move.b #1,(f_playerctrl).w
        player.setObjectControlled(true);

        // move.b #1,(f_wtunnelallow).w
        setWindTunnelDisabled(true);

        // move.b #1,pole_grabbed(a0)
        poleGrabbed = true;
        jumpPressedPrevious = player.isJumpPressed();
    }

    private void updateGrabbedPlayer(AbstractPlayableSprite player) {
        if (player == null) {
            releasePlayer(null, false);
            return;
        }

        if (poleTime != 0) {
            poleTime--;
            if (poleTime == 0) {
                mappingFrame = FRAME_BROKEN;
                releasePlayer(player, false);
                return;
            }
        }

        int minY = getY() - CLIMB_MIN_Y_OFFSET;
        if (player.isUpPressed()) {
            int newY = player.getCentreY() - 1;
            if (newY < minY) {
                newY = minY;
            }
            player.setCentreY((short) newY);
        }

        int maxY = minY + CLIMB_RANGE;
        if (player.isDownPressed()) {
            int newY = player.getCentreY() + 1;
            if (newY > maxY) {
                newY = maxY;
            }
            player.setCentreY((short) newY);
        }

        boolean jumpPressedNow = player.isJumpPressed();
        if (jumpPressedNow && !jumpPressedPrevious) {
            mappingFrame = FRAME_BROKEN;
            releasePlayer(player, true);
            return;
        }
        jumpPressedPrevious = jumpPressedNow;
    }

    private void releasePlayer(AbstractPlayableSprite player, boolean consumeJumpPress) {
        // clr.b obColType(a0)
        collisionFlags = 0;

        // addq.b #2,obRoutine(a0)
        routine = Routine.DISPLAY;

        // clr.b (f_playerctrl).w / clr.b (f_wtunnelallow).w
        if (player != null) {
            if (consumeJumpPress) {
                // ROM: the ABC edge that releases the pole is read by the pole object
                // after Sonic's mode logic for that frame has already run, so it does
                // not trigger an immediate jump on release.
                player.suppressNextJumpPress();
            }
            player.deferObjectControlRelease();
        }
        setWindTunnelDisabled(false);

        // clr.b pole_grabbed(a0)
        poleGrabbed = false;
        touchSignal = false;
        jumpPressedPrevious = false;
    }

    @Override
    public int getCollisionFlags() {
        return collisionFlags;
    }

    @Override
    public int getCollisionProperty() {
        return touchSignal ? 1 : 0;
    }

    @Override
    public boolean requiresContinuousTouchCallbacks() {
        return true;
    }

    @Override
    public void onTouchResponse(AbstractPlayableSprite player, TouchResponseResult result, int frameCounter) {
        if (routine != Routine.ACTION || poleGrabbed || player == null || player.isCpuControlled()) {
            return;
        }
        touchSignal = true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.LZ_BREAKABLE_POLE);
        if (renderer == null) return;
        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), hFlip, vFlip);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(DISPLAY_PRIORITY);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        if (!SonicConfigurationService.getInstance().getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED)) {
            return;
        }

        int x = getX();
        int y = getY();
        ctx.drawCross(x, y, 4, 0.9f, 0.8f, 0.2f);
        ctx.drawRect(x, y, 8, 0x20, 0.2f, 0.8f, 1.0f);

        int minY = y - CLIMB_MIN_Y_OFFSET;
        int maxY = minY + CLIMB_RANGE;
        ctx.drawLine(x + GRAB_X_OFFSET, minY, x + GRAB_X_OFFSET, maxY, 0.4f, 1.0f, 0.4f);
        ctx.drawWorldLabel(x, y, -2, "Pole t=" + poleTime + " f=" + mappingFrame
                + (poleGrabbed ? " GRAB" : ""), DebugColor.CYAN);
    }

    private void setWindTunnelDisabled(boolean disabled) {
        ZoneFeatureProvider provider = GameServices.level().getZoneFeatureProvider();
        if (provider instanceof Sonic1ZoneFeatureProvider sonic1Provider) {
            sonic1Provider.setWindTunnelDisabled(disabled);
        }
    }
}
