package com.openggf.game.sonic3k.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Object 0x53 - MGZ Swinging Platform.
 *
 * <p>ROM: Obj_MGZSwingingPlatform (sonic3k.asm:70459-70558).
 * A platform on a chain of 4 links that rotates continuously around its pivot
 * at a constant angular velocity of 1 angle unit per frame. The player can
 * stand on the platform piece at the end of the chain (SolidObjectTop).
 *
 * <p>Subtype = initial angle byte (0-255).
 * Status bit 0 (x-flip) reverses rotation direction.
 * Status bit 1 (y-flip) selects chain link visual (frame 0 vs frame 1).
 */
public class MGZSwingingPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final String ART_KEY = Sonic3kObjectArtKeys.MGZ_SWINGING_PLATFORM;

    // ROM: move.w #$200,priority(a0)
    private static final int PRIORITY_BUCKET = 4;

    // Mapping frames from Map_MGZSwingingPlatform
    private static final int FRAME_LINK = 0;     // Chain link (child mapframe default = 0)
    private static final int FRAME_PIVOT = 1;    // Pivot/anchor piece (child mapping_frame default)
    private static final int FRAME_PLATFORM = 2; // Platform piece (2 mirrored 24x24 halves)

    // ROM: mainspr_childsprites = 4
    private static final int LINK_COUNT = 4;

    // ROM: move.b #$18,width_pixels(a0)
    private static final int SOLID_HALF_WIDTH = 0x18;
    // ROM: move.b height_pixels(a0),d3; addq.w #1,d3 -> $0C + 1 = $0D
    private static final int SOLID_HALF_HEIGHT = 0x0D;

    private final int pivotX;
    private final int pivotY;
    private final int pivotFrame; // ROM: child mapping_frame (1 default, 0 if y-flip)
    private final int angleStep;  // +1 or -1

    private final int[] linkX = new int[LINK_COUNT];
    private final int[] linkY = new int[LINK_COUNT];

    private int platformX;
    private int platformY;
    private int angleByte;

    public MGZSwingingPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "MGZSwingingPlatform");
        this.pivotX = spawn.x();
        this.pivotY = spawn.y();

        // ROM: move.b subtype(a0),$34(a0) -- initial angle
        this.angleByte = spawn.subtype() & 0xFF;

        // ROM: btst #0,status(a0); neg.b $36(a0)
        boolean xFlip = (spawn.renderFlags() & 0x01) != 0;
        this.angleStep = xFlip ? -1 : 1;

        // ROM: status bit 1 selects pivot frame (set = frame 0, clear = frame 1).
        // Chain links always use frame 0 (zeroed mapframe from AllocateObjectAfterCurrent).
        boolean yFlip = (spawn.renderFlags() & 0x02) != 0;
        this.pivotFrame = yFlip ? FRAME_LINK : FRAME_PIVOT;

        updateChainPositions();
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (isDestroyed()) {
            return;
        }

        updateChainPositions();

        // ROM: move.b $36(a0),d0; add.b d0,$34(a0) -- constant angular velocity
        angleByte = (angleByte + angleStep) & 0xFF;
    }

    /**
     * ROM: sub_34074 -- computes chain link positions and platform endpoint.
     *
     * <p>GetSineCosine returns sin/cos for the current angle, each scaled to
     * 16.16 fixed-point by {@code swap; asr.l #4}. The accumulator starts at
     * the pivot and adds one step per link, with the platform at the 5th step.
     */
    private void updateChainPositions() {
        // ROM: GetSineCosine -> d0=sin, d1=cos; swap; asr.l #4
        int sinStep = TrigLookupTable.sinHex(angleByte) << 12;
        int cosStep = TrigLookupTable.cosHex(angleByte) << 12;

        // ROM: d3 = pivotX << 16, d2 = pivotY << 16
        int accumX = pivotX << 16;
        int accumY = pivotY << 16;

        // ROM: loop over mainspr_childsprites, accumulating and writing positions
        for (int i = 0; i < LINK_COUNT; i++) {
            accumX += cosStep;
            accumY += sinStep;
            linkX[i] = accumX >> 16;
            linkY[i] = accumY >> 16;
        }

        // ROM: one more step for the platform position (x_pos/y_pos)
        accumX += cosStep;
        accumY += sinStep;
        platformX = accumX >> 16;
        platformY = accumY >> 16;
    }

    // ===== SolidObjectProvider (SolidObjectTop) =====

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, SOLID_HALF_HEIGHT);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public int getTopSolidPlayerPositionHistoryFrames(PlayableEntity player) {
        // Obj_MGZSwingingPlatform updates the endpoint and immediately calls
        // SolidObjectTop (sonic3k.asm:70501-70513). SolidObjectTop's new-landing
        // path reads the player's position/radius before RideObject_SetRide
        // (sonic3k.asm:41982-42015). For the airborne rolling state established
        // by Player_DoRoll/Sonic_Jump (sonic3k.asm:23259-23264,
        // 23335-23342) and cleared by Player_TouchFloor
        // (sonic3k.asm:24341-24368), this object samples the pre-control player
        // position; non-rolling fall landings use the current position.
        return player != null
                && !player.isCpuControlled()
                && player.getAir()
                && player.getRolling() ? 1 : 0;
    }

    // ===== SolidObjectListener =====

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        // Platform movement is tracked via position delta by the SolidContacts system.
    }

    // ===== Rendering =====

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(ART_KEY);
        if (renderer == null) {
            return;
        }

        // Draw pivot and chain links first (behind platform, ROM child priority $280)
        renderer.drawFrameIndex(pivotFrame, pivotX, pivotY, false, false);
        for (int i = 0; i < LINK_COUNT; i++) {
            renderer.drawFrameIndex(FRAME_LINK, linkX[i], linkY[i], false, false);
        }

        // Draw platform last (in front, ROM parent priority $200)
        renderer.drawFrameIndex(FRAME_PLATFORM, platformX, platformY, false, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        if (ctx == null) {
            return;
        }
        // Pivot marker
        ctx.drawCross(pivotX, pivotY, 4, 1.0f, 1.0f, 0.0f);

        // Chain link connections
        int prevX = pivotX;
        int prevY = pivotY;
        for (int i = 0; i < LINK_COUNT; i++) {
            ctx.drawLine(prevX, prevY, linkX[i], linkY[i], 0.6f, 0.6f, 0.6f);
            prevX = linkX[i];
            prevY = linkY[i];
        }
        ctx.drawLine(prevX, prevY, platformX, platformY, 0.3f, 1.0f, 0.3f);

        // Platform collision box
        ctx.drawRect(platformX, platformY, SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT,
                0.3f, 1.0f, 0.3f);
    }

    // ===== Position (platform endpoint, not pivot) =====

    @Override
    public int getX() {
        return platformX;
    }

    @Override
    public int getY() {
        return platformY;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY_BUCKET);
    }
}
