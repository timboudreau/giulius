package com.mastfrog.giulius.tests;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
@TestWith(SettingsModule.class)
@Configurations("com/mastfrog/giulius/tests/settings_test.properties")
public class TestModuleConstructionWithSettings extends GuiceTest {
    @Test
    public void test(String val) {
        assertNotNull(SettingsModule.settings);
        assertEquals("Hello from Settings", val);
    }

    @Test
    public void testSettingsConstructorIsPreferred(X x) {
        assertNotNull(x);
        assertEquals("I am named", x.named);
    }
    static class X {
        //special_value=I am named
        final String named;

        @Inject
        public X(@Named("special_value") String named) {
            this.named = named;
        }
    }
}
