package uk.co.jamesj999.sonic.game.sonic1.objects;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.audio.GameAudioProfile;
import uk.co.jamesj999.sonic.audio.GameSound;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1AudioProfile;
import uk.co.jamesj999.sonic.game.sonic2.objects.ExplosionObjectInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.ObjectAnimationState;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.*;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;
import uk.co.jamesj999.sonic.physics.ObjectTerrainUtils;
import uk.co.jamesj999.sonic.physics.TerrainCheckResult;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Sonic 1 Monitor (item box) object.
 * <p>
 * Object ID 0x26. Contains power-ups awarded when broken by the player.
 * Subtypes: 0=static, 1=Eggman, 2=Sonic/1-up, 3=Speed Shoes, 4=Shield,
 * 5=Invincibility, 6=10 Rings, 7='S', 8=Goggles, 9=Broken.
 * <p>
 * Reference: docs/s1disasm/_incObj/26 Monitor.asm
 */
public class Sonic1MonitorObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener,
        SolidObjectProvider, SolidObjectListener {
    private static final Logger LOGGER = Logger.getLogger(Sonic1MonitorObjectInstance.class.getName());

    // From disassembly: obHeight/obWidth = $0E
    private static final int HALF_RADIUS = 0x0E;

    // From disassembly: Pow_Main sets obVelY = -$300
    private static final int ICON_INITIAL_VELOCITY = -0x300;

    // From disassembly: Pow_Move adds $18 to obVelY per frame
    private static final int ICON_RISE_ACCEL = 0x18;

    // From disassembly: Pow_Move sets timer to $1D (29 frames)
    private static final int ICON_WAIT_FRAMES = 0x1D;

    // Map_Monitor frame 11 = broken shell
    private static final int BROKEN_FRAME = 0x0B;

    // From disassembly: Pow_ChkRings adds 10 rings
    private static final int RING_MONITOR_REWARD = 10;

    // From disassembly: Touch_Monitor upward pop velocity
    private static final int FALLING_INITIAL_VEL = -0x180;

    // Standard object gravity
    private static final int FALLING_GRAVITY = 0x38;

    // Icon frame = type.id + 2 (from disassembly: obFrame = obAnim + 2 in Pow_Main)
    private static final int ICON_FRAME_OFFSET = 2;

    private final MonitorType type;
    private final ObjectAnimationState animationState;
    private boolean broken;
    private int mappingFrame;

    // Icon rising state (PowerUp object in disassembly)
    private boolean iconActive;
    private int iconSubY;
    private int iconVelY;
    private int iconWaitFrames;
    private boolean effectApplied;
    private AbstractPlayableSprite effectTarget;
    private AbstractPlayableSprite iconPlayer;

    // Falling state (ob2ndRout = 4 in disassembly)
    private boolean falling;
    private int yVel;
    private int yFixed;
    private int currentY;

    public Sonic1MonitorObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Monitor");
        this.type = MonitorType.fromSubtype(spawn.subtype());

        // Check persistence: if previously broken, spawn as broken shell
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        boolean previouslyBroken = objectManager != null && objectManager.isRemembered(spawn);
        this.broken = this.type == MonitorType.BROKEN || previouslyBroken;

        // Initialize animation: obAnim = obSubtype (from Mon_Main)
        int initialAnim = type.id;
        int initialFrame = broken ? BROKEN_FRAME : 0;
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        this.animationState = new ObjectAnimationState(
                renderManager != null ? renderManager.getMonitorAnimations() : null,
                initialAnim,
                initialFrame);
        this.mappingFrame = initialFrame;
        if (broken) {
            effectApplied = true;
        }

        this.currentY = spawn.y();
        this.yFixed = spawn.y() << 8;
    }

    @Override
    public boolean shouldStayActiveWhenRemembered() {
        return true;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (falling) {
            updateFalling();
        }

        if (!broken) {
            animationState.update();
            mappingFrame = animationState.getMappingFrame();
            return;
        }
        updateIcon();
    }

    /**
     * Update falling monitor after being hit from below.
     * ROM: ObjectFall + ObjFloorDist
     */
    private void updateFalling() {
        yFixed += yVel;
        yVel += FALLING_GRAVITY;
        currentY = yFixed >> 8;

        TerrainCheckResult result = ObjectTerrainUtils.checkFloorDist(spawn.x(), currentY, HALF_RADIUS);
        if (result.hasCollision()) {
            currentY = currentY + result.distance();
            yFixed = currentY << 8;
            yVel = 0;
            falling = false;
        }
    }

    @Override
    public void onTouchResponse(AbstractPlayableSprite player, TouchResponseResult result, int frameCounter) {
        if (broken || player == null) {
            return;
        }

        // Hit from below: player moving upward
        // ROM: Touch_Monitor - checks player y_pos - $10 >= monitor y_pos
        if (player.getYSpeed() < 0) {
            int playerCenterY = player.getCentreY();
            int monitorY = currentY;

            if (playerCenterY - 0x10 >= monitorY) {
                // Bounce player down: neg.w y_vel(a0)
                player.setYSpeed((short) -player.getYSpeed());

                // Make monitor pop up and fall
                if (!falling) {
                    falling = true;
                    yVel = FALLING_INITIAL_VEL;
                }
            }
            return;
        }

        // Hit from above: must be rolling (spinning)
        if (!player.getRolling()) {
            return;
        }

        breakMonitor(player);
    }

    /**
     * Break the monitor: spawn explosion, start icon rising, mark persistence.
     * ROM: Mon_BreakOpen
     */
    private void breakMonitor(AbstractPlayableSprite player) {
        broken = true;

        // Mark as broken in persistence table
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager != null) {
            objectManager.markRemembered(spawn);
        }

        // Bounce player: neg.w obVelY(a0)
        player.setYSpeed((short) -player.getYSpeed());

        mappingFrame = BROKEN_FRAME;

        // Initialize icon rising (PowerUp object)
        iconActive = true;
        iconSubY = spawn.y() << 8;
        iconVelY = ICON_INITIAL_VELOCITY;
        iconWaitFrames = 0;
        effectApplied = false;
        effectTarget = player;
        iconPlayer = player;

        // Spawn explosion (id_ExplosionItem = $27) - only if explosion art is loaded
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager != null && objectManager != null
                && renderManager.getExplosionRenderer() != null) {
            objectManager.addDynamicObject(
                    new ExplosionObjectInstance(0x27, spawn.x(), spawn.y(), renderManager));
        }
        AudioManager.getInstance().playSfx(Sonic1AudioProfile.SFX_BREAK_ITEM);
    }

    /**
     * Update the rising icon and apply power-up effect when it reaches the top.
     * ROM: Pow_Move, Pow_ChkX
     */
    private void updateIcon() {
        if (!iconActive) {
            return;
        }
        if (iconVelY < 0) {
            // Rising phase: apply velocity and deceleration
            iconSubY += iconVelY;
            iconVelY += ICON_RISE_ACCEL;
            if (iconVelY >= 0) {
                iconVelY = 0;
                iconWaitFrames = ICON_WAIT_FRAMES;
                if (!effectApplied && effectTarget != null) {
                    applyMonitorEffect(effectTarget);
                    effectApplied = true;
                    effectTarget = null;
                }
            }
            return;
        }

        // Waiting phase: count down then delete icon
        if (iconWaitFrames > 0) {
            iconWaitFrames--;
            return;
        }
        iconActive = false;
        iconPlayer = null;
    }

    /**
     * Apply the monitor's power-up effect.
     * ROM: Pow_ChkX branch table
     */
    private void applyMonitorEffect(AbstractPlayableSprite player) {
        switch (type) {
            // Pow_ChkRings: v_rings += 10, play sfx_Ring
            case RINGS -> {
                player.addRings(RING_MONITOR_REWARD);
                AudioManager.getInstance().playSfx(GameSound.RING);
            }
            // Pow_ChkShield: v_shield = 1, play sfx_Shield
            case SHIELD -> {
                player.giveShield();
                AudioManager.getInstance().playSfx(Sonic1AudioProfile.SFX_SHIELD);
            }
            // Pow_ChkShoes: speed shoes on, play bgm_Speedup (CMD_SPEED_UP = $E2)
            case SHOES -> {
                player.giveSpeedShoes();
                GameAudioProfile audioProfile = AudioManager.getInstance().getAudioProfile();
                if (audioProfile != null) {
                    AudioManager.getInstance().playMusic(audioProfile.getSpeedShoesOnCommandId());
                }
            }
            // Pow_ChkInvinc: invincibility on, play bgm_Invincible
            case INVINCIBILITY -> {
                player.giveInvincibility();
                AudioManager.getInstance().playMusic(Sonic1AudioProfile.MUS_INVINCIBILITY);
            }
            // Pow_ChkSonic: v_lives++, play bgm_ExtraLife
            case SONIC -> {
                AudioManager.getInstance().playMusic(Sonic1AudioProfile.MUS_EXTRA_LIFE);
                GameServices.gameState().addLife();
            }
            // Pow_ChkEggman, Pow_ChkS, Pow_ChkGoggles: no effect
            default -> { }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            appendFallbackBox(commands);
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getMonitorRenderer();
        if (renderer == null || !renderer.isReady()) {
            appendFallbackBox(commands);
            return;
        }

        // Draw monitor body (broken shell or animated frame)
        int frameIndex = broken ? BROKEN_FRAME : mappingFrame;
        renderer.drawFrameIndex(frameIndex, spawn.x(), currentY, false, false);

        // Draw rising icon
        if (iconActive) {
            int iconFrame = resolveIconFrame();
            ObjectSpriteSheet sheet = renderManager.getMonitorSheet();
            if (iconFrame >= 0 && sheet != null && iconFrame < sheet.getFrameCount()) {
                SpriteMappingFrame frame = sheet.getFrame(iconFrame);
                if (frame != null && !frame.pieces().isEmpty()) {
                    // Draw only the first piece (the icon overlay, not the box base)
                    SpriteMappingPiece iconPiece = frame.pieces().get(0);
                    renderer.drawPieces(List.of(iconPiece), spawn.x(), iconSubY >> 8, false, false);
                }
            }
        }
    }

    /**
     * Resolve the mapping frame index for the rising icon.
     * ROM: Pow_Main sets obFrame = obAnim + 2
     */
    private int resolveIconFrame() {
        if (type == MonitorType.STATIC || type == MonitorType.BROKEN) {
            return -1;
        }
        return type.id + ICON_FRAME_OFFSET;
    }

    /**
     * Debug fallback: render as a colored box when art is unavailable.
     */
    private void appendFallbackBox(List<GLCommand> commands) {
        int cx = spawn.x();
        int cy = currentY;
        int left = cx - HALF_RADIUS;
        int right = cx + HALF_RADIUS;
        int top = cy - HALF_RADIUS;
        int bottom = cy + HALF_RADIUS;
        float r = 0.4f, g = 0.9f, b = 1.0f;
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, left, top, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, right, top, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, right, top, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, right, bottom, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, right, bottom, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, left, bottom, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, left, bottom, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, left, top, 0, 0));
    }

    // -- Collision interfaces --

    // From disassembly: obColType = $46
    @Override
    public int getCollisionFlags() {
        return 0x46;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    // From disassembly: Mon_SolidSides params d1=$1A, d2=$0F, d3=$10
    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(0x1A, 0x0F, 0x10);
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        if (broken) {
            return false;
        }
        if (player == null) {
            return true;
        }
        // Monitors are not solid when player is rolling (allows breaking from above)
        return !player.getRolling();
    }

    @Override
    public boolean hasMonitorSolidity() {
        return true;
    }

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        // Solid contact used for standing/edge checks; no additional behavior needed.
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(3);
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public ObjectSpawn getSpawn() {
        // Return spawn with dynamic Y for solid collision checks during falling
        if (currentY != spawn.y()) {
            return new ObjectSpawn(
                    spawn.x(),
                    currentY,
                    spawn.objectId(),
                    spawn.subtype(),
                    spawn.renderFlags(),
                    spawn.respawnTracked(),
                    spawn.rawYWord());
        }
        return spawn;
    }

    /**
     * S1 Monitor subtypes (from obSubtype in disassembly).
     * Mapping: 0=static, 1=eggman, 2=sonic/1-up, 3=shoes, 4=shield,
     * 5=invincibility, 6=rings, 7=S, 8=goggles, 9=broken shell.
     */
    private enum MonitorType {
        STATIC(0),
        EGGMAN(1),
        SONIC(2),
        SHOES(3),
        SHIELD(4),
        INVINCIBILITY(5),
        RINGS(6),
        S_MONITOR(7),
        GOGGLES(8),
        BROKEN(9);

        private final int id;

        MonitorType(int id) {
            this.id = id;
        }

        static MonitorType fromSubtype(int subtype) {
            int value = subtype & 0xF;
            for (MonitorType t : values()) {
                if (t.id == value) {
                    return t;
                }
            }
            return STATIC;
        }
    }
}
