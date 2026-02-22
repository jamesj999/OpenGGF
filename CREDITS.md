# Credits

This project uses documentation, tools, and reference implementations from many talented members of the Sonic hacking and emulation communities. 

**It would not have been possible without their hard work; we truly stand on the shoulders of giants.**

## Audio & Sound

| Contributor          | Contribution                                                                                                                    |
|----------------------|---------------------------------------------------------------------------------------------------------------------------------|
| **ValleyBell**       | SMPSPlay - SMPS music playback tool and libvgm integration <br/> <br/> https://github.com/ValleyBell/SMPSPlay                   |
| **Tweaker**          | Hacking CulT music hacking guide <br/> <br/> https://www.hacking-cult.org/?r/4/80                                               |
| **drx**              | Hacking CulT <br/> <br/> https://www.hacking-cult.org                                                                           |
| **jsgroth**          | "Emulating the YM2612" blog series - FM synthesis reference <br/> <br/> https://jsgroth.dev/blog/posts/emulating-ym2612-part-1/ |
| **Maxim**            | SN76489 PSG documentation (SMS Power!) <br/> <br/> https://www.smspower.org/Development/SN76489                                                                             |
| **Stephan Dittrich** | Gens YM2612 Java port (YM2612.java.example)                                                                                     |
| **Xeeynamo**         | SMPSPlay contributions (wave output, channel muting)                                                                            |
| **Eke-Eke**          | Genesis Plus GX - YM2612 and PSG emulation cores <br/><br/> https://github.com/ekeeke/Genesis-Plus-GX                           |
| **MAME Team**        | Sound emulation cores used by SMPSPlay                                                                                          |
| **libvgm**           | Audio output and emulation libraries                                                                                            |
| **flamewing**        | S3K Z80 sound driver documentation and bugfixes                                                                                 |
| **clownacy**         | SMPS sound driver disassembly work across S1, S2, and S3K                                                                       |
| **MarkeyJester**     | Original S3K Z80 sound driver disassembly                                                                                       |
| **Linncaki**         | S3K sound driver routines, pointers, and data identification                                                                    |
| **Xenowhirl**        | Sonic 2 Z80 sound driver disassembly                                                                                            |

## Physics & Collision

| Contributor       | Contribution                                                                                                                         |
|-------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| **Sonic Retro**   | Sonic Physics Guide (SPG) - comprehensive physics documentation <br/> <br/> https://info.sonicretro.org/Sonic_Physics_Guide          |
| **LapperDev**     | SPGSonic2Overlay.Lua - sensors, hitboxes, and solid object visualization <br/> <br/> https://info.sonicretro.org/SPG:Overlay_Scripts |
| **MercurySilver** | SPGSonic2Overlay.Lua - terrain and misc additions                                                                                    |

## Disassembly & ROM Research

### Sonic 1 Disassembly (s1disasm)

https://github.com/sonicretro/s1disasm

| Contributor        | Contribution                                          |
|--------------------|-------------------------------------------------------|
| **Hivebrain**      | Created the original Sonic 1 disassembly              |
| **MainMemory**     | Major contributor, ongoing maintenance                 |
| **clownacy**       | Disassembly improvements                               |
| **flamewing**      | Disassembly contributions                              |
| **DevonArtmeier**  | Disassembly contributions                              |

### Sonic 2 Disassembly (s2disasm)

https://github.com/sonicretro/s2disasm

| Contributor        | Contribution                                                                        |
|--------------------|-------------------------------------------------------------------------------------|
| **Nemesis**        | Created original Sonic 2 disassembly (2004, SNASM68K), Nemesis compression research <br/><br/> https://info.sonicretro.org/SCHG:Nem%20s2 |
| **Xenowhirl**      | Ported to AS assembler, extensive annotation, Z80 sound driver disassembly (2007)   |
| **FraGag**         | Host/maintainer, constants/equates system, major refactoring                        |
| **shobiz**         | VDP command conversion, commenting, label cleanup                                   |
| **qiuu**           | RAM address equates, collision and level select commenting                           |
| **flamewing**      | Sound driver work, merged contributions                                             |
| **clownacy**       | Disassembly improvements, decompression tools, documentation                        |
| **MainMemory**     | Disassembly contributions                                                           |
| **Marzo (marzojr)**| Disassembly contributions                                                           |

### Sonic 3 & Knuckles Disassembly (skdisasm)

https://github.com/sonicretro/skdisasm

| Contributor        | Contribution                                                                        |
|--------------------|-------------------------------------------------------------------------------------|
| **MainMemory**     | Primary maintainer, split disassembly                                               |
| **flamewing**      | Thorough Z80 sound driver documentation and bugfixes                                |
| **MarkeyJester**   | Original Z80 sound driver disassembly                                               |
| **Linncaki**       | Sound driver routines, pointers, and data identification                            |
| **clownacy**       | Disassembly contributions                                                           |
| **Natsumi**        | Disassembly contributions                                                           |

### General ROM & Hardware Research

| Contributor        | Contribution                                                                         |
|--------------------|--------------------------------------------------------------------------------------|
| **Sonic Retro**    | SCHG documentation and community disassembly hosting <br/><br/> https://info.sonicretro.org |
| **Brett Kosinski** | Kosinski compression research <br/><br/> https://segaretro.org/Kosinski_compression  |

## Compression & Tools

| Contributor   | Contribution                                                                                    |
|---------------|-------------------------------------------------------------------------------------------------|
| **clownacy**  | Decompression tools                                                                             |
| **flamewing** | s2ssedit (Sonic 2 Special Stage Editor) - used as reference                                     |

## Communities

- **Sonic Retro** - The invaluable wiki, forums, and community resources
- **SMS Power!** - Sega hardware documentation
- **Hacking CulT** - Pioneering Sonic ROM hacking research

---

*If you contributed to resources used in this project and are not listed, please open an issue or PR!*
