package com.openggf.game.sonic3k.objects;

import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.GameSound;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ExplosionObjectInstance;
import com.openggf.level.objects.ObjectAnimationState;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractMonitorObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.ShieldType;

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
public class Sonic3kMonitorObjectInstance extends AbstractMonitorObjectInstance
        implements TouchResponseProvider, TouchResponseListener,
        SolidObjectProvider, SolidObjectListener {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kMonitorObjectInstance.class.getName());

    // From disassembly: solid params d1=$19, d2=$10, d3=$11
    private static final int SOLID_WIDTH = 0x19;
    private static final int SOLID_D2 = 0x10;
    private static final int SOLID_D3 = 0x11;

    // Map_Monitor frame 11 = broken shell
    private static final int BROKEN_FRAME = 0x0B;

    // Monitor gives 10 rings
    private static final int RING_MONITOR_REWARD = 10;

    // Super monitor gives 50 rings
    private static final int SUPER_RING_REWARD = 50;

    // Icon frame offset: icon mapping frame = type.animId + 1
    // ROM: addq.b #1,d0 (sonic3k.asm line 40699)
    // (Mapping frames: 0=box, 1=eggman, 2=1up, 3=eggman2, 4=rings, ...)
    private static final int ICON_FRAME_OFFSET = 1;

    // Y radius for floor collision (from solid params d2)
    private static final int Y_RADIUS = 0x10;

    private final MonitorType type;
    private final ObjectAnimationState animationState;
    private boolean broken;
    private int mappingFrame;

    // "Revealed from hidden monitor" mode: pop up with velocity, fall with gravity
    private boolean revealed;
    private final SubpixelMotion.State motion;

    // (Icon rising state is managed by AbstractMonitorObjectInstance)

    public Sonic3kMonitorObjectInstance(ObjectSpawn spawn) {
        super(spawn, "Monitor");
        this.type = MonitorType.fromSubtype(spawn.subtype());
        this.motion = new SubpixelMotion.State(spawn.x(), spawn.y(), 0, 0, 0, 0);

        // Check persistence: if previously broken, spawn as broken shell
        ObjectManager objectManager = GameServices.level().getObjectManager();
        boolean previouslyBroken = objectManager != null && objectManager.isRemembered(spawn);
        this.broken = previouslyBroken;

        // Initialize animation: animId = subtype
        int initialAnim = type.animId;
        int initialFrame = broken ? BROKEN_FRAME : 0;
        ObjectRenderManager renderManager = GameServices.level().getObjectRenderManager();
        this.animationState = new ObjectAnimationState(
                renderManager != null ? renderManager.getMonitorAnimations() : null,
                initialAnim,
                initialFrame);
        this.mappingFrame = initialFrame;
        if (broken) {
            effectApplied = true;
        }
    }

    /**
     * Activate "revealed from hidden monitor" mode.
     * ROM: loc_83760 — sets y_vel = -$500, transforms to Obj_Monitor routine 2.
     * The monitor pops upward and falls with standard gravity until landing.
     */
    public void revealFromHidden() {
        revealed = true;
        motion.yVel = -0x500;
    }

    @Override
    public int getX() {
        return motion.x;
    }

    @Override
    public int getY() {
        return motion.y;
    }

    /** Current X position (uses motion state, which tracks spawn for static monitors). */
    private int posX() {
        return motion.x;
    }

    /** Current Y position (uses motion state, which tracks spawn for static monitors). */
    private int posY() {
        return motion.y;
    }

    @Override
    public boolean isPersistent() {
        return revealed;
    }

    @Override
    public boolean shouldStayActiveWhenRemembered() {
        return true;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (revealed && !broken) {
            updateRevealed();
        }
        if (!broken) {
            animationState.update();
            mappingFrame = animationState.getMappingFrame();
            return;
        }
        updateIcon();
    }

    /**
     * Physics for a monitor popping out of a hidden monitor slot.
     * ROM: Obj_MonitorNorm — SpeedToPos + gravity + ObjCheckFloorDist.
     */
    private void updateRevealed() {
        SubpixelMotion.moveSprite(motion, SubpixelMotion.S3K_GRAVITY);

        // Only check floor when moving downward
        if (motion.yVel > 0) {
            TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(
                    motion.x, motion.y, Y_RADIUS);
            if (floor.distance() < 0) {
                motion.y += floor.distance();
                motion.yVel = 0;
                revealed = false; // Landed — become a normal static monitor
                LOGGER.fine("Revealed monitor landed at Y=" + motion.y);
            }
        }
    }

    /**
     * S&K touch response: monitors break directly when hit by a rolling/spinning player.
     * No falling behavior — unlike S2, hitting from below while rolling breaks immediately.
     * <p>
     * ROM: Touch_Monitor (sonic3k.asm ~line 20800)
     */
    @Override
    public void onTouchResponse(PlayableEntity playerEntity, TouchResponseResult result, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (broken || player == null) {
            return;
        }

        // ROM: CPU sidekick cannot break monitors (s2.asm Touch_Monitor check)
        if (player.isCpuControlled()) {
            return;
        }

        // S&K: check if player can break monitors
        // Must be rolling (spinning), spindashing, or Knuckles gliding/sliding
        boolean canBreak = player.getRolling() || player.getSpindash();
        // Knuckles glide/slide check requires PlayerCharacter system (not yet implemented)
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
        ObjectManager objectManager = services().objectManager();
        if (objectManager != null) {
            objectManager.markRemembered(spawn);
        }

        mappingFrame = BROKEN_FRAME;

        // Initialize icon rising
        startIconRise(posY(), player);

        // Spawn explosion
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager != null && objectManager != null
                && renderManager.getExplosionRenderer() != null) {
            objectManager.addDynamicObject(
                    new ExplosionObjectInstance(0x27, posX(), posY(), renderManager));
        }
        // ROM: Obj_Explosion loc_1E61A plays sfx_Break ($3D)
        services().playSfx(Sonic3kSfx.BREAK.id);
    }

    /**
     * Apply the monitor's power-up effect.
     * ROM: Pow_ChkX branch table (sonic3k.asm ~line 40780)
     */
    @Override
    protected void applyPowerup(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
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
                services().playMusic(Sonic3kMusic.EXTRA_LIFE.id);
            }
            case RINGS -> {
                player.addRings(RING_MONITOR_REWARD);
                GameServices.audio().playSfx(GameSound.RING);
            }
            case SPEED_SHOES -> {
                player.giveSpeedShoes();
                GameAudioProfile audioProfile = GameServices.audio().getAudioProfile();
                if (audioProfile != null) {
                    services().playMusic(audioProfile.getSpeedShoesOnCommandId());
                }
            }
            case FIRE_SHIELD -> {
                player.giveShield(ShieldType.FIRE);
                GameServices.audio().playSfx(GameSound.FIRE_SHIELD);
            }
            case LIGHTNING_SHIELD -> {
                player.giveShield(ShieldType.LIGHTNING);
                GameServices.audio().playSfx(GameSound.LIGHTNING_SHIELD);
            }
            case BUBBLE_SHIELD -> {
                player.giveShield(ShieldType.BUBBLE);
                GameServices.audio().playSfx(GameSound.BUBBLE_SHIELD);
            }
            case INVINCIBILITY -> {
                // Skip invincibility if player is already Super Sonic
                if (!player.isSuperSonic()) {
                    player.giveInvincibility();
                    services().playMusic(Sonic3kMusic.INVINCIBILITY.id);
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
        ObjectRenderManager renderManager = services().renderManager();
        PatternSpriteRenderer renderer = renderManager != null ? renderManager.getMonitorRenderer() : null;
        boolean hasRenderer = renderer != null && renderer.isReady();

        if (hasRenderer) {
            // Draw monitor body (broken shell or animated frame)
            int frameIndex = broken ? BROKEN_FRAME : mappingFrame;
            renderer.drawFrameIndex(frameIndex, posX(), posY(), false, false);
        } else {
            // Fallback: full box when intact, half-height shell when broken
            appendFallbackBox(commands, broken);
        }

        // Draw rising icon
        if (iconActive) {
            if (hasRenderer) {
                int iconFrame = resolveIconFrame();
                ObjectSpriteSheet sheet = renderManager.getMonitorSheet();
                if (iconFrame >= 0 && sheet != null && iconFrame < sheet.getFrameCount()) {
                    SpriteMappingFrame frame = sheet.getFrame(iconFrame);
                    if (frame != null && !frame.pieces().isEmpty()) {
                        // Draw only the first piece (the icon overlay, not the box base)
                        SpriteMappingPiece iconPiece = frame.pieces().get(0);
                        renderer.drawPieces(List.of(iconPiece), posX(), iconSubY >> 8, false, false);
                    }
                }
            } else {
                // Fallback: small box for rising icon
                appendFallbackIcon(commands, posX(), iconSubY >> 8);
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

    private void appendFallbackBox(List<GLCommand> commands, boolean isBroken) {
        int cx = posX();
        int cy = posY();
        int half = 0x0E;
        int left = cx - half;
        int right = cx + half;
        // Broken shell: bottom half only (y to y+half)
        int top = isBroken ? cy : cy - half;
        int bottom = cy + half;
        float r = isBroken ? 0.6f : 0.4f;
        float g = isBroken ? 0.6f : 0.9f;
        float b = isBroken ? 0.6f : 1.0f;
        appendWireRect(commands, left, top, right, bottom, r, g, b);
    }

    private void appendFallbackIcon(List<GLCommand> commands, int cx, int cy) {
        int half = 6;
        appendWireRect(commands, cx - half, cy - half, cx + half, cy + half,
                1.0f, 1.0f, 0.4f);
    }

    private void appendWireRect(List<GLCommand> commands,
            int left, int top, int right, int bottom,
            float r, float g, float b) {
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

    // From disassembly: obColType = $46; cleared to 0 when broken (line 40642)
    @Override
    public int getCollisionFlags() {
        return broken ? 0 : 0x46;
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
    public boolean isSolidFor(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
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
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
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
