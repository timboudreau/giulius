package com.mastfrog.giulius.tests;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith(value=ModuleC.class, iterate={ModuleA.class, ModuleB.class})
public class TestIteration {
    static int runcount;
    static List<String> injectedValues = new ArrayList<String>();

    @Inject
    String val;
    @Test
    public void iterative() {
        injectedValues.add(val);
        runcount++;
        if (runcount == 2) {
            assertEquals (2, injectedValues.size());
            assertTrue (injectedValues.contains("A!"));
            assertTrue (injectedValues.contains("B!"));
            // Will randomly fail if JUnit > 4.7 parallel tests enabled
            assertEquals ("Order not obeyed", injectedValues.get(1),"B!");
        }
    }
}
