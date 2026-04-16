package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ROM object: {@code Obj_CNZBalloon}.
 *
 * <p>CNZ balloons are local launchers, not path-following transport objects.
 * The S3K disassembly loads {@code Map_CNZBalloon} and uses the balloon's
 * center position as the contact anchor; on contact it applies the ROM bounce
 * impulse and restores normal player control.
 *
 * <p>The art sheet is loaded from the verified CNZ mapping table in
 * {@link com.openggf.game.sonic3k.Sonic3kObjectArt} using the lock-on ROM
 * offsets captured in {@code Sonic3kConstants}. The subtype's low 3 bits select
 * the balloon color variant, matching the SonLVL CNZ definition.
 */
public final class CnzBalloonInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener {

    private static final int COLLISION_FLAGS = 0x40 | 0x17;
    private static final int WIDTH_HALF = 0x10;
    private static final int HEIGHT_HALF = 0x20;
    private static final int ROM_BOUNCE_Y_SPEED = 0x700;
    private static final int BOB_AMPLITUDE = 8;
    private static final int[] FRAME_BY_COLOR = {0, 5, 10, 15, 20};

    private final int subtype;
    private final int baseY;
    private int angle;
    private int lastLaunchFrame = Integer.MIN_VALUE;

    public CnzBalloonInstance(ObjectSpawn spawn) {
        super(spawn, "CNZBalloon");
        this.subtype = spawn.subtype();
        this.baseY = spawn.y();
        this.angle = (spawn.x() ^ spawn.y() ^ subtype) & 0xFF;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        int bobbedY = baseY + bobOffset(angle);
        updateDynamicSpawn(spawn.x(), bobbedY);
        angle = (angle + 1) & 0xFF;

        if (playerEntity != null && isTouchingPlayer(playerEntity)) {
            launchPlayer(playerEntity, frameCounter);
        }
    }

    @Override
    public int getCollisionFlags() {
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public boolean requiresContinuousTouchCallbacks() {
        return true;
    }

    @Override
    public void onTouchResponse(PlayableEntity player, TouchResponseResult result, int frameCounter) {
        launchPlayer(player, frameCounter);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.CNZ_BALLOON);
        if (renderer == null) {
            return;
        }

        boolean hFlip = (spawn.renderFlags() & 0x01) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x02) != 0;
        renderer.drawFrameIndex(getFrameIndex(), getX(), getY(), hFlip, vFlip);
    }

    private int getFrameIndex() {
        int color = subtype & 0x07;
        if (color >= FRAME_BY_COLOR.length) {
            color = FRAME_BY_COLOR.length - 1;
        }
        return FRAME_BY_COLOR[color];
    }

    private void launchPlayer(PlayableEntity playerEntity, int frameCounter) {
        if (playerEntity == null || lastLaunchFrame == frameCounter) {
            return;
        }
        lastLaunchFrame = frameCounter;

        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        player.setCentreX((short) getX());
        player.setCentreY((short) getY());
        player.setAir(true);
        player.setJumping(false);
        player.setRolling(false);
        player.setObjectControlled(false);
        player.setControlLocked(false);
        player.setXSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setYSpeed((short) -ROM_BOUNCE_Y_SPEED);
    }

    private boolean isTouchingPlayer(PlayableEntity player) {
        int dx = Math.abs(player.getCentreX() - getX());
        int dy = Math.abs(player.getCentreY() - getY());
        return dx <= WIDTH_HALF && dy <= HEIGHT_HALF;
    }

    private static int bobOffset(int angle) {
        return (int) Math.round(Math.sin((angle & 0xFF) * (Math.PI * 2.0 / 256.0)) * BOB_AMPLITUDE);
    }
}
