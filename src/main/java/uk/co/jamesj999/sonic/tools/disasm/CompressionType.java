package uk.co.jamesj999.sonic.tools.disasm;

public enum CompressionType {
    NEMESIS(".nem", "Nemesis"),
    KOSINSKI(".kos", "Kosinski"),
    KOSINSKI_MODULED(".kosm", "Kosinski Moduled"),
    ENIGMA(".eni", "Enigma"),
    SAXMAN(".sax", "Saxman"),
    UNCOMPRESSED(".bin", "Uncompressed"),
    ASSEMBLY_DATA(".asm", "Assembly Data"),
    UNKNOWN("", "Unknown");

    private final String extension;
    private final String displayName;

    CompressionType(String extension, String displayName) {
        this.extension = extension;
        this.displayName = displayName;
    }

    public String getExtension() {
        return extension;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static CompressionType fromExtension(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".nem")) return NEMESIS;
        if (lower.endsWith(".kosm")) return KOSINSKI_MODULED;
        if (lower.endsWith(".kos")) return KOSINSKI;
        if (lower.endsWith(".eni")) return ENIGMA;
        if (lower.endsWith(".sax")) return SAXMAN;
        if (lower.endsWith(".bin")) return UNCOMPRESSED;
        if (lower.endsWith(".asm")) return ASSEMBLY_DATA;
        return UNKNOWN;
    }

    /**
     * Infer compression type from label suffix when the file extension is ambiguous.
     * S3K uses .bin for many compressed files, encoding the type in the label
     * (e.g., AIZ1_8x8_Primary_KosM, ArtNem_TitleScreenText).
     *
     * @param label    The disassembly label (may be null)
     * @param filePath The file path to check extension
     * @return The inferred compression type
     */
    public static CompressionType fromLabelAndExtension(String label, String filePath) {
        CompressionType fromExt = fromExtension(filePath);
        if (fromExt != UNCOMPRESSED || label == null) return fromExt;

        String upper = label.toUpperCase();
        if (upper.endsWith("_KOSM")) return KOSINSKI_MODULED;
        if (upper.endsWith("_KOS")) return KOSINSKI;
        if (upper.endsWith("_NEM")) return NEMESIS;
        if (upper.endsWith("_ENI")) return ENIGMA;
        if (upper.endsWith("_SAX")) return SAXMAN;

        // Also check label prefixes for S3K patterns like ArtKosM_, ArtNem_, ArtKos_
        if (upper.startsWith("ARTKOSM_")) return KOSINSKI_MODULED;
        if (upper.startsWith("ARTNEM_")) return NEMESIS;
        if (upper.startsWith("ARTKOS_")) return KOSINSKI;

        return fromExt;
    }
}

