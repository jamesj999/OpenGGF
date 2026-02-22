package com.openggf.physics;

/**
 * Aggregates sensor results for terrain collision detection.
 * Delegates individual scans to Sensor implementations.
 */
public class TerrainCollisionManager {
	private static TerrainCollisionManager instance;

	// Pre-allocated result arrays to avoid per-frame allocations.
	// Max sensor count is 6 for player sprites (2 ground, 2 ceiling, 2 push).
	private static final int MAX_SENSORS = 6;
	private final SensorResult[] pooledResults = new SensorResult[MAX_SENSORS];

	/**
	 * Execute all sensors and return their results.
	 * NOTE: The returned array is reused between calls - callers must not
	 * store references to it across frames.
	 *
	 * @param sensors Array of sensors to scan (max 6)
	 * @return Array of results (parallel to input array, reused buffer)
	 */
	public SensorResult[] getSensorResult(Sensor[] sensors) {
		int count = Math.min(sensors.length, MAX_SENSORS);
		for (int i = 0; i < count; i++) {
			pooledResults[i] = sensors[i].scan();
		}
		// Clear unused slots to prevent stale data from previous calls
		for (int i = count; i < MAX_SENSORS; i++) {
			pooledResults[i] = null;
		}
		return pooledResults;
	}

	public static synchronized TerrainCollisionManager getInstance() {
		if (instance == null) {
			instance = new TerrainCollisionManager();
		}
		return instance;
	}
}
