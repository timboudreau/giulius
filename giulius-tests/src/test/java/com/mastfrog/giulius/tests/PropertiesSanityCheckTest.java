package com.mastfrog.giulius.tests;

import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import java.io.IOException;
import static org.junit.Assert.*;
import org.junit.Test;

public class PropertiesSanityCheckTest {
    String[] locations = new String[]{
        "com/mastfrog/giulius/tests/a.properties", "com/mastfrog/giulius/tests/b.properties",
        "com/mastfrog/giulius/tests/c.properties", "com/mastfrog/giulius/tests/d.properties"};

    @Test
    public void testPropertiesFilesForSettingsInjectionExist() throws IOException {
        SettingsBuilder b = SettingsBuilder.createDefault();
        for (String location : locations) {
            b = b.add(location);
        }
        Settings s = b.build();
        for (char c = 'a'; c < 'd'; c++) {
            String key = c + "val";
            assertNotNull(key + " not found", s.getString(c + "val"));
            assertFalse(key + " empty", s.getString(c + "val").length() == 0);
            assertEquals(key + " incorrect", "" + c,
                 s.getString(c + "val"));
        }
    }
}
