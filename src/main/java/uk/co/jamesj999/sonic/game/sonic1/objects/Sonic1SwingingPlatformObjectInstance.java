package uk.co.jamesj999.sonic.game.sonic1.objects;

import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.debug.DebugOverlayManager;
import uk.co.jamesj999.sonic.debug.DebugOverlayToggle;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.game.sonic2.OscillationManager;
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
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

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

    // ROM-accurate 256-entry sine table (from CalcSine lookup, values -256 to +256)
    private static final short[] SINE_TABLE = {
            0, 6, 12, 18, 25, 31, 37, 43, 49, 56, 62, 68, 74, 80, 86, 92,
            97, 103, 109, 115, 120, 126, 131, 136, 142, 147, 152, 157, 162, 167, 171, 176,
            181, 185, 189, 193, 197, 201, 205, 209, 212, 216, 219, 222, 225, 228, 231, 234,
            236, 238, 241, 243, 244, 246, 248, 249, 251, 252, 253, 254, 254, 255, 255, 255,
            256, 255, 255, 255, 254, 254, 253, 252, 251, 249, 248, 246, 244, 243, 241, 238,
            236, 234, 231, 228, 225, 222, 219, 216, 212, 209, 205, 201, 197, 193, 189, 185,
            181, 176, 171, 167, 162, 157, 152, 147, 142, 136, 131, 126, 120, 115, 109, 103,
            97, 92, 86, 80, 74, 68, 62, 56, 49, 43, 37, 31, 25, 18, 12, 6,
            0, -6, -12, -18, -25, -31, -37, -43, -49, -56, -62, -68, -74, -80, -86, -92,
            -97, -103, -109, -115, -120, -126, -131, -136, -142, -147, -152, -157, -162, -167, -171, -176,
            -181, -185, -189, -193, -197, -201, -205, -209, -212, -216, -219, -222, -225, -228, -231, -234,
            -236, -238, -241, -243, -244, -246, -248, -249, -251, -252, -253, -254, -254, -255, -255, -255,
            -256, -255, -255, -255, -254, -254, -253, -252, -251, -249, -248, -246, -244, -243, -241, -238,
            -236, -234, -231, -228, -225, -222, -219, -216, -212, -209, -205, -201, -197, -193, -189, -185,
            -181, -176, -171, -167, -162, -157, -152, -147, -142, -136, -131, -126, -120, -115, -109, -103,
            -97, -92, -86, -80, -74, -68, -62, -56, -49, -43, -37, -31, -25, -18, -12, -6
    };

    // Oscillation data offset: v_oscillate+$1A → data offset 0x18
    private static final int OSC_OFFSET = 0x18;

    // Zone variant types
    private enum ZoneVariant {
        GHZ_MZ,       // Standard platform — GHZ and MZ
        SLZ,          // Larger platform — Star Light Zone
        SBZ,          // Spiked ball — Scrap Brain Zone
        GIANT_BALL    // Giant ball — GHZ subtype $1X
    }

    // Debug state
    private static final boolean DEBUG_VIEW_ENABLED = SonicConfigurationService.getInstance()
            .getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
    private static final DebugOverlayManager OVERLAY_MANAGER = GameServices.debugOverlay();

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

    // Dynamic spawn for position tracking
    private ObjectSpawn dynamicSpawn;

    public Sonic1SwingingPlatformObjectInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, "SwingingPlatform");

        int zoneIndex = levelManager.getCurrentZone();
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
        updatePositions();
        refreshDynamicSpawn();
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
        int sin = SINE_TABLE[d0 & 0xFF];
        int cos = SINE_TABLE[(d0 + 0x40) & 0xFF];

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
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Draw debug overlay
        if (isDebugViewEnabled()) {
            appendDebug(commands);
        }

        // Render anchor at pivot point (frame 2)
        renderer.drawFrameIndex(2, baseX, baseY, false, false);

        // Render chain links from anchor toward platform
        // Iterate in reverse so links closer to anchor render first (behind)
        for (int i = chainDistances.length - 1; i >= 0; i--) {
            renderer.drawFrameIndex(linkFrame[i], linkX[i], linkY[i], false, false);
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

    // ---- Debug rendering ----

    private void appendDebug(List<GLCommand> commands) {
        // Draw pivot point (yellow cross)
        appendLine(commands, baseX - 4, baseY, baseX + 4, baseY, 1.0f, 1.0f, 0.0f);
        appendLine(commands, baseX, baseY - 4, baseX, baseY + 4, 1.0f, 1.0f, 0.0f);

        // Draw chain link positions (cyan)
        for (int i = 0; i < chainDistances.length; i++) {
            appendLine(commands, linkX[i] - 2, linkY[i], linkX[i] + 2, linkY[i], 0.0f, 1.0f, 1.0f);
            appendLine(commands, linkX[i], linkY[i] - 2, linkX[i], linkY[i] + 2, 0.0f, 1.0f, 1.0f);
        }

        // Draw collision box (green for solid platforms, red for harmful objects)
        float r = isSolid ? 0.0f : 1.0f;
        float g = isSolid ? 1.0f : 0.0f;
        int left = x - halfWidth;
        int right = x + halfWidth;
        int top = y - halfHeight;
        int bottom = y + halfHeight;
        appendLine(commands, left, top, right, top, r, g, 0.0f);
        appendLine(commands, right, top, right, bottom, r, g, 0.0f);
        appendLine(commands, right, bottom, left, bottom, r, g, 0.0f);
        appendLine(commands, left, bottom, left, top, r, g, 0.0f);

        // Draw platform center (red cross)
        appendLine(commands, x - 4, y, x + 4, y, 1.0f, 0.0f, 0.0f);
        appendLine(commands, x, y - 4, x, y + 4, 1.0f, 0.0f, 0.0f);
    }

    private void appendLine(List<GLCommand> commands, int x1, int y1, int x2, int y2,
                            float r, float g, float b) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x2, y2, 0, 0));
    }

    private boolean isDebugViewEnabled() {
        return DEBUG_VIEW_ENABLED && OVERLAY_MANAGER.isEnabled(DebugOverlayToggle.OVERLAY);
    }
}
