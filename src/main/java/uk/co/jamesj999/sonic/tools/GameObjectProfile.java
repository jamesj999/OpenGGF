package uk.co.jamesj999.sonic.tools;

import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.tools.ObjectDiscoveryTool.DynamicBoss;
import uk.co.jamesj999.sonic.tools.ObjectDiscoveryTool.LevelConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Game-specific data profile for the ObjectDiscoveryTool.
 * Each game (S1, S2, S3K) provides its own implementation.
 */
public interface GameObjectProfile {

    /** Human-readable game name for report title. */
    String gameName();

    /** Short game ID (s1, s2, s3k). */
    String gameId();

    /** Default ROM filename. */
    String defaultRomPath();

    /** Output markdown filename. */
    String outputFilename();

    /** Ordered list of levels to scan. */
    List<LevelConfig> getLevels();

    /** Object IDs that have factory or manager-based implementations. */
    Set<Integer> getImplementedIds();

    /** Object IDs classified as badniks. */
    Set<Integer> getBadnikIds();

    /** Object IDs classified as bosses. */
    Set<Integer> getBossIds();

    /** Bosses spawned dynamically (not in placement data), keyed by zone short name. */
    Map<String, List<DynamicBoss>> getDynamicBosses();

    /** Object name lookup: ID -> [primary name, alias1, alias2, ...]. */
    Map<Integer, List<String>> getObjectNames();

    /** Load object placements for the given level. */
    List<ObjectSpawn> loadObjects(RomByteReader rom, LevelConfig level);

    /** Whether this level is the final act of its zone (for dynamic boss display). */
    boolean isFinalAct(LevelConfig level);
}
