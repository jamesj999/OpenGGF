package com.openggf.game.rewind.schema;

import com.openggf.game.rewind.FieldKey;
import com.openggf.game.GameModule;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RewindPolicyRegistry {
    private static final Map<Class<?>, RewindFieldPolicy> DEFAULT_DECLARED_TYPE_POLICIES = Map.of(
            GameModule.class, RewindFieldPolicy.TRANSIENT,
            GraphicsManager.class, RewindFieldPolicy.TRANSIENT,
            ObjectRenderManager.class, RewindFieldPolicy.TRANSIENT,
            ObjectServices.class, RewindFieldPolicy.TRANSIENT,
            PatternSpriteRenderer.class, RewindFieldPolicy.TRANSIENT,
            PlayerSpriteRenderer.class, RewindFieldPolicy.TRANSIENT,
            SpritePieceRenderer.class, RewindFieldPolicy.TRANSIENT
    );

    private static final Map<FieldKey, RewindFieldPolicy> FIELD_POLICIES = new LinkedHashMap<>();
    private static final Map<Class<?>, RewindFieldPolicy> DECLARED_TYPE_POLICIES = new LinkedHashMap<>();
    private static final Map<Class<?>, RewindFieldPolicy> ASSIGNABLE_TYPE_POLICIES = new LinkedHashMap<>();
    private static final List<PackagePolicy> PACKAGE_POLICIES = new ArrayList<>();

    public static synchronized void registerFieldPolicy(FieldKey field, RewindFieldPolicy policy) {
        FIELD_POLICIES.put(Objects.requireNonNull(field, "field"), requirePolicy(policy));
    }

    public static synchronized void registerDeclaredTypePolicy(Class<?> type, RewindFieldPolicy policy) {
        DECLARED_TYPE_POLICIES.put(Objects.requireNonNull(type, "type"), requirePolicy(policy));
    }

    public static synchronized void registerAssignableTypePolicy(Class<?> type, RewindFieldPolicy policy) {
        ASSIGNABLE_TYPE_POLICIES.put(Objects.requireNonNull(type, "type"), requirePolicy(policy));
    }

    public static synchronized void registerPackagePolicy(String packagePrefix, RewindFieldPolicy policy) {
        PACKAGE_POLICIES.add(new PackagePolicy(
                normalizePackagePrefix(packagePrefix),
                requirePolicy(policy)));
    }

    static synchronized Optional<RewindFieldPolicy> policyFor(Field field) {
        Objects.requireNonNull(field, "field");

        RewindFieldPolicy fieldPolicy = FIELD_POLICIES.get(FieldKey.of(field));
        if (fieldPolicy != null) {
            return Optional.of(fieldPolicy);
        }

        Class<?> declaredType = field.getType();
        RewindFieldPolicy declaredTypePolicy = DECLARED_TYPE_POLICIES.get(declaredType);
        if (declaredTypePolicy != null) {
            return Optional.of(declaredTypePolicy);
        }

        for (Map.Entry<Class<?>, RewindFieldPolicy> entry : ASSIGNABLE_TYPE_POLICIES.entrySet()) {
            if (entry.getKey().isAssignableFrom(declaredType)) {
                return Optional.of(entry.getValue());
            }
        }

        String packageName = declaredType.getPackageName();
        for (PackagePolicy policy : PACKAGE_POLICIES) {
            if (packageName.equals(policy.packagePrefix())
                    || packageName.startsWith(policy.packagePrefix() + ".")) {
                return Optional.of(policy.policy());
            }
        }

        RewindFieldPolicy defaultDeclaredTypePolicy = DEFAULT_DECLARED_TYPE_POLICIES.get(declaredType);
        if (defaultDeclaredTypePolicy != null) {
            return Optional.of(defaultDeclaredTypePolicy);
        }

        RewindFieldPolicy defaultObjectPolicy = DefaultObjectRewindPolicies.policyFor(field);
        if (defaultObjectPolicy != null) {
            return Optional.of(defaultObjectPolicy);
        }

        return Optional.empty();
    }

    public static synchronized Optional<RewindFieldPolicy> policyForAudit(Field field) {
        return policyFor(field);
    }

    static synchronized void clearForTest() {
        FIELD_POLICIES.clear();
        DECLARED_TYPE_POLICIES.clear();
        ASSIGNABLE_TYPE_POLICIES.clear();
        PACKAGE_POLICIES.clear();
    }

    private static RewindFieldPolicy requirePolicy(RewindFieldPolicy policy) {
        return Objects.requireNonNull(policy, "policy");
    }

    private static String normalizePackagePrefix(String packagePrefix) {
        String normalized = Objects.requireNonNull(packagePrefix, "packagePrefix").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("packagePrefix must not be empty");
        }
        return normalized;
    }

    private record PackagePolicy(String packagePrefix, RewindFieldPolicy policy) {
    }

    private RewindPolicyRegistry() {}
}
