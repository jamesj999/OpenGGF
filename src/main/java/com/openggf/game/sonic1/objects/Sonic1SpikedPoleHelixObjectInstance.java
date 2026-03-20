package com.openggf.game.sonic1.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x17 — Spiked Pole Helix (GHZ).
 * <p>
 * A horizontal row of spike balls rotating around a pole. Each spike cycles through
 * 8 animation frames driven by the global sync counter {@code v_ani0_frame}. Adjacent
 * spikes are phase-offset by 1, creating a helical rotation effect.
 * <p>
 * Only frame 0 (spike pointing straight up) is harmful ({@code obColType = $84}).
 * All other frames are harmless. Since each spike has a different phase offset, the
 * "harmful" position travels along the helix.
 * <p>
 * <b>Subtype:</b> Total number of spikes in the helix (default 16 = 0x10).
 * <p>
 * <b>Disassembly reference:</b> docs/s1disasm/_incObj/17 Spiked Pole Helix.asm
 */
public class Sonic1SpikedPoleHelixObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider {

    // ---- Constants from disassembly ----

    // Spacing between spikes: addi.w #$10,d3
    private static final int SPIKE_SPACING = 0x10;

    // Display priority: move.b #3,obPriority(a0)
    private static final int DISPLAY_PRIORITY = 3;

    // Collision type when harmful (frame 0 only): move.b #$84,obColType(a0)
    // HURT ($80) + size index 4
    private static final int COLLISION_TYPE_HARMFUL = 0x84;

    // v_ani0_frame timer period: v_ani0_time resets to $0B (12 frames per tick)
    private static final int ANIM_FRAME_DURATION = 12;

    // Number of animation frames in the rotation cycle: andi.b #7,d0
    private static final int FRAME_COUNT = 8;

    // ---- Spike data ----

    // Per-spike state: positions and phase offsets
    private final int spikeCount;       // Total number of spikes (including parent at center)
    private final int[] spikeX;         // X position of each spike
    private final int spikeY;           // Y position (all share the same Y)
    private final int[] spikePhase;     // hel_frame per spike (0-7)
    private final int[] spikeFrame;     // Current display frame per spike (computed each update)
    private final boolean[] spikeHarmful; // Whether each spike is harmful this frame

    // Parent spike index (the one at the original spawn X position)
    private final int parentIndex;

    // v_ani0_frame: local counter replicating global sync animation counter 0
    // Decrements every ANIM_FRAME_DURATION frames, wraps at FRAME_COUNT (AND #7)
    private int animTimer = ANIM_FRAME_DURATION - 1;
    private int animCounter = 0;

    public Sonic1SpikedPoleHelixObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SpikedPoleHelix");

        int subtype = spawn.subtype() & 0xFF;
        if (subtype == 0) {
            subtype = 0x10; // Default from SonLVL
        }
        this.spikeCount = subtype;
        this.spikeY = spawn.y();

        spikeX = new int[spikeCount];
        spikePhase = new int[spikeCount];
        spikeFrame = new int[spikeCount];
        spikeHarmful = new boolean[spikeCount];

        // Calculate leftmost X position:
        // d3 = obX(a0) - (helix_length / 2) * 16
        // Disasm: move.w d1,d0 / lsr.w #1,d0 / lsl.w #4,d0 / sub.w d0,d3
        int centerX = spawn.x();
        int halfOffset = (spikeCount / 2) * SPIKE_SPACING;
        int leftX = centerX - halfOffset;

        // Build spike positions and phase offsets.
        // The disasm creates children from leftmost to rightmost, skipping the center
        // position (which is the parent). Phase counter d6 increments sequentially.
        //
        // From Hel_Build:
        //   d6 starts at 0, increments by 1 per spike (AND #7 to wrap)
        //   When d3 == obX(a0), the parent gets the current d6 value,
        //   d6 increments again, and d3 advances past the center.
        int d6 = 0;
        int parentIdx = -1;
        int spikeIdx = 0;

        for (int i = 0; i < spikeCount; i++) {
            int x = leftX + i * SPIKE_SPACING;

            if (x == centerX && parentIdx == -1) {
                // This is the parent spike position
                parentIdx = spikeIdx;
                spikeX[spikeIdx] = x;
                spikePhase[spikeIdx] = d6 & 0x07;
                d6++;
                spikeIdx++;
            } else {
                // Child spike
                spikeX[spikeIdx] = x;
                spikePhase[spikeIdx] = d6 & 0x07;
                d6++;
                spikeIdx++;
            }
        }

        // If centerX wasn't exactly hit (e.g., odd count), the parent is at the center-most position
        if (parentIdx == -1) {
            parentIdx = spikeCount / 2;
        }
        this.parentIndex = parentIdx;

        // Compute initial frames
        updateSpikeFrames();
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        // Replicate v_ani0_frame timing: decrement counter every ANIM_FRAME_DURATION frames
        // Disasm (SynchroAnimate): subq.b #1,(v_ani0_time).w / bpl.s .nochange
        //   move.b #$0B,(v_ani0_time).w / subq.b #1,(v_ani0_frame).w
        animTimer--;
        if (animTimer < 0) {
            animTimer = ANIM_FRAME_DURATION - 1;
            animCounter = (animCounter - 1) & 0x07;
        }

        updateSpikeFrames();
    }

    /**
     * Compute display frame and harmfulness for each spike.
     * <p>
     * From Hel_RotateSpikes:
     * <pre>
     *   move.b (v_ani0_frame).w,d0
     *   move.b #0,obColType(a0)        ; harmless by default
     *   add.b  hel_frame(a0),d0        ; add per-spike phase
     *   andi.b #7,d0                   ; wrap to 0-7
     *   move.b d0,obFrame(a0)          ; set display frame
     *   bne.s  locret_7DA6             ; if not frame 0, stay harmless
     *   move.b #$84,obColType(a0)      ; frame 0 = harmful
     * </pre>
     */
    private void updateSpikeFrames() {
        for (int i = 0; i < spikeCount; i++) {
            int frame = (animCounter + spikePhase[i]) & 0x07;
            spikeFrame[i] = frame;
            spikeHarmful[i] = (frame == 0);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.SPIKED_POLE_HELIX);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Render each spike at its position with its current frame
        for (int i = 0; i < spikeCount; i++) {
            int frame = spikeFrame[i];
            // Frame 6 is the invisible hack (empty mapping) — renderer handles 0-piece frames
            renderer.drawFrameIndex(frame, spikeX[i], spikeY, false, false);
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(DISPLAY_PRIORITY);
    }

    // ---- TouchResponseProvider ----
    // The parent spike reports its own collision state. The engine's touch response system
    // processes each provider once per frame. For multi-spike collision, each spike that
    // is harmful needs to participate. Since we render all spikes from one object instance,
    // we override getMultiTouchRegions() to report all harmful spike positions.

    @Override
    public int getCollisionFlags() {
        // The parent spike's collision state
        return spikeHarmful[parentIndex] ? COLLISION_TYPE_HARMFUL : 0;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    /**
     * Returns touch collision regions for all currently harmful spikes.
     * Each harmful spike (frame 0) reports its position with collision type $84.
     * This allows the engine to check collision against every spike independently.
     */
    @Override
    public TouchRegion[] getMultiTouchRegions() {
        int harmfulCount = 0;
        for (int i = 0; i < spikeCount; i++) {
            if (spikeHarmful[i]) {
                harmfulCount++;
            }
        }
        if (harmfulCount == 0) {
            return null;
        }

        TouchRegion[] regions = new TouchRegion[harmfulCount];
        int idx = 0;
        for (int i = 0; i < spikeCount; i++) {
            if (spikeHarmful[i]) {
                regions[idx++] = new TouchRegion(spikeX[i], spikeY, COLLISION_TYPE_HARMFUL);
            }
        }
        return regions;
    }

    // ---- Persistence ----

    @Override
    public boolean isPersistent() {
        // Disasm: Hel_ChkDel: out_of_range.w Hel_DelAll
        // Uses the parent's (spawn) X position for range check
        return !isDestroyed() && isBaseXOnScreen();
    }

    private boolean isBaseXOnScreen() {
        var camera = Camera.getInstance();
        if (camera == null) {
            return true;
        }
        int objRounded = spawn.x() & 0xFF80;
        int camRounded = (camera.getX() - 128) & 0xFF80;
        int distance = (objRounded - camRounded) & 0xFFFF;
        return distance <= (128 + 320 + 192);
    }

    // ---- Debug rendering ----

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        for (int i = 0; i < spikeCount; i++) {
            float r = spikeHarmful[i] ? 1.0f : 0.0f;
            float g = spikeHarmful[i] ? 0.0f : 1.0f;
            // Draw small cross at each spike position
            ctx.drawLine(spikeX[i] - 4, spikeY, spikeX[i] + 4, spikeY, r, g, 0.0f);
            ctx.drawLine(spikeX[i], spikeY - 4, spikeX[i], spikeY + 4, r, g, 0.0f);
        }
    }


}
