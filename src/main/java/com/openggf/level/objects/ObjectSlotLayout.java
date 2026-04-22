package com.openggf.level.objects;

/**
 * Game-specific dynamic object slot layout for the shared {@link ObjectManager}.
 *
 * <p>Only the allocatable dynamic slot window is modeled here. Fixed player/UI/support
 * slots that live outside the manager remain owned by their respective systems.
 */
public record ObjectSlotLayout(int firstDynamicSlot, int dynamicSlotCount) {
    public static final ObjectSlotLayout SONIC_1 = new ObjectSlotLayout(32, 96);
    public static final ObjectSlotLayout SONIC_2 = new ObjectSlotLayout(16, 112);
    public static final ObjectSlotLayout SONIC_3K = new ObjectSlotLayout(3, 90);

    public ObjectSlotLayout {
        if (firstDynamicSlot < 0) {
            throw new IllegalArgumentException("firstDynamicSlot must be >= 0");
        }
        if (dynamicSlotCount < 0) {
            throw new IllegalArgumentException("dynamicSlotCount must be >= 0");
        }
    }

    public int lastDynamicSlotExclusive() {
        return firstDynamicSlot + dynamicSlotCount;
    }

    public boolean isDynamicSlot(int slotIndex) {
        return slotIndex >= firstDynamicSlot && slotIndex < lastDynamicSlotExclusive();
    }

    public int toExecIndex(int slotIndex) {
        return slotIndex - firstDynamicSlot;
    }

    public int toSlotIndex(int execIndex) {
        return firstDynamicSlot + execIndex;
    }
}
