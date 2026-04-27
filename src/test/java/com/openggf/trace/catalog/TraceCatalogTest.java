package com.openggf.trace.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceCatalogTest {

    @Test
    void scanFiltersInvalidDirsAndSortsByGameZoneAct(@TempDir Path tmp) throws Exception {
        writeValidTrace(tmp.resolve("s3k/z_cnz"), "s3k", 11, 0);
        writeValidTrace(tmp.resolve("s1/ghz1"), "s1", 0, 0);
        writeValidTrace(tmp.resolve("s2/ehz1"), "s2", 0, 0);
        writeValidTrace(tmp.resolve("s3k/aiz1"), "s3k", 0, 0);
        Files.createDirectories(tmp.resolve("bogus"));            // missing files
        Files.createDirectories(tmp.resolve("synthetic/v3"));     // filtered by path

        List<TraceEntry> entries = TraceCatalog.scan(tmp);

        assertEquals(List.of("s1", "s2", "s3k", "s3k"),
                entries.stream().map(TraceEntry::gameId).toList());
        assertEquals(List.of(0, 0, 0, 11),
                entries.stream().map(TraceEntry::zone).toList());
    }

    @Test
    void scanSkipsDirWithMultipleBk2Files(@TempDir Path tmp) throws Exception {
        Path dir = tmp.resolve("s1/bad");
        writeValidTrace(dir, "s1", 0, 0);
        Files.writeString(dir.resolve("extra.bk2"), "extra");
        assertTrue(TraceCatalog.scan(tmp).isEmpty());
    }

    @Test
    void scanSkipsSyntheticSubtree(@TempDir Path tmp) throws Exception {
        writeValidTrace(tmp.resolve("synthetic/fake"), "s1", 0, 0);
        assertTrue(TraceCatalog.scan(tmp).isEmpty());
    }

    @Test
    void scanTranslatesS1RomZoneToProgressionIndex(@TempDir Path tmp) throws Exception {
        // Sonic 1 ROM zone IDs differ from the engine's progression
        // order: LZ=ROM 1 but progression 3, MZ=ROM 2 but progression 1.
        writeValidTrace(tmp.resolve("s1/mz1"), "s1", 2, 1); // MZ Act 1
        writeValidTrace(tmp.resolve("s1/lz3"), "s1", 1, 3); // LZ Act 3

        List<TraceEntry> entries = TraceCatalog.scan(tmp);

        // MZ progression=1 sorts before LZ progression=3.
        assertEquals(List.of(1, 3),
                entries.stream().map(TraceEntry::zone).toList());
        // Acts are 1-indexed in metadata → 0-indexed in TraceEntry.
        assertEquals(List.of(0, 2),
                entries.stream().map(TraceEntry::act).toList());
    }

    @Test
    void scanS2AndS3kUseIdentityZoneMapping(@TempDir Path tmp) throws Exception {
        writeValidTrace(tmp.resolve("s2/cpz1"), "s2", 4, 1); // CPZ Act 1
        writeValidTrace(tmp.resolve("s3k/cnz1"), "s3k", 3, 1); // CNZ Act 1

        List<TraceEntry> entries = TraceCatalog.scan(tmp);

        assertEquals(List.of(4, 3),
                entries.stream().map(TraceEntry::zone).toList());
        assertEquals(List.of(0, 0),
                entries.stream().map(TraceEntry::act).toList());
    }

    @Test
    void scanAcceptsGzippedPhysicsCsv(@TempDir Path tmp) throws Exception {
        Path dir = tmp.resolve("s3k/cnz1");
        writeValidTrace(dir, "s3k", 3, 1);
        String physics = Files.readString(dir.resolve("physics.csv"));
        Files.delete(dir.resolve("physics.csv"));
        try (var out = Files.newOutputStream(dir.resolve("physics.csv.gz"));
             var gzip = new java.util.zip.GZIPOutputStream(out)) {
            gzip.write(physics.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        List<TraceEntry> entries = TraceCatalog.scan(tmp);

        assertEquals(1, entries.size());
        assertEquals(2, entries.getFirst().frameCount());
    }

    private static void writeValidTrace(Path dir, String game, int zoneId, int act)
            throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("metadata.json"), String.format("""
            {
              "game": "%s",
              "zone": "ZONE",
              "zone_id": %d,
              "act": %d,
              "trace_schema": 3,
              "bk2_frame_offset": 100,
              "pre_trace_osc_frames": 12,
              "main_character": "sonic",
              "sidekicks": []
            }
            """, game, zoneId, act));
        Files.writeString(dir.resolve("physics.csv"),
                "0,0,0,0,0,0,0,0,0,0,0\n1,0,0,0,0,0,0,0,0,0,0\n");
        Files.writeString(dir.resolve("trace.bk2"), "stub");
    }
}
