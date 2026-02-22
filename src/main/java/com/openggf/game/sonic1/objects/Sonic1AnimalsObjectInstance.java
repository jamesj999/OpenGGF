package com.openggf.game.sonic1.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic2.objects.badniks.AnimalType;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sonic 1 Object 0x28 - Animals.
 * <p>
 * ROM reference: docs/s1disasm/_incObj/28 Animals.asm
 * <p>
 * Supports both object paths:
 * <ul>
 *   <li>Subtype 0x00: from destroyed enemy/capsule ("Anml_FromEnemy")</li>
 *   <li>Subtype 0x0A-0x14: ending animals ("Anml_Ending" subtype path)</li>
 * </ul>
 */
public class Sonic1AnimalsObjectInstance extends AbstractObjectInstance {
    private static final int GRAVITY = 0x18;
    private static final int FLOOR_CHECK_HEIGHT = 12;
    private static final int INITIAL_POP_Y_VELOCITY = -0x400;
    private static final int START_MOVE_DISTANCE = 0xB8;
    private static final int DELETE_RANGE_AHEAD = 0x180;
    private static final int ROUTINE_FROM_ENEMY_FALL = 0x02;
    private static final int ROUTINE_PRISON_WAIT = 0x12;
    private static final int DEFAULT_PRIORITY = 6;
    private static final int PRISON_PRIORITY = 3;
    private static final int INITIAL_ANIM_TIMER = 7;
    private static final int WING_FLAP_TIMER = 1;
    private static final int FRAMES_PER_ANIMAL = 3;
    private static final int SUBTYPE_ENDING_BASE = 0x0A;
    private static final int SUBTYPE_ENDING_MAX = 0x14;
    private static final int ENDING_SPECIES_COUNT = 7;
    private static final int PRISON_WAIT_FRAMES = 0x0C;

    // Anml_VarIndex table (GHZ, LZ, MZ, SLZ, SYZ, SBZ)
    private static final int[][] ZONE_VARIANT_INDEX = {
            {0, 5}, {2, 3}, {6, 3}, {4, 5}, {4, 1}, {0, 1}
    };

    // Anml_Variables index -> species/speeds.
    private static final AnimalType[] VARIABLE_ANIMALS = {
            AnimalType.RABBIT,
            AnimalType.CHICKEN,
            AnimalType.PENGUIN,
            AnimalType.SEAL,
            AnimalType.PIG,
            AnimalType.FLICKY,
            AnimalType.SQUIRREL
    };

    // Anml_EndVram + Anml_EndSpeed tables, in subtype order 0x0A..0x14.
    private static final EndingProfile[] ENDING_PROFILES = {
            new EndingProfile(AnimalType.FLICKY, -0x440, -0x400),
            new EndingProfile(AnimalType.FLICKY, -0x440, -0x400),
            new EndingProfile(AnimalType.FLICKY, -0x440, -0x400),
            new EndingProfile(AnimalType.RABBIT, -0x300, -0x400),
            new EndingProfile(AnimalType.RABBIT, -0x300, -0x400),
            new EndingProfile(AnimalType.PENGUIN, -0x180, -0x300),
            new EndingProfile(AnimalType.PENGUIN, -0x180, -0x300),
            new EndingProfile(AnimalType.SEAL, -0x140, -0x180),
            new EndingProfile(AnimalType.PIG, -0x1C0, -0x300),
            new EndingProfile(AnimalType.CHICKEN, -0x200, -0x300),
            new EndingProfile(AnimalType.SQUIRREL, -0x280, -0x380)
    };

    private static final class EndingProfile {
        private final AnimalType animalType;
        private final int initialXVelocity;
        private final int initialYVelocity;

        private EndingProfile(AnimalType animalType, int initialXVelocity, int initialYVelocity) {
            this.animalType = animalType;
            this.initialXVelocity = initialXVelocity;
            this.initialYVelocity = initialYVelocity;
        }
    }

    private final PatternSpriteRenderer zoneAnimalRenderer;
    private final PatternSpriteRenderer endingAnimalRenderer;
    private final int subtype;

    private int currentX;
    private int currentY;
    private int xVelocity;
    private int yVelocity;
    private int xSub;
    private int ySub;
    private int initialXVelocity;
    private int initialYVelocity;
    private int routine;
    private int priority = DEFAULT_PRIORITY;
    private int animFrame;
    private int animTimer = INITIAL_ANIM_TIMER;
    private int prisonWaitTimer;
    private int fromEnemyVariantIndex;
    private int artVariant;
    private int mappingSetIndex;
    private int endingSpeciesIndex;
    private int bounceToggle;
    private boolean hFlip = true;
    private boolean endingMode;

    public Sonic1AnimalsObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Animals");
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        this.zoneAnimalRenderer = renderManager != null ? renderManager.getAnimalRenderer() : null;
        this.endingAnimalRenderer = renderManager != null ? renderManager.getRenderer(ObjectArtKeys.ANIMAL_ENDING) : null;
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.subtype = spawn.subtype() & 0xFF;

        if (isEndingSubtype(subtype)) {
            initialiseEndingSubtype(subtype);
        } else {
            initialiseFromEnemy();
        }
    }

    private void initialiseEndingSubtype(int endingSubtype) {
        // ROM: move.b obSubtype(a0),d0 / add.w d0,d0 / move.b d0,obRoutine(a0)
        int endingIndex = endingSubtype - SUBTYPE_ENDING_BASE;
        EndingProfile profile = ENDING_PROFILES[Math.max(0, Math.min(endingIndex, ENDING_PROFILES.length - 1))];
        this.endingMode = true;
        this.routine = (endingSubtype & 0xFF) << 1;
        this.initialXVelocity = profile.initialXVelocity;
        this.initialYVelocity = profile.initialYVelocity;
        this.xVelocity = initialXVelocity;
        this.yVelocity = initialYVelocity;
        this.mappingSetIndex = profile.animalType.mappingSet().ordinal();
        this.endingSpeciesIndex = endingSpeciesIndex(profile.animalType);
        this.animFrame = 0;
    }

    private void initialiseFromEnemy() {
        // ROM: Anml_FromEnemy random zone pair selection.
        int zoneId = LevelManager.getInstance().getRomZoneId();
        int[] zoneVariants = resolveZoneVariants(zoneId);
        this.fromEnemyVariantIndex = zoneVariants[ThreadLocalRandom.current().nextInt(2)];
        AnimalType animalType = VARIABLE_ANIMALS[fromEnemyVariantIndex];

        this.endingMode = false;
        this.routine = ROUTINE_FROM_ENEMY_FALL;
        this.initialXVelocity = animalType.xVel();
        this.initialYVelocity = animalType.yVel();
        this.mappingSetIndex = animalType.mappingSet().ordinal();
        this.artVariant = fromEnemyVariantIndex & 1;
        this.animFrame = 2;
        this.xVelocity = 0;
        this.yVelocity = INITIAL_POP_Y_VELOCITY;

        if (GameServices.gameState().isBossFightActive()) {
            this.routine = ROUTINE_PRISON_WAIT;
            this.prisonWaitTimer = PRISON_WAIT_FRAMES;
            this.xVelocity = 0;
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        switch (routine) {
            case 0x02 -> updateRoutine912A(frameCounter);
            case 0x04, 0x08, 0x0A, 0x0C, 0x10 -> updateRoutine9184(player);
            case 0x06, 0x0E -> updateRoutine91C0(player);
            case 0x12 -> updateRoutine9240();
            case 0x14, 0x16 -> updateRoutine9260(player);
            case 0x18 -> updateRoutine9280(player);
            case 0x1A -> updateRoutine92BA(player);
            case 0x1C, 0x20, 0x24 -> updateRoutine9314(player);
            case 0x1E, 0x22 -> updateRoutine9332(player);
            case 0x26 -> updateRoutine9370(player);
            case 0x28 -> updateRoutine92D6(player);
            default -> updateRoutine9184(player);
        }
    }

    /**
     * loc_912A: initial falling state for enemy-spawned animals.
     */
    private void updateRoutine912A(int frameCounter) {
        if (!isOnScreenX()) {
            setDestroyed(true);
            return;
        }

        objectFall();
        if (yVelocity >= 0 && checkFloorCollision()) {
            xVelocity = initialXVelocity;
            yVelocity = initialYVelocity;
            animFrame = 1;
            routine = (fromEnemyVariantIndex << 1) + 4;

            if (GameServices.gameState().isBossFightActive() && (frameCounter & 0x10) != 0) {
                xVelocity = -xVelocity;
                hFlip = !hFlip;
            }
        }
    }

    /**
     * loc_9184: walking/hopping state.
     */
    private void updateRoutine9184(AbstractPlayableSprite player) {
        objectFall();
        animFrame = 1;
        if (yVelocity >= 0) {
            animFrame = 0;
            if (checkFloorCollision()) {
                yVelocity = initialYVelocity;
            }
        }
        applyDistanceCull(player);
    }

    /**
     * loc_91C0: flight/bounce state with frame toggling.
     */
    private void updateRoutine91C0(AbstractPlayableSprite player) {
        speedToPos();
        yVelocity += GRAVITY;

        if (yVelocity >= 0 && checkFloorCollision()) {
            yVelocity = initialYVelocity;
            // ROM: subtype $0A skips bounce-direction toggle.
            if (subtype != 0 && subtype != SUBTYPE_ENDING_BASE) {
                xVelocity = -xVelocity;
                hFlip = !hFlip;
            }
        }

        animateWings();
        applyDistanceCull(player);
    }

    /**
     * loc_9240: prison wait, then transition back to routine 2.
     */
    private void updateRoutine9240() {
        if (!isOnScreenX()) {
            setDestroyed(true);
            return;
        }
        if (--prisonWaitTimer == 0) {
            routine = ROUTINE_FROM_ENEMY_FALL;
            priority = PRISON_PRIORITY;
        }
    }

    /**
     * loc_9260: wait for player proximity, then start movement (routine $E).
     */
    private void updateRoutine9260(AbstractPlayableSprite player) {
        if (isPlayerClose(player)) {
            xVelocity = initialXVelocity;
            yVelocity = initialYVelocity;
            routine = 0x0E;
            updateRoutine91C0(player);
            return;
        }
        applyDistanceCull(player);
    }

    /**
     * loc_9280: grounded motion with player-facing render flag updates.
     */
    private void updateRoutine9280(AbstractPlayableSprite player) {
        if (isPlayerClose(player)) {
            xVelocity = 0;
            initialXVelocity = 0;
            speedToPos();
            yVelocity += GRAVITY;
            applyGroundFrame();
            facePlayer(player);
            animateWings();
        }
        applyDistanceCull(player);
    }

    /**
     * loc_92BA: wait for proximity, then jump to routine 4 behavior.
     */
    private void updateRoutine92BA(AbstractPlayableSprite player) {
        if (isPlayerClose(player)) {
            xVelocity = initialXVelocity;
            yVelocity = initialYVelocity;
            routine = 0x04;
            updateRoutine9184(player);
            return;
        }
        applyDistanceCull(player);
    }

    /**
     * loc_92D6: walk with alternating direction flips.
     */
    private void updateRoutine92D6(AbstractPlayableSprite player) {
        objectFall();
        animFrame = 1;
        if (yVelocity >= 0) {
            animFrame = 0;
            if (checkFloorCollision()) {
                bounceToggle ^= 1;
                if (bounceToggle == 0) {
                    xVelocity = -xVelocity;
                    hFlip = !hFlip;
                }
                yVelocity = initialYVelocity;
            }
        }
        applyDistanceCull(player);
    }

    /**
     * loc_9314: grounded movement that always faces player.
     */
    private void updateRoutine9314(AbstractPlayableSprite player) {
        if (isPlayerClose(player)) {
            xVelocity = 0;
            initialXVelocity = 0;
            objectFall();
            applyGroundFrame();
            facePlayer(player);
        }
        applyDistanceCull(player);
    }

    /**
     * loc_9332: hop state, reversing direction on each floor hit.
     */
    private void updateRoutine9332(AbstractPlayableSprite player) {
        if (isPlayerClose(player)) {
            objectFall();
            animFrame = 1;
            if (yVelocity >= 0) {
                animFrame = 0;
                if (checkFloorCollision()) {
                    xVelocity = -xVelocity;
                    hFlip = !hFlip;
                    yVelocity = initialYVelocity;
                }
            }
        }
        applyDistanceCull(player);
    }

    /**
     * loc_9370: airborne state with alternating direction flips.
     */
    private void updateRoutine9370(AbstractPlayableSprite player) {
        if (isPlayerClose(player)) {
            speedToPos();
            yVelocity += GRAVITY;
            if (yVelocity >= 0 && checkFloorCollision()) {
                bounceToggle ^= 1;
                if (bounceToggle == 0) {
                    xVelocity = -xVelocity;
                    hFlip = !hFlip;
                }
                yVelocity = initialYVelocity;
            }
            animateWings();
        }
        applyDistanceCull(player);
    }

    private void speedToPos() {
        xSub += xVelocity;
        ySub += yVelocity;
        currentX += (xSub >> 8);
        currentY += (ySub >> 8);
        xSub &= 0xFF;
        ySub &= 0xFF;
    }

    private void objectFall() {
        speedToPos();
        yVelocity += GRAVITY;
    }

    private boolean checkFloorCollision() {
        TerrainCheckResult result = ObjectTerrainUtils.checkFloorDist(currentX, currentY, FLOOR_CHECK_HEIGHT);
        if (result.hasCollision()) {
            currentY += result.distance();
            return true;
        }
        return false;
    }

    // loc_93C4 helper
    private void applyGroundFrame() {
        animFrame = 1;
        if (yVelocity >= 0) {
            animFrame = 0;
            if (checkFloorCollision()) {
                yVelocity = initialYVelocity;
            }
        }
    }

    // loc_93EC helper
    private void facePlayer(AbstractPlayableSprite player) {
        hFlip = currentX >= playerX(player);
    }

    // sub_9404 sign check
    private boolean isPlayerClose(AbstractPlayableSprite player) {
        return playerX(player) - currentX - START_MOVE_DISTANCE < 0;
    }

    // loc_9224 and subtype=0 off-screen cleanup paths
    private void applyDistanceCull(AbstractPlayableSprite player) {
        if (subtype == 0) {
            if (!isOnScreenX()) {
                setDestroyed(true);
            }
            return;
        }

        int dx = currentX - playerX(player);
        if (dx >= 0 && dx < DELETE_RANGE_AHEAD && !isOnScreenX()) {
            setDestroyed(true);
        }
    }

    private void animateWings() {
        if (--animTimer < 0) {
            animTimer = WING_FLAP_TIMER;
            animFrame = (animFrame + 1) & 1;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        PatternSpriteRenderer renderer = endingMode ? endingAnimalRenderer : zoneAnimalRenderer;
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        renderer.drawFrameIndex(resolveFrameIndex(), currentX, currentY, hFlip, false);
    }

    private int resolveFrameIndex() {
        int clampedFrame = Math.max(0, Math.min(animFrame, FRAMES_PER_ANIMAL - 1));
        if (endingMode) {
            return (endingSpeciesIndex * FRAMES_PER_ANIMAL) + clampedFrame;
        }
        int base = ((mappingSetIndex * 2) + artVariant) * FRAMES_PER_ANIMAL;
        return base + clampedFrame;
    }

    private static boolean isEndingSubtype(int subtype) {
        return subtype >= SUBTYPE_ENDING_BASE && subtype <= SUBTYPE_ENDING_MAX;
    }

    private static int[] resolveZoneVariants(int zoneId) {
        if (zoneId >= 0 && zoneId < ZONE_VARIANT_INDEX.length) {
            return ZONE_VARIANT_INDEX[zoneId];
        }
        return ZONE_VARIANT_INDEX[0];
    }

    private static int endingSpeciesIndex(AnimalType type) {
        return switch (type) {
            case FLICKY -> 0;
            case RABBIT -> 1;
            case PENGUIN -> 2;
            case SEAL -> 3;
            case PIG -> 4;
            case CHICKEN -> 5;
            case SQUIRREL -> 6;
            default -> Math.min(type.ordinal(), ENDING_SPECIES_COUNT - 1);
        };
    }

    private int playerX(AbstractPlayableSprite player) {
        return player != null ? player.getCentreX() : currentX;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(priority);
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }
}
