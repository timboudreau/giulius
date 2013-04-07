package com.mastfrog.giulius.tests;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 *
 * @author Tim Boudreau
 */
@TestWith({ModuleA.class, ModuleC.class})
@RunWith(GuiceRunner.class)
public class TestTest {

    @Inject
    String val;

    @Inject
    StringBuilder sb;

    @Test
    public void test() {
        assertEquals (val, "A!");
        assertNotNull (sb);
        assertEquals ("Injected by C", sb.toString());
    }

}
