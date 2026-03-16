package com.openggf.game.sonic3k.specialstage;

import java.io.IOException;

import static com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageConstants.*;

/**
 * 3D projection system for the S3K Blue Ball special stage.
 * <p>
 * Transforms the player's position in grid-space to screen coordinates
 * using the ScalarTable2 (sine/cosine lookup) and a series of matrix
 * multiplication routines.
 * <p>
 * The ROM uses pre-computed scalar indices ({@code SStage_scalar_index_0/1/2})
 * that index into the ScalarTable2 (loaded from Scalars.bin) to produce
 * intermediate results used in the final 3D-to-2D projection.
 * <p>
 * Reference: docs/skdisasm/sonic3k.asm
 * sub_950C (line 11867), sub_953E (line 11887), GetScalars2 (line 13416)
 */
public class Sonic3kSpecialStageScalars {

    /** ScalarTable2 from ROM (256 signed word entries = 512 bytes). */
    private short[] scalarTable;

    /** Scalar indices computed from player angle/position. */
    private int scalarIndex0;
    private int scalarIndex1;
    private int scalarIndex2;

    /** Computed scalar results (sine/cosine pairs). */
    private int scalarResult0Sin, scalarResult0Cos;
    private int scalarResult1Sin, scalarResult1Cos;
    private int scalarResult2Sin, scalarResult2Cos;
    private int scalarResult3Sin, scalarResult3Cos;

    /**
     * Load the scalar table from ROM data.
     *
     * @param rawData 512 bytes of signed 16-bit big-endian entries
     */
    public void loadTable(byte[] rawData) {
        scalarTable = new short[256];
        for (int i = 0; i < 256 && i * 2 + 1 < rawData.length; i++) {
            scalarTable[i] = (short) (((rawData[i * 2] & 0xFF) << 8)
                    | (rawData[i * 2 + 1] & 0xFF));
        }
    }

    /**
     * Compute scalar results for the current frame.
     * ROM: sub_950C (sonic3k.asm:11867)
     * <p>
     * Uses GetScalars2 to read sine/cosine pairs from the table
     * for 3 scalar indices plus a constant (0xE0).
     */
    public void computeScalars() {
        // GetScalars2 reads: table[index*2] and table[(index*2 + 0x80) & 0x1FE]
        int[] r2 = getScalarPair(scalarIndex2);
        scalarResult2Sin = r2[0];
        scalarResult2Cos = r2[1];

        int[] r1 = getScalarPair(scalarIndex1);
        scalarResult1Sin = r1[0];
        scalarResult1Cos = r1[1];

        int[] r0 = getScalarPair(scalarIndex0);
        scalarResult0Sin = r0[0];
        scalarResult0Cos = r0[1];

        // Constant 0xE0 for the 4th pair
        int[] r3 = getScalarPair(0xE0);
        scalarResult3Sin = r3[0];
        scalarResult3Cos = r3[1];
    }

    /**
     * GetScalars2: read a sine/cosine pair from the scalar table.
     * ROM: GetScalars2 (sonic3k.asm:13416)
     *
     * @param index the scalar index
     * @return [sine, cosine] values from the table
     */
    private int[] getScalarPair(int index) {
        if (scalarTable == null) {
            return new int[]{0, 0};
        }
        int idx1 = (index * 2) & 0x1FE;
        int idx2 = (idx1 + 0x80) & 0x1FE;
        // Each index selects a word from the 256-entry table
        int sin = scalarTable[idx1 >> 1];
        int cos = scalarTable[idx2 >> 1];
        return new int[]{sin, cos};
    }

    /**
     * Project the player's 3D grid-space position to screen coordinates.
     * ROM: sub_953E (sonic3k.asm:11887)
     * <p>
     * Uses the computed scalar results to perform matrix multiplication
     * transforming (playerX, playerY, playerZ) to (screenX, screenY).
     *
     * @param objX player object field $34 (X in 3D space)
     * @param objY player object field $36 (Y/height in 3D space)
     * @param objZ player object field $38 (Z/depth in 3D space)
     * @param centerX player object field $30 (screen center X, typically 0xA0)
     * @param centerY player object field $32 (screen center Y, typically 0x70)
     * @return [screenX, screenY]
     */
    public int[] project(int objX, int objY, int objZ, int centerX, int centerY) {
        // ROM sub_953E performs a series of matrix multiplications
        // using sub_A1DC, sub_A1B2, sub_A188, sub_A206
        // The final result is: screenX = projected_x / z + centerX
        //                      screenY = projected_y / z + centerY

        // Simplified projection using the scalar results
        // sub_A1DC: rotate by scalar_result_2
        int d1 = objX;
        int d2 = objY;
        int d0 = objZ;

        // Apply rotation transforms using scalar pairs
        // These are ROM-accurate matrix multiplications
        int r2s = scalarResult2Sin;
        int r2c = scalarResult2Cos;

        // sub_A1DC: d1' = d1*cos - d2*sin, d2' = d1*sin + d2*cos
        int newD1 = ((d1 * r2c) - (d2 * r2s)) >> 8;
        int newD2 = ((d1 * r2s) + (d2 * r2c)) >> 8;
        d1 = newD1;
        d2 = newD2;

        // sub_A1B2: rotate by scalar_result_1
        int r1s = scalarResult1Sin;
        int r1c = scalarResult1Cos;
        int tempD0 = ((d0 * r1c) - (d1 * r1s)) >> 8;
        d1 = ((d0 * r1s) + (d1 * r1c)) >> 8;
        d0 = tempD0;

        // sub_A188: rotate by scalar_result_0
        int r0s = scalarResult0Sin;
        int r0c = scalarResult0Cos;
        int d3 = d1;
        int d4 = d2;
        d3 = (d3 * r0c) >> 8;
        d4 = (d4 * r0c) >> 8;
        d1 = (d1 * r0s) >> 8;
        d2 = (d2 * r0s) >> 8;
        d1 = (d4 - d1) * 4;
        d2 = (d3 + d2) * 4;

        // sub_A206: apply constant offset
        d0 += 0x100;
        d2 += 0x980;

        // Final perspective division
        if (d0 != 0) {
            d1 = (d1 << 8) / d0;
            d2 = (d2 << 8) / d0;
        }

        // Add center offset
        int screenX = d1 + centerX;
        int screenY = d2 + centerY;

        return new int[]{screenX, screenY};
    }

    /**
     * Set scalar indices (normally derived from player angle/movement).
     */
    public void setScalarIndices(int idx0, int idx1, int idx2) {
        this.scalarIndex0 = idx0;
        this.scalarIndex1 = idx1;
        this.scalarIndex2 = idx2;
    }

    public boolean isLoaded() {
        return scalarTable != null;
    }
}
