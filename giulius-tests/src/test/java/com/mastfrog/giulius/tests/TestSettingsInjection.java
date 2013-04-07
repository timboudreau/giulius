package com.mastfrog.giulius.tests;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@Configurations(value = {"com/mastfrog/giulius/tests/a.properties", "com/mastfrog/giulius/tests/b.properties"})
public class TestSettingsInjection {

    @TestWith(iterate={MA.class, MB.class}, iterateSettings={"com/mastfrog/giulius/tests/ia.properties,com/mastfrog/giulius/tests/ia_1.properties", "com/mastfrog/giulius/tests/ib.properties"})
    public void testIteratedSettings(MultiImplThing thing, @Named("iterated") String whatzit) {
        assertNotNull (thing);
        assertNotNull (whatzit);
        if (thing instanceof MIT1) {
            MIT1 m = (MIT1) thing;
            assertEquals ("A", m.toString());
            assertEquals ("something", m.getOther());
            assertEquals("A", whatzit);
        } else {
            MIT2 m = (MIT2) thing;
            assertEquals ("B", m.toString());
            assertEquals ("nothing", m.getOther());
            assertEquals("B", whatzit);
        }
        assertEquals ("a", thing.getAVal());
        assertEquals ("b", thing.getBVal());
    }
    
    @TestWith(expected=IllegalStateException.class)
    public void throwSomething() {
        throw new IllegalStateException("Bad");
    }
    
    static File propsFile;
    
    @BeforeClass
    public static void setUpClass() {
        System.out.println("SetUpClass");
        String homeDirName = System.getProperty("user.home");
        File homeDir = new File (homeDirName);
        if (homeDir.exists()) {
            String property = System.currentTimeMillis() + ".properties";
            Properties props = new Properties();
            props.setProperty("thisIsMyTest", "mastfrog");
            File f = new File (homeDir, property);
            if (f.exists()) {
                if (!f.delete()) {
                    return;
                }
            }
            try {
                if (!f.createNewFile()) {
                    return;
                }
                FileOutputStream out = new FileOutputStream(f);
                try {
                    props.store(out, null);
                    System.setProperty(TestMethodRunner.TESTS_HOME_SETTINGS_FILE_NAME_SYSTEM_PROPERTY, property);
                    propsFile = f;
                } finally {
                    out.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(TestSettingsInjection.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    @AfterClass
    public static void tearDownClass() {
        if (propsFile != null && propsFile.exists()) {
            assertTrue (propsFile.delete());
        }
    }
    
    @TestWith(MA.class)
    @Configurations("com/mastfrog/giulius/tests/ib.properties")
    public void deleteMe(@Named("thisIsMyTest") String mastfrog) {
        assertNotNull(mastfrog);
        assertEquals("mastfrog", mastfrog);
    }
    
    static class MA extends AbstractModule {
        @Override
        protected void configure() {
            bind (MultiImplThing.class).to(MIT1.class);
        }
    }

    static class MB extends AbstractModule {
        @Override
        protected void configure() {
            bind (MultiImplThing.class).to(MIT2.class);
        }
    }

    interface MultiImplThing {
        public String getOther();
        public String getAVal();
        public String getBVal();
    }

    static class MIT1 implements MultiImplThing {
        private final String what;
        private final String other;
        private final String aval;
        private final String bval;
        @Inject
        public MIT1(@Named("iterated") String what, @Named("otherValue") String other, @Named("aval") String aval, @Named("bval") String bval) {
            this.what = what;
            this.other = other;
            this.aval = aval;
            this.bval = bval;
        }

        public String toString() {
            return what;
        }

        @Override
        public String getOther() {
            return other;
        }

        @Override
        public String getAVal() {
            return aval;
        }

        @Override
        public String getBVal() {
            return bval;
        }
    }

    static class MIT2 implements MultiImplThing {
        private final String what;
        private final String other;
        private final String aval;
        private final String bval;
        @Inject
        public MIT2(@Named("iterated") String what, @Named("otherValue") String other, @Named("aval") String aval, @Named("bval") String bval) {
            this.what = what;
            this.other = other;
            this.aval = aval;
            this.bval = bval;
            System.out.println("Created an MIT2 with " + what);
        }

        public String toString() {
            return what;
        }

        @Override
        public String getOther() {
            return other;
        }

        @Override
        public String getAVal() {
            return aval;
        }

        @Override
        public String getBVal() {
            return bval;
        }
    }
    @TestWith(ModuleC.class)
    @Configurations(value = {"com/mastfrog/giulius/tests/c.properties", "com/mastfrog/giulius/tests/d.properties"})
    public void test(Thing t) {
        assertNotNull (t);
        assertEquals("a", t.a);
        assertEquals("b", t.b);
        assertEquals("c", t.c);
        assertEquals("d", t.d);
    }

    static class Thing {
        private final String a;
        private final String b;
        private final String c;
        private final String d;
        @Inject
        public Thing(@Named("aval")String a, @Named("bval")String b, @Named("cval")String c, @Named("dval")String d) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
        }
    }
}
