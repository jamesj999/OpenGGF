package uk.co.jamesj999.sonic.game.sonic2.menu;

import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.PatternDesc;

/**
 * Renders the Sonic/Miles menu background map (MapEng_MenuBack).
 */
public class MenuBackgroundRenderer {
    private final PatternDesc reusableDesc = new PatternDesc();

    public void render(GraphicsManager graphicsManager, int[] mappings, int width, int height,
                       int patternBase, int patternOffset) {
        if (graphicsManager == null || mappings == null || mappings.length == 0) {
            return;
        }

        for (int ty = 0; ty < height; ty++) {
            for (int tx = 0; tx < width; tx++) {
                int idx = ty * width + tx;
                if (idx < 0 || idx >= mappings.length) {
                    continue;
                }
                int word = mappings[idx];
                if (word == 0) {
                    continue;
                }
                int flags = word & 0xF800;
                int patternIndex = (word & 0x7FF) + patternOffset;
                int adjusted = flags | (patternIndex & 0x7FF);
                reusableDesc.set(adjusted);
                int patternId = patternBase + patternIndex;
                graphicsManager.renderPatternWithId(patternId, reusableDesc, tx * 8, ty * 8);
            }
        }
    }
}
