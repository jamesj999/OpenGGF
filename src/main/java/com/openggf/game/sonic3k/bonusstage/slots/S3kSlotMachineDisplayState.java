package com.openggf.game.sonic3k.bonusstage.slots;

import java.util.Arrays;

public record S3kSlotMachineDisplayState(
        int worldX,
        int worldY,
        int[] faces,
        int[] nextFaces,
        float[] offsets
) {
    public static S3kSlotMachineDisplayState fromState(S3kSlotStageState state, int worldX, int worldY) {
        if (state == null) {
            return new S3kSlotMachineDisplayState(worldX, worldY, new int[] {0, 0, 0},
                    new int[] {0, 0, 0}, new float[] {0f, 0f, 0f});
        }
        int[] reelWords = state.optionCycleReelWords();
        if (reelWords != null && Arrays.stream(reelWords).anyMatch(word -> word != 0)) {
            int[] faces = {
                    faceForReelWord(reelWords[0], S3kSlotRomData.REEL_SEQUENCE_A),
                    faceForReelWord(reelWords[1], S3kSlotRomData.REEL_SEQUENCE_B),
                    faceForReelWord(reelWords[2], S3kSlotRomData.REEL_SEQUENCE_C)
            };
            int[] nextFaces = {
                    nextFaceForReelWord(reelWords[0], S3kSlotRomData.REEL_SEQUENCE_A),
                    nextFaceForReelWord(reelWords[1], S3kSlotRomData.REEL_SEQUENCE_B),
                    nextFaceForReelWord(reelWords[2], S3kSlotRomData.REEL_SEQUENCE_C)
            };
            float[] offsets = {
                    ((reelWords[0] & 0xF8) / 256f),
                    ((reelWords[1] & 0xF8) / 256f),
                    ((reelWords[2] & 0xF8) / 256f)
            };
            return new S3kSlotMachineDisplayState(worldX, worldY, faces, nextFaces, offsets);
        }
        int[] faces = state.optionCycleDisplaySymbols().clone();
        int[] nextFaces = {
                nextSymbolFor(faces[0], S3kSlotRomData.REEL_SEQUENCE_A),
                nextSymbolFor(faces[1], S3kSlotRomData.REEL_SEQUENCE_B),
                nextSymbolFor(faces[2], S3kSlotRomData.REEL_SEQUENCE_C)
        };
        int[] rawOffsets = state.optionCycleOffsets();
        float[] offsets = {
                (rawOffsets[0] & 0xFF) / 256f,
                (rawOffsets[1] & 0xFF) / 256f,
                (rawOffsets[2] & 0xFF) / 256f
        };
        return new S3kSlotMachineDisplayState(worldX, worldY, faces, nextFaces, offsets);
    }

    private static int faceForReelWord(int reelWord, byte[] sequence) {
        return sequence[(reelWord >>> 8) & 0x07] & 0xFF;
    }

    private static int nextFaceForReelWord(int reelWord, byte[] sequence) {
        return sequence[(((reelWord >>> 8) & 0x07) + 1) & 0x07] & 0xFF;
    }

    private static int nextSymbolFor(int currentSymbol, byte[] sequence) {
        if (sequence == null || sequence.length == 0) {
            return currentSymbol;
        }
        for (int i = 0; i < sequence.length; i++) {
            if ((sequence[i] & 0xFF) == (currentSymbol & 0xFF)) {
                return sequence[(i + 1) % sequence.length] & 0xFF;
            }
        }
        return currentSymbol & 0xFF;
    }
}
