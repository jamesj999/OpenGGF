package com.openggf.audio.smps;

/**
 * Exposes sequencer internals to {@link CoordFlagHandler} implementations
 * without making private methods public on {@code SmpsSequencer} itself.
 *
 * <p>Implemented by {@code SmpsSequencer}. Game-specific flag handlers receive
 * this interface so they can manipulate track state, load voices/envelopes,
 * read pointers, manage tempo, and write directly to the synth when needed.
 */
public interface CoordFlagContext {

    // -----------------------------------------------------------------------
    // Data / config access
    // -----------------------------------------------------------------------

    /** Raw SMPS data bytes. */
    byte[] getData();

    /** The parsed SMPS header data. */
    AbstractSmpsData getSmpsData();

    /** Sequencer configuration. */
    SmpsSequencerConfig getConfig();

    // -----------------------------------------------------------------------
    // Track operations
    // -----------------------------------------------------------------------

    /** Load an FM voice (instrument) into the track. */
    void loadVoice(SmpsSequencer.Track t, int voiceId);

    /** Load a PSG envelope into the track. */
    void loadPsgEnvelope(SmpsSequencer.Track t, int envId);

    /** Stop the currently playing note on the track (key off / mute). */
    void stopNote(SmpsSequencer.Track t);

    /** Refresh the track's volume (re-apply TL for FM, attenuation for PSG). */
    void refreshVolume(SmpsSequencer.Track t);

    /** Refresh the track's instrument (re-send all voice registers). */
    void refreshInstrument(SmpsSequencer.Track t);

    // -----------------------------------------------------------------------
    // Pointer operations
    // -----------------------------------------------------------------------

    /**
     * Read a jump/loop/call pointer from the track data, handling both
     * PC-relative (S1) and absolute Z80 (S2/S3K) addressing modes.
     *
     * @return the resolved data offset, or -1 if invalid
     */
    int readJumpPointer(SmpsSequencer.Track t);

    // -----------------------------------------------------------------------
    // Tempo / timing
    // -----------------------------------------------------------------------

    /** Set the normal (base) tempo value. */
    void setNormalTempo(int tempo);

    /** Get the current normal (base) tempo value. */
    int getNormalTempo();

    /** Recalculate the effective tempo weight from current settings. */
    void recalculateTempo();

    /** Update the dividing timing (tick multiplier) for all tracks. */
    void updateDividingTiming(int newDividingTiming);

    // -----------------------------------------------------------------------
    // Modulation
    // -----------------------------------------------------------------------

    /** Clear all modulation state on the track. */
    void clearModulation(SmpsSequencer.Track t);

    // -----------------------------------------------------------------------
    // Fade
    // -----------------------------------------------------------------------

    /** Trigger a fade-in effect. */
    void triggerFadeIn();

    /** Trigger a fade-out effect. */
    void triggerFadeOut(int steps, int delay);

    // -----------------------------------------------------------------------
    // Communication byte
    // -----------------------------------------------------------------------

    /** Set the communication data byte (E2 flag in S2). */
    void setCommData(int value);

    /** Get the communication data byte. */
    int getCommData();

    // -----------------------------------------------------------------------
    // Synth access (for direct hardware writes)
    // -----------------------------------------------------------------------

    /** Write an FM register value. */
    void writeFm(int port, int reg, int value);

    /** Write a PSG byte. */
    void writePsg(int value);

    /** Play a DAC sample by note ID. */
    void playDac(int noteId);

    /** Stop DAC playback. */
    void stopDac();
}
