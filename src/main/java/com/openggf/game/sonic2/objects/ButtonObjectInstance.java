package com.openggf.game.sonic2.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic2.ButtonVineTriggerManager;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.constants.Sonic2AudioConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import com.openggf.debug.DebugColor;
import java.util.List;

/**
 * Button (Object 0x47) - Trigger button used in MTZ.
 * <p>
 * A floor-mounted button that activates when a player stands on it.
 * Sets a bit in the ButtonVine_Trigger array, which other objects
 * (MTZLongPlatform, MCZDrawbridge, MCZBridge, etc.) monitor.
 * <p>
 * ROM reference: s2.asm Obj47 (lines 50391-50452)
 * <ul>
 *   <li>Solid collision: width=$1B(27), air_half=4, ground_half=5</li>
 *   <li>Art: ArtNem_Button, palette line 0</li>
 *   <li>Frame 0: unpressed, Frame 1: pressed</li>
 *   <li>Sound: SndID_Blip ($CD) on press transition</li>
 *   <li>Init: y_pos += 4 (addq.w #4,y_pos)</li>
 * </ul>
 * <p>
 * Subtype encoding:
 * <ul>
 *   <li>Bits 0-3 (0x0F): Switch ID for ButtonVine_Trigger (0-15)</li>
 *   <li>Bit 6 (0x40): Use bit 7 instead of bit 0 in the trigger byte</li>
 * </ul>
 */
public class ButtonObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // ROM: move.w #$1B,d1 - solid object half-width
    private static final int HALF_WIDTH = 0x1B;

    // ROM: move.w #4,d2 - air half-height for SolidObject
    private static final int AIR_HALF_HEIGHT = 4;

    // ROM: move.w #5,d3 - ground half-height for SolidObject
    private static final int GROUND_HALF_HEIGHT = 5;

    // ROM: move.b #$10,width_pixels(a0) - sprite width for on-screen check
    private static final int WIDTH_PIXELS = 0x10;

    // ROM: move.b #4,priority(a0)
    private static final int PRIORITY = 4;

    // ROM: addq.w #4,y_pos(a0) - Y offset applied during init
    private static final int INIT_Y_OFFSET = 4;

    // Frame indices from Map_obj47
    private static final int FRAME_UNPRESSED = 0;
    private static final int FRAME_PRESSED = 1;

    // Subtype-derived state
    private final int switchId;    // subtype & 0x0F: index into ButtonVine_Trigger array
    private final int triggerBit;  // 0 or 7: which bit to set/clear in the trigger byte

    // Adjusted Y position (after init offset)
    private final int adjustedY;

    // Standing detection via SolidObjectListener callback
    private int lastStandingFrame = -2;

    // Current mapping frame (0=unpressed, 1=pressed)
    private int mappingFrame = FRAME_UNPRESSED;

    // Solid collision params (constant)
    private static final SolidObjectParams SOLID_PARAMS =
            new SolidObjectParams(HALF_WIDTH, AIR_HALF_HEIGHT, GROUND_HALF_HEIGHT);

    public ButtonObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Button");

        // ROM: andi.w #$F,d0 - extract switch ID from lower nibble
        this.switchId = spawn.subtype() & 0x0F;

        // ROM: btst #6,subtype(a0) / beq.s + / moveq #7,d3
        this.triggerBit = ((spawn.subtype() & 0x40) != 0) ? 7 : 0;

        // ROM: addq.w #4,y_pos(a0)
        this.adjustedY = spawn.y() + INIT_Y_OFFSET;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // ROM: move.b #0,mapping_frame(a0) - reset to unpressed each frame
        mappingFrame = FRAME_UNPRESSED;

        // Determine if any player is currently standing on this button.
        // ROM: move.b status(a0),d0 / andi.b #standing_mask,d0 / bne.s +
        // The onSolidContact callback sets lastStandingFrame when contact.standing() is true.
        // We use the (frameCounter - lastStandingFrame <= 1) pattern because update()
        // runs before collision resolution for the current frame.
        boolean standing = (frameCounter - lastStandingFrame) <= 1;

        if (!standing) {
            // ROM: bclr d3,(a3) - clear the trigger bit when nobody is standing
            ButtonVineTriggerManager.setBit(switchId, triggerBit, false);
        } else {
            // ROM: tst.b (a3) / bne.s + / move.w #SndID_Blip,d0 / jsr (PlaySound).l
            // Sound plays only when entire trigger byte is zero (no bits set by any source)
            if (!ButtonVineTriggerManager.testAny(switchId)) {
                services().playSfx(Sonic2AudioConstants.SFX_BLIP);
            }

            // ROM: bset d3,(a3) - set the trigger bit
            ButtonVineTriggerManager.setBit(switchId, triggerBit, true);

            // ROM: move.b #1,mapping_frame(a0) - show pressed frame
            mappingFrame = FRAME_PRESSED;
        }
    }

    @Override
    public int getY() {
        return adjustedY;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    // ========================================================================================
    // SolidObjectProvider
    // ========================================================================================

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    // ========================================================================================
    // SolidObjectListener
    // ========================================================================================

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (contact.standing()) {
            lastStandingFrame = frameCounter;
        }
    }

    // ========================================================================================
    // Rendering
    // ========================================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.BUTTON);
        if (renderer != null) {
            renderer.drawFrameIndex(mappingFrame, spawn.x(), adjustedY, false, false);
        }
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        int x = spawn.x();

        // Yellow rectangle for solid collision area
        ctx.drawRect(x, adjustedY, HALF_WIDTH, AIR_HALF_HEIGHT, 0.9f, 0.9f, 0.2f);

        // Cross at spawn position (before Y offset)
        ctx.drawCross(x, spawn.y(), 3, 0.5f, 0.5f, 0.5f);

        // State label
        boolean pressed = mappingFrame == FRAME_PRESSED;
        String label = String.format("Btn sw=%d bit=%d %s",
                switchId, triggerBit, pressed ? "ON" : "off");
        ctx.drawWorldLabel(x, adjustedY, -2, label,
                pressed ? DebugColor.GREEN : DebugColor.YELLOW);
    }
}
