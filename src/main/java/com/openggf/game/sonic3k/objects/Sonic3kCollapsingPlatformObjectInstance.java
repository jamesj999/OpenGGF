package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractFallingFragment;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x04 - Collapsing Platform (Sonic 3 &amp; Knuckles).
 * <p>
 * A sloped platform that crumbles when Sonic stands on it for 7 frames.
 * Used in AIZ (Angel Island Zone) and ICZ (IceCap Zone).
 * When the timer expires, the platform splits into individual fragment objects
 * that fall with gravity in a staggered order based on a per-zone delay table.
 * <p>
 * Subtype encoding: subtype = initial mapping_frame (selects platform variant).
 * Fragment frame = mapping_frame + 2.
 * <p>
 * Uses sloped collision ({@code SolidObjectTopSloped2} in disassembly) with a
 * per-pixel height map. Uses level art (no dedicated compressed art).
 * <p>
 * ROM references: Obj_CollapsingPlatform (sonic3k.asm), loc_20594, loc_205CE.
 */
public class Sonic3kCollapsingPlatformObjectInstance extends AbstractObjectInstance
        implements SlopedSolidProvider, SolidObjectListener {

    private static final Logger LOG = Logger.getLogger(Sonic3kCollapsingPlatformObjectInstance.class.getName());

    // Initial collapse delay: 7 frames (ROM: move.b #7,$38(a0))
    private static final int INITIAL_COLLAPSE_DELAY = 7;

    // Gravity constant from MoveSprite: addi.w #$38,y_vel(a0)
    private static final int GRAVITY = 0x38;

    // Priority: $280 = bucket 5 (ROM: move.w #$280,priority(a0))
    private static final int PRIORITY = 5;

    // ===== Zone-specific configuration =====

    /**
     * AIZ collapse delay table (byte_20CB6, 30 entries).
     * Used for both AIZ Act 1 and Act 2. Fragment count may be less than 30;
     * only entries up to the fragment piece count are used.
     */
    private static final int[] AIZ_COLLAPSE_DELAYS = {
            0x30, 0x2C, 0x28, 0x24, 0x20, 0x1C, 0x2E, 0x2A, 0x26, 0x22, 0x1E, 0x1A,
            0x2C, 0x28, 0x24, 0x20, 0x1C, 0x18, 0x2A, 0x26, 0x22, 0x1E, 0x1A, 0x16,
            0x28, 0x24, 0x20, 0x1C, 0x18, 0x14
    };

    /**
     * ICZ collapse delay table (byte_20CD4, 32 entries).
     */
    private static final int[] ICZ_COLLAPSE_DELAYS = {
            0x30, 0x2C, 0x28, 0x24, 0x20, 0x1C, 0x2E, 0x2A, 0x26, 0x22, 0x1E, 0x1A,
            0x2C, 0x28, 0x24, 0x20, 0x1C, 0x18, 0x2A, 0x26, 0x22, 0x1E, 0x1A, 0x16,
            0x28, 0x24, 0x20, 0x1C, 0x18, 0x14, 0x12, 0x10
    };

    /**
     * AIZ slope height map (byte_20E9E, 64 bytes).
     * Gentle slope from 0x1F (left) to 0x0E (right).
     */
    private static final byte[] AIZ_SLOPE_DATA = {
            0x1F, 0x1F, 0x1F, 0x1F, 0x1F, 0x1E, 0x1E, 0x1E,
            0x1E, 0x1D, 0x1D, 0x1D, 0x1D, 0x1C, 0x1C, 0x1C,
            0x1C, 0x1B, 0x1B, 0x1B, 0x1B, 0x1A, 0x1A, 0x1A,
            0x1A, 0x19, 0x19, 0x19, 0x19, 0x18, 0x18, 0x18,
            0x18, 0x17, 0x17, 0x17, 0x17, 0x16, 0x16, 0x16,
            0x16, 0x15, 0x15, 0x15, 0x15, 0x14, 0x14, 0x14,
            0x14, 0x13, 0x13, 0x13, 0x13, 0x12, 0x12, 0x12,
            0x12, 0x11, 0x11, 0x10, 0x10, 0x0F, 0x0F, 0x0E
    };

    /**
     * ICZ slope height map (byte_20EDE, 48 bytes).
     * Mostly flat at 0x30 with slight drop on right edge.
     */
    private static final byte[] ICZ_SLOPE_DATA = {
            0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
            0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
            0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
            0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
            0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30,
            0x30, 0x2F, 0x2E, 0x2D, 0x2C, 0x2B, 0x2A, 0x29
    };

    /** Per-zone configuration record. */
    private record ZoneConfig(
            String artKey,
            int halfWidth,
            int halfHeight,
            int[] collapseDelays,
            byte[] slopeData,
            int fragmentFrameOffset
    ) {}

    private static final ZoneConfig AIZ1_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.COLLAPSING_PLATFORM_AIZ1,
            0x3C, 0x20, AIZ_COLLAPSE_DELAYS, AIZ_SLOPE_DATA, 2);

    private static final ZoneConfig AIZ2_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.COLLAPSING_PLATFORM_AIZ2,
            0x3C, 0x20, AIZ_COLLAPSE_DELAYS, AIZ_SLOPE_DATA, 2);

    private static final ZoneConfig ICZ_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.COLLAPSING_PLATFORM_ICZ,
            0x30, 0x30, ICZ_COLLAPSE_DELAYS, ICZ_SLOPE_DATA, 2);

    // ===== Instance state =====

    private final ZoneConfig config;
    private final int mappingFrame;  // subtype selects intact variant
    private final boolean hFlip;

    private int x;
    private int y;

    // State machine: 0=normal, 1=collapsing(timer), 2=solid-stay(invisible but solid),
    //                3=falling(gravity, offscreen destroy)
    // ROM flow: loc_20594 (states 0-1) -> loc_205DE (state 2) -> loc_20620 (state 3)
    private int state;
    private int collapseTimer;
    // Note: triggered flag is set alongside state=1 in onSolidContact() - kept for clarity
    private boolean triggered;  // player has stood on platform ($3A flag)
    private boolean fragmented;

    // Post-fragment solid-stay timer: ROM reuses $38 with first delay table entry.
    // The parent remains solid and invisible for this many frames after fragments spawn,
    // then releases the player. (ROM: loc_205DE countdown -> sub_205FC release)
    private int solidStayTimer;

    // Post-fragment parent fall state
    private int velY;
    private int yFrac;

    public Sonic3kCollapsingPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "CollapsingPlatform");
        this.x = spawn.x();
        this.y = spawn.y();
        this.mappingFrame = spawn.subtype() & 0xFF;
        this.hFlip = (spawn.renderFlags() & 0x01) != 0;
        this.collapseTimer = INITIAL_COLLAPSE_DELAY;
        this.config = resolveConfig();
    }

    // ===== SlopedSolidProvider =====

    @Override
    public byte[] getSlopeData() {
        return config.slopeData;
    }

    @Override
    public boolean isSlopeFlipped() {
        return hFlip;
    }

    @Override
    public int getSlopeBaseline() {
        return 0; // ROM uses absolute slope values
    }

    // ===== SolidObjectProvider =====

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(config.halfWidth, 0, 0);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean isSolidFor(AbstractPlayableSprite player) {
        // Solid during normal, collapsing, AND solid-stay states.
        // ROM: loc_205DE still calls SolidObjectTopSloped2 after fragments spawn.
        return state < 3;
    }

    @Override
    public boolean shouldStayActiveWhenRemembered() {
        // Platform must remain in the active set during the solid-stay phase so
        // SolidContacts can keep repositioning the player on the invisible parent.
        return state < 3;
    }

    // ===== SolidObjectListener =====

    @Override
    public void onSolidContact(AbstractPlayableSprite player, SolidContact contact, int frameCounter) {
        if (contact.standing() && state == 0) {
            // Player stepped on: set trigger flag (ROM: $3A)
            triggered = true;
            state = 1; // begin collapse countdown
        }
    }

    // ===== Update =====

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        switch (state) {
            case 0 -> {
                // Normal: just check if triggered via onSolidContact
                if (triggered) {
                    state = 1;
                }
            }
            case 1 -> {
                // Collapsing: countdown timer
                if (collapseTimer <= 0) {
                    performCollapse();
                } else {
                    collapseTimer--;
                }
            }
            case 2 -> {
                // Solid-stay: parent is invisible but still solid (ROM: loc_205DE).
                // Player continues standing on the invisible platform while fragments
                // fall away beneath them. Timer counts down from collapseDelays[0].
                solidStayTimer--;
                if (solidStayTimer <= 0) {
                    // Timer expired: release the player and start falling.
                    // ROM: sub_205FC clears Status_OnObj, Status_Push, sets Status_InAir.
                    state = 3;
                    if (player != null) {
                        player.setAir(true);
                        player.setOnObject(false);
                    }
                }
            }
            case 3 -> {
                // Post-fragment: parent falls with gravity, destroy when offscreen
                velY += GRAVITY;
                int y32 = (y << 16) | (yFrac & 0xFFFF);
                y32 += ((int) (short) velY) << 8;
                y = y32 >> 16;
                yFrac = y32 & 0xFFFF;

                if (!isOnScreen(128)) {
                    setDestroyed(true);
                }
            }
        }
    }

    /**
     * Fragments the platform: spawns individual fragment children and plays SFX.
     * ROM: ObjPlatformCollapse_CreateFragments
     */
    private void performCollapse() {
        if (fragmented) {
            return;
        }
        fragmented = true;

        // Enter solid-stay state: parent remains invisible but solid while fragments fall.
        // ROM: ObjPlatformCollapse_SmashObject writes collapseDelays[0] into the parent's $38,
        // and loc_205DE counts it down while still calling SolidObjectTopSloped2.
        state = 2;
        solidStayTimer = config.collapseDelays[0];

        // Play collapse SFX
        if (isOnScreen()) {
            try {
                services().playSfx(Sonic3kSfx.COLLAPSE.id);
            } catch (Exception e) {
                // Prevent audio failure from breaking game logic
            }
        }

        // Get the fragment mapping frame
        int fragmentFrameIndex = mappingFrame + config.fragmentFrameOffset;

        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            return;
        }
        ObjectSpriteSheet sheet = renderManager.getSheet(config.artKey);
        if (sheet == null || fragmentFrameIndex >= sheet.getFrameCount()) {
            return;
        }

        SpriteMappingFrame fragmentFrame = sheet.getFrame(fragmentFrameIndex);
        int pieceCount = fragmentFrame.pieces().size();
        int maxFragments = Math.min(pieceCount, config.collapseDelays.length);

        // Spawn fragment children for each piece, starting from index 1.
        // ROM: ObjPlatformCollapse_SmashObject's first loop iteration writes to the
        // parent (a1=a0), consuming piece 0 and delay[0] for the parent's solid-stay.
        // Subsequent iterations allocate new fragment objects starting from piece 1.
        for (int i = 1; i < maxFragments; i++) {
            int delay = config.collapseDelays[i];
            CollapsingPlatformFragment fragment = new CollapsingPlatformFragment(
                    x, y, fragmentFrameIndex, i, delay, config.artKey, hFlip);
            spawnDynamicObject(fragment);
        }

        // Mark remembered so platform doesn't respawn when player returns
        markRemembered();

        // Do NOT release the player here - they continue riding the invisible solid
        // platform until solidStayTimer expires (handled in state 2 update).
    }

    private void markRemembered() {
        try {
            LevelManager lm = GameServices.level();
            if (lm != null && lm.getObjectManager() != null) {
                lm.getObjectManager().markRemembered(spawn);
            }
        } catch (Exception e) {
            // Safe fallback for test environments
        }
    }

    // ===== Rendering =====

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(config.artKey);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        if (fragmented && state == 2) {
            // ROM: static_mappings mode — parent renders only piece 0 from the
            // fragment frame while providing invisible collision during solid-stay.
            int fragmentFrameIndex = mappingFrame + config.fragmentFrameOffset;
            renderer.drawFramePieceByIndex(fragmentFrameIndex, 0, x, y, hFlip, false);
        } else if (!fragmented) {
            renderer.drawFrameIndex(mappingFrame, x, y, hFlip, false);
        }
        // state 3 (falling): parent is off-screen heading down, no need to render
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }

    // ===== Helpers =====

    // Uses inherited getRenderManager() from AbstractObjectInstance

    private static ZoneConfig resolveConfig() {
        try {
            LevelManager lm = GameServices.level();
            if (lm != null) {
                int zone = lm.getCurrentZone();
                int act = lm.getCurrentAct();
                if (zone == Sonic3kZoneIds.ZONE_AIZ) {
                    return act == 0 ? AIZ1_CONFIG : AIZ2_CONFIG;
                }
                if (zone == Sonic3kZoneIds.ZONE_ICZ) {
                    return ICZ_CONFIG;
                }
                LOG.warning("CollapsingPlatform: unknown zone 0x" + Integer.toHexString(zone) + ", defaulting to AIZ1 config");
            }
        } catch (Exception e) {
            LOG.fine("Could not resolve zone config: " + e.getMessage());
        }
        return AIZ1_CONFIG; // fallback
    }

    // ===================================================================
    // Fragment inner class
    // ===================================================================

    /**
     * Fragment object spawned when the collapsing platform breaks apart.
     * Each fragment renders a single piece from the fragment mapping frame and
     * falls with gravity after its individual delay expires.
     * <p>
     * ROM: loc_205CE (fragment routine in Obj_CollapsingPlatform).
     */
    public static class CollapsingPlatformFragment extends AbstractFallingFragment {

        private final int fragmentFrameIndex;
        private final int pieceIndex;
        private final String artKey;
        private final boolean hFlip;

        public CollapsingPlatformFragment(int parentX, int parentY,
                                          int fragmentFrameIndex, int pieceIndex,
                                          int delay, String artKey, boolean hFlip) {
            super(new ObjectSpawn(parentX, parentY, Sonic3kObjectIds.COLLAPSING_PLATFORM,
                    0, hFlip ? 1 : 0, false, 0), "PlatformFragment", delay, PRIORITY);
            this.fragmentFrameIndex = fragmentFrameIndex;
            this.pieceIndex = pieceIndex;
            this.artKey = artKey;
            this.hFlip = hFlip;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(artKey);
            if (renderer == null) {
                return;
            }

            renderer.drawFramePieceByIndex(fragmentFrameIndex, pieceIndex, getX(), getY(), hFlip, false);
        }
    }
}
