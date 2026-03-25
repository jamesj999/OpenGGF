package com.openggf.level.objects;

/**
 * Cross-game animal type enum.
 * Physics and mapping-set data are game-agnostic; ROM art addresses
 * are resolved by each game's art provider.
 */
public enum AnimalType {
    RABBIT(-0x200, -0x400, MappingSet.E, false, "Rabbit"),
    CHICKEN(-0x200, -0x300, MappingSet.A, true, "Chicken"),
    PENGUIN(-0x180, -0x300, MappingSet.E, false, "Penguin"),
    SEAL(-0x140, -0x180, MappingSet.D, false, "Seal"),
    PIG(-0x1C0, -0x300, MappingSet.C, false, "Pig"),
    FLICKY(-0x300, -0x400, MappingSet.A, true, "Flicky"),
    SQUIRREL(-0x280, -0x380, MappingSet.B, false, "Squirrel"),
    EAGLE(-0x280, -0x300, MappingSet.A, true, "Eagle"),
    MOUSE(-0x200, -0x380, MappingSet.B, false, "Mouse"),
    MONKEY(-0x2C0, -0x300, MappingSet.B, false, "Monkey"),
    TURTLE(-0x140, -0x200, MappingSet.B, false, "Turtle"),
    BEAR(-0x200, -0x300, MappingSet.B, false, "Bear");

    public enum MappingSet {
        A,
        B,
        C,
        D,
        E
    }

    private final int xVel;
    private final int yVel;
    private final MappingSet mappingSet;
    private final boolean flying;
    private final String displayName;

    AnimalType(int xVel, int yVel, MappingSet mappingSet, boolean flying, String displayName) {
        this.xVel = xVel;
        this.yVel = yVel;
        this.mappingSet = mappingSet;
        this.flying = flying;
        this.displayName = displayName;
    }

    public int xVel() {
        return xVel;
    }

    public int yVel() {
        return yVel;
    }

    public MappingSet mappingSet() {
        return mappingSet;
    }

    public boolean flying() {
        return flying;
    }

    public String displayName() {
        return displayName;
    }

    public static AnimalType fromIndex(int index) {
        AnimalType[] values = values();
        if (index < 0 || index >= values.length) {
            return RABBIT;
        }
        return values[index];
    }
}
