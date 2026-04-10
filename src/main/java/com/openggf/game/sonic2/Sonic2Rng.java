package com.openggf.game.sonic2;

import com.openggf.game.GameRng;
import com.openggf.game.sonic2.credits.Sonic2CreditsData;

/**
 * Sonic 2 call-site helpers for ROM RNG bit reuse patterns.
 */
public final class Sonic2Rng {
    public static final int[] ENDING_CLOUD_Y_VELS = Sonic2CreditsData.CLOUD_Y_VELS;
    public static final int[] ENDING_CLOUD_FRAMES = Sonic2CreditsData.CLOUD_FRAMES;

    private Sonic2Rng() {
    }

    public record BubbleBurstChoice(int bubbleCount, int sequenceOffset) {
    }

    public record LeafSpawn(int offsetX, int offsetY, int mappingFrame, int angle) {
    }

    public record GunkDropletVelocity(int xVel, int yVel) {
    }

    public record PipeShardMotion(int xVel, int timer) {
    }

    public record EndingCloudSpawn(int x, int y, int xVel, int yVel, int frame) {
    }

    public record EndingBirdSpawn(int x, int y, int xVel, int yVel) {
    }

    public record BossExplosionOffset(int xOffset, int yOffset) {
    }

    public static BubbleBurstChoice nextBubbleBurstChoice(GameRng rng) {
        int raw;
        int bubbleCount;
        do {
            raw = rng.nextRaw();
            bubbleCount = raw & 7;
        } while (bubbleCount >= 6);
        return new BubbleBurstChoice(bubbleCount, raw & 0x0C);
    }

    public static int nextMaskedOffset(GameRng rng, int mask, int bias) {
        return (rng.nextRaw() & mask) + bias;
    }

    public static LeafSpawn nextLeafSpawn(GameRng rng) {
        int raw = rng.nextRaw();
        int offsetX = (raw & 0x0F) - 8;
        int offsetY = ((raw >>> 16) & 0x0F) - 8;
        int mappingFrame = offsetY & 1;
        int angle = (int) (rng.getSeed() & 0xFF);
        return new LeafSpawn(offsetX, offsetY, mappingFrame, angle);
    }

    public static int nextPipeShardXVelocity(GameRng rng) {
        return nextPipeShardMotion(rng).xVel();
    }

    public static PipeShardMotion nextPipeShardMotion(GameRng rng) {
        int raw = rng.nextRaw();
        int xVel = ((short) raw) >> 14;
        int timer = (((raw >>> 16) & 0xFFFF) + 0x1E) & 0x7F;
        return new PipeShardMotion(xVel, timer);
    }

    public static EndingCloudSpawn nextEndingCloudSpawn(GameRng rng, boolean horizontal) {
        int rotated = rotateSeedRight(rng, 1);
        int index = rotated & 3;
        int velocity = ENDING_CLOUD_Y_VELS[index];
        int frame = ENDING_CLOUD_FRAMES[index];
        if (horizontal) {
            return new EndingCloudSpawn(0x150, rotated & 0xFF, velocity, 0, frame);
        }
        return new EndingCloudSpawn(rotated & 0x1FF, 0x100, 0, velocity, frame);
    }

    public static EndingBirdSpawn nextEndingBirdSpawn(GameRng rng) {
        int rotated = rotateSeedRight(rng, 3);
        int yBits = Integer.rotateRight(rotated, 3) & 0xFF;
        int yVel = yBits < 0x20 ? 0x20 : -0x20;
        return new EndingBirdSpawn(-0xA0 + (rotated & 0x7F), 8 + yBits, 0x100, yVel);
    }

    public static int currentEndingSeedDelay(GameRng rng, int mask) {
        return (int) rng.getSeed() & mask;
    }

    public static int nextAnimalArtVariant(GameRng rng) {
        return rng.nextBits(1);
    }

    public static BossExplosionOffset nextBossExplosionOffset(GameRng rng) {
        int random = rng.nextWord();
        int xOffset = ((random & 0xFF) >>> 2) - 0x20;
        int yOffset = (((random >>> 8) & 0xFF) >>> 2) - 0x20;
        return new BossExplosionOffset(xOffset, yOffset);
    }

    public static int nextCpzGunkMainXVelocity(GameRng rng) {
        int xVel = ((short) rng.nextRaw()) >> 6;
        if (xVel >= 0) {
            xVel += 0x200;
        }
        return xVel - 0x100;
    }

    public static GunkDropletVelocity nextCpzGunkDropletVelocity(GameRng rng, int baseYVel) {
        int raw = rng.nextRaw();
        int xVel = ((short) raw) >> 6;
        if (xVel >= 0) {
            xVel += 0x80;
        }
        xVel -= 0x80;
        int yVel = baseYVel - ((raw >>> 16) & 0x3FF);
        return new GunkDropletVelocity(xVel, yVel);
    }

    public static int nextArzArrowOffsetIndex(GameRng rng) {
        return rng.nextRaw() & 3;
    }

    public static int nextMczDebrisX(GameRng rng) {
        int x;
        do {
            rng.nextRaw();
            x = 0x20F0 + (((int) rng.getSeed() >>> 16) & 0x1FF);
        } while (x > 0x2230);
        return x;
    }

    public static int nextWhispPauseTimer(GameRng rng) {
        rng.nextRaw();
        return (int) rng.getSeed() & 0x1F;
    }

    public static int nextTornadoSmokeOffset(GameRng rng) {
        rng.nextRaw();
        return (int) rng.getSeed() & 0x1C;
    }

    public static int nextEggPrisonAnimalXOffset(GameRng rng) {
        int raw = rng.nextRaw();
        int offset = (raw & 0x1F) - 6;
        if (((int) rng.getSeed() & 0x8000) != 0) {
            offset = -offset;
        }
        return offset;
    }

    private static int rotateSeedRight(GameRng rng, int amount) {
        int rotated = Integer.rotateRight((int) rng.getSeed(), amount);
        rng.setSeed(rotated & 0xFFFFFFFFL);
        return rotated;
    }
}
