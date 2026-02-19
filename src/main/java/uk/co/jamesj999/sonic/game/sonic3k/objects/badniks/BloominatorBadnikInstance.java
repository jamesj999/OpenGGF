package uk.co.jamesj999.sonic.game.sonic3k.objects.badniks;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic3k.audio.Sonic3kSfx;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

/**
 * S3K Obj $8C - Bloominator (AIZ).
 * Core routine mapping: Obj_Bloominator (sonic3k.asm loc_86D8A..loc_86E42).
 */
public final class BloominatorBadnikInstance extends AbstractS3kBadnikInstance {
    private static final int COLLISION_SIZE_INDEX = 0x23; // ObjDat_Bloominator flags $23
    private static final int PRIORITY_BUCKET = 4;         // ObjDat_Bloominator priority $200

    private static final int INITIAL_WAIT_FRAMES = 0x1F;  // loc_86D8A
    private static final int REPEAT_WAIT_FRAMES = 2 * 60; // loc_86DFC

    private static final int PROJECTILE_FRAME = 4;        // ObjDat3_86E1E mapping frame
    private static final int PROJECTILE_COLLISION_SIZE = 0x18; // ObjDat3 collision flags $98
    private static final int PROJECTILE_X_VEL = 0x100;    // ChildObjDat_86E2A
    private static final int PROJECTILE_Y_VEL = -0x500;   // ChildObjDat_86E2A
    private static final int PROJECTILE_GRAVITY = 0x38;   // Child callback MoveSprite
    private static final int PROJECTILE_Y_OFFSET = -0x10; // ChildObjDat_86E2A
    private static final int PROJECTILE_PRIORITY = 5;     // ObjDat3_86E1E priority $280

    // byte_86E42 (frame, delay pairs). Spawn points at offsets 6 and $E => step 3 and 7.
    private static final int[] ATTACK_FRAMES = {0, 1, 2, 3, 0, 1, 2, 3, 0};
    private static final int[] ATTACK_DELAYS = {7, 9, 4, 4, 9, 9, 4, 4, 0};
    private static final int FIRST_FIRE_STEP = 3;
    private static final int SECOND_FIRE_STEP = 7;

    private enum State {
        IDLE_WAIT,
        ATTACK
    }

    private State state = State.IDLE_WAIT;
    private int stateTimer = INITIAL_WAIT_FRAMES;
    private int attackStep;
    private int attackStepTimer;
    private int shotToggleCounter;

    public BloominatorBadnikInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, "Bloominator", levelManager,
                Sonic3kObjectArtKeys.BLOOMINATOR, COLLISION_SIZE_INDEX, PRIORITY_BUCKET);
        this.mappingFrame = 0;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (destroyed) {
            return;
        }

        switch (state) {
            case IDLE_WAIT -> updateIdleWait();
            case ATTACK -> updateAttack();
        }
    }

    private void updateIdleWait() {
        // loc_86DA2: wait timer only while object is visible/active.
        if (!isOnScreenX()) {
            return;
        }
        stateTimer--;
        if (stateTimer >= 0) {
            return;
        }

        state = State.ATTACK;
        attackStep = 0;
        attackStepTimer = ATTACK_DELAYS[0];
        mappingFrame = ATTACK_FRAMES[0];
    }

    private void updateAttack() {
        if (attackStepTimer > 0) {
            attackStepTimer--;
            return;
        }

        if (attackStep == FIRST_FIRE_STEP || attackStep == SECOND_FIRE_STEP) {
            fireProjectile();
        }

        attackStep++;
        if (attackStep >= ATTACK_FRAMES.length) {
            state = State.IDLE_WAIT;
            stateTimer = REPEAT_WAIT_FRAMES;
            mappingFrame = 0;
            return;
        }

        mappingFrame = ATTACK_FRAMES[attackStep];
        attackStepTimer = ATTACK_DELAYS[attackStep];
    }

    private void fireProjectile() {
        AudioManager.getInstance().playSfx(Sonic3kSfx.PROJECTILE.id);

        int xVel = PROJECTILE_X_VEL;
        shotToggleCounter++;
        if ((shotToggleCounter & 1) != 0) {
            xVel = -xVel;
        }

        spawnProjectile(new S3kBadnikProjectileInstance(
                spawn,
                Sonic3kObjectArtKeys.BLOOMINATOR,
                PROJECTILE_FRAME,
                currentX,
                currentY + PROJECTILE_Y_OFFSET,
                xVel,
                PROJECTILE_Y_VEL,
                PROJECTILE_GRAVITY,
                PROJECTILE_COLLISION_SIZE,
                RenderPriority.clamp(PROJECTILE_PRIORITY),
                xVel > 0));
    }
}
