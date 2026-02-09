package uk.co.jamesj999.sonic.game.sonic1.objects;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Sfx;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.SolidContact;
import uk.co.jamesj999.sonic.level.objects.SolidObjectListener;
import uk.co.jamesj999.sonic.level.objects.SolidObjectParams;
import uk.co.jamesj999.sonic.level.objects.SolidObjectProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Sonic 1 Spikes - Object ID 0x36.
 * <p>
 * Subtype high nybble selects visual variant (Spik_Var table):
 * <ul>
 *   <li>0: 3 upward spikes (frame 0, actWidth=$14)</li>
 *   <li>1: 3 sideways spikes (frame 1, actWidth=$10)</li>
 *   <li>2: 1 upward spike (frame 2, actWidth=4)</li>
 *   <li>3: 3 widely spaced upward spikes (frame 3, actWidth=$1C)</li>
 *   <li>4: 6 upward spikes (frame 4, actWidth=$40)</li>
 *   <li>5: 1 sideways spike (frame 5, actWidth=$10)</li>
 * </ul>
 * <p>
 * Subtype low nybble selects movement behavior:
 * <ul>
 *   <li>0: Static</li>
 *   <li>1: Vertical oscillation (32px displacement, 60-frame wait)</li>
 *   <li>2: Horizontal oscillation (32px displacement, 60-frame wait)</li>
 * </ul>
 * <p>
 * Reference: docs/s1disasm/_incObj/36 Spikes.asm
 */
public class Sonic1SpikeObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // Spik_Var table: frame index and actWidth per visual type (high nybble)
    // From disassembly: dc.b frame, width pairs
    private static final int[] FRAME_TABLE = {0, 1, 2, 3, 4, 5};
    private static final int[] ACT_WIDTH_TABLE = {0x14, 0x10, 4, 0x1C, 0x40, 0x10};

    // Movement constants from disassembly
    private static final int RETRACT_STEP = 0x800;    // addi.w #$800,objoff_34(a0)
    private static final int RETRACT_MAX = 0x2000;     // cmpi.w #$2000,objoff_34(a0)
    private static final int RETRACT_DELAY = 60;       // move.w #60,objoff_38(a0)
    private static final int UPRIGHT_AIR_HALF_HEIGHT = 0x10; // d2 in Spik_Upright
    private static final int LANDING_WINDOW = 0x10;          // cmpi.w #$10,d3 in SolidObject_TopBottom

    // From disassembly: move.b #4,obPriority(a0)
    private static final int PRIORITY = 4;

    private final int baseX;
    private final int baseY;
    private final int frameIndex;
    private final int actWidth;
    private final int movementType;

    private int currentX;
    private int currentY;
    private int displacement;    // objoff_34: current movement offset (8.8 fixed point)
    private int direction;       // objoff_36: 0=extending, 1=retracting
    private int delayTimer;      // objoff_38: frame delay counter

    private ObjectSpawn dynamicSpawn;

    public Sonic1SpikeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Spikes");
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.currentX = baseX;
        this.currentY = baseY;
        this.dynamicSpawn = spawn;

        // From disassembly: high nybble selects Spik_Var entry
        int visualType = (spawn.subtype() >> 4) & 0x0F;
        if (visualType >= FRAME_TABLE.length) {
            visualType = 0;
        }
        this.frameIndex = FRAME_TABLE[visualType];
        this.actWidth = ACT_WIDTH_TABLE[visualType];

        // From disassembly: andi.b #$F,obSubtype(a0) — low nybble is movement type
        this.movementType = spawn.subtype() & 0x0F;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        updateMovement();
        updateDynamicSpawn();
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (player == null) {
            return;
        }
        if (!shouldHurt(player, contact)) {
            return;
        }
        // From disassembly: tst.b (v_invinc).w / bne.s Spik_Display
        if (player.getInvincibleFrames() > 0) {
            return;
        }
        // S1 spike exception: unlike most hazards, spikes still hurt during post-hit
        // invulnerability frames (while still respecting invincibility power-up).
        if (player.isCpuControlled()) {
            player.applyHurtIgnoringIFrames(currentX, true);
            return;
        }
        boolean hadRings = player.getRingCount() > 0;
        if (hadRings && !player.hasShield()) {
            LevelManager.getInstance().spawnLostRings(player, frameCounter);
        }
        // spikeHit=true triggers DamageCause.SPIKE → GameSound.HURT_SPIKE (SFX 0xA6)
        player.applyHurtOrDeathIgnoringIFrames(currentX, true, hadRings);
    }

    @Override
    public boolean usesStickyContactBuffer() {
        // Spikes should not hold contact via the generic riding sticky buffer.
        // This keeps collision/hurt timing aligned with ROM spike behavior.
        return false;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        if (isSideways()) {
            // S1 SolidObject uses the d2 path for overlap in this code path, so keep
            // effective half-height the same for air/ground here.
            int halfHeight = (frameIndex == 5) ? 4 : 0x14;
            return new SolidObjectParams(0x1B, halfHeight, halfHeight);
        }
        // Spik_Upright: d1=obActWid+$B, d2=$10. Match ROM-effective overlap height.
        return new SolidObjectParams(actWidth + 0x0B, 0x10, 0x10);
    }

    @Override
    public ObjectSpawn getSpawn() {
        return dynamicSpawn;
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
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.SPIKE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
        renderer.drawFrameIndex(frameIndex, currentX, currentY, hFlip, vFlip);
    }

    private boolean isSideways() {
        return frameIndex == 1 || frameIndex == 5;
    }

    /**
     * Determines whether the given contact should hurt the player.
     * <p>
     * From disassembly:
     * - Sideways spikes (Spik_SideWays): hurt on side contact (d4==1)
     * - Upright spikes (Spik_Upright): hurt on standing (bit 3) or bottom contact (d4 < 0)
     */
    private boolean shouldHurt(AbstractPlayableSprite player, SolidContact contact) {
        if (isSideways()) {
            return contact.touchSide();
        }
        // From disassembly: btst #3,obStatus(a0) / bne.s Spik_Hurt (standing)
        // tst.w d4 / bpl.s Spik_Display (d4 < 0 = bottom contact also hurts)
        return (contact.standing() && isRomAccurateStandingHit(player)) || contact.touchBottom();
    }

    /**
     * ROM parity for Spik_Upright standing damage:
     * SolidObject must have reached Solid_Landed, which requires:
     * - d3 < $10 (top-contact window)
     * - X within obActWid window
     * - y_vel >= 0
     */
    private boolean isRomAccurateStandingHit(AbstractPlayableSprite player) {
        if (player.getYSpeed() < 0) {
            return false;
        }

        int maxTop = player.getYRadius() + UPRIGHT_AIR_HALF_HEIGHT;
        int relY = player.getCentreY() - currentY + 4 + maxTop;
        if (relY < 0 || relY >= LANDING_WINDOW) {
            return false;
        }

        int relX = player.getCentreX() - currentX + actWidth;
        int xWindow = actWidth * 2;
        return relX >= 0 && relX < xWindow;
    }

    /**
     * Updates spike movement based on movement type (Spik_Type0x).
     */
    private void updateMovement() {
        switch (movementType) {
            case 1 -> {
                // Spik_Type01: vertical movement
                updateDelay();
                int offsetPixels = displacement >> 8;
                currentX = baseX;
                currentY = baseY + offsetPixels;
            }
            case 2 -> {
                // Spik_Type02: horizontal movement
                updateDelay();
                int offsetPixels = displacement >> 8;
                currentX = baseX + offsetPixels;
                currentY = baseY;
            }
            default -> {
                // Spik_Type00: static
                currentX = baseX;
                currentY = baseY;
            }
        }
    }

    /**
     * Oscillation state machine (Spik_Wait).
     * Extends to RETRACT_MAX, waits RETRACT_DELAY frames, retracts to 0, waits again.
     * Plays sfx_SpikesMove when delay expires and object is on screen.
     */
    private void updateDelay() {
        if (delayTimer > 0) {
            delayTimer--;
            if (delayTimer == 0 && isOnScreen()) {
                // From disassembly: move.w #sfx_SpikesMove,d0 / jsr (QueueSound2).l
                try {
                    AudioManager.getInstance().playSfx(Sonic1Sfx.SPIKES_MOVE.id);
                } catch (Exception e) {
                    // Prevent audio failure from breaking game logic
                }
            }
            return;
        }

        if (direction != 0) {
            // Retracting: subi.w #$800,objoff_34(a0) / bcc.s locret_CFE6
            displacement -= RETRACT_STEP;
            if (displacement < 0) {
                displacement = 0;
                direction = 0;
                delayTimer = RETRACT_DELAY;
            }
            return;
        }

        // Extending: addi.w #$800,objoff_34(a0)
        displacement += RETRACT_STEP;
        if (displacement >= RETRACT_MAX) {
            displacement = RETRACT_MAX;
            direction = 1;
            delayTimer = RETRACT_DELAY;
        }
    }

    private void updateDynamicSpawn() {
        if (dynamicSpawn.x() == currentX && dynamicSpawn.y() == currentY) {
            return;
        }
        dynamicSpawn = new ObjectSpawn(
                currentX, currentY,
                spawn.objectId(), spawn.subtype(),
                spawn.renderFlags(), spawn.respawnTracked(),
                spawn.rawYWord());
    }
}
