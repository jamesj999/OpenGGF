package com.openggf.game.rewind.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CompactFieldCapturer {
    public static RewindObjectStateBlob capture(Object target) {
        return capture(target, RewindCaptureContext.none());
    }

    public static RewindObjectStateBlob capture(Object target, RewindCaptureContext context) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(context, "context");
        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(target.getClass());
        validateSupported(schema);

        RewindStateBuffer scalarData = new RewindStateBuffer();
        List<Object> opaqueValues = new ArrayList<>();
        for (RewindFieldPlan field : schema.capturedFields()) {
            field.codec().capture(field.field(), target, scalarData, opaqueValues, context);
        }
        return new RewindObjectStateBlob(schema.schemaId(), schema.type(), scalarData.toByteArray(), opaqueValues.toArray());
    }

    public static void restore(Object target, RewindObjectStateBlob blob) {
        restore(target, blob, RewindCaptureContext.none());
    }

    public static void restore(Object target, RewindObjectStateBlob blob, RewindCaptureContext context) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(blob, "blob");
        Objects.requireNonNull(context, "context");
        if (target.getClass() != blob.type()) {
            throw new IllegalArgumentException("Cannot restore rewind blob for " + blob.type().getName()
                    + " into " + target.getClass().getName() + ".");
        }

        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(target.getClass());
        validateSupported(schema);
        if (schema.schemaId() != blob.schemaId()) {
            throw new IllegalArgumentException("Cannot restore rewind blob with schema id " + blob.schemaId()
                    + " into schema id " + schema.schemaId() + " for " + schema.type().getName() + ".");
        }

        RewindStateBuffer.Reader scalarData = RewindStateBuffer.reader(blob.scalarData());
        Object[] opaqueValues = blob.opaqueValues();
        RewindCodec.OpaqueIndex opaqueIndex = new RewindCodec.OpaqueIndex();
        for (RewindFieldPlan field : schema.capturedFields()) {
            field.codec().restore(field.field(), target, scalarData, opaqueValues, opaqueIndex, context);
        }
    }

    private static void validateSupported(RewindClassSchema schema) {
        if (schema.unsupportedFields().isEmpty()) {
            return;
        }
        String fields = schema.unsupportedFields().stream()
                .map(field -> field.key().declaringClassName() + "." + field.key().fieldName())
                .reduce((left, right) -> left + ", " + right)
                .orElse("<unknown>");
        throw new IllegalStateException("Unsupported rewind fields on " + schema.type().getName() + ": " + fields);
    }

    private CompactFieldCapturer() {}
}
