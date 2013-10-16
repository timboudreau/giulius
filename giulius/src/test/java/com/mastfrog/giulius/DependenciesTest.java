/*
 *               BSD LICENSE NOTICE
 * Copyright (c) 2010-2012, Tim Boudreau
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
package com.mastfrog.giulius;

import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.settings.MutableSettings;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.guicy.annotations.Namespace;
import java.io.IOException;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Tests the {@link Dependencies} class.
 */
public class DependenciesTest {

    @Test
    public void testDefault() throws IOException {
        Dependencies dependencies = Dependencies.builder().add(new SettingsBuilder().add("productionMode", "false").build(), Namespace.DEFAULT).build();
        assertFalse(dependencies.isProductionMode());
    }

    @Test
    public void testProductionMode() throws IOException {
        Dependencies dependencies = Dependencies.builder().add(new SettingsBuilder().add("productionMode", "true").build(), Namespace.DEFAULT).build();
        assertTrue(dependencies.isProductionMode());
    }

    @Test
    public void testNamedSettings() throws Throwable {
        MutableSettings settings = SettingsBuilder.createDefault().buildMutableSettings();

        settings.setString("stuff", "This is stuff");
        assertEquals("This is stuff", settings.getString("stuff"));
        assertTrue(settings.allKeys().contains("stuff"));
        assertTrue(settings.toProperties().containsKey("stuff"));

        Dependencies deps = new Dependencies(settings);
        Thing thing = deps.getInstance(Thing.class);
        assertNotNull(thing);
        assertNotNull(thing.value);
        assertEquals("This is stuff", thing.value);
        assertNull(thing.moreStuff);
    }

    private static class Thing {

        @Named("stuff")
        @Inject
        String value;
        @Named("optional")
        @Inject(optional = true)
        String moreStuff;
    }
}
