package com.openggf.level.objects;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.graphics.GLCommand;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.rewind.GenericFieldCapturer;
import com.openggf.game.rewind.GenericRewindEligibility;
import com.openggf.level.LevelManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;

import java.util.List;
import java.util.logging.Logger;

public abstract class AbstractObjectInstance implements ObjectInstance {
    private static final Logger LOG = Logger.getLogger(AbstractObjectInstance.class.getName());

    /**
     * Construction-time services context. Set by {@link ObjectManager} before calling
     * a factory, cleared in a finally block after construction completes.
     * <p>
     * This allows {@link #services()} to work during construction without requiring
     * constructor injection through every factory and subclass constructor.
     * Package-private: only {@link ObjectManager} should set/clear this.
     */
    static final ThreadLocal<ObjectServices> CONSTRUCTION_CONTEXT = new ThreadLocal<>();

    /**
     * Pre-allocated slot index for the object being constructed.
     * Set by {@link ObjectManager#syncActiveSpawnsLoad()} before calling the factory,
     * consumed (and cleared) by the first {@code super()} call in the constructor.
     * <p>
     * This ensures the parent object already has its slot assigned when its constructor
     * spawns children (e.g., GlassBlock's reflection). Without this, children would get
     * LOWER slots than the parent (FindFreeObj starts from bit 0), but the ROM's
     * FindNextFreeObj gives children HIGHER slots (scanning from the parent forward).
     * Package-private: only {@link ObjectManager} should set this.
     */
    static final ThreadLocal<Integer> PRE_ALLOCATED_SLOT = new ThreadLocal<>();

    /**
     * Cached camera bounds, updated once per frame by ObjectManager.
     * Avoids repeated camera lookups when checking visibility.
     */
    private static CameraBounds cameraBounds = new CameraBounds(0, 0, 320, 224);

    /**
     * Y-axis half-margin used by {@link #isOnScreenForTouch()} to mirror ROM's
     * BuildSprites {@code .assumeHeight} band. ROM (S1
     * {@code docs/s1disasm/_inc/BuildSprites.asm:71-78}, S2/S3K equivalents)
     * computes {@code obY - cameraY + 0x80} and checks the result against
     * {@code [0x60, 0x180)} -- equivalently, an object's Y must fall within
     * {@code [cameraY - 32, cameraY + 224 + 32)} for {@code obRender} bit 7 to
     * be set. The 32-pixel padding above and below the visible 224-line
     * viewport is what this constant captures.
     * <p>
     * This margin is deliberately coarser than ROM's {@code btst #4}
     * explicit-height path (which uses each object's per-object half-height
     * read from {@code height_pixels}). The trade-off is intentional: the
     * touch gate accepts touch tests for slightly more objects than ROM
     * would, but never rejects an object ROM would accept (i.e. it never
     * wrongly skips a touch). False positives are filtered by the
     * subsequent collision-flags / box test inside {@code TouchResponses};
     * false negatives would silently break game-state parity and have no
     * downstream filter. For per-object override semantics see
     * {@link #getOnScreenHalfHeight()}, which the solid-contact gate
     * ({@link #isOnScreen()}) consults instead -- the touch gate
     * intentionally uses this constant rather than the per-object height.
     */
    private static final int TOUCH_RESPONSE_Y_MARGIN = 32;
    protected final ObjectSpawn spawn;
    protected final String name;
    private boolean destroyed;
    /**
     * ROM parity: true when this destroy was triggered by an off-screen check
     * (Sprite_OnScreen_Test family in sonic3k.asm, e.g. loc_1B5A0), where ROM
     * clears bit 7 of the respawn-table entry ({@code bclr #7,(a2)} at
     * sonic3k.asm:37275) so the object can be re-spawned by the placement
     * system when the camera returns. Without this flag, the engine's
     * {@code permanentDestroyLatch} (S3K) treats every destroy as a latched
     * "do not respawn" (modeling player-kill explosions which never clear
     * the respawn bit). Off-screen self-deletes are explicitly NOT a
     * latched destroy in the ROM and must be respawnable.
     */
    private boolean destroyedRespawnable;
    private ObjectSpawn dynamicSpawn;
    private ObjectServices services;

    /**
     * Pre-update position snapshot, saved by ObjectManager before the object update loop.
     * Used by touch response collision checks to match ROM ordering (ReactToItem runs
     * before ExecuteObjects in the ROM, so objects are at their pre-update positions
     * during touch collision).
     */
    private int preUpdateX;
    private int preUpdateY;
    private boolean preUpdateValid;

    /**
     * Set when this object receives a same-frame update after being spawned mid-loop.
     * While true, touch response collision checks skip this object (ROM parity:
     * Sonic's ReactToItem at slot 0 runs before the parent creates this child).
     * Cleared on the next call to {@link #snapshotPreUpdatePosition()}.
     */
    private boolean skipTouchThisFrame;

    /**
     * ROM parity: Objects skip SolidObject on their first frame because obRender bit 7
     * (set by DisplaySprite) hasn't been set yet. The object's init routine sets
     * obRender to 4 (no bit 7), then DisplaySprite sets bit 7 if on-screen. On the next
     * frame, the routine checks bit 7 and proceeds with SolidObject.
     * <p>
     * This flag starts true and is cleared after the first {@link #snapshotPreUpdatePosition()}.
     */
    private boolean solidContactFirstFrame = true;

    /**
     * Pre-update collision flags snapshot. ROM parity: ReactToItem runs before other
     * objects update, so it sees enemies at their previous frame's collision type.
     * -1 means no snapshot (use current flags).
     */
    private int preUpdateCollisionFlags = -1;

    /**
     * ROM parity: Object slot index matching the Mega Drive's Object Status Table.
     * <p>
     * In the ROM, ExecuteObjects processes slots 0-127 sequentially. The d7 register
     * holds the loop counter: d7 = 127 for slot 0 (Sonic), d7 = 126 for slot 1, etc.
     * Some objects (e.g. Batbrain/Basaran) use d7 for frame-based randomization gates:
     * {@code (v_vbla_byte + d7) & 7 == 0}.
     * <p>
     * Assigned by {@link ObjectManager} when objects are created. -1 means unassigned
     * (should not happen for objects created through ObjectManager).
     */
    private int slotIndex = -1;

    /**
     * ROM parity (S1): Index into the respawn state table (v_objstate+2).
     * <p>
     * In S1, ObjPosLoad assigns each remember-state object a counter-based index
     * via {@code move.b d2,obRespawnNo(a1)}. When the object goes off-screen,
     * RememberState clears bit 7 at this index, allowing respawn. When the object
     * is destroyed (e.g. via .chkdel), the bit stays set, preventing respawn.
     * <p>
     * -1 means not tracked (non-remember-state object or S2/S3K mode).
     */
    private int respawnStateIndex = -1;

    protected AbstractObjectInstance(ObjectSpawn spawn, String name) {
        this.spawn = spawn;
        this.name = name;
        // ROM parity: consume the pre-allocated slot so that getSlotIndex()
        // returns the correct value if the constructor spawns children.
        // Only the first super() call gets the slot; child constructors see null.
        Integer preSlot = PRE_ALLOCATED_SLOT.get();
        if (preSlot != null) {
            this.slotIndex = preSlot;
            PRE_ALLOCATED_SLOT.remove(); // Consume — only first constructor gets it
        }
    }

    /**
     * Updates the cached camera bounds in place. Called once per frame by ObjectManager
     * before any object updates run.
     *
     * @param verticalWrapRange Vertical wrap range in pixels (0 = no wrapping).
     *                          When > 0, Y visibility checks use modular arithmetic.
     */
    public static void updateCameraBounds(int left, int top, int right, int bottom, int verticalWrapRange) {
        cameraBounds.update(left, top, right, bottom);
        cameraBounds.setVerticalWrapRange(verticalWrapRange);
    }

    /**
     * Reset the previous-frame snapshot so the next
     * {@link #updateCameraBounds(int, int, int, int, int)} call mirrors the
     * current camera. Used by test infrastructure that recycles the static
     * camera bounds across fresh fixtures (each fixture starts with a fresh
     * level / camera and should not inherit the prior fixture's snapshot).
     */
    public static void resetCameraBoundsForTests() {
        cameraBounds.update(0, 0, 320, 224);
    }

    @Override
    public ObjectSpawn getSpawn() {
        return dynamicSpawn != null ? dynamicSpawn : spawn;
    }

    /**
     * Lazily updates the dynamic spawn to track the object's current position.
     * After this call, {@link #getSpawn()} returns a spawn at (x, y) instead of
     * the original placement position. No allocation occurs if the position is unchanged.
     */
    protected void updateDynamicSpawn(int x, int y) {
        if (dynamicSpawn == null || dynamicSpawn.x() != x || dynamicSpawn.y() != y) {
            dynamicSpawn = buildSpawnAt(x, y);
        }
    }

    public String getName() {
        return name;
    }

    @Override
    public void snapshotPreUpdatePosition() {
        // Guard: some dynamic objects (effects, projectiles) may have null spawn
        if (getSpawn() == null) return;
        preUpdateX = getX();
        preUpdateY = getY();
        preUpdateValid = true;
        // Snapshot collision flags for touch response timing parity.
        // ROM: ReactToItem sees enemies at their pre-update collision type.
        if (this instanceof TouchResponseProvider trp) {
            preUpdateCollisionFlags = trp.getCollisionFlags();
        }
        // Clear same-frame spawn flag: this object has survived one full frame
        // and is now eligible for touch collision checks.
        skipTouchThisFrame = false;
        // Clear first-frame flag: object has completed one frame cycle.
        // ROM: obRender bit 7 is now set by DisplaySprite, so SolidObject runs.
        solidContactFirstFrame = false;
    }

    @Override
    public void snapshotTouchResponseState() {
        if (getSpawn() == null) return;
        preUpdateX = getX();
        preUpdateY = getY();
        preUpdateValid = true;
        if (this instanceof TouchResponseProvider trp) {
            preUpdateCollisionFlags = trp.getCollisionFlags();
        }
        // Frame-start ReactToItem snapshots happen before object execution.
        // A child created after the player slot in the previous object pass is
        // no longer same-frame-spawned here and must be touch-eligible.
        skipTouchThisFrame = false;
    }

    @Override
    public int getPreUpdateX() {
        return preUpdateValid ? preUpdateX : getX();
    }

    @Override
    public int getPreUpdateY() {
        return preUpdateValid ? preUpdateY : getY();
    }

    @Override
    public int getPreUpdateCollisionFlags() {
        return preUpdateCollisionFlags;
    }

    @Override
    public boolean isSkipTouchThisFrame() {
        return skipTouchThisFrame;
    }

    @Override
    public boolean isSkipSolidContactThisFrame() {
        return solidContactFirstFrame;
    }

    /**
     * Marks this object as having received a same-frame update.
     * Called by ObjectManager when processing pending children in the finally block.
     */
    public void setSkipTouchThisFrame(boolean skip) {
        this.skipTouchThisFrame = skip;
    }

    /**
     * Returns this object's slot index in the Object Status Table (0-127).
     * <p>
     * Use {@code 127 - getSlotIndex()} to compute the ROM's d7 register value
     * for randomization gates.
     *
     * @return slot index, or -1 if not yet assigned
     */
    public int getSlotIndex() {
        return slotIndex;
    }

    /**
     * Returns the SST slot whose turn should execute this object.
     * <p>
     * Most engine objects map one Java instance to one ROM object slot. A small
     * number of consolidated multi-slot objects keep the parent slot for
     * lifecycle/allocation bookkeeping while executing gameplay from a child
     * slot that owned the ROM routine.
     */
    public int getExecutionSlotIndex() {
        return slotIndex;
    }

    /**
     * Assigns this object's slot index. Called by ObjectManager during object creation.
     *
     * @param index slot index (0-127 for ROM-matching slots, may exceed 127 for overflow)
     */
    public void setSlotIndex(int index) {
        this.slotIndex = index;
    }

    /**
     * Returns this object's respawn state table index (S1 counter-based system).
     * @return respawn state index, or -1 if not tracked
     */
    public int getRespawnStateIndex() {
        return respawnStateIndex;
    }

    /**
     * Sets this object's respawn state table index (S1 counter-based system).
     * Called by ObjectManager when creating objects during counter-based spawn.
     */
    public void setRespawnStateIndex(int index) {
        this.respawnStateIndex = index;
    }

    /**
     * Sets the injectable services handle. Called by ObjectManager after construction.
     */
    public void setServices(ObjectServices services) {
        this.services = services;
    }

    /**
     * Returns the injectable services handle. Safe to call at any point in the
     * object lifecycle — during construction, update, or rendering — as long as
     * the object was created through {@link ObjectManager} or
     * {@link ObjectManager#addDynamicObject}.
     * <p>
     * During construction, falls back to the {@link #CONSTRUCTION_CONTEXT} ThreadLocal
     * set by ObjectManager before calling the factory. After construction,
     * uses the instance field set by {@link #setServices}.
     */
    protected ObjectServices services() {
        if (services != null) {
            return services;
        }
        ObjectServices ctx = CONSTRUCTION_CONTEXT.get();
        if (ctx != null) {
            return ctx;
        }
        throw new IllegalStateException(
                getClass().getSimpleName() + ": services not available — "
                + "object must be created through ObjectManager");
    }

    protected ObjectServices tryServices() {
        if (services != null) {
            return services;
        }
        return CONSTRUCTION_CONTEXT.get();
    }

    /**
     * Returns the construction-time services context.
     * Usable from static factory methods called during object construction.
     */
    protected static ObjectServices constructionContext() {
        return CONSTRUCTION_CONTEXT.get();
    }

    protected SonicConfigurationService config() {
        return services().configuration();
    }

    /**
     * Returns the debug overlay manager.
     */
    protected DebugOverlayManager debugOverlay() {
        return services().debugOverlay();
    }

    /**
     * Static accessor for debug view config check, usable from field initializers.
     */
    protected static boolean staticDebugViewEnabled() {
        ObjectServices ctx = constructionContext();
        SonicConfigurationService configuration = ctx != null ? ctx.configuration() : null;
        if (configuration == null) {
            configuration = GameServices.configuration();
        }
        return configuration.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
    }

    /**
     * Static accessor for debug overlay manager, usable from field initializers.
     */
    protected static DebugOverlayManager staticDebugOverlay() {
        ObjectServices ctx = constructionContext();
        DebugOverlayManager overlayManager = ctx != null ? ctx.debugOverlay() : null;
        return overlayManager != null ? overlayManager : GameServices.debugOverlay();
    }

    /**
     * Sets the construction context for child objects created during update.
     * Must be paired with {@link #clearConstructionContext()} in a finally block.
     */
    protected static void setConstructionContext(ObjectServices services) {
        CONSTRUCTION_CONTEXT.set(services);
    }

    /**
     * Clears the construction context after child object creation.
     */
    protected static void clearConstructionContext() {
        CONSTRUCTION_CONTEXT.remove();
    }

    /**
     * Static accessor for RomManager, usable from helper methods during object setup.
     */
    protected static RomManager staticRomManager() {
        ObjectServices ctx = constructionContext();
        RomManager romManager = ctx != null ? ctx.romManager() : null;
        return romManager != null ? romManager : GameServices.rom();
    }

    /**
     * Static accessor for LevelManager's ObjectManager, usable from static factory methods.
     * Keeps runtime lookups out of leaf-class bytecode.
     */
    protected static ObjectManager staticObjectManager() {
        LevelManager lm = GameServices.levelOrNull();
        return lm != null ? lm.getObjectManager() : null;
    }

    /**
     * Static accessor for LevelManager, usable from object helper methods that are
     * invoked outside a live object-services context.
     */
    protected static LevelManager staticLevelManager() {
        return GameServices.levelOrNull();
    }

    /**
     * Static accessor for RingManager, usable from constructors/static helpers while
     * keeping singleton access out of leaf-class bytecode.
     */
    protected static com.openggf.level.rings.RingManager staticRingManager() {
        LevelManager lm = staticLevelManager();
        return lm != null ? lm.getRingManager() : null;
    }

    public void setDestroyed(boolean destroyed) {
        this.destroyed = destroyed;
        if (!destroyed) {
            this.destroyedRespawnable = false;
        }
    }

    /**
     * Marks this object as destroyed via an off-screen check
     * (ROM Sprite_OnScreen_Test family, sonic3k.asm:37262 etc.). The placement
     * system will release the slot but will NOT latch the spawn into
     * {@code destroyedInWindow}, so when the camera re-enters the placement
     * window the object can re-spawn. This mirrors ROM's
     * {@code bclr #7,(a2)} at loc_1B5A0 (sonic3k.asm:37275).
     */
    public void setDestroyedByOffscreen() {
        this.destroyed = true;
        this.destroyedRespawnable = true;
    }

    @Override
    public boolean isDestroyed() {
        return destroyed;
    }

    /**
     * Returns true when the most recent destroy was an off-screen self-delete
     * (Sprite_OnScreen_Test) and the spawn should remain re-spawnable. See
     * {@link #setDestroyedByOffscreen()}.
     */
    @Override
    public boolean isDestroyedRespawnable() {
        return destroyedRespawnable;
    }

    @Override
    public boolean isHighPriority() {
        return false;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        // Default no-op.
    }

    /**
     * Hydrates this instance from a pre-trace ROM SST slot snapshot.
     * <p>
     * Used by the trace replay test harness to restore state-machine progress that
     * the ROM accumulates during title card / level-init iterations before the
     * Lua recorder begins emitting trace frames. Invoked once per slot, immediately
     * after the object is constructed and registered with the ObjectManager, and
     * before trace frame 0 is driven.
     * <p>
     * Default implementation is a no-op for objects that either hold no significant
     * pre-trace state or that have not yet been wired for snapshot hydration.
     * Subclasses override to read canonical fields (position, velocity, routine,
     * status, per-object state variables) and copy them onto their engine-side state.
     * <p>
     * <b>Must not spawn children, play audio, or emit render commands</b> — this is
     * a pure data copy. Any derived state (animation timers, render caches) should
     * be rebuilt lazily on the next {@link #update} / render call.
     *
     * @param snapshot immutable snapshot of SST bytes/words for this slot
     */
    public void hydrateFromRomSnapshot(RomObjectSnapshot snapshot) {
        // Default: no hydration. Subclasses override.
    }

    @Override
    public abstract void appendRenderCommands(List<GLCommand> commands);

    /**
     * Checks if this object is currently visible on screen.
     * ROM: render_flags.on_screen bit is set by MarkObjGone when object
     * is within camera bounds. Used by PlaySoundLocal (s2.asm line 1555)
     * to prevent off-screen objects from playing audio.
     * <p>
     * Uses pre-computed camera bounds (updated once per frame) for efficiency.
     *
     * @return true if the object is within the camera viewport
     */
    protected boolean isOnScreen() {
        return cameraBounds.contains(getX(), getY());
    }

    /**
     * ROM parity for SolidObject_OnScreenTest (s2.asm:35140-35145,
     * sonic3k.asm:41390-41392 loc_1DF88, s1disasm/_incObj/sub SolidObject.asm
     * Solid_ChkEnter / SolidObject2F): returns true when the object's render
     * box currently overlaps the camera viewport. ROM equivalent is render_flags
     * bit 7 which Render_Sprites sets each frame based on bounding-box
     * overlap. Used by the inline solid contact path to skip side / top / bottom
     * resolution for objects the camera has already scrolled past, matching the
     * ROM's "if Sonic outruns the screen then he can phase through solid objects"
     * optimisation that the S2 disassembly explicitly documents.
     */
    public boolean isWithinSolidContactBounds() {
        // ROM Render_Sprites (sonic3k.asm:36336-36370 SolidObject_OnScreenTest,
        // s2.asm:35140-35145, s1disasm Solid_ChkEnter / SolidObject2F) sets
        // render_flags bit 7 when the object's bounding box overlaps the
        // 320x224 screen rectangle. The bounding box is centered on x_pos with
        // half-width = width_pixels(a0) (sonic3k.asm:36347 reads width_pixels
        // into d2, then 36350/36353 add/subtract d2 from (x_pos - cam) before
        // comparing against [0, 320]). Most gameplay objects use width_pixels=16,
        // but larger sprites (e.g. the CNZ horizontal door at sonic3k.asm:66167
        // byte_30FCE = $20, $08) use a wider rendered half-width and stay
        // on-screen longer than a hardcoded 16-px margin allows. Defer to the
        // per-object on-screen half-width so collision parity matches the ROM
        // for both small and large sprites.
        //
        // ObjectManager runs before the current frame's camera step, so the
        // cached camera bounds already represent the prior Render_Sprites pass
        // that set render_flags bit 7. The extra previous-frame snapshot lags
        // the ROM gate by two frames in inline object order.
        return cameraBounds.contains(
                getX(), getY(), getOnScreenHalfWidth(), getOnScreenHalfHeight());
    }

    /**
     * Per-object rendered half-width used by the on-screen / solid-contact
     * gate. ROM equivalent: {@code width_pixels(a0)} as read by Render_Sprites
     * (sonic3k.asm:36347 / s2.asm equivalent). Defaults to the widely shared
     * gameplay sprite half-width of 16 px so existing call sites stay
     * unchanged; objects with a wider rendered footprint (e.g. CNZ horizontal
     * door byte_30FCE = $20, $08 at sonic3k.asm:66167) override this to match
     * the ROM-side on-screen test.
     */
    public int getOnScreenHalfWidth() {
        return 16;
    }

    /**
     * Per-object rendered half-height used by the on-screen / solid-contact
     * gate. ROM equivalent: {@code height_pixels(a0)} as read by Render_Sprites
     * while setting render_flags bit 7.
     */
    public int getOnScreenHalfHeight() {
        return 16;
    }

    /**
     * ROM parity for ReactToItem: returns true if the object was on-screen
     * as of the pre-update snapshot (equivalent to obRender bit 7 from
     * the previous frame's BuildSprites).
     * <p>
     * The render-flag-driven Y gate is S1-specific. ROM S1's
     * {@code ReactToItem} ({@code docs/s1disasm/_incObj/sub
     * ReactToItem.asm:26-27}) reads {@code obRender(a1) / bpl.s .next}
     * and skips objects whose bit 7 has been cleared by
     * {@code BuildSprites} ({@code docs/s1disasm/_inc/BuildSprites.asm:71-78},
     * {@code .assumeHeight} branch when {@code obRender} bit 4 is clear).
     * That bit clears for any object whose Y falls outside
     * {@code [cameraY - 32, cameraY + 256)} (the visible 224-line viewport
     * plus a 32-px margin above and below). ROM S2 {@code Touch_Loop}
     * ({@code docs/s2disasm/s2.asm} ~84502-84551) has no equivalent
     * render-flag gate; S3K {@code TouchResponse}
     * ({@code docs/skdisasm/sonic3k.asm:20655}) consumes a pre-built
     * {@code Collision_response_list} where the gate happens upstream
     * during list build, not at touch time.
     * <p>
     * The engine therefore branches on
     * {@link PhysicsFeatureSet#touchResponseUsesRenderFlagYGate()}: S1
     * gets the X+Y check; S2/S3K fall back to the X-only check the
     * engine used pre-Task-3 (commits b4ff4ea01/86871035c). Without this
     * gating the universal X+Y check filters S3K objects ROM allows to
     * interact with Tails, regressing MGZ trace replay first-fail from
     * frame 2395 to frame 1659.
     * <p>
     * Uses pre-update position so the gate matches the previous frame's
     * BuildSprites pass, mirroring the ROM ordering where the render
     * flag set this frame would not be observable until the next frame's
     * ReactToItem. The S1 xMargin uses {@link #getOnScreenHalfWidth()}
     * (default 16 px = ROM {@code width_pixels} for typical sprites) and
     * the yMargin uses 32 to mirror the {@code .assumeHeight} 32-pixel
     * band; this makes the gate slightly more inclusive than the
     * {@code btst #4} explicit-height path (which uses the per-object
     * half-height) but never more restrictive, so it will not introduce
     * false-negative collision skips for objects whose ROM render flag
     * would have been set.
     */
    public boolean isOnScreenForTouch() {
        if (!preUpdateValid) return false; // No snapshot → first frame, skip
        if (resolveTouchResponseUsesRenderFlagYGate()) {
            // S1: include the BuildSprites .assumeHeight Y band.
            return cameraBounds.contains(preUpdateX, preUpdateY,
                    getOnScreenHalfWidth(), TOUCH_RESPONSE_Y_MARGIN);
        }
        // S2/S3K: pre-Task-3 X-only behaviour. Matches ROM S2 Touch_Loop
        // (no render-flag gate) and S3K Collision_response_list (gate
        // happens upstream during list build, not at touch time).
        return cameraBounds.containsX(preUpdateX);
    }

    /**
     * Resolves whether the active game gates {@link #isOnScreenForTouch()}
     * on the BuildSprites Y-band. Defaults to {@code true} when no game
     * module / feature set is available so test fixtures (which often run
     * without a fully-bootstrapped runtime) keep the stricter S1 gate the
     * regression suite was calibrated against.
     */
    private boolean resolveTouchResponseUsesRenderFlagYGate() {
        ObjectServices ctx = tryServices();
        GameModule module = ctx != null ? ctx.gameModule() : null;
        if (module == null) {
            return true;
        }
        PhysicsProvider physProvider = module.getPhysicsProvider();
        PhysicsFeatureSet featureSet = physProvider != null ? physProvider.getFeatureSet() : null;
        if (featureSet == null) {
            return true;
        }
        return featureSet.touchResponseUsesRenderFlagYGate();
    }

    /**
     * Checks if this object's X is within the camera viewport.
     * Matches ROM's MarkObjGone which only checks X distance for the on_screen flag.
     * Use this for detection checks where Y proximity is handled separately.
     */
    protected boolean isOnScreenX() {
        return cameraBounds.containsX(getX());
    }

    protected boolean isOnScreenX(int margin) {
        return cameraBounds.containsX(getX(), margin);
    }

    /**
     * Checks if this object is within the camera viewport with a margin.
     * Useful for projectiles that should persist slightly off-screen.
     *
     * @param margin pixels of extra space beyond camera bounds
     * @return true if the object is within the extended camera viewport
     */
    protected boolean isOnScreen(int margin) {
        return cameraBounds.contains(getX(), getY(), margin);
    }

    /**
     * ROM-accurate {@code ChkObjectVisible} check.
     * <p>
     * Returns true if the object position falls within the exact screen rectangle:
     * {@code 0 <= (obX - cameraX) < 320} AND {@code 0 <= (obY - cameraY) < 224}.
     * No margin, exclusive upper bounds (matching {@code bge.s .offscreen}).
     * <p>
     * Used by objects that call {@code ChkObjectVisible} in the ROM
     * (lava ball maker, gargoyle, invisible barriers).
     * <p>
     * Reference: docs/s1disasm/_incObj/sub ChkObjectVisible.asm
     */
    protected boolean isChkObjectVisible() {
        int dx = getX() - cameraBounds.left();
        if (dx < 0 || dx >= 320) return false;
        int dy = getY() - cameraBounds.top();
        return dy >= 0 && dy < 224;
    }

    /**
     * ROM-accurate out_of_range check (X-only, chunk-aligned).
     * <p>
     * Matches the S1/S2 {@code out_of_range} macro (Macros.asm):
     * <pre>
     *   move.w  obX(a0),d0
     *   andi.w  #$FF80,d0           ; chunk-align object X
     *   move.w  (v_screenposx).w,d1
     *   subi.w  #128,d1
     *   andi.w  #$FF80,d1           ; chunk-align (screenX - 128)
     *   sub.w   d1,d0
     *   cmpi.w  #128+320+192,d0     ; 640 = total range
     *   bhi     exit
     * </pre>
     *
     * @return true if object is within range (should NOT be deleted)
     */
    protected boolean isInRange() {
        int objAligned = getX() & 0xFF80;
        int screenAligned = (cameraBounds.left() - 128) & 0xFF80;
        // ROM does a 16-bit sub.w followed by unsigned bhi, so preserve wrap semantics.
        int dist = (objAligned - screenAligned) & 0xFFFF;
        return dist <= (128 + 320 + 192);
    }

    /**
     * Adds an already-constructed object using FindNextFreeObj semantics.
     * <p>
     * <b>Does NOT set {@link #CONSTRUCTION_CONTEXT}.</b> If the object's constructor
     * needs {@link #services()}, use {@link #spawnChild(java.util.function.Supplier)}
     * or {@link #spawnFreeChild(java.util.function.Supplier)} instead.
     * <p>
     * Safe to call in test environments where LevelManager may not be initialized.
     *
     * @param object the already-constructed object instance to spawn
     */
    protected void spawnDynamicObject(AbstractObjectInstance object) {
        try {
            ObjectManager om = services().objectManager();
            if (om != null) {
                om.addDynamicObjectAfterCurrent(object);
            }
        } catch (IllegalStateException e) {
            // Fallback for test environments or objects not managed by ObjectManager
            try {
                LevelManager lm = staticLevelManager();
                if (lm != null && lm.getObjectManager() != null) {
                    lm.getObjectManager().addDynamicObjectAfterCurrent(object);
                }
            } catch (Exception ex) {
                LOG.fine("Could not spawn dynamic object (test env?): " + ex.getMessage());
            }
        }
    }

    /**
     * Creates a dynamic child object with FindNextFreeObj semantics.
     * The supplier is called with the {@link #CONSTRUCTION_CONTEXT} set, so the
     * child's constructor can safely call {@link #services()}.
     * <p>
     * Use this when the ROM object calls FindNextFreeObj and expects the child to
     * allocate from the current slot forward.
     *
     * @param factory supplier that constructs the child object
     * @return the constructed child, already added to the object manager
     * @param <T> the child type
     */
    protected <T extends AbstractObjectInstance> T spawnChild(java.util.function.Supplier<T> factory) {
        ObjectServices svc = services();
        CONSTRUCTION_CONTEXT.set(svc);
        try {
            T child = factory.get();
            ObjectManager om = svc.objectManager();
            if (om != null) {
                om.addDynamicObjectAfterCurrent(child);
            }
            return child;
        } finally {
            CONSTRUCTION_CONTEXT.remove();
        }
    }

    /**
     * Creates a dynamic child object with FindFreeObj semantics.
     * The supplier is called with the {@link #CONSTRUCTION_CONTEXT} set, so the
     * child's constructor can safely call {@link #services()}.
     * <p>
     * Use this when the ROM object calls FindFreeObj and expects the child to
     * take the lowest free SST slot, even if that slot is below the parent.
     *
     * @param factory supplier that constructs the child object
     * @return the constructed child, already added to the object manager
     * @param <T> the child type
     */
    protected <T extends AbstractObjectInstance> T spawnFreeChild(java.util.function.Supplier<T> factory) {
        ObjectServices svc = services();
        CONSTRUCTION_CONTEXT.set(svc);
        try {
            T child = factory.get();
            ObjectManager om = svc.objectManager();
            if (om != null) {
                om.addDynamicObject(child);
            }
            return child;
        } finally {
            CONSTRUCTION_CONTEXT.remove();
        }
    }

    /**
     * Builds an ObjectSpawn at the given position, preserving all other fields from the
     * original spawn. Use in getSpawn() overrides and dynamic spawn tracking.
     */
    protected ObjectSpawn buildSpawnAt(int x, int y) {
        return new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(),
                spawn.renderFlags(), spawn.respawnTracked(), spawn.rawYWord(),
                spawn.layoutIndex());
    }

    /**
     * Returns true if any player is currently riding (standing on) this object.
     */
    protected boolean isPlayerRiding() {
        ObjectManager om = services().objectManager();
        return om != null && om.isAnyPlayerRiding(this);
    }

    protected SolidCheckpointBatch checkpointAll() {
        return services().solidExecution().resolveSolidNowAll();
    }

    protected boolean hasStandingContact(SolidCheckpointBatch batch) {
        for (PlayerSolidContactResult result : batch.perPlayer().values()) {
            if (result != null && result.standingNow()) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Rewind snapshot support
    // -------------------------------------------------------------------------

    /**
     * Captures this object's standard mutable gameplay state for a rewind snapshot.
     *
     * <p>The default implementation covers every field declared on
     * {@code AbstractObjectInstance}: destruction flags, dynamic spawn position,
     * pre-update position cache, touch/solid-contact gating flags, slot index,
     * and respawn state index.
     *
     * <p><strong>Subclass contract:</strong> Subclasses that hold private
     * gameplay-relevant state (boss phase counters, badnik AI timers, sub-state
     * machine indices, etc.) <em>must</em> override this method (and the matching
     * {@link #restoreRewindState}) to include their own fields — otherwise that
     * state will silently fail to round-trip across a rewind.
     *
     * <p>Known subclasses likely to require overrides (non-exhaustive):
     * <ul>
     *   <li>Any boss instance — phase counters, arena/boundary flags, hit counters</li>
     *   <li>{@code AbstractBadnikInstance} subclasses with multi-phase AI — per-frame
     *       timers beyond {@code animTimer}, direction change state</li>
     *   <li>CNZ bumper — reload timer</li>
     *   <li>HTZ earthquake object — oscillation accumulator</li>
     *   <li>Any object that uses {@code objoff_*} scratch fields for state machines</li>
     * </ul>
     *
     * <p>The current default path is centrally gated by
     * {@link GenericRewindEligibility#usesDefaultObjectSubclassCapture(Class)}:
     * subclasses without a concrete rewind override automatically capture fields
     * accepted by {@link GenericFieldCapturer#captureObjectSubclassScalars(AbstractObjectInstance)}.
     *
     * @return immutable snapshot of this object's standard mutable field surface
     */
    public PerObjectRewindSnapshot captureRewindState() {
        PerObjectRewindSnapshot snapshot = new PerObjectRewindSnapshot(
                destroyed,
                destroyedRespawnable,
                dynamicSpawn != null,
                dynamicSpawn != null ? dynamicSpawn.x() : 0,
                dynamicSpawn != null ? dynamicSpawn.y() : 0,
                preUpdateX,
                preUpdateY,
                preUpdateValid,
                preUpdateCollisionFlags,
                skipTouchThisFrame,
                solidContactFirstFrame,
                slotIndex,
                respawnStateIndex,
                null,  // Base class does not capture badnik extra; subclass overrides if needed
                null,  // Base class does not capture badnik subclass extra
                null   // Base class does not capture player extra; subclass overrides if needed
        );
        if (GenericRewindEligibility.usesDefaultObjectSubclassCapture(getClass())) {
            var genericState = GenericFieldCapturer.captureObjectSubclassScalars(this);
            if (!genericState.keys().isEmpty()) {
                snapshot = snapshot.withGenericState(genericState);
            }
        }
        return snapshot;
    }

    /**
     * Restores this object's standard mutable gameplay state from a rewind snapshot.
     *
     * <p>See {@link #captureRewindState()} for the subclass contract.
     *
     * @param s the snapshot to restore from
     */
    public void restoreRewindState(PerObjectRewindSnapshot s) {
        this.destroyed = s.destroyed();
        this.destroyedRespawnable = s.destroyedRespawnable();
        if (s.hasDynamicSpawn()) {
            updateDynamicSpawn(s.dynamicSpawnX(), s.dynamicSpawnY());
        } else {
            this.dynamicSpawn = null;
        }
        this.preUpdateX = s.preUpdateX();
        this.preUpdateY = s.preUpdateY();
        this.preUpdateValid = s.preUpdateValid();
        this.preUpdateCollisionFlags = s.preUpdateCollisionFlags();
        this.skipTouchThisFrame = s.skipTouchThisFrame();
        this.solidContactFirstFrame = s.solidContactFirstFrame();
        this.slotIndex = s.slotIndex();
        this.respawnStateIndex = s.respawnStateIndex();
        if (s.genericState() != null) {
            GenericFieldCapturer.restore(this, s.genericState());
        }
        // badnikExtra is handled by subclass overrides; base class does nothing
    }

    /**
     * Returns the ObjectRenderManager, or null if not available.
     */
    protected ObjectRenderManager getRenderManager() {
        ObjectServices services = tryServices();
        return services != null ? services.renderManager() : null;
    }

    /**
     * Returns the ready PatternSpriteRenderer for the given art key, or null
     * if the render manager or renderer is unavailable/not ready.
     */
    protected PatternSpriteRenderer getRenderer(String artKey) {
        ObjectRenderManager rm = getRenderManager();
        if (rm == null) return null;
        PatternSpriteRenderer renderer = rm.getRenderer(artKey);
        return (renderer != null && renderer.isReady()) ? renderer : null;
    }
}
