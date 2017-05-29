package com.mastfrog.giulius;

import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.DependenciesBuilder;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.giulius.annotations.Namespace;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author tim
 */
public class ConversionTest {

    @Test
    public void test() throws IOException {
        Settings s = new SettingsBuilder().add("foo", "23").add("bar", "32").build();
        DependenciesBuilder b = new DependenciesBuilder().add(s, Namespace.DEFAULT);
        Dependencies deps = b.build();

        Q q = deps.getInstance(Q.class);
        assertNotNull(q);
    }

//    @Namespace("foo")
    static class Q {

        @Inject
        Q(@Named("foo") int i, @Named("foo") double d, @Named("foo") byte b,
                @Named("foo") float f, @Named("foo") long l,
                @Named("foo") short s) {
            assertEquals(23, i);
            assertEquals(23, (int) d);
            assertEquals(23, b);
            assertEquals(23, (int) f);
            assertEquals(23, l);
            assertEquals(23, s);
        }
    }
}
