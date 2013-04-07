package com.mastfrog.giulius.tests;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
/**
 *
 * @author Tim Boudreau
 */
@RunWith (GuiceRunner.class)
public class TestIterateWithOneValue {
    @TestWith(value={ModuleC.class}, iterate={ModuleA.class})
    public void testIterateOne(String val, StringBuilder sb) {
        assertNotNull (val);
        assertEquals ("A!", val);
        assertNotNull (sb);
    }

    @TestWith(iterate={ModuleA.class})
    public void testIterateOneWithNoFixedModules(String val) {
        assertNotNull (val);
        assertEquals ("A!", val);
    }
}
