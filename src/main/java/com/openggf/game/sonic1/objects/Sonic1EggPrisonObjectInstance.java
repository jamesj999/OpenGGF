package com.openggf.game.sonic1.objects;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic1.audio.Sonic1Music;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.objects.EggPrisonAnimalInstance;
import com.openggf.game.sonic2.objects.ExplosionObjectInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Sonic 1 Prison Capsule / EggPrison (Object 0x3E).
 * <p>
 * ROM-accurate implementation based on docs/s1disasm/_incObj/3E Prison Capsule.asm.
 * <p>
 * The capsule is a solid object that opens when the boss is defeated. The button
 * (subtype 1) sits on top; when Sonic lands on it after the boss is gone, the
 * opening sequence begins: explosions spawn, then 8 animals burst out, followed
 * by continuous animal spawning. When all animals are gone, GotThroughAct fires.
 * <p>
 * State machine (mirroring ROM routine progression):
 * <ol>
 *   <li>IDLE - Solid body, waiting for button trigger (Pri_BodyMain, routine 2)</li>
 *   <li>EXPLODING - Spawning explosion particles, 60 frames (Pri_Explosion, routine $A)</li>
 *   <li>ANIMAL_SPAWN - Initial burst of 8 + continuous spawning, 150 frames (Pri_Animals, routine $C)</li>
 *   <li>END_ACT - Waiting for all animals to leave, then GotThroughAct (Pri_EndAct, routine $E)</li>
 * </ol>
 */
public class Sonic1EggPrisonObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider {
    private static final Logger LOGGER = Logger.getLogger(Sonic1EggPrisonObjectInstance.class.getName());

    // === Solid collision from Pri_BodyMain: d1=$2B, d2=$18, d3=$18 ===
    private static final int BODY_HALF_WIDTH = 0x2B;   // 43 pixels
    private static final int BODY_HALF_HEIGHT = 0x18;   // 24 pixels

    // From Pri_Var: subtype 0 priority = 4
    private static final int PRIORITY = 4;

    // From disassembly: move.w obY(a0),pri_origY(a0)
    // Button sits at original Y, body renders at original Y
    // After opening: addq.w #8,obY(a0) moves switch down 8 pixels

    // === Explosion phase timing ===
    // From disassembly: move.w #60,obTimeFrame(a0) in Pri_Switched
    private static final int EXPLOSION_TIMER = 60;

    // === Animal burst parameters ===
    // From disassembly: moveq #7,d6 (8 animals); move.w #$9A,d5; moveq #-$1C,d4
    private static final int INITIAL_ANIMAL_COUNT = 8;
    private static final int INITIAL_ANIMAL_DELAY_BASE = 0x9A;
    private static final int INITIAL_ANIMAL_DELAY_STEP = 8;
    private static final int INITIAL_ANIMAL_X_OFFSET_START = -0x1C;
    private static final int INITIAL_ANIMAL_X_OFFSET_STEP = 7;

    // === Continuous spawn phase ===
    // From disassembly: move.w #150,obTimeFrame(a0)
    private static final int SPAWN_PHASE_DURATION = 150;
    // From disassembly: move.w #$C,objoff_36(a1) — animal delay
    private static final int SPAWN_ANIMAL_DELAY = 0xC;

    // === End-act phase ===
    // From disassembly: move.w #180,obTimeFrame(a0)
    private static final int END_ACT_WAIT = 180;

    // === Explosion random spread ===
    // ROM: move.b d0,d1 / lsr.b #2,d1 / subi.w #$20,d1 → X range [-32, +31]
    // ROM: lsr.w #8,d0 / lsr.b #3,d0 → Y range [0, 31]
    private static final int EXPLOSION_X_RANGE = 0x20;  // subtracted from 0-63
    private static final int EXPLOSION_Y_RANGE = 32;    // 0-31 pixels

    // === Mapping frame indices from Map_Pri ===
    private static final int FRAME_CAPSULE = 0;
    private static final int FRAME_BROKEN = 2;
    private static final int FRAME_BLANK = 6;

    // === State machine ===
    private enum State {
        IDLE,           // Pri_BodyMain (routine 2): waiting for button
        EXPLODING,      // Pri_Explosion (routine $A): spawning explosions
        ANIMAL_SPAWN,   // Pri_Animals (routine $C): continuous random spawning
        END_ACT,        // Pri_EndAct (routine $E): waiting for animals to clear
        COMPLETE        // GotThroughAct triggered, results screen active
    }

    private State state = State.IDLE;
    private int timer;
    private int currentFrame = FRAME_CAPSULE;
    private boolean buttonTriggered;
    private boolean resultsTriggered;

    // Button sub-object
    private Sonic1EggPrisonButtonObjectInstance buttonObject;

    // Player reference for results screen
    private AbstractPlayableSprite lastPlayer;

    public Sonic1EggPrisonObjectInstance(ObjectSpawn spawn) {
        super(spawn, "EggPrison");
    }

    /**
     * Called by the button sub-object (subtype 1) to register itself with this body.
     * The button is a separate placement entry in the ROM; it finds us on first update.
     */
    public void registerButton(Sonic1EggPrisonButtonObjectInstance button) {
        this.buttonObject = button;
    }

    /**
     * Called by button when player lands on it.
     * Corresponds to Pri_Switched first-time trigger path.
     */
    public void onButtonTriggered() {
        if (buttonTriggered) {
            return;
        }
        buttonTriggered = true;

        LOGGER.info("S1 EggPrison triggered at X=" + spawn.x());

        // ROM: clr.b (f_timecount).w — stop time counter
        var levelGamestate = LevelManager.getInstance().getLevelGamestate();
        if (levelGamestate != null) {
            levelGamestate.pauseTimer();
        }

        // Lock the camera at the current position so it stays on the prison
        // while Sonic runs off the right side of the screen.
        // ROM: clr.b (f_lockscreen).w — in the ROM this clears the scroll lock,
        // but the camera stays put because v_limitleft2 = v_limitright2.
        Camera camera = Camera.getInstance();
        if (camera != null) {
            if (camera.getFrozen()) {
                camera.setFrozen(false);
            }
            // Lock camera horizontally at current position
            camera.setMinX(camera.getX());
            camera.setMaxX(camera.getX());
        }

        // Clear boss fight state so doLevelBoundary allows Sonic to exceed
        // the right screen edge (+64 extra when boss fight is not active).
        // This is needed even if the boss was never "defeated" (e.g. LZ boss
        // just escapes without being hit 8 times).
        GameServices.gameState().setCurrentBossId(0);

        // ROM: move.b #1,(f_lockctrl).w — lock player controls
        // ROM: move.w #(btnR<<8),(v_jpadhold2).w — force right input
        // These are applied in the update loop to ensure they stick

        // Body stays on FRAME_CAPSULE during explosion phase.
        // It switches to FRAME_BROKEN when .makeanimal fires (v_bossstatus = 2 in ROM)

        // Begin explosion phase (Pri_Switched sets routine = $A)
        state = State.EXPLODING;
        timer = EXPLOSION_TIMER;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }
        this.lastPlayer = player;

        switch (state) {
            case IDLE -> updateIdle();
            case EXPLODING -> updateExploding(frameCounter);
            case ANIMAL_SPAWN -> updateAnimalSpawn(frameCounter);
            case END_ACT -> updateEndAct(player);
            case COMPLETE -> { /* Nothing — results screen active */ }
        }

        // Keep player locked to right during opening sequence
        if (buttonTriggered && state != State.IDLE && state != State.COMPLETE) {
            player.setForceInputRight(true);
            player.setControlLocked(true);
        }
    }

    /**
     * Pri_BodyMain (routine 2): Solid object, waiting for button trigger.
     * In S1, the body just acts as solid until the button activates.
     */
    private void updateIdle() {
        // Nothing to do — button handles the trigger via onButtonTriggered()
    }

    /**
     * Pri_Explosion (routines 6/8/$A): Spawn explosion particles.
     * ROM: Spawn one explosion every 8 frames with random offset.
     * After timer expires, spawn initial animal burst.
     */
    private void updateExploding(int frameCounter) {
        // ROM: move.b (v_vbla_byte).w,d0 / andi.b #7,d0 / bne.s .skip
        if ((frameCounter & 7) == 0) {
            spawnExplosion();
        }

        timer--;
        if (timer <= 0) {
            // ROM: .makeanimal — move.b #2,(v_bossstatus).w
            // This triggers Pri_BodyMain to show broken frame (frame 2)
            currentFrame = FRAME_BROKEN;

            // ROM: move.b #$C,obRoutine(a0) — switch to animal spawning
            // ROM: move.b #6,obFrame(a0) — blank frame for explosion sub-object
            spawnInitialAnimals();
            state = State.ANIMAL_SPAWN;
            timer = SPAWN_PHASE_DURATION;
        }
    }

    /**
     * Pri_Animals (routine $C): Continuous random animal spawning.
     * ROM: Every 8 frames, spawn one animal at random X offset.
     */
    private void updateAnimalSpawn(int frameCounter) {
        // ROM: move.b (v_vbla_byte).w,d0 / andi.b #7,d0 / bne.s .skip
        if ((frameCounter & 7) == 0) {
            spawnRandomAnimal();
        }

        timer--;
        if (timer <= 0) {
            state = State.END_ACT;
            timer = END_ACT_WAIT;
        }
    }

    /**
     * Pri_EndAct (routine $E): Wait for all animals to leave, then trigger level end.
     * ROM: Loops through object RAM looking for id_Animals (0x28).
     */
    private void updateEndAct(AbstractPlayableSprite player) {
        if (timer > 0) {
            timer--;
            return;
        }

        // After wait, check every frame if animals are gone
        if (!areAnimalsPresent()) {
            triggerGotThroughAct(player);
        }
    }

    /**
     * Spawns an explosion at random offset from capsule center.
     * ROM: Pri_Explosion random offset logic.
     */
    private void spawnExplosion() {
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (objectManager == null || renderManager == null) {
            return;
        }

        int baseX = spawn.x();
        int baseY = spawn.y();

        // ROM: move.b d0,d1 / lsr.b #2,d1 / subi.w #$20,d1 → X offset [-32, +31]
        int xOff = ThreadLocalRandom.current().nextInt(64) - EXPLOSION_X_RANGE;
        // ROM: lsr.w #8,d0 / lsr.b #3,d0 → Y offset [0, 31]
        int yOff = ThreadLocalRandom.current().nextInt(EXPLOSION_Y_RANGE);

        // ROM: Explosion object 0x3F plays sfx_Bomb on init
        ExplosionObjectInstance explosion = new ExplosionObjectInstance(
                0x3F, baseX + xOff, baseY + yOff, renderManager, Sonic1Sfx.BOSS_EXPLOSION.id);
        objectManager.addDynamicObject(explosion);
    }

    /**
     * Spawns 8 initial animals with staggered delays.
     * ROM: Pri_Explosion .makeanimal loop — d6=7, d5=$9A, d4=-$1C
     */
    private void spawnInitialAnimals() {
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager == null) {
            return;
        }

        int baseX = spawn.x();
        int baseY = spawn.y();
        int xOffset = INITIAL_ANIMAL_X_OFFSET_START;
        int delay = INITIAL_ANIMAL_DELAY_BASE;

        for (int i = 0; i < INITIAL_ANIMAL_COUNT; i++) {
            ObjectSpawn animalSpawn = new ObjectSpawn(
                    baseX + xOffset, baseY,
                    0x28, 0, 0, false, 0);
            EggPrisonAnimalInstance animal = new EggPrisonAnimalInstance(animalSpawn, delay);
            objectManager.addDynamicObject(animal);

            xOffset += INITIAL_ANIMAL_X_OFFSET_STEP;
            delay -= INITIAL_ANIMAL_DELAY_STEP;
        }
    }

    /**
     * Spawns a random animal at the capsule position.
     * ROM: Pri_Animals random spawn — andi.w #$1F,d0 / subq.w #6,d0
     */
    private void spawnRandomAnimal() {
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager == null) {
            return;
        }

        int baseX = spawn.x();
        int baseY = spawn.y();

        // ROM: jsr (RandomNumber).l / andi.w #$1F,d0 / subq.w #6,d0
        int randomOffset = ThreadLocalRandom.current().nextInt(32) - 6;
        // ROM: tst.w d1 / bpl.s + / neg.w d0
        if (ThreadLocalRandom.current().nextBoolean()) {
            randomOffset = -randomOffset;
        }

        ObjectSpawn animalSpawn = new ObjectSpawn(
                baseX + randomOffset, baseY,
                0x28, 0, 0, false, 0);
        EggPrisonAnimalInstance animal = new EggPrisonAnimalInstance(animalSpawn, SPAWN_ANIMAL_DELAY);
        objectManager.addDynamicObject(animal);
    }

    /**
     * Checks if any EggPrisonAnimalInstance objects remain active.
     * ROM: Pri_EndAct loop through object RAM for id_Animals.
     */
    private boolean areAnimalsPresent() {
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager == null) {
            return false;
        }

        for (var obj : objectManager.getActiveObjects()) {
            if (obj instanceof EggPrisonAnimalInstance && !obj.isDestroyed()) {
                return true;
            }
        }
        return false;
    }

    /**
     * GotThroughAct — triggers level completion and results screen.
     * ROM: jsr (GotThroughAct).l / jmp (DeleteObject).l
     */
    private void triggerGotThroughAct(AbstractPlayableSprite player) {
        if (resultsTriggered) {
            return;
        }
        resultsTriggered = true;
        state = State.COMPLETE;

        LOGGER.info("S1 EggPrison: all animals gone, triggering GotThroughAct");

        // ROM: clr.b (v_invinc).w — clear invincibility
        player.setInvincibleFrames(0);

        // ROM: move.w #bgm_GotThrough,d0; jsr (QueueSound2).l
        try {
            AudioManager.getInstance().playMusic(Sonic1Music.GOT_THROUGH.id);
        } catch (Exception e) {
            LOGGER.warning("Failed to play stage clear music: " + e.getMessage());
        }

        // Spawn results screen
        LevelManager levelManager = LevelManager.getInstance();
        var levelGamestate = levelManager.getLevelGamestate();
        int elapsedSeconds = levelGamestate != null ? levelGamestate.getElapsedSeconds() : 0;
        int ringCount = player.getRingCount();
        int actNumber = levelManager.getCurrentAct() + 1;

        Sonic1ResultsScreenObjectInstance resultsScreen = new Sonic1ResultsScreenObjectInstance(
                elapsedSeconds, ringCount, actNumber);
        ObjectManager objectManager = levelManager.getObjectManager();
        if (objectManager != null) {
            objectManager.addDynamicObject(resultsScreen);
        }

        // Detach button (keep it alive for visual during results)
        if (buttonObject != null) {
            buttonObject.detachFromParent();
        }
    }

    // === SolidObjectProvider ===

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(BODY_HALF_WIDTH, BODY_HALF_HEIGHT, BODY_HALF_HEIGHT);
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        return true;
    }

    @Override
    public boolean shouldStayActiveWhenRemembered() {
        return true;
    }

    // === Rendering ===

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        PatternSpriteRenderer renderer = renderManager != null
                ? renderManager.getEggPrisonRenderer()
                : null;

        if (renderer == null || !renderer.isReady()) {
            renderPlaceholder(commands);
            return;
        }

        renderer.drawFrameIndex(currentFrame, spawn.x(), spawn.y(), false, false);
    }

    private void renderPlaceholder(List<GLCommand> commands) {
        int x = spawn.x();
        int y = spawn.y();
        int left = x - BODY_HALF_WIDTH;
        int right = x + BODY_HALF_WIDTH;
        int top = y - BODY_HALF_HEIGHT;
        int bottom = y + BODY_HALF_HEIGHT;

        appendLine(commands, left, top, right, top, 0.8f, 0.6f, 0.2f);
        appendLine(commands, right, top, right, bottom, 0.8f, 0.6f, 0.2f);
        appendLine(commands, right, bottom, left, bottom, 0.8f, 0.6f, 0.2f);
        appendLine(commands, left, bottom, left, top, 0.8f, 0.6f, 0.2f);
    }

    private void appendLine(List<GLCommand> commands, int x1, int y1, int x2, int y2,
                             float r, float g, float b) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x2, y2, 0, 0));
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        ctx.drawRect(spawn.x(), spawn.y(), BODY_HALF_WIDTH, BODY_HALF_HEIGHT, 0.0f, 1.0f, 0.0f);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }
}
