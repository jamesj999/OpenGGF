# Game Status

Last updated: 2026-04-12 (v0.6.prerelease development)

This page describes the current state of each supported game. It is intended to set
expectations honestly -- what works well, what is incomplete, and what you might encounter.

---

## Sonic the Hedgehog (S1)

**Status: Broadly playable from start to finish.**

### What works

- All 7 zones (Green Hill through Final Zone) are loadable and playable.
- 6 boss fights implemented (GHZ, MZ, SYZ, LZ, SLZ, FZ).
- Special stages functional with emerald collection.
- Title screen, level select, title cards, and HUD.
- Labyrinth Zone water system with drowning mechanics, air bubbles, and underwater
  palettes.
- Per-zone level events (camera boundaries, zone-specific triggers).
- Ending sequence and credits.
- Demo playback.
- SMPS audio with S1-specific driver configuration.

### Known gaps

- Some objects and badniks may be missing or have minor behavior differences.
- Edge-case physics behaviors (specific slope interactions, push block scenarios) may
  differ from the original.
- SBZ2-to-FZ transition sequence may have minor visual differences.
- Special stage parity is functional but not pixel-perfect.

### Notable quirks

- S1 uses 256x256 metatiles (vs. S2/S3K's 128x128), which affects level layout
  granularity.
- Loop plane switching uses a different mechanism than S2 (coordinate-based triggers
  rather than object-based switchers).

---

## Sonic the Hedgehog 2 (S2)

**Status: Most complete. Playable from start to finish with minor gaps.**

### What works

- All zones playable: EHZ, CPZ, ARZ, CNZ, HTZ, MCZ, OOZ, MTZ (3 acts), SCZ, WFZ, DEZ.
- 9 boss fights: EHZ, CPZ, ARZ, CNZ, HTZ, MCZ, WFZ, and both DEZ bosses (Mecha Sonic
  and Death Egg Robot) plus the Robotnik escape sequence.
- 97.5% of objects implemented (117 of 120 unique object types).
- Special stages functional with emerald collection.
- Tails CPU AI follower with flight and input replay.
- Super Sonic with per-game physics.
- Title screen, level select, title cards, HUD.
- Complete credits and ending cutscene system.
- Water system for ARZ and CPZ.
- HTZ earthquake and lava systems.
- Per-zone level events across all zones.
- Demo playback.
- Full SMPS audio.

### Known gaps

- 3 unimplemented objects (see OBJECT_CHECKLIST.md for details).
- MTZ boss is implemented but may have minor accuracy issues.
- Some visual effects (screen distortion, specific palette transitions) may differ
  slightly from the original.
- Oil Ocean Zone oil surface behavior is partially implemented.

### Notable quirks

- S2 is the engine's reference game -- it has the most test coverage and the most
  refined implementations.
- Cross-game feature donation uses S2 as the default donor for sprites and spindash.

---

## Sonic 3 & Knuckles (S3K)

**Status: Expanding. AIZ is substantially playable and HCZ now has early gameplay coverage into HCZ2.**

### What works

- Angel Island Zone intro cutscene, Act 1 gameplay, miniboss defeat flow, signpost, and results.
- AIZ fire transition, Flying Battery bombing sequence, AIZ2 end boss, post-boss capsule flow,
  and AIZ-to-HCZ transition.
- HCZ water rush, conveyor, fan, block, door, water skim, miniboss, HCZ1-to-HCZ2 transition,
  and the HCZ2 moving-wall chase sequence work.
- Title screen and level select screen.
- Knuckles as a playable character, including glide/climb support.
- Blue Ball special stages and active bonus-stage work across Gumball, Glowing Sphere/Pachinko,
  and Slots.
- Shield system, water system, palette cycling, and expanding badnik/object coverage.
- Water state now restores correctly after returning from side stages.
- SMPS audio with S3K-specific driver configuration (Z80 bank-switching, DPCM).

### Known gaps

- S3K is not yet playable from start to finish.
- Non-AIZ/HCZ zones may load but still need major object, event, scroll, boss, and PLC parity work.
- Many S3K objects, badniks, and bosses are not yet implemented.
- Bonus stages are still in active parity work rather than final polish.
- S3K's more complex PLC/art loading system still has partial parity.
- No save/load system.

### Notable quirks

- S3K uses KosinskiM (Kosinski Moduled) compression, combined 1P+2P mapping tables,
  and a more complex Z80 sound driver than S1/S2.
- S3K remains the current development focus after v0.5; see ROADMAP.md for the v0.6+ direction.

---

## Experimental Tooling

### Level Editor Overlay

An experimental editor overlay is now available behind `EDITOR_ENABLED` in `config.json`.
When enabled, use `Shift+Tab` during gameplay to park the current playtest and enter the editor
overlay, then use the same shortcut to resume. The current snapshot supports:

- World cursor and grid navigation.
- Focused block and chunk previews.
- Early derive/edit flows for live level data.
- Resume and restart handling around editor playtests.

This is still a development tool rather than a polished end-user level editor.

---

## Cross-Game Features

The engine supports **cross-game feature donation**: a donor game provides player
sprites, spindash mechanics, and sound effects while you play a different base game.

| Feature | Status |
|---------|--------|
| S2 sprites in S1 | Working |
| S2 spindash in S1 | Working |
| Super Sonic cross-delegation | Working |
| S3K sprites in S1/S2 | Experimental |

Enable with `CROSS_GAME_FEATURES_ENABLED` and `CROSS_GAME_SOURCE` in config.json.
