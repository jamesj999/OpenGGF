package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.boss.AbstractBossInstance;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Aquatic Ruin Zone boss (Object 0x89) - Eggman's hammer totem boss.
 * Extends AbstractBossInstance for unified boss architecture.
 * ROM Reference: s2.asm:64267 (Obj89_Init)
 *
 * State Machine (routine_secondary):
 * - SUB0: Descend to target Y
 * - SUB2: Move left/right between columns
 * - SUB4: Prepare hammer swing
 * - SUB6: Execute hammer swing
 * - SUB8: Defeat exploding
 * - SUBA: Defeat bounce/settle
 * - SUBC: Flee off-screen
 */
public class Sonic2ARZBossInstance extends AbstractBossInstance {

    private static final int RENDER_X_FLIP = 0x01;

    // Main routine states
    private static final int MAIN_SUB0 = 0x00;
    private static final int MAIN_SUB2 = 0x02;
    private static final int MAIN_SUB4 = 0x04;
    private static final int MAIN_SUB6 = 0x06;
    private static final int MAIN_SUB8 = 0x08;
    private static final int MAIN_SUBA = 0x0A;
    private static final int MAIN_SUBC = 0x0C;

    // Position constants
    private static final int MAIN_START_X = 0x2AE0;
    private static final int MAIN_START_Y = 0x388;
    private static final int MAIN_TARGET_Y = 0x430;
    private static final int LEFT_TARGET_X = 0x2AB0;
    private static final int RIGHT_TARGET_X = 0x2B10;
    private static final int LEFT_PILLAR_X = 0x2A50;
    private static final int RIGHT_PILLAR_X = 0x2B70;
    private static final int PILLAR_START_Y = 0x510;
    private static final int LEFT_EYES_X = 0x2A6A;
    private static final int RIGHT_EYES_X = 0x2B56;
    private static final int PLAYER_CHECK_LEFT_X = 0x2A60;
    private static final int PLAYER_CHECK_RIGHT_X = 0x2B60;
    private static final int CAMERA_ESCAPE_MAX_X = 0x2C00;
    private static final int HAMMER_OFFSET_X = 0x50;
    private static final int HAMMER_HITBOX_WIDTH = 0x10;
    private static final int HAMMER_HITBOX_HEIGHT = 0x14;

    // Velocity constants (8.8 fixed-point)
    private static final int MAIN_DESCEND_VEL = 0x0100;
    private static final int MAIN_MOVE_VEL = 0x00C8;
    private static final int MAIN_ESCAPE_XVEL = 0x0400;
    private static final int MAIN_ESCAPE_YVEL = -0x0040;
    private static final int HAMMER_INITIAL_YVEL = -0x0380;

    // Timing
    private static final int ARZ_INVULNERABLE_DURATION = 64;

    private static final int[] ARROW_OFFSETS = { 0x458, 0x478, 0x498, 0x4B8 };

    private static final int[][] ARZ_BOSS_ANIMS = {
            { 7, 0, 1, 0xFF, 2, 3, 2, 3, 2, 3, 2, 3, 0xFF, 4, 4, 4, 4, 4, 4, 4, 4, 0xFF },
            { 1, 6, 7, 0xFF },
            { 0x0F, 9, 0xFF },
            { 2, 0x0A, 0x0A, 0x0B, 0x0B, 0x0B, 0x0B, 0x0B, 0x0A, 0x0A, 0xFD, 2 },
            { 0x0F, 8, 0xFF },
            { 7, 5, 0xFF }
    };

    // Boss position and velocity (16.16 fixed-point)
    private int bossXPos;
    private int bossYPos;
    private int bossXVel;
    private int bossYVel;

    // State
    private int bossCountdown;
    private int hammerFlags;
    private int bossCollisionRoutine;
    private boolean targetFlag;
    private boolean initialized;

    // Multi-sprite data
    private int mainMapFrame;
    private int sub2MapFrame;
    private int sub3MapFrame;
    private int sub4MapFrame;
    private int sub2X, sub2Y;
    private int sub3X, sub3Y;
    private int sub4X, sub4Y;

    // Hammer state
    private int hammerYPos;
    private int hammerYVel;
    private int[] bossAnim;

    public Sonic2ARZBossInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "ARZ Boss");
    }

    @Override
    protected void initializeBossState() {
        // Initialize the animation array (must happen before initBossAnimationArray)
        bossAnim = new int[8];
        // Don't fully initialize until player position check passes
        initialized = false;
        state.routine = MAIN_SUB0;
        initBossAnimationArray();
    }

    @Override
    protected int getInitialHitCount() {
        return 8;
    }

    @Override
    protected int getInvulnerabilityDuration() {
        return ARZ_INVULNERABLE_DURATION;
    }

    @Override
    protected int getPaletteLineForFlash() {
        return 0; // ARZ flashes palette line 0
    }

    @Override
    protected int getCollisionSizeIndex() {
        return 0x0F;
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        bossAnim[3] = 0xC0; // Hurt animation
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false; // ARZ has custom defeat logic
    }

    @Override
    protected void onDefeatStarted() {
        bossCountdown = DEFEAT_TIMER_START;
        state.routine = MAIN_SUB8;
        bossAnim[2] = 0x05;
        bossAnim[3] = 0x00;
        sub2MapFrame = 5;
    }

    @Override
    protected void updateBossLogic(int frameCounter, AbstractPlayableSprite player) {
        if (!initialized) {
            if (!checkInitConditions(player)) {
                return;
            }
            finishInitialization();
        }

        if (bossCollisionRoutine != 0) {
            checkHammerCollision(frameCounter, player);
        }

        switch (state.routine) {
            case MAIN_SUB0 -> updateMainSub0(player);
            case MAIN_SUB2 -> updateMainSub2(player);
            case MAIN_SUB4 -> updateMainSub4(player);
            case MAIN_SUB6 -> updateMainSub6(frameCounter, player);
            case MAIN_SUB8 -> updateMainSub8(frameCounter);
            case MAIN_SUBA -> updateMainSubA(player);
            case MAIN_SUBC -> updateMainSubC(player);
        }
    }

    private boolean checkInitConditions(AbstractPlayableSprite player) {
        AbstractPlayableSprite sidekick = SpriteManager.getInstance().getSidekick();
        if (sidekick != null) {
            AbstractPlayableSprite mainPlayer = Camera.getInstance().getFocusedSprite();
            if (mainPlayer != null) {
                int mainX = mainPlayer.getCentreX();
                if (mainX < PLAYER_CHECK_LEFT_X || mainX > PLAYER_CHECK_RIGHT_X) {
                    return false;
                }
            }
            int sidekickX = sidekick.getCentreX();
            if (sidekickX < PLAYER_CHECK_LEFT_X || sidekickX > PLAYER_CHECK_RIGHT_X) {
                return false;
            }
        }
        return true;
    }

    private void finishInitialization() {
        initialized = true;

        GameServices.gameState().setScreenShakeActive(true);

        state.renderFlags &= 0x03;
        bossXPos = MAIN_START_X << 16;
        bossYPos = MAIN_START_Y << 16;
        bossXVel = 0;
        bossYVel = MAIN_DESCEND_VEL;
        bossCountdown = 0;
        state.sineCounter = 0;
        hammerFlags = 0;
        bossCollisionRoutine = 0;
        targetFlag = false;
        hammerYVel = HAMMER_INITIAL_YVEL;

        mainMapFrame = 8;
        sub2MapFrame = 0;
        sub3MapFrame = 9;
        sub4MapFrame = 6;
        sub2X = MAIN_START_X;
        sub2Y = 0x488;
        sub3X = MAIN_START_X;
        sub3Y = 0x488;
        sub4X = MAIN_START_X;
        sub4Y = 0x488;
        hammerYPos = 0x488 << 16;

        state.x = MAIN_START_X;
        state.y = MAIN_START_Y;
        state.xFixed = state.x << 16;
        state.yFixed = state.y << 16;

        spawnPillars();
    }

    private void initBossAnimationArray() {
        bossAnim[0] = 0x04;
        bossAnim[1] = 0x00;
        bossAnim[2] = 0x00;
        bossAnim[3] = 0x00;
        bossAnim[4] = 0x02;
        bossAnim[5] = 0x00;
        bossAnim[6] = 0x01;
        bossAnim[7] = 0x00;
    }

    private void updateMainSub0(AbstractPlayableSprite player) {
        bossMoveObject();
        handleFace(player);
        alignParts();
        if ((bossYPos >> 16) >= MAIN_TARGET_Y) {
            bossYPos = MAIN_TARGET_Y << 16;
            state.routine = MAIN_SUB2;
            bossYVel = 0;
            bossXVel = -MAIN_MOVE_VEL;
            targetFlag = true;
        }
        animateBoss();
    }

    private void updateMainSub2(AbstractPlayableSprite player) {
        bossMoveObject();
        handleFace(player);
        alignParts();
        if (!targetFlag) {
            if ((bossXPos >> 16) >= RIGHT_TARGET_X) {
                state.routine = MAIN_SUB4;
                bossXVel = 0;
            }
        } else {
            if ((bossXPos >> 16) <= LEFT_TARGET_X) {
                state.routine = MAIN_SUB4;
                bossXVel = 0;
            }
        }
        animateBoss();
    }

    private void updateMainSub4(AbstractPlayableSprite player) {
        bossMoveObject();
        handleFace(player);
        alignParts();
        if ((state.sineCounter & 0xFF) == 0xC0) {
            bossAnim[4] = (bossAnim[4] & 0xF0) | 0x03;
            state.routine = MAIN_SUB6;
            targetFlag = (state.renderFlags & RENDER_X_FLIP) != 0;
            bossCountdown = 0x1E;
            AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_HAMMER);
        }
        animateBoss();
    }

    private void updateMainSub6(int frameCounter, AbstractPlayableSprite player) {
        if (bossCountdown == 0x14) {
            hammerFlags |= 0x01;
            bossCollisionRoutine = 1;
        }
        bossCountdown--;
        if (bossCountdown < 0) {
            bossCollisionRoutine = 0;
            state.routine = MAIN_SUB2;
            state.renderFlags ^= RENDER_X_FLIP;
            bossXVel = (state.renderFlags & RENDER_X_FLIP) != 0 ? MAIN_MOVE_VEL : -MAIN_MOVE_VEL;
        }
        bossMoveObject();
        handleFace(player);
        alignParts();
        animateBoss();
    }

    private void updateMainSub8(int frameCounter) {
        bossCountdown--;
        if (bossCountdown < 0) {
            setupEscapeAnim();
        } else {
            if ((frameCounter & 7) == 0) {
                spawnDefeatExplosion();
            }
        }
        state.x = bossXPos >> 16;
        state.y = bossYPos >> 16;
        animateBoss();
        alignParts();
    }

    private void updateMainSubA(AbstractPlayableSprite player) {
        bossCountdown++;
        if (bossCountdown == 0) {
            bossYVel = 0;
        } else if (bossCountdown < 0) {
            bossYVel += 0x18;
        } else if (bossCountdown < 0x18) {
            bossYVel -= 8;
        } else if (bossCountdown == 0x18) {
            bossYVel = 0;
            int levelMusic = levelManager != null ? levelManager.getCurrentLevelMusicId() : -1;
            if (levelMusic >= 0) {
                AudioManager.getInstance().playMusic(levelMusic);
            }
        } else if (bossCountdown >= 0x20) {
            state.routine = MAIN_SUBC;
        }
        bossMoveObject();
        handleHoveringAndHits();
        state.x = bossXPos >> 16;
        state.y = bossYPos >> 16;
        animateBoss();
        alignParts();
    }

    private void updateMainSubC(AbstractPlayableSprite player) {
        bossXVel = MAIN_ESCAPE_XVEL;
        bossYVel = MAIN_ESCAPE_YVEL;
        Camera camera = Camera.getInstance();
        if (camera.getMaxX() < CAMERA_ESCAPE_MAX_X) {
            camera.setMaxX((short) (camera.getMaxX() + 2));
        } else if (!isOnScreen()) {
            setDestroyed(true);
            GameServices.gameState().setCurrentBossId(0);
            return;
        }
        bossMoveObject();
        handleHoveringAndHits();
        state.x = bossXPos >> 16;
        state.y = bossYPos >> 16;
        animateBoss();
        alignParts();
    }

    private void handleFace(AbstractPlayableSprite player) {
        handleHoveringAndHits();
        if (player != null && player.isHurt()) {
            bossAnim[3] = 0x31;
        }
        if (state.invulnerabilityTimer == getInvulnerabilityDuration() - 1) {
            bossAnim[3] = 0xC0;
        }
    }

    private void handleHoveringAndHits() {
        int yOffset = calculateHoverOffset();
        state.y = (bossYPos >> 16) + yOffset;
        state.x = bossXPos >> 16;

        if (state.routine >= MAIN_SUB8) {
            return;
        }
        if (state.hitCount == 0 && !state.defeated) {
            state.defeated = true;
            GameServices.gameState().addScore(1000);
            onDefeatStarted();
        }
    }

    private void setupEscapeAnim() {
        bossAnim[4] = 0x01;
        bossAnim[5] = 0x00;
        bossAnim[2] = 0x00;
        bossAnim[3] = 0x00;
        state.renderFlags |= RENDER_X_FLIP;
        bossXVel = 0;
        bossYVel = 0;
        state.routine = MAIN_SUBA;
        bossCountdown = -0x12;
    }

    private void bossMoveObject() {
        bossXPos += bossXVel << 8;
        bossYPos += bossYVel << 8;
    }

    private void alignParts() {
        sub2X = state.x;
        sub2Y = state.y;
        sub4X = state.x;
        sub4Y = state.y;
        if (!state.defeated) {
            sub3X = state.x;
            sub3Y = state.y;
            hammerYPos = state.y << 16;
            return;
        }
        dropHammer();
    }

    private void dropHammer() {
        if (bossCountdown > 0x78) {
            return;
        }
        sub3X -= 1;
        hammerYVel += GRAVITY;
        hammerYPos += hammerYVel << 8;
        sub3Y = hammerYPos >> 16;
        if (sub3Y >= 0x540) {
            hammerYVel = 0;
        }
    }

    private void checkHammerCollision(int frameCounter, AbstractPlayableSprite player) {
        if (bossCollisionRoutine == 0 || player == null) {
            return;
        }
        int hammerX = (bossXPos >> 16) - HAMMER_OFFSET_X;
        if ((state.renderFlags & RENDER_X_FLIP) != 0) {
            hammerX += HAMMER_OFFSET_X * 2;
        }
        int hammerY = (bossYPos >> 16) + 4;
        int dx = Math.abs(player.getCentreX() - hammerX);
        int dy = Math.abs(player.getCentreY() - hammerY);
        int rx = player.getXRadius();
        int ry = player.getYRadius();
        if (dx < (rx + HAMMER_HITBOX_WIDTH) && dy < (ry + HAMMER_HITBOX_HEIGHT)) {
            boolean hadRings = player.getRingCount() > 0;
            if (hadRings && !player.hasShield()) {
                levelManager.spawnLostRings(player, frameCounter);
            }
            player.applyHurtOrDeath(hammerX, false, hadRings);
        }
    }

    private void animateBoss() {
        int animIndex = 0;
        if (mainMapFrame != 0) {
            animateBossEntry(0, frame -> mainMapFrame = frame);
            animIndex = 1;
        } else {
            animIndex = 1;
        }
        animateBossEntry(animIndex, frame -> sub2MapFrame = frame);
        animateBossEntry(animIndex + 1, frame -> sub3MapFrame = frame);
        animateBossEntry(animIndex + 2, frame -> sub4MapFrame = frame);
    }

    private void animateBossEntry(int entryIndex, FrameSetter setter) {
        if (entryIndex < 0 || entryIndex * 2 + 1 >= bossAnim.length) {
            return;
        }
        int animIdByte = bossAnim[entryIndex * 2] & 0xFF;
        int animId1 = (animIdByte >> 4) & 0xF;
        int animId2 = animIdByte & 0xF;
        int frameTimerByte = bossAnim[entryIndex * 2 + 1] & 0xFF;
        int animFrame = (frameTimerByte >> 4) & 0xF;
        int animTimer = frameTimerByte & 0xF;

        if (animId1 != animId2) {
            animFrame = 0;
            animTimer = 0;
        }

        animTimer -= 1;
        if (animTimer >= 0) {
            storeBossAnim(entryIndex, animId2, animId2, animFrame, animTimer);
            return;
        }

        int[] script = ARZ_BOSS_ANIMS[animId2];
        int delay = script[0] & 0xFF;
        int frameValue = script[1 + animFrame] & 0xFF;
        if ((frameValue & 0x80) != 0) {
            handleBossCommand(entryIndex, animId2, animFrame, delay, frameValue, script, setter);
            return;
        }
        setter.set(frameValue & 0x7F);
        animFrame += 1;
        storeBossAnim(entryIndex, animId2, animId2, animFrame, delay);
    }

    private void handleBossCommand(int entryIndex, int animId, int animFrame, int delay,
                                   int command, int[] script, FrameSetter setter) {
        if (command == 0xFF) {
            animFrame = 0;
            int frameValue = script[1] & 0xFF;
            setter.set(frameValue & 0x7F);
            animFrame += 1;
            storeBossAnim(entryIndex, animId, animId, animFrame, delay);
            return;
        }
        if (command == 0xFE) {
            state.routine += 2;
            return;
        }
        if (command == 0xFD) {
            int nextAnim = script[2 + animFrame] & 0xF;
            storeBossAnim(entryIndex, animId, nextAnim, animFrame, delay);
            return;
        }
        if (command == 0xFC) {
            int newFrame = script[2 + animFrame] & 0xF;
            int frameValue = script[1 + newFrame] & 0xFF;
            setter.set(frameValue & 0x7F);
            storeBossAnim(entryIndex, animId, animId, newFrame + 1, delay);
        }
    }

    private void storeBossAnim(int entryIndex, int animIdHigh, int animIdLow, int animFrame, int animTimer) {
        int packedAnimId = ((animIdHigh & 0xF) << 4) | (animIdLow & 0xF);
        bossAnim[entryIndex * 2] = packedAnimId & 0xFF;
        int packedFrame = ((animFrame & 0xF) << 4) | (animTimer & 0xF);
        bossAnim[entryIndex * 2 + 1] = packedFrame & 0xFF;
    }

    private void spawnPillars() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }

        ObjectSpawn leftSpawn = new ObjectSpawn(LEFT_PILLAR_X, PILLAR_START_Y,
                Sonic2ObjectIds.ARZ_BOSS, 0x04, 0, false, spawn.rawYWord());
        ARZBossPillar left = new ARZBossPillar(leftSpawn, levelManager, this);
        levelManager.getObjectManager().addDynamicObject(left);

        ObjectSpawn rightSpawn = new ObjectSpawn(RIGHT_PILLAR_X, PILLAR_START_Y,
                Sonic2ObjectIds.ARZ_BOSS, 0x04, RENDER_X_FLIP, false, spawn.rawYWord());
        ARZBossPillar right = new ARZBossPillar(rightSpawn, levelManager, this);
        levelManager.getObjectManager().addDynamicObject(right);
    }

    /**
     * Called by ARZBossPillar to spawn arrow and eyes.
     */
    public void spawnArrowAndEyes(boolean leftPillar) {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }

        int eyesX = leftPillar ? LEFT_EYES_X : RIGHT_EYES_X;
        int eyesFlags = leftPillar ? 0 : RENDER_X_FLIP;
        int offsetIndex = ThreadLocalRandom.current().nextInt(ARROW_OFFSETS.length);
        int eyesY = ARROW_OFFSETS[offsetIndex];

        ObjectSpawn eyesSpawn = new ObjectSpawn(eyesX, eyesY, Sonic2ObjectIds.ARZ_BOSS,
                0x08, eyesFlags, false, spawn.rawYWord());
        ARZBossEyes eyes = new ARZBossEyes(eyesSpawn, levelManager);
        levelManager.getObjectManager().addDynamicObject(eyes);

        ObjectSpawn arrowSpawn = new ObjectSpawn(eyesX, eyesY, Sonic2ObjectIds.ARZ_BOSS,
                0x06, eyesFlags, false, spawn.rawYWord());
        ARZBossArrow arrow = new ARZBossArrow(arrowSpawn, levelManager, this, eyes, !leftPillar);
        levelManager.getObjectManager().addDynamicObject(arrow);
    }

    // ========================================================================
    // PUBLIC ACCESSORS - Used by child objects
    // ========================================================================

    public boolean isHammerActive() {
        return (hammerFlags & 0x01) != 0;
    }

    public void clearHammerFlag() {
        hammerFlags &= ~0x01;
    }

    public boolean isTargetingRight() {
        return targetFlag;
    }

    public boolean isInDefeatSequence() {
        return state.routine >= MAIN_SUB8;
    }

    // ========================================================================
    // COLLISION
    // ========================================================================

    @Override
    public int getCollisionFlags() {
        if (state.routine >= MAIN_SUB8 || state.defeated) {
            return 0;
        }
        if (state.invulnerable) {
            return 0;
        }
        return 0xC0 | (getCollisionSizeIndex() & 0x3F);
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialized) {
            return;
        }

        ObjectRenderManager renderManager = levelManager != null ? levelManager.getObjectRenderManager() : null;
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.ARZ_BOSS_MAIN);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean hFlip = (state.renderFlags & RENDER_X_FLIP) != 0;
        renderer.drawFrameIndex(mainMapFrame, state.x, state.y, hFlip, false);
        renderer.drawFrameIndex(sub2MapFrame, sub2X, sub2Y, hFlip, false);
        renderer.drawFrameIndex(sub3MapFrame, sub3X, sub3Y, hFlip, false);
        renderer.drawFrameIndex(sub4MapFrame, sub4X, sub4Y, hFlip, false);
    }

    @Override
    public int getPriorityBucket() {
        return 2;
    }

    @Override
    protected boolean isOnScreen() {
        Camera camera = Camera.getInstance();
        int screenX = state.x - camera.getX();
        int screenY = state.y - camera.getY();
        return screenX >= -64 && screenX <= camera.getWidth() + 64
                && screenY >= -64 && screenY <= camera.getHeight() + 64;
    }

    @FunctionalInterface
    private interface FrameSetter {
        void set(int frame);
    }
}
