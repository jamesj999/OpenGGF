package com.openggf.testmode;

import com.openggf.game.save.SelectedTeam;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.catalog.TraceEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link TestModeTracePicker}'s navigation helpers. The
 * picker's rendering and input paths need a live GL context, so these
 * only exercise the pure navigation logic.
 */
class TestModeTracePickerTest {

    @Test
    void nextGroupStartJumpsAcrossGames() {
        TestModeTracePicker picker = new TestModeTracePicker(
                entries("s1", "s1", "s2", "s2", "s3k", "s3k"), null);
        assertEquals(2, picker.nextGroupStart(0));  // s1-0 -> s2-0
        assertEquals(2, picker.nextGroupStart(1));  // s1-1 -> s2-0
        assertEquals(4, picker.nextGroupStart(2));  // s2-0 -> s3k-0
        assertEquals(4, picker.nextGroupStart(3));  // s2-1 -> s3k-0
        assertEquals(5, picker.nextGroupStart(5));  // last stays put
    }

    @Test
    void prevGroupStartJumpsToStartOfPreviousGroup() {
        TestModeTracePicker picker = new TestModeTracePicker(
                entries("s1", "s1", "s2", "s2", "s3k", "s3k"), null);
        // Bug fixed in review: from the middle of s3k, should jump to
        // s2-0 (the start of the preceding group), not s1-0.
        assertEquals(2, picker.prevGroupStart(4));  // s3k-0 -> s2-0
        assertEquals(2, picker.prevGroupStart(5));  // s3k-1 -> s2-0
        assertEquals(0, picker.prevGroupStart(2));  // s2-0 -> s1-0
        assertEquals(0, picker.prevGroupStart(3));  // s2-1 -> s1-0
        assertEquals(0, picker.prevGroupStart(0));  // first stays put
    }

    @Test
    void singleGroupPrevStaysAtZero() {
        TestModeTracePicker picker = new TestModeTracePicker(
                entries("s1", "s1", "s1"), null);
        assertEquals(0, picker.prevGroupStart(2));
        // nextGroupStart returns `from` when no following group exists.
        assertEquals(2, picker.nextGroupStart(2));
    }

    @Test
    void singleEntryNavigationIsStable() {
        TestModeTracePicker picker = new TestModeTracePicker(
                entries("s1"), null);
        assertEquals(0, picker.prevGroupStart(0));
        assertEquals(0, picker.nextGroupStart(0));
        assertEquals(0, picker.cursor());
    }

    @Test
    void emptyPickerStartsWithNoResult() {
        TestModeTracePicker picker = new TestModeTracePicker(List.of(), null);
        assertEquals(TestModeTracePicker.Result.NONE, picker.consumeResult());
    }

    private static List<TraceEntry> entries(String... gameIds) {
        SelectedTeam team = new SelectedTeam("sonic", List.of());
        return java.util.stream.IntStream.range(0, gameIds.length)
                .mapToObj(i -> new TraceEntry(
                        Path.of("entry-" + i),
                        gameIds[i],
                        0,
                        0,
                        0,
                        0,
                        0,
                        team,
                        Path.of("entry-" + i + ".bk2"),
                        metadataStub(gameIds[i])))
                .toList();
    }

    private static TraceMetadata metadataStub(String gameId) {
        return new TraceMetadata(
                gameId, "TEST", 0, 0, 0, 0,
                "0x0000", "0x0000",
                null, null, 3, null, null, null,
                "sonic", List.of(), 0, null, null, null, null, null);
    }
}
