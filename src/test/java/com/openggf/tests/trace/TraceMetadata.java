package com.openggf.tests.trace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Metadata for a trace recording directory, parsed from metadata.json.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TraceMetadata(
    @JsonProperty("game") String game,
    @JsonProperty("zone") String zone,
    @JsonProperty("zone_id") Integer zoneId,
    @JsonProperty("act") int act,
    @JsonProperty("bk2_frame_offset") int bk2FrameOffset,
    @JsonProperty("trace_frame_count") int traceFrameCount,
    @JsonProperty("start_x") String startXHex,
    @JsonProperty("start_y") String startYHex,
    @JsonProperty("recording_date") String recordingDate,
    @JsonProperty("lua_script_version") String luaScriptVersion,
    @JsonProperty("rom_checksum") String romChecksum,
    @JsonProperty("notes") String notes,
    @JsonProperty("pre_trace_osc_frames") Integer preTraceOscFrames,
    @JsonProperty("trace_type") String traceType,
    @JsonProperty("input_source") String inputSource,
    @JsonProperty("credits_demo_index") Integer creditsDemoIndex,
    @JsonProperty("credits_demo_slug") String creditsDemoSlug
) {

    /**
     * Number of Level_MainLoop frames the ROM executed between OscillateNumInit
     * and the first trace frame. The engine must pre-advance OscillationManager
     * by this many updates to match the ROM's oscillation phase.
     * Returns 0 if not specified in the metadata.
     */
    public int preTraceOscillationFrames() {
        return preTraceOscFrames != null ? preTraceOscFrames : 0;
    }

    /** Parse start_x hex string to short. */
    public short startX() {
        return (short) Integer.parseInt(startXHex.replace("0x", ""), 16);
    }

    /** Parse start_y hex string to short. */
    public short startY() {
        return (short) Integer.parseInt(startYHex.replace("0x", ""), 16);
    }

    /** Load metadata from a metadata.json file. */
    public static TraceMetadata load(Path metadataFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(metadataFile.toFile(), TraceMetadata.class);
    }
}


