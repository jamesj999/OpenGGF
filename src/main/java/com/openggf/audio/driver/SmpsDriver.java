package com.openggf.audio.driver;

import com.openggf.audio.AudioStream;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.synth.VirtualSynthesizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SmpsDriver extends VirtualSynthesizer implements AudioStream {
    private final Object sequencersLock = new Object();
    private final List<SmpsSequencer> sequencers = new ArrayList<>();
    private final Set<SmpsSequencer> sfxSequencers = new HashSet<>();
    private final SmpsSequencer[] fmLocks = new SmpsSequencer[6];
    private final SmpsSequencer[] psgLocks = new SmpsSequencer[4];
    private final Map<Object, Integer> psgLatches = new HashMap<>();
    private SmpsSequencer.Region region = SmpsSequencer.Region.NTSC;

    private final List<SmpsSequencer> pendingRemovals = new ArrayList<>();

    // Reusable buffer for stopAllSfx() to avoid per-call ArrayList allocation
    private final List<SmpsSequencer> sfxRemovalBuffer = new ArrayList<>();

    // Scratch buffer for read() to avoid per-frame allocations
    private final short[] scratchFrameBuf = new short[2];

    // --- Continuous SFX state (Z80: zContinuousSFX, zContinuousSFXFlag, zContSFXLoopCnt) ---
    // S3K continuous SFX (0xBC+) loop via the 0xFC coord flag (cfLoopContinuousSFX).
    // When the same continuous SFX is re-triggered, the flag is set to extend playback
    // instead of restarting. The loop counter tracks how many tracks still need to hit
    // their loop point before the flag is consumed.
    private int continuousSfxId;
    private boolean continuousSfxFlag;
    private int contSfxLoopCnt;

    public SmpsDriver() {
        super();
    }

    public SmpsDriver(double outputSampleRate) {
        super(outputSampleRate);
    }

    // --- Continuous SFX API ---

    /**
     * Attempt to extend a currently-playing continuous SFX instead of restarting it.
     * ROM: when the same continuous SFX ID is re-triggered, set zContinuousSFXFlag = 0x80
     * and refresh zContSFXLoopCnt, but do NOT restart the SFX.
     *
     * @param sfxId the SFX ID being triggered
     * @param trackCount total FM+PSG track count for the SFX (from header byte 3)
     * @return true if the SFX was extended (caller should skip creating a new sequencer)
     */
    public boolean extendContinuousSfx(int sfxId, int trackCount) {
        synchronized (sequencersLock) {
            if (continuousSfxId == sfxId && continuousSfxId != 0) {
                // Verify the sequencer is still alive. If the SFX finished its first
                // playthrough before being re-triggered (flag wasn't set yet when 0xFC
                // was hit), the sequencer will have been removed — in that case we must
                // NOT claim to extend, so the caller creates a fresh sequencer.
                boolean stillAlive = false;
                for (SmpsSequencer s : sfxSequencers) {
                    if (s.getSmpsData().getId() == sfxId) {
                        stillAlive = true;
                        break;
                    }
                }
                if (stillAlive) {
                    continuousSfxFlag = true;
                    contSfxLoopCnt = trackCount;
                    return true;
                }
                // Sequencer already completed — fall through to start fresh
            }
            return false;
        }
    }

    /**
     * Mark a new continuous SFX as starting playback.
     * ROM: store SFX index in zContinuousSFX, clear zContinuousSFXFlag, set loop count.
     */
    public void startContinuousSfx(int sfxId, int trackCount) {
        synchronized (sequencersLock) {
            continuousSfxFlag = false;
            continuousSfxId = sfxId;
            contSfxLoopCnt = trackCount;
        }
    }

    /** ROM: read zContinuousSFXFlag (0x80 when set). */
    public boolean isContinuousSfxFlagSet() {
        return continuousSfxFlag;
    }

    /** ROM: clear zContinuousSFX. */
    public void clearContinuousSfxId() {
        continuousSfxId = 0;
    }

    /** ROM: clear zContinuousSFXFlag. */
    public void clearContinuousSfxFlag() {
        continuousSfxFlag = false;
    }

    /**
     * ROM: decrement zContSFXLoopCnt and return whether it reached zero.
     * When zero, all tracks have passed their loop point for this extension cycle.
     */
    public boolean decrementContSfxLoopCnt() {
        return --contSfxLoopCnt <= 0;
    }

    public void setRegion(SmpsSequencer.Region region) {
        this.region = region;
        synchronized (sequencersLock) {
            for (SmpsSequencer seq : sequencers) {
                seq.setRegion(region);
            }
        }
    }

    public void addSequencer(SmpsSequencer seq, boolean isSfx) {
        seq.setRegion(region);
        seq.setIsSfx(isSfx); // Cache isSfx flag on the sequencer for O(1) lookup
        synchronized (sequencersLock) {
            // ROM behavior: re-triggering the same SFX replaces the old one.
            // Without this, two sequencers for the same sound compete for the same
            // FM/PSG channels, causing lock ping-pong when priority bit 7 is set
            // (S1/S2 jump SFX priority 0x80 allows any SFX to steal the lock,
            // so the old sequencer steals back from the new one every sample).
            if (isSfx) {
                int newId = seq.getSmpsData().getId();
                SmpsSequencer existing = null;
                for (SmpsSequencer s : sfxSequencers) {
                    if (s.getSmpsData().getId() == newId) {
                        existing = s;
                        break;
                    }
                }
                if (existing != null) {
                    sequencers.remove(existing);
                    releaseLocks(existing);
                    sfxSequencers.remove(existing);
                }

                // Channel-based SFX conflict resolution (ROM: s2.sounddriver.asm lines 2203-2266)
                // When a new SFX uses a channel already in use by another SFX, kill the old
                // SFX's track on that channel. This prevents the old SFX from resuming after
                // the new one finishes and stops noise mode from leaking through shared PSG
                // channels (e.g., DrawbridgeMove noise leaking into BLIP on PSG3).
                List<SmpsSequencer.Track> newTracks = seq.getTracks();
                Set<SmpsSequencer> deadSequencers = null;
                boolean killedPsg3Track = false;
                for (int i = 0; i < newTracks.size(); i++) {
                    SmpsSequencer.Track newTrack = newTracks.get(i);
                    for (SmpsSequencer existingSfx : sfxSequencers) {
                        List<SmpsSequencer.Track> existingTracks = existingSfx.getTracks();
                        for (int j = 0; j < existingTracks.size(); j++) {
                            SmpsSequencer.Track existingTrack = existingTracks.get(j);
                            if (existingTrack.active
                                    && existingTrack.type == newTrack.type
                                    && existingTrack.channelId == newTrack.channelId) {
                                existingTrack.active = false;
                                existingSfx.stopNote(existingTrack);
                                // Release the lock for this channel
                                if (existingTrack.type == SmpsSequencer.TrackType.FM
                                        || existingTrack.type == SmpsSequencer.TrackType.DAC) {
                                    if (fmLocks[existingTrack.channelId] == existingSfx) {
                                        fmLocks[existingTrack.channelId] = null;
                                        updateOverrides(SmpsSequencer.TrackType.FM,
                                                existingTrack.channelId, false);
                                    }
                                } else if (existingTrack.type == SmpsSequencer.TrackType.PSG) {
                                    if (psgLocks[existingTrack.channelId] == existingSfx) {
                                        psgLocks[existingTrack.channelId] = null;
                                        updateOverrides(SmpsSequencer.TrackType.PSG,
                                                existingTrack.channelId, false);
                                    }
                                    if (existingTrack.channelId == 2) {
                                        killedPsg3Track = true;
                                    }
                                }
                            }
                        }
                        // If all tracks in existing SFX are now inactive, mark for removal
                        boolean allInactive = true;
                        for (int j = 0; j < existingTracks.size(); j++) {
                            if (existingTracks.get(j).active) {
                                allInactive = false;
                                break;
                            }
                        }
                        if (allInactive) {
                            if (deadSequencers == null) deadSequencers = new LinkedHashSet<>();
                            deadSequencers.add(existingSfx);
                        }
                    }
                }
                if (deadSequencers != null) {
                    for (SmpsSequencer dead : deadSequencers) {
                        sequencers.remove(dead);
                        releaseLocks(dead);
                        sfxSequencers.remove(dead);
                    }
                }

                // ROM lines 2221-2228: when PSG3 SFX replaces another, silence both
                // tone2 and noise. stopNote() only silences one (tone or noise depending
                // on noiseMode), so this ensures both are cleaned up to prevent noise
                // mode leaking from the old SFX.
                if (killedPsg3Track) {
                    writeRawPsg(0xDF); // silence PSG3 (tone2): 0x80|(2<<5)|(1<<4)|0x0F
                    writeRawPsg(0xFF); // silence noise channel: 0x80|(3<<5)|(1<<4)|0x0F
                }
            }
            sequencers.add(seq);
            if (isSfx) {
                sfxSequencers.add(seq);
                // SFX constructor calls synth.setDacData() which overwrites the music's
                // DAC sample bank on the shared synthesizer. Restore the music sequencer's
                // DAC data so donor music (e.g. S3K invincibility) keeps its correct samples.
                restoreMusicDacData();
            }
        }
    }

    /**
     * Restores the music (non-SFX) sequencer's DAC data on the shared synthesizer.
     * Called after adding an SFX sequencer whose constructor may have overwritten it.
     */
    private void restoreMusicDacData() {
        for (int i = 0; i < sequencers.size(); i++) {
            SmpsSequencer s = sequencers.get(i);
            if (!isSfx(s) && s.getDacData() != null) {
                setDacData(s.getDacData());
                return;
            }
        }
    }

    public void stopAll() {
        synchronized (sequencersLock) {
            sequencers.clear();
            sfxSequencers.clear();
            for (int i = 0; i < 6; i++)
                fmLocks[i] = null;
            for (int i = 0; i < 4; i++)
                psgLocks[i] = null;
            psgLatches.clear();
            continuousSfxId = 0;
            continuousSfxFlag = false;
            contSfxLoopCnt = 0;
        }
        // Silence hardware (ROM: zFMSilenceAll + zPSGSilenceAll)
        silenceAll();
    }

    /**
     * Stop all SFX sequencers, releasing their channel locks and silencing them.
     * Used when starting override music to prevent partial SFX playback on restore.
     */
    public void stopAllSfx() {
        synchronized (sequencersLock) {
            sfxRemovalBuffer.clear();
            sfxRemovalBuffer.addAll(sfxSequencers);
            for (int i = 0; i < sfxRemovalBuffer.size(); i++) {
                SmpsSequencer sfx = sfxRemovalBuffer.get(i);
                sequencers.remove(sfx);
                releaseLocks(sfx);
                sfxSequencers.remove(sfx);
            }
            continuousSfxId = 0;
            continuousSfxFlag = false;
            contSfxLoopCnt = 0;
        }
    }

    @Override
    public int read(short[] buffer) {
        int frames = buffer.length / 2;

        // Per-sample processing is required because sequencer state changes (note events,
        // instrument changes, etc.) must happen in lockstep with rendering. Batching
        // breaks audio fidelity because synth state changes mid-batch would be lost.
        synchronized (sequencersLock) {
            for (int i = 0; i < frames; i++) {
                int size = sequencers.size();
                for (int j = 0; j < size; j++) {
                    SmpsSequencer seq = sequencers.get(j);
                    seq.advance(1.0);
                    if (seq.isComplete()) {
                        pendingRemovals.add(seq);
                    }
                }

                if (!pendingRemovals.isEmpty()) {
                    for (int j = 0; j < pendingRemovals.size(); j++) {
                        SmpsSequencer seq = pendingRemovals.get(j);
                        sequencers.remove(seq);
                        releaseLocks(seq);
                        sfxSequencers.remove(seq);
                    }
                    pendingRemovals.clear();
                }

                super.render(scratchFrameBuf);
                buffer[i * 2] = scratchFrameBuf[0];
                buffer[i * 2 + 1] = scratchFrameBuf[1];
            }
        }
        return buffer.length;
    }

    @Override
    public boolean isComplete() {
        return sequencers.isEmpty();
    }

    /**
     * Check if a source is an SFX sequencer.
     * Uses cached isSfx field on SmpsSequencer for O(1) lookup instead of HashSet.contains().
     */
    private boolean isSfx(Object source) {
        if (source instanceof SmpsSequencer seq) {
            return seq.isSfx();
        }
        // Fallback to HashSet for non-SmpsSequencer sources (shouldn't happen normally)
        return sfxSequencers.contains(source);
    }

    private void releaseLocks(SmpsSequencer seq) {
        boolean isSfx = isSfx(seq);
        for (int i = 0; i < 6; i++) {
            if (fmLocks[i] == seq) {
                // If this was an SFX, ensure the channel is silenced before handing it back.
                if (isSfx) {
                    seq.forceSilence(SmpsSequencer.TrackType.FM, i);
                }
                fmLocks[i] = null;
                updateOverrides(SmpsSequencer.TrackType.FM, i, false);
            }
        }
        for (int i = 0; i < 4; i++) {
            if (psgLocks[i] == seq) {
                if (isSfx) {
                    seq.forceSilence(SmpsSequencer.TrackType.PSG, i);
                }
                psgLocks[i] = null;
                updateOverrides(SmpsSequencer.TrackType.PSG, i, false);
            }
        }
        // Clear cached PSG latch channel and remove from fallback HashMap
        seq.setPsgLatchChannel(-1);
        psgLatches.remove(seq);
    }

    private void updateOverrides(SmpsSequencer.TrackType type, int ch, boolean overridden) {
        synchronized (sequencersLock) {
            for (SmpsSequencer s : sequencers) {
                if (!isSfx(s)) {
                    s.setChannelOverridden(type, ch, overridden);
                }
            }
        }
    }

    @Override
    public void writeFm(Object source, int port, int reg, int val) {
        int ch = -1;
        int rawReg = reg & 0xFF;

        // Map Register to Channel
        if (rawReg >= 0x30 && rawReg <= 0x9E) {
            ch = (rawReg & 0x03) + (port * 3);
        } else if (rawReg >= 0xA0 && rawReg <= 0xA2) {
            ch = (rawReg - 0xA0) + (port * 3);
        } else if (rawReg >= 0xA4 && rawReg <= 0xA6) {
            ch = (rawReg - 0xA4) + (port * 3);
        } else if (rawReg >= 0xB0 && rawReg <= 0xB2) {
            ch = (rawReg - 0xB0) + (port * 3);
        } else if (rawReg >= 0xB4 && rawReg <= 0xB6) {
            ch = (rawReg - 0xB4) + (port * 3);
        } else if (rawReg == 0x28) {
            // Key On/Off: 0x28 is Port 0 only.
            // Val: d7-d4 (slot mask), d2-d0 (channel). d2 (bit 4 of ch?) No.
            // Channel is 0-2 (0,1,2) or 4-6 (4,5,6).
            // Ym2612Chip: "if (chIdx >= 4) chIdx -= 1;" -> Maps 4,5,6 to 3,4,5.
            // So Ch 0,1,2 -> 0,1,2. Ch 4,5,6 -> 3,4,5.
            // We need linear channel 0-5.
            int c = val & 0x07;
            if (c >= 4)
                c -= 1;
            ch = c;
        }

        if (ch >= 0 && ch < 6) {
            if (isSfx(source)) {
                if (shouldStealLock(fmLocks[ch], (SmpsSequencer) source)) {
                    // Silence channel if stealing from music (not from another SFX or self)
                    if (fmLocks[ch] != source && !isSfx(fmLocks[ch])) {
                        silenceFmChannel(ch);
                    }
                    fmLocks[ch] = (SmpsSequencer) source;
                    updateOverrides(SmpsSequencer.TrackType.FM, ch, true);
                }

                if (fmLocks[ch] == source) {
                    super.writeFm(source, port, reg, val);
                }
            } else {
                if (fmLocks[ch] == null) {
                    super.writeFm(source, port, reg, val);
                }
            }
        } else {
            // Global or unmapped
            super.writeFm(source, port, reg, val);
        }
    }

    @Override
    public void writePsg(Object source, int val) {
        // Use cached psgLatchChannel on SmpsSequencer for O(1) lookup instead of HashMap
        SmpsSequencer seq = (source instanceof SmpsSequencer) ? (SmpsSequencer) source : null;

        if ((val & 0x80) != 0) {
            // Latch
            int ch = (val >> 5) & 0x03;

            // Cache latch channel on sequencer (fast path) and in HashMap (fallback)
            if (seq != null) {
                seq.setPsgLatchChannel(ch);
            } else {
                psgLatches.put(source, ch);
            }

            if (isSfx(source)) {
                if (shouldStealLock(psgLocks[ch], (SmpsSequencer) source)) {
                    // Silence channel if stealing from music (not from another SFX or self)
                    if (psgLocks[ch] != source && !isSfx(psgLocks[ch])) {
                        silencePsgChannel(ch);
                    }
                    psgLocks[ch] = (SmpsSequencer) source;
                    updateOverrides(SmpsSequencer.TrackType.PSG, ch, true);
                }

                if (psgLocks[ch] == source) {
                    super.writePsg(source, val);
                }
            } else {
                if (psgLocks[ch] == null) {
                    super.writePsg(source, val);
                }
            }
        } else {
            // Data - get cached latch channel
            int ch = (seq != null) ? seq.getPsgLatchChannel() : -1;
            if (ch < 0) {
                // Fallback to HashMap for non-SmpsSequencer sources
                Integer chObj = psgLatches.get(source);
                ch = (chObj != null) ? chObj : -1;
            }

            if (ch >= 0) {
                if (isSfx(source)) {
                    // Update lock just in case? Already locked by Latch.
                    if (shouldStealLock(psgLocks[ch], (SmpsSequencer) source)) {
                        // Silence channel if stealing from music (not from another SFX or self)
                        if (psgLocks[ch] != source && !isSfx(psgLocks[ch])) {
                            silencePsgChannel(ch);
                        }
                        psgLocks[ch] = (SmpsSequencer) source;
                        updateOverrides(SmpsSequencer.TrackType.PSG, ch, true);
                    }

                    if (psgLocks[ch] == (SmpsSequencer) source) {
                        super.writePsg(source, val);
                    }
                } else {
                    if (psgLocks[ch] == null) {
                        super.writePsg(source, val);
                    }
                }
            } else {
                // Unknown channel (no previous latch from this source), drop or pass?
                // Pass for safety/compatibility
                super.writePsg(source, val);
            }
        }
    }

    // Override other methods if needed (setInstrument calls writeFm, so it's
    // covered)
    @Override
    public void setInstrument(Object source, int channelId, byte[] voice) {
        // Channel ID is passed explicitly.
        if (channelId >= 0 && channelId < 6) {
            if (isSfx(source)) {
                if (shouldStealLock(fmLocks[channelId], (SmpsSequencer) source)) {
                    // Silence channel if stealing from music (not from another SFX or self)
                    if (fmLocks[channelId] != source && !isSfx(fmLocks[channelId])) {
                        silenceFmChannel(channelId);
                    }
                    fmLocks[channelId] = (SmpsSequencer) source;
                    updateOverrides(SmpsSequencer.TrackType.FM, channelId, true);
                }

                if (fmLocks[channelId] == source) {
                    super.setInstrument(source, channelId, voice);
                }
            } else {
                if (fmLocks[channelId] == null) {
                    super.setInstrument(source, channelId, voice);
                }
            }
        }
    }

    @Override
    public void playDac(Object source, int note) {
        // DAC is on Channel 5 (FM6)
        int ch = 5;
        if (isSfx(source)) {
            if (shouldStealLock(fmLocks[ch], (SmpsSequencer) source)) {
                // Silence channel if stealing from music (not from another SFX or self)
                if (fmLocks[ch] != source && !isSfx(fmLocks[ch])) {
                    silenceFmChannel(5);
                    super.stopDac(null);
                }
                fmLocks[ch] = (SmpsSequencer) source;
                updateOverrides(SmpsSequencer.TrackType.FM, ch, true);
            }

            if (fmLocks[ch] == source) {
                super.playDac(source, note);
            }
        } else {
            if (fmLocks[ch] == null) {
                super.playDac(source, note);
            }
        }
    }

    private boolean shouldStealLock(SmpsSequencer currentLock, SmpsSequencer challenger) {
        if (currentLock == null)
            return true;
        if (currentLock == challenger)
            return true;
        if (!isSfx(currentLock))
            return true; // Challenger is SFX, current is Music -> Steal

        // Both are SFX.
        // Sonic 1 has a dedicated "special SFX" class (e.g. GHZ waterfall) that can be
        // overridden by normal SFX on shared channels, but not vice versa.
        boolean currentSpecial = currentLock.isSpecialSfx();
        boolean challengerSpecial = challenger.isSpecialSfx();
        if (currentSpecial && !challengerSpecial) {
            return true;
        }
        if (!currentSpecial && challengerSpecial) {
            return false;
        }

        // Priority arbitration:
        // Higher priority steals. If current priority has bit 7 set, treat it as
        // non-storing/transient (ROM-style), so any subsequent SFX can steal.
        int currentPriority = currentLock.getSfxPriority();
        int challengerPriority = challenger.getSfxPriority();
        if ((currentPriority & 0x80) != 0) {
            return true;
        }

        if (challengerPriority > currentPriority) {
            return true; // Higher priority always steals
        } else if (challengerPriority == currentPriority) {
            // Equal priority: newer SFX wins (prevents old SFX from stealing back)
            int currentIdx = sequencers.indexOf(currentLock);
            int challengerIdx = sequencers.indexOf(challenger);
            return challengerIdx > currentIdx;
        }
        return false; // Lower priority cannot steal
    }

    /**
     * Silence an FM channel before SFX takes it over from music.
     * This directly resets envelope state to prevent the "chirp" artifact
     * that occurs when SFX first samples inherit envelope state from the
     * previous music note.
     *
     * Unlike register writes (which would be overwritten by the subsequent
     * voice load), this directly resets the envelope counters to fully
     * silent state, ensuring the next Key On starts from a clean slate.
     */
    private void silenceFmChannel(int ch) {
        // Directly reset envelope state - this takes effect immediately
        // without needing audio samples to be rendered
        super.forceSilenceChannel(ch);

        // Also send Key Off via registers for completeness
        int port = (ch < 3) ? 0 : 1;
        int hwCh = ch % 3;
        int chVal = (port == 0) ? hwCh : (hwCh + 4);
        super.writeFm(null, 0, 0x28, 0x00 | chVal);
    }

    /**
     * Write directly to PSG hardware, bypassing SFX lock checks.
     * Used for unconditional channel silencing during SFX load (ROM: zPlaySound).
     * Protected to allow test spy access.
     */
    protected void writeRawPsg(int val) {
        super.writePsg(null, val);
    }

    /**
     * Silence a PSG channel before SFX takes it over from music.
     * Sets volume to 0xF (silence).
     */
    private void silencePsgChannel(int ch) {
        if (ch >= 0 && ch <= 3) {
            super.writePsg(null, 0x80 | (ch << 5) | (1 << 4) | 0x0F);
        }
    }

    @Override
    public void stopDac(Object source) {
        int ch = 5;
        if (isSfx(source)) {
            // Don't release lock here, just stop sound.
            // Lock is released when track ends or channel unused?
            // Actually, stopDac is just stopping sound.
            super.stopDac(source);
        } else {
            if (fmLocks[ch] == null) {
                super.stopDac(source);
            }
        }
    }
}
