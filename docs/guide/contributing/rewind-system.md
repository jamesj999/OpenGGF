# Rewind System

The rewind system lets a gameplay session return to an earlier frame by restoring a
stored keyframe and replaying deterministic inputs forward to the requested frame.
It is built as a debugger and trace-validation tool first, with the same primitives
intended to support live in-game rewind later.

## Where It Can Be Used

Today rewind is safe to use in gameplay-scoped sessions that install a
`PlaybackController` through `GameplayModeContext.installPlaybackController(...)`.
The current production use case is trace playback and headless validation, where
the complete input stream is already known.

Good uses:

- Trace visualisation and trace replay tooling.
- Headless tests that need to seek backward and replay a deterministic segment.
- Rewind determinism debugging for player, sidekick, object, ring, level, palette,
  parallax, and zone-runtime state.
- Future live gameplay rewind, once a live input recorder and bounded history store
  are wired in.

Avoid using it for:

- Menu/title/data-select state. Rewind is owned by `GameplayModeContext`, not the
  global application shell.
- Audio scrubbing. Audio is not snapshotted and may pop or restart around a seek.
- Rewinding across level, act, or mode boundaries. Those events reset the rewind
  buffer by design.
- Editor undo/redo. The level editor uses its own `MutableLevel` snapshot and command
  history semantics.

## User-Facing Behaviour

Visual Trace Test Mode installs the rewind controller automatically after a trace
launches. To use it:

1. Enable Trace Test Mode in `config.json`:

   ```json
   {
     "MASTER_TITLE_SCREEN_ON_STARTUP": true,
     "TEST_MODE_ENABLED": true,
     "TRACE_CATALOG_DIR": "src/test/resources/traces"
   }
   ```

2. Launch the engine and choose a trace from the picker with `Enter`.
3. Hold `TRACE_REWIND_KEY` while the trace is running. The default key is `R`.

The HUD shows `Hold R Rewind` while rewind is available and changes to `REWIND <frame>`
while the key is held. Releasing the key resumes the BK2-driven replay from the
restored frame. `Enter` still pauses/resumes the trace, `Q` still frame-steps while
paused, and `Esc` exits the trace back to the picker.

The playback wrapper has three states:

| State | Behaviour |
| --- | --- |
| `PLAYING` | Step one frame forward, capture keyframes at the configured interval. |
| `PAUSED` | Do not advance until asked to single-step or resume. |
| `REWINDING` | Move the cursor backward, using cached per-frame snapshots inside the active segment. |

The lowest-level API is `RewindController`:

```java
RewindController controller = gameplayMode.getRewindController();

controller.seekTo(900);       // restore nearest keyframe and replay to frame 900
controller.stepBackward();    // move back one frame, clamped to available history
controller.step();            // step forward using the installed InputSource

int frame = controller.currentFrame();
int earliest = controller.earliestAvailableFrame();
```

Most UI code should use `PlaybackController` instead:

```java
PlaybackController playback = gameplayMode.getPlaybackController();

playback.pause();
playback.stepBackwardOnce();
playback.stepForwardOnce();
playback.play();
```

## Limits And Guarantees

Rewind is deterministic only for state captured by registered
`RewindSnapshottable` adapters or derived from captured state on the next forward
frame. The current covered state includes:

- Camera, timers, game state, RNG, fades, oscillation, water, parallax, and solid
  execution state.
- Playable sprites, CPU sidekick state, and sidekick follow-history.
- Object manager placement state, slot inventory, per-object state, dynamic object
  entries, and restorable child/projectile state.
- Rings, collected-ring bitsets, sparkle state, and lost-ring state.
- Level event state, level layout state, and mutation-pipeline pending work.
- Runtime-owned zone state, palette ownership, animated tile channels, special
  render effects, advanced render modes, and S2 PLC art progress.

Known limitations:

- Audio state is cosmetic and not captured.
- OpenGL/VRAM state is not captured. Rendering is re-derived after restore.
- Level/act changes reset the rewind buffer, so seeks cannot cross act boundaries.
- Death can be rewound until the level reset commits at the end of the death flow.
  Once the level reload boundary is reached, the old buffer is gone.
- Some fields are deliberately annotated `@RewindTransient` because they are derived,
  structural, or live object links. Fields annotated `@RewindDeferred` are known
  synchronization risks that need explicit identity/value codecs before they are
  treated as fully covered.

## Keyframe Interval

The keyframe interval is the number of forward frames between stored full snapshots.
When seeking to frame `F`, the controller restores the nearest keyframe `K <= F` and
replays forward to `F`.

| Interval | Worst replay after restore | Memory use | Seek responsiveness |
| ---: | ---: | --- | --- |
| 60 | 59 frames | lowest | lowest |
| 30 | 29 frames | about 2x interval-60 | better |
| 15 | 14 frames | about 4x interval-60 | best |

For the current S2 EHZ1 benchmark trace, 1200 frames of retained data measured:

| Interval | Stored keyframes | Retained bytes | Bytes per keyframe |
| ---: | ---: | ---: | ---: |
| 60 | 21 | 123,544 | 5,883 |
| 30 | 41 | 232,472 | 5,670 |
| 15 | 81 | 449,232 | 5,546 |

For a 10-minute act budget of 36,000 frames, this projects to roughly:

- Interval 60: 601 keyframes, about 3.37 MiB.
- Interval 30: 1201 keyframes, about 6.49 MiB.
- Interval 15: 2401 keyframes, about 12.70 MiB.

The practical default is interval 60. Use interval 30 if live scrubbing latency
becomes visible under heavier S3K object loads. Interval 15 is useful for stress
testing and very responsive debugging, but it spends more history memory.

## Running The Rewind Tests

Run the normal rewind suite:

```bash
mvn -Dmse=off "-Dtest=*Rewind*" test
```

`RewindBenchmark` is opt-in so default test runs stay fast:

```bash
mvn -Dmse=off "-Dtest=RewindBenchmark" \
  "-Dopenggf.rewind.benchmark.run=true" test
```

To compare keyframe intervals:

```bash
mvn -Dmse=off "-Dtest=RewindBenchmark" \
  "-Dopenggf.rewind.benchmark.run=true" \
  "-Dopenggf.rewind.benchmark.keyframeInterval=30" test
```

The benchmark writes `target/rewind-benchmark-results.json` and prints:

- Forward playback overhead with rewind off/on.
- Capture and restore cost.
- Cold seek cost.
- Hot held-rewind cost within and across segments.
- Retained resident-size estimate by subsystem.
- Long-tail determinism result, currently expected to stay clean for 1200 frames.

## Technical Architecture

The main classes live under `com.openggf.game.rewind`:

| Type | Purpose |
| --- | --- |
| `RewindController` | Public seek/step API. Restores keyframes, replays forward, and owns the segment cache. |
| `PlaybackController` | UI-oriented state machine over the controller. |
| `InputSource` | Supplies deterministic per-frame inputs. Trace mode uses `TraceInputSource`; live mode will need a recorder-backed implementation. |
| `EngineStepper` | Runs one engine frame for a supplied input sample. |
| `KeyframeStore` | Maps frame numbers to `CompositeSnapshot`s. Current implementation is in-memory. |
| `SegmentCache` | Expands one keyframe segment into per-frame snapshots for cheap held rewind. |
| `RewindRegistry` | Ordered registry of subsystem `RewindSnapshottable` adapters. |
| `CompositeSnapshot` | Per-frame map from stable subsystem key to immutable snapshot record. |

The controller never runs the game backward. It always restores an earlier state and
then advances forward using the same simulation path as normal play. This is the
central determinism guarantee: if seek+replay diverges from original forward play,
some synchronization-relevant state is missing, restored incorrectly, or derived from
an untracked source.

## Adding Rewind Coverage

When adding a new gameplay-scoped subsystem, decide whether its state is:

- Captured directly: add a snapshot record and a `RewindSnapshottable`.
- Derived: mark structural/derived fields with `@RewindTransient` and ensure the
  next forward frame recreates them from captured state.
- Deferred: annotate with `@RewindDeferred` only when a stable identity/value codec
  is required but not implemented yet.

For object and badnik work, synchronization-relevant fields include routine/state
bytes, timers, velocities, phase counters, child/projectile spawn state, RNG-derived
choices, collision latches that affect future frames, and any player/object link that
cannot be cheaply re-derived. Do not store raw live object references in snapshots;
store stable identities such as object slots, spawn records, player role, or explicit
value records.

Before considering a new subsystem covered, run:

```bash
mvn -Dmse=off "-Dtest=*Rewind*" test
mvn -Dmse=off "-Dtest=RewindBenchmark" \
  "-Dopenggf.rewind.benchmark.run=true" test
```

If the benchmark reports a divergent key, treat it as a real coverage gap unless the
diff comparator itself is demonstrably wrong.
