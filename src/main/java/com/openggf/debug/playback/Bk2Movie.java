package com.openggf.debug.playback;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Parsed BK2 movie container with metadata and frame inputs.
 */
public final class Bk2Movie {
    private final Path sourcePath;
    private final String logKey;
    private final Map<String, String> headerMetadata;
    private final List<Bk2FrameInput> frames;
    private final int firstFrameLineNumber;

    public Bk2Movie(Path sourcePath, String logKey, Map<String, String> headerMetadata,
            List<Bk2FrameInput> frames, int firstFrameLineNumber) {
        this.sourcePath = sourcePath;
        this.logKey = logKey;
        this.headerMetadata = Map.copyOf(headerMetadata);
        this.frames = List.copyOf(frames);
        this.firstFrameLineNumber = firstFrameLineNumber;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public String getLogKey() {
        return logKey;
    }

    public Map<String, String> getHeaderMetadata() {
        return headerMetadata;
    }

    public List<Bk2FrameInput> getFrames() {
        return frames;
    }

    public int getFrameCount() {
        return frames.size();
    }

    public Bk2FrameInput getFrame(int index) {
        if (frames.isEmpty()) {
            throw new IllegalStateException("BK2 movie has no frames");
        }
        int clamped = Math.max(0, Math.min(index, frames.size() - 1));
        return frames.get(clamped);
    }

    /**
     * Converts a BizHawk frame number (1-based file line number in the Input Log)
     * to a 0-based internal frame index.
     */
    public int bk2FrameToIndex(int bk2Frame) {
        return bk2Frame - firstFrameLineNumber;
    }

    /**
     * Converts a 0-based internal frame index to a BizHawk frame number.
     */
    public int indexToBk2Frame(int index) {
        return index + firstFrameLineNumber;
    }

    public int getFirstFrameLineNumber() {
        return firstFrameLineNumber;
    }
}
