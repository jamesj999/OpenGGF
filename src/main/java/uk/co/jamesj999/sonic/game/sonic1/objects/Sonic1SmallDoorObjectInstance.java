package uk.co.jamesj999.sonic.game.sonic1.objects;

import uk.co.jamesj999.sonic.debug.DebugRenderContext;
import uk.co.jamesj999.sonic.graphics.GLCommand;
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

import java.awt.Color;
import java.util.List;

/**
 * Sonic 1 Object 0x2A - Small Vertical Door (SBZ).
 * <p>
 * An automatic door that opens when Sonic approaches from the correct side
 * and closes when he moves away. The door is solid when closed (frame 0),
 * blocking Sonic's path.
 * <p>
 * The approach direction is controlled by {@code obStatus} bit 0, which
 * comes from the spawn render flags x-flip bit:
 * <ul>
 *   <li>bit 0 = 0: door opens when Sonic approaches from the LEFT</li>
 *   <li>bit 0 = 1: door opens when Sonic approaches from the RIGHT</li>
 * </ul>
 * <p>
 * Detection range: Sonic must be within $40 (64) pixels horizontally
 * of the door's X position.
 * <p>
 * Animation: 9 mapping frames (0=closed, 8=fully open). The two door
 * halves slide apart vertically. Frame delay is 0 (every frame).
 * <p>
 * SolidObject params (when closed): d1=$11, d2=$20, d3=$21.
 * <p>
 * ROM reference: docs/s1disasm/_incObj/2A SBZ Small Door.asm
 */
public class Sonic1SmallDoorObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // Detection range for door opening: move.w #$40,d1
    private static final int DETECTION_RANGE = 0x40;

    // obActWid from ROM: move.b #8,obActWid(a0)
    private static final int ACT_WIDTH = 8;

    // SolidObject parameters when door is closed (frame 0):
    // move.w #$11,d1  ; half-width
    // move.w #$20,d2  ; air half-height (top)
    // move.w d2,d3 / addq.w #1,d3  ; ground half-height (bottom) = $21
    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x11, 0x20, 0x21);

    // Ani_ADoor animation 0 (close): dc.b 0, 8, 7, 6, 5, 4, 3, 2, 1, 0, afBack, 1
    // Frame delay 0, descending frames, holds on frame 0.
    private static final int[] CLOSE_SEQUENCE = {8, 7, 6, 5, 4, 3, 2, 1, 0};

    // Ani_ADoor animation 1 (open): dc.b 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, afBack, 1
    // Frame delay 0, ascending frames, holds on frame 8.
    private static final int[] OPEN_SEQUENCE = {0, 1, 2, 3, 4, 5, 6, 7, 8};

    // Frame delay from animation script: dc.b 0 (every-frame update)
    private static final int FRAME_DELAY = 0;

    // obStatus bit 0: determines which side the door opens from.
    // Set from spawn renderFlags x-flip bit.
    private final boolean openFromRight;

    // Animation state
    private int animationId;         // 0 = closing, 1 = opening
    private int prevAnimationId;     // Tracks previous animation for change detection (AnimateSprite behavior)
    private int animationFrameIndex;
    private int animationTimer;
    private int mappingFrame;

    // Whether the door is currently acting as a solid object
    private boolean solidActive;

    private static final Color DEBUG_COLOR = new Color(200, 140, 60);

    public Sonic1SmallDoorObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SmallDoor");
        // obStatus bit 0 comes from spawn renderFlags bit 0 (x-flip)
        this.openFromRight = (spawn.renderFlags() & 0x1) != 0;
        this.animationId = 0;
        this.prevAnimationId = -1; // Force initial reset on first frame
        this.animationFrameIndex = 0;
        this.animationTimer = 0;
        this.mappingFrame = 0;
        this.solidActive = false;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // ADoor_OpenShut logic:
        // clr.b obAnim(a0)  ; default to closing animation (0)
        int newAnimId = 0;

        if (player != null) {
            int playerX = player.getCentreX();
            int doorX = getX();

            // Check if Sonic is within detection range ($40 pixels)
            // move.w (v_player+obX).w,d0 / add.w d1,d0 / cmp.w obX(a0),d0
            int testHigh = playerX + DETECTION_RANGE;
            if (testHigh >= doorX) {
                // sub.w d1,d0 / sub.w d1,d0 / cmp.w obX(a0),d0
                int testLow = playerX - DETECTION_RANGE;
                if (testLow < doorX) {
                    // Sonic is within range. Check which side he's on.
                    // add.w d1,d0 -> d0 = playerX
                    // cmp.w obX(a0),d0 -> if playerX < doorX, Sonic is left
                    if (playerX < doorX) {
                        // Sonic is left of door
                        // btst #0,obStatus(a0) / bne.s ADoor_Animate
                        // If openFromRight is false (bit 0 clear), open; else close
                        if (!openFromRight) {
                            newAnimId = 1;
                        }
                    } else {
                        // Sonic is right of (or at) door (loc_899A)
                        // btst #0,obStatus(a0) / beq.s ADoor_Animate
                        // If openFromRight is true (bit 0 set), open; else close
                        if (openFromRight) {
                            newAnimId = 1;
                        }
                    }
                }
            }
        }

        // AnimateSprite detects animation changes and resets when the ID differs
        animationId = newAnimId;
        if (animationId != prevAnimationId) {
            animationFrameIndex = 0;
            animationTimer = 0;
            prevAnimationId = animationId;
        }

        animate();

        // Door is solid only when fully closed (obFrame == 0)
        // tst.b obFrame(a0) / bne.s .remember
        solidActive = (mappingFrame == 0);
    }

    /**
     * Animates the door using the current animation sequence.
     * Matches AnimateSprite behavior with frame delay from Ani_ADoor.
     */
    private void animate() {
        animationTimer--;
        if (animationTimer >= 0) {
            return;
        }
        animationTimer = FRAME_DELAY;

        int[] sequence = (animationId == 0) ? CLOSE_SEQUENCE : OPEN_SEQUENCE;
        if (animationFrameIndex >= sequence.length) {
            // afBack,1: loop back to the last frame in the sequence (hold)
            animationFrameIndex = sequence.length - 1;
        }
        mappingFrame = sequence[animationFrameIndex];
        animationFrameIndex++;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.SBZ_SMALL_DOOR);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // ori.b #4,obRender(a0) -> screen-relative rendering, no flip from render flags
        // The door uses the same art regardless of x-flip status; x-flip only
        // controls obStatus bit 0 for approach direction logic.
        renderer.drawFrameIndex(mappingFrame, getX(), getY(), false, false);
    }

    // ---- SolidObjectProvider ----

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return solidActive;
    }

    // ---- SolidObjectListener ----

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // SolidObject handles the collision response; no extra per-contact behavior.
    }

    // ---- Persistence ----

    @Override
    public boolean isPersistent() {
        // RememberState: object persists while on screen
        return !isDestroyed() && isOnScreenX(160);
    }

    // ---- Debug Rendering ----

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        int objX = getX();
        int objY = getY();
        // Draw solid bounds when door is closed
        float r = solidActive ? 0.8f : 0.3f;
        float g = solidActive ? 0.5f : 0.8f;
        float b = solidActive ? 0.2f : 0.4f;
        ctx.drawRect(objX, objY, SOLID_PARAMS.halfWidth(), SOLID_PARAMS.airHalfHeight(), r, g, b);
        ctx.drawWorldLabel(objX, objY, -1,
                String.format("Door frm=%d anim=%d side=%s",
                        mappingFrame, animationId,
                        openFromRight ? "R" : "L"),
                DEBUG_COLOR);
    }
}
