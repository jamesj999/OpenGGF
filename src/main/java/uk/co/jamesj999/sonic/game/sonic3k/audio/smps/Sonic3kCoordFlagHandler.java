package uk.co.jamesj999.sonic.game.sonic3k.audio.smps;

import uk.co.jamesj999.sonic.audio.smps.CoordFlagContext;
import uk.co.jamesj999.sonic.audio.smps.CoordFlagHandler;
import uk.co.jamesj999.sonic.audio.smps.SmpsSequencer;

import java.util.logging.Logger;

/**
 * Sonic 3 &amp; Knuckles coordination flag handler.
 *
 * <p>S3K uses a modified SMPS Z80 Type 2 driver with significantly different
 * coordination flag assignments compared to S2. This handler intercepts all
 * flags E0-FF and dispatches them according to the S3K DefCFlag.txt definitions.
 *
 * <p>Key differences from S2:
 * <ul>
 *   <li>E3 = TRK_END (mute), not Return</li>
 *   <li>E9 = SPINDASH_REV with 0 params (S2 has 1 param)</li>
 *   <li>F9 = RETURN (S2 has F9 = SND_OFF)</li>
 *   <li>FF = META_CF prefix for sub-commands 00-07</li>
 *   <li>Many new flags: E2, E4, E5, EA, EB, EE, F1, F4, FC, FD, FE</li>
 * </ul>
 */
public class Sonic3kCoordFlagHandler implements CoordFlagHandler {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kCoordFlagHandler.class.getName());

    private int spindashRevCounter = 0;

    @Override
    public boolean handleFlag(CoordFlagContext ctx, SmpsSequencer.Track t, int cmd) {
        byte[] data = ctx.getData();
        switch (cmd) {
            // ---- Basic flags (S2 equivalents or simple logic) ----

            case 0xE0: // PANAFMS - set pan/AMS/FMS
                if (t.pos < data.length) {
                    int val = data[t.pos++] & 0xFF;
                    t.pan = ((val & 0x80) != 0 ? 0x80 : 0) | ((val & 0x40) != 0 ? 0x40 : 0);
                    t.ams = (val >> 4) & 0x3;
                    t.fms = val & 0x7;
                    if (t.type == SmpsSequencer.TrackType.FM) {
                        int hwCh = t.channelId;
                        int port = (hwCh < 3) ? 0 : 1;
                        int ch = hwCh % 3;
                        int reg = 0xB4 + ch;
                        int regVal = (t.pan & 0xC0) | ((t.ams & 0x3) << 4) | (t.fms & 0x7);
                        ctx.writeFm(port, reg, regVal);
                    }
                }
                return true;

            case 0xE1: // DETUNE - set track detune
                if (t.pos < data.length) {
                    t.detune = data[t.pos++]; // signed byte
                }
                return true;

            case 0xE2: // FADE_IN_SONG - fade previous track in
                if (t.pos < data.length) {
                    t.pos++;
                    ctx.triggerFadeIn();
                }
                return true;

            case 0xE3: // TRK_END (TEND_MUTE) - stop/mute track
                t.active = false;
                ctx.stopNote(t);
                return true;

            case 0xE4: // VOL_ABS_S3K - set absolute volume
                if (t.pos < data.length) {
                    int raw = data[t.pos++] & 0xFF;
                    if (t.type == SmpsSequencer.TrackType.FM) {
                        // 00(min)..7F(max) -> 7F(min)..00(max)
                        t.volumeOffset = (~raw) & 0x7F;
                        ctx.refreshVolume(t);
                    } else if (t.type == SmpsSequencer.TrackType.PSG) {
                        // 00(min)..7F(max) -> 0F(min)..00(max)
                        t.volumeOffset = ((raw >> 3) & 0x0F) ^ 0x0F;
                        ctx.refreshVolume(t);
                    }
                }
                return true;

            case 0xE5: // VOL_CC_FMP2 - S3K broken: ignore first param, apply second as FM volume delta
                if (t.pos + 1 < data.length) {
                    t.pos++; // First parameter is ignored in S3K.
                    int volChange = (byte) data[t.pos++];
                    if (t.type == SmpsSequencer.TrackType.FM) {
                        applySignedFmVolumeDelta(t, volChange);
                        ctx.refreshVolume(t);
                    }
                }
                return true;

            case 0xE6: // VOL_CC_FM - add to volume offset
                if (t.pos < data.length) {
                    int delta = (byte) data[t.pos++];
                    if (t.type == SmpsSequencer.TrackType.FM) {
                        applySignedFmVolumeDelta(t, delta);
                        ctx.refreshVolume(t);
                    }
                }
                return true;

            case 0xE7: // HOLD - tie next note
                t.tieNext = true;
                return true;

            case 0xE8: // NOTE_STOP (NSTOP_MULT) - set fill
                if (t.pos < data.length) {
                    t.fill = data[t.pos++] & 0xFF;
                }
                return true;

            case 0xE9: // SPINDASH_REV (SDREV_INC) - no params in S3K!
                spindashRevCounter++;
                return true;

            case 0xEA: // PLAY_DAC - play DAC sample
                if (t.pos < data.length) {
                    int dacId = data[t.pos++] & 0xFF;
                    ctx.playDac(dacId);
                }
                return true;

            case 0xEB: // LOOP_EXIT - counter, count, pointer (4 bytes total)
                handleLoopExit(ctx, t, data);
                return true;

            case 0xEC: // PSG_VOL (VOL_CC_PSG) - add to PSG volume
                if (t.pos < data.length) {
                    int delta = (byte) data[t.pos++];
                    if (t.type == SmpsSequencer.TrackType.PSG) {
                        // Z80 Type 2 behavior: unsigned add then clip upper bound to 0x0F.
                        int updated = (t.volumeOffset + delta) & 0xFF;
                        if (updated > 0x0F) {
                            updated = 0x0F;
                        }
                        t.volumeOffset = updated;
                        t.envAtRest = false;
                        ctx.refreshVolume(t);
                    }
                }
                return true;

            case 0xED: // TRANSPOSE_SET (TRNSP_SET_S3K) - set absolute transposition
                if (t.pos < data.length) {
                    t.keyOffset = wrapSignedByte((data[t.pos++] & 0xFF) - 0x40);
                }
                return true;

            case 0xEE: // FM_COMMAND - direct FM register write
                if (t.pos + 1 < data.length) {
                    int fmReg = data[t.pos++] & 0xFF;
                    int fmVal = data[t.pos++] & 0xFF;
                    if (t.type == SmpsSequencer.TrackType.FM) {
                        int hwCh = t.channelId;
                        int port = (hwCh < 3) ? 0 : 1;
                        ctx.writeFm(port, fmReg, fmVal);
                    }
                }
                return true;

            case 0xEF: // INSTRUMENT (INS_C_FMP) - load voice/instrument
                if (t.pos < data.length) {
                    int voiceId = data[t.pos++] & 0xFF;
                    ctx.loadVoice(t, voiceId);
                }
                return true;

            case 0xF0: // MOD_SETUP - modulation setup (4 params: delay, rate, delta, steps)
                if (t.pos + 3 < data.length) {
                    t.modDelayInit = data[t.pos++] & 0xFF;
                    t.modDelay = t.modDelayInit;
                    int rate = data[t.pos++] & 0xFF;
                    t.modRate = (rate == 0) ? 256 : rate;
                    t.modDelta = data[t.pos++]; // signed
                    int steps = data[t.pos++] & 0xFF;
                    t.modStepsFull = steps;
                    // S3K uses Z80 driver: halve mod steps (srl a)
                    t.modSteps = steps / 2;
                    t.modRateCounter = t.modRate;
                    t.modStepCounter = t.modSteps;
                    t.modAccumulator = 0;
                    t.modCurrentDelta = t.modDelta;
                    t.modEnabled = true;
                }
                return true;

            case 0xF1: // MOD_ENV (MENV_FMP) - FM modulation envelope (2 params)
                if (t.pos + 1 < data.length) {
                    int psgEnvId = data[t.pos++] & 0xFF;
                    int fmEnvId = data[t.pos++] & 0xFF;
                    t.modEnvId = (t.type == SmpsSequencer.TrackType.PSG) ? psgEnvId : fmEnvId;
                    if (t.modEnvId == 0) {
                        ctx.clearModulation(t);
                    } else {
                        t.modEnabled = true;
                    }
                }
                return true;

            case 0xF2: // TRK_END (TEND_STD) - standard track end
                t.active = false;
                ctx.stopNote(t);
                return true;

            case 0xF3: // PSG_NOISE (PNOIS_SRES) - set + reset
                if (t.pos < data.length) {
                    int noiseVal = data[t.pos++] & 0xFF;
                    if (t.type == SmpsSequencer.TrackType.PSG) {
                        ctx.writePsg(0xDF);
                        if (noiseVal == 0) {
                            t.noiseMode = false;
                            t.psgNoiseParam = 0;
                            ctx.writePsg(0xFF);
                        } else {
                            int noiseReg = ((noiseVal & 0xE0) == 0xE0)
                                    ? noiseVal
                                    : (0xE0 | (noiseVal & 0x0F));
                            t.noiseMode = true;
                            t.psgNoiseParam = noiseReg & 0x0F;
                            ctx.writePsg(noiseReg);
                        }
                    }
                }
                return true;

            case 0xF4: // MOD_ENV (MENV_GEN) - generic modulation envelope (1 param)
                if (t.pos < data.length) {
                    t.modEnvId = data[t.pos++] & 0xFF;
                    if (t.modEnvId == 0) {
                        ctx.clearModulation(t);
                    } else {
                        t.modEnabled = true;
                    }
                }
                return true;

            case 0xF5: // PSG_INSTRUMENT (INS_C_PSG) - load PSG envelope
                if (t.pos < data.length) {
                    int insId = data[t.pos++] & 0xFF;
                    t.instrumentId = insId;
                    ctx.loadPsgEnvelope(t, insId);
                }
                return true;

            case 0xF6: // GOTO - jump to pointer
                handleGoto(ctx, t);
                return true;

            case 0xF7: // LOOP - counter, count, pointer
                handleLoop(ctx, t, data);
                return true;

            case 0xF8: // GOSUB - call subroutine
                handleGosub(ctx, t);
                return true;

            case 0xF9: // RETURN - pop return stack (NOT SND_OFF like S2!)
                if (t.returnSp > 0) {
                    t.pos = t.returnStack[--t.returnSp];
                } else {
                    t.active = false;
                }
                return true;

            case 0xFA: // MODS_OFF - disable modulation
                ctx.clearModulation(t);
                return true;

            case 0xFB: // TRANSPOSE_ADD - add to transposition
                if (t.pos < data.length) {
                    t.keyOffset = wrapSignedByte(t.keyOffset + (byte) data[t.pos++]); // signed 8-bit add
                }
                return true;

            case 0xFC: // CONT_SFX - continuous SFX loop (like goto for SFX)
                handleContSfx(ctx, t);
                return true;

            case 0xFD: // RAW_FREQ - set raw frequency mode
                if (t.pos < data.length) {
                    int rawFreqVal = data[t.pos++] & 0xFF;
                    t.rawFreqMode = (rawFreqVal == 0x01);
                }
                return true;

            case 0xFE: // SPC_FM3 - FM3 special mode (4 params)
                if (t.pos + 3 < data.length) {
                    // Read and discard 4 bytes - FM3 special mode is broken per DefCFlag.txt
                    int p1 = data[t.pos++] & 0xFF;
                    int p2 = data[t.pos++] & 0xFF;
                    int p3 = data[t.pos++] & 0xFF;
                    int p4 = data[t.pos++] & 0xFF;
                    LOGGER.fine("S3K SPC_FM3 (broken): " + p1 + ", " + p2 + ", " + p3 + ", " + p4);
                }
                return true;

            case 0xFF: // META_CF - meta command prefix
                handleMetaCommand(ctx, t, data);
                return true;

            default:
                return false; // Unknown flag - fall through to default S2 handler
        }
    }

    @Override
    public int flagParamLength(int cmd) {
        if (cmd == 0xFF) return 1; // Meta prefix + at least sub-command byte
        return switch (cmd) {
            case 0xE0 -> 1; // PANAFMS
            case 0xE1 -> 1; // DETUNE
            case 0xE2 -> 1; // FADE_IN_SONG
            case 0xE3 -> 0; // TRK_END (mute)
            case 0xE4 -> 1; // VOL_ABS
            case 0xE5 -> 2; // VOL_CC_FMP2
            case 0xE6 -> 1; // VOL_CC_FM
            case 0xE7 -> 0; // HOLD
            case 0xE8 -> 1; // NOTE_STOP
            case 0xE9 -> 0; // SPINDASH_REV (no param in S3K!)
            case 0xEA -> 1; // PLAY_DAC
            case 0xEB -> 3; // LOOP_EXIT (counter, count, ptr)
            case 0xEC -> 1; // PSG_VOL
            case 0xED -> 1; // TRANSPOSE_SET
            case 0xEE -> 2; // FM_COMMAND
            case 0xEF -> 1; // INSTRUMENT (basic)
            case 0xF0 -> 4; // MOD_SETUP
            case 0xF1 -> 2; // MOD_ENV (FMP)
            case 0xF2 -> 0; // TRK_END
            case 0xF3 -> 1; // PSG_NOISE
            case 0xF4 -> 1; // MOD_ENV (generic)
            case 0xF5 -> 1; // PSG_INSTRUMENT
            case 0xF6 -> 2; // GOTO
            case 0xF7 -> 4; // LOOP
            case 0xF8 -> 2; // GOSUB
            case 0xF9 -> 0; // RETURN
            case 0xFA -> 0; // MODS_OFF
            case 0xFB -> 1; // TRANSPOSE_ADD
            case 0xFC -> 2; // CONT_SFX
            case 0xFD -> 1; // RAW_FREQ
            case 0xFE -> 4; // SPC_FM3
            default -> -1;
        };
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void handleGoto(CoordFlagContext ctx, SmpsSequencer.Track t) {
        int newPos = ctx.readJumpPointer(t);
        if (newPos != -1) {
            t.pos = newPos;
        } else {
            t.active = false;
        }
    }

    private void handleLoop(CoordFlagContext ctx, SmpsSequencer.Track t, byte[] data) {
        if (t.pos + 1 < data.length) {
            int index = data[t.pos++] & 0xFF;
            int count = data[t.pos++] & 0xFF;
            int newPos = ctx.readJumpPointer(t);
            if (newPos == -1) {
                t.active = false;
                return;
            }
            if (count == 0) {
                t.pos = newPos;
                return;
            }
            if (index >= t.loopCounters.length) {
                int[] newCounters = new int[Math.max(t.loopCounters.length * 2, index + 1)];
                System.arraycopy(t.loopCounters, 0, newCounters, 0, t.loopCounters.length);
                t.loopCounters = newCounters;
            }
            if (t.loopCounters[index] == 0) {
                t.loopCounters[index] = count;
            }
            if (t.loopCounters[index] > 0) {
                t.loopCounters[index]--;
                if (t.loopCounters[index] > 0) {
                    t.pos = newPos;
                }
            }
        }
    }

    private void handleGosub(CoordFlagContext ctx, SmpsSequencer.Track t) {
        int newPos = ctx.readJumpPointer(t);
        if (newPos == -1 || t.returnSp >= t.returnStack.length) {
            t.active = false;
            return;
        }
        t.returnStack[t.returnSp++] = t.pos;
        t.pos = newPos;
    }

    private void handleLoopExit(CoordFlagContext ctx, SmpsSequencer.Track t, byte[] data) {
        // EB: counter index, target count, pointer
        // If loop counter has reached the target count, skip the pointer and continue;
        // otherwise jump to the pointer address.
        if (t.pos + 1 < data.length) {
            int index = data[t.pos++] & 0xFF;
            int targetCount = data[t.pos++] & 0xFF;
            int jumpTarget = ctx.readJumpPointer(t);
            if (jumpTarget == -1) {
                return;
            }
            if (index >= t.loopCounters.length) {
                // Counter doesn't exist yet - not at target, jump
                t.pos = jumpTarget;
                return;
            }
            // Check if the loop counter has reached the exit condition
            if (t.loopCounters[index] == targetCount) {
                // Exit: continue past the pointer (already advanced by readJumpPointer)
            } else {
                // Not yet: jump to the pointer
                t.pos = jumpTarget;
            }
        }
    }

    private void handleContSfx(CoordFlagContext ctx, SmpsSequencer.Track t) {
        // FC: Continuous SFX loop - functions like goto for SFX tracks
        int newPos = ctx.readJumpPointer(t);
        if (newPos != -1) {
            t.pos = newPos;
        } else {
            t.active = false;
        }
    }

    private void handleMetaCommand(CoordFlagContext ctx, SmpsSequencer.Track t, byte[] data) {
        if (t.pos >= data.length) return;
        int sub = data[t.pos++] & 0xFF;

        switch (sub) {
            case 0x00: // TEMPO_SET - set tempo
                if (t.pos < data.length) {
                    int tempo = data[t.pos++] & 0xFF;
                    ctx.setNormalTempo(tempo);
                    ctx.recalculateTempo();
                }
                break;

            case 0x01: // SND_CMD - sound command
                if (t.pos < data.length) {
                    int sndCmd = data[t.pos++] & 0xFF;
                    // Stub: sound command dispatch
                    LOGGER.fine("S3K META SND_CMD: " + sndCmd);
                }
                break;

            case 0x02: // MUS_PAUSE (MUSP_Z80) - music pause
                if (t.pos < data.length) {
                    int pauseVal = data[t.pos++] & 0xFF;
                    // Stub: music pause control
                    LOGGER.fine("S3K META MUS_PAUSE: " + pauseVal);
                }
                break;

            case 0x03: // COPY_MEM - copy memory (3 params after sub)
                if (t.pos + 2 < data.length) {
                    int memP1 = data[t.pos++] & 0xFF;
                    int memP2 = data[t.pos++] & 0xFF;
                    int memP3 = data[t.pos++] & 0xFF;
                    // Stub: memory copy not modeled
                    LOGGER.fine("S3K META COPY_MEM: " + memP1 + ", " + memP2 + ", " + memP3);
                }
                break;

            case 0x04: // TICK_MULT (TMULT_ALL) - set tick multiplier
                if (t.pos < data.length) {
                    int tickMult = data[t.pos++] & 0xFF;
                    ctx.updateDividingTiming(tickMult);
                }
                break;

            case 0x05: // SSG_EG (SEG_NORMAL) - write SSG-EG registers for all 4 operators
                if (t.pos + 3 < data.length) {
                    if (t.type == SmpsSequencer.TrackType.FM) {
                        int hwCh = t.channelId;
                        int port = (hwCh < 3) ? 0 : 1;
                        int ch = hwCh % 3;
                        // Write SSG-EG for all 4 operators (registers 0x90-0x9C)
                        ctx.writeFm(port, 0x90 + ch, data[t.pos++] & 0xFF);
                        ctx.writeFm(port, 0x94 + ch, data[t.pos++] & 0xFF);
                        ctx.writeFm(port, 0x98 + ch, data[t.pos++] & 0xFF);
                        ctx.writeFm(port, 0x9C + ch, data[t.pos++] & 0xFF);
                    } else {
                        // Not an FM track, just skip the 4 bytes
                        t.pos += 4;
                    }
                }
                break;

            case 0x06: // FM_VOLENV - FM volume envelope (2 params)
                if (t.pos + 1 < data.length) {
                    int envId = data[t.pos++] & 0xFF;
                    int opMask = data[t.pos++] & 0x0F;
                    if (t.type == SmpsSequencer.TrackType.FM && envId != 0 && opMask != 0) {
                        t.fmVolEnvData = ctx.getSmpsData().getPsgEnvelope(envId);
                        t.fmVolEnvPos = 0;
                        t.fmVolEnvValue = 0;
                        t.fmVolEnvHold = false;
                        t.fmVolEnvOpMask = opMask;
                    } else {
                        t.fmVolEnvData = null;
                        t.fmVolEnvPos = 0;
                        t.fmVolEnvValue = 0;
                        t.fmVolEnvHold = true;
                        t.fmVolEnvOpMask = 0;
                    }
                    ctx.refreshVolume(t);
                }
                break;

            case 0x07: // SPINDASH_REV_RESET (SDREV_RESET) - reset spindash counter
                spindashRevCounter = 0;
                break;

            default:
                LOGGER.warning("S3K unknown meta command: FF " + String.format("%02X", sub));
                break;
        }
    }

    private static void applySignedFmVolumeDelta(SmpsSequencer.Track t, int delta) {
        int updated = t.volumeOffset + delta;
        if (updated < 0) {
            updated = 0;
        } else if (updated > 0x7F) {
            updated = 0x7F;
        }
        t.volumeOffset = updated;
    }

    private static int wrapSignedByte(int value) {
        return (byte) value;
    }
}
