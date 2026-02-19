package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.physics.TrigLookupTable;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

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

    public AizGiantRideVineObjectInstance(ObjectSpawn spawn) {
        super(spawn, "AIZGiantRideVine");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
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
    public void update(int frameCounter, AbstractPlayableSprite player) {
        updateSegmentsFromGlobalAngle(frameCounter);
        updateHandle(player);

        // ROM cull path in loc_22442/loc_2245C.
        int coarse = (currentX & 0xFF80) - Camera.getInstance().getX();
        if (coarse < 0 || coarse > 0x280) {
            setDestroyed(true);
        }
    }

    @Override
    public void onUnload() {
        clearGrabbedPlayers();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
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

        // loc_2248A default path: angle = sin(AIZ_vine_angle + subtypePhase) * $2C.
        int angleByte = (currentAizVineAngleByte(frameCounter) + phaseOffset) & 0xFF;
        int sin = TrigLookupTable.sinHex(angleByte);
        first.angle = asSigned16(sin * 0x2C);
        first.value3A = first.angle >> 3;
        first.mappingFrame = ((angleByte(first.angle) + 4) & 0xFF) >> 3;
        first.x = currentX;
        first.y = currentY;

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
        AbstractPlayableSprite sidekick = SpriteManager.getInstance().getSidekick();
        AizVineHandleLogic.updatePlayers(handle, player, sidekick, parentAngle);
    }

    private void clearGrabbedPlayers() {
        AbstractPlayableSprite player = resolveMainPlayer();
        AbstractPlayableSprite sidekick = SpriteManager.getInstance().getSidekick();
        clearControlFor(player, handle.p1.grabFlag != 0);
        clearControlFor(sidekick, handle.p2.grabFlag != 0);
        handle.p1.grabFlag = 0;
        handle.p2.grabFlag = 0;
    }

    private AbstractPlayableSprite resolveMainPlayer() {
        var sprite = SpriteManager.getInstance().getSprite(
                uk.co.jamesj999.sonic.configuration.SonicConfigurationService.getInstance()
                        .getString(uk.co.jamesj999.sonic.configuration.SonicConfiguration.MAIN_CHARACTER_CODE));
        return sprite instanceof AbstractPlayableSprite playable ? playable : null;
    }

    private static void clearControlFor(AbstractPlayableSprite player, boolean wasGrabbed) {
        if (player == null || !wasGrabbed) {
            return;
        }
        player.setControlLocked(false);
        player.setObjectControlled(false);
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
