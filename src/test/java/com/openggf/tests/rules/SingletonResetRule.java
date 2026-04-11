package com.openggf.tests.rules;

import com.openggf.tests.TestEnvironment;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * JUnit 4 rule that resets singleton state before each test.
 * Equivalent of {@link com.openggf.tests.SingletonResetExtension} for JUnit 5.
 *
 * <p>Usage:
 * <pre>
 * public class MyTest {
 *     {@literal @}Rule
 *     public SingletonResetRule resetRule = new SingletonResetRule();
 * }
 * </pre>
 */
public class SingletonResetRule implements TestRule {
    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                TestEnvironment.resetAll();
                base.evaluate();
            }
        };
    }
}
