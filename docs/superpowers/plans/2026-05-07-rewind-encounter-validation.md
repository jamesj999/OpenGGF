# Rewind Encounter Validation Catalog

This pass adds a small test-side encounter shape for validating engine
forward-only state against engine rewind+replay state. Trace recordings are
used only as deterministic input streams; the encounter assertions do not read
ROM trace state as an oracle.

Initial non-disabled catalog entry:

| ID | Game | Zone | Family | Mechanic | Window | Compared snapshot keys |
| --- | --- | --- | --- | --- | --- | --- |
| `s2-ehz1-early-traversal` | Sonic 2 | EHZ1 | `baseline-objects` | Trace-input traversal before torture-scale dynamic spawns | rewind frame 180, compare frame 300 | `camera`, `object-manager`, `rings`, `sprites` |

Incremental enabling path:

1. Add focused encounter entries by game, zone, object family, and mechanic
   before enabling broad torture coverage.
2. Prefer short windows around one mechanic: monitor break, badnik collision,
   platform ride, spring launch, bumper, boss phase, act boundary.
3. Keep `TestRewindTorture` disabled until the matching focused encounters for
   transient dynamic objects are stable. Its current comments remain the
   architectural checklist for enabling the stress patterns.
4. Keep lightweight pattern/bounds tests non-disabled; they validate schedule
   generation without depending on object snapshot coverage.
