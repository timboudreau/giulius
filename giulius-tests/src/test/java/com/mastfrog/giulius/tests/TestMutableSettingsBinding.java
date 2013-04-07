package com.mastfrog.giulius.tests;

import com.mastfrog.settings.MutableSettings;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
public class TestMutableSettingsBinding {
    
    @Test
    public void test(MutableSettings a, MutableSettings b) {
        assertEquals(a.allKeys(), b.allKeys());
        assertNull(a.getString("wug"));
        assertNull(b.getString("wug"));
        a.setString("wug", "wubble");
        assertEquals("wubble", a.getString("wug"));
        assertEquals("wubble", b.getString("wug"));
    }
}
