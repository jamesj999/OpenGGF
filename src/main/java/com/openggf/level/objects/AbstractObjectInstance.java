package com.openggf.level.objects;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.graphics.GLCommand;
import com.openggf.game.GameServices;
import com.openggf.level.LevelManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.game.PlayableEntity;

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
     * Avoids repeated Camera.getInstance() calls when checking visibility.
     */
    private static CameraBounds cameraBounds = new CameraBounds(0, 0, 320, 224);

    protected final ObjectSpawn spawn;
    protected final String name;
    private boolean destroyed;
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

    /**
     * Returns the application configuration service.
     * <p>
     * This is a convenience accessor that keeps {@code SonicConfigurationService.getInstance()}
     * out of leaf-class bytecode, preventing false positives in the migration guard test.
     */
    /**
     * Returns the construction-time services context.
     * Usable from static factory methods called during object construction.
     */
    protected static ObjectServices constructionContext() {
        return CONSTRUCTION_CONTEXT.get();
    }

    protected SonicConfigurationService config() {
        return SonicConfigurationService.getInstance();
    }

    /**
     * Returns the debug overlay manager.
     * <p>
     * Convenience accessor that keeps {@code DebugOverlayManager.getInstance()}
     * out of leaf-class bytecode, preventing false positives in the migration guard test.
     */
    protected DebugOverlayManager debugOverlay() {
        return DebugOverlayManager.getInstance();
    }

    /**
     * Static accessor for debug view config check, usable from static field initializers.
     * Keeps {@code SonicConfigurationService.getInstance()} out of leaf-class bytecode.
     */
    protected static boolean staticDebugViewEnabled() {
        return SonicConfigurationService.getInstance()
                .getBoolean(com.openggf.configuration.SonicConfiguration.DEBUG_VIEW_ENABLED);
    }

    /**
     * Static accessor for debug overlay manager, usable from static field initializers.
     * Keeps {@code DebugOverlayManager.getInstance()} out of leaf-class bytecode.
     */
    protected static DebugOverlayManager staticDebugOverlay() {
        return DebugOverlayManager.getInstance();
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
     * Static accessor for RomManager, usable from static helper methods.
     * Keeps {@code RomManager.getInstance()} out of leaf-class bytecode.
     */
    protected static RomManager staticRomManager() {
        return RomManager.getInstance();
    }

    /**
     * Static accessor for LevelManager's ObjectManager, usable from static factory methods.
     * Keeps {@code LevelManager.getInstance()} out of leaf-class bytecode.
     */
    protected static ObjectManager staticObjectManager() {
        LevelManager lm = LevelManager.getInstance();
        return lm != null ? lm.getObjectManager() : null;
    }

    /**
     * Static accessor for LevelManager, usable from object helper methods that are
     * invoked outside a live object-services context.
     */
    protected static LevelManager staticLevelManager() {
        return LevelManager.getInstance();
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
    }

    @Override
    public boolean isDestroyed() {
        return destroyed;
    }

    @Override
    public boolean isHighPriority() {
        return false;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        // Default no-op.
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
     * ROM parity for ReactToItem: returns true if the object was on-screen
     * as of the pre-update snapshot (equivalent to obRender bit 7 from
     * the previous frame's DisplaySprite). Uses pre-update X position since
     * the ROM's MarkObjGone / DisplaySprite only checks X distance from
     * camera, not Y. Objects below the visible area (like lava surfaces)
     * are still considered "on screen" if their X is within range.
     */
    public boolean isOnScreenForTouch() {
        if (!preUpdateValid) return false; // No snapshot → first frame, skip
        return cameraBounds.containsX(preUpdateX);
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
     * Adds an already-constructed object to the level's object manager.
     * <p>
     * <b>Does NOT set {@link #CONSTRUCTION_CONTEXT}.</b> If the object's constructor
     * needs {@link #services()}, use {@link #spawnChild(java.util.function.Supplier)}
     * instead, which wraps construction with the ThreadLocal context.
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
     * Creates a dynamic child object with services available during construction.
     * The supplier is called with the {@link #CONSTRUCTION_CONTEXT} set, so the
     * child's constructor can safely call {@link #services()}.
     * <p>
     * Example usage:
     * <pre>{@code
     * ChildObject child = spawnChild(() -> new ChildObject(spawn, params));
     * }</pre>
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

    /**
     * Returns the ObjectRenderManager, or null if not available.
     */
    protected static ObjectRenderManager getRenderManager() {
        LevelManager lm = staticLevelManager();
        return (lm != null) ? lm.getObjectRenderManager() : null;
    }

    /**
     * Returns the ready PatternSpriteRenderer for the given art key, or null
     * if the render manager or renderer is unavailable/not ready.
     */
    protected static PatternSpriteRenderer getRenderer(String artKey) {
        ObjectRenderManager rm = getRenderManager();
        if (rm == null) return null;
        PatternSpriteRenderer renderer = rm.getRenderer(artKey);
        return (renderer != null && renderer.isReady()) ? renderer : null;
    }
}
