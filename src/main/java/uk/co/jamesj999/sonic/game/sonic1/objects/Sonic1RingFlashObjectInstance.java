package uk.co.jamesj999.sonic.game.sonic1.objects;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Music;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Sonic 1 Ring Flash effect (Object 0x7C) - spawned when the Giant Ring is collected.
 * <p>
 * From docs/s1disasm/_incObj/7C Ring Flash.asm:
 * <ul>
 *   <li>Routine 0 (Flash_Main): Init - set mappings, priority 0 (foreground), frame $FF</li>
 *   <li>Routine 2 (Flash_ChkDel): Animate via Flash_Collect subroutine</li>
 *   <li>Routine 4 (Flash_Delete): Object deletion</li>
 * </ul>
 * <p>
 * Animation: 8 frames at 2 game frames each (total 16 frames = ~0.267s).
 * At frame 3: deletes parent Giant Ring, sets f_bigring flag.
 * At frame 8: removes flash object.
 * <p>
 * Art: Nem_BigFlash at ArtTile_Giant_Ring_Flash ($462), palette line 1.
 */
public class Sonic1RingFlashObjectInstance extends AbstractObjectInstance {

    private static final Logger LOGGER = Logger.getLogger(Sonic1RingFlashObjectInstance.class.getName());

    // Flash_Collect: move.b #1,obTimeFrame(a0) - 2 game frames per animation frame
    private static final int FRAME_DURATION = 2;

    // Flash_Collect: cmpi.b #8,obFrame(a0) - total animation frames
    private static final int TOTAL_FRAMES = 8;

    // Flash_Collect: cmpi.b #3,obFrame(a0) - trigger frame for parent deletion
    private static final int TRIGGER_FRAME = 3;

    private final Sonic1GiantRingObjectInstance parent;
    private final int posX;
    private final int posY;
    private final boolean hFlip;

    // ROM: obFrame starts at $FF, obTimeFrame starts at 0
    // First tick: timer=0 -> subq makes it $FF (negative) -> advances frame from $FF to $00
    private int frameTimer = 0;
    private int animFrame = -1; // Will advance to 0 on first tick
    private boolean triggerFired = false;
    private boolean finished = false;

    /**
     * Creates a Ring Flash at the given position.
     *
     * @param parent the parent Giant Ring object
     * @param x      center X position (from parent ring)
     * @param y      center Y position (from parent ring)
     * @param hFlip  true if Sonic approached from the right
     */
    public Sonic1RingFlashObjectInstance(Sonic1GiantRingObjectInstance parent,
                                         int x, int y, boolean hFlip) {
        super(new ObjectSpawn(x, y, 0x7C, 0, 0, false, 0), "RingFlash");
        this.parent = parent;
        this.posX = x;
        this.posY = y;
        this.hFlip = hFlip;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (finished) {
            setDestroyed(true);
            return;
        }

        // Flash_Collect subroutine
        frameTimer--;
        if (frameTimer >= 0) {
            return; // Timer hasn't expired: bpl.s locret_9F76
        }

        // Reset timer: move.b #1,obTimeFrame(a0)
        frameTimer = FRAME_DURATION - 1;

        // Advance frame: addq.b #1,obFrame(a0)
        animFrame++;

        // Check animation end: cmpi.b #8,obFrame(a0) / bhs.s Flash_End
        if (animFrame >= TOTAL_FRAMES) {
            // Flash_End: trigger level ending sequence, then transition to special stage
            triggerLevelEnd(player);
            finished = true;
            return;
        }

        // Check trigger frame: cmpi.b #3,obFrame(a0) / bne.s locret_9F76
        if (animFrame == TRIGGER_FRAME && !triggerFired) {
            triggerFired = true;

            // ROM: move.b #6,obRoutine(a1) - delete parent Giant Ring
            if (parent != null) {
                parent.onFlashFrame3();
            }

            // ROM: move.b #id_Null,(v_player+obAnim).w - make Sonic invisible
            // ROM: clr.b (v_invinc).w - remove invincibility
            // ROM: clr.b (v_shield).w - remove shield
            if (player != null) {
                player.setHidden(true);
                player.setObjectControlled(true);
                player.clearPowerUps();
            }

            // Pause the level timer
            var levelGamestate = LevelManager.getInstance().getLevelGamestate();
            if (levelGamestate != null) {
                levelGamestate.pauseTimer();
            }
        }
    }

    /**
     * Triggers the level ending sequence (results screen) and queues a special stage transition.
     * Called when the flash animation completes (frame 8).
     */
    private void triggerLevelEnd(AbstractPlayableSprite player) {
        LevelManager levelManager = LevelManager.getInstance();

        // Play "Got Through" music
        try {
            AudioManager.getInstance().playMusic(Sonic1Music.GOT_THROUGH.id);
        } catch (Exception e) {
            LOGGER.warning("Failed to play stage clear music: " + e.getMessage());
        }

        // Gather results data
        var levelGamestate = levelManager.getLevelGamestate();
        int elapsedSeconds = levelGamestate != null ? levelGamestate.getElapsedSeconds() : 0;
        int ringCount = player != null ? player.getRingCount() : 0;
        int actNumber = levelManager.getCurrentAct() + 1;

        // Spawn results screen with special stage transition
        Sonic1ResultsScreenObjectInstance resultsScreen = new Sonic1ResultsScreenObjectInstance(
                elapsedSeconds, ringCount, actNumber);
        resultsScreen.setSpecialStageAfter(true);

        ObjectManager objectManager = levelManager.getObjectManager();
        if (objectManager != null) {
            objectManager.addDynamicObject(resultsScreen);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (finished || animFrame < 0) {
            return;
        }
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.GIANT_RING_FLASH);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(animFrame, posX, posY, hFlip, false);
    }

    @Override
    public int getPriorityBucket() {
        // ROM: move.b #0,obPriority(a0) - highest priority (foreground)
        return RenderPriority.clamp(0);
    }

    @Override
    public boolean isPersistent() {
        // Flash must complete its animation even if spawn position goes off-screen
        return true;
    }

    @Override
    public boolean shouldStayActiveWhenRemembered() {
        return true;
    }
}
