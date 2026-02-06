package uk.co.jamesj999.sonic.tests.rules;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a test class needs a game module configured but not a real ROM.
 * Useful for tests that use a MockRom or don't need ROM data at all.
 * The {@link RequiresRomRule} will set the appropriate {@link uk.co.jamesj999.sonic.game.GameModule}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RequiresGameModule {
    SonicGame value();
}
