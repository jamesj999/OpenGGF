package uk.co.jamesj999.sonic.graphics;

import uk.co.jamesj999.sonic.game.GameId;
import uk.co.jamesj999.sonic.level.Palette;

import java.util.Collection;
import java.util.Collections;
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
     * Returns all active donor render contexts (unmodifiable).
     */
    public static Collection<RenderContext> getDonorContexts() {
        return Collections.unmodifiableCollection(donorContexts.values());
    }

    /**
     * Derives an underwater palette for a donor sprite by applying the base
     * game's normal-to-underwater color ratio to each donor color.
     *
     * @param donorNormal    the donor's normal (above-water) palette
     * @param normalBase     the base game's normal palette (line 0)
     * @param underwaterBase the base game's underwater palette (line 0)
     * @return a new Palette with underwater-tinted donor colors
     */
    public static Palette deriveUnderwaterPalette(Palette donorNormal,
                                                   Palette normalBase,
                                                   Palette underwaterBase) {
        Palette result = new Palette();
        for (int i = 0; i < 16; i++) {
            Palette.Color dn = donorNormal.getColor(i);
            Palette.Color nb = normalBase.getColor(i);
            Palette.Color ub = underwaterBase.getColor(i);

            int dnR = Byte.toUnsignedInt(dn.r);
            int dnG = Byte.toUnsignedInt(dn.g);
            int dnB = Byte.toUnsignedInt(dn.b);

            int nbR = Byte.toUnsignedInt(nb.r);
            int nbG = Byte.toUnsignedInt(nb.g);
            int nbB = Byte.toUnsignedInt(nb.b);

            int ubR = Byte.toUnsignedInt(ub.r);
            int ubG = Byte.toUnsignedInt(ub.g);
            int ubB = Byte.toUnsignedInt(ub.b);

            int r, g, b;
            if (nbR > 0) { r = Math.min(255, dnR * ubR / nbR); } else { r = ubR; }
            if (nbG > 0) { g = Math.min(255, dnG * ubG / nbG); } else { g = ubG; }
            if (nbB > 0) { b = Math.min(255, dnB * ubB / nbB); } else { b = ubB; }

            result.setColor(i, new Palette.Color((byte) r, (byte) g, (byte) b));
        }
        return result;
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
