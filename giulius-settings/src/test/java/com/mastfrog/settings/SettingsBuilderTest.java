package com.mastfrog.settings;

import com.mastfrog.util.collections.CollectionUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author tim
 */
public class SettingsBuilderTest {

    @Test
    public void test() throws IOException {
        assertTrue(true);
        SettingsBuilder b = new SettingsBuilder();
        Map<Character, String> m = new HashMap<>();
        m.put('f', "foo");
        m.put('b', "bar");
        m.put('q', "quux");

        b.parseCommandLineArguments("--foo", "23", "--bar", "57");

        Settings s = b.build();
        assertFalse(s instanceof MutableSettings);

        assertEquals(s + "", (Integer) 23, s.getInt("foo"));
        assertEquals(s + "", (Integer) 57, s.getInt("bar"));

        b = new SettingsBuilder();
        b.parseCommandLineArguments(m, "-f", "23", "-b", "57", "-q");
        s = b.build();

        assertEquals(s + "", (Integer) 23, s.getInt("foo"));
        assertEquals(s + "", (Integer) 57, s.getInt("bar"));
        assertNull(s.getString("f"));
        assertNull(s.getString("b"));

        b = new SettingsBuilder();
        b.parseCommandLineArguments("--foo", "--bar");
        s = b.build();
        assertEquals("true", s.getString("foo"));
        assertEquals("true", s.getString("bar"));

        b = new SettingsBuilder();
        b.parseCommandLineArguments("--foo");
        s = b.build();
        assertEquals("true", s.getString("foo"));

        b = new SettingsBuilder();
        b.parseCommandLineArguments(m, "-fbq");
        s = b.build();
        assertEquals("true", s.getString("foo"));
        assertEquals("true", s.getString("bar"));
        assertEquals("true", s.getString("quux"));
    }

    @Test
    public void testLayeredSettingsToProperties() throws IOException {
        SettingsBuilder b = new SettingsBuilder("foo")
                .add("foo", "bar").addSystemProperties().addEnv()
                .parseCommandLineArguments("--foo", "--baz");
        Settings s = b.build();
        assertTrue(s.getClass().getName(), s instanceof LayeredSettings);
        Properties p = s.toProperties();
        assertTrue(p.keySet().toString(), p.keySet().equals(s.allKeys()));
    }

    @Test
    public void testEnvPrecedence() throws IOException {
        SettingsBuilder b = new SettingsBuilder("stuff").add("intprop", 52).addDefaultLocationsAndParseArgs("--intprop", "423");
        Settings s = b.build();
        assertEquals(Integer.valueOf(32), s.getInt("intprop"));
        assertEquals("hello", s.getString("some.property"));
        assertEquals("woo hoo", s.getString("another.property"));

        Map<Character, String> exts = CollectionUtils.<Character, String>map('i').to("intprop").build();
        s = new SettingsBuilder("stuff").add("intprop", 52).addDefaultLocationsAndParseArgs(exts, "-i", "423").build();
        assertEquals(Integer.valueOf(32), s.getInt("intprop"));
        assertEquals("hello", s.getString("some.property"));
        assertEquals("woo hoo", s.getString("another.property"));

        s = new SettingsBuilder("stuff").add("intprop", 52).add("some.property", "yuck").add("another.property", "flooz")
                .restrictEnvironmentProperties("some.property")
                .addDefaultLocationsAndParseArgs(exts, "-i", "423").build();
        assertEquals(Integer.valueOf(423), s.getInt("intprop"));
        assertEquals("hello", s.getString("some.property"));
        assertEquals("flooz", s.getString("another.property"));
    }

    @Test
    public void testPrecedence() throws IOException {
        SettingsBuilder b = new SettingsBuilder("x").add("a", "b");
        b.add("c", "d");
        b.add("e", "f");
        b.add("a", "c");
        // Ensure we are coalescing layers, but not modifying properties
        // objects we were passed
        assertEquals(2, b.sourceCount());
        Settings s = b.build();
        assertEquals("c", s.getString("a"));
        assertEquals("d", s.getString("c"));

        b.restrictEnvironmentProperties("some.property").addEnv();
        assertEquals(3, b.sourceCount());
        // Ensure that we don't try to write into an EnvironmentProperties
        // instance and throw an exception
        b.add("hey", "you");
        b.add("woo", "hoo");
        b.add("a", "d");
        assertEquals(4, b.sourceCount());
    }
}
