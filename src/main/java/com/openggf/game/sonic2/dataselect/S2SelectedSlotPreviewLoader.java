package com.openggf.game.sonic2.dataselect;

import com.openggf.graphics.RgbaImage;
import com.openggf.game.sonic1.dataselect.S1SelectedSlotPreviewLoader;

import java.util.Map;

public final class S2SelectedSlotPreviewLoader {
    private final S1SelectedSlotPreviewLoader delegate = new S1SelectedSlotPreviewLoader();

    public Map<Integer, S1SelectedSlotPreviewLoader.LoadedPreview> loadAll(Map<Integer, RgbaImage> previews) {
        return delegate.loadAll(previews);
    }

    public S1SelectedSlotPreviewLoader.LoadedPreview load(RgbaImage image) {
        return delegate.load(image);
    }
}
