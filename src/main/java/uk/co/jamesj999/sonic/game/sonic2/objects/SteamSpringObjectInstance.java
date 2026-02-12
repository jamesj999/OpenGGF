package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.audio.GameSound;
import uk.co.jamesj999.sonic.debug.DebugRenderContext;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AnimationIds;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectManager;
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
 * Steam Spring (Object 0x42) - Steam-powered spring piston from Metropolis Zone.
 * <p>
 * A piston platform that periodically rises and sinks. When fully risen (state 2),
 * standing on it launches the player upward with spring force. At peak rise, two
 * steam puff child objects are spawned that animate outward and briefly have
 * enemy-type collision.
 * <p>
 * ROM reference: s2.asm Obj42 (lines 52000-52189)
 * <ul>
 *   <li>Solid: d1=$1B (27), d2=$10 (16), d3=$10 (16)</li>
 *   <li>Art (piston): ArtKos_LevelArt, palette line 3, frame 7</li>
 *   <li>Art (steam): ArtNem_MtzSteam, palette line 1, frames 0-6</li>
 *   <li>Spring velocity: -$A00</li>
 *   <li>State machine: Wait -> Rise -> Wait -> Sink -> repeat</li>
 * </ul>
 */
public class SteamSpringObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    // ROM: move.w #-$A00,y_vel(a1) at loc_26798
    private static final int SPRING_VELOCITY = -0xA00;

    // ROM: move.w #$3B,objoff_32(a0) - wait timer (59 frames)
    private static final int WAIT_TIMER = 0x3B;

    // ROM: move.w #$10,objoff_36(a0) - initial Y offset (piston starts lowered by 16px)
    private static final int INITIAL_Y_OFFSET = 0x10;

    // ROM: subq.w #8,objoff_36(a0) - rise step per frame
    private static final int RISE_STEP = 8;

    // ROM: d1=$1B, d2=$10, d3=$10
    private static final int SOLID_HALF_WIDTH = 0x1B;
    private static final int SOLID_HALF_HEIGHT = 0x10;

    // ROM: addi.w #$28,x_pos(a1) / subi.w #$28,x_pos(a1) - steam puff X offsets
    private static final int STEAM_PUFF_X_OFFSET = 0x28;

    // State machine (routine_secondary values, divided by 2 for indexing)
    private static final int STATE_WAIT_BEFORE_RISE = 0;
    private static final int STATE_RISING = 1;
    private static final int STATE_WAIT_BEFORE_SINK = 2;
    private static final int STATE_SINKING = 3;

    // Position tracking
    private final int baseY;     // objoff_34: original Y position (before +16 offset)
    private int yOffset;         // objoff_36: current Y offset from baseY (16 = fully lowered, 0 = fully risen)
    private int timer;           // objoff_32: countdown timer for wait states
    private int state;           // routine_secondary / 2

    // Dynamic spawn for solid collision (Y position changes)
    private ObjectSpawn dynamicSpawn;

    public SteamSpringObjectInstance(ObjectSpawn spawn) {
        // ROM: move.w y_pos(a0),objoff_34(a0) stores original Y
        // ROM: addi.w #$10,y_pos(a0) shifts visible position down 16px
        super(spawn, "SteamSpring");
        this.baseY = spawn.y();
        this.yOffset = INITIAL_Y_OFFSET;
        this.timer = 0;
        this.state = STATE_WAIT_BEFORE_RISE;
        updateDynamicSpawn();
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        switch (state) {
            case STATE_WAIT_BEFORE_RISE -> updateWaitBeforeRise();
            case STATE_RISING -> updateRising();
            case STATE_WAIT_BEFORE_SINK -> updateWaitBeforeSink();
            case STATE_SINKING -> updateSinking();
        }
        updateDynamicSpawn();
    }

    // ROM: routine_secondary == 0 (loc_266C6)
    private void updateWaitBeforeRise() {
        timer--;
        if (timer < 0) {
            // ROM: move.w #$3B,objoff_32(a0) / addq.b #2,routine_secondary(a0)
            timer = WAIT_TIMER;
            state = STATE_RISING;
        }
    }

    // ROM: routine_secondary == 2 (loc_266E4)
    private void updateRising() {
        // ROM: subq.w #8,objoff_36(a0)
        yOffset -= RISE_STEP;
        if (yOffset <= 0) {
            yOffset = 0;
            // ROM: addq.b #2,routine_secondary(a0)
            state = STATE_WAIT_BEFORE_SINK;
            // ROM: bsr.s loc_2674C (spawn steam puffs)
            spawnSteamPuffs();
        }
    }

    // ROM: routine_secondary == 4 (loc_26716)
    private void updateWaitBeforeSink() {
        timer--;
        if (timer < 0) {
            // ROM: move.w #$3B,objoff_32(a0) / addq.b #2,routine_secondary(a0)
            timer = WAIT_TIMER;
            state = STATE_SINKING;
        }
    }

    // ROM: routine_secondary == 6 (loc_2672C)
    private void updateSinking() {
        // ROM: addq.w #8,objoff_36(a0)
        yOffset += RISE_STEP;
        if (yOffset >= INITIAL_Y_OFFSET) {
            yOffset = INITIAL_Y_OFFSET;
            // ROM: clr.b routine_secondary(a0) - reset to state 0
            state = STATE_WAIT_BEFORE_RISE;
        }
    }

    /**
     * ROM: loc_2674C - Spawn two steam puff child objects.
     * First puff at x+0x28, second at x-0x28 (with x_flip).
     */
    private void spawnSteamPuffs() {
        LevelManager levelManager = LevelManager.getInstance();
        if (levelManager == null) {
            return;
        }
        ObjectManager objectManager = levelManager.getObjectManager();
        if (objectManager == null) {
            return;
        }

        int cx = spawn.x();
        int cy = baseY; // ROM: move.w objoff_34(a0),y_pos(a1)

        // Right puff (normal orientation)
        SteamPuffObjectInstance rightPuff = new SteamPuffObjectInstance(
                cx + STEAM_PUFF_X_OFFSET, cy, false);
        objectManager.addDynamicObject(rightPuff);

        // Left puff (x-flipped)
        // ROM: subi.w #$28,x_pos(a1) / bset #render_flags.x_flip,render_flags(a1)
        SteamPuffObjectInstance leftPuff = new SteamPuffObjectInstance(
                cx - STEAM_PUFF_X_OFFSET, cy, true);
        objectManager.addDynamicObject(leftPuff);
    }

    // --- Solid object interface ---

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, SOLID_HALF_HEIGHT);
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (player == null || !contact.standing()) {
            return;
        }
        // ROM: loc_2678E - only spring when state == STATE_RISING (routine_secondary == 2)
        // Actually ROM checks cmpi.b #2,routine_secondary(a0) which is the rising state
        if (state != STATE_RISING) {
            return;
        }
        // ROM: yOffset reaches 0 at the moment of spring, but the spring check happens
        // every frame during state 2 when player is standing
        applySpring(player);
    }

    /**
     * ROM: loc_26798 - Apply spring force to standing player.
     */
    private void applySpring(AbstractPlayableSprite player) {
        // ROM: move.w #-$A00,y_vel(a1)
        player.setYSpeed((short) SPRING_VELOCITY);

        // ROM: bset #status.player.in_air,status(a1)
        player.setAir(true);

        // ROM: bclr #status.player.on_object,status(a1)
        // Engine handles this through the solid contact system

        // ROM: move.b #AniIDSonAni_Spring,anim(a1)
        player.setAnimationId(Sonic2AnimationIds.SPRING);

        // ROM: move.b #2,routine(a1)
        // Engine handles this through state management

        // ROM: move.b #0,spindash_flag(a1)
        player.setSpindash(false);

        player.setGSpeed((short) 0);
        player.setSpringing(15);

        // ROM: move.b subtype(a0),d0 / bpl.s + / move.w #0,x_vel(a1)
        // Subtype bit 7: if set, clear X velocity
        if ((spawn.subtype() & 0x80) != 0) {
            player.setXSpeed((short) 0);
        }

        // ROM: btst #0,d0 / beq.s loc_26808
        // Subtype bit 0: enable flip/twirl animation
        int subtype = spawn.subtype();
        if ((subtype & 0x01) != 0) {
            // ROM: move.w #1,inertia(a1)
            player.setGSpeed((short) 1);
            // ROM: move.b #1,flip_angle(a1)
            player.setFlipAngle(1);
            // ROM: move.b #AniIDSonAni_Walk,anim(a1)
            player.setAnimationId(Sonic2AnimationIds.WALK);
            // ROM: move.b #0,flips_remaining(a1) / move.b #4,flip_speed(a1)
            player.setFlipsRemaining(0);
            player.setFlipSpeed(4);
            // ROM: btst #1,d0 / bne.s + / move.b #1,flips_remaining(a1)
            if ((subtype & 0x02) == 0) {
                player.setFlipsRemaining(1);
            }
            // ROM: btst #status.player.x_flip,status(a1) / beq.s loc_26808
            // Negate flip_angle and inertia if player facing left
            if (player.getDirection() == uk.co.jamesj999.sonic.physics.Direction.LEFT) {
                player.setFlipAngle(-player.getFlipAngle());
                player.setGSpeed((short) -player.getGSpeed());
            }
        }

        // ROM: loc_26808 - Apply collision layer bits from subtype
        SpringHelper.applyCollisionLayerBits(player, subtype);

        // ROM: move.w #SndID_Spring,d0 / jmp (PlaySound).l
        try {
            if (AudioManager.getInstance() != null) {
                AudioManager.getInstance().playSfx(GameSound.SPRING);
            }
        } catch (Exception e) {
            // Prevent audio failure from breaking game logic
        }
    }

    // --- Position ---

    @Override
    public int getX() {
        return spawn.x();
    }

    @Override
    public int getY() {
        // ROM: y_pos = objoff_34 + objoff_36 (base + offset)
        return baseY + yOffset;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return dynamicSpawn;
    }

    private void updateDynamicSpawn() {
        int currentY = baseY + yOffset;
        if (dynamicSpawn != null && dynamicSpawn.y() == currentY) {
            return;
        }
        dynamicSpawn = new ObjectSpawn(
                spawn.x(),
                currentY,
                spawn.objectId(),
                spawn.subtype(),
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
    }

    // --- Rendering ---

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager != null) {
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.MTZ_STEAM_PISTON);
            if (renderer != null && renderer.isReady()) {
                // Render piston body at current position (frame 0 = the only frame in piston sheet)
                renderer.drawFrameIndex(0, spawn.x(), baseY + yOffset, false, false);
                return;
            }
        }
        // Fallback: debug box
        appendDebugBox(commands);
    }

    private void appendDebugBox(List<GLCommand> commands) {
        int cx = spawn.x();
        int cy = baseY + yOffset;
        int hw = 0x10;
        int hh = 0x10;
        float r = 0.6f, g = 0.6f, b = 1.0f;
        addLine(commands, cx - hw, cy - hh, cx + hw, cy - hh, r, g, b);
        addLine(commands, cx + hw, cy - hh, cx + hw, cy + hh, r, g, b);
        addLine(commands, cx + hw, cy + hh, cx - hw, cy + hh, r, g, b);
        addLine(commands, cx - hw, cy + hh, cx - hw, cy - hh, r, g, b);
    }

    private void addLine(List<GLCommand> commands, int x1, int y1, int x2, int y2,
                          float r, float g, float b) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x2, y2, 0, 0));
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        int cx = spawn.x();
        int cy = baseY + yOffset;

        // Solid bounds
        ctx.drawRect(cx, cy, SOLID_HALF_WIDTH, SOLID_HALF_HEIGHT, 0.2f, 0.8f, 1.0f);

        // State info
        String stateStr = switch (state) {
            case STATE_WAIT_BEFORE_RISE -> "WAIT(rise) " + timer;
            case STATE_RISING -> "RISING off=" + yOffset;
            case STATE_WAIT_BEFORE_SINK -> "WAIT(sink) " + timer;
            case STATE_SINKING -> "SINKING off=" + yOffset;
            default -> "?";
        };
        ctx.drawWorldLabel(cx, cy - SOLID_HALF_HEIGHT - 10, 0, stateStr, java.awt.Color.CYAN);
    }
}
