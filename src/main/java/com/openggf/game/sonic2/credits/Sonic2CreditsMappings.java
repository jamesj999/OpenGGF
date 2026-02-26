package com.openggf.game.sonic2.credits;

import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprite mapping definitions for Sonic 2 ending credits text.
 * <p>
 * Transcribed from {@code docs/s2disasm/s2.asm} (EndgameCredits / ShowCreditsScreen).
 * Each frame renders one credit screen. Tile indices reference the credit text font
 * (Nemesis art at {@code ArtNem_CreditText}).
 * <p>
 * The S2 credit font uses a <b>2-wide character encoding</b> where each character
 * is 2 tiles wide x 2 tiles tall (16x16 px). The font's tile layout uses TWO charset
 * tables ({@code charset2}): each character maps to two tile indices (left column,
 * right column), and each column is 1 tile wide x 2 tiles tall.
 * <p>
 * Frame assignments correspond to the 21 credit screens (0-20).
 */
public final class Sonic2CreditsMappings {

    private Sonic2CreditsMappings() {}

    /** Screen center X used as origin for drawFrameIndex. */
    private static final int CENTER_X = 160;
    /** Screen center Y used as origin for drawFrameIndex. */
    private static final int CENTER_Y = 112;

    /**
     * Character-to-tile mapping for the S2 credit text font ({@code charset2}).
     * Each entry is {@code {leftTile, rightTile}} where each tile is 1 wide x 2 tall.
     * <p>
     * From s2.asm:
     * <pre>
     * charset2 '`',"\x15\x17\x19\x1B\x1D\x1F\x21\x23\x25\x27\x29\x2B\x2D\x2F\x31\x33\x35\x37\x39\x3B\x3D\x3F\x41\x43\x45\x47"
     * charset2 'a',"\3\5\7\9\xB\xD\xF\x11\x12\x14\x16\x18\x1A\x1C\x1E\x20\x22\x24\x26\x28\x2A\x2C\x2E\x30\x32\x34"
     * </pre>
     */
    private static final int[][] CHAR_TILES;

    static {
        // 26 letters A-Z, plus digits and special chars
        // Index 0-25 = A-Z
        CHAR_TILES = new int[128][];
        // A-Z: left column from charset2 '`' (backtick=A), right column from charset2 'a'
        int[] leftTiles =  {0x15,0x17,0x19,0x1B,0x1D,0x1F,0x21,0x23,0x25,0x27,0x29,0x2B,0x2D,0x2F,0x31,0x33,0x35,0x37,0x39,0x3B,0x3D,0x3F,0x41,0x43,0x45,0x47};
        int[] rightTiles = {0x03,0x05,0x07,0x09,0x0B,0x0D,0x0F,0x11,0x12,0x14,0x16,0x18,0x1A,0x1C,0x1E,0x20,0x22,0x24,0x26,0x28,0x2A,0x2C,0x2E,0x30,0x32,0x34};
        for (int i = 0; i < 26; i++) {
            CHAR_TILES['A' + i] = new int[]{leftTiles[i], rightTiles[i]};
        }
        // Digits 0-9 from charset2 '0'-'9'
        CHAR_TILES['0'] = new int[]{0x02, 0x03};
        CHAR_TILES['1'] = new int[]{0x04, 0x05};
        CHAR_TILES['2'] = new int[]{0x06, 0x07};
        CHAR_TILES['3'] = new int[]{0x08, 0x09};
        CHAR_TILES['4'] = new int[]{0x0A, 0x0B};
        CHAR_TILES['5'] = new int[]{0x0C, 0x0D};
        CHAR_TILES['6'] = new int[]{0x0E, 0x0F};
        CHAR_TILES['7'] = new int[]{0x10, 0x11};
        CHAR_TILES['8'] = new int[]{0x12, 0x13};
        CHAR_TILES['9'] = new int[]{0x14, 0x15};
        // Period (used in "S.O", "N.GEE")
        CHAR_TILES['.'] = new int[]{0x3A, 0x34};
        // Copyright symbol '@' (used in "(@1992" as copyright C)
        CHAR_TILES['@'] = new int[]{0x3A, 0x34};
        // Parentheses
        CHAR_TILES['('] = new int[]{0x36, 0x34};
        CHAR_TILES[')'] = new int[]{0x38, 0x34};
        // Apostrophe (single tick)
        CHAR_TILES['\''] = new int[]{0x38, 0x34};
    }

    /**
     * Creates all 21 credit text mapping frames.
     *
     * @return list of frames indexed 0-20
     */
    public static List<SpriteMappingFrame> createFrames() {
        List<SpriteMappingFrame> frames = new ArrayList<>(21);

        // Screen 0: SONIC 2 / CAST OF CHARACTERS
        // ROM: off_B322, creditText strings use double spaces between words
        frames.add(buildScreen(
                text("SONIC", 14, 11, 0),
                text("2", 24, 11, 1),
                text("CAST  OF  CHARACTERS", 2, 15, 0)
        ));

        // Screen 1: EXECUTIVE PRODUCER / HAYAO NAKAYAMA
        frames.add(buildScreen(
                text("EXECUTIVE", 3, 11, 1),
                text("PRODUCER", 22, 11, 1),
                text("HAYAO  NAKAYAMA", 6, 15, 0)
        ));

        // Screen 2: PRODUCER / SHINOBU TOYODA
        frames.add(buildScreen(
                text("PRODUCER", 12, 11, 1),
                text("SHINOBU  TOYODA", 7, 15, 0)
        ));

        // Screen 3: DIRECTOR / MASAHARU YOSHII
        frames.add(buildScreen(
                text("DIRECTOR", 12, 11, 1),
                text("MASAHARU  YOSHII", 6, 15, 0)
        ));

        // Screen 4: CHIEF PROGRAMMER / YUJI NAKA (YU2)
        frames.add(buildScreen(
                text("CHIEF  PROGRAMMER", 5, 11, 1),
                text("YUJI  NAKA (YU2)", 7, 15, 0)
        ));

        // Screen 5: GAME PLANNER / HIROKAZU YASUHARA / (CAROL YAS)
        frames.add(buildScreen(
                text("GAME  PLANNER", 8, 10, 1),
                text("HIROKAZU  YASUHARA", 4, 14, 0),
                text("(CAROL  YAS)", 10, 16, 0)
        ));

        // Screen 6: CHARACTER DESIGN / AND / CHIEF ARTIST / YASUSHI YAMAGUCHI / (JUDY TOTOYA)
        frames.add(buildScreen(
                text("CHARACTER  DESIGN", 4, 8, 1),
                text("AND", 17, 10, 1),
                text("CHIEF  ARTIST", 9, 12, 1),
                text("YASUSHI  YAMAGUCHI", 4, 16, 0),
                text("(JUDY  TOTOYA)", 8, 18, 0)
        ));

        // Screen 7: ASSISTANT / PROGRAMMERS / BILL WILLIS / MASANOBU YAMAMOTO
        frames.add(buildScreen(
                text("ASSISTANT", 11, 9, 1),
                text("PROGRAMMERS", 9, 11, 1),
                text("BILL  WILLIS", 10, 15, 0),
                text("MASANOBU  YAMAMOTO", 3, 17, 0)
        ));

        // Screen 8: OBJECT PLACEMENT / HIROKAZU YASUHARA / TAKAHIRO ANTO / YUTAKA SUGANO
        frames.add(buildScreen(
                text("OBJECT  PLACEMENT", 4, 9, 1),
                text("HIROKAZU  YASUHARA", 4, 13, 0),
                text("TAKAHIRO  ANTO", 7, 15, 0),
                text("YUTAKA  SUGANO", 7, 17, 0)
        ));

        // Screen 9: SPECIALSTAGE / OBJECT PLACEMENT / YUTAKA SUGANO
        // Note: "SPECIALSTAGE" has no space in ROM (creditText: "SPECIALSTAGE")
        // Note: "OBJECT  PLACEMENT" reuses byte_B75C which is creditText 1 (role palette)
        frames.add(buildScreen(
                text("SPECIALSTAGE", 8, 10, 1),
                text("OBJECT  PLACEMENT", 4, 12, 1),
                text("YUTAKA  SUGANO", 7, 16, 0)
        ));

        // Screen 10: ZONE ARTISTS / names...
        frames.add(buildScreen(
                text("ZONE  ARTISTS", 9, 6, 1),
                text("YASUSHI  YAMAGUCHI", 4, 10, 0),
                text("CRAIG  STITT", 10, 12, 0),
                text("BRENDA  ROSS", 9, 14, 0),
                text("JINA  ISHIWATARI", 7, 16, 0),
                text("TOM  PAYNE", 11, 18, 0),
                text("PHENIX  RIE", 11, 20, 0)
        ));

        // Screen 11: SPECIALSTAGE / ART AND CG / TIM SKELLY / PETER MORAWIEC
        frames.add(buildScreen(
                text("SPECIALSTAGE", 9, 9, 1),
                text("ART  AND  CG", 10, 11, 1),
                text("TIM  SKELLY", 11, 15, 0),
                text("PETER  MORAWIEC", 7, 17, 0)
        ));

        // Screen 12: MUSIC COMPOSER / MASATO NAKAMURA / (@1992 / DREAMS COME TRUE)
        frames.add(buildScreen(
                text("MUSIC  COMPOSER", 6, 9, 1),
                text("MASATO  NAKAMURA", 5, 13, 0),
                text("( @1992", 3, 15, 0),
                text("DREAMS  COME  TRUE)", 4, 17, 0)
        ));

        // Screen 13: SOUND PROGRAMMER / TOMOYUKI SHIMADA
        frames.add(buildScreen(
                text("SOUND  PROGRAMMER", 4, 11, 1),
                text("TOMOYUKI  SHIMADA", 5, 15, 0)
        ));

        // Screen 14: SOUND ASSISTANTS / MACKY / JIMITA / MILPO / IPPO / S.O / OYZ / N.GEE
        frames.add(buildScreen(
                text("SOUND  ASSISTANTS", 4, 5, 1),
                text("MACKY", 15, 9, 0),
                text("JIMITA", 15, 11, 0),
                text("MILPO", 15, 13, 0),
                text("IPPO", 16, 15, 0),
                text("S.O", 17, 17, 0),
                text("OYZ", 17, 19, 0),
                text("N.GEE", 15, 21, 0)
        ));

        // Screen 15: PROJECT ASSISTANTS / names...
        frames.add(buildScreen(
                text("PROJECT  ASSISTANTS", 3, 8, 1),
                text("SYUICHI  KATAGI", 8, 12, 0),
                text("TAKAHIRO  HAMANO", 6, 14, 0),
                text("YOSHIKI  OOKA", 9, 16, 0),
                text("STEVE  WOITA", 10, 18, 0)
        ));

        // Screen 16: GAME MANUAL / YOUICHI TAKAHASHI / CAROL ANN HANSHAW
        frames.add(buildScreen(
                text("GAME  MANUAL", 9, 10, 1),
                text("YOUICHI  TAKAHASHI", 5, 14, 0),
                text("CAROL  ANN  HANSHAW", 3, 16, 0)
        ));

        // Screen 17: EXECUTIVE / SUPPORTERS / names...
        frames.add(buildScreen(
                text("EXECUTIVE", 11, 6, 1),
                text("SUPPORTERS", 10, 8, 1),
                text("DAIZABUROU  SAKURAI", 3, 12, 0),
                text("HISASHI  SUZUKI", 7, 14, 0),
                text("THOMAS  KALINSKE", 5, 16, 0),
                text("FUJIO  MINEGISHI", 7, 18, 0),
                text("TAKAHARU UTSUNOMIYA", 2, 20, 0) // ROM: single space (string length constraint)
        ));

        // Screen 18: SPECIAL THANKS / TO / names...
        // ROM: off_B4F0 - byte_BC9F = "FRANCE  TANTIADO" (original ROM spelling)
        frames.add(buildScreen(
                text("SPECIAL  THANKS", 6, 6, 1),
                text("TO", 18, 8, 1),
                text("CINDY  CLAVERAN", 6, 12, 0),
                text("FRANCE  TANTIADO", 5, 14, 0),
                text("DAISUKE  SAITO", 8, 16, 0),
                text("KUNITAKE  AOKI", 8, 18, 0),
                text("TSUNEKO  AOKI", 9, 20, 0)
        ));

        // Screen 19: SPECIAL THANKS / TO / names...
        frames.add(buildScreen(
                text("SPECIAL  THANKS", 6, 6, 1),
                text("TO", 18, 8, 1),
                text("DEBORAH  MCCRACKEN", 3, 12, 0),
                text("TATSUO  YAMADA", 7, 14, 0),
                text("RICK  MACARAEG", 7, 16, 0),
                text("LOCKY  P", 13, 18, 0),
                text("MASAAKI  KAWAMURA", 4, 20, 0)
        ));

        // Screen 20: PRESENTED / BY / SEGA
        frames.add(buildScreen(
                text("PRESENTED", 11, 9, 0),
                text("BY", 18, 13, 0),
                text("SEGA", 16, 17, 0)
        ));

        return frames;
    }

    // ---- Internal helpers ----

    /** Helper record for a text entry before conversion to pieces. */
    private record TextEntry(String text, int col, int line, int palette) {}

    private static TextEntry text(String text, int col, int line, int palette) {
        return new TextEntry(text, col, line, palette);
    }

    /**
     * Builds a single credit screen frame from its text entries.
     */
    private static SpriteMappingFrame buildScreen(TextEntry... entries) {
        List<SpriteMappingPiece> pieces = new ArrayList<>();
        for (TextEntry entry : entries) {
            addTextPieces(pieces, entry.text, entry.col, entry.line, entry.palette);
        }
        return new SpriteMappingFrame(pieces);
    }

    /**
     * Converts a text string at a given (col, line, palette) into SpriteMappingPieces.
     * <p>
     * Each character produces TWO pieces (left column 1x2, right column 1x2).
     * Spaces advance by 16 px (2 columns) without producing pieces.
     * Positions are stored as offsets from screen center (160, 112) since
     * {@link com.openggf.level.render.PatternSpriteRenderer#drawFrameIndex} adds an origin.
     *
     * @param pieces   accumulator list
     * @param text     credit text string
     * @param col      VDP starting column (pixelX = col * 8)
     * @param line     VDP line (pixelY = line * 8)
     * @param palette  VDP palette line (0 or 1)
     */
    private static void addTextPieces(List<SpriteMappingPiece> pieces,
                                       String text, int col, int line, int palette) {
        int baseX = col * 8 - CENTER_X;
        int baseY = line * 8 - CENTER_Y;
        int x = baseX;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                x += 16; // space = one character width (2 tiles)
                continue;
            }
            int[] tiles = charToTiles(c);
            if (tiles != null) {
                // Left column: 1 tile wide x 2 tiles tall
                pieces.add(piece(x, baseY, 1, 2, tiles[0], false, false, palette, false));
                // Right column: 1 tile wide x 2 tiles tall, offset 8px right
                pieces.add(piece(x + 8, baseY, 1, 2, tiles[1], false, false, palette, false));
            }
            x += 16; // each character is 16px (2 tiles) wide
        }
    }

    /**
     * Returns the {leftTile, rightTile} indices for a character, or null if unmapped.
     */
    private static int[] charToTiles(char c) {
        if (c >= 0 && c < CHAR_TILES.length && CHAR_TILES[c] != null) {
            return CHAR_TILES[c];
        }
        return null;
    }

    private static SpriteMappingPiece piece(int xOff, int yOff, int w, int h,
                                            int tile, boolean hFlip, boolean vFlip,
                                            int palette, boolean priority) {
        return new SpriteMappingPiece(xOff, yOff, w, h, tile, hFlip, vFlip, palette, priority);
    }
}
