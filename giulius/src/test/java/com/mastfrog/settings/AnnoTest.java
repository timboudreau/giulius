package com.mastfrog.settings;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.DependenciesBuilder;
import com.mastfrog.giulius.annotations.Defaults;
import com.mastfrog.giulius.annotations.Namespace;
import com.mastfrog.giulius.annotations.Value;
import com.mastfrog.util.Streams;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
@Defaults(value = {"a=b", "b=a"}, location = AnnoTest.LOC)
public class AnnoTest {
    public static final String LOC = "ms/glo/whoopie.foo";

    @Test
    public void test() throws Throwable {
        InputStream[] ins = Streams.locate(LOC);
        assertNotNull(ins);
        assertEquals(1, ins.length);
        Properties p = new Properties();
        p.load(ins[0]);
        assertEquals("b", p.getProperty("a"));
        assertEquals("a", p.getProperty("b"));

        Settings settings = new SettingsBuilder().add(LOC).build();
        assertEquals("b", settings.getString("a"));
        assertEquals("a", settings.getString("b"));
    }

    @Test
    public void testNamespace() throws Exception {
        InputStream[] ins = Streams.locate(SettingsBuilder.DEFAULT_PATH + "generated-foo.properties");
        assertNotNull(ins);
        assertEquals(1, ins.length);
        Properties p = new Properties();
        for (InputStream i : ins) {
            p.load(i);
            i.close();
        }
        assertEquals("you", p.getProperty("me"));

        ins = Streams.locate(SettingsBuilder.DEFAULT_PATH + "generated-ns1.properties");
        assertNotNull(ins);
        assertEquals(1, ins.length);
        p.clear();
        for (InputStream i : ins) {
            p.load(i);
            i.close();
        }
        assertEquals("namespaced", p.getProperty("ns1prop"));

        Settings sb = new SettingsBuilder("foo").addDefaultsFromClasspath().addGeneratedDefaultsFromClasspath().build();
        assertEquals("poodle", sb.getString("monkey"));

        DependenciesBuilder dp = Dependencies.builder().add(sb, "foo").addDefaultSettings();
        System.out.println("BUILDER: \n" + dp);
        
        for (String ns : dp.namespaces()) {
            List<SettingsBuilder> l = dp.getSettings(ns);
            if (l == null) {
                System.out.println("NULL SETTINGS FOR " + ns);
            } else {
                System.out.println("  " + ns);
                for (SettingsBuilder s : l) {
                    System.out.println("    " + s);
                }
            }
        }
        
        Dependencies deps = dp.build();
        X x = deps.getInstance(X.class);
        assertNotNull(x);
        assertEquals("poodle", x.val);
    }
    
    @Test
    public void testValueBinding() throws Throwable {
        Y y = Dependencies.builder().addDefaultSettings().build().getInstance(Y.class);
        assertEquals("chicken", y.val);
    }
    
    @Namespace("foo")
    static class X {
        private final String val;

        @Inject
        X(@Named("monkey") String val) {
            this.val = val;
        }
    }
    static class Y {
        private final String val;

        @Inject
        Y(@Value(value = "robot", namespace =
                @Namespace("ns1")) String val) {
            this.val = val;
        }
    }
}
