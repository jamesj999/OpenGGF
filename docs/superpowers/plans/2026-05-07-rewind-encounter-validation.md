# Rewind Encounter Validation Catalog

This pass adds a small test-side encounter shape for validating engine
forward-only state against engine rewind+replay state. Trace recordings are
used only as deterministic input streams; the encounter assertions do not read
ROM trace state as an oracle.

Initial non-disabled catalog entries:

| ID | Game | Zone | Family | Mechanic | Window | Compared snapshot keys |
| --- | --- | --- | --- | --- | --- | --- |
| `s2-ehz1-early-traversal` | Sonic 2 | EHZ1 | `baseline-objects` | Trace-input traversal before torture-scale dynamic spawns | rewind frame 180, compare frame 300 | `camera`, `object-manager`, `rings`, `sprites` |
| `s2-ehz1-mid-run-traversal` | Sonic 2 | EHZ1 | `transient-dynamics` | Mid-run traversal crossing badnik kills, animals, points popups (single rewind) | rewind frame 180, compare frame 1500 | `camera`, `object-manager`, `rings`, `sprites` |

The `s2-ehz1-mid-run-traversal` scenario passes today: a single rewind+replay
matches the forward run across a 22-second window covering early-trace badnik
encounters. Slot drift surfaces in `TestRewindTorture` only after many rewinds
in succession, not from any one rewind window.

## Slot-drift mitigation progress

The `TestRewindTorture` checklist itemizes three architectural fixes; the
status as of this branch is:

1. **Capture live `usedSlots` BitSet directly.** Done. `ObjectManagerSnapshot`
   now stores the live `usedSlots.toLongArray()` instead of synthesizing a
   restorable subset, so the allocator's view at restore matches the reference
   run at the rewind point even when transient classes lack codecs.
2. **Add rewind codecs for transient dynamic objects.** Partial.
   `AnimalObjectInstance`, the `AbstractPointsObjectInstance` family
   (Sonic1/Sonic2/Sonic3k), and `ExplosionObjectInstance` are covered.
   `InvincibilityStarsObjectInstance` and `ShieldObjectInstance` remain — both
   are player-bound and require coordination with the existing post-restore
   power-up re-pin in `AbstractPlayableSprite#refreshPowerUpObjectsAfterRewindRestore`.
3. **Coordinate shield re-pin to honour the captured shield slot.** Pending,
   blocked on (2) for the shield codec.

`RewindObjectStateBlob` also needed content-aware `equals`/`hashCode` so the
diff helper doesn't report false divergence on byte-identical compact sidecar
blobs.

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
