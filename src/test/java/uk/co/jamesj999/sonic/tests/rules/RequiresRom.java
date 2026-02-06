package uk.co.jamesj999.sonic.tests.rules;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a test class needs a real ROM loaded.
 * The {@link RequiresRomRule} will load the ROM, detect the game module,
 * and configure {@link uk.co.jamesj999.sonic.data.RomManager} before each test.
 * If the ROM is unavailable, tests are gracefully skipped.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RequiresRom {
    SonicGame value();
}
