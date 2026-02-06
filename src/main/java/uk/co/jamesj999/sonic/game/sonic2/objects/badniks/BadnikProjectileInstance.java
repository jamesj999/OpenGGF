package uk.co.jamesj999.sonic.game.sonic2.objects.badniks;

import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Projectile fired by Badniks (Buzzer stinger, Coconuts coconut).
 * Moves with configurable velocity and optional gravity.
 */
public class BadnikProjectileInstance extends AbstractObjectInstance
        implements TouchResponseProvider {

    public enum ProjectileType {
        BUZZER_STINGER,
        COCONUT,
        SPINY_SPIKE,
        REXON_FIREBALL
    }

    private static final int COLLISION_SIZE_STINGER = 0x18; // From disassembly $98 & 0x3F
    private static final int COLLISION_SIZE_COCONUT = 0x0B; // From disassembly $8B & 0x3F
    private static final int COLLISION_SIZE_SPINY_SPIKE = 0x0B; // Same as coconut
    private static final int COLLISION_SIZE_REXON_FIREBALL = 0x18; // From disassembly $98 & 0x3F
    private static final int GRAVITY_COCONUT = 0x20; // Obj98_CoconutFall
    private static final int GRAVITY_SPINY_SPIKE = 0x20; // From disassembly +$20 per frame
    private static final int GRAVITY_REXON_FIREBALL = 0x80; // From disassembly $80 per frame

    private final ProjectileType type;
    private int currentX;
    private int currentY;
    private int xVelocity; // In subpixels (8.8 fixed point)
    private int yVelocity; // In subpixels (8.8 fixed point)
    private int xSubpixel; // Fractional X position (low 8 bits of 16.8 position)
    private int ySubpixel; // Fractional Y position (low 8 bits of 16.8 position)
    private boolean applyGravity;
    private int gravity;
    private int collisionSizeIndex;
    private int animFrame;
    private boolean hFlip;

    /**
     * Create a new projectile.
     * 
     * @param spawn   Original spawn data
     * @param type    Type of projectile (determines graphics)
     * @param x       Starting X position
     * @param y       Starting Y position
     * @param xVel    X velocity in subpixels (positive = right)
     * @param yVel    Y velocity in subpixels (positive = down)
     * @param gravity Whether to apply gravity
     * @param hFlip   Horizontal flip for sprite
     */
    public BadnikProjectileInstance(ObjectSpawn spawn, ProjectileType type,
            int x, int y, int xVel, int yVel, boolean gravity, boolean hFlip) {
        super(spawn, "Projectile");
        this.type = type;
        this.currentX = x;
        this.currentY = y;
        this.xVelocity = xVel;
        this.yVelocity = yVel;
        this.applyGravity = gravity;
        this.animFrame = 0;
        this.hFlip = hFlip;
        switch (type) {
            case BUZZER_STINGER -> {
                this.gravity = 0;
                this.collisionSizeIndex = COLLISION_SIZE_STINGER;
            }
            case COCONUT -> {
                this.gravity = GRAVITY_COCONUT;
                this.collisionSizeIndex = COLLISION_SIZE_COCONUT;
            }
            case SPINY_SPIKE -> {
                this.gravity = GRAVITY_SPINY_SPIKE;
                this.collisionSizeIndex = COLLISION_SIZE_SPINY_SPIKE;
            }
            case REXON_FIREBALL -> {
                this.gravity = GRAVITY_REXON_FIREBALL;
                this.collisionSizeIndex = COLLISION_SIZE_REXON_FIREBALL;
            }
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Apply gravity if enabled
        if (applyGravity) {
            yVelocity += gravity;
        }

        // Update position using 16.8 fixed-point (matches ObjectMove in s2.asm:29969-29982)
        // Velocity (8.8) is added to position (16.8) for subpixel precision
        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
        xPos24 += xVelocity;
        yPos24 += yVelocity;
        currentX = xPos24 >> 8;
        currentY = yPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;
        ySubpixel = yPos24 & 0xFF;

        // Check if off-screen (with margin) and destroy
        if (!isOnScreen(32)) {
            setDestroyed(true);
        }

        // Simple animation cycling
        animFrame = ((frameCounter >> 2) & 1);
    }

    @Override
    public int getCollisionFlags() {
        // HURT category (0x80) + size index
        return 0x80 | (collisionSizeIndex & 0x3F);
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public ObjectSpawn getSpawn() {
        // Return dynamic spawn with current position
        return new ObjectSpawn(
                currentX,
                currentY,
                spawn.objectId(),
                spawn.subtype(),
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
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
        return RenderPriority.clamp(4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer;
        int frame;
        int paletteOverride = -1; // -1 = use sprite sheet default

        switch (type) {
            case BUZZER_STINGER:
                renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.BUZZER);
                // Buzzer projectile uses frames 5-6 (animation 2 in disassembly)
                frame = 5 + animFrame;
                break;
            case COCONUT:
                renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.COCONUTS);
                // Coconut uses frame 3
                frame = 3;
                break;
            case SPINY_SPIKE:
                renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.SPINY);
                // Spiny spike uses frames 6-7 (alternating)
                frame = 6 + animFrame;
                break;
            case REXON_FIREBALL:
                renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.REXON);
                // Rexon fireball uses frame 3 (1x1 tile)
                // Fireball uses palette line 1 (Obj94_SubObjData2: make_art_tile(ArtTile_ArtNem_Rexon,1,0))
                // while head/body use palette line 3 (the sheet default)
                frame = 3;
                paletteOverride = 1;
                break;
            default:
                return;
        }

        if (renderer == null || !renderer.isReady()) {
            return;
        }

        renderer.drawFrameIndex(frame, currentX, currentY, hFlip, false, paletteOverride);
    }
}
