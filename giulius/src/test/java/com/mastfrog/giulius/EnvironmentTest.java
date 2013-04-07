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

import com.mastfrog.giulius.Dependencies;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.settings.SettingsBuilder;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Tim Boudreau
 */
public class EnvironmentTest {
    @Test
    public void test() throws IOException {
        String osname = System.getProperty("os.name");
        assertNotNull(osname);
        
        Dependencies d1 = new Dependencies(SettingsBuilder.createDefault().build());
        EnvThing e = d1.getInstance(EnvThing.class);
        assertNotNull(e);
        assertEquals(osname, e.toString());
    }

    private static class EnvThing {
        private final String osName;

        @Inject
         EnvThing(@Named("os.name") String osName) {
            this.osName = osName;
        }

        @Override
        public String toString() {
            return osName;
        }
    }

    private static class M1 extends AbstractModule {
        @Override
        protected void configure() {
        }
    }
}
