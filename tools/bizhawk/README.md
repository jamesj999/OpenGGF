# BizHawk Trace Recording

The canonical trace replay documentation now lives in:

- [`docs/guide/contributing/trace-replay.md`](../../docs/guide/contributing/trace-replay.md)

Use this folder for the recorder scripts and local BizHawk assets:

- `record_trace.bat` launches headless recording
- `s1_trace_recorder.lua` captures the ROM-side trace data
- `record_s1_credits_traces.bat` launches forced Sonic 1 credits-demo capture
- `s1_credits_trace_recorder.lua` records the built-in ending replays without a BK2

If you update the trace workflow, update the guide page above first so the contributor docs stay in
sync with the tools.
