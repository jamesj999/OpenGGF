package com.openggf.game.rewind.schema;

import com.openggf.game.rewind.FieldKey;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.game.GameModule;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRewindPolicyRegistry {
    @AfterEach
    void clearRegistry() {
        RewindSchemaRegistry.clearForTest();
    }

    @Test
    void exactFieldPolicyTakesPrecedenceOverTypeAndPackageRules() {
        RewindPolicyRegistry.registerPackagePolicy(
                ExactFieldFixture.class.getPackageName(),
                RewindFieldPolicy.TRANSIENT);
        RewindPolicyRegistry.registerDeclaredTypePolicy(
                UnsupportedValue.class,
                RewindFieldPolicy.STRUCTURAL);
        RewindPolicyRegistry.registerFieldPolicy(
                new FieldKey(ExactFieldFixture.class.getName(), "value"),
                RewindFieldPolicy.DEFERRED);

        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(ExactFieldFixture.class);

        assertPolicy(schema, "value", RewindFieldPolicy.DEFERRED);
    }

    @Test
    void declaredTypePolicyCanMarkUnsupportedMutableTypeAsStructural() {
        RewindPolicyRegistry.registerDeclaredTypePolicy(
                UnsupportedValue.class,
                RewindFieldPolicy.STRUCTURAL);

        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(ExactFieldFixture.class);

        assertPolicy(schema, "value", RewindFieldPolicy.STRUCTURAL);
        assertTrue(schema.unsupportedFields().isEmpty());
    }

    @Test
    void assignableTypePolicyAppliesToSubclasses() {
        RewindPolicyRegistry.registerAssignableTypePolicy(
                BaseUnsupportedValue.class,
                RewindFieldPolicy.TRANSIENT);

        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(AssignableFixture.class);

        assertPolicy(schema, "value", RewindFieldPolicy.TRANSIENT);
    }

    @Test
    void packagePolicyAppliesToDeclaredTypePackage() {
        RewindPolicyRegistry.registerPackagePolicy(
                UnsupportedValue.class.getPackageName(),
                RewindFieldPolicy.DEFERRED);

        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(ExactFieldFixture.class);

        assertPolicy(schema, "value", RewindFieldPolicy.DEFERRED);
    }

    @Test
    void annotationsRemainStrongerThanRegistryRules() {
        RewindPolicyRegistry.registerDeclaredTypePolicy(
                int.class,
                RewindFieldPolicy.CAPTURED);

        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(AnnotatedFixture.class);

        assertPolicy(schema, "value", RewindFieldPolicy.TRANSIENT);
    }

    @Test
    void forcedCapturedPolicyRequiresAnAvailableCodec() {
        RewindPolicyRegistry.registerDeclaredTypePolicy(
                UnsupportedValue.class,
                RewindFieldPolicy.CAPTURED);

        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(ExactFieldFixture.class);

        assertPolicy(schema, "value", RewindFieldPolicy.UNSUPPORTED);
        assertEquals(1, schema.unsupportedFields().size());
    }

    @Test
    void forcedCapturedPolicyUsesDefaultCodecWhenAvailable() {
        RewindPolicyRegistry.registerDeclaredTypePolicy(
                BitSet.class,
                RewindFieldPolicy.CAPTURED);

        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(SupportedCapturedFixture.class);

        assertPolicy(schema, "value", RewindFieldPolicy.CAPTURED);
        assertTrue(schema.unsupportedFields().isEmpty());
    }

    @Test
    void runtimeRendererAndServiceTypesAreTransientByDefaultWithoutAnnotations() {
        RewindClassSchema schema = RewindSchemaRegistry.schemaFor(DefaultRuntimePolicyFixture.class);

        assertPolicy(schema, "graphics", RewindFieldPolicy.TRANSIENT);
        assertPolicy(schema, "gameModule", RewindFieldPolicy.TRANSIENT);
        assertPolicy(schema, "objectRenderManager", RewindFieldPolicy.TRANSIENT);
        assertPolicy(schema, "objectServices", RewindFieldPolicy.TRANSIENT);
        assertPolicy(schema, "patternRenderer", RewindFieldPolicy.TRANSIENT);
        assertPolicy(schema, "playerRenderer", RewindFieldPolicy.TRANSIENT);
        assertPolicy(schema, "spritePieceRenderer", RewindFieldPolicy.TRANSIENT);
        assertTrue(schema.unsupportedFields().isEmpty());
    }

    private static void assertPolicy(RewindClassSchema schema, String fieldName, RewindFieldPolicy policy) {
        RewindFieldPlan plan = schema.fields().stream()
                .filter(field -> field.key().fieldName().equals(fieldName))
                .findFirst()
                .orElseThrow();
        assertEquals(policy, plan.policy());
    }

    private static class BaseUnsupportedValue {
        int state;
    }

    private static final class UnsupportedValue extends BaseUnsupportedValue {
    }

    private static final class ExactFieldFixture {
        UnsupportedValue value = new UnsupportedValue();
    }

    private static final class AssignableFixture {
        UnsupportedValue value = new UnsupportedValue();
    }

    private static final class AnnotatedFixture {
        @RewindTransient(reason = "test")
        int value;
    }

    private static final class SupportedCapturedFixture {
        BitSet value = new BitSet();
    }

    private static final class DefaultRuntimePolicyFixture {
        GraphicsManager graphics;
        GameModule gameModule;
        ObjectRenderManager objectRenderManager;
        ObjectServices objectServices;
        PatternSpriteRenderer patternRenderer;
        PlayerSpriteRenderer playerRenderer;
        SpritePieceRenderer spritePieceRenderer;
    }
}
