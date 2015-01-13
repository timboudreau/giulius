/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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

import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import static com.mastfrog.giulius.SettingsBindings.BIG_DECIMAL;
import static com.mastfrog.giulius.SettingsBindings.BIG_INTEGER;
import static com.mastfrog.giulius.SettingsBindings.BOOLEAN;
import static com.mastfrog.giulius.SettingsBindings.DOUBLE;
import static com.mastfrog.giulius.SettingsBindings.FLOAT;
import static com.mastfrog.giulius.SettingsBindings.INT;
import static com.mastfrog.giulius.SettingsBindings.STRING;
import com.mastfrog.guicy.annotations.Namespace;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import java.io.IOException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class TestBindingDisablement {

    private Settings settings() throws IOException {
        Settings s = new SettingsBuilder()
                .add("foo", "1.5")
                .add("bar", "hey")
                .add("baz", "true").build();
        return s;
    }

    @Test
    public void normalBindings() throws Exception {
        Dependencies deps = Dependencies.builder()
                .add(new M())
                .add(settings(), Namespace.DEFAULT)
                .build();
        Thing thing = deps.getInstance(Thing.class);
        assertNotNull(thing);
        assertEquals(1.5, thing.foo, 0.01F);
        assertEquals("hey", thing.bar);
        assertTrue(thing.baz);
    }

    @Test
    public void trimmedBindings() throws Exception {
        Dependencies deps = Dependencies.builder()
                .add(new M())
                .add(settings(), Namespace.DEFAULT)
                .disableBindings(INT, BIG_DECIMAL, BIG_INTEGER, DOUBLE)
                .build();
        Thing thing = deps.getInstance(Thing.class);
        assertNotNull(thing);
        assertEquals(1.5, thing.foo, 0.01F);
        assertEquals("hey", thing.bar);
        assertTrue(thing.baz);
    }

    @Test
    public void trimmedBindings2() throws Exception {
        Dependencies deps = Dependencies.builder()
                .add(new M())
                .add(settings(), Namespace.DEFAULT)
                .enableOnlyBindingsFor(STRING, FLOAT, BOOLEAN)
                .build();
        Thing thing = deps.getInstance(Thing.class);
        assertNotNull(thing);
        assertEquals(1.5, thing.foo, 0.01F);
        assertEquals("hey", thing.bar);
        assertTrue(thing.baz);
    }

    @Test(expected = CreationException.class)
    public void brokenBindings() throws Exception {
        Dependencies deps = Dependencies.builder()
                .add(new M())
                .add(settings(), Namespace.DEFAULT)
                .disableBindings(STRING, FLOAT, BOOLEAN)
                .build();
        deps.getInstance(Thing.class);
    }

    @Test(expected = CreationException.class)
    public void moreBrokenBindings() throws Exception {
        Dependencies deps = Dependencies.builder()
                .add(new M())
                .add(settings(), Namespace.DEFAULT)
                .disableBindings(FLOAT, BIG_DECIMAL)
                .build();
        deps.getInstance(Thing.class);
    }

    static class M extends AbstractModule {

        @Override
        protected void configure() {
            bind(Thing.class);
        }
    }

    static class Thing {

        final float foo;
        final String bar;
        final boolean baz;

        @Inject
        public Thing(@Named("foo") float foo, @Named("bar") String bar, @Named("baz") boolean baz) {
            this.foo = foo;
            this.bar = bar;
            this.baz = baz;
        }
    }
}
