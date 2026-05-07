package com.openggf.game.rewind.schema;

import com.openggf.game.rewind.FieldKey;

import java.lang.reflect.Field;
import java.util.Objects;

public record RewindFieldPlan(FieldKey key, Field field, RewindFieldPolicy policy, RewindCodec codec) {
    public RewindFieldPlan(FieldKey key, Field field, RewindFieldPolicy policy) {
        this(key, field, policy, null);
    }

    public RewindFieldPlan {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(policy, "policy");
        if (policy == RewindFieldPolicy.CAPTURED && codec == null) {
            throw new IllegalArgumentException("Captured rewind field requires a codec: " + key);
        }
        if (policy == RewindFieldPolicy.CAPTURED) {
            field.setAccessible(true);
        }
    }

    public boolean captured() {
        return policy == RewindFieldPolicy.CAPTURED;
    }
}
