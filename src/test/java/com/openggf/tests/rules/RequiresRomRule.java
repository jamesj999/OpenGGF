package com.openggf.tests.rules;

import com.openggf.tests.TestEnvironment;
import org.junit.Assume;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1GameModule;

/**
 * JUnit 4 rule that handles ROM loading and game module configuration
 * based on {@link RequiresRom} or {@link RequiresGameModule} annotations.
 *
 * <p>Usage:
 * <pre>
 * {@literal @}RequiresRom(SonicGame.SONIC_2)
 * public class MyTest {
 *     {@literal @}Rule
 *     public RequiresRomRule romRule = new RequiresRomRule();
 *
 *     {@literal @}Test
 *     public void testSomething() {
 *         Rom rom = romRule.rom();
 *         // ...
 *     }
 * }
 * </pre>
 */
public class RequiresRomRule implements TestRule {
    private Rom rom;

    @Override
    public Statement apply(Statement base, Description description) {
        Class<?> testClass = description.getTestClass();
        RequiresRom requiresRom = testClass.getAnnotation(RequiresRom.class);
        RequiresGameModule requiresModule = testClass.getAnnotation(RequiresGameModule.class);

        if (requiresRom != null && requiresModule != null) {
            throw new IllegalStateException(
                    "@RequiresRom and @RequiresGameModule are mutually exclusive on " + testClass.getName());
        }

        if (requiresRom != null) {
            return applyRequiresRom(base, requiresRom.value());
        }
        if (requiresModule != null) {
            return applyRequiresGameModule(base, requiresModule.value());
        }

        // No annotation — pass through
        return base;
    }

    /**
     * Returns the loaded ROM. Only valid when {@link RequiresRom} is present
     * and the ROM was successfully loaded.
     */
    public Rom rom() {
        return rom;
    }

    private Statement applyRequiresRom(Statement base, SonicGame game) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TestEnvironment.resetAll();
                rom = RomCache.getRom(game);
                Assume.assumeTrue(
                        game.getDisplayName() + " ROM not available — skipping test",
                        rom != null);
                GameModuleRegistry.detectAndSetModule(rom);
                RuntimeManager.destroyCurrent();
                SessionManager.clear();
                RuntimeManager.createGameplay();
                RomManager.getInstance().setRom(rom);
                base.evaluate();
            }
        };
    }

    private Statement applyRequiresGameModule(Statement base, SonicGame game) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TestEnvironment.resetAll();
                switch (game) {
                    case SONIC_1 -> GameModuleRegistry.setCurrent(new Sonic1GameModule());
                    case SONIC_2 -> GameModuleRegistry.reset();
                    case SONIC_3K -> throw new UnsupportedOperationException(
                            "Sonic 3K GameModule not yet implemented");
                }
                RuntimeManager.destroyCurrent();
                SessionManager.clear();
                RuntimeManager.createGameplay();
                base.evaluate();
            }
        };
    }
}
