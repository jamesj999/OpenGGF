package com.openggf.game.sonic2.objects;
import com.openggf.level.objects.ObjectAnimationState;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import com.openggf.debug.DebugColor;
import java.util.List;

/**
 * Object 0x71 - MTZ Lava Bubble / HPZ Pulsing Orb / HPZ Bridge Stake.
 * <p>
 * This is a shared object type with behavior selected by subtype low nibble:
 * <ul>
 *   <li>Subtype low nibble 0: HPZ Bridge Stake (uses Obj11 mappings)</li>
 *   <li>Subtype low nibble 1: HPZ Pulsing Orb (uses Obj71_a mappings)</li>
 *   <li>Subtype low nibble 2: MTZ Lava Bubble (uses Obj71_b mappings)</li>
 * </ul>
 * The subtype high nibble selects the animation script (0-3).
 * <p>
 * In MTZ, all 15 placements use subtype 0x22 (low nibble=2 for lava bubble art,
 * high nibble=2 for animation script 2).
 * <p>
 * This is purely a decorative animated object with no collision or solid behavior.
 * <p>
 * <b>Disassembly Reference:</b> s2.asm lines 23985-24040 (Obj71)
 * <p>
 * Animation scripts (Ani_obj71):
 * <ul>
 *   <li>0: delay=8,  frames=[3,3,4,5,5,4], LOOP</li>
 *   <li>1: delay=5,  frames=[0,0,0,1,2,3,3,2,1,2,3,3,1], LOOP</li>
 *   <li>2: delay=$B, frames=[0,1,2,3,4,5], LOOP_BACK(3) - loops to index 3</li>
 *   <li>3: delay=$7F, frames=[6], LOOP_BACK(2) - single empty frame hold</li>
 * </ul>
 */
public class MTZLavaBubbleObjectInstance extends AbstractObjectInstance {

    // Animation scripts from Ani_obj71 (s2.asm lines 24028-24040)
    private static final SpriteAnimationSet ANIMATIONS;

    static {
        ANIMATIONS = new SpriteAnimationSet();

        // Script 0 (byte_11372): dc.b 8, 3, 3, 4, 5, 5, 4, $FF
        ANIMATIONS.addScript(0, new SpriteAnimationScript(
                8, List.of(3, 3, 4, 5, 5, 4),
                SpriteAnimationEndAction.LOOP, 0
        ));

        // Script 1 (byte_1137A): dc.b 5, 0, 0, 0, 1, 2, 3, 3, 2, 1, 2, 3, 3, 1, $FF
        ANIMATIONS.addScript(1, new SpriteAnimationScript(
                5, List.of(0, 0, 0, 1, 2, 3, 3, 2, 1, 2, 3, 3, 1),
                SpriteAnimationEndAction.LOOP, 0
        ));

        // Script 2 (byte_11389): dc.b $B, 0, 1, 2, 3, 4, 5, $FD, 3
        // $FD = LOOP_BACK, param 3 = loop back 3 frames from end (index 3)
        ANIMATIONS.addScript(2, new SpriteAnimationScript(
                0x0B, List.of(0, 1, 2, 3, 4, 5),
                SpriteAnimationEndAction.LOOP_BACK, 3
        ));

        // Script 3 (byte_11392): dc.b $7F, 6, $FD, 2
        // $FD = LOOP_BACK, param 2 = loop back 2 frames from end
        // Only 1 frame (empty frame 6), so effectively holds on empty frame
        ANIMATIONS.addScript(3, new SpriteAnimationScript(
                0x7F, List.of(6),
                SpriteAnimationEndAction.LOOP_BACK, 2
        ));
    }

    private final ObjectAnimationState animationState;

    public MTZLavaBubbleObjectInstance(ObjectSpawn spawn) {
        super(spawn, "MTZLavaBubble");

        // ROM: subtype high nibble selects animation
        // move.b subtype(a0),d0 / andi.w #$F0,d0 / lsr.b #4,d0 / move.b d0,anim(a0)
        int animId = (spawn.subtype() >> 4) & 0x0F;
        this.animationState = new ObjectAnimationState(ANIMATIONS, animId, 0);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        // ROM: Obj71_Main just calls AnimateSprite then MarkObjGone
        animationState.update();
    }

    @Override
    public int getPriorityBucket() {
        // ROM: objsubdecl priority=1 for MTZ lava bubble
        return RenderPriority.clamp(1);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.MTZ_LAVA_BUBBLE);
        if (renderer == null) return;

        int frame = animationState.getMappingFrame();
        renderer.drawFrameIndex(frame, spawn.x(), spawn.y(), false, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        int x = spawn.x();
        int y = spawn.y();
        // Draw spawn position cross and object info label
        ctx.drawCross(x, y, 4, 1.0f, 1.0f, 0.0f);
        ctx.drawWorldLabel(x, y, -1,
                String.format("71 a%d f%d", animationState.getAnimId(), animationState.getMappingFrame()),
                DebugColor.YELLOW);
    }
}
