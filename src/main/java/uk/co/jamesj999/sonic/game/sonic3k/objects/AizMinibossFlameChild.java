package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.objects.boss.AbstractBossInstance;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * AIZ miniboss flame child.
 *
 * ROM: Obj_AIZMiniboss_Flame (sub_6890E / loc_68956 / loc_68962)
 * - collision_flags=$8B while active flame
 * - shield_reaction bit 4
 * - wait delay based on subtype, then flame anim, then explosion anim
 */
public class AizMinibossFlameChild extends AbstractObjectInstance implements TouchResponseProvider {
    private static final int COLLISION_FLAGS = 0x8B;
    private static final int SHIELD_REACTION = 1 << 4;

    private enum Phase {
        WAIT,
        FLAME,
        EXPLODE
    }

    private final AbstractBossInstance parent;
    private final int xOffset;
    private final int yOffset;
    private final int subtype;

    private Phase phase = Phase.WAIT;
    private int waitTimer;
    private int frame;
    private int animTimer;
    private int phaseTimer;

    private int worldX;
    private int worldY;

    public AizMinibossFlameChild(AbstractBossInstance parent, int xOffset, int yOffset, int subtype) {
        super(new ObjectSpawn(
                parent != null ? parent.getX() + xOffset : xOffset,
                parent != null ? parent.getY() + yOffset : yOffset,
                0x90,
                subtype,
                0,
                false,
                0), "AIZMinibossFlame");
        this.parent = parent;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.subtype = subtype & 0xFF;
        this.worldX = spawn.x();
        this.worldY = spawn.y();

        // ROM loc_68928: timer = (6 - subtype) * 2
        int raw = (6 - this.subtype) * 2;
        this.waitTimer = Math.max(0, raw);
        this.frame = 0;
        this.animTimer = 0;
        this.phaseTimer = 0;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        syncWithParent();

        switch (phase) {
            case WAIT -> {
                waitTimer--;
                if (waitTimer < 0) {
                    phase = Phase.FLAME;
                    frame = 0;
                    animTimer = 1;
                    phaseTimer = 12;
                }
            }
            case FLAME -> {
                animTimer--;
                if (animTimer <= 0) {
                    animTimer = 2;
                    frame = (frame == 0) ? 1 : 0;
                }
                phaseTimer--;
                if (phaseTimer <= 0) {
                    phase = Phase.EXPLODE;
                    frame = 2;
                    animTimer = 3;
                }
            }
            case EXPLODE -> {
                animTimer--;
                if (animTimer > 0) {
                    return;
                }
                animTimer = 3;
                frame++;
                if (frame > 4) {
                    setDestroyed(true);
                }
            }
        }
    }

    private void syncWithParent() {
        if (parent == null || parent.isDestroyed()) {
            return;
        }

        int signedXOffset = xOffset;
        if ((parent.getState().renderFlags & 1) != 0) {
            signedXOffset = -signedXOffset;
        }
        worldX = parent.getX() + signedXOffset;
        worldY = parent.getY() + yOffset;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (phase == Phase.WAIT) {
            return;
        }
        ObjectRenderManager rm = LevelManager.getInstance().getObjectRenderManager();
        if (rm == null) {
            return;
        }
        PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS_FLAME);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(frame, worldX, worldY, false, false);
    }

    @Override
    public int getX() {
        return worldX;
    }

    @Override
    public int getY() {
        return worldY;
    }

    @Override
    public int getCollisionFlags() {
        if (phase != Phase.FLAME || isDestroyed()) {
            return 0;
        }
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public int getShieldReactionFlags() {
        return SHIELD_REACTION;
    }

    @Override
    public boolean onShieldDeflect(AbstractPlayableSprite player) {
        setDestroyed(true);
        return true;
    }
}
