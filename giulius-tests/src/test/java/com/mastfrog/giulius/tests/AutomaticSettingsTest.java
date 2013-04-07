package com.mastfrog.giulius.tests;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test that we do not need to specify SettingsLocations annotation for
 * a properties file whose name matches the test, in the same package as the
 * test.
 *
 * @author Tim Boudreau
 */
@TestWith(ModuleA.class)
public class AutomaticSettingsTest extends GuiceTest {

    @Test
    @Configurations("com/mastfrog/giulius/tests/AutomaticSettingsTest.properties")
    public void testWithExplicitLocation (Thing thing) {
        assertNotNull(thing);
        assertEquals ("I was loaded", thing.val);
    }

    @Test
    public void testNoExplicitLocation (Thing thing) {
        assertNotNull(thing);
        assertEquals ("I was loaded", thing.val);
    }

    @Test
    @Configurations("com/mastfrog/giulius/tests/AutomaticSettingsTestOverride.properties")
    public void testOverrideValueFromDefaultLocation (Thing thing) {
        assertNotNull(thing);
        assertEquals ("Something else", thing.val);
    }

    static class Thing {
        final String val;
        @Inject
        public Thing(@Named("automatic") String val) {
            this.val = val;
        }
    }

    @TestWith(iterate={A.class, B.class}, iterateSettings={"com/mastfrog/giulius/tests/oa.properties", "com/mastfrog/giulius/tests/ob.properties"})
    public void testSettingsOverrideOrderWithIteratedSettings(StringBuilder value, @Named("overridden") String overridden) {
        assertNotNull (value);
        assertNotSame(0, value.length());
        assertEquals (value.toString(), overridden);
    }

    static final class A extends AbstractModule {
        @Override
        protected void configure() {
            bind(StringBuilder.class).toInstance(new StringBuilder("A"));
        }
    }

    static final class B extends AbstractModule {
        @Override
        protected void configure() {
            bind(StringBuilder.class).toInstance(new StringBuilder("B"));
        }
    }

}
