package com.openggf.audio.smps;

import java.util.List;

public interface SmpsSfxData {
    int getTickMultiplier();

    List<? extends SmpsSfxTrack> getTrackEntries();

    interface SmpsSfxTrack {
        int channelMask();

        int pointer();

        int transpose();

        int volume();
    }
}
