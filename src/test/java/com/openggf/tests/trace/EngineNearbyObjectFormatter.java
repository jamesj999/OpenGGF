package com.openggf.tests.trace;

import java.util.List;

final class EngineNearbyObjectFormatter {

    private EngineNearbyObjectFormatter() {
    }

    static String summarise(List<EngineNearbyObject> objects) {
        if (objects == null || objects.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (EngineNearbyObject object : objects) {
            if (shown > 0) {
                sb.append(" | ");
            }
            sb.append(String.format("eng-near s%d 0x%02X %s @%04X,%04X col=%02X",
                    object.slot(),
                    object.objectId() & 0xFF,
                    object.name(),
                    object.x() & 0xFFFF,
                    object.y() & 0xFFFF,
                    object.collisionFlags() & 0xFF));
            if (object.preUpdateCollisionFlags() >= 0
                    && object.preUpdateCollisionFlags() != object.collisionFlags()) {
                sb.append(String.format(" preCol=%02X", object.preUpdateCollisionFlags() & 0xFF));
            }
            if (object.preUpdateX() != object.x() || object.preUpdateY() != object.y()) {
                sb.append(String.format(" pre=@%04X,%04X",
                        object.preUpdateX() & 0xFFFF,
                        object.preUpdateY() & 0xFFFF));
            }
            String gateSummary = buildGateSummary(object);
            if (!gateSummary.isEmpty()) {
                sb.append(" gate=").append(gateSummary);
            }
            shown++;
            if (shown >= 6) {
                break;
            }
        }
        return sb.toString();
    }

    private static String buildGateSummary(EngineNearbyObject object) {
        StringBuilder sb = new StringBuilder();
        if (object.skipTouchThisFrame()) {
            sb.append("skipTouch");
        }
        if (object.skipSolidThisFrame()) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append("skipSolid");
        }
        if (!object.onScreenForTouch()) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append("offscreenTouch");
        }
        return sb.toString();
    }
}
