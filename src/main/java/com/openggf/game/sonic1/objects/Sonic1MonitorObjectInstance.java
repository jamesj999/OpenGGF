package com.openggf.game.sonic1.objects;

import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.GameSound;
import com.openggf.game.sonic1.audio.Sonic1Music;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.level.objects.ExplosionObjectInstance;
import com.openggf.level.objects.ObjectAnimationState;
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
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

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
public class Sonic1MonitorObjectInstance extends AbstractMonitorObjectInstance
        implements TouchResponseProvider, TouchResponseListener,
        SolidObjectProvider, SolidObjectListener {
    private static final Logger LOGGER = Logger.getLogger(Sonic1MonitorObjectInstance.class.getName());

    // From disassembly: obHeight/obWidth = $0E
    private static final int HALF_RADIUS = 0x0E;

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

    // Player reference preserved for icon rendering (effectTarget is cleared after apply)
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
        ObjectManager objectManager = services().objectManager();
        boolean previouslyBroken = objectManager != null && objectManager.isRemembered(spawn);
        this.broken = this.type == MonitorType.BROKEN || previouslyBroken;

        // Initialize animation: obAnim = obSubtype (from Mon_Main)
        int initialAnim = type.id;
        int initialFrame = broken ? BROKEN_FRAME : 0;
        ObjectRenderManager renderManager = services().renderManager();
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
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
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
    public void onTouchResponse(PlayableEntity playerEntity, TouchResponseResult result, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
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
        ObjectManager objectManager = services().objectManager();
        if (objectManager != null) {
            objectManager.markRemembered(spawn);
        }

        // Bounce player: neg.w obVelY(a0)
        player.setYSpeed((short) -player.getYSpeed());

        mappingFrame = BROKEN_FRAME;

        // Initialize icon rising (PowerUp object)
        startIconRise(currentY, player);
        iconPlayer = player;

        // Spawn explosion (id_ExplosionItem = $27) - only if explosion art is loaded
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager != null && objectManager != null
                && renderManager.getExplosionRenderer() != null) {
            objectManager.addDynamicObject(
                    new ExplosionObjectInstance(0x27, spawn.x(), currentY, renderManager));
        }
        services().playSfx(Sonic1Sfx.BREAK_ITEM.id);
    }

    @Override
    protected void onIconDeactivated() {
        iconPlayer = null;
    }

    /**
     * Apply the monitor's power-up effect.
     * ROM: Pow_ChkX branch table
     */
    @Override
    protected void applyPowerup(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        switch (type) {
            // Pow_ChkRings: v_rings += 10, play sfx_Ring
            case RINGS -> {
                player.addRings(RING_MONITOR_REWARD);
                services().playSfx(GameSound.RING);
            }
            // Pow_ChkShield: v_shield = 1, play sfx_Shield
            case SHIELD -> {
                player.giveShield();
                services().playSfx(Sonic1Sfx.SHIELD.id);
            }
            // Pow_ChkShoes: speed shoes on, play bgm_Speedup (CMD_SPEED_UP = $E2)
            case SHOES -> {
                player.giveSpeedShoes();
                GameAudioProfile audioProfile = services().audioManager().getAudioProfile();
                if (audioProfile != null) {
                    services().playMusic(audioProfile.getSpeedShoesOnCommandId());
                }
            }
            // Pow_ChkInvinc: invincibility on, play bgm_Invincible
            case INVINCIBILITY -> {
                player.giveInvincibility();
                services().playMusic(Sonic1Music.INVINCIBILITY.id);
            }
            // Pow_ChkSonic: v_lives++, play bgm_ExtraLife
            case SONIC -> {
                services().playMusic(Sonic1Music.EXTRA_LIFE.id);
                services().gameState().addLife();
            }
            // Pow_ChkEggman, Pow_ChkS, Pow_ChkGoggles: no effect
            default -> { }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
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
    public boolean isSolidFor(PlayableEntity playerEntity) {
        // ROM: Mon_Solid only calls Mon_SolidSides when ob2ndRout=0 (normal state).
        // When ob2ndRout=4 (falling after hit from below), Mon_Solid runs ObjectFall
        // + ObjFloorDist but does NOT call Mon_SolidSides — no solid collision.
        // When broken, the monitor is in routine 6/8 which never calls solid checks.
        return !broken && !falling;
    }

    @Override
    public boolean hasMonitorSolidity() {
        return true;
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
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
            return buildSpawnAt(spawn.x(), currentY);
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
