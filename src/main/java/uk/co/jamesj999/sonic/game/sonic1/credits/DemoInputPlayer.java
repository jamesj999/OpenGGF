package uk.co.jamesj999.sonic.game.sonic1.credits;

import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

/**
 * Replays pre-recorded demo input from ROM data during ending credits.
 * <p>
 * The demo data format is pairs of bytes: {@code [button_state, duration]}.
 * Each pair specifies a joypad button mask held for a number of frames.
 * The sequence ends when both bytes are 0x00.
 * <p>
 * Button mapping (Mega Drive joypad bits → engine input constants):
 * <ul>
 *   <li>Bit 0 (0x01): Up → {@code INPUT_UP}</li>
 *   <li>Bit 1 (0x02): Down → {@code INPUT_DOWN}</li>
 *   <li>Bit 2 (0x04): Left → {@code INPUT_LEFT}</li>
 *   <li>Bit 3 (0x08): Right → {@code INPUT_RIGHT}</li>
 *   <li>Bit 4 (0x10): B → {@code INPUT_JUMP}</li>
 *   <li>Bit 5 (0x20): C → {@code INPUT_JUMP}</li>
 *   <li>Bit 6 (0x40): A → {@code INPUT_JUMP}</li>
 * </ul>
 * <p>
 * Reference: docs/s1disasm/_inc/MoveSonicInDemo.asm
 */
public class DemoInputPlayer {

    private final byte[] data;
    private int dataOffset;
    private int remainingFrames;
    private int currentButtons;
    private boolean complete;

    /**
     * Creates a demo input player from raw ROM demo data.
     *
     * @param demoData byte array of demo input pairs
     */
    public DemoInputPlayer(byte[] demoData) {
        this.data = demoData;
        this.dataOffset = 0;
        this.complete = false;
        loadNextPair();
        // ROM: subq.b #1,(v_btnpushtime2).w (sonic.asm:2983)
        // The initial pair's duration is decremented once during setup,
        // so it plays for exactly data[1] frames (not data[1]+1).
        if (!complete) {
            remainingFrames--;
        }
    }

    /**
     * Advances one frame. Call once per game frame to step through the demo.
     */
    public void advanceFrame() {
        if (complete) {
            return;
        }
        remainingFrames--;
        if (remainingFrames < 0) {
            dataOffset += 2;
            loadNextPair();
        }
    }

    /**
     * Returns the current engine input mask for this frame.
     * The mask uses {@link AbstractPlayableSprite} INPUT_* constants.
     */
    public int getInputMask() {
        if (complete) {
            return 0;
        }
        return mapToEngineInput(currentButtons);
    }

    /**
     * @return true when all demo input pairs have been exhausted
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Loads the next button/duration pair from the data array.
     */
    private void loadNextPair() {
        if (dataOffset + 1 >= data.length) {
            complete = true;
            currentButtons = 0;
            return;
        }
        currentButtons = data[dataOffset] & 0xFF;
        remainingFrames = data[dataOffset + 1] & 0xFF;
        // Terminator: both bytes zero
        if (currentButtons == 0 && remainingFrames == 0) {
            complete = true;
        }
    }

    /**
     * Maps Mega Drive joypad button bits to engine input constants.
     */
    private static int mapToEngineInput(int mdButtons) {
        int mask = 0;
        if ((mdButtons & 0x01) != 0) mask |= AbstractPlayableSprite.INPUT_UP;
        if ((mdButtons & 0x02) != 0) mask |= AbstractPlayableSprite.INPUT_DOWN;
        if ((mdButtons & 0x04) != 0) mask |= AbstractPlayableSprite.INPUT_LEFT;
        if ((mdButtons & 0x08) != 0) mask |= AbstractPlayableSprite.INPUT_RIGHT;
        if ((mdButtons & 0x70) != 0) mask |= AbstractPlayableSprite.INPUT_JUMP; // B, C, or A
        return mask;
    }
}
