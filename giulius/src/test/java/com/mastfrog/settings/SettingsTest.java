package com.mastfrog.settings;

import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.annotations.Defaults;
import com.mastfrog.giulius.annotations.Namespace;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Timer;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
@Defaults({"auto=true", "whee=whoopty"})
public class SettingsTest {
    
    @Test
    public void testEphemeralMutableSettingsAreBound() throws IOException {
        Settings s = new SettingsBuilder().build();
        Dependencies deps = Dependencies.builder().useMutableSettings().add(s, Namespace.DEFAULT).build();
        MutableSettings a = deps.getInstance(MutableSettings.class);
        MutableSettings b = deps.getInstance(MutableSettings.class);
        assertNotSame(a, b);
        a.setInt("foo", 23);
        assertEquals(23, (int) a.getInt("foo"));
        assertNull(b.getString("foo"));
    }

    @Test
    public void test() throws IOException {
        Settings config = new SettingsBuilder().addDefaultsFromClasspath().addGeneratedDefaultsFromClasspath().build();
        assertNotNull(config);
        assertTrue(config.getBoolean("auto"));

        assertEquals("bar", config.getString("foo"));

        assertFalse(config.getBoolean("hey", false));
        assertNull(config.getString("Nothing!"));

        assertEquals("whoopty", config.getString("whee"));
    }

    @Test
    public void testLoad() throws IOException {
        com.mastfrog.settings.Settings config = new SettingsBuilder().add("com/mastfrog/configuration/others.properties").add("com/mastfrog/configuration/more.properties").build();
        for (String s : config) {
            System.out.println(s + "=" + config.getString(s));
        }
        assertTrue(config.allKeys().contains("whee"));
        assertEquals("oobiedoobie", config.getString("whee"));
        assertTrue(config.allKeys().contains("foo"));
        assertEquals("fiz", config.getString("foo"));

        config = new SettingsBuilder().add("com/mastfrog/configuration/others.properties").add("com/mastfrog/configuration/more.properties").add("com/mastfrog/configuration/andmore.properties").build();
        assertTrue(config.allKeys().contains("abc"));
        assertEquals("def", config.getString("abc"));
    }

    @Test
    public void testLayered() throws IOException {
        com.mastfrog.settings.Settings config = new SettingsBuilder().addDefaultsFromClasspath().addGeneratedDefaultsFromClasspath().build();
        Settings config2 = new SettingsBuilder().add(config).add("com/mastfrog/configuration/others.properties").add("com/mastfrog/configuration/more.properties").build();
        assertEquals("oobiedoobie", config2.getString("whee"));
    }

    @Test
    public void testToProperties() throws IOException {
        com.mastfrog.settings.Settings config = new SettingsBuilder().addDefaultsFromClasspath().addGeneratedDefaultsFromClasspath().add(
                "com/mastfrog/configuration/others.properties").add(
                "com/mastfrog/configuration/more.properties").add(
                "com/mastfrog/configuration/andmore.properties").build();

        Properties p = config.toProperties();
        assertEquals(new HashSet<>(Arrays.asList("age", "auto", "bang", "whee", "foo", "abc", "liesel", "monkey")), new HashSet<>(p.stringPropertyNames()));
        assertEquals("fiz", p.getProperty("foo"));
    }

    @Test
    public void testLifecycle() throws Exception {
        RefreshInterval interval = SettingsRefreshInterval.CLASSPATH;
        interval.setMilliseconds(1);
        Object notify = new Object();
        PS ps = new PS(interval, notify);
        Settings s = new SettingsBuilder().add(ps).build();
        Reference<Settings> ref = new WeakReference<>(s);
        synchronized(notify) {
            notify.wait(40);
        }
        int cc = ps.callCount;
        assertNotSame("Refresh task not called",0, cc);
        synchronized(notify) {
            notify.wait(40);
        }
        assertNotSame("Refresh task not being called continuously",cc, ps.callCount);
        s = null;
        for (int i = 0; i < 10; i++) {
            System.gc();
            if (ref.get() == null) {
                break;
            }
            Thread.sleep(20);
        }
        assertNull("Settings not garbage collected",ref.get());

        cc = ps.callCount;
        synchronized(notify) {
            notify.wait(30);
        }

        assertSame("Settings garbage collected, but its internals are "
                + "still being refreshed", cc, ps.callCount);
    }

    private static class PS extends SettingsBuilder.PropertiesSource {

        private Properties props = new Properties();
        private final Object notify;

        PS(RefreshInterval interval, Object notify) {
            super (interval);
            props.setProperty("test", "foo");
            this.notify = notify;
        }
        volatile int callCount;

        @Override
        public Properties getProperties() throws IOException {
            callCount++;
            synchronized(notify) {
                notify.notifyAll();
            }
            return props;
        }
    }
    

    @Test
    public void testWritable() throws IOException {
        assertTrue(true);
        Settings settings = SettingsBuilder.createDefault().build();
        assertNotNull(settings.getString("foo"));
        assertEquals("bar", settings.getString("foo"));
        assertNotNull(settings.getString("os.name"));
        assertEquals(System.getProperty("os.name"), settings.getString("os.name"));

        WritableSettings w = new WritableSettings("whoo", settings);
        w.setString("wow", "its writable");
        assertNotNull(w.getString("wow"));
        assertEquals("its writable", w.getString("wow"));
        assertEquals("x", w.getString("wubbity", "x"));
        assertEquals(23, w.getInt("age", -1));
        assertEquals(23L, w.getLong("age", -1L));
        assertNull(settings.getString("wow"));

        assertEquals(System.getProperty("os.name"), w.getString("os.name"));
        w.setString("os.name", "BeOS");
        assertEquals("BeOS", w.getString("os.name"));

        w.clear("os.name");
        assertNull(w.getString("os.name"));

        w.setString("os.name", "OS/2");
        assertEquals("OS/2", w.getString("os.name"));
    }

    /*
     @Test
     public void testRemote() throws Exception {
     Settings s = new SettingsBuilder().add(new URL("http://timboudreau.com/test.properties"), 2000).build();
     String val = s.getString("remotevalue");
     assertNotNull(val);
     assertEquals("Hey, it worked", val);
     for (int i = 0; i < 120; i++) {
     System.out.println(i + ":" + s.getString("cloud"));
     Thread.sleep(1000);
     }
     }
     */    
}
