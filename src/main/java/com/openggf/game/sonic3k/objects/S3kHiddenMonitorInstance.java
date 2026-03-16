package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * S3K hidden monitor (Object 0x80).
 *
 * <p>ROM: Obj_HiddenMonitor (sonic3k.asm) — invisible until the signpost
 * lands nearby, at which point it either reveals itself (in range) or
 * plays a sound and disappears (out of range).
 *
 * <p>Subtype encodes the monitor contents type.
 */
public class S3kHiddenMonitorInstance extends AbstractObjectInstance {
    private static final Logger LOG = Logger.getLogger(S3kHiddenMonitorInstance.class.getName());

    // Range check box: signpost position relative to THIS hidden monitor
    private static final int RANGE_LEFT = -0x0E;
    private static final int RANGE_RIGHT = 0x1C;
    private static final int RANGE_TOP = -0x80;
    private static final int RANGE_BOTTOM = 0xC0;

    private final int monitorX;
    private final int monitorY;
    private final int monitorSubtype;
    private boolean resolved;

    public S3kHiddenMonitorInstance(ObjectSpawn spawn) {
        super(spawn, "HiddenMonitor");
        this.monitorX = spawn.x();
        this.monitorY = spawn.y();
        this.monitorSubtype = spawn.subtype();
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed() || resolved) {
            return;
        }

        S3kSignpostInstance signpost = S3kSignpostInstance.getActiveSignpost();
        if (signpost == null) {
            return;
        }

        if (!signpost.isLanded()) {
            return;
        }

        // Signpost has landed — resolve this hidden monitor
        resolved = true;

        int dx = signpost.getWorldX() - monitorX;
        int dy = signpost.getWorldY() - monitorY;

        if (dx >= RANGE_LEFT && dx < RANGE_RIGHT && dy >= RANGE_TOP && dy < RANGE_BOTTOM) {
            // In range: reveal monitor, bounce signpost
            LOG.fine("Hidden monitor at (" + monitorX + "," + monitorY
                    + ") IN RANGE of signpost — revealing");
            try {
                AudioManager.getInstance().playSfx(Sonic3kSfx.BUBBLE_ATTACK.id);
            } catch (Exception e) {
                LOG.fine("Could not play bubble attack SFX: " + e.getMessage());
            }
            signpost.setLanded(false);

            // TODO: Transform into visible monitor (requires MonitorInstance integration).
            // For now, just destroy self as the monitor cannot yet be interacted with.
            setDestroyed(true);
        } else {
            // Out of range: play sound and disappear
            LOG.fine("Hidden monitor at (" + monitorX + "," + monitorY
                    + ") OUT OF RANGE of signpost — dismissing");
            try {
                AudioManager.getInstance().playSfx(Sonic3kSfx.GROUND_SLIDE.id);
            } catch (Exception e) {
                LOG.fine("Could not play ground slide SFX: " + e.getMessage());
            }
            setDestroyed(true);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Hidden monitors are invisible until revealed.
        // No rendering needed.
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(3);
    }
}
