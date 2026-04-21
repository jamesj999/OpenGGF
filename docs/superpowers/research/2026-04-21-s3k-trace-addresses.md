# Sonic 3K Trace Recorder RAM Addresses

This note resolves the Sonic 3 & Knuckles RAM labels the v3 trace recorder needs beyond the frozen counter matrix. All addresses below are 68K absolute RAM addresses from `docs/skdisasm/`; if the recorder reads BizHawk Genesis `mainmemory`, subtract `$FF0000` before indexing.

## Core RAM / OST Values

| Recorder field | S3K label(s) | Value | Evidence | Notes |
| --- | --- | --- | --- | --- |
| Game mode byte | `Game_mode` | abs `$FFFFF600` / mainmemory `$F600` | `docs/skdisasm/sonic3k.constants.asm:525-536`, `docs/superpowers/research/2026-04-19-trace-lag-model-matrix.md:90-104` | `Game_mode` is the first byte before the controller block that ends at `_tempF608`, so the mainmemory offset is `$F600`. |
| Player 1 SST base | `Player_1` | abs `$FFFFB400` / mainmemory `$B400` | `docs/skdisasm/sonic3k.constants.asm:283-304` | `RAM_start` begins at `$FFFF0000`; `Chunk_table` `$8000` + layout/header `$1000` + `Block_table` `$1800` + `HScroll_table` `$0200` + `Nem_code_table` `$0200` + `Sprite_table_input` `$0400` places `Object_RAM` at `$B400`, and `Player_1` is the first slot. |
| Player 2 SST base | `Player_2` | abs `$FFFFB44A` / mainmemory `$B44A` | `docs/skdisasm/sonic3k.constants.asm:303-305` | `object_size` is `$4A`, so `Player_2 = Player_1 + $4A`. |
| SST slot size | `object_size` | `$4A` bytes | `docs/skdisasm/sonic3k.constants.asm:303-323` | The object pool comment fixes the per-slot size. |
| Player 1 control-lock timer | `Player_1 + move_lock` | abs `$FFFFB432` / mainmemory `$B432` | `docs/skdisasm/sonic3k.constants.asm:61`, `docs/skdisasm/sonic3k.constants.asm:303-304` | `move_lock` is an OST field offset, not a standalone RAM label. Recorder reads `Player_1` base plus `$32`. |
| Player 2 control-lock timer | `Player_2 + move_lock` | abs `$FFFFB47C` / mainmemory `$B47C` | `docs/skdisasm/sonic3k.constants.asm:61`, `docs/skdisasm/sonic3k.constants.asm:304-305` | Useful for Sonic/Tails movies when verifying sidekick lock state during cutscenes. |
| Level-start flag | `Level_started_flag` | abs `$FFFFF711` / mainmemory `$F711` | `docs/skdisasm/sonic3k.constants.asm:627-629` | `Rings_manager_routine` is the preceding byte at `$F710`; `_unkF712` immediately follows, so `Level_started_flag` is `$F711`. |
| P1 input-lock byte | `Ctrl_1_locked` | abs `$FFFFF7CA` / mainmemory `$F7CA` | `docs/skdisasm/sonic3k.constants.asm:683-689` | This is the global cutscene/control lock byte, distinct from the per-player `move_lock` timer in the SST. |
| Apparent zone/act word | `Apparent_zone_and_act` | abs `$FFFFEE50` / mainmemory `$EE50` | `docs/skdisasm/sonic3k.constants.asm:386-388`, `docs/skdisasm/sonic3k.constants.asm:421` | `_unkEE8E` fixes a later anchor at `$EE8E`. Counting the intervening layout back from that anchor places `Apparent_zone_and_act` at `$EE50`. |
| Apparent zone byte | `Apparent_zone` | abs `$FFFFEE50` / mainmemory `$EE50` | `docs/skdisasm/sonic3k.constants.asm:386-388` | Always matches the actual zone in AIZ/HCZ. |
| Apparent act byte | `Apparent_act` | abs `$FFFFEE51` / mainmemory `$EE51` | `docs/skdisasm/sonic3k.constants.asm:386-388` | This is the ROM-backed act presentation byte the spec requires. The disassembly comment explicitly calls out the AIZ fire transition case. |
| AIZ fire-transition signal | `Events_fg_5` | abs `$FFFFEEC6` / mainmemory `$EEC6` | `docs/skdisasm/sonic3k.constants.asm:438-448`, `docs/skdisasm/sonic3k.constants.asm:421`, `docs/skdisasm/sonic3k.asm:104613-104639` | `_unkEE8E` again provides the anchor. `AIZ1BGE_Normal` branches into `AIZ1_AIZ2_Transition` when this word becomes non-zero. |
| Background-event scratch block | `Events_bg` | abs `$FFFFEED2` / mainmemory `$EED2` | `docs/skdisasm/sonic3k.constants.asm:449-459`, `docs/skdisasm/sonic3k.constants.asm:421` | Included because AIZ fire/handoff code writes several phase values into `Events_bg+$00/$02/$04`. |
| Current zone/act word | `Current_zone_and_act` | abs `$FFFFFE14` / mainmemory `$FE14` | `docs/skdisasm/sonic3k.constants.asm:790-793`, `docs/superpowers/research/2026-04-19-trace-lag-model-matrix.md:85-104` | `V_int_run_count` is already frozen at `$FE10`; `Current_zone_and_act` follows that longword at `$FE14`. |
| Current zone byte | `Current_zone` | abs `$FFFFFE14` / mainmemory `$FE14` | `docs/skdisasm/sonic3k.constants.asm:791-793` | Recorder should treat this as the authoritative zone byte. |
| Current act byte | `Current_act` | abs `$FFFFFE15` / mainmemory `$FE15` | `docs/skdisasm/sonic3k.constants.asm:791-793` | Recorder should treat this as the authoritative act byte. |
| Restart flag | `Restart_level_flag` | abs `$FFFFFE06` / mainmemory `$FE06` | `docs/skdisasm/sonic3k.constants.asm:780-782`, `docs/superpowers/research/2026-04-19-trace-lag-model-matrix.md:102-104`, `docs/skdisasm/sonic3k.asm:180637-180643` | `StartNewLevel` sets `Current_zone_and_act`, `Apparent_zone_and_act`, and `Restart_level_flag` together. |
| End-of-level active flag | `_unkFAA8` | abs `$FFFFFAA8` / mainmemory `$FAA8` | `docs/skdisasm/sonic3k.constants.asm:723-726`, `docs/skdisasm/sonic3k.asm:138248-138277`, `docs/skdisasm/sonic3k.asm:62702-62712` | This is the AIZ2 post-boss/results gate. The end boss waits for it to clear before starting the HCZ handoff walk-right sequence. |
| End-of-level completion flag | `End_of_level_flag` | abs `$FFFFFAAA` / mainmemory `$FAAA` | `docs/skdisasm/sonic3k.constants.asm:724-727`, `docs/skdisasm/sonic3k.asm:62702-62705` | Set when the act-2 results object finishes; useful as a secondary diagnostic field, but `_unkFAA8` is the stronger `hcz_handoff_begin` seam. |

## Checkpoint Signal Mapping

| Checkpoint | Signal | Value / edge | Evidence | Recorder note |
| --- | --- | --- | --- | --- |
| `intro_begin` | `Game_mode` | first movie frame with `Game_mode == $0C` | BK2 header + normal gameplay path | For this fixture, emit the checkpoint on the first recorded frame. Pre-level intro frames still use the same ROM/game session. |
| `gameplay_start` | `Level_started_flag` | `0 -> non-zero` | `docs/skdisasm/sonic3k.constants.asm:627-629` | This is the cleanest gameplay-entry byte for the recorder. |
| `aiz1_fire_transition_begin` | `Events_fg_5` | `0 -> non-zero` | `docs/skdisasm/sonic3k.asm:104613-104639` | `AIZ1BGE_Normal` branches to `AIZ1_AIZ2_Transition` exactly on this signal. |
| `aiz2_reload_resume` | `Current_zone_and_act`, `Apparent_act` | `Current_zone_and_act == $0001` while `Apparent_act == $00` | `docs/skdisasm/sonic3k.asm:104722-104770`, `docs/skdisasm/sonic3k.constants.asm:386-388` | This is the act-divergence seam the fixture spec cares about: actual act is 2, displayed act remains 1. |
| `hcz_handoff_begin` | `_unkFAA8` | `non-zero -> 0` during AIZ2 post-boss flow | `docs/skdisasm/sonic3k.asm:138248-138277`, `docs/skdisasm/sonic3k.asm:62702-62712` | This is the first frame where the AIZ2 end-boss object stops waiting on the capsule/results phase and begins the HCZ handoff cutscene. Track `Ctrl_1_locked` alongside it for diagnostics. |
| Later actual HCZ load edge | `Restart_level_flag`, `Current_zone_and_act`, `Apparent_zone_and_act` | `Restart_level_flag == 1` and both zone/act words become `$0100` | `docs/skdisasm/sonic3k.asm:180637-180643` | This is later than `hcz_handoff_begin`. Use it to confirm the handoff eventually resolves into HCZ1, not as the checkpoint itself. |

## AIZ Fire / HCZ Handoff Notes

- `Apparent_act` is ROM RAM, not an engine-only mirror. The disassembly explicitly documents the AIZ fire-transition case: actual act has already become act 2 while `Apparent_act` still reports act 1.
- `move_lock` is also not a standalone RAM label. It is an SST field offset (`$32`) that must be read from the player object base. For this movie, recorder diagnostics should at minimum sample `Player_1 + move_lock`; sampling `Player_2 + move_lock` is useful because the fixture is a Sonic/Tails run.
- The AIZ2 post-boss route has two distinct seams:
  - `hcz_handoff_begin`: `_unkFAA8` clears and the post-results walk-right cutscene begins.
  - actual level jump: `StartNewLevel($0100)` writes HCZ1 into `Current_zone_and_act` / `Apparent_zone_and_act` and raises `Restart_level_flag`.

## Emulator Pin

The recorder should treat this fixture as pinned to the emulator/core combination stored in the movie itself:

| Field | Value | Evidence | Notes |
| --- | --- | --- | --- |
| BizHawk version | `2.11` | `docs/BizHawk-2.11-win-x64/Movies/s3-aiz1&2-sonictails.bk2` -> `BizVersion.txt` = `Version 2.11` | Matches the local BizHawk install directory name. |
| Genesis core | `Genplus-gx` | BK2 `Header.txt` -> `Core Genplus-gx`; `SyncSettings.json` uses `BizHawk.Emulation.Cores.Consoles.Sega.gpgx.GPGX+GPGXSyncSettings` | Use this exact core for reproducible rerecording. |
| Platform | `GEN` | BK2 `Header.txt` | Confirms Genesis movie format. |
| ROM checksum string | `C5B1C655C19F462ADE0AC4E17A844D10` | BK2 `Header.txt` field named `SHA1` | Carry this exact non-empty header value into fixture metadata as `rom_checksum`. |

The BK2 header therefore already contains the pin the fixture needs:

```text
Core Genplus-gx
Platform GEN
emuVersion Version 2.11
GameName Sonic and Knuckles & Sonic 3 (W) [!]
SHA1 C5B1C655C19F462ADE0AC4E17A844D10
```
