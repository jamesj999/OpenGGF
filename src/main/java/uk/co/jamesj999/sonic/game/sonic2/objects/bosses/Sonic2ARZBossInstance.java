package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.game.sonic2.objects.BossExplosionObjectInstance;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.Palette;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.SolidContact;
import uk.co.jamesj999.sonic.level.objects.SolidObjectListener;
import uk.co.jamesj999.sonic.level.objects.SolidObjectParams;
import uk.co.jamesj999.sonic.level.objects.SolidObjectProvider;
import uk.co.jamesj999.sonic.level.objects.TouchResponseAttackable;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.objects.TouchResponseResult;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.physics.TrigLookupTable;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Aquatic Ruin Zone boss (Object 0x89).
 * Implements Obj89 routines (main, pillars, arrows, bulging eyes) for ROM parity.
 */
public class Sonic2ARZBossInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseAttackable, SolidObjectProvider, SolidObjectListener {

    private enum Role {
        INIT,
        MAIN,
        PILLAR,
        ARROW,
        EYES
    }

    private static final int RENDER_X_FLIP = 0x01;
    private static final int RENDER_Y_FLIP = 0x02;

    private static final int SUBTYPE_INIT = 0x00;
    private static final int SUBTYPE_MAIN = 0x02;
    private static final int SUBTYPE_PILLAR = 0x04;
    private static final int SUBTYPE_ARROW = 0x06;
    private static final int SUBTYPE_EYES = 0x08;

    private static final int MAIN_SUB0 = 0x00;
    private static final int MAIN_SUB2 = 0x02;
    private static final int MAIN_SUB4 = 0x04;
    private static final int MAIN_SUB6 = 0x06;
    private static final int MAIN_SUB8 = 0x08;
    private static final int MAIN_SUBA = 0x0A;
    private static final int MAIN_SUBC = 0x0C;

    private static final int PILLAR_SUB0 = 0x00;
    private static final int PILLAR_SUB2 = 0x02;
    private static final int PILLAR_SUB4 = 0x04;

    private static final int ARROW_SUB0 = 0x00;
    private static final int ARROW_SUB2 = 0x02;
    private static final int ARROW_SUB4 = 0x04;
    private static final int ARROW_SUB6 = 0x06;

    private static final int MAIN_START_X = 0x2AE0;
    private static final int MAIN_START_Y = 0x388;
    private static final int MAIN_TARGET_Y = 0x430;
    private static final int LEFT_TARGET_X = 0x2AB0;
    private static final int RIGHT_TARGET_X = 0x2B10;
    private static final int LEFT_PILLAR_X = 0x2A50;
    private static final int RIGHT_PILLAR_X = 0x2B70;
    private static final int PILLAR_START_Y = 0x510;
    private static final int PILLAR_TARGET_Y = 0x488;
    private static final int LEFT_EYES_X = 0x2A6A;
    private static final int RIGHT_EYES_X = 0x2B56;
    private static final int LEFT_ARROW_STOP_X = 0x2A77;
    private static final int RIGHT_ARROW_STOP_X = 0x2B49;
    private static final int ARROW_FLOOR_Y = 0x4F0;
    // ROM: Player position check boundaries for init (lines 64270-64280)
    private static final int PLAYER_CHECK_LEFT_X = 0x2A60;
    private static final int PLAYER_CHECK_RIGHT_X = 0x2B60;
    private static final int CAMERA_ESCAPE_MAX_X = 0x2C00;
    private static final int HAMMER_OFFSET_X = 0x50;
    private static final int HAMMER_HITBOX_WIDTH = 0x10;
    private static final int HAMMER_HITBOX_HEIGHT = 0x14;
    private static final int DEFEAT_TIMER_START = 0xB3;
    private static final int INVULNERABLE_DURATION = 64;

    private static final int MAIN_PRIORITY = 2;
    private static final int PILLAR_PRIORITY = 2;
    private static final int ARROW_PRIORITY = 4;
    private static final int EYES_PRIORITY = 2;

    private static final int BOSS_COLLISION_FLAGS = 0x0F;
    private static final int ARROW_COLLISION_FLAGS = 0xB0;

    private static final int HAMMER_GRAVITY = 0x38;
    private static final int MAIN_DESCEND_VEL = 0x0100;
    private static final int MAIN_MOVE_VEL = 0x00C8;
    private static final int MAIN_ESCAPE_XVEL = 0x0400;
    private static final int MAIN_ESCAPE_YVEL = -0x0040;
    private static final int HAMMER_INITIAL_YVEL = -0x0380;

    private static final int[] ARROW_OFFSETS = { 0x458, 0x478, 0x498, 0x4B8 };

    private static final SolidObjectParams PILLAR_SOLID_PARAMS = new SolidObjectParams(0x23, 0x44, 0x45, 0, 4);
    private static final SolidObjectParams ARROW_SOLID_PARAMS = new SolidObjectParams(0x1B, 1, 2);

    private static final Palette.Color BLACK = new Palette.Color((byte) 0, (byte) 0, (byte) 0);
    private static final Palette.Color WHITE = new Palette.Color((byte) 255, (byte) 255, (byte) 255);

    private static final int[][] ARZ_ARROW_ANIMS = {
            { 1, 4, 6, 5, 4, 6, 4, 5, 4, 6, 4, 4, 6, 5, 4, 6, 4, 5, 4, 6, 4, 0xFD, 1 },
            { 0x0F, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
                    4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 0xF9 }
    };

    private static final int[][] ARZ_BOSS_ANIMS = {
            { 7, 0, 1, 0xFF, 2, 3, 2, 3, 2, 3, 2, 3, 0xFF, 4, 4, 4, 4, 4, 4, 4, 4, 0xFF },
            { 1, 6, 7, 0xFF },
            { 0x0F, 9, 0xFF },
            { 2, 0x0A, 0x0A, 0x0B, 0x0B, 0x0B, 0x0B, 0x0B, 0x0A, 0x0A, 0xFD, 2 },
            { 0x0F, 8, 0xFF },
            { 7, 5, 0xFF }
    };

    private final LevelManager levelManager;

    private Role role;
    private int bossSubtype;
    private int renderFlags;
    private boolean initialized;

    private Sonic2ARZBossInstance mainBoss;
    private Sonic2ARZBossInstance arrowParent;
    private Sonic2ARZBossInstance arrowParent2;

    private int x;
    private int y;

    private int bossRoutine;
    private int bossXPos;
    private int bossYPos;
    private int bossXVel;
    private int bossYVel;
    private int bossCountdown;
    private int bossSineCount;
    private int hitCount;
    private int collisionFlags;
    private int invulnerableTime;
    private boolean bossDefeated;
    private int hammerFlags;
    private int bossCollisionRoutine;
    private boolean targetFlag;

    private int mainMapFrame;
    private int sub2MapFrame;
    private int sub3MapFrame;
    private int sub4MapFrame;
    private int sub2X;
    private int sub2Y;
    private int sub3X;
    private int sub3Y;
    private int sub4X;
    private int sub4Y;

    private int hammerYPos;
    private int hammerYVel;
    private final int[] bossAnim = new int[8];

    private Palette.Color storedPaletteColor;
    private boolean storedPalette;
    private boolean flashWhite;

    private int routineSecondary;
    private boolean pillarShaking;
    private int pillarShakeTime;

    private int mappingFrame;
    private int arrowRoutine;
    private int arrowTimer;
    private int arrowAnim;
    private int arrowAnimLast = -1;
    private int arrowAnimFrame;
    private int arrowAnimTimer;
    private int arrowXVel;
    private int arrowYVel;
    private int arrowSubtype;
    private int arrowCollisionFlags;
    private int eyesTimer;
    // ROM: status.npc.no_balancing bit equivalent - when set, arrow should be deleted
    private boolean arrowDeleteFlag;

    public Sonic2ARZBossInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, "ARZ Boss");
        this.levelManager = levelManager;
        this.renderFlags = spawn.renderFlags();
        this.bossSubtype = spawn.subtype();
        this.role = roleFromSubtype(bossSubtype);
        this.x = spawn.x();
        this.y = spawn.y();
        if (role == Role.MAIN || role == Role.INIT) {
            this.mainBoss = this;
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }
        if (role == Role.PILLAR && mainBoss == null && routineSecondary >= SUBTYPE_ARROW) {
            if (routineSecondary == SUBTYPE_ARROW) {
                role = Role.ARROW;
            } else if (routineSecondary == SUBTYPE_EYES) {
                role = Role.EYES;
            }
        }
        switch (role) {
            case INIT -> initMain();
            case MAIN -> updateMain(frameCounter, player);
            case PILLAR -> updatePillar(frameCounter, player);
            case ARROW -> updateArrow(frameCounter, player);
            case EYES -> updateEyes();
            default -> {
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager != null ? levelManager.getObjectRenderManager() : null;
        if (renderManager == null) {
            return;
        }

        boolean hFlip = (renderFlags & RENDER_X_FLIP) != 0;
        boolean vFlip = (renderFlags & RENDER_Y_FLIP) != 0;

        switch (role) {
            case MAIN -> {
                PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.ARZ_BOSS_MAIN);
                if (renderer == null || !renderer.isReady()) {
                    return;
                }
                renderer.drawFrameIndex(mainMapFrame, x, y, hFlip, vFlip);
                renderer.drawFrameIndex(sub2MapFrame, sub2X, sub2Y, hFlip, vFlip);
                renderer.drawFrameIndex(sub3MapFrame, sub3X, sub3Y, hFlip, vFlip);
                renderer.drawFrameIndex(sub4MapFrame, sub4X, sub4Y, hFlip, vFlip);
            }
            case PILLAR, ARROW, EYES -> {
                PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.ARZ_BOSS_PARTS);
                if (renderer == null || !renderer.isReady()) {
                    return;
                }
                renderer.drawFrameIndex(mappingFrame, x, y, hFlip, vFlip);
            }
            default -> {
            }
        }
    }

    @Override
    public int getCollisionFlags() {
        if (role == Role.MAIN) {
            if (bossRoutine >= MAIN_SUB8 || bossDefeated) {
                return 0;
            }
            if (collisionFlags == 0) {
                return 0;
            }
            return 0xC0 | (collisionFlags & 0x3F);
        }
        if (role == Role.ARROW) {
            return arrowCollisionFlags;
        }
        return 0;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public void onPlayerAttack(AbstractPlayableSprite player, TouchResponseResult result) {
        if (role != Role.MAIN) {
            return;
        }
        if (collisionFlags == 0 || bossRoutine >= MAIN_SUB8 || bossDefeated) {
            return;
        }
        hitCount--;
        collisionFlags = 0;
        if (hitCount <= 0) {
            killBoss();
        }
    }

    @Override
    public SolidObjectParams getSolidParams() {
        if (role == Role.PILLAR) {
            return PILLAR_SOLID_PARAMS;
        }
        if (role == Role.ARROW) {
            return ARROW_SOLID_PARAMS;
        }
        return new SolidObjectParams(0, 0, 0);
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        if (role == Role.PILLAR) {
            return routineSecondary != PILLAR_SUB4;
        }
        if (role == Role.ARROW) {
            return arrowRoutine == ARROW_SUB4;
        }
        return false;
    }

    @Override
    public boolean isTopSolidOnly() {
        return role == Role.ARROW && arrowRoutine == ARROW_SUB4;
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (role != Role.ARROW || arrowRoutine != ARROW_SUB4) {
            return;
        }
        if (!contact.standing()) {
            return;
        }
        if (arrowTimer == 0) {
            arrowTimer = 0x1F;
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
        return RenderPriority.clamp(switch (role) {
            case ARROW -> ARROW_PRIORITY;
            case PILLAR -> PILLAR_PRIORITY;
            case EYES -> EYES_PRIORITY;
            default -> MAIN_PRIORITY;
        });
    }

    private static Role roleFromSubtype(int subtype) {
        return switch (subtype) {
            case SUBTYPE_PILLAR -> Role.PILLAR;
            case SUBTYPE_ARROW -> Role.ARROW;
            case SUBTYPE_EYES -> Role.EYES;
            case SUBTYPE_MAIN -> Role.MAIN;
            default -> Role.INIT;
        };
    }

    private void initMain() {
        if (initialized) {
            return;
        }

        // ROM: Obj89_Init lines 64267-64280
        // Position checks are ONLY done in two-player "Sonic & Tails" mode.
        // In single-player mode, go straight to raising pillars.
        //
        // ROM code:
        //   tst.w (Player_mode).w  ; is player mode anything other than Sonic & Tails?
        //   bne.s Obj89_Init_RaisePillars  ; if yes (single player), branch
        AbstractPlayableSprite sidekick = SpriteManager.getInstance().getSidekick();
        if (sidekick != null) {
            // Two-player mode: check both players are in valid position
            AbstractPlayableSprite mainPlayer = Camera.getInstance().getFocusedSprite();
            if (mainPlayer != null) {
                int mainX = mainPlayer.getCentreX();
                // ROM: cmpi.w #$2A60,d0 / blt.w Obj89_Init_Standard
                // ROM: cmpi.w #$2B60,d0 / bgt.w Obj89_Init_Standard
                if (mainX < PLAYER_CHECK_LEFT_X || mainX > PLAYER_CHECK_RIGHT_X) {
                    initBossAnimationArray();
                    return;
                }
            }

            // ROM: cmpi.b #$81,(Sidekick+obj_control).w / beq.w Obj89_Init_RaisePillars
            // If Tails is flying (obj_control == $81), skip his position check
            // TODO: Add sidekick.isFlying() check when Tails AI is implemented
            int sidekickX = sidekick.getCentreX();
            if (sidekickX < PLAYER_CHECK_LEFT_X || sidekickX > PLAYER_CHECK_RIGHT_X) {
                initBossAnimationArray();
                return;
            }
        }
        // Single-player mode or both players in range: proceed to raise pillars

        initialized = true;
        role = Role.MAIN;
        bossSubtype = SUBTYPE_MAIN;
        mainBoss = this;

        // ROM: move.b #1,(Screen_Shaking_Flag).w - enable screen shake
        GameServices.gameState().setScreenShakeActive(true);

        renderFlags &= 0x03;
        bossRoutine = MAIN_SUB0;
        bossXPos = MAIN_START_X << 16;
        bossYPos = MAIN_START_Y << 16;
        bossXVel = 0;
        bossYVel = MAIN_DESCEND_VEL;
        bossCountdown = 0;
        bossSineCount = 0;
        hitCount = 8;
        collisionFlags = BOSS_COLLISION_FLAGS;
        invulnerableTime = 0;
        bossDefeated = false;
        hammerFlags = 0;
        bossCollisionRoutine = 0;
        targetFlag = false;
        hammerYVel = HAMMER_INITIAL_YVEL;

        mainMapFrame = 8;
        sub2MapFrame = 0;
        sub3MapFrame = 9;
        sub4MapFrame = 6;
        sub2X = MAIN_START_X;
        sub2Y = PILLAR_TARGET_Y;
        sub3X = MAIN_START_X;
        sub3Y = PILLAR_TARGET_Y;
        sub4X = MAIN_START_X;
        sub4Y = PILLAR_TARGET_Y;
        hammerYPos = PILLAR_TARGET_Y << 16;

        initBossAnimationArray();

        setPosition(MAIN_START_X, MAIN_START_Y);
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

    private void updateMain(int frameCounter, AbstractPlayableSprite player) {
        if (!initialized) {
            initMain();
            return;
        }
        if (bossCollisionRoutine != 0) {
            checkHammerCollision(frameCounter, player);
        }
        switch (bossRoutine) {
            case MAIN_SUB0 -> updateMainSub0(player);
            case MAIN_SUB2 -> updateMainSub2(player);
            case MAIN_SUB4 -> updateMainSub4(player);
            case MAIN_SUB6 -> updateMainSub6(frameCounter, player);
            case MAIN_SUB8 -> updateMainSub8(frameCounter);
            case MAIN_SUBA -> updateMainSubA(player);
            case MAIN_SUBC -> updateMainSubC(player);
            default -> {
            }
        }
    }

    private void updateMainSub0(AbstractPlayableSprite player) {
        bossMoveObject();
        handleFace(player);
        alignParts();
        if ((bossYPos >> 16) >= MAIN_TARGET_Y) {
            bossYPos = MAIN_TARGET_Y << 16;
            bossRoutine = MAIN_SUB2;
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
                bossRoutine = MAIN_SUB4;
                bossXVel = 0;
            }
        } else {
            if ((bossXPos >> 16) <= LEFT_TARGET_X) {
                bossRoutine = MAIN_SUB4;
                bossXVel = 0;
            }
        }
        animateBoss();
    }

    private void updateMainSub4(AbstractPlayableSprite player) {
        bossMoveObject();
        handleFace(player);
        alignParts();
        if ((bossSineCount & 0xFF) == 0xC0) {
            bossAnim[4] = (bossAnim[4] & 0xF0) | 0x03;
            bossRoutine = MAIN_SUB6;
            targetFlag = (renderFlags & RENDER_X_FLIP) != 0;
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
            bossRoutine = MAIN_SUB2;
            renderFlags ^= RENDER_X_FLIP;
            bossXVel = (renderFlags & RENDER_X_FLIP) != 0 ? MAIN_MOVE_VEL : -MAIN_MOVE_VEL;
        }
        bossMoveObject();
        handleFace(player);
        alignParts();
        animateBoss();
    }

    private void updateMainSub8(int frameCounter) {
        bossDefeated = true;
        bossCountdown--;
        if (bossCountdown < 0) {
            setupEscapeAnim();
        } else {
            spawnBossExplosion(frameCounter);
        }
        x = bossXPos >> 16;
        y = bossYPos >> 16;
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
            bossRoutine = MAIN_SUBC;
        }
        bossMoveObject();
        handleHoveringAndHits();
        x = bossXPos >> 16;
        y = bossYPos >> 16;
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
        x = bossXPos >> 16;
        y = bossYPos >> 16;
        animateBoss();
        alignParts();
    }

    private void handleFace(AbstractPlayableSprite player) {
        handleHoveringAndHits();
        if (player != null && player.isHurt()) {
            bossAnim[3] = 0x31;
        }
        if (invulnerableTime == INVULNERABLE_DURATION - 1) {
            bossAnim[3] = 0xC0;
        }
    }

    private void handleHoveringAndHits() {
        int sine = TrigLookupTable.sinHex(bossSineCount);
        int offset = sine >> 6;
        y = (bossYPos >> 16) + offset;
        x = bossXPos >> 16;
        bossSineCount = (bossSineCount + 2) & 0xFF;

        if (bossRoutine >= MAIN_SUB8) {
            return;
        }
        if (hitCount == 0) {
            killBoss();
            return;
        }
        if (collisionFlags != 0) {
            return;
        }
        if (invulnerableTime == 0) {
            invulnerableTime = INVULNERABLE_DURATION;
            AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_BOSS_HIT);
        }
        flashPalette();
        invulnerableTime--;
        if (invulnerableTime <= 0) {
            collisionFlags = BOSS_COLLISION_FLAGS;
            restorePalette();
        }
    }

    private void flashPalette() {
        Palette palette = getPaletteLine2();
        if (palette == null) {
            return;
        }
        if (!storedPalette) {
            Palette.Color current = palette.getColor(1);
            storedPaletteColor = new Palette.Color(current.r, current.g, current.b);
            storedPalette = true;
            flashWhite = false;
        }
        palette.setColor(1, flashWhite ? WHITE : BLACK);
        flashWhite = !flashWhite;
    }

    private void restorePalette() {
        if (!storedPalette) {
            return;
        }
        Palette palette = getPaletteLine2();
        if (palette != null && storedPaletteColor != null) {
            palette.setColor(1, storedPaletteColor);
        }
        storedPalette = false;
    }

    private Palette getPaletteLine2() {
        if (levelManager == null || levelManager.getCurrentLevel() == null) {
            return null;
        }
        if (levelManager.getCurrentLevel().getPaletteCount() <= 1) {
            return null;
        }
        return levelManager.getCurrentLevel().getPalette(0);
    }

    private void killBoss() {
        GameServices.gameState().addScore(1000);
        bossCountdown = DEFEAT_TIMER_START;
        bossRoutine = MAIN_SUB8;
        bossAnim[2] = 0x05;
        bossAnim[3] = 0x00;
        sub2MapFrame = 5;
    }

    private void setupEscapeAnim() {
        bossAnim[4] = 0x01;
        bossAnim[5] = 0x00;
        bossAnim[2] = 0x00;
        bossAnim[3] = 0x00;
        renderFlags |= RENDER_X_FLIP;
        bossXVel = 0;
        bossYVel = 0;
        bossRoutine = MAIN_SUBA;
        bossCountdown = -0x12;
    }

    private void bossMoveObject() {
        bossXPos += bossXVel << 8;
        bossYPos += bossYVel << 8;
    }

    private void alignParts() {
        sub2X = x;
        sub2Y = y;
        sub4X = x;
        sub4Y = y;
        if (!bossDefeated) {
            sub3X = x;
            sub3Y = y;
            hammerYPos = y << 16;
            return;
        }
        dropHammer();
    }

    private void dropHammer() {
        if (bossCountdown > 0x78) {
            return;
        }
        sub3X -= 1;
        hammerYVel += HAMMER_GRAVITY;
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
        if ((renderFlags & RENDER_X_FLIP) != 0) {
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
            bossRoutine += 2;
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

    private void updatePillar(int frameCounter, AbstractPlayableSprite player) {
        if (mainBoss != null && mainBoss.bossRoutine >= MAIN_SUB8) {
            routineSecondary = PILLAR_SUB4;
        }
        switch (routineSecondary) {
            case PILLAR_SUB0 -> updatePillarRaise(frameCounter, player);
            case PILLAR_SUB2 -> updatePillarIdle(frameCounter, player);
            case PILLAR_SUB4 -> updatePillarLower(player);
            default -> {
            }
        }
    }

    /**
     * Pillar raising state.
     * ROM: Obj89_Pillar_Sub0 (lines 64813-64828)
     * Calls solid object handling and plays rumble sound every 32 frames.
     */
    private void updatePillarRaise(int frameCounter, AbstractPlayableSprite player) {
        // ROM: bsr.w Obj89_Pillar_SolidObject - pillar is solid while rising
        handlePillarSolid(player);

        if ((frameCounter & 0x1F) == 0) {
            AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_RUMBLING_2);
        }
        y -= 1;
        if (y <= PILLAR_TARGET_Y) {
            y = PILLAR_TARGET_Y;
            routineSecondary = PILLAR_SUB2;
            // ROM: move.b #0,(Screen_Shaking_Flag).w - stop screen shaking
            GameServices.gameState().setScreenShakeActive(false);
        }
        mappingFrame = 0;
    }

    /**
     * Pillar idle state.
     * ROM: Obj89_Pillar_Sub2 (lines 64831-64857)
     * Checks for hammer hit and spawns arrows.
     */
    private void updatePillarIdle(int frameCounter, AbstractPlayableSprite player) {
        // ROM: bsr.w Obj89_Pillar_SolidObject
        handlePillarSolid(player);

        if (mainBoss != null && (mainBoss.hammerFlags & 0x01) != 0) {
            if (!mainBoss.targetFlag) {
                if ((renderFlags & RENDER_X_FLIP) == 0) {
                    mainBoss.hammerFlags &= ~0x01;
                    spawnArrowAndEyes();
                    pillarShaking = true;
                }
            } else {
                if ((renderFlags & RENDER_X_FLIP) != 0) {
                    mainBoss.hammerFlags &= ~0x01;
                    spawnArrowAndEyes();
                    pillarShaking = true;
                }
            }
        }
        updatePillarShake(frameCounter);
        mappingFrame = 0;
    }

    /**
     * Pillar lowering state (boss defeated).
     * ROM: Obj89_Pillar_Sub4 (lines 64963-65001)
     * Lowers pillar and handles screen shake.
     */
    private void updatePillarLower(AbstractPlayableSprite player) {
        // ROM: move.b #1,(Screen_Shaking_Flag).w - make screen shake
        GameServices.gameState().setScreenShakeActive(true);

        y += 1;
        if (y >= PILLAR_START_Y) {
            // ROM: move.b #0,(Screen_Shaking_Flag).w - stop shaking the screen
            GameServices.gameState().setScreenShakeActive(false);
            // Drop any standing players
            dropStandingPlayers(player);
            setDestroyed(true);
            return;
        }
        mappingFrame = 0;
    }

    /**
     * Drop any players standing on this pillar.
     * ROM: Obj89_Pillar_Sub4 fix (lines 64984-64996)
     */
    private void dropStandingPlayers(AbstractPlayableSprite player) {
        if (player == null || levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        if (levelManager.getObjectManager().isRidingObject(this)) {
            levelManager.getObjectManager().clearRidingObject();
            player.setOnObject(false);
            player.setAir(true);
        }
    }

    /**
     * Handles pillar solid object collision.
     * ROM: Obj89_Pillar_SolidObject (lines 65014-65023)
     * Uses +4 Y offset for collision detection.
     */
    private void handlePillarSolid(AbstractPlayableSprite player) {
        // Solid collision is handled by the SolidObjectProvider interface
        // The ObjectManager will call our getSolidParams() and isSolidFor() methods
        // We just need to ensure we're registered properly
    }

    private void updatePillarShake(int frameCounter) {
        if (!pillarShaking) {
            return;
        }
        if (pillarShakeTime <= 0) {
            pillarShakeTime = 0x1F;
        }
        pillarShakeTime--;
        if (pillarShakeTime <= 0) {
            pillarShaking = false;
            pillarShakeTime = 0;
            resetPillarBasePosition();
            return;
        }
        int baseX = (mainBoss != null && mainBoss.targetFlag) ? RIGHT_PILLAR_X : LEFT_PILLAR_X;
        int baseY = PILLAR_TARGET_Y;
        int offset = ((frameCounter & 1) == 0) ? 1 : -1;
        x = baseX + offset;
        y = baseY + offset;
    }

    private void resetPillarBasePosition() {
        if (mainBoss != null && mainBoss.targetFlag) {
            x = RIGHT_PILLAR_X;
        } else {
            x = LEFT_PILLAR_X;
        }
        y = PILLAR_TARGET_Y;
    }

    private void spawnPillars() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        ObjectSpawn leftSpawn = new ObjectSpawn(
                LEFT_PILLAR_X,
                PILLAR_START_Y,
                Sonic2ObjectIds.ARZ_BOSS,
                SUBTYPE_PILLAR,
                0,
                false,
                spawn.rawYWord());
        Sonic2ARZBossInstance left = new Sonic2ARZBossInstance(leftSpawn, levelManager);
        left.role = Role.PILLAR;
        left.mainBoss = this;
        left.routineSecondary = PILLAR_SUB0;
        left.mappingFrame = 0;
        left.renderFlags = 0;
        left.setPosition(LEFT_PILLAR_X, PILLAR_START_Y);

        ObjectSpawn rightSpawn = new ObjectSpawn(
                RIGHT_PILLAR_X,
                PILLAR_START_Y,
                Sonic2ObjectIds.ARZ_BOSS,
                SUBTYPE_PILLAR,
                RENDER_X_FLIP,
                false,
                spawn.rawYWord());
        Sonic2ARZBossInstance right = new Sonic2ARZBossInstance(rightSpawn, levelManager);
        right.role = Role.PILLAR;
        right.mainBoss = this;
        right.routineSecondary = PILLAR_SUB0;
        right.mappingFrame = 0;
        right.renderFlags = RENDER_X_FLIP;
        right.setPosition(RIGHT_PILLAR_X, PILLAR_START_Y);

        levelManager.getObjectManager().addDynamicObject(left);
        levelManager.getObjectManager().addDynamicObject(right);
    }

    private void spawnArrowAndEyes() {
        if (levelManager == null || levelManager.getObjectManager() == null || mainBoss == null) {
            return;
        }
        int eyesX = LEFT_EYES_X;
        int eyesFlags = 0;
        int arrowSubtypeValue = 0;
        if (mainBoss.targetFlag) {
            eyesX = RIGHT_EYES_X;
            eyesFlags = RENDER_X_FLIP;
            arrowSubtypeValue = 0xFF;
        }
        int offsetIndex = ThreadLocalRandom.current().nextInt(ARROW_OFFSETS.length);
        int eyesY = ARROW_OFFSETS[offsetIndex];

        ObjectSpawn eyesSpawn = new ObjectSpawn(
                eyesX,
                eyesY,
                Sonic2ObjectIds.ARZ_BOSS,
                SUBTYPE_EYES,
                eyesFlags,
                false,
                spawn.rawYWord());
        Sonic2ARZBossInstance eyes = new Sonic2ARZBossInstance(eyesSpawn, levelManager);
        eyes.role = Role.EYES;
        eyes.mainBoss = mainBoss;
        eyes.renderFlags = eyesFlags;
        eyes.mappingFrame = 2;
        eyes.eyesTimer = 0x28;
        eyes.setPosition(eyesX, eyesY);

        ObjectSpawn arrowSpawn = new ObjectSpawn(
                eyesX,
                eyesY,
                Sonic2ObjectIds.ARZ_BOSS,
                SUBTYPE_ARROW,
                eyesFlags,
                false,
                spawn.rawYWord());
        Sonic2ARZBossInstance arrow = new Sonic2ARZBossInstance(arrowSpawn, levelManager);
        arrow.role = Role.ARROW;
        arrow.mainBoss = mainBoss;
        arrow.arrowParent = mainBoss;
        arrow.arrowParent2 = eyes;
        arrow.arrowSubtype = arrowSubtypeValue;
        arrow.renderFlags = eyesFlags;
        arrow.arrowRoutine = ARROW_SUB0;
        arrow.arrowAnim = 0;
        arrow.arrowCollisionFlags = 0;
        arrow.setPosition(eyesX, eyesY);

        levelManager.getObjectManager().addDynamicObject(eyes);
        levelManager.getObjectManager().addDynamicObject(arrow);
    }

    private void updateEyes() {
        if (eyesTimer <= 0) {
            setDestroyed(true);
            return;
        }
        eyesTimer--;
        mappingFrame = 2;
    }

    private void updateArrow(int frameCounter, AbstractPlayableSprite player) {
        if (arrowParent != null && arrowParent.bossRoutine >= MAIN_SUB8) {
            arrowRoutine = ARROW_SUB6;
        }
        switch (arrowRoutine) {
            case ARROW_SUB0 -> initArrow();
            case ARROW_SUB2 -> updateArrowFlying();
            case ARROW_SUB4 -> updateArrowStuck();
            case ARROW_SUB6 -> updateArrowFalling(player);
            default -> {
            }
        }
    }

    private void initArrow() {
        arrowRoutine = ARROW_SUB2;
        arrowCollisionFlags = ARROW_COLLISION_FLAGS;
        mappingFrame = 4;
        arrowYVel = 4;
        if (arrowParent2 != null) {
            x = arrowParent2.x;
            y = arrowParent2.y;
        }
        y += 9;
        if (arrowSubtype != 0) {
            renderFlags |= RENDER_X_FLIP;
            arrowXVel = -3;
        } else {
            arrowXVel = 3;
        }
    }

    /**
     * Arrow flying state.
     * ROM: Obj89_Arrow_Sub2 (lines 65077-65108)
     * Checks delete flag first, then moves toward target pillar.
     */
    private void updateArrowFlying() {
        // ROM: btst #status.npc.no_balancing,status(a0)
        // If delete flag is set, mark for deletion
        if (arrowDeleteFlag) {
            setDestroyed(true);
            return;
        }

        int nextX = x + arrowXVel;
        if (arrowXVel < 0) {
            if (nextX <= LEFT_ARROW_STOP_X) {
                x = LEFT_ARROW_STOP_X;
                arrowRoutine = ARROW_SUB4;
                AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_ARROW_STICK);
            } else {
                x = nextX;
            }
        } else {
            if (nextX >= RIGHT_ARROW_STOP_X) {
                x = RIGHT_ARROW_STOP_X;
                arrowRoutine = ARROW_SUB4;
                AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_ARROW_STICK);
            } else {
                x = nextX;
            }
        }
    }

    /**
     * Arrow stuck in pillar state.
     * ROM: Obj89_Arrow_Sub4 (lines 65111-65120)
     * Arrow acts as temporary platform, checks delete flag.
     */
    private void updateArrowStuck() {
        arrowCollisionFlags = 0;

        // ROM: btst #status.npc.no_balancing,status(a0)
        // If delete flag is set, transition to falling
        if (arrowDeleteFlag) {
            arrowRoutine = ARROW_SUB6;
            return;
        }

        updateArrowPlatform();
        animateArrow();
        mappingFrame = mappingFrame == 0 ? 4 : mappingFrame;
    }

    private void updateArrowFalling(AbstractPlayableSprite player) {
        dropPlayers(player);
        int nextY = y + arrowYVel;
        if (nextY > ARROW_FLOOR_Y) {
            setDestroyed(true);
            return;
        }
        y = nextY;
    }

    private void updateArrowPlatform() {
        if (arrowTimer == 0) {
            return;
        }
        arrowTimer--;
        if (arrowTimer == 0) {
            arrowRoutine = ARROW_SUB6;
        }
    }

    private void dropPlayers(AbstractPlayableSprite player) {
        if (player == null || levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        if (!levelManager.getObjectManager().isRidingObject(this)) {
            return;
        }
        levelManager.getObjectManager().clearRidingObject();
        player.setOnObject(false);
        player.setAir(true);
    }

    private void animateArrow() {
        int[] script = ARZ_ARROW_ANIMS[arrowAnim];
        int delay = script[0] & 0xFF;
        if (arrowAnim != arrowAnimLast) {
            arrowAnimFrame = 0;
            arrowAnimTimer = delay;
            arrowAnimLast = arrowAnim;
        }
        arrowAnimTimer--;
        if (arrowAnimTimer >= 0) {
            return;
        }
        arrowAnimTimer = delay;
        int frameValue = script[1 + arrowAnimFrame] & 0xFF;
        if ((frameValue & 0x80) != 0) {
            handleArrowCommand(frameValue, script);
            return;
        }
        mappingFrame = frameValue & 0x7F;
        arrowAnimFrame++;
    }

    private void handleArrowCommand(int command, int[] script) {
        if (command == 0xFF) {
            arrowAnimFrame = 0;
            mappingFrame = script[1] & 0x7F;
            arrowAnimFrame++;
            return;
        }
        if (command == 0xFD) {
            int nextAnim = script[2 + arrowAnimFrame] & 0xFF;
            arrowAnim = nextAnim;
            arrowAnimLast = -1;
            return;
        }
        if (command == 0xF9) {
            arrowRoutine += 2;
        }
    }

    private void spawnBossExplosion(int frameCounter) {
        if ((frameCounter & 0x07) != 0) {
            return;
        }
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        int rand = ThreadLocalRandom.current().nextInt(0x10000);
        int offsetX = ((rand & 0xFF) >> 2) - 0x20;
        int offsetY = (((rand >> 8) & 0xFF) >> 2) - 0x20;
        int baseX = bossXPos >> 16;
        int baseY = bossYPos >> 16;
        BossExplosionObjectInstance explosion = new BossExplosionObjectInstance(
                baseX + offsetX,
                baseY + offsetY,
                levelManager.getObjectRenderManager());
        levelManager.getObjectManager().addDynamicObject(explosion);
    }

    private void setPosition(int newX, int newY) {
        this.x = newX;
        this.y = newY;
    }

    @FunctionalInterface
    private interface FrameSetter {
        void set(int frame);
    }
}
