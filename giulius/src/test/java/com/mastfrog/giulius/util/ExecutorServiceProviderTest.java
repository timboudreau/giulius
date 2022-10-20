/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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
package com.mastfrog.giulius.util;

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.mastfrog.giulius.Dependencies;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class ExecutorServiceProviderTest {

    @Test
    public void testSomeMethod() throws IOException, InterruptedException, ExecutionException {
        Dependencies deps = new Dependencies(new M());
        Thing thing = deps.getInstance(Thing.class);
        ExecutorService exe = deps.getInstance(ExecutorService.class);
        assertNotNull(exe);
        assertSame(thing.p.get(), exe);
        exe.submit(() -> {
            new StringBuilder("Hello.").append(1);
        }).get();
        assertFalse(exe.isShutdown());
        assertFalse(exe.isTerminated());
        assertSame(exe, thing.p.get());
        deps.shutdown();
        assertTrue(exe.isShutdown());
    }

    static class M implements Module {

        @Override
        @SuppressWarnings("deprecation")
        public void configure(Binder binder) {
            Provider<ExecutorService> a = ExecutorServiceProvider.provider(2, binder);
            binder.bind(ExecutorService.class).toProvider(a);
        }
    }

    static class Thing {

        final Provider<ExecutorService> p;

        @Inject
        public Thing(Provider<ExecutorService> p) {
            this.p = p;
        }
    }
}
