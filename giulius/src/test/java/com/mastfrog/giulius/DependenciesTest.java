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

import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.settings.MutableSettings;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.giulius.annotations.Namespace;
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
