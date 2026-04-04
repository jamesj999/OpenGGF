package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.EggPrisonAnimalInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Floating upside-down egg prison used by the AIZ2 post-boss cutscene.
 *
 * <p>ROM reference: Obj_EggCapsule routine 8 (camera-relative descent/hover).
 * The capsule floats and bobs while panning left-right. The player must
 * jump up from below to hit the button, which opens the capsule and
 * triggers an explosion sequence with animals. After the explosions
 * finish, the results screen is shown.
 *
 * <p>ROM collision: SolidObjectFull with d1=$2B, d2=$18, d3=$18.
 * ROM open trigger: Check_PlayerInRange on button child (word_867C2).
 * ROM opening: sub_865DE — mapping_frame=1, spawn 5 flicker + 9 animal +
 * boss explosion (subtype 8).
 */
public class Aiz2EndEggCapsuleInstance extends AbstractObjectInstance
        implements SolidObjectProvider {
    private static final Logger LOG = Logger.getLogger(Aiz2EndEggCapsuleInstance.class.getName());

    private static final int OBJECT_ID = 0x81;
    private static final int X_OFFSET = 0xA0;
    private static final int Y_START_OFFSET = -0x40;
    private static final int Y_TARGET_OFFSET = 0x40;
    private static final int LEFT_BOUND_OFFSET = 0x30;
    private static final int RIGHT_BOUND_OFFSET = 0x110;
    private static final int PRIORITY = 5;

    // ROM: SolidObjectFull parameters (sonic3k.asm:181502-181506)
    private static final int SOLID_HALF_WIDTH = 0x2B;
    private static final int SOLID_HALF_HEIGHT = 0x18;

    // ROM: Check_PlayerInRange bounds for the button child (word_867C2)
    private static final int BUTTON_X_LEFT = -0x1A;
    private static final int BUTTON_X_RIGHT = 0x34;
    private static final int BUTTON_Y_TOP = -0x1C;
    private static final int BUTTON_Y_BOTTOM = 0x38;

    // Button offset from capsule centre (ROM: ChildObjDat_86B64, child_dy=+$24)
    private static final int BUTTON_Y_OFFSET = 0x24;

    // Post-open delay before results (frames to let explosions play out)
    private static final int POST_OPEN_DELAY = 60;

    // Animal spawn count (ROM: ChildObjDat_86B9A has 9 entries)
    private static final int ANIMAL_COUNT = 8;

    private int currentX;
    private int currentY;
    private int verticalAccumulator;
    private int bobAngle;
    private int xDirection = 1;
    private int mappingFrame;
    private boolean opened;
    private boolean resultsStarted;
    private boolean releaseTriggered;
    private int postOpenTimer;

    // Explosion controller (spawned when capsule opens)
    private S3kBossExplosionController explosionController;

    public Aiz2EndEggCapsuleInstance(int initialX, int initialY) {
        super(new ObjectSpawn(initialX, initialY, OBJECT_ID, 0, 0, false, 0), "AIZ2EndEggCapsule");
        this.currentX = initialX;
        this.currentY = initialY;
    }

    public static Aiz2EndEggCapsuleInstance createForCamera(int cameraX, int cameraY) {
        return new Aiz2EndEggCapsuleInstance(cameraX + X_OFFSET, cameraY + Y_START_OFFSET);
    }

    // ===== Position / lifecycle =====

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
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

    // ===== Solid collision (ROM: SolidObjectFull d1=$2B, d2=$18, d3=$18) =====

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, SOLID_HALF_HEIGHT);
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        return !opened;  // Capsule loses solidity once opened
    }

    // ===== Update =====

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        // ROM: Once opened, the capsule stops all movement (no panning, no
        // camera tracking). It stays at its current position and only the
        // bob animation continues. The camera scrolls past it naturally.
        if (!opened) {
            updateMovement();
        }

        if (!opened) {
            // Check if player hits the button from below
            if (playerEntity instanceof AbstractPlayableSprite player && shouldHitButton(player)) {
                openCapsule();
            }
            for (PlayableEntity sidekickEntity : services().sidekicks()) {
                if (sidekickEntity instanceof AbstractPlayableSprite sidekick && shouldHitButton(sidekick)) {
                    openCapsule();
                    break;
                }
            }
        } else if (!resultsStarted) {
            // Tick explosion controller
            if (explosionController != null && !explosionController.isFinished()) {
                explosionController.tick();
                spawnPendingExplosions();
            }

            // Wait for post-open delay (explosion sequence) then start results
            if (postOpenTimer > 0) {
                postOpenTimer--;
            }
            if (postOpenTimer == 0
                    && playerEntity instanceof AbstractPlayableSprite player
                    && !player.getAir()) {
                startResults(player);
            }
        } else if (!releaseTriggered && services().gameState().isEndOfLevelFlag()) {
            releaseTriggered = true;
            Aiz2BossEndSequenceState.releaseEggCapsule();
            // ROM: The capsule stays visible — it doesn't disappear.
            // It remains on screen while Sonic walks right and Knuckles
            // does his cutscene. It only leaves when the camera scrolls
            // past it or the zone transitions.
        }

        bobAngle = (bobAngle + 3) & 0xFF;
    }

    /**
     * Camera-relative panning and descent. Only runs while the capsule is
     * still closed (pre-open phase).
     */
    private void updateMovement() {
        int cameraX = services().camera().getX();
        int cameraY = services().camera().getY();

        // Horizontal panning within camera bounds
        int leftBound = cameraX + LEFT_BOUND_OFFSET;
        int rightBound = cameraX + RIGHT_BOUND_OFFSET;
        currentX += xDirection;
        if (currentX <= leftBound || currentX >= rightBound) {
            currentX = Math.max(leftBound, Math.min(rightBound, currentX));
            xDirection = -xDirection;
        }

        // Vertical descent toward target
        int targetY = cameraY + Y_TARGET_OFFSET;
        if (currentY != targetY) {
            verticalAccumulator += 0x4000;
            int step = verticalAccumulator >> 16;
            verticalAccumulator &= 0xFFFF;
            if (step > 0) {
                if (currentY < targetY) {
                    currentY = Math.min(targetY, currentY + step);
                } else {
                    currentY = Math.max(targetY, currentY - step);
                }
            }
        }
    }

    /**
     * ROM: Check_PlayerInRange on the button child (loc_86770 / word_867C2).
     * Player must be jumping upward (y_vel < 0) within the button's
     * collision box, which is offset below the capsule body.
     */
    private boolean shouldHitButton(AbstractPlayableSprite player) {
        // ROM: loc_86770 — Check_PlayerInRange (word_867C2) bounds relative to
        // the button child at child_dy=+$24 below the capsule centre.
        // ROM checks: in range + y_vel < 0 + (anim==2 OR character_id==1).
        // In S3K a normal jump always sets anim=2 (ball/rolling), so the
        // anim check effectively just confirms the player is jumping.
        int buttonY = currentY + BUTTON_Y_OFFSET;
        int dx = player.getCentreX() - currentX;
        int dy = player.getCentreY() - buttonY;
        return player.getYSpeed() < 0
                && dx >= BUTTON_X_LEFT
                && dx < BUTTON_X_RIGHT
                && dy >= BUTTON_Y_TOP
                && dy < BUTTON_Y_BOTTOM;
    }

    // ===== Capsule opening =====

    /**
     * ROM: sub_865DE — capsule opens, spawns explosions, animals, and visual effects.
     */
    private void openCapsule() {
        if (opened) {
            return;
        }
        opened = true;
        mappingFrame = 1;  // ROM: move.b #1,mapping_frame(a0) — open lid
        postOpenTimer = POST_OPEN_DELAY;

        // Play explosion SFX
        try {
            services().playSfx(Sonic3kSfx.EXPLODE.id);
        } catch (Exception e) {
            // Ignore audio errors
        }

        // Spawn boss explosion controller (compact type for capsule, subtype 3)
        explosionController = new S3kBossExplosionController(currentX, currentY, 3);

        // Spawn animals (ROM: ChildObjDat_86B9A creates 9 animal children)
        spawnAnimals();
    }

    /**
     * Drains pending explosions from the controller into dynamic children.
     */
    private void spawnPendingExplosions() {
        if (explosionController == null) return;
        var pending = explosionController.drainPendingExplosions();
        for (var entry : pending) {
            if (entry.playSfx()) {
                try {
                    services().playSfx(Sonic3kSfx.EXPLODE.id);
                } catch (Exception e) {
                    // Ignore audio errors
                }
            }
            spawnChild(() -> new S3kBossExplosionChild(entry.x(), entry.y()));
        }
    }

    /**
     * ROM: ChildObjDat_86B9A — spawn animals that burst out of the capsule.
     * Each animal gets a staggered delay so they pop out in sequence.
     *
     * <p>Uses {@code addDynamicObject()} rather than {@code spawnChild()} because
     * {@link EggPrisonAnimalInstance}'s constructor calls {@code getRenderManager()}
     * via static access (to obtain the animal renderer and zone-specific animal
     * types). This static pattern is safe with {@code addDynamicObject()} but
     * would conflict with the ThreadLocal context set by {@code spawnChild()}.
     */
    private void spawnAnimals() {
        var objectManager = services().objectManager();
        if (objectManager == null) return;
        for (int i = 0; i < ANIMAL_COUNT; i++) {
            int animalX = currentX + (i % 2 == 0 ? -(8 + i * 4) : (8 + i * 4));
            int animalY = currentY - 8;
            int delay = i * 4;  // Staggered: 0, 4, 8, 12, ...
            ObjectSpawn spawn = new ObjectSpawn(animalX, animalY, 0x28, 0, 0, false, 0);
            objectManager.addDynamicObject(new HighPriorityAnimal(spawn, delay));
        }
    }

    /**
     * Animal subclass that renders in front of high-priority foreground tiles.
     * The AIZ2 post-boss area has waterfall foreground tiles; without high
     * priority the animals would appear behind them.
     */
    private static final class HighPriorityAnimal extends EggPrisonAnimalInstance {
        HighPriorityAnimal(ObjectSpawn spawn, int delay) {
            super(spawn, delay);
        }

        @Override
        public boolean isHighPriority() {
            return true;
        }
    }

    // ===== Results =====

    private void startResults(AbstractPlayableSprite player) {
        if (resultsStarted) {
            return;
        }
        resultsStarted = true;
        services().gameState().setEndOfLevelActive(true);
        lockForResults(player);
        for (PlayableEntity sidekickEntity : services().sidekicks()) {
            if (sidekickEntity instanceof AbstractPlayableSprite sidekick) {
                lockForResults(sidekick);
            }
        }
        spawnChild(() -> new S3kResultsScreenObjectInstance(getPlayerCharacter(), services().currentAct()));
    }

    private void lockForResults(AbstractPlayableSprite sprite) {
        sprite.setObjectControlled(true);
        sprite.setControlLocked(true);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAnimationId(Sonic3kAnimationIds.VICTORY);
    }

    private PlayerCharacter getPlayerCharacter() {
        try {
            return ((Sonic3kLevelEventManager) services().levelEventProvider()).getPlayerCharacter();
        } catch (Exception e) {
            return PlayerCharacter.SONIC_AND_TAILS;
        }
    }

    // ===== Rendering =====

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.EGG_CAPSULE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        int bobY = currentY + (int) Math.round(Math.sin((bobAngle * Math.PI * 2.0) / 256.0) * 3.0);

        // ROM: loc_86592 sets bset #1,render_flags(a0) for the floating capsule.
        // Draw body vFlip=true to hang upside-down.
        renderer.drawFrameIndex(mappingFrame, currentX, bobY, false, true);

        // ROM: The button child sits at child_dy=+$24 below the capsule centre.
        // It hangs below the upside-down capsule body, facing the ground.
        int buttonFrame = opened ? 0xC : 0x5;
        int buttonY = bobY + BUTTON_Y_OFFSET;
        renderer.drawFrameIndex(buttonFrame, currentX, buttonY, false, true);
    }
}
