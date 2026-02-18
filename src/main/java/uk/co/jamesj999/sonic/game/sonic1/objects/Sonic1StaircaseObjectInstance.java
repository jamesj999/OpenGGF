package uk.co.jamesj999.sonic.game.sonic1.objects;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.debug.DebugRenderContext;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.MultiPieceSolidProvider;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.SolidContact;
import uk.co.jamesj999.sonic.level.objects.SolidObjectListener;
import uk.co.jamesj999.sonic.level.objects.SolidObjectParams;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x5B - Blocks that form a staircase (SLZ).
 * <p>
 * A parent object that manages 4 platform pieces which rise/fall in a
 * coordinated staircase pattern when triggered by the player. The parent
 * handles the state machine and movement logic; child pieces read their
 * Y offsets from the parent.
 * <p>
 * State machine (subtype & 0x07):
 * <ul>
 *   <li>Type 0: Wait for player contact on top, 30-frame countdown, advance to type 1</li>
 *   <li>Type 1: Rise/fall — each piece gets an interpolated Y offset from a counter.
 *       Counter increments each frame. piece[0]=counter, piece[1]=75%, piece[2]=50%, piece[3]=25%.</li>
 *   <li>Type 2: Wait for player contact from below (d4 < 0), 60-frame countdown
 *       with 4-frame oscillation pattern, advance to type 3</li>
 *   <li>Type 3: Same as type 1 (continued movement after oscillation)</li>
 * </ul>
 * <p>
 * Multi-piece structure:
 * <ul>
 *   <li>4 platform pieces spaced 32 pixels apart horizontally</li>
 *   <li>When not flipped: initial offsets are $38, $39, $3A, $3B into parent's Y offset array</li>
 *   <li>When flipped (obStatus bit 0): initial offsets are $3B, $3A, $39, $38 (reversed)</li>
 *   <li>Each piece is a 32x32 block using level tile art (tile $21, palette 2)</li>
 * </ul>
 * <p>
 * Subtypes found in SLZ: 0x00 (wait-top), 0x02 (wait-bottom).
 * <p>
 * Reference: docs/s1disasm/_incObj/5B Staircase.asm
 */
public class Sonic1StaircaseObjectInstance extends AbstractObjectInstance
        implements MultiPieceSolidProvider, SolidObjectListener {

    // From disassembly: NUM_PIECES = dbf loop count (3) + 1
    private static final int NUM_PIECES = 4;

    // From disassembly: addi.w #$20,d2 — piece spacing
    private static final int PIECE_SPACING = 0x20;

    // From disassembly: move.b #$10,obActWid(a1)
    private static final int PIECE_ACTIVE_WIDTH = 0x10;

    // From disassembly Stair_Solid:
    //   addi.w #$B,d1 → half_width = obActWid + $B = $10 + $B = $1B
    //   move.w #$10,d2 → top height
    //   move.w #$11,d3 → bottom height
    private static final int PIECE_HALF_WIDTH = 0x1B;
    private static final int PIECE_TOP_HEIGHT = 0x10;
    private static final int PIECE_BOTTOM_HEIGHT = 0x11;

    // From disassembly: move.b #3,obPriority(a1)
    private static final int PRIORITY = 3;

    // From disassembly Stair_Type00: move.w #$1E,objoff_34(a0)
    private static final int TOP_CONTACT_DELAY = 0x1E; // 30 frames

    // From disassembly Stair_Type02: move.w #$3C,objoff_34(a0)
    private static final int BOTTOM_CONTACT_DELAY = 0x3C; // 60 frames

    // From disassembly Stair_Type01: cmpi.b #$80,(a1) — max counter value
    private static final int MAX_COUNTER = 0x80;

    // Collision parameters (shared by all pieces)
    private static final SolidObjectParams PIECE_PARAMS =
            new SolidObjectParams(PIECE_HALF_WIDTH, PIECE_TOP_HEIGHT, PIECE_BOTTOM_HEIGHT);

    // State
    private int state;              // subtype & 0x07 — incremented by state machine
    private int timer;              // objoff_34 — countdown timer
    private boolean playerOnTop;    // objoff_36 == 1 (standing on top)
    private boolean playerBelow;    // objoff_36 < 0 (d4 negative from SolidObject)
    private final int baseX;        // stair_origX
    private final int baseY;        // stair_origY
    private final boolean xFlip;    // obStatus bit 0

    // Y offsets for each piece: objoff_38..objoff_3B stored as bytes
    // Index 0 is the "master" counter, 1-3 are interpolated
    private final int[] yOffsets = new int[NUM_PIECES];

    // Per-piece assignment offsets (which yOffset index each piece reads)
    private final int[] pieceToOffset = new int[NUM_PIECES];

    // Contact tracking (same frame-delay approach as CPZ staircase)
    private int lastTopContactFrame = -2;
    private int lastBottomContactFrame = -2;

    // Dynamic spawn for position tracking
    private ObjectSpawn dynamicSpawn;

    public Sonic1StaircaseObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Staircase");
        this.baseX = spawn.x();
        this.baseY = spawn.y();
        this.xFlip = (spawn.renderFlags() & 0x01) != 0;
        this.state = spawn.subtype() & 0x07;
        this.timer = 0;
        this.playerOnTop = false;
        this.playerBelow = false;

        // From disassembly Stair_Main:
        //   moveq #$38,d3 / moveq #1,d4 (not flipped)
        //   moveq #$3B,d3 / moveq #-1,d4 (flipped)
        // d3 is stored in objoff_37 for each piece, d4 is the increment direction.
        // This determines which byte in objoff_38..objoff_3B each piece reads.
        // The offset $38 maps to yOffsets[0], $39 to yOffsets[1], etc.
        int startOffset = xFlip ? 3 : 0;
        int direction = xFlip ? -1 : 1;
        for (int i = 0; i < NUM_PIECES; i++) {
            pieceToOffset[i] = startOffset + (i * direction);
        }

        refreshDynamicSpawn();
    }

    @Override
    public int getX() {
        return baseX;
    }

    @Override
    public int getY() {
        return baseY;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return dynamicSpawn != null ? dynamicSpawn : spawn;
    }

    // MultiPieceSolidProvider implementation

    @Override
    public int getPieceCount() {
        return NUM_PIECES;
    }

    @Override
    public int getPieceX(int pieceIndex) {
        // From disassembly: pieces are spaced 32 pixels apart, X always increases
        return baseX + (pieceIndex * PIECE_SPACING);
    }

    @Override
    public int getPieceY(int pieceIndex) {
        // Each piece reads from a different offset in the parent's Y offset array
        int offsetIndex = pieceToOffset[pieceIndex];
        return baseY + yOffsets[offsetIndex];
    }

    @Override
    public SolidObjectParams getPieceParams(int pieceIndex) {
        return PIECE_PARAMS;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return PIECE_PARAMS;
    }

    @Override
    public boolean isTopSolidOnly() {
        // Original uses full SolidObject (not top-solid only)
        return false;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return !isDestroyed();
    }

    @Override
    public void onPieceContact(int pieceIndex, AbstractPlayableSprite player,
                               SolidContact contact, int frameCounter) {
        if (contact.standing() || contact.touchTop()) {
            lastTopContactFrame = frameCounter;
        }
        if (contact.touchBottom()) {
            lastBottomContactFrame = frameCounter;
        }
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact,
                               int frameCounter) {
        if (contact.standing() || contact.touchTop()) {
            lastTopContactFrame = frameCounter;
        }
        if (contact.touchBottom()) {
            lastBottomContactFrame = frameCounter;
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Decode contact from the previous frame
        boolean touchTop = (frameCounter - lastTopContactFrame) <= 1;
        boolean touchBottom = (frameCounter - lastBottomContactFrame) <= 1;

        // Run state machine (subtype & 0x07 dispatch)
        switch (state & 0x07) {
            case 0 -> updateType00(touchTop);
            case 1, 3 -> updateType01();
            case 2 -> updateType02(touchBottom);
            default -> {} // Subtypes 4-7 unused in SLZ placement data
        }

        refreshDynamicSpawn();
    }

    /**
     * Type 0 (Stair_Type00): Wait for player contact on TOP, 30-frame countdown.
     * <pre>
     *   tst.w   objoff_34(a0)        ; timer active?
     *   bne.s   loc_10FC0            ; yes, decrement
     *   cmpi.b  #1,objoff_36(a0)     ; player standing on top?
     *   bne.s   locret_10FBE         ; no, return
     *   move.w  #$1E,objoff_34(a0)   ; start 30-frame timer
     *
     * loc_10FC0:
     *   subq.w  #1,objoff_34(a0)
     *   bne.s   locret_10FBE
     *   addq.b  #1,obSubtype(a0)     ; advance to type 1
     * </pre>
     */
    private void updateType00(boolean touchTop) {
        if (timer == 0) {
            if (touchTop) {
                // From disassembly: cmpi.b #1,objoff_36(a0)
                // objoff_36 is set to 1 when player stands on a piece (bit 3 of obStatus)
                timer = TOP_CONTACT_DELAY;
            }
        }
        if (timer > 0) {
            timer--;
            if (timer == 0) {
                // addq.b #1,obSubtype(a0)
                state++;
            }
        }
        // Reset contact flag each frame
        playerOnTop = false;
    }

    /**
     * Type 1/3 (Stair_Type01): Rise — increment counter and apply interpolation.
     * <pre>
     *   lea     objoff_38(a0),a1
     *   cmpi.b  #$80,(a1)        ; max counter reached?
     *   beq.s   locret_11038     ; yes, stop
     *   addq.b  #1,(a1)          ; increment counter
     *   moveq   #0,d1
     *   move.b  (a1)+,d1         ; d1 = counter
     *   swap    d1               ; d1 = counter << 16
     *   lsr.l   #1,d1            ; d1 = counter/2 << 16
     *   move.l  d1,d2            ; d2 = 50%
     *   lsr.l   #1,d1            ; d1 = counter/4 << 16
     *   move.l  d1,d3
     *   add.l   d2,d3            ; d3 = 75%
     *   swap    d1 / swap d2 / swap d3
     *   move.b  d3,(a1)+ → yOffsets[1] = 75%
     *   move.b  d2,(a1)+ → yOffsets[2] = 50%
     *   move.b  d1,(a1)+ → yOffsets[3] = 25%
     * </pre>
     */
    private void updateType01() {
        if (yOffsets[0] >= MAX_COUNTER) {
            return; // Counter maxed out
        }
        yOffsets[0]++;
        applyStaircaseInterpolation();
    }

    /**
     * Type 2 (Stair_Type02): Wait for player contact from BELOW, 60-frame countdown
     * with oscillation.
     * <pre>
     *   tst.w   objoff_34(a0)        ; timer active?
     *   bne.s   loc_10FE0            ; yes, process
     *   tst.b   objoff_36(a0)        ; d4 from SolidObject negative?
     *   bpl.s   locret_10FDE         ; no, return
     *   move.w  #$3C,objoff_34(a0)   ; start 60-frame timer
     *
     * loc_10FE0:
     *   subq.w  #1,objoff_34(a0)
     *   bne.s   loc_10FEC            ; not zero yet, oscillate
     *   addq.b  #1,obSubtype(a0)     ; advance to type 3
     *
     * loc_10FEC (oscillation):
     *   lea     objoff_38(a0),a1
     *   move.w  objoff_34(a0),d0
     *   lsr.b   #2,d0               ; divide by 4
     *   andi.b  #1,d0               ; toggle bit
     *   move.b  d0,(a1)+            ; piece 0
     *   eori.b  #1,d0               ; flip
     *   move.b  d0,(a1)+            ; piece 1
     *   eori.b  #1,d0               ; flip
     *   move.b  d0,(a1)+            ; piece 2
     *   eori.b  #1,d0               ; flip
     *   move.b  d0,(a1)+            ; piece 3
     * </pre>
     */
    private void updateType02(boolean touchBottom) {
        if (timer == 0) {
            if (touchBottom) {
                // tst.b objoff_36(a0) / bpl.s — trigger on negative d4 (bottom contact)
                timer = BOTTOM_CONTACT_DELAY;
            }
        }
        if (timer > 0) {
            timer--;
            if (timer == 0) {
                // addq.b #1,obSubtype(a0)
                state++;
                // Clear oscillation offsets when transitioning
                for (int i = 0; i < NUM_PIECES; i++) {
                    yOffsets[i] = 0;
                }
            } else {
                // Oscillation pattern: checkerboard toggling every 4 frames
                // lsr.b #2,d0 / andi.b #1,d0
                int baseBit = (timer >> 2) & 1;
                yOffsets[0] = baseBit;
                yOffsets[1] = baseBit ^ 1;
                yOffsets[2] = baseBit;
                yOffsets[3] = baseBit ^ 1;
            }
        }
        playerBelow = false;
    }

    /**
     * Applies staircase interpolation from the disassembly using fixed-point arithmetic.
     * <p>
     * The original code uses 16.16 fixed point:
     * <pre>
     *   moveq #0,d1 / move.b (a1)+,d1 / swap d1  → d1 = counter << 16
     *   lsr.l #1,d1                                → d1 = counter/2 << 16
     *   move.l d1,d2                               → d2 = 50%
     *   lsr.l #1,d1                                → d1 = counter/4 << 16
     *   move.l d1,d3 / add.l d2,d3                 → d3 = 75%
     *   swap d1 / swap d2 / swap d3                → extract high words
     * </pre>
     * Result: yOffsets[1]=75%, yOffsets[2]=50%, yOffsets[3]=25% of counter.
     */
    private void applyStaircaseInterpolation() {
        int counter = yOffsets[0]; // Master piece offset (100%)

        // Convert to 16.16 fixed-point
        // moveq #0,d1 / move.b (a1)+,d1 → d1 is unsigned byte
        // swap d1 → d1 = counter << 16
        long d1 = ((long) (counter & 0xFF)) << 16;

        // lsr.l #1,d1 → logical shift right (unsigned)
        long d2 = d1 >>> 1;         // d2 = 50%
        d1 = d1 >>> 1;              // d1 = 50%
        d1 = d1 >>> 1;              // d1 = 25%
        long d3 = d1 + d2;          // d3 = 75%

        // swap extracts high word
        yOffsets[1] = (int) ((d3 >> 16) & 0xFF);
        yOffsets[2] = (int) ((d2 >> 16) & 0xFF);
        yOffsets[3] = (int) ((d1 >> 16) & 0xFF);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.SLZ_STAIRCASE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Render all 4 pieces at their computed positions
        for (int i = 0; i < NUM_PIECES; i++) {
            int pieceX = getPieceX(i);
            int pieceY = getPieceY(i);
            // Frame 0 is the 32x32 stair block
            renderer.drawFrameIndex(0, pieceX, pieceY, false, false);
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public boolean isPersistent() {
        // From disassembly: out_of_range.w DeleteObject,stair_origX(a0)
        // Uses stored original X for range check
        if (isDestroyed()) {
            return false;
        }
        Camera camera = Camera.getInstance();
        if (camera == null) {
            return true;
        }
        int objRounded = baseX & 0xFF80;
        int camRounded = (camera.getX() - 128) & 0xFF80;
        int distance = (objRounded - camRounded) & 0xFFFF;
        return distance <= (128 + 320 + 192);
    }

    private void refreshDynamicSpawn() {
        int pieceY = baseY + yOffsets[0];
        if (dynamicSpawn == null || dynamicSpawn.y() != pieceY) {
            dynamicSpawn = new ObjectSpawn(
                    baseX, pieceY,
                    spawn.objectId(),
                    spawn.subtype(),
                    spawn.renderFlags(),
                    spawn.respawnTracked(),
                    spawn.rawYWord());
        }
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        for (int i = 0; i < NUM_PIECES; i++) {
            int pieceX = getPieceX(i);
            int pieceY = getPieceY(i);
            ctx.drawRect(pieceX, pieceY, PIECE_HALF_WIDTH, PIECE_TOP_HEIGHT,
                    0.6f, 0.8f, 0.3f);
        }
    }
}
