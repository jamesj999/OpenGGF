package com.openggf.game;

/**
 * Identifies a specific Sonic game for cross-game resource isolation
 * (e.g., palette contexts, art providers).
 */
public enum GameId {
    S1("s1"),
    S2("s2"),
    S3K("s3k");

    private final String code;

    GameId(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static GameId fromCode(String code) {
        for (GameId id : values()) {
            if (id.code.equalsIgnoreCase(code)) {
                return id;
            }
        }
        throw new IllegalArgumentException("Unknown game: " + code);
    }
}
