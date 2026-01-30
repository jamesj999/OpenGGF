
package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.game.sonic2.objects.BossExplosionObjectInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.ObjectAnimationState;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.Palette;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseAttackable;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.objects.TouchResponseResult;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.physics.ObjectTerrainUtils;
import uk.co.jamesj999.sonic.physics.TerrainCheckResult;
import uk.co.jamesj999.sonic.physics.TrigLookupTable;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Chemical Plant Zone Boss (Object 0x5D).
 * Implements all Obj5D routines in a single class to preserve ROM behavior.
 */
public class Sonic2CPZBossInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable {

    private static final int ROUTINE_INIT = 0x00;
    private static final int ROUTINE_MAIN = 0x02;
    private static final int ROUTINE_PIPE = 0x04;
    private static final int ROUTINE_PIPE_PUMP = 0x06;
    private static final int ROUTINE_PIPE_RETRACT = 0x08;
    private static final int ROUTINE_DRIPPER = 0x0A;
    private static final int ROUTINE_GUNK = 0x0C;
    private static final int ROUTINE_PIPE_SEGMENT = 0x0E;
    private static final int ROUTINE_CONTAINER = 0x10;
    private static final int ROUTINE_PUMP = 0x12;
    private static final int ROUTINE_FALLING_PARTS = 0x14;
    private static final int ROUTINE_ROBOTNIK = 0x16;
    private static final int ROUTINE_FLAME = 0x18;
    private static final int ROUTINE_SMOKE_PUFF = 0x1A;

    private static final int MAIN_DESCEND = 0x00;
    private static final int MAIN_MOVE_TOWARD_TARGET = 0x02;
    private static final int MAIN_WAIT = 0x04;
    private static final int MAIN_FOLLOW_PLAYER = 0x06;
    private static final int MAIN_EXPLODE = 0x08;
    private static final int MAIN_STOP_EXPLODING = 0x0A;
    private static final int MAIN_RETREAT = 0x0C;

    private static final int MAIN_START_X = 0x2B80;
    private static final int MAIN_START_Y = 0x04B0;
    private static final int MAIN_TARGET_Y = 0x04C0;
    private static final int MAIN_TARGET_RIGHT = 0x2B30;
    private static final int MAIN_TARGET_LEFT = 0x2A50;
    private static final int MAIN_FOLLOW_MIN_X = 0x2A28;
    private static final int MAIN_FOLLOW_MAX_X = 0x2B70;
    private static final int MAIN_RETREAT_CAMERA_MAX_X = 0x2C30;

    private static final int MAIN_DESCEND_YVEL = 0x0100;
    private static final int MAIN_MOVE_VEL = 0x0300;
    private static final int MAIN_RETREAT_XVEL = 0x0400;
    private static final int MAIN_RETREAT_YVEL = -0x0040;
    private static final int MAIN_DEFEAT_TIMER_START = 60 * 3 - 1;
    private static final int MAIN_INVULNERABLE_DURATION = 32;
    private static final int MAIN_COLLISION_SIZE_INDEX = 0x0F;
    private static final int MAIN_HIT_COUNT = 8;

    private static final int STATUS_SIDE = 0x08; // Obj5D_status bit3
    private static final int STATUS_HIT = 0x02;  // Obj5D_status bit1
    private static final int STATUS_GUNK_READY = 0x04; // Obj5D_status bit2

    private static final int STATUS2_ACTION0 = 0x01;
    private static final int STATUS2_ACTION1 = 0x02;
    private static final int STATUS2_ACTION2 = 0x04;
    private static final int STATUS2_ACTION3 = 0x08;
    private static final int STATUS2_ACTION4 = 0x10;
    private static final int STATUS2_ACTION5 = 0x20;
    private static final int STATUS2_RETREAT = 0x40;
    private static final int STATUS2_CONTAINER_INIT = 0x80;

    private static final int CONTAINER_OFFSET_Y = 0x38;
    private static final int CONTAINER_INIT_XVEL = -0x10;

    private static final int PIPE_SEGMENT_COUNT = 0x0C; // ROM bug: should be 0x0B

    private static final int GUNK_FLOOR_Y = 0x0518;
    private static final int GUNK_COLLISION_FLAGS = 0x87;

    private static final int FALLING_PARTS_GRAVITY = 0x38;

    private static final int[] FLAME_FRAMES = {0, -1, 1};
    private static final int[] CONTAINER_FALLOFF_X_OFFSETS = {0x18, 0x30, 0x48};

    private enum AnimationSetType {
        NONE,
        EGGPOD,
        DRIPPER
    }

    private final LevelManager levelManager;
    private Sonic2CPZBossInstance parent;
    private Sonic2CPZBossInstance mainBoss;
    private Sonic2CPZBossInstance robotnik;

    private int routine;
    private int routineSecondary;

    private int x;
    private int y;
    private int xFixed;
    private int yFixed;
    private int xVel;
    private int yVel;

    private int status;
    private int status2;
    private boolean noBalancing;
    private boolean bossDefeated;

    private int collisionFlags;
    private int hitCount;
    private int invulnerableTime;
    private final PaletteFlasher paletteFlasher;

    private int hoverCounter;
    private int defeatTimer;
    private int timer;
    private int timer2;
    private int timer3;
    private int timer4;
    private int yOffset;
    private int pipeSegments;
    private boolean retractFlag;
    private int yRadius;
    private int subtype;

    private int anim;
    private int mappingFrame;
    private int animFrameDuration;
    private AnimationSetType animationSetType = AnimationSetType.NONE;
    private ObjectAnimationState animationState;
    private int priority;
    private int renderFlags;
    private int paletteOverride = -1;

    private int lastFrameCounter;

    public Sonic2CPZBossInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, "CPZ Boss");
        this.levelManager = levelManager;
        this.paletteFlasher = new PaletteFlasher(levelManager);
        this.routine = ROUTINE_INIT;
        this.mainBoss = this;
    }

    private Sonic2CPZBossInstance(ObjectSpawn spawn, LevelManager levelManager, boolean skipInit) {
        super(spawn, "CPZ Boss");
        this.levelManager = levelManager;
        this.paletteFlasher = new PaletteFlasher(levelManager);
        if (!skipInit) {
            this.routine = ROUTINE_INIT;
            this.mainBoss = this;
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }
        lastFrameCounter = frameCounter;
        switch (routine) {
            case ROUTINE_INIT -> initMain();
            case ROUTINE_MAIN -> updateMain(player, frameCounter);
            case ROUTINE_PIPE -> updatePipe();
            case ROUTINE_PIPE_PUMP -> updatePipePump();
            case ROUTINE_PIPE_RETRACT -> updatePipeRetract();
            case ROUTINE_DRIPPER -> updateDripper();
            case ROUTINE_GUNK -> updateGunk();
            case ROUTINE_PIPE_SEGMENT -> updatePipeSegment();
            case ROUTINE_CONTAINER -> updateContainer();
            case ROUTINE_PUMP -> updatePump();
            case ROUTINE_FALLING_PARTS -> updateFallingParts();
            case ROUTINE_ROBOTNIK -> updateRobotnik(player);
            case ROUTINE_FLAME -> updateFlame();
            case ROUTINE_SMOKE_PUFF -> updateSmokePuff();
            default -> {
                // no-op
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager != null ? levelManager.getObjectRenderManager() : null;
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = switch (routine) {
            case ROUTINE_MAIN, ROUTINE_ROBOTNIK -> renderManager.getRenderer(Sonic2ObjectArtKeys.CPZ_BOSS_EGGPOD);
            case ROUTINE_FLAME -> renderManager.getRenderer(Sonic2ObjectArtKeys.CPZ_BOSS_JETS);
            case ROUTINE_SMOKE_PUFF -> renderManager.getRenderer(Sonic2ObjectArtKeys.CPZ_BOSS_SMOKE);
            case ROUTINE_PIPE,
                    ROUTINE_PIPE_PUMP,
                    ROUTINE_PIPE_RETRACT,
                    ROUTINE_DRIPPER,
                    ROUTINE_GUNK,
                    ROUTINE_PIPE_SEGMENT,
                    ROUTINE_CONTAINER,
                    ROUTINE_PUMP,
                    ROUTINE_FALLING_PARTS -> renderManager.getRenderer(Sonic2ObjectArtKeys.CPZ_BOSS_PARTS);
            default -> null;
        };

        if (renderer == null || !renderer.isReady()) {
            return;
        }

        if (mappingFrame < 0) {
            return;
        }

        boolean flipped = (renderFlags & 1) != 0;
        renderer.drawFrameIndex(mappingFrame, x, y, flipped, false, paletteOverride);
    }

    @Override
    public int getCollisionFlags() {
        if (routine == ROUTINE_MAIN) {
            if (routineSecondary >= MAIN_EXPLODE || bossDefeated || invulnerableTime > 0 || collisionFlags == 0) {
                return 0;
            }
            return 0xC0 | (MAIN_COLLISION_SIZE_INDEX & 0x3F);
        }
        return collisionFlags;
    }

    @Override
    public int getCollisionProperty() {
        if (routine == ROUTINE_MAIN) {
            return hitCount;
        }
        return 0;
    }

    @Override
    public void onPlayerAttack(AbstractPlayableSprite player, TouchResponseResult result) {
        if (routine != ROUTINE_MAIN || collisionFlags == 0 || invulnerableTime > 0 || bossDefeated) {
            return;
        }
        hitCount--;
        collisionFlags = 0;
        status |= STATUS_HIT;
        invulnerableTime = MAIN_INVULNERABLE_DURATION;
        paletteFlasher.startFlash();
        AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_BOSS_HIT);
        if (hitCount <= 0) {
            triggerDefeat();
        }
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(
                x,
                y,
                spawn.objectId(),
                spawn.subtype(),
                renderFlags,
                spawn.respawnTracked(),
                spawn.rawYWord());
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
    public int getPriorityBucket() {
        return RenderPriority.clamp(priority);
    }

    private void initMain() {
        routine = ROUTINE_MAIN;
        routineSecondary = MAIN_DESCEND;
        priority = 3;
        collisionFlags = MAIN_COLLISION_SIZE_INDEX;
        hitCount = MAIN_HIT_COUNT;
        status = 0;
        status2 = 0;
        noBalancing = false;
        bossDefeated = false;
        hoverCounter = 0;
        invulnerableTime = 0;
        setPosition(MAIN_START_X, MAIN_START_Y);
        xVel = 0;
        yVel = 0;
        anim = 0;
        mappingFrame = 0;
        animationSetType = AnimationSetType.EGGPOD;
        paletteOverride = -1;
        renderFlags = 0;

        Sonic2CPZBossInstance robotnikInstance = spawnRobotnik();
        if (robotnikInstance != null) {
            robotnik = robotnikInstance;
            parent = robotnikInstance;
        }
        spawnFlame();
        spawnPump();
        spawnContainer();
        spawnPipe();
    }

    private void updateMain(AbstractPlayableSprite player, int frameCounter) {
        lookAtPlayer(player);
        switch (routineSecondary) {
            case MAIN_DESCEND -> updateMainDescend();
            case MAIN_MOVE_TOWARD_TARGET -> updateMainMoveTowardTarget();
            case MAIN_WAIT -> updateMainWait();
            case MAIN_FOLLOW_PLAYER -> updateMainFollowPlayer(player);
            case MAIN_EXPLODE -> updateMainExplode(frameCounter);
            case MAIN_STOP_EXPLODING -> updateMainStopExploding();
            case MAIN_RETREAT -> updateMainRetreat();
            default -> {
            }
        }

        animate(AnimationSetType.EGGPOD);
    }

    private void updateMainDescend() {
        yVel = MAIN_DESCEND_YVEL;
        applyMainMove();
        if ((yFixed >> 16) == MAIN_TARGET_Y) {
            yVel = 0;
            routineSecondary = MAIN_MOVE_TOWARD_TARGET;
        }
        updateMainPositionAndCollision();
    }

    private void updateMainMoveTowardTarget() {
        int target = (status & STATUS_SIDE) != 0 ? MAIN_TARGET_LEFT : MAIN_TARGET_RIGHT;
        int baseX = xFixed >> 16;
        int diff = Math.abs(target - baseX);
        if (diff <= 3) {
            if ((yFixed >> 16) == MAIN_TARGET_Y) {
                routineSecondary = MAIN_WAIT;
                status ^= STATUS_SIDE;
                status2 |= STATUS2_ACTION0;
            }
        } else {
            xVel = target > baseX ? MAIN_MOVE_VEL : -MAIN_MOVE_VEL;
        }
        applyMainMove();
        updateMainPositionAndCollision();
    }

    private void updateMainWait() {
        if ((status2 & STATUS2_ACTION0) != 0) {
            updateMainPositionAndCollision();
            return;
        }
        routineSecondary = MAIN_FOLLOW_PLAYER;
        updateMainPositionAndCollision();
    }

    private void updateMainFollowPlayer(AbstractPlayableSprite player) {
        int target = xFixed >> 16;
        if (player != null) {
            target = player.getCentreX() + 0x4C;
        }
        int baseX = xFixed >> 16;
        if (target > baseX) {
            xFixed += 0x10000;
        } else if (target < baseX) {
            xFixed -= 0x10000;
        }
        if ((xFixed >> 16) < MAIN_FOLLOW_MIN_X) {
            xFixed = MAIN_FOLLOW_MIN_X << 16;
        } else if ((xFixed >> 16) > MAIN_FOLLOW_MAX_X) {
            xFixed = MAIN_FOLLOW_MAX_X << 16;
        }
        updateMainPositionAndCollision();
    }

    private void updateMainExplode(int frameCounter) {
        defeatTimer--;
        if (defeatTimer >= 0) {
            if ((frameCounter & 7) == 0) {
                spawnBossExplosion(x, y, true);
            }
        } else {
            renderFlags |= 1;
            noBalancing = false;
            xVel = 0;
            routineSecondary = MAIN_STOP_EXPLODING;
            defeatTimer = -0x26;
        }
        updateMainPositionAndCollision();
    }

    private void updateMainStopExploding() {
        defeatTimer++;
        if (defeatTimer == 0) {
            yVel = 0;
        } else if (defeatTimer < 0) {
            yVel += 0x18;
        } else {
            if (defeatTimer < 0x30) {
                yVel -= 8;
            } else if (defeatTimer == 0x30) {
                yVel = 0;
                AudioManager.getInstance().playMusic(Sonic2AudioConstants.MUS_CHEMICAL_PLANT);
            } else if (defeatTimer >= 0x38) {
                routineSecondary = MAIN_RETREAT;
            }
        }
        applyMainMove();
        updateMainPositionAndCollision();
    }

    private void updateMainRetreat() {
        status2 |= STATUS2_RETREAT;
        xVel = MAIN_RETREAT_XVEL;
        yVel = MAIN_RETREAT_YVEL;
        Camera camera = Camera.getInstance();
        if (camera != null) {
            if (camera.getMaxX() < MAIN_RETREAT_CAMERA_MAX_X) {
                camera.setMaxX((short) (camera.getMaxX() + 2));
            } else if (!isOnScreen()) {
                deleteMain();
                return;
            }
        }
        applyMainMove();
        updateMainPositionAndCollision();
    }

    private void updateMainPositionAndCollision() {
        int baseX = xFixed >> 16;
        int baseY = yFixed >> 16;
        int hover = TrigLookupTable.sinHex(hoverCounter) >> 6;
        x = baseX;
        y = baseY + hover;
        hoverCounter = (hoverCounter + 2) & 0xFF;

        if (routineSecondary >= MAIN_EXPLODE) {
            return;
        }

        if (noBalancing && !bossDefeated) {
            triggerDefeat();
            return;
        }

        if (invulnerableTime > 0) {
            paletteFlasher.update();
            invulnerableTime--;
            if (invulnerableTime == 0) {
                collisionFlags = MAIN_COLLISION_SIZE_INDEX;
                status &= ~STATUS_HIT;
                paletteFlasher.stopFlash();
            }
        }
    }

    private void lookAtPlayer(AbstractPlayableSprite player) {
        if (routineSecondary >= MAIN_EXPLODE || player == null) {
            return;
        }
        int dx = player.getCentreX() - x;
        if (dx > 0) {
            renderFlags |= 1;
        } else {
            renderFlags &= ~1;
        }
    }

    private void applyMainMove() {
        xFixed += (xVel << 8);
        yFixed += (yVel << 8);
    }

    private void triggerDefeat() {
        bossDefeated = true;
        noBalancing = true;
        routineSecondary = MAIN_EXPLODE;
        defeatTimer = MAIN_DEFEAT_TIMER_START;
        GameServices.gameState().addScore(1000);
        if (robotnik != null) {
            robotnik.setAnim(4);
        }
    }

    private void deleteMain() {
        if (robotnik != null) {
            robotnik.setDestroyed(true);
        }
        GameServices.gameState().setCurrentBossId(0);
        setDestroyed(true);
    }

    private void updatePipe() {
        switch (routineSecondary) {
            case 0 -> updatePipeWait();
            case 2 -> updatePipeExtend();
            default -> updatePipeSegment();
        }
    }

    private void updatePipeWait() {
        if (parent == null) {
            setDestroyed(true);
            return;
        }
        if ((parent.status2 & STATUS2_ACTION0) == 0) {
            return;
        }
        x = parent.x;
        y = parent.y + 0x18;
        renderFlags = parent.renderFlags;
        status = parent.status;
        pipeSegments = PIPE_SEGMENT_COUNT;
        routineSecondary = 2;
        pipeSegments--;
        int segmentIndex = (PIPE_SEGMENT_COUNT - 1) - pipeSegments;
        yOffset = segmentIndex * 8;
        anim = 1;
        animationSetType = AnimationSetType.DRIPPER;
        paletteOverride = -1;
        updatePipeSegment();
    }

    private void updatePipeExtend() {
        if (parent == null) {
            setDestroyed(true);
            return;
        }
        if (levelManager == null || levelManager.getObjectManager() == null) {
            updatePipeSegment();
            return;
        }
        int nextSegments = pipeSegments - 1;
        if (nextSegments < 0) {
            routineSecondary = 0;
            routine = ROUTINE_PIPE_PUMP;
            updatePipeSegment();
            return;
        }
        int segmentIndex = (PIPE_SEGMENT_COUNT - 1) - nextSegments;
        int offset = segmentIndex * 8;
        Sonic2CPZBossInstance segment = spawnPipeSegment(offset);
        if (segment != null) {
            pipeSegments = nextSegments;
        }
        updatePipeSegment();
    }

    private void updatePipePump() {
        switch (routineSecondary) {
            case 0 -> updatePipePumpInit();
            case 2 -> updatePipePumpAnimate();
            case 4 -> updatePipePumpEnd();
            default -> updatePipePumpAnimate();
        }
    }

    private void updatePipePumpInit() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            updatePipeSegment();
            return;
        }
        Sonic2CPZBossInstance pump = spawnPipePumpObject();
        if (pump == null) {
            updatePipeSegment();
            return;
        }
        spawnDripperFromPipe();
        routine = ROUTINE_PIPE_SEGMENT;
        routineSecondary = 0;
        anim = 1;
        updatePipeSegment();
    }

    private void updatePipePumpAnimate() {
        if (parent == null) {
            setDestroyed(true);
            return;
        }
        Sonic2CPZBossInstance boss = mainBoss;
        if (boss != null && boss.noBalancing) {
            setDestroyed(true);
            return;
        }
        x = parent.x;
        y = parent.y;
        renderFlags = parent.renderFlags;
        status = parent.status;
        timer--;
        if (timer == 0) {
            timer = 0x12;
            yOffset -= 8;
            if (yOffset < 0) {
                timer = 6;
                routineSecondary = 4;
                return;
            }
            if (yOffset == 0) {
                anim = 3;
                timer = 0x0C;
            }
        }
        y += yOffset;
        animate(AnimationSetType.DRIPPER);
    }

    private void updatePipePumpEnd() {
        timer--;
        if (timer != 0) {
            return;
        }
        timer3--;
        if (timer3 != 0) {
            anim = 2;
            timer = 0x12;
            routineSecondary = 2;
            yOffset = 0x58;
            return;
        }
        if (parent != null) {
            parent.routine = ROUTINE_PIPE_RETRACT;
            parent.yOffset = 0x58;
        }
        setDestroyed(true);
    }

    private void updatePipeRetract() {
        if (parent == null) {
            setDestroyed(true);
            return;
        }
        if (!retractFlag) {
            int targetY = y + yOffset;
            Sonic2CPZBossInstance match = findPipeSegmentAtY(targetY);
            if (match != null) {
                match.noBalancing = true;
                yOffset -= 8;
                if (yOffset == 0) {
                    retractFlag = true;
                }
            }
        } else if (yOffset == 0) {
            noBalancing = true;
        }
        updatePipeSegment();
    }

    private Sonic2CPZBossInstance findPipeSegmentAtY(int targetY) {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return null;
        }
        for (ObjectInstance instance : levelManager.getObjectManager().getActiveObjects()) {
            if (instance instanceof Sonic2CPZBossInstance bossInstance) {
                if (bossInstance.getY() != targetY) {
                    continue;
                }
                if (bossInstance.mainBoss != null && mainBoss != null
                        && bossInstance.mainBoss != mainBoss) {
                    continue;
                }
                return bossInstance;
            }
        }
        return null;
    }

    private void updatePipeSegment() {
        Sonic2CPZBossInstance pipeParent = parent;
        if (pipeParent == null) {
            setDestroyed(true);
            return;
        }
        if (mainBoss != null && mainBoss.noBalancing) {
            routine = ROUTINE_FALLING_PARTS;
            timer = randomFallTimer();
            xVel = randomPipeVelocity();
            yVel = -0x380;
            yFixed = y << 16;
            return;
        }

        x = pipeParent.x;
        y = pipeParent.y;
        renderFlags = pipeParent.renderFlags;
        status = pipeParent.status;
        if (routineSecondary == 4) {
            y += 0x18;
        }
        if (noBalancing) {
            setDestroyed(true);
            return;
        }
        y += yOffset;
        anim = 1;
        animate(AnimationSetType.DRIPPER);
    }

    private void updateDripper() {
        if (noBalancing) {
            setDestroyed(true);
            return;
        }
        switch (routineSecondary) {
            case 0 -> updateDripperInit();
            case 2 -> updateDripperMain();
            case 4 -> updateDripperEnd();
            default -> updateDripperMain();
        }
    }

    private void updateDripperInit() {
        routineSecondary = 2;
        paletteOverride = 3;
        anim = 4;
        timer = 0x0F;
        if (parent != null) {
            x = parent.x;
            y = parent.y;
        }
        animationSetType = AnimationSetType.DRIPPER;
        updateDripperMain();
    }

    private void updateDripperMain() {
        timer--;
        if (timer == 0) {
            anim = 5;
            timer = 4;
            routineSecondary = 4;
            if (parent != null) {
                x = parent.x - 2;
                y = parent.y - 0x24;
            }
            return;
        }
        if (parent == null) {
            setDestroyed(true);
            return;
        }
        x = parent.x;
        y = parent.y;
        status = parent.status;
        renderFlags = parent.renderFlags;
        animate(AnimationSetType.DRIPPER);
    }

    private void updateDripperEnd() {
        timer--;
        if (timer == 0) {
            routineSecondary = 0;
            if (parent != null) {
                parent.status2 |= STATUS2_ACTION1;
            }
            timer4++;
            if (timer4 >= 0x0C) {
                setDestroyed(true);
            }
            return;
        }
        if (parent == null) {
            setDestroyed(true);
            return;
        }
        x = parent.x - 2;
        y = parent.y - 0x24;
        if ((renderFlags & 1) != 0) {
            x += 4;
        }
        animate(AnimationSetType.DRIPPER);
    }

    private void updateGunk() {
        switch (routineSecondary) {
            case 0 -> initGunk();
            case 2 -> updateGunkMain();
            case 4 -> updateGunkDroplets();
            case 6 -> updateGunkDelay();
            case 8 -> updateGunkStuck();
            default -> updateGunkMain();
        }
    }

    private void initGunk() {
        routineSecondary = 2;
        syncFixedFromPosition();
        yRadius = 0x20;
        anim = 0x19;
        yVel = 0;
        paletteOverride = 3;
        animationSetType = AnimationSetType.DRIPPER;
        Sonic2CPZBossInstance boss = mainBoss;
        if (boss != null && (boss.status & STATUS_GUNK_READY) != 0) {
            boss.status &= ~STATUS_GUNK_READY;
            routineSecondary = 6;
            timer2 = 9;
        }
        updateGunkMain();
    }

    private void updateGunkMain() {
        applyObjectMoveAndFall();
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(x, y, yRadius);
        if (floor.hasCollision() && floor.distance() < 0) {
            y += floor.distance();
            // Only reset boss state if not already defeated
            if (mainBoss != null && !mainBoss.bossDefeated) {
                mainBoss.status2 |= STATUS2_ACTION2;
                mainBoss.status2 |= STATUS2_ACTION4;
                mainBoss.routineSecondary = MAIN_MOVE_TOWARD_TARGET;
            }
            routineSecondary = 4;
            subtype = 0;
            AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_MEGA_MACK_DROP);
            return;
        }
        if (y >= GUNK_FLOOR_Y) {
            gunkOffScreen();
            return;
        }
        animate(AnimationSetType.DRIPPER);
    }

    private void gunkOffScreen() {
        // Only reset boss state if not already defeated
        if (mainBoss != null && !mainBoss.bossDefeated) {
            mainBoss.status2 |= STATUS2_ACTION2;
            mainBoss.status2 |= STATUS2_ACTION4;
            mainBoss.routineSecondary = MAIN_MOVE_TOWARD_TARGET;
        }
        setDestroyed(true);
    }

    private void updateGunkDelay() {
        timer2--;
        if (timer2 < 0) {
            priority = 2;
            mappingFrame = 0x25;
            if (mainBoss != null) {
                x = mainBoss.x;
                y = mainBoss.y;
            }
            routineSecondary = 8;
            animFrameDuration = 8;
            updateGunkStuck();
            return;
        }
        applyObjectMove();
        animate(AnimationSetType.DRIPPER);
    }

    private void updateGunkStuck() {
        animFrameDuration--;
        if (animFrameDuration < 0) {
            mappingFrame++;
            animFrameDuration = 8;
            if (mappingFrame > 0x27) {
                gunkOffScreen();
                return;
            }
            if (mappingFrame == 0x27) {
                animFrameDuration += 0x0C;
            }
        }
        if (mainBoss != null) {
            x = mainBoss.x;
            y = mainBoss.y;
        }
    }

    private void updateGunkDroplets() {
        if (subtype != 0) {
            updateGunkDropletsMove();
            return;
        }
        y += 0x18;
        x += 0x0C;
        if ((renderFlags & 1) != 0) {
            x -= 0x18;
        }
        yRadius = 4;
        subtype = 1;
        mappingFrame = 9;
        yVel = -(yVel >> 1);
        int random = ThreadLocalRandom.current().nextInt(0x10000);
        int xRand = (random >> 6);
        if (xRand >= 0) {
            xRand += 0x200;
        }
        xRand += -0x100;
        xVel = xRand;
        collisionFlags = 0;

        for (int i = 0; i < 4; i++) {
            Sonic2CPZBossInstance droplet = spawnGunkDroplet();
            if (droplet == null) {
                break;
            }
        }
    }

    private Sonic2CPZBossInstance spawnGunkDroplet() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return null;
        }
        ObjectSpawn dropletSpawn = new ObjectSpawn(
                x,
                y,
                Sonic2ObjectIds.CPZ_BOSS,
                0,
                renderFlags,
                false,
                spawn.rawYWord());
        Sonic2CPZBossInstance droplet = new Sonic2CPZBossInstance(dropletSpawn, levelManager, true);
        droplet.mainBoss = mainBoss;
        droplet.parent = this;
        droplet.routine = ROUTINE_GUNK;
        droplet.routineSecondary = 4;
        droplet.subtype = 1;
        droplet.mappingFrame = 9;
        droplet.priority = 2;
        droplet.paletteOverride = 3;
        droplet.yRadius = 4;
        droplet.x = x;
        droplet.y = y;
        droplet.xFixed = x << 16;
        droplet.yFixed = y << 16;
        droplet.yVel = yVel;
        droplet.collisionFlags = collisionFlags;
        int random = ThreadLocalRandom.current().nextInt(0x10000);
        int xRand = (random >> 6);
        if (xRand >= 0) {
            xRand += 0x80;
        }
        xRand += -0x80;
        droplet.xVel = xRand;
        int extra = ThreadLocalRandom.current().nextInt(0x400);
        droplet.yVel -= extra;
        levelManager.getObjectManager().addDynamicObject(droplet);
        return droplet;
    }

    private void updateGunkDropletsMove() {
        applyObjectMoveAndFall();
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(x, y, yRadius);
        if (floor.hasCollision() && floor.distance() < 0) {
            setDestroyed(true);
            return;
        }
        if (!isOnScreen(32)) {
            setDestroyed(true);
        }
    }

    private void updateContainer() {
        switch (routineSecondary) {
            case 0 -> updateContainerInit();
            case 2 -> updateContainerMain();
            case 4 -> updateContainerFloor();
            case 6 -> updateContainerExtend();
            case 8 -> updateContainerFloor2();
            case 0x0A -> updateContainerFallOff();
            default -> updateContainerMain();
        }
    }

    private void updateContainerInit() {
        if (parent == null) {
            setDestroyed(true);
            return;
        }
        if ((parent.status2 & STATUS2_CONTAINER_INIT) == 0) {
            parent.status2 |= STATUS2_CONTAINER_INIT;
            spawnContainerFloor();
            spawnContainerExtend();
        }
        routineSecondary = 2;
    }

    private void updateContainerMain() {
        if (parent == null) {
            setDestroyed(true);
            return;
        }
        x = parent.x;
        y = parent.y - CONTAINER_OFFSET_Y;
        if (noBalancing) {
            routineSecondary = 0x0A;
        }
        if ((parent.status2 & STATUS2_ACTION2) != 0) {
            updateContainerMovement();
            updateContainerDropTrigger();
        } else if ((parent.status2 & STATUS2_ACTION5) != 0) {
            timer2--;
            if (timer2 == 0) {
                parent.status2 &= ~STATUS2_ACTION5;
                parent.status2 |= STATUS2_ACTION3;
                parent.status2 |= STATUS2_ACTION4;
            }
        }
        updateContainerPosition();
    }

    private void updateContainerPosition() {
        if (parent == null) {
            return;
        }
        status = parent.status;
        renderFlags = parent.renderFlags;
        int offset = xVel;
        if ((renderFlags & 1) != 0) {
            offset = -offset;
        }
        x += offset;
        animate(AnimationSetType.DRIPPER);
    }

    private void updateContainerMovement() {
        int direction = (parent.status2 & STATUS2_ACTION4) != 0 ? 1 : -1;

        // Only reset if xVel is at initial position AND ACTION4 is set
        // ROM: beq.s loc_2E552 - if ACTION4 wasn't set, branch to acceleration
        if (xVel == CONTAINER_INIT_XVEL && (parent.status2 & STATUS2_ACTION4) != 0) {
            // Reset cycle
            parent.status2 &= ~STATUS2_ACTION4;
            parent.status2 &= ~STATUS2_ACTION2;
            // Clear CONTAINER_INIT so new floor/extend can be spawned
            parent.status2 &= ~STATUS2_CONTAINER_INIT;
            routineSecondary = 0;
            spawnPipe();
            return;
        }

        // Acceleration code - always reached unless reset happened
        if (xVel >= -0x28) {
            anim = 6;
        } else if (xVel >= -0x40) {
            anim = 7;
        } else {
            anim = 8;
        }

        // Cap speed - don't accelerate past -0x58
        // ROM: cmpi.w #-$58,d1 / blt.s loc_2E57E / bgt.s loc_2E578
        if (xVel <= -0x58) {
            if ((parent.status2 & STATUS2_ACTION4) == 0) {
                return;  // At cap and not reversing, do nothing
            }
            // ACTION4 is set, decelerate (direction = 1)
        }
        xVel += direction;
    }

    private void updateContainerDropTrigger() {
        if ((parent.status2 & STATUS2_ACTION3) != 0 || (parent.status2 & STATUS2_ACTION4) != 0) {
            return;
        }
        if (xVel >= -0x14) {
            if ((parent.status & STATUS_HIT) == 0) {
                return;
            }
            parent.status &= ~STATUS_HIT;
            parent.status |= STATUS_GUNK_READY;
            startContainerDump();
            return;
        }
        if (xVel >= -0x40) {
            return;
        }
        AbstractPlayableSprite player = Camera.getInstance().getFocusedSprite();
        int d1 = player != null ? player.getCentreX() - 8 : x;
        if ((renderFlags & 1) != 0) {
            d1 += xVel;
            d1 -= x;
            if (d1 > 0) {
                return;
            }
            if (d1 >= -0x18) {
                startContainerDump();
            }
            return;
        }
        d1 -= xVel;
        d1 -= x;
        if (d1 < 0) {
            return;
        }
        if (d1 <= 0x18) {
            startContainerDump();
        }
    }

    private void startContainerDump() {
        parent.status2 |= STATUS2_ACTION5;
        parent.status2 &= ~STATUS2_ACTION2;
        timer2 = 0x12;
        spawnContainerFloor2();
    }

    private void updateContainerFloor() {
        if (noBalancing) {
            setDestroyed(true);
            return;
        }
        if (parent != null && parent.parent != null) {
            if ((parent.parent.status2 & STATUS2_ACTION5) != 0 && anim == 9) {
                anim = 0x0A;
            }
        }
        updateContainerFloorEnd();
    }

    private void updateContainerExtend() {
        if (noBalancing) {
            setDestroyed(true);
            return;
        }
        if (parent == null || parent.parent == null) {
            setDestroyed(true);
            return;
        }
        Sonic2CPZBossInstance boss = parent.parent;
        if ((boss.status2 & STATUS2_ACTION3) != 0) {
            boss.status2 &= ~STATUS2_ACTION3;
            routine = ROUTINE_GUNK;
            routineSecondary = 0;
            collisionFlags = GUNK_COLLISION_FLAGS;
            paletteOverride = 3;
            syncFixedFromPosition();
            return;
        }
        // ROM: bclr #1,Obj5D_status2(a1) / bne.s + / tst.b anim(a0) / bne.s Floor_End / rts
        // When ACTION1 is NOT set:
        //   - If anim == 0: return without rendering (wait state)
        //   - If anim != 0: go to Floor_End (render but DON'T increment)
        // When ACTION1 IS set:
        //   - Clear ACTION1, set anim to 0x0B if it was 0, then increment
        if ((boss.status2 & STATUS2_ACTION1) == 0) {
            if (anim == 0) {
                // Wait state - don't render (set mappingFrame to -1)
                mappingFrame = -1;
                return;
            }
            // anim != 0 but ACTION1 not set - render but don't increment
            updateContainerFloorEnd();
            return;
        }
        // ACTION1 was set - clear it and advance animation
        boss.status2 &= ~STATUS2_ACTION1;
        if (anim == 0) {
            anim = 0x0B;
        }
        anim += 1;
        if (anim >= 0x17) {
            boss.status2 &= ~STATUS2_ACTION0;
            boss.status2 |= STATUS2_ACTION2;
        }
        updateContainerFloorEnd();
    }

    private void updateContainerFloor2() {
        if (noBalancing) {
            setDestroyed(true);
            return;
        }
        timer2--;
        if (timer2 == 0) {
            setDestroyed(true);
            return;
        }
        updateContainerFloorEnd();
    }

    private void updateContainerFallOff() {
        if (parent == null) {
            setDestroyed(true);
            return;
        }
        timer = 0x1E;
        x = parent.x;
        y = parent.y - CONTAINER_OFFSET_Y;
        int offset = xVel;
        if ((renderFlags & 1) != 0) {
            offset = -offset;
        }
        x += offset;
        mappingFrame = 0x20;
        routine = ROUTINE_FALLING_PARTS;
        xVel = randomPipeVelocity();
        yVel = -0x380;
        yFixed = y << 16;

        int count;
        if (xVel >= -0x18) {
            count = 0;
        } else if (xVel >= -0x30) {
            count = 1;
        } else if (xVel >= -0x48) {
            count = 2;
        } else {
            count = 3;
        }
        for (int i = count - 1; i >= 0; i--) {
            spawnContainerPiece(CONTAINER_FALLOFF_X_OFFSETS[i]);
        }
    }

    private void spawnContainerPiece(int offset) {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn pieceSpawn = new ObjectSpawn(
                x,
                y + 8,
                Sonic2ObjectIds.CPZ_BOSS,
                0,
                renderFlags,
                false,
                spawn.rawYWord());
        Sonic2CPZBossInstance piece = new Sonic2CPZBossInstance(pieceSpawn, levelManager, true);
        piece.mainBoss = mainBoss;
        piece.parent = null;
        piece.routine = ROUTINE_FALLING_PARTS;
        piece.mappingFrame = 0x21;
        piece.priority = 2;
        piece.renderFlags = renderFlags;
        piece.x = x + ((renderFlags & 1) != 0 ? -offset : offset);
        piece.y = y + 8;
        piece.xVel = randomPipeVelocity();
        piece.yVel = -0x380;
        piece.yFixed = piece.y << 16;
        piece.timer = randomFallTimer();
        levelManager.getObjectManager().addDynamicObject(piece);
    }

    private void updateContainerFloorEnd() {
        if (parent == null) {
            setDestroyed(true);
            return;
        }
        x = parent.x;
        y = parent.y;
        renderFlags = parent.renderFlags;
        status = parent.status;
        animate(AnimationSetType.DRIPPER);
    }

    private void updatePump() {
        if (mainBoss != null && mainBoss.noBalancing) {
            splitPumpIntoFallingParts();
            return;
        }
        if (parent == null) {
            setDestroyed(true);
            return;
        }
        x = parent.x;
        y = parent.y;
        renderFlags = parent.renderFlags;
        status = parent.status;
        animate(AnimationSetType.DRIPPER);
    }

    private void splitPumpIntoFallingParts() {
        mappingFrame = 0x22;
        routine = ROUTINE_FALLING_PARTS;
        timer = 0x78;
        xVel = randomPipeVelocity();
        yVel = -0x380;
        yFixed = y << 16;
        for (int i = 0; i < 2; i++) {
            spawnPumpPiece(0x23 + i);
        }
    }

    private void spawnPumpPiece(int frame) {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn pieceSpawn = new ObjectSpawn(
                x,
                y,
                Sonic2ObjectIds.CPZ_BOSS,
                0,
                renderFlags,
                false,
                spawn.rawYWord());
        Sonic2CPZBossInstance piece = new Sonic2CPZBossInstance(pieceSpawn, levelManager, true);
        piece.mainBoss = mainBoss;
        piece.routine = ROUTINE_FALLING_PARTS;
        piece.mappingFrame = frame;
        piece.priority = 2;
        piece.renderFlags = renderFlags;
        piece.x = x;
        piece.y = y;
        piece.xVel = randomPipeVelocity();
        piece.yVel = -0x380;
        piece.yFixed = y << 16;
        piece.timer = randomFallTimer();
        levelManager.getObjectManager().addDynamicObject(piece);
    }

    private void updateFallingParts() {
        if (timer != -7) {
            timer--;
            if (timer > 0) {
                return;
            }
            spawnBossExplosion(x, y, false);
            timer = -7;
            timer2 = 0x1E;
        }
        timer2--;
        if (timer2 >= 0) {
            return;
        }
        applyFallingPartsMove();
        if (yFixed >= (0x580 << 16)) {
            setDestroyed(true);
        }
    }

    private void updateRobotnik(AbstractPlayableSprite player) {
        if (parent == null) {
            setDestroyed(true);
            return;
        }
        x = parent.x;
        y = parent.y;
        status = parent.status;
        renderFlags = parent.renderFlags;
        if (mainBoss != null && mainBoss.invulnerableTime == 0x1F) {
            anim = 2;
        }
        if (player != null && player.isHurt()) {
            anim = 3;
        }
        animate(AnimationSetType.EGGPOD);
    }

    private void updateFlame() {
        if (mappingFrame < 0) {
            mappingFrame = 0;
        }
        if (mainBoss != null && mainBoss.noBalancing) {
            if (mainBoss.status2 != 0 && (mainBoss.status2 & STATUS2_RETREAT) != 0) {
                routineSecondary += 2;
            }
            return;
        }
        if (parent == null) {
            setDestroyed(true);
            return;
        }
        x = parent.x;
        y = parent.y;
        status = parent.status;
        renderFlags = parent.renderFlags;
        animFrameDuration--;
        if (animFrameDuration < 0) {
            animFrameDuration = 1;
            int next = timer2 + 1;
            if (next > 2) {
                next = 0;
            }
            timer2 = next;
            mappingFrame = FLAME_FRAMES[next];
            if (mappingFrame < 0) {
                return;
            }
        }
    }

    private void updateSmokePuff() {
        animFrameDuration--;
        if (animFrameDuration < 0) {
            animFrameDuration = 5;
            mappingFrame++;
            if (mappingFrame == 4) {
                mappingFrame = 0;
                if (parent == null || parent.isDestroyed()) {
                    setDestroyed(true);
                    return;
                }
                x = parent.x - 0x28;
                y = parent.y + 4;
            }
        }
        if (parent != null) {
            x = parent.x - 0x28;
            y = parent.y + 4;
        }
    }

    private void animate(AnimationSetType setType) {
        if (setType == AnimationSetType.NONE) {
            return;
        }
        if (animationSetType != setType || animationState == null) {
            animationSetType = setType;
            animationState = new ObjectAnimationState(
                    setType == AnimationSetType.EGGPOD
                            ? CPZBossAnimations.getEggpodAnimations()
                            : CPZBossAnimations.getDripperAnimations(),
                    anim,
                    Math.max(mappingFrame, 0));
        }
        animationState.setAnimId(anim);
        animationState.update();
        mappingFrame = animationState.getMappingFrame();
    }

    private void setAnim(int anim) {
        this.anim = anim;
        if (animationState != null) {
            animationState.setAnimId(anim);
        }
    }

    private void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        syncFixedFromPosition();
    }

    private void syncFixedFromPosition() {
        xFixed = x << 16;
        yFixed = y << 16;
    }

    private void syncPositionFromFixed() {
        x = xFixed >> 16;
        y = yFixed >> 16;
    }

    private void applyObjectMove() {
        xFixed += (xVel << 8);
        yFixed += (yVel << 8);
        syncPositionFromFixed();
    }

    private void applyObjectMoveAndFall() {
        xFixed += (xVel << 8);
        yFixed += (yVel << 8);
        yVel += FALLING_PARTS_GRAVITY;
        syncPositionFromFixed();
    }

    private void applyFallingPartsMove() {
        x += xVel;
        yFixed += (yVel << 8);
        yVel += FALLING_PARTS_GRAVITY;
        y = yFixed >> 16;
    }

    private int randomPipeVelocity() {
        int random = ThreadLocalRandom.current().nextInt(0x10000);
        int result = (short) (random >> 8);
        result >>= 6;
        return result;
    }

    private int randomFallTimer() {
        int random = ThreadLocalRandom.current().nextInt();
        return ((random >>> 16) + 0x1E) & 0x7F;
    }

    private Sonic2CPZBossInstance spawnPipeSegment(int offset) {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return null;
        }
        ObjectSpawn segmentSpawn = new ObjectSpawn(
                x,
                y,
                Sonic2ObjectIds.CPZ_BOSS,
                0,
                renderFlags,
                false,
                spawn.rawYWord());
        Sonic2CPZBossInstance segment = new Sonic2CPZBossInstance(segmentSpawn, levelManager, true);
        segment.mainBoss = mainBoss;
        segment.parent = this;
        segment.routine = ROUTINE_PIPE_SEGMENT;
        segment.priority = 5;
        segment.anim = 1;
        segment.yOffset = offset;
        segment.animationSetType = AnimationSetType.DRIPPER;
        segment.renderFlags = renderFlags;
        segment.status = status;
        segment.setPosition(x, y);
        levelManager.getObjectManager().addDynamicObject(segment);
        return segment;
    }

    private Sonic2CPZBossInstance spawnPipePumpObject() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return null;
        }
        ObjectSpawn pumpSpawn = new ObjectSpawn(
                x,
                y,
                Sonic2ObjectIds.CPZ_BOSS,
                0,
                renderFlags,
                false,
                spawn.rawYWord());
        Sonic2CPZBossInstance pump = new Sonic2CPZBossInstance(pumpSpawn, levelManager, true);
        pump.mainBoss = mainBoss;
        pump.parent = this;
        pump.routine = ROUTINE_PIPE_PUMP;
        pump.routineSecondary = 2;
        pump.priority = 4;
        pump.anim = 2;
        pump.timer3 = 2;
        pump.yOffset = 0x58;
        pump.timer = 0x12;
        pump.animationSetType = AnimationSetType.DRIPPER;
        pump.renderFlags = renderFlags;
        pump.status = status;
        pump.setPosition(x, y);
        levelManager.getObjectManager().addDynamicObject(pump);
        return pump;
    }

    private void spawnDripperFromPipe() {
        if (levelManager == null || levelManager.getObjectManager() == null || parent == null) {
            return;
        }
        ObjectSpawn dripperSpawn = new ObjectSpawn(
                x,
                y,
                Sonic2ObjectIds.CPZ_BOSS,
                0,
                renderFlags,
                false,
                spawn.rawYWord());
        Sonic2CPZBossInstance dripper = new Sonic2CPZBossInstance(dripperSpawn, levelManager, true);
        dripper.mainBoss = mainBoss;
        dripper.parent = parent;
        dripper.routine = ROUTINE_DRIPPER;
        dripper.priority = 4;
        dripper.paletteOverride = 3;
        dripper.mappingFrame = -1;  // Don't render until position is set in updateDripperInit
        // Set position from parent (main boss) so first frame is in correct location
        if (parent != null) {
            dripper.setPosition(parent.x, parent.y);
        } else {
            dripper.setPosition(x, y);
        }
        levelManager.getObjectManager().addDynamicObject(dripper);
    }

    private void spawnContainerFloor() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn floorSpawn = new ObjectSpawn(
                x,
                y,
                Sonic2ObjectIds.CPZ_BOSS,
                0,
                renderFlags,
                false,
                spawn.rawYWord());
        Sonic2CPZBossInstance floor = new Sonic2CPZBossInstance(floorSpawn, levelManager, true);
        floor.mainBoss = mainBoss;
        floor.parent = this;
        floor.routine = ROUTINE_CONTAINER;
        floor.routineSecondary = 4;
        floor.priority = 4;
        floor.anim = 9;
        floor.animationSetType = AnimationSetType.DRIPPER;
        floor.setPosition(x, y);
        levelManager.getObjectManager().addDynamicObject(floor);
    }

    private void spawnContainerExtend() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn extendSpawn = new ObjectSpawn(
                x,
                y,
                Sonic2ObjectIds.CPZ_BOSS,
                0,
                renderFlags,
                false,
                spawn.rawYWord());
        Sonic2CPZBossInstance extend = new Sonic2CPZBossInstance(extendSpawn, levelManager, true);
        extend.mainBoss = mainBoss;
        extend.parent = this;
        extend.routine = ROUTINE_CONTAINER;
        extend.routineSecondary = 6;
        extend.priority = 4;
        extend.paletteOverride = 3;
        extend.anim = 0;
        extend.mappingFrame = -1;  // Don't render until animation starts
        extend.animationSetType = AnimationSetType.DRIPPER;
        extend.setPosition(x, y);
        levelManager.getObjectManager().addDynamicObject(extend);
    }

    private void spawnContainerFloor2() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn floorSpawn = new ObjectSpawn(
                x,
                y,
                Sonic2ObjectIds.CPZ_BOSS,
                0,
                renderFlags,
                false,
                spawn.rawYWord());
        Sonic2CPZBossInstance floor = new Sonic2CPZBossInstance(floorSpawn, levelManager, true);
        floor.mainBoss = mainBoss;
        floor.parent = this;
        floor.routine = ROUTINE_CONTAINER;
        floor.routineSecondary = 8;
        floor.priority = 5;
        floor.anim = 0x0B;
        floor.timer2 = 0x24;
        floor.animationSetType = AnimationSetType.DRIPPER;
        floor.setPosition(x, y);
        levelManager.getObjectManager().addDynamicObject(floor);
    }

    private void spawnBossExplosion(int originX, int originY, boolean randomOffset) {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        int xOffset = 0;
        int yOffset = 0;
        if (randomOffset) {
            int random = ThreadLocalRandom.current().nextInt(0x10000);
            xOffset = ((random & 0xFF) >> 2) - 0x20;
            yOffset = (((random >> 8) & 0xFF) >> 2) - 0x20;
        }
        BossExplosionObjectInstance explosion = new BossExplosionObjectInstance(
                originX + xOffset,
                originY + yOffset,
                levelManager.getObjectRenderManager());
        levelManager.getObjectManager().addDynamicObject(explosion);
    }

    private Sonic2CPZBossInstance spawnRobotnik() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return null;
        }
        ObjectSpawn robotnikSpawn = new ObjectSpawn(
                x,
                y,
                Sonic2ObjectIds.CPZ_BOSS,
                0,
                renderFlags,
                false,
                spawn.rawYWord());
        Sonic2CPZBossInstance robotnikInstance = new Sonic2CPZBossInstance(robotnikSpawn, levelManager, true);
        robotnikInstance.routine = ROUTINE_ROBOTNIK;
        robotnikInstance.anim = 1;
        robotnikInstance.priority = 3;
        robotnikInstance.parent = this;
        robotnikInstance.mainBoss = this;
        robotnikInstance.paletteOverride = 0;
        robotnikInstance.animationSetType = AnimationSetType.EGGPOD;
        robotnikInstance.setPosition(x, y);
        levelManager.getObjectManager().addDynamicObject(robotnikInstance);
        return robotnikInstance;
    }

    private void spawnFlame() {
        if ((spawn.subtype() & 0x80) != 0) {
            return;
        }
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn flameSpawn = new ObjectSpawn(
                x,
                y,
                Sonic2ObjectIds.CPZ_BOSS,
                0,
                renderFlags,
                false,
                spawn.rawYWord());
        Sonic2CPZBossInstance flame = new Sonic2CPZBossInstance(flameSpawn, levelManager, true);
        flame.routine = ROUTINE_FLAME;
        flame.priority = 3;
        flame.parent = this;
        flame.mainBoss = this;
        flame.animFrameDuration = 1;
        flame.timer2 = 0;
        flame.mappingFrame = 0;
        flame.setPosition(x, y);
        levelManager.getObjectManager().addDynamicObject(flame);
    }

    private void spawnPump() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn pumpSpawn = new ObjectSpawn(
                x,
                y,
                Sonic2ObjectIds.CPZ_BOSS,
                0,
                renderFlags,
                false,
                spawn.rawYWord());
        Sonic2CPZBossInstance pump = new Sonic2CPZBossInstance(pumpSpawn, levelManager, true);
        pump.routine = ROUTINE_PUMP;
        pump.priority = 2;
        pump.parent = this;
        pump.mainBoss = this;
        pump.animationSetType = AnimationSetType.DRIPPER;
        pump.setPosition(x, y);
        levelManager.getObjectManager().addDynamicObject(pump);
    }

    private void spawnContainer() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn containerSpawn = new ObjectSpawn(
                x - 0x10,
                y - CONTAINER_OFFSET_Y,
                Sonic2ObjectIds.CPZ_BOSS,
                0,
                renderFlags,
                false,
                spawn.rawYWord());
        Sonic2CPZBossInstance container = new Sonic2CPZBossInstance(containerSpawn, levelManager, true);
        container.routine = ROUTINE_CONTAINER;
        container.priority = 4;
        container.anim = 6;
        container.parent = this;
        container.mainBoss = this;
        container.xVel = CONTAINER_INIT_XVEL;
        container.animationSetType = AnimationSetType.DRIPPER;
        container.setPosition(x - 0x10, y - CONTAINER_OFFSET_Y);
        levelManager.getObjectManager().addDynamicObject(container);
    }

    private void spawnPipe() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn pipeSpawn = new ObjectSpawn(
                x,
                y,
                Sonic2ObjectIds.CPZ_BOSS,
                0,
                renderFlags,
                false,
                spawn.rawYWord());
        Sonic2CPZBossInstance pipe = new Sonic2CPZBossInstance(pipeSpawn, levelManager, true);
        pipe.routine = ROUTINE_PIPE;
        pipe.priority = 4;
        // ROM: pipe's parent is always the main boss (for status2 checks)
        // When called from container reset, Obj5D_parent(a0) = container's parent = mainBoss
        pipe.parent = mainBoss;
        pipe.mainBoss = mainBoss;
        pipe.animationSetType = AnimationSetType.DRIPPER;
        pipe.setPosition(mainBoss.x, mainBoss.y);
        levelManager.getObjectManager().addDynamicObject(pipe);
    }

    private static final class PaletteFlasher {
        private static final Palette.Color BLACK = new Palette.Color((byte) 0, (byte) 0, (byte) 0);
        private static final Palette.Color WHITE = new Palette.Color((byte) 255, (byte) 255, (byte) 255);

        private final LevelManager levelManager;
        private boolean flashing;
        private Palette.Color originalColor;
        private boolean stored;
        private boolean flashWhite;

        private PaletteFlasher(LevelManager levelManager) {
            this.levelManager = levelManager;
        }

        public void startFlash() {
            flashing = true;
            flashWhite = false;
            stored = false;
        }

        public void stopFlash() {
            if (!flashing) {
                return;
            }
            Palette palette = getPalette();
            if (palette != null && stored) {
                palette.setColor(1, originalColor);
            }
            flashing = false;
            stored = false;
        }

        public void update() {
            if (!flashing) {
                return;
            }
            Palette palette = getPalette();
            if (palette == null) {
                return;
            }
            if (!stored) {
                originalColor = palette.getColor(1);
                stored = true;
            }
            palette.setColor(1, flashWhite ? WHITE : BLACK);
            flashWhite = !flashWhite;
        }

        private Palette getPalette() {
            if (levelManager == null || levelManager.getCurrentLevel() == null) {
                return null;
            }
            if (levelManager.getCurrentLevel().getPaletteCount() <= 1) {
                return null;
            }
            return levelManager.getCurrentLevel().getPalette(1);
        }
    }
}
