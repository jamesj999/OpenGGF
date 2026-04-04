package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Button used by the AIZ2 Knuckles post-boss cutscene.
 *
 * <p>ROM reference: Obj_CutsceneButton subtype 0.
 * The button is pressed when cutscene Knuckles finishes his jump/bounce
 * sequence and lands near it. It's NOT triggered during Knuckles' initial
 * run-in — only after the LAUGH_2 phase begins (Knuckles has completed
 * his jump and is now laughing at the player).
 */
public class S3kCutsceneButtonObjectInstance extends AbstractObjectInstance {

    private static final int INIT_Y_OFFSET = 4;
    private static final int PRIORITY = 4;
    private static final int RANGE_LEFT = -0x18;
    private static final int RANGE_RIGHT = 0x30;
    private static final int RANGE_TOP = -0x18;
    private static final int RANGE_BOTTOM = 0x30;

    private final int x;
    private final int y;
    private final boolean cutsceneOverride;
    private boolean pressed;

    public S3kCutsceneButtonObjectInstance(ObjectSpawn spawn) {
        this(spawn, false);
    }

    private S3kCutsceneButtonObjectInstance(ObjectSpawn spawn, boolean cutsceneOverride) {
        super(spawn, "CutsceneButton");
        this.x = spawn.x();
        this.y = spawn.y() + INIT_Y_OFFSET;
        this.cutsceneOverride = cutsceneOverride;
    }

    public static S3kCutsceneButtonObjectInstance createCutsceneOverride() {
        return new S3kCutsceneButtonObjectInstance(
                new ObjectSpawn(0x4B18, 0x0189, 0x83, 0, 0, false, 0), true);
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
    public boolean isPersistent() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (!cutsceneOverride && Aiz2BossEndSequenceState.isCutsceneOverrideObjectsActive()) {
            setDestroyed(true);
            return;
        }
        if (pressed) {
            return;
        }
        CutsceneKnucklesAiz2Instance knuckles = Aiz2BossEndSequenceState.getActiveKnuckles();
        if (knuckles == null) {
            return;
        }
        // ROM: The button is pressed when Knuckles lands ON it during the first
        // arc of his jump (loc_620EA uses SolidObjectFull2). This is before
        // the bounce back — NOT during his initial run-in or after the full
        // jump sequence. Gate on hasLandedOnButton() which becomes true at the
        // first bounce (when Knuckles physically touches down on the button).
        if (!knuckles.hasLandedOnButton()) {
            return;
        }
        int dx = knuckles.getX() - x;
        int dy = knuckles.getY() - y;
        if (dx >= RANGE_LEFT && dx < RANGE_RIGHT && dy >= RANGE_TOP && dy < RANGE_BOTTOM) {
            pressed = true;
            Aiz2BossEndSequenceState.pressButton();
            services().playSfx(Sonic3kSfx.SWITCH.id);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.BUTTON);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(pressed ? 1 : 0, x, y, false, false);
    }
}
