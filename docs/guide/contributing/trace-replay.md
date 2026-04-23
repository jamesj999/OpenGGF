# Trace Replay Testing

Trace replay tests compare the engine against a frame-by-frame recording from the original ROM
running in BizHawk. They are the highest-signal tests in the repo for physics, object timing,
spawn timing, and collision parity work.

The current in-tree Sonic 1 traces are:

- `TestS1Ghz1TraceReplay` using `src/test/resources/traces/s1/ghz1_fullrun/`
- `TestS1Mz1TraceReplay` using `src/test/resources/traces/s1/mz1_fullrun/`

The repo also contains tooling for capturing the eight built-in Sonic 1 ending-credits demo replays.
Those traces are intended for future trace-replay coverage of the credits sequence and do not require
BK2 movies, because the ROM owns the input stream.

Each trace directory contains:

- `metadata.json` for trace metadata and start state
- `physics.csv` for per-frame core state
- `aux_state.jsonl` for richer diagnostic events
- a `.bk2` movie used to drive the engine replay

Replay phase is derived from recorded ROM counters:

- `gameplay_frame_counter` changes only when the level main loop completed
- `vblank_counter` changes on every VBlank
- `lag_counter` is diagnostic where the ROM exposes it

## When To Use It

Use trace replay when:

- a bug is about ROM parity rather than just local correctness
- a fix affects movement, terrain collision, object timing, or hurt/bounce flow
- you need to prove that a change fixed the first real divergence instead of only masking symptoms

Prefer ordinary headless tests for small isolated behaviours. Use trace replay when the interaction
between multiple systems is the thing under test.

## Running Existing Trace Tests

Trace replay tests require:

- a Sonic 1 REV01 ROM in the repo root, or `-Dsonic1.rom.path=...`
- the `.bk2` file to be present in the trace directory

Run both current traces:

```bash
mvn test -Dtest=TestS1Ghz1TraceReplay,TestS1Mz1TraceReplay
```

Run one trace:

```bash
mvn test -Dtest=TestS1Mz1TraceReplay
```

Useful optional override while tuning S1 oscillation alignment:

```bash
mvn test -Dtest=TestS1Mz1TraceReplay -Dosc.override=0
```

Reports are written to `target/trace-reports/`. On divergence you will typically see:

- `s1_ghz1_report.json` or `s1_mz1_report.json`
- `s1_ghz1_context.txt` or `s1_mz1_context.txt`

The JSON groups divergences by field and frame range. The context file is the fastest way to find
the first non-cascading failure.

## Recording Or Refreshing A Trace

The recorder workflow is now game-specific at the BizHawk entrypoint, but the emitted trace contract
is the same schema v3 format for all three main games.

| Game | Recorder script | Launcher | `metadata.json["game"]` | `gameplay_frame_counter` | `vblank_counter` | `lag_counter` |
| --- | --- | --- | --- | --- | --- | --- |
| Sonic 1 | [`s1_trace_recorder.lua`](../../../tools/bizhawk/s1_trace_recorder.lua) | [`record_trace.bat`](../../../tools/bizhawk/record_trace.bat) | `s1` | `0xFE04` | `0xFE0E` | placeholder `0` |
| Sonic 2 | [`s2_trace_recorder.lua`](../../../tools/bizhawk/s2_trace_recorder.lua) | [`record_s2_trace.bat`](../../../tools/bizhawk/record_s2_trace.bat) | `s2` | `0xFE04` | `0xFE0E` | placeholder `0` |
| Sonic 3&K | [`s3k_trace_recorder.lua`](../../../tools/bizhawk/s3k_trace_recorder.lua) | [`record_s3k_trace.bat`](../../../tools/bizhawk/record_s3k_trace.bat) | `s3k` | `0xFE08` | `0xFE12` | `0xF628` diagnostic counter |

Prerequisites:

- BizHawk 2.11 installed at `docs/BizHawk-2.11-win-x64/EmuHawk.exe`, or set `BIZHAWK_EXE`
- the matching game ROM for the recorder you are using
- a BK2 movie for the act you want to record

Examples:

```bat
tools\bizhawk\record_trace.bat ^
  "Sonic The Hedgehog (W) (REV01) [!].gen" ^
  "docs\BizHawk-2.11-win-x64\Movies\s1-mz1.bk2"

tools\bizhawk\record_s2_trace.bat ^
  "Sonic The Hedgehog 2 (W) (REV01) [!].gen" ^
  "docs\BizHawk-2.11-win-x64\Movies\s2-ehz1.bk2"

tools\bizhawk\record_s3k_trace.bat ^
  "Sonic and Knuckles & Sonic 3 (W) [!].gen" ^
  "docs\BizHawk-2.11-win-x64\Movies\s3k-aiz1.bk2"

tools\bizhawk\record_s3k_trace.bat ^
  "Sonic and Knuckles & Sonic 3 (W) [!].gen" ^
  "src\test\resources\traces\s3k\aiz1_to_hcz_fullrun\s3k-aiz1-aiz2-sonictails.bk2" ^
  aiz_end_to_end
```

The recorder writes output relative to the recorder script:

- `tools/bizhawk/trace_output/`

After recording, copy:

- `metadata.json`
- `physics.csv`
- `aux_state.jsonl`

into the target trace directory under `src/test/resources/traces/<game>/...`, and place the `.bk2`
movie in the same directory if it is not already there.

Sanity-check `metadata.json` before trusting the trace:

- `game` should be `s1`, `s2`, or `s3k` to match the recorder
- `zone` and `act` should match the intended recording
- `trace_frame_count` should be in the expected range for that route
- `start_x` and `start_y` should look plausible for the level start
- `trace_schema` should be `3`
- `csv_version` should remain `4`

For the authoritative S3K `aiz1_to_hcz_fullrun` fixture, the recorder metadata also carries:

- `lua_script_version = "3.1-s3k"`
- `bizhawk_version = "2.11"`
- `genesis_core = "Genplus-gx"`
- `notes = "AIZ intro through HCZ handoff end-to-end fixture"`

Interpret the execution counters the same way across games:

- `gameplay_frame_counter` advances only when the level main loop completed
- `vblank_counter` advances on every VBlank
- `lag_counter` is meaningful only for Sonic 3&K; Sonic 1 and Sonic 2 record `0`

## Recording The S3K End-To-End Fixture

Use the `aiz_end_to_end` profile when refreshing the full AIZ intro through HCZ handoff trace.
That profile starts at BK2 frame `0` and augments `aux_state.jsonl` with:

- edge-triggered `zone_act_state` events whenever actual zone/act, apparent act, or game mode changes
- one-shot semantic `checkpoint` events for `intro_begin`, `gameplay_start`,
  `aiz1_fire_transition_begin`, `aiz2_reload_resume`, `aiz2_main_gameplay`,
  `hcz_handoff_begin`, and `hcz_handoff_complete`

Workflow:

1. Keep the locked-on ROM at the repo root.
2. Run:

```bat
tools\bizhawk\record_s3k_trace.bat ^
  "Sonic and Knuckles & Sonic 3 (W) [!].gen" ^
  "src\test\resources\traces\s3k\aiz1_to_hcz_fullrun\s3k-aiz1-aiz2-sonictails.bk2" ^
  aiz_end_to_end
```

3. Copy `tools\bizhawk\trace_output\metadata.json`, `physics.csv`, and `aux_state.jsonl` into
   `src\test\resources\traces\s3k\aiz1_to_hcz_fullrun\`.
4. Run the fixture sanity gate against `tools\bizhawk\trace_output\aux_state.jsonl`:

```powershell
$events = Get-Content tools/bizhawk/trace_output/aux_state.jsonl | ForEach-Object { $_ | ConvertFrom-Json }
$required = 'intro_begin','gameplay_start','aiz1_fire_transition_begin','aiz2_reload_resume','aiz2_main_gameplay','hcz_handoff_begin','hcz_handoff_complete'
$frames = $events | Select-Object -ExpandProperty frame

if (($events | Where-Object event -eq 'checkpoint' | Where-Object name -eq 'intro_begin').Count -ne 1) { throw 'intro_begin count != 1' }
if ((($frames | Sort-Object) -join ',') -ne ($frames -join ',')) { throw 'frame order regressed' }
foreach ($name in $required) {
  if (($events | Where-Object event -eq 'checkpoint' | Where-Object name -eq $name).Count -ne 1) {
    throw "required checkpoint missing or duplicated: $name"
  }
}
for ($i = 1; $i -lt $events.Count; $i++) {
  if ($events[$i].frame -eq $events[$i-1].frame -and $events[$i].event -eq 'zone_act_state' -and $events[$i-1].event -eq 'checkpoint') {
    throw "zone_act_state emitted after checkpoint on frame $($events[$i].frame)"
  }
}
```

Expected result: no output and no exception.

## Recording Sonic 1 Credits Demo Traces

Use the dedicated credits recorder when you want the ROM's ending replays rather than a human-played
BK2 route. This path forces `GM_Credits` from the title screen, waits for each demo to become active,
and records each replay into its own trace directory under `tools/bizhawk/trace_output/credits_demos/`.

Command:

```bat
tools\bizhawk\record_s1_credits_traces.bat ^
  "Sonic The Hedgehog (W) (REV01) [!].gen" ^
  all
```

Record one replay only:

```bat
tools\bizhawk\record_s1_credits_traces.bat ^
  "Sonic The Hedgehog (W) (REV01) [!].gen" ^
  3
```

Supported replay indices:

- `0` = `ghz1_credits_demo_1`
- `1` = `mz2_credits_demo`
- `2` = `syz3_credits_demo`
- `3` = `lz3_credits_demo`
- `4` = `slz3_credits_demo`
- `5` = `sbz1_credits_demo`
- `6` = `sbz2_credits_demo`
- `7` = `ghz1_credits_demo_2`

Output layout:

- `tools/bizhawk/trace_output/credits_demos/manifest.json`
- `tools/bizhawk/trace_output/credits_demos/00_ghz1_credits_demo_1/`
- `tools/bizhawk/trace_output/credits_demos/01_mz2_credits_demo/`
- `...`

Each replay directory contains the same core files as a normal trace capture:

- `metadata.json`
- `physics.csv`
- `aux_state.jsonl`

Credits-demo metadata adds a few fields that are useful for future trace-framework work:

- `trace_type = "credits_demo"`
- `input_source = "rom_ending_demo"`
- `credits_demo_index`
- `credits_demo_slug`
- `emu_frame_start`

Notes:

- No `.bk2` is produced or required for this workflow.
- The script exits after the last gameplay replay finishes; it does not wait through the final
  text-only "PRESENTED BY SEGA" card or the `TRY AGAIN / END` screen.
- The capture format is intentionally aligned with the existing S1 `physics.csv` / `aux_state.jsonl`
  schema so the replay-side test harness can be extended later without inventing a parallel parser.

## Adding A New Trace Test

1. Create a new directory under `src/test/resources/traces/s1/<name>/`.
2. Record or copy in `metadata.json`, `physics.csv`, `aux_state.jsonl`, and the `.bk2`.
3. Add a test class under `src/test/java/com/openggf/tests/trace/s1/` extending `AbstractTraceReplayTest`.
4. Return the correct engine zone index, act index, and trace directory path.
5. Run the new test once and inspect `target/trace-reports/` before treating the baseline as valid.

Minimal example:

```java
@RequiresRom(SonicGame.SONIC_1)
public class TestS1ExampleTraceReplay extends AbstractTraceReplayTest {
    @Override
    protected SonicGame game() { return SonicGame.SONIC_1; }

    @Override
    protected int zone() { return 0; }

    @Override
    protected int act() { return 0; }

    @Override
    protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/s1/example_fullrun");
    }
}
```

Important: the test's `zone()` is the engine's zone index, not necessarily the raw ROM `v_zone`
value. [`TestS1Mz1TraceReplay.java`](../../../src/test/java/com/openggf/tests/trace/s1/TestS1Mz1TraceReplay.java)
shows the Marble Zone mapping nuance.

## Reading Divergences

Focus on the first non-cascading error. Everything after that may be fallout.

The main sources are:

- `physics.csv`
  Core compared fields: position, speeds, angle, air/rolling flags, and ground mode.
- `aux_state.jsonl`
  Event stream for object appearances, removals, routine changes, slot dumps, and nearby objects.
- `target/trace-reports/*_context.txt`
  Side-by-side ROM and engine diagnostic context generated by `AbstractTraceReplayTest`.

The current recorder/test pipeline also carries diagnostic-only fields that do not fail the test by
themselves, but greatly narrow investigation:

- subpixel position
- routine
- camera position
- ring count
- status byte
- gameplay frame counter
- VBlank counter
- lag counter
- ridden object slot
- ObjPosLoad cursor state

Typical debugging pattern:

1. open the first error in the context file
2. check whether the first mismatch is terrain, object contact, hurt/bounce, or spawn timing
3. cross-reference the same frame in `aux_state.jsonl`
4. only after the first error is understood should you trust later divergences

## Common Pitfalls

- Missing `.bk2` file: the test will skip even if `metadata.json` and `physics.csv` exist.
- Wrong zone index in the test class: the metadata may be correct while the engine loads the wrong level.
- Regenerating only `physics.csv` but not `aux_state.jsonl` or `metadata.json`: diagnostics become misleading.
- Treating warning-only 1px drift as the root cause when a later object timing error is the real break.
- Updating the trace baseline before understanding whether a behaviour change is a real fix or a regression.

## Related Files

- [`AbstractTraceReplayTest.java`](../../../src/test/java/com/openggf/tests/trace/AbstractTraceReplayTest.java)
- [`TraceBinder.java`](../../../src/test/java/com/openggf/tests/trace/TraceBinder.java)
- [`DivergenceReport.java`](../../../src/test/java/com/openggf/tests/trace/DivergenceReport.java)
- [`s1_trace_recorder.lua`](../../../tools/bizhawk/s1_trace_recorder.lua)
- [`s2_trace_recorder.lua`](../../../tools/bizhawk/s2_trace_recorder.lua)
- [`s3k_trace_recorder.lua`](../../../tools/bizhawk/s3k_trace_recorder.lua)
- [`s1_credits_trace_recorder.lua`](../../../tools/bizhawk/s1_credits_trace_recorder.lua)
- [`record_trace.bat`](../../../tools/bizhawk/record_trace.bat)
- [`record_s2_trace.bat`](../../../tools/bizhawk/record_s2_trace.bat)
- [`record_s3k_trace.bat`](../../../tools/bizhawk/record_s3k_trace.bat)
- [`record_s1_credits_traces.bat`](../../../tools/bizhawk/record_s1_credits_traces.bat)

## Next Steps

- [Testing](testing.md)
- [Tutorial: Implement an Object](tutorial-implement-object.md)
- [Tooling](../cross-referencing/tooling.md)
