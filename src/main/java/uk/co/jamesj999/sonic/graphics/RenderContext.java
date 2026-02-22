package uk.co.jamesj999.sonic.graphics;

import uk.co.jamesj999.sonic.game.GameId;
import uk.co.jamesj999.sonic.level.Palette;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-game palette isolation for cross-game sprite rendering.
 *
 * <p>The base game occupies palette lines 0-3. Each donor game gets its own
 * block of 4 palette lines (4-7, 8-11, etc.) so donor sprites render with
 * their own colors without interfering with the base game's palettes.
 *
 * <p>Combines instance state (one game's palette context) with a static
 * registry that manages all active donor contexts.
 */
public class RenderContext {

    // --- Static registry ---

    /** Number of palette lines per context (matches Mega Drive's 4 palette lines). */
    public static final int LINES_PER_CONTEXT = 4;

    private static final Map<GameId, RenderContext> donorContexts = new LinkedHashMap<>();
    private static int nextPaletteBase = LINES_PER_CONTEXT; // first donor starts at line 4

    /**
     * Returns (or creates) the donor render context for the given game.
     * The first donor gets palette lines 4-7, the second 8-11, etc.
     */
    public static RenderContext getOrCreateDonor(GameId gameId) {
        return donorContexts.computeIfAbsent(gameId, id -> {
            RenderContext ctx = new RenderContext(id, nextPaletteBase);
            nextPaletteBase += LINES_PER_CONTEXT;
            return ctx;
        });
    }

    /**
     * Returns the total number of palette lines needed (base + all donors).
     */
    public static int getTotalPaletteLines() {
        return nextPaletteBase;
    }

    /**
     * Uploads all donor palettes to the GPU palette texture.
     */
    public static void uploadDonorPalettes(GraphicsManager gm) {
        for (RenderContext ctx : donorContexts.values()) {
            for (int line = 0; line < LINES_PER_CONTEXT; line++) {
                Palette p = ctx.palettes[line];
                if (p != null) {
                    gm.cachePaletteTexture(p, ctx.paletteLineBase + line);
                }
            }
        }
    }

    /**
     * Clears all donor contexts. Call on engine cleanup or game reset.
     */
    public static void reset() {
        donorContexts.clear();
        nextPaletteBase = LINES_PER_CONTEXT;
    }

    // --- Instance state ---

    private final GameId gameId;
    private final int paletteLineBase;
    private final Palette[] palettes = new Palette[LINES_PER_CONTEXT];

    private RenderContext(GameId gameId, int paletteLineBase) {
        this.gameId = gameId;
        this.paletteLineBase = paletteLineBase;
    }

    public GameId getGameId() {
        return gameId;
    }

    public int getPaletteLineBase() {
        return paletteLineBase;
    }

    /**
     * Maps a logical palette line (0-3) to the effective line in the
     * expanded palette texture.
     */
    public int getEffectivePaletteLine(int logical) {
        return paletteLineBase + logical;
    }

    public void setPalette(int line, Palette p) {
        palettes[line] = p;
    }

    public Palette getPalette(int line) {
        return palettes[line];
    }
}
