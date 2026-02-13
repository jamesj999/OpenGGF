package uk.co.jamesj999.sonic.physics;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestSwingMotion {

    @Test
    public void swingsDownFromZeroVelocity() {
        // direction=true (down), accel=0x18, vel=0, max=0x100
        var result = SwingMotion.update(0x18, 0, 0x100, true);
        assertEquals(0x18, result.velocity());
        assertTrue(result.directionDown());
        assertFalse(result.directionChanged());
    }

    @Test
    public void reversesAtMaxVelocity() {
        // direction=true (down), vel is near max
        var result = SwingMotion.update(0x18, 0xF0, 0x100, true);
        // 0xF0 + 0x18 = 0x108 >= 0x100, should reverse
        assertEquals(0xF0 + 0x18, result.velocity()); // velocity still updated
        assertFalse(result.directionDown()); // direction flipped
        assertTrue(result.directionChanged());
    }

    @Test
    public void swingsUpFromZero() {
        // direction=false (up), accel=0x18, vel=0, max=0x100
        var result = SwingMotion.update(0x18, 0, 0x100, false);
        assertEquals(-0x18, result.velocity());
        assertFalse(result.directionDown());
        assertFalse(result.directionChanged());
    }

    @Test
    public void reversesAtNegativeMaxVelocity() {
        // direction=false (up), vel is near -max
        var result = SwingMotion.update(0x18, -0xF0, 0x100, false);
        // -0xF0 - 0x18 = -0x108, magnitude >= 0x100, should reverse
        assertTrue(result.directionDown());
        assertTrue(result.directionChanged());
    }
}
