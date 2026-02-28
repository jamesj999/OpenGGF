package com.openggf.tests.rules;

import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameModuleRegistry;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 (Jupiter) equivalent of {@link RequiresRomRule}.
 * <p>
 * Reads the {@link RequiresRom} annotation on the test class, checks ROM
 * availability, and disables the test when the ROM is absent. When the ROM
 * is present it resets test environment, loads the ROM, detects the game
 * module, and configures {@link RomManager}.
 * <p>
 * Usage:
 * <pre>
 * {@literal @}RequiresRom(SonicGame.SONIC_3K)
 * {@literal @}ExtendWith(RequiresRomCondition.class)
 * class MyJupiterTest {
 *     // ...
 * }
 * </pre>
 */
public class RequiresRomCondition implements ExecutionCondition, BeforeAllCallback {

    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(RequiresRomCondition.class);

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        RequiresRom annotation = testClass.getAnnotation(RequiresRom.class);
        if (annotation == null) {
            return ConditionEvaluationResult.enabled("No @RequiresRom annotation");
        }

        SonicGame game = annotation.value();
        Rom rom = RomCache.getRom(game);
        if (rom == null) {
            return ConditionEvaluationResult.disabled(
                    game.getDisplayName() + " ROM not available — skipping test");
        }
        return ConditionEvaluationResult.enabled(game.getDisplayName() + " ROM available");
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        RequiresRom annotation = testClass.getAnnotation(RequiresRom.class);
        if (annotation == null) {
            return;
        }

        SonicGame game = annotation.value();
        Rom rom = RomCache.getRom(game);
        if (rom == null) {
            return; // Will be skipped by evaluateExecutionCondition
        }

        TestEnvironment.resetAll();
        GameModuleRegistry.detectAndSetModule(rom);
        RomManager.getInstance().setRom(rom);

        // Store rom in extension store for test access
        context.getStore(NS).put("rom", rom);
    }

    /**
     * Retrieves the loaded ROM from the extension context.
     */
    public static Rom getRom(ExtensionContext context) {
        return context.getStore(NS).get("rom", Rom.class);
    }
}
