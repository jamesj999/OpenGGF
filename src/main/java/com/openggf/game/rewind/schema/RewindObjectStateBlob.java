package com.openggf.game.rewind.schema;

import java.util.Arrays;
import java.util.Objects;

public final class RewindObjectStateBlob {
    private final int schemaId;
    private final Class<?> type;
    private final byte[] scalarData;
    private final Object[] opaqueValues;

    public RewindObjectStateBlob(int schemaId, Class<?> type, byte[] scalarData, Object[] opaqueValues) {
        this.schemaId = schemaId;
        this.type = Objects.requireNonNull(type, "type");
        this.scalarData = Arrays.copyOf(Objects.requireNonNull(scalarData, "scalarData"), scalarData.length);
        this.opaqueValues = Arrays.copyOf(Objects.requireNonNull(opaqueValues, "opaqueValues"), opaqueValues.length);
    }

    public int schemaId() {
        return schemaId;
    }

    public Class<?> type() {
        return type;
    }

    public byte[] scalarData() {
        return Arrays.copyOf(scalarData, scalarData.length);
    }

    public Object[] opaqueValues() {
        return Arrays.copyOf(opaqueValues, opaqueValues.length);
    }
}
