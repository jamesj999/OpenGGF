package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.S3kBossExplosionChild;
import com.openggf.game.sonic3k.objects.S3kBossExplosionController;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
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
 * Standard ground-based egg capsule for HCZ2 (and other S3K zones).
 *
 * <p>ROM reference: Obj_EggCapsule (sonic3k.asm line 181496).
 * The capsule sits at a fixed position on the ground with the button
 * on TOP at offset (0, -0x24). The player must STAND on the button
 * (SolidObjectFull) to open the capsule.
 *
 * <p>On button press:
 * <ol>
 *   <li>Changes to open frame (mapping_frame = 1)</li>
 *   <li>Spawns boss explosions and animals</li>
 *   <li>Sets 64-frame timer</li>
 *   <li>After timer + player on ground: spawns results screen</li>
 * </ol>
 *
 * <p>ROM collision: SolidObjectFull with d1=$2B, d2=$18, d3=$18.
 * Button detection: SolidObjectFull with small hitbox d1=$1B, d2=4, d3=6.
 */
public class HczEndBossEggCapsuleInstance extends AbstractObjectInstance
        implements SolidObjectProvider {
    private static final Logger LOG = Logger.getLogger(HczEndBossEggCapsuleInstance.class.getName());

    private static final int OBJECT_ID = Sonic3kObjectIds.EGG_CAPSULE;
    private static final int PRIORITY = 5;

    // ROM: SolidObjectFull parameters (sonic3k.asm:181502-181506)
    private static final int SOLID_HALF_WIDTH = 0x2B;
    private static final int SOLID_HALF_HEIGHT = 0x18;

    // ROM: Button on TOP at offset (0, -0x24) from capsule centre
    private static final int BUTTON_Y_OFFSET = -0x24;

    // ROM: Button detection — player stands on button (SolidObjectFull d1=$1B, d2=4, d3=6)
    // The player is riding the button if standing on its solid surface.
    // We check using a positional range matching the button SolidObjectFull dimensions.
    private static final int BUTTON_SOLID_HALF_WIDTH = 0x1B;
    private static final int BUTTON_HALF_HEIGHT_AIR = 4;
    private static final int BUTTON_HALF_HEIGHT_GROUND = 6;

    // Post-open delay before results (ROM: 64-frame timer)
    private static final int POST_OPEN_DELAY = 64;

    // Animal spawn count (ROM: 14 animals)
    private static final int ANIMAL_COUNT = 14;

    // Fixed position
    private final int fixedX;
    private final int fixedY;

    // State
    private int mappingFrame;
    private boolean opened;
    private boolean resultsStarted;
    private int postOpenTimer;

    // Explosion controller (spawned when capsule opens)
    private S3kBossExplosionController explosionController;

    public HczEndBossEggCapsuleInstance(int x, int y) {
        super(new ObjectSpawn(x, y, OBJECT_ID, 0, 0, false, 0), "HCZEggCapsule");
        this.fixedX = x;
        this.fixedY = y;
    }

    // ===== Position / lifecycle =====

    @Override
    public int getX() {
        return fixedX;
    }

    @Override
    public int getY() {
        return fixedY;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
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
        if (!opened) {
            // Check if player is standing on the button (on top of capsule)
            checkButtonPress(playerEntity);
            for (PlayableEntity sidekickEntity : services().sidekicks()) {
                if (!opened) {
                    checkButtonPress(sidekickEntity);
                }
            }
        } else if (!resultsStarted) {
            // Tick explosion controller
            if (explosionController != null && !explosionController.isFinished()) {
                explosionController.tick();
                spawnPendingExplosions();
            }

            // Wait for post-open delay then start results when player is on ground
            if (postOpenTimer > 0) {
                postOpenTimer--;
            }
            if (postOpenTimer == 0
                    && playerEntity instanceof AbstractPlayableSprite player
                    && !player.getAir()) {
                startResults(player);
            }
        }
    }

    /**
     * ROM: Obj_EggCapsule button detection. The button sits on TOP of the
     * capsule at (0, -0x24). Player must be standing on the button's
     * solid surface — detected by checking if the player is riding
     * within the button's X range and at the correct Y height.
     *
     * <p>ROM uses SolidObjectFull for the button (d1=$1B, d2=4, d3=6),
     * which means the player physically lands on it. We approximate this
     * by checking if the player's feet are within the button's top surface
     * and they are on the ground (not jumping).
     */
    private void checkButtonPress(PlayableEntity playerEntity) {
        if (opened) return;
        if (!(playerEntity instanceof AbstractPlayableSprite player)) return;

        // Player must be on the ground (standing), not in the air
        if (player.getAir()) return;

        // Check horizontal range: within button half-width of capsule X
        int dx = Math.abs(player.getCentreX() - fixedX);
        if (dx > BUTTON_SOLID_HALF_WIDTH) return;

        // Check vertical: player's centre should be near the button's Y position
        // Button Y = capsule Y + BUTTON_Y_OFFSET (negative = above capsule centre)
        int buttonY = fixedY + BUTTON_Y_OFFSET;
        int dy = Math.abs(player.getCentreY() - buttonY);
        if (dy <= BUTTON_HALF_HEIGHT_GROUND + 8) {
            openCapsule();
        }
    }

    // ===== Capsule opening =====

    /**
     * ROM: sub_865DE equivalent — capsule opens, spawns explosions and animals.
     */
    private void openCapsule() {
        if (opened) return;
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
        explosionController = new S3kBossExplosionController(fixedX, fixedY, 3, services().rng());

        // Spawn animals
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
     * ROM: Spawn animals that burst out of the capsule.
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
            int animalX = fixedX + (i % 2 == 0 ? -(8 + i * 4) : (8 + i * 4));
            int animalY = fixedY - 16;  // Animals pop up from top of capsule
            int delay = i * 4;  // Staggered: 0, 4, 8, 12, ...
            ObjectSpawn spawn = new ObjectSpawn(animalX, animalY, 0x28, 0, 0, false, 0);
            objectManager.addDynamicObject(new EggPrisonAnimalInstance(
                    spawn, delay, services().rng().nextBits(1)));
        }
    }

    // ===== Results =====

    private void startResults(AbstractPlayableSprite player) {
        if (resultsStarted) return;
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

        // ROM: Ground-based capsule — draw body upright (no vFlip)
        renderer.drawFrameIndex(mappingFrame, fixedX, fixedY, false, false);

        // ROM: Button on TOP at offset (0, -0x24). Draw button frame upright.
        int buttonFrame = opened ? 0xC : 0x5;
        int buttonY = fixedY + BUTTON_Y_OFFSET;
        renderer.drawFrameIndex(buttonFrame, fixedX, buttonY, false, false);
    }
}
