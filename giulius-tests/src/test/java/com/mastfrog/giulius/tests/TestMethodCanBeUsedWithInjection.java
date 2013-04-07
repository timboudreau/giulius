package com.mastfrog.giulius.tests;

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith(ModuleA.class)
public class TestMethodCanBeUsedWithInjection {
    @Test
    public void test(String val) {
        assertNotNull (val);
        assertEquals ("A!", val);
    }
}
