package uk.co.jamesj999.sonic.game.sonic3k.constants;

/**
 * The locked-on S3K ROM uses two different object pointer tables that remap
 * many IDs depending on the current zone.
 *
 * <ul>
 *   <li><b>S3KL</b> — S3K-Level Object Set (disasm: "SK Set 1" / Sprite_Listing3):
 *       Zones 0-6 (AIZ, HCZ, MGZ, CNZ, FBZ, ICZ, LBZ) and competition zones (0x0E+)</li>
 *   <li><b>SKL</b> — SK-Level Object Set (disasm: "SK Set 2" / Sprite_ListingK):
 *       Zones 7-13 (MHZ, SOZ, LRZ, SSZ, DEZ, DDZ)</li>
 * </ul>
 *
 * <p>Selection logic from sonic3k.asm line 37411: purely zone-based, NOT game-mode-based.
 */
public enum S3kZoneSet {
    S3KL,  // S3K-Level Object Set: Zones 0-6 (AIZ, HCZ, MGZ, CNZ, FBZ, ICZ, LBZ)
    SKL;   // SK-Level Object Set: Zones 7-13 (MHZ, SOZ, LRZ, SSZ, DEZ, DDZ)

    public static S3kZoneSet forZone(int zone) {
        return (zone <= 6) ? S3KL : SKL;
    }
}
