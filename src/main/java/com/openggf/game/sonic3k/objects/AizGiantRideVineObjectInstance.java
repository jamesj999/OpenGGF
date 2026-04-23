package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * S3K Obj 0x0C - AIZ Giant Ride Vine.
 *
 * <p>Primary disassembly references:
 * Obj_AIZGiantRideVine (sonic3k.asm:46749-46963).
 */
public class AizGiantRideVineObjectInstance extends AbstractObjectInstance {
    private static final int ROOT_FRAME = 0x21;
    private static final int HANDLE_FRAME = 0x20;
    private static final int PRIORITY_BUCKET = 4; // priority $200
    private static final int SEGMENT_GAP = 0x10;
    private static final int ACTIVATED_SWING_STEP = 0x08;
    private static final int ACTIVATED_SWING_INITIAL_VELOCITY = -0x1B0;

    private static final class Segment {
        int x;
        int y;
        int angle;
        int value3A;
        int mappingFrame;
    }

    private final int currentX;
    private final int currentY;
    private final int segmentCount;
    private final int phaseOffset;

    private final Segment first;
    private final Segment[] chain;
    private final AizVineHandleLogic.State handle = new AizVineHandleLogic.State();
    private boolean activatedSwingStarted;
    private boolean activatedSwingReturning;
    private int activatedSwingAngle;
    private int activatedSwingVelocity;

    public AizGiantRideVineObjectInstance(ObjectSpawn spawn) {
        super(spawn, "AIZGiantRideVine");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        // ROM reuses the last allocated child as the handle (move.l #loc_2257E,(a1)),
        // so the number of actual vine segments before the handle is the low nibble.
        this.segmentCount = spawn.subtype() & 0x0F;
        this.phaseOffset = spawn.subtype() & 0xF0;

        if (segmentCount > 0) {
            this.first = new Segment();
            this.first.x = currentX;
            this.first.y = currentY;
            this.chain = new Segment[Math.max(0, segmentCount - 1)];
            for (int i = 0; i < chain.length; i++) {
                chain[i] = new Segment();
                chain[i].x = currentX;
                chain[i].y = currentY + ((i + 1) * SEGMENT_GAP);
            }
        } else {
            this.first = null;
            this.chain = new Segment[0];
        }

        int handleYOffset = (segmentCount + 1) * SEGMENT_GAP;
        handle.x = currentX;
        handle.y = currentY + handleYOffset;
        handle.prevX = handle.x;
        handle.prevY = handle.y;
        activatedSwingAngle = asSigned16(phaseOffset << 8);
        activatedSwingVelocity = ACTIVATED_SWING_INITIAL_VELOCITY;
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public boolean isPersistent() {
        return AizVineHandleLogic.anyGrabbed(handle);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        int gameplayFrameCounter = frameCounter;
        ObjectServices svc = tryServices();
        if (svc != null && svc.levelManager() != null) {
            // ROM: AIZ_vine_angle advances from ChangeRingFrame in the gameplay loop,
            // not from the object/VBlank counter that ObjectManager passes here.
            gameplayFrameCounter = svc.levelManager().getFrameCounter();
        }
        updateSegmentsFromGlobalAngle(gameplayFrameCounter);
        updateHandle(player);
        // Off-screen lifecycle is handled by the Placement system: non-persistent
        // objects are unloaded when the spawn leaves the window and respawned on
        // re-entry.  The ROM's Delete_Current_Sprite immediately clears the respawn
        // bit, but our setDestroyed() latches until the spawn leaves the window,
        // which can prevent respawn when the vine's cull range is narrower than
        // the Placement window.
    }

    @Override
    public void onUnload() {
        clearGrabbedPlayers();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.AIZ_RIDE_VINE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        renderer.drawFrameIndex(ROOT_FRAME, currentX, currentY, false, false);

        if (first != null) {
            renderer.drawFrameIndex(first.mappingFrame, first.x, first.y, false, false);
            for (Segment segment : chain) {
                renderer.drawFrameIndex(segment.mappingFrame, segment.x, segment.y, false, false);
            }
        }

        if (AizVineHandleLogic.shouldRender(handle)) {
            renderer.drawFrameIndex(HANDLE_FRAME, handle.x, handle.y, false, false);
        }
    }

    private void updateSegmentsFromGlobalAngle(int frameCounter) {
        if (first == null) {
            return;
        }

        if (activatedSwingStarted) {
            updateActivatedFirstSegment();
        } else {
            updatePassiveFirstSegment(frameCounter);
        }

        Segment parent = first;
        for (Segment segment : chain) {
            segment.value3A = parent.value3A;
            segment.angle = asSigned16(parent.angle + segment.value3A);
            segment.mappingFrame = ((angleByte(segment.angle) + 4) & 0xFF) >> 3;
            int[] offset = offsetFromAngle(parent.angle);
            segment.x = parent.x + offset[0];
            segment.y = parent.y + offset[1];
            parent = segment;
        }
    }

    private void updatePassiveFirstSegment(int frameCounter) {
        // loc_2248A default path: angle = sin(AIZ_vine_angle + subtypePhase) * $2C.
        int angleByte = (currentAizVineAngleByte(frameCounter) + phaseOffset) & 0xFF;
        int sin = TrigLookupTable.sinHex(angleByte);
        first.angle = asSigned16(sin * 0x2C);
        first.value3A = first.angle >> 3;
        first.mappingFrame = ((angleByte(first.angle) + 4) & 0xFF) >> 3;
        first.x = currentX;
        first.y = currentY;
    }

    private void updateActivatedFirstSegment() {
        int velocity = activatedSwingVelocity;
        if (!activatedSwingReturning) {
            velocity = asSigned16(velocity + ACTIVATED_SWING_STEP);
            activatedSwingVelocity = velocity;
            activatedSwingAngle = asSigned16(activatedSwingAngle + velocity);
            if ((byte) angleByte(activatedSwingAngle) >= 0) {
                activatedSwingReturning = true;
            }
        } else {
            velocity = asSigned16(velocity - ACTIVATED_SWING_STEP);
            activatedSwingVelocity = velocity;
            activatedSwingAngle = asSigned16(activatedSwingAngle + velocity);
            if ((byte) angleByte(activatedSwingAngle) < 0) {
                activatedSwingReturning = false;
            }
        }

        first.angle = activatedSwingAngle;
        first.value3A = first.angle >> 3;
        first.mappingFrame = ((angleByte(first.angle) + 4) & 0xFF) >> 3;
        first.x = currentX;
        first.y = currentY;
    }

    private void updateHandle(AbstractPlayableSprite player) {
        int parentX;
        int parentY;
        int parentAngle;
        if (first == null) {
            parentX = currentX;
            parentY = currentY;
            parentAngle = 0;
        } else if (chain.length == 0) {
            parentX = first.x;
            parentY = first.y;
            parentAngle = first.angle;
        } else {
            Segment parent = chain[chain.length - 1];
            parentX = parent.x;
            parentY = parent.y;
            parentAngle = parent.angle;
        }

        AizVineHandleLogic.positionFromParent(handle, parentX, parentY, parentAngle);
        var sidekicks = services().sidekicks();
        AbstractPlayableSprite sidekick = sidekicks.isEmpty() ? null : (AbstractPlayableSprite) sidekicks.getFirst();
        AizVineHandleLogic.updatePlayers(handle, services(), player, sidekick, parentAngle);
    }

    private void clearGrabbedPlayers() {
        AbstractPlayableSprite player = resolveMainPlayer();
        var sidekicks = services().sidekicks();
        AbstractPlayableSprite sidekick = sidekicks.isEmpty() ? null : (AbstractPlayableSprite) sidekicks.getFirst();
        clearControlFor(player, handle.p1.grabFlag != 0);
        clearControlFor(sidekick, handle.p2.grabFlag != 0);
        handle.p1.grabFlag = 0;
        handle.p2.grabFlag = 0;
    }

    private AbstractPlayableSprite resolveMainPlayer() {
        var sprite = services().spriteManager().getSprite(
                ActiveGameplayTeamResolver.resolveMainCharacterCode(config()));
        return sprite instanceof AbstractPlayableSprite playable ? playable : null;
    }

    private static void clearControlFor(AbstractPlayableSprite player, boolean wasGrabbed) {
        if (player == null || !wasGrabbed) {
            return;
        }
        AizVineHandleLogic.clearPlayerControl(player);
    }

    private static int currentAizVineAngleByte(int frameCounter) {
        // ROM: (AIZ_vine_angle).w is cleared on level init and incremented by $180 each frame.
        int word = (frameCounter * 0x180) & 0xFFFF;
        // move.b (AIZ_vine_angle).w,d0 reads the high byte.
        return (word >> 8) & 0xFF;
    }

    private static int asSigned16(int value) {
        return (short) value;
    }

    private static int angleByte(int angleWord) {
        return (angleWord >> 8) & 0xFF;
    }

    private static int[] offsetFromAngle(int angle) {
        int byteAngle = (angleByte(angle) + 4) & 0xF8;
        int sin = TrigLookupTable.sinHex(byteAngle);
        int cos = TrigLookupTable.cosHex(byteAngle);
        return new int[]{(-sin + 8) >> 4, (cos + 8) >> 4};
    }
}
