package com.openggf.game.sonic1.objects;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractFallingFragment;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import com.openggf.debug.DebugColor;
import java.util.List;

/**
 * Object 0x53 - Collapsing Floors (MZ, SLZ, SBZ).
 * <p>
 * A platform that collapses when Sonic stands on it. After a brief delay,
 * the floor splits into 8 fragment objects that fall with gravity. Used in
 * Marble Zone, Star Light Zone, and Scrap Brain Zone.
 * <p>
 * Zone-specific art:
 * <ul>
 *   <li>MZ: Nem_MzBlock at ArtTile_MZ_Block ($2B8), palette 2. Frames 0/1.</li>
 *   <li>SLZ: Nem_SlzBlock at ArtTile_SLZ_Collapsing_Floor ($4E0), palette 2. Frames 2/3.</li>
 *   <li>SBZ: Nem_SbzFloor at ArtTile_SBZ_Collapsing_Floor ($3F5), palette 2. Frames 0/1.</li>
 * </ul>
 * <p>
 * Subtypes:
 * <ul>
 *   <li>Bit 7: If set, floor X-flips to face the player when standing on it</li>
 *   <li>Bit 0: Fragment data table select (0 = CFlo_Data2, 1 = CFlo_Data3)</li>
 * </ul>
 * <p>
 * State machine (obRoutine values):
 * <ul>
 *   <li>0 (CFlo_Main): Initialize - set zone-specific art, render flags, priority, delay</li>
 *   <li>2 (CFlo_Touch): Platform collision, collapse timer countdown, optional X-flip</li>
 *   <li>4 (CFlo_Collapse): Collapse initiated, countdown with WalkOff behavior</li>
 *   <li>6 (CFlo_Display): Fragment falling - WalkOff while collapse_flag set, ObjectFall when delay=0</li>
 *   <li>8 (CFlo_Delete): Delete when offscreen</li>
 *   <li>$A (CFlo_WalkOff): ExitPlatform + MvSonicOnPtfm2 + RememberState</li>
 * </ul>
 * <p>
 * Reference: docs/s1disasm/_incObj/53 Collapsing Floors.asm
 */
public class Sonic1CollapsingFloorObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // From disassembly: move.w #$20,d1 (half-width for PlatformObject)
    private static final int PLATFORM_HALF_WIDTH = 0x20;

    // PlatformObject applies subq.w #8,d0 to place the platform surface
    // 8 pixels above the object's Y center. Model this as half-height = 8.
    private static final int PLATFORM_HALF_HEIGHT = 0x08;

    // From disassembly: move.b #$44,obActWid(a0)
    private static final int ACTIVE_WIDTH = 0x44;

    // From disassembly: move.b #4,obPriority(a0)
    private static final int PRIORITY = 4;

    // From disassembly: move.b #7,cflo_timedelay(a0)
    private static final int INITIAL_COLLAPSE_DELAY = 7;

    // Gravity constant from ObjectFall: addi.w #$38,obVelY(a0)
    private static final int GRAVITY = 0x38;

    // Fragment count: d1 = 7 -> dbf loop = 8 iterations (moveq #7,d1 at loc_846C)
    private static final int FRAGMENT_COUNT = 8;

    // Intact mapping frame index (always 0 in the zone-specific sheet)
    private static final int FRAME_INTACT = 0;
    // Smash mapping frame index (always 1 in the zone-specific sheet)
    private static final int FRAME_SMASH = 1;

    // Disintegration delay data from CFlo_Data2 (8 bytes)
    // Used when subtype bit 0 = 0
    // From sonic.asm: CFlo_Data2: dc.b $1E, $16, $E, 6, $1A, $12, $A, 2
    private static final int[] CFLO_DATA2 = {
            0x1E, 0x16, 0x0E, 0x06, 0x1A, 0x12, 0x0A, 0x02
    };

    // Disintegration delay data from CFlo_Data3 (8 bytes)
    // Used when subtype bit 0 = 1
    // From sonic.asm: CFlo_Data3: dc.b $16, $1E, $1A, $12, 6, $E, $A, 2
    private static final int[] CFLO_DATA3 = {
            0x16, 0x1E, 0x1A, 0x12, 0x06, 0x0E, 0x0A, 0x02
    };

    // The subtype byte from ROM placement
    private final int subtype;

    // The art key for rendering (zone-specific)
    private final String artKey;

    // Routine state: 0=init, 2=touch, 4=collapse, 6=display(fragment), 8=delete
    private int routine;

    // Current position (center coordinates)
    private int x;
    private int y;

    // Collapse timer (cflo_timedelay = objoff_38)
    private int collapseDelay;

    // Collapse flag (cflo_collapse_flag = objoff_3A): set when player activates collapse
    private boolean collapseFlag;

    // 16.16 fall state for ObjectFall (routine 6). y is synced to/from motion.y.
    private final SubpixelMotion.State fallMotion = new SubpixelMotion.State(0, 0, 0, 0, 0, 0);

    // Whether fragments have been spawned
    private boolean fragmented;

    // X-flip state (obRender bit 0). Can change dynamically for subtype bit 7 objects.
    private boolean hFlip;

    public Sonic1CollapsingFloorObjectInstance(ObjectSpawn spawn, int zoneIndex) {
        super(spawn, "CollapsingFloor");
        this.subtype = spawn.subtype() & 0xFF;
        this.x = spawn.x();
        this.y = spawn.y();
        this.collapseDelay = INITIAL_COLLAPSE_DELAY;
        this.collapseFlag = false;
        this.fragmented = false;
        this.hFlip = (spawn.renderFlags() & 0x01) != 0;

        // Zone-specific art key selection
        // From disassembly CFlo_Main: checks v_zone for SLZ and SBZ
        this.artKey = switch (zoneIndex) {
            case Sonic1Constants.ZONE_SLZ -> ObjectArtKeys.SLZ_COLLAPSING_FLOOR;
            case Sonic1Constants.ZONE_SBZ -> ObjectArtKeys.SBZ_COLLAPSING_FLOOR;
            default -> ObjectArtKeys.MZ_COLLAPSING_FLOOR; // MZ default
        };

        // Skip init routine, start at routine 2 (CFlo_Touch)
        this.routine = 2;
        updateDynamicSpawn(x, y);
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
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        switch (routine) {
            case 2 -> updateTouch(player);
            case 4 -> updateCollapse(player);
            case 6 -> updateDisplay(player);
            case 8 -> destroyWithWindowGatedRespawn();
            default -> { }
        }
        updateDynamicSpawn(x, y);
    }

    /**
     * Routine 2 (CFlo_Touch): Platform collision with collapse trigger.
     * <p>
     * From disassembly:
     * <pre>
     *     tst.b  cflo_collapse_flag(a0)  ; has Sonic touched the object?
     *     beq.s  .solid                  ; if not, branch
     *     tst.b  cflo_timedelay(a0)      ; has time delay reached zero?
     *     beq.w  CFlo_Fragment           ; if yes, branch
     *     subq.b #1,cflo_timedelay(a0)   ; subtract 1 from time
     * .solid:
     *     move.w #$20,d1
     *     bsr.w  PlatformObject
     *     tst.b  obSubtype(a0)
     *     bpl.s  .remstate               ; skip X-flip if subtype bit 7 clear
     *     btst   #3,obStatus(a1)
     *     beq.s  .remstate
     *     bclr   #0,obRender(a0)
     *     move.w obX(a1),d0
     *     sub.w  obX(a0),d0
     *     bcc.s  .remstate               ; if player X >= object X, don't flip
     *     bset   #0,obRender(a0)         ; player is to the left, flip
     * </pre>
     */
    private void updateTouch(AbstractPlayableSprite player) {
        if (collapseFlag) {
            if (collapseDelay <= 0) {
                // CFlo_Fragment (line 125): clears collapse_flag, then fragments
                performFragment(true);
                return;
            }
            collapseDelay--;
        }
        // PlatformObject + RememberState handled via SolidObjectProvider

        // Subtype bit 7: X-flip to face the player
        if ((subtype & 0x80) != 0 && player != null) {
            ObjectManager objectManager = services().objectManager();
            if (objectManager != null && objectManager.isAnyPlayerRiding(this)) {
                // bclr #0,obRender(a0) - default to no flip
                hFlip = false;
                // move.w obX(a1),d0 / sub.w obX(a0),d0 / bcc.s .remstate
                int playerX = player.getCentreX();
                if (playerX < x) {
                    // bset #0,obRender(a0) - player is to the left, flip
                    hFlip = true;
                }
            }
        }
    }

    /**
     * Routine 4 (CFlo_Collapse): Entered when player stands on the platform.
     * <p>
     * From disassembly:
     * <pre>
     *     tst.b  cflo_timedelay(a0)
     *     beq.w  loc_8458                ; timer expired -> fragment
     *     move.b #1,cflo_collapse_flag(a0)
     *     subq.b #1,cflo_timedelay(a0)
     * </pre>
     * Falls through to CFlo_WalkOff: ExitPlatform + MvSonicOnPtfm2 + RememberState.
     */
    private void updateCollapse(AbstractPlayableSprite player) {
        if (collapseDelay <= 0) {
            // loc_8458 (line 128): enters fragment code WITHOUT clearing collapse_flag.
            // The flag remains set so routine 6 runs WalkOff behavior (loc_8402).
            performFragment(false);
            return;
        }
        collapseFlag = true;
        collapseDelay--;
        // CFlo_WalkOff (ExitPlatform + MvSonicOnPtfm2) handled by SolidContacts
    }

    /**
     * Routine 6 (CFlo_Display): Fragment display and falling.
     * <p>
     * Three paths depending on state:
     * <ol>
     *   <li>Timer > 0 and collapse_flag NOT set: decrement timer, display only</li>
     *   <li>Timer > 0 and collapse_flag set: WalkOff behavior, detach player when timer=0</li>
     *   <li>Timer = 0: ObjectFall (gravity) + display, delete when offscreen</li>
     * </ol>
     * <p>
     * From disassembly (CFlo_Display / loc_8402 / CFlo_TimeZero):
     * <pre>
     *     tst.b  cflo_timedelay(a0)
     *     beq.s  CFlo_TimeZero
     *     tst.b  cflo_collapse_flag(a0)
     *     bne.w  loc_8402
     *     subq.b #1,cflo_timedelay(a0)
     *     bra.w  DisplaySprite
     * loc_8402:
     *     subq.b #1,cflo_timedelay(a0)
     *     bsr.w  CFlo_WalkOff
     *     lea    (v_player).w,a1
     *     btst   #3,obStatus(a1)
     *     beq.s  loc_842E
     *     tst.b  cflo_timedelay(a0)
     *     bne.s  locret_843A
     *     bclr   #3,obStatus(a1)
     *     bclr   #5,obStatus(a1)
     *     move.b #id_Run,obPrevAni(a1)
     * loc_842E:
     *     move.b #0,cflo_collapse_flag(a0)
     *     move.b #6,obRoutine(a0)
     * </pre>
     */
    private void updateDisplay(AbstractPlayableSprite player) {
        if (collapseDelay > 0) {
            if (!collapseFlag) {
                // Simple countdown, display only
                collapseDelay--;
                return;
            }

            // loc_8402: WalkOff with collapse flag set
            collapseDelay--;

            ObjectManager objectManager = services().objectManager();
            boolean playerRiding = objectManager != null && player != null
                    && objectManager.isAnyPlayerRiding(this);

            if (!playerRiding) {
                // loc_842E: Player walked off - clear flag, stay in routine 6
                collapseFlag = false;
                routine = 6;
            } else if (collapseDelay <= 0) {
                // Timer expired with player still standing:
                // bclr #3,obStatus(a1) / bclr #5,obStatus(a1)
                objectManager.clearRidingObject(player);
                // move.b #id_Run,obPrevAni(a1) - restart Sonic's animation
                // loc_842E: clear flag
                collapseFlag = false;
                routine = 6;
            }
            // locret_843A: return while timer > 0 and player still riding
            return;
        }

        // CFlo_TimeZero: ObjectFall + DisplaySprite
        applyObjectFall();

        // tst.b obRender(a0) / bpl.s CFlo_Delete
        if (!isOnScreen()) {
            destroyWithWindowGatedRespawn();
        }
    }

    /**
     * Fragment the collapsing floor into 8 individual pieces.
     * <p>
     * Shared fragment code from sonic.asm lines 4629-4683:
     * <pre>
     *     lea    (CFlo_Data2).l,a4       ; default delay table
     *     btst   #0,obSubtype(a0)
     *     beq.s  loc_846C
     *     lea    (CFlo_Data3).l,a4       ; alternate delay table
     * loc_846C:
     *     moveq  #7,d1                   ; 8 fragments (0-7)
     *     addq.b #1,obFrame(a0)          ; advance to smash mapping frame
     * </pre>
     * Each fragment object spawns with its delay from the selected data table.
     * First fragment reuses this object; remaining 7 are new dynamic objects.
     * Plays sfx_Collapse.
     *
     * @param clearFlag true when called from Routine 2 (CFlo_Fragment, line 125: clears flag),
     *                  false when called from Routine 4 (loc_8458, line 128: preserves flag)
     */
    private void performFragment(boolean clearFlag) {
        if (fragmented) {
            return;
        }
        fragmented = true;
        if (clearFlag) {
            // CFlo_Fragment: move.b #0,cflo_collapse_flag(a0)
            collapseFlag = false;
        }
        // When !clearFlag (routine 4 entry), collapse_flag remains set.
        // This causes routine 6 (CFlo_Display) to run the WalkOff path (loc_8402)
        // which keeps the player supported on the first fragment until delay expires.

        ObjectManager objectManager = services().objectManager();
        if (objectManager == null) {
            return;
        }

        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }

        ObjectSpriteSheet sheet = renderManager.getSheet(artKey);
        PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
        if (sheet == null || renderer == null || FRAME_SMASH >= sheet.getFrameCount()) {
            return;
        }

        // Select delay table based on subtype bit 0
        // From disassembly: btst #0,obSubtype(a0)
        int[] delays = ((subtype & 0x01) != 0) ? CFLO_DATA3 : CFLO_DATA2;

        // Convert self to fragment (routine 6, first piece)
        this.routine = 6;
        this.collapseDelay = delays[0];

        // Spawn remaining 7 fragments as dynamic objects
        int maxFragments = Math.min(FRAGMENT_COUNT, delays.length);
        for (int i = 1; i < maxFragments; i++) {
            CollapsingFloorFragmentInstance fragment = new CollapsingFloorFragmentInstance(
                    x, y, FRAME_SMASH, i, delays[i], hFlip, artKey);
            objectManager.addDynamicObject(fragment);
        }

        // Play collapse sound: move.w #sfx_Collapse,d0 / jmp (QueueSound2).l
        services().playSfx(Sonic1Sfx.COLLAPSE.id);
    }

    /**
     * ObjectFall subroutine: applies gravity to Y velocity and updates position.
     * Delegates to {@link SubpixelMotion#objectFall(SubpixelMotion.State, int)}.
     */
    private void applyObjectFall() {
        fallMotion.y = y;
        SubpixelMotion.objectFall(fallMotion, GRAVITY);
        y = fallMotion.y;
    }

    /**
     * Destroy this object and allow it to respawn when camera leaves the area.
     * Uses DeleteObject behavior (not RememberState).
     */
    private void destroyWithWindowGatedRespawn() {
        if (!isDestroyed()) {
            ObjectManager objectManager = services().objectManager();
            if (objectManager != null) {
                objectManager.removeFromActiveSpawns(spawn);
            }
        }
        setDestroyed(true);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        if (routine == 6 && fragmented) {
            // Fragment: render only piece 0 from the smash frame
            renderer.drawFramePieceByIndex(FRAME_SMASH, 0, x, y, hFlip, false);
        } else {
            // Normal: render the intact floor
            renderer.drawFrameIndex(FRAME_INTACT, x, y, hFlip, false);
        }
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        if (isDestroyed()) {
            return;
        }
        // Draw platform collision bounds
        ctx.drawRect(getX(), getY(), PLATFORM_HALF_WIDTH, 8,
                0.6f, 0.4f, 0.0f);
        ctx.drawWorldLabel(getX(), getY(), -2,
                String.format("CFloor r%d d=%d %s",
                        routine, collapseDelay,
                        collapseFlag ? "COLLAPSE" : ""),
                DebugColor.ORANGE);
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // PlatformObject uses half-width for platform checks.
        // Half-height of 8 models PlatformObject's subq.w #8,d0 (surface 8px above center).
        return new SolidObjectParams(PLATFORM_HALF_WIDTH, PLATFORM_HALF_HEIGHT, PLATFORM_HALF_HEIGHT);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (isDestroyed()) {
            return false;
        }
        if (routine <= 4) {
            return true;
        }
        // Routine 6: remains collidable while collapse_flag is set
        // (CFlo_Display -> loc_8402 runs CFlo_WalkOff/MvSonicOnPtfm2)
        return routine == 6 && collapseFlag;
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (routine == 2 && contact.standing()) {
            // Player stepped on floor: transition to routine 4 (CFlo_Collapse)
            // From disassembly: addq.b #2,obRoutine(a0) in PlatformObject
            routine = 4;
        }
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
        Camera camera = GameServices.camera();
        if (camera == null) {
            return true;
        }
        int objRounded = objectX & 0xFF80;
        int camRounded = (camera.getX() - 128) & 0xFF80;
        int distance = (objRounded - camRounded) & 0xFFFF;
        return distance <= (128 + 320 + 192);
    }

    /**
     * Fragment object spawned when the collapsing floor breaks apart.
     * <p>
     * Each fragment renders a single piece from the smash mapping frame and
     * falls with gravity after its individual delay expires.
     * <p>
     * From disassembly (sonic.asm lines 4657-4683):
     * <ul>
     *   <li>obRoutine = 6 (CFlo_Display)</li>
     *   <li>Inherits position, graphics, priority, width from parent</li>
     *   <li>cflo_timedelay = delay from CFlo_Data2/CFlo_Data3</li>
     *   <li>Falls via ObjectFall when delay reaches 0</li>
     * </ul>
     */
    public static class CollapsingFloorFragmentInstance extends AbstractFallingFragment {

        private final int smashFrameIndex;
        private final int pieceIndex;
        private final boolean hFlip;
        private final String artKey;

        public CollapsingFloorFragmentInstance(int parentX, int parentY,
                                               int smashFrameIndex, int pieceIndex,
                                               int delay, boolean hFlip, String artKey) {
            super(new ObjectSpawn(parentX, parentY, Sonic1ObjectIds.COLLAPSING_FLOOR,
                    0, 0, false, 0), "CFloFragment", delay, PRIORITY);
            this.smashFrameIndex = smashFrameIndex;
            this.pieceIndex = pieceIndex;
            this.hFlip = hFlip;
            this.artKey = artKey;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }
            PatternSpriteRenderer renderer = getRenderer(artKey);
            if (renderer == null) {
                return;
            }

            renderer.drawFramePieceByIndex(smashFrameIndex, pieceIndex, getX(), getY(), hFlip, false);
        }
    }
}
