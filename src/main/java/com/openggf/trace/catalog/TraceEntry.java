package com.openggf.trace.catalog;

import com.openggf.game.save.SelectedTeam;
import com.openggf.trace.TraceMetadata;

import java.nio.file.Path;

/**
 * One trace directory scanned by {@link TraceCatalog}. Constructed from
 * {@code metadata.json} + {@code physics.csv} row count + BK2 path.
 */
public record TraceEntry(
        Path dir,
        String gameId,
        int zone,
        int act,
        int frameCount,
        int bk2StartOffset,
        int preTraceOscFrames,
        SelectedTeam team,
        Path bk2Path,
        TraceMetadata metadata) {
}
