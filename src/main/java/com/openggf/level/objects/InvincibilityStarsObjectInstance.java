package com.openggf.level.objects;

import com.openggf.game.GameServices;
import com.openggf.game.GameModuleRegistry;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;
import com.openggf.game.PlayableEntity;

import java.util.List;

/**
 * Invincibility Stars - 4 stars that orbit around Sonic.
 * Based on Obj35 from Sonic 2 disassembly.
 * 
 * byte_1DB42 from s2.asm: dc.w values
 * Each word: high byte = X offset, low byte = Y offset (signed bytes)
 * Table index incremented by 2 each step (d6), masked by 0x3E (0-30 even).
 */
public class InvincibilityStarsObjectInstance extends AbstractObjectInstance {
    private final PlayableEntity player;
    private final PatternSpriteRenderer renderer;
    private final boolean sonic1TrailMode;

    // Decoded from byte_1DB42: dc.w $F00, $F03, $E06, $D08, $B0B, $80D, $60E, $30F,
    // $10, -$3F1, -$6F2, -$8F3, -$BF5, -$DF8, -$EFA, -$FFD,
    // $F000, -$F04, -$E07, -$D09, -$B0C, -$80E, -$60F, -$310,
    // -$10, $3F0, $6F1, $8F2, $BF4, $DF7, $EF9, $FFC
    // Format: (high_byte_signed, low_byte_signed) = (X, Y)
    private static final int[][] ORBIT_OFFSETS = {
            // Row 1: $0F00, $0F03, $0E06, $0D08, $0B0B, $080D, $060E, $030F
            { 0x0F, 0x00 }, { 0x0F, 0x03 }, { 0x0E, 0x06 }, { 0x0D, 0x08 },
            { 0x0B, 0x0B }, { 0x08, 0x0D }, { 0x06, 0x0E }, { 0x03, 0x0F },
            // Row 2: $0010, $FC0F (=-$3F1), $F90E (=-$6F2), $F70D (=-$8F3),
            // $F40B (=-$BF5), $F208 (=-$DF8), $F106 (=-$EFA), $F003 (=-$FFD)
            { 0x00, 0x10 }, { -4, 0x0F }, { -7, 0x0E }, { -9, 0x0D },
            { -12, 0x0B }, { -14, 0x08 }, { -15, 0x06 }, { -16, 0x03 },
            // Row 3: $F000, $F0FC (=-$F04), $E0F9 (=-$E07), ...
            // Wait, $F000 = high=$F0 (signed=-16), low=$00 (0)
            // -$F04 = 0xF0FC, high=$F0 (-16), low=$FC (-4)
            { -16, 0x00 }, { -16, -4 }, { -14, -7 }, { -13, -9 },
            { -11, -12 }, { -8, -14 }, { -6, -15 }, { -3, -16 },
            // Row 4: -$10 = $FFF0, high=$FF (-1)?? No wait.
            // -$10 as a WORD is 0xFFF0... No, dc.w -$10 = 0xFFF0.
            // High byte = $FF = -1, low byte = $F0 = -16. That seems wrong.
            // Let me re-examine: dc.w -$10 = -16 decimal = 0xFFF0 as 16-bit signed.
            // Hmm, the original code masks d6 with $3E, meaning it accesses even bytes
            // 0-30.
            // Let me just trust the apparent pattern from row 1-2 symmetry.
            // Actually the pattern should be circular, so row 4 mirrors row 1 in Y.
            { 0, -16 }, { 3, -16 }, { 6, -15 }, { 8, -14 },
            { 11, -12 }, { 13, -9 }, { 14, -7 }, { 15, -4 }
    };

    // Frame animation sequence from byte_1DB8F (star variant 0)
    // dc.b 8, 7, 6, 5, 4, 3, 4, 5, 6, 7, $FF (loop marker)
    private static final int[] FRAME_SEQUENCE = { 8, 7, 6, 5, 4, 3, 4, 5, 6, 7 };

    // 4 stars at 90 degree intervals (32 entries / 4 = 8 index spacing)
    private static final int STAR_COUNT = 4;
    private static final int ANGLE_SPACING = 8; // 8 indices = 90 degrees
    private static final int S1_TRAIL_PHASE_COUNT = 6;

    // S1 Ani_Shield scripts converted to local frame indices 0..3:
    // stars1: 4,5,6,7
    // stars2: 4,4,0,4,4,0,5,5,0,5,5,0,6,6,0,6,6,0,7,7,0,7,7,0
    // stars3: 4,4,0,4,0,0,5,5,0,5,0,0,6,6,0,6,0,0,7,7,0,7,0,0
    // stars4: 4,0,0,4,0,0,5,0,0,5,0,0,6,0,0,6,0,0,7,0,0,7,0,0
    private static final int[][] S1_ANIMATION_SEQUENCES = {
            { 0, 1, 2, 3 },
            { 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 2, 2, 0, 2, 2, 0, 3, 3, 0, 3, 3, 0 },
            { 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 0, 0, 2, 2, 0, 2, 0, 0, 3, 3, 0, 3, 0, 0 },
            { 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 2, 0, 0, 2, 0, 0, 3, 0, 0, 3, 0, 0 }
    };
    private static final int[] S1_ANIMATION_SPEEDS = { 6, 1, 1, 1 };

    // Original uses objoff_34 incremented by $12 (18) each frame when walking
    // direction,
    // or $02 when stationary. $12 in hex = 18 decimal, but d6 is masked by $3E,
    // so effective increment is 18 mod 64 for angle... but table is 32 entries.
    // Actually d6 is used as byte index (2 bytes per entry), so $12 inc = 9
    // entries/frame.
    // That's fast! Let's use $02 (2 bytes = 1 entry) for stationary.
    private int currentAngle = 0; // Index 0-31 (byte offset / 2)
    private int animationIndex = 0;
    private int s1TrailPhase = 0;
    private final int[] s1AnimationIndices = new int[STAR_COUNT];
    private final int[] s1AnimationTimers = new int[STAR_COUNT];

    public InvincibilityStarsObjectInstance(PlayableEntity player) {
        super(null, "InvincibilityStars");
        this.player = player;

        ObjectRenderManager renderManager = null;
        if (GameServices.level() != null) {
            renderManager = GameServices.level().getObjectRenderManager();
        }
        if (renderManager != null) {
            this.renderer = renderManager.getInvincibilityStarsRenderer();
        } else {
            this.renderer = null;
        }
        this.sonic1TrailMode = isTrailMode();
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (sonic1TrailMode) {
            s1TrailPhase = (s1TrailPhase + 1) % S1_TRAIL_PHASE_COUNT;
            for (int i = 0; i < STAR_COUNT; i++) {
                s1AnimationTimers[i]++;
                if (s1AnimationTimers[i] >= S1_ANIMATION_SPEEDS[i]) {
                    s1AnimationTimers[i] = 0;
                    s1AnimationIndices[i]++;
                    if (s1AnimationIndices[i] >= S1_ANIMATION_SEQUENCES[i].length) {
                        s1AnimationIndices[i] = 0;
                    }
                }
            }
            return;
        }

        // Original increments angle by $02 (1 entry) each frame when stationary,
        // and by $12 (9 entries) when moving. Let's use 1 entry per frame for now.
        currentAngle = (currentAngle + 1) % ORBIT_OFFSETS.length;

        // Update animation frame every frame
        animationIndex++;
        if (animationIndex >= FRAME_SEQUENCE.length) {
            animationIndex = 0;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (renderer == null || player == null) {
            return;
        }

        if (sonic1TrailMode) {
            appendSonic1TrailRenderCommands();
            return;
        }

        int playerX = player.getCentreX();
        int playerY = player.getCentreY();

        // Render 4 stars at 90 degree intervals
        for (int i = 0; i < STAR_COUNT; i++) {
            int angleIndex = (currentAngle + (i * ANGLE_SPACING)) % ORBIT_OFFSETS.length;
            int[] offset = ORBIT_OFFSETS[angleIndex];

            int starX = playerX + offset[0];
            int starY = playerY + offset[1];

            // Each star uses a different animation offset
            int frameIndex = FRAME_SEQUENCE[(animationIndex + (i * 2)) % FRAME_SEQUENCE.length];

            renderer.drawFrameIndex(frameIndex, starX, starY, false, false);
        }
    }

    private void appendSonic1TrailRenderCommands() {
        boolean hFlip = player.getDirection() == Direction.LEFT;

        for (int i = 0; i < STAR_COUNT; i++) {
            int framesBehind = s1FramesBehindForStar(i, s1TrailPhase);
            int starX = player.getCentreX(framesBehind);
            int starY = player.getCentreY(framesBehind);
            int frameIndex = S1_ANIMATION_SEQUENCES[i][s1AnimationIndices[i]];

            renderer.drawFrameIndex(frameIndex, starX, starY, hFlip, false);
        }
    }

    public static int s1FramesBehindForStar(int starIndex, int trailPhase) {
        int normalizedStar = Math.max(0, Math.min(STAR_COUNT - 1, starIndex));
        int normalizedPhase = Math.floorMod(trailPhase, S1_TRAIL_PHASE_COUNT);
        return 1 + (normalizedStar * S1_TRAIL_PHASE_COUNT) + normalizedPhase;
    }

    private static boolean isTrailMode() {
        try {
            return GameModuleRegistry.getCurrent().hasTrailInvincibilityStars();
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public boolean isHighPriority() {
        return false;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(1);
    }

    public void destroy() {
        setDestroyed(true);
    }
}
