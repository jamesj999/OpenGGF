package com.openggf.game.sonic1.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.DebugRenderContext;
import com.openggf.game.OscillationManager;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
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
 * Object 0x5A -- Platforms moving in circles (SLZ).
 * <p>
 * A top-solid platform that moves in a circle driven by two oscillation
 * values (v_oscillate+$22 and v_oscillate+$26). The subtype byte controls
 * the direction and phase of the circular motion.
 * <p>
 * <b>Subtype encoding:</b>
 * <ul>
 *   <li>Bits 2-3: Movement type (0 = type00, 1 = type04)</li>
 *   <li>Bit 0: Negate both X and Y offsets (180-degree phase shift)</li>
 *   <li>Bit 1: Negate X and exchange X/Y (90-degree rotation)</li>
 * </ul>
 * <p>
 * <b>Type difference:</b>
 * <ul>
 *   <li>type00: Standard circular motion</li>
 *   <li>type04: Same as type00 but with X offset negated (mirror about Y axis)</li>
 * </ul>
 * <p>
 * <b>Disassembly reference:</b> docs/s1disasm/_incObj/5A SLZ Circling Platform.asm
 */
public class Sonic1CirclingPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // From disassembly: move.b #$18,obActWid(a0)
    private static final int HALF_WIDTH = 0x18;

    // Platform surface height (thin platform)
    private static final int HALF_HEIGHT = 0x08;

    // From disassembly: move.b #4,obPriority(a0)
    private static final int PRIORITY = 4;

    // Oscillation centre offset: subi.b #$50,d1 / subi.b #$50,d2
    private static final int OSC_CENTRE = 0x50;

    // Oscillation data offsets (v_oscillate+$22 -> getByte(0x20), v_oscillate+$26 -> getByte(0x24))
    // v_oscillate has a 2-byte control prefix, so OscillationManager offset = ROM offset - 2
    private static final int OSC_X_OFFSET = 0x20; // v_oscillate+$22 = oscillator 8 value high byte
    private static final int OSC_Y_OFFSET = 0x24; // v_oscillate+$26 = oscillator 9 value high byte

    // Saved original positions (circ_origX = objoff_32, circ_origY = objoff_30)
    private final int origX;
    private final int origY;

    // Current dynamic position
    private int x;
    private int y;

    // Subtype configuration
    private final boolean negateBoth;   // Bit 0: negate both offsets
    private final boolean rotated;      // Bit 1: negate X, exchange X/Y
    private final boolean type04;       // Bits 2-3: type04 additionally negates X

    // Dynamic spawn for position tracking
    private ObjectSpawn dynamicSpawn;

    public Sonic1CirclingPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "CirclingPlatform");

        int subtype = spawn.subtype() & 0xFF;

        // Disasm: move.w obX(a0),circ_origX(a0) / move.w obY(a0),circ_origY(a0)
        this.origX = spawn.x();
        this.origY = spawn.y();

        // Subtype bit decoding:
        // andi.w #$C,d0 / lsr.w #1,d0 → type index (0 or 2 in jump table = type00 or type04)
        int typeIndex = (subtype & 0x0C) >> 1;
        this.type04 = (typeIndex >= 2);

        // btst #0,obSubtype(a0) — negate both d1 and d2
        this.negateBoth = (subtype & 0x01) != 0;

        // btst #1,obSubtype(a0) — negate d1 and exchange d1/d2 (90-degree rotation)
        this.rotated = (subtype & 0x02) != 0;

        // Set initial position
        this.x = origX;
        this.y = origY;
        updatePosition();
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
        if (isDestroyed()) {
            return;
        }
        updatePosition();
        refreshDynamicSpawn();
    }

    /**
     * Calculates the platform position from oscillation values.
     * <p>
     * From disassembly Circ_Types:
     * <pre>
     *   move.b  (v_oscillate+$22).w,d1   ; X oscillation
     *   subi.b  #$50,d1                  ; centre around $50
     *   ext.w   d1
     *   move.b  (v_oscillate+$26).w,d2   ; Y oscillation
     *   subi.b  #$50,d2
     *   ext.w   d2
     *
     *   ; Bit 0: negate both
     *   btst    #0,obSubtype(a0)
     *   beq.s   .noshift
     *   neg.w   d1
     *   neg.w   d2
     *
     *   ; Bit 1: negate d1, exchange d1/d2
     *   btst    #1,obSubtype(a0)
     *   beq.s   .norotate
     *   neg.w   d1
     *   exg     d1,d2
     *
     *   ; Type04 additionally negates d1 before adding to origX
     *   ; (.type04: neg.w d1 at line 106)
     *
     *   add.w   circ_origX(a0),d1
     *   move.w  d1,obX(a0)
     *   add.w   circ_origY(a0),d2
     *   move.w  d2,obY(a0)
     * </pre>
     */
    private void updatePosition() {
        // Read oscillation values as unsigned bytes, subtract centre, sign-extend
        // move.b (v_oscillate+$22).w,d1 / subi.b #$50,d1 / ext.w d1
        int d1 = (byte) ((OscillationManager.getByte(OSC_X_OFFSET) & 0xFF) - OSC_CENTRE);
        // move.b (v_oscillate+$26).w,d2 / subi.b #$50,d2 / ext.w d2
        int d2 = (byte) ((OscillationManager.getByte(OSC_Y_OFFSET) & 0xFF) - OSC_CENTRE);

        // btst #0,obSubtype(a0) — negate both
        if (negateBoth) {
            d1 = -d1;
            d2 = -d2;
        }

        // btst #1,obSubtype(a0) — negate d1, exchange
        if (rotated) {
            d1 = -d1;
            int tmp = d1;
            d1 = d2;
            d2 = tmp;
        }

        // type04: neg.w d1 (additional X negation)
        if (type04) {
            d1 = -d1;
        }

        // add.w circ_origX(a0),d1 / move.w d1,obX(a0)
        x = origX + d1;
        // add.w circ_origY(a0),d2 / move.w d2,obY(a0)
        y = origY + d2;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.SLZ_CIRCLING_PLATFORM);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Render platform at current position (single frame: frame 0)
        renderer.drawFrameIndex(0, x, y, false, false);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    // ---- SolidObjectProvider ----

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(HALF_WIDTH, HALF_HEIGHT, HALF_HEIGHT);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return !isDestroyed();
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // Standing state is managed by ObjectManager
    }

    // ---- Persistence ----

    @Override
    public boolean isPersistent() {
        // Disasm: out_of_range.w DeleteObject,circ_origX(a0)
        // Uses stored original X (not current X) for range check
        return !isDestroyed() && isOrigXOnScreen();
    }

    /**
     * Range check using original X position, matching the disassembly's
     * out_of_range.w macro applied to circ_origX.
     */
    private boolean isOrigXOnScreen() {
        var camera = Camera.getInstance();
        if (camera == null) {
            return true;
        }
        int objRounded = origX & 0xFF80;
        int camRounded = (camera.getX() - 128) & 0xFF80;
        int distance = (objRounded - camRounded) & 0xFFFF;
        // out_of_range: cmpi.w #128+320+192,d0 / bhi.s exit
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

    // ---- Debug rendering ----

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Draw origin anchor point (yellow cross)
        ctx.drawLine(origX - 4, origY, origX + 4, origY, 1.0f, 1.0f, 0.0f);
        ctx.drawLine(origX, origY - 4, origX, origY + 4, 1.0f, 1.0f, 0.0f);

        // Draw line from origin to current position (cyan)
        ctx.drawLine(origX, origY, x, y, 0.0f, 1.0f, 1.0f);

        // Draw collision box (green for solid platform)
        int left = x - HALF_WIDTH;
        int right = x + HALF_WIDTH;
        int top = y - HALF_HEIGHT;
        int bottom = y + HALF_HEIGHT;
        ctx.drawLine(left, top, right, top, 0.0f, 1.0f, 0.0f);
        ctx.drawLine(right, top, right, bottom, 0.0f, 1.0f, 0.0f);
        ctx.drawLine(right, bottom, left, bottom, 0.0f, 1.0f, 0.0f);
        ctx.drawLine(left, bottom, left, top, 0.0f, 1.0f, 0.0f);

        // Draw platform center (red cross)
        ctx.drawLine(x - 4, y, x + 4, y, 1.0f, 0.0f, 0.0f);
        ctx.drawLine(x, y - 4, x, y + 4, 1.0f, 0.0f, 0.0f);
    }


}
