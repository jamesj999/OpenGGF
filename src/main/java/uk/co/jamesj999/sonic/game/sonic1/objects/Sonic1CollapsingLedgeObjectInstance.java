package uk.co.jamesj999.sonic.game.sonic1.objects;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Sfx;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1ObjectIds;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.SlopedSolidProvider;
import uk.co.jamesj999.sonic.level.objects.SolidContact;
import uk.co.jamesj999.sonic.level.objects.SolidObjectListener;
import uk.co.jamesj999.sonic.level.objects.SolidObjectParams;
import uk.co.jamesj999.sonic.level.objects.SolidObjectProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 1A - GHZ Collapsing Ledge.
 * <p>
 * A sloped platform that crumbles when Sonic stands on it. After a delay,
 * the ledge splits into individual fragment objects that fall with gravity.
 * <p>
 * Subtypes:
 * <ul>
 *   <li>0x00: Left-facing ledge (mapping frame 0, smash frame 2)</li>
 *   <li>0x01: Right-facing ledge (mapping frame 1, smash frame 3)</li>
 * </ul>
 * <p>
 * State machine (obRoutine values):
 * <ul>
 *   <li>0 (Ledge_Main): Initialize</li>
 *   <li>2 (Ledge_Touch): Slope platform, check for collapse trigger</li>
 *   <li>4 (Ledge_Collapse): Countdown to fragment, with ExitPlatform checks</li>
 *   <li>6 (Ledge_Display): Fragment falling with gravity (ObjectFall)</li>
 *   <li>8 (Ledge_Delete): Destroy when offscreen</li>
 *   <li>A (Ledge_WalkOff): ExitPlatform + SlopeObject2 subroutine</li>
 * </ul>
 * <p>
 * Reference: docs/s1disasm/_incObj/1A Collapsing Ledge (part 1).asm
 */
public class Sonic1CollapsingLedgeObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, SlopedSolidProvider {

    // From disassembly: move.w #$30,d1 (half-width for platform collision)
    private static final int PLATFORM_HALF_WIDTH = 0x30;

    // From disassembly: move.b #$64,obActWid(a0)
    private static final int ACTIVE_WIDTH = 0x64;

    // From disassembly: move.b #4,obPriority(a0)
    private static final int PRIORITY = 4;

    // From disassembly: move.b #7,ledge_timedelay(a0)
    private static final int INITIAL_COLLAPSE_DELAY = 7;

    // Gravity constant from ObjectFall: addi.w #$38,obVelY(a0)
    private static final int GRAVITY = 0x38;

    // GHZ Collapsing Ledge Heightmap (48 bytes from misc/GHZ Collapsing Ledge Heightmap.bin)
    // Each byte = height offset from object Y. Index = (playerX - objectX + $30) / 2
    // Values: columns 0-7 = 0x20, then linearly increasing by 1 per 2 columns up to 0x30
    private static final byte[] SLOPE_DATA = {
            0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
            0x21, 0x21, 0x22, 0x22, 0x23, 0x23, 0x24, 0x24,
            0x25, 0x25, 0x26, 0x26, 0x27, 0x27, 0x28, 0x28,
            0x29, 0x29, 0x2A, 0x2A, 0x2B, 0x2B, 0x2C, 0x2C,
            0x2D, 0x2D, 0x2E, 0x2E, 0x2F, 0x2F, 0x30, 0x30,
            0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30
    };

    // Disintegration delay data from CFlo_Data1 (26 bytes).
    // Each value = frame delay before that fragment starts falling.
    // Fragments are created from the "smash" mapping frame pieces.
    // The first piece of the parent object (index 0) gets its delay from here.
    private static final int[] COLLAPSE_DELAYS = {
            0x1C, 0x18, 0x14, 0x10, 0x1A, 0x16, 0x12, 0x0E,
            0x0A, 0x06, 0x18, 0x14, 0x10, 0x0C, 0x08, 0x04,
            0x16, 0x12, 0x0E, 0x0A, 0x06, 0x02, 0x14, 0x10,
            0x0C, 0x00
    };

    private final LevelManager levelManager;

    // Routine state: 0=init, 2=touch, 4=collapse, 6=display(fragment), 8=delete, A=walkoff
    private int routine;

    // Current position (center coordinates)
    private int x;
    private int y;

    // Subtype determines facing: 0 = left, 1 = right
    private final int subtype;

    // Mapping frame index: 0=left, 1=right (from obSubtype -> obFrame in init)
    private final int mappingFrame;

    // Collapse timer (ledge_timedelay = objoff_38)
    private int collapseDelay;

    // Collapse flag (ledge_collapse_flag = objoff_3A): set when player steps on during routine 4
    private boolean collapseFlag;

    // Velocity for fragment falling (routine 6: ObjectFall)
    private int velX;
    private int velY;
    private int xFrac;
    private int yFrac;

    // Whether fragments have been spawned
    private boolean fragmented;

    private ObjectSpawn dynamicSpawn;

    public Sonic1CollapsingLedgeObjectInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, "CollapsingLedge");
        this.levelManager = levelManager;
        this.subtype = spawn.subtype() & 0xFF;
        this.mappingFrame = subtype; // obSubtype -> obFrame: 0=left, 1=right
        this.x = spawn.x();
        this.y = spawn.y();
        this.collapseDelay = INITIAL_COLLAPSE_DELAY;
        this.collapseFlag = false;
        this.routine = 2; // Skip init, go straight to Ledge_Touch
        this.fragmented = false;
        refreshDynamicSpawn();
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
    public ObjectSpawn getSpawn() {
        return dynamicSpawn != null ? dynamicSpawn : spawn;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        switch (routine) {
            case 2 -> updateTouch(player);
            case 4 -> updateCollapse(player);
            case 6 -> updateFragmentFall(player);
            case 8 -> destroyWithWindowGatedRespawn();
            default -> { }
        }
        refreshDynamicSpawn();
    }

    /**
     * Routine 2 (Ledge_Touch): Sloped platform behavior.
     * If collapse flag set, counts down delay then fragments.
     * Otherwise acts as a normal slope platform.
     */
    private void updateTouch(AbstractPlayableSprite player) {
        if (collapseFlag) {
            if (collapseDelay <= 0) {
                // Timer expired: fragment the ledge (Ledge_Fragment path - clears flag)
                performFragment(player, true);
                return;
            }
            collapseDelay--;
        }
        // SlopeObject + RememberState handled via SolidObjectProvider
    }

    /**
     * Routine 4 (Ledge_Collapse): Entered when player walks on a fragment piece.
     * Sets collapse flag and counts down, with ExitPlatform + SlopeObject2 checking.
     * This state is entered when the player is standing on us (via SolidContacts).
     */
    private void updateCollapse(AbstractPlayableSprite player) {
        if (collapseDelay <= 0) {
            // Transition to fragmentation (loc_847A path - preserves flag)
            performFragment(player, false);
            return;
        }
        collapseFlag = true;
        collapseDelay--;

        // Ledge_WalkOff: ExitPlatform + SlopeObject2 + RememberState
        // The engine's SolidContacts system handles exit/slope automatically
    }

    /**
     * Routine 6 (Ledge_Display): Fragment pieces fall with gravity.
     * Each fragment piece is a separate object in routine 6.
     * When collapse_flag is set, continues WalkOff behavior until delay expires,
     * then detaches player and falls. Otherwise falls immediately when delay = 0.
     */
    private void updateFragmentFall(AbstractPlayableSprite player) {
        if (collapseDelay > 0) {
            if (collapseFlag) {
                // loc_82D0: WalkOff behavior while collapsing
                collapseDelay--;
                var objectManager = levelManager.getObjectManager();
                boolean playerRiding = objectManager != null && player != null
                        && objectManager.isAnyPlayerRiding(this);

                if (!playerRiding) {
                    // loc_82FC: Player walked off - clear flag, stay in routine 6
                    collapseFlag = false;
                } else if (collapseDelay <= 0) {
                    // Delay expired with player still standing:
                    // bclr #3,obStatus(a1) / bclr #5,obStatus(a1)
                    objectManager.clearRidingObject(player);
                    // loc_82FC: clear flag
                    collapseFlag = false;
                }
                // locret_8308: return (continue supporting player while delay > 0)
                return;
            }

            // Not collapsing: just count down delay before falling
            collapseDelay--;
            return;
        }

        // Ledge_TimeZero: ObjectFall - apply gravity
        applyObjectFall();

        // Check if offscreen (obRender bit 7 clear = offscreen)
        if (!isOnScreen()) {
            destroyWithWindowGatedRespawn();
        }
    }

    /**
     * Object 1A uses DeleteObject when falling fragments leave the screen.
     * Keep the spawn suppressed until it leaves the object window so it doesn't
     * recreate immediately while still near the camera.
     */
    private void destroyWithWindowGatedRespawn() {
        if (!isDestroyed() && levelManager != null) {
            var objectManager = levelManager.getObjectManager();
            if (objectManager != null) {
                objectManager.removeFromActiveSpawns(spawn);
            }
        }
        setDestroyed(true);
    }

    /**
     * ObjectFall subroutine: updates position with velocity and applies gravity.
     * From _incObj/sub ObjectFall.asm:
     *   move.l obX(a0),d2 / move.l obY(a0),d3
     *   move.w obVelX(a0),d0 / ext.l d0 / asl.l #8,d0 / add.l d0,d2
     *   move.w obVelY(a0),d0 / addi.w #$38,obVelY(a0)
     *   ext.l d0 / asl.l #8,d0 / add.l d0,d3
     */
    private void applyObjectFall() {
        // X position update with fractional part
        int x32 = (x << 16) | (xFrac & 0xFFFF);
        int vx32 = (int) (short) velX;
        x32 += vx32 << 8;
        x = x32 >> 16;
        xFrac = x32 & 0xFFFF;

        // Y position update with fractional part
        int y32 = (y << 16) | (yFrac & 0xFFFF);
        int vy32 = (int) (short) velY;
        velY += GRAVITY; // addi.w #$38,obVelY(a0)
        y32 += vy32 << 8;
        y = y32 >> 16;
        yFrac = y32 & 0xFFFF;
    }

    /**
     * Ledge_Fragment: Split ledge into individual fragment objects.
     * Uses the "smash" mapping frame (frame 2 for left, frame 3 for right).
     * Each piece from the smash mapping becomes a separate fragment object
     * with its own collapse delay from CFlo_Data1.
     * <p>
     * From disassembly (sonic.asm lines 4635-4683):
     * - Ledge_Fragment path (routine 2): clears ledge_collapse_flag = 0
     * - loc_847A path (routine 4): preserves ledge_collapse_flag (remains true)
     * - Loads CFlo_Data1 delay table
     * - Uses smash frame (obFrame + 2)
     * - Creates up to 25 fragment objects (d1 = $18 = 24, +1 for self = 25)
     * - Each fragment gets its own delay from CFlo_Data1
     * - Plays sfx_Collapse
     *
     * @param clearFlag true when called from Routine 2 (Ledge_Fragment), false from Routine 4 (loc_847A)
     */
    private void performFragment(AbstractPlayableSprite player, boolean clearFlag) {
        if (fragmented) {
            return;
        }
        fragmented = true;
        if (clearFlag) {
            collapseFlag = false;
        }

        var objectManager = levelManager.getObjectManager();
        if (objectManager == null) {
            return;
        }

        // Detach player if riding
        if (player != null && objectManager.isAnyPlayerRiding(this)) {
            objectManager.clearRidingObject(player);
        }

        // Get the smash frame pieces for this subtype
        // Frame 2 = leftsmash, Frame 3 = rightsmash
        int smashFrameIndex = mappingFrame + 2;
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.COLLAPSING_LEDGE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        var sheet = renderManager.getSheet(ObjectArtKeys.COLLAPSING_LEDGE);
        if (sheet == null) {
            return;
        }

        if (smashFrameIndex >= sheet.getFrameCount()) {
            return;
        }

        SpriteMappingFrame smashFrame = sheet.getFrame(smashFrameIndex);
        int pieceCount = smashFrame.pieces().size();

        // The first fragment reuses this object (self), rest are new dynamic objects.
        // d1 = $18 (24), loop creates up to 25 fragments total.
        // Each fragment gets a delay from CFlo_Data1.

        // Convert self to fragment (routine 6)
        this.routine = 6;
        // bset #5,obRender(a0) - set "use mapped position" flag
        if (COLLAPSE_DELAYS.length > 0) {
            this.collapseDelay = COLLAPSE_DELAYS[0];
        }

        // Spawn remaining fragments as dynamic objects
        int maxFragments = Math.min(pieceCount, COLLAPSE_DELAYS.length);
        for (int i = 1; i < maxFragments; i++) {
            int delay = COLLAPSE_DELAYS[i];
            CollapsingLedgeFragmentInstance fragment = new CollapsingLedgeFragmentInstance(
                    x, y, smashFrameIndex, i, delay,
                    spawn.renderFlags());
            objectManager.addDynamicObject(fragment);
        }

        // Play collapse sound: move.w #sfx_Collapse,d0 / jmp (QueueSound2).l
        AudioManager.getInstance().playSfx(Sonic1Sfx.COLLAPSE.id);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.COLLAPSING_LEDGE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // obRender bit 0 = X-flip, inherited by all fragments in disassembly
        boolean hFlip = (spawn.renderFlags() & 0x01) != 0;
        if (routine == 6) {
            // Fragment: render only piece 0 from the smash frame (self is first piece)
            int smashFrameIndex = mappingFrame + 2;
            renderer.drawFramePieceByIndex(smashFrameIndex, 0, x, y, hFlip, false);
        } else {
            // Normal: render the full ledge
            renderer.drawFrameIndex(mappingFrame, x, y, hFlip, false);
        }
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // ROM SlopeObject logic does not add object half-height to surface checks;
        // it tests directly against (obY - slopeSample). Keep vertical extents at 0
        // so sloped contact matches Platform3 landing math.
        return new SolidObjectParams(PLATFORM_HALF_WIDTH, 0, 0);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public byte[] getSlopeData() {
        return SLOPE_DATA;
    }

    @Override
    public boolean isSlopeFlipped() {
        // SlopeObject checks obRender bit 0 for x-flip.
        return (spawn.renderFlags() & 0x01) != 0;
    }

    @Override
    public int getSlopeBaseline() {
        // ROM uses absolute slope values: surface Y = obY - slopeSample.
        return 0;
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (routine == 2 && contact.standing()) {
            // Player stepped on ledge: transition to routine 4 (Ledge_Collapse)
            // From disassembly: addq.b #2,obRoutine(a0) in PlatformObject
            routine = 4;
        }
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        // Not solid once fragmented
        return !isDestroyed() && routine <= 4;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public boolean isPersistent() {
        return !isDestroyed() && isOnScreenX(spawn.x(), 320);
    }

    private boolean isOnScreenX(int objectX, int range) {
        var camera = uk.co.jamesj999.sonic.camera.Camera.getInstance();
        if (camera == null) {
            return true;
        }
        int objRounded = objectX & 0xFF80;
        int camRounded = (camera.getX() - 128) & 0xFF80;
        int distance = (objRounded - camRounded) & 0xFFFF;
        return distance <= (128 + 320 + 192);
    }

    private void refreshDynamicSpawn() {
        if (dynamicSpawn == null || dynamicSpawn.x() != x || dynamicSpawn.y() != y) {
            dynamicSpawn = new ObjectSpawn(
                    x, y,
                    spawn.objectId(),
                    spawn.subtype(),
                    spawn.renderFlags(),
                    spawn.respawnTracked(),
                    spawn.rawYWord());
        }
    }

    /**
     * Fragment object spawned when the collapsing ledge breaks apart.
     * Each fragment renders a single piece from the smash mapping frame and
     * falls with gravity after its individual delay expires.
     * <p>
     * From disassembly (sonic.asm lines 4657-4683):
     * - obRoutine = 6 (Ledge_Display)
     * - obMap = pointer to the specific piece's mapping data
     * - Inherits position, graphics, priority, width from parent
     * - ledge_timedelay = delay from CFlo_Data1
     * - Falls via ObjectFall when delay reaches 0
     */
    public static class CollapsingLedgeFragmentInstance extends AbstractObjectInstance {

        private int x;
        private int y;
        private int velY;
        private int xFrac;
        private int yFrac;
        private int collapseDelay;
        private boolean falling;
        private final int smashFrameIndex;
        private final int pieceIndex;
        private final boolean hFlip;

        public CollapsingLedgeFragmentInstance(int parentX, int parentY,
                                               int smashFrameIndex, int pieceIndex,
                                               int delay, int renderFlags) {
            super(new ObjectSpawn(parentX, parentY, Sonic1ObjectIds.COLLAPSING_LEDGE,
                    0, renderFlags, false, 0), "LedgeFragment");
            this.x = parentX;
            this.y = parentY;
            this.smashFrameIndex = smashFrameIndex;
            this.pieceIndex = pieceIndex;
            this.collapseDelay = delay;
            this.falling = false;
            this.hFlip = (renderFlags & 0x01) != 0;
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
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (collapseDelay > 0) {
                collapseDelay--;
                return;
            }

            // Ledge_TimeZero: ObjectFall
            falling = true;
            int y32 = (y << 16) | (yFrac & 0xFFFF);
            int vy32 = (int) (short) velY;
            velY += GRAVITY;
            y32 += vy32 << 8;
            y = y32 >> 16;
            yFrac = y32 & 0xFFFF;

            // Delete when offscreen
            var camera = uk.co.jamesj999.sonic.camera.Camera.getInstance();
            if (camera != null) {
                int screenY = y - camera.getY();
                if (screenY > 224 + 0x80) {
                    setDestroyed(true);
                }
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.COLLAPSING_LEDGE);
            if (renderer == null || !renderer.isReady()) {
                return;
            }

            // Render just this piece from the smash frame (inheriting parent's X-flip)
            renderer.drawFramePieceByIndex(smashFrameIndex, pieceIndex, x, y, hFlip, false);
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY);
        }

        @Override
        public boolean isPersistent() {
            return !isDestroyed();
        }
    }
}
