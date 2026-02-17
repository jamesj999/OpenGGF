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
 * The disassembly assembles files sequentially, so we can calculate any offset if we know:
 * 1. A nearby anchor offset (from verified constants)
 * 2. The file sizes between the anchor and target
 */
public class RomOffsetCalculator {

    private static final Pattern BINCLUDE_PATTERN = Pattern.compile(
            "^\\s*(\\w+):\\s*(?:BINCLUDE|binclude(?:Palette)?)\\s+\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BINCLUDE_NO_LABEL_PATTERN = Pattern.compile(
            "^\\s*(?:BINCLUDE|binclude(?:Palette)?)\\s+\"([^\"]+)\"",
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
    // Label on its own line (for multiline label+binclude resolution in S3K)
    private static final Pattern STANDALONE_LABEL_PATTERN = Pattern.compile(
            "^(\\w+):\\s*$"
    );

    // Pattern for S2 palette macro: "Label: palette path[,path2] [; comment]"
    // The macro expands to BINCLUDE "art/palettes/{path}"
    private static final Pattern PALETTE_PATTERN = Pattern.compile(
            "^\\s*(\\w+):\\s*palette\\s+([^,;]+?)(?:\\s*,\\s*([^;]+?))?(?:\\s*;.*)?\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private final Path disasmRoot;
    private final RomOffsetFinder.GameProfile profile;
    private final Map<String, Long> anchorOffsets;
    private List<BincludeEntry> orderedEntries;

    /**
     * Runtime-discovered anchors from verified offsets.
     * These supplement the profile anchor offsets.
     */
    private final Map<String, Long> runtimeAnchors = new LinkedHashMap<>();

    public RomOffsetCalculator(Path disasmRoot) {
        this(disasmRoot, null);
    }

    public RomOffsetCalculator(String disasmRootPath) {
        this(Path.of(disasmRootPath), null);
    }

    public RomOffsetCalculator(String disasmRootPath, RomOffsetFinder.GameProfile profile) {
        this(Path.of(disasmRootPath), profile);
    }

    public RomOffsetCalculator(Path disasmRoot, RomOffsetFinder.GameProfile profile) {
        this.disasmRoot = disasmRoot;
        this.profile = profile;
        this.anchorOffsets = profile != null
                ? new LinkedHashMap<>(profile.anchorOffsets())
                : defaultS2Anchors();
    }

    private static Map<String, Long> defaultS2Anchors() {
        return RomOffsetFinder.GameProfile.sonic2().anchorOffsets();
    }

    private boolean hasPaletteMacro() {
        return profile == null || profile.paletteDirPrefix() != null;
    }

    private String mainAsmFile() {
        return profile != null ? profile.mainAsmFile() : "s2.asm";
    }

    /**
     * Add a verified anchor offset discovered at runtime.
     * This helps improve offset calculation accuracy for nearby items.
     *
     * @param label The label to add as anchor
     * @param offset The verified ROM offset
     */
    public void addVerifiedAnchor(String label, long offset) {
        // Don't override profile anchors
        if (!anchorOffsets.containsKey(label)) {
            runtimeAnchors.put(label, offset);
        }
    }

    /**
     * Get all anchors (profile + runtime).
     */
    public Map<String, Long> getAllAnchors() {
        Map<String, Long> all = new LinkedHashMap<>(anchorOffsets);
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
     * Check if a label is any anchor (profile or runtime).
     */
    private boolean isAnyAnchor(String label) {
        return anchorOffsets.containsKey(label) || runtimeAnchors.containsKey(label);
    }

    /**
     * Get anchor offset (profile or runtime).
     */
    private long getAnyAnchorOffset(String label) {
        if (anchorOffsets.containsKey(label)) {
            return anchorOffsets.get(label);
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
            orderedEntries = parseMainAsm();
        }
    }

    private List<BincludeEntry> parseMainAsm() throws IOException {
        List<BincludeEntry> entries = new ArrayList<>();
        Path s2asm = disasmRoot.resolve(mainAsmFile());

        if (!Files.exists(s2asm)) {
            return entries;
        }

        try (BufferedReader reader = Files.newBufferedReader(s2asm)) {
            String line;
            int lineNumber = 0;
            String pendingLabel = null;
            int pendingLabelLine = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // 1. Same-line label+binclude
                Matcher matcher = BINCLUDE_PATTERN.matcher(line);
                if (matcher.find()) {
                    pendingLabel = null;
                    entries.add(new BincludeEntry(
                            matcher.group(1),
                            matcher.group(2),
                            lineNumber
                    ));
                    continue;
                }

                // 2. Unlabeled binclude — associate with pendingLabel if available
                Matcher noLabelMatcher = BINCLUDE_NO_LABEL_PATTERN.matcher(line);
                if (noLabelMatcher.find()) {
                    String resolvedLabel = pendingLabel;
                    pendingLabel = null;
                    entries.add(new BincludeEntry(
                            resolvedLabel,
                            noLabelMatcher.group(1),
                            resolvedLabel != null ? pendingLabelLine : lineNumber
                    ));
                    continue;
                }

                // 3. Standalone label — store as pending for next binclude
                Matcher standaloneMatcher = STANDALONE_LABEL_PATTERN.matcher(line);
                if (standaloneMatcher.find()) {
                    pendingLabel = standaloneMatcher.group(1);
                    pendingLabelLine = lineNumber;
                    continue;
                }

                // 4. Alignment directives — don't clear pendingLabel (can appear between label and binclude)
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

                // 5. Any other line — clear pendingLabel
                pendingLabel = null;

                // Check for S2 palette macro (S1 uses bincludePalette, caught by BINCLUDE regex)
                if (hasPaletteMacro()) {
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
     * Check if a label is a known anchor (profile anchor).
     */
    public boolean isKnownAnchor(String label) {
        return anchorOffsets.containsKey(label);
    }

    /**
     * Get all known anchor offsets (profile anchors).
     */
    public Map<String, Long> getKnownAnchors() {
        return Collections.unmodifiableMap(anchorOffsets);
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
