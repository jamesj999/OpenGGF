package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.SolidContact;
import uk.co.jamesj999.sonic.level.objects.SolidObjectListener;
import uk.co.jamesj999.sonic.level.objects.SolidObjectParams;
import uk.co.jamesj999.sonic.level.objects.SolidObjectProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ARZ Boss Pillar - Rising/lowering solid platform.
 * ROM Reference: s2.asm Obj89 (subtype 4)
 *
 * States:
 * - RAISING: Pillar rises from floor, screen shakes
 * - IDLE: Pillar at target height, spawns arrows when hammer hits
 * - LOWERING: Pillar sinks when boss defeated
 */
public class ARZBossPillar extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final int PILLAR_SUB_RAISING = 0;
    private static final int PILLAR_SUB_IDLE = 2;
    private static final int PILLAR_SUB_LOWERING = 4;

    private static final int PILLAR_TARGET_Y = 0x488;
    private static final int PILLAR_START_Y = 0x510;

    private static final SolidObjectParams PILLAR_SOLID_PARAMS = new SolidObjectParams(0x23, 0x44, 0x45, 0, 4);

    private final LevelManager levelManager;
    private final Sonic2ARZBossInstance mainBoss;

    private int x;
    private int y;
    private int renderFlags;
    private int routineSecondary;
    private int mappingFrame;

    private boolean pillarShaking;
    private int pillarShakeTime;

    public ARZBossPillar(ObjectSpawn spawn, LevelManager levelManager, Sonic2ARZBossInstance mainBoss) {
        super(spawn, "ARZ Boss Pillar");
        this.levelManager = levelManager;
        this.mainBoss = mainBoss;
        this.x = spawn.x();
        this.y = spawn.y();
        this.renderFlags = spawn.renderFlags();
        this.routineSecondary = PILLAR_SUB_RAISING;
        this.mappingFrame = 0;
        this.pillarShaking = false;
        this.pillarShakeTime = 0;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        // Check if main boss is defeated
        if (mainBoss != null && mainBoss.isInDefeatSequence()) {
            routineSecondary = PILLAR_SUB_LOWERING;
        }

        switch (routineSecondary) {
            case PILLAR_SUB_RAISING -> updatePillarRaise(frameCounter);
            case PILLAR_SUB_IDLE -> updatePillarIdle(frameCounter);
            case PILLAR_SUB_LOWERING -> updatePillarLower(player);
        }
    }

    /**
     * Pillar raising state.
     * ROM: Obj89_Pillar_Sub0 (lines 64813-64828)
     */
    private void updatePillarRaise(int frameCounter) {
        if ((frameCounter & 0x1F) == 0) {
            AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_RUMBLING_2);
        }
        y -= 1;
        if (y <= PILLAR_TARGET_Y) {
            y = PILLAR_TARGET_Y;
            routineSecondary = PILLAR_SUB_IDLE;
            // ROM: move.b #0,(Screen_Shaking_Flag).w - stop screen shaking
            GameServices.gameState().setScreenShakeActive(false);
        }
        mappingFrame = 0;
    }

    /**
     * Pillar idle state.
     * ROM: Obj89_Pillar_Sub2 (lines 64831-64857)
     */
    private void updatePillarIdle(int frameCounter) {
        if (mainBoss != null && mainBoss.isHammerActive()) {
            boolean isLeftPillar = (renderFlags & 1) == 0;
            boolean hammerTargetingLeft = !mainBoss.isTargetingRight();

            if (isLeftPillar == hammerTargetingLeft) {
                mainBoss.clearHammerFlag();
                mainBoss.spawnArrowAndEyes(isLeftPillar);
                pillarShaking = true;
            }
        }
        updatePillarShake(frameCounter);
        mappingFrame = 0;
    }

    /**
     * Pillar lowering state (boss defeated).
     * ROM: Obj89_Pillar_Sub4 (lines 64963-65001)
     */
    private void updatePillarLower(AbstractPlayableSprite player) {
        GameServices.gameState().setScreenShakeActive(true);

        y += 1;
        if (y >= PILLAR_START_Y) {
            GameServices.gameState().setScreenShakeActive(false);
            dropStandingPlayers(player);
            setDestroyed(true);
            return;
        }
        mappingFrame = 0;
    }

    private void dropStandingPlayers(AbstractPlayableSprite player) {
        if (player == null || levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }
        if (levelManager.getObjectManager().isRidingObject(player, this)) {
            levelManager.getObjectManager().clearRidingObject(player);
            player.setOnObject(false);
            player.setAir(true);
        }
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
        int baseX = isRightPillar() ? 0x2B70 : 0x2A50;
        int offset = ((frameCounter & 1) == 0) ? 1 : -1;
        x = baseX + offset;
        y = PILLAR_TARGET_Y + offset;
    }

    private void resetPillarBasePosition() {
        x = isRightPillar() ? 0x2B70 : 0x2A50;
        y = PILLAR_TARGET_Y;
    }

    private boolean isRightPillar() {
        return (renderFlags & 1) != 0;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager != null ? levelManager.getObjectRenderManager() : null;
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.ARZ_BOSS_PARTS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean hFlip = (renderFlags & 1) != 0;
        renderer.drawFrameIndex(mappingFrame, x, y, hFlip, false);
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
        return 2;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return PILLAR_SOLID_PARAMS;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return routineSecondary != PILLAR_SUB_LOWERING;
    }

    @Override
    public boolean isTopSolidOnly() {
        return false;
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // Pillar doesn't need special contact handling
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), renderFlags, spawn.respawnTracked(), spawn.rawYWord());
    }
}
