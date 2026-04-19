package com.openggf.tests.trace;

record EngineNearbyObject(
        int slot,
        int objectId,
        String name,
        int x,
        int y,
        int collisionFlags,
        int preUpdateCollisionFlags,
        int preUpdateX,
        int preUpdateY,
        boolean skipTouchThisFrame,
        boolean skipSolidThisFrame,
        boolean onScreenForTouch
) {
}
