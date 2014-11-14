package com.mastfrog.settings;

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
        assertTrue (s.getClass().getName(), s instanceof LayeredSettings);
        Properties p = s.toProperties();
        assertTrue(p.keySet().toString(), p.keySet().equals(s.allKeys()));
    }
}
