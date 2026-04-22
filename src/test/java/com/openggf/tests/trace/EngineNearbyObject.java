package com.openggf.tests.trace;

record EngineNearbyObject(
        int slot,
        int objectId,
        String name,
        int currentX,
        int currentY,
        int spawnX,
        int spawnY,
        boolean touchResponseProvider,
        int collisionFlags,
        int preUpdateCollisionFlags,
        int preUpdateX,
        int preUpdateY,
        boolean skipTouchThisFrame,
        boolean skipSolidThisFrame,
        boolean onScreenForTouch
) {
}
