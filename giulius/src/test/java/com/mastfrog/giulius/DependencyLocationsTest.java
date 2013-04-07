package com.mastfrog.giulius;

import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.DependenciesBuilder;
import com.mastfrog.guicy.annotations.Defaults;
import com.mastfrog.guicy.annotations.Namespace;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author tim
 */
@Defaults(namespace=@Namespace("hoo"), value="foo=whuz\nchortle=buzz")
public class DependencyLocationsTest {

    File dir = new File(new File(System.getProperty("java.io.tmpdir")), getClass().getName() + "_" + System.currentTimeMillis());
    File hooProps = new File(dir, "hoo" + SettingsBuilder.DEFAULT_EXTENSION);
    File defProps = new File(dir, SettingsBuilder.DEFAULT_NAMESPACE + SettingsBuilder.DEFAULT_EXTENSION);
    File genDefProps = new File(dir, SettingsBuilder.GENERATED_PREFIX + "hoo" + SettingsBuilder.DEFAULT_EXTENSION);

    @Before
    public void setUp() throws IOException {
        System.setProperty(SettingsBuilder.class.getName() + ".log", "true");
        System.setProperty(Dependencies.class.getName() + ".log", "true");
        System.setProperty(Settings.class.getName() + ".log", "true");
        
        dir.mkdirs();
        hooProps = new File(dir, "hoo" + SettingsBuilder.DEFAULT_EXTENSION);
        defProps = new File(dir, SettingsBuilder.DEFAULT_NAMESPACE + SettingsBuilder.DEFAULT_EXTENSION);
        genDefProps = new File(dir, SettingsBuilder.GENERATED_PREFIX + "hoo" + SettingsBuilder.DEFAULT_EXTENSION);
        assertTrue(hooProps.createNewFile());
        assertTrue(defProps.createNewFile());
        assertTrue(genDefProps.createNewFile());
        
        System.out.println("Create " + hooProps);
        System.out.println("Create " + defProps);
        System.out.println("Create " + genDefProps);

        Properties fpp = new Properties();
        Properties dpp = new Properties();
        Properties gpp = new Properties();

        fpp.setProperty("hoo", "bar");
        dpp.setProperty("hoo", "ugg");
        gpp.setProperty("hoo", "goo");

        fpp.setProperty("stuff", "moo");
        dpp.setProperty("werg", "gweez");
        gpp.setProperty("mab", "pladge");

        store(fpp, hooProps);
        store(dpp, defProps);
        store(gpp, genDefProps);
    }

    @Test
    public void test() throws IOException {
        DependenciesBuilder b = new DependenciesBuilder();
        b.addDefaultLocation(dir);
        b.addNamespace("hoo");
        b.addDefaultSettings();
        System.out.println("BUILDER:\n" + b);
        Dependencies deps = b.build();
        
        Settings hooNs = deps.getSettings("hoo");
        assertNotNull(hooNs);
        
        Settings def = deps.getSettings(Namespace.DEFAULT);
        assertNotNull(def);
        
        assertEquals ("bar", hooNs.getString("hoo"));
        assertEquals ("moo", hooNs.getString("stuff"));
        
        assertEquals ("ugg", def.getString("hoo"));
        assertEquals ("buzz", hooNs.getString("chortle"));
    }

    private void store(Properties properties, File file) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            properties.store(out, "x");
        }
    }
}
