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

package com.mastfrog.giulius.tests;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
public class TestHasMatchingPropertiesFile {
    
    @TestWith(QM.class)
    public void test(Q q) {
        System.out.println("abcdefgh");
        assertNotNull(q);
        assertEquals("Bub's", q.barbecue);
    }
    
    @TestWith(QM.class)
    @Configurations("ms/glo/x.properties")
    public void more(R r) {
        assertNotNull(r);
        assertEquals("spoon", r.fork);
    }
    
    public static final class QM extends AbstractModule {

        @Override
        protected void configure() {
            System.out.println("CONFIGURE-module");
        }
        
    }
    
    private static final class Q {
        @Inject
        @Named("barbecue")
        public String barbecue;
    }
    
    private static final class R {
        @Inject
        @Named("fork")
        public String fork;
    }
}
