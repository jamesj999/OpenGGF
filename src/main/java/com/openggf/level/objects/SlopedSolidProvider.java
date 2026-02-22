package com.openggf.level.objects;

public interface SlopedSolidProvider extends SolidObjectProvider {
    byte[] getSlopeData();

    boolean isSlopeFlipped();

    /**
     * Returns the baseline value to subtract from slope samples.
     * Default implementation returns slopeData[0] (existing behavior for most objects).
     * Override to return 0 for objects like Seesaw where the ROM uses absolute slope values.
     */
    default int getSlopeBaseline() {
        byte[] data = getSlopeData();
        return (data != null && data.length > 0) ? (byte) data[0] : 0;
    }
}
