/*
 * The MIT License
 *
 * Copyright 2010-2018 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
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
