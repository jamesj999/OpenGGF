package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * MCZ Boss falling debris - stones and spikes spawned during descent phases.
 * ROM Reference: Obj57_FallingStuff (s2.asm:65856-65861)
 *
 * Stones: frame 0x0D in ROM (frame 0 in our sheet), no collision hazard
 * Spikes: frame 0x14 in ROM (frame 1 in our sheet), collision_flags 0xB1 (hazard)
 *
 * Uses ObjectMoveAndFall for gravity. Deleted when Y >= 0x6F0.
 */
public class MCZFallingDebrisInstance extends AbstractObjectInstance implements TouchResponseProvider {

    // ROM: ObjectMoveAndFall adds $38 to y_vel, then subi.w #$28,y_vel(a0)
    // sub2_y_pos at SST offset $12 aliases y_vel. Net gravity = $38 - $28 = $10
    private static final int GRAVITY = 0x10;
    private static final int DELETE_Y = 0x6F0;

    private final boolean isSpike;
    private int posX;
    private int posY;
    private int yFixed;
    private int yVel;
    private ObjectSpawn dynamicSpawn;

    public MCZFallingDebrisInstance(int x, int y, boolean isSpike) {
        super(new ObjectSpawn(x, y, 0x57, 4, 0, false, 0), "MCZ Debris");
        this.isSpike = isSpike;
        this.posX = x;
        this.posY = y;
        this.yFixed = y << 16;
        this.yVel = 0;
        this.dynamicSpawn = getSpawn();
    }

    @Override
    public ObjectSpawn getSpawn() {
        if (dynamicSpawn != null && dynamicSpawn.x() == posX && dynamicSpawn.y() == posY) {
            return dynamicSpawn;
        }
        dynamicSpawn = new ObjectSpawn(posX, posY, 0x57, 4, 0, false, 0);
        return dynamicSpawn;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // ROM: Obj57_FallingStuff - ObjectMoveAndFall
        yFixed += (yVel << 8);
        yVel += GRAVITY;

        posY = yFixed >> 16;

        // ROM: cmpi.w #$6F0,y_pos(a0) - delete if below boundary
        if (posY > DELETE_Y) {
            setDestroyed(true);
        }
    }

    @Override
    public int getCollisionFlags() {
        // ROM: Spikes have collision_flags $B1 (hazard), stones have none
        return isSpike ? 0xB1 : 0;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public int getPriorityBucket() {
        return 3; // ROM: move.b #3,priority(a1)
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.MCZ_FALLING_ROCKS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Frame 0 = stone, Frame 1 = spike
        int frame = isSpike ? 1 : 0;
        renderer.drawFrameIndex(frame, posX, posY, false, false);
    }
}
