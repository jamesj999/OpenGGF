package uk.co.jamesj999.sonic.sprites.playable;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestSuperStateController {
    @Test
    public void superStateEnumValues() {
        assertEquals(SuperState.NORMAL, SuperState.valueOf("NORMAL"));
        assertEquals(SuperState.TRANSFORMING, SuperState.valueOf("TRANSFORMING"));
        assertEquals(SuperState.SUPER, SuperState.valueOf("SUPER"));
        assertEquals(SuperState.REVERTING, SuperState.valueOf("REVERTING"));
        assertEquals(4, SuperState.values().length);
    }
}
