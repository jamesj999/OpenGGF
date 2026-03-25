package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Projectile fired by WallTurret (Object B8) in Wing Fortress Zone.
 * <p>
 * Uses the Obj98 (Projectile) system with WallTurretShotMove behavior.
 * <p>
 * Behavior from disassembly (s2.asm lines 74197-74200):
 * <ul>
 *   <li>Obj98_WallTurretShotMove: ObjectMove (apply velocity) + AnimateSprite</li>
 *   <li>Travels in a fixed direction based on turret aim (velocities set by parent)</li>
 *   <li>Deleted when off screen (Obj98_Main: _beq.w JmpTo65_DeleteObject)</li>
 * </ul>
 * <p>
 * SubObjData2 (ObjB8_SubObjData2 at 0x377BE):
 * mappings = ObjB8_Obj98_MapUnc_3BA46, art_tile = ArtTile_ArtNem_WfzWallTurret (palette 0),
 * render_flags = on_screen|level_fg, priority = 3, width_pixels = 4, collision_flags = 0x98.
 * <p>
 * Animation (Ani_WallTurretShot): delay=2, frames 3,4 looping ($FF terminator).
 * Initial mapping_frame = 3 (set by parent: move.b #3,mapping_frame(a1)).
 */
public class WallTurretShotInstance extends AbstractObjectInstance
        implements TouchResponseProvider {
    private static final Logger LOGGER = Logger.getLogger(WallTurretShotInstance.class.getName());

    // From ObjB8_SubObjData2: priority=3, width_pixels=4, collision_flags=$98
    private static final int PRIORITY = 3;
    private static final int WIDTH_PIXELS = 4;

    // collision_flags = 0x98: HURT (bit 7 set, bits 6-5 = 00 -> touch_hurt category) + size index 0x18
    private static final int COLLISION_FLAGS = 0x98;

    // Animation: Ani_WallTurretShot = delay 2, frames {3, 4, $FF (loop)}
    private static final int ANIM_DELAY = 2;
    private static final int[] ANIM_FRAMES = {3, 4};

    // State
    private int currentX;
    private int currentY;
    private int xVelocity;   // Fixed-point 8.8
    private int yVelocity;   // Fixed-point 8.8
    private final SubpixelMotion.State motionState; // Subpixel position/velocity state
    private int animTimer;
    private int animIndex;
    private int mappingFrame; // 3 or 4 (projectile art frames from shared mapping set)

    public WallTurretShotInstance(ObjectSpawn parentSpawn, int startX, int startY,
                                  int xVel, int yVel) {
        super(createShotSpawn(parentSpawn, startX, startY), "WallTurretShot");
        this.currentX = startX;
        this.currentY = startY;
        this.xVelocity = xVel;
        this.yVelocity = yVel;
        this.motionState = new SubpixelMotion.State(startX, startY, 0, 0, xVel, yVel);
        // Initial mapping_frame = 3 (set by parent: move.b #3,mapping_frame(a1))
        this.mappingFrame = 3;
        // ROM: object slot is zero-cleared by AllocateObjectAfterCurrent, so
        // anim_frame_duration starts at 0. First AnimateSprite call does subq.b #1
        // (0 -> 0xFF, negative), so bpl is not taken and animation processes immediately.
        this.animTimer = 0;
        this.animIndex = 0;
    }

    private static ObjectSpawn createShotSpawn(ObjectSpawn parent, int x, int y) {
        return new ObjectSpawn(
                x, y,
                parent.objectId(),
                parent.subtype(),
                parent.renderFlags(),
                false, // Don't track respawn for projectiles
                parent.rawYWord());
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        // Obj98_WallTurretShotMove:
        //   jsrto JmpTo26_ObjectMove   ; apply velocity to position
        //   lea (Ani_WallTurretShot).l,a1
        //   jmpto JmpTo25_AnimateSprite

        // ObjectMove: apply velocity to position (fixed-point 8.8)
        motionState.x = currentX;
        motionState.y = currentY;
        motionState.xVel = xVelocity;
        motionState.yVel = yVelocity;
        SubpixelMotion.moveSprite2(motionState);
        currentX = motionState.x;
        currentY = motionState.y;

        // AnimateSprite: cycle through animation frames
        animTimer--;
        if (animTimer < 0) {
            animTimer = ANIM_DELAY;
            animIndex = (animIndex + 1) % ANIM_FRAMES.length;
            mappingFrame = ANIM_FRAMES[animIndex];
        }

        // Obj98_Main: _btst #render_flags.on_screen / _beq.w JmpTo65_DeleteObject
        // Delete when off screen
        if (!isOnScreen(480)) {
            setDestroyed(true);
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
    public ObjectSpawn getSpawn() {
        // Return dynamic spawn with current position for collision detection
        return buildSpawnAt(currentX, currentY);
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.WFZ_WALL_TURRET);
        if (renderer == null) {
            appendDebugBox(commands);
            return;
        }

        // Mapping frames 3 and 4 are the projectile frames from the shared sprite sheet
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, false, false);
    }

    private void appendDebugBox(List<GLCommand> commands) {
        int halfWidth = WIDTH_PIXELS;
        int halfHeight = WIDTH_PIXELS;
        int left = currentX - halfWidth;
        int right = currentX + halfWidth;
        int top = currentY - halfHeight;
        int bottom = currentY + halfHeight;

        float r = 0.8f, g = 0.2f, b = 0.2f;
        appendLine(commands, left, top, right, top, r, g, b);
        appendLine(commands, right, top, right, bottom, r, g, b);
        appendLine(commands, right, bottom, left, bottom, r, g, b);
        appendLine(commands, left, bottom, left, top, r, g, b);
    }

    private void appendLine(List<GLCommand> commands, int x1, int y1, int x2, int y2,
                            float r, float g, float b) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x2, y2, 0, 0));
    }
}
