# Sonic 1 Object Implementation Checklist

Generated: 2026-02-09 23:03:25

## Summary

- **Total unique objects found:** 81
- **Implemented:** 40 (49.4%)
- **Unimplemented:** 41 (50.6%)

## Implemented Objects

| ID | Name | Total Uses | Zones |
|----|------|------------|-------|
| 0x0D | Signpost | 13 | GHZ1, GHZ2, LZ1, LZ2, MZ1, MZ2, SLZ1, SLZ2, SYZ1, SYZ2, SBZ1, SBZ2 |
| 0x11 | Bridge | 11 | GHZ1, GHZ2, GHZ3 |
| 0x18 | Platform | 96 | GHZ1, GHZ2, GHZ3, SLZ2, SLZ3, SYZ1, SYZ2, SYZ3 |
| 0x1A | CollapsingLedge | 19 | GHZ1, GHZ2, GHZ3 |
| 0x1C | Scenery | 41 | GHZ1, GHZ2, GHZ3, SLZ1, SLZ2, SLZ3 |
| 0x1F | Crabmeat | 43 | GHZ1, GHZ2, GHZ3, SYZ1, SYZ2, SYZ3 |
| 0x22 | BuzzBomber | 78 | GHZ1, GHZ2, GHZ3, MZ1, MZ2, MZ3, SYZ1, SYZ2, SYZ3 |
| 0x25 | Ring | 802 | GHZ1, GHZ2, GHZ3, LZ1, LZ2, LZ3, MZ1, MZ2, MZ3, SLZ1, SLZ2, SLZ3, SYZ1, SYZ2, SYZ3, SBZ1, SBZ2 |
| 0x26 | Monitor | 199 | GHZ1, GHZ2, GHZ3, LZ1, LZ2, LZ3, MZ1, MZ2, MZ3, SLZ1, SLZ2, SLZ3, SYZ1, SYZ2, SYZ3, SBZ1, SBZ2 |
| 0x36 | Spikes | 190 | GHZ1, GHZ2, GHZ3, LZ1, LZ2, LZ3, MZ1, MZ2, MZ3 |
| 0x3B | Rock | 25 | GHZ1, GHZ2, GHZ3 |
| 0x2B | Chopper | 14 | GHZ1, GHZ2 |
| 0x2F | MzLargeGrassyPlatform | 37 | MZ1, MZ2, MZ3 |
| 0x40 | Motobug | 15 | GHZ1, GHZ2, GHZ3 |
| 0x41 | Spring | 150 | GHZ1, GHZ2, GHZ3, LZ1, LZ2, LZ3, MZ2, MZ3, SLZ1, SLZ2, SLZ3, SYZ1, SYZ2, SYZ3, SBZ1, SBZ2 |
| 0x42 | Newtron | 32 | GHZ1, GHZ2, GHZ3 |
| 0x44 | EdgeWalls | 74 | GHZ1, GHZ2, GHZ3 |
| 0x79 | Lamppost | 23 | GHZ1, GHZ2, GHZ3, LZ1, LZ2, LZ3, MZ1, MZ2, MZ3, SLZ3, SYZ1, SYZ2, SYZ3, SBZ1 |
| 0x49 | WaterfallSound | 11 | GHZ1, GHZ2, GHZ3 |
| 0x3C | BreakableWall | 26 | GHZ2, GHZ3, SLZ1, SLZ3 |
| 0x4B | GiantRing | 11 | GHZ1, GHZ2, LZ1, LZ2, MZ1, MZ2, SLZ1, SLZ2, SYZ1, SYZ2 |
| 0x15 | SwingingPlatform | 26 | GHZ2, GHZ3, MZ2, MZ3, SLZ3, SBZ2 |
| 0x17 | SpikedPoleHelix | 4 | GHZ3 |
| 0x3D | GHZBoss | 1 | GHZ3 |
| 0x3E | EggPrison | 10 | GHZ3, LZ3, MZ3, SLZ3, SYZ3 |
| 0x7D | HiddenBonus | 60 | GHZ1, GHZ2, LZ1, LZ2, MZ1, MZ2, SLZ1, SLZ2, SYZ1, SYZ2, SBZ1 |
| 0x54 | LavaTag | 48 | MZ1, MZ2, MZ3 |
| 0x46 | MzBrick | 99 | MZ1, MZ2, MZ3 |
| 0x55 | Batbrain | 37 | MZ1, MZ2, MZ3 |
| 0x78 | Caterkiller | 37 | MZ1, MZ2, MZ3, SBZ1, SBZ2 |
| 0x51 | SmashBlock | 30 | MZ2, MZ3 |
| 0x30 | MzGlassBlock | 14 | MZ1, MZ2, MZ3 |
| 0x31 | ChainedStomper | 23 | MZ1, MZ2, MZ3 |
| 0x32 | Button | 38 | LZ1, LZ2, LZ3, MZ1, MZ2, MZ3, SYZ1, SYZ3, SBZ1, SBZ2 |
| 0x33 | PushBlock | 6 | MZ1, MZ2, MZ3 |
| 0x52 | MovingBlock | 17 | LZ1, MZ1, MZ2, MZ3, SBZ1, SBZ2 |
| 0x71 | InvisibleBarrier | 88 | LZ1, LZ3, MZ1, MZ2, MZ3, SYZ2, SYZ3, SBZ1, SBZ2, SBZ3 |
| 0x13 | LavaBallMaker | 51 | MZ1, MZ2, MZ3, SLZ1, SLZ2, SLZ3 |

## Unimplemented Objects (By Usage)

| ID | Category | Name | Total Uses | Zones |
|----|----------|------|------------|-------|
| 0x56 | Object | S1_Obj_56 | 224 | LZ1, LZ2, LZ3, SLZ2, SLZ3, SYZ1, SYZ2, SYZ3 |
| 0x5F | Badnik | Bomb | 99 | SLZ1, SLZ2, SLZ3, SBZ1, SBZ2 |
| 0x5A | Object | S1_Obj_5A | 68 | SLZ1, SLZ2, SLZ3 |
| 0x6C | Object | S1_Obj_6C | 64 | SBZ1, SBZ2 |
| 0x65 | Object | S1_Obj_65 | 60 | LZ1, LZ2, LZ3 |
| 0x28 | Object | S1_Obj_28 | 57 | FZ1 |
| 0x69 | Object | S1_Obj_69 | 56 | SBZ1, SBZ2 |
| 0x64 | Object | S1_Obj_64 | 52 | LZ1, LZ2, LZ3 |
| 0x6E | Object | S1_Obj_6E | 52 | SBZ1, SBZ2 |
| 0x2D | Badnik | Burrobot | 50 | LZ1, LZ2, LZ3 |
| 0x53 | Object | S1_Obj_53 | 45 | MZ3, SLZ1, SLZ2, SLZ3, SBZ1, SBZ2 |
| 0x47 | Object | Bumper | 43 | SYZ1, SYZ2, SYZ3 |
| 0x61 | Object | S1_Obj_61 | 43 | LZ1, LZ2, LZ3 |
| 0x6D | Object | S1_Obj_6D | 42 | SBZ1, SBZ2 |
| 0x5D | Object | S1_Obj_5D | 36 | SLZ1, SLZ2, SLZ3 |
| 0x63 | Object | S1_Obj_63 | 36 | LZ1, LZ2, LZ3 |
| 0x58 | Object | S1_Obj_58 | 34 | SYZ1, SYZ2, SYZ3 |
| 0x60 | Badnik | Orbinaut | 34 | LZ1, LZ2, LZ3, SLZ1, SLZ2, SLZ3 |
| 0x16 | Object | S1_Obj_16 | 28 | LZ1, LZ2, LZ3 |
| 0x5B | Object | S1_Obj_5B | 23 | SLZ1, SLZ2, SLZ3 |
| 0x2C | Badnik | Jaws | 22 | LZ1, LZ2, LZ3 |
| 0x57 | Object | S1_Obj_57 | 22 | LZ1, LZ2, LZ3, SYZ1, SYZ2, SYZ3 |
| 0x12 | Object | S1_Obj_12 | 21 | SYZ1, SYZ2, SYZ3 |
| 0x68 | Object | S1_Obj_68 | 20 | SBZ2 |
| 0x6B | Object | S1_Obj_6B | 17 | SBZ1, SBZ2 |
| 0x59 | Object | S1_Obj_59 | 16 | SLZ1, SLZ2, SLZ3 |
| 0x5E | Object | Seesaw | 16 | SLZ2, SLZ3 |
| 0x2A | Object | S1_Obj_2A | 14 | SBZ1, SBZ2 |
| 0x6A | Object | S1_Obj_6A | 14 | SBZ1, SBZ2 |
| 0x70 | Object | S1_Obj_70 | 12 | SBZ1 |
| 0x1E | Object | S1_Obj_1E | 10 | SBZ1, SBZ2 |
| 0x50 | Badnik | Yadrin | 10 | SYZ1, SYZ2, SYZ3 |
| 0x62 | Object | S1_Obj_62 | 9 | LZ1, LZ2, LZ3 |
| 0x67 | Object | S1_Obj_67 | 8 | SBZ2 |
| 0x72 | Object | S1_Obj_72 | 8 | SBZ2 |
| 0x6F | Object | S1_Obj_6F | 6 | SBZ1 |
| 0x0B | Object | S1_Obj_0B | 5 | LZ3 |
| 0x43 | Object | S1_Obj_43 | 4 | SYZ1, SYZ2 |
| 0x4C | Object | S1_Obj_4C | 4 | MZ2, MZ3 |
| 0x5C | Object | S1_Obj_5C | 3 | SLZ1, SLZ2, SLZ3 |
| 0x0C | Object | S1_Obj_0C | 2 | LZ2, LZ3 |
| 0x66 | Object | S1_Obj_66 | 2 | SBZ1 |
| 0x4E | Object | S1_Obj_4E | 1 | MZ2 |

---

## By Zone

### Green Hill Zone

#### Act 1

Total: 214 objects | Implemented: 20 | Unimplemented: 0

**Badniks:**
- [x] 0x1F Crabmeat (x3) [0x00]
- [x] 0x22 BuzzBomber (x11) [0x00]
- [x] 0x2B Chopper (x5) [0x00]
- [x] 0x40 Motobug (x3) [0x00]
- [x] 0x42 Newtron (x10) [0x00, 0x01]

**Objects:**
- [x] 0x0D Signpost (x1) [0x00]
- [x] 0x11 Bridge (x3) [0x0C]
- [x] 0x18 Platform (x20) [5 subtypes]
- [x] 0x1A CollapsingLedge (x3) [0x00, 0x01]
- [x] 0x1C Scenery (x6) [0x03]
- [x] 0x25 Ring (x82) [8 subtypes]
- [x] 0x26 Monitor (x10) [0x04, 0x05, 0x06]
- [x] 0x36 Spikes (x23) [0x00, 0x20]
- [x] 0x3B Rock (x7) [0x00]
- [x] 0x41 Spring (x4) [0x02]
- [x] 0x44 EdgeWalls (x12) [0x00, 0x01, 0x02]
- [x] 0x49 WaterfallSound (x3) [0x00]
- [x] 0x4B GiantRing (x1) [0x00]
- [x] 0x79 Lamppost (x2) [0x01, 0x02]
- [x] 0x7D HiddenBonus (x5) [0x01, 0x02, 0x03]

#### Act 2

Total: 244 objects | Implemented: 22 | Unimplemented: 0

**Badniks:**
- [x] 0x1F Crabmeat (x4) [0x00]
- [x] 0x22 BuzzBomber (x12) [0x00]
- [x] 0x2B Chopper (x9) [0x00]
- [x] 0x40 Motobug (x6) [0x00]
- [x] 0x42 Newtron (x11) [0x00, 0x01]

**Objects:**
- [x] 0x0D Signpost (x1) [0x00]
- [x] 0x11 Bridge (x4) [0x0C]
- [x] 0x15 SwingingPlatform (x3) [0x06, 0x08]
- [x] 0x18 Platform (x9) [0x01, 0x03, 0x0A]
- [x] 0x1A CollapsingLedge (x4) [0x00, 0x01]
- [x] 0x1C Scenery (x8) [0x03]
- [x] 0x25 Ring (x74) [11 subtypes]
- [x] 0x26 Monitor (x10) [5 subtypes]
- [x] 0x36 Spikes (x33) [4 subtypes]
- [x] 0x3B Rock (x11) [0x00]
- [x] 0x3C BreakableWall (x6) [0x00, 0x01, 0x02]
- [x] 0x41 Spring (x6) [0x10, 0x00, 0x02]
- [x] 0x44 EdgeWalls (x22) [5 subtypes]
- [x] 0x49 WaterfallSound (x4) [0x00]
- [x] 0x4B GiantRing (x1) [0x00]
- [x] 0x79 Lamppost (x1) [0x01]
- [x] 0x7D HiddenBonus (x5) [0x01, 0x02, 0x03]

#### Act 3

Total: 286 objects | Implemented: 21 | Unimplemented: 0

**Badniks:**
- [x] 0x1F Crabmeat (x3) [0x00]
- [x] 0x22 BuzzBomber (x24) [0x00]
- [x] 0x40 Motobug (x6) [0x00]
- [x] 0x42 Newtron (x11) [0x00, 0x01]

**Bosses:**
- [x] 0x3D GHZBoss (x1) [0x00]

**Objects:**
- [x] 0x11 Bridge (x4) [0x0C]
- [x] 0x15 SwingingPlatform (x2) [0x07, 0x08]
- [x] 0x17 SpikedPoleHelix (x4) [0x10]
- [x] 0x18 Platform (x17) [6 subtypes]
- [x] 0x1A CollapsingLedge (x12) [0x00, 0x01]
- [x] 0x1C Scenery (x8) [0x03]
- [x] 0x25 Ring (x74) [6 subtypes]
- [x] 0x26 Monitor (x20) [5 subtypes]
- [x] 0x36 Spikes (x19) [5 subtypes]
- [x] 0x3B Rock (x7) [0x00]
- [x] 0x3C BreakableWall (x12) [0x00, 0x01, 0x02]
- [x] 0x3E EggPrison (x2) [0x00, 0x01]
- [x] 0x41 Spring (x13) [0x10, 0x02]
- [x] 0x44 EdgeWalls (x40) [6 subtypes]
- [x] 0x49 WaterfallSound (x4) [0x00]
- [x] 0x79 Lamppost (x4) [4 subtypes]

### Labyrinth Zone

#### Act 1

Total: 189 objects | Implemented: 10 | Unimplemented: 12

**Badniks:**
- [ ] 0x2C Jaws (x8) [4 subtypes]
- [ ] 0x2D Burrobot (x21) [0x00]
- [ ] 0x60 Orbinaut (x1) [0x00]

**Objects:**
- [x] 0x0D Signpost (x1) [0x00]
- [ ] 0x16 S1_Obj_16 (x8) [0x00, 0x02]
- [x] 0x25 Ring (x29) [7 subtypes]
- [x] 0x26 Monitor (x5) [0x04, 0x06]
- [x] 0x32 Button (x10) [10 subtypes]
- [x] 0x36 Spikes (x18) [5 subtypes]
- [x] 0x41 Spring (x2) [0x00]
- [x] 0x4B GiantRing (x1) [0x00]
- [x] 0x52 MovingBlock (x1) [0x07]
- [ ] 0x56 S1_Obj_56 (x10) [10 subtypes]
- [ ] 0x57 S1_Obj_57 (x4) [0xD4, 0xD5, 0xB5]
- [ ] 0x61 S1_Obj_61 (x21) [0x30, 0x13, 0x27]
- [ ] 0x62 S1_Obj_62 (x2) [0x01]
- [ ] 0x63 S1_Obj_63 (x13) [0x80, 0x81, 0x7F]
- [ ] 0x64 S1_Obj_64 (x16) [0x80, 0x81]
- [ ] 0x65 S1_Obj_65 (x8) [0x07, 0x08, 0x09]
- [x] 0x71 InvisibleBarrier (x4) [0x31]
- [x] 0x79 Lamppost (x1) [0x01]
- [x] 0x7D HiddenBonus (x5) [0x01, 0x02, 0x03]

#### Act 2

Total: 138 objects | Implemented: 8 | Unimplemented: 13

**Badniks:**
- [ ] 0x2C Jaws (x7) [4 subtypes]
- [ ] 0x2D Burrobot (x4) [0x00]
- [ ] 0x60 Orbinaut (x4) [0x00]

**Objects:**
- [ ] 0x0C S1_Obj_0C (x1) [0x02]
- [x] 0x0D Signpost (x1) [0x00]
- [ ] 0x16 S1_Obj_16 (x6) [0x00, 0x02]
- [x] 0x25 Ring (x15) [6 subtypes]
- [x] 0x26 Monitor (x9) [0x04, 0x05, 0x06]
- [x] 0x32 Button (x3) [0x00, 0x01, 0x02]
- [x] 0x36 Spikes (x24) [0x00, 0x30, 0x01]
- [x] 0x41 Spring (x2) [0x10, 0x00]
- [x] 0x4B GiantRing (x1) [0x00]
- [ ] 0x56 S1_Obj_56 (x3) [0xF0, 0xF1, 0xE2]
- [ ] 0x57 S1_Obj_57 (x5) [4 subtypes]
- [ ] 0x61 S1_Obj_61 (x3) [0x01, 0x13]
- [ ] 0x62 S1_Obj_62 (x2) [0x02, 0x04]
- [ ] 0x63 S1_Obj_63 (x10) [0x82, 0x83, 0x7F]
- [ ] 0x64 S1_Obj_64 (x15) [0x80, 0x81, 0x82]
- [ ] 0x65 S1_Obj_65 (x17) [6 subtypes]
- [x] 0x79 Lamppost (x1) [0x01]
- [x] 0x7D HiddenBonus (x5) [0x01, 0x02, 0x03]

#### Act 3

Total: 245 objects | Implemented: 8 | Unimplemented: 13

**Badniks:**
- [ ] 0x2C Jaws (x7) [0x08, 0x0C]
- [ ] 0x2D Burrobot (x25) [0x00]
- [ ] 0x60 Orbinaut (x1) [0x00]

**Objects:**
- [ ] 0x0B S1_Obj_0B (x5) [0x04]
- [ ] 0x0C S1_Obj_0C (x1) [0x02]
- [ ] 0x16 S1_Obj_16 (x14) [0x00, 0x02]
- [x] 0x25 Ring (x6) [4 subtypes]
- [x] 0x26 Monitor (x17) [4 subtypes]
- [x] 0x32 Button (x10) [10 subtypes]
- [x] 0x36 Spikes (x39) [5 subtypes]
- [x] 0x3E EggPrison (x2) [0x00, 0x01]
- [x] 0x41 Spring (x3) [0x00]
- [ ] 0x56 S1_Obj_56 (x9) [9 subtypes]
- [ ] 0x57 S1_Obj_57 (x8) [5 subtypes]
- [ ] 0x61 S1_Obj_61 (x19) [0x30, 0x01, 0x27]
- [ ] 0x62 S1_Obj_62 (x5) [0x03]
- [ ] 0x63 S1_Obj_63 (x13) [0x84, 0x85, 0x7F]
- [ ] 0x64 S1_Obj_64 (x21) [0x80, 0x81]
- [ ] 0x65 S1_Obj_65 (x35) [9 subtypes]
- [x] 0x71 InvisibleBarrier (x3) [0x31, 0x11]
- [x] 0x79 Lamppost (x2) [0x01, 0x02]

### Marble Zone

#### Act 1

Total: 145 objects | Implemented: 19 | Unimplemented: 1

**Badniks:**
- [x] 0x22 BuzzBomber (x4) [0x00]
- [x] 0x55 Batbrain (x5) [0x00]
- [x] 0x78 Caterkiller (x3) [0x00]

**Objects:**
- [x] 0x0D Signpost (x1) [0x00]
- [x] 0x13 LavaBallMaker (x4) [0x30, 0x41, 0x42]
- [x] 0x25 Ring (x35) [5 subtypes]
- [x] 0x26 Monitor (x10) [0x02, 0x04, 0x06]
- [x] 0x2F MzLargeGrassyPlatform (x22) [8 subtypes]
- [x] 0x30 MzGlassBlock (x5) [0x01, 0x02]
- [x] 0x31 ChainedStomper (x4) [4 subtypes]
- [x] 0x32 Button (x1) [0x80]
- [x] 0x33 PushBlock (x1) [0x00]
- [x] 0x36 Spikes (x2) [0x01, 0x12]
- [x] 0x46 MzBrick (x26) [4 subtypes]
- [x] 0x4B GiantRing (x1) [0x00]
- [x] 0x52 MovingBlock (x2) [0x41]
- [x] 0x54 LavaTag (x10) [0x01, 0x02]
- [x] 0x71 InvisibleBarrier (x3) [0x31, 0x11]
- [x] 0x79 Lamppost (x1) [0x01]
- [x] 0x7D HiddenBonus (x5) [0x01, 0x02, 0x03]

#### Act 2

Total: 198 objects | Implemented: 21 | Unimplemented: 4

**Badniks:**
- [x] 0x22 BuzzBomber (x5) [0x00]
- [x] 0x55 Batbrain (x11) [0x00]
- [x] 0x78 Caterkiller (x10) [0x00]

**Objects:**
- [x] 0x0D Signpost (x1) [0x00]
- [x] 0x13 LavaBallMaker (x10) [5 subtypes]
- [x] 0x15 SwingingPlatform (x4) [0x04, 0x05]
- [x] 0x25 Ring (x23) [5 subtypes]
- [x] 0x26 Monitor (x11) [4 subtypes]
- [x] 0x2F MzLargeGrassyPlatform (x10) [7 subtypes]
- [x] 0x30 MzGlassBlock (x2) [0x14, 0x04]
- [x] 0x31 ChainedStomper (x4) [0x11, 0x12]
- [x] 0x32 Button (x2) [0x00, 0x01]
- [x] 0x33 PushBlock (x3) [0x00, 0x81]
- [x] 0x36 Spikes (x9) [4 subtypes]
- [x] 0x41 Spring (x1) [0x10]
- [x] 0x46 MzBrick (x39) [0x00]
- [x] 0x4B GiantRing (x1) [0x00]
- [ ] 0x4C S1_Obj_4C (x3) [0x01]
- [ ] 0x4E S1_Obj_4E (x1) [0x00]
- [x] 0x51 SmashBlock (x17) [0x00]
- [x] 0x52 MovingBlock (x4) [0x41, 0x02]
- [x] 0x54 LavaTag (x17) [0x00, 0x01, 0x02]
- [x] 0x71 InvisibleBarrier (x4) [0x11, 0x31]
- [x] 0x79 Lamppost (x1) [0x05]
- [x] 0x7D HiddenBonus (x5) [0x01, 0x02, 0x03]

#### Act 3

Total: 232 objects | Implemented: 20 | Unimplemented: 3

**Badniks:**
- [x] 0x22 BuzzBomber (x3) [0x00]
- [x] 0x55 Batbrain (x21) [0x00]
- [x] 0x78 Caterkiller (x8) [0x00, 0x10]

**Objects:**
- [x] 0x13 LavaBallMaker (x18) [9 subtypes]
- [x] 0x15 SwingingPlatform (x3) [0x04]
- [x] 0x25 Ring (x31) [5 subtypes]
- [x] 0x26 Monitor (x7) [0x02, 0x04, 0x06]
- [x] 0x2F MzLargeGrassyPlatform (x5) [4 subtypes]
- [x] 0x30 MzGlassBlock (x7) [0x01, 0x02, 0x14]
- [x] 0x31 ChainedStomper (x15) [0x11, 0x02, 0x23]
- [x] 0x32 Button (x1) [0x01]
- [x] 0x33 PushBlock (x2) [0x00]
- [x] 0x36 Spikes (x23) [4 subtypes]
- [x] 0x3E EggPrison (x2) [0x00, 0x01]
- [x] 0x41 Spring (x1) [0x10]
- [x] 0x46 MzBrick (x34) [0x00, 0x02]
- [ ] 0x4C S1_Obj_4C (x1) [0x01]
- [x] 0x51 SmashBlock (x13) [0x00]
- [x] 0x52 MovingBlock (x3) [0x01, 0x02]
- [ ] 0x53 S1_Obj_53 (x5) [0x01]
- [x] 0x54 LavaTag (x21) [0x00, 0x01, 0x02]
- [x] 0x71 InvisibleBarrier (x6) [0x00, 0x31, 0x11]
- [x] 0x79 Lamppost (x2) [0x01, 0x02]

### Star Light Zone

#### Act 1

Total: 223 objects | Implemented: 8 | Unimplemented: 9

**Badniks:**
- [ ] 0x5F Bomb (x13) [0x00]
- [ ] 0x60 Orbinaut (x9) [0x02]

**Objects:**
- [x] 0x0D Signpost (x1) [0x00]
- [x] 0x13 LavaBallMaker (x11) [0x36, 0x37]
- [x] 0x1C Scenery (x11) [0x00]
- [x] 0x25 Ring (x54) [7 subtypes]
- [x] 0x26 Monitor (x15) [0x02, 0x05, 0x06]
- [x] 0x3C BreakableWall (x4) [0x01]
- [x] 0x41 Spring (x16) [0x00, 0x10, 0x02]
- [x] 0x4B GiantRing (x1) [0x00]
- [ ] 0x53 S1_Obj_53 (x10) [0x81]
- [ ] 0x59 S1_Obj_59 (x5) [4 subtypes]
- [ ] 0x5A S1_Obj_5A (x44) [8 subtypes]
- [ ] 0x5B S1_Obj_5B (x15) [0x00, 0x02]
- [ ] 0x5C S1_Obj_5C (x1) [0x00]
- [ ] 0x5D S1_Obj_5D (x8) [0x00, 0x02]
- [x] 0x7D HiddenBonus (x5) [0x01, 0x02, 0x03]

#### Act 2

Total: 187 objects | Implemented: 8 | Unimplemented: 11

**Badniks:**
- [ ] 0x5F Bomb (x20) [0x00]
- [ ] 0x60 Orbinaut (x7) [0x02]

**Objects:**
- [x] 0x0D Signpost (x1) [0x00]
- [x] 0x13 LavaBallMaker (x1) [0x17]
- [x] 0x18 Platform (x3) [0x03]
- [x] 0x1C Scenery (x1) [0x00]
- [x] 0x25 Ring (x65) [8 subtypes]
- [x] 0x26 Monitor (x8) [0x02, 0x05, 0x06]
- [x] 0x41 Spring (x15) [0x10, 0x00, 0x02]
- [x] 0x4B GiantRing (x1) [0x00]
- [ ] 0x53 S1_Obj_53 (x8) [0x81]
- [ ] 0x56 S1_Obj_56 (x8) [4 subtypes]
- [ ] 0x59 S1_Obj_59 (x5) [0x00, 0x03]
- [ ] 0x5A S1_Obj_5A (x16) [8 subtypes]
- [ ] 0x5B S1_Obj_5B (x3) [0x00]
- [ ] 0x5C S1_Obj_5C (x1) [0x00]
- [ ] 0x5D S1_Obj_5D (x14) [0x00, 0x01, 0x02]
- [ ] 0x5E Seesaw (x5) [0x00]
- [x] 0x7D HiddenBonus (x5) [0x01, 0x02, 0x03]

#### Act 3

Total: 250 objects | Implemented: 10 | Unimplemented: 10

**Badniks:**
- [ ] 0x5F Bomb (x48) [0x00]
- [ ] 0x60 Orbinaut (x12) [0x02]

**Objects:**
- [x] 0x13 LavaBallMaker (x7) [0x36, 0x37]
- [x] 0x15 SwingingPlatform (x2) [0x07]
- [x] 0x18 Platform (x4) [0x03]
- [x] 0x1C Scenery (x7) [0x00]
- [x] 0x25 Ring (x65) [11 subtypes]
- [x] 0x26 Monitor (x17) [4 subtypes]
- [x] 0x3C BreakableWall (x4) [0x01]
- [x] 0x3E EggPrison (x2) [0x00, 0x01]
- [x] 0x41 Spring (x11) [0x10, 0x00, 0x02]
- [ ] 0x53 S1_Obj_53 (x9) [0x81, 0x01]
- [ ] 0x56 S1_Obj_56 (x16) [4 subtypes]
- [ ] 0x59 S1_Obj_59 (x6) [4 subtypes]
- [ ] 0x5A S1_Obj_5A (x8) [8 subtypes]
- [ ] 0x5B S1_Obj_5B (x5) [0x00, 0x02]
- [ ] 0x5C S1_Obj_5C (x1) [0x00]
- [ ] 0x5D S1_Obj_5D (x14) [0x00, 0x01, 0x02]
- [ ] 0x5E Seesaw (x11) [0x00, 0xFF]
- [x] 0x79 Lamppost (x1) [0x01]

### Spring Yard Zone

#### Act 1

Total: 193 objects | Implemented: 10 | Unimplemented: 8

**Badniks:**
- [x] 0x1F Crabmeat (x9) [0x00]
- [x] 0x22 BuzzBomber (x10) [0x00]
- [ ] 0x50 Yadrin (x3) [0x00]

**Objects:**
- [x] 0x0D Signpost (x1) [0x00]
- [ ] 0x12 S1_Obj_12 (x8) [0x00]
- [x] 0x18 Platform (x8) [4 subtypes]
- [x] 0x25 Ring (x42) [15 subtypes]
- [x] 0x26 Monitor (x6) [5 subtypes]
- [x] 0x32 Button (x2) [0x80, 0x00]
- [x] 0x41 Spring (x30) [5 subtypes]
- [ ] 0x43 S1_Obj_43 (x2) [0x00]
- [ ] 0x47 Bumper (x13) [0x00]
- [x] 0x4B GiantRing (x1) [0x00]
- [ ] 0x56 S1_Obj_56 (x41) [5 subtypes]
- [ ] 0x57 S1_Obj_57 (x2) [0x54]
- [ ] 0x58 S1_Obj_58 (x8) [4 subtypes]
- [x] 0x79 Lamppost (x2) [0x01, 0x02]
- [x] 0x7D HiddenBonus (x5) [0x01, 0x02, 0x03]

#### Act 2

Total: 230 objects | Implemented: 10 | Unimplemented: 8

**Badniks:**
- [x] 0x1F Crabmeat (x10) [0x00]
- [x] 0x22 BuzzBomber (x3) [0x00]
- [ ] 0x50 Yadrin (x5) [0x00]

**Objects:**
- [x] 0x0D Signpost (x2) [0x00]
- [ ] 0x12 S1_Obj_12 (x4) [0x00]
- [x] 0x18 Platform (x11) [6 subtypes]
- [x] 0x25 Ring (x67) [25 subtypes]
- [x] 0x26 Monitor (x3) [0x02, 0x04, 0x05]
- [x] 0x41 Spring (x24) [5 subtypes]
- [ ] 0x43 S1_Obj_43 (x2) [0x00]
- [ ] 0x47 Bumper (x13) [0x00]
- [x] 0x4B GiantRing (x2) [0x00]
- [ ] 0x56 S1_Obj_56 (x57) [4 subtypes]
- [ ] 0x57 S1_Obj_57 (x1) [0x54]
- [ ] 0x58 S1_Obj_58 (x12) [4 subtypes]
- [x] 0x71 InvisibleBarrier (x3) [0x11, 0x31]
- [x] 0x79 Lamppost (x1) [0x01]
- [x] 0x7D HiddenBonus (x10) [0x01, 0x02, 0x03]

#### Act 3

Total: 257 objects | Implemented: 10 | Unimplemented: 6

**Badniks:**
- [x] 0x1F Crabmeat (x14) [0x00]
- [x] 0x22 BuzzBomber (x6) [0x00]
- [ ] 0x50 Yadrin (x2) [0x00]

**Objects:**
- [ ] 0x12 S1_Obj_12 (x9) [0x00]
- [x] 0x18 Platform (x24) [5 subtypes]
- [x] 0x25 Ring (x46) [16 subtypes]
- [x] 0x26 Monitor (x11) [5 subtypes]
- [x] 0x32 Button (x2) [0x00, 0x0F]
- [x] 0x3E EggPrison (x2) [0x00, 0x01]
- [x] 0x41 Spring (x17) [0x00, 0x02, 0x12]
- [ ] 0x47 Bumper (x17) [0x00]
- [ ] 0x56 S1_Obj_56 (x80) [7 subtypes]
- [ ] 0x57 S1_Obj_57 (x2) [0x54]
- [ ] 0x58 S1_Obj_58 (x14) [5 subtypes]
- [x] 0x71 InvisibleBarrier (x9) [0x31, 0x13]
- [x] 0x79 Lamppost (x2) [0x01, 0x02]

### Scrap Brain Zone

#### Act 1

Total: 308 objects | Implemented: 9 | Unimplemented: 14

**Badniks:**
- [ ] 0x5F Bomb (x6) [0x00]
- [x] 0x78 Caterkiller (x9) [0x00]

**Objects:**
- [x] 0x0D Signpost (x1) [0x00]
- [ ] 0x1E S1_Obj_1E (x4) [0x06]
- [x] 0x25 Ring (x45) [9 subtypes]
- [x] 0x26 Monitor (x15) [0x04, 0x06]
- [ ] 0x2A S1_Obj_2A (x8) [0x00]
- [x] 0x32 Button (x4) [0x00, 0x01, 0x02]
- [x] 0x41 Spring (x3) [0x10, 0x00]
- [x] 0x52 MovingBlock (x6) [0x39]
- [ ] 0x53 S1_Obj_53 (x3) [0x01]
- [ ] 0x66 S1_Obj_66 (x2) [0x00, 0x02]
- [ ] 0x69 S1_Obj_69 (x25) [6 subtypes]
- [ ] 0x6A S1_Obj_6A (x3) [0x03]
- [ ] 0x6B S1_Obj_6B (x11) [5 subtypes]
- [ ] 0x6C S1_Obj_6C (x48) [4 subtypes]
- [ ] 0x6D S1_Obj_6D (x23) [0x43]
- [ ] 0x6E S1_Obj_6E (x17) [0x08]
- [ ] 0x6F S1_Obj_6F (x6) [6 subtypes]
- [ ] 0x70 S1_Obj_70 (x12) [0x00]
- [x] 0x71 InvisibleBarrier (x50) [7 subtypes]
- [x] 0x79 Lamppost (x2) [0x01, 0x02]
- [x] 0x7D HiddenBonus (x5) [0x01, 0x02, 0x03]

#### Act 2

Total: 292 objects | Implemented: 8 | Unimplemented: 14

**Badniks:**
- [ ] 0x5F Bomb (x12) [0x00]
- [x] 0x78 Caterkiller (x7) [0x00]

**Objects:**
- [x] 0x0D Signpost (x1) [0x00]
- [x] 0x15 SwingingPlatform (x12) [0x06, 0x07]
- [ ] 0x1E S1_Obj_1E (x6) [0x08]
- [x] 0x25 Ring (x49) [0x10, 0x12, 0x14]
- [x] 0x26 Monitor (x25) [8 subtypes]
- [ ] 0x2A S1_Obj_2A (x6) [0x00]
- [x] 0x32 Button (x3) [0x00, 0x01, 0x03]
- [x] 0x41 Spring (x2) [0x10]
- [x] 0x52 MovingBlock (x1) [0x28]
- [ ] 0x53 S1_Obj_53 (x10) [0x01]
- [ ] 0x67 S1_Obj_67 (x8) [0x40]
- [ ] 0x68 S1_Obj_68 (x20) [5 subtypes]
- [ ] 0x69 S1_Obj_69 (x31) [10 subtypes]
- [ ] 0x6A S1_Obj_6A (x11) [0x01, 0x02, 0x03]
- [ ] 0x6B S1_Obj_6B (x6) [5 subtypes]
- [ ] 0x6C S1_Obj_6C (x16) [7 subtypes]
- [ ] 0x6D S1_Obj_6D (x19) [0x43]
- [ ] 0x6E S1_Obj_6E (x35) [0x02, 0x04, 0x08]
- [x] 0x71 InvisibleBarrier (x4) [4 subtypes]
- [ ] 0x72 S1_Obj_72 (x8) [8 subtypes]

#### Act 3

Total: 2 objects | Implemented: 1 | Unimplemented: 0

**Objects:**
- [x] 0x71 InvisibleBarrier (x2) [0x31]

### Final Zone

#### Act 1

Total: 57 objects | Implemented: 0 | Unimplemented: 1

**Objects:**
- [ ] 0x28 S1_Obj_28 (x57) [10 subtypes]

