package com.openggf.game.sonic2.objects;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * HTZ Zipline Lift (Object 0x16) - diagonal moving platform from Hill Top Zone.
 * <p>
 * A platform that waits idle until the player stands on it, then slides diagonally
 * for a duration based on subtype. After sliding, the platform section falls off
 * and the rope remains.
 * <p>
 * Based on Sonic 2 disassembly s2.asm lines 47326-47449.
 * <p>
 * <b>Subtype:</b> Duration in 8-frame units.
 * Duration = subtype * 8 frames. E.g., subtype 0x14 = 160 frames (~2.67 seconds).
 * <p>
 * <b>State machine:</b>
 * <ul>
 *   <li>0 = Wait: Platform waits until player stands on it</li>
 *   <li>1 = Slide: Moves diagonally, plays click sound every 16 frames</li>
 *   <li>2 = Fall: Platform section falls off, rope remains</li>
 * </ul>
 * <p>
 * <b>Velocities:</b>
 * <ul>
 *   <li>X velocity: ±0x200 (based on x_flip flag)</li>
 *   <li>Y velocity: +0x100 (always downward)</li>
 *   <li>Fall gravity: +0x38 per frame</li>
 * </ul>
 */
public class HTZLiftObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final Logger LOGGER = Logger.getLogger(HTZLiftObjectInstance.class.getName());

    // State constants
    private static final int STATE_WAIT = 0;
    private static final int STATE_SLIDE = 1;
    private static final int STATE_FALL = 2;

    // Physics constants from ROM
    private static final int X_VEL = 0x200;         // 2.0 pixels/frame (8.8 fixed-point)
    private static final int Y_VEL = 0x100;         // 1.0 pixels/frame
    private static final int FALL_GRAVITY = 0x38;   // 0.21875 pixels/frame^2

    // Collision params - adjusted for platform standing detection
    // Standing surface should be at objectY + 0x28 (40px down to platform)
    // Since offsetY positions the collision box CENTER, we need: offsetY - y_radius = 0x28
    private static final int COLLISION_WIDTH = 0x20;    // 32 pixels half-width
    private static final int COLLISION_Y_RADIUS = 0x10; // 16 pixels
    private static final int COLLISION_Y_OFFSET = 0x38; // 0x28 + 0x10 = center offset for 40px standing surface

    // Render priority from ROM (priority = 1)
    private static final int PRIORITY = 1;

    // Platform offset from center (d3 = #-$28 in JmpTo3_PlatformObject)
    private static final int PLATFORM_Y_OFFSET = -0x28; // -40 pixels

    // Position state (8.8 fixed-point for sub-pixel accuracy)
    private final int baseX;
    private final int baseY;
    private int xFixed;     // 8.8 fixed-point X position
    private int yFixed;     // 8.8 fixed-point Y position

    // Velocity (8.8 fixed-point)
    private int xVel;
    private int yVel;

    // State machine
    private int routineSecondary;   // 0=Wait, 1=Slide, 2=Fall
    private int slideTimer;         // Countdown timer for slide duration
    private int mappingFrame;       // 0=main, 2=rope-only

    // Flip flag
    private final boolean flippedX;

    // Track if scenery spawned
    private boolean scenerySpawned;

    // Dynamic spawn for moving position
    private ObjectSpawn dynamicSpawn;

    public HTZLiftObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.xFixed = spawn.x() << 8;
        this.yFixed = spawn.y() << 8;
        this.flippedX = (spawn.renderFlags() & 0x1) != 0;

        // Calculate slide duration: subtype * 8 frames
        int subtype = spawn.subtype() & 0xFF;
        this.slideTimer = subtype << 3; // subtype * 8

        // Initialize state
        this.routineSecondary = STATE_WAIT;
        this.mappingFrame = 0;
        this.xVel = 0;
        this.yVel = 0;
        this.scenerySpawned = false;

        refreshDynamicSpawn();

        LOGGER.fine(() -> String.format(
                "HTZLift init: pos=(%d,%d), subtype=0x%02X, duration=%d frames, flipped=%b",
                baseX, baseY, subtype, slideTimer, flippedX));
    }

    @Override
    public int getX() {
        return xFixed >> 8;
    }

    @Override
    public int getY() {
        return yFixed >> 8;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return dynamicSpawn != null ? dynamicSpawn : spawn;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        switch (routineSecondary) {
            case STATE_WAIT -> updateWait();
            case STATE_SLIDE -> updateSlide(frameCounter);
            case STATE_FALL -> updateFall(player);
        }

        refreshDynamicSpawn();
    }

    /**
     * Wait state: Check if player is standing on the platform.
     * ROM: Obj16_Wait (lines 47380-47392)
     */
    private void updateWait() {
        // The solid contact callback will set routineSecondary to STATE_SLIDE
        // when player stands on the platform
    }

    /**
     * Slide state: Move diagonally and play click sound every 16 frames.
     * ROM: Obj16_Slide (lines 47395-47416)
     */
    private void updateSlide(int frameCounter) {
        // Play click sound every 16 frames
        // ROM: andi.w #$F,d0 / bne.s + / move.w #SndID_HTZLiftClick,d0
        if ((frameCounter & 0x0F) == 0) {
            AudioManager.getInstance().playSfx(Sonic2Sfx.HTZ_LIFT_CLICK.id);
        }

        // Apply velocity (ObjectMove equivalent)
        xFixed += xVel;
        yFixed += yVel;

        // Decrement timer
        slideTimer--;
        if (slideTimer <= 0) {
            // Transition to fall state
            routineSecondary = STATE_FALL;
            mappingFrame = 2;  // Switch to rope-only frame
            xVel = 0;
            yVel = 0;

            // Spawn scenery marker (BridgeStake subtype 6)
            spawnScenery();
        }
    }

    /**
     * Fall state: Apply gravity and eject player when off-screen.
     * ROM: Obj16_Fall (lines 47419-47441)
     */
    private void updateFall(AbstractPlayableSprite player) {
        // Apply gravity
        // ROM: addi.w #$38,y_vel(a0)
        yVel += FALL_GRAVITY;

        // Apply velocity
        yFixed += yVel;

        // Check if fallen off bottom of screen
        Camera camera = Camera.getInstance();
        int screenBottom = camera.getMaxY() + 224;
        int currentY = yFixed >> 8;

        if (currentY > screenBottom) {
            // Eject any standing player and destroy self
            // ROM: move.w #$4000,x_pos(a0) effectively removes the object
            setDestroyed(true);
        }
    }

    /**
     * Spawn a scenery marker (BridgeStake) at current position.
     * ROM: move.b #ObjID_Scenery,id(a1) / move.b #6,subtype(a1)
     */
    private void spawnScenery() {
        if (scenerySpawned) {
            return;
        }
        scenerySpawned = true;

        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager == null) {
            return;
        }

        int currentX = xFixed >> 8;
        int currentY = yFixed >> 8;

        // Create a BridgeStake with subtype 6 at current position
        // This uses the zipline mappings frame 3 (left stake) or 4 (right stake) for scenery
        ObjectSpawn scenerySpawn = new ObjectSpawn(
                currentX,
                currentY,
                0x1C,  // ObjID_Scenery / BridgeStake
                6,     // subtype 6 for HTZ zipline stake
                spawn.renderFlags(),
                false,
                0);

        BridgeStakeObjectInstance stake = new BridgeStakeObjectInstance(scenerySpawn, "BridgeStake");
        objectManager.addDynamicObject(stake);

        LOGGER.fine(() -> String.format("HTZLift spawned scenery at (%d,%d)", currentX, currentY));
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (player == null || !contact.standing()) {
            return;
        }

        if (routineSecondary == STATE_WAIT) {
            // Player just stood on the platform - start sliding
            // ROM: addq.b #2,routine_secondary(a0)
            routineSecondary = STATE_SLIDE;

            // Set X velocity based on flip
            // ROM: move.w #$200,x_vel(a0) / btst #status.npc.x_flip,status(a0) / neg.w x_vel(a0)
            xVel = flippedX ? -X_VEL : X_VEL;
            yVel = Y_VEL;

            LOGGER.fine("HTZLift: Player stepped on, starting slide");
        }

        // In fall state, player gets ejected when the platform goes off-screen
        // This is handled in updateFall by the destroy() call
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(
                COLLISION_WIDTH,
                COLLISION_Y_RADIUS,
                COLLISION_Y_RADIUS + 1,
                0,                    // offsetX
                COLLISION_Y_OFFSET);  // offsetY - move collision down to platform
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;  // Platform is only solid from the top
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        // Only solid in wait and slide states
        return !isDestroyed() && routineSecondary != STATE_FALL;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.HTZ_LIFT);
        if (renderer == null) return;

        int drawX = xFixed >> 8;
        int drawY = yFixed >> 8;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;

        renderer.drawFrameIndex(mappingFrame, drawX, drawY, flippedX, vFlip);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    private void refreshDynamicSpawn() {
        int currentX = xFixed >> 8;
        int currentY = yFixed >> 8;

        if (dynamicSpawn == null || dynamicSpawn.x() != currentX || dynamicSpawn.y() != currentY) {
            dynamicSpawn = new ObjectSpawn(
                    currentX,
                    currentY,
                    spawn.objectId(),
                    spawn.subtype(),
                    spawn.renderFlags(),
                    spawn.respawnTracked(),
                    spawn.rawYWord());
        }
    }
}
