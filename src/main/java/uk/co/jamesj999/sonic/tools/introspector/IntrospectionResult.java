package uk.co.jamesj999.sonic.tools.introspector;

/**
 * A single discovered ROM address from an introspection chain.
 *
 * @param key        the address name (e.g. "LEVEL_DATA_DIR")
 * @param value      the ROM offset value
 * @param confidence how the address was determined ("traced", "pattern", "inferred")
 * @param traceLog   human-readable description of how the address was found
 */
public record IntrospectionResult(String key, int value, String confidence, String traceLog) {
}
