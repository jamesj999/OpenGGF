package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x2D - AIZ Falling Log (Sonic 3 &amp; Knuckles).
 * <p>
 * An invisible spawner that periodically creates paired child objects: a falling log
 * body (top-solid platform) and a splash effect. The log falls at 1 pixel/frame until
 * reaching the water surface, then floats for 60 frames with a visibility bobbing effect.
 * <p>
 * Subtype encoding:
 * <ul>
 *   <li>Bits [3:0]: period index into word_2B566 timing mask table (spawn interval)</li>
 *   <li>Bits [7:4]: phase offset (shifted left by max(period-3, 0))</li>
 * </ul>
 * <p>
 * ROM references: Obj_AIZFallingLog (sonic3k.asm line 59887), word_2B566 (line 59868),
 * loc_2B5D4 (spawner loop), loc_2B6A0 (log falling), loc_2B6BC (log at water),
 * loc_2B6D8 (solid + draw), loc_2B72C (splash animation).
 */
public class AizFallingLogObjectInstance extends AbstractObjectInstance {

    private static final Logger LOG = Logger.getLogger(AizFallingLogObjectInstance.class.getName());

    // word_2B566: timing mask table (sonic3k.asm line 59868)
    // Index N → mask = (2^(N+1)) - 1, so effective period = mask + 1 frames.
    private static final int[] TIMING_MASKS = {
            0x0001, 0x0003, 0x0007, 0x000F,
            0x001F, 0x003F, 0x007F, 0x00FF,
            0x01FF, 0x03FF, 0x07FF, 0x0FFF,
            0x1FFF, 0x3FFF, 0x7FFF, 0xFFFF
    };

    // Intro-specific deletion: delete spawner at these X positions when trigger is active.
    // ROM: cmpi.w #$26B0,x_pos / cmpi.w #$2700,x_pos / tst.b (Level_trigger_array).w
    private static final int INTRO_DELETE_X_A = 0x26B0;
    private static final int INTRO_DELETE_X_B = 0x2700;

    // Level_trigger_array[0] is set by various AIZ events (e.g., DynamicWaterHeight_AIZ2
    // at camera X >= 0x2850). Approximated here by checking if the event routine has advanced
    // past initial state, indicating dynamic level changes are active.
    private static final int EVENT_TRIGGER_ROUTINE_THRESHOLD = 2;

    private final int timingMask;   // $32(a0): AND mask for Level_frame_counter
    private final int phaseOffset;  // $34(a0): added to frame counter before masking
    private final int spawnX;
    private final int spawnY;
    private final String logArtKey;
    private final String splashArtKey;

    public AizFallingLogObjectInstance(ObjectSpawn spawn) {
        super(spawn, "AIZFallingLog");

        this.spawnX = spawn.x();
        this.spawnY = spawn.y();

        // Parse subtype (sonic3k.asm lines 59900-59914)
        int subtype = spawn.subtype();
        int periodIndex = subtype & 0x0F;
        this.timingMask = periodIndex < TIMING_MASKS.length
                ? TIMING_MASKS[periodIndex] : TIMING_MASKS[TIMING_MASKS.length - 1];

        // Phase offset: high nibble shifted left by max(periodIndex - 3, 0)
        int shift = Math.max(periodIndex - 3, 0);
        this.phaseOffset = ((subtype >> 4) & 0x0F) << shift;

        // Act-dependent art keys
        // Act 1: ArtTile_AIZFallingLog, palette 2 for both
        // Act 2: ArtTile_AIZMisc2, palette 2 for log, palette 3 for splash
        int act = 0;
        try {
            act = services().currentAct();
        } catch (Exception e) {
            LOG.fine(() -> "AizFallingLogObjectInstance.<init>: " + e.getMessage());
        }
        if (act == 0) {
            logArtKey = Sonic3kObjectArtKeys.AIZ1_FALLING_LOG;
            splashArtKey = Sonic3kObjectArtKeys.AIZ1_FALLING_LOG_SPLASH;
        } else {
            logArtKey = Sonic3kObjectArtKeys.AIZ2_FALLING_LOG;
            splashArtKey = Sonic3kObjectArtKeys.AIZ2_FALLING_LOG_SPLASH;
        }
    }

    /**
     * Checks the Level_trigger_array deletion condition.
     * ROM: Obj_AIZFallingLog checks x_pos == $26B0 or $2700, AND Level_trigger_array[0] != 0.
     * Level_trigger_array[0] is set by AIZ event handlers (e.g., DynamicWaterHeight_AIZ2
     * when camera X >= $2850). Approximated via event routine progression.
     */
    private boolean shouldDeleteForIntro() {
        if (spawnX != INTRO_DELETE_X_A && spawnX != INTRO_DELETE_X_B) {
            return false;
        }
        try {
            Sonic3kLevelEventManager lem = Sonic3kLevelEventManager.getInstance();
            if (lem != null) {
                if (services().romZoneId() == Sonic3kZoneIds.ZONE_AIZ) {
                    return lem.getEventRoutineFg() >= EVENT_TRIGGER_ROUTINE_THRESHOLD;
                }
            }
        } catch (Exception e) {
            LOG.fine(() -> "AizFallingLogObjectInstance.shouldDeleteForIntro: " + e.getMessage());
        }
        return false;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (isDestroyed()) return;

        // Intro deletion check (first-frame only via lazy check)
        if (shouldDeleteForIntro()) {
            setDestroyed(true);
            return;
        }

        // Spawner timing: (Level_frame_counter + phaseOffset) & timingMask == 0
        // ROM: loc_2B5D4 (sonic3k.asm line 59918-59922)
        if (((frameCounter + phaseOffset) & timingMask) != 0) {
            return;
        }

        // Spawn paired children: log body + splash
        FallingLogChild log = new FallingLogChild(spawnX, spawnY, logArtKey);
        SplashChild splash = new SplashChild(log, splashArtKey);
        log.setLinkedSplash(splash);

        spawnDynamicObject(log);
        spawnDynamicObject(splash);
    }

    @Override
    public int getX() {
        return spawnX;
    }

    @Override
    public int getY() {
        return spawnY;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Spawner is invisible: no mappings, no rendering (ROM: render_flags = 4 only)
    }

    // ===== Child: Falling Log Body =====

    /**
     * The log body child. Falls at 1px/frame until reaching water level, then floats
     * for 60 frames with a visibility bob (toggling every 4 frames).
     * <p>
     * Top-solid platform: SolidObjectTop with half-width 0x18, half-height 8.
     * <p>
     * ROM references: loc_2B6A0 (falling), loc_2B6BC (at water), loc_2B6D8 (solid+draw).
     */
    static class FallingLogChild extends AbstractObjectInstance
            implements SolidObjectProvider, SolidObjectListener {

        // ROM: move.b #$18,width_pixels(a1)
        private static final int HALF_WIDTH = 0x18;
        // ROM: moveq #8,d3 passed to SolidObjectTop
        private static final int HALF_HEIGHT = 8;
        // ROM: move.w #$280,priority(a1) → bucket 5
        private static final int PRIORITY = 5;
        // ROM: move.b #60-1,anim_frame_timer(a0) → 60 frames at water
        private static final int WATER_SURFACE_TIMER = 59;
        // ROM: every 4 frames toggle visibility (andi.b #3,d0)
        private static final int BOB_MASK = 3;
        // ROM: cmpi.w #$280,d0 — coarse range threshold for culling
        private static final int COARSE_RANGE_THRESHOLD = 0x280;

        private static final SolidObjectParams SOLID_PARAMS =
                new SolidObjectParams(HALF_WIDTH, HALF_HEIGHT, HALF_HEIGHT + 1);

        private static final int STATE_FALLING = 0;
        private static final int STATE_AT_WATER = 1;

        private int x;
        private int y;
        private int state = STATE_FALLING;
        private int timer;
        private boolean bobHidden; // $36(a0) bit 0: toggled for visibility bob
        private final String artKey;
        private SplashChild linkedSplash;

        FallingLogChild(int x, int y, String artKey) {
            super(createDummySpawn(x, y), "FallingLogBody");
            this.x = x;
            this.y = y;
            this.artKey = artKey;
        }

        void setLinkedSplash(SplashChild splash) {
            this.linkedSplash = splash;
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return SOLID_PARAMS;
        }

        @Override
        public boolean isTopSolidOnly() {
            return true;
        }

        @Override
        public boolean isSolidFor(PlayableEntity playerEntity) {
            AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
            return !isDestroyed();
        }

        @Override
        public void onSolidContact(PlayableEntity playerEntity, SolidContact contact,
                int frameCounter) {
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
            return RenderPriority.clamp(PRIORITY);
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
            if (isDestroyed()) return;

            switch (state) {
                case STATE_FALLING -> updateFalling();
                case STATE_AT_WATER -> updateAtWater();
            }

            // ROM coarse range check (loc_2B6D8, sonic3k.asm lines 59994-59998):
            // andi.w #$FF80,d0 / sub.w (Camera_X_pos_coarse_back).w,d0 / cmpi.w #$280,d0
            int coarse = (x & 0xFF80) - services().camera().getX();
            if (coarse < 0 || coarse > COARSE_RANGE_THRESHOLD) {
                destroyWithSplash();
                return;
            }
        }

        /**
         * Log falling state: increment Y by 1 each frame.
         * ROM: loc_2B6A0 - addq.w #1,y_pos(a0)
         * Transitions to water surface state when y >= Water_level.
         */
        private void updateFalling() {
            y += 1;

            int waterLevel = getWaterLevel();
            if (waterLevel > 0 && y >= waterLevel) {
                state = STATE_AT_WATER;
                timer = WATER_SURFACE_TIMER;
            }
        }

        /**
         * Log at water surface: count down timer, bob visibility.
         * ROM: loc_2B6BC - subq.b #1,anim_frame_timer(a0)
         * When timer expires, move offscreen (destroy). Every 4 frames, toggle visibility.
         */
        private void updateAtWater() {
            timer--;
            if (timer < 0) {
                destroyWithSplash();
                return;
            }

            // ROM: andi.b #3,d0 / bchg #0,$36(a0)
            if ((timer & BOB_MASK) == 0) {
                bobHidden = !bobHidden;
            }
        }

        private void destroyWithSplash() {
            setDestroyed(true);
            if (linkedSplash != null && !linkedSplash.isDestroyed()) {
                linkedSplash.setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (bobHidden) return;

            PatternSpriteRenderer renderer = getRenderer(artKey);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(0, x, y, false, false);
            } else {
                appendDebugBox(commands, x, y, HALF_WIDTH, HALF_HEIGHT, 0.6f, 0.4f, 0.2f);
            }
        }

        private int getWaterLevel() {
            try {
                var ws = services().waterSystem();
                if (ws != null) {
                    return ws.getWaterLevelY(services().romZoneId(), services().currentAct());
                }
            } catch (Exception e) {
                LOG.fine(() -> "AizFallingLogObjectInstance.getWaterLevel: " + e.getMessage());
            }
            return 0;
        }
    }

    // ===== Child: Splash Animation =====

    /**
     * Splash effect child. Tracks the linked log body position and animates
     * through 4 frames, cycling every 4 frames.
     * <p>
     * ROM reference: loc_2B72C (sonic3k.asm line 60024).
     */
    static class SplashChild extends AbstractObjectInstance {

        // ROM: move.w #$200,priority(a1) → bucket 4
        private static final int PRIORITY = 4;
        // ROM: move.b #3,anim_frame_timer(a0) → 4 frames per animation frame
        private static final int FRAME_DELAY = 3;
        // ROM: andi.b #3,mapping_frame(a0) → 4 frames total
        private static final int FRAME_COUNT = 4;

        private final FallingLogChild linkedLog;
        private final String artKey;
        private int mappingFrame;
        // ROM: AllocateObjectAfterCurrent zeroes object RAM, so anim_frame_timer starts at 0.
        // First decrement goes to -1 (bpl fails), immediately advancing to frame 1.
        private int animTimer = 0;

        SplashChild(FallingLogChild linkedLog, String artKey) {
            super(createDummySpawn(linkedLog.getX(), linkedLog.getY()), "FallingLogSplash");
            this.linkedLog = linkedLog;
            this.artKey = artKey;
        }

        @Override
        public int getX() {
            return linkedLog.getX();
        }

        @Override
        public int getY() {
            return linkedLog.getY();
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY);
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
            if (isDestroyed()) return;

            // If parent log is destroyed, self-destruct
            if (linkedLog.isDestroyed()) {
                setDestroyed(true);
                return;
            }

            // ROM: loc_2B72C - animation cycling
            animTimer--;
            if (animTimer < 0) {
                animTimer = FRAME_DELAY;
                mappingFrame = (mappingFrame + 1) & (FRAME_COUNT - 1);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (linkedLog.isDestroyed()) return;

            PatternSpriteRenderer renderer = getRenderer(artKey);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(mappingFrame, linkedLog.getX(), linkedLog.getY(), false, false);
            }
        }

    }

    // ===== Utility =====

    private static ObjectSpawn createDummySpawn(int x, int y) {
        return new ObjectSpawn(x, y, 0x2D, 0, 4, false, 0);
    }

    // Uses inherited getRenderer(String) from AbstractObjectInstance

    private static void appendDebugBox(List<GLCommand> commands, int cx, int cy,
            int hw, int hh, float r, float g, float b) {
        int l = cx - hw, rr = cx + hw, t = cy - hh, bt = cy + hh;
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, l, t, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, rr, t, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, rr, t, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, rr, bt, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, rr, bt, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, l, bt, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, l, bt, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1,
                GLCommand.BlendType.SOLID, r, g, b, l, t, 0, 0));
    }
}
