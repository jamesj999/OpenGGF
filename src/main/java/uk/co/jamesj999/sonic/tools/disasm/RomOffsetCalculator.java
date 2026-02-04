package uk.co.jamesj999.sonic.tools.disasm;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calculates ROM offsets for disassembly items by using known anchor offsets
 * and summing file sizes in assembly order.
 *
 * The s2disasm assembles files sequentially, so we can calculate any offset if we know:
 * 1. A nearby anchor offset (from Sonic2SpecialStageConstants or similar)
 * 2. The file sizes between the anchor and target
 */
public class RomOffsetCalculator {

    private static final Pattern BINCLUDE_PATTERN = Pattern.compile(
            "^\\s*(\\w+):\\s*(?:BINCLUDE|binclude)\\s+\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BINCLUDE_NO_LABEL_PATTERN = Pattern.compile(
            "^\\s*(?:BINCLUDE|binclude)\\s+\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ALIGN_PATTERN = Pattern.compile(
            "^\\s*align\\s+(\\$?[0-9A-Fa-f]+)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EVEN_PATTERN = Pattern.compile(
            "^\\s*even\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern for palette macro: "Label: palette path[,path2] [; comment]"
    // The macro expands to BINCLUDE "art/palettes/{path}"
    private static final Pattern PALETTE_PATTERN = Pattern.compile(
            "^\\s*(\\w+):\\s*palette\\s+([^,;]+?)(?:\\s*,\\s*([^;]+?))?(?:\\s*;.*)?\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Known anchor offsets from verified ROM locations.
     * These are used as starting points for offset calculation.
     */
    private static final Map<String, Long> ANCHOR_OFFSETS = new LinkedHashMap<>();
    static {
        // Special stage art anchors (verified)
        ANCHOR_OFFSETS.put("ArtNem_SpecialBack", 0x0DCD68L);
        ANCHOR_OFFSETS.put("ArtNem_SpecialHUD", 0x0DD48AL);
        ANCHOR_OFFSETS.put("ArtNem_SpecialStart", 0x0DD790L);
        ANCHOR_OFFSETS.put("ArtNem_SpecialRings", 0x0DDA7EL);
        ANCHOR_OFFSETS.put("ArtNem_SpecialFlatShadow", 0x0DDFA4L);
        ANCHOR_OFFSETS.put("ArtNem_SpecialDiagShadow", 0x0DE05AL);
        ANCHOR_OFFSETS.put("ArtNem_SpecialSideShadow", 0x0DE120L);
        ANCHOR_OFFSETS.put("ArtNem_SpecialBomb", 0x0DE4BCL);
        ANCHOR_OFFSETS.put("ArtNem_SpecialEmerald", 0x0DE8ACL);
        ANCHOR_OFFSETS.put("ArtNem_SpecialMessages", 0x0DEAF4L);
        ANCHOR_OFFSETS.put("ArtNem_SpecialSonicAndTails", 0x0DEEAEL);

        // Track data anchors
        ANCHOR_OFFSETS.put("ArtKos_SpecialStage", 0x0DCA38L);

        // Palette anchors (verified)
        ANCHOR_OFFSETS.put("Pal_Result", 0x3302L);  // Special Stage Results Screen palette
    }

    private final Path disasmRoot;
    private List<BincludeEntry> orderedEntries;

    /**
     * Runtime-discovered anchors from verified offsets.
     * These supplement the static ANCHOR_OFFSETS map.
     */
    private final Map<String, Long> runtimeAnchors = new LinkedHashMap<>();

    public RomOffsetCalculator(Path disasmRoot) {
        this.disasmRoot = disasmRoot;
    }

    public RomOffsetCalculator(String disasmRootPath) {
        this(Path.of(disasmRootPath));
    }

    /**
     * Add a verified anchor offset discovered at runtime.
     * This helps improve offset calculation accuracy for nearby items.
     *
     * @param label The label to add as anchor
     * @param offset The verified ROM offset
     */
    public void addVerifiedAnchor(String label, long offset) {
        // Don't override static anchors
        if (!ANCHOR_OFFSETS.containsKey(label)) {
            runtimeAnchors.put(label, offset);
        }
    }

    /**
     * Get all anchors (static + runtime).
     */
    public Map<String, Long> getAllAnchors() {
        Map<String, Long> all = new LinkedHashMap<>(ANCHOR_OFFSETS);
        all.putAll(runtimeAnchors);
        return Collections.unmodifiableMap(all);
    }

    /**
     * Get only runtime-discovered anchors.
     */
    public Map<String, Long> getRuntimeAnchors() {
        return Collections.unmodifiableMap(runtimeAnchors);
    }

    /**
     * Clear all runtime anchors.
     */
    public void clearRuntimeAnchors() {
        runtimeAnchors.clear();
    }

    /**
     * Check if a label is any anchor (static or runtime).
     */
    private boolean isAnyAnchor(String label) {
        return ANCHOR_OFFSETS.containsKey(label) || runtimeAnchors.containsKey(label);
    }

    /**
     * Get anchor offset (static or runtime).
     */
    private long getAnyAnchorOffset(String label) {
        if (ANCHOR_OFFSETS.containsKey(label)) {
            return ANCHOR_OFFSETS.get(label);
        }
        return runtimeAnchors.getOrDefault(label, -1L);
    }

    /**
     * Calculate the ROM offset for a label.
     *
     * @param label The label to find (e.g., "ArtNem_SpecialStars")
     * @return The ROM offset, or -1 if not found
     */
    public long calculateOffset(String label) throws IOException {
        ensureEntriesLoaded();

        // First check if this label is a known anchor (static or runtime)
        long anchorOffset = getAnyAnchorOffset(label);
        if (anchorOffset >= 0) {
            return anchorOffset;
        }

        // Find the target entry
        int targetIndex = -1;
        for (int i = 0; i < orderedEntries.size(); i++) {
            if (label.equals(orderedEntries.get(i).label)) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex < 0) {
            return -1; // Label not found
        }

        // Find nearest anchor (prefer before, then after)
        int anchorIndex = -1;
        String anchorLabel = null;

        // Search backwards for nearest anchor (check both static and runtime)
        for (int i = targetIndex - 1; i >= 0; i--) {
            String entryLabel = orderedEntries.get(i).label;
            if (isAnyAnchor(entryLabel)) {
                anchorIndex = i;
                anchorLabel = entryLabel;
                anchorOffset = getAnyAnchorOffset(entryLabel);
                break;
            }
        }

        // If no anchor before, search forwards
        if (anchorIndex < 0) {
            for (int i = targetIndex + 1; i < orderedEntries.size(); i++) {
                String entryLabel = orderedEntries.get(i).label;
                if (isAnyAnchor(entryLabel)) {
                    anchorIndex = i;
                    anchorLabel = entryLabel;
                    anchorOffset = getAnyAnchorOffset(entryLabel);
                    break;
                }
            }
        }

        if (anchorIndex < 0) {
            return -1; // No anchor found
        }

        // Calculate offset by summing file sizes
        long offset = anchorOffset;

        if (anchorIndex < targetIndex) {
            // Anchor is before target - add file sizes
            for (int i = anchorIndex; i < targetIndex; i++) {
                BincludeEntry entry = orderedEntries.get(i);
                if (entry.isAlignmentEntry()) {
                    offset = alignOffset(offset, entry.alignment);
                    continue;
                }
                long fileSize = getFileSize(entry.filePath);
                if (fileSize < 0) {
                    return -1; // File not found
                }
                offset += fileSize;
                // Align to even boundary (MC68000 requirement)
                if (offset % 2 != 0) {
                    offset++;
                }
            }
        } else {
            // Anchor is after target - subtract file sizes
            for (int i = anchorIndex - 1; i >= targetIndex; i--) {
                BincludeEntry entry = orderedEntries.get(i);
                if (entry.isAlignmentEntry()) {
                    if (entry.alignment > 2) {
                        return -1; // Cannot reliably reverse non-even alignment
                    }
                    continue;
                }
                long fileSize = getFileSize(entry.filePath);
                if (fileSize < 0) {
                    return -1; // File not found
                }
                // Account for alignment padding
                long alignedSize = fileSize;
                if (alignedSize % 2 != 0) {
                    alignedSize++;
                }
                offset -= alignedSize;
            }
        }

        return offset;
    }

    /**
     * Get offset calculation details for debugging.
     */
    public OffsetCalculation getCalculationDetails(String label) throws IOException {
        ensureEntriesLoaded();

        // Check if this is any anchor (static or runtime)
        if (isAnyAnchor(label)) {
            return new OffsetCalculation(label, getAnyAnchorOffset(label), label, 0, true);
        }

        int targetIndex = -1;
        for (int i = 0; i < orderedEntries.size(); i++) {
            if (label.equals(orderedEntries.get(i).label)) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex < 0) {
            return null;
        }

        // Find nearest anchor (static or runtime)
        int anchorIndex = -1;
        String anchorLabel = null;

        for (int i = targetIndex - 1; i >= 0; i--) {
            String entryLabel = orderedEntries.get(i).label;
            if (isAnyAnchor(entryLabel)) {
                anchorIndex = i;
                anchorLabel = entryLabel;
                break;
            }
        }

        if (anchorIndex < 0) {
            for (int i = targetIndex + 1; i < orderedEntries.size(); i++) {
                String entryLabel = orderedEntries.get(i).label;
                if (isAnyAnchor(entryLabel)) {
                    anchorIndex = i;
                    anchorLabel = entryLabel;
                    break;
                }
            }
        }

        if (anchorIndex < 0) {
            return null;
        }

        long offset = calculateOffset(label);
        int distance = Math.abs(targetIndex - anchorIndex);

        return new OffsetCalculation(label, offset, anchorLabel, distance, false);
    }

    private void ensureEntriesLoaded() throws IOException {
        if (orderedEntries == null) {
            orderedEntries = parseS2Asm();
        }
    }

    private List<BincludeEntry> parseS2Asm() throws IOException {
        List<BincludeEntry> entries = new ArrayList<>();
        Path s2asm = disasmRoot.resolve("s2.asm");

        if (!Files.exists(s2asm)) {
            return entries;
        }

        try (BufferedReader reader = Files.newBufferedReader(s2asm)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                Matcher matcher = BINCLUDE_PATTERN.matcher(line);
                if (matcher.find()) {
                    entries.add(new BincludeEntry(
                            matcher.group(1),
                            matcher.group(2),
                            lineNumber
                    ));
                    continue;
                }

                Matcher noLabelMatcher = BINCLUDE_NO_LABEL_PATTERN.matcher(line);
                if (noLabelMatcher.find()) {
                    entries.add(new BincludeEntry(
                            null,
                            noLabelMatcher.group(1),
                            lineNumber
                    ));
                    continue;
                }

                Matcher alignMatcher = ALIGN_PATTERN.matcher(line);
                if (alignMatcher.find()) {
                    int alignment = parseAlignment(alignMatcher.group(1));
                    if (alignment > 1) {
                        entries.add(BincludeEntry.alignment(alignment, lineNumber));
                    }
                    continue;
                }

                Matcher evenMatcher = EVEN_PATTERN.matcher(line);
                if (evenMatcher.find()) {
                    entries.add(BincludeEntry.alignment(2, lineNumber));
                    continue;
                }

                // Check for palette macro
                Matcher paletteMatcher = PALETTE_PATTERN.matcher(line);
                if (paletteMatcher.find()) {
                    String label = paletteMatcher.group(1);
                    String path1 = paletteMatcher.group(2).trim();
                    String path2 = paletteMatcher.group(3) != null ? paletteMatcher.group(3).trim() : null;

                    // First palette file
                    entries.add(new BincludeEntry(label, "art/palettes/" + path1, lineNumber));

                    // Second palette file (if present) - same label, follows immediately
                    if (path2 != null && !path2.isEmpty()) {
                        entries.add(new BincludeEntry(label + "_2", "art/palettes/" + path2, lineNumber));
                    }
                }
            }
        }

        return entries;
    }

    private long getFileSize(String relativePath) {
        try {
            Path path = disasmRoot.resolve(relativePath);
            if (Files.exists(path)) {
                return Files.size(path);
            }
        } catch (IOException e) {
            // Ignore
        }
        return -1;
    }

    /**
     * Check if a label is a known anchor.
     */
    public static boolean isKnownAnchor(String label) {
        return ANCHOR_OFFSETS.containsKey(label);
    }

    /**
     * Get all known anchor offsets.
     */
    public static Map<String, Long> getKnownAnchors() {
        return Collections.unmodifiableMap(ANCHOR_OFFSETS);
    }

    private static class BincludeEntry {
        final String label;
        final String filePath;
        final int lineNumber;
        final int alignment;

        BincludeEntry(String label, String filePath, int lineNumber) {
            this.label = label;
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.alignment = 0;
        }

        private BincludeEntry(int alignment, int lineNumber) {
            this.label = null;
            this.filePath = null;
            this.lineNumber = lineNumber;
            this.alignment = alignment;
        }

        static BincludeEntry alignment(int alignment, int lineNumber) {
            return new BincludeEntry(alignment, lineNumber);
        }

        boolean isAlignmentEntry() {
            return alignment > 0;
        }
    }

    private static int parseAlignment(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("$")) {
            return Integer.parseInt(trimmed.substring(1), 16);
        }
        if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
            return Integer.parseInt(trimmed.substring(2), 16);
        }
        return Integer.parseInt(trimmed, 10);
    }

    private static long alignOffset(long offset, int alignment) {
        if (alignment <= 1) {
            return offset;
        }
        long mask = alignment - 1L;
        return (offset + mask) & ~mask;
    }

    /**
     * Details about how an offset was calculated.
     */
    public static class OffsetCalculation {
        public final String label;
        public final long offset;
        public final String anchorLabel;
        public final int distanceFromAnchor;
        public final boolean isAnchor;

        OffsetCalculation(String label, long offset, String anchorLabel,
                         int distanceFromAnchor, boolean isAnchor) {
            this.label = label;
            this.offset = offset;
            this.anchorLabel = anchorLabel;
            this.distanceFromAnchor = distanceFromAnchor;
            this.isAnchor = isAnchor;
        }
    }
}
