# S3K Bug List

Last updated: 2026-03-25

## Open Bugs

- [ ] AIZ rope swing: jumping off the rope swing for the first time activates Quick Shield immediately.
- [ ] AIZ water launcher: the object that launches Sonic over the water does not launch him far enough.
- [ ] AIZ water rendering: no water surface sprites are visible.
- [ ] AIZ water effects: Act 1 has no water effect before the fire sequence, while post-fire shows the wavy effect. Verify which state is ROM-accurate.
- [ ] S3K invincibility: no invincibility sprite is rendered. This may be intentional for now, but it should be tracked explicitly.
- [x] AIZ loop before the hollow log: weird physics behavior occurs on the loop. **(Fixed: restore ROM-aligned ground wall collision guard when no level is loaded so shared collision logic no longer regresses tests)**
- [ ] AIZ1 mid-act transition: weird snapping/teleportation occurs after the transition once the player goes past the first set of spikes.
- [x] Regression: S3K big rings no longer send the player to a special stage. **(Fixed: FadeManager identity mismatch after master title screen exit)**
- [ ] S3K big rings should only be interactable after their initial growing animation finishes.
- [x] S3K death flow regression: dying fades out the music but does not trigger the level reload. This may share a root cause with special stages not starting. **(Fixed: FadeManager identity mismatch — UiRenderPipeline was not updated to the runtime's FadeManager)**

## Notes

- This list is intended for today's S3K/AIZ parity work.
- Items with uncertain expected behavior should be verified against `docs/skdisasm/` and the original ROM before tuning constants or timings.
