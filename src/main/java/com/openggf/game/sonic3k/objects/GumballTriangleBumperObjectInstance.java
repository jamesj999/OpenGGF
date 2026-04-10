package com.openggf.game.sonic3k.objects;

import com.openggf.audio.GameSound;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
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

import java.util.List;

/**
 * Object 0x87 - Gumball Triangle Bumper (Sonic 3 & Knuckles Gumball bonus stage).
 * <p>
 * ROM reference: sonic3k.asm Obj_GumballTriangleBumper (line 127634).
 * <p>
 * A fixed triangular bumper that bounces the player with fixed velocity on contact:
 * <ul>
 *   <li>X velocity: +/-0x300 (direction determined by h-flip render flag)</li>
 *   <li>Y velocity: -0x600 (always upward)</li>
 *   <li>Sets player airborne, clears riding/on-object flags, sets facing direction</li>
 *   <li>Sets player animation to SPRING (0x10), clears jumping flag</li>
 *   <li>0x0F frame cooldown between bounces</li>
 *   <li>Plays sfx_Spring on bounce</li>
 * </ul>
 * <p>
 * ROM attributes (ObjDat3_613A4):
 * <ul>
 *   <li>Mappings: Map_GumballBonus</li>
 *   <li>Art tile: make_art_tile(ArtTile_BonusStage, 1, 1) = palette 1, high priority</li>
 *   <li>Priority: $0100</li>
 *   <li>Sprite width: 4, height: $10</li>
 *   <li>Mapping frame: $12</li>
 * </ul>
 * <p>
 * ROM collision: SolidObjectFull with D1=$D (13), D2=8, D3=$11 (17).
 * On player standing or side push contact, applies bounce and deletes self
 * (the Gumball machine respawns bumpers). For placed-engine operation we keep
 * the bumper inert after a hit, and add a symmetric proximity fallback so
 * mirrored bumpers still trigger when the generic solid-contact classifier
 * drops an edge case.
 */
public class GumballTriangleBumperObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final int BOUNCE_X_SPEED = 0x300;
    private static final int BOUNCE_Y_SPEED = -0x600;
    private static final int MAPPING_FRAME = 0x12;
    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(13, 8, 17);
    private boolean consumed;

    public GumballTriangleBumperObjectInstance(ObjectSpawn spawn) {
        super(spawn, "GumballTriangleBumper");
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (consumed) {
            return;
        }
        if (playerEntity instanceof AbstractPlayableSprite player) {
            tryFallbackBounce(player);
        }
        for (PlayableEntity sidekickEntity : services().sidekicks()) {
            if (sidekickEntity instanceof AbstractPlayableSprite sidekick) {
                tryFallbackBounce(sidekick);
            }
        }
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        return !consumed;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        if (!(playerEntity instanceof AbstractPlayableSprite player)) {
            return;
        }
        if (consumed) {
            return;
        }

        GumballMachineObjectInstance machine = currentMachineForThisContext();
        if (machine != null && !machine.areBumpersActive()) {
            return;
        }

        if (!contact.standing() && !contact.touchSide()) {
            return;
        }

        applyBounce(player);
    }

    private void tryFallbackBounce(AbstractPlayableSprite player) {
        if (consumed || player == null) {
            return;
        }

        GumballMachineObjectInstance machine = currentMachineForThisContext();
        if (machine != null && !machine.areBumpersActive()) {
            return;
        }
        if (player.isObjectControlled() || player.isControlLocked()) {
            return;
        }

        int halfPlayerWidth = Math.max(1, player.getWidth() / 2);
        int halfPlayerHeight = Math.max(1, player.getHeight() / 2);
        int dx = player.getCentreX() - spawn.x();
        int dy = player.getCentreY() - spawn.y();
        int xRange = SOLID_PARAMS.halfWidth() + halfPlayerWidth;
        int yRangeAbove = SOLID_PARAMS.airHalfHeight() + halfPlayerHeight;
        int yRangeBelow = SOLID_PARAMS.groundHalfHeight() + halfPlayerHeight;

        if (dx < -xRange || dx > xRange || dy < -yRangeAbove || dy > yRangeBelow) {
            return;
        }
        if (!player.getAir() && !player.isOnObject() && player.getXSpeed() == 0 && player.getYSpeed() == 0) {
            return;
        }

        applyBounce(player);
    }

    private void applyBounce(AbstractPlayableSprite player) {
        boolean hFlipped = (spawn.renderFlags() & 0x1) != 0;

        int xSpeed;
        if (hFlipped) {
            xSpeed = -BOUNCE_X_SPEED;
            player.setDirection(Direction.LEFT);
        } else {
            xSpeed = BOUNCE_X_SPEED;
            player.setDirection(Direction.RIGHT);
        }

        player.setXSpeed((short) xSpeed);
        player.setGSpeed((short) xSpeed);
        player.setYSpeed((short) BOUNCE_Y_SPEED);
        player.setAir(true);
        player.setOnObject(false);
        player.setAnimationId(Sonic3kAnimationIds.SPRING);
        player.setJumping(false);

        try {
            services().playSfx(GameSound.SPRING);
        } catch (Exception e) {
            // Prevent audio failure from breaking game logic.
        }

        GumballMachineObjectInstance machine = currentMachineForThisContext();
        if (machine != null) {
            machine.onBumperHit(spawn.subtype() & 0xFF);
        }

        consumed = true;
    }

    private GumballMachineObjectInstance currentMachineForThisContext() {
        GumballMachineObjectInstance machine = GumballMachineObjectInstance.current();
        if (machine == null || services().currentLevel() == null) {
            return null;
        }
        return machine;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(2);
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (consumed) {
            return;
        }
        if (!GumballMachineObjectInstance.shouldDebugRender(
                getPriorityBucket(), isHighPriority(), GumballMachineObjectInstance.DEBUG_SOURCE_BUMPER)) {
            return;
        }
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.GUMBALL_BONUS);
        if (renderer == null) {
            return;
        }

        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
        renderer.drawFrameIndex(MAPPING_FRAME, spawn.x(), spawn.y(), hFlip, vFlip);
    }
}
