package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.EggPrisonAnimalInstance;
import com.openggf.level.objects.MultiPieceSolidProvider;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Shared route-8 floating egg prison used by S3K end sequences.
 *
 * <p>ROM reference: Obj_EggCapsule routine 8 (camera-relative descent/hover).
 * The capsule floats upside-down, pans with the camera, opens from an
 * underside button hit, then runs the explosion, animal, and results flow.
 */
public abstract class AbstractS3kFloatingEndEggCapsuleInstance extends AbstractObjectInstance
        implements MultiPieceSolidProvider {
    private static final int OBJECT_ID = 0x81;
    protected static final int X_OFFSET = 0xA0;
    protected static final int Y_START_OFFSET = -0x40;
    private static final int Y_TARGET_OFFSET = 0x40;
    private static final int LEFT_BOUND_OFFSET = 0x30;
    private static final int RIGHT_BOUND_OFFSET = 0x110;
    private static final int PRIORITY = 5;

    private static final int SOLID_HALF_WIDTH = 0x2B;
    private static final int SOLID_HALF_HEIGHT = 0x18;
    private static final int BUTTON_SOLID_HALF_WIDTH = 0x1B;
    private static final int BUTTON_SOLID_HALF_HEIGHT_AIR = 0x5;
    private static final int BUTTON_SOLID_HALF_HEIGHT_GROUND = 0x9;
    private static final int BUTTON_RECESS = 8;
    private static final int PIECE_BUTTON = 1;
    private static final int BUTTON_Y_OFFSET = 0x24;
    private static final int TRIGGER_X_LEFT = -0x1A;
    private static final int TRIGGER_X_RIGHT = -0x1A + 0x34;
    private static final int TRIGGER_Y_TOP = -0x1C;
    private static final int TRIGGER_Y_BOTTOM = -0x1C + 0x38;
    private static final int POST_OPEN_DELAY = 60;
    private static final int ANIMAL_COUNT = 9;

    private int currentX;
    private int currentY;
    private int verticalAccumulator;
    private int bobAngle;
    private int bobOffset;
    private int xDirection = 1;
    private int mappingFrame;
    private boolean opened;
    private boolean resultsStarted;
    private boolean releaseTriggered;
    private int postOpenTimer;
    private int buttonRecess;
    @com.openggf.game.rewind.RewindDeferred(reason = "explosion controller has mutable queued state needing explicit value codec")
    private S3kBossExplosionController explosionController;

    protected AbstractS3kFloatingEndEggCapsuleInstance(int initialX, int initialY, String debugName) {
        super(new ObjectSpawn(initialX, initialY, OBJECT_ID, 0, 0, false, 0), debugName);
        this.currentX = initialX;
        this.currentY = initialY;
    }

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

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, SOLID_HALF_HEIGHT);
    }

    @Override
    public int getPieceCount() {
        return 2;
    }

    @Override
    public int getPieceX(int pieceIndex) {
        return currentX;
    }

    @Override
    public int getPieceY(int pieceIndex) {
        int bobY = currentY + bobOffset;
        if (pieceIndex == PIECE_BUTTON) {
            return bobY + BUTTON_Y_OFFSET - buttonRecess;
        }
        return bobY;
    }

    @Override
    public SolidObjectParams getPieceParams(int pieceIndex) {
        if (pieceIndex == PIECE_BUTTON) {
            return new SolidObjectParams(BUTTON_SOLID_HALF_WIDTH,
                    BUTTON_SOLID_HALF_HEIGHT_AIR, BUTTON_SOLID_HALF_HEIGHT_GROUND);
        }
        return getSolidParams();
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        bobOffset = (int) Math.round(Math.sin((bobAngle * Math.PI * 2.0) / 256.0) * 3.0);

        if (!opened) {
            updateMovement();
        }

        if (!opened) {
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
            if (explosionController != null && !explosionController.isFinished()) {
                explosionController.tick();
                spawnPendingExplosions();
            }

            if (postOpenTimer > 0) {
                postOpenTimer--;
            }
            if (postOpenTimer == 0
                    && playerEntity instanceof AbstractPlayableSprite player
                    && shouldStartResults(player)) {
                startResults(player);
            }
        } else if (resultsStarted && !releaseTriggered && services().gameState().isEndOfLevelFlag()) {
            releaseTriggered = true;
            onResultsComplete();
        }

        bobAngle = (bobAngle + 3) & 0xFF;
    }

    private void updateMovement() {
        int cameraX = services().camera().getX();
        int cameraY = services().camera().getY();

        int leftBound = cameraX + LEFT_BOUND_OFFSET;
        int rightBound = cameraX + RIGHT_BOUND_OFFSET;
        currentX += xDirection;
        if (currentX <= leftBound || currentX >= rightBound) {
            currentX = Math.max(leftBound, Math.min(rightBound, currentX));
            xDirection = -xDirection;
        }

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

    private boolean shouldHitButton(AbstractPlayableSprite player) {
        int buttonY = currentY + bobOffset + BUTTON_Y_OFFSET - buttonRecess;
        int dx = player.getCentreX() - currentX;
        int dy = player.getCentreY() - buttonY;
        return player.getYSpeed() < 0
                && dx >= TRIGGER_X_LEFT
                && dx < TRIGGER_X_RIGHT
                && dy >= TRIGGER_Y_TOP
                && dy < TRIGGER_Y_BOTTOM;
    }

    private void openCapsule() {
        if (opened) {
            return;
        }
        opened = true;
        mappingFrame = 1;
        buttonRecess = BUTTON_RECESS;
        postOpenTimer = POST_OPEN_DELAY;

        try {
            services().playSfx(Sonic3kSfx.EXPLODE.id);
        } catch (Exception e) {
            // Ignore audio errors.
        }

        explosionController = new S3kBossExplosionController(currentX, currentY, 3, services().rng());
        spawnAnimals();
    }

    private void spawnPendingExplosions() {
        if (explosionController == null) {
            return;
        }
        var pending = explosionController.drainPendingExplosions();
        for (var entry : pending) {
            if (entry.playSfx()) {
                try {
                    services().playSfx(Sonic3kSfx.EXPLODE.id);
                } catch (Exception e) {
                    // Ignore audio errors.
                }
            }
            spawnChild(() -> new S3kBossExplosionChild(entry.x(), entry.y()));
        }
    }

    private void spawnAnimals() {
        for (int i = 0; i < getAnimalCount(); i++) {
            int animalX = currentX + (i % 2 == 0 ? -(8 + i * 4) : (8 + i * 4));
            int animalY = currentY - 8;
            int delay = i * 4;
            ObjectSpawn spawn = new ObjectSpawn(animalX, animalY, 0x28, 0, 0, false, 0);
            int artVariant = services().rng().nextBits(1);
            int index = i;
            spawnChild(() -> createCapsuleAnimal(spawn, delay, artVariant, index));
        }
    }

    protected int getAnimalCount() {
        return ANIMAL_COUNT;
    }

    protected AbstractObjectInstance createCapsuleAnimal(ObjectSpawn spawn, int delay, int artVariant, int index) {
        return new EggPrisonAnimalInstance(spawn, delay, artVariant);
    }

    protected boolean shouldStartResults(AbstractPlayableSprite player) {
        return !player.getAir();
    }

    protected void startResults(AbstractPlayableSprite player) {
        if (resultsStarted) {
            return;
        }
        resultsStarted = true;
        services().gameState().setEndOfLevelActive(true);
        if (shouldLockPlayersForResults()) {
            lockForResults(player);
            for (PlayableEntity sidekickEntity : services().sidekicks()) {
                if (sidekickEntity instanceof AbstractPlayableSprite sidekick) {
                    lockForResults(sidekick);
                }
            }
        }
        spawnChild(this::createResultsScreen);
    }

    protected AbstractObjectInstance createResultsScreen() {
        return new S3kResultsScreenObjectInstance(getPlayerCharacter(), services().currentAct());
    }

    protected boolean shouldLockPlayersForResults() {
        return true;
    }

    protected void lockForResults(AbstractPlayableSprite sprite) {
        sprite.setObjectControlled(true);
        sprite.setControlLocked(true);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAnimationId(Sonic3kAnimationIds.VICTORY);
    }

    protected PlayerCharacter getPlayerCharacter() {
        return S3kRuntimeStates.resolvePlayerCharacter(
                services().zoneRuntimeRegistry(),
                services().configuration());
    }

    protected void onResultsComplete() {
        // Zone-specific post-results handoff.
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.EGG_CAPSULE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        int bobY = currentY + bobOffset;

        renderer.drawFrameIndex(mappingFrame, currentX, bobY, false, true);

        int buttonFrame = opened ? 0xC : 0x5;
        int buttonY = bobY + BUTTON_Y_OFFSET - buttonRecess;
        renderer.drawFrameIndex(buttonFrame, currentX, buttonY, false, true);
    }
}
