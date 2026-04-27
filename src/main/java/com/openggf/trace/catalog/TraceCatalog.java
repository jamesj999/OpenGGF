package com.openggf.trace.catalog;

import com.openggf.game.save.SelectedTeam;
import com.openggf.trace.TraceMetadata;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Scans a traces root directory (default {@code src/test/resources/traces}) and
 * returns an immutable, sorted list of valid {@link TraceEntry} records.
 *
 * <p>A directory is a valid trace iff it contains {@code metadata.json},
 * {@code physics.csv} (or {@code physics.csv.gz}), and exactly one {@code .bk2}
 * file, and its metadata declares one of the supported games ({@code s1},
 * {@code s2}, {@code s3k}). The {@code synthetic/} subtree is always filtered
 * out.
 */
public final class TraceCatalog {
    private static final Logger LOGGER = Logger.getLogger(TraceCatalog.class.getName());
    private static final List<String> VALID_GAME_IDS = List.of("s1", "s2", "s3k");
    private static final Comparator<String> GAME_ORDER =
            Comparator.comparingInt(VALID_GAME_IDS::indexOf);

    private TraceCatalog() {
    }

    public static List<TraceEntry> scan(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<TraceEntry> entries = new ArrayList<>();
        // Depth 2 reaches `<root>/<game>/<trace-dir>` — the exact level
        // where every valid trace lives. Bounding the walk here avoids
        // multi-minute whole-project scans if TRACE_CATALOG_DIR is
        // misconfigured and resolves to the project root.
        try (Stream<Path> stream = Files.walk(root, 2)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> !isSyntheticSubtree(root, p))
                    .forEach(dir -> tryLoad(dir).ifPresent(entries::add));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not scan " + root, e);
        }
        entries.sort(Comparator
                .comparing(TraceEntry::gameId, GAME_ORDER)
                .thenComparingInt(TraceEntry::zone)
                .thenComparingInt(TraceEntry::act)
                .thenComparing(e -> e.dir().getFileName().toString()));
        return Collections.unmodifiableList(entries);
    }

    private static boolean isSyntheticSubtree(Path root, Path dir) {
        Path rel = root.relativize(dir);
        return rel.getNameCount() > 0
                && "synthetic".equals(rel.getName(0).toString());
    }

    private static Optional<TraceEntry> tryLoad(Path dir) {
        Path metaPath = dir.resolve("metadata.json");
        Path physicsPath = resolveTraceFile(dir, "physics.csv");
        if (!Files.isRegularFile(metaPath) || physicsPath == null) {
            return Optional.empty();
        }
        List<Path> bk2s;
        try (Stream<Path> s = Files.list(dir)) {
            bk2s = s.filter(p -> p.getFileName().toString().endsWith(".bk2")).toList();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "list() failed for " + dir, e);
            return Optional.empty();
        }
        if (bk2s.size() != 1) {
            return Optional.empty();
        }
        TraceMetadata meta;
        int frameCount;
        try {
            meta = TraceMetadata.load(metaPath);
            frameCount = countCsvRows(physicsPath);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Could not load trace at " + dir, e);
            return Optional.empty();
        }
        if (meta.game() == null || !VALID_GAME_IDS.contains(meta.game())) {
            return Optional.empty();
        }
        String main = meta.recordedMainCharacter();
        SelectedTeam team = new SelectedTeam(
                main == null ? "sonic" : main,
                meta.recordedSidekicks());
        int romZoneId = meta.zoneId() != null ? meta.zoneId() : 0;
        int engineZone = romZoneToProgressionIndex(meta.game(), romZoneId);
        // metadata "act" is 1-indexed (Act 1 == 1). Engine's
        // LevelManager.loadZoneAndAct expects 0-indexed acts.
        int engineAct = Math.max(0, meta.act() - 1);
        return Optional.of(new TraceEntry(
                dir,
                meta.game(),
                engineZone,
                engineAct,
                frameCount,
                meta.bk2FrameOffset(),
                meta.preTraceOscillationFrames(),
                team,
                bk2s.get(0),
                meta));
    }

    /**
     * Convert a metadata ROM zone id into the engine's progression
     * zone index. For S2 and S3K these coincide, but S1 stores zones
     * in ROM order (GHZ=0, LZ=1, MZ=2, SLZ=3, SYZ=4, SBZ=5, FZ=6)
     * while the engine's {@code Sonic1ZoneRegistry} uses play order
     * (GHZ=0, MZ=1, SYZ=2, LZ=3, SLZ=4, SBZ=5, FZ=6).
     */
    private static int romZoneToProgressionIndex(String gameId, int romZoneId) {
        if ("s1".equals(gameId)) {
            return switch (romZoneId) {
                case 0 -> 0;  // GHZ
                case 1 -> 3;  // LZ
                case 2 -> 1;  // MZ
                case 3 -> 4;  // SLZ
                case 4 -> 2;  // SYZ
                case 5 -> 5;  // SBZ
                case 6 -> 6;  // FZ
                default -> romZoneId;
            };
        }
        // S2 and S3K already use progression ordering in their
        // recorded zone_id.
        return romZoneId;
    }

    private static int countCsvRows(Path physicsCsv) throws IOException {
        try (BufferedReader reader = openTraceReader(physicsCsv)) {
            int count = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    count++;
                }
            }
            return count;
        }
    }

    private static Path resolveTraceFile(Path dir, String fileName) {
        Path plain = dir.resolve(fileName);
        if (Files.isRegularFile(plain)) {
            return plain;
        }
        Path gzip = dir.resolve(fileName + ".gz");
        return Files.isRegularFile(gzip) ? gzip : null;
    }

    private static BufferedReader openTraceReader(Path path) throws IOException {
        if (path.getFileName().toString().endsWith(".gz")) {
            InputStream input = Files.newInputStream(path);
            try {
                return new BufferedReader(new InputStreamReader(
                        new GZIPInputStream(input), StandardCharsets.UTF_8));
            } catch (IOException e) {
                input.close();
                throw e;
            }
        }
        return Files.newBufferedReader(path);
    }
}
