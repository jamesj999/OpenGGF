package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * AIZ2 post-boss controller for the Sonic/Tails route.
 *
 * <p>ROM reference: loc_694D4 onward.
 */
public class Aiz2BossEndSequenceController extends AbstractObjectInstance {

    private static final int MAX_X_TARGET_OFFSET = 0x158;
    private static final int PLAYER_TRIGGER_X_OFFSET = 0x1F8;
    private static final int NEXT_LEVEL_Y_OFFSET = 0x1E6;

    private final int arenaMaxX;
    private final int arenaBaseY;
    private boolean initialized;
    private boolean postCapsuleSequenceStarted;
    private boolean knucklesSpawned;
    private boolean buttonHandled;
    private boolean transitionRequested;

    public Aiz2BossEndSequenceController(int arenaMaxX, int arenaBaseY) {
        super(new ObjectSpawn(arenaMaxX, arenaBaseY, Sonic3kObjectIds.EGG_CAPSULE, 0, 0, false, 0),
                "AIZ2BossEndSequence");
        this.arenaMaxX = arenaMaxX;
        this.arenaBaseY = arenaBaseY;
    }

    @Override
    public int getX() {
        return arenaMaxX;
    }

    @Override
    public int getY() {
        return arenaBaseY;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (!(playerEntity instanceof AbstractPlayableSprite player)) {
            return;
        }

        if (!initialized) {
            initialize(player);
        }

        if (!Aiz2BossEndSequenceState.isEggCapsuleReleased()) {
            player.clearForcedInputMask();
            player.setForceInputRight(false);
            return;
        }

        if (!postCapsuleSequenceStarted) {
            startPostCapsuleSequence(player);
        }

        if (!buttonHandled) {
            if ((player.getX() & 0xFFFF) < arenaMaxX + PLAYER_TRIGGER_X_OFFSET) {
                player.setControlLocked(true);
                player.clearForcedInputMask();
                player.setForceInputRight(true);
                setSidekickControlLocked(true);
                return;
            }

            if (!knucklesSpawned) {
                knucklesSpawned = true;
                spawnDynamicObject(CutsceneKnucklesAiz2Instance.createDefault());
            }

            player.setControlLocked(true);
            player.setForceInputRight(false);
            player.setForcedInputMask(AbstractPlayableSprite.INPUT_UP);
            setSidekickControlLocked(true);
        }

        if (Aiz2BossEndSequenceState.isButtonPressed() && !buttonHandled) {
            buttonHandled = true;
            player.clearForcedInputMask();
            player.setForceInputRight(false);
            player.setControlLocked(false);
            setSidekickControlLocked(false);
            services().camera().setMaxYTarget((short) 0x1000);
        }

        if (buttonHandled) {
            if ((player.getY() & 0xFFFF) >= arenaBaseY + NEXT_LEVEL_Y_OFFSET && !transitionRequested) {
                transitionRequested = true;
                services().requestZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0);
            }
        }
    }

    private void initialize(AbstractPlayableSprite player) {
        initialized = true;
        Aiz2BossEndSequenceState.triggerBridgeDrop();
        player.clearForcedInputMask();
        player.setForceInputRight(false);
    }

    private void startPostCapsuleSequence(AbstractPlayableSprite player) {
        postCapsuleSequenceStarted = true;
        services().camera().setMaxXTarget((short) (arenaMaxX + MAX_X_TARGET_OFFSET));
        int levelMusicId = services().getCurrentLevelMusicId();
        if (levelMusicId > 0) {
            services().playMusic(levelMusicId);
        }
        player.setControlLocked(true);
        player.clearForcedInputMask();
        player.setForceInputRight(true);
        setSidekickControlLocked(true);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }

    private void setSidekickControlLocked(boolean locked) {
        for (PlayableEntity sidekick : services().sidekicks()) {
            if (sidekick instanceof AbstractPlayableSprite sprite) {
                sprite.setControlLocked(locked);
                if (!locked) {
                    sprite.clearForcedInputMask();
                }
            }
        }
    }
}
