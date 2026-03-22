package com.openggf.game.sonic1.objects;
import com.openggf.game.GameServices;

import com.openggf.camera.Camera;
import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.OscillationManager;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Object 0x15 — Swinging Platforms (GHZ, MZ, SLZ) / Spiked Ball on Chain (SBZ).
 * <p>
 * A platform (or ball) that swings from a fixed pivot point on a chain.
 * Uses the global oscillation table (v_oscillate+$1A) for pendulum motion.
 * Chain links are rendered at intermediate positions along the arc.
 * <p>
 * <b>Zone variants:</b>
 * <ul>
 *   <li>GHZ/MZ: Solid platform, top-solid only, palette 2</li>
 *   <li>SLZ: Larger solid platform ($20 wide, $10 tall), obColType $99</li>
 *   <li>SBZ: Spiked ball (hurts player), obColType $86, NOT solid</li>
 *   <li>GHZ subtype $1X: Giant ball variant (hurts player), obColType $81</li>
 * </ul>
 * <p>
 * <b>Subtype encoding:</b>
 * <ul>
 *   <li>Bits 0-3: Chain length (number of chain segments)</li>
 *   <li>Bit 4: Giant ball variant flag (GHZ only)</li>
 * </ul>
 * <p>
 * <b>Disassembly reference:</b> docs/s1disasm/_incObj/15 Swinging Platforms (part 1).asm,
 * (part 2).asm
 */
public class Sonic1SwingingPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener, TouchResponseProvider {

    // Oscillation data offset: v_oscillate+$1A → data offset 0x18
    private static final int OSC_OFFSET = 0x18;

    // Zone variant types
    private enum ZoneVariant {
        GHZ_MZ,       // Standard platform — GHZ and MZ
        SLZ,          // Larger platform — Star Light Zone
        SBZ,          // Spiked ball — Scrap Brain Zone
        GIANT_BALL    // Giant ball — GHZ subtype $1X
    }

    // Position state (anchor/pivot point)
    private final int baseX;  // swing_origX = objoff_3A
    private final int baseY;  // swing_origY = objoff_38

    // Current platform position (end of chain)
    private int x;
    private int y;

    // Zone variant and configuration
    private final ZoneVariant variant;
    private final int chainCount;     // Number of chain segments (bits 0-3 of subtype)
    private final int halfWidth;      // obActWid
    private final int halfHeight;     // obHeight
    private final boolean isSolid;    // Whether this variant is top-solid
    private final int priority;       // obPriority

    // Chain link positions (one entry per chain link, including platform at end)
    // Index 0..chainCount-1 = chain links from anchor down, last entry = platform itself
    private final int[] chainDistances;
    private final int[] linkX;
    private final int[] linkY;
    // Frame index per link: 0=platform, 1=chain, 2=anchor
    private final int[] linkFrame;

    // Art key for this variant
    private final String artKey;

    // obColType for touch collision (SBZ=$86, Giant Ball=$81, 0=no touch collision)
    private final int collisionType;

    public Sonic1SwingingPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SwingingPlatform");

        int zoneIndex = GameServices.level().getRomZoneId();
        int subtype = spawn.subtype() & 0xFF;
        this.chainCount = subtype & 0x0F;
        boolean isGiantBall = (subtype & 0x10) != 0;

        this.baseX = spawn.x();
        this.baseY = spawn.y();

        // Determine zone variant
        // Disasm: cmpi.b #id_SBZ,(v_zone).w
        if (zoneIndex == Sonic1Constants.ZONE_SBZ) {
            this.variant = ZoneVariant.SBZ;
            this.halfWidth = 0x18;
            this.halfHeight = 0x18;
            this.isSolid = false;
            this.priority = 3;
            this.artKey = ObjectArtKeys.SWING_SBZ_BALL;
            // Disasm: move.b #$86,obColType(a0) — HURT ($80) + size 6
            this.collisionType = 0x86;
        } else if (zoneIndex == Sonic1Constants.ZONE_SLZ) {
            this.variant = ZoneVariant.SLZ;
            this.halfWidth = 0x20;
            this.halfHeight = 0x10;
            this.isSolid = true;
            this.priority = 3;
            this.artKey = ObjectArtKeys.SWING_SLZ;
            this.collisionType = 0; // Solid platform, no touch collision
        } else if (isGiantBall) {
            // Subtype $1X: Giant ball (GHZ only)
            // Disasm: btst #4,d1 / move.l #Map_GBall,obMap(a0)
            this.variant = ZoneVariant.GIANT_BALL;
            this.halfWidth = 0x18;
            this.halfHeight = 0x18;
            this.isSolid = false;
            this.priority = 2;
            this.artKey = ObjectArtKeys.SWING_GIANT_BALL;
            // Disasm: move.b #$81,obColType(a0) — HURT ($80) + size 1
            this.collisionType = 0x81;
        } else {
            // Default: GHZ/MZ platform
            this.variant = ZoneVariant.GHZ_MZ;
            this.halfWidth = 0x18;
            this.halfHeight = 8;
            this.isSolid = true;
            this.priority = 3;
            this.artKey = ObjectArtKeys.SWING_GHZ;
            this.collisionType = 0; // Solid platform, no touch collision
        }

        // Build chain with distances from anchor.
        // Disasm: lsl.w #4,d3 / addq.b #8,d3 → distance = (chainCount * 16) + 8 for platform
        // Chain links spaced 16 pixels apart: link[i] distance = (chainCount - i) * 16
        // Platform is at the end: distance = chainCount * 16 + 8
        // Anchor is at distance 0 (the pivot point)
        //
        // The disasm creates children starting from the platform end moving toward anchor:
        //   d3 starts at (chainCount * 16) + 8, then subtracts 16 per link.
        //   When d3 goes below 0, the link becomes the anchor (frame 2).
        //
        // Total items in chain = chainCount + 2 (anchor + chain links + platform)
        // But the platform position is this object itself.
        // We track: anchor (1) + chain links (chainCount-1) + platform (this object).
        // Actually from the disasm, the chain has chainCount links BETWEEN anchor and platform.

        int totalLinks = chainCount + 1; // +1 for anchor, chainCount includes the chain links
        // The platform itself is this object, tracked by x/y.
        // We store positions for all children: anchor + intermediate chain links.
        // From disasm: obSubtype stores count of children, each child has objoff_3C = distance.
        // Platform distance: (chainCount << 4) + 8
        // First chain link: same minus 16, etc.
        // Last link (closest to anchor): when d3 < 0 → becomes anchor (frame 2)

        // Build the chain distances and frame assignments
        // Start from platform end, work toward anchor
        int platformDistance = (chainCount << 4) + 8;
        int numChildren = chainCount; // Number of child objects created by the disasm loop

        // The disasm loop creates numChildren child links.
        // If obFrame is initially 0 (not giant ball): starts at platformDistance, decrements by 16
        // If obFrame is initially 1 (giant ball): starts at platformDistance+8, numChildren-1 times
        // For giant ball: addq.b #8,d3 / subq.w #1,d1 before makechain loop

        int startDist;
        int childCount;
        if (variant == ZoneVariant.GIANT_BALL) {
            // Giant ball: d3 = (chainCount<<4)+8+8 = (chainCount<<4)+16
            startDist = platformDistance + 8;
            childCount = Math.max(0, chainCount - 1);
        } else {
            startDist = platformDistance;
            childCount = chainCount;
        }

        // Allocate arrays for all elements we need to render:
        // anchor (at pivot) + child chain links + platform (at end)
        // anchor is always rendered at baseX, baseY with frame 2
        // chain links are rendered at computed positions with frame 1
        // platform is rendered at x, y with frame 0

        // Store child link data (chain segments between anchor and platform)
        chainDistances = new int[childCount];
        linkX = new int[childCount];
        linkY = new int[childCount];
        linkFrame = new int[childCount];

        int d3 = startDist;
        for (int i = 0; i < childCount; i++) {
            d3 -= 0x10;
            chainDistances[i] = d3;
            // Disasm: subi.b #$10,d3 / bcc.s .notanchor
            // If d3 < 0 (borrow), this is the anchor piece
            if (d3 < 0) {
                linkFrame[i] = 2; // Anchor frame
            } else {
                linkFrame[i] = 1; // Chain link frame
            }
        }

        // Calculate initial positions
        this.x = baseX;
        this.y = baseY;
        updatePositions();
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
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }
        updatePositions();
        updateDynamicSpawn(x, y);
    }

    /**
     * Calculates positions for all chain elements using oscillation-driven pendulum physics.
     * <p>
     * From disasm Swing_Move:
     * <pre>
     *   move.b (v_oscillate+$1A).w,d0
     *   move.w #$80,d1
     *   btst   #0,obStatus(a0)
     *   beq.s  loc_7B78
     *   neg.w  d0
     *   add.w  d1,d0
     * loc_7B78:
     *   bra.s  Swing_Move2
     * </pre>
     * Then Swing_Move2 calls CalcSine and positions each chain element.
     */
    private void updatePositions() {
        // Read oscillation value: move.b (v_oscillate+$1A).w,d0
        int oscByte = OscillationManager.getByte(OSC_OFFSET);

        // move.w #$80,d1
        // btst #0,obStatus(a0) — check render flip bit from spawn
        // If obStatus bit 0 is set: neg.w d0 / add.w d1,d0 → d0 = $80 - d0
        boolean reversed = (spawn.renderFlags() & 0x01) != 0;
        int d0;
        if (reversed) {
            d0 = (-oscByte + 0x80) & 0xFF;
        } else {
            d0 = oscByte & 0xFF;
        }

        // CalcSine: d0 = angle → d0 = sine, d1 = cosine
        int sin = TrigLookupTable.sinHex(d0);
        int cos = TrigLookupTable.cosHex(d0);

        // swing_origY = objoff_38, swing_origX = objoff_3A
        int origY = baseY;
        int origX = baseX;

        // Position platform (this object) at its distance from anchor
        int platDist = (chainCount << 4) + 8;
        if (variant == ZoneVariant.GIANT_BALL) {
            platDist += 8;
        }
        // Disasm: muls.w d0,d4 / asr.l #8,d4 → yOffset = (sin * distance) >> 8
        // Disasm: muls.w d1,d5 / asr.l #8,d5 → xOffset = (cos * distance) >> 8
        // move.w d4,obY(a1) — Y = origY + yOffset
        // move.w d5,obX(a1) — X = origX + xOffset
        x = origX + ((cos * platDist) >> 8);
        y = origY + ((sin * platDist) >> 8);

        // Position chain links
        for (int i = 0; i < chainDistances.length; i++) {
            int dist = chainDistances[i];
            // For anchor frame (dist < 0), it stays near the pivot but still uses the formula
            linkX[i] = origX + ((cos * dist) >> 8);
            linkY[i] = origY + ((sin * dist) >> 8);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(artKey);
        if (renderer == null) return;

        // Render anchor at pivot point (frame 2)
        renderer.drawFrameIndex(2, baseX, baseY, false, false);

        // Render chain links from anchor toward platform
        // Iterate in reverse so links closer to anchor render first (behind)
        // Disasm: bclr #6,obGfx(a1) clears palette bit 14 for all children (palette 0);
        //         bset #6,obGfx(a1) restores it only for the anchor (palette 2).
        for (int i = chainDistances.length - 1; i >= 0; i--) {
            int palOverride = (linkFrame[i] == 1) ? 0 : -1;
            renderer.drawFrameIndex(linkFrame[i], linkX[i], linkY[i], false, false, palOverride);
        }

        // Render platform/ball at end of chain (frame 0)
        // Giant ball: frame 1 (set by disasm: move.b #1,obFrame(a0))
        int platformFrame = (variant == ZoneVariant.GIANT_BALL) ? 1 : 0;
        renderer.drawFrameIndex(platformFrame, x, y, false, false);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(priority);
    }

    // ---- SolidObjectProvider ----

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(halfWidth, halfHeight, halfHeight);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        // SBZ spiked ball and GHZ giant ball are NOT solid (they hurt on touch)
        return isSolid && !isDestroyed();
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // Standing state managed by ObjectManager
    }

    // ---- TouchResponseProvider (SBZ spiked ball and GHZ giant ball hurt the player) ----

    @Override
    public int getCollisionFlags() {
        return collisionType;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    // ---- Persistence ----

    @Override
    public boolean isPersistent() {
        // out_of_range uses objoff_3A (spawn X), not current X
        // Disasm: Swing_ChkDel: out_of_range.w Swing_DelAll,objoff_3A(a0)
        return !isDestroyed() && isBaseXOnScreen();
    }

    private boolean isBaseXOnScreen() {
        int objectX = baseX;
        var camera = GameServices.camera();
        if (camera == null) {
            return true;
        }
        int objRounded = objectX & 0xFF80;
        int camRounded = (camera.getX() - 128) & 0xFF80;
        int distance = (objRounded - camRounded) & 0xFFFF;
        return distance <= (128 + 320 + 192);
    }

    // ---- Debug rendering ----

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Draw pivot point (yellow cross)
        ctx.drawLine(baseX - 4, baseY, baseX + 4, baseY, 1.0f, 1.0f, 0.0f);
        ctx.drawLine(baseX, baseY - 4, baseX, baseY + 4, 1.0f, 1.0f, 0.0f);

        // Draw chain link positions (cyan)
        for (int i = 0; i < chainDistances.length; i++) {
            ctx.drawLine(linkX[i] - 2, linkY[i], linkX[i] + 2, linkY[i], 0.0f, 1.0f, 1.0f);
            ctx.drawLine(linkX[i], linkY[i] - 2, linkX[i], linkY[i] + 2, 0.0f, 1.0f, 1.0f);
        }

        // Draw collision box (green for solid platforms, red for harmful objects)
        float r = isSolid ? 0.0f : 1.0f;
        float g = isSolid ? 1.0f : 0.0f;
        int left = x - halfWidth;
        int right = x + halfWidth;
        int top = y - halfHeight;
        int bottom = y + halfHeight;
        ctx.drawLine(left, top, right, top, r, g, 0.0f);
        ctx.drawLine(right, top, right, bottom, r, g, 0.0f);
        ctx.drawLine(right, bottom, left, bottom, r, g, 0.0f);
        ctx.drawLine(left, bottom, left, top, r, g, 0.0f);

        // Draw platform center (red cross)
        ctx.drawLine(x - 4, y, x + 4, y, 1.0f, 0.0f, 0.0f);
        ctx.drawLine(x, y - 4, x, y + 4, 1.0f, 0.0f, 0.0f);
    }


}
