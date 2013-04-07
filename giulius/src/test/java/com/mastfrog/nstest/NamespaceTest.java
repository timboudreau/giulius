/*                       BSD LICENSE NOTICE
 * Copyright (c) 2010-2012, Tim Boudreau, All Rights Reserved
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met: 
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer. 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution. 
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.mastfrog.nstest;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.guicy.annotations.Namespace;
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
/*
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
        System.out.println("NS " + ns.liesel);
        System.out.println("NON " + non.liesel);
        assertEquals("frog", non.liesel);
        assertEquals("cat", ns.liesel);
        assertFalse(ns.liesel.equals(non.liesel));
    }
    */

    @Test
    public void testNamespaceOnImplementationClass() throws Exception {
        SettingsBuilder sb = new SettingsBuilder("foo").add("bar", "true");

        Dependencies deps = Dependencies.builder().add(sb.build(), "foo").add(new MM()).build();
        
        assertNotNull(deps.getSettings("foo"));
        assertTrue(deps.getSettings("foo").getBoolean("bar"));
        
        deps.getInjector();
        
        System.err.println("\n\n\n\n\n\n\n\n********************************************\n\n\n");

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
            System.out.println("INJECTED SETTINGS IS " + settings);
            this.settings = settings;
            Thread.dumpStack();
        }

        @Override
        public Boolean getFoo() {
            return settings.getBoolean("bar");
        }
    }
}
