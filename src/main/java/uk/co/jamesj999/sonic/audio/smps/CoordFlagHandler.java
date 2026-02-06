package uk.co.jamesj999.sonic.audio.smps;

/**
 * Game-specific coordination flag dispatch.
 *
 * <p>S3K coordination flags (E0-FF) have almost entirely different semantics
 * from S2. Rather than adding game conditionals throughout
 * {@code SmpsSequencer.handleFlag()}, game modules supply a handler that
 * intercepts flags before the default S2 switch.
 *
 * <p>The handler receives a {@link CoordFlagContext} that exposes sequencer
 * internals for manipulating track state.
 */
public interface CoordFlagHandler {

    /**
     * Attempt to handle a coordination flag command.
     *
     * @param ctx  the sequencer context (for data access, track ops, synth writes)
     * @param t    the track being processed
     * @param cmd  the flag byte (0xE0-0xFF)
     * @return {@code true} if the flag was handled; {@code false} to fall through
     *         to the default S2 handler
     */
    boolean handleFlag(CoordFlagContext ctx, SmpsSequencer.Track t, int cmd);

    /**
     * Return the number of parameter bytes for a given flag command.
     * Used when skipping unknown/unimplemented flags to keep the stream in sync.
     *
     * @param cmd the flag byte (0xE0-0xFF)
     * @return parameter byte count, or -1 if this handler does not know the flag
     *         (fall through to default table)
     */
    int flagParamLength(int cmd);
}
