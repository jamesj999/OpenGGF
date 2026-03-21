package com.openggf.level.objects;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Base class for game-specific object registries.
 *
 * <p>Provides the shared {@code factories} map, lazy {@code ensureLoaded()} guard,
 * default placeholder factory, and the standard {@link #create(ObjectSpawn)} flow.
 * Subclasses implement {@link #registerDefaultFactories()} to populate the map
 * and {@link #getPrimaryName(int)} to return human-readable object names.
 */
public abstract class AbstractObjectRegistry implements ObjectRegistry {

    private static final Logger LOG = Logger.getLogger(AbstractObjectRegistry.class.getName());

    protected final Map<Integer, ObjectFactory> factories = new HashMap<>();
    private boolean loaded;

    protected final ObjectFactory defaultFactory = (spawn, registry) ->
            new PlaceholderObjectInstance(spawn, registry.getPrimaryName(spawn.objectId()));

    // ------------------------------------------------------------------
    // ObjectRegistry contract
    // ------------------------------------------------------------------

    @Override
    public ObjectInstance create(ObjectSpawn spawn) {
        ensureLoaded();
        int id = spawn.objectId();
        ObjectFactory factory = factories.getOrDefault(id, defaultFactory);
        return factory.create(spawn, this);
    }

    @Override
    public void reportCoverage(List<ObjectSpawn> spawns) {
        // No-op by default; S2 overrides with coverage logging.
    }

    @Override
    public abstract String getPrimaryName(int objectId);

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    /**
     * Registers a factory for the given object ID.
     * Called from {@link #registerDefaultFactories()} in each subclass.
     */
    protected void registerFactory(int objectId, ObjectFactory factory) {
        factories.put(objectId, factory);
    }

    /**
     * Lazily initialises the registry on first access.
     * Subclasses may override to perform additional setup (e.g. loading name tables)
     * but <em>must</em> call {@code super.ensureLoaded()} first.
     */
    protected void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        registerDefaultFactories();
        LOG.fine(getClass().getSimpleName() + " loaded with " + factories.size() + " factories.");
    }

    /**
     * Populates {@link #factories} with game-specific object factories.
     * Called exactly once from {@link #ensureLoaded()}.
     */
    protected abstract void registerDefaultFactories();
}
