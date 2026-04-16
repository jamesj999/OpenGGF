package com.openggf.game.sonic3k.events;

import com.openggf.level.objects.ObjectServices;

/**
 * CNZ object helpers mirroring the AIZ write-support pattern.
 *
 * <p>Objects should not know about the concrete level-event manager type. They
 * resolve the active {@link CnzObjectEventBridge} through {@link ObjectServices}
 * and perform small, ROM-shaped writes such as boss scroll updates or water
 * target changes.
 */
public final class S3kCnzEventWriteSupport {
    private S3kCnzEventWriteSupport() {
    }

    /**
     * Publishes one pending arena-destruction request. Later CNZ slices can
     * widen this to a real queue if object sequencing needs more than one slot.
     */
    public static void setPendingArenaChunkDestruction(ObjectServices services,
                                                       int chunkWorldX,
                                                       int chunkWorldY) {
        CnzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) {
            bridge.setPendingArenaChunkDestruction(chunkWorldX, chunkWorldY);
        }
    }

    public static void setBossScrollState(ObjectServices services, int offsetY, int velocityY) {
        CnzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) {
            bridge.setBossScrollState(offsetY, velocityY);
        }
    }

    public static void setBossFlag(ObjectServices services, boolean value) {
        CnzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) {
            bridge.setBossFlag(value);
        }
    }

    public static void setEventsFg5(ObjectServices services, boolean value) {
        CnzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) {
            bridge.setEventsFg5(value);
        }
    }

    public static void setWallGrabSuppressed(ObjectServices services, boolean value) {
        CnzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) {
            bridge.setWallGrabSuppressed(value);
        }
    }

    public static void setWaterButtonArmed(ObjectServices services, boolean value) {
        CnzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) {
            bridge.setWaterButtonArmed(value);
        }
    }

    public static boolean isWaterButtonArmed(ObjectServices services) {
        CnzObjectEventBridge bridge = bridgeOrNull(services);
        return bridge != null && bridge.isWaterButtonArmed();
    }

    public static void setWaterTargetY(ObjectServices services, int targetY) {
        CnzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) {
            bridge.setWaterTargetY(targetY);
        }
    }

    public static void beginKnucklesTeleporterRoute(ObjectServices services) {
        CnzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) {
            bridge.beginKnucklesTeleporterRoute();
        }
    }

    public static void markTeleporterBeamSpawned(ObjectServices services) {
        CnzObjectEventBridge bridge = bridgeOrNull(services);
        if (bridge != null) {
            bridge.markTeleporterBeamSpawned();
        }
    }

    private static CnzObjectEventBridge bridgeOrNull(ObjectServices services) {
        Object provider = services.levelEventProvider();
        return provider instanceof CnzObjectEventBridge bridge ? bridge : null;
    }
}
