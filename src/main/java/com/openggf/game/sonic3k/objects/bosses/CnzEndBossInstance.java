package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.events.S3kCnzEventWriteSupport;
import com.openggf.game.sonic3k.objects.CnzEggCapsuleInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Bounded CNZ Act 2 end-boss wrapper for Task 8.
 *
 * <p>ROM anchor: {@code Obj_CNZEndBoss}.
 *
 * <p>This implementation explicitly does <strong>not</strong> claim full attack
 * parity. Task 8 only owns two seams from the verified ROM notes:
 * <ul>
 *   <li>bounded startup presence for the CNZ boss slot without claiming the
 *   wider attack-state choreography</li>
 *   <li>defeat handoff: clear {@code Boss_flag}, widen the camera max,
 *   spawn the capsule, and restore player control/music</li>
 * </ul>
 *
 * <p>Any later swing/attack logic should replace this bounded wrapper once the
 * remaining CNZ end-boss choreography is implemented.
 */
public final class CnzEndBossInstance extends AbstractObjectInstance {
    /**
     * Task 8 approximation for the post-defeat camera release.
     *
     * <p>The verified ROM note only requires that the camera max widens once the
     * boss handoff completes. The exact attack-phase arena management is outside
     * this slice, so the implementation widens by a conservative 0x100 pixels to
     * make the release explicit without claiming full boss-boundary parity.
     */
    private static final int CAMERA_RELEASE_DELTA = 0x100;

    private final int centreX;
    private final int centreY;

    private boolean defeatRequestedForTest;
    private boolean defeatHandoffComplete;

    public CnzEndBossInstance(ObjectSpawn spawn) {
        super(spawn, "CNZEndBoss");
        this.centreX = spawn.x();
        this.centreY = spawn.y();
    }

    /**
     * Task 8 keeps the defeat path bounded and therefore exposes a narrow test
     * seam instead of pretending the full damage/attack state machine exists.
     */
    public void forceDefeatForTest() {
        defeatRequestedForTest = true;
    }

    @Override
    public int getX() {
        return centreX;
    }

    @Override
    public int getY() {
        return centreY;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(5);
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (defeatRequestedForTest && !defeatHandoffComplete) {
            applyDefeatHandoff();
        }
    }

    /**
     * Returns whether the wider CNZ script has already declared this boss slot
     * active through shared state.
     *
     * <p>Task 8 intentionally does not let the promoted production slot claim
     * boss mode on its own. The real startup gate belongs to the later attack
     * choreography and CNZ event flow; this bounded wrapper only participates in
     * defeat cleanup once that wider state already exists.
     */
    private boolean isBossModeAlreadyOwnedExternally() {
        if (services().gameState().getCurrentBossId() == Sonic3kObjectIds.CNZ_END_BOSS) {
            return true;
        }
        Object provider = services().levelEventProvider();
        if (provider instanceof Sonic3kLevelEventManager manager) {
            Sonic3kCNZEvents events = manager.getCnzEvents();
            return events != null && events.isBossFlag();
        }
        return false;
    }

    /**
     * Verified Task 8 defeat handoff.
     *
     * <p>This is the honest boundary from the ROM findings:
     * <ol>
     *   <li>clear {@code Boss_flag}</li>
     *   <li>widen the camera max so the player can move past the boss arena</li>
     *   <li>spawn the CNZ-local egg capsule wrapper</li>
     *   <li>restore player control and CNZ Act 2 music</li>
     * </ol>
     */
    private void applyDefeatHandoff() {
        if (!isBossModeAlreadyOwnedExternally()) {
            return;
        }
        defeatHandoffComplete = true;

        S3kCnzEventWriteSupport.setBossFlag(services(), false);
        services().gameState().setCurrentBossId(0);

        int widenedMaxX = services().camera().getMaxX() + CAMERA_RELEASE_DELTA;
        services().camera().setMaxX((short) widenedMaxX);

        spawnChild(() -> new CnzEggCapsuleInstance(
                new ObjectSpawn(centreX, centreY, Sonic3kObjectIds.EGG_CAPSULE, 0, 0, false, 0)));

        restorePlayerControl();
        restoreLevelMusic();
        setDestroyed(true);
    }

    /**
     * Restores main-player and sidekick control after the bounded defeat handoff.
     *
     * <p>The teleporter beam can leave the player object-controlled, rolled, and
     * hidden. CNZ's boss release must clear all three so the capsule handoff
     * leaves the player in a normal controllable state.
     */
    private void restorePlayerControl() {
        if (services().camera().getFocusedSprite() instanceof AbstractPlayableSprite player) {
            releaseSprite(player);
        }
        for (PlayableEntity sidekickEntity : services().sidekicks()) {
            if (sidekickEntity instanceof AbstractPlayableSprite sidekick) {
                releaseSprite(sidekick);
            }
        }
    }

    private void releaseSprite(AbstractPlayableSprite sprite) {
        sprite.setControlLocked(false);
        sprite.setObjectControlled(false);
        sprite.setHidden(false);
        sprite.setRolling(false);
    }

    /**
     * Restores CNZ Act 2 music instead of claiming a full boss music / fade
     * state machine.
     */
    private void restoreLevelMusic() {
        services().playMusic(Sonic3kMusic.CNZ2.id);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.CNZ_END_BOSS);
        if (renderer == null) {
            return;
        }
        renderer.drawFrameIndex(0, centreX, centreY, false, false);
    }
}
