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
package com.mastfrog.nstest;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.annotations.Namespace;
import com.mastfrog.settings.GetsNonNamespacedValue;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.settings.ns1.GetsNamespacedValue;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class NamespaceTest {
    @Test
    public void testNamespaceAnnotationOnPackage() throws Exception {
        Dependencies deps = Dependencies.builder().addDefaultSettings()
                .add(
                new SettingsBuilder("ns1")
                .addGeneratedDefaultsFromClasspath()
                .addDefaultsFromClasspath().build(),
                "ns1").build();

        assertNotNull(deps.getSettings());
        assertNotNull(deps.getSettings().getString("liesel"));

        assertNotNull(deps.getSettings(Namespace.DEFAULT));
        assertNotNull(deps.getSettings("ns1"));

        assertNotNull(deps.getSettings(Namespace.DEFAULT).getString("liesel"));
        assertNotNull(deps.getSettings("ns1").getString("liesel"));

        GetsNonNamespacedValue non = deps.getInstance(GetsNonNamespacedValue.class);
        GetsNamespacedValue ns = deps.getInstance(GetsNamespacedValue.class);
        assertEquals("frog", non.liesel);
        assertEquals("cat", ns.liesel);
        assertFalse(ns.liesel.equals(non.liesel));
    }

    @Test
    public void testNamespaceOnImplementationClass() throws Exception {
        SettingsBuilder sb = new SettingsBuilder("foo").add("bar", "true");

        Dependencies deps = Dependencies.builder().add(sb.build(), "foo").add(new MM()).build();
        
        assertNotNull(deps.getSettings("foo"));
        assertTrue(deps.getSettings("foo").getBoolean("bar"));
        
        deps.getInjector();
        
        IFace i = deps.getInstance(IFace.class);
        assertNotNull(i);
        assertTrue(i instanceof Implementation);

        assertNotNull(i.getFoo());

        assertTrue(i.getFoo());

    }

    public static class MM extends AbstractModule {

        @Override
        protected void configure() {
            bind(IFace.class).to(Implementation.class);
        }
    }

    @Namespace("foo")
    public interface IFace {

        public Boolean getFoo();
    }

    @Namespace("foo")
    public static class Implementation implements IFace {

        private final Settings settings;

        @Inject
        public Implementation(Settings settings) {
            this.settings = settings;
        }

        @Override
        public Boolean getFoo() {
            return settings.getBoolean("bar");
        }
    }
}
