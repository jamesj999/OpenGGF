# Game Status

Last updated: 2026-03-25 (post-v0.4.20260304)

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

**Status: Early. Angel Island Zone with intro cutscene and first-act gameplay.**

### What works

- Angel Island Zone Act 1 playable with gameplay objects.
- AIZ intro cutscene (biplane sequence).
- AIZ miniboss encounter.
- Hollow tree traversal and vine mechanics.
- Water system with underwater palettes.
- AIZ fire transition (Act 1 to Act 2 seamless zone change).
- Shield system (fire, electric, water) with PLC integration.
- Initial badnik implementations for AIZ.
- SMPS audio with S3K-specific driver configuration (Z80 bank-switching, DPCM).

### Known gaps

- Only Angel Island Zone has meaningful gameplay support. Other zones may load but are
  not playable.
- Many S3K objects, badniks, and bosses are not yet implemented.
- S3K's more complex PLC/art loading system has partial parity.
- No Knuckles character support.
- No save/load system.
- No special stages.
- Zone transitions beyond AIZ Act 1 -> Act 2 are not implemented.

### Notable quirks

- S3K uses KosinskiM (Kosinski Moduled) compression, combined 1P+2P mapping tables,
  and a more complex Z80 sound driver than S1/S2.
- S3K is the current development focus for v0.5 (see ROADMAP.md).

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
