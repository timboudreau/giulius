package com.mastfrog.giulius.tests;

import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(GuiceRunner.class)
@Configurations(value = {"com/mastfrog/giulius/tests/a.properties", "com/mastfrog/giulius/tests/b.properties"})
public class TestFailureModes {
    
    @Test(expected=ConfigurationException.class)
    public void xx (@Named("X") String x, Foo foo) {
        assertNull ("Expected null got " + x, x);
        assertNull (foo.nothing);
    }

    static class Foo {
        @Inject
        @Named("Nothing")
        String nothing;
    }

//    @Test(expected=ConfigurationException.class)
//    public void xxx (@Named("X") String x) {
//        fail("This should not be run");
//    }

}
