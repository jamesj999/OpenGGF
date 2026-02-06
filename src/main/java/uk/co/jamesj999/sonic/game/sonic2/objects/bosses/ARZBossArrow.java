package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.SolidContact;
import uk.co.jamesj999.sonic.level.objects.SolidObjectListener;
import uk.co.jamesj999.sonic.level.objects.SolidObjectParams;
import uk.co.jamesj999.sonic.level.objects.SolidObjectProvider;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ARZ Boss Arrow - Projectile that becomes a platform.
 * ROM Reference: s2.asm Obj89 (subtype 6)
 *
 * States:
 * - INIT: Initialize arrow position/velocity
 * - FLYING: Arrow flies toward target pillar
 * - STUCK: Arrow stuck in pillar, acts as platform
 * - FALLING: Arrow falls when stood on too long or boss defeated
 */
public class ARZBossArrow extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, TouchResponseProvider {

    private static final int ARROW_SUB_INIT = 0;
    private static final int ARROW_SUB_FLYING = 2;
    private static final int ARROW_SUB_STUCK = 4;
    private static final int ARROW_SUB_FALLING = 6;

    private static final int LEFT_ARROW_STOP_X = 0x2A77;
    private static final int RIGHT_ARROW_STOP_X = 0x2B49;
    private static final int ARROW_FLOOR_Y = 0x4F0;
    private static final int ARROW_COLLISION_FLAGS = 0xB0;

    private static final SolidObjectParams ARROW_SOLID_PARAMS = new SolidObjectParams(0x1B, 1, 2);

    private static final int[][] ARZ_ARROW_ANIMS = {
            { 1, 4, 6, 5, 4, 6, 4, 5, 4, 6, 4, 4, 6, 5, 4, 6, 4, 5, 4, 6, 4, 0xFD, 1 },
            { 0x0F, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
                    4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 0xF9 }
    };

    private final LevelManager levelManager;
    private final Sonic2ARZBossInstance mainBoss;
    private final ARZBossEyes eyes;
    private final boolean fromRightPillar;

    private int x;
    private int y;
    private int renderFlags;
    private int routineState;
    private int mappingFrame;
    private int collisionFlags;
    private int xVel;
    private int yVel;
    private int arrowTimer;

    // Animation state
    private int arrowAnim;
    private int arrowAnimLast = -1;
    private int arrowAnimFrame;
    private int arrowAnimTimer;

    public ARZBossArrow(ObjectSpawn spawn, LevelManager levelManager, Sonic2ARZBossInstance mainBoss, ARZBossEyes eyes, boolean fromRightPillar) {
        super(spawn, "ARZ Boss Arrow");
        this.levelManager = levelManager;
        this.mainBoss = mainBoss;
        this.eyes = eyes;
        this.fromRightPillar = fromRightPillar;
        this.x = spawn.x();
        this.y = spawn.y();
        this.renderFlags = spawn.renderFlags();
        this.routineState = ARROW_SUB_INIT;
        this.mappingFrame = 4;
        this.collisionFlags = 0;
        this.arrowAnim = 0;
        this.arrowTimer = 0;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        // Check if boss defeated
        if (mainBoss != null && mainBoss.isInDefeatSequence()) {
            routineState = ARROW_SUB_FALLING;
        }

        switch (routineState) {
            case ARROW_SUB_INIT -> initArrow();
            case ARROW_SUB_FLYING -> updateArrowFlying();
            case ARROW_SUB_STUCK -> updateArrowStuck();
            case ARROW_SUB_FALLING -> updateArrowFalling(player);
        }
    }

    private void initArrow() {
        routineState = ARROW_SUB_FLYING;
        collisionFlags = ARROW_COLLISION_FLAGS;
        mappingFrame = 4;
        yVel = 4;

        if (eyes != null) {
            x = eyes.getX();
            y = eyes.getY();
        }
        y += 9;

        if (fromRightPillar) {
            renderFlags |= 1;
            xVel = -3;
        } else {
            xVel = 3;
        }
    }

    private void updateArrowFlying() {
        int nextX = x + xVel;
        if (xVel < 0) {
            if (nextX <= LEFT_ARROW_STOP_X) {
                x = LEFT_ARROW_STOP_X;
                routineState = ARROW_SUB_STUCK;
                AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_ARROW_STICK);
            } else {
                x = nextX;
            }
        } else {
            if (nextX >= RIGHT_ARROW_STOP_X) {
                x = RIGHT_ARROW_STOP_X;
                routineState = ARROW_SUB_STUCK;
                AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_ARROW_STICK);
            } else {
                x = nextX;
            }
        }
    }

    private void updateArrowStuck() {
        collisionFlags = 0;
        updateArrowPlatform();
        animateArrow();
        if (mappingFrame == 0) {
            mappingFrame = 4;
        }
    }

    private void updateArrowFalling(AbstractPlayableSprite player) {
        dropPlayers(player);
        int nextY = y + yVel;
        if (nextY > ARROW_FLOOR_Y) {
            setDestroyed(true);
            return;
        }
        y = nextY;
    }

    private void updateArrowPlatform() {
        if (arrowTimer == 0) {
            return;
        }
        arrowTimer--;
        if (arrowTimer == 0) {
            routineState = ARROW_SUB_FALLING;
        }
    }

    private void dropPlayers(AbstractPlayableSprite player) {
        if (player == null || levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        if (!levelManager.getObjectManager().isRidingObject(player, this)) {
            return;
        }
        levelManager.getObjectManager().clearRidingObject(player);
        player.setOnObject(false);
        player.setAir(true);
    }

    private void animateArrow() {
        int[] script = ARZ_ARROW_ANIMS[arrowAnim];
        int delay = script[0] & 0xFF;
        if (arrowAnim != arrowAnimLast) {
            arrowAnimFrame = 0;
            arrowAnimTimer = delay;
            arrowAnimLast = arrowAnim;
        }
        arrowAnimTimer--;
        if (arrowAnimTimer >= 0) {
            return;
        }
        arrowAnimTimer = delay;
        int frameValue = script[1 + arrowAnimFrame] & 0xFF;
        if ((frameValue & 0x80) != 0) {
            handleArrowCommand(frameValue, script);
            return;
        }
        mappingFrame = frameValue & 0x7F;
        arrowAnimFrame++;
    }

    private void handleArrowCommand(int command, int[] script) {
        if (command == 0xFF) {
            arrowAnimFrame = 0;
            mappingFrame = script[1] & 0x7F;
            arrowAnimFrame++;
            return;
        }
        if (command == 0xFD) {
            int nextAnim = script[2 + arrowAnimFrame] & 0xFF;
            arrowAnim = nextAnim;
            arrowAnimLast = -1;
            return;
        }
        if (command == 0xF9) {
            routineState = ARROW_SUB_FALLING;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager != null ? levelManager.getObjectRenderManager() : null;
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.ARZ_BOSS_PARTS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean hFlip = (renderFlags & 1) != 0;
        renderer.drawFrameIndex(mappingFrame, x, y, hFlip, false);
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
        return 4;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return ARROW_SOLID_PARAMS;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return routineState == ARROW_SUB_STUCK;
    }

    @Override
    public boolean isTopSolidOnly() {
        return routineState == ARROW_SUB_STUCK;
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (routineState != ARROW_SUB_STUCK) {
            return;
        }
        if (!contact.standing()) {
            return;
        }
        if (arrowTimer == 0) {
            arrowTimer = 0x1F;
        }
    }

    @Override
    public int getCollisionFlags() {
        return collisionFlags;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), renderFlags, spawn.respawnTracked(), spawn.rawYWord());
    }
}
