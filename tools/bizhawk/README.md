# BizHawk Trace Recording

## Requirements

- BizHawk 2.9+ (tested with 2.9.1)
- Genesis/Mega Drive core: "Genplus-gx" (default) or "PicoDrive"
- Sonic 1 REV01 ROM: `Sonic The Hedgehog (W) (REV01) [!].gen`

## Recording a Trace

1. Open BizHawk and load the Sonic 1 ROM
2. File > Movie > Play Movie... > select your .bk2 file
3. Tools > Lua Console
4. Script > Open Script... > select `s1_trace_recorder.lua`
5. Close the Lua Console dialog (the script keeps running)
6. The movie plays automatically. The console prints when recording starts
7. When the movie ends or you stop it, trace files are written

## Output

The script creates a `trace_output/` directory in BizHawk's working directory
containing:

- `metadata.json` - Recording metadata (game, zone, BK2 offset, etc.)
- `physics.csv` - Per-frame physics state (position, speed, angle, flags)
- `aux_state.jsonl` - Auxiliary events (object appearances, mode changes, snapshots)

## Using with OpenGGF

1. Copy the output files to `src/test/resources/traces/s1/<trace_name>/`
2. Copy the .bk2 file to the same directory
3. Update `metadata.json` zone/act fields if not GHZ Act 1
4. Create a test class extending `AbstractTraceReplayTest`
5. Run with `mvn test -Dtest=TestS1Ghz1TraceReplay`

## Notes

- Recording starts automatically when level gameplay begins (Game_Mode=0x0C, controls unlocked)
- Recording stops when leaving level gameplay (act clear, death, etc.)
- The `zone` and `act` fields in metadata.json default to "ghz"/1 -- update manually for other levels
- Ground mode is derived from the terrain angle quadrant when grounded
