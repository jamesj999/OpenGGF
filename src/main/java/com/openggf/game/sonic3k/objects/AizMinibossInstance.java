package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * AIZ miniboss object (0x91).
 *
 * ROM: Obj_AIZMiniboss (sonic3k.asm):
 * - loc_68A46 init
 * - loc_68A6E trigger
 * - loc_68A94 descend entry
 * - loc_68ACC/loc_68AFE attack cycle
 * - loc_68B34/loc_68B92 movement variants
 */
public class AizMinibossInstance extends AbstractBossInstance {
    private static final int ROUTINE_INIT = 0;
    private static final int ROUTINE_WAIT_TRIGGER = 2;
    private static final int ROUTINE_WAIT = 4;
    private static final int ROUTINE_DESCEND = 6;
    private static final int ROUTINE_SWING = 8;
    private static final int ROUTINE_BREATH = 10;
    private static final int ROUTINE_HOLD = 12;
    private static final int ROUTINE_HORIZONTAL_SWING = 14;
    private static final int ROUTINE_DEFEATED = 16;

    private static final int HIT_COUNT = 6;
    private static final int COLLISION_SIZE = 0x0F;
    private static final int TRIGGER_X = 0x10E0;
    private static final int WAIT_AFTER_TRIGGER = 3 * 60;
    private static final int DESCEND_VEL = 0x100;
    private static final int DESCEND_TIME = 0xAF;
    private static final int SWING_PREP_TIME = 20;
    private static final int FLAME_PREP_TIME = 30;
    private static final int BREATH_SWING_TIME = 0x80;
    private static final int VERTICAL_DRIFT_TIME = 0x5F;
    private static final int HOLD_TIME = 0x10;
    private static final int HORIZONTAL_TIME = 0x60;
    private static final int HORIZONTAL_RECOVERY_TIME = 0x30;
    private static final int INVULN_TIME = 0x20;

    private static final int FLAG_PARENT_BITS = 0x38;
    private static final int FLAG_PARENT_COUNTER = 0x39;
    private static final int PARENT_BIT_BARREL_ACTIVATE = 1 << 1;
    private static final int PARENT_BIT_ALT_VERTICAL = 1 << 2;
    private static final int PARENT_BIT_ALT_HORIZONTAL = 1 << 3;
    /** ROM: bit set by boss when Knuckles fight activates napalm. */
    private static final int PARENT_BIT_NAPALM_ACTIVATE = 1 << 4;
    private static final int TRIGGER_X_KNUCKLES = 0x10C0;

    private static final int[] BREATH_FLAME_X_OFFSETS = {-0x64, -0x54, -0x44, -0x2C};
    private static final int[] BREATH_FLAME_Y_OFFSETS = {4, 4, 4, 3};

    /** ROM: loc_68F62 custom flash — palette line 2 color indices (byte offsets $0E,$14,$16,$1C). */
    private static final int[] CUSTOM_FLASH_INDICES = {7, 10, 11, 14};
    /** ROM: loc_68F62 dark color set (bit 0 of timer set). */
    private static final int[] CUSTOM_FLASH_DARK = {0x0644, 0x0240, 0x0020, 0x0644};
    /** ROM: loc_68F62 bright color set (bit 0 of timer clear). */
    private static final int[] CUSTOM_FLASH_BRIGHT = {0x0888, 0x0AAA, 0x0EEE, 0x0AAA};

    private final AizMinibossSwingMotion swingMotion = new AizMinibossSwingMotion();

    private int waitTimer = -1;
    private Runnable waitCallback;
    private boolean defeatRenderComplete;

    /** Stagger explosion controller for boss defeat (ROM: Child6_CreateBossExplosion subtype 0). */
    private S3kBossExplosionController defeatExplosionController;

    public AizMinibossInstance(ObjectSpawn spawn) {
        super(spawn, "AIZMiniboss");
    }

    @Override
    protected void initializeBossState() {
        state.routine = ROUTINE_INIT;
        state.hitCount = HIT_COUNT;
        waitTimer = -1;
        waitCallback = null;
        defeatRenderComplete = false;
        defeatExplosionController = null;
    }

    @Override
    protected int getInitialHitCount() {
        return HIT_COUNT;
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE;
    }

    @Override
    protected void onHitTaken(int remainingHits) {
        // Behavior changes are driven by the state machine and parent flags.
    }

    @Override
    protected boolean usesDefeatSequencer() {
        return false;
    }

    @Override
    protected int getInvulnerabilityDuration() {
        return INVULN_TIME;
    }

    @Override
    protected int getPaletteLineForFlash() {
        return -1; // Disable standard flash — custom flash via updateCustomFlash()
    }

    @Override
    public void onPlayerAttack(PlayableEntity playerEntity, TouchResponseResult result) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (state.invulnerable || state.defeated) {
            return;
        }

        state.hitCount--;
        state.invulnerabilityTimer = INVULN_TIME;
        state.invulnerable = true;
        paletteFlasher.startFlash();
        services().playSfx(Sonic3kSfx.BOSS_HIT.id);
        onHitTaken(state.hitCount);

        if (state.hitCount <= 0) {
            state.hitCount = 0;
            state.defeated = true;
            services().gameState().addScore(1000);
            onDefeatStarted();
        }
    }

    @Override
    protected void onDefeatStarted() {
        state.routine = ROUTINE_DEFEATED;
        state.xVel = 0;
        state.yVel = 0;
        waitTimer = -1;
        waitCallback = null;

        // Clear invulnerability immediately to stop palette flash
        state.invulnerable = false;
        state.invulnerabilityTimer = 0;
        loadBossPalette(); // Restore clean boss palette colors on line 1

        // ROM: loc_46ED4 creates Child6_CreateBossExplosion (sub_52850, subtype 0).
        // CreateBossExp00: timer=$20 (33 explosions), xRange=$20, yRange=$20.
        // Plays sfx_Explode (0xB4) once at creation, then staggers explosions 3 frames apart.
        defeatExplosionController = new S3kBossExplosionController(state.x, state.y, 0);
        services().playSfx(Sonic3kSfx.EXPLODE.id);

        services().fadeOutMusic();

        // Clean up all visible children — barrels, body, arm, napalm controller.
        for (var child : childComponents) {
            child.setDestroyed(true);
        }
        childComponents.clear();

        // Destroy all in-flight attack objects (barrel shots, flames, napalm projectiles).
        // ROM: barrel children enter defeat animation and stop spawning new attacks;
        // existing projectiles check parent state and become inert.
        destroyAttackObjects();
    }

    @Override
    protected void updateBossLogic(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        switch (state.routine) {
            case ROUTINE_INIT -> updateInit();
            case ROUTINE_WAIT_TRIGGER -> updateWaitTrigger();
            case ROUTINE_WAIT -> updateWaitOnly();
            case ROUTINE_DESCEND -> updateMoveAndWait(false);
            case ROUTINE_SWING -> updateMoveAndWait(true);
            case ROUTINE_BREATH -> updateMoveAndWait(true);
            case ROUTINE_HOLD -> updateWaitOnly();
            case ROUTINE_HORIZONTAL_SWING -> updateMoveAndWait(true);
            case ROUTINE_DEFEATED -> updateDefeated(frameCounter);
            default -> {
            }
        }
        updateCustomFlash();
    }

    /**
     * ROM: loc_68F62 custom AIZ miniboss flash.
     * Writes 4 specific palette entries on palette line 1 (engine index 1),
     * alternating between dark and bright color sets every frame.
     */
    private void updateCustomFlash() {
        if (state.defeated) {
            return;
        }
        if (!state.invulnerable) {
            return;
        }
        var level = services().currentLevel();
        if (level == null || level.getPaletteCount() <= 1) {
            return;
        }
        Palette pal = level.getPalette(1);
        // ROM: bit 0 of $20(a0) determines which color set
        boolean useDark = (state.invulnerabilityTimer & 1) != 0;
        int[] colors = useDark ? CUSTOM_FLASH_DARK : CUSTOM_FLASH_BRIGHT;
        for (int i = 0; i < CUSTOM_FLASH_INDICES.length; i++) {
            byte[] bytes = {(byte) ((colors[i] >> 8) & 0xFF), (byte) (colors[i] & 0xFF)};
            pal.getColor(CUSTOM_FLASH_INDICES[i]).fromSegaFormat(bytes, 0);
        }
        GraphicsManager gm = GraphicsManager.getInstance();
        if (gm.isGlInitialized()) {
            gm.cachePaletteTexture(pal, 1);
        }
    }

    private void updateInit() {
        Sonic3kAIZEvents events = getAizEvents();
        if (events != null) {
            events.setBossFlag(true);
        }
        services().gameState().setCurrentBossId(0x91);
        loadBossPalette();
        state.routine = ROUTINE_WAIT_TRIGGER;
    }

    private void updateWaitTrigger() {
        Camera camera = services().camera();
        PlayerCharacter character = Sonic3kLevelEventManager.getInstance().getPlayerCharacter();
        int triggerX = (character == PlayerCharacter.KNUCKLES) ? TRIGGER_X_KNUCKLES : TRIGGER_X;
        if (camera.getX() < triggerX) {
            return;
        }

        camera.setMinX((short) triggerX);
        camera.setMaxX((short) triggerX);
        services().fadeOutMusic();

        state.routine = ROUTINE_WAIT;
        setWait(WAIT_AFTER_TRIGGER, this::onInitialDelayComplete);
    }

    private void onInitialDelayComplete() {
        state.routine = ROUTINE_DESCEND;
        state.yVel = DESCEND_VEL;
        setWait(DESCEND_TIME, this::onDescendComplete);

        var objectManager = services().objectManager();
        spawnChild(new AizMinibossBodyChild(this), objectManager);
        spawnChild(new AizMinibossArmChild(this), objectManager);
        for (int i = 0; i < 3; i++) {
            spawnChild(new AizMinibossFlameBarrelChild(this, i, false), objectManager);
        }
        // Napalm controller (stays idle for Sonic, activates for Knuckles)
        spawnChild(new AizMinibossNapalmController(this, 0), objectManager);

        services().playMusic(Sonic3kMusic.MINIBOSS.id);
    }

    private void onDescendComplete() {
        state.routine = ROUTINE_SWING;
        setCustomFlag(FLAG_PARENT_COUNTER, 3);
        setCustomFlag(FLAG_PARENT_BITS, getCustomFlag(FLAG_PARENT_BITS) | PARENT_BIT_BARREL_ACTIVATE);
        state.yVel = 0;
        swingMotion.setup1(state);
        setWait(SWING_PREP_TIME, this::onSwingPrepComplete);
    }

    private void onSwingPrepComplete() {
        setWait(FLAME_PREP_TIME, this::onFlamePrepComplete);
    }

    private void onFlamePrepComplete() {
        state.routine = ROUTINE_BREATH;
        setCustomFlag(FLAG_PARENT_COUNTER, 8);
        services().playSfx(Sonic3kSfx.FLAMETHROWER_QUIET.id);
        spawnBreathFlames();
        setWait(BREATH_SWING_TIME, this::onBreathCycleComplete);
    }

    private void onBreathCycleComplete() {
        // ROM: loc_68ADE — Knuckles fight triggers napalm after breath cycle
        PlayerCharacter character = Sonic3kLevelEventManager.getInstance().getPlayerCharacter();
        if (character == PlayerCharacter.KNUCKLES) {
            setCustomFlag(FLAG_PARENT_BITS, getCustomFlag(FLAG_PARENT_BITS) | PARENT_BIT_NAPALM_ACTIVATE);
        }

        state.routine = ROUTINE_DESCEND;
        int bits = getCustomFlag(FLAG_PARENT_BITS) ^ PARENT_BIT_ALT_VERTICAL;
        setCustomFlag(FLAG_PARENT_BITS, bits);

        if ((bits & PARENT_BIT_ALT_VERTICAL) != 0) {
            state.yVel = -DESCEND_VEL;
            setWait(VERTICAL_DRIFT_TIME, this::onVerticalArcPrep);
        } else {
            state.yVel = DESCEND_VEL;
            setWait(VERTICAL_DRIFT_TIME, this::onDescendComplete);
        }
    }

    private void onVerticalArcPrep() {
        state.routine = ROUTINE_HOLD;
        state.xVel = 0;
        state.yVel = 0;
        setWait(HOLD_TIME, this::onHorizontalArcStart);
    }

    private void onHorizontalArcStart() {
        state.routine = ROUTINE_HORIZONTAL_SWING;
        setCustomFlag(FLAG_PARENT_COUNTER, 4);

        int bits = getCustomFlag(FLAG_PARENT_BITS) ^ PARENT_BIT_ALT_HORIZONTAL;
        setCustomFlag(FLAG_PARENT_BITS, bits);

        int xVel = ((bits & PARENT_BIT_ALT_HORIZONTAL) != 0) ? -DESCEND_VEL : DESCEND_VEL;
        state.xVel = xVel;
        swingMotion.setup1(state);
        setWait(HORIZONTAL_TIME, this::onHorizontalArcPivot);
    }

    private void onHorizontalArcPivot() {
        state.renderFlags ^= 1;
        setCustomFlag(FLAG_PARENT_COUNTER, 4);
        setWait(HORIZONTAL_RECOVERY_TIME, this::onHorizontalArcComplete);
    }

    private void onHorizontalArcComplete() {
        state.routine = ROUTINE_HOLD;
        state.xVel = 0;
        state.yVel = 0;
        setWait(HOLD_TIME, this::onBreathCycleComplete);
    }

    private void updateWaitOnly() {
        tickWait();
    }

    private void updateMoveAndWait(boolean applySwing) {
        if (applySwing) {
            swingMotion.update(state);
        }
        state.applyVelocity();
        tickWait();
    }

    private void updateDefeated(int frameCounter) {
        // Tick the stagger explosion controller each frame.
        // ROM: Obj_CreateBossExplosion (sub_52850) spawns one explosion every 3 frames
        // at random offsets within ±$20 pixels. Total: 33 explosions over ~102 frames.
        if (defeatExplosionController != null && !defeatExplosionController.isFinished()) {
            defeatExplosionController.tick();
            var objectManager = services().objectManager();
            if (objectManager != null) {
                for (var pending : defeatExplosionController.drainPendingExplosions()) {
                    objectManager.addDynamicObject(
                            new S3kBossExplosionChild(pending.x(), pending.y()));
                }
            }
            return;
        }

        // Explosions finished — spawn music fade-to-level transition, then signpost flow.
        // ROM: Wait_FadeToLevelMusic → Obj_Song_Fade_ToLevelMusic → Restore_LevelMusic
        // The fire transition swaps tileset/palette to AIZ2's look, but the zone is still
        // technically AIZ1 and the level music is AIZ1.
        if (!defeatRenderComplete) {
            defeatRenderComplete = true;
            spawnDynamicObject(new SongFadeTransitionInstance(120, Sonic3kMusic.AIZ1.id));

            // apparentAct = 0: AIZ miniboss ends act 1 (ROM's Apparent_act is 0 here,
            // even though the engine reloaded act 2 resources for the fire terrain swap)
            S3kBossDefeatSignpostFlow defeatFlow = new S3kBossDefeatSignpostFlow(
                    state.x, 0,
                    () -> {
                        // AfterBoss_AIZ2: restore fire palette to palette line 1.
                        // ROM: lea (Pal_AIZFire).l,a1 / jsr (PalLoad_Line1).l
                        // PalLoad_Line1 copies 32 bytes to Normal_palette_line_2
                        // (S3K 1-based naming: line_2 = engine index 1).
                        // The real miniboss fights in the post-fire section (technically AIZ2),
                        // so we restore Pal_AIZFire, NOT Pal_AIZ (green AIZ1 palette).
                        try {
                            byte[] palData = GameServices.rom().getRom().readBytes(
                                    Sonic3kConstants.PAL_AIZ_FIRE_ADDR, 32);
                            com.openggf.game.GameServices.level().updatePalette(1, palData);
                        } catch (Exception ignored) {
                            // Palette restore failures should not crash gameplay.
                        }
                    }
            );
            spawnDynamicObject(defeatFlow);
        }
    }

    private void setWait(int frames, Runnable callback) {
        waitTimer = frames;
        waitCallback = callback;
    }

    private void tickWait() {
        if (waitTimer < 0) {
            return;
        }
        waitTimer--;
        if (waitTimer >= 0) {
            return;
        }
        Runnable callback = waitCallback;
        waitCallback = null;
        if (callback != null) {
            callback.run();
        }
    }

    private void spawnChild(AbstractBossChild child,
                            ObjectManager objectManager) {
        childComponents.add(child);
        if (objectManager != null) {
            objectManager.addDynamicObject(child);
        }
    }

    private void spawnBreathFlames() {
        var objectManager = services().objectManager();
        if (objectManager == null) {
            return;
        }
        for (int i = 0; i < BREATH_FLAME_X_OFFSETS.length; i++) {
            // CreateChild1_Normal sets subtype = d2 (increments by 2): 0, 2, 4, 6
            objectManager.addDynamicObject(new AizMinibossFlameChild(
                    this,
                    BREATH_FLAME_X_OFFSETS[i],
                    BREATH_FLAME_Y_OFFSETS[i],
                    i * 2));
        }
    }

    /**
     * Destroy all in-flight attack objects spawned by this boss.
     * ROM: barrel children enter defeat animation; existing shots/flames check parent
     * state and stop. We achieve the same by scanning active objects.
     */
    private void destroyAttackObjects() {
        var objectManager = services().objectManager();
        if (objectManager == null) {
            return;
        }
        for (var obj : objectManager.getActiveObjects()) {
            if (obj instanceof AizMinibossBarrelShotChild attack) {
                attack.setDestroyed(true);
            } else if (obj instanceof AizMinibossFlameChild flame) {
                flame.setDestroyed(true);
            } else if (obj instanceof AizMinibossImpactFlameChild impact) {
                impact.setDestroyed(true);
            } else if (obj instanceof AizMinibossNapalmProjectile napalm) {
                napalm.setDestroyed(true);
            } else if (obj instanceof AizMinibossBarrelShotFlareChild flare) {
                flare.setDestroyed(true);
            }
        }
    }

    private void loadBossPalette() {
        try {
            byte[] line = GameServices.rom().getRom().readBytes(
                    Sonic3kConstants.PAL_AIZ_MINIBOSS_ADDR, 32);
            com.openggf.game.GameServices.level().updatePalette(1, line);
        } catch (Exception ignored) {
            // Palette load failures should not crash gameplay.
        }
    }

    private Sonic3kAIZEvents getAizEvents() {
        return Sonic3kLevelEventManager.getInstance().getAizEvents();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || state.routine < ROUTINE_DESCEND || defeatRenderComplete) {
            return;
        }
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        boolean hFlip = (state.renderFlags & 1) != 0;
        renderer.drawFrameIndex(0, state.x, state.y, hFlip, false);
    }

    @Override
    public boolean isHighPriority() {
        // ROM: make_art_tile(ArtTile_AIZMiniboss,1,1) — priority bit = 1
        return true;
    }

    @Override
    public int getPriorityBucket() {
        // ROM: ObjDat_AIZMiniboss priority $0200 → $200/$80 = bucket 4
        return 4;
    }
}
