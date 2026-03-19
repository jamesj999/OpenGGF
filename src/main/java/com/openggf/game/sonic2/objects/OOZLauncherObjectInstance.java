package com.openggf.game.sonic2.objects;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * OOZ Launcher (Object 0x3D) - Striped block from Oil Ocean Zone that launches
 * a rolling player and breaks into fragments.
 * <p>
 * When a player lands on this block while rolling, the block breaks apart into
 * 16 fragments and spawns an invisible child object that tracks the player and
 * launches them toward the nearest LauncherBall (Object 0x48).
 * <p>
 * <b>Disassembly Reference:</b> s2.asm lines 50465-50742 (Obj3D)
 * <p>
 * <h3>Subtypes</h3>
 * <table border="1">
 *   <tr><th>Subtype</th><th>Behavior</th></tr>
 *   <tr><td>0x00</td><td>Vertical: uses ArtNem_StripedBlocksVert, launches player RIGHT (+$800 X)</td></tr>
 *   <tr><td>!=0</td><td>Horizontal: uses ArtNem_StripedBlocksHoriz, launches player UP (-$800 Y)</td></tr>
 * </table>
 */
public class OOZLauncherObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {
    private static final Logger LOGGER = Logger.getLogger(OOZLauncherObjectInstance.class.getName());

    // ========================================================================
    // ROM Constants (s2.asm lines 50494-50593)
    // ========================================================================

    // Solid collision parameters: d1=$1B, d2=$10, d3=$11
    private static final int SOLID_HALF_WIDTH = 0x1B;   // 27 pixels
    private static final int SOLID_HALF_HEIGHT = 0x11;   // 17 pixels (d3)
    private static final int SOLID_HEIGHT_D2 = 0x10;     // 16 pixels (d2 = half height for air)

    // Width for display/proximity (ROM: move.b #$10,width_pixels)
    private static final int WIDTH_PIXELS = 0x10;

    // Launch velocity (ROM: move.w #$800,x_vel or y_vel)
    private static final int LAUNCH_VELOCITY = 0x800;

    // Inertia set on player during launch (ROM: move.w #$800,inertia)
    private static final int LAUNCH_INERTIA = 0x800;

    // Fragment gravity (ROM: addi.w #$18,y_vel)
    private static final int FRAGMENT_GRAVITY = 0x18;

    // Fragment velocity table (ROM: word_2507A - 16 entries, X/Y pairs)
    // Each pair is initial velocity for one fragment piece
    private static final int[][] FRAGMENT_VELOCITIES = {
            {-0x400, -0x400},   // 0: Upper-left corner
            {-0x200, -0x400},   // 1: Upper-center-left
            { 0x200, -0x400},   // 2: Upper-center-right
            { 0x400, -0x400},   // 3: Upper-right corner
            {-0x3C0, -0x200},   // 4: Mid-upper-left
            {-0x1C0, -0x200},   // 5: Mid-upper-center-left
            { 0x1C0, -0x200},   // 6: Mid-upper-center-right
            { 0x3C0, -0x200},   // 7: Mid-upper-right
            {-0x380,  0x200},   // 8: Mid-lower-left
            {-0x180,  0x200},   // 9: Mid-lower-center-left
            { 0x180,  0x200},   // 10: Mid-lower-center-right
            { 0x380,  0x200},   // 11: Mid-lower-right
            {-0x340,  0x400},   // 12: Lower-left corner
            {-0x140,  0x400},   // 13: Lower-center-left
            { 0x140,  0x400},   // 14: Lower-center-right
            { 0x340,  0x400},   // 15: Lower-right corner
    };

    // Proximity detection bounds for invisible launcher
    // ROM: addi.w #$10,d0; cmpi.w #$20,d0 (horizontal)
    // ROM: cmpi.w #$10,d1 (vertical)
    private static final int PROXIMITY_HALF_X = 0x10;   // 16 pixels
    private static final int PROXIMITY_FULL_X = 0x20;   // 32 pixels
    private static final int PROXIMITY_Y = 0x10;         // 16 pixels

    // ========================================================================
    // State
    // ========================================================================

    private final boolean isVertical;    // subtype == 0 → vertical (launch right)
    private boolean broken = false;
    private boolean launcherActive = false;

    // Invisible launcher states per player (ROM routine 6 states)
    private int sonicLauncherState = 0;   // 0 = proximity detection, 2 = tracking movement
    private int tailsLauncherState = 0;

    // Saved player state before solid collision (ROM: objoff_32-36)
    private int savedSonicAnim;
    private int savedTailsAnim;
    private int savedSonicYVel;
    private int savedTailsYVel;

    private final SolidObjectParams solidParams;

    public OOZLauncherObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.isVertical = (spawn.subtype() & 0xFF) == 0;
        this.solidParams = new SolidObjectParams(SOLID_HALF_WIDTH, SOLID_HEIGHT_D2, SOLID_HALF_HEIGHT);
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        if (!broken) {
            updateMainBlock(frameCounter, player);
        } else if (launcherActive) {
            updateInvisibleLauncher(frameCounter, player);
        }
    }

    /**
     * Routine 2: Main block logic.
     * Saves player animation/velocity before solid collision check,
     * then checks if rolling player is standing on block.
     */
    private void updateMainBlock(int frameCounter, AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }

        // Save player state before solid collision (ROM: Obj3D_Main)
        savedSonicAnim = player.getAnimationId();
        savedSonicYVel = player.getYSpeed();

        for (AbstractPlayableSprite sidekick : SpriteManager.getInstance().getSidekicks()) {
            savedTailsAnim = sidekick.getAnimationId();
            savedTailsYVel = sidekick.getYSpeed();
        }

        // Solid collision is handled by SolidObjectProvider/SolidObjectListener
        // The onSolidContact callback will check if player is rolling and trigger launch
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (broken || !contact.standing()) {
            return;
        }

        // Determine which player is contacting
        boolean isSidekick = SpriteManager.getInstance().getSidekicks().contains(player);

        // Check if standing player is rolling (ROM: cmpi.b #AniIDSonAni_Roll,objoff_32)
        int savedAnim = isSidekick ? savedTailsAnim : savedSonicAnim;
        int savedYVel = isSidekick ? savedTailsYVel : savedSonicYVel;

        if (savedAnim == Sonic2AnimationIds.ROLL.id()) {
            launchPlayer(player, savedYVel, frameCounter);
            breakBlock(frameCounter);
        }
    }

    /**
     * Launch a player from the block (ROM: loc_24EB8).
     * Sets rolling state, preserves Y velocity, and transitions to airborne.
     */
    private void launchPlayer(AbstractPlayableSprite player, int savedYVel, int frameCounter) {
        // ROM: bset #status.player.rolling,status(a1)
        // setRolling(true) handles y_radius=14, x_radius=7 internally
        player.setRolling(true);
        // ROM: move.b #AniIDSonAni_Roll,anim(a1)
        player.setAnimationId(Sonic2AnimationIds.ROLL);
        // ROM: move.w d1,y_vel(a1) - restore saved Y velocity
        player.setYSpeed((short) savedYVel);
        // ROM: bset #status.player.in_air,status(a1)
        player.setAir(true);
        // ROM: bclr #status.player.on_object,status(a1)
        player.setOnObject(false);
        // ROM: move.b #2,routine(a1) - airborne routine (handled by setAir)
    }

    /**
     * Break the block into fragments and spawn invisible launcher (ROM: loc_24F04).
     */
    private void breakBlock(int frameCounter) {
        broken = true;
        launcherActive = true;
        sonicLauncherState = 0;
        tailsLauncherState = 0;

        spawnFragments();

        // Play smash sound
        try {
            AudioManager.getInstance().playSfx(Sonic2Sfx.SLOW_SMASH.id);
        } catch (Exception e) {
            // Don't let audio failure break game logic
        }
    }

    /**
     * Spawn 16 fragment pieces with velocities from ROM table (ROM: JmpTo2_BreakObjectToPieces).
     */
    private void spawnFragments() {
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (objectManager == null || renderManager == null) {
            return;
        }

        String artKey = isVertical ? Sonic2ObjectArtKeys.OOZ_LAUNCHER_VERT : Sonic2ObjectArtKeys.OOZ_LAUNCHER_HORIZ;
        PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
        ObjectSpriteSheet sheet = renderManager.getSheet(artKey);
        if (renderer == null || sheet == null) {
            return;
        }

        // Frame 1 of either sheet is the fragment grid (16 pieces, 1x1 tiles each)
        SpriteMappingFrame fragmentFrame = sheet.getFrameCount() > 1 ? sheet.getFrame(1) : null;
        if (fragmentFrame == null) {
            return;
        }

        List<SpriteMappingPiece> pieces = fragmentFrame.pieces();
        int count = Math.min(pieces.size(), FRAGMENT_VELOCITIES.length);

        for (int i = 0; i < count; i++) {
            SpriteMappingPiece piece = pieces.get(i);
            LauncherFragmentInstance fragment = new LauncherFragmentInstance(
                    spawn.x(), spawn.y(),
                    FRAGMENT_VELOCITIES[i][0], FRAGMENT_VELOCITIES[i][1],
                    piece, renderer);
            objectManager.addDynamicObject(fragment);
        }
    }

    // ========================================================================
    // Routine 6: Invisible Launcher (ROM: Obj3D_InvisibleLauncher)
    // ========================================================================

    /**
     * Update the invisible launcher that tracks players after block breaks.
     * Each player has independent state: 0 = proximity detect, 2 = tracking.
     */
    private void updateInvisibleLauncher(int frameCounter, AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }

        // Process main character (Sonic)
        sonicLauncherState = processLauncherState(player, sonicLauncherState);

        // Process Tails
        for (AbstractPlayableSprite sidekick : SpriteManager.getInstance().getSidekicks()) {
            tailsLauncherState = processLauncherState(sidekick, tailsLauncherState);
        }

        // Delete when both states are 0 (ROM: beq.w JmpTo3_MarkObjGone3)
        if (sonicLauncherState == 0 && tailsLauncherState == 0) {
            launcherActive = false;
        }
    }

    /**
     * Per-player launcher state machine.
     *
     * @return updated state
     */
    private int processLauncherState(AbstractPlayableSprite player, int state) {
        return switch (state) {
            case 0 -> processProximityDetection(player);
            case 2 -> processTracking(player);
            default -> 0;
        };
    }

    /**
     * State 0: Proximity detection (ROM: loc_24F84).
     * Checks if player is within bounds and starts the launch.
     */
    private int processProximityDetection(AbstractPlayableSprite player) {
        // Horizontal check: within 32 pixels centered on object
        // ROM: sub.w x_pos(a1),d0; addi.w #$10,d0; cmpi.w #$20,d0
        int dx = player.getCentreX() - spawn.x() + PROXIMITY_HALF_X;
        if (dx < 0 || dx >= PROXIMITY_FULL_X) {
            return 0;
        }

        // Vertical check
        int dy = player.getCentreY() - spawn.y();
        if (!isVertical) {
            dy += PROXIMITY_HALF_X;  // ROM: addi.w #$10,d1 (for horizontal subtype)
        }
        if (dy < 0 || dy >= PROXIMITY_Y) {
            return 0;
        }

        // ROM: Skip Tails if flying (CPU routine 4)
        // The engine doesn't expose Tails CPU routine directly, but this check
        // prevents capturing Tails while they're in flight mode
        if (SpriteManager.getInstance().getSidekicks().contains(player)
                && player.getAir() && !player.getRolling()) {
            return 0;
        }

        // Launch the player (ROM: loc_24FC2)
        // ROM: move.b #$81,obj_control(a1)
        player.setObjectControlled(true);
        player.setControlLocked(true);
        // ROM: move.b #AniIDSonAni_Roll,anim(a1)
        player.setAnimationId(Sonic2AnimationIds.ROLL);
        // ROM: move.w #$800,inertia(a1)
        player.setGSpeed((short) LAUNCH_INERTIA);

        if (isVertical) {
            // Subtype 0: Launch right (ROM: loc_24FF0)
            // ROM: move.w y_pos(a0),y_pos(a1)
            player.setCentreY((short) spawn.y());
            // ROM: move.w #$800,x_vel(a1); move.w #0,y_vel(a1)
            player.setXSpeed((short) LAUNCH_VELOCITY);
            player.setYSpeed((short) 0);
        } else {
            // Subtype != 0: Launch up (ROM: after tst.b subtype)
            // ROM: move.w x_pos(a0),x_pos(a1)
            player.setCentreX((short) spawn.x());
            // ROM: move.w #0,x_vel(a1); move.w #-$800,y_vel(a1)
            player.setXSpeed((short) 0);
            player.setYSpeed((short) -LAUNCH_VELOCITY);
        }

        // ROM: Common post-launch setup (loc_25002)
        player.setPushing(false);
        player.setAir(true);
        player.setOnObject(true);

        // Play roll sound (ROM: move.w #SndID_Roll,d0; jsr PlaySound)
        try {
            AudioManager.getInstance().playSfx(Sonic2Sfx.ROLL.id);
        } catch (Exception e) {
            // Don't let audio failure break game logic
        }

        return 2; // Advance to tracking state
    }

    /**
     * State 2: Tracking (ROM: loc_25036 / Obj3D_MoveCharacter).
     * Moves the player along their velocity until off-screen or captured by LauncherBall.
     */
    private int processTracking(AbstractPlayableSprite player) {
        // If player is no longer on-object (captured by LauncherBall or released), stop tracking
        if (!player.isOnObject() || !player.isObjectControlled()) {
            return 0;
        }

        // Check if player is off-screen (ROM: btst #render_flags.on_screen)
        if (!isPlayerOnScreen(player)) {
            // Release player
            player.setObjectControlled(false);
            player.setControlLocked(false);
            player.setAir(true);
            player.setOnObject(false);
            return 0;
        }

        // Move player by velocity (ROM: Obj3D_MoveCharacter)
        // ROM uses 16.16 fixed point: ext.l d0; asl.l #8,d0; add.l d0,x_pos(a1)
        int xVel = player.getXSpeed();
        int yVel = player.getYSpeed();
        player.setCentreX((short) (player.getCentreX() + (xVel >> 8)));
        player.setCentreY((short) (player.getCentreY() + (yVel >> 8)));

        return 2; // Stay in tracking state
    }

    private boolean isPlayerOnScreen(AbstractPlayableSprite player) {
        Camera camera = Camera.getInstance();
        int px = player.getCentreX();
        int py = player.getCentreY();
        int cx = camera.getX();
        int cy = camera.getY();
        // Generous bounds (player moves at 8 px/frame)
        return px >= cx - 64 && px < cx + 384 && py >= cy - 64 && py < cy + 288;
    }

    // ========================================================================
    // SolidObjectProvider
    // ========================================================================

    @Override
    public SolidObjectParams getSolidParams() {
        return solidParams;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return !broken;
    }

    @Override
    public boolean isTopSolidOnly() {
        return false;
    }

    @Override
    public boolean hasMonitorSolidity() {
        return false;
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (broken) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        String artKey = isVertical ? Sonic2ObjectArtKeys.OOZ_LAUNCHER_VERT : Sonic2ObjectArtKeys.OOZ_LAUNCHER_HORIZ;
        PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Frame 0 = intact block
        renderer.drawFrameIndex(0, spawn.x(), spawn.y(), false, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Draw solid collision box
        int x = spawn.x();
        int y = spawn.y();
        float r = broken ? 0.3f : 0.0f;
        float g = broken ? 0.3f : 1.0f;
        float b = isVertical ? 1.0f : 0.5f;
        ctx.drawRect(x, y, SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, r, g, b);

        if (launcherActive) {
            // Draw proximity detection box
            int proxCenterX = x;
            int proxCenterY = isVertical ? (y + PROXIMITY_Y / 2) : y;
            ctx.drawRect(proxCenterX, proxCenterY, PROXIMITY_HALF_X, PROXIMITY_Y / 2,
                    1.0f, 1.0f, 0.0f);
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    @Override
    public boolean isPersistent() {
        return launcherActive;
    }

    // ========================================================================
    // Fragment inner class
    // ========================================================================

    /**
     * Fragment piece spawned when the launcher block breaks.
     * Follows ballistic trajectory with gravity (ROM: Obj3D_Fragment, routine 4).
     */
    public static class LauncherFragmentInstance extends AbstractObjectInstance {

        private static final int GRAVITY = 0x18;  // ROM: addi.w #$18,y_vel(a0)

        private int currentX;
        private int currentY;
        private int subX;   // 8.8 fixed point
        private int subY;   // 8.8 fixed point
        private int velX;   // 8.8 fixed point
        private int velY;   // 8.8 fixed point
        private final SpriteMappingPiece piece;
        private final PatternSpriteRenderer renderer;
        private final List<SpriteMappingPiece> pieceList;

        public LauncherFragmentInstance(int x, int y, int velX, int velY,
                                        SpriteMappingPiece piece, PatternSpriteRenderer renderer) {
            super(new ObjectSpawn(x, y, 0x3D, 0, 0, false, 0), "LauncherFragment");
            this.currentX = x;
            this.currentY = y;
            this.subX = x << 8;
            this.subY = y << 8;
            this.velX = velX;
            this.velY = velY;
            this.piece = piece;
            this.renderer = renderer;
            this.pieceList = piece != null ? List.of(piece) : List.of();
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (isDestroyed()) {
                return;
            }

            // ROM: JmpTo10_ObjectMove + addi.w #$18,y_vel(a0)
            velY += GRAVITY;

            // Update position (8.8 fixed point)
            subX += velX;
            subY += velY;
            currentX = subX >> 8;
            currentY = subY >> 8;

            // ROM: btst #render_flags.on_screen; beq JmpTo26_DeleteObject
            int cameraY = Camera.getInstance().getY();
            if (currentY > cameraY + 224 + 32) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed() || renderer == null || !renderer.isReady() || pieceList.isEmpty()) {
                return;
            }
            renderer.drawPieces(pieceList, currentX, currentY, false, false);
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(4);
        }
    }

}
