package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.audio.GameAudioProfile;
import uk.co.jamesj999.sonic.audio.GameSound;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.objects.ExplosionObjectInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.ObjectAnimationState;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic3k.audio.Sonic3kMusic;
import uk.co.jamesj999.sonic.game.sonic3k.audio.Sonic3kSfx;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.*;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;
import uk.co.jamesj999.sonic.sprites.playable.ShieldType;

import java.util.List;
import java.util.logging.Logger;

/**
 * Sonic 3&K Monitor (item box) object.
 * <p>
 * Object ID 0x01. Contains power-ups awarded when broken by the player.
 * S&K monitors do NOT fall when hit from below — they break directly if
 * the player is rolling, spinning, or (for Knuckles) gliding/sliding.
 * <p>
 * Subtypes: 0=Eggman, 1=1-Up, 2=Eggman, 3=Rings, 4=SpeedShoes,
 * 5=FireShield, 6=LightningShield, 7=BubbleShield, 8=Invincibility, 9=Super.
 * <p>
 * Reference: docs/skdisasm/sonic3k.asm lines 40442-40995
 */
public class Sonic3kMonitorObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, TouchResponseListener,
        SolidObjectProvider, SolidObjectListener {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kMonitorObjectInstance.class.getName());

    // From disassembly: solid params d1=$19, d2=$10, d3=$11
    private static final int SOLID_WIDTH = 0x19;
    private static final int SOLID_D2 = 0x10;
    private static final int SOLID_D3 = 0x11;

    // Icon rising physics (same as S1/S2: Pow_Move)
    private static final int ICON_INITIAL_VELOCITY = -0x300;
    private static final int ICON_RISE_ACCEL = 0x18;
    private static final int ICON_WAIT_FRAMES = 0x1D;

    // Map_Monitor frame 11 = broken shell
    private static final int BROKEN_FRAME = 0x0B;

    // Monitor gives 10 rings
    private static final int RING_MONITOR_REWARD = 10;

    // Super monitor gives 50 rings
    private static final int SUPER_RING_REWARD = 50;

    // Icon frame offset: icon mapping frame = type.animId + 2
    // (Mapping frames: 0=box, 1=eggman, 2=1up, 3=eggman2, 4=rings, ...)
    private static final int ICON_FRAME_OFFSET = 2;

    private final MonitorType type;
    private final ObjectAnimationState animationState;
    private boolean broken;
    private int mappingFrame;

    // Icon rising state (inline PowerUp object)
    private boolean iconActive;
    private int iconSubY;
    private int iconVelY;
    private int iconWaitFrames;
    private boolean effectApplied;
    private AbstractPlayableSprite effectTarget;

    public Sonic3kMonitorObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Monitor");
        this.type = MonitorType.fromSubtype(spawn.subtype());

        // Check persistence: if previously broken, spawn as broken shell
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        boolean previouslyBroken = objectManager != null && objectManager.isRemembered(spawn);
        this.broken = previouslyBroken;

        // Initialize animation: animId = subtype
        int initialAnim = type.animId;
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
    }

    @Override
    public boolean shouldStayActiveWhenRemembered() {
        return true;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!broken) {
            animationState.update();
            mappingFrame = animationState.getMappingFrame();
            return;
        }
        updateIcon();
    }

    /**
     * S&K touch response: monitors break directly when hit by a rolling/spinning player.
     * No falling behavior — unlike S2, hitting from below while rolling breaks immediately.
     * <p>
     * ROM: Touch_Monitor (sonic3k.asm ~line 20800)
     */
    @Override
    public void onTouchResponse(AbstractPlayableSprite player, TouchResponseResult result, int frameCounter) {
        if (broken || player == null) {
            return;
        }

        // S&K: check if player can break monitors
        // Must be rolling (spinning), spindashing, or Knuckles gliding/sliding
        boolean canBreak = player.getRolling() || player.getSpindash();
        // TODO: Add Knuckles glide/slide check when PlayerCharacter system is complete
        // canBreak |= (player.getCharacter() == PlayerCharacter.KNUCKLES
        //              && (player.getDoubleJumpFlag() == 1 || player.getDoubleJumpFlag() == 3));

        if (!canBreak) {
            return;
        }

        // Negate player's Y-speed: neg.w y_vel(a0)
        player.setYSpeed((short) -player.getYSpeed());

        breakMonitor(player);
    }

    /**
     * Break the monitor: spawn explosion, start icon rising, mark persistence.
     * ROM: Mon_BreakOpen (sonic3k.asm ~line 40685)
     */
    private void breakMonitor(AbstractPlayableSprite player) {
        broken = true;

        // Mark as broken in persistence table
        ObjectManager objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager != null) {
            objectManager.markRemembered(spawn);
        }

        mappingFrame = BROKEN_FRAME;

        // Initialize icon rising
        iconActive = true;
        iconSubY = spawn.y() << 8;
        iconVelY = ICON_INITIAL_VELOCITY;
        iconWaitFrames = 0;
        effectApplied = false;
        effectTarget = player;

        // Spawn explosion
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager != null && objectManager != null
                && renderManager.getExplosionRenderer() != null) {
            objectManager.addDynamicObject(
                    new ExplosionObjectInstance(0x27, spawn.x(), spawn.y(), renderManager));
        }
        AudioManager.getInstance().playSfx(Sonic3kSfx.EXPLODE.id);
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
            // Rising phase
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

        // Waiting phase
        if (iconWaitFrames > 0) {
            iconWaitFrames--;
            return;
        }
        iconActive = false;
    }

    /**
     * Apply the monitor's power-up effect.
     * ROM: Pow_ChkX branch table (sonic3k.asm ~line 40780)
     */
    private void applyMonitorEffect(AbstractPlayableSprite player) {
        switch (type) {
            case EGGMAN, EGGMAN_2 -> {
                // Eggman monitor hurts the player
                // ROM: calls HurtCharacter — checks invincibility first
                if (player.getInvincibleFrames() <= 0 && player.getInvulnerableFrames() <= 0) {
                    player.applyHurtOrDeath(player.getCentreX(), false, player.getRingCount() > 0);
                }
            }
            case ONE_UP -> {
                GameServices.gameState().addLife();
                AudioManager.getInstance().playMusic(Sonic3kMusic.EXTRA_LIFE.id);
            }
            case RINGS -> {
                player.addRings(RING_MONITOR_REWARD);
                AudioManager.getInstance().playSfx(GameSound.RING);
            }
            case SPEED_SHOES -> {
                player.giveSpeedShoes();
                GameAudioProfile audioProfile = AudioManager.getInstance().getAudioProfile();
                if (audioProfile != null) {
                    AudioManager.getInstance().playMusic(audioProfile.getSpeedShoesOnCommandId());
                }
            }
            case FIRE_SHIELD -> {
                player.giveShield(ShieldType.FIRE);
                AudioManager.getInstance().playSfx(Sonic3kSfx.FIRE_SHIELD.id);
            }
            case LIGHTNING_SHIELD -> {
                player.giveShield(ShieldType.LIGHTNING);
                AudioManager.getInstance().playSfx(Sonic3kSfx.LIGHTNING_SHIELD.id);
            }
            case BUBBLE_SHIELD -> {
                player.giveShield(ShieldType.BUBBLE);
                AudioManager.getInstance().playSfx(Sonic3kSfx.BUBBLE_SHIELD.id);
            }
            case INVINCIBILITY -> {
                // Skip invincibility if player is already Super Sonic
                if (!player.isSuperSonic()) {
                    player.giveInvincibility();
                    AudioManager.getInstance().playMusic(Sonic3kMusic.INVINCIBILITY.id);
                }
            }
            case SUPER -> {
                // Award 50 rings; super transformation system not yet implemented
                player.addRings(SUPER_RING_REWARD);
                LOGGER.info("Super monitor collected — 50 rings awarded (super transformation TODO)");
            }
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
        renderer.drawFrameIndex(frameIndex, spawn.x(), spawn.y(), false, false);

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
        return type.animId + ICON_FRAME_OFFSET;
    }

    private void appendFallbackBox(List<GLCommand> commands) {
        int cx = spawn.x();
        int cy = spawn.y();
        int half = 0x0E;
        int left = cx - half;
        int right = cx + half;
        int top = cy - half;
        int bottom = cy + half;
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

    // From disassembly: SolidObject_Monitor_SetValues params d1=$19, d2=$10, d3=$11
    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(SOLID_WIDTH, SOLID_D2, SOLID_D3);
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
        // Solid contact for standing/edge checks; no additional behavior needed.
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(3);
    }

    /**
     * S3K Monitor subtypes.
     * Mapping: 0=Eggman, 1=1-Up, 2=Eggman, 3=Rings, 4=SpeedShoes,
     * 5=FireShield, 6=LightningShield, 7=BubbleShield, 8=Invincibility, 9=Super.
     */
    private enum MonitorType {
        EGGMAN(0),
        ONE_UP(1),
        EGGMAN_2(2),
        RINGS(3),
        SPEED_SHOES(4),
        FIRE_SHIELD(5),
        LIGHTNING_SHIELD(6),
        BUBBLE_SHIELD(7),
        INVINCIBILITY(8),
        SUPER(9);

        /** Animation ID (also used for subtype matching). */
        private final int animId;

        MonitorType(int animId) {
            this.animId = animId;
        }

        static MonitorType fromSubtype(int subtype) {
            int value = subtype & 0xF;
            for (MonitorType t : values()) {
                if (t.animId == value) {
                    return t;
                }
            }
            return EGGMAN;
        }
    }
}
