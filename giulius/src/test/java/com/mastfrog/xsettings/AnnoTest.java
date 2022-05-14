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
package com.mastfrog.xsettings;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.DependenciesBuilder;
import com.mastfrog.giulius.annotations.Defaults;
import com.mastfrog.giulius.annotations.Namespace;
import com.mastfrog.giulius.annotations.Value;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.util.streams.Streams;
import java.io.InputStream;
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
