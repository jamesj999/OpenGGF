package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.ButtonVineTriggerManager;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * MTZ Long Platform (Object 0x65) - Long moving platform from Metropolis Zone.
 * <p>
 * Disassembly Reference: s2.asm lines 52348-52750 (Obj65 code)
 * <p>
 * Properties table (indexed by (subtype >> 4) & 0x0E):
 * <pre>
 * Index 0: width=0x40, y_radius=0x0C, frame=0 (4-block platform)
 * Index 1: width=0x80, y_radius=0x01, frame=1 (2-block platform, no_balancing)
 * Index 2: width=0x20, y_radius=0x0C, frame=2 -> cog object (routine 6)
 * Index 3: width=0x40, y_radius=0x03, frame=3 (3-block platform)
 * Index 4: width=0x10, y_radius=0x10, frame=4 (small block)
 * Index 5: width=0x20, y_radius=0x00, frame=5 (unused?)
 * Index 6: width=0x40, y_radius=0x0C, frame=6 (4-block platform)
 * Index 7: width=0x80, y_radius=0x07, frame=7 (big platform with button trigger)
 * </pre>
 * <p>
 * Movement subtypes (bits 0-3 after init):
 * <ul>
 *   <li>0: Stationary (return_26C8E)</li>
 *   <li>1: Button-triggered move right (loc_26CA4)</li>
 *   <li>2: Timer-triggered move right (loc_26D34)</li>
 *   <li>3: Player proximity move (loc_26D94)</li>
 *   <li>4: Step-on advance (loc_26E3C)</li>
 *   <li>5: Conveyor-style continuous movement (loc_26E4A)</li>
 *   <li>6: Timer-triggered move right with delay (loc_26C90)</li>
 *   <li>7: Button-triggered move right with maxDist init (loc_26D14)</li>
 * </ul>
 * <p>
 * Bit 7 of initial subtype triggers child cog spawn via AllocateObjectAfterCurrent.
 */
public class MTZLongPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // Obj65_Properties table (s2.asm line 52362)
    // {width_pixels, y_radius, movementData, childSubtype}
    private static final int[][] PROPERTIES = {
            {0x40, 0x0C},  // Index 0: frame 0
            {0x80, 0x01},  // Index 1: frame 1 (no_balancing)
            {0x20, 0x0C},  // Index 2: frame 2 -> standalone cog (routine 6)
            {0x40, 0x03},  // Index 3: frame 3
            {0x10, 0x10},  // Index 4: frame 4
            {0x20, 0x00},  // Index 5: frame 5
            {0x40, 0x0C},  // Index 6: frame 6
            {0x80, 0x07},  // Index 7: frame 7
    };

    // Delay timer value: move.w #$B4,objoff_36(a0)
    private static final int DELAY_FRAMES = 0xB4;

    // Movement speed per frame: addq.w #2,objoff_3A(a0)
    private static final int MOVE_SPEED = 2;

    // Movement speed for subtype 5: addq.w #2,x_pos(a0)
    private static final int CONVEYOR_SPEED = 2;

    // Proximity detection speed: addi.w #$10,objoff_3A(a0) / subi.w #$10
    private static final int PROXIMITY_SPEED = 0x10;

    // Proximity detection offsets when NOT x-flipped (s2.asm lines 52606-52607)
    private static final int PROX_LEFT_NORMAL = -0x20;
    private static final int PROX_RIGHT_NORMAL = 0x60;
    // When x-flipped (s2.asm lines 52612-52613)
    private static final int PROX_LEFT_FLIPPED = -0xA0;
    private static final int PROX_RIGHT_FLIPPED = -0x20;
    private static final int PROX_TOP = -0x10;
    private static final int PROX_BOTTOM = 0x40;

    // Subtype 5 hardcoded X limits
    // MTZ Act 3 (metropolis_zone_2): two stop points
    private static final int MTZ3_STOP_1 = 0x1CC0;
    private static final int MTZ3_STOP_2 = 0x2940;
    // MTZ Acts 1&2: single stop point
    private static final int MTZ12_STOP = 0x1BC0;
    // MTZ Acts 1&2 reverse direction limit
    private static final int MTZ12_REVERSE = 0x1880;

    /**
     * Shared variable: MTZ_Platform_Cog_X ($FFFFF7B0).
     * Written by subtype 5 platform, read by standalone cog (routine 6).
     */
    private static int mtzPlatformCogX;

    // Position tracking
    private int x;
    private int y;
    private int baseX;           // objoff_34 - original X position
    private int baseY;           // objoff_30 - original Y position

    // Subtype configuration
    private int widthPixels;
    private int yRadius;
    private int mappingFrame;
    private int moveSubtype;     // Current movement subtype (bits 0-3, mutable)

    // Movement state
    private int maxDist;         // objoff_3C - maximum movement distance
    private int currentDist;     // objoff_3A - current movement distance
    private int delayTimer;      // objoff_36 - delay countdown
    private boolean triggered;   // objoff_38 - trigger flag
    private int buttonId;        // objoff_3E - ButtonVine trigger ID
    private boolean xFlip;       // status.npc.x_flip

    // Standing detection
    private int lastContactFrame = -2;

    // Dynamic spawn for position updates
    private ObjectSpawn dynamicSpawn;

    public MTZLongPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "MTZLongPlatform");
        init();
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
    public SolidObjectParams getSolidParams() {
        // From s2.asm lines 52457-52463: d1=width+5, d2=y_radius, d3=y_radius+1
        int halfWidth = widthPixels + 5;
        return new SolidObjectParams(halfWidth, yRadius, yRadius + 1);
    }

    @Override
    public boolean isTopSolidOnly() {
        return false;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return !isDestroyed();
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (contact.standing() || contact.touchTop()) {
            lastContactFrame = frameCounter;
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        executeMovement(frameCounter, player);
        refreshDynamicSpawn();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        PatternSpriteRenderer renderer = null;
        if (renderManager != null) {
            // Platform uses level art tiles. CPZ_STAIR_BLOCK sheet provides compatible mappings:
            // Sheet frame 0 = 4-block platform (obj65 frame 0/2/3)
            // Sheet frame 1 = 2-block platform (obj65 frame 1)
            renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.CPZ_STAIR_BLOCK);
        }
        if (renderer != null && renderer.isReady()) {
            // Map propsIndex to sheet frame: index 1 -> frame 1, all others -> frame 0
            int sheetFrame = (mappingFrame == 1) ? 1 : 0;
            renderer.drawFrameIndex(sheetFrame, x, y, xFlip, false);
        }
    }

    /**
     * Returns the shared MTZ_Platform_Cog_X value for standalone cog objects.
     */
    public static int getMtzPlatformCogX() {
        return mtzPlatformCogX;
    }

    /**
     * Resets all global state for MTZLongPlatform objects.
     * Call on level load to ensure clean state across level transitions.
     */
    public static void resetGlobalState() {
        mtzPlatformCogX = 0;
    }

    /**
     * Returns the current distance for child cog animation.
     */
    public int getCurrentDist() {
        return currentDist;
    }

    private void init() {
        // Calculate properties index: (subtype >> 2) & 0x1C gives byte offset,
        // then >> 2 again for index (s2.asm lines 52383-52389)
        int rawSubtype = spawn.subtype();
        int d0 = (rawSubtype >> 2) & 0x1C; // lsr.w #2 + andi.w #$1C
        int propsIndex = d0 >> 2; // lsr.w #2 for mapping_frame
        if (propsIndex >= PROPERTIES.length) {
            propsIndex = 0;
        }

        widthPixels = PROPERTIES[propsIndex][0];
        yRadius = PROPERTIES[propsIndex][1];
        mappingFrame = propsIndex;

        // Note: propsIndex 2 (standalone cog) is routed to MTZLongPlatformCogInstance by the factory.

        xFlip = (spawn.renderFlags() & 0x01) != 0;

        // Store base positions (s2.asm lines 52404-52405)
        baseX = spawn.x();
        baseY = spawn.y();
        x = baseX;
        y = baseY;

        // After reading width_pixels and y_radius from (a3)+, the pointer advances
        // to the next entry. The 3rd read ((a3)+) gives maxDist (= width of next entry),
        // and the 4th read ((a3)) gives the child subtype (= y_radius of next entry).
        // s2.asm lines 52407-52414
        int nextIndex = propsIndex + 1;
        if (nextIndex < PROPERTIES.length) {
            maxDist = PROPERTIES[nextIndex][0];
        } else {
            maxDist = 0;
        }

        // Check bit 7 of original subtype for child cog spawn
        // s2.asm line 52411: move.b subtype(a0),d0; bpl.w loc_26C16
        if ((rawSubtype & 0x80) != 0) {
            // Extract button ID from lower nibble (s2.asm line 52412-52413)
            buttonId = rawSubtype & 0x0F;

            // Read child subtype from properties (4th byte = y_radius of next entry)
            // s2.asm line 52414: move.b (a3),subtype(a0)
            int childSubtype = 0;
            if (nextIndex < PROPERTIES.length) {
                childSubtype = PROPERTIES[nextIndex][1];
            }

            // Special case: if child subtype is 7, init currentDist=maxDist
            // s2.asm lines 52415-52417
            if (childSubtype == 7) {
                currentDist = maxDist;
            }

            // Spawn child cog (s2.asm lines 52419-52437)
            spawnChildCog();

            // At loc_26C16: andi.b #$F,subtype(a0) - applies to the NEW subtype
            moveSubtype = childSubtype & 0x0F;

            // Clear respawn bit (s2.asm lines 52440-52444)
            // Handled by engine respawn system
        } else {
            // No child cog - just mask subtype to lower 4 bits
            // s2.asm line 52447: andi.b #$F,subtype(a0)
            moveSubtype = rawSubtype & 0x0F;
        }
    }

    private void spawnChildCog() {
        ObjectManager objManager = LevelManager.getInstance().getObjectManager();
        if (objManager == null) {
            return;
        }

        // Calculate child position (s2.asm lines 52423-52430)
        int childX = spawn.x() - 0x4C; // addi.w #-$4C,x_pos(a1)
        int childY = spawn.y() + 0x14;  // addi.w #$14,y_pos(a1)
        boolean childXFlip;

        if (xFlip) {
            // btst #status.npc.x_flip -> bne.s +
            // When parent is x-flipped, child stays at base offset
            childXFlip = false;
        } else {
            // When parent is NOT x-flipped: subi.w #-$18,x_pos(a1)
            // subi.w #-$18 = addi.w #$18 (subtract negative = add)
            childX += 0x18;
            childXFlip = true; // bset #render_flags.x_flip
        }

        MTZLongPlatformCogInstance cog = new MTZLongPlatformCogInstance(
                childX, childY, childXFlip, this);
        objManager.addDynamicObject(cog);
    }

    private void executeMovement(int frameCounter, AbstractPlayableSprite player) {
        switch (moveSubtype) {
            case 0 -> { /* Stationary - return_26C8E */ }
            case 1 -> moveButtonTriggered();
            case 2 -> moveTimerTriggered();
            case 3 -> movePlayerProximity(player);
            case 4 -> moveStepOnAdvance(frameCounter);
            case 5 -> moveConveyor();
            case 6 -> moveTimerTriggeredWithDelay();
            case 7 -> moveButtonTriggeredBack();
        }
    }

    /**
     * Subtype 1: Button-triggered move right (loc_26CA4).
     * Waits for ButtonVine trigger, then moves right until maxDist.
     * On reaching maxDist, advances subtype (wraps to subtype 2).
     */
    private void moveButtonTriggered() {
        if (!triggered) {
            // Check ButtonVine trigger (s2.asm lines 52511-52516)
            if (ButtonVineTriggerManager.getTrigger(buttonId)) {
                triggered = true;
            } else {
                updatePositionFromDist();
                return;
            }
        }

        // Moving: check if reached maxDist (s2.asm lines 52519-52522)
        if (currentDist == maxDist) {
            // Advance subtype (s2.asm line 52539)
            moveSubtype++;
            delayTimer = DELAY_FRAMES;
            triggered = false;
            // Set respawn bit (s2.asm line 52546)
        } else {
            currentDist += MOVE_SPEED;
        }

        updatePositionFromDist();
    }

    /**
     * Subtype 6: Timer-triggered move right with delay (loc_26C90).
     * Same as subtype 1 but uses delay timer instead of button.
     */
    private void moveTimerTriggeredWithDelay() {
        if (!triggered) {
            delayTimer--;
            if (delayTimer <= 0) {
                triggered = true;
            } else {
                updatePositionFromDist();
                return;
            }
        }

        // Same forward movement as subtype 1
        if (currentDist == maxDist) {
            moveSubtype++;
            delayTimer = DELAY_FRAMES;
            triggered = false;
        } else {
            currentDist += MOVE_SPEED;
        }

        updatePositionFromDist();
    }

    /**
     * Subtype 2: Timer-triggered move left/retract (loc_26D34).
     * Moves back to start after delay.
     */
    private void moveTimerTriggered() {
        if (!triggered) {
            delayTimer--;
            if (delayTimer <= 0) {
                triggered = true;
            } else {
                updatePositionFromDist();
                return;
            }
        }

        // Moving back: check if retracted to 0 (s2.asm lines 52571-52573)
        if (currentDist == 0) {
            // Retract subtype (s2.asm line 52590)
            moveSubtype--;
            delayTimer = DELAY_FRAMES;
            triggered = false;
            // Clear respawn bit (s2.asm line 52597)
        } else {
            currentDist -= MOVE_SPEED;
        }

        updatePositionFromDist();
    }

    /**
     * Subtype 7: Button-triggered retract (loc_26D14).
     * Same as subtype 2 but uses button trigger instead of timer.
     */
    private void moveButtonTriggeredBack() {
        if (!triggered) {
            // Check ButtonVine trigger (s2.asm lines 52553-52558)
            if (ButtonVineTriggerManager.getTrigger(buttonId)) {
                triggered = true;
            } else {
                updatePositionFromDist();
                return;
            }
        }

        // Retract (s2.asm lines 52571-52573)
        if (currentDist == 0) {
            moveSubtype--;
            delayTimer = DELAY_FRAMES;
            triggered = false;
        } else {
            currentDist -= MOVE_SPEED;
        }

        updatePositionFromDist();
    }

    /**
     * Subtype 3: Player proximity triggered (loc_26D94).
     * Extends when player is in detection zone, retracts when player leaves.
     */
    private void movePlayerProximity(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }

        // Calculate detection zone (s2.asm lines 52602-52614)
        int left, right;
        if (xFlip) {
            left = baseX + PROX_LEFT_FLIPPED;
            right = baseX + PROX_RIGHT_FLIPPED;
        } else {
            left = baseX + PROX_LEFT_NORMAL;
            right = baseX + PROX_RIGHT_NORMAL;
        }
        int top = y + PROX_TOP;
        int bottom = y + PROX_BOTTOM;

        // Check if any player is in the detection zone
        int playerX = player.getCentreX();
        int playerY = player.getCentreY();
        boolean playerInZone = playerX >= left && playerX < right
                && playerY >= top && playerY < bottom;

        if (playerInZone) {
            // Extend (s2.asm lines 52647-52651)
            if (currentDist < maxDist) {
                currentDist += PROXIMITY_SPEED;
                if (currentDist > maxDist) {
                    currentDist = maxDist;
                }
            }
        } else {
            // Retract (s2.asm lines 52654-52657)
            if (currentDist > 0) {
                currentDist -= PROXIMITY_SPEED;
                if (currentDist < 0) {
                    currentDist = 0;
                }
            }
        }

        // Update position (s2.asm lines 52660-52669)
        int d0 = currentDist;
        if (xFlip) {
            d0 = -d0 + 0x40;
        }
        x = baseX - d0;
    }

    /**
     * Subtype 4: Step-on advance (loc_26E3C).
     * Increments subtype when player stands on it.
     */
    private void moveStepOnAdvance(int frameCounter) {
        // s2.asm lines 52676-52679: btst #p1_standing_bit,status(a0); beq +; addq.b #1,subtype
        boolean standing = (frameCounter - lastContactFrame) <= 1;
        if (standing) {
            moveSubtype++;
        }
    }

    /**
     * Subtype 5: Conveyor-style continuous movement (loc_26E4A).
     * Moves platform continuously, reverses at zone-specific boundaries.
     * Writes x_pos to MTZ_Platform_Cog_X shared variable.
     */
    private void moveConveyor() {
        LevelManager lm = LevelManager.getInstance();
        boolean isMtzAct3 = (lm.getCurrentZone() == Sonic2ZoneConstants.ZONE_MTZ
                && lm.getCurrentAct() == 2);

        if (!triggered) {
            // Moving right: addq.w #2,x_pos(a0)
            x += CONVEYOR_SPEED;

            if (isMtzAct3) {
                // s2.asm lines 52688-52696: two stop points
                if (x == MTZ3_STOP_1 || x == MTZ3_STOP_2) {
                    moveSubtype = 0; // move.b #0,subtype(a0)
                }
            } else {
                // s2.asm lines 52700-52703: single stop, then reverse
                if (x == MTZ12_STOP) {
                    triggered = true;
                }
            }
        } else {
            // Moving left: subq.w #2,x_pos(a0)
            x -= CONVEYOR_SPEED;

            // s2.asm lines 52708-52710: reverse when reaching left limit
            if (x == MTZ12_REVERSE) {
                triggered = false;
            }
        }

        // Update base position and shared variable
        // s2.asm lines 52713-52714
        baseX = x;
        mtzPlatformCogX = x;
    }

    /**
     * Common position update for subtypes 1, 2, 6, 7 (s2.asm lines 52525-52535 / 52576-52586).
     * Applies currentDist to baseX, respecting x_flip.
     */
    private void updatePositionFromDist() {
        int d0 = currentDist;
        if (xFlip) {
            d0 = -d0 + 0x80; // neg.w d0; addi.w #$80,d0
        }
        x = baseX - d0;
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

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        int halfWidth = widthPixels + 5;
        int left = x - halfWidth;
        int right = x + halfWidth;
        int top = y - yRadius;
        int bottom = y + yRadius + 1;

        ctx.drawLine(left, top, right, top, 0.4f, 0.7f, 0.9f);
        ctx.drawLine(right, top, right, bottom, 0.4f, 0.7f, 0.9f);
        ctx.drawLine(right, bottom, left, bottom, 0.4f, 0.7f, 0.9f);
        ctx.drawLine(left, bottom, left, top, 0.4f, 0.7f, 0.9f);

        // Center cross
        ctx.drawLine(x - 4, y, x + 4, y, 0.4f, 0.7f, 0.9f);
        ctx.drawLine(x, y - 4, x, y + 4, 0.4f, 0.7f, 0.9f);
    }

}
