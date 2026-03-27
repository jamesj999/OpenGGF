package com.openggf.game.sonic1.objects;
import com.openggf.game.PlayableEntity;

import com.openggf.audio.GameSound;
import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Sonic 1 Smashable Wall (Object 0x3C) - GHZ and SLZ.
 * <p>
 * A solid wall that breaks when Sonic rolls into it at speed >= $480.
 * On impact, spawns 8 fragment objects that scatter with gravity.
 * Uses RememberState to stay destroyed on revisit.
 * <p>
 * Subtypes (obSubtype → obFrame):
 * <ul>
 *   <li>0 = Left section</li>
 *   <li>1 = Middle section</li>
 *   <li>2 = Right section</li>
 * </ul>
 * <p>
 * Reference: docs/s1disasm/_incObj/3C Smashable Wall.asm
 */
public class Sonic1BreakableWallObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // From disassembly: move.w #$1B,d1
    private static final int HALF_WIDTH = 0x1B;
    // From disassembly: move.w #$20,d2 / move.w #$20,d3
    private static final int HALF_HEIGHT = 0x20;
    // From disassembly: move.b #4,obPriority(a0)
    private static final int PRIORITY = 4;
    // From disassembly: cmpi.w #$480,d0
    private static final int BREAK_SPEED_THRESHOLD = 0x480;
    // From disassembly: move.w #$70,d2 (fragment gravity)
    private static final int FRAGMENT_GRAVITY = 0x70;

    // Smash_FragSpd1: fragments move right (Sonic approaching from left)
    // From sonic.asm lines 5286-5293
    private static final int[][] FRAG_SPD_RIGHT = {
            { 0x400, -0x500},
            { 0x600, -0x100},
            { 0x600,  0x100},
            { 0x400,  0x500},
            { 0x600, -0x600},
            { 0x800, -0x200},
            { 0x800,  0x200},
            { 0x600,  0x600},
    };

    // Smash_FragSpd2: fragments move left (Sonic approaching from right)
    // From sonic.asm lines 5295-5302
    private static final int[][] FRAG_SPD_LEFT = {
            {-0x600, -0x600},
            {-0x800, -0x200},
            {-0x800,  0x200},
            {-0x600,  0x600},
            {-0x400, -0x500},
            {-0x600, -0x100},
            {-0x600,  0x100},
            {-0x400,  0x500},
    };

    // Fragment count (d1 = 7 means 8 fragments, dbf loop)
    private static final int FRAGMENT_COUNT = 8;

    private final int frameIndex;
    private boolean broken;
    private boolean initialized;

    // Cached Sonic speed for break check (smash_speed = objoff_30)
    private int cachedSonicSpeed;

    public Sonic1BreakableWallObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SmashableWall");
        // From disassembly: move.b obSubtype(a0),obFrame(a0)
        this.frameIndex = spawn.subtype() & 0xFF;
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;

        // RememberState: check if already broken
        ObjectManager objectManager = services().objectManager();
        if (objectManager != null && objectManager.isRemembered(spawn)) {
            this.broken = true;
            setDestroyed(true);
        }
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        ensureInitialized();
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (broken || player == null) {
            return;
        }
        // From disassembly: move.w (v_player+obVelX).w,smash_speed(a0)
        // Cache Sonic's horizontal speed each frame
        cachedSonicSpeed = player.getXSpeed();
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(HALF_WIDTH, HALF_HEIGHT, HALF_HEIGHT);
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        return !broken;
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (broken || player == null) {
            return;
        }

        // From disassembly: btst #5,obStatus(a0) - is Sonic pushing against the wall?
        // In the ROM, SolidObject sets bit 5 for ANY ground side contact (lines 189-194
        // of sub SolidObject.asm). The engine's contact.pushing() is more restrictive —
        // it also requires player.getXSpeed() != 0, which can fail if terrain collision
        // zeroed the speed before solid object resolution runs. Use touchSide() instead,
        // matching the ROM's unconditional bit 5 set on side collision + ground check.
        if (!contact.touchSide() || player.getAir()) {
            return;
        }

        // From disassembly: cmpi.b #id_Roll,obAnim(a1) - is Sonic rolling?
        if (!player.getRolling()) {
            return;
        }

        // From disassembly: check absolute speed >= $480
        int absSpeed = Math.abs(cachedSonicSpeed);
        if (absSpeed < BREAK_SPEED_THRESHOLD) {
            return;
        }

        smashWall(player);
    }

    private void smashWall(AbstractPlayableSprite player) {
        broken = true;

        // Mark as remembered (RememberState) so it stays broken
        ObjectManager objectManager = services().objectManager();
        if (objectManager != null) {
            objectManager.markRemembered(spawn);
        }

        // From disassembly: move.w smash_speed(a0),obVelX(a1)
        // Transfer cached speed to Sonic's velocity
        player.setXSpeed((short) cachedSonicSpeed);

        // From disassembly (lines 53-59):
        //   addq.w  #4,obX(a1)              ; always add 4 first
        //   lea     (Smash_FragSpd1).l,a4   ; default = RIGHT fragments
        //   move.w  obX(a0),d0              ; d0 = wall X
        //   cmp.w   obX(a1),d0              ; compare wallX with (sonicX+4)
        //   blo.s   .smash                  ; if wallX < sonicX+4, keep FragSpd1 (RIGHT)
        //   subq.w  #8,obX(a1)              ; else subtract 8 (net -4)
        //   lea     (Smash_FragSpd2).l,a4   ; use FragSpd2 (LEFT)
        int wallX = spawn.x();
        int sonicX = player.getCentreX();
        int adjustedSonicX = sonicX + 4; // addq.w #4 applied first

        int[][] fragSpeeds;
        if (wallX < adjustedSonicX) {
            // Sonic is to the RIGHT of wall: keep +4 adjustment, fragments scatter right
            fragSpeeds = FRAG_SPD_RIGHT;
            player.setCentreX((short) adjustedSonicX);
        } else {
            // Sonic is to the LEFT of wall: net -4 adjustment, fragments scatter left
            fragSpeeds = FRAG_SPD_LEFT;
            player.setCentreX((short) (adjustedSonicX - 8));
        }

        // From disassembly: move.w obVelX(a1),obInertia(a1)
        player.setGSpeed(player.getXSpeed());

        // From disassembly: bclr #5,obStatus(a0) / bclr #5,obStatus(a1)
        // Clearing push status - handled implicitly by the engine

        // Spawn 8 fragments
        spawnFragments(fragSpeeds);

        // Mark this object as destroyed
        setDestroyed(true);
    }

    private void spawnFragments(int[][] fragSpeeds) {
        ObjectManager objectManager = services().objectManager();
        ObjectRenderManager renderManager = services().renderManager();
        if (objectManager == null || renderManager == null) {
            return;
        }

        ObjectSpriteSheet sheet = renderManager.getSheet(ObjectArtKeys.BREAKABLE_WALL);
        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.BREAKABLE_WALL);
        if (sheet == null || renderer == null) {
            return;
        }

        // SmashObject: each fragment gets one mapping piece from the current frame.
        // From disassembly: the subroutine reads pieces sequentially from the mapping
        // data, advancing a3 by 5 bytes per piece (S1 mapping piece size).
        // We have 8 pieces per frame, one fragment per piece.
        SpriteMappingFrame frame = (frameIndex >= 0 && frameIndex < sheet.getFrameCount())
                ? sheet.getFrame(frameIndex) : null;
        if (frame == null || frame.pieces() == null || frame.pieces().size() < FRAGMENT_COUNT) {
            return;
        }

        List<SpriteMappingPiece> pieces = frame.pieces();
        int wallX = spawn.x();
        int wallY = spawn.y();

        for (int i = 0; i < FRAGMENT_COUNT; i++) {
            SpriteMappingPiece piece = pieces.get(i);
            int velX = fragSpeeds[i][0];
            int velY = fragSpeeds[i][1];

            WallFragmentInstance fragment = new WallFragmentInstance(
                    wallX, wallY, velX, velY, piece, renderer);
            objectManager.addDynamicObject(fragment);
        }

        // From disassembly: move.w #sfx_WallSmash,d0 / jmp (QueueSound2).l
        services().playSfx(GameSound.WALL_SMASH);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (broken) {
            return;
        }

        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.BREAKABLE_WALL);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        renderer.drawFrameIndex(frameIndex, getX(), getY(), false, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        if (broken) {
            return;
        }
        // Debug: draw solid collision bounds
        int x = getX();
        int y = getY();
        ctx.drawRect(x, y, HALF_WIDTH, HALF_HEIGHT, 0.0f, 1.0f, 0.0f);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    /**
     * Wall fragment - a single mapping piece that flies away with velocity and gravity.
     * <p>
     * From disassembly Smash_FragMove (Routine 4):
     * <pre>
     *     bsr.w   SpeedToPos
     *     addi.w  #$70,obVelY(a0)     ; gravity
     *     bsr.w   DisplaySprite
     *     tst.b   obRender(a0)        ; off-screen check
     *     bpl.w   DeleteObject
     * </pre>
     */
    static class WallFragmentInstance extends AbstractObjectInstance {

        private int posX, posY;
        private int subX, subY; // 8.8 fixed point sub-pixel
        private int velX, velY; // 8.8 fixed point velocity
        private final SpriteMappingPiece piece;
        private final PatternSpriteRenderer renderer;

        WallFragmentInstance(int x, int y, int velX, int velY,
                             SpriteMappingPiece piece, PatternSpriteRenderer renderer) {
            super(new ObjectSpawn(x, y, 0x3C, 0, 0, false, 0), "WallFragment");
            this.posX = x;
            this.posY = y;
            this.subX = x << 8;
            this.subY = y << 8;
            this.velX = velX;
            this.velY = velY;
            this.piece = piece;
            this.renderer = renderer;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
            if (isDestroyed()) {
                return;
            }

            // SpeedToPos: position += velocity (8.8 fixed point)
            subX += velX;
            subY += velY;
            posX = subX >> 8;
            posY = subY >> 8;

            // From disassembly: addi.w #$70,obVelY(a0) - gravity
            velY += FRAGMENT_GRAVITY;

            // From disassembly: tst.b obRender(a0) / bpl.w DeleteObject
            // Delete when off-screen (render flag bit 7 indicates on-screen)
            var cam = services().camera();
            int cameraX = cam.getX();
            int cameraY = cam.getY();
            if (posX < cameraX - 64 || posX > cameraX + 320 + 64
                    || posY > cameraY + 224 + 64) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed() || renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawPieces(List.of(piece), posX, posY, false, false);
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY);
        }
    }
}
