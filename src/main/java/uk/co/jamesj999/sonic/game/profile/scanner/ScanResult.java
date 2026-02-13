package uk.co.jamesj999.sonic.game.profile.scanner;

/**
 * The result of a successful pattern scan: the value read from the ROM
 * and the ROM offset where the pattern was found.
 *
 * @param value         the address value read at the match location
 * @param foundAtOffset the ROM offset where the signature matched
 */
public record ScanResult(int value, int foundAtOffset) {
}
