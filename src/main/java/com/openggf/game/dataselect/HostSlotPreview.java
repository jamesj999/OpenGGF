package com.openggf.game.dataselect;

/**
 * Preview metadata for a save slot on the donated S3K data select screen.
 * Host profiles (S1/S2) provide this so the S3K presentation can render
 * host-appropriate zone labels and preview art instead of S3K zone data.
 *
 * @param type the preview rendering mode
 * @param zoneLabelText short label for the zone (e.g. "GHZ", "EHZ 1"), max ~6 chars;
 *                       must only use characters supported by {@code S3kSaveTextCodec}
 */
public record HostSlotPreview(HostSlotPreviewType type, String zoneLabelText) {

    public enum HostSlotPreviewType {
        /** Render zone name as text only (no zone card image). */
        TEXT_ONLY,
        /** Render a scaled host-game image (reserved for future use). */
        IMAGE
    }
}
