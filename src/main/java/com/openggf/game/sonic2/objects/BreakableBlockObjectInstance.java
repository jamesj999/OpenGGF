package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.GameServices;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * CPZ Breakable Block (Object 0x32) - Metal blocks that shatter when Sonic rolls into them.
 *
 * Based on Obj32 in the Sonic 2 disassembly (s2.asm lines 48829-49020).
 *
 * Behavior:
 * - Acts as a solid platform that players can stand on
 * - Only breaks when a player standing on it is rolling (spin attack)
 * - When broken, spawns 4 fragment objects that fly apart
 * - Player bounces upward when block breaks
 * - Plays SLOW_SMASH sound effect (0xCB)
 */
public class BreakableBlockObjectInstance extends BoxObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOGGER = Logger.getLogger(BreakableBlockObjectInstance.class.getName());

    // From disassembly:
    // - CPZ: move.b #$10,width_pixels(a0) (32px wide)
    // - HTZ: move.b #$18,width_pixels(a0) (48px wide)
    private static final int CPZ_HALF_WIDTH = 0x10;  // 16 pixels
    private static final int HTZ_HALF_WIDTH = 0x18;  // 24 pixels
    private static final int HALF_HEIGHT = 0x10;     // 16 pixels

    // From disassembly: move.w #-$300,y_vel(a1) for player bounce
    private static final int PLAYER_BOUNCE_VELOCITY = -0x300;

    // Fragment velocities from Obj32_VelArray2 (CPZ version):
    // -$100, -$200  ; top-left
    //  $100, -$200  ; top-right
    // -$C0,  -$1C0  ; bottom-left
    //  $C0,  -$1C0  ; bottom-right
    private static final int[][] CPZ_FRAGMENT_VELOCITIES = {
            {-0x100, -0x200},  // Fragment 0: top-left
            { 0x100, -0x200},  // Fragment 1: top-right
            {-0x0C0, -0x1C0},  // Fragment 2: bottom-left
            { 0x0C0, -0x1C0}   // Fragment 3: bottom-right
    };

    // Fragment velocities from Obj32_VelArray1 (HTZ version, 6 pieces)
    private static final int[][] HTZ_FRAGMENT_VELOCITIES = {
            {-0x200, -0x200},
            { 0x000, -0x280},
            { 0x200, -0x200},
            {-0x1C0, -0x1C0},
            { 0x000, -0x200},
            { 0x1C0, -0x1C0}
    };

    // Mapping frame indices
    private static final int FRAME_INTACT = 0;
    private static final int FRAME_FRAGMENT_BASE = 1;  // Fragments use frames 1-4 (legacy fallback)

    private final int halfWidth;
    private boolean broken;
    private boolean playerWasRolling;

    public BreakableBlockObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name, resolveHalfWidth(), HALF_HEIGHT, 0.6f, 0.6f, 0.8f, false);
        this.halfWidth = resolveHalfWidth();
        this.broken = false;

        // Check persistence: if already broken, stay broken
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager != null && objectManager.isRemembered(spawn)) {
            this.broken = true;
            setDestroyed(true);
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (broken) {
            return;
        }

        // Check if player is standing on us and rolling
        // The original tracks player animation state each frame
        if (player != null) {
            playerWasRolling = player.getRolling();

            // Check for breaking from below - when player is rolling and moving upward
            // We handle this here because isSolidFor returns false for this case,
            // so onSolidContact won't be called
            if (player.getRolling() && player.getYSpeed() < 0) {
                if (isPlayerOverlapping(player)) {
                    // Create a synthetic contact for breaking from below
                    SolidContact contact = new SolidContact(false, false, true, false, false);
                    breakBlock(player, contact);
                }
            }
        }
    }

    /**
     * Check if the player overlaps with this block's collision area.
     */
    private boolean isPlayerOverlapping(AbstractPlayableSprite player) {
        int blockX = spawn.x();
        int blockY = spawn.y();
        int playerX = player.getCentreX();
        int playerY = player.getCentreY();
        int playerYRadius = player.getYRadius();

        // Check X overlap
        int dx = Math.abs(playerX - blockX);
        if (dx >= halfWidth + 11) {
            return false;
        }

        // Check Y overlap - player's top must be near or overlapping block's bottom
        int playerTop = playerY - playerYRadius;
        int blockBottom = blockY + HALF_HEIGHT;
        int blockTop = blockY - HALF_HEIGHT;

        // Player is overlapping if their top is within the block's vertical range
        return playerTop <= blockBottom && playerTop >= blockTop - playerYRadius;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // From disassembly: width_pixels = $10 (CPZ) / $18 (HTZ)
        // SolidObject routine uses: halfWidth + 11 for x check, halfHeight for y check
        return new SolidObjectParams(halfWidth + 11, HALF_HEIGHT, HALF_HEIGHT + 1);
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        // Block is not solid once broken
        if (broken) {
            return false;
        }
        // Don't be solid for rolling players moving upward - they should break through
        // The break detection is handled in update() instead
        if (player.getRolling() && player.getYSpeed() < 0) {
            return false;
        }
        return true;
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (broken || player == null) {
            return;
        }

        // Break the block if player is rolling and either:
        // 1. Standing on top of the block
        // 2. Hitting from below (e.g., exiting a spin tube upward)
        // 3. Hitting from the side while rolling
        boolean isRolling = playerWasRolling || player.getRolling();

        if (isRolling && (contact.standing() || contact.touchBottom() || contact.touchSide())) {
            breakBlock(player, contact);
        }
    }

    private void breakBlock(AbstractPlayableSprite player, SolidContact contact) {
        if (broken) {
            return;
        }

        broken = true;

        // Mark as broken in persistence table (stays broken on respawn/revisit)
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager != null) {
            objectManager.markRemembered(spawn);
        }

        // Force player into rolling state with proper hitbox (disassembly lines 48916-48919)
        // bset #status.player.rolling,status(a1)
        // move.b #$E,y_radius(a1)
        // move.b #7,x_radius(a1)
        // move.b #AniIDSonAni_Roll,anim(a1)
        // setRolling(true) handles radius change and animation internally
        player.setRolling(true);

        // Handle velocity based on contact direction:
        // - Standing on top: bounce upward
        // - Hitting from below: continue through (don't change velocity)
        // - Hitting from side: continue through (don't change velocity)
        if (contact.standing()) {
            // Bounce player upward only when breaking from above
            // From disassembly: move.w #-$300,y_vel(a1)
            player.setYSpeed((short) PLAYER_BOUNCE_VELOCITY);
        }
        // When hitting from below or side, player maintains their momentum

        // Set player state to in-air
        // From disassembly: bset #status.player.in_air, bclr #status.player.on_object
        player.setAir(true);

        // Spawn fragment objects
        spawnFragments();

        // Play slow smash sound effect
        AudioManager.getInstance().playSfx(GameSound.SLOW_SMASH);

        // Award 100 points
        GameServices.gameState().addScore(100);

        // Spawn points display popup
        if (objectManager != null) {
            PointsObjectInstance points = new PointsObjectInstance(
                    new ObjectSpawn(spawn.x(), spawn.y(), 0x29, 0, 0, false, 0),
                    LevelManager.getInstance(), 100);
            objectManager.addDynamicObject(points);
        }

        // Mark this object as destroyed so it stops rendering/updating
        setDestroyed(true);

        LOGGER.fine(() -> String.format("Breakable block at (%d,%d) broken by player", spawn.x(), spawn.y()));
    }

    private void spawnFragments() {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager == null) {
            return;
        }

        ObjectSpriteSheet sheet = renderManager.getSheet(Sonic2ObjectArtKeys.BREAKABLE_BLOCK);
        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.BREAKABLE_BLOCK);
        if (sheet == null || renderer == null) {
            return;
        }

        List<SpriteMappingPiece> pieces = List.of();
        if (sheet.getFrameCount() > 0) {
            SpriteMappingFrame frame = sheet.getFrame(0);
            if (frame != null && frame.pieces() != null) {
                pieces = frame.pieces();
            }
        }

        if (!pieces.isEmpty()) {
            int[][] velocities = pieces.size() >= HTZ_FRAGMENT_VELOCITIES.length
                    ? HTZ_FRAGMENT_VELOCITIES
                    : CPZ_FRAGMENT_VELOCITIES;
            int count = Math.min(pieces.size(), velocities.length);

            for (int i = 0; i < count; i++) {
                SpriteMappingPiece piece = pieces.get(i);
                BreakableBlockFragmentInstance fragment = new BreakableBlockFragmentInstance(
                        spawn.x(),
                        spawn.y(),
                        velocities[i][0],
                        velocities[i][1],
                        piece,
                        renderer);
                objectManager.addDynamicObject(fragment);
            }
            return;
        }

        // Fallback: spawn 4 simple fragments if mappings are missing
        for (int i = 0; i < 4; i++) {
            int velX = CPZ_FRAGMENT_VELOCITIES[i][0];
            int velY = CPZ_FRAGMENT_VELOCITIES[i][1];
            int frameIndex = FRAME_FRAGMENT_BASE + i;

            BreakableBlockFragmentInstance fragment = new BreakableBlockFragmentInstance(
                    spawn.x(), spawn.y(), velX, velY, frameIndex, renderManager);
            objectManager.addDynamicObject(fragment);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (broken) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            super.appendRenderCommands(commands);
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.BREAKABLE_BLOCK);
        if (renderer == null || !renderer.isReady()) {
            super.appendRenderCommands(commands);
            return;
        }

        renderer.drawFrameIndex(FRAME_INTACT, spawn.x(), spawn.y(), false, false);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    @Override
    protected int getHalfWidth() {
        return halfWidth;
    }

    @Override
    protected int getHalfHeight() {
        return HALF_HEIGHT;
    }

    private static int resolveHalfWidth() {
        LevelManager manager = LevelManager.getInstance();
        if (manager == null || manager.getCurrentLevel() == null) {
            return CPZ_HALF_WIDTH;
        }
        int zoneId = manager.getCurrentLevel().getZoneIndex();
        return zoneId == Sonic2Constants.ZONE_HTZ
                ? HTZ_HALF_WIDTH
                : CPZ_HALF_WIDTH;
    }

    /**
     * Inner class for the fragment objects that fly apart when the block breaks.
     * These are simple falling objects with initial velocity that despawn when off-screen.
     */
    public static class BreakableBlockFragmentInstance extends AbstractObjectInstance {

        private static final int GRAVITY = 0x18;  // From disassembly: addi.w #$18,y_vel(a0)

        private int currentX;
        private int currentY;
        private int subX;  // 8.8 fixed point
        private int subY;  // 8.8 fixed point
        private int velX;  // 8.8 fixed point
        private int velY;  // 8.8 fixed point
        private final SpriteMappingPiece piece;
        private final PatternSpriteRenderer renderer;
        private final List<SpriteMappingPiece> pieceList;
        private final int frameIndex;
        private final ObjectRenderManager renderManager;

        public BreakableBlockFragmentInstance(int x, int y, int velX, int velY, SpriteMappingPiece piece,
                                              PatternSpriteRenderer renderer) {
            super(new ObjectSpawn(x, y, 0x32, 0, 0, false, 0), "BlockFragment");
            this.currentX = x;
            this.currentY = y;
            this.subX = x << 8;
            this.subY = y << 8;
            this.velX = velX;
            this.velY = velY;
            this.piece = piece;
            this.renderer = renderer;
            this.pieceList = piece != null ? List.of(piece) : List.of();
            this.frameIndex = -1;
            this.renderManager = null;
        }

        public BreakableBlockFragmentInstance(int x, int y, int velX, int velY, int frameIndex,
                                              ObjectRenderManager renderManager) {
            super(new ObjectSpawn(x, y, 0x32, 0, 0, false, 0), "BlockFragment");
            this.currentX = x;
            this.currentY = y;
            this.subX = x << 8;
            this.subY = y << 8;
            this.velX = velX;
            this.velY = velY;
            this.piece = null;
            this.renderer = null;
            this.pieceList = List.of();
            this.frameIndex = frameIndex;
            this.renderManager = renderManager;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (isDestroyed()) {
                return;
            }

            // Apply gravity
            velY += GRAVITY;

            // Update position (8.8 fixed point)
            subX += velX;
            subY += velY;
            currentX = subX >> 8;
            currentY = subY >> 8;

            // Check if off-screen (destroy if too far below camera)
            int cameraY = Camera.getInstance().getY();
            int screenHeight = 224;  // Standard MD screen height
            if (currentY > cameraY + screenHeight + 32) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }

            if (renderer != null) {
                if (!renderer.isReady() || pieceList.isEmpty()) {
                    return;
                }
                renderer.drawPieces(pieceList, currentX, currentY, false, false);
                return;
            }

            if (renderManager != null) {
                PatternSpriteRenderer fallbackRenderer = renderManager.getRenderer(Sonic2ObjectArtKeys.BREAKABLE_BLOCK);
                if (fallbackRenderer == null || !fallbackRenderer.isReady()) {
                    return;
                }
                fallbackRenderer.drawFrameIndex(frameIndex, currentX, currentY, false, false);
            }
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(4);
        }
    }
}

