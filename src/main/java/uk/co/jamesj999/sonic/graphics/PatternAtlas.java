package uk.co.jamesj999.sonic.graphics;

import org.lwjgl.system.MemoryUtil;
import uk.co.jamesj999.sonic.level.Pattern;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.GL_RED;

/**
 * Texture atlas for 8x8 indexed patterns.
 * Stores patterns across one or more GL textures to avoid per-tile texture binds.
 */
public class PatternAtlas {
    private static final Logger LOGGER = Logger.getLogger(PatternAtlas.class.getName());

    public static final int TILE_SIZE = Pattern.PATTERN_WIDTH;
    private static final float UV_INSET_PIXELS = 0.01f;
    private static final int MAX_ATLASES = 2;

    private final int atlasWidth;
    private final int atlasHeight;
    private final int tilesPerRow;
    private final int tilesPerColumn;
    private final int maxSlots;

    private final Map<Integer, Entry> entries = new HashMap<>();
    private final List<AtlasPage> pages = new ArrayList<>();
    // Lazily allocated to avoid LWJGL native library loading in headless tests
    private ByteBuffer patternUploadBuffer;
    private boolean initialized = false;

    // Batch upload support: CPU-side pixel buffer mirrors the GPU atlas.
    // During batch mode, uploadPattern() writes to cpuPixels only (no GL calls).
    // endBatch() uploads each dirty page with a single glTexSubImage2D.
    private byte[][] cpuPixels;      // per-atlas-page pixel data [atlasWidth * atlasHeight]
    private boolean[] dirtyPages;    // tracks which pages were written during batch
    private boolean batchMode = false;

    public PatternAtlas(int atlasWidth, int atlasHeight) {
        if (atlasWidth % TILE_SIZE != 0 || atlasHeight % TILE_SIZE != 0) {
            throw new IllegalArgumentException("Atlas size must be divisible by tile size");
        }
        this.atlasWidth = atlasWidth;
        this.atlasHeight = atlasHeight;
        this.tilesPerRow = atlasWidth / TILE_SIZE;
        this.tilesPerColumn = atlasHeight / TILE_SIZE;
        this.maxSlots = tilesPerRow * tilesPerColumn;
        // patternUploadBuffer is lazily allocated when first needed
    }

    /**
     * Lazily allocate the pattern upload buffer.
     * This avoids triggering LWJGL native library loading during construction.
     */
    private ByteBuffer ensurePatternUploadBuffer() {
        if (patternUploadBuffer == null) {
            patternUploadBuffer = MemoryUtil.memAlloc(TILE_SIZE * TILE_SIZE);
        }
        return patternUploadBuffer;
    }

    // Lazily allocated full-page upload buffer for endBatch()
    private ByteBuffer fullPageUploadBuffer;

    private ByteBuffer ensureFullPageUploadBuffer() {
        int pagePixels = atlasWidth * atlasHeight;
        if (fullPageUploadBuffer == null || fullPageUploadBuffer.capacity() < pagePixels) {
            if (fullPageUploadBuffer != null) {
                MemoryUtil.memFree(fullPageUploadBuffer);
            }
            fullPageUploadBuffer = MemoryUtil.memAlloc(pagePixels);
        }
        return fullPageUploadBuffer;
    }

    public int getAtlasWidth() {
        return atlasWidth;
    }

    public int getAtlasHeight() {
        return atlasHeight;
    }

    public int getMaxSlotsPerAtlas() {
        return maxSlots;
    }

    public int getTextureId() {
        return getTextureId(0);
    }

    public int getTextureId(int atlasIndex) {
        if (atlasIndex < 0 || atlasIndex >= pages.size()) {
            return 0;
        }
        return pages.get(atlasIndex).textureId();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void init() {
        if (initialized) {
            return;
        }
        if (pages.isEmpty()) {
            pages.add(createPage(0));
        } else {
            for (AtlasPage page : pages) {
                if (page.textureId() == 0) {
                    page.setTextureId(createTexture());
                }
            }
        }
        initialized = true;
    }

    public Entry cachePattern(Pattern pattern, int patternId) {
        Entry entry = ensureEntry(patternId, false);
        if (entry == null) {
            return null;
        }
        if (initialized && pattern != null) {
            uploadPattern(pattern, entry);
        }
        return entry;
    }

    public Entry cachePatternHeadless(Pattern pattern, int patternId) {
        return ensureEntry(patternId, true);
    }

    public Entry updatePattern(Pattern pattern, int patternId) {
        return cachePattern(pattern, patternId);
    }

    public Entry updatePatternHeadless(Pattern pattern, int patternId) {
        return cachePatternHeadless(pattern, patternId);
    }

    public Entry getEntry(int patternId) {
        return entries.get(patternId);
    }

    /**
     * Remove a pattern entry from the atlas.
     * This makes getEntry() return null for this pattern ID, causing
     * the renderer to skip it. The atlas slot is not reclaimed.
     *
     * @param patternId The pattern ID to remove
     * @return true if the pattern was removed, false if it wasn't cached
     */
    public boolean removeEntry(int patternId) {
        return entries.remove(patternId) != null;
    }

    /**
     * Create an alias entry that points to the same atlas slot as another pattern.
     * This allows multiple pattern IDs to share the same texture data without
     * allocating additional atlas slots.
     *
     * Will NOT overwrite an existing entry - if aliasId already has an entry,
     * this method returns false and leaves it unchanged.
     *
     * @param aliasId The new pattern ID to create
     * @param targetId The existing pattern ID to alias to
     * @return true if the alias was created, false if target doesn't exist or aliasId already exists
     */
    public boolean aliasEntry(int aliasId, int targetId) {
        // Don't overwrite existing entries (e.g., ring patterns)
        if (entries.containsKey(aliasId)) {
            return false;
        }
        Entry target = entries.get(targetId);
        if (target == null) {
            return false;
        }
        // Create a new entry with the alias ID but same atlas coordinates as target
        Entry alias = new Entry(aliasId, target.atlasIndex(), target.slot(),
                target.tileX(), target.tileY(), target.u0(), target.v0(), target.u1(), target.v1());
        entries.put(aliasId, alias);
        return true;
    }

    public int getAtlasCount() {
        return pages.size();
    }

    public void cleanup() {
        for (AtlasPage page : pages) {
            if (page.textureId() != 0) {
                glDeleteTextures(page.textureId());
            }
        }
        cleanupCommon();
    }

    public void cleanupHeadless() {
        cleanupCommon();
    }

    private void cleanupCommon() {
        initialized = false;
        batchMode = false;
        entries.clear();
        pages.clear();
        cpuPixels = null;
        dirtyPages = null;
        if (patternUploadBuffer != null) {
            MemoryUtil.memFree(patternUploadBuffer);
            patternUploadBuffer = null;
        }
        if (fullPageUploadBuffer != null) {
            MemoryUtil.memFree(fullPageUploadBuffer);
            fullPageUploadBuffer = null;
        }
    }

    private Entry ensureEntry(int patternId, boolean headless) {
        Entry existing = entries.get(patternId);
        if (existing != null) {
            return existing;
        }

        AtlasPage page = getOrCreatePage(headless);
        if (page == null) {
            LOGGER.warning("Pattern atlas capacity exceeded; patternId=" + patternId);
            return null;
        }

        int slot = page.allocateSlot();
        int tileX = slot % tilesPerRow;
        int tileY = slot / tilesPerRow;

        int pixelX = tileX * TILE_SIZE;
        int pixelY = tileY * TILE_SIZE;

        float u0 = (pixelX + UV_INSET_PIXELS) / (float) atlasWidth;
        float u1 = (pixelX + TILE_SIZE - UV_INSET_PIXELS) / (float) atlasWidth;
        float v0 = (pixelY + UV_INSET_PIXELS) / (float) atlasHeight;
        float v1 = (pixelY + TILE_SIZE - UV_INSET_PIXELS) / (float) atlasHeight;

        Entry entry = new Entry(patternId, page.atlasIndex(), slot, tileX, tileY, u0, v0, u1, v1);
        entries.put(patternId, entry);
        return entry;
    }

    /**
     * Begin batch mode. Pattern uploads write to a CPU-side buffer only.
     * Call {@link #endBatch()} to flush everything to the GPU in one call per page.
     */
    public void beginBatch() {
        if (cpuPixels == null) {
            int pagePixels = atlasWidth * atlasHeight;
            cpuPixels = new byte[MAX_ATLASES][pagePixels];
            dirtyPages = new boolean[MAX_ATLASES];
        }
        batchMode = true;
    }

    /**
     * End batch mode and upload every dirty atlas page to the GPU
     * with a single {@code glTexSubImage2D} per page.
     */
    public void endBatch() {
        if (!batchMode) {
            return;
        }
        batchMode = false;
        if (cpuPixels == null || dirtyPages == null) {
            return;
        }
        int pagePixels = atlasWidth * atlasHeight;
        ByteBuffer buf = ensureFullPageUploadBuffer();
        for (int i = 0; i < pages.size(); i++) {
            if (!dirtyPages[i]) {
                continue;
            }
            int textureId = getTextureId(i);
            if (textureId == 0) {
                continue;
            }
            buf.clear();
            buf.put(cpuPixels[i], 0, pagePixels);
            buf.flip();
            glBindTexture(GL_TEXTURE_2D, textureId);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, atlasWidth, atlasHeight,
                    GL_RED, GL_UNSIGNED_BYTE, buf);
            glBindTexture(GL_TEXTURE_2D, 0);
            dirtyPages[i] = false;
        }
    }

    private void uploadPattern(Pattern pattern, Entry entry) {
        int pixelX = entry.tileX() * TILE_SIZE;
        int pixelY = entry.tileY() * TILE_SIZE;

        // Always write to the CPU-side buffer (keeps it in sync for future batches)
        if (cpuPixels != null && entry.atlasIndex() < cpuPixels.length) {
            byte[] page = cpuPixels[entry.atlasIndex()];
            for (int col = 0; col < TILE_SIZE; col++) {
                int dstRowStart = (pixelY + col) * atlasWidth + pixelX;
                for (int row = 0; row < TILE_SIZE; row++) {
                    page[dstRowStart + row] = pattern.getPixel(row, col);
                }
            }
        }

        if (batchMode) {
            // Mark page dirty — actual GL upload deferred to endBatch()
            if (dirtyPages != null && entry.atlasIndex() < dirtyPages.length) {
                dirtyPages[entry.atlasIndex()] = true;
            }
            return;
        }

        // Immediate upload (non-batch path)
        ByteBuffer patternBuffer = ensurePatternUploadBuffer();
        patternBuffer.clear();
        for (int col = 0; col < TILE_SIZE; col++) {
            for (int row = 0; row < TILE_SIZE; row++) {
                byte colorIndex = pattern.getPixel(row, col);
                patternBuffer.put(colorIndex);
            }
        }
        patternBuffer.flip();

        int textureId = getTextureId(entry.atlasIndex());
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexSubImage2D(GL_TEXTURE_2D, 0, pixelX, pixelY, TILE_SIZE, TILE_SIZE,
                GL_RED, GL_UNSIGNED_BYTE, patternBuffer);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private AtlasPage getOrCreatePage(boolean headless) {
        if (pages.isEmpty()) {
            pages.add(createPage(0, headless));
        }
        AtlasPage current = pages.get(pages.size() - 1);
        if (current.hasCapacity()) {
            return current;
        }
        if (pages.size() >= MAX_ATLASES) {
            return null;
        }
        AtlasPage next = createPage(pages.size(), headless);
        pages.add(next);
        return next;
    }

    private AtlasPage createPage(int atlasIndex) {
        return createPage(atlasIndex, false);
    }

    private AtlasPage createPage(int atlasIndex, boolean headless) {
        int textureId = headless ? 0 : createTexture();
        return new AtlasPage(atlasIndex, textureId, maxSlots);
    }

    private int createTexture() {
        int textureId = glGenTextures();

        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, atlasWidth, atlasHeight, 0,
                GL_RED, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glBindTexture(GL_TEXTURE_2D, 0);
        return textureId;
    }

    private static final class AtlasPage {
        private final int atlasIndex;
        private int textureId;
        private final int maxSlots;
        private int nextSlot;

        private AtlasPage(int atlasIndex, int textureId, int maxSlots) {
            this.atlasIndex = atlasIndex;
            this.textureId = textureId;
            this.maxSlots = maxSlots;
        }

        private boolean hasCapacity() {
            return nextSlot < maxSlots;
        }

        private int allocateSlot() {
            return nextSlot++;
        }

        private int atlasIndex() {
            return atlasIndex;
        }

        private int textureId() {
            return textureId;
        }

        private void setTextureId(int textureId) {
            this.textureId = textureId;
        }
    }

    public record Entry(int patternId, int atlasIndex, int slot, int tileX, int tileY,
            float u0, float v0, float u1, float v1) {
    }
}
