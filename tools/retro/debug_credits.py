#!/usr/bin/env python3
"""Debug script to find the actual v_creditsnum address in stable-retro RAM."""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from trace_core import GenesisRAM, ADDR_GAME_MODE

import stable_retro
import numpy as np

env = stable_retro.make(
    game="SonicTheHedgehog-Genesis-v0",
    state=stable_retro.State.DEFAULT,
    render_mode=None,
    info="trace_data",
)
env.reset()
no_input = np.zeros(env.action_space.shape[0], dtype=np.int8)

# Force credits
env.data.set_value("demo_flag", 0)
env.data.set_value("credits_num", 0)
env.data.set_value("game_mode", 0x1C)

prev_gm = -1
for i in range(1500):
    env.step(no_input)
    ram = env.get_ram()
    mem = GenesisRAM(ram)
    gm = mem.u8(ADDR_GAME_MODE)
    if gm != prev_gm or i % 300 == 0:
        # Raw bytes FFF0-FFFF (no byte swap, direct array index)
        raw_bytes = [ram[a] for a in range(0xFFF0, 0x10000)]
        hex_str = " ".join(f"{b:02X}" for b in raw_bytes)
        # Also read using GenesisRAM u16 at key addresses
        df = mem.u16(0xFFF2)  # f_demo
        dn = mem.u16(0xFFF4)  # v_demonum
        cn = mem.u16(0xFFF6)  # v_creditsnum
        print(f"f{i:04d} gm=0x{gm:02X} df=0x{df:04X} dn={dn} cn={cn} RAW: {hex_str}")
        prev_gm = gm

env.close()
print("Done")
